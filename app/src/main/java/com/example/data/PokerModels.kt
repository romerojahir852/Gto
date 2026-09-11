package com.example.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Street(
    val displayName: String,
    val description: String,
    val promptTemplate: String
) {
    PREFLOP(
        displayName = "Preflop",
        description = "Enfoque en Equity inicial, posición y 3-Bet",
        promptTemplate = "Analiza la mesa de Texas Hold'em en la imagen. Responde estrictamente con esta estructura: Cartas: [Valor y palo] | Posición aparente: [UTG, MP, BTN, etc.] | Preflop Equity: [%] | Jugada GTO: [Fold/Call/3-Bet]."
    ),
    POSTFLOP(
        displayName = "Postflop (Flop/Turn)",
        description = "Enfoque en Proyectos, Outs y Win %",
        promptTemplate = "Analiza la mesa. Responde estrictamente con esta estructura: Mano: [Tus cartas] | Mesa: [Flop/Turn] | Proyecto actual: [Ej. Flush draw, Gutshot] | Total de Outs: [Número] | Win %: [%] | Acción GTO: [Check/Bet/Fold]."
    ),
    FAST_GTO(
        displayName = "GTO Ultra-Rápido",
        description = "Mano | Mesa | Acción GTO inmediata",
        promptTemplate = "Analiza esta imagen. Responde solo con este formato. Cartas: [Tus cartas, ej. AhKd] | Mesa: [Cartas comunitarias] | Jugada GTO: [Fold/Call/Raise]. Cero explicaciones."
    )
}

enum class CardSuit(val symbol: String, val suitName: String, val color: Color) {
    HEARTS("♥", "Corazones", Color(0xFFEF4444)),
    DIAMONDS("♦", "Diamantes", Color(0xFF3B82F6)),
    CLUBS("♣", "Tréboles", Color(0xFF10B981)),
    SPADES("♠", "Picas", Color(0xFF1E293B)),
    UNKNOWN("?", "Desconocido", Color(0xFF94A3B8));

    companion object {
        fun fromChar(c: Char): CardSuit {
            return when (c.lowercaseChar()) {
                'h', '♥' -> HEARTS
                'd', '♦' -> DIAMONDS
                's', '♠', 'p' -> SPADES
                'c', '♣', 't' -> CLUBS
                else -> UNKNOWN
            }
        }

        fun fromString(str: String): CardSuit {
            val lower = str.lowercase()
            return when {
                lower.contains("corazón") || lower.contains("corazones") || lower.contains("heart") -> HEARTS
                lower.contains("diamante") || lower.contains("diamond") -> DIAMONDS
                lower.contains("trébol") || lower.contains("trebol") || lower.contains("club") -> CLUBS
                lower.contains("pica") || lower.contains("spade") -> SPADES
                else -> UNKNOWN
            }
        }
    }
}

data class PokerCard(
    val rank: String,
    val suit: CardSuit
) {
    val displayRank: String
        get() = if (rank.equals("T", ignoreCase = true) || rank == "10") "10" else rank.uppercase()

    val displayString: String
        get() = "$displayRank${suit.symbol}"

    companion object {
        fun parse(token: String): PokerCard? {
            val clean = token.trim().replace("[", "").replace("]", "").replace(",", "")
            if (clean.length < 2) return null
            val suitChar = clean.last()
            val rankStr = clean.substring(0, clean.length - 1)
            val suit = CardSuit.fromChar(suitChar)
            return PokerCard(rank = rankStr, suit = suit)
        }

        fun parseMultiple(text: String): List<PokerCard> {
            val results = mutableListOf<PokerCard>()
            val regex = Regex("""([AKQJT0-9]{1,2})\s*([hdcs♥♦♣♠])""", RegexOption.IGNORE_CASE)
            regex.findAll(text).forEach { match ->
                val r = match.groupValues[1]
                val s = match.groupValues[2].first()
                results.add(PokerCard(rank = r, suit = CardSuit.fromChar(s)))
            }
            return results
        }
    }
}

