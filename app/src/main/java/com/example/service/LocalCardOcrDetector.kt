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

/**
 * LocalCardOcrDetector: On-device Computer Vision & OCR Poker Table Scanner.
 * 
 * Works 100% offline with zero cloud latency and no API key required.
 * Acts as an instantaneous fallback and offline engine when Gemini is unreachable.
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
        val candidateCards = mutableListOf<DetectedCard>()
        var detectedPot: Double? = null
        var handCombinationHint: String? = null

        for (block in visionText.textBlocks) {
            val blockText = block.text.trim()

            // Check for hand combination hints from poker room (e.g. "Escalera real", "poker de 6s", "Trío de Ases")
            val lower = blockText.lowercase()
            if (lower.contains("escalera") || lower.contains("poker de") || lower.contains("trío") ||
                lower.contains("trio") || lower.contains("full") || lower.contains("color") ||
                lower.contains("doble pareja") || lower.contains("pareja de")
            ) {
                handCombinationHint = blockText
            }

            for (line in block.lines) {
                for (element in line.elements) {
                    val text = element.text.trim()
                    val box = element.boundingBox ?: continue

                    // 1. Check if element matches Card Pattern (e.g. "10d", "As", "Q♣", "6")
                    val rank = when {
                        validRanks.contains(text.uppercase()) -> text.uppercase()
                        text.length in 2..3 && validRanks.contains(text.substring(0, text.length - 1).uppercase()) -> {
                            text.substring(0, text.length - 1).uppercase()
                        }
                        text.equals("T", ignoreCase = true) -> "10"
                        else -> null
                    }

                    if (rank != null) {
                        // Check if suit symbol or char is in element
                        val explicitSuit = when {
                            text.contains("♥") || text.endsWith("h", ignoreCase = true) -> CardSuit.HEARTS
                            text.contains("♦") || text.endsWith("d", ignoreCase = true) -> CardSuit.DIAMONDS
                            text.contains("♣") || text.endsWith("c", ignoreCase = true) -> CardSuit.CLUBS
                            text.contains("♠") || text.endsWith("s", ignoreCase = true) -> CardSuit.SPADES
                            else -> null
                        }

                        val suit = explicitSuit ?: sampleCardSuitFromPixels(bitmap, box)
                        candidateCards.add(DetectedCard(rank, suit, box))
                    }

                    // 2. Check for Pot amount (e.g. "Bote: 150", "Pot 2,892", "115.02K")
                    if (box.top in (height * 0.20).toInt()..(height * 0.65).toInt()) {
                        val numStr = text.replace(",", "").replace("$", "").trim()
                        if (numStr.endsWith("K", ignoreCase = true)) {
                            val v = numStr.dropLast(1).toDoubleOrNull()
                            if (v != null && v > 0) detectedPot = v * 1000.0
                        } else {
                            val v = numStr.toDoubleOrNull()
                            if (v != null && v > 10.0 && detectedPot == null) detectedPot = v
                        }
                    }
                }
            }
        }

        // Separate cards into Hero (lower screen, y > 58%) and Board (center screen, y in 22%..58%)
        val heroCandidates = candidateCards.filter { it.box.centerY() > height * 0.58f }
        val boardCandidates = candidateCards.filter { it.box.centerY() in (height * 0.22f)..(height * 0.58f) }

        // Deduplicate and select closest cards
        val heroCards = heroCandidates
            .distinctBy { it.rank + it.suit.symbol }
            .sortedBy { it.box.left }
            .take(2)
            .map { PokerCard(it.rank, it.suit) }

        val boardCards = boardCandidates
            .distinctBy { it.rank + it.suit.symbol }
            .sortedBy { it.box.left }
            .take(5)
            .map { PokerCard(it.rank, it.suit) }

        val finalHeroCards = if (heroCards.isNotEmpty()) heroCards else currentState.cartasPropias
        val finalBoardCards = if (boardCards.isNotEmpty()) boardCards else currentState.cartasComunitarias

        val detectedFase = when (finalBoardCards.size) {
            0 -> "Preflop"
            3 -> "Flop"
            4 -> "Turn"
            5 -> "River"
            else -> currentState.fase
        }

        val updatedBote = detectedPot ?: currentState.bote

        // Calculate GTO decision
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

        val status = if (finalHeroCards.isNotEmpty()) {
            val combo = handCombinationHint?.let { " ($it)" } ?: ""
            "⚡ OCR Local: ${finalHeroCards.joinToString(" "){it.displayString}}$combo · ${latencyMs}ms"
        } else {
            "⚡ OCR Local: No se detectaron cartas claras · ${latencyMs}ms"
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
     * Inspects the pixel color palette in and around the card rank bounding box.
     * Accurately distinguishes Red (Hearts/Diamonds) from Black/Green/Blue (Spades/Clubs).
     */
    private fun sampleCardSuitFromPixels(bitmap: Bitmap, box: Rect): CardSuit {
        val sampleLeft = (box.left - 5).coerceIn(0, bitmap.width - 1)
        val sampleTop = (box.top - 5).coerceIn(0, bitmap.height - 1)
        val sampleRight = (box.right + 25).coerceIn(0, bitmap.width - 1)
        val sampleBottom = (box.bottom + 25).coerceIn(0, bitmap.height - 1)

        var redCount = 0
        var blueCount = 0
        var greenCount = 0
        var darkCount = 0
        var total = 0

        for (x in sampleLeft..sampleRight step 2) {
            for (y in sampleTop..sampleBottom step 2) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Skip white card backgrounds
                if (r > 215 && g > 215 && b > 215) continue

                // 4-Color & 2-Color Deck chromatic analysis
                if (r > 140 && g < 100 && b < 100) redCount++
                else if (b > 140 && r < 100) blueCount++
                else if (g > 140 && r < 100) greenCount++
                else if (r < 80 && g < 80 && b < 80) darkCount++
                total++
            }
        }

        return when {
            redCount > 10 && redCount >= blueCount && redCount >= greenCount -> CardSuit.HEARTS
            blueCount > 10 && blueCount >= greenCount -> CardSuit.DIAMONDS
            greenCount > 10 -> CardSuit.CLUBS
            darkCount > 10 -> CardSuit.SPADES
            else -> CardSuit.HEARTS
        }
    }

    private data class DetectedCard(
        val rank: String,
        val suit: CardSuit,
        val box: Rect
    )
}
