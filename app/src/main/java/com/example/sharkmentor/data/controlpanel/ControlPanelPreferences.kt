package com.example.sharkmentor.data.controlpanel

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Modular, user-customizable blocks rendered inside the right-side Control Panel Drawer.
 */
enum class ControlPanelModule(
    val id: String,
    val title: String,
    val description: String
) {
    DAILY_SUMMARY(
        id = "daily_summary",
        title = "Canlı Günlük Özet",
        description = "Bugünkü odak süresi, seri ve tekrar soruları"
    ),
    STUDY_MACROS(
        id = "study_macros",
        title = "Tek Dokunuş Makrolar",
        description = "Derin Odak, Gece Çalışması ve Normal Mod"
    ),
    QUICK_TILES(
        id = "quick_tiles",
        title = "Hızlı Kontrol Karoları",
        description = "Zen, Tema Modu, Göz Koruma ve Ekran Açık"
    ),
    THEME_STRIP(
        id = "theme_strip",
        title = "Hızlı Tema Paleti",
        description = "Tek dokunuşla tema paleti değiştir"
    ),
    SHORTCUTS(
        id = "shortcuts",
        title = "Hızlı Kısayollar",
        description = "Zen Odası, PACE/KEEP, Hata Bankası, Müzik ve Ayarlar"
    );

    companion object {
        val DEFAULT_ORDER: List<ControlPanelModule> = listOf(
            DAILY_SUMMARY,
            STUDY_MACROS,
            QUICK_TILES,
            THEME_STRIP,
            SHORTCUTS
        )

        fun fromId(id: String?): ControlPanelModule? =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }

        fun normalizeOrder(rawOrder: List<ControlPanelModule>): List<ControlPanelModule> {
            val seen = LinkedHashSet<ControlPanelModule>()
            rawOrder.forEach { seen.add(it) }
            DEFAULT_ORDER.forEach { seen.add(it) }
            return seen.toList()
        }
    }
}

data class ControlPanelSettings(
    val moduleOrder: List<ControlPanelModule> = ControlPanelModule.DEFAULT_ORDER,
    val hiddenModules: Set<ControlPanelModule> = emptySet()
) {
    fun isModuleVisible(module: ControlPanelModule): Boolean = module !in hiddenModules

    fun visibleModulesInOrder(): List<ControlPanelModule> =
        ControlPanelModule.normalizeOrder(moduleOrder).filter { it !in hiddenModules }

    fun toggleVisibility(module: ControlPanelModule): ControlPanelSettings {
        val nextHidden = if (module in hiddenModules) {
            hiddenModules - module
        } else {
            hiddenModules + module
        }
        return copy(hiddenModules = nextHidden)
    }

    fun moveUp(module: ControlPanelModule): ControlPanelSettings {
        val normalized = ControlPanelModule.normalizeOrder(moduleOrder).toMutableList()
        val idx = normalized.indexOf(module)
        if (idx <= 0) return copy(moduleOrder = normalized)
        normalized[idx] = normalized[idx - 1].also { normalized[idx - 1] = normalized[idx] }
        return copy(moduleOrder = normalized)
    }

    fun moveDown(module: ControlPanelModule): ControlPanelSettings {
        val normalized = ControlPanelModule.normalizeOrder(moduleOrder).toMutableList()
        val idx = normalized.indexOf(module)
        if (idx < 0 || idx >= normalized.lastIndex) return copy(moduleOrder = normalized)
        normalized[idx] = normalized[idx + 1].also { normalized[idx + 1] = normalized[idx] }
        return copy(moduleOrder = normalized)
    }
}

/**
 * Process-level SharedPreferences repository for user-customizable Control Panel layout.
 */
class ControlPanelPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ControlPanelSettings> = _settings.asStateFlow()

    private fun loadSettings(): ControlPanelSettings {
        val rawOrder = prefs.getString(KEY_MODULE_ORDER, null)
            ?.split(",")
            ?.mapNotNull { ControlPanelModule.fromId(it) }
            ?: ControlPanelModule.DEFAULT_ORDER

        val rawHidden = prefs.getStringSet(KEY_HIDDEN_MODULES, emptySet())
            ?.mapNotNull { ControlPanelModule.fromId(it) }
            ?.toSet()
            ?: emptySet()

        return ControlPanelSettings(
            moduleOrder = ControlPanelModule.normalizeOrder(rawOrder),
            hiddenModules = rawHidden
        )
    }

    private fun save(next: ControlPanelSettings) {
        val normalized = next.copy(
            moduleOrder = ControlPanelModule.normalizeOrder(next.moduleOrder)
        )
        prefs.edit()
            .putString(KEY_MODULE_ORDER, normalized.moduleOrder.joinToString(",") { it.id })
            .putStringSet(KEY_HIDDEN_MODULES, normalized.hiddenModules.map { it.id }.toSet())
            .apply()
        _settings.value = normalized
    }

    fun toggleModule(module: ControlPanelModule) {
        save(_settings.value.toggleVisibility(module))
    }

    fun moveModuleUp(module: ControlPanelModule) {
        save(_settings.value.moveUp(module))
    }

    fun moveModuleDown(module: ControlPanelModule) {
        save(_settings.value.moveDown(module))
    }

    fun resetToDefault() {
        save(ControlPanelSettings())
    }

    companion object {
        private const val PREFS_NAME = "shark_control_panel_prefs_v1"
        private const val KEY_MODULE_ORDER = "module_order"
        private const val KEY_HIDDEN_MODULES = "hidden_modules"

        @Volatile
        private var INSTANCE: ControlPanelPreferences? = null

        fun isModularControlPanelEnabled(isJarvisUnlocked: Boolean): Boolean = isJarvisUnlocked

        fun getInstance(context: Context): ControlPanelPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ControlPanelPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
