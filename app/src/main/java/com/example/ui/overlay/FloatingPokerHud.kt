package com.example.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BettingUnit
import com.example.data.GTOStateManager
import com.example.data.HandState
import com.example.data.LocalVisualMapper
import com.example.data.PokerCard
import com.example.data.PokerGameStateManager
import com.example.data.VisualOutItem
import com.example.ui.components.PokerCardBadge

/**
 * Compact Pro Texas Hold'em HUD Overlay.
 * 
 * Diseñado específicamente para pantallas móviles de póker (GGPoker, PokerStars, Suprema):
 * 1. Factor de forma ultra-compacto (<180dp de alto) para no obstruir la mesa, cartas ni pozos.
 * 2. Barra de configuración plegable con botón de ajuste rápido (⚙).
 * 3. Selector manual de unidades BB / Fichas instantáneo.
 * 4. Latencia sub-segundo con soporte para Gemini Serie 3 Flash y OCR local offline.
 */
@Composable
fun FloatingPokerHud(
    state: HandState,
    onDrag: (Offset) -> Unit,
    onTriggerClick: () -> Unit,
    onCloseCloud: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var enteredKey by remember { mutableStateOf(com.example.data.ApiKeyManager.getApiKey(context) ?: "") }
    var showTableDetails by remember { mutableStateOf(false) }

    var isPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "button_scale"
    )

    val visualOutItems by remember(state.outs) {
        derivedStateOf { LocalVisualMapper.parseOutsToVisuals(state.outs) }
    }

    Column(
        modifier = modifier
            .padding(2.dp)
            .widthIn(max = 275.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Fila del Botón Flotante Draggable + Píldora de Estado Rápido
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 3.dp)
        ) {
            // Botón Circular de Disparo (Trigger) - 38dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(buttonScale)
                    .size(38.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1B3D2B),
                                Color(0xFF0A1F14),
                                Color(0xFF040A07)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF00E676),
                                Color(0xFFFFD700),
                                Color(0xFF00E676)
                            )
                        ),
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isPressed = true },
                            onDragEnd = { isPressed = false },
                            onDragCancel = { isPressed = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            }
                        )
                    }
                    .clickable {
                        onTriggerClick()
                    }
                    .testTag("floating_overlay_button")
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF00E676),
                        strokeWidth = 2.dp
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "♠",
                            color = Color(0xFF00E676),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 14.sp
                        )
                        Text(
                            text = "GTO",
                            color = Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            // Píldora de Estado Rápido cuando la nube está colapsada
            if (!state.isExpanded && !state.isLoading) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xEE0A1810),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onTriggerClick() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = state.fase.uppercase(),
                            color = Color(0xFFFFD700),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "•", color = Color(0xFF6B7280), fontSize = 9.sp)
                        Text(
                            text = state.winRate,
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(text = "•", color = Color(0xFF6B7280), fontSize = 9.sp)
                        Text(
                            text = state.gtoAction.title,
                            color = state.gtoAction.color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // Panel de Resultados Desplegable ("La Nube Compact Pro")
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = fadeIn(tween(140)) + expandVertically(tween(140)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(14.dp))
                    .testTag("floating_result_cloud"),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xF60A140F),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF00E676),
                            Color(0xFF1E3A2B)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Fila 1: Cabecera compacta (Fase, Bote, Unidad, Latencia, Botón Cerrar)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Izquierda: Fase + Bote
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF163824),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF00E676))
                            ) {
                                Text(
                                    text = state.fase.uppercase(),
                                    color = Color(0xFF00E676),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }

                            // Bote & Rival
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF112217),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF224832))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "Bote:",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = state.displayBote,
                                        color = Color(0xFFFFD700),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (state.displayApuestaRival != "0 BB" && state.displayApuestaRival != "0 $") {
                                        Text(
                                            text = "| Riv:",
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = state.displayApuestaRival,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Selector Rápido BB ⇄ $
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (state.bettingUnit == BettingUnit.BB) Color(0xFF0F3820) else Color(0xFF382A0F),
                                border = androidx.compose.foundation.BorderStroke(
                                    0.8.dp,
                                    if (state.bettingUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFFFFD700)
                                ),
                                modifier = Modifier
                                    .testTag("hud_header_unit_toggle")
                                    .clickable { PokerGameStateManager.toggleBettingUnit() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = if (state.bettingUnit == BettingUnit.BB) "BB" else "$",
                                        color = if (state.bettingUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFFFFD700),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Cambiar unidad",
                                        tint = if (state.bettingUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFFFFD700),
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }

                        // Derecha: Latencia, API Key y Botón Cerrar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (!state.isLoading && state.latencyMs > 0) {
                                Text(
                                    text = "⚡${state.latencyMs}ms",
                                    color = if (state.latencyMs <= 1000) Color(0xFF00E676) else Color(0xFFFFD700),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (com.example.data.ApiKeyManager.hasApiKey(context)) Color(0xFF0F3820) else Color(0xFF382A0F),
                                border = androidx.compose.foundation.BorderStroke(
                                    0.8.dp,
                                    if (com.example.data.ApiKeyManager.hasApiKey(context)) Color(0xFF00E676) else Color(0xFFFFD700)
                                ),
                                modifier = Modifier
                                    .testTag("hud_header_api_key_btn")
                                    .clickable {
                                        enteredKey = com.example.data.ApiKeyManager.getApiKey(context) ?: ""
                                        showApiKeyDialog = !showApiKeyDialog
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Configurar API Key",
                                    tint = if (com.example.data.ApiKeyManager.hasApiKey(context)) Color(0xFF00E676) else Color(0xFFFFD700),
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .size(10.dp)
                                )
                            }

                            IconButton(
                                onClick = onCloseCloud,
                                modifier = Modifier
                                    .size(20.dp)
                                    .testTag("floating_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Ocultar nube",
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Fila 2: Mensaje de Estado / Feedback (Solo si es relevante)
                    if (state.statusMessage.isNotBlank() && state.statusMessage != "Listo para capturar") {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (state.statusMessage.startsWith("⚠️")) Color(0xFF2E1515) else Color(0xFF132A1C),
                            border = androidx.compose.foundation.BorderStroke(
                                0.8.dp,
                                if (state.statusMessage.startsWith("⚠️")) Color(0xFF7F1D1D) else Color(0xFF1B4D2E)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (state.statusMessage.contains("API Key", ignoreCase = true)) {
                                        enteredKey = com.example.data.ApiKeyManager.getApiKey(context) ?: ""
                                        showApiKeyDialog = true
                                    }
                                }
                        ) {
                            Text(
                                text = state.statusMessage,
                                color = if (state.statusMessage.startsWith("⚠️")) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }

                    // Diálogo desplegable de API Key (si se activa)
                    if (showApiKeyDialog) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🔑 Clave Gemini Flash",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { showApiKeyDialog = false },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cerrar",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                androidx.compose.material3.OutlinedTextField(
                                    value = enteredKey,
                                    onValueChange = { enteredKey = it },
                                    placeholder = { Text("AIzaSy...", fontSize = 9.sp, color = Color.Gray) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier.clickable {
                                            val clip = clipboardManager.getText()?.text
                                            if (!clip.isNullOrBlank()) enteredKey = clip.trim()
                                        }
                                    ) {
                                        Text(
                                            text = "Pegar",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Color(0xFF00E676),
                                        modifier = Modifier.clickable {
                                            if (enteredKey.isNotBlank()) {
                                                com.example.data.ApiKeyManager.saveApiKey(context, enteredKey)
                                                PokerGameStateManager.updateStatus("✅ API Key guardada")
                                            } else {
                                                com.example.data.ApiKeyManager.clearApiKey(context)
                                                PokerGameStateManager.updateStatus("⚡ Modo OCR Local activo")
                                            }
                                            showApiKeyDialog = false
                                        }
                                    ) {
                                        Text(
                                            text = "Guardar",
                                            color = Color.Black,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Fila 3: Configuración de Mesa Compacta Plegable (👥 Jugadores • D BTN • Mi Pos ⚙)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF141923),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF263044)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hud_gto_memory_bar")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // Cabecera compacta de la barra de mesa
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
                                        text = "${state.jugadores} AUTO",
                                        color = Color(0xFF00E676),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(text = "•", color = Color(0xFF475569), fontSize = 8.sp)
                                    // Dealer Badge
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFFFD700),
                                        modifier = Modifier
                                            .testTag("hud_dealer_badge")
                                            .clickable { GTOStateManager.rotateDealer() }
                                    ) {
                                        Text(
                                            text = "D",
                                            color = Color.Black,
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                                        )
                                    }
                                    Text(
                                        text = state.dealerPosition,
                                        color = Color(0xFFFFD700),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { GTOStateManager.rotateDealer() }
                                    )
                                    Text(text = "•", color = Color(0xFF475569), fontSize = 8.sp)
                                    Text(
                                        text = "Pos: ${state.posicion}",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Botón desplegar/colapsar ajustes manuales ⚙
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = if (showTableDetails) Color(0xFF00E676) else Color(0xFF1E293B),
                                    modifier = Modifier
                                        .size(17.dp)
                                        .clickable { showTableDetails = !showTableDetails }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (showTableDetails) Icons.Default.ExpandLess else Icons.Default.Settings,
                                            contentDescription = "Ajustar mesa",
                                            tint = if (showTableDetails) Color.Black else Color(0xFF94A3B8),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }

                            // Sección expandible con controles manuales completos (si el usuario la activa)
                            if (showTableDetails) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "JUG:", color = Color(0xFF94A3B8), fontSize = 8.5.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFF1E293B),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { GTOStateManager.decrementPlayers() }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(9.dp))
                                            }
                                        }
                                        Text(
                                            text = "${state.jugadores}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFF1E293B),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { GTOStateManager.incrementPlayers() }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(9.dp))
                                            }
                                        }
                                    }

                                    // Selector de posición manual
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        listOf("UTG", "MP", "CO", "BTN", "SB", "BB").forEach { pos ->
                                            val isSelected = state.posicion.equals(pos, ignoreCase = true)
                                            Surface(
                                                shape = RoundedCornerShape(2.dp),
                                                color = if (isSelected) Color(0xFF00E676) else Color(0xFF1E293B),
                                                modifier = Modifier.clickable { GTOStateManager.setPosition(pos) }
                                            ) {
                                                Text(
                                                    text = pos,
                                                    color = if (isSelected) Color.Black else Color(0xFFCBD5E1),
                                                    fontSize = 8.sp,
                                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Fila 4: Contenido de Análisis / Cartas & Métricas
                    if (state.isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF00E676),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Analizando con Gemini Serie 3...",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Cartas Propias, Mesa y Win Equity en 1 Fila Integrada
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mis Cartas
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = "MIS CARTAS",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    if (state.cartasPropias.isNotEmpty()) {
                                        state.cartasPropias.forEach { card ->
                                            PokerCardBadge(card = card)
                                        }
                                    } else {
                                        val parsed = PokerCard.parseMultiple(state.cartasPropiasDisplay)
                                        if (parsed.isNotEmpty()) {
                                            parsed.forEach { PokerCardBadge(card = it) }
                                        } else {
                                            Text(
                                                text = state.cartasPropiasDisplay,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Mesa Comunitaria
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = "MESA",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (state.cartasComunitarias.isNotEmpty()) {
                                        state.cartasComunitarias.forEach { card ->
                                            PokerCardBadge(card = card)
                                        }
                                    } else if (state.cartasComunitariasDisplay != "-" && !state.cartasComunitariasDisplay.contains("Preflop")) {
                                        val parsed = PokerCard.parseMultiple(state.cartasComunitariasDisplay)
                                        if (parsed.isNotEmpty()) {
                                            parsed.forEach { PokerCardBadge(card = it) }
                                        } else {
                                            Text(
                                                text = state.cartasComunitariasDisplay,
                                                color = Color(0xFFD1D5DB),
                                                fontSize = 10.sp
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "— (Preflop)",
                                            color = Color(0xFF6B7280),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Win Equity & Outs
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = "EQUITY",
                                    color = Color(0xFF00E676),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = state.winRate,
                                    color = Color(0xFF00E676),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                                if (state.outs != "-" && state.outs.isNotBlank()) {
                                    Text(
                                        text = "Outs: ${state.outs.take(10)}",
                                        color = Color(0xFF60A5FA),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Badges Visuales Locales de Proyectos (si existen)
                        if (visualOutItems.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                visualOutItems.forEach { item ->
                                    VisualOutBadge(item = item)
                                }
                            }
                        }

                        // Fila 5: Banner de Decisión GTO Óptima (Sleek & Compact)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = state.gtoAction.bgTint,
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, state.gtoAction.color)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "DECISIÓN GTO ÓPTIMA",
                                        color = state.gtoAction.color.copy(alpha = 0.8f),
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp
                                    )
                                    Text(
                                        text = state.fullGtoDecision.ifBlank { state.gtoAction.title },
                                        color = state.gtoAction.color,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = state.gtoAction.color,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Fila 6: Controles Inferiores (Selector de Unidad BB/$ y Botón Re-analizar)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Selector manual BB o Fichas
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = if (state.bettingUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFF1B2E23),
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.8.dp,
                                        if (state.bettingUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFF335C45)
                                    ),
                                    modifier = Modifier
                                        .testTag("floating_select_bb_btn")
                                        .clickable { PokerGameStateManager.setBettingUnit(BettingUnit.BB) }
                                ) {
                                    Text(
                                        text = "BB",
                                        color = if (state.bettingUnit == BettingUnit.BB) Color.Black else Color.White,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = if (state.bettingUnit == BettingUnit.CHIPS) Color(0xFFFFD700) else Color(0xFF2C2411),
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.8.dp,
                                        if (state.bettingUnit == BettingUnit.CHIPS) Color(0xFFFFD700) else Color(0xFF59481E)
                                    ),
                                    modifier = Modifier
                                        .testTag("floating_select_chips_btn")
                                        .clickable { PokerGameStateManager.setBettingUnit(BettingUnit.CHIPS) }
                                ) {
                                    Text(
                                        text = "Fichas ($)",
                                        color = if (state.bettingUnit == BettingUnit.CHIPS) Color.Black else Color.White,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Botón de re-analizar rápido
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF142B1E),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF224832)),
                                modifier = Modifier
                                    .testTag("floating_reanalyze_btn")
                                    .clickable { onTriggerClick() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Re-evaluar",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "Re-analizar",
                                        color = Color(0xFF00E676),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Local visual badge mapping text keywords (e.g. Corazones, Escalera)
 * to local icons and drawables instantly without waiting for AI to describe visuals.
 */
@Composable
fun VisualOutBadge(
    item: VisualOutItem,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(3.dp),
        color = item.iconColor.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, item.iconColor.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.iconSymbol,
                color = item.iconColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = item.label,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
