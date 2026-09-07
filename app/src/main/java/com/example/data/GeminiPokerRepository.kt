package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream

class GeminiPokerRepository {

    companion object {
        private const val TAG = "GeminiPokerRepo"
        // Strict 4000ms timeout for Gemini Vision calls
        private const val TIMEOUT_MS = 4000L
        private const val MAX_IMAGE_DIMENSION = 720
        private const val JPEG_COMPRESSION_QUALITY = 70
    }

    /**
     * Ktor Client configurado con motor Android y timeouts defensivos de 4000ms
     */
    private val ktorClient by lazy {
        HttpClient(Android) {
            engine {
                connectTimeout = 4_000
                socketTimeout = 4_000
            }
        }
    }

    /**
     * Optimizes captured bitmap to sub-720p and compresses to JPEG 70%
     * Executed strictly in Dispatchers.IO to never block the main thread.
     */
    fun optimizeBitmap(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        val maxDim = maxOf(width, height)

        val scaled = if (maxDim > MAX_IMAGE_DIMENSION) {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / maxDim.toFloat()
            val targetW = (width * scale).toInt().coerceAtLeast(1)
            val targetH = (height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(original, targetW, targetH, true)
        } else {
            original
        }

        // Compress to JPEG 70% to eliminate high bandwidth latency
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, stream)
        val compressedBytes = stream.toByteArray()

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size, options) ?: scaled
    }

    /**
     * Prompt Espacial para Gemini Vision con detección integral de la partida:
     * - Las 2 cartas propias están SIEMPRE en el cuadro inferior del recorte.
     * - Las cartas comunitarias están alineadas en el centro (Flop/Turn/River).
     * - Contabilizar automáticamente el número de jugadores activos en la mesa (2-9).
     * - Detectar la ficha/botón del Dealer ('D' / 'BTN') y la posición del jugador.
     * - Detectar fase de la partida: Preflop, Flop, Turn o River.
     */
    fun buildSurgicalPrompt(state: HandState): String {
        return "Contexto GTO: Fase[${state.fase}], Jugadores[${state.jugadores}], MiPosicion[${state.posicion}], Dealer[${state.dealerPosition}], Bote[${state.bote}]. " +
                "Eres un escáner y analizador visual de mesa de póker profesional. " +
                "REGLA 1 (CARTAS PROPIAS): Tus 2 cartas de la mano están SIEMPRE situadas en el cuadro de la PARTE INFERIOR. Selecciónalas como tus cartas propias. " +
                "REGLA 2 (CARTAS COMUNITARIAS): Las cartas comunitarias (Flop, Turn, River) están alineadas exclusivamente en el CENTRO de la mesa. Si no hay cartas comunitarias, la fase es Preflop. " +
                "REGLA 3 (JUGADORES Y DEALER): En el panorama de la mesa, contabiliza el número total de jugadores activos (entre 2 y 9) y localiza la posición del botón del Dealer ('D'). Identifica la posición del jugador: BTN, SB, BB, UTG, MP, CO. " +
                "REGLA 4: Ignora animaciones y emojis. Diferencia palos rojos (Corazones h/Diamantes d) de negros (Picas s/Tréboles c). " +
                "Responde ÚNICAMENTE con este formato Regex-ready: Cartas:[ValorPalo] | Mesa:[ValorPalo] | Jugadores:[2-9] | Dealer:[Posición] | MiPosicion:[Posición] | Fase:[Preflop/Flop/Turn/River] | Outs:[Numero] | Win:[X]% | GTO:[Acción y Tamaño]. Cero explicaciones."
    }

    private data class GeminiCallResult(
        val text: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Analyzes poker screen frame 100% on Dispatchers.IO.
     * Evaluates clean cropped bitmap with Gemini (gemini-1.5-flash prioritized)
     * strictly bound to 4000ms timeout with zero crashes.
     */
    suspend fun analyzeHand(
        bitmap: Bitmap,
        currentState: HandState = PokerGameStateManager.handState.value
    ): Result<HandState> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val prompt = buildSurgicalPrompt(currentState)
        val apiKey = BuildConfig.GEMINI_API_KEY

        // Si la clave no está configurada o sigue con el valor por defecto
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val missingKeyMsg = "⚠️ Falta GEMINI_API_KEY en .env / Secrets"
            Log.w("GEMINI_INFO", missingKeyMsg)
            val updatedState = currentState.copy(
                statusMessage = missingKeyMsg,
                latencyMs = 0L,
                isLoading = false,
                isExpanded = true
            )
            PokerGameStateManager.updateIncremental(
                statusMessage = missingKeyMsg,
                latencyMs = 0L
            )
            return@withContext Result.success(updatedState)
        }

        try {
            // Compress and scale image strictly on IO
            val compressedBitmap = optimizeBitmap(bitmap)

            val callResult = withTimeout(TIMEOUT_MS) {
                callGeminiFast(apiKey, prompt, compressedBitmap)
            }

            val latency = System.currentTimeMillis() - startTime
            val responseText = callResult.text

            if (!responseText.isNullOrBlank()) {
                Log.d("GEMINI_DEBUG", "RAW AI RESPONSE: $responseText")
                val parsedState = parseSurgicalResponse(responseText, currentState, latency)
                PokerGameStateManager.updateIncremental(
                    fase = parsedState.fase,
                    cartasPropias = parsedState.cartasPropias,
                    cartasComunitarias = parsedState.cartasComunitarias,
                    jugadores = parsedState.jugadores,
                    posicion = parsedState.posicion,
                    dealerPosition = parsedState.dealerPosition,
                    dealerDetected = parsedState.dealerDetected,
                    tablePositionsSummary = parsedState.tablePositionsSummary,
                    outs = parsedState.outs,
                    winRate = parsedState.winRate,
                    gtoAction = parsedState.gtoAction,
                    gtoActionValue = parsedState.gtoActionValue,
                    rawText = responseText,
                    latencyMs = latency,
                    isSimulation = false,
                    statusMessage = "Lectura IA exitosa (${latency}ms)"
                )
                Result.success(parsedState)
            } else {
                val errorMsg = when {
                    callResult.errorMessage?.contains("quota", ignoreCase = true) == true ||
                    callResult.errorMessage?.contains("resource_exhausted", ignoreCase = true) == true -> "⚠️ Cuota Gemini agotada (Límite de peticiones)"
                    callResult.errorMessage?.contains("API_KEY_INVALID", ignoreCase = true) == true -> "⚠️ Clave GEMINI_API_KEY inválida"
                    else -> "⚠️ Error IA: ${callResult.errorMessage?.take(35) ?: "Sin lectura"}"
                }
                Log.w("GEMINI_ERROR", "Sin respuesta de IA: ${callResult.errorMessage}")
                val updatedState = currentState.copy(
                    statusMessage = errorMsg,
                    latencyMs = latency,
                    isLoading = false,
                    isExpanded = true
                )
                PokerGameStateManager.updateIncremental(
                    statusMessage = errorMsg,
                    latencyMs = latency
                )
                Result.success(updatedState)
            }
        } catch (e: TimeoutCancellationException) {
            val latency = System.currentTimeMillis() - startTime
            Log.w("GEMINI_TIMEOUT", "TimeoutCancellationException (4000ms): Red lenta o timeout de Gemini.", e)
            val timeoutMsg = "⚠️ Timeout (4s) de Gemini"
            val timeoutState = currentState.copy(
                statusMessage = timeoutMsg,
                latencyMs = latency,
                isLoading = false,
                isExpanded = true
            )
            PokerGameStateManager.updateIncremental(
                statusMessage = timeoutMsg,
                latencyMs = latency
            )
            Result.success(timeoutState)
        } catch (e: Throwable) {
            val latency = System.currentTimeMillis() - startTime
            val msg = e.message ?: e.javaClass.simpleName
            Log.e("GEMINI_ERROR", "Fallo general en analyzeHand: $msg", e)
            val errorMsg = when {
                msg.contains("quota", ignoreCase = true) || msg.contains("resource_exhausted", ignoreCase = true) -> "⚠️ Cuota Gemini agotada"
                else -> "⚠️ Error: ${msg.take(35)}"
            }
            val fallbackState = currentState.copy(
                statusMessage = errorMsg,
                latencyMs = latency,
                isLoading = false,
                isExpanded = true
            )
            PokerGameStateManager.updateIncremental(
                statusMessage = errorMsg,
                latencyMs = latency
            )
            Result.success(fallbackState)
        }
    }

    /**
     * Backward-compatible overload for legacy callers expecting PokerAnalysisResult
     */
    suspend fun analyzePokerFrame(
        bitmap: Bitmap,
        street: Street = Street.FAST_GTO
    ): Result<PokerAnalysisResult> = withContext(Dispatchers.IO) {
        val handResult = analyzeHand(bitmap, PokerGameStateManager.handState.value)
        handResult.map { it.toAnalysisResult() }
    }

    /**
     * Backward-compatible overload accepting HandState
     */
    suspend fun analyzePokerFrame(
        bitmap: Bitmap,
        state: HandState
    ): Result<HandState> = withContext(Dispatchers.IO) {
        analyzeHand(bitmap, state)
    }

    /**
     * Executes Gemini API call with minimal tokens (maxOutputTokens: 70) and temperature: 0.0f.
     * DEFENSIVE AUDIT: Wrapped completely in a try-catch catching ANY Throwable
     * (ClassNotFoundException, NoClassDefFoundError, SocketTimeoutException, LinkageError, etc.)
     * guaranteeing ZERO crashes and total UI resilience.
     */
    private suspend fun callGeminiFast(
        apiKey: String,
        prompt: String,
        bitmap: Bitmap
    ): GeminiCallResult {
        return try {
            val config = generationConfig {
                temperature = 0.0f // Zero temperature for deterministic, immediate GTO decision
                maxOutputTokens = 120 // Tokens for complete structured detection
            }

            val candidateModels = listOf(
                "gemini-1.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash-8b",
                "gemini-1.5-pro"
            )

            var lastErrorMsg: String? = null

            for (modelName in candidateModels) {
                try {
                    val model = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey,
                        generationConfig = config
                    )
                    val response = model.generateContent(
                        content {
                            image(bitmap)
                            text(prompt)
                        }
                    )
                    val text = response.text
                    if (!text.isNullOrBlank()) {
                        Log.d("GEMINI_DEBUG", "RAW AI RESPONSE ($modelName): $text")
                        return GeminiCallResult(text = text)
                    }
                } catch (t: Throwable) {
                    val msg = t.message ?: t.javaClass.simpleName
                    Log.w(TAG, "Model $modelName attempt failed ($msg)")
                    lastErrorMsg = msg
                }
            }
            GeminiCallResult(errorMessage = lastErrorMsg)
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "Defensive catch-all in callGeminiFast caught: $msg", t)
            GeminiCallResult(errorMessage = msg)
        }
    }

    /**
     * Motor de Visión y Decisión Texas Hold'em Local.
     * Mantiene el estado real de la partida sin inyectar manos simuladas o precargadas.
     */
    fun analyzeBitmapLocally(
        bitmap: Bitmap,
        currentState: HandState,
        latencyMs: Long
    ): HandState {
        return currentState.copy(
            latencyMs = latencyMs,
            isLoading = false,
            isExpanded = true,
            isSimulation = false,
            statusMessage = "Captura procesada (${latencyMs}ms)"
        )
    }

    /**
     * Creates a safe default error state: Win 0%, GTO Error/Reintentar,
     * allowing the HUD overlay to display an intuitive retry state without crashing.
     */
    fun createDefensiveErrorState(
        currentState: HandState,
        latencyMs: Long,
        errorMessage: String = "Fallo de conexión o SDK. Toca para reintentar."
    ): HandState {
        return currentState.copy(
            outs = "Reintentar",
            winRate = "0%",
            gtoAction = GtoAction.ERROR,
            gtoActionValue = "Reintentar",
            rawText = errorMessage,
            latencyMs = latencyMs,
            isLoading = false,
            isExpanded = true,
            isSimulation = false,
            statusMessage = errorMessage
        )
    }

    /**
     * Parses the strict response format:
     * Cartas:[X] | Outs:[X-Palo/Valor] | Win:[X]% | GTO:[Fold/Call/Raise-Valor]
     */
    fun parseSurgicalResponse(
        rawText: String,
        currentState: HandState,
        latencyMs: Long
    ): HandState {
        var holeCards = currentState.cartasPropias
        var communityCards = currentState.cartasComunitarias
        var outs = currentState.outs
        var winRate = currentState.winRate
        var gtoAction = currentState.gtoAction
        var gtoActionValue = currentState.gtoActionValue
        var detectedPlayers = currentState.jugadores
        var detectedDealer = currentState.dealerPosition
        var detectedMyPos = currentState.posicion
        var parsedFase: String? = null

        val parts = rawText.split("|", "\n").map { it.trim() }.filter { it.isNotEmpty() }

        for (part in parts) {
            val lower = part.lowercase()
            when {
                lower.startsWith("cartas:") || lower.startsWith("mano:") -> {
                    val value = part.substringAfter(":").trim().replace("[", "").replace("]", "")
                    val parsed = PokerCard.parseMultiple(value)
                    if (parsed.isNotEmpty()) {
                        holeCards = parsed.take(2)
                        if (parsed.size > 2) {
                            communityCards = parsed.drop(2)
                        }
                    }
                }
                lower.startsWith("mesa:") || lower.startsWith("comunitarias:") -> {
                    val value = part.substringAfter(":").trim().replace("[", "").replace("]", "")
                    if (value != "-" && !value.equals("ninguna", ignoreCase = true)) {
                        val parsed = PokerCard.parseMultiple(value)
                        if (parsed.isNotEmpty()) {
                            communityCards = parsed
                        }
                    }
                }
                lower.startsWith("jugadores:") || lower.startsWith("players:") -> {
                    val value = part.substringAfter(":").trim().replace("[", "").replace("]", "")
                    val count = value.filter { it.isDigit() }.toIntOrNull()
                    if (count != null && count in 2..9) {
                        detectedPlayers = count
                    }
                }
                lower.startsWith("dealer:") || lower.startsWith("btn:") || lower.startsWith("boton:") -> {
                    val value = part.substringAfter(":").trim().replace("[", "").replace("]", "").uppercase()
                    if (value.isNotBlank()) {
                        detectedDealer = value
                    }
                }
                lower.startsWith("miposicion:") || lower.startsWith("posicion:") || lower.startsWith("pos:") -> {
                    val value = part.substringAfter(":").trim().replace("[", "").replace("]", "").uppercase()
                    if (value.isNotBlank()) {
                        detectedMyPos = value
                    }
                }
                lower.startsWith("fase:") || lower.startsWith("street:") -> {
                    val value = part.substringAfter(":").trim().replace("[", "").replace("]", "")
                    if (value.isNotBlank()) {
                        parsedFase = value.replaceFirstChar { it.uppercase() }
                    }
                }
                lower.startsWith("outs:") -> {
                    val value = part.substringAfter(":").trim().replace("[", "").replace("]", "")
                    outs = if (value.isBlank() || value == "-") "0 Outs" else value
                }
                lower.startsWith("win:") || lower.startsWith("win%:") -> {
                    val rawWin = part.substringAfter(":").trim().replace("[", "").replace("]", "")
                    winRate = if (rawWin.endsWith("%") || rawWin == "-") rawWin else "$rawWin%"
                }
                lower.startsWith("gto:") -> {
                    val rawGto = part.substringAfter(":").trim().replace("[", "").replace("]", "")
                    gtoAction = GtoAction.fromString(rawGto)
                    // Extract value component if present, e.g. "Raise-3BB" -> "3BB"
                    gtoActionValue = if (rawGto.contains("-")) {
                        rawGto.substringAfter("-").trim()
                    } else if (rawGto.contains(" ")) {
                        rawGto.substringAfter(" ").trim()
                    } else {
                        ""
                    }
                }
            }
        }

        // Auto-detect phase from community card count if available
        val detectedFase = parsedFase ?: when (communityCards.size) {
            0 -> "Preflop"
            3 -> "Flop"
            4 -> "Turn"
            5 -> "River"
            else -> currentState.fase
        }

        // Si la IA detectó jugadores o posiciones, sincronizar con GTOStateManager
        GTOStateManager.updateFromAnalysis(
            fase = detectedFase,
            bote = null,
            jugadores = detectedPlayers,
            dealerPos = detectedDealer,
            myPos = detectedMyPos
        )

        // Si la IA devolvió cartas pero no una decisión GTO válida, invocar motor GTO determinista
        if (gtoAction == GtoAction.UNKNOWN && holeCards.isNotEmpty()) {
            val engineDecision = PokerGtoEngine.calculate(
                holeCards = holeCards,
                board = communityCards,
                jugadores = detectedPlayers,
                posicion = detectedMyPos,
                fase = detectedFase,
                bote = currentState.bote,
                apuestaRival = currentState.apuestaRival
            )
            gtoAction = engineDecision.action
            if (gtoActionValue.isBlank()) gtoActionValue = engineDecision.actionValue
            if (winRate == "—" || winRate.isBlank()) winRate = engineDecision.winRate
            if (outs == "—" || outs.isBlank()) outs = engineDecision.outs
        }

        val tablePositionsSummary = GTOStateManager.getPositionsSummary(detectedPlayers, detectedMyPos)

        return currentState.copy(
            fase = detectedFase,
            cartasPropias = holeCards,
            cartasComunitarias = communityCards,
            jugadores = detectedPlayers,
            posicion = detectedMyPos,
            dealerPosition = detectedDealer,
            dealerDetected = true,
            tablePositionsSummary = tablePositionsSummary,
            outs = outs,
            winRate = winRate,
            gtoAction = gtoAction,
            gtoActionValue = gtoActionValue,
            rawText = rawText,
            latencyMs = latencyMs,
            isLoading = false,
            isExpanded = true,
            isSimulation = false
        )
    }

    /**
     * Fast local heuristic fallback when offline or using placeholder key
     */
    private fun generateFastHeuristic(currentState: HandState, latency: Long): HandState {
        return currentState.copy(
            fase = if (currentState.cartasComunitarias.isEmpty()) "Preflop" else currentState.fase,
            cartasPropias = if (currentState.cartasPropias.isEmpty()) {
                listOf(PokerCard("A", CardSuit.SPADES), PokerCard("K", CardSuit.HEARTS))
            } else {
                currentState.cartasPropias
            },
            outs = if (currentState.outs == "—") "9-Corazones ♥" else currentState.outs,
            winRate = if (currentState.winRate == "—") "65%" else currentState.winRate,
            gtoAction = if (currentState.gtoAction == GtoAction.UNKNOWN) GtoAction.RAISE else currentState.gtoAction,
            gtoActionValue = "3.5x",
            latencyMs = latency,
            isLoading = false,
            isExpanded = true,
            isSimulation = true
        )
    }
}
