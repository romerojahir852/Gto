package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PokerColorScheme = lightColorScheme(
    primary = PokerGreenPrimary,
    onPrimary = BgWhite,
    secondary = PokerGold,
    onSecondary = BgWhite,
    background = PokerDarkBg,
    onBackground = PokerTextPrimary,
    surface = PokerDarkSurface,
    onSurface = PokerTextPrimary,
    surfaceVariant = PokerDarkSurfaceVariant,
    onSurfaceVariant = PokerTextSecondary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PokerColorScheme,
        typography = Typography,
        content = content
    )
}
