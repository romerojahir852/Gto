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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
 * High-performance, luxury Texas Hold'em HUD Overlay in Royal Casino Ivory & Emerald.
 * 1. Draggable FloatingActionButton (Trigger) styled as an authentic Ceramic Casino Chip.
 * 2. Tapping the bubble toggles the panel (minimizes when open, expands when closed).
 * 3. Drag-to-trash support at the bottom of the screen.
 * 4. Two-row un-clippable header: Minimize and Close buttons are ALWAYS 100% visible.
 * 5. Porcelain Ivory background with Gold & Emerald trims.
 */
@Composable
fun FloatingPokerHud(
    state: HandState,
    onDrag: (Offset) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onTriggerClick: () -> Unit,
    onCloseCloud: () -> Unit,
    onDismissOverlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
            .padding(2.dp)
            .widthIn(max = 350.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Row with Floating Trigger Button + Status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            // Draggable & Clickable Floating Button (Trigger) - Luxury Ceramic Casino Chip
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(buttonScale)
                    .size(46.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF), // Pure Pearl White Center
                                Color(0xFFF7F8F4), // Warm Ivory
                                Color(0xFFE2E7DD)  // Ceramic Clay Rim
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFFD4AF37), // Champagne Gold
                                Color(0xFF0A6E3D), // Monaco Emerald
                                Color(0xFFD4AF37), // Champagne Gold
                                Color(0xFF0A6E3D), // Monaco Emerald
                                Color(0xFFD4AF37)  // Champagne Gold
                            )
                        ),
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isPressed = true
                                onDragStart()
                            },
                            onDragEnd = {
                                isPressed = false
                                onDragEnd()
                            },
                            onDragCancel = {
                                isPressed = false
                                onDragCancel()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            }
                        )
                    }
                    .clickable {
                        if (state.isExpanded) {
                            onCloseCloud()
                        } else {
                            onTriggerClick()
                        }
                    }
                    .testTag("floating_overlay_button")
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF0A6E3D),
                        trackColor = Color(0xFFD4AF37).copy(alpha = 0.3f),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    // Concentric engraved inner ring for casino chip depth
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .border(
                                width = 0.8.dp,
                                color = Color(0xFFD4AF37).copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "♠",
                                color = Color(0xFF0A6E3D),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = "GTO",
                                color = Color(0xFF0F172A),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // Quick State Pill (shows phase & win rate when collapsed)
            if (!state.isExpanded && !state.isLoading) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFAF9F5),
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.7f)),
                    modifier = Modifier.clickable { onTriggerClick() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = state.fase.uppercase(),
                            color = Color(0xFF0A6E3D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "•",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                        Text(
                            text = state.winRate,
                            color = Color(0xFF0A6E3D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "•",
                            color = Color(0xFF94A3B8),
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

            // Botón rápido para cerrar y retirar la burbuja flotante cuando está colapsada
            if (!state.isExpanded) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .shadow(2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF1F2))
                        .border(1.dp, Color(0xFFE11D48).copy(alpha = 0.6f), CircleShape)
                        .clickable { onDismissOverlay() }
                        .testTag("floating_bubble_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar burbuja flotante y detener servicio",
                        tint = Color(0xFFE11D48),
                        modifier = Modifier.size(14.dp)
                    )
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
                color = Color(0xFFFDFBF7), // Warm Ivory Porcelain
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFD4AF37), // Champagne Gold
                            Color(0xFF0A6E3D), // Monaco Emerald
                            Color(0xFFD4AF37)  // Champagne Gold
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ROW 1: Stage Badge & Prominent Window Controls (Never cut off!)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Stage Chip Badge + Live Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0A6E3D),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4AF37))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "♠",
                                        color = Color(0xFFFFD700),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = state.fase.uppercase(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            if (!state.isLoading && state.latencyMs > 0) {
                                val isFast = state.latencyMs <= 1000
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isFast) Color(0xFFECFDF5) else Color(0xFFFFFBEB),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isFast) Color(0xFFA7F3D0) else Color(0xFFFDE68A)
                                    )
                                ) {
                                    Text(
                                        text = "⚡ ${state.latencyMs}ms",
                                        color = if (isFast) Color(0xFF065F46) else Color(0xFF92400E),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Right: Dedicated Window Controls (Always fully visible!)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            // Minimizar a burbuja (—)
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("floating_minimize_button")
                                    .clickable { onCloseCloud() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Minimizar a burbuja",
                                        tint = Color(0xFF334155),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            // Cerrar completamente el overlay y detener el servicio (✕)
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFF1F2),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFE11D48)),
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("floating_close_button")
                                    .clickable { onDismissOverlay() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar HUD y detener servicio",
                                        tint = Color(0xFFE11D48),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ROW 2: Pot, Rival Bet, and Unit Selector (BB ⇄ $)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pot & Rival Bet chips (Ivory Card)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFFFFF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Bote:",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = state.displayBote,
                                    color = Color(0xFFB45309), // Monaco Amber/Gold
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "|",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Rival:",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = state.displayApuestaRival,
                                    color = Color(0xFF0F172A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Quick Unit Selector Pill (BB ⇄ $)
                        val isBB = state.bettingUnit == BettingUnit.BB
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isBB) Color(0xFFE6F4EA) else Color(0xFFFBF3D9),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isBB) Color(0xFF0A6E3D) else Color(0xFFC59B27)
                            ),
                            modifier = Modifier
                                .testTag("hud_header_unit_toggle")
                                .clickable {
                                    PokerGameStateManager.toggleBettingUnit()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isBB) "Unidad: BB" else "Unidad: $",
                                    color = if (isBB) Color(0xFF0A6E3D) else Color(0xFF92400E),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Cambiar unidad BB o Fichas",
                                    tint = if (isBB) Color(0xFF0A6E3D) else Color(0xFF92400E),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    // ROW 3: Status feedback banner (Calm and natural poker language)
                    if (state.statusMessage.isNotBlank() && state.statusMessage != "Listo para capturar") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFECFDF5),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = state.statusMessage,
                                color = Color(0xFF065F46),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 2
                            )
                        }
                    }



                    // GTO State Bar: Active Players counter (+ / -), Dealer Button 'D' & Hero Position + Game Phase
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF4F6F0),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                        color = Color(0xFF64748B),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .testTag("hud_btn_dec_players")
                                            .clickable { GTOStateManager.decrementPlayers() }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Restar jugador",
                                                tint = Color(0xFF0F172A),
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF0A6E3D),
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    ) {
                                        Text(
                                            text = "${state.jugadores} AUTO",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .testTag("hud_btn_inc_players")
                                            .clickable { GTOStateManager.incrementPlayers() }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Sumar jugador",
                                                tint = Color(0xFF0F172A),
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
                                    // Dealer Badge (Authentic Casino Dealer Button - White with Black Border)
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0F172A)),
                                        modifier = Modifier
                                            .testTag("hud_dealer_badge")
                                            .clickable { GTOStateManager.rotateDealer() }
                                    ) {
                                        Text(
                                            text = "D",
                                            color = Color(0xFF0F172A),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }

                                    Text(
                                        text = state.dealerPosition,
                                        color = Color(0xFFB45309), // Warm Amber/Gold
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { GTOStateManager.rotateDealer() }
                                    )

                                    // Phase Badge (Preflop, Flop, Turn, River - Clickable to advance)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFE6F4EA),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0A6E3D)),
                                        modifier = Modifier.clickable { GTOStateManager.nextPhase() }
                                    ) {
                                        Text(
                                            text = state.fase.uppercase(),
                                            color = Color(0xFF0A6E3D),
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
                                    color = Color(0xFF64748B),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    listOf("UTG", "MP", "CO", "BTN", "SB", "BB").forEach { pos ->
                                        val isSelected = state.posicion.equals(pos, ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSelected) Color(0xFF0A6E3D) else Color.White,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) Color(0xFFD4AF37) else Color(0xFFE2E8F0)
                                            ),
                                            modifier = Modifier
                                                .testTag("hud_pos_$pos")
                                                .clickable { GTOStateManager.setPosition(pos) }
                                        ) {
                                            Text(
                                                text = pos,
                                                color = if (isSelected) Color.White else Color(0xFF475569),
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
                                color = Color(0xFF0A6E3D),
                                trackColor = Color(0xFFD4AF37).copy(alpha = 0.3f),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Calculando GTO Óptimo...",
                                color = Color(0xFF0F172A),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lectura de mesa en vivo • Probabilidades GTO",
                                color = Color(0xFF64748B),
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
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
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
                                                color = Color(0xFF0F172A),
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
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
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
                                                color = Color(0xFF334155),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "— (Preflop)",
                                            color = Color(0xFF94A3B8),
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
                                color = Color(0xFFFFFFFF),
                                shadowElevation = 2.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "OUTS & PROYECTOS",
                                        color = Color(0xFF64748B),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = state.outs,
                                        color = Color(0xFF1D4ED8), // Royal Sapphire Blue
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

                            // Win % Card (Monaco Emerald Felt with Gold Border)
                            Surface(
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0A6E3D),
                                shadowElevation = 3.dp,
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFD4AF37))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "WIN EQUITY",
                                        color = Color(0xFFFDE68A), // Champagne Gold text
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = state.winRate,
                                        color = Color.White,
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
                            color = state.gtoAction.bgTint.copy(alpha = 0.16f),
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
                                        color = state.gtoAction.color,
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
                                            tint = Color.White,
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
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                // Opción BB
                                val isBBSelected = state.bettingUnit == BettingUnit.BB
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isBBSelected) Color(0xFF0A6E3D) else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isBBSelected) Color(0xFFD4AF37) else Color(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier
                                        .testTag("floating_select_bb_btn")
                                        .clickable {
                                            PokerGameStateManager.setBettingUnit(BettingUnit.BB)
                                        }
                                ) {
                                    Text(
                                        text = "BB",
                                        color = if (isBBSelected) Color.White else Color(0xFF475569),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                                // Opción Fichas / $
                                val isChipsSelected = state.bettingUnit == BettingUnit.CHIPS
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isChipsSelected) Color(0xFFC59B27) else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isChipsSelected) Color(0xFF92400E) else Color(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier
                                        .testTag("floating_select_chips_btn")
                                        .clickable {
                                            PokerGameStateManager.setBettingUnit(BettingUnit.CHIPS)
                                        }
                                ) {
                                    Text(
                                        text = "Fichas ($)",
                                        color = if (isChipsSelected) Color.White else Color(0xFF475569),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Botón de re-analizar (Casino Monaco Emerald)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0A6E3D),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4AF37)),
                                shadowElevation = 2.dp,
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
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Re-analizar",
                                        color = Color.White,
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
        color = item.iconColor.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, item.iconColor.copy(alpha = 0.5f))
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
                color = Color(0xFF1E293B),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
