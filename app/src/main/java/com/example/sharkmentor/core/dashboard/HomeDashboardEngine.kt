package com.example.sharkmentor.core.dashboard

import com.example.sharkmentor.data.local.entity.NetProjectionEntity
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.StudyTaskEntity
import kotlin.math.roundToInt

/**
 * Dual-view landing mode for the Home screen:
 * - [DASHBOARD]: Study OS Command Center showcasing focus goal, SharkAI visual solver & simulation lab,
 *   FSRS Error Bank, SharkStudio Notes/PDF, Library progress, and Today's Plan in one calm viewport.
 * - [FOCUS_DESK]: Distraction-free classic timer & music deck (automatically entered when starting focus).
 */
enum class HomeLandingTab(val label: String) {
    DASHBOARD("Genel Bakış"),
    FOCUS_DESK("Odak Masası")
}

data class HomeDashboardSnapshot(
    val greetingTitle: String,
    val greetingSubtitle: String,
    val todayFocusMinutes: Int,
    val todayTargetMinutes: Int,
    val focusProgressFraction: Float,
    val focusProgressPercent: Int,
    val streakDays: Int,
    val streakGoalDays: Int,
    val dueErrorCount: Int,
    val totalErrorCount: Int,
    val errorCardTitle: String,
    val errorCardSubtitle: String,
    val errorCardBadge: String,
    val errorCardHighlighted: Boolean,
    val studioCardTitle: String,
    val studioCardSubtitle: String,
    val studioCardBadge: String,
    val libraryCardTitle: String,
    val libraryCardSubtitle: String,
    val libraryCardBadge: String,
    val libraryProgressFraction: Float?,
    val planCardTitle: String,
    val planCardSubtitle: String,
    val planCardBadge: String,
    val planCardRoute: String,
    val isColdStart: Boolean
)

/**
 * Pure-Kotlin snapshot builder for SharkMentor's Study OS Dashboard landing screen.
 * Handles both rich live Room data and engaging Cold-Start discovery states when a brand-new
 * student opens the app with empty tables.
 */
object HomeDashboardEngine {

    fun formatDurationCompact(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        return if (safe >= 60) {
            val h = safe / 60
            val m = safe % 60
            if (m == 0) "${h}s" else "${h}s ${m}dk"
        } else {
            "${safe} dk"
        }
    }

