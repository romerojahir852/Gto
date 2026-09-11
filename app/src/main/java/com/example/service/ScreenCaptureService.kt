package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    private val binder = LocalBinder()

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val CHANNEL_ID = "poker_gto_screen_capture_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_CAPTURE = "com.example.ACTION_CAPTURE"
        const val ACTION_SHOW_OVERLAY = "com.example.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.ACTION_HIDE_OVERLAY"
        const val ACTION_TRIGGER_OVERLAY_ANALYSIS = "com.example.ACTION_TRIGGER_OVERLAY_ANALYSIS"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        // Reactive state flow for service running
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        // Shared flow for captured frames
        private val _capturedBitmapFlow = MutableSharedFlow<Bitmap>(extraBufferCapacity = 1)
        val capturedBitmapFlow = _capturedBitmapFlow.asSharedFlow()

        // Static instance reference for convenient UI binding
        private var instance: ScreenCaptureService? = null

        fun requestScreenCapture() {
            instance?.captureSingleFrame()
        }

        fun showFloatingOverlay() {
            instance?.showOverlay()
        }

        fun hideFloatingOverlay() {
            instance?.hideOverlay()
        }

        fun triggerFloatingAnalysis() {
            instance?.triggerFloatingAnalysis()
        }

        fun updateOverlayResult(result: com.example.data.PokerAnalysisResult) {
            instance?.updateOverlayResult(result)
        }
    }

    private var overlayManager: FloatingOverlayManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenDensity: Int = 0
    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920

    @Volatile
    private var lastCapturedBitmap: Bitmap? = null

    @Volatile
    private var pendingFrameDeferred: CompletableDeferred<Bitmap>? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.data.ApiKeyManager.init(this)
        createNotificationChannel()

        overlayManager = FloatingOverlayManager(this).apply {
            frameProvider = { captureFreshFrame() }
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            screenHeight = metrics.bounds.height()
            screenDensity = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            screenDensity = displayMetrics.densityDpi
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                startForegroundServiceWithNotification()

                // If user has granted overlay permission, display the floating button right away
                if (overlayManager?.canDrawOverlays() == true) {
                    overlayManager?.showOverlay()
                }

                if (resultData != null && resultCode != 0) {
                    val projectionManager =
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    initMediaProjection(projectionManager, resultCode, resultData)
                }
            }

            ACTION_SHOW_OVERLAY -> {
                showOverlay()
            }

            ACTION_HIDE_OVERLAY -> {
                hideOverlay()
            }

            ACTION_TRIGGER_OVERLAY_ANALYSIS -> {
                triggerOverlayAnalysis()
            }

            ACTION_CAPTURE -> {
                captureSingleFrame()
            }

            ACTION_STOP -> {
                stopSelfService()
            }
        }

        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notification = buildPersistentNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isServiceRunning.value = true
        Log.d(TAG, "ScreenCaptureService started in foreground")
    }

    private fun initMediaProjection(
        projectionManager: MediaProjectionManager,
        resultCode: Int,
        resultData: Intent
    ) {
        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            // Register callback required in Android 14+ (API 34)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d(TAG, "MediaProjection session stopped by system")
                    stopSelfService()
                }
            }, handler)

            setupImageReader()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaProjection", e)
        }
    }

    private fun setupImageReader() {
        // High-resolution native screen capture for maximum mathematical clarity before cropping
        val captureWidth = screenWidth.coerceAtLeast(720)
        val captureHeight = screenHeight.coerceAtLeast(1280)

        imageReader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                var img: Image? = null
                try {
                    img = reader.acquireLatestImage()
                    if (img != null) {
                        val bmp = imageToBitmap(img)
                        if (bmp != null) {
                            lastCapturedBitmap = bmp
                            val waiter = pendingFrameDeferred
                            if (waiter != null && !waiter.isCompleted) {
                                pendingFrameDeferred = null
                                waiter.complete(bmp)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient acquisition hiccups
                } finally {
                    img?.close()
                }
            }, handler)
        }

        val mp = mediaProjection ?: return

        virtualDisplay = mp.createVirtualDisplay(
            "PokerGtoDisplay",
            captureWidth,
            captureHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        Log.d(TAG, "VirtualDisplay initialized: ${captureWidth}x${captureHeight}")
    }

    /**
     * Captures a single frame exclusively when requested by the user,
     * maintaining high performance and zero unnecessary background rendering.
     */
    fun captureSingleFrame() {
        val reader = imageReader
        if (reader == null) {
            Log.w(TAG, "ImageReader not ready for capture")
            return
        }

        handler.postDelayed({
            var image: Image? = null
            try {
                image = reader.acquireLatestImage() ?: reader.acquireNextImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    if (bitmap != null) {
                        lastCapturedBitmap = bitmap
                        _capturedBitmapFlow.tryEmit(bitmap)
                        Log.d(TAG, "Successfully captured single frame: ${bitmap.width}x${bitmap.height}")
                    }
                } else {
                    Log.w(TAG, "No frame available in ImageReader")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring frame from ImageReader", e)
            } finally {
                image?.close()
            }
        }, 150) // Small delay to ensure virtual display buffer is rendered
    }

    /**
     * Captura garantizada de frame 100% fresco directamente desde MediaProjection
     * después de haber ocultado el overlay, evitando frames cacheados o congelados.
     */
    suspend fun captureFreshFrame(timeoutMs: Long = 350L): Bitmap? {
        val reader = imageReader ?: return lastCapturedBitmap

        // 1. Si ya hay un frame disponible en el reader tras el delay de ocultamiento, tomarlo de inmediato
        try {
            val directImg = reader.acquireLatestImage()
            if (directImg != null) {
                val bmp = imageToBitmap(directImg)
                directImg.close()
                if (bmp != null) {
                    lastCapturedBitmap = bmp
                    return bmp
                }
            }
        } catch (e: Exception) {
            // Buffer transitorio, pasar a deferred
        }

        // 2. Si no hay frame inmediato, esperar el siguiente render de Android con timeout ágil (350ms)
        val deferred = CompletableDeferred<Bitmap>()
        pendingFrameDeferred = deferred

        return try {
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: lastCapturedBitmap
        } catch (e: Exception) {
            Log.w(TAG, "Timeout esperando frame fresco, usando último buffer disponible", e)
            lastCapturedBitmap
        } finally {
            if (pendingFrameDeferred == deferred) {
                pendingFrameDeferred = null
            }
        }
    }

    fun captureCurrentFrame(): Bitmap? {
        val reader = imageReader ?: return lastCapturedBitmap
        var image: Image? = null
        return try {
            image = reader.acquireLatestImage() ?: reader.acquireNextImage()
            if (image != null) {
                val bmp = imageToBitmap(image)
                if (bmp != null) {
                    lastCapturedBitmap = bmp
                }
                bmp ?: lastCapturedBitmap
            } else {
                lastCapturedBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring current frame from ImageReader", e)
            lastCapturedBitmap
        } finally {
            image?.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop padding if necessary
        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        } else {
            bitmap
        }
    }

    private fun buildPersistentNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_CAPTURE
        }
        val capturePendingIntent = PendingIntent.getService(
            this,
            1,
            captureIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Poker GTO Vision Activo")
            .setContentText("Listo para capturar jugadas de Texas Hold'em en tiempo real")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Capturar Mano", capturePendingIntent)
            .addAction(0, "Detener", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Captura Poker GTO",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente para capturar pantalla y enviar fotogramas a Gemini"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun showOverlay() {
        handler.post {
            overlayManager?.showOverlay()
        }
    }

    fun hideOverlay() {
        handler.post {
            overlayManager?.hideOverlay()
        }
    }

    fun triggerOverlayAnalysis() {
        handler.post {
            overlayManager?.analizarPantalla()
        }
    }

    fun triggerFloatingAnalysis() {
        triggerOverlayAnalysis()
    }

    fun updateOverlayResult(result: com.example.data.PokerAnalysisResult) {
        handler.post {
            overlayManager?.updateWithAnalysisResult(result)
        }
    }

    private fun stopSelfService() {
        _isServiceRunning.value = false

        try {
            overlayManager?.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up overlayManager", e)
        } finally {
            overlayManager = null
        }

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing virtualDisplay", e)
        } finally {
            virtualDisplay = null
        }

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing imageReader", e)
        } finally {
            imageReader = null
        }

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mediaProjection", e)
        } finally {
            mediaProjection = null
        }

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground service", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopSelfService()
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }
}
