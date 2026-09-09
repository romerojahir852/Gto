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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
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
 * High-performance, reactive Texas Hold'em HUD Overlay.
 * 1. Draggable FloatingActionButton (Trigger) with minimal touch latency.
 * 2. Expandable / Collapsible Result Panel ("La Nube") with State:
 *    - Fase (Preflop, Flop, Turn, River)
 *    - Bote & Apuesta Rival
 *    - Cartas Propias & Mesa
 *    - Outs mapped immediately to local graphical drawables & badges
 *    - Win Rate & GTO Optimal Decision
 * 3. Sub-second latency badge
 * 4. Close 'X' button to collapse panel while keeping trigger accessible.
 */
@Composable
fun FloatingPokerHud(
    state: HandState,
    onDrag: (Offset) -> Unit,
    onTriggerClick: () -> Unit,
    onCloseCloud: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "button_scale"
    )

    // Compute visual out mappings with derivedStateOf to prevent unnecessary recomposition
    val visualOutItems by remember(state.outs) {
        derivedStateOf { LocalVisualMapper.parseOutsToVisuals(state.outs) }
    }

    Column(
        modifier = modifier
            .padding(8.dp)
            .widthIn(max = 350.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Row with Floating Trigger Button + Status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            // Draggable & Clickable Floating Button (Trigger)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(buttonScale)
                    .size(56.dp)
                    .shadow(12.dp, CircleShape)
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
                        width = 2.dp,
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
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF00E676),
                        strokeWidth = 3.dp
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "♠",
                            color = Color(0xFF00E676),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 20.sp
                        )
                        Text(
                            text = "GTO",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Quick State Pill (shows phase & win rate when collapsed)
            if (!state.isExpanded && !state.isLoading) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xEE0A1810),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onTriggerClick() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = state.fase.uppercase(),
                            color = Color(0xFFFFD700),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "•",
                            color = Color(0xFF6B7280),
                            fontSize = 10.sp
                        )
                        Text(
                            text = state.winRate,
                            color = Color(0xFF00E676),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "•",
                            color = Color(0xFF6B7280),
                            fontSize = 10.sp
                        )
                        Text(
                            text = state.gtoAction.title,
                            color = state.gtoAction.color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // Expandable / Collapsible Result Panel ("La Nube")
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .testTag("floating_result_cloud"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xF80A140F),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF00E676),
                            Color(0xFF1E3A2B)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header: Phase Badge, Pot / Bet Info & Latency / Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Phase Pill + Pot info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF163824),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
                            ) {
                                Text(
                                    text = state.fase.uppercase(),
                                    color = Color(0xFF00E676),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }

                            // Pot & Rival Bet chips
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF112217),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF224832))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Bote:",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = state.displayBote,
                                        color = Color(0xFFFFD700),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "| Rival:",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = state.displayApuestaRival,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Quick Unit Selector Pill (BB ⇄ $)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (state.bettingUnit == BettingUnit.BB) Color(0xFF0F3820) else Color(0xFF382A0F),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (state.bettingUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFFFFD700)
                                ),
                                modifier = Modifier
                                    .testTag("hud_header_unit_toggle")
                                    .clickable {
                                        PokerGameStateManager.toggleBettingUnit()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (state.bettingUnit == BettingUnit.BB) "BB" else "$",
                                        color = if (state.bettingUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFFFFD700),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Cambiar unidad BB o Fichas",
                                        tint = if (state.bettingUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFFFFD700),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }

                        // Right: Latency indicator + Close Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (!state.isLoading && state.latencyMs > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF162D20)
                                ) {
                                    Text(
                                        text = "⚡ ${state.latencyMs}ms",
                                        color = if (state.latencyMs <= 1000) Color(0xFF00E676) else Color(0xFFFFD700),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onCloseCloud,
                                modifier = Modifier
                                    .size(26.dp)
                                    .testTag("floating_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Ocultar nube",
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Status feedback banner (Warnings, Errors, or Model Info)
                    if (state.statusMessage.isNotBlank() && state.statusMessage != "Listo para capturar") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (state.statusMessage.startsWith("⚠️")) Color(0xFF2E1515) else Color(0xFF132A1C),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (state.statusMessage.startsWith("⚠️")) Color(0xFF7F1D1D) else Color(0xFF1B4D2E)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = state.statusMessage,
                                color = if (state.statusMessage.startsWith("⚠️")) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 2
                            )
                        }
                    }

                    // GTO State Bar: Active Players counter (+ / -), Dealer Button 'D' & Hero Position + Game Phase
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF141923),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263044)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hud_gto_memory_bar")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Active Players with auto-detection indicator
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "JUGADORES:",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .testTag("hud_btn_dec_players")
                                            .clickable { GTOStateManager.decrementPlayers() }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Restar jugador",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF0F172A),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    ) {
                                        Text(
                                            text = "${state.jugadores} AUTO",
                                            color = Color(0xFF00E676),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .testTag("hud_btn_inc_players")
                                            .clickable { GTOStateManager.incrementPlayers() }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Sumar jugador",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }

                                // Dealer Button Badge & Game Phase Indicator
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Dealer Badge
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFFFD700),
                                        modifier = Modifier.testTag("hud_dealer_badge")
                                    ) {
                                        Text(
                                            text = "D",
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }

                                    Text(
                                        text = state.dealerPosition,
                                        color = Color(0xFFFFD700),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Phase Badge (Preflop, Flop, Turn, River)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF1E3A8A)
                                    ) {
                                        Text(
                                            text = state.fase.uppercase(),
                                            color = Color(0xFF93C5FD),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Hero Position chips (UTG, MP, CO, BTN, SB, BB)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "MI POS:",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    listOf("UTG", "MP", "CO", "BTN", "SB", "BB").forEach { pos ->
                                        val isSelected = state.posicion.equals(pos, ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSelected) Color(0xFF00E676) else Color(0xFF1E293B),
                                            modifier = Modifier
                                                .testTag("hud_pos_$pos")
                                                .clickable { GTOStateManager.setPosition(pos) }
                                        ) {
                                            Text(
                                                text = pos,
                                                color = if (isSelected) Color.Black else Color(0xFFCBD5E1),
                                                fontSize = 9.sp,
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Content: Loading state vs Structured Poker Analysis Result
                    if (state.isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = Color(0xFF00E676),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Calculando...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Recorte limpio • Gemini Flash (Timeout 4s)",
                                color = Color(0xFF9CA3AF),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // 1. CARDS ROW: Cartas Propias & Mesa
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mis Cartas
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "MIS CARTAS",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (state.cartasPropias.isNotEmpty()) {
                                        state.cartasPropias.forEach { card ->
                                            PokerCardBadge(card = card)
                                        }
                                    } else {
                                        val cards = PokerCard.parseMultiple(state.cartasPropiasDisplay)
                                        if (cards.isNotEmpty()) {
                                            cards.forEach { PokerCardBadge(card = it) }
                                        } else {
                                            Text(
                                                text = state.cartasPropiasDisplay,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Mesa
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "MESA",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (state.cartasComunitarias.isNotEmpty()) {
                                        state.cartasComunitarias.forEach { card ->
                                            PokerCardBadge(card = card)
                                        }
                                    } else if (state.cartasComunitariasDisplay != "-" && !state.cartasComunitariasDisplay.contains("Preflop")) {
                                        val cards = PokerCard.parseMultiple(state.cartasComunitariasDisplay)
                                        if (cards.isNotEmpty()) {
                                            cards.forEach { PokerCardBadge(card = it) }
                                        } else {
                                            Text(
                                                text = state.cartasComunitariasDisplay,
                                                color = Color(0xFFD1D5DB),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "— (Preflop)",
                                            color = Color(0xFF6B7280),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // 2. METRICS ROW: Outs & Win Equity
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Outs Card with Local Visual Badges
                            Surface(
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF112319),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1C3C2A))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "OUTS & PROYECTOS",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = state.outs,
                                        color = Color(0xFF60A5FA),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )

                                    // Local Visual Badges mapped instantly from AI text tokens
                                    if (visualOutItems.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            visualOutItems.forEach { item ->
                                                VisualOutBadge(item = item)
                                            }
                                        }
                                    }
                                }
                            }

                            // Win % Card (Vibrant Green)
                            Surface(
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0A291A),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E676))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "WIN EQUITY",
                                        color = Color(0xFF00E676),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = state.winRate,
                                        color = Color(0xFF00E676),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        // 3. GTO OPTIMAL DECISION BANNER
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = state.gtoAction.bgTint,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, state.gtoAction.color)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "DECISIÓN GTO ÓPTIMA",
                                        color = state.gtoAction.color.copy(alpha = 0.8f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = state.fullGtoDecision.ifBlank { state.gtoAction.title },
                                        color = state.gtoAction.color,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = state.gtoAction.color,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Bottom Controls: Manual Unit Selection (BB vs Fichas/$) + Quick Re-evaluate
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Selector manual para que el usuario elija exactamente BB o Fichas y no haya errores
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Unidad:",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                // Opción BB
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (state.bettingUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFF1B2E23),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (state.bettingUnit == BettingUnit.BB) Color(0xFF00E676) else Color(0xFF335C45)
                                    ),
                                    modifier = Modifier
                                        .testTag("floating_select_bb_btn")
                                        .clickable {
                                            PokerGameStateManager.setBettingUnit(BettingUnit.BB)
                                        }
                                ) {
                                    Text(
                                        text = "BB",
                                        color = if (state.bettingUnit == BettingUnit.BB) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                                // Opción Fichas / $
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (state.bettingUnit == BettingUnit.CHIPS) Color(0xFFFFD700) else Color(0xFF2C2411),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (state.bettingUnit == BettingUnit.CHIPS) Color(0xFFFFD700) else Color(0xFF59481E)
                                    ),
                                    modifier = Modifier
                                        .testTag("floating_select_chips_btn")
                                        .clickable {
                                            PokerGameStateManager.setBettingUnit(BettingUnit.CHIPS)
                                        }
                                ) {
                                    Text(
                                        text = "Fichas ($)",
                                        color = if (state.bettingUnit == BettingUnit.CHIPS) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Botón de re-analizar
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF142B1E),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF224832)),
                                modifier = Modifier
                                    .testTag("floating_reanalyze_btn")
                                    .clickable { onTriggerClick() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Re-evaluar",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Re-analizar",
                                        color = Color(0xFF00E676),
                                        fontSize = 10.sp,
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
        shape = RoundedCornerShape(4.dp),
        color = item.iconColor.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, item.iconColor.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.iconSymbol,
                color = item.iconColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = item.label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
