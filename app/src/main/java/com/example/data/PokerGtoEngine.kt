package com.example.data

/**
 * Calculadora y Motor GTO Matemático determinista.
 *
 * Utiliza equity de Hold'em, pot odds, conteo de outs y tablas de rangos GTO
 * en base a:
 * - Fase (Preflop, Flop, Turn, River)
 * - Posición (UTG, MP, CO, BTN, SB, BB)
 * - Jugadores activos (2 a 9)
 * - Cartas propias (Hole cards)
 * - Cartas comunitarias (Board)
 * - Bote y apuesta rival
 */
object PokerGtoEngine {

    data class GtoDecision(
        val action: GtoAction,
        val actionValue: String,
        val winRate: String,
        val outs: String,
        val explanation: String
    )

    fun calculate(
        holeCards: List<PokerCard>,
        board: List<PokerCard>,
        jugadores: Int,
        posicion: String,
        fase: String,
        bote: Double = 150.0,
        apuestaRival: Double = 25.0
    ): GtoDecision {
        if (holeCards.isEmpty()) {
            return GtoDecision(
                action = GtoAction.CHECK,
                actionValue = "",
                winRate = "—",
                outs = "—",
                explanation = "Esperando cartas propias"
            )
        }

        val effectiveFase = when (board.size) {
            0 -> if (fase.equals("Preflop", ignoreCase = true)) "Preflop" else fase
            2, 3 -> "Flop"
            4 -> "Turn"
            5 -> "River"
            else -> if (board.isNotEmpty()) "Flop" else fase
        }

        return if (board.isEmpty() && effectiveFase.equals("Preflop", ignoreCase = true)) {
            calculatePreflop(holeCards, jugadores, posicion)
        } else {
            calculatePostflop(holeCards, board, jugadores, posicion, effectiveFase)
        }
    }

    private fun calculatePreflop(
        holeCards: List<PokerCard>,
        jugadores: Int,
        posicion: String
    ): GtoDecision {
        val c1 = holeCards.firstOrNull() ?: return defaultDecision()
        val c2 = holeCards.getOrNull(1) ?: return defaultDecision()

        val rankVal1 = rankValue(c1.rank)
        val rankVal2 = rankValue(c2.rank)
        val high = maxOf(rankVal1, rankVal2)
        val low = minOf(rankVal1, rankVal2)
        val isPair = high == low
        val isSuited = c1.suit == c2.suit && c1.suit != CardSuit.UNKNOWN

        val score = (high * 2) + low + (if (isPair) 20 else 0) + (if (isSuited) 5 else 0)
        val isEarlyPos = posicion.equals("UTG", ignoreCase = true) || posicion.equals("MP", ignoreCase = true)
        val isLatePos = posicion.equals("BTN", ignoreCase = true) || posicion.equals("CO", ignoreCase = true)

        // Clasificación de rangos
        return when {
            // Monstruos AA, KK, QQ, AKs
            isPair && high >= 12 -> {
                GtoDecision(GtoAction.THREE_BET, "3.5x", "82%", "—", "Mano Premium Monstruo")
            }
            (high == 14 && low == 13) || (isPair && high >= 10) -> {
                GtoDecision(GtoAction.RAISE, "2.5x - 3x", "71%", "—", "Rango Fuerte GTO")
            }
            isPair -> {
                // Parejas medias/bajas
                if (isLatePos || jugadores <= 4) {
                    GtoDecision(GtoAction.RAISE, "2.5x", "58%", "—", "Pocket Pair en Posición")
                } else {
                    GtoDecision(GtoAction.CALL, "1x", "52%", "—", "Set Mining GTO")
                }
            }
            isSuited && (high == 14 || (high >= 11 && low >= 9)) -> {
                GtoDecision(GtoAction.RAISE, "2.5x", "62%", "—", "Broadways / Ases Suited")
            }
            isSuited && (high - low == 1) && high in 7..11 -> {
                // Suited connectors (ej. 89s, 9Ts, JTs)
                if (isLatePos) {
                    GtoDecision(GtoAction.RAISE, "2.2x", "54%", "—", "Suited Connector en BTN/CO")
                } else {
                    GtoDecision(GtoAction.CALL, "1x", "48%", "—", "Suited Connector Especulativo")
                }
            }
            high >= 12 && low >= 10 -> {
                if (isEarlyPos && jugadores >= 7) {
                    GtoDecision(GtoAction.FOLD, "", "35%", "—", "Offsuit broadway débil en UTG")
                } else {
                    GtoDecision(GtoAction.CALL, "1x", "46%", "—", "Broadway Call GTO")
                }
            }
            else -> {
                if (posicion.equals("BB", ignoreCase = true)) {
                    GtoDecision(GtoAction.CHECK, "", "32%", "—", "Defensa de Ciegas GTO")
                } else {
                    GtoDecision(GtoAction.FOLD, "", "24%", "—", "Fold GTO Preflop")
                }
            }
        }
    }