enum class GtoAction(val title: String, val color: Color, val bgTint: Color) {
    FOLD("FOLD", Color(0xFFEF4444), Color(0x33EF4444)),
    CHECK("CHECK", Color(0xFF3B82F6), Color(0x333B82F6)),
    CALL("CALL", Color(0xFFFBBF24), Color(0x33F59E0B)),
    BET("BET", Color(0xFF10B981), Color(0x3310B981)),
    RAISE("RAISE", Color(0xFF00E676), Color(0x3300E676)),
    THREE_BET("3-BET", Color(0xFF00E676), Color(0x3300E676)),
    ALL_IN("ALL-IN", Color(0xFFA855F7), Color(0x338B5CF6)),
    ERROR("ERROR", Color(0xFFEF4444), Color(0x33EF4444)),
    RETRY("REINTENTAR", Color(0xFFF97316), Color(0x33F97316)),
    UNKNOWN("ANÁLISIS", Color(0xFF94A3B8), Color(0x2294A3B8));

    companion object {
        fun fromString(str: String): GtoAction {
            val upper = str.uppercase()
            return when {
                upper.contains("ERROR") -> ERROR
                upper.contains("REINTENTAR") || upper.contains("RETRY") -> RETRY
                upper.contains("FOLD") -> FOLD
                upper.contains("3-BET") || upper.contains("3BET") -> THREE_BET
                upper.contains("ALL-IN") || upper.contains("ALL IN") -> ALL_IN
                upper.contains("RAISE") || upper.contains("SUBIR") -> RAISE
                upper.contains("BET") || upper.contains("APOSTAR") -> BET
                upper.contains("CALL") || upper.contains("PAGAR") -> CALL
                upper.contains("CHECK") || upper.contains("PASAR") -> CHECK
                else -> UNKNOWN
            }
        }
    }
}

enum class PokerDraw(val title: String, val icon: String, val color: Color) {
    FLUSH_DRAW("Color (Flush Draw)", "♥", Color(0xFFEF4444)),
    OPEN_ENDED("Escalera Abierta (OESD)", "⇄", Color(0xFFF59E0B)),
    GUTSHOT("Gutshot (Escalera Int.)", "⇥", Color(0xFF3B82F6)),
    OVERCARDS("Overcards", "↑", Color(0xFF10B981)),
    SET_TRIPS("Set / Trío", "★", Color(0xFF8B5CF6)),
    TWO_PAIR("Doble Pareja", "♦", Color(0xFFEC4899)),
    PAIR("Pareja", "♠", Color(0xFF64748B))
}

enum class BettingUnit(
    val code: String,
    val title: String,
    val symbol: String,
    val description: String
) {
    BB(
        code = "BB",
        title = "Ciegas Grandes (BB)",
        symbol = "BB",
        description = "Mesa en Ciegas Grandes (ej. 150 BB)"
    ),
    CHIPS(
        code = "FICHAS / $",
        title = "Fichas / Dinero ($)",
        symbol = "$",
        description = "Mesa en Fichas / Dinero Real ($)"
    );

    fun toggle(): BettingUnit = if (this == BB) CHIPS else BB
}

