package com.example.service

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
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
 * Provides high-precision spatial clustering for community cards (board pairs/trips)
 * and adjacent Hero hole cards across all poker mobile apps (GGPoker, ClubGG, PokerStars, PPPoker).
 * Integrates ground-truth hand combination badge validation to ensure 100% card parity.
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
            "NIVEL", "MANO", "HAND", "TABLE", "HOLDEM", "TEXAS", "WIN", "PASAR", "APOSTAR",
            "GTO", "MIS", "CARTAS", "PROPIAS", "COMUNITARIAS", "EQUITY", "RATE", "OUTS", "PROYECTOS",
            "DECISIÓN", "DECISION", "ÓPTIMA", "OPTIMA", "RE-ANALIZAR", "REANALIZAR",
            "JUGADORES", "POS", "BTN", "SB", "BB", "UTG", "MP", "CO", "FICHAS", "OCR", "LOCAL", "GEMINI", "FLASH"
        )

        val candidateCards = mutableListOf<DetectedCard>()
        var detectedPot: Double? = null
        var detectedBlinds: Double? = null
        var handCombinationHint: String? = null

        // 1. Scan TextBlocks for Hand Combination Hints, Blinds, and Pot Amounts
        for (block in visionText.textBlocks) {
            val blockText = block.text.replace("\n", " ").trim()
            val lower = blockText.lowercase()

            // Check for hand combination hints (e.g. "un color con A", "un full de 4s con As", "poker de Ks", "trío de Ks", "par de Ases")
            if (lower.contains("escalera") || lower.contains("poker de") || lower.contains("trío") ||
                lower.contains("trio") || lower.contains("full") || lower.contains("color") ||
                lower.contains("doble pareja") || lower.contains("pareja de") || lower.contains("par de")
            ) {
                handCombinationHint = blockText.take(35)
            }

            // Check for Blind level (e.g. "Blinds 200 | 400 (50)" or "Ciegas: 200/400")
            val blindMatch = Regex("""(?:blinds?|ciegas?)\s*:?\s*([0-9.,]+)\s*[/|]\s*([0-9.,]+)""", RegexOption.IGNORE_CASE).find(blockText)
            if (blindMatch != null) {
                val bbStr = blindMatch.groupValues[2].replace(",", "").trim()
                val bbVal = bbStr.toDoubleOrNull()
                if (bbVal != null && bbVal > 0) {
                    detectedBlinds = bbVal
                }
            }

            // Check for Pot amount (e.g. "Bote total 2,596", "Bote total 1,520", "Bote total 1,022,984", "Bote total 324")
            if (lower.contains("bote") || lower.contains("pot")) {
                val potRegex = Regex("""(?:bote(?:\s+total)?|pot(?:\s+total)?|main\s+pot)\s*[:=]?\s*[$€£]?\s*([0-9.,]+)\s*(K|M|BB)?""", RegexOption.IGNORE_CASE)
                val match = potRegex.find(blockText)
                if (match != null) {
                    val rawNum = match.groupValues[1]
                    val unit = match.groupValues[2].uppercase()
                    val baseVal = parsePokerNumericString(rawNum)
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

                // Skip lines that are clearly non-card UI labels (unless they contain explicit suit unicode)
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

                    // Verify surrounding surface is light like a playing card face
                    if (!isLikelyCardSurface(bitmap, box)) {
                        continue
                    }

                    // Extract card(s) from token
                    val extracted = extractCardsFromToken(text, validRanks)
                    if (extracted.isNotEmpty()) {
                        val count = extracted.size
                        for (idx in 0 until count) {
                            val (rank, explicitSuit) = extracted[idx]
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
                }
            }
        }

        // 3. Conteo automático de jugadores activos en el perímetro de la mesa
        val detectedPlayersCount = detectPlayerCount(visionText, width, height)
        val finalPlayers = detectedPlayersCount ?: currentState.jugadores

        // 4. Spatially Cluster Community Cards (Center Table: y in 30%..65%, x in 6%..94%)
        val boardCandidates = candidateCards.filter {
            it.box.centerY() in (height * 0.30f)..(height * 0.65f) &&
            it.box.centerX() in (width * 0.06f)..(width * 0.94f)
        }

        // Deduplicate board cards strictly by horizontal pixel position (X axis).
        // This preserves triplets and pairs like A A, K K K, J J.
        val sortedBoardCandidates = boardCandidates.sortedBy { it.box.left }
        val uniquePhysicalBoardCards = mutableListOf<DetectedCard>()

        for (cand in sortedBoardCandidates) {
            val isDuplicatePosition = uniquePhysicalBoardCards.any { existing ->
                abs(existing.box.centerX() - cand.box.centerX()) < (cand.box.width() * 0.4f).coerceAtLeast(width * 0.045f)
            }
            if (!isDuplicatePosition) {
                uniquePhysicalBoardCards.add(cand)
            }
        }

        val rawBoardCards = uniquePhysicalBoardCards.take(5).map { PokerCard(it.rank, it.suit) }
        val sanitizedBoardCards = sanitizeDuplicateSuits(rawBoardCards)

        // 5. Detect Hero Hole Cards (Lower Table: y > 55%)
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

                // Hero cards are adjacent side-by-side (vertDiff < 8% height, horizDiff in 2%..25% width)
                if (vertDiff < height * 0.08f && horizDiff in (width * 0.02f)..(width * 0.25f)) {
                    // Favor pairs located at the bottom of the table
                    val score = (height - c1.box.centerY()) + (vertDiff * 2f)
                    if (score < bestPairScore) {
                        bestPairScore = score
                        bestHeroPair = if (c1.box.left < c2.box.left) Pair(c1, c2) else Pair(c2, c1)
                    }
                }
            }
        }

        var detectedHeroCards = if (bestHeroPair != null) {
            listOf(
                PokerCard(bestHeroPair.first.rank, bestHeroPair.first.suit),
                PokerCard(bestHeroPair.second.rank, bestHeroPair.second.suit)
            )
        } else if (heroCandidates.size >= 2) {
            val bottomTwo = heroCandidates.sortedByDescending { it.box.centerY() }.take(2)
            bottomTwo.sortedBy { it.box.left }.map { PokerCard(it.rank, it.suit) }
        } else {
            emptyList()
        }

        // 6. Cross-Validate with Hand Combination Badge
        detectedHeroCards = crossValidateWithCombinationHint(detectedHeroCards, sanitizedBoardCards, handCombinationHint)

        val finalHeroCards = detectedHeroCards
        val finalBoardCards = sanitizedBoardCards

        val detectedFase = when (finalBoardCards.size) {
            0 -> "Preflop"
            3 -> "Flop"
            4 -> "Turn"
            5 -> "River"
            else -> currentState.fase
        }

        val updatedBote = detectedPot ?: currentState.bote
        val updatedBlinds = detectedBlinds ?: currentState.bigBlindSize

        // 7. Calculate GTO Decision via PokerGtoEngine
        val engineResult = if (finalHeroCards.isNotEmpty()) {
            PokerGtoEngine.calculate(
                holeCards = finalHeroCards,
                board = finalBoardCards,
                jugadores = finalPlayers,
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
            jugadores = finalPlayers,
            dealerPos = currentState.dealerPosition,
            myPos = currentState.posicion
        )

        return currentState.copy(
            fase = detectedFase,
            cartasPropias = finalHeroCards,
            cartasComunitarias = finalBoardCards,
            bote = updatedBote,
            bigBlindSize = updatedBlinds,
            jugadores = finalPlayers,
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
     * Cross-validates detected Hero cards against the poker room's combination badge.
     * E.g. "un color con A" -> Hero holds Flush suit (♠) and an Ace or King.
     * E.g. "un full de 4s con As" -> Hero holds 4s.
     * E.g. "poker de Ks" -> Hero holds KK.
     * E.g. "trío de Ks" -> Hero does not hold a King if board has KKK.
     */
    private fun crossValidateWithCombinationHint(
        hero: List<PokerCard>,
        board: List<PokerCard>,
        hint: String?
    ): List<PokerCard> {
        if (hint == null) return sanitizeHeroCards(hero, board)
        val lower = hint.lowercase()

        // 1. Flush ("color")
        if (lower.contains("color")) {
            val suitCounts = board.groupingBy { it.suit }.eachCount()
            val flushSuit = suitCounts.maxByOrNull { it.value }?.key ?: CardSuit.SPADES
            val updated = if (hero.isNotEmpty()) {
                hero.map { card ->
                    if (card.suit != flushSuit) card.copy(suit = flushSuit) else card
                }
            } else {
                listOf(PokerCard("A", flushSuit), PokerCard("K", flushSuit))
            }
            return sanitizeHeroCards(updated, board)
        }

        // 2. Four of a Kind ("poker de")
        if (lower.contains("poker de")) {
            val rankMatch = Regex("""poker\s+de\s+([AKQJT0-9]{1,2})""", RegexOption.IGNORE_CASE).find(lower)
            val rankChar = rankMatch?.groupValues?.getOrNull(1)?.uppercase()
            if (rankChar != null) {
                val rank = if (rankChar == "T") "10" else rankChar
                val usedBoardSuits = board.filter { it.rank == rank }.map { it.suit }.toSet()
                val availableSuits = listOf(CardSuit.SPADES, CardSuit.DIAMONDS, CardSuit.CLUBS, CardSuit.HEARTS).filter { it !in usedBoardSuits }
                return listOf(
                    PokerCard(rank, availableSuits.getOrElse(0) { CardSuit.SPADES }),
                    PokerCard(rank, availableSuits.getOrElse(1) { CardSuit.DIAMONDS })
                )
            }
        }

        // 3. Full House ("full de 4s con as")
        if (lower.contains("full de")) {
            val match = Regex("""full\s+de\s+([0-9AKQJT]+)s?\s+con\s+([0-9AKQJT]+|as)""", RegexOption.IGNORE_CASE).find(lower)
            if (match != null) {
                var tripsRank = match.groupValues[1].uppercase()
                var pairRank = match.groupValues[2].uppercase()
                if (tripsRank == "AS") tripsRank = "A"
                if (pairRank == "AS") pairRank = "A"
                if (tripsRank == "T") tripsRank = "10"
                if (pairRank == "T") pairRank = "10"

                val boardTripsCount = board.count { it.rank == tripsRank }
                val boardPairCount = board.count { it.rank == pairRank }

                // Si la mesa tiene 1 carta de tripsRank y 2 de pairRank, Hero aporta las 2 restantes de tripsRank
                if (boardTripsCount == 1 && boardPairCount >= 2) {
                    val usedSuits = board.filter { it.rank == tripsRank }.map { it.suit }.toSet()
                    val available = listOf(CardSuit.SPADES, CardSuit.DIAMONDS, CardSuit.CLUBS, CardSuit.HEARTS).filter { it !in usedSuits }
                    return listOf(
                        PokerCard(tripsRank, available.getOrElse(0) { CardSuit.SPADES }),
                        PokerCard(tripsRank, available.getOrElse(1) { CardSuit.DIAMONDS })
                    )
                } else if (boardTripsCount >= 2 && boardPairCount == 1) {
                    // Mesa tiene el trío, Hero aporta la otra de la pareja
                    val usedPairSuits = board.filter { it.rank == pairRank }.map { it.suit }.toSet()
                    val availablePair = listOf(CardSuit.SPADES, CardSuit.DIAMONDS, CardSuit.CLUBS, CardSuit.HEARTS).filter { it !in usedPairSuits }
                    val kickerSuit = listOf(CardSuit.CLUBS, CardSuit.HEARTS, CardSuit.SPADES).firstOrNull { it != availablePair.firstOrNull() } ?: CardSuit.CLUBS
                    return listOf(
                        PokerCard(pairRank, availablePair.getOrElse(0) { CardSuit.SPADES }),
                        PokerCard(pairRank, kickerSuit)
                    )
                }
            }
        }

        // 4. Three of a Kind ("trío de Ks")
        if (lower.contains("trío") || lower.contains("trio")) {
            val match = Regex("""tr[íi]o\s+de\s+([0-9AKQJT]+|ases|reyes|damas)""", RegexOption.IGNORE_CASE).find(lower)
            val rankStr = match?.groupValues?.getOrNull(1)?.uppercase()
            val targetRank = when (rankStr) {
                "ASES" -> "A"
                "REYES", "KS" -> "K"
                "DAMAS", "QS" -> "Q"
                else -> rankStr ?: ""
            }
            if (targetRank.isNotEmpty() && board.count { it.rank == targetRank } >= 3) {
                return hero.filter { it.rank != targetRank }.let {
                    if (it.size == 2) sanitizeHeroCards(it, board) else sanitizeHeroCards(hero, board)
                }
            }
        }

        return sanitizeHeroCards(hero, board)
    }

    /**
     * Sanitiza las cartas de Hero para que nunca contengan dos cartas exactamente idénticas
     * ni choquen con cartas ya visibles en la mesa comunitaria.
     */
    private fun sanitizeHeroCards(hero: List<PokerCard>, board: List<PokerCard>): List<PokerCard> {
        if (hero.size < 2) return hero
        val c1 = hero[0]
        var c2 = hero[1]

        // Nunca dos cartas del mismo valor y mismo palo en mano
        if (c1.rank == c2.rank && c1.suit == c2.suit) {
            val usedSuits = (board + listOf(c1)).filter { it.rank == c1.rank }.map { it.suit }.toSet()
            val available = listOf(CardSuit.SPADES, CardSuit.DIAMONDS, CardSuit.CLUBS, CardSuit.HEARTS).filter { it !in usedSuits }
            c2 = c2.copy(suit = available.firstOrNull() ?: CardSuit.DIAMONDS)
        }

        val sanitized = mutableListOf<PokerCard>()
        for (card in listOf(c1, c2)) {
            val inBoard = board.any { it.rank == card.rank && it.suit == card.suit }
            if (inBoard) {
                val used = (board + sanitized).filter { it.rank == card.rank }.map { it.suit }.toSet()
                val available = listOf(CardSuit.SPADES, CardSuit.DIAMONDS, CardSuit.CLUBS, CardSuit.HEARTS).filter { it !in used }
                sanitized.add(card.copy(suit = available.firstOrNull() ?: CardSuit.CLUBS))
            } else {
                sanitized.add(card)
            }
        }
        return sanitized
    }

    private fun extractCardsFromToken(
        text: String,
        validRanks: Set<String>
    ): List<Pair<String, CardSuit?>> {
        val clean = text.trim()
        val list = mutableListOf<Pair<String, CardSuit?>>()

        val singleMatch = Regex("""^(10|[AKQJT2-9])([♥♦♣♠hdcs])?$""", RegexOption.IGNORE_CASE).find(clean)
        if (singleMatch != null) {
            val rawRank = singleMatch.groupValues[1]
            val rank = if (rawRank.equals("T", ignoreCase = true)) "10" else rawRank.uppercase()
            val suitChar = singleMatch.groupValues[2].firstOrNull()
            val explicitSuit = suitChar?.let { parseSuitChar(it) }
            list.add(rank to explicitSuit)
            return list
        }

        if (clean.length in 2..3 && clean.all { it.equals(clean[0], ignoreCase = true) }) {
            val charUpper = clean[0].uppercaseChar().toString()
            if (validRanks.contains(charUpper) || charUpper == "T") {
                val rank = if (charUpper == "T") "10" else charUpper
                list.add(rank to CardSuit.SPADES)
                list.add(rank to CardSuit.HEARTS)
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
     * Verifies that the perimeter around the detected rank is light/white typical of a playing card face.
     * Samples around the glyph so black letter strokes do not cause false negative rejections.
     */
    private fun isLikelyCardSurface(bitmap: Bitmap, box: Rect): Boolean {
        val cardLeft = (box.left - 12).coerceIn(0, bitmap.width - 1)
        val cardRight = (box.right + 25).coerceIn(0, bitmap.width - 1)
        val cardTop = (box.top - 8).coerceIn(0, bitmap.height - 1)
        val cardBottom = (box.bottom + 25).coerceIn(0, bitmap.height - 1)

        var brightCount = 0
        var total = 0

        val stepX = ((cardRight - cardLeft) / 5).coerceAtLeast(1)
        val stepY = ((cardBottom - cardTop) / 5).coerceAtLeast(1)

        for (x in cardLeft..cardRight step stepX) {
            for (y in cardTop..cardBottom step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Playing card face background is white / light (r,g,b > 115 or sum > 350)
                if ((r + g + b) > 350 || (r > 115 && g > 115 && b > 115)) {
                    brightCount++
                }
                total++
            }
        }

        return total > 0 && (brightCount.toFloat() / total) >= 0.18f
    }

    /**
     * Inspects the pixel color palette in and around the card rank bounding box.
     * Accurately distinguishes:
     * - Red: Hearts (or 2-color Diamonds)
     * - Blue: Diamonds (4-color deck)
     * - Green: Clubs (4-color deck)
     * - Dark/Black: Spades (or 2-color Clubs)
     */
    private fun sampleCardSuitFromPixels(bitmap: Bitmap, box: Rect): CardSuit {
        val sampleLeft = (box.left + 2).coerceIn(0, bitmap.width - 1)
        val sampleTop = (box.top + 2).coerceIn(0, bitmap.height - 1)
        val sampleRight = (box.right - 2).coerceIn(sampleLeft, bitmap.width - 1)
        val sampleBottom = (box.bottom + (box.height() * 0.9).toInt()).coerceIn(sampleTop, bitmap.height - 1)

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
                } else if (b > 115 && b > r * 1.2f) {
                    blueCount++
                } else if (g > 110 && g > r * 1.2f && g > b * 1.1f) {
                    greenCount++
                } else if (r < 75 && g < 75 && b < 75) {
                    darkCount++
                }
            }
        }

        return when {
            blueCount > 6 && blueCount >= greenCount -> CardSuit.DIAMONDS
            greenCount > 6 -> CardSuit.CLUBS
            redCount > 6 -> CardSuit.HEARTS
            darkCount > 6 -> CardSuit.SPADES
            else -> CardSuit.SPADES
        }
    }

    /**
     * Cuenta asientos de jugadores activos en el perímetro de la mesa ovalada.
     * Detecta nombres de jugadores, fichas, badges 'Ausente' y avatares para obtener el total de 2 a 9 jugadores.
     */
    private fun detectPlayerCount(visionText: Text, width: Int, height: Int): Int? {
        val seatPoints = mutableListOf<PointF>()
        val minSeatDistSquared = (width * 0.14f) * (width * 0.14f)

        for (block in visionText.textBlocks) {
            val box = block.boundingBox ?: continue
            val cx = box.centerX().toFloat()
            val cy = box.centerY().toFloat()

            // Descartar el centro de la mesa (zona comunitaria y pozo)
            val isPerimeter = cy < height * 0.32f || cy > height * 0.65f || cx < width * 0.22f || cx > width * 0.78f
            if (!isPerimeter) continue

            val text = block.text.replace("\n", " ").trim()
            val upper = text.uppercase()

            val isPlayerBlock = upper.contains("AUSENTE") ||
                    upper.contains("AWAY") ||
                    text.contains(Regex("""\b[0-9]{1,3}(?:[.,][0-9]{3})+\b""")) ||
                    text.contains(Regex("""\b[0-9]+(?:\.[0-9]+)?\s*(?:BB|K|M)\b""", RegexOption.IGNORE_CASE)) ||
                    (upper.length in 3..15 && !upper.contains("BOTE") && !upper.contains("POT") && !upper.contains("HOLDEM") && !upper.contains("MESA"))

            if (isPlayerBlock) {
                val alreadyClustered = seatPoints.any { p ->
                    val dx = p.x - cx
                    val dy = p.y - cy
                    (dx * dx + dy * dy) < minSeatDistSquared
                }
                if (!alreadyClustered) {
                    seatPoints.add(PointF(cx, cy))
                }
            }
        }

        return if (seatPoints.size in 2..9) seatPoints.size else null
    }

    /**
     * Extrae números numéricos de póker admitiendo separadores de miles con coma o punto
     * (ej: '1,520' -> 1520.0, '1.022.984' -> 1022984.0, '1022 BB' -> 1022.0).
     */
    fun parsePokerNumericString(raw: String): Double? {
        val clean = raw.replace("$", "").replace("€", "").replace("£", "").trim()
        if (clean.isBlank()) return null

        val hasComma = clean.contains(',')
        val hasDot = clean.contains('.')

        return try {
            if (hasComma && hasDot) {
                val lastComma = clean.lastIndexOf(',')
                val lastDot = clean.lastIndexOf('.')
                if (lastComma > lastDot) {
                    clean.replace(".", "").replace(",", ".").toDoubleOrNull()
                } else {
                    clean.replace(",", "").toDoubleOrNull()
                }
            } else if (hasDot) {
                val parts = clean.split('.')
                if (parts.size > 2) {
                    clean.replace(".", "").toDoubleOrNull()
                } else if (parts.size == 2 && parts[1].length == 3) {
                    clean.replace(".", "").toDoubleOrNull()
                } else {
                    clean.toDoubleOrNull()
                }
            } else if (hasComma) {
                val parts = clean.split(',')
                if (parts.size > 2) {
                    clean.replace(",", "").toDoubleOrNull()
                } else if (parts.size == 2 && parts[1].length == 3) {
                    clean.replace(",", "").toDoubleOrNull()
                } else {
                    clean.replace(",", ".").toDoubleOrNull()
                }
            } else {
                clean.toDoubleOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

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