    fun buildGreeting(
        hour: Int,
        studentName: String?,
        isJarvisActive: Boolean
    ): Pair<String, String> {
        val safeHour = ((hour % 24) + 24) % 24
        if (isJarvisActive) {
            val title = when (safeHour) {
                in 5..11 -> "Günaydın efendim"
                in 12..17 -> "İyi günler efendim"
                in 18..21 -> "İyi akşamlar efendim"
                else -> "İyi geceler efendim"
            }
            return title to "Tüm SharkMentor sistemleri komutanızda."
        }

        val firstName = studentName
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals("Şampiyon", ignoreCase = true) && !it.equals("Misafir", ignoreCase = true) }
            ?.replaceFirstChar { it.uppercase() }
        val suffix = if (firstName != null) ", $firstName" else ""
        val title = when (safeHour) {
            in 5..11 -> "Günaydın$suffix"
            in 12..17 -> "İyi çalışmalar$suffix"
            in 18..21 -> "İyi akşamlar$suffix"
            else -> "Gece odağı$suffix"
        }
        val subtitle = when (safeHour) {
            in 5..11 -> "Günün komuta merkezi hazır — hedefine bir adım daha."
            in 12..17 -> "Odak, yapay zeka çözücü ve tekrarların tek ekranda."
            in 18..21 -> "Bugünü güçlü kapat, yarının planını şekillendir."
            else -> "Sessiz saatlerde verimli bir tekrar bloğu seni bekliyor."
        }
        return title to subtitle
    }

    fun buildSnapshot(
        hour: Int,
        studentName: String?,
        isJarvisActive: Boolean,
        todayFocusMinutes: Int,
        todayTargetMinutes: Int,
        streakDays: Int,
        streakGoalDays: Int,
        dueErrorCount: Int,
        totalErrorCount: Int,
        notes: List<NoteEntity>,
        resources: List<ResourceEntity>,
        todayTasks: List<StudyTaskEntity>,
        netProjection: NetProjectionEntity? = null
    ): HomeDashboardSnapshot {
        val (greetingTitle, greetingSubtitle) = buildGreeting(hour, studentName, isJarvisActive)

        val safeTarget = todayTargetMinutes.coerceAtLeast(30)
        val safeFocus = todayFocusMinutes.coerceAtLeast(0)
        val focusFraction = (safeFocus.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)
        val focusPercent = (focusFraction * 100f).roundToInt()

        val activeResources = resources.filter { !it.isDeleted }
        val activeNotes = notes.sortedByDescending { it.updatedAt }
        val isColdStart = totalErrorCount == 0 && activeNotes.isEmpty() && activeResources.isEmpty() && todayTasks.isEmpty()

        // 1. Error Bank (FSRS 1-3-7) Card
        val (errorTitle, errorSub, errorBadge, errorHighlight) = when {
            dueErrorCount > 0 -> Quadruple(
                "Hata Bankası",
                "Unutma eğrisine göre tekrar zamanı geldi — Sokratik ipucu hazır.",
                "$dueErrorCount bekliyor",
                true
            )
            totalErrorCount > 0 -> Quadruple(
                "Hata Bankası",
                "Tüm tekrarlar güncel! İkiz soru veya antrenman başlatabilirsin.",
                "$totalErrorCount kayıt",
                false
            )
            else -> Quadruple(
                "FSRS Hata Bankası",
                "Yanlış sorularını ekle, 1-3-7 akıllı tekrar ve ikiz soruyla kapat.",
                "Keşfet",
                false
            )
        }

        // 2. SharkStudio (Notes & PDF + Lasso AI) Card
        val latestNote = activeNotes.firstOrNull()
        val (studioTitle, studioSub, studioBadge) = if (latestNote != null) {
            val cleanTitle = latestNote.title.trim().ifBlank { "Başlıksız Not" }
            Triple(
                cleanTitle,
                "Kalem, PDF ve Kement AI ile kaldığın yerden devam et.",
                "${activeNotes.size} not"
            )
        } else {
            Triple(
                "SharkStudio PDF & Not",
                "PDF üstünde çizim yap, kementle soruyu seçip anında AI'a çözdür.",
                "Kement AI"
            )
        }

        // 3. Library & Book Coaching Card
        val unfinishedBooks = activeResources
            .filter { it.totalUnits <= 0 || it.completedUnits < it.totalUnits }
            .sortedByDescending { it.updatedAtMillis }
        val featuredBook = unfinishedBooks.firstOrNull() ?: activeResources.maxByOrNull { it.updatedAtMillis }
        val (libTitle, libSub, libBadge, libProgress) = if (featuredBook != null) {
            val pct = if (featuredBook.totalUnits > 0) {
                (featuredBook.completedUnits.toFloat() / featuredBook.totalUnits.toFloat()).coerceIn(0f, 1f)
            } else null
            val pctLabel = if (pct != null) "%${(pct * 100f).roundToInt()}" else "${activeResources.size} kitap"
            val subj = featuredBook.subject.trim().ifBlank { "Kütüphane" }
            val unitSummary = if (featuredBook.totalUnits > 0) {
                "$subj • ${featuredBook.completedUnits}/${featuredBook.totalUnits} ${featuredBook.unitType}"
            } else {
                "$subj • ${activeResources.size} aktif kaynak"
            }
            Quadruple(
                featuredBook.title.trim().ifBlank { "Kaynak Kitabın" },
                unitSummary,
                pctLabel,
                pct
            )
        } else {
            Quadruple(
                "Kitap & Konu Koçu",
                "Kitaplarını ekle, ders/tür filtresi ve video kampıyla takip et.",
                "Kütüphane",
                null
            )
        }

        // 4. Today's Plan / Net Projection Card
        val completedToday = todayTasks.count { it.isCompleted }
        val nextTask = todayTasks.firstOrNull { !it.isCompleted }
        val hasNetData = netProjection != null && (netProjection.tytProjectedNet > 0f || netProjection.aytProjectedNet > 0f)
        val (planTitle, planSub, planBadge, planRoute) = when {
            nextTask != null -> {
                val topicPart = nextTask.topic.trim().takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
                val timePart = nextTask.time.trim().takeIf { it.isNotBlank() }?.let { "$it — " }.orEmpty()
                Quadruple(
                    "${nextTask.subject.ifBlank { "Etüt" }}$topicPart",
                    "${timePart}Bugün $completedToday/${todayTasks.size} blok tamamlandı.",
                    "$completedToday/${todayTasks.size}",
                    "plans"
                )
            }
            todayTasks.isNotEmpty() -> Quadruple(
                "Bugünkü Plan Tamam!",
                "${todayTasks.size} etüt bloğunun tamamını bitirdin, harika gidiyorsun.",
                "${todayTasks.size}/${todayTasks.size}",
                "plans"
            )
            hasNetData && netProjection != null -> Quadruple(
                "Net Tahmin Sihirbazı",
                "TYT ${"%.1f".format(netProjection.tytProjectedNet)} • AYT ${"%.1f".format(netProjection.aytProjectedNet)} projeksiyonu hazır.",
                "Analiz",
                "detailed_analytics"
            )
            else -> Quadruple(
                "AI Haftalık Plan",
                "Ders programına ve kitaplarına göre dengeli etüt planı oluştur.",
                "Planla",
                "plans"
            )
        }

        return HomeDashboardSnapshot(
            greetingTitle = greetingTitle,
            greetingSubtitle = greetingSubtitle,
            todayFocusMinutes = safeFocus,
            todayTargetMinutes = safeTarget,
            focusProgressFraction = focusFraction,
            focusProgressPercent = focusPercent,
            streakDays = streakDays.coerceAtLeast(0),
            streakGoalDays = streakGoalDays.coerceAtLeast(7),
            dueErrorCount = dueErrorCount.coerceAtLeast(0),
            totalErrorCount = totalErrorCount.coerceAtLeast(0),
            errorCardTitle = errorTitle,
            errorCardSubtitle = errorSub,
            errorCardBadge = errorBadge,
            errorCardHighlighted = errorHighlight,
            studioCardTitle = studioTitle,
            studioCardSubtitle = studioSub,
            studioCardBadge = studioBadge,
            libraryCardTitle = libTitle,
            libraryCardSubtitle = libSub,
            libraryCardBadge = libBadge,
            libraryProgressFraction = libProgress,
            planCardTitle = planTitle,
            planCardSubtitle = planSub,
            planCardBadge = planBadge,
            planCardRoute = planRoute,
            isColdStart = isColdStart
        )
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
