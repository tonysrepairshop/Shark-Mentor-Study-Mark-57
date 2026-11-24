package com.example.sharkmentor.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM, DARK, LIGHT, CIRCADIAN
}

fun isCircadianNightHour(hourOfDay: Int): Boolean {
    val safeHour = ((hourOfDay % 24) + 24) % 24
    return safeHour >= 20 || safeHour < 7
}

/**
 * Process-level singleton for persisting and broadcasting user theme preferences and Zen Mode.
 *
 * Backed by [SharedPreferences] — no DataStore dependency required.
 * Exposes state as [StateFlow] so [androidx.compose.runtime.collectAsState] drives instant theme updates.
 */
class ThemePreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Dark / light mode ─────────────────────────────────────────────────────
    private val _themeMode = MutableStateFlow(
        runCatching {
            AppThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)!!)
        }.getOrDefault(AppThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    // ── Color palette ─────────────────────────────────────────────────────────
    private val _palette = MutableStateFlow(
        runCatching {
            ThemePalette.valueOf(prefs.getString(KEY_PALETTE, ThemePalette.OCEAN.name)!!)
        }.getOrDefault(ThemePalette.OCEAN)
    )
    val palette: StateFlow<ThemePalette> = _palette.asStateFlow()

    fun setPalette(p: ThemePalette) {
        prefs.edit().putString(KEY_PALETTE, p.name).apply()
        _palette.value = p
    }

    // ── Pure Focus Zen Mode ───────────────────────────────────────────────────
    private val _isZenMode = MutableStateFlow(
        prefs.getBoolean(KEY_ZEN_MODE, false)
    )
    val isZenMode: StateFlow<Boolean> = _isZenMode.asStateFlow()

    fun setZenMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ZEN_MODE, enabled).apply()
        _isZenMode.value = enabled
    }

    // ── Companion (singleton gate) ────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME   = "sharkmentor_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PALETTE    = "theme_palette"
        private const val KEY_ZEN_MODE   = "is_zen_mode"

        @Volatile private var INSTANCE: ThemePreferences? = null

        fun initialize(context: Context): ThemePreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferences(context.applicationContext).also { INSTANCE = it }
            }

        val instance: ThemePreferences
            get() = INSTANCE
                ?: error("ThemePreferences.initialize(context) must be called in Application.onCreate()")
    }
}
