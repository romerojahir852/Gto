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

// PALETA 1: Royal Casino Gold, Pure White & Obsidian Black (Estilo Claro de Lujo)
val LightPokerColors = PokerThemeColors(
    isDark = false,
    background = Color(0xFFF8F9FA), // Blanco perla limpio de casino
    surface = Color(0xFFFFFFFF),    // Blanco puro para naipes y tarjetas
    surfaceVariant = Color(0xFFF3F4F6), // Contraste suave marfil
    surfaceMuted = Color(0xFFECEFF1),
    surfaceSubtle = Color(0xFFF8FAFC),
    border = Color(0xFFD4AF37),     // Oro Casino Real (Luxury Gold)
    borderSubtle = Color(0xFFE5E7EB), // Borde suave sutil
    borderLight = Color(0xFFF1F5F9),
    borderWidth = 1.5.dp,
    textPrimary = Color(0xFF111827), // Negro obsidiana elegante
    textSecondary = Color(0xFF4B5563),
    textMuted = Color(0xFF9CA3AF),
    accent = Color(0xFFD4AF37),      // Oro Real Casino
    accentGreen = Color(0xFF0D6838), // Verde Fieltro Casino para victorias y outs
    accentGreenBg = Color(0xFFECFDF5),
    accentGold = Color(0xFFD4AF37),   // Oro Real Casino
    accentGoldBg = Color(0xFFFEF3C7),
    accentBlue = Color(0xFF1D4ED8),   // Azul Zafiro Real (Diamantes)
    accentBlueBg = Color(0xFFEFF6FF),
    accentRed = Color(0xFFDC2626),    // Rojo Carmesí Casino (Corazones)
    accentRedBg = Color(0xFFFEE2E2),
    accentSelected = Color(0xFF111827), // Negro obsidiana para selección de alto impacto
    accentSelectedText = Color(0xFFFFD700), // Texto oro brillante sobre negro
    cardFaceBg = Color(0xFFFFFFFF),
    cardFaceBorder = Color(0xFFD4AF37)
)

// PALETA 2: Modo Oscuro Elegante (Obsidiana y Oro Casino)
val DarkPokerColors = PokerThemeColors(
    isDark = true,
    background = Color(0xFF0D0F14), // Negro terciopelo obsidiana
    surface = Color(0xFF161B24),    // Superficie carbón elegante
    surfaceVariant = Color(0xFF1F2633),
    surfaceMuted = Color(0xFF1A202C),
    surfaceSubtle = Color(0xFF242C3D),
    border = Color(0xFFD4AF37),     // Oro Casino Real
    borderSubtle = Color(0xFF2D3748),
    borderLight = Color(0xFF374151),
    borderWidth = 1.5.dp,
    textPrimary = Color(0xFFF9FAFB),
    textSecondary = Color(0xFF9CA3AF),
    textMuted = Color(0xFF6B7280),
    accent = Color(0xFFD4AF37),
    accentGreen = Color(0xFF10B981),
    accentGreenBg = Color(0xFF064E3B),
    accentGold = Color(0xFFFFD700),
    accentGoldBg = Color(0xFF451A03),
    accentBlue = Color(0xFF38BDF8),
    accentBlueBg = Color(0xFF0C4A6E),
    accentRed = Color(0xFFEF4444),
    accentRedBg = Color(0xFF450A0A),
    accentSelected = Color(0xFFD4AF37), // Oro seleccionado en modo oscuro
    accentSelectedText = Color(0xFF000000), // Texto negro sobre oro
    cardFaceBg = Color(0xFFFFFFFF),
    cardFaceBorder = Color(0xFFD4AF37)
)

// Compatibilidad con variables estáticas legadas
val BgWhite = Color(0xFFF8F9FA)
val BgSoft = Color(0xFFF3F4F6)
val Ink = Color(0xFF111827)
val Ink2 = Color(0xFF4B5563)
val Ink3 = Color(0xFF9CA3AF)
val Line = Color(0xFFD4AF37)
val Line2 = Color(0xFFE5E7EB)

val PokerGreenPrimary = Color(0xFF0D6838)
val PokerGreenSecondary = Color(0xFF10B981)
val PokerGold = Color(0xFFD4AF37)
val PokerCrimson = Color(0xFFDC2626)

val PokerDarkBg = BgWhite
val PokerDarkSurface = BgSoft
val PokerDarkSurfaceVariant = BgWhite
val PokerTextPrimary = Ink
val PokerTextSecondary = Ink2
