package com.example.sharkmentor.core.data

import android.content.Context
import android.content.SharedPreferences
import com.example.sharkmentor.core.theme.SolarScheduleCalculator
import com.example.sharkmentor.core.theme.SolarTimes
import com.example.sharkmentor.core.theme.isMinuteInNightWindow
import com.example.sharkmentor.data.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class AppThemeMode {
    SYSTEM, DARK, LIGHT, CIRCADIAN
}

enum class CircadianScheduleMode {
    LOCATION_SUNSET,
    CUSTOM_TIME
}

enum class BlueLightFilterMode {
    OFF,
    MANUAL_ON,
    LOCATION_SUNSET,
    CUSTOM_TIME
}

fun isCircadianNightHour(hourOfDay: Int): Boolean {
    val safeHour = ((hourOfDay % 24) + 24) % 24
    return safeHour >= 20 || safeHour < 7
}

/**
 * Process-level singleton for persisting and broadcasting user theme preferences,
 * configurable Circadian schedules (location sunrise/sunset vs custom hours),
 * Blue Light Eye Comfort filter, Stopwatch Keep-Screen-On, and Zen Mode.
 */
class ThemePreferences private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Dark / light / circadian mode ─────────────────────────────────────────
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

    // ── Configurable Circadian Schedule ───────────────────────────────────────
    private val _circadianScheduleMode = MutableStateFlow(
        runCatching {
            CircadianScheduleMode.valueOf(
                prefs.getString(KEY_CIRCADIAN_MODE, CircadianScheduleMode.LOCATION_SUNSET.name)!!
            )
        }.getOrDefault(CircadianScheduleMode.LOCATION_SUNSET)
    )
    val circadianScheduleMode: StateFlow<CircadianScheduleMode> = _circadianScheduleMode.asStateFlow()

    private val _circadianStartMinute = MutableStateFlow(
        prefs.getInt(KEY_CIRCADIAN_START_MIN, 20 * 60).coerceIn(0, 1439)
    )
    val circadianStartMinute: StateFlow<Int> = _circadianStartMinute.asStateFlow()

    private val _circadianEndMinute = MutableStateFlow(
        prefs.getInt(KEY_CIRCADIAN_END_MIN, 7 * 60).coerceIn(0, 1439)
    )
    val circadianEndMinute: StateFlow<Int> = _circadianEndMinute.asStateFlow()

    fun setCircadianScheduleMode(mode: CircadianScheduleMode) {
        prefs.edit().putString(KEY_CIRCADIAN_MODE, mode.name).apply()
        _circadianScheduleMode.value = mode
    }

    fun setCircadianCustomWindow(startMinuteOfDay: Int, endMinuteOfDay: Int) {
        val safeStart = ((startMinuteOfDay % 1440) + 1440) % 1440
        val safeEnd = ((endMinuteOfDay % 1440) + 1440) % 1440
        prefs.edit()
            .putInt(KEY_CIRCADIAN_START_MIN, safeStart)
            .putInt(KEY_CIRCADIAN_END_MIN, safeEnd)
            .apply()
        _circadianStartMinute.value = safeStart
        _circadianEndMinute.value = safeEnd
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

    // ── Blue Light Filter / Eye Comfort Mode ("Göz Koruma Modu") ──────────────
    private val _blueLightMode = MutableStateFlow(
        runCatching {
            BlueLightFilterMode.valueOf(
                prefs.getString(KEY_BLUE_LIGHT_MODE, BlueLightFilterMode.OFF.name)!!
            )
        }.getOrDefault(BlueLightFilterMode.OFF)
    )
    val blueLightMode: StateFlow<BlueLightFilterMode> = _blueLightMode.asStateFlow()

    private val _blueLightIntensity = MutableStateFlow(
        prefs.getFloat(KEY_BLUE_LIGHT_INTENSITY, 0.22f).coerceIn(0.08f, 0.48f)
    )
    val blueLightIntensity: StateFlow<Float> = _blueLightIntensity.asStateFlow()

    private val _blueLightStartMinute = MutableStateFlow(
        prefs.getInt(KEY_BLUE_LIGHT_START_MIN, 21 * 60).coerceIn(0, 1439)
    )
    val blueLightStartMinute: StateFlow<Int> = _blueLightStartMinute.asStateFlow()

    private val _blueLightEndMinute = MutableStateFlow(
        prefs.getInt(KEY_BLUE_LIGHT_END_MIN, 7 * 60).coerceIn(0, 1439)
    )
    val blueLightEndMinute: StateFlow<Int> = _blueLightEndMinute.asStateFlow()

    fun setBlueLightMode(mode: BlueLightFilterMode) {
        prefs.edit().putString(KEY_BLUE_LIGHT_MODE, mode.name).apply()
        _blueLightMode.value = mode
    }

    fun setBlueLightIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.08f, 0.48f)
        prefs.edit().putFloat(KEY_BLUE_LIGHT_INTENSITY, clamped).apply()
        _blueLightIntensity.value = clamped
    }

    fun setBlueLightCustomWindow(startMinuteOfDay: Int, endMinuteOfDay: Int) {
        val safeStart = ((startMinuteOfDay % 1440) + 1440) % 1440
        val safeEnd = ((endMinuteOfDay % 1440) + 1440) % 1440
        prefs.edit()
            .putInt(KEY_BLUE_LIGHT_START_MIN, safeStart)
            .putInt(KEY_BLUE_LIGHT_END_MIN, safeEnd)
            .apply()
        _blueLightStartMinute.value = safeStart
        _blueLightEndMinute.value = safeEnd
    }

    // ── Stopwatch-Only Keep Screen Awake ("Kronometrede Ekran Kapanmasın") ───
    private val _keepScreenOnInStopwatch = MutableStateFlow(
        prefs.getBoolean(KEY_KEEP_SCREEN_ON_STOPWATCH, false)
    )
    val keepScreenOnInStopwatch: StateFlow<Boolean> = _keepScreenOnInStopwatch.asStateFlow()

    fun setKeepScreenOnInStopwatch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON_STOPWATCH, enabled).apply()
        _keepScreenOnInStopwatch.value = enabled
    }

    // ── Auto-Hide Bottom Bar after inactivity ("Alt Barı Otomatik Gizle") ────
    private val _autoHideBottomBar = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_HIDE_BOTTOM_BAR, false)
    )
    val autoHideBottomBar: StateFlow<Boolean> = _autoHideBottomBar.asStateFlow()

    fun setAutoHideBottomBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE_BOTTOM_BAR, enabled).apply()
        _autoHideBottomBar.value = enabled
    }

    // ── Video Player Picture-in-Picture ("Video Yüzen Pencere (PiP)") ────────
    private val _videoPipEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_VIDEO_PIP_ENABLED, true)
    )
    val videoPipEnabled: StateFlow<Boolean> = _videoPipEnabled.asStateFlow()

    fun setVideoPipEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIDEO_PIP_ENABLED, enabled).apply()
        _videoPipEnabled.value = enabled
    }

    // ── Live Solar & Window Evaluators ────────────────────────────────────────

    fun resolveTodaySolarTimes(): SolarTimes {
        val (lat, lon) = runCatching {
            WeatherRepository.getInstance(appContext).getEffectiveCoordinates()
        }.getOrDefault(39.7477 to 37.0179)
        return SolarScheduleCalculator.calculateSunriseSunset(
            latitude = lat,
            longitude = lon,
            date = LocalDate.now(),
            zoneId = ZoneId.systemDefault()
        )
    }

    fun isCircadianNightNow(nowMinuteOfDay: Int = LocalTime.now().let { it.hour * 60 + it.minute }): Boolean {
        return when (_circadianScheduleMode.value) {
            CircadianScheduleMode.LOCATION_SUNSET -> {
                val solar = resolveTodaySolarTimes()
                isMinuteInNightWindow(nowMinuteOfDay, solar.sunsetMinuteOfDay, solar.sunriseMinuteOfDay)
            }
            CircadianScheduleMode.CUSTOM_TIME -> {
                isMinuteInNightWindow(nowMinuteOfDay, _circadianStartMinute.value, _circadianEndMinute.value)
            }
        }
    }

    fun isBlueLightFilterActiveNow(nowMinuteOfDay: Int = LocalTime.now().let { it.hour * 60 + it.minute }): Boolean {
        return when (_blueLightMode.value) {
            BlueLightFilterMode.OFF -> false
            BlueLightFilterMode.MANUAL_ON -> true
            BlueLightFilterMode.LOCATION_SUNSET -> {
                val solar = resolveTodaySolarTimes()
                isMinuteInNightWindow(nowMinuteOfDay, solar.sunsetMinuteOfDay, solar.sunriseMinuteOfDay)
            }
            BlueLightFilterMode.CUSTOM_TIME -> {
                isMinuteInNightWindow(nowMinuteOfDay, _blueLightStartMinute.value, _blueLightEndMinute.value)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "sharkmentor_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_CIRCADIAN_MODE = "circadian_schedule_mode"
        private const val KEY_CIRCADIAN_START_MIN = "circadian_start_min"
        private const val KEY_CIRCADIAN_END_MIN = "circadian_end_min"
        private const val KEY_PALETTE = "theme_palette"
        private const val KEY_ZEN_MODE = "is_zen_mode"
        private const val KEY_BLUE_LIGHT_MODE = "blue_light_mode"
        private const val KEY_BLUE_LIGHT_INTENSITY = "blue_light_intensity"
        private const val KEY_BLUE_LIGHT_START_MIN = "blue_light_start_min"
        private const val KEY_BLUE_LIGHT_END_MIN = "blue_light_end_min"
        private const val KEY_KEEP_SCREEN_ON_STOPWATCH = "keep_screen_on_stopwatch"
        private const val KEY_AUTO_HIDE_BOTTOM_BAR = "auto_hide_bottom_bar"
        private const val KEY_VIDEO_PIP_ENABLED = "video_pip_enabled"

        @Volatile
        private var INSTANCE: ThemePreferences? = null

        fun initialize(context: Context): ThemePreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferences(context.applicationContext).also { INSTANCE = it }
            }

        val instance: ThemePreferences
            get() = INSTANCE
                ?: error("ThemePreferences.initialize(context) must be called in Application.onCreate()")
    }
}
