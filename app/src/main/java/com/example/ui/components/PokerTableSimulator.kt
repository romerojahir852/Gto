package com.example.ui.components

import com.example.ui.theme.BgWhite
import com.example.ui.theme.BgSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.Ink2
import com.example.ui.theme.Ink3
import com.example.ui.theme.Line
import com.example.ui.theme.Line2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.example.data.CardSuit
import com.example.data.PokerCard
import com.example.data.Street

data class PokerHandPreset(
    val title: String,
    val subtitle: String,
    val street: Street,
    val holeCards: List<PokerCard>,
    val communityCards: List<PokerCard>,
    val potSize: String,
    val position: String
) {
    val name: String get() = title
    val description: String get() = subtitle
    fun renderBitmap(): Bitmap = renderPresetToBitmap(this)
}

val PRESET_HANDS = listOf(
    PokerHandPreset(
        title = "Flop: Flush Draw + Gutshot",
        subtitle = "Q♥ J♥ con 10♥ 9♣ 2♥ (15 Outs)",
        street = Street.POSTFLOP,
        holeCards = listOf(PokerCard("Q", CardSuit.HEARTS), PokerCard("J", CardSuit.HEARTS)),
        communityCards = listOf(PokerCard("10", CardSuit.HEARTS), PokerCard("9", CardSuit.CLUBS), PokerCard("2", CardSuit.HEARTS)),
        potSize = "14.5 BB",
        position = "BTN"
    ),
    PokerHandPreset(
        title = "Flop: Gutshot Puro (Escalera Int.)",
        subtitle = "A♥ 5♠ con 4♦ 3♣ K♥ (4 Outs al 2)",
        street = Street.POSTFLOP,
        holeCards = listOf(PokerCard("A", CardSuit.HEARTS), PokerCard("5", CardSuit.SPADES)),
        communityCards = listOf(PokerCard("4", CardSuit.DIAMONDS), PokerCard("3", CardSuit.CLUBS), PokerCard("K", CardSuit.HEARTS)),
        potSize = "8.0 BB",
        position = "CO"
    ),
    PokerHandPreset(
        title = "Preflop: Big Slick (AKs)",
        subtitle = "A♠ K♠ en BTN frente a Open",
        street = Street.PREFLOP,
        holeCards = listOf(PokerCard("A", CardSuit.SPADES), PokerCard("K", CardSuit.SPADES)),
        communityCards = emptyList(),
        potSize = "4.5 BB",
        position = "BTN"
    ),
    PokerHandPreset(
        title = "Turn: Escalera Abierta (OESD)",
        subtitle = "8♠ 9♦ con 10♣ 7♥ 2♠ K♦ (8 Outs)",
        street = Street.POSTFLOP,
        holeCards = listOf(PokerCard("8", CardSuit.SPADES), PokerCard("9", CardSuit.DIAMONDS)),
        communityCards = listOf(PokerCard("10", CardSuit.CLUBS), PokerCard("7", CardSuit.HEARTS), PokerCard("2", CardSuit.SPADES), PokerCard("K", CardSuit.DIAMONDS)),
        potSize = "22.0 BB",
        position = "SB"
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PokerTableSimulator(
    selectedPreset: PokerHandPreset,
    onPresetSelected: (PokerHandPreset) -> Unit,
    onAnalyzePreset: (Bitmap, Street) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("poker_table_simulator_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFCBD8CD))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                        tint = Color(0xFFC59B27),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Simulador de Mesa de Póker",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                }

                Surface(
                    color = Color(0xFFE8F5EC),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0A6E3D).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "TEST BENCH",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A6E3D)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PRESET_HANDS.forEach { preset ->
                    val isSelected = preset.title == selectedPreset.title
                    Surface(
                        color = if (isSelected) Color(0xFF0A6E3D) else Color(0xFFF1F6F1),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFFC59B27) else Color(0xFFCBD8CD)
                        ),
                        modifier = Modifier
                            .clickable { onPresetSelected(preset) }
                            .testTag("preset_${preset.street.name.lowercase()}")
                    ) {
                        Text(
                            text = preset.title,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Ink
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .clip(RoundedCornerShape(92.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0F6840), // Centro fieltro verde esmeralda brillante
                                Color(0xFF0A4F30), // Verde fieltro medio
                                Color(0xFF063B23)  // Verde oscuro profundo
                            )
                        )
                    )
                    .border(5.dp, Color(0xFF382215), RoundedCornerShape(92.dp))
                    .border(1.5.dp, Color(0xFFD4AF37), RoundedCornerShape(92.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF1E1103).copy(alpha = 0.8f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4AF37))
                        ) {
                            Text(
                                text = "BOTE: ${selectedPreset.potSize}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        }
                        Surface(
                            color = Color(0xFF03162E).copy(alpha = 0.8f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF60A5FA))
                        ) {
                            Text(
                                text = "POS: ${selectedPreset.position}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF93C5FD)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    CardHandRow(
                        cards = selectedPreset.communityCards,
                        cardWidth = 36.dp,
                        cardHeight = 52.dp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFF0A6E3D).copy(alpha = 0.4f),
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4AF37))
                        ) {
                            Text(
                                text = "TÚ",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFD700)
                            )
                        }
                        CardHandRow(
                            cards = selectedPreset.holeCards,
                            cardWidth = 38.dp,
                            cardHeight = 54.dp
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val bitmap = renderPresetToBitmap(selectedPreset)
                    onAnalyzePreset(bitmap, selectedPreset.street)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analyze_simulator_preset_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A6E3D)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF06140E)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Analizar Mano con Asistente GTO",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF06140E)
                )
            }
        }
    }
}

