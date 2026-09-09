package com.example.ui

import com.example.ui.theme.BgWhite
import com.example.ui.theme.BgSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.Ink2
import com.example.ui.theme.Ink3
import com.example.ui.theme.Line
import com.example.ui.theme.Line2

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
import com.example.data.ApiKeyManager
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
    var showApiKeyModal by remember { mutableStateOf(false) }

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

    // ActivityResultLauncher for MediaProjection screen capture consent
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Ink)
                                .border(1.dp, Line, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "♠",
                                color = BgWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = "POKER GTO VISION",
                            color = Ink,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 0.28.sp
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(
                            onClick = { showApiKeyModal = true },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Configurar Gemini API Key",
                                tint = if (ApiKeyManager.hasApiKey(context)) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isServiceRunning) Color(0xFFECFDF5) else Color(0xFFF3F4F6),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isServiceRunning) Color(0xFFA7F3D0) else Color(0xFFDCDCDC)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isServiceRunning) Color(0xFF10B981) else Ink3)
                                )
                                Text(
                                    text = if (isServiceRunning) "Captura activa" else "Captura inactiva",
                                    color = if (isServiceRunning) Color(0xFF047857) else Ink3,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgWhite.copy(alpha = 0.92f)
                )
            )
        },
        bottomBar = {
            BottomActionBar(
                isServiceRunning = isServiceRunning,
                isAnalyzing = isAnalyzing,
                onAnalyzeNow = {
                    if (isServiceRunning) {
                        viewModel.triggerScreenCapture()
                    } else if (canDrawOverlays) {
                        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayPermissionLauncher.launch(intent)
                        }
                    }
                }
            )
        },
        containerColor = BgWhite,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundGeometry(modifier = Modifier.fillMaxSize())
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    TopApiKeyStatusBanner(
                        isConfigured = ApiKeyManager.isConfigured(context),
                        maskedKey = ApiKeyManager.getMaskedKey(context),
                        onClick = { showApiKeyModal = true }
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.ui.components.Eyebrow(text = "Fase de la mano")
                        Text(
                            text = "Prompt optimizado GTO",
                            color = Ink,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = FontWeight.Light,
                            fontSize = 24.sp,
                            letterSpacing = 0.02.sp
                        )
                        com.example.ui.components.SectionSub(
                            text = "Elige la fase o usa el simulador. El prompt se adapta al contexto para extraer la jugada GTO."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StreetModeSelector(
                            currentStreet = selectedStreet,
                            onStreetSelected = { viewModel.setStreet(it) }
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.ui.components.Eyebrow(text = "Memoria GTO")
                        Text(
                            text = "Estado de la mesa",
                            color = Ink,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = FontWeight.Light,
                            fontSize = 24.sp,
                            letterSpacing = 0.02.sp
                        )
                        com.example.ui.components.SectionSub(
                            text = "Define jugadores, dealer, tu posición y la unidad de apuestas. El prompt se inyecta con este contexto."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BettingUnitSelectorCard(
                            currentUnit = handState.bettingUnit,
                            jugadores = handState.jugadores,
                            posicion = handState.posicion,
                            dealerPosition = handState.dealerPosition,
                            tablePositionsSummary = handState.tablePositionsSummary,
                            onUnitSelected = { PokerGameStateManager.setBettingUnit(it) },
                            boteDisplay = handState.displayBote,
                            apuestaDisplay = handState.displayApuestaRival
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.ui.components.Eyebrow(text = "Resultado en vivo")
                        Text(
                            text = "Jugada GTO recomendada",
                            color = Ink,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = FontWeight.Light,
                            fontSize = 24.sp,
                            letterSpacing = 0.02.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PokerHudOverlay(
                            result = latestResult,
                            isAnalyzing = isAnalyzing
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.ui.components.Eyebrow(text = "Overlay flotante")
                        Text(
                            text = "Nube sobre la mesa",
                            color = Ink,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = FontWeight.Light,
                            fontSize = 24.sp,
                            letterSpacing = 0.02.sp
                        )
                        com.example.ui.components.SectionSub(
                            text = "Permite mostrar el botón flotante y la nube de resultados sobre cualquier app de poker."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FloatingOverlayControlCard(
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
                            onTriggerSimulation = {
                                if (isServiceRunning) {
                                    ScreenCaptureService.triggerFloatingAnalysis()
                                } else {
                                    val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                                }
                            }
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.ui.components.Eyebrow(text = "Motor de Inteligencia")
                        Text(
                            text = "Gemini AI y OCR Local",
                            color = Ink,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = FontWeight.Light,
                            fontSize = 24.sp,
                            letterSpacing = 0.02.sp
                        )
                        com.example.ui.components.SectionSub(
                            text = "Configura tu API Key de Gemini para análisis neuronal de alta fidelidad, o deja que el motor OCR local con ML Kit procese las cartas sin internet."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ApiKeySettingsCard()
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.ui.components.Eyebrow(text = "Pilares del prompt")
                        Text(
                            text = "Arquitectura espacial",
                            color = Ink,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = FontWeight.Light,
                            fontSize = 24.sp,
                            letterSpacing = 0.02.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        UniversalPromptCard()
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.ui.components.Eyebrow(text = "Controles rápidos")
                        Text(
                            text = "Captura y análisis",
                            color = Ink,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = FontWeight.Light,
                            fontSize = 24.sp,
                            letterSpacing = 0.02.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.ui.components.Eyebrow(text = "Simulador")
                        Text(
                            text = "Manos de prueba",
                            color = Ink,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = FontWeight.Light,
                            fontSize = 24.sp,
                            letterSpacing = 0.02.sp
                        )
                        com.example.ui.components.SectionSub(
                            text = "Flush Draw, Gutshot, OESD, Big Slick. Sin capturar pantalla real."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SimulatorSection(
                            selectedPreset = selectedPreset,
                            onPresetSelected = { viewModel.selectPreset(it) },
                            previewBitmap = currentPreviewBitmap ?: selectedPreset.renderBitmap()
                        )
                    }
                }

                if (history.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.example.ui.components.Eyebrow(text = "Historial")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Manos evaluadas",
                                    color = Ink,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 24.sp,
                                    letterSpacing = 0.02.sp
                                )
                                IconButton(
                                    onClick = { viewModel.clearHistory() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Limpiar historial",
                                        tint = Ink3,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    items(history) { item ->
                        HistoryItemCard(result = item)
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showApiKeyModal) {
        ApiKeyConfigDialog(
            onDismiss = { showApiKeyModal = false },
            onKeySaved = { showApiKeyModal = false }
        )
    }
}

@Composable
fun StreetModeSelector(
    currentStreet: Street,
    onStreetSelected: (Street) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = BgSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1D3B2C))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "FASE DE LA MANO (PROMPT OPTIMIZADO GTO)",
                color = Color(0xFF00E676),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

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
                        color = if (isSelected) Color(0xFF00E676) else BgSoft,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF00E676) else Color(0xFF264736)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = street.displayName,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (street) {
                                    Street.PREFLOP -> "Equity Inicial"
                                    Street.POSTFLOP -> "Outs & Win %"
                                    Street.FAST_GTO -> "< 1s Directo"
                                },
                                color = if (isSelected) Color(0xFF1E3A2B) else Ink3,
                                fontSize = 10.sp,
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = BgSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1D3B2C))
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
                Text(
                    text = "ESTADO Y MEMORIA GTO",
                    color = Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (currentUnit == BettingUnit.BB) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFFD700).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (currentUnit == BettingUnit.BB) "MODO CIEGAS" else "MODO DINERO/FICHAS",
                        color = if (currentUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFFFFD700),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // GTO Controls: Jugadores Activos (+ / -), Botón Dealer y Posición
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0C1912),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B3728))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Jugadores Activos:",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Detección visual automática",
                                color = Color(0xFF00E676),
                                fontSize = 9.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = BgSoft,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("dashboard_btn_dec_players")
                                    .clickable { GTOStateManager.decrementPlayers() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Restar jugador",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BgSoft,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
                            ) {
                                Text(
                                    text = "$jugadores",
                                    color = Color(0xFF00E676),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = BgSoft,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("dashboard_btn_inc_players")
                                    .clickable { GTOStateManager.incrementPlayers() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Sumar jugador",
                                        tint = Color(0xFF00E676),
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
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("BTN", "CO", "MP", "UTG", "BB", "SB").forEach { dPos ->
                                val isDealer = dealerPosition.equals(dPos, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isDealer) Color(0xFFFFD700) else Line2,
                                    modifier = Modifier
                                        .testTag("dashboard_dealer_$dPos")
                                        .clickable { GTOStateManager.setDealerPosition(dPos) }
                                ) {
                                    Text(
                                        text = dPos,
                                        color = if (isDealer) Color.Black else Ink3,
                                        fontSize = 9.sp,
                                        fontWeight = if (isDealer) FontWeight.Black else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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
                            color = Ink3,
                            fontSize = 11.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("UTG", "MP", "CO", "BTN", "SB", "BB").forEach { pos ->
                                val isSelected = posicion.equals(pos, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) Color(0xFF00E676) else Color(0xFF142A1E),
                                    modifier = Modifier
                                        .testTag("dashboard_pos_$pos")
                                        .clickable { GTOStateManager.setPosition(pos) }
                                ) {
                                    Text(
                                        text = pos,
                                        color = if (isSelected) Color.Black else Color(0xFFCBD5E1),
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
                        color = Color(0xFF08120D),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Mesa: $tablePositionsSummary",
                            color = Ink3,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = "Unidad de Apuestas de la Mesa:",
                color = Ink3,
                fontSize = 11.sp
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
                    color = if (isBb) Color(0xFF00E676) else BgSoft,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isBb) Color(0xFF00E676) else Color(0xFF264736)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Ciegas (BB)",
                            color = if (isBb) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ej. 150 BB / 25 BB",
                            color = if (isBb) Color(0xFF1E3A2B) else Ink3,
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
                    color = if (isChips) Color(0xFFFFD700) else BgSoft,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isChips) Color(0xFFFFD700) else Color(0xFF264736)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Fichas / Cash ($)",
                            color = if (isChips) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ej. $1,500 / $250",
                            color = if (isChips) Color(0xFF382A0F) else Ink3,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Vista en tiempo real de Bote y Rival
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0A1610)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Valores actuales en Nube:",
                        color = Ink3,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Bote: $boteDisplay  |  Rival: $apuestaDisplay",
                        color = if (currentUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFFFFD700),
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
        color = BgSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1D3B2C))
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
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("analyze_now_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAnalyzing) "Analizando con Gemini..." else "Capturar y Evaluar con Gemini",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
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
                        contentColor = if (isServiceRunning) Color(0xFFEF4444) else Color(0xFF60A5FA)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isServiceRunning) Color(0xFFEF4444) else Color(0xFF3B82F6)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(if (isServiceRunning) "stop_capture_button" else "start_capture_button")
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "Detener Servicio de Captura" else "Iniciar Captura de Pantalla Real",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
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
        color = BgSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1C3A2A))
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
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Mano de Prueba / Simulador",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Prueba Directa en Emulador",
                    color = Ink3,
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
                        color = if (isSelected) Color(0xFF1E4330) else Color(0xFF14271D),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isSelected) Color(0xFF00E676) else Color(0xFF264936)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = preset.name,
                                color = if (isSelected) Color(0xFF00E676) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = preset.description,
                                color = Ink3,
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
                        .border(1.dp, Color(0xFF264936), RoundedCornerShape(10.dp))
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
        color = BgSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A3325))
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
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• ${result.latencyMs} ms",
                        color = Ink3,
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
                            color = Color(0xFF4B5563),
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
                        color = Color(0xFF60A5FA),
                        fontSize = 11.sp
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
        colors = CardDefaults.cardColors(containerColor = BgSoft),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E293B)),
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
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Superposición Flotante (Floating UI)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (canDrawOverlays) Color(0xFF14532D) else Color(0xFF7F1D1D)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (canDrawOverlays) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (canDrawOverlays) Color(0xFF4ADE80) else Color(0xFFF87171),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (canDrawOverlays) "Permiso Activo" else "Permiso Requerido",
                            color = if (canDrawOverlays) Color(0xFF4ADE80) else Color(0xFFF87171),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "Muestra el botón flotante arrastrable y el panel GTO sobre cualquier mesa de póker externa (BC Poker, GG Poker, etc.).",
                color = Ink3,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            if (!canDrawOverlays) {
                Button(
                    onClick = onRequestOverlayPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("request_overlay_permission_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Conceder Permiso SYSTEM_ALERT_WINDOW",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("launch_floating_button")
                        ) {
                            Text(
                                text = "Mostrar Overlay",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onStopService,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("launch_floating_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Iniciar Captura y Overlay Flotante",
                                color = Color.Black,
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
 * Universal Prompt Card displaying the exact Spatial GTO prompt and 4 pillars architecture
 */
@Composable
private fun UniversalPromptCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1F16)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E4330)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Prompt Espacial GTO Vision",
                        color = Color(0xFF00E676),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1B3D2B)
                ) {
                    Text(
                        text = "⚡ Timeout 4s • Zero Crashes",
                        color = Color(0xFFFFD700),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgWhite,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF153322))
            ) {
                val spatialPrompt = "Contexto GTO: Fase[\${state.fase}], JugadoresActivos[\${state.jugadores}], MiPosicion[\${state.posicion}], Bote[\${state.bote}]. Eres un escáner de póker estricto. REGLA 1 (CARTAS PROPIAS): Tus 2 cartas de la mano están SIEMPRE situadas en el cuadro de la PARTE INFERIOR. Selecciónalas como tus cartas propias. REGLA 2 (CARTAS COMUNITARIAS): Las cartas comunitarias (Flop, Turn, River) están alineadas exclusivamente en el CENTRO de la mesa. Responde ÚNICAMENTE con este formato Regex-ready: Cartas:[ValorPalo] | Mesa:[ValorPalo] | Outs:[Numero] | Win:[X]% | GTO:[Acción y Tamaño]. Cero explicaciones."
                Text(
                    text = spatialPrompt,
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp),
                    lineHeight = 15.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tags = listOf("1. Captura Limpia", "2. Memoria GTO", "3. Timeout 4s", "4. Prompt Espacial")
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF12281D)
                    ) {
                        Text(
                            text = tag,
                            color = Color(0xFF86EFAC),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApiKeySettingsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentKey by remember { mutableStateOf(ApiKeyManager.getApiKey(context)) }
    var inputKey by remember { mutableStateOf("") }
    var saveSuccess by remember { mutableStateOf(false) }
    val isConfigured = ApiKeyManager.isConfigured(context)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgSoft),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
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
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = if (isConfigured) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isConfigured) "Gemini Serie 3 Flash Activo" else "OCR Local ML Kit Activo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isConfigured) Color(0xFFECFDF5) else Color(0xFFFEF3C7),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isConfigured) Color(0xFFA7F3D0) else Color(0xFFFDE68A)
                    )
                ) {
                    Text(
                        text = if (isConfigured) "Nube Online" else "Offline / ML Kit",
                        color = if (isConfigured) Color(0xFF047857) else Color(0xFFB45309),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = if (isConfigured) {
                    "Tu clave API está configurada (${ApiKeyManager.getMaskedKey(context)}). La visión multimodal de Gemini Serie 3 Flash (3.8 / 3.7 / 3.6) analizará las capturas con visión multirresolución de alta precisión."
                } else {
                    "Sin API Key configurada. El motor OCR Local integrado (ML Kit) lee las cartas en el dispositivo sin internet. Para activar Gemini AI, ingresa tu clave gratuita de Google AI Studio."
                },
                fontSize = 12.sp,
                color = Ink2,
                lineHeight = 16.sp
            )

            OutlinedTextField(
                value = inputKey,
                onValueChange = {
                    inputKey = it
                    saveSuccess = false
                },
                placeholder = {
                    Text(
                        text = if (isConfigured) "Reemplazar API Key..." else "Pega tu Gemini API Key (AIzaSy...)",
                        fontSize = 12.sp,
                        color = Ink3
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Ink
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Line2,
                    focusedContainerColor = BgWhite,
                    unfocusedContainerColor = BgWhite
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConfigured) {
                    OutlinedButton(
                        onClick = {
                            ApiKeyManager.clearApiKey(context)
                            currentKey = ""
                            inputKey = ""
                            saveSuccess = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Quitar", fontSize = 12.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (inputKey.isNotBlank()) {
                            ApiKeyManager.saveApiKey(context, inputKey)
                            currentKey = inputKey
                            inputKey = ""
                            saveSuccess = true
                        }
                    },
                    enabled = inputKey.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardar Clave", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (saveSuccess) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFECFDF5),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ ¡API Key guardada con éxito! Gemini Flash activado.",
                        color = Color(0xFF047857),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TopApiKeyStatusBanner(
    isConfigured: Boolean,
    maskedKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isConfigured) Color(0xFF0F291E) else Color(0xFF2E200B),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConfigured) Color(0xFF10B981) else Color(0xFFF59E0B)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = if (isConfigured) Color(0xFF10B981) else Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isConfigured) "Gemini Serie 3 Flash Activo" else "Modo OCR Local (Sin API Key)",
                        color = if (isConfigured) Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConfigured) "Clave: $maskedKey • Toca para gestionar" else "Escaneando en dispositivo. Toca para ingresar API Key",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isConfigured) Color(0xFF065F46) else Color(0xFF78350F)
            ) {
                Text(
                    text = if (isConfigured) "GESTIONAR" else "CONFIGURAR",
                    color = if (isConfigured) Color(0xFFA7F3D0) else Color(0xFFFDE68A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ApiKeyConfigDialog(
    onDismiss: () -> Unit,
    onKeySaved: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var inputKey by remember { mutableStateOf(ApiKeyManager.getApiKey(context) ?: "") }
    var saveSuccess by remember { mutableStateOf(false) }
    val isConfigured = ApiKeyManager.isConfigured(context)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
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
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Gemini API Key",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Para activar la visión multimodal de Gemini Serie 3 Flash (3.8 / 3.7 / 3.6), ingresa tu clave gratuita de Google AI Studio. Si no tienes una, la app usará automáticamente el motor OCR Local de ML Kit.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = {
                        inputKey = it
                        saveSuccess = false
                    },
                    placeholder = {
                        Text("AIzaSy...", color = Color(0xFF64748B), fontSize = 12.sp)
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    inputKey = clip.trim()
                                }
                            }
                        ) {
                            Text("Pegar", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                        context.startActivity(intent)
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Obtener API Key Gratis en Google AI Studio",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConfigured) {
                        OutlinedButton(
                            onClick = {
                                ApiKeyManager.clearApiKey(context)
                                inputKey = ""
                                saveSuccess = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Borrar Clave", fontSize = 11.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (inputKey.isNotBlank()) {
                                ApiKeyManager.saveApiKey(context, inputKey)
                                saveSuccess = true
                                onKeySaved()
                            }
                        },
                        enabled = inputKey.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar Clave", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

