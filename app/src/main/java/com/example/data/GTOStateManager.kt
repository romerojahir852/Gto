package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * GTOStateManager: Singleton de Memoria de Estado GTO de la partida en curso.
 * Rastrea de manera fluida y atómica las variables críticas de la mesa:
 * - NumeroJugadoresActivos (Int, 2..9)
 * - MiPosicion (String: UTG, MP, CO, BTN, SB, BB)
 * - FaseActual (Preflop, Flop, Turn, River)
 * - PotSize (Tamaño del Bote)
 *
 * Mantiene sincronizado el estado con PokerGameStateManager para que la UI,
 * el HUD flotante y las llamadas a la IA compartan una única fuente de verdad.
 */
object GTOStateManager {

    private val _numeroJugadoresActivos = MutableStateFlow(6)
    val numeroJugadoresActivos: StateFlow<Int> = _numeroJugadoresActivos.asStateFlow()

    private val _miPosicion = MutableStateFlow("BTN")
    val miPosicion: StateFlow<String> = _miPosicion.asStateFlow()

    private val _faseActual = MutableStateFlow("Flop")
    val faseActual: StateFlow<String> = _faseActual.asStateFlow()

    private val _potSize = MutableStateFlow(150.0)
    val potSize: StateFlow<Double> = _potSize.asStateFlow()

    private val _dealerPosition = MutableStateFlow("BTN")
    val dealerPosition: StateFlow<String> = _dealerPosition.asStateFlow()

    private val _dealerDetected = MutableStateFlow(true)
    val dealerDetected: StateFlow<Boolean> = _dealerDetected.asStateFlow()

    val availablePositions = listOf("UTG", "MP", "CO", "BTN", "SB", "BB")
    val availablePhases = listOf("Preflop", "Flop", "Turn", "River")

    /**
     * Calcula la lista ordenada de posiciones activas en la mesa según el número de jugadores (2 a 9)
     */
    fun computeActivePositions(playersCount: Int): List<String> {
        return when (playersCount) {
            2 -> listOf("BTN/SB", "BB")
            3 -> listOf("BTN", "SB", "BB")
            4 -> listOf("CO", "BTN", "SB", "BB")
            5 -> listOf("MP", "CO", "BTN", "SB", "BB")
            6 -> listOf("UTG", "MP", "CO", "BTN", "SB", "BB")
            7 -> listOf("UTG", "MP", "LJ", "CO", "BTN", "SB", "BB")
            8 -> listOf("UTG", "UTG+1", "MP", "LJ", "CO", "BTN", "SB", "BB")
            else -> listOf("UTG", "UTG+1", "UTG+2", "MP", "LJ", "CO", "BTN", "SB", "BB")
        }
    }

    fun getPositionsSummary(playersCount: Int = _numeroJugadoresActivos.value, myPos: String = _miPosicion.value): String {
        val positions = computeActivePositions(playersCount)
        return positions.joinToString(" • ") { pos ->
            if (pos.contains("BTN", ignoreCase = true)) "$pos (D)" else pos
        }
    }

    init {
        // Inicializar sincronización con PokerGameStateManager
        syncToGameState()
    }

    fun incrementPlayers() {
        _numeroJugadoresActivos.update { current ->
            (current + 1).coerceAtMost(9)
        }
        syncToGameState()
    }

    fun decrementPlayers() {
        _numeroJugadoresActivos.update { current ->
            (current - 1).coerceAtLeast(2)
        }
        syncToGameState()
    }

    fun setPlayers(count: Int) {
        _numeroJugadoresActivos.value = count.coerceIn(2, 9)
        syncToGameState()
    }

    fun setPosition(pos: String) {
        if (pos.isNotBlank()) {
            _miPosicion.value = pos.uppercase().trim()
            syncToGameState()
        }
    }

    fun setDealerPosition(dealerPos: String, detected: Boolean = true) {
        _dealerPosition.value = dealerPos.uppercase().trim()
        _dealerDetected.value = detected
        syncToGameState()
    }

    fun setFase(fase: String) {
        if (fase.isNotBlank()) {
            _faseActual.value = fase.trim()
            syncToGameState()
        }
    }

    fun setPotSize(pot: Double) {
        if (pot > 0) {
            _potSize.value = pot
            syncToGameState()
        }
    }

    /**
     * Sincroniza las variables de memoria local con HandState en PokerGameStateManager
     */
    private fun syncToGameState() {
        val positionsSummary = getPositionsSummary(_numeroJugadoresActivos.value, _miPosicion.value)
        PokerGameStateManager.updateIncremental(
            fase = _faseActual.value,
            bote = _potSize.value,
            jugadores = _numeroJugadoresActivos.value,
            posicion = _miPosicion.value,
            dealerPosition = _dealerPosition.value,
            dealerDetected = _dealerDetected.value,
            tablePositionsSummary = positionsSummary
        )
    }

    /**
     * Actualiza la memoria a partir de una lectura externa confirmada de la mesa
     */
    fun updateFromAnalysis(
        fase: String?,
        bote: Double?,
        jugadores: Int? = null,
        dealerPos: String? = null,
        myPos: String? = null
    ) {
        fase?.let { if (it.isNotBlank()) _faseActual.value = it }
        bote?.let { if (it > 0) _potSize.value = it }
        jugadores?.let { if (it in 2..9) _numeroJugadoresActivos.value = it }
        dealerPos?.let { if (it.isNotBlank()) _dealerPosition.value = it.uppercase().trim() }
        myPos?.let { if (it.isNotBlank()) _miPosicion.value = it.uppercase().trim() }
        syncToGameState()
    }
}
