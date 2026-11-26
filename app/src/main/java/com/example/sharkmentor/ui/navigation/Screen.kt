package com.example.sharkmentor.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.ui.graphics.vector.ImageVector

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
        label  = "Planlar",
        icon   = Icons.Default.CalendarMonth,
    )

    /** Timer-based home / daily focus view. */
    data object Focus : Screen(
        route  = "home",
        label  = "Masa",
        icon   = Icons.Default.Timer,
    )

    /** Resources & Topic Mastery (GoalsScreen) */
    data object Resources : Screen(
        route  = "resources",
        label  = "Kaynaklar",
        icon   = Icons.Default.LibraryBooks,
    )

    /** Focus analytics and productivity metrics. */
    data object Shark : Screen(
        route  = "analytics",
        label  = "SharkAI",
        icon   = Icons.Default.AutoGraph, // or Psychology if preferred
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
        label  = "Antrenmanlar",
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
        label = "SharkStudio",
        icon = Icons.Default.Draw,
    )

    data object PaceKeep : Screen(
        route = "pace_keep",
        label = "PACE / KEEP",
        icon = Icons.Default.Timer,
    )

    companion object {
        /** Ordered list used to build the bottom navigation bar. */
        val bottomBarItems: List<Screen>
            get() = listOf(Focus, Dashboard, Notes, Shark, ErrorBank)
    }
}
