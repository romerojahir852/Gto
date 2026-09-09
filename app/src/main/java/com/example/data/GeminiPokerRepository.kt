package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

class GeminiPokerRepository {

    companion object {
        private const val TAG = "GeminiPokerRepo"
        // 12000ms: tiempo óptimo para upload en alta resolución 1280p y respuesta visual instantánea
        private const val TIMEOUT_MS = 12000L
        private const val MAX_IMAGE_DIMENSION = 1280
        private const val JPEG_COMPRESSION_QUALITY = 90
        private const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    /**
     * Cascada oficial de modelos Google Gemini multimodal vision.
     * Prioriza Gemini 2.5 Flash (la última generación de Google AI Studio con razonamiento espacial)
     * y enlaza fluidamente con 2.5 Flash-Lite, 2.0 Flash y 1.5 Flash.
     */
    private val candidateModels = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-flash"
    )

    /**
     * Ktor HTTP client con timeouts de 5000ms.
     */
    private val ktorClient by lazy {
        HttpClient(Android) {
            engine {
                connectTimeout = 5_000
                socketTimeout = 5_000
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
     * Prompt Quirúrgico de Alta Precisión para Mesas de Póker Móviles (GGPoker, BC Poker, PokerStars, etc.).
     */
    fun buildSurgicalPrompt(state: HandState): String {
        return """
        You are a World-Class Texas Hold'em Computer Vision Analyzer with millimetric spatial precision.
        Context: Phase[${state.fase}], Players[${state.jugadores}], MyPos[${state.posicion}], Dealer[${state.dealerPosition}].

        Carefully scan the image and identify:
        1. HERO HOLE CARDS (Tus 2 cartas propias):
           - Located near the bottom of the table (often bottom-center or bottom-left next to the user's avatar, chips, and name, e.g. Jr699).
           - The Hero's 2 cards are face-up with clearly visible rank and suit (e.g. '10h 4h' or 'As Kd').
           - Note: Check if there is helper text underneath Hero's avatar (e.g. 'trío de Ks', 'par de ases', 'doble pareja', 'escalera') to confirm the cards!
        2. COMMUNITY CARDS (Mesa / Flop / Turn / River):
           - Located horizontally in the center of the table (Flop = 3 cards, Turn = 4 cards, River = 5 cards).
           - Example: three kings on board is 'Kh Kc Ks'.
           - If Preflop and no community cards are dealt yet, return "".
        3. POT:
           - Total pot amount in the center (e.g. '2596' or '1600').
        4. DEALER BUTTON:
           - Find the player seat with the yellow 'D' dealer button.
        5. ACTIVE SEATED PLAYERS:
           - Count total players active in the hand (2 to 9).
        6. GTO ACTION & EQUITY:
           - Best action (FOLD, CHECK, CALL, BET, RAISE, ALL-IN) and Win Equity %.

        Format: Output STRICT JSON ONLY with these exact keys:
        {
          "cartas": "10h 4h",
          "mesa": "Kh Kc Ks",
          "fase": "Flop",
          "bote": "2596",
          "jugadores": 6,
          "dealer": "BTN",
          "miPosicion": "SB",
          "outs": "Trío de Reyes",
          "win": "72%",
          "gto": "CALL 1x"
        }
        Suits: h=hearts ♥, d=diamonds ♦, c=clubs ♣, s=spades ♠.
        """.trimIndent()
    }

    private data class GeminiCallResult(
        val text: String? = null,
        val modelUsed: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Analyzes poker screen frame 100% on Dispatchers.IO.
     * Combines Gemini Multimodal Cloud Vision with automatic On-Device Local OCR fallback.
     */
    suspend fun analyzeHand(
        bitmap: Bitmap,
        currentState: HandState = PokerGameStateManager.handState.value,
        context: android.content.Context? = null
    ): Result<HandState> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = ApiKeyManager.getApiKey(context)
        val compressedBitmap = optimizeBitmap(bitmap)

        if (!apiKey.isNullOrBlank()) {
            try {
                val prompt = buildSurgicalPrompt(currentState)
                val callResult = withTimeout(TIMEOUT_MS) {
                    callGeminiFast(apiKey, prompt, compressedBitmap)
                }

                val latency = System.currentTimeMillis() - startTime
                val responseText = callResult.text

                if (!responseText.isNullOrBlank()) {
                    Log.d("GEMINI_DEBUG", "RAW AI RESPONSE (${callResult.modelUsed}): $responseText")
                    val parsedState = parseSurgicalResponse(responseText, currentState, latency)
                    if (parsedState.cartasPropias.isNotEmpty() || parsedState.cartasComunitarias.isNotEmpty()) {
                        PokerGameStateManager.updateIncremental(
                            fase = parsedState.fase,
                            cartasPropias = if (parsedState.cartasPropias.isNotEmpty()) parsedState.cartasPropias else null,
                            cartasComunitarias = if (parsedState.cartasComunitarias.isNotEmpty()) parsedState.cartasComunitarias else null,
                            bote = parsedState.bote,
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
                            statusMessage = "IA ${callResult.modelUsed ?: "Gemini"}: ${parsedState.cartasPropias.joinToString(" "){it.displayString}} | Mesa: ${parsedState.cartasComunitarias.joinToString(" "){it.displayString}} · ${latency}ms"
                        )
                        return@withContext Result.success(parsedState)
                    }
                }
            } catch (t: Throwable) {
                Log.w("GEMINI_FALLBACK", "Gemini no completó (${t.message}), activando OCR local en dispositivo", t)
            }
        }

        // MOTOR ON-DEVICE: Detección visual OCR local directa sobre el frame
        Log.d("OCR_LOCAL", "Ejecutando escaneo visual OCR local en dispositivo")
        val localState = com.example.service.LocalCardOcrDetector.detect(compressedBitmap, currentState)
        val finalStatus = if (apiKey.isNullOrBlank()) {
            localState.statusMessage + " • 🔑 Toca para ingresar API Key"
        } else {
            localState.statusMessage
        }
        val finalResult = localState.copy(statusMessage = finalStatus)

        PokerGameStateManager.updateIncremental(
            fase = finalResult.fase,
            cartasPropias = finalResult.cartasPropias,
            cartasComunitarias = finalResult.cartasComunitarias,
            bote = finalResult.bote,
            jugadores = finalResult.jugadores,
            posicion = finalResult.posicion,
            dealerPosition = finalResult.dealerPosition,
            dealerDetected = finalResult.dealerDetected,
            tablePositionsSummary = finalResult.tablePositionsSummary,
            outs = finalResult.outs,
            winRate = finalResult.winRate,
            gtoAction = finalResult.gtoAction,
            gtoActionValue = finalResult.gtoActionValue,
            latencyMs = finalResult.latencyMs,
            isSimulation = false,
            statusMessage = finalStatus
        )
        return@withContext Result.success(finalResult)
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
     * REST directo a Gemini Flash via generateContent endpoint.
     * Cascada multi-modelo resiliente con soporte de API key en URL y headers.
     */
    private suspend fun callGeminiFast(
        apiKey: String,
        prompt: String,
        bitmap: Bitmap
    ): GeminiCallResult {
        // Codificar imagen a base64 JPEG 85% usando Android native Base64 NO_WRAP
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, stream)
        val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

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
                                    put("inlineData", buildJsonObject {
                                        put("mimeType", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                            })
                        })
                    })
                    put("generationConfig", buildJsonObject {
                        put("responseMimeType", "application/json")
                        put("maxOutputTokens", 500)
                    })
                }

