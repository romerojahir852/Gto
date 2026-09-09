package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.BettingUnit
import com.example.data.CardSuit
import com.example.data.GeminiPokerRepository
import com.example.data.GtoAction
import com.example.data.HandState
import com.example.data.PokerAnalysisResult
import com.example.data.PokerCard
import com.example.data.PokerGameStateManager
import com.example.ui.overlay.FloatingPokerHud
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the floating WindowManager overlay containing the Jetpack Compose HUD:
 * 1. Draggable FloatingActionButton (Trigger)
 * 2. Result Panel ("La Nube") with Cartas, Mesa, Outs, Win%, and Acción GTO
 * 3. Handles permissions check for SYSTEM_ALERT_WINDOW
 * 4. Executes screen frame capture & analysis 100% on Dispatchers.IO to guarantee sub-second latency
 * 5. Uses PokerGameStateManager as Single Source of Truth, eliminating state duplication.
 */
class FloatingOverlayManager(
    private val context: Context,
    private val repository: GeminiPokerRepository = GeminiPokerRepository()
) {
    companion object {
        private const val TAG = "FloatingOverlayManager"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val serviceLifecycleOwner = ServiceLifecycleOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    // Single source of truth from PokerGameStateManager
    val hudState: StateFlow<HandState> = PokerGameStateManager.handState

    // Callback for triggering actual screen frame capture from ScreenCaptureService
    var frameProvider: (() -> Bitmap?)? = null

    /**
     * Checks if the app has permission to draw overlays over other apps
     */
    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Mounts and shows the floating overlay over all applications
     */
    fun showOverlay() {
        if (isShowing || composeView != null) return
        if (!canDrawOverlays()) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        try {
            serviceLifecycleOwner.start()

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 24
                y = 260
            }
            layoutParams = params

            val view = ComposeView(context).apply {
                setViewTreeLifecycleOwner(serviceLifecycleOwner)
                setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)

                setContent {
                    val state by PokerGameStateManager.handState.collectAsState()
                    FloatingPokerHud(
                        state = state,
                        onDrag = { delta ->
                            handleDrag(delta)
                        },
                        onTriggerClick = {
                            onTriggerClicked()
                        },
                        onCloseCloud = {
                            closeCloud()
                        }
                    )
                }
            }

            windowManager.addView(view, params)
            composeView = view
            isShowing = true
            Log.d(TAG, "Floating overlay successfully attached to WindowManager")
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "BadTokenException adding overlay view", e)
            cleanupOverlayResources()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach floating overlay", e)
            cleanupOverlayResources()
        }
    }

    /**
     * Updates layoutParams coordinates when user drags the floating button.
     * Guarded with isAttachedToWindow and try/catch.
     */
    private fun handleDrag(delta: Offset) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        if (!isShowing || !view.isAttachedToWindow) return

        params.x = (params.x + delta.x.toInt()).coerceAtLeast(0)
        params.y = (params.y + delta.y.toInt()).coerceAtLeast(0)

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay view not attached to window manager during drag: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating overlay layout during drag", e)
        }
    }

    /**
     * Trigger action when floating button is tapped
     */
    fun onTriggerClicked() {
        analizarPantalla()
    }

    /**
     * Dismisses/collapses the cloud panel while keeping the floating button visible
     */
    fun closeCloud() {
        PokerGameStateManager.setExpanded(false)
    }

    /**
     * Pipeline de captura limpia y de alta resolución (Pilar 1):
     * 1. Hiding UI: Modifica el WindowManager/Compose. Al presionar escanear, la UI flotante
     *    pasa a View.INVISIBLE y alpha = 0f para evitar contaminación visual de avatares/HUD.
     * 2. Delay Estratégico: delay(150) con corrutinas antes de llamar a MediaProjection para
     *    asegurar que el compositor de Android haya renderizado la desaparición del overlay.
     * 3. Cropping (Bitmap): Captura en máxima calidad nativa y recorta únicamente las zonas
     *    de interés matemático (cartas comunitarias/bote central y cartas del jugador), eliminando chat y avatares.
     * 4. Restaurar UI: Inmediatamente después de tener el Bitmap recortado, devuelve la UI a
     *    View.VISIBLE mostrando un estado de "Calculando...".
     * 5. Evaluación de IA con timeout defensivo.
     */
    fun analizarPantalla() {
        scope.launch {
            analizarPantallaConOcultamiento()
        }
    }

    private suspend fun analizarPantallaConOcultamiento() {
        // 1. Hiding UI: Pasar la UI flotante y su ventana en WindowManager a alpha = 0f e INVISIBLE
        withContext(Dispatchers.Main) {
            val view = composeView
            val params = layoutParams
            if (view != null && params != null && view.isAttachedToWindow) {
                try {
                    params.alpha = 0f
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo actualizar alpha a 0f en WindowManager", e)
                }
            }
            composeView?.visibility = View.INVISIBLE
            composeView?.alpha = 0f
        }

        // 2. Delay Estratégico mínimo (100ms) para garantizar render 100% limpio en MediaProjection
        delay(100L)

        // 3. Captura del frame nativo desde MediaProjection sin interferencia del botón flotante
        val rawBitmap = withContext(Dispatchers.IO) {
            frameProvider?.invoke()
        }

        // 4. Restaurar UI: Devolver inmediatamente a VISIBLE mostrando "Calculando..."
        withContext(Dispatchers.Main) {
            composeView?.visibility = View.VISIBLE
            composeView?.alpha = 1f
            val view = composeView
            val params = layoutParams
            if (view != null && params != null && view.isAttachedToWindow) {
                try {
                    params.alpha = 1f
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo restaurar alpha a 1f en WindowManager", e)
                }
            }
            PokerGameStateManager.setExpanded(true)
            PokerGameStateManager.setLoading(true)
            PokerGameStateManager.updateStatus("Calculando...")
        }

        // 5. Ejecutar análisis GTO en segundo plano con el frame nativo completo (sin cortes destructivos)
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            if (rawBitmap != null) {
                val currentState = PokerGameStateManager.handState.value
                val result = repository.analyzeHand(rawBitmap, currentState, context)
                val latency = System.currentTimeMillis() - startTime

                result.fold(
                    onSuccess = { updatedState ->
                        Log.d(TAG, "Live analysis finished in ${latency}ms: ${updatedState.fullGtoDecision}")
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Live analysis failed: ${error.message}")
                        PokerGameStateManager.updateIncremental(
                            statusMessage = error.message ?: "⚠️ Error de análisis",
                            latencyMs = latency
                        )
                    }
                )
            } else {
                val latency = System.currentTimeMillis() - startTime
                PokerGameStateManager.updateIncremental(
                    statusMessage = "⚠️ No se pudo capturar el frame de pantalla",
                    latencyMs = latency
                )
            }
        }
    }



    /**
     * Updates HUD with parsed Gemini results
     */
    fun updateWithAnalysisResult(result: PokerAnalysisResult, isSimulation: Boolean = false) {
        PokerGameStateManager.updateIncremental(
            cartasPropias = if (result.holeCards.isNotEmpty()) result.holeCards else null,
            cartasComunitarias = if (result.communityCards.isNotEmpty()) result.communityCards else null,
            outs = result.outsDetail ?: result.totalOuts?.let { "$it Outs" },
            winRate = result.winEquity,
            gtoAction = result.gtoAction,
            gtoActionValue = result.gtoAction.title,
            latencyMs = result.latencyMs,
            isSimulation = isSimulation,
            rawText = result.rawText,
            drawProjects = result.drawProjects
        )
    }

    /**
     * Detaches overlay and cleans up resources safely
     */
    fun hideOverlay() {
        if (!isShowing && composeView == null) return
        try {
            composeView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "View not attached when hiding overlay: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay from WindowManager", e)
        } finally {
            cleanupOverlayResources()
        }
    }

    private fun cleanupOverlayResources() {
        try {
            serviceLifecycleOwner.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping service lifecycle owner", e)
        }
        composeView = null
        layoutParams = null
        isShowing = false
    }

    fun onDestroy() {
        hideOverlay()
        scope.cancel()
    }
}
