package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CardSuit
import com.example.data.PokerCard
import com.example.ui.theme.AppTheme

/**
 * CardPickerBottomSheet: Selector modal rápido de 52 cartas en 1 toque.
 *
 * Aplica la regla invariante de Texas Hold'em: cualquier carta que ya se
 * encuentre en la mano del jugador o en la mesa comunitaria queda deshabilitada
 * automáticamente para impedir duplicados y garantizar 100% de integridad GTO.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPickerBottomSheet(
    title: String,
    currentlySelectedCard: PokerCard?,
    usedCards: List<PokerCard>,
    onCardSelected: (PokerCard) -> Unit,
    onClearCard: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = AppTheme.colors
    var selectedSuitFilter by remember { mutableStateOf<CardSuit?>(null) } // null = Todos

    // Generar las 52 cartas estándar
    val ranks = listOf("A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2")
    val suits = listOf(CardSuit.SPADES, CardSuit.HEARTS, CardSuit.DIAMONDS, CardSuit.CLUBS)

    val allCards = remember {
        ranks.flatMap { rank ->
            suits.map { suit -> PokerCard(rank, suit) }
        }
    }

    // Filtrar por palo si hay uno seleccionado
    val displayedCards = remember(selectedSuitFilter) {
        if (selectedSuitFilter == null) {
            allCards
        } else {
            allCards.filter { it.suit == selectedSuitFilter }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.borderSubtle, RoundedCornerShape(2.dp))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header con título y botón cerrar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Toca para asignar en 1 toque (Anti-duplicados)",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector de filtro de palos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SuitFilterChip(
                    label = "Todas",
                    isSelected = selectedSuitFilter == null,
                    onClick = { selectedSuitFilter = null }
                )
                suits.forEach { suit ->
                    SuitFilterChip(
                        label = "${suit.symbol} ${suit.suitName}",
                        suitColor = suit.color,
                        isSelected = selectedSuitFilter == suit,
                        onClick = { selectedSuitFilter = suit }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Grilla de Cartas (4 columnas para fácil lectura táctil)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
            ) {
                items(displayedCards) { card ->
                    // Está en uso si ya está asignada en la mesa Y NO es la carta de este slot
                    val isCurrentSlot = currentlySelectedCard?.rank == card.rank && currentlySelectedCard?.suit == card.suit
                    val isUsedElsewhere = usedCards.any { it.rank == card.rank && it.suit == card.suit } && !isCurrentSlot

                    MiniCardPickerItem(
                        card = card,
                        isSelected = isCurrentSlot,
                        isUsed = isUsedElsewhere,
                        onClick = {
                            if (!isUsedElsewhere) {
                                onCardSelected(card)
                                onDismiss()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón para limpiar slot
            OutlinedButton(
                onClick = {
                    onClearCard()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Eliminar carta del slot",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SuitFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    suitColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = if (isSelected) colors.accentGreen.copy(alpha = 0.15f) else colors.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) colors.accentGreen else colors.borderSubtle
        )
    ) {
        Text(
            text = label,
            color = if (isSelected) colors.accentGreen else (suitColor ?: colors.textSecondary),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MiniCardPickerItem(
    card: PokerCard,
    isSelected: Boolean,
    isUsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val suitColor = if (isUsed) colors.textMuted.copy(alpha = 0.4f) else card.suit.color

    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = !isUsed) { onClick() },
        color = if (isUsed) colors.surfaceVariant.copy(alpha = 0.5f) else colors.cardFaceBg,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colors.accentGreen else if (isUsed) colors.borderSubtle else colors.cardFaceBorder
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = card.displayRank,
                color = suitColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = card.suit.symbol,
                color = suitColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
