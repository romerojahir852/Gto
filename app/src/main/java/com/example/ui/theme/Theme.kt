package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PokerColorScheme = darkColorScheme(
    primary = PokerGreenPrimary,
    onPrimary = Color(0xFF04130B),
    secondary = PokerGold,
    onSecondary = Color(0xFF1E1500),
    tertiary = PokerCrimson,
    onTertiary = Color.White,
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
