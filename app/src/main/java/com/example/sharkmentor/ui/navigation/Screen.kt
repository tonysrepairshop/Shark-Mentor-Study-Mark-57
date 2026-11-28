package com.example.sharkmentor.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Quick slash action exposed on long-pressing the center SharkAI Core button.
 */
data class SharkCoreQuickAction(
    val command: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

/**
 * Sealed class representing every top-level destination in the app.
 *
 * Each entry carries the [route] string used by [NavHost], a human-readable
 * [label] displayed in the bottom bar, and the [icon] rendered as its tab icon.
 */
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    /** Dashboard Hub (PlansScreen) */
    data object Dashboard : Screen(
        route  = "plans",
        label  = "Plan & Kitap",
        icon   = Icons.Default.CalendarMonth,
    )

    /** Timer-based home / daily focus view. */
    data object Focus : Screen(
        route  = "home",
        label  = "Odak",
        icon   = Icons.Default.Timer,
    )

    /** Resources & Topic Mastery (GoalsScreen) */
    data object Resources : Screen(
        route  = "resources",
        label  = "Kaynaklar",
        icon   = Icons.Default.LibraryBooks,
    )

    /** Focus analytics and productivity metrics / Shark AI Hub. */
    data object Shark : Screen(
        route  = "analytics",
        label  = "SharkAI",
        icon   = Icons.Default.AutoAwesome,
    )

    /** Chronological focus-session history log. */
    data object History : Screen(
        route  = "history",
        label  = "Geçmiş",
        icon   = Icons.Default.History,
    )

    data object DetailedAnalytics : Screen(
        route = "detailed_analytics",
        label = "Detaylı Analiz",
        icon = Icons.Default.BarChart,
    )

    /** Hata Kumbarası / Smart Error Bank */
    data object ErrorBank : Screen(
        route  = "error_bank",
        label  = "Hata",
        icon   = Icons.Default.FitnessCenter,
    )

    /** Flashcard Training Mode (Not in bottom bar) */
    data object ErrorTraining : Screen(
        route  = "error_training",
        label  = "Çalışma Modu",
        icon   = Icons.Default.FolderSpecial,
    )

    data object CoachNotes : Screen(
        route = "coach_notes",
        label = "Koç Mesajları & Notlar",
        icon = Icons.Default.FolderSpecial,
    )

    /** Student ↔ Coach in-app messaging chat */
    data object CoachChat : Screen(
        route = "coach_chat",
        label = "Koçunla Mesajlaş",
        icon = Icons.Default.FolderSpecial,
    )

    data object InstitutionInfo : Screen(
        route = "institution_info",
        label = "Kurum Bilgisi",
        icon = Icons.Default.FolderSpecial,
    )

    /** Institutional Shark Micro-League */
    data object SharkLeague : Screen(
        route = "shark_league",
        label = "Shark Ligi",
        icon = Icons.Default.AutoGraph,
    )

    data object Login : Screen(
        route = "login",
        label = "Giriş Yap",
        icon = Icons.Default.FolderSpecial,
    )

    data object Profile : Screen(
        route = "profile",
        label = "Profil",
        icon = Icons.Default.Person,
    )

    data object Schedule : Screen(
        route = "schedule_screen",
        label = "Program",
        icon = Icons.Default.Event,
    )

    data object Notes : Screen(
        route = "notes_screen",
        label = "Studio",
        icon = Icons.Default.Draw,
    )

    data object PaceKeep : Screen(
        route = "pace_keep",
        label = "PACE / KEEP",
        icon = Icons.Default.Timer,
    )

    companion object {
        const val CENTER_CORE_INDEX = 2

        /** Ordered list used to build the bottom navigation bar (SharkAI centered at index 2). */
        val bottomBarItems: List<Screen>
            get() = listOf(Focus, Dashboard, Shark, Notes, ErrorBank)

        /** Quick mode shortcuts triggered via long-press on the center SharkAI Core button. */
        val sharkCoreQuickActions: List<SharkCoreQuickAction>
            get() = listOf(
                SharkCoreQuickAction(
                    command = "/detayli",
                    title = "Detaylı Çözüm",
                    subtitle = "Grafik & geometri çizimli çözüm",
                    icon = Icons.Default.AutoFixHigh,
                ),
                SharkCoreQuickAction(
                    command = "/simulasyon",
                    title = "Simülasyon",
                    subtitle = "İnteraktif Fen & Tıp laboratuvarı",
                    icon = Icons.Default.Science,
                ),
                SharkCoreQuickAction(
                    command = "/plan",
                    title = "Akıllı Plan",
                    subtitle = "Eksikleri öne alan ders programı",
                    icon = Icons.Default.DateRange,
                ),
            )
    }
}

/**
 * Formats pending ErrorBank due-question count for bottom-bar numeric badges (`1..99+`).
 * Returns `null` when there are no pending due questions (`<= 0`).
 */
fun formatDueBadgeCount(dueCount: Int): String? = when {
    dueCount <= 0 -> null
    dueCount > 99 -> "99+"
    else -> dueCount.toString()
}

/**
 * Returns the center slot index for an odd-sized bottom bar list (or `-1` if empty).
 */
fun bottomBarCenterIndex(items: List<Screen> = Screen.bottomBarItems): Int =
    if (items.isEmpty()) -1 else items.size / 2

/**
 * Returns `true` only when [screen] sits at the dead-center index of an odd-length bar and is [Screen.Shark].
 */
fun isCenterAiCore(
    screen: Screen,
    items: List<Screen> = Screen.bottomBarItems,
): Boolean {
    if (items.size % 2 == 0) return false
    val centerIdx = bottomBarCenterIndex(items)
    return centerIdx >= 0 && items.getOrNull(centerIdx) == screen && screen == Screen.Shark
}

/**
 * Resolves whether [tab] should appear active for [currentRoute].
 * [Screen.Dashboard] ("Plan & Kitap") covers both [Screen.Dashboard] and [Screen.Resources]
 * so switching between Plans and Library keeps bottom-bar context clear without losing [Screen.Resources].
 */
fun isBottomTabSelected(tab: Screen, currentRoute: String?): Boolean {
    if (currentRoute.isNullOrBlank()) return false
    return when (tab) {
        Screen.Dashboard -> currentRoute == Screen.Dashboard.route || currentRoute == Screen.Resources.route
        else -> currentRoute == tab.route
    }
}

/**
 * Builds the prefill prompt string for a SharkAI quick action (trailing space avoids re-opening the `/` menu).
 */
fun buildSharkQuickActionPrompt(command: String): String = "${command.trim()} "

