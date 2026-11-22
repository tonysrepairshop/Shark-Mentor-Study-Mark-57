package com.example.sharkmentor.core.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import com.example.sharkmentor.core.analytics.MasteryScoreEngine
import com.example.sharkmentor.core.service.FocusTimerService
import com.example.sharkmentor.core.timer.FocusSessionRecorder
import com.example.sharkmentor.core.timer.TimerMode
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.core.timer.TimerState
import com.example.sharkmentor.data.focus.FocusSessionManager
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.repository.StreakRepository
import com.example.sharkmentor.ui.widget.FocusTimerGlanceWidget
import com.example.sharkmentor.widget.DeskGlanceWidget
import com.example.sharkmentor.widget.SharkStreakWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

data class DayFocusStatus(
    val dayLabel: String,
    val minutes: Long,
    val isCompleted: Boolean,
    val isToday: Boolean
)

data class WidgetSnapshot(
    val masteryScore: Int = 0,
    val todayFocusMinutes: Long = 0L,
    val dailyGoalMinutes: Long = 180L,
    val pendingReviews: Int = 0,
    val streakCount: Int = 0,
    val streakFreezeAvailable: Int = 0,
    val isStreakDoneToday: Boolean = false,
    val daysSinceLastStudy: Int = 0,
    val last7Days: List<DayFocusStatus> = emptyList(),
    val timerState: TimerState = TimerState(),
    val isSessionManagerActive: Boolean = false,
    val isDeskActive: Boolean = false
)

object WidgetThemeTokens {
    val DeepOceanBg = ColorProvider(day = Color(0xFF071426), night = Color(0xFF071426))
    val ElevatedCardBg = ColorProvider(day = Color(0xFF0F223D), night = Color(0xFF0F223D))
    val SoftCardBg = ColorProvider(day = Color(0xFF152E52), night = Color(0xFF152E52))
    val PillSurface = ColorProvider(day = Color(0xFF1C3A63), night = Color(0xFF1C3A63))
    val OffWhite = ColorProvider(day = Color(0xFFF8FAFC), night = Color(0xFFF8FAFC))
    val MutedBlue = ColorProvider(day = Color(0xFF9FB3CE), night = Color(0xFF9FB3CE))
    val IceBlue = ColorProvider(day = Color(0xFF38BDF8), night = Color(0xFF38BDF8))
    val WaterDropAqua = ColorProvider(day = Color(0xFF22D3EE), night = Color(0xFF22D3EE))
    val WaterDropBg = ColorProvider(day = Color(0x3322D3EE), night = Color(0x3322D3EE))
    val OceanTeal = ColorProvider(day = Color(0xFF2EC4B6), night = Color(0xFF2EC4B6))
    val SignaturePurple = ColorProvider(day = Color(0xFF7C3AED), night = Color(0xFF7C3AED))
    val AlertAmber = ColorProvider(day = Color(0xFFF59E0B), night = Color(0xFFF59E0B))
    val AlertAmberBg = ColorProvider(day = Color(0x33F59E0B), night = Color(0x33F59E0B))
    val SuccessGreen = ColorProvider(day = Color(0xFF10B981), night = Color(0xFF10B981))
    val SuccessGreenBg = ColorProvider(day = Color(0x3310B981), night = Color(0x3310B981))
    val PauseRed = ColorProvider(day = Color(0xFFEF4444), night = Color(0xFFEF4444))
    val PauseRedBg = ColorProvider(day = Color(0x33EF4444), night = Color(0x33EF4444))
    val ActiveBlueBg = ColorProvider(day = Color(0x3338BDF8), night = Color(0x3338BDF8))
    val TrackBg = ColorProvider(day = Color(0xFF1B3459), night = Color(0xFF1B3459))
    val DarkOnAccent = ColorProvider(day = Color(0xFF04101E), night = Color(0xFF04101E))
    val WaterCelebrationBg = ColorProvider(day = Color(0xFF08223A), night = Color(0xFF08223A))
    val WaterUrgentBg = ColorProvider(day = Color(0xFF28101C), night = Color(0xFF28101C))
    val WaterWarningBg = ColorProvider(day = Color(0xFF1B1933), night = Color(0xFF1B1933))
}

