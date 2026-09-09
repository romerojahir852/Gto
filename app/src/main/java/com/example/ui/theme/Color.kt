package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens de color para el sistema de diseño de Poker GTO Vision:
 * 1. Modo Blanco Profesional con Bordes Negros (predeterminado)
 * 2. Modo Oscuro Elegante
 */
data class PokerThemeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceMuted: Color,
    val surfaceSubtle: Color,
    val border: Color,
    val borderSubtle: Color,
    val borderLight: Color,
    val borderWidth: Dp = 1.5.dp,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentGreen: Color,
    val accentGreenBg: Color,
    val accentGold: Color,
    val accentGoldBg: Color,
    val accentBlue: Color,
    val accentBlueBg: Color,
    val accentRed: Color,
    val accentRedBg: Color,
    val accentSelected: Color,
    val accentSelectedText: Color,
    val cardFaceBg: Color = Color.White,
    val cardFaceBorder: Color
) {
    val bg: Color get() = background
}

// PALETA 1: Blanco Profesional con Bordes Negros (Estilo Stripe / Linear Pro)
val LightPokerColors = PokerThemeColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF8FAFC),
    surfaceMuted = Color(0xFFF1F5F9),
    surfaceSubtle = Color(0xFFF8FAFC),
    border = Color(0xFF000000), // Bordes negros nítidos
    borderSubtle = Color(0xFFCBD5E1),
    borderLight = Color(0xFFE2E8F0),
    borderWidth = 1.5.dp,
    textPrimary = Color(0xFF000000), // Negro profundo de máxima nitidez
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    accent = Color(0xFF059669),
    accentGreen = Color(0xFF059669),
    accentGreenBg = Color(0xFFECFDF5),
    accentGold = Color(0xFFD97706),
    accentGoldBg = Color(0xFFFEF3C7),
    accentBlue = Color(0xFF2563EB),
    accentBlueBg = Color(0xFFEFF6FF),
    accentRed = Color(0xFFDC2626),
    accentRedBg = Color(0xFFFEF2F2),
    accentSelected = Color(0xFF000000),
    accentSelectedText = Color(0xFFFFFFFF),
    cardFaceBg = Color(0xFFFFFFFF),
    cardFaceBorder = Color(0xFF000000)
)

// PALETA 2: Modo Oscuro Elegante (Obsidiana de lujo)
val DarkPokerColors = PokerThemeColors(
    isDark = true,
    background = Color(0xFF090D14),
    surface = Color(0xFF111722),
    surfaceVariant = Color(0xFF1A2332),
    surfaceMuted = Color(0xFF162030),
    surfaceSubtle = Color(0xFF1F2B3D),
    border = Color(0xFF2A3B52),
    borderSubtle = Color(0xFF1E2D40),
    borderLight = Color(0xFF33435C),
    borderWidth = 1.5.dp,
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    accent = Color(0xFF00E676),
    accentGreen = Color(0xFF00E676),
    accentGreenBg = Color(0xFF0F2B1D),
    accentGold = Color(0xFFFFD700),
    accentGoldBg = Color(0xFF2B240F),
    accentBlue = Color(0xFF38BDF8),
    accentBlueBg = Color(0xFF0F2338),
    accentRed = Color(0xFFEF4444),
    accentRedBg = Color(0xFF2B1214),
    accentSelected = Color(0xFF00E676),
    accentSelectedText = Color(0xFF000000),
    cardFaceBg = Color(0xFFFFFFFF),
    cardFaceBorder = Color(0xFF334155)
)

// Compatibilidad con variables estáticas legadas
val BgWhite = Color(0xFFFFFFFF)
val BgSoft = Color(0xFFF8F9FA)
val Ink = Color(0xFF000000)
val Ink2 = Color(0xFF1E293B)
val Ink3 = Color(0xFF475569)
val Line = Color(0xFF000000)
val Line2 = Color(0xFF1E293B)

val PokerGreenPrimary = Color(0xFF059669)
val PokerGreenSecondary = Color(0xFF10B981)
val PokerGold = Color(0xFFD97706)
val PokerCrimson = Color(0xFFDC2626)

val PokerDarkBg = BgWhite
val PokerDarkSurface = BgSoft
val PokerDarkSurfaceVariant = BgWhite
val PokerTextPrimary = Ink
val PokerTextSecondary = Ink2
