package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PokerAnalysisResult

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GtoHudCard(
    result: PokerAnalysisResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gto_hud_result_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF13221C)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(result.gtoAction.color.copy(alpha = 0.6f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = result.gtoAction.bgTint,
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(result.gtoAction.color)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .testTag("gto_action_badge")
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(result.gtoAction.color, CircleShape)
                        )
                        Text(
                            text = "JUGADA GTO: ${result.gtoAction.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = result.gtoAction.color,
                            letterSpacing = 1.sp
                        )
                    }
                }

                val isUnder2s = result.latencyMs in 1..2000
                val latencyColor = if (isUnder2s) Color(0xFF10B981) else Color(0xFFF59E0B)

                Surface(
                    color = latencyColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(latencyColor.copy(alpha = 0.4f))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Latencia",
                            tint = latencyColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${(result.latencyMs / 100.0).toInt() / 10.0}s",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = latencyColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                CardHandRow(
                    cards = result.holeCards,
                    label = "TUS CARTAS",
                    cardWidth = 44.dp,
                    cardHeight = 64.dp
                )

                CardHandRow(
                    cards = result.communityCards,
                    label = "MESA (BOARD)",
                    cardWidth = 40.dp,
                    cardHeight = 60.dp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val equityValue = result.winEquity ?: "50%"
                val equityNum = Regex("""\d+""").find(equityValue)?.value?.toFloatOrNull() ?: 50f

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0F1B16), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "EQUITY / WIN %",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = equityValue,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF34D399)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (equityNum / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF10B981),
                            trackColor = Color(0xFF1F382E)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0F1B16), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "OUTS CALCULADOS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val outsDisplay = result.totalOuts?.toString() ?: (if (result.outsDetail != null) result.outsDetail else "-")
                        Text(
                            text = outsDisplay ?: "-",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFBBF24)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.outsDetail ?: "Regla del 4 y del 2",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            maxLines = 1
                        )
                    }
                }
            }

            if (result.drawProjects.isNotEmpty() || !result.drawText.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PROYECTOS Y LECTURA MATEMÁTICA:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        result.drawProjects.forEach { draw ->
                            Surface(
                                color = draw.color.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(8.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(draw.color.copy(alpha = 0.5f))
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = draw.icon,
                                        fontSize = 12.sp,
                                        color = draw.color,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = draw.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = draw.color
                                    )
                                }
                            }
                        }

                        if (result.drawProjects.isEmpty() && !result.drawText.isNullOrBlank()) {
                            Surface(
                                color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = result.drawText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF60A5FA)
                                )
                            }
                        }
                    }
                }
            }

            if (!result.position.isNullOrBlank()) {
                Text(
                    text = "Posición en Mesa: ${result.position}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
