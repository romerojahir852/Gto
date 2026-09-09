package com.example.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor reactivo del tema visual de la aplicación.
 * Permite alternar entre:
 * - Modo Blanco Profesional con Bordes Negros (predeterminado)
 * - Modo Oscuro Elegante
 *
 * La preferencia se almacena en SharedPreferences para persistir entre reinicios.
 */
object AppThemeManager {
    private const val PREFS_NAME = "poker_theme_prefs"
    private const val KEY_IS_DARK = "key_is_dark_mode"

    // Modo blanco profesional como predeterminado
    private val _isDark = MutableStateFlow(false)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _isDark.value = prefs.getBoolean(KEY_IS_DARK, false)
            isInitialized = true
        }
    }

    fun toggleTheme(context: Context) {
        val newValue = !_isDark.value
        _isDark.value = newValue
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_DARK, newValue).apply()
    }

    fun setDarkMode(context: Context, dark: Boolean) {
        _isDark.value = dark
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_DARK, dark).apply()
    }
}
