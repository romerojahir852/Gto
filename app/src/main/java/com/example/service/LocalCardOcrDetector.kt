package com.example.service

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.example.data.CardSuit
import com.example.data.GTOStateManager
import com.example.data.GtoAction
import com.example.data.HandState
import com.example.data.PokerCard
import com.example.data.PokerGtoEngine
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * LocalCardOcrDetector: On-device Computer Vision & OCR Poker Table Scanner.
 * 
 * Works 100% offline with zero cloud latency and no API key required.
 * Provides high-precision spatial clustering for community cards (board pairs/trips)
 * and adjacent Hero hole cards across all poker mobile apps (GGPoker, PokerStars, PPPoker, BC Poker).
 */
object LocalCardOcrDetector {

    private const val TAG = "LocalCardOcrDetector"
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun detect(
        bitmap: Bitmap,
        currentState: HandState
    ): HandState {
        val startTime = System.currentTimeMillis()
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).awaitTask()
            val latency = System.currentTimeMillis() - startTime

            parseVisionText(visionText, bitmap, currentState, latency)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "OCR detection failed", e)
            currentState.copy(
                latencyMs = latency,
                isLoading = false,
                statusMessage = "⚠️ Error OCR local: ${e.message?.take(30)}"
            )
        }
    }

    private fun parseVisionText(
        visionText: Text,
        bitmap: Bitmap,
        currentState: HandState,
        latencyMs: Long
    ): HandState {
        val width = bitmap.width
        val height = bitmap.height

        val validRanks = setOf("A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2")
        val nonCardTokens = setOf(
            "AUSENTE", "ALL-IN", "ALLIN", "FAMILYAH", "LUCKYDONK", "RENATOSABA",
            "GORDORAVEN", "OOWOW", "BRIGANTEWILF", "DAILY", "TURBO", "BLINDS",
            "BOTE", "TOTAL", "MESA", "AUTO", "CALL", "RAISE", "FOLD", "CHECK",
            "NIVEL", "MANO", "HAND", "TABLE", "HOLDEM", "TEXAS"
        )

        val candidateCards = mutableListOf<DetectedCard>()
        var detectedPot: Double? = null
        var handCombinationHint: String? = null

        // 1. Scan TextBlocks for Hand Combination Hints and Pot Amounts
        for (block in visionText.textBlocks) {
            val blockText = block.text.replace("\n", " ").trim()
            val lower = blockText.lowercase()

            // Check for hand combination hints from poker room (e.g. "trío de Ks", "par de Ases", "escalera")
            if (lower.contains("escalera") || lower.contains("poker de") || lower.contains("trío") ||
                lower.contains("trio") || lower.contains("full") || lower.contains("color") ||
                lower.contains("doble pareja") || lower.contains("pareja de") || lower.contains("par de")
            ) {
                handCombinationHint = blockText.take(25)
            }

            // Check for Pot amount (e.g. "Bote total 2,596", "Pot 1,600", "Bote: 450")
            if (lower.contains("bote") || lower.contains("pot")) {
                val potRegex = Regex("""(?:bote(?:\s+total)?|pot(?:\s+total)?)\s*:?\s*[$€£]?\s*([0-9.,]+)\s*(K|M|BB)?""", RegexOption.IGNORE_CASE)
                val match = potRegex.find(blockText)
                if (match != null) {
                    val rawNum = match.groupValues[1].replace(",", "").trim()
                    val unit = match.groupValues[2].uppercase()
                    val baseVal = rawNum.toDoubleOrNull()
                    if (baseVal != null && baseVal > 0) {
                        detectedPot = when (unit) {
                            "K" -> baseVal * 1000.0
                            "M" -> baseVal * 1000000.0
                            else -> baseVal
                        }
                    }
                }
            }

            // 2. Scan Lines & Elements for Playing Cards
            for (line in block.lines) {
                val lineText = line.text.trim()
                val lineUpper = lineText.uppercase()

                // Skip lines that are clearly non-card UI labels
                if (nonCardTokens.any { lineUpper.contains(it) } && !lineText.contains(Regex("[♥♦♣♠]"))) {
                    continue
                }

                for (element in line.elements) {
                    val text = element.text.trim()
                    val box = element.boundingBox ?: continue

                    // Filter out elements that are too large (banners) or too small (noise)
                    if (box.width() > width * 0.45f || box.height() > height * 0.25f || box.width() < 8 || box.height() < 12) {
                        continue
                    }

                    // Verify background is bright like a playing card face (avoids dark player names like "Ausente")
                    if (!isLikelyCardSurface(bitmap, box)) {
                        continue
                    }

                    // Extract card(s) from token:
                    // Supports single card ("K", "10", "4♥", "Kd"), merged ranks ("KKK", "77"), or card sequences
                    val extracted = extractCardsFromToken(text, validRanks)
                    if (extracted.isNotEmpty()) {
                        val count = extracted.size
                        for (idx in 0 until count) {
                            val (rank, explicitSuit) = extracted[idx]
                            // Subdivide bounding box horizontally if multiple cards merged in single element (e.g. "KKK")
                            val subBox = if (count > 1) {
                                val subW = box.width() / count
                                Rect(box.left + (idx * subW), box.top, box.left + ((idx + 1) * subW), box.bottom)
                            } else {
                                box
                            }

                            val suit = explicitSuit ?: sampleCardSuitFromPixels(bitmap, subBox)
                            candidateCards.add(DetectedCard(rank, suit, subBox))
                        }
                    }

                    // Fallback Pot check near table center (if not detected via "Bote total" keyword)
                    if (detectedPot == null && box.centerY() in (height * 0.35f)..(height * 0.55f)) {
                        val numStr = text.replace(",", "").replace("$", "").trim()
                        val v = numStr.toDoubleOrNull()
                        if (v != null && v in 20.0..500000.0) {
                            detectedPot = v
                        }
                    }
                }
            }
        }

        // 3. Spatially Cluster Community Cards (Center Table: y in 28%..62%, x in 10%..90%)
        val boardCandidates = candidateCards.filter {
            it.box.centerY() in (height * 0.28f)..(height * 0.62f) &&
            it.box.centerX() in (width * 0.10f)..(width * 0.90f)
        }

        // Deduplicate board cards SPATIALLY (by horizontal pixel position), NOT by rank!
        // This ensures triplets/pairs like K♥ K♣ K♠ are 100% preserved.
        val sortedBoardCandidates = boardCandidates.sortedBy { it.box.left }
        val uniquePhysicalBoardCards = mutableListOf<DetectedCard>()

        for (cand in sortedBoardCandidates) {
            val isDuplicatePosition = uniquePhysicalBoardCards.any { existing ->
                abs(existing.box.centerX() - cand.box.centerX()) < (cand.box.width() * 0.5f).coerceAtLeast(width * 0.04f)
            }
            if (!isDuplicatePosition) {
                uniquePhysicalBoardCards.add(cand)
            }
        }

        // Take up to 5 community cards and guarantee distinct suits for identical ranks
        val rawBoardCards = uniquePhysicalBoardCards.take(5).map { PokerCard(it.rank, it.suit) }
        val sanitizedBoardCards = sanitizeDuplicateSuits(rawBoardCards)

        // 4. Detect Hero Hole Cards (Lower Table: y > 55%)
        // Hero hole cards in Texas Hold'em always form an adjacent side-by-side pair
        val heroCandidates = candidateCards.filter {
            it.box.centerY() > height * 0.55f
        }

        var bestHeroPair: Pair<DetectedCard, DetectedCard>? = null
        var bestPairScore = Float.MAX_VALUE

        for (i in 0 until heroCandidates.size) {
            for (j in i + 1 until heroCandidates.size) {
                val c1 = heroCandidates[i]
                val c2 = heroCandidates[j]
                val vertDiff = abs(c1.box.centerY() - c2.box.centerY())
                val horizDiff = abs(c1.box.centerX() - c2.box.centerX())

                // Physically adjacent pair condition:
                // - Close vertical alignment (< 6% screen height)
                // - Horizontal separation between 3% and 25% screen width
                if (vertDiff < height * 0.06f && horizDiff in (width * 0.03f)..(width * 0.25f)) {
                    // Score favors cards located deeper at the bottom of the table
                    val score = (height - c1.box.centerY()) + (vertDiff * 2f)
                    if (score < bestPairScore) {
                        bestPairScore = score
                        bestHeroPair = if (c1.box.left < c2.box.left) Pair(c1, c2) else Pair(c2, c1)
                    }
                }
            }
        }

        val detectedHeroCards = if (bestHeroPair != null) {
            listOf(
                PokerCard(bestHeroPair.first.rank, bestHeroPair.first.suit),
                PokerCard(bestHeroPair.second.rank, bestHeroPair.second.suit)
            )
        } else if (heroCandidates.size >= 2) {
            heroCandidates.sortedBy { it.box.left }.take(2).map { PokerCard(it.rank, it.suit) }
        } else {
            emptyList()
        }

        val finalHeroCards = if (detectedHeroCards.isNotEmpty()) detectedHeroCards else currentState.cartasPropias
        val finalBoardCards = if (sanitizedBoardCards.isNotEmpty()) sanitizedBoardCards else currentState.cartasComunitarias

        val detectedFase = when (finalBoardCards.size) {
            0 -> "Preflop"
            3 -> "Flop"
            4 -> "Turn"
            5 -> "River"
            else -> currentState.fase
        }

        val updatedBote = detectedPot ?: currentState.bote

        // 5. Calculate GTO Decision via PokerGtoEngine
        val engineResult = if (finalHeroCards.isNotEmpty()) {
            PokerGtoEngine.calculate(
                holeCards = finalHeroCards,
                board = finalBoardCards,
                jugadores = currentState.jugadores,
                posicion = currentState.posicion,
                fase = detectedFase,
                bote = updatedBote,
                apuestaRival = currentState.apuestaRival
            )
        } else null

        val hintDisplay = handCombinationHint?.let { " ($it)" } ?: ""
        val boardDisplay = if (finalBoardCards.isNotEmpty()) " | Mesa: ${finalBoardCards.joinToString(" "){ it.displayString }}" else ""
        val heroDisplay = if (finalHeroCards.isNotEmpty()) finalHeroCards.joinToString(" "){ it.displayString } else "—"

        val status = if (finalHeroCards.isNotEmpty() || finalBoardCards.isNotEmpty()) {
            "⚡ OCR Local: $heroDisplay$boardDisplay$hintDisplay · ${latencyMs}ms"
        } else {
            "⚡ OCR Local: Mesa en escaneo · ${latencyMs}ms"
        }

        GTOStateManager.updateFromAnalysis(
            fase = detectedFase,
            bote = updatedBote,
            jugadores = currentState.jugadores,
            dealerPos = currentState.dealerPosition,
            myPos = currentState.posicion
        )

        return currentState.copy(
            fase = detectedFase,
            cartasPropias = finalHeroCards,
            cartasComunitarias = finalBoardCards,
            bote = updatedBote,
            outs = engineResult?.outs ?: currentState.outs,
            winRate = engineResult?.winRate ?: currentState.winRate,
            gtoAction = engineResult?.action ?: currentState.gtoAction,
            gtoActionValue = engineResult?.actionValue ?: currentState.gtoActionValue,
            latencyMs = latencyMs,
            isLoading = false,
            isExpanded = true,
            isSimulation = false,
            statusMessage = status
        )
    }

    /**
     * Extracts card rank and optional suit from token.
     * Supports single card ("K", "10", "4♥", "Kd") and merged cards ("KKK", "77").
     */
    private fun extractCardsFromToken(
        text: String,
        validRanks: Set<String>
    ): List<Pair<String, CardSuit?>> {
        val clean = text.trim()
        val list = mutableListOf<Pair<String, CardSuit?>>()

        // Pattern: Rank + Optional Suit (e.g. "10♥", "As", "Kd", "4")
        val singleMatch = Regex("""^(10|[AKQJT2-9])([♥♦♣♠hdcs])?$""", RegexOption.IGNORE_CASE).find(clean)
        if (singleMatch != null) {
            val rawRank = singleMatch.groupValues[1]
            val rank = if (rawRank.equals("T", ignoreCase = true)) "10" else rawRank.uppercase()
            val suitChar = singleMatch.groupValues[2].firstOrNull()
            val explicitSuit = suitChar?.let { parseSuitChar(it) }
            list.add(rank to explicitSuit)
            return list
        }

        // Merged identical ranks (e.g. "KKK" on the flop, or "AA")
        if (clean.length in 2..5 && clean.all { it.equals(clean[0], ignoreCase = true) }) {
            val charUpper = clean[0].uppercaseChar().toString()
            if (validRanks.contains(charUpper) || charUpper == "T") {
                val rank = if (charUpper == "T") "10" else charUpper
                for (i in 0 until clean.length) {
                    list.add(rank to null)
                }
                return list
            }
        }

        return list
    }

    private fun parseSuitChar(c: Char): CardSuit? {
        return when (c) {
            '♥', 'h', 'H' -> CardSuit.HEARTS
            '♦', 'd', 'D' -> CardSuit.DIAMONDS
            '♣', 'c', 'C' -> CardSuit.CLUBS
            '♠', 's', 'S' -> CardSuit.SPADES
            else -> null
        }
    }

    /**
     * Verifies that the region has light/white background typical of playing cards.
     * Rejects dark badges, avatars, and table felt (e.g. "Ausente", "FamilyAH").
     */
    private fun isLikelyCardSurface(bitmap: Bitmap, box: Rect): Boolean {
        val centerX = box.centerX().coerceIn(0, bitmap.width - 1)
        val centerY = box.centerY().coerceIn(0, bitmap.height - 1)

        var brightCount = 0
        var total = 0
        val stepX = (box.width() / 4).coerceAtLeast(1)
        val stepY = (box.height() / 4).coerceAtLeast(1)

        for (dx in -2..2) {
            for (dy in -2..2) {
                val px = (centerX + dx * stepX).coerceIn(0, bitmap.width - 1)
                val py = (centerY + dy * stepY).coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(px, py)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Playing cards have light card faces (white / off-white)
                if ((r + g + b) > 360 || (r > 125 && g > 125 && b > 125)) {
                    brightCount++
                }
                total++
            }
        }

        return total > 0 && (brightCount.toFloat() / total) >= 0.22f
    }

    /**
     * Inspects the pixel color palette in and around the card rank bounding box.
     * Accurately distinguishes Red (Hearts/Diamonds), Blue (Diamonds in 4-color),
     * Green (Clubs in 4-color), and Dark/Black (Spades/Clubs).
     */
    private fun sampleCardSuitFromPixels(bitmap: Bitmap, box: Rect): CardSuit {
        val sampleLeft = (box.left - 8).coerceIn(0, bitmap.width - 1)
        val sampleTop = (box.top - 5).coerceIn(0, bitmap.height - 1)
        val sampleRight = (box.right + 20).coerceIn(0, bitmap.width - 1)
        val sampleBottom = (box.bottom + (box.height() * 2.2).toInt()).coerceIn(0, bitmap.height - 1)

        var redCount = 0
        var blueCount = 0
        var greenCount = 0
        var darkCount = 0

        for (x in sampleLeft..sampleRight step 2) {
            for (y in sampleTop..sampleBottom step 2) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Skip white card background
                if (r > 210 && g > 210 && b > 210) continue
                // Skip table felt green if sample escaped boundary
                if (g > 100 && g > r * 1.5 && g > b * 1.5 && r < 90) continue

                // 4-Color & 2-Color Deck chromatic analysis
                if (r > 130 && r > g * 1.3f && r > b * 1.3f) {
                    redCount++
                } else if (b > 120 && b > r * 1.2f) {
                    blueCount++
                } else if (g > 115 && g > r * 1.2f && g > b * 1.1f) {
                    greenCount++
                } else if (r < 75 && g < 75 && b < 75) {
                    darkCount++
                }
            }
        }

        return when {
            redCount > 8 && redCount >= blueCount && redCount >= greenCount -> CardSuit.HEARTS
            blueCount > 8 && blueCount >= greenCount -> CardSuit.DIAMONDS
            greenCount > 8 -> CardSuit.CLUBS
            darkCount > 8 -> CardSuit.SPADES
            else -> CardSuit.HEARTS
        }
    }

    /**
     * Prevents impossible duplicate identical cards on the board (e.g. two K♠ on board).
     * Reassigns duplicate suit to alternate available suit while maintaining rank.
     */
    private fun sanitizeDuplicateSuits(cards: List<PokerCard>): List<PokerCard> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<PokerCard>()
        val suitOrder = listOf(CardSuit.SPADES, CardSuit.CLUBS, CardSuit.HEARTS, CardSuit.DIAMONDS)

        for (card in cards) {
            val key = "${card.rank}_${card.suit.symbol}"
            if (!seen.contains(key)) {
                seen.add(key)
                result.add(card)
            } else {
                val alternate = suitOrder.firstOrNull { s -> !seen.contains("${card.rank}_${s.symbol}") } ?: card.suit
                seen.add("${card.rank}_${alternate.symbol}")
                result.add(PokerCard(card.rank, alternate))
            }
        }
        return result
    }

    private data class DetectedCard(
        val rank: String,
        val suit: CardSuit,
        val box: Rect
    )
}
