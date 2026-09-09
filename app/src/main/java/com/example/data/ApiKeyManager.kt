package com.example.data

import android.content.Context
import com.example.BuildConfig

object ApiKeyManager {
    private const val PREFS_NAME = "gemini_poker_prefs"
    private const val KEY_API_KEY = "gemini_user_api_key"

    @Volatile
    private var cachedApiKey: String? = null

    fun getApiKey(context: Context? = null): String? {
        cachedApiKey?.let { if (it.isNotBlank()) return it }

        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedKey = prefs.getString(KEY_API_KEY, null)?.trim()
            if (!savedKey.isNullOrBlank()) {
                cachedApiKey = savedKey
                return savedKey
            }
        }

        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            cachedApiKey = buildKey
            return buildKey
        }

        return null
    }

    fun saveApiKey(context: Context, key: String) {
        val trimmed = key.trim()
        cachedApiKey = trimmed
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, trimmed).apply()
    }

    fun hasApiKey(context: Context? = null): Boolean {
        return getApiKey(context) != null
    }

    fun isConfigured(context: Context? = null): Boolean {
        return hasApiKey(context)
    }

    fun getMaskedKey(context: Context? = null): String {
        val key = getApiKey(context) ?: return ""
        return if (key.length > 8) {
            "${key.take(6)}...${key.takeLast(4)}"
        } else {
            "****"
        }
    }

    fun clearApiKey(context: Context) {
        cachedApiKey = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_API_KEY).apply()
    }
}
