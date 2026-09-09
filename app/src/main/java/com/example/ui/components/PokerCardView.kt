package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CardSuit
import com.example.data.PokerCard
import com.example.ui.theme.AppTheme

@Composable
fun PokerCardView(
    card: PokerCard,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 48.dp,
    cardHeight: Dp = 68.dp,
    isSelected: Boolean = false
) {
    val colors = AppTheme.colors
    val suitColor = card.suit.color

    Surface(
        modifier = modifier
            .size(width = cardWidth, height = cardHeight)
            .shadow(if (colors.isDark) 0.dp else 3.dp, RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = if (isSelected) colors.accentGreen else colors.cardFaceBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .testTag("poker_card_${card.displayRank}_${card.suit.name}"),
        color = colors.cardFaceBg
    ) {
        Column(
            modifier = Modifier
                .padding(3.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Rank top-left
            Text(
                text = card.displayRank,
                color = suitColor,
                fontSize = (cardHeight.value * 0.26f).sp,
                fontWeight = FontWeight.Black,
                lineHeight = (cardHeight.value * 0.26f).sp
            )

            // Center large suit symbol
            Text(
                text = card.suit.symbol,
                color = suitColor,
                fontSize = (cardHeight.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PokerCardBadge(
    card: PokerCard,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.cardFaceBg)
            .border(1.dp, colors.cardFaceBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = card.displayRank,
            color = card.suit.color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = card.suit.symbol,
            color = card.suit.color,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun CardHandRow(
    cards: List<PokerCard>,
    modifier: Modifier = Modifier,
    label: String = "",
    cardWidth: Dp = 44.dp,
    cardHeight: Dp = 64.dp
) {
    val colors = AppTheme.colors

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .size(width = cardWidth * 2 + 6.dp, height = cardHeight)
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.5.dp, colors.border, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin cartas",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            } else {
                cards.forEach { card ->
                    PokerCardView(
                        card = card,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight
                    )
                }
            }
        }
    }
}
