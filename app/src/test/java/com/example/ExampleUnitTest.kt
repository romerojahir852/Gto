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
            bote = "150 BB",
            jugadores = 6,
            posicion = "BTN",
            dealerPosition = "BTN"
        )
        val prompt = repo.buildSurgicalPrompt(state)

        assertTrue(prompt.contains("Contexto GTO: Fase[Flop], Jugadores[6], MiPosicion[BTN], Dealer[BTN], Bote[150 BB]"))
        assertTrue(prompt.contains("REGLA 1 (CARTAS PROPIAS): Tus 2 cartas de la mano están SIEMPRE situadas en el cuadro de la PARTE INFERIOR."))
        assertTrue(prompt.contains("REGLA 2 (CARTAS COMUNITARIAS): Las cartas comunitarias (Flop, Turn, River) están alineadas exclusivamente en el CENTRO de la mesa."))
        assertTrue(prompt.contains("REGLA 3 (JUGADORES Y DEALER): En el panorama de la mesa, contabiliza el número total de jugadores activos (entre 2 y 9) y localiza la posición del botón del Dealer ('D')."))
        assertTrue(prompt.contains("Cartas:[ValorPalo] | Mesa:[ValorPalo] | Jugadores:[2-9] | Dealer:[Posición] | MiPosicion:[Posición] | Fase:[Preflop/Flop/Turn/River]"))
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
        val rawResponse = "Cartas:[As Kd] | Mesa:[Qh Jh 2c] | Jugadores:[7] | Dealer:[CO] | MiPosicion:[BTN] | Fase:[Flop] | Outs:[8] | Win:[48]% | GTO:[Raise 3.5BB]"
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
}
