package com.example.sharkmentor.core.coaching

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.core.timer.FocusSessionRecorder
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.ConcreteTargetEntity
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dual-mode architecture controlled by the "Kapsamlı Destek" setting:
 * - [FREE_TRACKING]: Kronometre & Odak Sayıcıları view.
 * - [REAL_COACHING]: Koçluk Yolu (Duolingo-style guided daily path & coaching cockpit).
 */
enum class AppStudyMode(val title: String) {
    FREE_TRACKING("Kronometre"),
    REAL_COACHING("Koçluk Yolu")
}

@Immutable
data class StudentTargetContext(
    val examType: String = "YKS 2026",
    val studentGrade: String = "",
    val targetTitle: String = "",
    val targetScoreOrRank: String = "",
    val targetRanking: Int? = null,
    val targetUniversity: String? = null,
    val targetDepartment: String? = null,
    val aiAnalysisSummary: String = ""
) {
    val hasConcreteTarget: Boolean
        get() = targetTitle.isNotBlank() || !targetUniversity.isNullOrBlank() || !targetDepartment.isNullOrBlank()

    fun compassHeadline(): String {
        val parts = mutableListOf<String>()
        if (examType.isNotBlank()) parts.add(examType.trim())
        val mainTarget = when {
            !targetUniversity.isNullOrBlank() && !targetDepartment.isNullOrBlank() ->
                "${targetUniversity.trim()} ${targetDepartment.trim()}"
            targetTitle.isNotBlank() -> targetTitle.trim()
            else -> ""
        }
        if (mainTarget.isNotBlank()) parts.add(mainTarget)
        val rankBadge = when {
            targetRanking != null && targetRanking > 0 -> "#${String.format(Locale("tr"), "%,d", targetRanking)}"
            targetScoreOrRank.isNotBlank() -> targetScoreOrRank.trim()
            else -> ""
        }
        if (rankBadge.isNotBlank()) parts.add(rankBadge)
        return if (parts.isEmpty()) "Hedef Belirlenmedi" else parts.joinToString(" • ")
    }

    /**
     * Computes AI-recommended daily focus duration (in minutes) tailored to the student's target rank/exam.
     */
    fun recommendDailyMinutes(isWeekend: Boolean): Int {
        val rank = targetRanking ?: extractRankFromText(targetScoreOrRank)
        val upperExam = examType.uppercase(Locale.ROOT)
        val baseWeekday = when {
            upperExam.contains("TUS") || upperExam.contains("DUS") -> 300
            rank != null && rank in 1..5_000 -> 330
            rank != null && rank in 5_001..20_000 -> 270
            rank != null && rank in 20_001..60_000 -> 240
            upperExam.contains("KPSS") || upperExam.contains("DGS") -> 240
            upperExam.contains("LGS") -> 180
            hasConcreteTarget -> 240
            else -> 210
        }
        return if (isWeekend) (baseWeekday + 60).coerceAtMost(480) else baseWeekday
    }

    private fun extractRankFromText(raw: String): Int? {
        if (raw.isBlank()) return null
        val digits = raw.replace(".", "").replace(",", "")
        val match = Regex("""\b(\d{1,6})\b""").find(digits) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}

@Immutable
data class DailyGoalStatus(
    val workedMinutes: Int,
    val targetMinutes: Int,
    val deltaMinutes: Int,
    val remainingMinutes: Int,
    val overtimeMinutes: Int,
    val progressFraction: Float,
    val isGoalReached: Boolean,
    val isOvertime: Boolean
) {
    fun formattedWorked(): String = CoachingEngine.formatMinutesShort(workedMinutes)
    fun formattedTarget(): String = CoachingEngine.formatMinutesShort(targetMinutes)

    fun formattedDeltaBadge(): String {
        return when {
            overtimeMinutes > 0 -> "+${CoachingEngine.formatMinutesShort(overtimeMinutes)}"
            remainingMinutes > 0 -> "-${CoachingEngine.formatMinutesShort(remainingMinutes)}"
            else -> "Tamamlandı"
        }
    }

    fun formattedDeltaDetail(): String {
        return when {
            overtimeMinutes > 0 -> "Hedef aşıldı • +${CoachingEngine.formatMinutesShort(overtimeMinutes)} artı"
            remainingMinutes > 0 -> "Hedefe ${CoachingEngine.formatMinutesShort(remainingMinutes)} kaldı"
            else -> "Günlük hedef tamamlandı"
        }
    }
}

@Immutable
data class DailyGoalLedgerEntry(
    val dateKey: String,        // yyyy-MM-dd
    val dayOfWeekIso: Int,      // 1 = Pazartesi .. 7 = Pazar
    val dayLabelTr: String,     // Pazartesi .. Pazar
    val workedMinutes: Int,
    val targetMinutes: Int,
    val topSubject: String = ""
) {
    val deltaMinutes: Int get() = workedMinutes - targetMinutes
}

@Immutable
data class WeeklyVarianceAdvice(
    val lowDayLabel: String,
    val lowDeficitMinutes: Int,
    val highDayLabel: String?,
    val highSurplusMinutes: Int,
    val TotalWeekWorkedMinutes: Int,
    val TotalWeekTargetMinutes: Int,
    val conciseMessage: String
)

@Immutable
data class LiveFocusPresence(
    val isStudying: Boolean = false,
    val activeSubject: String = "Genel",
    val startedAtMillis: Long = 0L,
    val pendingCloudFlush: Boolean = false
) {
    fun elapsedMinutes(nowMillis: Long = System.currentTimeMillis()): Int {
        if (!isStudying || startedAtMillis <= 0L) return 0
        return ((nowMillis - startedAtMillis).coerceAtLeast(0L) / 60_000L).toInt()
    }

    fun formattedLeagueStatus(nowMillis: Long = System.currentTimeMillis()): String {
        val cleanSubj = CoachingEngine.formatCleanLeagueSubject(activeSubject).ifBlank { "Odak" }
        val mins = elapsedMinutes(nowMillis)
        return if (mins > 0) {
            "Masada • $cleanSubj ($mins dk)"
        } else {
            "Masada • $cleanSubj"
        }
    }
}

/**
 * Central engine for Mark 57:
 * 1. Dual-mode management ([AppStudyMode.FREE_TRACKING] vs [AppStudyMode.REAL_COACHING]).
 * 2. Macro Target Compass ([StudentTargetContext]) & universal AI prompt injection.
 * 3. Smart Daily Study Goal (`-/+` live variance) & 7-day ledger with adaptive weekly revision advice.
 * 4. Custom Focus Titles / Categories + optional sub-notes & offline-first Firestore presence sync.
 */
object CoachingEngine {

    private const val TAG = "CoachingEngine"
    private const val PREFS_NAME = "shark_mark57_coaching_prefs"
    const val MAX_DAILY_GOAL_MINUTES = 960 // 16 hours/day max (supports 12h weekday / 15h weekend)

    private const val KEY_COMPREHENSIVE_SUPPORT_ENABLED = "comprehensive_support_enabled"
    private const val KEY_STUDY_MODE = "study_mode"
    private const val KEY_WEEKDAY_GOAL_MINS = "weekday_goal_mins"
    private const val KEY_WEEKEND_GOAL_MINS = "weekend_goal_mins"
    private const val KEY_USE_AI_GOAL = "use_ai_goal"
    private const val KEY_CUSTOM_FOCUS_TITLES = "custom_focus_titles_json"
    private const val KEY_SELECTED_FOCUS_TITLE = "selected_focus_title"
    private const val KEY_FOCUS_SUB_NOTE = "focus_sub_note"
    private const val KEY_DAILY_LEDGER_JSON = "daily_ledger_json"

    // Offline-first live presence keys
    private const val KEY_PRESENCE_STUDYING = "presence_is_studying"
    private const val KEY_PRESENCE_SUBJECT = "presence_active_subject"
    private const val KEY_PRESENCE_STARTED_AT = "presence_started_at"
    private const val KEY_PRESENCE_NEEDS_FLUSH = "presence_needs_cloud_flush"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isInitialized = false

    private val _comprehensiveSupportEnabled = MutableStateFlow(false)
    val comprehensiveSupportEnabled: StateFlow<Boolean> = _comprehensiveSupportEnabled.asStateFlow()

    private val _studyMode = MutableStateFlow(AppStudyMode.FREE_TRACKING)
    val studyMode: StateFlow<AppStudyMode> = _studyMode.asStateFlow()

    private val _targetContext = MutableStateFlow(StudentTargetContext())
    val targetContext: StateFlow<StudentTargetContext> = _targetContext.asStateFlow()

    private val _weekdayGoalMinutes = MutableStateFlow(240)
    val weekdayGoalMinutes: StateFlow<Int> = _weekdayGoalMinutes.asStateFlow()

    private val _weekendGoalMinutes = MutableStateFlow(300)
    val weekendGoalMinutes: StateFlow<Int> = _weekendGoalMinutes.asStateFlow()

    private val _useAiRecommendedGoal = MutableStateFlow(false)
    val useAiRecommendedGoal: StateFlow<Boolean> = _useAiRecommendedGoal.asStateFlow()

    private val _customFocusTitles = MutableStateFlow<List<String>>(emptyList())
    val customFocusTitles: StateFlow<List<String>> = _customFocusTitles.asStateFlow()

    private val _selectedFocusTitle = MutableStateFlow("TYT")
    val selectedFocusTitle: StateFlow<String> = _selectedFocusTitle.asStateFlow()

    private val _focusSubNote = MutableStateFlow("")
    val focusSubNote: StateFlow<String> = _focusSubNote.asStateFlow()

    private val _recentLedger = MutableStateFlow<List<DailyGoalLedgerEntry>>(emptyList())
    val recentLedger: StateFlow<List<DailyGoalLedgerEntry>> = _recentLedger.asStateFlow()

    private val _weeklyVarianceAdvice = MutableStateFlow<WeeklyVarianceAdvice?>(null)
    val weeklyVarianceAdvice: StateFlow<WeeklyVarianceAdvice?> = _weeklyVarianceAdvice.asStateFlow()

    private val _weakMasterySummary = MutableStateFlow("")
    val weakMasterySummary: StateFlow<String> = _weakMasterySummary.asStateFlow()

    private val _livePresence = MutableStateFlow(LiveFocusPresence())
    val livePresence: StateFlow<LiveFocusPresence> = _livePresence.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            val appCtx = context.applicationContext
            val sp = prefs(appCtx)

            val supportEnabled = sp.getBoolean(KEY_COMPREHENSIVE_SUPPORT_ENABLED, false)
            _comprehensiveSupportEnabled.value = supportEnabled

            val savedMode = sp.getString(KEY_STUDY_MODE, AppStudyMode.FREE_TRACKING.name)
            val parsedMode = runCatching {
                AppStudyMode.valueOf(savedMode ?: AppStudyMode.FREE_TRACKING.name)
            }.getOrDefault(AppStudyMode.FREE_TRACKING)
            _studyMode.value = if (supportEnabled) parsedMode else AppStudyMode.FREE_TRACKING

            _weekdayGoalMinutes.value = sp.getInt(KEY_WEEKDAY_GOAL_MINS, 240).coerceIn(30, MAX_DAILY_GOAL_MINUTES)
            _weekendGoalMinutes.value = sp.getInt(KEY_WEEKEND_GOAL_MINS, 300).coerceIn(30, MAX_DAILY_GOAL_MINUTES)
            _useAiRecommendedGoal.value = sp.getBoolean(KEY_USE_AI_GOAL, false)

            _customFocusTitles.value = parseStringListJson(sp.getString(KEY_CUSTOM_FOCUS_TITLES, null))
            val defaultExamTitle = defaultFocusTitlesForExam(
                UserPreferences.getInstance(appCtx).studentTargetExam
            ).firstOrNull() ?: "TYT"
            _selectedFocusTitle.value = sp.getString(KEY_SELECTED_FOCUS_TITLE, defaultExamTitle)
                ?.takeIf { it.isNotBlank() } ?: defaultExamTitle
            _focusSubNote.value = sp.getString(KEY_FOCUS_SUB_NOTE, "") ?: ""

            _livePresence.value = LiveFocusPresence(
                isStudying = sp.getBoolean(KEY_PRESENCE_STUDYING, false),
                activeSubject = sp.getString(KEY_PRESENCE_SUBJECT, "Genel") ?: "Genel",
                startedAtMillis = sp.getLong(KEY_PRESENCE_STARTED_AT, 0L),
                pendingCloudFlush = sp.getBoolean(KEY_PRESENCE_NEEDS_FLUSH, false)
            )

            isInitialized = true
            refreshTargetAndLedgerAsync(appCtx)
        }
    }

    fun setComprehensiveSupportEnabled(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        _comprehensiveSupportEnabled.value = enabled
        val editor = prefs(context).edit().putBoolean(KEY_COMPREHENSIVE_SUPPORT_ENABLED, enabled)
        if (!enabled && _studyMode.value != AppStudyMode.FREE_TRACKING) {
            _studyMode.value = AppStudyMode.FREE_TRACKING
            editor.putString(KEY_STUDY_MODE, AppStudyMode.FREE_TRACKING.name)
        }
        editor.apply()
    }

    fun setStudyMode(context: Context, mode: AppStudyMode) {
        ensureInitialized(context)
        val effectiveMode = if (_comprehensiveSupportEnabled.value) mode else AppStudyMode.FREE_TRACKING
        _studyMode.value = effectiveMode
        prefs(context).edit().putString(KEY_STUDY_MODE, effectiveMode.name).apply()
        val uid = GoogleServicesHelper.getCurrentUser(context = context)?.uid
        if (!uid.isNullOrBlank() && FocusSessionRecorder.isNetworkConnected(context)) {
            GoogleServicesHelper.getFirebaseFirestore(context)
                ?.collection("students")?.document(uid)
                ?.set(mapOf("coachingMode" to effectiveMode.name), SetOptions.merge())
        }
    }

    fun toggleStudyMode(context: Context): AppStudyMode {
        ensureInitialized(context)
        val next = if (_studyMode.value == AppStudyMode.REAL_COACHING) {
            AppStudyMode.FREE_TRACKING
        } else {
            AppStudyMode.REAL_COACHING
        }
        setStudyMode(context, next)
        return next
    }

    fun setDailyGoals(
        context: Context,
        weekdayMinutes: Int,
        weekendMinutes: Int,
        useAiRecommendation: Boolean
    ) {
        ensureInitialized(context)
        val clampedWeekday = weekdayMinutes.coerceIn(30, MAX_DAILY_GOAL_MINUTES)
        val clampedWeekend = weekendMinutes.coerceIn(30, MAX_DAILY_GOAL_MINUTES)
        _weekdayGoalMinutes.value = clampedWeekday
        _weekendGoalMinutes.value = clampedWeekend
        _useAiRecommendedGoal.value = useAiRecommendation
        prefs(context).edit()
            .putInt(KEY_WEEKDAY_GOAL_MINS, clampedWeekday)
            .putInt(KEY_WEEKEND_GOAL_MINS, clampedWeekend)
            .putBoolean(KEY_USE_AI_GOAL, useAiRecommendation)
            .apply()
        refreshTargetAndLedgerAsync(context)
    }

    fun getTodayTargetMinutes(
        targetCtx: StudentTargetContext = _targetContext.value,
        calendar: Calendar = Calendar.getInstance()
    ): Int {
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        return if (_useAiRecommendedGoal.value) {
            targetCtx.recommendDailyMinutes(isWeekend)
        } else {
            if (isWeekend) _weekendGoalMinutes.value else _weekdayGoalMinutes.value
        }
    }

    fun getTargetMinutesForIsoDay(
        isoDayOfWeek: Int,
        targetCtx: StudentTargetContext = _targetContext.value
    ): Int {
        val isWeekend = isoDayOfWeek == 6 || isoDayOfWeek == 7
        return if (_useAiRecommendedGoal.value) {
            targetCtx.recommendDailyMinutes(isWeekend)
        } else {
            if (isWeekend) _weekendGoalMinutes.value else _weekdayGoalMinutes.value
        }
    }

    /**
     * Pure calculation of live daily goal progress & variance (-/+).
     */
    fun computeDailyGoalStatus(
        todayCompletedMinutes: Int,
        activeRunningSeconds: Long = 0L,
        targetMinutes: Int
    ): DailyGoalStatus {
        val safeTarget = targetMinutes.coerceAtLeast(15)
        val activeMins = (activeRunningSeconds.coerceAtLeast(0L) / 60L).toInt()
        val totalWorked = (todayCompletedMinutes.coerceAtLeast(0)) + activeMins
        val delta = totalWorked - safeTarget
        val remaining = (safeTarget - totalWorked).coerceAtLeast(0)
        val overtime = (totalWorked - safeTarget).coerceAtLeast(0)
        val progress = (totalWorked.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)
        return DailyGoalStatus(
            workedMinutes = totalWorked,
            targetMinutes = safeTarget,
            deltaMinutes = delta,
            remainingMinutes = remaining,
            overtimeMinutes = overtime,
            progressFraction = progress,
            isGoalReached = totalWorked >= safeTarget,
            isOvertime = overtime > 0
        )
    }

    // ── Focus Titles / Categories & Sub-Notes ────────────────────────────────

    fun defaultFocusTitlesForExam(examRaw: String?): List<String> {
        val upper = (examRaw ?: "").uppercase(Locale.ROOT)
        val examSpecific = when {
            upper.contains("TUS") -> listOf("TUS Temel", "TUS Klinik", "Soru Çözümü", "Deneme", "Kitap Okuma")
            upper.contains("KPSS") -> listOf("KPSS GY-GK", "KPSS Alan", "Paragraf", "Deneme", "Kitap Okuma")
            upper.contains("DGS") || upper.contains("ALES") ->
                listOf("Sayısal Mantık", "Sözel Mantık", "Geometri", "Deneme", "Kitap Okuma")
            upper.contains("LGS") -> listOf("LGS Matematik", "LGS Fen", "LGS Türkçe", "Deneme", "Kitap Okuma")
            else -> listOf("TYT", "AYT", "Geometri", "Paragraf", "Deneme", "Kitap Okuma")
        }
        return examSpecific
    }

    fun availableFocusTitles(examRaw: String? = _targetContext.value.examType): List<String> {
        val defaults = defaultFocusTitlesForExam(examRaw)
        val custom = _customFocusTitles.value
        val merged = LinkedHashSet<String>(defaults.size + custom.size)
        defaults.forEach { merged.add(it) }
        custom.forEach { if (it.isNotBlank()) merged.add(it.trim()) }
        return merged.toList()
    }

    fun selectFocusTitle(context: Context, title: String) {
        ensureInitialized(context)
        val clean = title.trim().ifBlank { "Genel" }
        _selectedFocusTitle.value = clean
        prefs(context).edit().putString(KEY_SELECTED_FOCUS_TITLE, clean).apply()
    }

    fun setFocusSubNote(context: Context, subNote: String) {
        ensureInitialized(context)
        val clean = subNote.trim().take(48)
        _focusSubNote.value = clean
        prefs(context).edit().putString(KEY_FOCUS_SUB_NOTE, clean).apply()
    }

    fun addCustomFocusTitle(context: Context, newTitle: String) {
        ensureInitialized(context)
        val clean = newTitle.trim().take(28)
        if (clean.isBlank()) return
        val current = _customFocusTitles.value.toMutableList()
        if (current.none { it.equals(clean, ignoreCase = true) }) {
            current.add(clean)
            _customFocusTitles.value = current
            prefs(context).edit().putString(KEY_CUSTOM_FOCUS_TITLES, toStringListJson(current)).apply()
        }
        selectFocusTitle(context, clean)
    }

    fun removeCustomFocusTitle(context: Context, titleToRemove: String) {
        ensureInitialized(context)
        val updated = _customFocusTitles.value.filterNot { it.equals(titleToRemove.trim(), ignoreCase = true) }
        _customFocusTitles.value = updated
        prefs(context).edit().putString(KEY_CUSTOM_FOCUS_TITLES, toStringListJson(updated)).apply()
        if (_selectedFocusTitle.value.equals(titleToRemove.trim(), ignoreCase = true)) {
            val fallback = availableFocusTitles().firstOrNull() ?: "TYT"
            selectFocusTitle(context, fallback)
        }
    }

    fun buildFormattedFocusSubject(
        title: String = _selectedFocusTitle.value,
        subNote: String = _focusSubNote.value
    ): String {
        val cleanTitle = title.trim().ifBlank { "Odak" }
        val cleanNote = subNote.trim()
        return if (cleanNote.isNotBlank()) {
            "$cleanTitle • $cleanNote"
        } else {
            cleanTitle
        }
    }

    /**
     * Normalizes a raw focus subject (which may contain book titles, sub-notes, camp days, or topics like
     * "AYT Kimya • Organik Kimya 4. Gün" or "AYT Matematik - Türev Test 4") into a clean, concise league label
     * (e.g. "AYT Kimya", "AYT Matematik", "TYT", "Kitap Okuma").
     */
    fun formatCleanLeagueSubject(rawSubject: String): String {
        val trimmed = rawSubject.trim()
        if (trimmed.isBlank()) return ""
        var primary = trimmed
            .substringBefore("•")
            .substringBefore("·")
            .substringBefore(" - ")
            .substringBefore(" – ")
            .substringBefore(" — ")
            .substringBefore(":")
            .substringBefore("|")
            .substringBefore("(")
            .replace(Regex("""\b\d{1,3}\.\s*[Gg]ün\b.*"""), "")
            .replace(
                Regex(
                    """\b(Konu Anlatımı|Soru Çözümü|Video Ders Kampı|Video Kampı|Kampı|Tekrar|Test\s*\d*)\b""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('-', '•', ':', '|', ',')
            .trim()

        if (primary.isBlank()) primary = trimmed.take(24)

        val words = primary.split(" ").filter { it.isNotBlank() }
        if (words.size > 3 || primary.length > 22) {
            val lower = primary.lowercase(Locale("tr"))
            val examPrefix = when {
                lower.contains("tyt-ayt") || lower.contains("tyt ayt") -> "TYT-AYT"
                lower.contains("ayt") -> "AYT"
                lower.contains("tyt") -> "TYT"
                lower.contains("lgs") -> "LGS"
                lower.contains("kpss") -> "KPSS"
                lower.contains("tus") -> "TUS"
                lower.contains("dgs") -> "DGS"
                lower.contains("ales") -> "ALES"
                else -> ""
            }
            val subjectName = when {
                lower.contains("matematik") || Regex("""\bmat\b""").containsMatchIn(lower) -> "Matematik"
                lower.contains("geometri") || Regex("""\bgeo\b""").containsMatchIn(lower) -> "Geometri"
                lower.contains("fizik") -> "Fizik"
                lower.contains("kimya") -> "Kimya"
                lower.contains("biyoloji") -> "Biyoloji"
                lower.contains("türkçe") || lower.contains("turkce") -> "Türkçe"
                lower.contains("edebiyat") -> "Edebiyat"
                lower.contains("paragraf") -> "Paragraf"
                lower.contains("tarih") -> "Tarih"
                lower.contains("coğrafya") || lower.contains("cografya") -> "Coğrafya"
                lower.contains("felsefe") -> "Felsefe"
                lower.contains("din") -> "Din Kültürü"
                lower.contains("fen") -> "Fen Bilimleri"
                lower.contains("ingilizce") -> "İngilizce"
                else -> ""
            }
            if (subjectName.isNotBlank()) {
                return if (examPrefix.isNotBlank()) "$examPrefix $subjectName" else subjectName
            }
            return words.take(2).joinToString(" ").take(22).trim()
        }
        return primary
    }

    // ── Offline-First Live Presence Broadcast (Shark League & Friends) ───────

    fun updateLivePresenceOfflineFirst(
        context: Context,
        isStudying: Boolean,
        subject: String = buildFormattedFocusSubject(),
        startedAtMillis: Long = System.currentTimeMillis()
    ) {
        ensureInitialized(context)
        val appCtx = context.applicationContext
        val cleanSubject = formatCleanLeagueSubject(subject).ifBlank { "Odak" }
        val effectiveStart = if (isStudying) {
            if (_livePresence.value.isStudying && _livePresence.value.startedAtMillis > 0L) {
                _livePresence.value.startedAtMillis
            } else {
                startedAtMillis
            }
        } else 0L

        val connected = FocusSessionRecorder.isNetworkConnected(appCtx)
        val nextPresence = LiveFocusPresence(
            isStudying = isStudying,
            activeSubject = if (isStudying) cleanSubject else "",
            startedAtMillis = effectiveStart,
            pendingCloudFlush = !connected
        )
        _livePresence.value = nextPresence

        prefs(appCtx).edit()
            .putBoolean(KEY_PRESENCE_STUDYING, isStudying)
            .putString(KEY_PRESENCE_SUBJECT, if (isStudying) cleanSubject else "")
            .putLong(KEY_PRESENCE_STARTED_AT, effectiveStart)
            .putBoolean(KEY_PRESENCE_NEEDS_FLUSH, !connected)
            .apply()

        if (connected) {
            pushLivePresenceToFirestore(appCtx, nextPresence)
        } else {
            Log.i(TAG, "Offline presence queued locally: isStudying=$isStudying, subject=$cleanSubject")
        }
    }

    /**
     * Called on periodic timer ticks and network reconnects so an offline-started
     * focus session automatically publishes its live status ("Masada • AYT Kimya")
     * to Shark League as soon as connectivity becomes available.
     */
    fun flushPendingPresenceIfConnected(context: Context) {
        ensureInitialized(context)
        val appCtx = context.applicationContext
        val current = _livePresence.value
        if (!current.pendingCloudFlush && !current.isStudying) return
        if (!FocusSessionRecorder.isNetworkConnected(appCtx)) return
        pushLivePresenceToFirestore(appCtx, current)
    }

    private fun pushLivePresenceToFirestore(context: Context, presence: LiveFocusPresence) {
        val uid = GoogleServicesHelper.getCurrentUser(context = context)?.uid ?: return
        val db = GoogleServicesHelper.getFirebaseFirestore(context) ?: return
        val cleanLeagueSubject = formatCleanLeagueSubject(presence.activeSubject).ifBlank { "Odak" }

        val studentData: Map<String, Any> = if (presence.isStudying) {
            mapOf(
                "isStudying" to true,
                "activeSubject" to cleanLeagueSubject,
                "activeSessionStartedAtMillis" to presence.startedAtMillis,
                "lastActive" to FieldValue.serverTimestamp()
            )
        } else {
            mapOf(
                "isStudying" to false,
                "activeSubject" to FieldValue.delete(),
                "activeSessionStartedAtMillis" to FieldValue.delete(),
                "lastActive" to FieldValue.serverTimestamp()
            )
        }

        db.collection("students").document(uid)
            .set(studentData, SetOptions.merge())
            .addOnSuccessListener {
                if (_livePresence.value.pendingCloudFlush) {
                    _livePresence.value = _livePresence.value.copy(pendingCloudFlush = false)
                    prefs(context).edit().putBoolean(KEY_PRESENCE_NEEDS_FLUSH, false).apply()
                }
            }

        val leagueData: Map<String, Any> = if (presence.isStudying) {
            mapOf(
                "isStudying" to true,
                "activeSubject" to cleanLeagueSubject,
                "activeSessionStartedAtMillis" to presence.startedAtMillis
            )
        } else {
            mapOf(
                "isStudying" to false,
                "activeSubject" to ""
            )
        }
        db.collection("global_league").document(uid)
            .set(leagueData, SetOptions.merge())
    }

    // ── Target Compass & 7-Day Daily Goal Ledger Refresh ─────────────────────

    fun refreshTargetAndLedgerAsync(context: Context) {
        val appCtx = context.applicationContext
        scope.launch {
            runCatching {
                val db = SharkMentorDatabase.getDatabase(appCtx)
                val userPrefs = UserPreferences.getInstance(appCtx)
                val concrete: ConcreteTargetEntity? = db.concreteTargetDao().getTargetRaw()

                val profileExam = userPrefs.studentTargetExam?.takeIf { it.isNotBlank() }
                    ?: userPrefs.countdownExamTitle?.takeIf { it.isNotBlank() }
                    ?: concrete?.examType?.takeIf { it.isNotBlank() }
                    ?: "YKS 2026"
                val grade = userPrefs.studentGrade.orEmpty()

                val updatedTargetCtx = StudentTargetContext(
                    examType = profileExam,
                    studentGrade = grade,
                    targetTitle = concrete?.targetTitle.orEmpty(),
                    targetScoreOrRank = concrete?.targetScoreOrRank.orEmpty(),
                    targetRanking = concrete?.targetRanking,
                    targetUniversity = concrete?.targetUniversity,
                    targetDepartment = concrete?.targetDepartment,
                    aiAnalysisSummary = concrete?.aiAnalysisSummary.orEmpty()
                )
                _targetContext.value = updatedTargetCtx

                // Compute last 7 days ledger from Room FocusSessionEntity rows
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val todayStart = cal.timeInMillis
                val sevenDaysAgoStart = todayStart - (6L * 24L * 3600L * 1000L)

                val recentSessions = db.sessionDao().getSessionsSince(sevenDaysAgoStart)
                val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

                val entries = mutableListOf<DailyGoalLedgerEntry>()
                for (offset in -6..0) {
                    val dayCal = Calendar.getInstance()
                    dayCal.timeInMillis = todayStart
                    dayCal.add(Calendar.DAY_OF_YEAR, offset)
                    val dStart = dayCal.timeInMillis
                    val dEnd = dStart + (24L * 3600L * 1000L) - 1L
                    val dKey = dateFmt.format(Date(dStart))
                    val isoDow = calendarDayToIso(dayCal.get(Calendar.DAY_OF_WEEK))
                    val dayLabel = isoDayLabelTr(isoDow)

                    val daySessions = recentSessions.filter { it.completedAt in dStart..dEnd }
                    val workedMins = (daySessions.sumOf { it.durationSeconds } / 60L).toInt()
                    val topSubj = daySessions
                        .groupBy { it.modeName.ifBlank { "Genel" } }
                        .maxByOrNull { (_, list) -> list.sumOf { it.durationSeconds } }
                        ?.key.orEmpty()

                    val targetMins = getTargetMinutesForIsoDay(isoDow, updatedTargetCtx)
                    entries.add(
                        DailyGoalLedgerEntry(
                            dateKey = dKey,
                            dayOfWeekIso = isoDow,
                            dayLabelTr = dayLabel,
                            workedMinutes = workedMins,
                            targetMinutes = targetMins,
                            topSubject = topSubj
                        )
                    )
                }

                _recentLedger.value = entries
                saveLedgerCache(appCtx, entries)
                _weeklyVarianceAdvice.value = computeWeeklyVarianceAdvice(entries, updatedTargetCtx)

                // Load weak mastery topics (currentLevel <= 2) from GoalsDao for AI prompt & coaching path weighting
                val allTopics = runCatching { db.goalsDao().getAllTopicsRaw() }.getOrDefault(emptyList())
                val weakTopics = allTopics
                    .filter { it.currentLevel in 0..2 }
                    .sortedBy { it.currentLevel }
                    .take(12)
                _weakMasterySummary.value = if (weakTopics.isNotEmpty()) {
                    weakTopics.joinToString(", ") { "${it.subject}: ${it.topic} (${it.currentLevel}/5)" }
                } else ""
            }.onFailure { e ->
                Log.w(TAG, "refreshTargetAndLedgerAsync failed: ${e.message}")
            }
        }
    }

    internal fun computeWeeklyVarianceAdvice(
        ledger: List<DailyGoalLedgerEntry>,
        targetCtx: StudentTargetContext
    ): WeeklyVarianceAdvice? {
        if (ledger.size < 2) return null
        // Inspect completed past days (exclude today if today is still early, or include today if overtime)
        val pastDays = if (ledger.size > 1) ledger.dropLast(1) else ledger
        val activePastDays = pastDays.filter { it.workedMinutes > 0 || it == pastDays.lastOrNull() }
        if (activePastDays.isEmpty()) return null

        val lowestDay = activePastDays.minByOrNull { it.deltaMinutes }
        val highestDay = ledger.maxByOrNull { it.deltaMinutes }
        val totalWorked = ledger.sumOf { it.workedMinutes }
        val totalTarget = ledger.sumOf { it.targetMinutes }

        if (lowestDay == null) return null
        val deficit = (lowestDay.targetMinutes - lowestDay.workedMinutes).coerceAtLeast(0)
        val surplus = highestDay?.let { (it.workedMinutes - it.targetMinutes).coerceAtLeast(0) } ?: 0

        if (deficit < 25 && surplus < 25) return null

        val targetRef = if (targetCtx.hasConcreteTarget) {
            "(${targetCtx.compassHeadline()})"
        } else ""

        val message = when {
            deficit >= 25 && surplus >= 25 && highestDay != null && highestDay.dateKey != lowestDay.dateKey ->
                "${lowestDay.dayLabelTr} hedefin ${formatMinutesShort(deficit)} altında kaldı, ${highestDay.dayLabelTr} +${formatMinutesShort(surplus)} çalışıldı. $targetRef temposu için kalan günleri dengeleyelim mi?".trim()
            deficit >= 25 ->
                "${lowestDay.dayLabelTr} günlük hedefin ${formatMinutesShort(deficit)} altında kaldı. $targetRef rotası için eksik konuları kalan günlere dağıtalım mı?".trim()
            surplus >= 25 && highestDay != null ->
                "${highestDay.dayLabelTr} günlük hedefini +${formatMinutesShort(surplus)} aştın. Haftalık programı bu tempoya göre güncelleyebilirsin."
            else -> return null
        }

        return WeeklyVarianceAdvice(
            lowDayLabel = lowestDay.dayLabelTr,
            lowDeficitMinutes = deficit,
            highDayLabel = highestDay?.dayLabelTr,
            highSurplusMinutes = surplus,
            TotalWeekWorkedMinutes = totalWorked,
            TotalWeekTargetMinutes = totalTarget,
            conciseMessage = message
        )
    }

    /**
     * Generates a compact, high-priority system context block injected into ALL AI models
     * (AI Planner, AI Coach Chat, Split-Screen Video Tutor, Daily Review) so the AI acts as a
     * true personalized coach aligned with the student's target university/rank and daily adherence.
     */
    fun buildAiSystemCompassPrompt(context: Context): String {
        ensureInitialized(context)
        val t = _targetContext.value
        val ledger = _recentLedger.value
        val mode = _studyMode.value
        val todayGoalMins = getTodayTargetMinutes(t)
        val todayWorkedMins = ledger.lastOrNull()?.workedMinutes ?: 0
        val todayStatus = computeDailyGoalStatus(todayWorkedMins, 0L, todayGoalMins)

        val sb = StringBuilder()
        sb.appendLine("[ÖĞRENCİ HEDEF PUSULASI & KOÇLUK BAĞLAMI - YÜKSEK ÖNCELİK]")
        sb.appendLine("• Çalışma Modu: ${mode.title}")
        sb.appendLine("• Hedef Sınav / Sınıf: ${t.examType}${if (t.studentGrade.isNotBlank()) " (${t.studentGrade})" else ""}")
        if (t.hasConcreteTarget) {
            sb.appendLine("• Somut Hedef (Üniversite/Bölüm/Sıralama): ${t.compassHeadline()}")
            if (t.aiAnalysisSummary.isNotBlank()) {
                sb.appendLine("• Hedef Net/Puan Analizi: ${t.aiAnalysisSummary.take(360)}")
            }
        }
        sb.appendLine("• Günlük Çalışma Hedefi: Hafta içi ${formatMinutesShort(_weekdayGoalMinutes.value)}, Hafta sonu ${formatMinutesShort(_weekendGoalMinutes.value)} (Bugün: ${todayStatus.formattedWorked()} / ${todayStatus.formattedTarget()}, Fark: ${todayStatus.formattedDeltaBadge()})")
        if (ledger.isNotEmpty()) {
            val summary = ledger.takeLast(5).joinToString(", ") { entry ->
                val sign = if (entry.deltaMinutes >= 0) "+${entry.deltaMinutes}dk" else "${entry.deltaMinutes}dk"
                "${entry.dayLabelTr}: ${entry.workedMinutes}/${entry.targetMinutes}dk ($sign)"
            }
            sb.appendLine("• Son Günlerin Çalışma Sapması: $summary")
        }
        _weeklyVarianceAdvice.value?.let { advice ->
            sb.appendLine("• Adaptif Koçluk Notu: ${advice.conciseMessage}")
        }
        if (_weakMasterySummary.value.isNotBlank()) {
            sb.appendLine("• Konu Hakimiyeti Eksik / Zayıf Konular (Öncelikli Pekiştirilecek): ${_weakMasterySummary.value}")
        }
        sb.appendLine("• KURAL 1: Tüm önerilerini, planlamalarını ve soru çözümlerini öğrencinin bu hedef sıralaması/bölümü ve günlük çalışma sapmalarına göre net, doğrudan ve gereksiz dolgu cümleleri kurmadan şekillendir.")
        sb.appendLine("• KURAL 2 (MÜFREDAT BÜTÜNLÜĞÜ & ÖN KOŞUL ZEMİNİ): Konuları ders içi pedagojik sıraya göre sırala (Örn: Fonksiyonlar -> Polinomlar -> Limit -> Türev -> İntegral; asla Fonksiyonlar veya Limit zemini oturmadan ilk günlere Türev/İntegral yazma). Hakimiyet seviyesi düşük (1-2/5) olan konuların peşini bırakma, ek ağırlık ver ve ön koşul konunun zemini sağlamlaşmadan bağlantılı ileri konuya geçirtme.")
        return sb.toString()
    }

    // ── Formatting & Helper Utilities ────────────────────────────────────────

    fun formatMinutesShort(minutes: Int): String {
        val safe = abs(minutes)
        val h = safe / 60
        val m = safe % 60
        return when {
            h > 0 && m > 0 -> "${h} sa ${m} dk"
            h > 0 -> "${h} sa"
            else -> "${m} dk"
        }
    }

    fun calendarDayToIso(calendarDayOfWeek: Int): Int = when (calendarDayOfWeek) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }

    fun isoDayLabelTr(isoDay: Int): String = when (isoDay) {
        1 -> "Pazartesi"
        2 -> "Salı"
        3 -> "Çarşamba"
        4 -> "Perşembe"
        5 -> "Cuma"
        6 -> "Cumartesi"
        7 -> "Pazar"
        else -> "Pazartesi"
    }

    private fun saveLedgerCache(context: Context, entries: List<DailyGoalLedgerEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            val obj = JSONObject()
            obj.put("dateKey", e.dateKey)
            obj.put("dayOfWeekIso", e.dayOfWeekIso)
            obj.put("dayLabelTr", e.dayLabelTr)
            obj.put("workedMinutes", e.workedMinutes)
            obj.put("targetMinutes", e.targetMinutes)
            obj.put("topSubject", e.topSubject)
            arr.put(obj)
        }
        prefs(context).edit().putString(KEY_DAILY_LEDGER_JSON, arr.toString()).apply()
    }

    private fun parseStringListJson(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotBlank()) add(v)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun toStringListJson(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { if (it.isNotBlank()) arr.put(it.trim()) }
        return arr.toString()
    }
}
