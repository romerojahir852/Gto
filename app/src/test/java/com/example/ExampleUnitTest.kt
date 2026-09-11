package com.example

import android.graphics.Bitmap
import com.example.data.BettingUnit
import com.example.data.GTOStateManager
import com.example.data.GeminiPokerRepository
import com.example.data.GtoAction
import com.example.data.HandState
import com.example.data.PokerGameStateManager
import com.example.service.PokerImageProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun `gto state manager updates and syncs state correctly`() {
        GTOStateManager.setPlayers(6)
        GTOStateManager.setPosition("BTN")
        GTOStateManager.setFase("Flop")

        assertEquals(6, GTOStateManager.numeroJugadoresActivos.value)
        assertEquals("BTN", GTOStateManager.miPosicion.value)
        assertEquals("Flop", GTOStateManager.faseActual.value)

        // Increment test
        GTOStateManager.incrementPlayers()
        assertEquals(7, GTOStateManager.numeroJugadoresActivos.value)
        assertEquals(7, PokerGameStateManager.handState.value.jugadores)

        // Decrement test
        GTOStateManager.decrementPlayers()
        assertEquals(6, GTOStateManager.numeroJugadoresActivos.value)

        // Position change
        GTOStateManager.setPosition("SB")
        assertEquals("SB", GTOStateManager.miPosicion.value)
        assertEquals("SB", PokerGameStateManager.handState.value.posicion)
    }

    @Test
    fun `spatial prompt generates exact string required for gemini vision`() {
        val repo = GeminiPokerRepository()
        val state = HandState(
            fase = "Flop",
            bote = 150.0,
            jugadores = 6,
            posicion = "BTN",
            dealerPosition = "BTN"
        )
        val prompt = repo.buildSurgicalPrompt(state)

        assertTrue(prompt.contains("Phase[Flop]"))
        assertTrue(prompt.contains("Players[6]"))
        assertTrue(prompt.contains("MyPos[BTN]"))
        assertTrue(prompt.contains("Dealer[BTN]"))
        assertTrue(prompt.contains("Hero Hole Cards"))
        assertTrue(prompt.contains("Community Cards"))
    }

    @Test
    fun `gto engine calculates preflop decisions accurately`() {
        val heroPair = listOf(
            com.example.data.PokerCard("A", com.example.data.CardSuit.SPADES),
            com.example.data.PokerCard("A", com.example.data.CardSuit.HEARTS)
        )
        val decision = com.example.data.PokerGtoEngine.calculate(
            holeCards = heroPair,
            board = emptyList(),
            jugadores = 6,
            posicion = "BTN",
            fase = "Preflop"
        )
        assertEquals(GtoAction.THREE_BET, decision.action)
        assertTrue(decision.winRate.contains("%"))
    }

    @Test
    fun `parse surgical response correctly parses regex-ready output with players dealer and phase`() {
        val repo = GeminiPokerRepository()
        val rawResponse = """{"cartas": "As Kd", "mesa": "Qh Jh 2c", "jugadores": 7, "dealer": "CO", "miPosicion": "BTN", "fase": "Flop", "outs": "8", "win": "48%", "gto": "Raise 3.5BB"}"""
        val state = HandState()
        val parsed = repo.parseSurgicalResponse(rawResponse, state, 850L)

        assertEquals(2, parsed.cartasPropias.size)
        assertEquals(3, parsed.cartasComunitarias.size)
        assertEquals(7, parsed.jugadores)
        assertEquals("CO", parsed.dealerPosition)
        assertEquals("BTN", parsed.posicion)
        assertEquals("Flop", parsed.fase)
        assertEquals("8", parsed.outs)
        assertEquals("48%", parsed.winRate)
        assertEquals(GtoAction.RAISE, parsed.gtoAction)
        assertEquals("3.5BB", parsed.gtoActionValue)
        assertTrue(parsed.tablePositionsSummary.contains("BTN"))
    }

    @Test
    fun `active positions compute accurately for any table size`() {
        val pos6 = GTOStateManager.computeActivePositions(6)
        assertEquals(listOf("UTG", "MP", "CO", "BTN", "SB", "BB"), pos6)

        val pos2 = GTOStateManager.computeActivePositions(2)
        assertEquals(listOf("BTN/SB", "BB"), pos2)

        val summary = GTOStateManager.getPositionsSummary(6, "BTN")
        assertTrue(summary.contains("BTN (D)"))
    }

    @Test
    fun `image processor crops table safely without crashing`() {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val cropped = PokerImageProcessor.cropPokerTableRegions(bitmap)
        assertTrue(cropped.width > 0)
        assertTrue(cropped.height > 0)
    }

    @Test
    fun `parse poker numeric string accurately handles BB, commas and dots from screenshots`() {
        assertEquals(52.0, com.example.service.LocalCardOcrDetector.parsePokerNumericString("52 BB") ?: 0.0, 0.01)
        assertEquals(12.0, com.example.service.LocalCardOcrDetector.parsePokerNumericString("12 BB") ?: 0.0, 0.01)
        assertEquals(2596.0, com.example.service.LocalCardOcrDetector.parsePokerNumericString("2,596") ?: 0.0, 0.01)
        assertEquals(1151.0, com.example.service.LocalCardOcrDetector.parsePokerNumericString("1,151") ?: 0.0, 0.01)
        assertEquals(20700.0, com.example.service.LocalCardOcrDetector.parsePokerNumericString("20.700") ?: 0.0, 0.01)
        assertEquals(19000.0, com.example.service.LocalCardOcrDetector.parsePokerNumericString("19.000") ?: 0.0, 0.01)
    }

    @Test
    fun `gto engine accurately evaluates Screenshot 7 Turn board with 4 community cards`() {
        // Screenshot 7: Hero 9c 8s on Board 3d 7s 4c 2d (Turn)
        val hero = listOf(
            com.example.data.PokerCard("9", com.example.data.CardSuit.CLUBS),
            com.example.data.PokerCard("8", com.example.data.CardSuit.SPADES)
        )
        val turnBoard = listOf(
            com.example.data.PokerCard("3", com.example.data.CardSuit.DIAMONDS),
            com.example.data.PokerCard("7", com.example.data.CardSuit.SPADES),
            com.example.data.PokerCard("4", com.example.data.CardSuit.CLUBS),
            com.example.data.PokerCard("2", com.example.data.CardSuit.DIAMONDS)
        )

        val result = com.example.data.PokerGtoEngine.calculate(
            holeCards = hero,
            board = turnBoard,
            jugadores = 6,
            posicion = "BTN",
            fase = "Turn",
            bote = 52.0
        )

        assertTrue("Should have calculated win equity", result.winRate.isNotEmpty())
        assertTrue("Should have calculated GTO action", result.action != null)
    }

    @Test
    fun `gto engine evaluates Screenshot 4 Queens pair in preflop as premium value`() {
        // Screenshot 4: Hero Qs Qh (QQ) in Preflop
        val queens = listOf(
            com.example.data.PokerCard("Q", com.example.data.CardSuit.SPADES),
            com.example.data.PokerCard("Q", com.example.data.CardSuit.HEARTS)
        )
        val result = com.example.data.PokerGtoEngine.calculate(
            holeCards = queens,
            board = emptyList(),
            jugadores = 6,
            posicion = "BTN",
            fase = "Preflop"
        )
        assertTrue(result.action == GtoAction.RAISE || result.action == GtoAction.THREE_BET || result.action == GtoAction.ALL_IN)
    }

    @Test
    fun `gto engine evaluates Straight and Wheel Straight with Ace-low correctly`() {
        // Regular straight: Hero holds Jc 10s on Board 9d 8h 7c
        val heroStraight = listOf(
            com.example.data.PokerCard("J", com.example.data.CardSuit.CLUBS),
            com.example.data.PokerCard("10", com.example.data.CardSuit.SPADES)
        )
        val boardStraight = listOf(
            com.example.data.PokerCard("9", com.example.data.CardSuit.DIAMONDS),
            com.example.data.PokerCard("8", com.example.data.CardSuit.HEARTS),
            com.example.data.PokerCard("7", com.example.data.CardSuit.CLUBS)
        )
        val decision = com.example.data.PokerGtoEngine.calculate(
            holeCards = heroStraight,
            board = boardStraight,
            jugadores = 6,
            posicion = "BTN",
            fase = "Flop"
        )
        assertEquals(GtoAction.RAISE, decision.action)
        assertTrue(decision.explanation.contains("Escalera"))

        // Wheel straight (A-2-3-4-5): Hero holds As 2d on Board 3c 4h 5s
        val heroWheel = listOf(
            com.example.data.PokerCard("A", com.example.data.CardSuit.SPADES),
            com.example.data.PokerCard("2", com.example.data.CardSuit.DIAMONDS)
        )
        val boardWheel = listOf(
            com.example.data.PokerCard("3", com.example.data.CardSuit.CLUBS),
            com.example.data.PokerCard("4", com.example.data.CardSuit.HEARTS),
            com.example.data.PokerCard("5", com.example.data.CardSuit.SPADES)
        )
        val wheelDecision = com.example.data.PokerGtoEngine.calculate(
            holeCards = heroWheel,
            board = boardWheel,
            jugadores = 6,
            posicion = "BTN",
            fase = "Flop"
        )
        assertEquals(GtoAction.RAISE, wheelDecision.action)
        assertTrue(wheelDecision.explanation.contains("Escalera"))
    }

    @Test
    fun `gto engine evaluates Full House and Two Pair accurately`() {
        // Full House: Hero holds Kh Kd on Board Ks 7c 7d
        val heroFull = listOf(
            com.example.data.PokerCard("K", com.example.data.CardSuit.HEARTS),
            com.example.data.PokerCard("K", com.example.data.CardSuit.DIAMONDS)
        )
        val boardFull = listOf(
            com.example.data.PokerCard("K", com.example.data.CardSuit.SPADES),
            com.example.data.PokerCard("7", com.example.data.CardSuit.CLUBS),
            com.example.data.PokerCard("7", com.example.data.CardSuit.DIAMONDS)
        )
        val fullDecision = com.example.data.PokerGtoEngine.calculate(
            holeCards = heroFull,
            board = boardFull,
            jugadores = 4,
            posicion = "BTN",
            fase = "Flop"
        )
        assertEquals(GtoAction.ALL_IN, fullDecision.action)
        assertTrue(fullDecision.explanation.contains("Full House"))

        // Two Pair: Hero holds Ah Kd on Board As Kc 2s
        val heroTwoPair = listOf(
            com.example.data.PokerCard("A", com.example.data.CardSuit.HEARTS),
            com.example.data.PokerCard("K", com.example.data.CardSuit.DIAMONDS)
        )
        val boardTwoPair = listOf(
            com.example.data.PokerCard("A", com.example.data.CardSuit.SPADES),
            com.example.data.PokerCard("K", com.example.data.CardSuit.CLUBS),
            com.example.data.PokerCard("2", com.example.data.CardSuit.SPADES)
        )
        val twoPairDecision = com.example.data.PokerGtoEngine.calculate(
            holeCards = heroTwoPair,
            board = boardTwoPair,
            jugadores = 4,
            posicion = "BTN",
            fase = "Flop"
        )
        assertEquals(GtoAction.BET, twoPairDecision.action)
        assertTrue(twoPairDecision.explanation.contains("Doble Pareja"))
    }

    @Test
    fun `parse poker numeric string handles noisy tokens with internal whitespace and symbols`() {
        assertEquals(20700.0, com.example.service.LocalCardOcrDetector.parsePokerNumericString("20. 700") ?: 0.0, 0.01)
        assertEquals(1022984.0, com.example.service.LocalCardOcrDetector.parsePokerNumericString("1 022 984") ?: 0.0, 0.01)
        assertEquals(2596.0, com.example.service.LocalCardOcrDetector.parsePokerNumericString("$ 2,596") ?: 0.0, 0.01)
        assertEquals(52.0, com.example.service.LocalCardOcrDetector.parsePokerNumericString("52 BB") ?: 0.0, 0.01)
    }

    @Test
    fun `gto engine evaluates Screenshot 1 PokerStars Flop 8h 6c on 7d 9d Ac as 8-outs OESD Call`() {
        // PokerStars screenshot 1: Hero holds 8h 6c on board 7d 9d Ac
        val hero = listOf(
            com.example.data.PokerCard("8", com.example.data.CardSuit.HEARTS),
            com.example.data.PokerCard("6", com.example.data.CardSuit.CLUBS)
        )
        val flop = listOf(
            com.example.data.PokerCard("7", com.example.data.CardSuit.DIAMONDS),
            com.example.data.PokerCard("9", com.example.data.CardSuit.DIAMONDS),
            com.example.data.PokerCard("A", com.example.data.CardSuit.CLUBS)
        )
        val decision = com.example.data.PokerGtoEngine.calculate(
            holeCards = hero,
            board = flop,
            jugadores = 7,
            posicion = "BTN",
            fase = "Flop"
        )
        // 6-7-8-9 forms an open-ended straight draw (OESD) -> 8 outs!
        assertEquals(GtoAction.CALL, decision.action)
        assertTrue(decision.outs.contains("8") || decision.outs.contains("Escalera"))
        assertTrue("Win equity must reflect strong straight draw", decision.winRate.replace("%", "").toInt() >= 40)
    }

    @Test
    fun `gto engine evaluates Screenshot 5 BC Poker Turn 10h 4d on 4c Ac 7h Qd as active Turn pair`() {
        // BC Poker screenshot 5: Hero holds 10h 4d on board 4c Ac 7h Qd
        val hero = listOf(
            com.example.data.PokerCard("10", com.example.data.CardSuit.HEARTS),
            com.example.data.PokerCard("4", com.example.data.CardSuit.DIAMONDS)
        )
        val turnBoard = listOf(
            com.example.data.PokerCard("4", com.example.data.CardSuit.CLUBS),
            com.example.data.PokerCard("A", com.example.data.CardSuit.CLUBS),
            com.example.data.PokerCard("7", com.example.data.CardSuit.HEARTS),
            com.example.data.PokerCard("Q", com.example.data.CardSuit.DIAMONDS)
        )
        val decision = com.example.data.PokerGtoEngine.calculate(
            holeCards = hero,
            board = turnBoard,
            jugadores = 6,
            posicion = "BTN",
            fase = "Turn"
        )
        // Hero has a pair of 4s on the Turn -> Pot control / Check, NOT Preflop Fold!
        assertTrue(decision.action == GtoAction.CHECK || decision.action == GtoAction.CALL)
        assertTrue(decision.outs.contains("Pareja") || decision.outs.contains("3"))
    }
}