object WidgetUpdateManager {

    private const val DESK_PREFS = "shark_desk_prefs"
    private const val WIDGET_PREFS = "shark_widget_prefs"
    private const val DEFAULT_DAILY_GOAL_MINUTES = 180L

    suspend fun loadSnapshot(context: Context): WidgetSnapshot = withContext(Dispatchers.IO) {
        val timerState = FocusTimerService.timerStateFlow.value
        val sessionManager = FocusSessionManager(context)
        val isSessionActive = runCatching { sessionManager.isSessionActivelyRunning() }.getOrDefault(false)

        val deskPrefs = context.getSharedPreferences(DESK_PREFS, Context.MODE_PRIVATE)
        val isDeskFlag = deskPrefs.getBoolean("is_desk_active", false)
        val dailyGoalMinutes = deskPrefs.getLong("daily_goal_minutes", DEFAULT_DAILY_GOAL_MINUTES).coerceAtLeast(30L)
        val isDeskActive = isDeskFlag || timerState.timerPhase == TimerPhase.FOCUSING

        val streakRepository = StreakRepository(context)
        val initialStreakState = runCatching { streakRepository.streakState.value }
            .getOrElse { com.example.sharkmentor.data.repository.StreakState() }
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val initialDoneToday = initialStreakState.lastStreakDate == todayStr

        val dbSnapshot = runCatching {
            val db = SharkMentorDatabase.getDatabase(context)
            val zone = ZoneId.systemDefault()

            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val sevenDaysStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -6)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val isActiveFocusSession = timerState.currentMode != TimerMode.BREAK &&
                (timerState.timerPhase == TimerPhase.FOCUSING || timerState.timerPhase == TimerPhase.PAUSED)
            val activeSessionUuid = if (isActiveFocusSession) FocusSessionRecorder.getActiveSessionId() else null

            val weekSessions = db.sessionDao().getSessionsSince(sevenDaysStart)
                .filter { activeSessionUuid == null || it.cloudId != activeSessionUuid }
            val todaySessions = weekSessions.filter { it.completedAt >= startOfToday }
            val savedTodayMinutes = todaySessions.sumOf { it.durationSeconds } / 60L

            val liveActiveMinutes = if (isActiveFocusSession) {
                timerState.timeElapsedMillis / 60_000L
            } else {
                0L
            }
            val totalTodayMinutes = savedTodayMinutes + liveActiveMinutes

            val secondsByDate = weekSessions.groupBy { session ->
                Instant.ofEpochMilli(session.completedAt).atZone(zone).toLocalDate()
            }.mapValues { (_, list) -> list.sumOf { it.durationSeconds } }

            val allFocus = db.sessionDao().getAllSessionsRaw()
                .filter { activeSessionUuid == null || it.cloudId != activeSessionUuid }
            val syncedStreakState = runCatching {
                streakRepository.syncFromRoomSessions(allFocus, totalTodayMinutes)
            }.getOrDefault(initialStreakState)
            val syncedCompletedDates = syncedStreakState.completedDates
            val syncedDoneToday = syncedStreakState.lastStreakDate == todayStr || totalTodayMinutes >= 20L

            val dayStatuses = (6 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val mins = (secondsByDate[date] ?: 0L) / 60L
                val effectiveMins = if (date == today) maxOf(mins, totalTodayMinutes) else mins
                val label = when (date.dayOfWeek.value) {
                    1 -> "Pt"
                    2 -> "Sa"
                    3 -> "Ça"
                    4 -> "Pe"
                    5 -> "Cu"
                    6 -> "Ct"
                    else -> "Pz"
                }
                val completed = effectiveMins >= 20L ||
                    syncedCompletedDates.contains(dateIso) ||
                    (date == today && syncedDoneToday)
                DayFocusStatus(
                    dayLabel = label,
                    minutes = effectiveMins,
                    isCompleted = completed,
                    isToday = date == today
                )
            }

            val errors = db.errorQuestionDao().getAllQuestionsRaw()
            val now = System.currentTimeMillis()
            val dueCount = errors.count { it.state == 0 || it.dueDateMillis <= now }

            val topics = db.goalsDao().getAllTopicsRaw()
            val resources = db.goalsDao().getAllResourcesRaw()
            val totalAllMinutes = (allFocus.sumOf { it.durationSeconds } / 60L) + liveActiveMinutes
            val reflections = db.dailyReflectionDao().getRecentReflectionsRaw()
            val avgScore = if (reflections.isNotEmpty()) {
                reflections.map { it.productivityScore }.average().toFloat()
            } else {
                null
            }

            val report = MasteryScoreEngine.calculate(
                topics = topics,
                resources = resources,
                errorQuestions = errors,
                totalFocusMinutes = totalAllMinutes,
                recentReflectionAvg = avgScore
            )

            DbQueryResult(
                masteryScore = report.overallScore,
                todayFocusMinutes = totalTodayMinutes,
                pendingReviews = dueCount,
                last7Days = dayStatuses,
                syncedStreakState = syncedStreakState
            )
        }.getOrElse {
            val fallbackDates = initialStreakState.completedDates
            val fallback7Days = (6 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val label = when (date.dayOfWeek.value) {
                    1 -> "Pt"
                    2 -> "Sa"
                    3 -> "Ça"
                    4 -> "Pe"
                    5 -> "Cu"
                    6 -> "Ct"
                    else -> "Pz"
                }
                DayFocusStatus(
                    dayLabel = label,
                    minutes = 0L,
                    isCompleted = fallbackDates.contains(dateIso) || (date == today && initialDoneToday),
                    isToday = date == today
                )
            }
            DbQueryResult(
                masteryScore = 0,
                todayFocusMinutes = 0L,
                pendingReviews = 0,
                last7Days = fallback7Days,
                syncedStreakState = initialStreakState
            )
        }

