package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.data.toHandState
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
 * 1. Draggable FloatingActionButton (Trigger) with Chat-Head drag-to-trash & snap-to-edge
 * 2. Toggle behavior: tapping the bubble expands/collapses the result panel cleanly
 * 3. Result Panel ("La Nube") with Cartas, Mesa, Outs, Win%, and Acción GTO
 * 4. Handles permissions check for SYSTEM_ALERT_WINDOW
 * 5. Executes screen frame capture & analysis 100% on Dispatchers.IO to guarantee sub-second latency
 * 6. Uses PokerGameStateManager as Single Source of Truth, eliminating state duplication.
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

    // Bottom Trash Target view (Chat Head style)
    private var trashView: ComposeView? = null
    private val isOverTrashState = mutableStateOf(false)

    // Single source of truth from PokerGameStateManager
    val hudState: StateFlow<HandState> = PokerGameStateManager.handState

    // Callback for triggering actual screen frame capture from ScreenCaptureService
    var frameProvider: (suspend () -> Bitmap?)? = null

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
                    com.example.ui.theme.MyApplicationTheme {
                        val state by PokerGameStateManager.handState.collectAsState()
                        FloatingPokerHud(
                            state = state,
                            onDrag = { delta ->
                                handleDrag(delta)
                            },
                            onDragStart = {
                                onDragStarted()
                            },
                            onDragEnd = {
                                onDragEnded()
                            },
                            onDragCancel = {
                                hideTrashTarget()
                            },
                            onTriggerClick = {
                                if (PokerGameStateManager.handState.value.isExpanded) {
                                    closeCloud()
                                } else {
                                    onTriggerClicked()
                                }
                            },
                            onCloseCloud = {
                                closeCloud()
                            },
                            onDismissOverlay = {
                                stopServiceAndCloseOverlay()
                            }
                        )
                    }
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
     * Shows trash target at bottom center of the screen when user drags the bubble
     */
    private fun showTrashTarget() {
        if (trashView != null) return
        try {
            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val density = context.resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (48 * density).toInt()
            }

            val tv = ComposeView(context).apply {
                setViewTreeLifecycleOwner(serviceLifecycleOwner)
                setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)
                setContent {
                    val isOver by isOverTrashState
                    TrashTargetIndicator(isOver = isOver)
                }
            }
            windowManager.addView(tv, params)
            trashView = tv
        } catch (e: Exception) {
            Log.e(TAG, "Error showing trash target view", e)
        }
    }

    /**
     * Hides and removes the trash target view
     */
    private fun hideTrashTarget() {
        isOverTrashState.value = false
        val tv = trashView ?: return
        trashView = null
        try {
            if (tv.isAttachedToWindow) {
                windowManager.removeView(tv)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing trash target view: ${e.message}")
        }
    }

    private fun onDragStarted() {
        showTrashTarget()
    }

    private fun onDragEnded() {
        if (isOverTrashState.value) {
            hideTrashTarget()
            stopServiceAndCloseOverlay()
        } else {
            hideTrashTarget()
            // Snap gently to nearest horizontal edge (left or right)
            val params = layoutParams
            val view = composeView
            if (params != null && view != null && view.isAttachedToWindow) {
                val metrics = context.resources.displayMetrics
                val screenWidth = metrics.widthPixels
                val density = metrics.density
                val margin = (16 * density).toInt()
                val bubbleWidth = (46 * density).toInt()
                if (params.x + bubbleWidth / 2 < screenWidth / 2) {
                    params.x = margin
                } else {
                    params.x = (screenWidth - bubbleWidth - margin).coerceAtLeast(0)
                }
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.w(TAG, "Error snapping to screen edge", e)
                }
            }
        }
    }

    /**
     * Updates layoutParams coordinates when user drags the floating button.
     * Checks if bubble enters the bottom trash delete zone.
     */
    private fun handleDrag(delta: Offset) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        if (!isShowing || !view.isAttachedToWindow) return

        params.x = (params.x + delta.x.toInt()).coerceAtLeast(0)
        params.y = (params.y + delta.y.toInt()).coerceAtLeast(0)

        // Check if dragged over bottom trash zone
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val trashCenterX = screenWidth / 2
        val trashCenterY = screenHeight - (75 * density).toInt()

        val bubbleCenterX = params.x + (23 * density).toInt()
        val bubbleCenterY = params.y + (23 * density).toInt()

        val dx = bubbleCenterX - trashCenterX
        val dy = bubbleCenterY - trashCenterY
        val dist = Math.hypot(dx.toDouble(), dy.toDouble())

        isOverTrashState.value = dist < (110 * density)

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
     * Closes the overlay completely and stops the ScreenCaptureService
     */
    fun stopServiceAndCloseOverlay() {
        hideOverlay()
        val intent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        context.startService(intent)
        Log.d(TAG, "Floating overlay closed and ScreenCaptureService stopped")
    }

    /**
     * Pipeline de captura limpia y de alta resolución:
     * 1. Hiding UI: Modifica el WindowManager/Compose. Al presionar escanear, la UI flotante
     *    pasa a View.INVISIBLE y alpha = 0f para evitar contaminación visual del HUD.
     * 2. Delay Estratégico: delay(180) con corrutinas antes de llamar a MediaProjection para
     *    asegurar que el compositor de Android haya renderizado la desaparición del overlay.
     * 3. Cropping: Captura en máxima calidad nativa y recorta las zonas de cartas y mesa.
     * 4. Restaurar UI: Inmediatamente después de tener el Bitmap, devuelve la UI a View.VISIBLE.
     */
    fun analizarPantalla() {
        scope.launch {
            analizarPantallaConOcultamiento()
        }
    }

    private suspend fun analizarPantallaConOcultamiento() {
        // 1. Hiding UI: Pasar la UI flotante a GONE y alpha 0f en WindowManager
        withContext(Dispatchers.Main) {
            val view = composeView
            val params = layoutParams
            if (view != null && params != null && view.isAttachedToWindow) {
                try {
                    params.alpha = 0f
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo actualizar alpha a 0f en WindowManager", e)
                }
            }
            composeView?.visibility = View.GONE
            composeView?.alpha = 0f
        }

        // 2. Delay Estratégico (180ms) para garantizar render 100% limpio en MediaProjection sin el overlay
        delay(180L)

        // 3. Captura del frame nativo desde MediaProjection sin interferencia de la nube ni del botón
        val rawBitmap = withContext(Dispatchers.IO) {
            frameProvider?.invoke()
        }

        // 4. Restaurar la visibilidad de la UI flotante en WindowManager
        withContext(Dispatchers.Main) {
            val view = composeView
            val params = layoutParams
            if (view != null && params != null && view.isAttachedToWindow) {
                try {
                    params.alpha = 1f
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo restaurar alpha en WindowManager", e)
                }
            }
            composeView?.visibility = View.VISIBLE
            composeView?.alpha = 1f
        }

        if (rawBitmap == null) {
            Log.e(TAG, "FrameProvider devolvió null. MediaProjection no disponible o imagen vacía.")
            PokerGameStateManager.updateStatus("⚠️ Error de captura. Intenta nuevamente.")
            return
        }

        // Estado inicial de carga y expansión para mostrar progreso
        PokerGameStateManager.setLoading(true)
        PokerGameStateManager.setExpanded(true)

        // 5. Análisis en segundo plano
        withContext(Dispatchers.IO) {
            try {
                val currentState = PokerGameStateManager.handState.value

                val result = repository.analyzeHand(
                    bitmap = rawBitmap,
                    currentState = currentState,
                    context = context
                )

                result.onSuccess { analysis ->
                    Log.d(TAG, "Análisis exitoso: Hero=${analysis.cartasPropias}, Board=${analysis.cartasComunitarias}, GTO=${analysis.gtoAction.title}")
                }.onFailure { error ->
                    Log.e(TAG, "Fallo al evaluar mano: ${error.message}", error)
                    PokerGameStateManager.updateStatus("⚠️ ${error.message ?: "Error al procesar"}")
                }
            } finally {
                PokerGameStateManager.setLoading(false)
            }
        }
    }

    /**
     * Updates overlay state with direct PokerAnalysisResult from ScreenCaptureService
     */
    fun updateWithAnalysisResult(result: PokerAnalysisResult) {
        PokerGameStateManager.updateState { current ->
            result.toHandState(current)
        }
    }

    /**
     * Removes the overlay view completely from WindowManager.
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
        hideTrashTarget()
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

/**
 * Trash Target indicator shown at the bottom of the screen when user is dragging the floating bubble.
 */
@Composable
private fun TrashTargetIndicator(isOver: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isOver) 1.25f else 1f,
        animationSpec = spring(),
        label = "trash_scale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(bottom = 20.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(scale)
                .size(56.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(if (isOver) Color(0xFFDC2626) else Color(0xDD1E293B))
                .border(
                    width = 2.dp,
                    color = if (isOver) Color(0xFFFF8888) else Color(0xFFEF4444),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Arrastra aquí para cerrar",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xEE0F172A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44EF4444))
        ) {
            Text(
                text = if (isOver) "¡Soltar para cerrar!" else "Arrastra aquí para cerrar",
                color = if (isOver) Color(0xFFFCA5A5) else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
