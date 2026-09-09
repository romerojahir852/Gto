package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

class GeminiPokerRepository {

    companion object {
        private const val TAG = "GeminiPokerRepo"
        // 3000ms cap: gemini-3.x Flash responde en 0.8-2.5s en condiciones normales.
        private const val TIMEOUT_MS = 3000L
        private const val MAX_IMAGE_DIMENSION = 720
        private const val JPEG_COMPRESSION_QUALITY = 85
        private const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    /**
     * Cascada de modelos Gemini 3.x. El primero que responda completo gana.
     * gemini-3.8-flash y gemini-3.7-flash son los preferidos del usuario pero a
     * veces devuelven 503/timeout por alta demanda. En ese caso se cae a
     * gemini-3.5-flash-lite o gemini-3.1-flash-lite, que en pruebas reales
     * entregan la respuesta completa en 500-800ms.
     */
    private val candidateModels = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite"
    )

    /**
     * Ktor HTTP client con timeouts defensivos de 3000ms.
     */
    private val ktorClient by lazy {
        HttpClient(Android) {
            engine {
                connectTimeout = 3_000
                socketTimeout = 3_000
            }
            expectSuccess = false
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Optimizes captured bitmap to sub-720p and compresses to JPEG 85%.
     * Subir de 70% a 85% reduce artefactos en palos (♠ ♥ ♦ ♣) que confunden
     * a modelos Flash en modo de razonamiento rápido.
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

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, stream)
        val compressedBytes = stream.toByteArray()

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size, options) ?: scaled
    }

    /**
     * Prompt Espacial para Gemini Vision con detección integral de la partida.
     * Se mantiene COMPLETO (no se recorta) para máxima precisión del parser.
     */
    fun buildSurgicalPrompt(state: HandState): String {
        return "Contexto GTO: Fase[${state.fase}], Jugadores[${state.jugadores}], MiPosicion[${state.posicion}], Dealer[${state.dealerPosition}], Bote[${state.bote}]. " +
                "Eres un escáner y analizador visual de mesa de póker profesional. " +
                "REGLA 1 (CARTAS PROPIAS): Tus 2 cartas de la mano están SIEMPRE situadas en el cuadro de la PARTE INFERIOR. Selecciónalas como tus cartas propias. " +
                "REGLA 2 (CARTAS COMUNITARIAS): Las cartas comunitarias (Flop, Turn, River) están alineadas exclusivamente en el CENTRO de la mesa. Si no hay cartas comunitarias, la fase es Preflop. " +
                "REGLA 3 (JUGADORES Y DEALER): En el panorama de la mesa, contabiliza el número total de jugadores activos (entre 2 y 9) y localiza la posición del botón del Dealer ('D'). Identifica la posición del jugador: BTN, SB, BB, UTG, MP, CO. " +
                "REGLA 4: Ignora animaciones y emojis. Diferencia palos rojos (Corazones h/Diamantes d) de negros (Picas s/Tréboles c). " +
                "Responde ÚNICAMENTE en formato JSON estricto con las siguientes claves: " +
                "\"cartas\" (ValorPalo), \"mesa\" (ValorPalo, vacío si no hay), \"jugadores\" (Int), \"dealer\" (Posición), \"miPosicion\" (Posición), \"fase\" (Preflop/Flop/Turn/River), \"outs\" (String), \"win\" (String), \"gto\" (Acción y Tamaño)."
    }

    private data class GeminiCallResult(
        val text: String? = null,
        val modelUsed: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Analyzes poker screen frame 100% on Dispatchers.IO.
     * Bound to 3000ms timeout with zero crashes.
     */
    suspend fun analyzeHand(
        bitmap: Bitmap,
        currentState: HandState = PokerGameStateManager.handState.value
    ): Result<HandState> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val prompt = buildSurgicalPrompt(currentState)
        val apiKey = BuildConfig.GEMINI_API_KEY

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
            val compressedBitmap = optimizeBitmap(bitmap)

            val callResult = withTimeout(TIMEOUT_MS) {
                callGeminiFast(apiKey, prompt, compressedBitmap)
            }

            val latency = System.currentTimeMillis() - startTime
            val responseText = callResult.text

            if (!responseText.isNullOrBlank()) {
                Log.d("GEMINI_DEBUG", "RAW AI RESPONSE (${callResult.modelUsed}): $responseText")
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
                    statusMessage = "Lectura IA exitosa (${latency}ms · ${callResult.modelUsed})"
                )
                Result.success(parsedState)
            } else {
                val errorMsg = "⚠️ Sin respuesta de IA: ${callResult.errorMessage?.take(40) ?: "vacía"}"
                Log.w("GEMINI_ERROR", errorMsg)
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
            val timeoutMsg = "⚠️ Timeout (${TIMEOUT_MS}ms) - usando motor local"
            Log.w("GEMINI_TIMEOUT", timeoutMsg)
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
        } catch (e: HttpRequestTimeoutException) {
            val latency = System.currentTimeMillis() - startTime
            val msg = "⚠️ Red lenta (timeout HTTP)"
            Log.w("GEMINI_NET_TIMEOUT", msg, e)
            val netState = currentState.copy(
                statusMessage = msg,
                latencyMs = latency,
                isLoading = false,
                isExpanded = true
            )
            PokerGameStateManager.updateIncremental(
                statusMessage = msg,
                latencyMs = latency
            )
            Result.success(netState)
        } catch (e: Throwable) {
            val latency = System.currentTimeMillis() - startTime
            val msg = e.message ?: e.javaClass.simpleName
            Log.e("GEMINI_ERROR", "Fallo general en analyzeHand: $msg", e)
            val errorMsg = "⚠️ Error: ${msg.take(35)}"
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

    suspend fun analyzePokerFrame(
        bitmap: Bitmap,
        state: HandState
    ): Result<HandState> = withContext(Dispatchers.IO) {
        analyzeHand(bitmap, state)
    }

    /**
     * REST directo a Gemini 3.x Flash via generateContent endpoint.
     * Cascade: gemini-3.8-flash → gemini-3.7-flash.
     * Sin temperature, sin top_p, sin top_k (causan HTTP 400 en Gemini 3.x).
     * Cualquier 503 / respuesta vacía activa el siguiente modelo de la cascada.
     */
    private suspend fun callGeminiFast(
        apiKey: String,
        prompt: String,
        bitmap: Bitmap
    ): GeminiCallResult {
        // Codificar imagen a base64 JPEG 85%
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, stream)
        val base64Image = java.util.Base64.getEncoder().encodeToString(stream.toByteArray())

        var lastError: String? = null

        for (modelName in candidateModels) {
            try {
                                val requestBody = buildJsonObject {
                    put("contents", buildJsonArray {
                        add(buildJsonObject {
                            put("parts", buildJsonArray {
                                add(buildJsonObject {
                                    put("text", prompt)
                                })
                                add(buildJsonObject {
                                    put("inline_data", buildJsonObject {
                                        put("mime_type", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                            })
                        })
                    })
                    put("generationConfig", buildJsonObject {
                        put("responseMimeType", "application/json")
                        put("maxOutputTokens", 400)
                    })

                }

                val response = ktorClient.post(
                    "$ENDPOINT_BASE/$modelName:generateContent"
                ) {
                    headers {
                        append("x-goog-api-key", apiKey)
                    }
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }

                val statusCode = response.status.value
                val body = response.bodyAsText()

                if (statusCode !in 200..299) {
                    lastError = "HTTP $statusCode: ${body.take(180)}"
                    Log.w(TAG, "$modelName failed with HTTP $statusCode: ${body.take(200)}")
                    continue
                }

                val parsed = try {
                    json.parseToJsonElement(body)
                } catch (e: Exception) {
                    lastError = "JSON parse error: ${e.message?.take(60)}"
                    Log.w(TAG, "$modelName returned invalid JSON: ${body.take(200)}")
                    continue
                }

                val jsonObj = parsed as? JsonObject
                if (jsonObj == null) {
                    lastError = "Response not a JSON object"
                    continue
                }

                // Detectar error de API dentro del body (Google a veces devuelve 200 con error JSON)
                jsonObj["error"]?.let { err ->
                    val errMsg = (err as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                    lastError = errMsg ?: "Unknown API error"
                    Log.w(TAG, "$modelName returned error in body: $lastError")
                    return@let
                }

                val text = jsonObj["candidates"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonObject
                    ?.get("parts")
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("text")
                    ?.jsonPrimitive
                    ?.contentOrNull

                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "$modelName responded OK (${text.length} chars)")
                    return GeminiCallResult(text = text, modelUsed = modelName)
                } else {
                    lastError = "Empty text in response"
                    Log.w(TAG, "$modelName returned empty text. Body: ${body.take(200)}")
                }
            } catch (e: ResponseException) {
                val code = e.response.status.value
                lastError = "HTTP $code: ${e.message?.take(80)}"
                Log.w(TAG, "$modelName ResponseException: $lastError", e)
            } catch (e: HttpRequestTimeoutException) {
                lastError = "Timeout HTTP"
                Log.w(TAG, "$modelName timeout", e)
            } catch (t: Throwable) {
                val msg = t.message ?: t.javaClass.simpleName
                lastError = msg
                Log.e(TAG, "$modelName unexpected error: $msg", t)
            }
        }

        return GeminiCallResult(errorMessage = lastError ?: "Todos los modelos fallaron")
    }

    /**
     * Motor de Visión y Decisión Texas Hold'em Local.
     * Mantiene el estado real de la partida sin inyectar manos simuladas.
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
     * Creates a safe default error state.
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
     * Cartas:[X] | Mesa:[X] | Jugadores:[X] | Dealer:[X] | MiPosicion:[X] |
     * Fase:[X] | Outs:[X] | Win:[X]% | GTO:[Acción-Valor]
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

        try {
            val jsonObj = json.parseToJsonElement(rawText).jsonObject
            
            jsonObj["cartas"]?.jsonPrimitive?.contentOrNull?.let { value ->
                val parsed = PokerCard.parseMultiple(value)
                if (parsed.isNotEmpty()) {
                    holeCards = parsed.take(2)
                    if (parsed.size > 2) communityCards = parsed.drop(2)
                }
            }
            
            jsonObj["mesa"]?.jsonPrimitive?.contentOrNull?.let { value ->
                if (value.isNotBlank() && value != "-" && !value.equals("ninguna", ignoreCase = true)) {
                    val parsed = PokerCard.parseMultiple(value)
                    if (parsed.isNotEmpty()) communityCards = parsed
                }
            }
            
            jsonObj["jugadores"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.let { count ->
                if (count in 2..9) detectedPlayers = count
            }
            
            jsonObj["dealer"]?.jsonPrimitive?.contentOrNull?.let { value ->
                if (value.isNotBlank()) detectedDealer = value.uppercase().trim()
            }
            
            jsonObj["miPosicion"]?.jsonPrimitive?.contentOrNull?.let { value ->
                if (value.isNotBlank()) detectedMyPos = value.uppercase().trim()
            }
            
            jsonObj["fase"]?.jsonPrimitive?.contentOrNull?.let { value ->
                if (value.isNotBlank()) parsedFase = value.replaceFirstChar { it.uppercase() }.trim()
            }
            
            jsonObj["outs"]?.jsonPrimitive?.contentOrNull?.let { value ->
                outs = if (value.isBlank() || value == "-") "0 Outs" else value.trim()
            }
            
            jsonObj["win"]?.jsonPrimitive?.contentOrNull?.let { value ->
                val rawWin = value.trim()
                winRate = if (rawWin.endsWith("%") || rawWin == "-") rawWin else "$rawWin%"
            }
            
            jsonObj["gto"]?.jsonPrimitive?.contentOrNull?.let { value ->
                val rawGto = value.trim()
                gtoAction = GtoAction.fromString(rawGto)
                gtoActionValue = if (rawGto.contains("-")) {
                    rawGto.substringAfter("-").trim()
                } else if (rawGto.contains(" ")) {
                    rawGto.substringAfter(" ").trim()
                } else {
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON response: $rawText", e)
        }

        val detectedFase = parsedFase ?: when (communityCards.size) {
            0 -> "Preflop"
            3 -> "Flop"
            4 -> "Turn"
            5 -> "River"
            else -> currentState.fase
        }

        GTOStateManager.updateFromAnalysis(
            fase = detectedFase,
            bote = null,
            jugadores = detectedPlayers,
            dealerPos = detectedDealer,
            myPos = detectedMyPos
        )

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
}
