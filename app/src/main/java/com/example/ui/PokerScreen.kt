package com.example.ui

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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00E676).copy(alpha = 0.2f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "♠",
                                    color = Color(0xFF00E676),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Poker GTO Vision",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Gemini AI Texas Hold'em Advisor (<2s)",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF374151),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isServiceRunning) Color(0xFF00E676) else Color(0xFF4B5563)
                        ),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isServiceRunning) Color(0xFF00E676) else Color(0xFF9CA3AF))
                            )
                            Text(
                                text = if (isServiceRunning) "CAPTURA ACTIVA" else "STANDBY",
                                color = if (isServiceRunning) Color(0xFF00E676) else Color(0xFF9CA3AF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A140F)
                )
            )
        },
        containerColor = Color(0xFF070D0A),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Street Mode Selection (Preflop, Postflop, GTO Ultra-Rápido)
            item {
                StreetModeSelector(
                    currentStreet = selectedStreet,
                    onStreetSelected = { viewModel.setStreet(it) }
                )
            }

            // 2. Selector de Unidad de Mesa (Ciegas Grandes BB vs Fichas/Cash $) y Memoria GTO
            item {
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

            // 2. Main Live Results Overlay Card (Cards, Equity bar, Outs, GTO Pill)
            item {
                PokerHudOverlay(
                    result = latestResult,
                    isAnalyzing = isAnalyzing
                )
            }

            // 3. Floating Overlay (WindowManager FloatingActionButton + Nube Card)
            item {
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
                        if (isServiceRunning) {
                            ScreenCaptureService.showFloatingOverlay()
                        } else {
                            // Start foreground service with overlay action
                            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                                action = ScreenCaptureService.ACTION_START
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        }
                    },
                    onTriggerSimulation = {
                        if (!isServiceRunning) {
                            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                                action = ScreenCaptureService.ACTION_START
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        }
                        ScreenCaptureService.triggerFloatingAnalysis()
                    }
                )
            }

            // 4. Universal Default Prompt & Architecture Specs
            item {
                UniversalPromptCard()
            }

            // 3. Quick Action Controls (Capture, Service toggle)
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

            // 4. Interactive Poker Scenario Simulator
            item {
                SimulatorSection(
                    selectedPreset = selectedPreset,
                    onPresetSelected = { viewModel.selectPreset(it) },
                    previewBitmap = currentPreviewBitmap ?: selectedPreset.renderBitmap()
                )
            }

            // 5. Hand History Section
            if (history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historial de Manos Evaluadas",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Limpiar historial",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(18.dp)
                            )
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
        color = Color(0xFF102118),
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
                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF172D22),
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
                                color = if (isSelected) Color(0xFF1E3A2B) else Color(0xFF9CA3AF),
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
        color = Color(0xFF102118),
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
                                color = Color(0xFF1A3828),
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
                                color = Color(0xFF142B1F),
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
                                color = Color(0xFF1A3828),
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
                                    color = if (isDealer) Color(0xFFFFD700) else Color(0xFF1C281F),
                                    modifier = Modifier
                                        .testTag("dashboard_dealer_$dPos")
                                        .clickable { GTOStateManager.setDealerPosition(dPos) }
                                ) {
                                    Text(
                                        text = dPos,
                                        color = if (isDealer) Color.Black else Color(0xFF94A3B8),
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
                            color = Color(0xFF94A3B8),
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
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = "Unidad de Apuestas de la Mesa:",
                color = Color(0xFF9CA3AF),
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
                    color = if (isBb) Color(0xFF00E676) else Color(0xFF172D22),
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
                            color = if (isBb) Color(0xFF1E3A2B) else Color(0xFF9CA3AF),
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
                    color = if (isChips) Color(0xFFFFD700) else Color(0xFF172D22),
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
                            color = if (isChips) Color(0xFF382A0F) else Color(0xFF9CA3AF),
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
                        color = Color(0xFF9CA3AF),
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
        color = Color(0xFF102118),
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
        color = Color(0xFF0F1E16),
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
                    color = Color(0xFF9CA3AF),
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
                                color = Color(0xFF9CA3AF),
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
        color = Color(0xFF0E1A14),
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
                        color = Color(0xFF9CA3AF),
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
    onTriggerSimulation: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
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
                text = "Permite mostrar el botón flotante arrastrable y el panel de resultados ('La Nube') sobre cualquier aplicación de poker o mesa en pantalla.",
                color = Color(0xFF94A3B8),
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
                    Button(
                        onClick = onLaunchOverlay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("launch_floating_button")
                    ) {
                        Text(
                            text = "Mostrar Botón Flotante",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onTriggerSimulation,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_floating_simulation_button")
                    ) {
                        Text(
                            text = "Probar Nube (2s)",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
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
                color = Color(0xFF05110B),
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