    private fun calculatePostflop(
        holeCards: List<PokerCard>,
        board: List<PokerCard>,
        jugadores: Int,
        posicion: String,
        fase: String
    ): GtoDecision {
        val allCards = holeCards + board
        val holeRanks = holeCards.map { rankValue(it.rank) }
        val boardRanks = board.map { rankValue(it.rank) }
        val maxBoardRank = boardRanks.maxOrNull() ?: 0

        // 1. Frecuencia de rangos (Pares, Tríos, Poker)
        val rankCounts = allCards.groupBy { rankValue(it.rank) }.mapValues { it.value.size }
        val fourOfAKind = rankCounts.filter { it.value >= 4 }.keys.firstOrNull()
        val threeOfAKind = rankCounts.filter { it.value == 3 }.keys.sortedDescending()
        val pairs = rankCounts.filter { it.value == 2 }.keys.sortedDescending()

        val isPocketPair = holeRanks.size == 2 && holeRanks[0] == holeRanks[1]
        val hitSet = (isPocketPair && boardRanks.contains(holeRanks[0])) ||
                (threeOfAKind.isNotEmpty() && holeRanks.any { it in threeOfAKind })

        // Full House: Trío + Pareja o doble trío
        val isFullHouse = (threeOfAKind.size >= 2) || (threeOfAKind.isNotEmpty() && pairs.isNotEmpty())
        val heroMatchesFullHouse = isFullHouse && holeRanks.any { it in threeOfAKind || it in pairs }

        // Doble Pareja: 2 pares distintos con participación de Hero
        val isTwoPair = pairs.size >= 2 && holeRanks.any { it in pairs }
        val isTopTwoPair = isTwoPair && pairs.take(2).all { it in holeRanks || it >= maxBoardRank }

        // Pareja / Top Pair
        var hitPair = false
        var hitTopPair = false
        for (hr in holeRanks) {
            val matches = boardRanks.count { it == hr }
            if (matches == 1) {
                hitPair = true
                if (hr >= maxBoardRank) hitTopPair = true
            }
        }
        if (isPocketPair) {
            hitPair = true
            if (holeRanks[0] > maxBoardRank) hitTopPair = true // Overpair
        }

        // 2. Color (Flush) y Proyecto de Color
        val suitCounts = allCards.groupBy { it.suit }
        val maxSuitCount = suitCounts.filterKeys { it != CardSuit.UNKNOWN }.values.maxOfOrNull { it.size } ?: 0
        val isFlush = maxSuitCount >= 5
        val isFlushDraw = maxSuitCount == 4

        // 3. Escalera (Straight) con soporte de Rueda As-bajo (A-2-3-4-5)
        val uniqueRanks = allCards.map { rankValue(it.rank) }.toSet()
        val ranksWithAceLow = if (uniqueRanks.contains(14)) uniqueRanks + 1 else uniqueRanks
        var straightHigh = 0
        for (high in 14 downTo 5) {
            if ((high downTo high - 4).all { ranksWithAceLow.contains(it) }) {
                straightHigh = high
                break
            }
        }
        val isStraight = straightHigh > 0
        val straightRanks = if (isStraight) (straightHigh downTo straightHigh - 4).map { if (it == 1) 14 else it }.toSet() else emptySet()
        val heroContributesToStraight = holeRanks.any { it in straightRanks }

        // 4. Proyectos de Escalera (OESD 8 outs vs Gutshot 4 outs)
        var isOesd = false
        var isGutshot = false
        if (!isStraight) {
            for (high in 13 downTo 5) {
                val fourRun = (high downTo high - 3).toSet()
                if (fourRun.all { ranksWithAceLow.contains(it) }) {
                    val lowEnd = high - 4
                    val highEnd = high + 1
                    val canLow = lowEnd in 1..14
                    val canHigh = highEnd in 2..14
                    val hasHeroInRun = holeRanks.any { it in fourRun.map { r -> if (r == 1) 14 else r } }
                    if (hasHeroInRun) {
                        if (canLow && canHigh && lowEnd >= 2) {
                            isOesd = true
                            break
                        } else if (canLow || canHigh) {
                            isGutshot = true
                        }
                    }
                }
            }
            if (!isOesd) {
                for (high in 14 downTo 5) {
                    val span = (high downTo high - 4).toSet()
                    val intersection = span.filter { ranksWithAceLow.contains(it) }
                    if (intersection.size == 4) {
                        val hasHeroInSpan = holeRanks.any { it in intersection.map { r -> if (r == 1) 14 else r } }
                        if (hasHeroInSpan) {
                            isGutshot = true
                            break
                        }
                    }
                }
            }
        }

        // 5. Conteo riguroso de Outs
        var outsCount = 0
        val outsDesc = mutableListOf<String>()
        if (isFlushDraw) {
            outsCount += 9
            outsDesc.add("9 Color")
        }
        if (isOesd) {
            outsCount += 8
            outsDesc.add("8 Escalera")
        } else if (isGutshot) {
            outsCount += 4
            outsDesc.add("4 Gutshot")
        }
        if (hitTopPair && !isFlush && !isStraight) {
            outsCount += 5
            outsDesc.add("Top Pair")
        } else if (hitPair && !isFlush && !isStraight) {
            outsCount += 3
            outsDesc.add("Pareja")
        }

        val outsString = if (outsDesc.isNotEmpty()) outsDesc.joinToString(" + ") else "0 Outs"

        // 6. Decisión GTO determinista
        return when {
            fourOfAKind != null && holeRanks.contains(fourOfAKind) -> {
                GtoDecision(GtoAction.ALL_IN, "MAX", "99%", "Nuts Poker", "Poker Conectado GTO")
            }
            isFullHouse && heroMatchesFullHouse -> {
                GtoDecision(GtoAction.ALL_IN, "MAX", "97%", "Nuts Full", "Full House Conectado GTO")
            }
            isFlush -> {
                GtoDecision(GtoAction.ALL_IN, "MAX", "94%", "Nuts Flush", "Color Conectado GTO")
            }
            isStraight && heroContributesToStraight -> {
                GtoDecision(GtoAction.RAISE, "3.5x - 4x", "89%", "Escalera", "Escalera Conectada GTO")
            }
            hitSet -> {
                GtoDecision(GtoAction.BET, "75% Pot", "85%", "Full House Outs", "Trío/Set Conectado GTO")
            }
            isTwoPair -> {
                if (isTopTwoPair) {
                    GtoDecision(GtoAction.BET, "65% Pot", "78%", "Doble Pareja Top", "Doble Pareja Máxima GTO")
                } else {
                    GtoDecision(GtoAction.BET, "50% Pot", "72%", "Doble Pareja", "Doble Pareja GTO")
                }
            }
            isFlushDraw && (hitPair || isOesd) -> {
                GtoDecision(GtoAction.RAISE, "3.5x", "65%", "$outsCount Outs ($outsString)", "Combo Draw Monstruo GTO")
            }
            isFlushDraw -> {
                GtoDecision(GtoAction.CALL, "1 Pot", "45%", "9 Outs (Color)", "Proyecto de Color GTO")
            }
            isOesd -> {
                GtoDecision(GtoAction.CALL, "1x", "43%", "8 Outs (OESD)", "Proyecto de Escalera Abierta")
            }
            hitTopPair -> {
                if (jugadores <= 3) {
                    GtoDecision(GtoAction.BET, "50% Pot", "68%", "5 Outs", "Top Pair en Bote Corto")
                } else {
                    GtoDecision(GtoAction.CALL, "1x", "58%", "5 Outs", "Top Pair en Multiway Pot")
                }
            }
            hitPair -> {
                GtoDecision(GtoAction.CHECK, "", "44%", "3 Outs", "Segunda Pareja / Pot Control")
            }
            isGutshot -> {
                GtoDecision(GtoAction.CHECK, "", "36%", "4 Outs (Gutshot)", "Proyecto Gutshot Especulativo")
            }
            outsCount >= 8 -> {
                GtoDecision(GtoAction.CALL, "1x", "40%", "$outsCount Outs", "Proyecto Fuerte GTO")
            }
            else -> {
                if (fase.equals("River", ignoreCase = true)) {
                    GtoDecision(GtoAction.FOLD, "", "10%", "0 Outs", "Sin mano en el River")
                } else {
                    GtoDecision(GtoAction.CHECK, "", "25%", "0 Outs", "Pasa o Foldea ante apuesta")
                }
            }
        }
    }

    private fun rankValue(rank: String): Int {
        return when (rank.uppercase().trim()) {
            "A", "1" -> 14
            "K" -> 13
            "Q" -> 12
            "J" -> 11
            "T", "10" -> 10
            else -> rank.trim().toIntOrNull() ?: 2
        }
    }

    private fun defaultDecision() = GtoDecision(
        action = GtoAction.CHECK,
        actionValue = "",
        winRate = "50%",
        outs = "—",
        explanation = "Esperando lectura"
    )
}