        val resolvedStreak = dbSnapshot.syncedStreakState
        val isStreakDoneToday = resolvedStreak.lastStreakDate == todayStr || dbSnapshot.todayFocusMinutes >= 20L

        WidgetSnapshot(
            masteryScore = dbSnapshot.masteryScore,
            todayFocusMinutes = dbSnapshot.todayFocusMinutes,
            dailyGoalMinutes = dailyGoalMinutes,
            pendingReviews = dbSnapshot.pendingReviews,
            streakCount = resolvedStreak.streakCount,
            streakFreezeAvailable = resolvedStreak.streakFreezeAvailable,
            isStreakDoneToday = isStreakDoneToday,
            daysSinceLastStudy = resolvedStreak.daysSinceLastStudy,
            last7Days = dbSnapshot.last7Days,
            timerState = timerState,
            isSessionManagerActive = isSessionActive,
            isDeskActive = isDeskActive
        )
    }

    suspend fun syncWidgetData(
        context: Context,
        masteryScore: Int,
        todayFocusMinutes: Long,
        pendingReviews: Int
    ) {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("pref_mastery_score", masteryScore)
            .putLong("pref_today_focus_mins", todayFocusMinutes)
            .putInt("pref_pending_reviews", pendingReviews)
            .apply()

        updateAllWidgets(context)
    }

    suspend fun updateAllWidgets(context: Context) {
        runCatching { SharkMentorWidget().updateAll(context) }
        runCatching { DeskGlanceWidget().updateAll(context) }
        runCatching { FocusTimerGlanceWidget().updateAll(context) }
        runCatching { SharkStreakWidget().updateAll(context) }
    }

    fun formatMinutesCompact(totalMinutes: Long): String {
        val safe = totalMinutes.coerceAtLeast(0L)
        val hours = safe / 60L
        val mins = safe % 60L
        return if (hours > 0L) "${hours}s ${mins}dk" else "$mins dk"
    }

    private data class DbQueryResult(
        val masteryScore: Int,
        val todayFocusMinutes: Long,
        val pendingReviews: Int,
        val last7Days: List<DayFocusStatus>,
        val syncedStreakState: com.example.sharkmentor.data.repository.StreakState
    )
}
