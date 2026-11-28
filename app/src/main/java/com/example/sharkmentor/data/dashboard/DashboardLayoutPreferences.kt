package com.example.sharkmentor.data.dashboard

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Widget catalog available on the full-page customizable Mark 77 Dashboard (`Panel`).
 */
enum class DashboardWidgetType(
    val id: String,
    val title: String,
    val description: String,
    val defaultSize: DashboardWidgetSize,
    val defaultEnabled: Boolean = true
) {
    HERO_FOCUS(
        id = "hero_focus",
        title = "Odak Özeti & Seri",
        description = "Karşılama, günlük odak halkası ve Su Serisi",
        defaultSize = DashboardWidgetSize.MEDIUM,
        defaultEnabled = true
    ),
    LIVE_TIMER(
        id = "live_timer",
        title = "Canlı Kronometre & Sayaç",
        description = "Dashboard üzerinden doğrudan Pomodoro veya Kronometre başlat",
        defaultSize = DashboardWidgetSize.MEDIUM,
        defaultEnabled = true
    ),
    SHARK_AI(
        id = "shark_ai",
        title = "Shark Süper Güçler",
        description = "Grafikli çözüm (/detayli), Simülasyon Lab ve Plan Kur",
        defaultSize = DashboardWidgetSize.MEDIUM,
        defaultEnabled = true
    ),
    ERROR_BANK(
        id = "error_bank",
        title = "Hata Bankası (FSRS)",
        description = "Unutma eğrisi tekrar sayısı ve hızlı antrenman",
        defaultSize = DashboardWidgetSize.SMALL,
        defaultEnabled = true
    ),
    STUDIO_NOTES(
        id = "studio_notes",
        title = "Stüdyo (PDF & El Yazısı)",
        description = "Son notlar, kement AI ve el yazısı dönüştürme",
        defaultSize = DashboardWidgetSize.SMALL,
        defaultEnabled = true
    ),
    LIBRARY_BOOKS(
        id = "library_books",
        title = "Kütüphane & Kitaplar",
        description = "Aktif kitabın ilerleme çubuğu ve konu koçu",
        defaultSize = DashboardWidgetSize.SMALL,
        defaultEnabled = true
    ),
    TODAY_PLAN(
        id = "today_plan",
        title = "Günün Planı & Net",
        description = "Sıradaki etüt bloğu veya TYT/AYT net projeksiyonu",
        defaultSize = DashboardWidgetSize.SMALL,
        defaultEnabled = true
    ),
    QUICK_TOOLS(
        id = "quick_tools",
        title = "Hızlı Araçlar & Katalog",
        description = "Analiz, Lig, PACE/KEEP, Zen Odası ve Tümünü Gör kataloğu",
        defaultSize = DashboardWidgetSize.MEDIUM,
        defaultEnabled = true
    );

    companion object {
        fun fromId(raw: String?): DashboardWidgetType? =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Widget footprint sizes selectable inline on the Dashboard:
 * - [SMALL]: Half-width tile (packs 2-per-row on phones, 3-per-row on wide tablets)
 * - [MEDIUM]: Full-width balanced card
 * - [LARGE]: Full-width expanded showcase card
 */
enum class DashboardWidgetSize(val shortLabel: String, val label: String) {
    SMALL("K", "Küçük"),
    MEDIUM("O", "Orta"),
    LARGE("B", "Büyük");

    fun next(): DashboardWidgetSize = when (this) {
        SMALL -> MEDIUM
        MEDIUM -> LARGE
        LARGE -> SMALL
    }

    companion object {
        fun fromName(raw: String?): DashboardWidgetSize? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class DashboardWidgetPlacement(
    val type: DashboardWidgetType,
    val size: DashboardWidgetSize = type.defaultSize,
    val enabled: Boolean = type.defaultEnabled
)

data class DashboardLayoutState(
    val placements: List<DashboardWidgetPlacement> = defaultPlacements()
) {
    val visiblePlacements: List<DashboardWidgetPlacement>
        get() = normalizePlacements(placements).filter { it.enabled }

    val hiddenPlacements: List<DashboardWidgetPlacement>
        get() = normalizePlacements(placements).filter { !it.enabled }

    fun toggleWidget(type: DashboardWidgetType): DashboardLayoutState {
        val next = normalizePlacements(placements).map { item ->
            if (item.type == type) item.copy(enabled = !item.enabled) else item
        }
        return copy(placements = next)
    }

    fun setSize(type: DashboardWidgetType, size: DashboardWidgetSize): DashboardLayoutState {
        val next = normalizePlacements(placements).map { item ->
            if (item.type == type) item.copy(size = size) else item
        }
        return copy(placements = next)
    }

    fun cycleSize(type: DashboardWidgetType): DashboardLayoutState {
        val next = normalizePlacements(placements).map { item ->
            if (item.type == type) item.copy(size = item.size.next()) else item
        }
        return copy(placements = next)
    }

    fun moveUp(type: DashboardWidgetType): DashboardLayoutState {
        val list = normalizePlacements(placements).toMutableList()
        val idx = list.indexOfFirst { it.type == type }
        if (idx <= 0) return copy(placements = list)
        // Swap with the nearest preceding item that shares the same enabled state if possible
        val targetIdx = ((idx - 1) downTo 0).firstOrNull { list[it].enabled == list[idx].enabled } ?: (idx - 1)
        val tmp = list[idx]
        list[idx] = list[targetIdx]
        list[targetIdx] = tmp
        return copy(placements = list)
    }

    fun moveDown(type: DashboardWidgetType): DashboardLayoutState {
        val list = normalizePlacements(placements).toMutableList()
        val idx = list.indexOfFirst { it.type == type }
        if (idx < 0 || idx >= list.lastIndex) return copy(placements = list)
        val targetIdx = ((idx + 1)..list.lastIndex).firstOrNull { list[it].enabled == list[idx].enabled } ?: (idx + 1)
        val tmp = list[idx]
        list[idx] = list[targetIdx]
        list[targetIdx] = tmp
        return copy(placements = list)
    }

    /**
     * Packs consecutive [DashboardWidgetSize.SMALL] tiles into multi-column rows (`maxSmallPerRow`,
     * 2 on phones / 3 on wide screens) while giving [MEDIUM] and [LARGE] widgets full-width rows.
     */
    fun buildVisualRows(maxSmallPerRow: Int = 2): List<List<DashboardWidgetPlacement>> {
        val safeCols = maxSmallPerRow.coerceIn(1, 3)
        val rows = mutableListOf<List<DashboardWidgetPlacement>>()
        var smallBuffer = mutableListOf<DashboardWidgetPlacement>()

        fun flushBuffer() {
            if (smallBuffer.isNotEmpty()) {
                rows.add(smallBuffer.toList())
                smallBuffer = mutableListOf()
            }
        }

        for (item in visiblePlacements) {
            if (item.size == DashboardWidgetSize.SMALL) {
                smallBuffer.add(item)
                if (smallBuffer.size >= safeCols) {
                    flushBuffer()
                }
            } else {
                flushBuffer()
                rows.add(listOf(item))
            }
        }
        flushBuffer()
        return rows
    }

    companion object {
        fun defaultPlacements(): List<DashboardWidgetPlacement> =
            DashboardWidgetType.entries.map { type ->
                DashboardWidgetPlacement(
                    type = type,
                    size = type.defaultSize,
                    enabled = type.defaultEnabled
                )
            }

        fun normalizePlacements(raw: List<DashboardWidgetPlacement>): List<DashboardWidgetPlacement> {
            val seen = LinkedHashSet<DashboardWidgetType>()
            val result = mutableListOf<DashboardWidgetPlacement>()
            for (item in raw) {
                if (seen.add(item.type)) {
                    result.add(item)
                }
            }
            for (type in DashboardWidgetType.entries) {
                if (seen.add(type)) {
                    result.add(DashboardWidgetPlacement(type, type.defaultSize, type.defaultEnabled))
                }
            }
            return result
        }

        fun serialize(state: DashboardLayoutState): String =
            normalizePlacements(state.placements).joinToString(";") { p ->
                "${p.type.id}:${p.size.name}:${if (p.enabled) "1" else "0"}"
            }

        fun deserialize(raw: String?): DashboardLayoutState {
            if (raw.isNullOrBlank()) return DashboardLayoutState()
            val parsed = raw.split(";").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size < 3) return@mapNotNull null
                val type = DashboardWidgetType.fromId(parts[0]) ?: return@mapNotNull null
                val size = DashboardWidgetSize.fromName(parts[1]) ?: type.defaultSize
                val enabled = parts[2] == "1"
                DashboardWidgetPlacement(type = type, size = size, enabled = enabled)
            }
            return DashboardLayoutState(normalizePlacements(parsed))
        }
    }
}

class DashboardLayoutPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _layout = MutableStateFlow(
        DashboardLayoutState.deserialize(prefs.getString(KEY_LAYOUT_V1, null))
    )
    val layout: StateFlow<DashboardLayoutState> = _layout.asStateFlow()

    private fun save(next: DashboardLayoutState) {
        val normalized = DashboardLayoutState(DashboardLayoutState.normalizePlacements(next.placements))
        prefs.edit().putString(KEY_LAYOUT_V1, DashboardLayoutState.serialize(normalized)).apply()
        _layout.value = normalized
    }

    fun toggleWidget(type: DashboardWidgetType) = save(_layout.value.toggleWidget(type))
    fun setWidgetSize(type: DashboardWidgetType, size: DashboardWidgetSize) = save(_layout.value.setSize(type, size))
    fun cycleWidgetSize(type: DashboardWidgetType) = save(_layout.value.cycleSize(type))
    fun moveWidgetUp(type: DashboardWidgetType) = save(_layout.value.moveUp(type))
    fun moveWidgetDown(type: DashboardWidgetType) = save(_layout.value.moveDown(type))
    fun resetToDefault() = save(DashboardLayoutState())

    companion object {
        private const val PREFS_NAME = "shark_dashboard_layout_v1"
        private const val KEY_LAYOUT_V1 = "dashboard_layout_spec"

        @Volatile
        private var INSTANCE: DashboardLayoutPreferences? = null

        fun getInstance(context: Context): DashboardLayoutPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DashboardLayoutPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
