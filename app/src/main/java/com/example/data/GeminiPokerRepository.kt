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
        // 14000ms: tiempo óptimo para respuesta en redes móviles con latencia
        private const val TIMEOUT_MS = 14000L
        private const val MAX_IMAGE_DIMENSION = 960
        private const val JPEG_COMPRESSION_QUALITY = 88
        private const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

        @Volatile
        private var lastSuccessfulModel: String? = null
    }

    /**
     * Endpoint exclusivo Gemini 3.8 Flash según especificación estricta del usuario.
     */
    private val candidateModels = listOf(
        "gemini-3.8-flash"
    )

    /**
     * Ktor HTTP client con timeouts optimizados para baja latencia en redes móviles.
     */
    private val ktorClient by lazy {
        HttpClient(Android) {
            engine {
                connectTimeout = 10_000
                socketTimeout = 14_000
            }
            expectSuccess = false
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Optimiza el bitmap a dimensiones sub-960p sin re-codificación redundante
     * para ahorrar memoria y CPU antes del procesamiento multirresolución.
     */
    fun optimizeBitmap(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        val maxDim = maxOf(width, height)

        return if (maxDim > MAX_IMAGE_DIMENSION) {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / maxDim.toFloat()
            val targetW = (width * scale).toInt().coerceAtLeast(1)
            val targetH = (height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(original, targetW, targetH, true)
        } else {
            original
        }
    }

    /**
     * Prompt Quirúrgico de Alta Precisión para Mesas de Póker Móviles (GGPoker, BC Poker, PokerStars, etc.).
     */
    fun buildSurgicalPrompt(state: HandState): String {
        return """
        You are a World-Class Texas Hold'em Vision Engine with millimetric spatial precision across PokerStars, GGPoker, PokerBros, and BCPoker.
        You are provided with up to 4 optical perspectives of the poker table:
        - Image 1: Macro Full Table view (All seats, chips, dealer button, center pot, player counts).
        - Image 2: Micro-Zoom Community Cards (Center board: Flop/Turn/River).
        - Image 3: Micro-Zoom Hero Seat & Hole Cards (Bottom area where Hero sits, especially GGPoker and PokerBros where cards are tilted/overlapping over avatar).
        - Image 4: Micro-Zoom Top Status Pill (Present in PokerStars & BCPoker in the top-left corner).
        Context: Phase[${state.fase}], Players[${state.jugadores}], MyPos[${state.posicion}], Dealer[${state.dealerPosition}].

        STRICT MULTI-ROOM POKER AXIOMS:
        1. Standard 52-Card Deck: Every card is unique in rank and suit. A card CANNOT appear in both Hero's hand and the Community cards!
        2. Hero Hole Cards (ALWAYS EXACTLY 2 CARDS):
           - In GGPoker and PokerBros: Check the bottom-center seat. Hero's 2 cards are face-up, tilted in 3D perspective or overlapping. Read ranks & suits.
           - In PokerStars and BCPoker: Check BOTH the top-left digital pill AND the bottom-center avatar.
           - Opponents' cards are face-down (card backs with geometric patterns, solid dark/red/blue uniform color, or branded poker room logos). NEVER read opponent card backs as cards! Card backs have NO rank or suit symbols.
           - FACE-UP cards show a white/light body with a visible rank (A,K,Q,J,10,9,8,7,6,5,4,3,2) and a colored suit symbol.
           - Under Hero's avatar, read any combination badge (e.g. 'trío de Ks', 'par de ases', 'doble pareja', 'two pair', 'flush') to verify!
        3. Community Cards (Center table):
           - Preflop = "" (none). Flop = 3 cards. Turn = 4 cards. River = 5 cards.
           - Retain all cards in left-to-right order (e.g. "Kh Kc Ks").
        4. Card Suits:
           - ♥ Hearts = h (Red)
           - ♦ Diamonds = d (Blue/Cyan in 4-color deck, Red in 2-color deck)
           - ♣ Clubs = c (Green in 4-color deck, Black in 2-color deck)
           - ♠ Spades = s (Black/Dark)
        5. Total Pot: Number in center (e.g. "2596" or "12.5 BB" or "$150").
        6. Hero Chip Stack: Number on or directly beneath Hero's avatar.
        7. Dealer Button: Yellow, white, or golden circle marked 'D' or 'BTN'. Determine its position and Hero's position (BTN, SB, BB, UTG, MP, CO).
        8. Active Players: Count number of active seated players around the table (2 to 9).
        9. GTO Decision: Strict optimal action (e.g. "FOLD", "CHECK", "CALL 1x", "BET 33%", "BET 2.5 BB", "RAISE 3x", "ALL-IN").

        CRITICAL DETECTION RULES:
        10. COUNT EVERY CARD: Count the EXACT number of face-up community cards on the table. If you see 4 cards, you MUST report ALL 4. If you see 5, report ALL 5. Missing even ONE card makes your analysis INVALID.
        11. LEFT-TO-RIGHT ORDER: Read community cards strictly LEFT-TO-RIGHT. Card 1 is leftmost, Card 5 is rightmost.
        12. SELF-CHECK before responding: (a) No card appears in BOTH hole cards AND community cards. (b) Community cards total is 0, 3, 4, or 5. (c) Hero has EXACTLY 2 face-up cards. If any check fails, re-examine.

        Format: Output STRICT JSON ONLY with these exact keys:
        {
          "cartas": "10h 4h",
          "numCartasHero": 2,
          "mesa": "Kh Kc Ks 7d",
          "numCartasMesa": 4,
          "fase": "Turn",
          "bote": "2596",
          "fichasHero": "10000",
          "jugadores": 6,
          "dealer": "BTN",
          "miPosicion": "SB",
          "outs": "Trio de Reyes",
          "win": "72%",
          "gto": "CALL 1x"
        }
        IMPORTANT: "numCartasHero" MUST equal the actual count of cards in "cartas". "numCartasMesa" MUST equal the actual count of cards in "mesa".
        Suits: h=hearts ♥, d=diamonds ♦, c=clubs ♣, s=spades ♠.
        """.trimIndent()
    }


    /**
     * Punto 1,3: Fusion inteligente de resultados Gemini + OCR.
     * Toma las cartas Hero del motor con mayor confianza y las comunitarias del que detecte mas.
     */
    private fun mergeGeminiAndOcr(gemini: HandState, ocr: HandState, latency: Long): HandState {
        // Hero: preferir el que tenga exactamente 2 cartas; si ambos tienen 2, preferir Gemini
        val bestHero = when {
            gemini.cartasPropias.size == 2 && ocr.cartasPropias.size == 2 -> gemini.cartasPropias
            gemini.cartasPropias.size == 2 -> gemini.cartasPropias
            ocr.cartasPropias.size == 2 -> ocr.cartasPropias
            gemini.cartasPropias.isNotEmpty() -> gemini.cartasPropias
            else -> ocr.cartasPropias
        }

        // Board: preferir el que tenga MAS cartas (3,4,5). Si iguales, preferir Gemini.
        val bestBoard = when {
            gemini.cartasComunitarias.size > ocr.cartasComunitarias.size -> gemini.cartasComunitarias
            ocr.cartasComunitarias.size > gemini.cartasComunitarias.size -> ocr.cartasComunitarias
            gemini.cartasComunitarias.isNotEmpty() -> gemini.cartasComunitarias
            else -> ocr.cartasComunitarias
        }

        // Validar unicidad: no puede haber cartas duplicadas entre Hero y Board
        val heroSet = bestHero.map { "${it.rank}_${it.suit}" }.toSet()
        val cleanBoard = bestBoard.filter { "${it.rank}_${it.suit}" !in heroSet }

        val fusedFase = when (cleanBoard.size) {
            0 -> "Preflop"
            in 1..3 -> "Flop"
            4 -> "Turn"
            5 -> "River"
            else -> "River"
        }

        val heroStr = if (bestHero.isNotEmpty()) bestHero.joinToString(" ") { it.displayString } else "?"
        val boardStr = if (cleanBoard.isNotEmpty()) " | Mesa: ${cleanBoard.joinToString(" ") { it.displayString }}" else ""
        val source = if (gemini.cartasComunitarias.size >= ocr.cartasComunitarias.size) "Gemini" else "Gemini+OCR"
        val statusMsg = "\u2705 $source: $heroStr$boardStr \u2022 ${latency}ms"

        Log.d("FUSION", "Gemini: Hero=${gemini.cartasPropias.map { it.displayString }} Board=${gemini.cartasComunitarias.map { it.displayString }}")
        Log.d("FUSION", "OCR:    Hero=${ocr.cartasPropias.map { it.displayString }} Board=${ocr.cartasComunitarias.map { it.displayString }}")
        Log.d("FUSION", "FUSED:  Hero=${bestHero.map { it.displayString }} Board=${cleanBoard.map { it.displayString }}")

        // Usar GTO del motor Gemini si tiene resultado, sino del OCR
        return gemini.copy(
            fase = fusedFase,
            cartasPropias = bestHero,
            cartasComunitarias = cleanBoard,
            bote = if (gemini.bote > 0) gemini.bote else ocr.bote,
            jugadores = if (gemini.jugadores in 2..9) gemini.jugadores else ocr.jugadores,
            posicion = if (gemini.posicion.isNotBlank()) gemini.posicion else ocr.posicion,
            dealerPosition = if (gemini.dealerDetected) gemini.dealerPosition else ocr.dealerPosition,
            dealerDetected = gemini.dealerDetected || ocr.dealerDetected,
            outs = if (gemini.outs.isNotBlank() && gemini.outs != "?") gemini.outs else ocr.outs,
            winRate = if (gemini.winRate.isNotBlank() && gemini.winRate != "?") gemini.winRate else ocr.winRate,
            gtoAction = if (gemini.gtoAction != GtoAction.UNKNOWN) gemini.gtoAction else ocr.gtoAction,
            gtoActionValue = if (gemini.gtoActionValue.isNotBlank()) gemini.gtoActionValue else ocr.gtoActionValue,
            latencyMs = latency,
            statusMessage = statusMsg
        )
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
                var geminiFailureReason: String? = null

        if (!apiKey.isNullOrBlank()) {
            try {
                val prompt = buildSurgicalPrompt(currentState)
                val callResult = withTimeout(TIMEOUT_MS) {
                    callGeminiFast(apiKey, prompt, bitmap)
                }

                val latency = System.currentTimeMillis() - startTime
                val responseText = callResult.text

                if (!responseText.isNullOrBlank()) {
                    Log.d("GEMINI_DEBUG", "RAW AI RESPONSE (${callResult.modelUsed}): $responseText")
                    val parsedState = parseSurgicalResponse(responseText, currentState, latency)
                    val hasCards = parsedState.cartasPropias.isNotEmpty() || parsedState.cartasComunitarias.isNotEmpty()
                    val hasTableInfo = (parsedState.jugadores in 2..9) || parsedState.bote > 0.0 || parsedState.dealerPosition.isNotBlank()

                    if (hasCards || hasTableInfo) {
                        val modelLabel = "Gemini 3.8 Flash"
                        val cardsPart = if (hasCards) {
                            val heroStr = if (parsedState.cartasPropias.isNotEmpty()) parsedState.cartasPropias.joinToString(" ") { it.displayString } else "—"
                            val boardStr = if (parsedState.cartasComunitarias.isNotEmpty()) " | Mesa: ${parsedState.cartasComunitarias.joinToString(" ") { it.displayString }}" else ""
                            "$heroStr$boardStr"
                        } else {
                            "Mesa: ${parsedState.jugadores}j · BTN: ${parsedState.dealerPosition}"
                        }
                        val statusMsg = "⚡ $modelLabel: $cardsPart · ${latency}ms"
                        val finalParsed = parsedState.copy(statusMessage = statusMsg)
                        PokerGameStateManager.updateIncremental(
                            fase = finalParsed.fase,
                            cartasPropias = finalParsed.cartasPropias,
                            cartasComunitarias = finalParsed.cartasComunitarias,
                            bote = finalParsed.bote,
                            jugadores = finalParsed.jugadores,
                            posicion = finalParsed.posicion,
                            dealerPosition = finalParsed.dealerPosition,
                            dealerDetected = finalParsed.dealerDetected,
                            tablePositionsSummary = finalParsed.tablePositionsSummary,
                            outs = finalParsed.outs,
                            winRate = finalParsed.winRate,
                            gtoAction = finalParsed.gtoAction,
                            gtoActionValue = finalParsed.gtoActionValue,
                            rawText = responseText,
                            latencyMs = latency,
                            isSimulation = false,
                            statusMessage = statusMsg
                        )
                        // ------ PUNTO 1,3: FUSION GEMINI+OCR ------
                        // Gemini OK. Ahora TAMBIEN ejecutar OCR local para fusionar mejores partes.
                        val ocrState = try {
                            com.example.service.LocalCardOcrDetector.detect(bitmap, currentState)
                        } catch (e: Exception) {
                            Log.w("FUSION", "OCR fusion failed: " + e.message)
                            null
                        }
                        val fusedState = if (ocrState != null) {
                            mergeGeminiAndOcr(finalParsed, ocrState, latency)
                        } else {
                            finalParsed
                        }
                        return@withContext Result.success(fusedState)
                    }
                } else if (!callResult.errorMessage.isNullOrBlank()) {
                    geminiFailureReason = callResult.errorMessage
                    Log.w("GEMINI_FALLBACK", "Gemini devolvió error: ${callResult.errorMessage}")
                }
            } catch (t: Throwable) {
                geminiFailureReason = t.message ?: "Timeout"
                Log.w("GEMINI_FALLBACK", "Gemini no completó (${t.message}), activando OCR local en dispositivo", t)
            }
        }

        // MOTOR ON-DEVICE: Detección visual OCR local directa sobre el frame
        Log.d("OCR_LOCAL", "Ejecutando escaneo visual OCR local en dispositivo")
        val localState = com.example.service.LocalCardOcrDetector.detect(bitmap, currentState)
        val finalStatus = if (apiKey.isNullOrBlank()) {
            localState.statusMessage + " • 🔑 Toca para ingresar API Key"
        } else if (!geminiFailureReason.isNullOrBlank()) {
            val shortErr = if (geminiFailureReason.contains("400") || geminiFailureReason.contains("API key not valid", ignoreCase = true)) {
                "API Key inválida"
            } else if (geminiFailureReason.contains("404") || geminiFailureReason.contains("not found", ignoreCase = true)) {
                "Gemini 3.8: 404 No disp."
            } else if (geminiFailureReason.contains("429") || geminiFailureReason.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                "Cuota agotada"
            } else if (geminiFailureReason.contains("Timeout", ignoreCase = true)) {
                "Timeout de red"
            } else {
                geminiFailureReason.take(20)
            }
            "${localState.statusMessage} • Nube: $shortErr"
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
        // Generar perspectivas multirresolución: Macro (Mesa), Zoom Mesa (Comunitarias), Zoom Hero (Cartas Propias)
        val visionParts = com.example.service.PokerImageProcessor.createMultiresolutionVisionParts(bitmap)
        try {
            val base64Images = visionParts.map { part ->
                val stream = ByteArrayOutputStream()
                part.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }

            var lastError: String? = null

            // Cascada inteligente: si ya tenemos un modelo confirmado para esta API Key, se llama de inmediato
            val orderedModels = mutableListOf<String>()
            val cached = lastSuccessfulModel
            if (!cached.isNullOrBlank() && cached in candidateModels) {
                orderedModels.add(cached)
                orderedModels.addAll(candidateModels.filter { it != cached })
            } else {
                orderedModels.addAll(candidateModels)
            }

            for (modelName in orderedModels) {
                try {
                    val requestBody = buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", prompt)
                                    })
                                    for (base64 in base64Images) {
                                        add(buildJsonObject {
                                            put("inlineData", buildJsonObject {
                                                put("mimeType", "image/jpeg")
                                                put("data", base64)
                                            })
                                        })
                                    }
                                })
                            })
                        })
                        put("generationConfig", buildJsonObject {
                            put("responseMimeType", "application/json")
                            put("maxOutputTokens", 2048)
                            put("thinkingConfig", buildJsonObject {
                                put("thinking_level", "LOW")
                                put("thinkingLevel", "LOW")
                            })
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

                    val candidateObj = jsonObj["candidates"]
                        ?.jsonArray
                        ?.firstOrNull()
                        ?.jsonObject
                    val parts = candidateObj
                        ?.get("content")
                        ?.jsonObject
                        ?.get("parts")
                        ?.jsonArray

                    // Filtrar partes con "thought": true y extraer el texto JSON real
                    val realParts = parts?.mapNotNull { part ->
                        val pObj = part.jsonObject
                        val isThought = pObj["thought"]?.jsonPrimitive?.contentOrNull == "true"
                        val pText = pObj["text"]?.jsonPrimitive?.contentOrNull
                        if (!isThought && !pText.isNullOrBlank()) pText else null
                    }

                    val text = realParts?.joinToString("\n")?.takeIf { it.isNotBlank() }
                        ?: parts?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.lastOrNull { it.contains("{") }
                        ?: parts?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.lastOrNull()

                    if (!text.isNullOrBlank()) {
                        Log.d(TAG, "$modelName responded OK (${text.length} chars)")
                        lastSuccessfulModel = modelName
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
        } finally {
            visionParts.forEach { if (it != bitmap && !it.isRecycled) it.recycle() }
        }
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
        var holeCards: List<PokerCard> = emptyList()
        var communityCards: List<PokerCard> = emptyList()
        var outs = currentState.outs
        var winRate = currentState.winRate
        var gtoAction = currentState.gtoAction
        var gtoActionValue = currentState.gtoActionValue
        var detectedPlayers = currentState.jugadores
        var detectedDealer = currentState.dealerPosition
        var detectedMyPos = currentState.posicion
        var detectedBote = currentState.bote
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
            
            val cartasRaw = when (val elem = jsonObj["cartas"] ?: jsonObj["hole_cards"] ?: jsonObj["cartasPropias"]) {
                is JsonPrimitive -> elem.contentOrNull ?: ""
                is JsonArray -> elem.joinToString(" ") { (it as? JsonPrimitive)?.contentOrNull ?: "" }
                else -> ""
            }
            if (cartasRaw.isNotBlank() && cartasRaw != "-" && !cartasRaw.equals("ninguna", ignoreCase = true) && !cartasRaw.equals("none", ignoreCase = true)) {
                val parsed = PokerCard.parseMultiple(cartasRaw)
                if (parsed.isNotEmpty()) {
                    holeCards = parsed.take(2)
                    if (parsed.size > 2) communityCards = parsed.drop(2)
                }
            }

            val mesaRaw = when (val elem = jsonObj["mesa"] ?: jsonObj["community_cards"] ?: jsonObj["board"]) {
                is JsonPrimitive -> elem.contentOrNull ?: ""
                is JsonArray -> elem.joinToString(" ") { (it as? JsonPrimitive)?.contentOrNull ?: "" }
                else -> ""
            }
            if (mesaRaw.isNotBlank() && mesaRaw != "-" && !mesaRaw.equals("ninguna", ignoreCase = true) && !mesaRaw.equals("none", ignoreCase = true)) {
                val parsed = PokerCard.parseMultiple(mesaRaw)
                if (parsed.isNotEmpty()) communityCards = parsed
            }
            
            (jsonObj["jugadores"] ?: jsonObj["players"])?.jsonPrimitive?.let { prim ->
                val count = prim.intOrNull ?: prim.contentOrNull?.toIntOrNull()
                if (count != null && count in 2..9) detectedPlayers = count
            }
            
            (jsonObj["dealer"] ?: jsonObj["button"])?.jsonPrimitive?.contentOrNull?.let { value ->
                if (value.isNotBlank()) detectedDealer = value.uppercase().trim()
            }
            
            (jsonObj["miPosicion"] ?: jsonObj["position"])?.jsonPrimitive?.contentOrNull?.let { value ->
                if (value.isNotBlank()) detectedMyPos = value.uppercase().trim()
            }
            
            (jsonObj["bote"] ?: jsonObj["pot"])?.jsonPrimitive?.contentOrNull?.let { value ->
                val cleanBote = value.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
                if (cleanBote != null && cleanBote > 0.0) {
                    detectedBote = cleanBote
                    GTOStateManager.setPotSize(cleanBote)
                }
            }
            
            (jsonObj["fase"] ?: jsonObj["street"])?.jsonPrimitive?.contentOrNull?.let { value ->
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

            // Punto 30: Validar numCartasMesa y numCartasHero
            val numHero = (jsonObj["numCartasHero"])?.jsonPrimitive?.intOrNull
            val numMesa = (jsonObj["numCartasMesa"])?.jsonPrimitive?.intOrNull
            if (numMesa != null && numMesa != communityCards.size && numMesa in 3..5) {
                Log.w(TAG, "numCartasMesa=$numMesa but parsed ${communityCards.size} cards � Gemini may have missed cards")
            }
            if (numHero != null && numHero != holeCards.size && numHero == 2) {
                Log.w(TAG, "numCartasHero=$numHero but parsed ${holeCards.size} cards � re-check Hero detection")
            }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON response: $rawText", e)
        }

        val detectedFase = if (communityCards.isNotEmpty()) {
            when (communityCards.size) {
                2, 3 -> "Flop"
                4 -> "Turn"
                5 -> "River"
                else -> if (parsedFase != null && !parsedFase.equals("Preflop", ignoreCase = true)) parsedFase else "Flop"
            }
        } else {
            parsedFase ?: "Preflop"
        }

        GTOStateManager.updateFromAnalysis(
            fase = detectedFase,
            bote = detectedBote,
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
                bote = detectedBote,
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
            bote = detectedBote,
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