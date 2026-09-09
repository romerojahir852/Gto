package com.example.ui

import com.example.ui.theme.BgWhite
import com.example.ui.theme.BgSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.Ink2
import com.example.ui.theme.Ink3
import com.example.ui.theme.Line
import com.example.ui.theme.Line2
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AppThemeManager
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BettingUnit
import com.example.data.GTOStateManager
import com.example.data.GeminiPokerRepository
import com.example.data.PokerAnalysisResult
import com.example.data.PokerGameStateManager
import com.example.data.Street
import com.example.service.ScreenCaptureService
import com.example.ui.components.PRESET_HANDS
import com.example.ui.components.PokerCardBadge
import com.example.ui.components.PokerHandPreset
import com.example.ui.components.BackgroundGeometry
import com.example.ui.components.BottomActionBar
import com.example.ui.components.PokerHudOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokerScreen(
    viewModel: PokerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isAnalyzing = uiState is AnalysisUiState.Analyzing || uiState is AnalysisUiState.Capturing
    val selectedStreet by viewModel.selectedStreet.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    val history by viewModel.history.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val currentPreviewBitmap by viewModel.currentPreviewBitmap.collectAsState()
    val handState by PokerGameStateManager.handState.collectAsState()

    // Control de Pestañas de Navegación Móvil (0: En Vivo, 1: Mesa GTO, 2: Ajustes)
    var selectedTab by remember { mutableStateOf(0) }

    // Estados de Modales Informativos Interactivos
    var showOverlayInfoModal by remember { mutableStateOf(false) }
    var showStreetInfoModal by remember { mutableStateOf(false) }
    var showTableInfoModal by remember { mutableStateOf(false) }
    var showGlossaryModal by remember { mutableStateOf(false) }
    var showQuickGuideModal by remember { mutableStateOf(false) }

    var overlayCheckCounter by remember { mutableStateOf(0) }
    val canDrawOverlays = remember(overlayCheckCounter, isServiceRunning) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        overlayCheckCounter++
    }

    // ActivityResultLauncher para consentimiento de captura de pantalla
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            ScreenCaptureService.showFloatingOverlay()
        }
    }

    val isDarkTheme by AppThemeManager.isDark.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.textPrimary)
                                .border(1.5.dp, AppTheme.colors.accentGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "♠",
                                color = AppTheme.colors.accentGold,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = "POKER GTO",
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { AppThemeManager.toggleTheme(context) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Cambiar Tema",
                                tint = AppTheme.colors.accentGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }



                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isServiceRunning) Color(0xFFECFDF5) else AppTheme.colors.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isServiceRunning) AppTheme.colors.accentGreen else AppTheme.colors.borderSubtle
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isServiceRunning) AppTheme.colors.accentGreen else AppTheme.colors.textMuted)
                                )
                                Text(
                                    text = if (isServiceRunning) "En Vivo" else "Pausado",
                                    color = if (isServiceRunning) AppTheme.colors.accentGreen else AppTheme.colors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.background.copy(alpha = 0.98f)
                )
            )
        },
        bottomBar = {
            // Barra de Navegación Nativa Móvil con Estética Poker Casino
            NavigationBar(
                containerColor = AppTheme.colors.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.border(1.dp, AppTheme.colors.borderSubtle)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "En Vivo",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "En Vivo",
                            fontWeight = if (selectedTab == 0) FontWeight.Black else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppTheme.colors.accentGold,
                        selectedTextColor = AppTheme.colors.textPrimary,
                        indicatorColor = AppTheme.colors.textPrimary,
                        unselectedIconColor = AppTheme.colors.textMuted,
                        unselectedTextColor = AppTheme.colors.textMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = "Mesa GTO",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Mesa GTO",
                            fontWeight = if (selectedTab == 1) FontWeight.Black else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppTheme.colors.accentGold,
                        selectedTextColor = AppTheme.colors.textPrimary,
                        indicatorColor = AppTheme.colors.textPrimary,
                        unselectedIconColor = AppTheme.colors.textMuted,
                        unselectedTextColor = AppTheme.colors.textMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Ajustes",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Ajustes",
                            fontWeight = if (selectedTab == 2) FontWeight.Black else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppTheme.colors.accentGold,
                        selectedTextColor = AppTheme.colors.textPrimary,
                        indicatorColor = AppTheme.colors.textPrimary,
                        unselectedIconColor = AppTheme.colors.textMuted,
                        unselectedTextColor = AppTheme.colors.textMuted
                    )
                )
            }
        },
        containerColor = AppTheme.colors.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            BackgroundGeometry(modifier = Modifier.fillMaxSize())

            when (selectedTab) {
                0 -> {
                    // PESTAÑA 0: EN VIVO (Mesa en Vivo & HUD)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        // Tarjeta Principal de la Burbuja Flotante
                        item {
                            LiveFloatingHeroCard(
                                canDrawOverlays = canDrawOverlays,
                                isServiceRunning = isServiceRunning,
                                onRequestOverlayPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        overlayPermissionLauncher.launch(intent)
                                    }
                                },
                                onLaunchOverlay = {
                                    if (!canDrawOverlays) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            overlayPermissionLauncher.launch(intent)
                                        }
                                    } else if (isServiceRunning) {
                                        ScreenCaptureService.showFloatingOverlay()
                                    } else {
                                        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                        mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                                    }
                                },
                                onStopService = {
                                    val intent = Intent(context, ScreenCaptureService::class.java).apply {
                                        action = ScreenCaptureService.ACTION_STOP
                                    }
                                    context.startService(intent)
                                },
                                onOpenInfo = { showOverlayInfoModal = true }
                            )
                        }

                        // Selector de Ronda / Fase de la Mano
                        item {
                            StreetModeSelector(
                                currentStreet = selectedStreet,
                                onStreetSelected = { viewModel.setStreet(it) },
                                onOpenInfo = { showStreetInfoModal = true }
                            )
                        }

                        // Resultado GTO en Tiempo Real
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "JUGADA RECOMENDADA GTO",
                                        color = AppTheme.colors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    if (latestResult != null) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AppTheme.colors.accentGoldBg,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.accentGold)
                                        ) {
                                            Text(
                                                text = latestResult?.street?.displayName?.uppercase() ?: "EN VIVO",
                                                color = AppTheme.colors.accentGold,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                PokerHudOverlay(
                                    result = latestResult,
                                    isAnalyzing = isAnalyzing
                                )
                            }
                        }

                        // Botón de Captura y Análisis
                        item {
                            ActionControlCard(
                                isServiceRunning = isServiceRunning,
                                isAnalyzing = isAnalyzing,
                                onStartCapture = {
                                    val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                                },
                                onStopCapture = {
                                    val intent = Intent(context, ScreenCaptureService::class.java).apply {
                                        action = ScreenCaptureService.ACTION_STOP
                                    }
                                    context.startService(intent)
                                },
                                onAnalyzeNow = {
                                    if (isServiceRunning) {
                                        viewModel.triggerScreenCapture()
                                    } else {
                                        viewModel.analyzeCurrentPreset()
                                    }
                                }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }

                1 -> {
                    // PESTAÑA 1: MESA GTO (Posiciones, Ciegas & Simulador)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        // Posición, Jugadores, Dealer y Unidad
                        item {
                            BettingUnitSelectorCard(
                                currentUnit = handState.bettingUnit,
                                jugadores = handState.jugadores,
                                posicion = handState.posicion,
                                dealerPosition = handState.dealerPosition,
                                tablePositionsSummary = handState.tablePositionsSummary,
                                onUnitSelected = { PokerGameStateManager.setBettingUnit(it) },
                                boteDisplay = handState.displayBote,
                                apuestaDisplay = handState.displayApuestaRival,
                                onOpenInfo = { showTableInfoModal = true }
                            )
                        }

                        // Simulador Interactivo de Manos
                        item {
                            SimulatorSection(
                                selectedPreset = selectedPreset,
                                onPresetSelected = { viewModel.selectPreset(it) },
                                previewBitmap = currentPreviewBitmap ?: selectedPreset.renderBitmap()
                            )
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }

                2 -> {
                    // PESTAÑA 2: AJUSTES & AYUDA
                    SettingsTabContent(
                        context = context,
                        canDrawOverlays = canDrawOverlays,
                        isDarkTheme = isDarkTheme,
                        history = history,
                        onClearHistory = { viewModel.clearHistory() },
                        onRequestOverlayPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                overlayPermissionLauncher.launch(intent)
                            }
                        },
                        onOpenGlossaryModal = { showGlossaryModal = true },
                        onOpenQuickGuideModal = { showQuickGuideModal = true },
                        onToggleTheme = { AppThemeManager.toggleTheme(context) }
                    )
                }
            }
        }
    }

    // MODALES INFORMATIVOS INTERACTIVOS (Sin textos gigantes en pantalla)
    if (showOverlayInfoModal) {
        CasinoInfoModal(
            title = "Burbuja Flotante",
            icon = "♠",
            bullets = listOf(
                Pair("Flota sobre tu juego", "Abre tu sala de poker (GG Poker, PokerStars, etc.) y la ficha flotará discretamente sin tapar las cartas ni apuestas."),
                Pair("Toca para ver jugada", "Pulsa la ficha para ver la recomendación GTO óptima. Vuelve a tocarla para minimizarla a un punto discreto."),
                Pair("Arrastra al basurero para cerrar", "Mantén pulsada la burbuja y arrástrala hacia el fondo de la pantalla para cerrarla instantáneamente."),
                Pair("Lectura en tiempo real", "Presiona Capturar para evaluar tu mano al momento con cálculo de Outs y probabilidad de victoria.")
            ),
            onDismiss = { showOverlayInfoModal = false }
        )
    }

    if (showStreetInfoModal) {
        CasinoInfoModal(
            title = "Fases de la Mano",
            icon = "🎯",
            bullets = listOf(
                Pair("Preflop (Equity Inicial)", "Evalúa tus 2 cartas de mano según tu posición en la mesa, recomendando si debes Subir (Raise), Pagar (Call) o Retirarte (Fold)."),
                Pair("Postflop (Outs & Win %)", "Lee las cartas comunitarias (Flop/Turn). Calcula cuántas cartas te salvan (Outs) y tu probabilidad matemática exacta de ganar."),
                Pair("Acción Rápida (< 1s)", "Modo de decisión ultrarrápido para actuar de inmediato cuando el reloj de la mesa te presione.")
            ),
            onDismiss = { showStreetInfoModal = false }
        )
    }

    if (showTableInfoModal) {
        CasinoInfoModal(
            title = "Mesa y Posiciones GTO",
            icon = "🎲",
            bullets = listOf(
                Pair("Ciegas (BB) vs Dinero ($)", "Los profesionales usan Ciegas Grandes (BB) para medir el bote de forma exacta independientemente del nivel de apuesta."),
                Pair("Botón Dealer (D / BTN)", "Es la posición más rentable del poker, ya que eres el último en actuar en cada ronda de apuestas postflop."),
                Pair("Tu Posición (Hero)", "En primeras posiciones (UTG) debes jugar rangos de cartas muy fuertes. En el Botón (BTN) o Ciegas (SB/BB) puedes jugar más agresivo.")
            ),
            onDismiss = { showTableInfoModal = false }
        )
    }

    if (showGlossaryModal) {
        CasinoInfoModal(
            title = "Glosario Poker GTO",
            icon = "📖",
            bullets = listOf(
                Pair("GTO (Game Theory Optimal)", "Estrategia matemática inexploitable que maximiza tus ganancias a largo plazo minimizando tus errores."),
                Pair("Outs", "Número de cartas en la baraja que mejoran tu mano para ganar el bote (ej. 9 cartas para completar un color)."),
                Pair("Equity (% de Victoria)", "La probabilidad estadística exacta de ganar la mano al llegar al Showdown."),
                Pair("Pot Odds", "Relación matemática entre el costo de pagar y el tamaño del bote para saber si es rentable ver la jugada.")
            ),
            onDismiss = { showGlossaryModal = false }
        )
    }

    if (showQuickGuideModal) {
        CasinoInfoModal(
            title = "Guía Rápida de Uso",
            icon = "⚡",
            bullets = listOf(
                Pair("1. Activa el Asistente", "En la pestaña 'En Vivo', pulsa 'ACTIVAR ASISTENTE FLOTANTE'."),
                Pair("2. Abre tu Sala de Poker", "Ingresa a tu mesa habitual. Verás la ficha de casino flotando sobre la pantalla."),
                Pair("3. Toca en tu Turno", "Toca la ficha cuando sea tu turno para recibir la acción matemática recomendada."),
                Pair("4. Arrastra para Cerrar", "Cuando termines tu sesión, arrastra la ficha hacia el centro inferior para cerrarla.")
            ),
            onDismiss = { showQuickGuideModal = false }
        )
    }
}

