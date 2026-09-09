package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalAppThemeColors = staticCompositionLocalOf { LightPokerColors }

object AppTheme {
    val colors: PokerThemeColors
        @Composable
        get() = LocalAppThemeColors.current
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    AppThemeManager.init(context)
    val isDark = AppThemeManager.isDark.collectAsState().value
    val colors = if (isDark) DarkPokerColors else LightPokerColors

    val materialColorScheme = if (isDark) {
        darkColorScheme(
            primary = colors.accentGreen,
            onPrimary = Color.Black,
            secondary = colors.accentGold,
            onSecondary = Color.Black,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary
        )
    } else {
        lightColorScheme(
            primary = colors.border,
            onPrimary = Color.White,
            secondary = colors.accentGold,
            onSecondary = Color.White,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary
        )
    }

    CompositionLocalProvider(LocalAppThemeColors provides colors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}