data class PokerAnalysisResult(
    val rawText: String,
    val holeCards: List<PokerCard> = emptyList(),
    val communityCards: List<PokerCard> = emptyList(),
    val position: String? = null,
    val drawProjects: List<PokerDraw> = emptyList(),
    val drawText: String? = null,
    val totalOuts: Int? = null,
    val outsDetail: String? = null,
    val winEquity: String? = null,
    val gtoAction: GtoAction = GtoAction.UNKNOWN,
    val street: Street = Street.FAST_GTO,
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val isSimulated: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Immutable, reactive state of current Texas Hold'em hand (Single Source of Truth).
 * Updated incrementally without unnecessary variable resets.
 */
@Immutable
data class HandState(
    val fase: String = "Preflop", // Preflop, Flop, Turn, River
    val bote: Double = 150.0,
    val apuestaRival: Double = 25.0,
    val cartasPropias: List<PokerCard> = emptyList(),
    val cartasComunitarias: List<PokerCard> = emptyList(),
    val outs: String = "—",
    val winRate: String = "—",
    val gtoAction: GtoAction = GtoAction.UNKNOWN,
    val gtoActionValue: String = "",
    val rawText: String = "",
    val latencyMs: Long = 0L,
    val isLoading: Boolean = false,
    val isExpanded: Boolean = true,
    val isSimulation: Boolean = false,
    val statusMessage: String = "Listo para capturar",
    val drawProjects: List<PokerDraw> = emptyList(),
    val bettingUnit: BettingUnit = BettingUnit.BB,
    val bigBlindSize: Double = 2.0,
    val jugadores: Int = 6,
    val posicion: String = "BTN",
    val dealerPosition: String = "BTN",
    val dealerDetected: Boolean = false,
    val tablePositionsSummary: String = "BTN (Dealer) • SB • BB • UTG • MP • CO"
) {
    val displayBote: String
        get() = when (bettingUnit) {
            BettingUnit.BB -> {
                val bbValue = if (bigBlindSize > 0.0) bote / bigBlindSize else bote
                val formatted = if (bbValue % 1.0 == 0.0) {
                    bbValue.toInt().toString()
                } else {
                    String.format(java.util.Locale.US, "%.1f", bbValue)
                }
                "$formatted BB"
            }
            BettingUnit.CHIPS -> {
                val formatted = if (bote % 1.0 == 0.0) bote.toInt().toString() else bote.toString()
                "$$formatted"
            }
        }

    val displayApuestaRival: String
        get() = when (bettingUnit) {
            BettingUnit.BB -> {
                val bbValue = if (bigBlindSize > 0.0) apuestaRival / bigBlindSize else apuestaRival
                val formatted = if (bbValue % 1.0 == 0.0) {
                    bbValue.toInt().toString()
                } else {
                    String.format(java.util.Locale.US, "%.1f", bbValue)
                }
                "$formatted BB"
            }
            BettingUnit.CHIPS -> {
                val formatted = if (apuestaRival % 1.0 == 0.0) apuestaRival.toInt().toString() else apuestaRival.toString()
                "$$formatted"
            }
        }

    val cartasPropiasDisplay: String
        get() = if (cartasPropias.isNotEmpty()) {
            cartasPropias.joinToString(" ") { it.displayString }
        } else {
            "—"
        }

    val cartasComunitariasDisplay: String
        get() = if (cartasComunitarias.isNotEmpty()) {
            cartasComunitarias.joinToString(" ") { it.displayString }
        } else {
            "— (Preflop)"
        }

    val fullGtoDecision: String
        get() = if (gtoAction == GtoAction.UNKNOWN && gtoActionValue.isBlank()) {
            "Escanea para analizar"
        } else if (gtoActionValue.isNotBlank()) {
            if (gtoActionValue.equals(gtoAction.title, ignoreCase = true)) {
                gtoAction.title
            } else {
                "${gtoAction.title} $gtoActionValue"
            }
        } else {
            gtoAction.title
        }

    // Compatibility aliases
    val cartas: String get() = cartasPropiasDisplay
    val mesa: String get() = cartasComunitariasDisplay
    val gtoActionText: String get() = fullGtoDecision
    val holeCards: List<PokerCard> get() = cartasPropias
    val communityCards: List<PokerCard> get() = cartasComunitarias
}

data class VisualOutItem(
    val label: String,
    val iconSymbol: String,
    val iconColor: Color,
    val isSuit: Boolean = false
)

/**
 * Mappings for AI-returned poker keywords directly into local symbols and high-contrast badges,
 * eliminating the latency of waiting for the AI to describe visual elements.
 */
object LocalVisualMapper {
    fun parseOutsToVisuals(outsText: String): List<VisualOutItem> {
        val results = mutableListOf<VisualOutItem>()
        val clean = outsText.lowercase()

        // Card Suits
        if (clean.contains("coraz") || clean.contains("heart") || clean.contains("♥")) {
            results.add(VisualOutItem("Corazones", "♥", Color(0xFFEF4444), isSuit = true))
        }
        if (clean.contains("diaman") || clean.contains("diamond") || clean.contains("♦")) {
            results.add(VisualOutItem("Diamantes", "♦", Color(0xFF3B82F6), isSuit = true))
        }
        if (clean.contains("trebol") || clean.contains("trébol") || clean.contains("club") || clean.contains("♣")) {
            results.add(VisualOutItem("Tréboles", "♣", Color(0xFF10B981), isSuit = true))
        }
        if (clean.contains("pica") || clean.contains("spade") || clean.contains("♠")) {
            results.add(VisualOutItem("Picas", "♠", Color(0xFF1E293B), isSuit = true))
        }

        // Draw and Hand Types
        if (clean.contains("escalera") || clean.contains("straight") || clean.contains("oesd") || clean.contains("gutshot")) {
            results.add(VisualOutItem("Escalera", "⇄", Color(0xFFF59E0B)))
        }
        if (clean.contains("color") || clean.contains("flush")) {
            if (results.none { it.label == "Corazones" || it.label == "Color" }) {
                results.add(VisualOutItem("Color", "♥", Color(0xFFEF4444)))
            }
        }
        if (clean.contains("trio") || clean.contains("trío") || clean.contains("set") || clean.contains("trips")) {
            results.add(VisualOutItem("Trío/Set", "★", Color(0xFF8B5CF6)))
        }
        if (clean.contains("pareja") || clean.contains("pair") || clean.contains("doble")) {
            results.add(VisualOutItem("Pareja", "♠", Color(0xFF64748B)))
        }

        return results
    }
}

/**
 * Unified Texas Hold'em reactive game state manager (Single Source of Truth)
 * Shared identically between ScreenCaptureService, FloatingOverlayManager, and PokerViewModel.
 */
object PokerGameStateManager {
    private val _handState = MutableStateFlow(
        HandState(
            fase = "Preflop",
            bote = 0.0,
            apuestaRival = 0.0,
            cartasPropias = emptyList(),
            cartasComunitarias = emptyList(),
            outs = "—",
            winRate = "—",
            gtoAction = GtoAction.UNKNOWN,
            gtoActionValue = "",
            statusMessage = "Listo para capturar",
            latencyMs = 0L
        )
    )
    val handState: StateFlow<HandState> = _handState.asStateFlow()

    fun updateState(transform: (HandState) -> HandState) {
        _handState.update(transform)
    }

    fun setExpanded(expanded: Boolean) {
        _handState.update { it.copy(isExpanded = expanded) }
    }

    fun setLoading(loading: Boolean) {
        _handState.update { it.copy(isLoading = loading) }
    }

    fun updateStatus(status: String) {
        _handState.update { it.copy(statusMessage = status) }
    }

    fun updateIncremental(
        fase: String? = null,
        bote: Double? = null,
        apuestaRival: Double? = null,
        cartasPropias: List<PokerCard>? = null,
        cartasComunitarias: List<PokerCard>? = null,
        outs: String? = null,
        winRate: String? = null,
        gtoAction: GtoAction? = null,
        gtoActionValue: String? = null,
        rawText: String? = null,
        latencyMs: Long? = null,
        statusMessage: String? = null,
        isSimulation: Boolean? = null,
        drawProjects: List<PokerDraw>? = null,
        bettingUnit: BettingUnit? = null,
        bigBlindSize: Double? = null,
        jugadores: Int? = null,
        posicion: String? = null,
        dealerPosition: String? = null,
        dealerDetected: Boolean? = null,
        tablePositionsSummary: String? = null
    ) {
        _handState.update { current ->
            val newHero = cartasPropias ?: current.cartasPropias
            val heroUnchanged = current.cartasPropias.isNotEmpty() && newHero.isNotEmpty() &&
                current.cartasPropias.map { "${it.rank}_${it.suit}" }.toSet() == newHero.map { "${it.rank}_${it.suit}" }.toSet()

            // Memoria Acumulativa de Mesa: si las cartas de Hero no han cambiado y ya había cartas de mesa,
            // no vaciar la mesa a Preflop por una oclusión o animación transitoria.
            val effectiveBoard = if (cartasComunitarias != null) {
                if (cartasComunitarias.isEmpty() && heroUnchanged && current.cartasComunitarias.isNotEmpty()) {
                    current.cartasComunitarias
                } else {
                    cartasComunitarias
                }
            } else {
                current.cartasComunitarias
            }

            val effectiveFase = if (effectiveBoard.isNotEmpty()) {
                when (effectiveBoard.size) {
                    2, 3 -> "Flop"
                    4 -> "Turn"
                    5 -> "River"
                    else -> "Flop"
                }
            } else {
                fase ?: current.fase
            }

            current.copy(
                fase = effectiveFase,
                bote = bote ?: current.bote,
                apuestaRival = apuestaRival ?: current.apuestaRival,
                cartasPropias = newHero,
                cartasComunitarias = effectiveBoard,
                outs = outs ?: current.outs,
                winRate = winRate ?: current.winRate,
                gtoAction = gtoAction ?: current.gtoAction,
                gtoActionValue = gtoActionValue ?: current.gtoActionValue,
                rawText = rawText ?: current.rawText,
                latencyMs = latencyMs ?: current.latencyMs,
                statusMessage = statusMessage ?: current.statusMessage,
                isSimulation = isSimulation ?: current.isSimulation,
                drawProjects = drawProjects ?: current.drawProjects,
                bettingUnit = bettingUnit ?: current.bettingUnit,
                bigBlindSize = bigBlindSize ?: current.bigBlindSize,
                jugadores = jugadores ?: current.jugadores,
                posicion = posicion ?: current.posicion,
                dealerPosition = dealerPosition ?: current.dealerPosition,
                dealerDetected = dealerDetected ?: current.dealerDetected,
                tablePositionsSummary = tablePositionsSummary ?: current.tablePositionsSummary,
                isLoading = false,
                isExpanded = true
            )
        }
    }

    fun setJugadores(count: Int) {
        _handState.update { it.copy(jugadores = count.coerceIn(2, 9)) }
    }

    fun setPosicion(pos: String) {
        _handState.update { it.copy(posicion = pos) }
    }

    fun setFase(fase: String) {
        _handState.update { it.copy(fase = fase) }
    }

    fun setBettingUnit(unit: BettingUnit) {
        _handState.update { current ->
            current.copy(
                bettingUnit = unit
            )
        }
    }

    fun toggleBettingUnit() {
        setBettingUnit(_handState.value.bettingUnit.toggle())
    }

    fun resetHand() {
        _handState.value = HandState(isExpanded = true)
    }
}

fun PokerAnalysisResult.toHandState(
    current: HandState = PokerGameStateManager.handState.value
): HandState {
    return current.copy(
        cartasPropias = if (holeCards.isNotEmpty()) holeCards else current.cartasPropias,
        cartasComunitarias = if (communityCards.isNotEmpty()) communityCards else current.cartasComunitarias,
        outs = outsDetail ?: totalOuts?.let { "$it Outs" } ?: current.outs,
        winRate = winEquity ?: current.winRate,
        gtoAction = gtoAction,
        gtoActionValue = gtoAction.title,
        latencyMs = latencyMs,
        rawText = rawText,
        isSimulation = isSimulated,
        isLoading = false,
        isExpanded = true,
        drawProjects = drawProjects
    )
}

fun HandState.toAnalysisResult(): PokerAnalysisResult {
    return PokerAnalysisResult(
        rawText = rawText,
        holeCards = cartasPropias,
        communityCards = cartasComunitarias,
        outsDetail = outs,
        winEquity = winRate,
        gtoAction = gtoAction,
        latencyMs = latencyMs,
        isSimulated = isSimulation,
        drawProjects = drawProjects
    )
}