/**
 * Modal Informativo Elegante estilo Casino Royal (White, Gold & Obsidian)
 */
@Composable
fun CasinoInfoModal(
    title: String,
    icon: String = "♠",
    bullets: List<Pair<String, String>>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
            border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = icon,
                            fontSize = 20.sp,
                            color = AppTheme.colors.accentGold
                        )
                        Text(
                            text = title,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = AppTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bullets.forEach { (header, desc) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AppTheme.colors.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = header,
                                    color = AppTheme.colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = desc,
                                    color = AppTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.accentSelected,
                        contentColor = AppTheme.colors.accentSelectedText
                    ),
                    border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = "ENTENDIDO",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp,
                        color = AppTheme.colors.accentSelectedText
                    )
                }
            }
        }
    }
}

/**
 * Hero Card del Asistente Flotante en pestaña En Vivo
 */
@Composable
fun LiveFloatingHeroCard(
    canDrawOverlays: Boolean,
    isServiceRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onLaunchOverlay: () -> Unit,
    onStopService: () -> Unit,
    onOpenInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = AppTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.textPrimary)
                            .border(1.dp, AppTheme.colors.accentGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("♠", color = AppTheme.colors.accentGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                    Column {
                        Text(
                            text = "Asistente Flotante",
                            color = AppTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isServiceRunning) "Activo sobre la mesa" else "En pausa • Toca para iniciar",
                            color = if (isServiceRunning) AppTheme.colors.accentGreen else AppTheme.colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onOpenInfo,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Información sobre la burbuja",
                        tint = AppTheme.colors.accentGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (!canDrawOverlays) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF2F2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permiso Requerido",
                                color = Color(0xFFDC2626),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Permite a la app flotar sobre tus juegos",
                                color = Color(0xFF7F1D1D),
                                fontSize = 10.sp
                            )
                        }
                        Button(
                            onClick = onRequestOverlayPermission,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("request_overlay_permission_button")
                        ) {
                            Text("Permitir", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                if (isServiceRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onLaunchOverlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.accentSelected,
                                contentColor = AppTheme.colors.accentSelectedText
                            ),
                            border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("launch_floating_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = AppTheme.colors.accentGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VER BURBUJA",
                                color = AppTheme.colors.accentSelectedText,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onStopService,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444)),
                            modifier = Modifier
                                .weight(0.7f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DETENER",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onLaunchOverlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.accentSelected,
                            contentColor = AppTheme.colors.accentSelectedText
                        ),
                        border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("launch_floating_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = AppTheme.colors.accentGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ACTIVAR ASISTENTE FLOTANTE",
                            color = AppTheme.colors.accentSelectedText,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Contenido de la pestaña 2: AJUSTES & AYUDA
 */
@Composable
fun SettingsTabContent(
    context: Context,
    canDrawOverlays: Boolean,
    isDarkTheme: Boolean,
    history: List<PokerAnalysisResult>,
    onClearHistory: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenGlossaryModal: () -> Unit,
    onOpenQuickGuideModal: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Text(
                text = "AJUSTES Y AYUDA",
                color = AppTheme.colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Opción 2: Permisos de Superposición
        item {
            SettingRowCard(
                icon = Icons.Default.Layers,
                iconTint = if (canDrawOverlays) AppTheme.colors.accentGreen else Color(0xFFEF4444),
                title = "Permiso de Superposición",
                subtitle = if (canDrawOverlays) "Permitido • Puede flotar sobre apps de poker" else "Pendiente • Toca para conceder permiso",
                actionText = if (canDrawOverlays) "ACTIVO" else "PERMITIR",
                onClick = { if (!canDrawOverlays) onRequestOverlayPermission() }
            )
        }

        // Opción 3: Glosario de Poker GTO
        item {
            SettingRowCard(
                icon = Icons.Default.Casino,
                iconTint = AppTheme.colors.accentGold,
                title = "Glosario Poker GTO",
                subtitle = "Outs, Probabilidades (Equity), Pot Odds y Posiciones",
                actionText = "VER",
                onClick = onOpenGlossaryModal
            )
        }

        // Opción 4: Guía de la Burbuja
        item {
            SettingRowCard(
                icon = Icons.Default.Info,
                iconTint = AppTheme.colors.accentGold,
                title = "Guía del Asistente Flotante",
                subtitle = "Toque para ver jugada, gestos y arrastrar para cerrar",
                actionText = "VER",
                onClick = onOpenQuickGuideModal
            )
        }

        // Opción 5: Tema Visual
        item {
            SettingRowCard(
                icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                iconTint = AppTheme.colors.accentGold,
                title = "Estilo Visual",
                subtitle = if (isDarkTheme) "Modo Noche Obsidiana activo" else "Modo Blanco y Oro Real activo",
                actionText = "CAMBIAR",
                onClick = onToggleTheme
            )
        }

        // Opción 6: Historial de Manos
        if (history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HISTORIAL DE MANOS (${history.size})",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Limpiar historial",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            items(history) { item ->
                HistoryItemCard(result = item)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun SettingRowCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    actionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = AppTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = AppTheme.colors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AppTheme.colors.surfaceMuted,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle)
            ) {
                Text(
                    text = actionText,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StreetModeSelector(
    currentStreet: Street,
    onStreetSelected: (Street) -> Unit,
    onOpenInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = AppTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FASE DE LA MANO",
                    color = AppTheme.colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = onOpenInfo,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Ayuda Fases",
                        tint = AppTheme.colors.accentGold,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Street.entries.forEach { street ->
                    val isSelected = currentStreet == street
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onStreetSelected(street) }
                            .testTag("street_${street.name.lowercase()}_tab"),
                        color = if (isSelected) AppTheme.colors.accentSelected else AppTheme.colors.surfaceMuted,
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSelected) AppTheme.colors.borderWidth else 1.dp,
                            if (isSelected) AppTheme.colors.border else AppTheme.colors.borderSubtle
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = street.displayName,
                                color = if (isSelected) AppTheme.colors.accentSelectedText else AppTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (street) {
                                    Street.PREFLOP -> "Equity Inicial"
                                    Street.POSTFLOP -> "Outs & Win %"
                                    Street.FAST_GTO -> "< 1s Directo"
                                },
                                color = if (isSelected) AppTheme.colors.accentSelectedText.copy(alpha = 0.85f) else AppTheme.colors.textSecondary,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BettingUnitSelectorCard(
    currentUnit: BettingUnit,
    jugadores: Int = 6,
    posicion: String = "BTN",
    dealerPosition: String = "BTN",
    tablePositionsSummary: String = "BTN (Dealer) • SB • BB • UTG • MP • CO",
    onUnitSelected: (BettingUnit) -> Unit,
    boteDisplay: String,
    apuestaDisplay: String,
    onOpenInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = AppTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ESTADO Y MEMORIA GTO",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onOpenInfo,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Ayuda Mesa",
                            tint = AppTheme.colors.accentGold,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppTheme.colors.surfaceMuted,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle)
                ) {
                    Text(
                        text = if (currentUnit == BettingUnit.BB) "MODO CIEGAS (BB)" else "MODO DINERO ($)",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // GTO Controls: Jugadores Activos (+ / -), Botón Dealer y Posición
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AppTheme.colors.surfaceMuted,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Jugadores Activos:",
                                color = AppTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Detección visual automática",
                                color = AppTheme.colors.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = AppTheme.colors.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("dashboard_btn_dec_players")
                                    .clickable { GTOStateManager.decrementPlayers() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Restar jugador",
                                        tint = AppTheme.colors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppTheme.colors.surface,
                                border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border)
                            ) {
                                Text(
                                    text = "$jugadores",
                                    color = AppTheme.colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = AppTheme.colors.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("dashboard_btn_inc_players")
                                    .clickable { GTOStateManager.incrementPlayers() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Sumar jugador",
                                        tint = AppTheme.colors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Dealer Button Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFD700)
                            ) {
                                Text(
                                    text = "D",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "Botón Dealer:",
                                color = AppTheme.colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("BTN", "CO", "MP", "UTG", "BB", "SB").forEach { dPos ->
                                val isDealer = dealerPosition.equals(dPos, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isDealer) Color(0xFFFFD700) else AppTheme.colors.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isDealer) AppTheme.colors.border else AppTheme.colors.borderSubtle
                                    ),
                                    modifier = Modifier
                                        .testTag("dashboard_dealer_$dPos")
                                        .clickable { GTOStateManager.setDealerPosition(dPos) }
                                ) {
                                    Text(
                                        text = dPos,
                                        color = if (isDealer) Color.Black else AppTheme.colors.textSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = if (isDealer) FontWeight.Black else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Posición del Jugador (Hero)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mi Posición:",
                            color = AppTheme.colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("UTG", "MP", "CO", "BTN", "SB", "BB").forEach { pos ->
                                val isSelected = posicion.equals(pos, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) AppTheme.colors.accentSelected else AppTheme.colors.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) AppTheme.colors.border else AppTheme.colors.borderSubtle
                                    ),
                                    modifier = Modifier
                                        .testTag("dashboard_pos_$pos")
                                        .clickable { GTOStateManager.setPosition(pos) }
                                ) {
                                    Text(
                                        text = pos,
                                        color = if (isSelected) AppTheme.colors.accentSelectedText else AppTheme.colors.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Orden de la Mesa / Posiciones activas calculadas
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppTheme.colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Mesa: $tablePositionsSummary",
                            color = AppTheme.colors.textSecondary,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = "Unidad de Apuestas de la Mesa:",
                color = AppTheme.colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Opción 1: Ciegas (BB)
                val isBb = currentUnit == BettingUnit.BB
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onUnitSelected(BettingUnit.BB) }
                        .testTag("unit_selector_bb"),
                    color = if (isBb) AppTheme.colors.accentSelected else AppTheme.colors.surfaceMuted,
                    border = androidx.compose.foundation.BorderStroke(
                        AppTheme.colors.borderWidth,
                        if (isBb) AppTheme.colors.border else AppTheme.colors.borderSubtle
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Ciegas (BB)",
                            color = if (isBb) AppTheme.colors.accentSelectedText else AppTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ej. 150 BB / 25 BB",
                            color = if (isBb) AppTheme.colors.accentSelectedText.copy(alpha = 0.85f) else AppTheme.colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                // Opción 2: Fichas / Cash ($)
                val isChips = currentUnit == BettingUnit.CHIPS
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onUnitSelected(BettingUnit.CHIPS) }
                        .testTag("unit_selector_chips"),
                    color = if (isChips) AppTheme.colors.accentSelected else AppTheme.colors.surfaceMuted,
                    border = androidx.compose.foundation.BorderStroke(
                        AppTheme.colors.borderWidth,
                        if (isChips) AppTheme.colors.border else AppTheme.colors.borderSubtle
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Fichas / Cash ($)",
                            color = if (isChips) AppTheme.colors.accentSelectedText else AppTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ej. $1,500 / $250",
                            color = if (isChips) AppTheme.colors.accentSelectedText.copy(alpha = 0.85f) else AppTheme.colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Vista en tiempo real de Bote y Rival
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AppTheme.colors.surfaceMuted,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Valores detectados en mesa:",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Bote: $boteDisplay  |  Rival: $apuestaDisplay",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ActionControlCard(
    isServiceRunning: Boolean,
    isAnalyzing: Boolean,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onAnalyzeNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = AppTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Main Big Action: Instant Analysis
            Button(
                onClick = onAnalyzeNow,
                enabled = !isAnalyzing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.accentSelected,
                    contentColor = AppTheme.colors.accentSelectedText
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("analyze_now_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = AppTheme.colors.accentSelectedText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAnalyzing) "Analizando mesa de juego..." else "Capturar y Evaluar Mesa",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = AppTheme.colors.accentSelectedText
                )
            }

            // Secondary row: Foreground Service Start / Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (isServiceRunning) onStopCapture() else onStartCapture()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isServiceRunning) Color(0xFFEF4444) else AppTheme.colors.textPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        AppTheme.colors.borderWidth,
                        if (isServiceRunning) Color(0xFFEF4444) else AppTheme.colors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(if (isServiceRunning) "stop_capture_button" else "start_capture_button")
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isServiceRunning) Color(0xFFEF4444) else AppTheme.colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "Detener Servicio de Captura" else "Iniciar Captura de Pantalla Real",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceRunning) Color(0xFFEF4444) else AppTheme.colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun SimulatorSection(
    selectedPreset: PokerHandPreset,
    onPresetSelected: (PokerHandPreset) -> Unit,
    previewBitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = AppTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = null,
                        tint = Color(0xFFFFB800),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Mano de Prueba / Simulador",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Prueba Directa en Emulador",
                    color = AppTheme.colors.textSecondary,
                    fontSize = 11.sp
                )
            }

            // Horizontal Carousel of preset poker scenarios
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PRESET_HANDS) { preset ->
                    val isSelected = selectedPreset.name == preset.name
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPresetSelected(preset) }
                            .testTag("preset_${preset.name.replace(" ", "_")}"),
                        color = if (isSelected) AppTheme.colors.accentSelected else AppTheme.colors.surfaceMuted,
                        border = androidx.compose.foundation.BorderStroke(
                            AppTheme.colors.borderWidth,
                            if (isSelected) AppTheme.colors.border else AppTheme.colors.borderSubtle
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = preset.name,
                                color = if (isSelected) AppTheme.colors.accentSelectedText else AppTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = preset.description,
                                color = if (isSelected) AppTheme.colors.accentSelectedText.copy(alpha = 0.8f) else AppTheme.colors.textSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Table snapshot preview
            if (previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(AppTheme.colors.borderWidth, AppTheme.colors.border, RoundedCornerShape(10.dp))
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Fotograma capturado",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    result: PokerAnalysisResult,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = AppTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = result.street.displayName,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• ${result.latencyMs} ms",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.holeCards.forEach { card ->
                        PokerCardBadge(card = card)
                    }
                    if (result.communityCards.isNotEmpty()) {
                        Text(
                            text = "|",
                            color = AppTheme.colors.borderSubtle,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        result.communityCards.forEach { card ->
                            PokerCardBadge(card = card)
                        }
                    }
                }

                if (!result.drawText.isNullOrBlank() || !result.outsDetail.isNullOrBlank()) {
                    Text(
                        text = listOfNotNull(result.drawText, result.outsDetail).joinToString(" • "),
                        color = AppTheme.colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(result.gtoAction.bgTint)
                    .border(1.dp, result.gtoAction.color, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = result.gtoAction.title,
                    color = result.gtoAction.color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/**
 * Overlay Control Card providing:
 * - Permission verification for SYSTEM_ALERT_WINDOW
 * - Button to launch floating overlay above external poker apps
 * - Button to simulate 2s analysis with realistic Texas Hold'em scenarios
 */
@Composable
private fun FloatingOverlayControlCard(
    canDrawOverlays: Boolean,
    isServiceRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onLaunchOverlay: () -> Unit,
    onStopService: () -> Unit,
    onTriggerSimulation: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
        border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = AppTheme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Superposición Flotante (Floating UI)",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (canDrawOverlays) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (canDrawOverlays) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (canDrawOverlays) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (canDrawOverlays) Color(0xFF047857) else Color(0xFFDC2626),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (canDrawOverlays) "Permiso Activo" else "Permiso Requerido",
                            color = if (canDrawOverlays) Color(0xFF047857) else Color(0xFFDC2626),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "Muestra el botón flotante arrastrable y el panel GTO sobre cualquier mesa de póker externa (BC Poker, GG Poker, etc.).",
                color = AppTheme.colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            if (!canDrawOverlays) {
                Button(
                    onClick = onRequestOverlayPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.accentSelected,
                        contentColor = AppTheme.colors.accentSelectedText
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("request_overlay_permission_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = AppTheme.colors.accentSelectedText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Conceder Permiso SYSTEM_ALERT_WINDOW",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AppTheme.colors.accentSelectedText
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isServiceRunning) {
                        Button(
                            onClick = onLaunchOverlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.accentSelected,
                                contentColor = AppTheme.colors.accentSelectedText
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("launch_floating_button")
                        ) {
                            Text(
                                text = "Mostrar Overlay",
                                color = AppTheme.colors.accentSelectedText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onStopService,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Detener",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = onLaunchOverlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.accentSelected,
                                contentColor = AppTheme.colors.accentSelectedText
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("launch_floating_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AppTheme.colors.accentSelectedText, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Iniciar Captura y Overlay Flotante",
                                color = AppTheme.colors.accentSelectedText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Poker Guide Card explaining each feature naturally and clearly to the poker player.
 */
@Composable
private fun PokerGuideCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
        border = androidx.compose.foundation.BorderStroke(AppTheme.colors.borderWidth, AppTheme.colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AppTheme.colors.accentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Guía de Opciones",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = AppTheme.colors.accentGreenBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.accentGreen.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "♠ En Vivo",
                        color = AppTheme.colors.accentGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            val guideItems = listOf(
                Pair("1. Burbuja Flotante", "Flota como una ficha de casino sobre cualquier app de póker. Toca la ficha para ver la recomendación, vuelve a tocarla para minimizarla, o arrástrala hacia abajo para cerrarla."),
                Pair("2. Lectura Automática", "Detecta tus 2 cartas de la mano y las cartas comunitarias de la mesa sin interrumpir tu juego."),
                Pair("3. Posición en la Mesa", "Ajusta con un toque si estás en el Botón (BTN), Ciegas (SB/BB) o primeras posiciones (UTG) para afinar la recomendación matemática."),
                Pair("4. Unidad de Apuesta (BB vs $)", "Alterna entre Ciegas Grandes (BB) para juego profesional o Fichas en dólares ($)."),
                Pair("5. Decisión GTO y Probabilidades", "Te aconseja si debes Pasar (Check), Apostar (Bet), Subir (Raise) o Retirarte (Fold), junto con tus Outs y probabilidad de ganar (Win Equity).")
            )

            guideItems.forEach { (title, desc) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppTheme.colors.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = title,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = desc,
                            color = AppTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}


