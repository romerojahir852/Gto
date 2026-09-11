package com.example.service

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.example.data.CardSuit
import com.example.data.GTOStateManager
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
 * Provides high-precision spatial clustering for community cards (board pairs/trips/turn/river)
 * and adjacent Hero hole cards across all mobile poker rooms (GGPoker, ClubGG, PokerStars, PPPoker).
 * Integrates:
 * 1. Dedicated Community Board Scanner (Flop 3, Turn 4, River 5, Preflop 0).
 * 2. Dual-Source Hero Card Scanner (Top Status Badge & Bottom Felt Table).
 * 3. Chromatic 4-Color Deck Analyzer (Blue=♦, Green=♣, Red=♥, Black=♠).
 * 4. Ground-truth hand combination badge cross-validation.
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
        val noiseTokens = setOf(
            "AUSENTE", "ALL-IN", "ALLIN", "FAMILYAH", "LUCKYDONK", "RENATOSABA",
            "GORDORAVEN", "OOWOW", "BRIGANTEWILF", "DAILY", "TURBO", "BLINDS",
            "BOTE", "TOTAL", "MESA", "AUTO", "CALL", "RAISE", "FOLD", "CHECK",
            "NIVEL", "MANO", "HAND", "TABLE", "HOLDEM", "TEXAS", "WIN", "PASAR", "APOSTAR",
            "GTO", "MIS", "CARTAS", "PROPIAS", "COMUNITARIAS", "EQUITY", "RATE", "OUTS", "PROYECTOS",
            "DECISIÓN", "DECISION", "ÓPTIMA", "OPTIMA", "RE-ANALIZAR", "REANALIZAR",
            "JUGADORES", "POS", "BTN", "SB", "BB", "UTG", "MP", "CO", "FICHAS", "OCR", "LOCAL", "GEMINI", "FLASH"
        )

        val candidateCards = mutableListOf<DetectedCard>()
        val topBadgeCards = mutableListOf<DetectedCard>()
        var detectedPot: Double? = null
        var detectedBlinds: Double? = null
        var handCombinationHint: String? = null

        // 1. Scan TextBlocks for Hand Hints, Blinds, and Pot Amounts
        for (block in visionText.textBlocks) {
            val blockText = block.text.replace("\n", " ").trim()
            val lower = blockText.lowercase()

            // Check for hand combination hints (e.g. "carta alta", "un color con A", "un full de 4s", "trío de Ks", "par de Ases")
            if (lower.contains("carta alta") || lower.contains("escalera") || lower.contains("poker de") ||
                lower.contains("trío") || lower.contains("trio") || lower.contains("full") || lower.contains("color") ||
                lower.contains("doble pareja") || lower.contains("pareja de") || lower.contains("par de")
            ) {
                handCombinationHint = blockText.take(35)
            }

            // Check for Blind level (e.g. "Blinds 200 | 400 (50)" or "Ciegas: 200/400" or "Hold'em, 100 / 200")
            val blindMatch = Regex("""(?:blinds?|ciegas?|hold'?em,?\s*)\s*:?\s*([0-9.,]+)\s*[/|]\s*([0-9.,]+)""", RegexOption.IGNORE_CASE).find(blockText)
            if (blindMatch != null) {
                val bbStr = blindMatch.groupValues[2].replace(",", "").trim()
                val bbVal = bbStr.toDoubleOrNull()
                if (bbVal != null && bbVal > 0) {
                    detectedBlinds = bbVal
                }
            }

            // Check for Pot amount (e.g. "Bote total 52 BB", "Bote total 2,596", "Bote total 12 BB")
            if (lower.contains("bote") || lower.contains("pot") || lower.contains("pozo")) {
                val potRegex = Regex("""(?:bote(?:\s+total)?|pot(?:\s+total)?|main\s+pot|pozo)\s*[:=]?\s*[$€£]?\s*([0-9.,]+)\s*(K|M|BB)?""", RegexOption.IGNORE_CASE)
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

            // 2. Scan Lines & Elements for Playing Cards without discarding whole lines
            for (line in block.lines) {
                for (element in line.elements) {
                    val text = element.text.trim()
                    val upper = text.uppercase()
                    val box = element.boundingBox ?: continue

                    // Skip explicit UI keywords
                    if (noiseTokens.contains(upper)) continue
                    // Skip elements too wide or too small
                    if (box.width() > width * 0.40f || box.height() > height * 0.22f || box.width() < 6 || box.height() < 8) {
                        continue
                    }

                    val extracted = extractCardsFromToken(text, validRanks)
                    if (extracted.isEmpty()) continue

                    val isTopArea = box.centerY() < height * 0.16f && box.centerX() < width * 0.45f

                    // If not in top indicator pill, verify white card surface to filter out random numbers
                    if (!isTopArea && !isCardSurface(bitmap, box)) {
                        continue
                    }

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
                        val detectedCard = DetectedCard(rank, suit, subBox)

                        if (isTopArea) {
                            topBadgeCards.add(detectedCard)
                        } else {
                            candidateCards.add(detectedCard)
                        }
                    }
                }
            }
        }

        // 3. Conteo automático de jugadores activos en el perímetro de la mesa
        val detectedPlayersCount = detectPlayerCount(visionText, width, height)
        val finalPlayers = detectedPlayersCount ?: currentState.jugadores

        // 4. Dedicated Community Cards (Board) Detector
        // Central area: y between 32% and 62% of table, x between 8% and 92%
        val boardCandidates = candidateCards.filter {
            it.box.centerY().toFloat() in (height * 0.32f)..(height * 0.62f) &&
            it.box.centerX().toFloat() in (width * 0.08f)..(width * 0.92f)
        }

        val boardCards = detectCommunityCardsBoard(boardCandidates, width, height)

        // 5. Dual-Source Hero Hole Cards Detector
        val heroCards = detectHeroCards(candidateCards, topBadgeCards, boardCards, width, height, handCombinationHint)

        val detectedFase = when (boardCards.size) {
            0 -> "Preflop"
            3 -> "Flop"
            4 -> "Turn"
            5 -> "River"
            else -> if (boardCards.size >= 3) "Flop" else "Preflop"
        }

        val updatedBote = detectedPot ?: currentState.bote
        val updatedBlinds = detectedBlinds ?: currentState.bigBlindSize

        // 6. Calculate GTO Decision via PokerGtoEngine
        val engineResult = if (heroCards.isNotEmpty()) {
            PokerGtoEngine.calculate(
                holeCards = heroCards,
                board = boardCards,
                jugadores = finalPlayers,
                posicion = currentState.posicion,
                fase = detectedFase,
                bote = updatedBote,
                apuestaRival = currentState.apuestaRival
            )
        } else null

        val hintDisplay = handCombinationHint?.let { " ($it)" } ?: ""
        val boardDisplay = if (boardCards.isNotEmpty()) " | Mesa: ${boardCards.joinToString(" ") { it.displayString }}" else ""
        val heroDisplay = if (heroCards.isNotEmpty()) heroCards.joinToString(" ") { it.displayString } else "—"

        val status = if (heroCards.isNotEmpty() || boardCards.isNotEmpty()) {
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
            cartasPropias = heroCards,
            cartasComunitarias = boardCards,
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
     * Detección geométrica estricta de cartas comunitarias en la mesa.
     * En Texas Hold'em las cartas de la mesa están alineadas horizontalmente
     * y sólo pueden ser 3 (Flop), 4 (Turn) o 5 (River). Si hay menos de 3, es Preflop (0 cartas).
     */
    private fun detectCommunityCardsBoard(
        boardCandidates: List<DetectedCard>,
        width: Int,
        height: Int
    ): List<PokerCard> {
        if (boardCandidates.isEmpty()) return emptyList()

        // Agrupar cartas que comparten aproximadamente la misma línea horizontal (y)
        val clusters = mutableListOf<MutableList<DetectedCard>>()
        val yThreshold = height * 0.05f

        for (cand in boardCandidates) {
            val matchingCluster = clusters.firstOrNull { cluster ->
                val avgY = cluster.map { it.box.centerY() }.average()
                abs(cand.box.centerY() - avgY) < yThreshold
            }
            if (matchingCluster != null) {
                matchingCluster.add(cand)
            } else {
                clusters.add(mutableListOf(cand))
            }
        }

        // Elegir el cluster más horizontalmente poblado
        val bestCluster = clusters.maxByOrNull { it.size } ?: return emptyList()

        // Ordenar de izquierda a derecha por posición X y deduplicar tokens que caigan sobre la misma carta
        val sorted = bestCluster.sortedBy { it.box.left }
        val uniqueCards = mutableListOf<DetectedCard>()

        for (cand in sorted) {
            val isDuplicate = uniqueCards.any { existing ->
                abs(existing.box.centerX() - cand.box.centerX()) < (width * 0.055f).coerceAtLeast(cand.box.width() * 0.6f)
            }
            if (!isDuplicate) {
                uniqueCards.add(cand)
            }
        }

        // Texas Hold'em: sólo se aceptan 3, 4 o 5 cartas de mesa.
        // Si hay 1 o 2 cartas aisladas (ej. un número de apuesta o ficha), NO es una mesa válida.
        return if (uniqueCards.size in 3..5) {
            val rawCards = uniqueCards.take(5).map { PokerCard(it.rank, it.suit) }
            sanitizeDuplicateSuits(rawCards)
        } else {
            emptyList()
        }
    }

    /**
     * Detección de cartas del jugador (Hero) combinando dos fuentes de alta confianza:
     * 1. Badge digital en la parte superior (PokerStars, GGPoker, ClubGG).
     * 2. Cartas en el tapete de juego frente al avatar (mitad inferior).
     */
    private fun detectHeroCards(
        allCandidates: List<DetectedCard>,
        topBadgeCards: List<DetectedCard>,
        board: List<PokerCard>,
        width: Int,
        height: Int,
        handCombinationHint: String?
    ): List<PokerCard> {
        // Prioridad 1: Si la sala tiene la píldora digital con las cartas arriba
        if (topBadgeCards.size >= 2) {
            val topTwo = topBadgeCards.sortedBy { it.box.left }.take(2)
            val topHero = listOf(
                PokerCard(topTwo[0].rank, topTwo[0].suit),
                PokerCard(topTwo[1].rank, topTwo[1].suit)
            )
            return crossValidateWithCombinationHint(topHero, board, handCombinationHint)
        }

        // Prioridad 2: Buscar cartas adyacentes en la mitad inferior (y > 58%)
        val bottomCandidates = allCandidates.filter {
            it.box.centerY().toFloat() > height * 0.58f
        }

        var bestHeroPair: Pair<DetectedCard, DetectedCard>? = null
        var bestPairScore = Float.MAX_VALUE

        for (i in 0 until bottomCandidates.size) {
            for (j in i + 1 until bottomCandidates.size) {
                val c1 = bottomCandidates[i]
                val c2 = bottomCandidates[j]
                val vertDiff = abs(c1.box.centerY() - c2.box.centerY())
                val horizDiff = abs(c1.box.centerX() - c2.box.centerX())

                // Dos cartas en mano están una al lado de la otra
                if (vertDiff.toFloat() < height * 0.12f && horizDiff.toFloat() in (width * 0.02f)..(width * 0.35f)) {
                    val score = (height - c1.box.centerY()) + (vertDiff * 2f)
                    if (score < bestPairScore) {
                        bestPairScore = score
                        bestHeroPair = if (c1.box.left < c2.box.left) Pair(c1, c2) else Pair(c2, c1)
                    }
                }
            }
        }

        val detectedHero = if (bestHeroPair != null) {
            listOf(
                PokerCard(bestHeroPair.first.rank, bestHeroPair.first.suit),
                PokerCard(bestHeroPair.second.rank, bestHeroPair.second.suit)
            )
        } else if (bottomCandidates.size >= 2) {
            val bottomTwo = bottomCandidates.sortedByDescending { it.box.centerY() }.take(2)
            bottomTwo.sortedBy { it.box.left }.map { PokerCard(it.rank, it.suit) }
        } else if (topBadgeCards.size == 1 && bottomCandidates.isNotEmpty()) {
            val c1 = topBadgeCards[0]
            val c2 = bottomCandidates.first()
            listOf(PokerCard(c1.rank, c1.suit), PokerCard(c2.rank, c2.suit))
        } else {
            emptyList()
        }

        return crossValidateWithCombinationHint(detectedHero, board, handCombinationHint)
    }

    /**
     * Cross-validates detected Hero cards against the poker room's combination badge.
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

        // 3. Three of a Kind ("trío de Ks")
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

    private fun sanitizeHeroCards(hero: List<PokerCard>, board: List<PokerCard>): List<PokerCard> {
        if (hero.size < 2) return hero
        val c1 = hero[0]
        var c2 = hero[1]

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
        val clean = text.replace("[", "").replace("]", "").replace("(", "").replace(")", "").trim()
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

        // Reconocer pares de cartas unidas (ej: "10 4", "104", "9 8", "AK", "QQ", "KJ")
        val pairMatch = Regex("""^(10|[AKQJT2-9])\s*(10|[AKQJT2-9])$""", RegexOption.IGNORE_CASE).find(clean)
        if (pairMatch != null) {
            val r1 = pairMatch.groupValues[1].let { if (it.equals("T", true)) "10" else it.uppercase() }
            val r2 = pairMatch.groupValues[2].let { if (it.equals("T", true)) "10" else it.uppercase() }
            list.add(r1 to null)
            list.add(r2 to null)
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
     * Verifica que el elemento esté sobre el cuerpo blanco/claro de un naipe de póker.
     * Muestrea hacia la derecha y hacia abajo desde la posición del glifo superior izquierdo.
     */
    private fun isCardSurface(bitmap: Bitmap, box: Rect): Boolean {
        val width = bitmap.width
        val height = bitmap.height

        val sampleW = (box.width() * 2.2f).toInt().coerceIn(12, 120)
        val sampleH = (box.height() * 2.2f).toInt().coerceIn(14, 140)
        val right = (box.left + sampleW).coerceAtMost(width - 1)
        val bottom = (box.top + sampleH).coerceAtMost(height - 1)
        val left = (box.left - 4).coerceAtLeast(0)
        val top = (box.top - 4).coerceAtLeast(0)

        var brightCount = 0
        var total = 0

        val stepX = ((right - left) / 7).coerceAtLeast(1)
        val stepY = ((bottom - top) / 7).coerceAtLeast(1)

        for (x in left..right step stepX) {
            for (y in top..bottom step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Fondo blanco de naipe
                if ((r > 165 && g > 165 && b > 165) || (r + g + b > 510)) {
                    brightCount++
                }
                total++
            }
        }

        return total > 0 && (brightCount.toFloat() / total) >= 0.15f
    }

    /**
     * Muestreo cromático de 4 colores y 2 colores:
     * - Diamantes (♦): Azul
     * - Tréboles (♣): Verde
     * - Corazones (♥): Rojo
     * - Picas (♠): Negro / Gris oscuro
     */
    private fun sampleCardSuitFromPixels(bitmap: Bitmap, box: Rect): CardSuit {
        val width = bitmap.width
        val height = bitmap.height

        val sampleLeft = box.left.coerceIn(0, width - 1)
        val sampleTop = box.top.coerceIn(0, height - 1)
        val sampleRight = (box.right + (box.width() * 0.5f).toInt()).coerceIn(sampleLeft, width - 1)
        val sampleBottom = (box.bottom + (box.height() * 1.2f).toInt()).coerceIn(sampleTop, height - 1)

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

                // Omitir fondo blanco puro de la carta
                if (r > 215 && g > 215 && b > 215) continue

                // 1. Azul (Diamantes en 4-color deck)
                if (b > 105 && b > r * 1.15f && b >= g * 0.95f) {
                    blueCount++
                }
                // 2. Verde (Tréboles en 4-color deck)
                else if (g > 95 && g > r * 1.15f && g > b * 1.05f) {
                    greenCount++
                }
                // 3. Rojo (Corazones en 4-color deck y corazones/diamantes en 2-color)
                else if (r > 120 && r > g * 1.25f && r > b * 1.25f) {
                    redCount++
                }
                // 4. Oscuro (Picas en 4-color deck y picas/tréboles en 2-color)
                else if (r < 80 && g < 80 && b < 80) {
                    darkCount++
                }
            }
        }

        return when {
            blueCount > 4 && blueCount >= greenCount -> CardSuit.DIAMONDS
            greenCount > 4 -> CardSuit.CLUBS
            redCount > 4 -> CardSuit.HEARTS
            darkCount > 4 -> CardSuit.SPADES
            else -> CardSuit.SPADES
        }
    }

    /**
     * Cuenta asientos de jugadores activos en el perímetro de la mesa ovalada.
     */
    private fun detectPlayerCount(visionText: Text, width: Int, height: Int): Int? {
        val seatPoints = mutableListOf<PointF>()
        val minSeatDistSquared = (width * 0.14f) * (width * 0.14f)

        for (block in visionText.textBlocks) {
            val box = block.boundingBox ?: continue
            val cx = box.centerX().toFloat()
            val cy = box.centerY().toFloat()

            // Descartar el centro de la mesa (zona comunitaria y pozo)
            val isPerimeter = cy < height * 0.30f || cy > height * 0.65f || cx < width * 0.20f || cx > width * 0.80f
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
     * Extrae números numéricos de póker admitiendo separadores de miles con coma o punto.
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
                if (lastDot > lastComma) {
                    clean.replace(",", "").toDoubleOrNull()
                } else {
                    clean.replace(".", "").replace(",", ".").toDoubleOrNull()
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
