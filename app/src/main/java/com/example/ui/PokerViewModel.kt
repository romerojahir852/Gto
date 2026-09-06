package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiPokerRepository
import com.example.data.PokerAnalysisResult
import com.example.data.Street
import com.example.service.ScreenCaptureService
import com.example.ui.components.PRESET_HANDS
import com.example.ui.components.PokerHandPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalysisUiState {
    object Idle : AnalysisUiState
    object Capturing : AnalysisUiState
    object Analyzing : AnalysisUiState
    data class Success(val result: PokerAnalysisResult) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

class PokerViewModel : ViewModel() {

    private val repository: GeminiPokerRepository = GeminiPokerRepository()

    companion object {
        private const val TAG = "PokerViewModel"
    }

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val _selectedStreet = MutableStateFlow(Street.POSTFLOP)
    val selectedStreet: StateFlow<Street> = _selectedStreet.asStateFlow()

    private val _latestResult = MutableStateFlow<PokerAnalysisResult?>(null)
    val latestResult: StateFlow<PokerAnalysisResult?> = _latestResult.asStateFlow()

    private val _history = MutableStateFlow<List<PokerAnalysisResult>>(emptyList())
    val history: StateFlow<List<PokerAnalysisResult>> = _history.asStateFlow()

    private val _selectedPreset = MutableStateFlow(PRESET_HANDS[0])
    val selectedPreset: StateFlow<PokerHandPreset> = _selectedPreset.asStateFlow()

    private val _currentPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val currentPreviewBitmap: StateFlow<Bitmap?> = _currentPreviewBitmap.asStateFlow()

    val isServiceRunning: StateFlow<Boolean> = ScreenCaptureService.isServiceRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val handState = com.example.data.PokerGameStateManager.handState

    init {
        // Collect captured bitmaps from ScreenCaptureService
        viewModelScope.launch {
            ScreenCaptureService.capturedBitmapFlow.collect { bitmap ->
                _currentPreviewBitmap.value = bitmap
                analyzeBitmap(bitmap, _selectedStreet.value)
            }
        }
    }

    fun setStreet(street: Street) {
        _selectedStreet.value = street
    }

    fun selectPreset(preset: PokerHandPreset) {
        _selectedPreset.value = preset
        _selectedStreet.value = preset.street
        _currentPreviewBitmap.value = preset.renderBitmap()
    }

    fun triggerScreenCapture() {
        _uiState.value = AnalysisUiState.Capturing
        ScreenCaptureService.requestScreenCapture()
    }

    fun analyzeBitmap(bitmap: Bitmap, street: Street = _selectedStreet.value) {
        viewModelScope.launch {
            _uiState.value = AnalysisUiState.Analyzing
            _currentPreviewBitmap.value = bitmap
            val result = repository.analyzePokerFrame(bitmap, street)

            result.fold(
                onSuccess = { analysis ->
                    _latestResult.value = analysis
                    _history.value = listOf(analysis) + _history.value.take(9)
                    _uiState.value = AnalysisUiState.Success(analysis)
                    ScreenCaptureService.updateOverlayResult(analysis)
                    Log.d(TAG, "Analysis succeeded in ${analysis.latencyMs}ms: ${analysis.gtoAction}")
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "Error al procesar con Gemini"
                    _uiState.value = AnalysisUiState.Error(errorMsg)
                    Log.e(TAG, "Analysis failed", error)
                }
            )
        }
    }

    fun analyzeCurrentPreset() {
        val bitmap = _selectedPreset.value.renderBitmap()
        analyzeBitmap(bitmap, _selectedStreet.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        _latestResult.value = null
    }

    fun clearError() {
        if (_uiState.value is AnalysisUiState.Error) {
            _uiState.value = AnalysisUiState.Idle
        }
    }
}
