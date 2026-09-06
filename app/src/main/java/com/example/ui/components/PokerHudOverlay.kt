package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CardSuit
import com.example.data.GtoAction
import com.example.data.PokerAnalysisResult
import com.example.data.Street

@Composable
fun PokerHudOverlay(
    result: PokerAnalysisResult?,
    isAnalyzing: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Color(0xFF1E3A2B), RoundedCornerShape(16.dp))
            .testTag("poker_hud_overlay"),
        color = Color(0xFF0F1D16),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title & Latency chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAnalyzing) Color(0xFFFFD700) else Color(0xFF00E676))
                    )
                    Text(
                        text = if (isAnalyzing) "Analizando con Gemini..." else "Análisis GTO en Tiempo Real",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (result != null && !isAnalyzing) {
                    val isSubTwoSeconds = result.latencyMs < 2000L
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSubTwoSeconds) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = if (isSubTwoSeconds) Color(0xFF00E676) else Color(0xFFFFB74D),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${result.latencyMs} ms",
                            color = if (isSubTwoSeconds) Color(0xFF00E676) else Color(0xFFFFB74D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isAnalyzing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E676),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Evaluando probabilidades y outs matemáticos...",
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp
                    )
                }
            } else if (result != null) {
                // 1. GTO Action Hero Banner
                GtoActionBanner(action = result.gtoAction)

                // 2. Cards Section: Hero vs Community Board
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Hero Cards
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Tus Cartas (Hero)",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (result.holeCards.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                result.holeCards.forEach { card ->
                                    PokerCardView(card = card, cardWidth = 44.dp, cardHeight = 62.dp)
                                }
                            }
                        } else {
                            Text(
                                text = "Sin cartas detectadas",
                                color = Color(0xFF6B7280),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Board Cards
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Mesa (Comunitarias)",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (result.communityCards.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                result.communityCards.forEach { card ->
                                    PokerCardView(card = card, cardWidth = 36.dp, cardHeight = 52.dp)
                                }
                            }
                        } else {
                            Text(
                                text = "Preflop (Mesa vacía)",
                                color = Color(0xFF6B7280),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 3. Equity / Win % Progress Bar
                if (result.winEquity != null) {
                    val rawVal = result.winEquity.replace("%", "").trim().toFloatOrNull() ?: 50f
                    val progressFloat = (rawVal / 100f).coerceIn(0f, 1f)
                    val animatedProgress by animateFloatAsState(targetValue = progressFloat, label = "equity")

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (result.street == Street.PREFLOP) "Preflop Equity" else "Win % (Probabilidad de Ganar)",
                                color = Color(0xFFE5E7EB),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = result.winEquity,
                                color = Color(0xFFFFD700),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (progressFloat > 0.5f) Color(0xFF00E676) else Color(0xFF3B82F6),
                            trackColor = Color(0xFF1F2937),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                // 4. Mathematical Outs & Project Draws
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Outs badge
                    if (result.totalOuts != null || !result.outsDetail.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF152A20),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E4632))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "OUTS MATEMÁTICOS",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = result.outsDetail ?: "${result.totalOuts} Outs",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Draw Project
                    if (!result.drawText.isNullOrBlank() || result.drawProjects.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF172433),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A5F))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "PROYECTO ACTUAL",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = result.drawText ?: result.drawProjects.firstOrNull()?.title ?: "Ninguno",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Position if available
                if (!result.position.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Posición:",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp
                        )
                        Text(
                            text = result.position,
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!result.errorMessage.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3B1E1E))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = result.errorMessage,
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Ninguna jugada analizada aún",
                        color = Color(0xFF9CA3AF),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Pulsa 'Capturar y Evaluar con Gemini' para analizar la mesa",
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GtoActionBanner(
    action: GtoAction,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = action.bgTint,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, action.color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RECOMENDACIÓN GTO",
                    color = action.color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = action.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(action.color)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when (action) {
                        GtoAction.FOLD -> "RETIRARSE"
                        GtoAction.CHECK -> "PASAR"
                        GtoAction.CALL -> "PAGAR"
                        GtoAction.BET -> "APOSTAR"
                        GtoAction.RAISE -> "SUBIR"
                        GtoAction.THREE_BET -> "RE-SUBIR (3-BET)"
                        GtoAction.ALL_IN -> "TODO ADENTRO"
                        GtoAction.ERROR -> "ERROR"
                        GtoAction.RETRY -> "REINTENTAR"
                        GtoAction.UNKNOWN -> "ANALIZANDO"
                    },
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