                val response = ktorClient.post(
                    "$ENDPOINT_BASE/$modelName:generateContent?key=$apiKey"
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
            val cleanText = rawText
                .replace(Regex("^```(?:json)?", RegexOption.MULTILINE), "")
                .replace(Regex("```$", RegexOption.MULTILINE), "")
                .trim()
            val startIdx = cleanText.indexOf('{')
            val endIdx = cleanText.lastIndexOf('}')
            val jsonPayload = if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                cleanText.substring(startIdx, endIdx + 1)
            } else {
                cleanText
            }

            val jsonObj = json.parseToJsonElement(jsonPayload).jsonObject
            
            val cartasRaw = when (val elem = jsonObj["cartas"]) {
                is JsonPrimitive -> elem.contentOrNull ?: ""
                is JsonArray -> elem.joinToString(" ") { (it as? JsonPrimitive)?.contentOrNull ?: "" }
                else -> ""
            }
            if (cartasRaw.isNotBlank()) {
                val parsed = PokerCard.parseMultiple(cartasRaw)
                if (parsed.isNotEmpty()) {
                    holeCards = parsed.take(2)
                    if (parsed.size > 2) communityCards = parsed.drop(2)
                }
            }

            val mesaRaw = when (val elem = jsonObj["mesa"]) {
                is JsonPrimitive -> elem.contentOrNull ?: ""
                is JsonArray -> elem.joinToString(" ") { (it as? JsonPrimitive)?.contentOrNull ?: "" }
                else -> ""
            }
            if (mesaRaw.isNotBlank() && mesaRaw != "-" && !mesaRaw.equals("ninguna", ignoreCase = true)) {
                val parsed = PokerCard.parseMultiple(mesaRaw)
                if (parsed.isNotEmpty()) communityCards = parsed
            }
            
            jsonObj["jugadores"]?.jsonPrimitive?.let { prim ->
                val count = prim.intOrNull ?: prim.contentOrNull?.toIntOrNull()
                if (count != null && count in 2..9) detectedPlayers = count
            }
            
            jsonObj["dealer"]?.jsonPrimitive?.contentOrNull?.let { value ->
                if (value.isNotBlank()) detectedDealer = value.uppercase().trim()
            }
            
            jsonObj["miPosicion"]?.jsonPrimitive?.contentOrNull?.let { value ->
                if (value.isNotBlank()) detectedMyPos = value.uppercase().trim()
            }
            
            jsonObj["bote"]?.jsonPrimitive?.contentOrNull?.let { value ->
                val cleanBote = value.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
                if (cleanBote != null && cleanBote > 0.0) {
                    GTOStateManager.setPotSize(cleanBote)
                }
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