fun renderPresetToBitmap(preset: PokerHandPreset): Bitmap {
    val width = 720
    val height = 480
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val feltPaint = Paint().apply {
        color = android.graphics.Color.rgb(15, 38, 27)
        isAntiAlias = true
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), feltPaint)

    val railPaint = Paint().apply {
        color = android.graphics.Color.rgb(74, 50, 22)
        style = Paint.Style.STROKE
        strokeWidth = 24f
        isAntiAlias = true
    }
    val innerOval = RectF(30f, 30f, (width - 30).toFloat(), (height - 30).toFloat())
    canvas.drawRoundRect(innerOval, 120f, 120f, railPaint)

    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("TEXAS HOLD'EM - ${preset.position} (Bote: ${preset.potSize})", (width / 2).toFloat(), 80f, textPaint)

    val cardPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val cardStroke = Paint().apply {
        color = android.graphics.Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    val cardTextPaint = Paint().apply {
        textSize = 28f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    var startX = 140f
    val boardY = 150f
    preset.communityCards.forEach { card ->
        val cardRect = RectF(startX, boardY, startX + 70f, boardY + 100f)
        canvas.drawRoundRect(cardRect, 10f, 10f, cardPaint)
        canvas.drawRoundRect(cardRect, 10f, 10f, cardStroke)

        cardTextPaint.color = if (card.suit == CardSuit.HEARTS || card.suit == CardSuit.DIAMONDS) {
            android.graphics.Color.RED
        } else {
            android.graphics.Color.BLACK
        }
        canvas.drawText("${card.displayRank}${card.suit.symbol}", startX + 35f, boardY + 60f, cardTextPaint)
        startX += 85f
    }

    var holeX = 260f
    val holeY = 300f
    preset.holeCards.forEach { card ->
        val cardRect = RectF(holeX, holeY, holeX + 80f, holeY + 115f)
        canvas.drawRoundRect(cardRect, 12f, 12f, cardPaint)
        canvas.drawRoundRect(cardRect, 12f, 12f, cardStroke)

        cardTextPaint.color = if (card.suit == CardSuit.HEARTS || card.suit == CardSuit.DIAMONDS) {
            android.graphics.Color.RED
        } else {
            android.graphics.Color.BLACK
        }
        cardTextPaint.textSize = 34f
        canvas.drawText("${card.displayRank}${card.suit.symbol}", holeX + 40f, holeY + 68f, cardTextPaint)
        holeX += 100f
    }

    return bitmap
}
