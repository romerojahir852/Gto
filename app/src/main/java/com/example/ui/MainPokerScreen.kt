package com.example.ui

import com.example.ui.theme.BgWhite
import com.example.ui.theme.BgSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.Ink2
import com.example.ui.theme.Ink3
import com.example.ui.theme.Line
import com.example.ui.theme.Line2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PokerAnalysisResult
import com.example.data.Street
import com.example.ui.components.GtoHudCard
import com.example.ui.components.PokerTableSimulator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPokerScreen(
    viewModel: PokerViewModel,
    onStartServiceRequested: () -> Unit,
    onStopServiceRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedStreet by viewModel.selectedStreet.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val history by viewModel.history.collectAsState()

    var showRawResponse by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BgWhite,
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
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isServiceRunning) Color(0xFFECFDF5) else Color(0xFFF3F4F6),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isServiceRunning) Color(0xFFA7F3D0) else Line2
                        ),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable {
                                if (isServiceRunning) onStopServiceRequested() else onStartServiceRequested()
                            }
                            .testTag("service_status_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isServiceRunning) Color(0xFF10B981) else Ink3,
                                        CircleShape
                                    )
                            )
                            Text(
                                text = if (isServiceRunning) "Captura activa" else "Captura inactiva",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isServiceRunning) Color(0xFF047857) else Ink3
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgWhite.copy(alpha = 0.92f)
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            com.example.ui.components.BackgroundGeometry(modifier = Modifier.fillMaxSize())
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
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
                        StreetSelectorTabs(
                            selectedStreet = selectedStreet,
                            onStreetSelected = { viewModel.setStreet(it) }
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.ui.components.Eyebrow(text = "Servicio de captura")
                        Text(
                            text = "Activar MediaProjection",
                            color = Ink,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = FontWeight.Light,
                            fontSize = 24.sp,
                            letterSpacing = 0.02.sp
                        )
                        com.example.ui.components.SectionSub(
                            text = "Inicia el servicio foreground para leer la pantalla en tiempo real."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PrimaryCaptureActionCard(
                            isServiceRunning = isServiceRunning,
                            uiState = uiState,
                            onStartService = onStartServiceRequested,
                            onCaptureNow = { viewModel.triggerScreenCapture() },
                            onStopService = onStopServiceRequested
                        )
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = uiState is AnalysisUiState.Analyzing || uiState is AnalysisUiState.Capturing,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = BgSoft),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Ink,
                                    strokeWidth = 3.dp
                                )
                                Column {
                                    Text(
                                        text = if (uiState is AnalysisUiState.Capturing)
                                            "Leyendo fotograma de pantalla..."
                                        else
                                            "Calculando Outs, Equity y GTO con Gemini...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Ink
                                    )
                                    Text(
                                        text = "Visión multirresolución de alta fidelidad con Gemini Serie 3 Flash",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Ink3
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    if (uiState is AnalysisUiState.Error) {
                        val errorMsg = (uiState as AnalysisUiState.Error).message
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Atención en el análisis",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB91C1C)
                                    )
                                    Text(
                                        text = errorMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                                IconButton(onClick = { viewModel.clearError() }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Descartar",
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    if (latestResult != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            GtoHudCard(result = latestResult!!)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = if (showRawResponse) "Ocultar respuesta cruda" else "Ver formato enviado/recibido",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Ink3,
                                    modifier = Modifier
                                        .clickable { showRawResponse = !showRawResponse }
                                        .padding(vertical = 4.dp)
                                )
                            }

                            if (showRawResponse) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = BgSoft),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = latestResult!!.rawText,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Ink2,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
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
                        PokerTableSimulator(
                            selectedPreset = selectedPreset,
                            onPresetSelected = { viewModel.selectPreset(it) },
                            onAnalyzePreset = { bitmap, street ->
                                viewModel.analyzeBitmap(bitmap, street)
                            }
                        )
                    }
                }

                if (history.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.example.ui.components.Eyebrow(text = "Historial")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Ink3,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Manos evaluadas (${history.size})",
                                    color = Ink,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 20.sp,
                                    letterSpacing = 0.02.sp
                                )
                            }
                        }
                    }

                    itemsIndexed(history) { index, hist ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_item_$index"),
                            colors = CardDefaults.cardColors(containerColor = BgWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${hist.street.displayName} - ${hist.gtoAction.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = hist.gtoAction.color
                                    )
                                    Text(
                                        text = "Outs: ${hist.totalOuts ?: "-"} | Win: ${hist.winEquity ?: "-"} | Latencia: ${hist.latencyMs}ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Ink3
                                    )
                                }

                                Surface(
                                    color = hist.gtoAction.bgTint,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = hist.gtoAction.title,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Black,
                                        color = hist.gtoAction.color,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun StreetSelectorTabs(
    selectedStreet: Street,
    onStreetSelected: (Street) -> Unit
) {
    val streets = listOf(Street.PREFLOP, Street.POSTFLOP, Street.FAST_GTO)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E1B15), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1B362A), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            streets.forEach { street ->
                val isSelected = street == selectedStreet
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF10B981) else Color.Transparent)
                        .clickable { onStreetSelected(street) }
                        .padding(vertical = 8.dp)
                        .testTag("tab_${street.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = street.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF06140E) else Color(0xFF94A3B8)
                    )
                }
            }
        }

        Text(
            text = selectedStreet.description,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun PrimaryCaptureActionCard(
    isServiceRunning: Boolean,
    uiState: AnalysisUiState,
    onStartService: () -> Unit,
    onCaptureNow: () -> Unit,
    onStopService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13221C)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isServiceRunning) Color(0xFF10B981) else Color(0xFF334155)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isServiceRunning) "Servicio Foreground Activo" else "Captura en Tiempo Real",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )
                    Text(
                        text = if (isServiceRunning)
                            "Captura un fotograma exclusivo al pulsar el botón"
                        else
                            "Inicia el lector MediaProjection para leer la pantalla",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                if (isServiceRunning) {
                    OutlinedButton(
                        onClick = onStopService,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("stop_service_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Parar")
                    }
                }
            }

            if (isServiceRunning) {
                Button(
                    onClick = onCaptureNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("capture_screen_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = uiState !is AnalysisUiState.Capturing && uiState !is AnalysisUiState.Analyzing
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFF06140E)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CAPTURAR PANTALLA & ANALIZAR GTO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF06140E),
                        letterSpacing = 0.5.sp
                    )
                }
            } else {
                Button(
                    onClick = onStartService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_service_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A2E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF34D399)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Activar Servicio de Captura de Pantalla",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399)
                    )
                }
            }
        }
    }
}
