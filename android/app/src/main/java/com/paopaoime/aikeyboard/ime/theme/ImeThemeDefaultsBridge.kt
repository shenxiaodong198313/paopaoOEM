package com.paopaoime.aikeyboard.ime.theme

import android.content.Context
import androidx.preference.PreferenceManager

object ImeThemeDefaultsBridge {
    private const val KEY_MIGRATION_APPLIED = "paopao_ime_theme_defaults_v1_applied"
    private const val KEY_KEY_BORDER = "key_border"
    private const val KEY_KEY_RIPPLE_EFFECT = "key_ripple_effect"
    private const val KEY_FOLLOW_SYSTEM_DARK_MODE = "follow_system_dark_mode"

    fun ensureDefaults(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val migrationApplied = prefs.getBoolean(KEY_MIGRATION_APPLIED, false)
        if (!migrationApplied) {
            val looksLikeLegacyDefaults =
                prefs.getBoolean(KEY_KEY_BORDER, false).not() &&
                    prefs.getBoolean(KEY_KEY_RIPPLE_EFFECT, false).not() &&
                    prefs.getBoolean(KEY_FOLLOW_SYSTEM_DARK_MODE, true)
            prefs.edit().apply {
                if (looksLikeLegacyDefaults || !prefs.contains(KEY_KEY_BORDER)) {
                    putBoolean(KEY_KEY_BORDER, true)
                }
                if (looksLikeLegacyDefaults || !prefs.contains(KEY_KEY_RIPPLE_EFFECT)) {
                    putBoolean(KEY_KEY_RIPPLE_EFFECT, true)
                }
                if (looksLikeLegacyDefaults || !prefs.contains(KEY_FOLLOW_SYSTEM_DARK_MODE)) {
                    putBoolean(KEY_FOLLOW_SYSTEM_DARK_MODE, false)
                }
                putBoolean(KEY_MIGRATION_APPLIED, true)
                apply()
            }
            return
        }

        if (!prefs.contains(KEY_KEY_BORDER) ||
            !prefs.contains(KEY_KEY_RIPPLE_EFFECT) ||
            !prefs.contains(KEY_FOLLOW_SYSTEM_DARK_MODE)
        ) {
            prefs.edit().apply {
                if (!prefs.contains(KEY_KEY_BORDER)) {
                    putBoolean(KEY_KEY_BORDER, true)
                }
                if (!prefs.contains(KEY_KEY_RIPPLE_EFFECT)) {
                    putBoolean(KEY_KEY_RIPPLE_EFFECT, true)
                }
                if (!prefs.contains(KEY_FOLLOW_SYSTEM_DARK_MODE)) {
                    putBoolean(KEY_FOLLOW_SYSTEM_DARK_MODE, false)
                }
                apply()
            }
        }
    }
}
