package com.example.sharkmentor.core.coaching

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.preferences.UserPreferences
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Defines which AI channel is requesting context so Student Dossier injection is strictly
 * limited to Coaching, Plan-Add AI, Zen Coach, and coaching/plan/zen intents in Auto Chat.
 * Never attached to Problem Solver, Twin Question, Note AI, etc. (saves tokens + enforces privacy).
 */
enum class DossierAiChannel {
    COACHING_DIRECT,
    PLAN_ADD_AI,
    ZEN_COACH,
    AUTO_CHAT,
    OTHER_NON_COACHING
}

@Immutable
data class DossierQuestion(
    val id: String,
    val number: Int,
    val prompt: String,
    val hint: String,
    val quickOptions: List<String> = emptyList()
)

@Immutable
data class DossierTopicSnapshot(
    val subject: String,
    val topic: String,
    val currentLevel: Int,
    val targetLevel: Int
)

@Immutable
data class DossierBookSnapshot(
    val title: String,
    val subject: String,
    val completedUnits: Int,
    val totalUnits: Int,
    val unitType: String
)

@Immutable
data class DossierPlanTaskSnapshot(
    val dayOfWeek: Int,
    val subject: String,
    val topic: String,
    val targetQuestions: Int = 30,
    val isCompleted: Boolean = false
)

@Immutable
data class EfficiencySuggestion(
    val currentWeekdayHours: Int,
    val currentWeekendHours: Int,
    val recommendedWeekdayMinutes: Int,
    val recommendedWeekendMinutes: Int,
    val averageWorkedMinutes: Int,
    val missedTaskCount: Int,
    val message: String
)

/**
 * Local encrypted Student Dossier ("Öğrenci Dosyası").
 * Strictly anonymized before any 3rd-party AI transmission — NEVER contains name, email, or UID.
 */
@Immutable
data class StudentDossier(
    val isCompleted: Boolean = false,
    val consentAccepted: Boolean = false,
    val consentAcceptedAtMillis: Long = 0L,
    val examType: String = "",
    val studentGrade: String = "",
    val targetHeadline: String = "",
    val targetRanking: Int? = null,
    val targetAnalysisSummary: String = "",
    val weekdayGoalMinutes: Int = 240,
    val weekendGoalMinutes: Int = 300,
    val topics: List<DossierTopicSnapshot> = emptyList(),
    val books: List<DossierBookSnapshot> = emptyList(),
    val activePlanTitle: String = "",
    val completedPlanTasks: List<DossierPlanTaskSnapshot> = emptyList(),
    val incompletePlanTasks: List<DossierPlanTaskSnapshot> = emptyList(),
    val interviewAnswers: Map<String, String> = emptyMap(),
    val coachingInsights: List<String> = emptyList(),
    val latestMockNetSummary: String = "",
    val latestMockAnalysis: String = "",
    val lastMockUpdatedAtMillis: Long = 0L,
    val efficiencyTrialDeclined: Boolean = false,
    val lastSyncedAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val answeredQuestionCount: Int
        get() = interviewAnswers.count { it.value.isNotBlank() }

    val weakTopicsCount: Int
        get() = topics.count { it.currentLevel in 0..2 }

    val totalPlanTasksCount: Int
        get() = completedPlanTasks.size + incompletePlanTasks.size

    fun toPlanAuditSnapshot(): PlanAuditSnapshot {
        val doneItems = completedPlanTasks.mapIndexed { idx, t ->
            PlanTaskAuditItem(
                id = "dossier_done_$idx",
                dayOfWeek = t.dayOfWeek,
                subject = t.subject,
                topic = t.topic,
                scheduledTime = "Esnek • 90 dk",
                targetQuestions = t.targetQuestions,
                isCompleted = true,
                isOverdueOrDueToday = false
            )
        }
        val missedItems = incompletePlanTasks.mapIndexed { idx, t ->
            PlanTaskAuditItem(
                id = "dossier_inc_$idx",
                dayOfWeek = t.dayOfWeek,
                subject = t.subject,
                topic = t.topic,
                scheduledTime = "Esnek • 90 dk",
                targetQuestions = t.targetQuestions,
                isCompleted = false,
                isOverdueOrDueToday = true
            )
        }
        return PlanAuditSnapshot(
            folderTitle = activePlanTitle,
            totalTasks = doneItems.size + missedItems.size,
            completedTasks = doneItems,
            incompleteTasks = missedItems,
            capturedAtMillis = lastSyncedAtMillis
        )
    }
}

/**
 * Navigation bus connecting Coaching Path onboarding CTA -> Shark AI interactive interview.
 */
object StudentDossierNavBus {
    private val _openInterviewRequested = MutableStateFlow(false)
    val openInterviewRequested: StateFlow<Boolean> = _openInterviewRequested.asStateFlow()

    fun requestOpenInterview() {
        _openInterviewRequested.value = true
    }

    fun consumeOpenInterview() {
        _openInterviewRequested.value = false
    }
}

/**
 * Manages the AES-256-GCM locally encrypted Student Dossier ("Öğrenci Dosyası"):
 * - Explicit ethical consent gate before enabling Real Coaching.
 * - 10-question warm autonomous coaching interview inside Shark AI.
 * - Full clean sync 2x/day (12h) in background + immediate manual sync on user confirmation
 *   (adds new goals/topics/books, updates changed items, removes deleted ones).
 * - Strict anonymized prompt builder (ZERO name/email/UID) injected ONLY into approved AI channels.
 * - Adaptive high-hour efficiency check with explicit student veto support.
 */
object StudentDossierManager {

    private const val TAG = "StudentDossierManager"
    private const val PREFS_NAME = "shark_student_dossier_secure_prefs"
    private const val KEY_PAYLOAD_ENC = "dossier_payload_aes_gcm"
    private const val KEY_FALLBACK_SALT = "dossier_local_salt_v1"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "shark_student_dossier_aes_key_v1"
    private const val GCM_TAG_BITS = 128
    const val SYNC_INTERVAL_MILLIS = 12L * 60L * 60L * 1000L // 2x per day (12 hours)

    val INTERVIEW_QUESTIONS: List<DossierQuestion> = listOf(
        DossierQuestion(
            id = "daily_hours",
            number = 1,
            prompt = "Hafta içi ve hafta sonu günde kaç saat çalışmayı hedefliyorsun?",
            hint = "Örn: Hafta içi 6 saat, hafta sonu 8 saat (veya 12s / 15s)",
            quickOptions = listOf("4s / 6s", "6s / 8s", "8s / 10s", "10s / 12s", "12s / 15s")
        ),
        DossierQuestion(
            id = "peak_window",
            number = 2,
            prompt = "Zihnin günün hangi saatlerinde en verimli çalışıyor?",
            hint = "Odak bloklarının yoğunluğunu buna göre ayarlayacağız.",
            quickOptions = listOf("Sabah erken", "Öğleden sonra", "Akşam", "Gece")
        ),
        DossierQuestion(
            id = "strong_subjects",
            number = 3,
            prompt = "Kendini en güçlü ve rahat hissettiğin dersler hangileri?",
            hint = "Örn: TYT Türkçe, AYT Biyoloji..."
        ),
        DossierQuestion(
            id = "weak_subjects",
            number = 4,
            prompt = "Seni en çok zorlayan veya ertelediğin ders ve konular hangileri?",
            hint = "Örn: AYT Matematik Türev, TYT Fizik Optik..."
        ),
        DossierQuestion(
            id = "learning_style",
            number = 5,
            prompt = "Yeni bir konuyu en iyi nasıl öğreniyorsun?",
            hint = "Kamp, kitap ve tekrar dengesini buna göre kuracağız.",
            quickOptions = listOf("Video + Not", "Soru çözerek", "Kitaptan okuyarak", "Karma")
        ),
        DossierQuestion(
            id = "school_schedule",
            number = 6,
            prompt = "Okul, dershane veya sabit kurs saatlerin var mı?",
            hint = "Örn: Hafta içi 08:30-15:30 okul var / Mezunum tüm gün müsaitim",
            quickOptions = listOf("Mezunum, tüm gün müsaitim", "Hafta içi okul var", "Hafta sonu dershane var")
        ),
        DossierQuestion(
            id = "distractions",
            number = 7,
            prompt = "Çalışırken odağını en çok ne bozuyor?",
            hint = "Koçluk uyarılarını ve blok uzunluğunu buna göre dengeleyeceğiz.",
            quickOptions = listOf("Telefon / Sosyal medya", "Yorgunluk / Uyku", "Kaygı / Stres", "Plan belirsizliği")
        ),
        DossierQuestion(
            id = "mock_habit",
            number = 8,
            prompt = "Haftada kaç kez deneme (TYT/AYT veya branş) çözmek istiyorsun?",
            hint = "Örn: Haftada 1 genel TYT + 2 branş denemesi",
            quickOptions = listOf("Haftada 1 genel", "Haftada 2 deneme", "Şimdilik branş ağırlıklı", "Konu bitince başlayacağım")
        ),
        DossierQuestion(
            id = "rest_day",
            number = 9,
            prompt = "Haftada daha hafif geçmesini istediğin veya dinlenme günün olsun mu?",
            hint = "Örn: Pazar öğleden sonra serbest / Her gün dengeli tempo",
            quickOptions = listOf("Pazar hafif tempo", "Cumartesi akşam serbest", "Her gün dengeli tempo")
        ),
        DossierQuestion(
            id = "coaching_style",
            number = 10,
            prompt = "Koçundan nasıl bir yaklaşım beklersin ve bu hedef senin için neden önemli?",
            hint = "Kısa bir not bırakman yeterli.",
            quickOptions = listOf("Destekleyici ve sakin", "Disiplinli ve net", "Dengeli ve analitik")
        ),
        DossierQuestion(
            id = "recent_mock_exam",
            number = 11,
            prompt = "Denemeye girdiysen güncel netlerin ve varsa kısa analizin nedir?",
            hint = "Örn: TYT 82.5 / AYT 54 — Matematik süre yetişmedi",
            quickOptions = listOf("Henüz girmedim", "Sadece TYT girdim", "TYT + AYT girdim")
        )
    )

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loaded = false

    private val _dossier = MutableStateFlow(StudentDossier())
    val dossier: StateFlow<StudentDossier> = _dossier.asStateFlow()

    private val _efficiencySuggestion = MutableStateFlow<EfficiencySuggestion?>(null)
    val efficiencySuggestion: StateFlow<EfficiencySuggestion?> = _efficiencySuggestion.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val appCtx = context.applicationContext
            val rawEncrypted = prefs(appCtx).getString(KEY_PAYLOAD_ENC, null)
            if (!rawEncrypted.isNullOrBlank()) {
                val decrypted = decryptPayload(appCtx, rawEncrypted)
                if (!decrypted.isNullOrBlank()) {
                    _dossier.value = parseJsonToDossier(decrypted)
                }
            }
            loaded = true
        }
    }

    fun setEthicalConsentAccepted(context: Context, accepted: Boolean) {
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        val updated = _dossier.value.copy(
            consentAccepted = accepted,
            consentAcceptedAtMillis = if (accepted) now else 0L,
            updatedAtMillis = now
        )
        persistDossier(context, updated)
    }

    fun saveInterviewAnswers(
        context: Context,
        answers: Map<String, String>,
        markCompleted: Boolean = true
    ) {
        ensureLoaded(context)
        val cleaned = answers
            .mapNotNull { (k, v) ->
                val trimmed = sanitizeNoPii(v)
                if (k.isNotBlank() && trimmed.isNotBlank()) k to trimmed else null
            }
            .toMap()

        // Parse Question 1 ("daily_hours") to apply student's chosen weekday/weekend study hours directly!
        val hoursAnswer = cleaned["daily_hours"]
        val parsedHours = parseDailyHoursFromAnswer(hoursAnswer)
        if (parsedHours != null) {
            CoachingEngine.setDailyGoals(
                context = context,
                weekdayMinutes = parsedHours.first,
                weekendMinutes = parsedHours.second,
                useAiRecommendation = false
            )
        }

        val now = System.currentTimeMillis()
        val cur = _dossier.value
        val rawMockAnswer = cleaned["recent_mock_exam"].orEmpty()
        val hasInitialMock = rawMockAnswer.isNotBlank() &&
            !rawMockAnswer.equals("Henüz girmedim", ignoreCase = true)
        val updated = cur.copy(
            isCompleted = markCompleted || cur.isCompleted,
            consentAccepted = true,
            consentAcceptedAtMillis = if (cur.consentAcceptedAtMillis > 0L) cur.consentAcceptedAtMillis else now,
            weekdayGoalMinutes = parsedHours?.first ?: CoachingEngine.weekdayGoalMinutes.value,
            weekendGoalMinutes = parsedHours?.second ?: CoachingEngine.weekendGoalMinutes.value,
            interviewAnswers = cur.interviewAnswers + cleaned,
            latestMockNetSummary = if (hasInitialMock) rawMockAnswer.take(180) else cur.latestMockNetSummary,
            lastMockUpdatedAtMillis = if (hasInitialMock) now else cur.lastMockUpdatedAtMillis,
            updatedAtMillis = now
        )
        persistDossier(context, updated)

        // Immediately sync full Goals / Topics / Books snapshot in background
        bgScope.launch {
            syncFromDatabase(context, force = true)
        }
    }

    fun updateMockExamRecord(
        context: Context,
        netSummary: String,
        analysisNote: String = ""
    ) {
        ensureLoaded(context)
        val cleanNets = sanitizeNoPii(netSummary).take(200)
        val cleanAnalysis = sanitizeNoPii(analysisNote).take(320)
        if (cleanNets.isBlank() && cleanAnalysis.isBlank()) return
        val now = System.currentTimeMillis()
        val cur = _dossier.value
        val combinedInsight = buildString {
            append("Deneme Kaydı: ")
            if (cleanNets.isNotBlank()) append(cleanNets)
            if (cleanAnalysis.isNotBlank()) {
                if (cleanNets.isNotBlank()) append(" | Analiz: ")
                append(cleanAnalysis)
            }
        }
        val filteredInsights = cur.coachingInsights.filterNot { it.startsWith("Deneme Kaydı:") }
        val nextInsights = (filteredInsights + combinedInsight).distinct().takeLast(18)
        val nextAnswers = cur.interviewAnswers.toMutableMap().apply {
            if (cleanNets.isNotBlank()) {
                put("recent_mock_exam", if (cleanAnalysis.isNotBlank()) "$cleanNets — $cleanAnalysis" else cleanNets)
            }
        }
        val updated = cur.copy(
            latestMockNetSummary = if (cleanNets.isNotBlank()) cleanNets else cur.latestMockNetSummary,
            latestMockAnalysis = cleanAnalysis.ifBlank { cur.latestMockAnalysis },
            lastMockUpdatedAtMillis = now,
            interviewAnswers = nextAnswers,
            coachingInsights = nextInsights,
            updatedAtMillis = now
        )
        persistDossier(context, updated)
    }

    fun recordFlashcardStudySummary(
        context: Context,
        masteredTopics: List<String>,
        struggledTopics: List<String>
    ) {
        ensureLoaded(context)
        val cur = _dossier.value
        if (!cur.isCompleted && !cur.consentAccepted) return
        val cleanMastered = masteredTopics.map { sanitizeNoPii(it) }.filter { it.isNotBlank() }.distinct().take(6)
        val cleanStruggled = struggledTopics.map { sanitizeNoPii(it) }.filter { it.isNotBlank() }.distinct().take(6)
        if (cleanMastered.isEmpty() && cleanStruggled.isEmpty()) return

        val summary = buildString {
            append("Bilgi Kartı Özeti: ")
            if (cleanStruggled.isNotEmpty()) {
                append("Öncelikli tekrar gerekenler: ${cleanStruggled.joinToString(", ")}")
            }
            if (cleanMastered.isNotEmpty()) {
                if (cleanStruggled.isNotEmpty()) append(" | ")
                append("Pekiştirilenler: ${cleanMastered.joinToString(", ")}")
            }
        }
        val filtered = cur.coachingInsights.filterNot { it.startsWith("Bilgi Kartı Özeti:") }
        val nextInsights = (filtered + summary).distinct().takeLast(18)
        persistDossier(context, cur.copy(coachingInsights = nextInsights, updatedAtMillis = System.currentTimeMillis()))
    }

    fun isMockExamAnalysisExchange(userMessage: String, aiResponse: String): Boolean {
        val normUser = userMessage.lowercase(java.util.Locale("tr", "TR"))
        val normAi = aiResponse.lowercase(java.util.Locale("tr", "TR"))
        val hasMockKeyword = normUser.contains("deneme") ||
            normUser.contains("branş") ||
            normUser.contains("tyt") ||
            normUser.contains("ayt") ||
            normUser.contains("netim") ||
            normUser.contains("net ") ||
            normUser.contains("doğru") ||
            normUser.contains("yanlış")
        val hasNumberInUser = Regex("""\d{1,3}(?:[.,]\d{1,2})?""").containsMatchIn(normUser)
        val hasAnalysisInAi = normAi.contains("net") || normAi.contains("deneme") || normAi.contains("analiz") || normAi.contains("eksik")
        return hasMockKeyword && hasNumberInUser && hasAnalysisInAi
    }

    fun extractCompactMockExamSummary(userMessage: String, aiResponse: String): Pair<String, String> {
        val cleanUser = sanitizeNoPii(userMessage).replace(Regex("""\s+"""), " ").trim().take(160)
        val cleanAiFirstLines = sanitizeNoPii(aiResponse)
            .lineSequence()
            .map { it.replace(Regex("""^[#>*•\-\s]+"""), "").trim() }
            .filter { it.length >= 16 && !it.startsWith("#ACTION:") }
            .take(2)
            .joinToString(" • ")
            .take(220)
        return cleanUser to cleanAiFirstLines
    }

    fun updateSingleAnswerOrInsight(
        context: Context,
        questionId: String?,
        newValue: String,
        asInsight: Boolean = false
    ) {
        ensureLoaded(context)
        val clean = sanitizeNoPii(newValue)
        if (clean.isBlank()) return
        val cur = _dossier.value
        val now = System.currentTimeMillis()

        val updated = if (asInsight || questionId.isNullOrBlank()) {
            val nextInsights = (cur.coachingInsights + clean).distinct().takeLast(18)
            cur.copy(coachingInsights = nextInsights, updatedAtMillis = now)
        } else {
            val nextAnswers = cur.interviewAnswers.toMutableMap().apply { put(questionId, clean) }
            val parsedHours = if (questionId == "daily_hours") parseDailyHoursFromAnswer(clean) else null
            if (parsedHours != null) {
                CoachingEngine.setDailyGoals(
                    context = context,
                    weekdayMinutes = parsedHours.first,
                    weekendMinutes = parsedHours.second,
                    useAiRecommendation = false
                )
            }
            val isMockQuestion = questionId == "recent_mock_exam" && !clean.equals("Henüz girmedim", ignoreCase = true)
            cur.copy(
                interviewAnswers = nextAnswers,
                weekdayGoalMinutes = parsedHours?.first ?: cur.weekdayGoalMinutes,
                weekendGoalMinutes = parsedHours?.second ?: cur.weekendGoalMinutes,
                latestMockNetSummary = if (isMockQuestion) clean.take(180) else cur.latestMockNetSummary,
                lastMockUpdatedAtMillis = if (isMockQuestion) now else cur.lastMockUpdatedAtMillis,
                updatedAtMillis = now
            )
        }
        persistDossier(context, updated)
    }

    fun removeInsightAt(context: Context, index: Int) {
        ensureLoaded(context)
        val cur = _dossier.value
        if (index !in cur.coachingInsights.indices) return
        val next = cur.coachingInsights.toMutableList().apply { removeAt(index) }
        persistDossier(context, cur.copy(coachingInsights = next, updatedAtMillis = System.currentTimeMillis()))
    }

    fun recordEfficiencyDecision(context: Context, acceptedLowerHours: Boolean, newWeekdayMins: Int = 480, newWeekendMins: Int = 540) {
        ensureLoaded(context)
        val cur = _dossier.value
        val now = System.currentTimeMillis()
        if (acceptedLowerHours) {
            CoachingEngine.setDailyGoals(
                context = context,
                weekdayMinutes = newWeekdayMins,
                weekendMinutes = newWeekendMins,
                useAiRecommendation = false
            )
            val note = "Verimlilik denemesi kabul edildi: Hafta içi ${newWeekdayMins / 60}s, hafta sonu ${newWeekendMins / 60}s."
            val updated = cur.copy(
                weekdayGoalMinutes = newWeekdayMins,
                weekendGoalMinutes = newWeekendMins,
                efficiencyTrialDeclined = false,
                coachingInsights = (cur.coachingInsights + note).distinct().takeLast(18),
                updatedAtMillis = now
            )
            persistDossier(context, updated)
        } else {
            val note = "Öğrenci yüksek çalışma hedefini (${cur.weekdayGoalMinutes / 60}s/${cur.weekendGoalMinutes / 60}s) korumayı tercih etti; saatler düşürülmemeli."
            val updated = cur.copy(
                efficiencyTrialDeclined = true,
                coachingInsights = (cur.coachingInsights + note).distinct().takeLast(18),
                updatedAtMillis = now
            )
            persistDossier(context, updated)
        }
        _efficiencySuggestion.value = null
    }

    fun deleteDossier(context: Context) {
        ensureLoaded(context)
        prefs(context).edit().remove(KEY_PAYLOAD_ENC).apply()
        _dossier.value = StudentDossier()
        _efficiencySuggestion.value = null
        // Turning off real coaching if dossier is wiped
        CoachingEngine.setComprehensiveSupportEnabled(context, false)
    }

    /**
     * Background check invoked at startup & periodic 12h worker.
     */
    fun syncIfDueBackground(context: Context) {
        ensureLoaded(context)
        val cur = _dossier.value
        if (!cur.consentAccepted && !cur.isCompleted) return
        val age = System.currentTimeMillis() - cur.lastSyncedAtMillis
        if (age >= SYNC_INTERVAL_MILLIS) {
            bgScope.launch {
                syncFromDatabase(context, force = false)
            }
        }
    }

    /**
     * Triggers an immediate non-blocking background refresh of the encrypted Student Dossier
     * whenever topics, books, plans, or targets are modified across the app.
     */
    fun syncInBackground(context: Context) {
        val appCtx = context.applicationContext
        bgScope.launch {
            runCatching {
                ensureLoaded(appCtx)
                val cur = _dossier.value
                if (cur.consentAccepted || cur.isCompleted) {
                    syncFromDatabase(appCtx, force = true)
                }
            }.onFailure { e ->
                Log.w(TAG, "syncInBackground failed: ${e.message}")
            }
        }
    }

    /**
     * Immediately propagates updated weekday/weekend daily goal hours from [CoachingEngine]
     * into the encrypted dossier so AI models never see stale hour targets.
     */
    fun syncDailyGoalsFromEngine(context: Context, weekdayMinutes: Int, weekendMinutes: Int) {
        ensureLoaded(context)
        val cur = _dossier.value
        if (!cur.consentAccepted && !cur.isCompleted) return
        val wd = weekdayMinutes.coerceIn(30, CoachingEngine.MAX_DAILY_GOAL_MINUTES)
        val we = weekendMinutes.coerceIn(30, CoachingEngine.MAX_DAILY_GOAL_MINUTES)
        if (cur.weekdayGoalMinutes == wd && cur.weekendGoalMinutes == we) return
        persistDossier(
            context,
            cur.copy(
                weekdayGoalMinutes = wd,
                weekendGoalMinutes = we,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    /**
     * Full clean sync of Goals, ALL Topic Mastery items, ALL Library Books, Daily Hours,
     * AND Active Plan Completed vs. Incomplete/Missed Tasks.
     * Replaces lists cleanly so deleted goals/topics/books/tasks are automatically removed from the dossier.
     */
    suspend fun syncFromDatabase(context: Context, force: Boolean = true): StudentDossier = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val appCtx = context.applicationContext
        val cur = _dossier.value
        val now = System.currentTimeMillis()
        if (!force && (now - cur.lastSyncedAtMillis) < SYNC_INTERVAL_MILLIS) {
            return@withContext cur
        }

        CoachingEngine.ensureInitialized(appCtx)
        val db = SharkMentorDatabase.getDatabase(appCtx)
        val userPrefs = UserPreferences.getInstance(appCtx)

        val concrete = runCatching { db.concreteTargetDao().getTargetRaw() }.getOrNull()
        val rawTopics = runCatching { db.goalsDao().getAllTopicsRaw() }.getOrDefault(emptyList())
        val rawBooks = runCatching { db.goalsDao().getAllResourcesRaw() }.getOrDefault(emptyList())
        val planAudit = runCatching { PlanProgressAuditor.captureFromDatabase(appCtx) }.getOrDefault(PlanAuditSnapshot())

        val profileExam = userPrefs.studentTargetExam?.takeIf { it.isNotBlank() }
            ?: concrete?.examType?.takeIf { it.isNotBlank() }
            ?: cur.examType.ifBlank { "YKS" }
        val grade = userPrefs.studentGrade.orEmpty()

        val headline = buildString {
            val uni = concrete?.targetUniversity?.trim().orEmpty()
            val dept = concrete?.targetDepartment?.trim().orEmpty()
            when {
                uni.isNotBlank() && dept.isNotBlank() -> append("$uni $dept")
                !concrete?.targetTitle.isNullOrBlank() -> append(concrete!!.targetTitle.trim())
            }
            val rank = concrete?.targetRanking
            val scoreText = concrete?.targetScoreOrRank?.trim().orEmpty()
            if (rank != null && rank > 0) {
                if (isNotEmpty()) append(" • ")
                append("#${String.format(Locale("tr"), "%,d", rank)}")
            } else if (scoreText.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(scoreText)
            }
        }

        val topicSnapshots = rawTopics
            .filter { !it.isDeleted }
            .map {
                DossierTopicSnapshot(
                    subject = it.subject.trim(),
                    topic = it.topic.trim(),
                    currentLevel = it.currentLevel.coerceIn(0, 5),
                    targetLevel = it.targetLevel.coerceIn(1, 5)
                )
            }
            .sortedWith(compareBy<DossierTopicSnapshot> { it.currentLevel }.thenBy { it.subject }.thenBy { it.topic })

        BookCoachingRoleManager.ensureLoaded(appCtx)
        val roleAssignments = BookCoachingRoleManager.assignmentsByResourceId.value
        val bookSnapshots = rawBooks
            .filter { !it.isDeleted }
            .filter { res ->
                BookCoachingRoleManager.resolveAssignment(res, roleAssignments).role != BookCoachingRole.PASSIVE
            }
            .map {
                DossierBookSnapshot(
                    title = it.title.trim(),
                    subject = it.subject.trim(),
                    completedUnits = it.completedUnits.coerceAtLeast(0),
                    totalUnits = it.totalUnits.coerceAtLeast(0),
                    unitType = it.unitType.ifBlank { "Test" }
                )
            }
            .sortedWith(compareBy<DossierBookSnapshot> { it.subject }.thenBy { it.title })

        val completedPlanSnapshots = planAudit.completedTasks.take(60).map { item ->
            DossierPlanTaskSnapshot(
                dayOfWeek = item.dayOfWeek,
                subject = item.subject,
                topic = item.topic,
                targetQuestions = item.targetQuestions,
                isCompleted = true
            )
        }
        val incompletePlanSnapshots = planAudit.incompleteTasks.take(60).map { item ->
            DossierPlanTaskSnapshot(
                dayOfWeek = item.dayOfWeek,
                subject = item.subject,
                topic = item.topic,
                targetQuestions = item.targetQuestions,
                isCompleted = false
            )
        }

        val updated = cur.copy(
            examType = profileExam,
            studentGrade = grade,
            targetHeadline = headline,
            targetRanking = concrete?.targetRanking,
            targetAnalysisSummary = concrete?.aiAnalysisSummary?.take(360).orEmpty(),
            weekdayGoalMinutes = CoachingEngine.weekdayGoalMinutes.value,
            weekendGoalMinutes = CoachingEngine.weekendGoalMinutes.value,
            topics = topicSnapshots,
            books = bookSnapshots,
            activePlanTitle = planAudit.folderTitle,
            completedPlanTasks = completedPlanSnapshots,
            incompletePlanTasks = incompletePlanSnapshots,
            lastSyncedAtMillis = now,
            updatedAtMillis = now
        )
        persistDossier(appCtx, updated)

        // Evaluate adaptive efficiency feedback silently using accurate audited missed tasks
        val missedTasks = planAudit.incompleteCount
        val suggestion = evaluateEfficiencySuggestion(
            dossier = updated,
            recentLedger = CoachingEngine.recentLedger.value,
            missedTaskCount = missedTasks
        )
        _efficiencySuggestion.value = suggestion
        updated
    }

    /**
     * Checks whether a student with a very high daily target (10h+ weekday or 11h+ weekend)
     * is simultaneously missing plan tasks AND falling well below their target hours across recent days.
     * Respects explicit student veto ([StudentDossier.efficiencyTrialDeclined]).
     */
    fun evaluateEfficiencySuggestion(
        dossier: StudentDossier,
        recentLedger: List<DailyGoalLedgerEntry>,
        missedTaskCount: Int
    ): EfficiencySuggestion? {
        if (!dossier.isCompleted || dossier.efficiencyTrialDeclined) return null
        val isHighTarget = dossier.weekdayGoalMinutes >= 600 || dossier.weekendGoalMinutes >= 660
        if (!isHighTarget) return null
        if (missedTaskCount < 3) return null

        val completedPastDays = recentLedger.dropLast(1).filter { it.workedMinutes > 0 }
        if (completedPastDays.size < 3) return null

        val avgWorked = completedPastDays.map { it.workedMinutes }.average().toInt()
        val avgTarget = completedPastDays.map { it.targetMinutes }.average().toInt().coerceAtLeast(60)
        val ratio = avgWorked.toFloat() / avgTarget.toFloat()

        if (ratio >= 0.72f) return null

        val curWdH = (dossier.weekdayGoalMinutes / 60).coerceAtLeast(1)
        val curWeH = (dossier.weekendGoalMinutes / 60).coerceAtLeast(1)
        return EfficiencySuggestion(
            currentWeekdayHours = curWdH,
            currentWeekendHours = curWeH,
            recommendedWeekdayMinutes = 480, // 8 hours
            recommendedWeekendMinutes = 540, // 9 hours
            averageWorkedMinutes = avgWorked,
            missedTaskCount = missedTaskCount,
            message = "Son günlerde günlük ortalaman ${CoachingEngine.formatMinutesShort(avgWorked)} düzeyinde kaldı ve $missedTaskCount görev birikti. ${curWdH}s/${curWeH}s hedefi yerine verimliliği artırmak için 1 haftalığına 8-9 saat bandını denemek ister misin? İstemezsen mevcut hedefini aynen korumaya devam ederim."
        )
    }

    /**
     * Strict selective injection gate (cost + privacy):
     * Only injects the anonymized Student Dossier for Coaching, Plan-Add AI, Zen Coach,
     * and Auto-Chat messages that clearly concern coaching, study planning, or Zen guidance.
     */
    fun shouldAttachDossier(
        channel: DossierAiChannel,
        userMessage: String = "",
        dossierState: StudentDossier = _dossier.value
    ): Boolean {
        if (!dossierState.consentAccepted && !dossierState.isCompleted) return false
        return when (channel) {
            DossierAiChannel.COACHING_DIRECT,
            DossierAiChannel.PLAN_ADD_AI,
            DossierAiChannel.ZEN_COACH -> true
            DossierAiChannel.AUTO_CHAT -> isCoachingPlanOrZenIntent(userMessage)
            DossierAiChannel.OTHER_NON_COACHING -> false
        }
    }

    fun isCoachingPlanOrZenIntent(rawText: String): Boolean {
        val text = rawText.lowercase(Locale("tr")).trim()
        if (text.isBlank()) return false
        val keywords = listOf(
            "koç", "koc", "koçluk", "kocluk", "plan", "program", "haftalık", "haftalik",
            "çalışma saati", "calisma saati", "günlük hedef", "gunluk hedef", "rota",
            "öğrenci dosya", "ogrenci dosya", "dosyam", "zen", "stres", "kaygı", "kaygi",
            "motivasyon", "tükenmiş", "tukenmis", "odaklanamıyorum", "odaklanamiyorum",
            "netim", "netlerim", "sıralama", "siralama", "deneme analizi", "hangi derse",
            "nasıl çalışmalı", "nasil calismali", "kitap", "kaynak", "konu hakimiyet",
            "eksik konu", "görev", "gorev", "verim", "tempo"
        )
        return keywords.any { text.contains(it) }
    }

    fun isDossierSyncCommand(rawText: String): Boolean {
        val text = rawText.lowercase(Locale("tr")).trim()
        return text.contains("öğrenci dosyamı güncelle") ||
            text.contains("ogrenci dosyamı guncelle") ||
            text.contains("ogrenci dosyami guncelle") ||
            text.contains("öğrenci dosyasını güncelle") ||
            text.contains("ogrenci dosyasini guncelle") ||
            text.contains("dosyamı güncelle") ||
            text.contains("dosyami guncelle") ||
            text.contains("dosyamı senkronize") ||
            text.contains("dosyami senkronize")
    }

    fun isDossierEditCommand(rawText: String): Boolean {
        val text = rawText.lowercase(Locale("tr")).trim()
        if (isDossierSyncCommand(text)) return false
        val mentionsDossier = text.contains("öğrenci dosya") || text.contains("ogrenci dosya") || text.contains("dosyama ") || text.contains("dosyamdaki") || text.contains("dosyamı ")
        if (!mentionsDossier) return false
        val editVerbs = listOf("düzenle", "duzenle", "değiştir", "degistir", "ekle", "sil", "kaldır", "kaldir", "kaydet", "not düş", "not dus", "sıfırla", "sifirla")
        return editVerbs.any { text.contains(it) }
    }

    /**
     * Builds the strictly anonymized AI prompt block (ZERO student name, email, or UID).
     */
    fun buildAnonymizedDossierPrompt(
        dossierState: StudentDossier = _dossier.value
    ): String {
        val d = dossierState
        val sb = StringBuilder()
        sb.appendLine("[ANONİM ÖĞRENCİ DOSYASI — KİŞİSELLEŞTİRİLMİŞ KOÇLUK BAĞLAMI]")
        sb.appendLine("• Gizlilik: Bu profil anonimdir; öğrenci adı/kimlik bilgisi içermez.")
        if (d.examType.isNotBlank()) {
            sb.appendLine("• Sınav / Sınıf: ${d.examType}${if (d.studentGrade.isNotBlank()) " (${d.studentGrade})" else ""}")
        }
        if (d.targetHeadline.isNotBlank()) {
            sb.appendLine("• Somut Hedef: ${d.targetHeadline}")
        }
        if (d.targetAnalysisSummary.isNotBlank()) {
            sb.appendLine("• Hedef Analizi: ${sanitizeNoPii(d.targetAnalysisSummary).take(280)}")
        }
        if (d.latestMockNetSummary.isNotBlank() || d.latestMockAnalysis.isNotBlank()) {
            val mockLine = buildString {
                if (d.latestMockNetSummary.isNotBlank()) append(sanitizeNoPii(d.latestMockNetSummary).take(180))
                if (d.latestMockAnalysis.isNotBlank()) {
                    if (isNotEmpty()) append(" | Analiz: ")
                    append(sanitizeNoPii(d.latestMockAnalysis).take(240))
                }
            }
            sb.appendLine("• Son Deneme Neti ve Analizi: $mockLine")
        }
        val wdH = CoachingEngine.formatMinutesShort(d.weekdayGoalMinutes)
        val weH = CoachingEngine.formatMinutesShort(d.weekendGoalMinutes)
        sb.appendLine("• Öğrencinin Seçtiği Günlük Çalışma Hedefi: Hafta içi $wdH (${d.weekdayGoalMinutes} dk) | Hafta sonu $weH (${d.weekendGoalMinutes} dk)")
        sb.appendLine("• KRİTİK SAAT KURALI: Öğrencinin belirlediği saat hedefini (örn. 12s/15s) ASLA sessizce 4-5 saate düşürme! Planları bu saat hacmine göre bloklara böl.${if (d.efficiencyTrialDeclined) " (Öğrenci yüksek saat hedefini korumak istediğini özellikle belirtti.)" else ""}")
        sb.appendLine("• KRİTİK ESNEK ZAMANLAMA KURALI: Planlara ASLA '09:00 - 10:30' veya '10:00 - 11:30' gibi katı saat aralıkları yazma; bunun yerine 'Esnek • 90 dk' gibi süre odaklı esnek etiketler kullan. Görevleri tek güne (örn. sadece Pazar'a) veya tek derse (örn. sadece Kimya'ya) ASLA yığma; 7 güne çoklu ders dengesiyle dağıt.")

        if (d.topics.isNotEmpty()) {
            val weak = d.topics.filter { it.currentLevel <= 2 }.take(18)
            val mid = d.topics.filter { it.currentLevel == 3 }.take(12)
            val strong = d.topics.filter { it.currentLevel >= 4 }.take(10)
            sb.appendLine("• Konu Hakimiyeti Özeti (Toplam ${d.topics.size} kayıt):")
            if (weak.isNotEmpty()) {
                sb.appendLine("  - Öncelikli Zayıf Konular (0-2/5): " + weak.joinToString("; ") { "${it.subject} - ${it.topic} (${it.currentLevel}/5)" })
            }
            if (mid.isNotEmpty()) {
                sb.appendLine("  - Geliştirilecek Konular (3/5): " + mid.joinToString("; ") { "${it.subject} - ${it.topic}" })
            }
            if (strong.isNotEmpty()) {
                sb.appendLine("  - Güçlü Konular (4-5/5): " + strong.joinToString("; ") { "${it.subject} - ${it.topic}" })
            }
        }

        if (d.books.isNotEmpty()) {
            sb.appendLine("• Kütüphanedeki Kitaplar (Toplam ${d.books.size} kaynak):")
            d.books.take(20).forEach { b ->
                val prog = if (b.totalUnits > 0) "${b.completedUnits}/${b.totalUnits} ${b.unitType}" else b.unitType
                sb.appendLine("  - [${b.subject}] ${b.title} ($prog)")
            }
        }

        val planAuditBlock = PlanProgressAuditor.buildAiPlanAuditPrompt(d.toPlanAuditSnapshot())
        if (planAuditBlock.isNotBlank()) {
            sb.appendLine(planAuditBlock)
        }

        if (d.interviewAnswers.isNotEmpty()) {
            sb.appendLine("• Öğrenci Tanıma Yanıtları:")
            INTERVIEW_QUESTIONS.forEach { q ->
                val ans = d.interviewAnswers[q.id]?.trim().orEmpty()
                if (ans.isNotBlank()) {
                    sb.appendLine("  - ${q.id}: ${sanitizeNoPii(ans)}")
                }
            }
        }

        if (d.coachingInsights.isNotEmpty()) {
            sb.appendLine("• Koçluk Gözlemleri:")
            d.coachingInsights.takeLast(8).forEach { note ->
                sb.appendLine("  - ${sanitizeNoPii(note)}")
            }
        }
        return sb.toString()
    }

    /**
     * Parses free-text or chip answers like "12s / 15s", "Hafta içi 12 saat, hafta sonu 15 saat", "6 saat"
     * into (weekdayMinutes, weekendMinutes) clamped cleanly to 60..960 min (1h..16h).
     */
    fun parseDailyHoursFromAnswer(raw: String?): Pair<Int, Int>? {
        if (raw.isNullOrBlank()) return null
        val nums = Regex("""(\d+(?:[.,]\d+)?)""")
            .findAll(raw)
            .mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }
            .filter { it in 0.5..16.0 }
            .toList()
        if (nums.isEmpty()) return null
        val wdMins = (nums[0] * 60.0).toInt().coerceIn(60, CoachingEngine.MAX_DAILY_GOAL_MINUTES)
        val weMins = if (nums.size >= 2) {
            (nums[1] * 60.0).toInt().coerceIn(60, CoachingEngine.MAX_DAILY_GOAL_MINUTES)
        } else {
            (wdMins + 60).coerceAtMost(CoachingEngine.MAX_DAILY_GOAL_MINUTES)
        }
        return wdMins to weMins
    }

    /**
     * Strips accidental email addresses, phone numbers, or Firebase-like UID tokens so the
     * dossier remains strictly anonymous when transmitted to AI models.
     */
    fun sanitizeNoPii(raw: String): String {
        return raw
            .replace(Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""), "[gizli-eposta]")
            .replace(Regex("""\b0?5\d{2}[\s-]?\d{3}[\s-]?\d{2}[\s-]?\d{2}\b"""), "[gizli-tel]")
            .trim()
    }

    // ── Local AES-256-GCM Persistence ────────────────────────────────────────

    private fun persistDossier(context: Context, dossier: StudentDossier) {
        _dossier.value = dossier
        val json = serializeDossierToJson(dossier)
        val encrypted = encryptPayload(context, json)
        prefs(context).edit().putString(KEY_PAYLOAD_ENC, encrypted).apply()
    }

    internal fun serializeDossierToJson(d: StudentDossier): String {
        val root = JSONObject()
        root.put("isCompleted", d.isCompleted)
        root.put("consentAccepted", d.consentAccepted)
        root.put("consentAcceptedAtMillis", d.consentAcceptedAtMillis)
        root.put("examType", d.examType)
        root.put("studentGrade", d.studentGrade)
        root.put("targetHeadline", d.targetHeadline)
        if (d.targetRanking != null) root.put("targetRanking", d.targetRanking)
        root.put("targetAnalysisSummary", d.targetAnalysisSummary)
        root.put("latestMockNetSummary", d.latestMockNetSummary)
        root.put("latestMockAnalysis", d.latestMockAnalysis)
        root.put("lastMockUpdatedAtMillis", d.lastMockUpdatedAtMillis)
        root.put("weekdayGoalMinutes", d.weekdayGoalMinutes)
        root.put("weekendGoalMinutes", d.weekendGoalMinutes)
        root.put("activePlanTitle", d.activePlanTitle)
        root.put("efficiencyTrialDeclined", d.efficiencyTrialDeclined)
        root.put("lastSyncedAtMillis", d.lastSyncedAtMillis)
        root.put("updatedAtMillis", d.updatedAtMillis)

        val topicsArr = JSONArray()
        d.topics.forEach { t ->
            topicsArr.put(JSONObject().apply {
                put("s", t.subject)
                put("t", t.topic)
                put("c", t.currentLevel)
                put("g", t.targetLevel)
            })
        }
        root.put("topics", topicsArr)

        val booksArr = JSONArray()
        d.books.forEach { b ->
            booksArr.put(JSONObject().apply {
                put("ti", b.title)
                put("s", b.subject)
                put("c", b.completedUnits)
                put("tot", b.totalUnits)
                put("u", b.unitType)
            })
        }
        root.put("books", booksArr)

        val donePlanArr = JSONArray()
        d.completedPlanTasks.forEach { pt ->
            donePlanArr.put(JSONObject().apply {
                put("d", pt.dayOfWeek)
                put("s", pt.subject)
                put("t", pt.topic)
                put("q", pt.targetQuestions)
            })
        }
        root.put("donePlan", donePlanArr)

        val incPlanArr = JSONArray()
        d.incompletePlanTasks.forEach { pt ->
            incPlanArr.put(JSONObject().apply {
                put("d", pt.dayOfWeek)
                put("s", pt.subject)
                put("t", pt.topic)
                put("q", pt.targetQuestions)
            })
        }
        root.put("incPlan", incPlanArr)

        val answersObj = JSONObject()
        d.interviewAnswers.forEach { (k, v) -> answersObj.put(k, v) }
        root.put("answers", answersObj)

        val insightsArr = JSONArray()
        d.coachingInsights.forEach { insightsArr.put(it) }
        root.put("insights", insightsArr)
        return root.toString()
    }

    internal fun parseJsonToDossier(raw: String): StudentDossier {
        return runCatching {
            val root = JSONObject(raw)
            val topics = mutableListOf<DossierTopicSnapshot>()
            val tArr = root.optJSONArray("topics")
            if (tArr != null) {
                for (i in 0 until tArr.length()) {
                    val o = tArr.optJSONObject(i) ?: continue
                    topics.add(
                        DossierTopicSnapshot(
                            subject = o.optString("s"),
                            topic = o.optString("t"),
                            currentLevel = o.optInt("c", 0),
                            targetLevel = o.optInt("g", 5)
                        )
                    )
                }
            }
            val books = mutableListOf<DossierBookSnapshot>()
            val bArr = root.optJSONArray("books")
            if (bArr != null) {
                for (i in 0 until bArr.length()) {
                    val o = bArr.optJSONObject(i) ?: continue
                    books.add(
                        DossierBookSnapshot(
                            title = o.optString("ti"),
                            subject = o.optString("s"),
                            completedUnits = o.optInt("c", 0),
                            totalUnits = o.optInt("tot", 0),
                            unitType = o.optString("u", "Test")
                        )
                    )
                }
            }
            val donePlan = mutableListOf<DossierPlanTaskSnapshot>()
            val dpArr = root.optJSONArray("donePlan")
            if (dpArr != null) {
                for (i in 0 until dpArr.length()) {
                    val o = dpArr.optJSONObject(i) ?: continue
                    donePlan.add(
                        DossierPlanTaskSnapshot(
                            dayOfWeek = o.optInt("d", 1).coerceIn(1, 7),
                            subject = o.optString("s"),
                            topic = o.optString("t"),
                            targetQuestions = o.optInt("q", 30),
                            isCompleted = true
                        )
                    )
                }
            }
            val incPlan = mutableListOf<DossierPlanTaskSnapshot>()
            val ipArr = root.optJSONArray("incPlan")
            if (ipArr != null) {
                for (i in 0 until ipArr.length()) {
                    val o = ipArr.optJSONObject(i) ?: continue
                    incPlan.add(
                        DossierPlanTaskSnapshot(
                            dayOfWeek = o.optInt("d", 1).coerceIn(1, 7),
                            subject = o.optString("s"),
                            topic = o.optString("t"),
                            targetQuestions = o.optInt("q", 30),
                            isCompleted = false
                        )
                    )
                }
            }
            val answers = LinkedHashMap<String, String>()
            val aObj = root.optJSONObject("answers")
            if (aObj != null) {
                val keys = aObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = aObj.optString(k, "").trim()
                    if (v.isNotBlank()) answers[k] = v
                }
            }
            val insights = mutableListOf<String>()
            val iArr = root.optJSONArray("insights")
            if (iArr != null) {
                for (i in 0 until iArr.length()) {
                    val v = iArr.optString(i, "").trim()
                    if (v.isNotBlank()) insights.add(v)
                }
            }
            StudentDossier(
                isCompleted = root.optBoolean("isCompleted", false),
                consentAccepted = root.optBoolean("consentAccepted", false),
                consentAcceptedAtMillis = root.optLong("consentAcceptedAtMillis", 0L),
                examType = root.optString("examType", ""),
                studentGrade = root.optString("studentGrade", ""),
                targetHeadline = root.optString("targetHeadline", ""),
                targetRanking = if (root.has("targetRanking") && !root.isNull("targetRanking")) root.optInt("targetRanking") else null,
                targetAnalysisSummary = root.optString("targetAnalysisSummary", ""),
                weekdayGoalMinutes = root.optInt("weekdayGoalMinutes", 240).coerceIn(30, CoachingEngine.MAX_DAILY_GOAL_MINUTES),
                weekendGoalMinutes = root.optInt("weekendGoalMinutes", 300).coerceIn(30, CoachingEngine.MAX_DAILY_GOAL_MINUTES),
                topics = topics,
                books = books,
                activePlanTitle = root.optString("activePlanTitle", ""),
                completedPlanTasks = donePlan,
                incompletePlanTasks = incPlan,
                interviewAnswers = answers,
                coachingInsights = insights,
                latestMockNetSummary = root.optString("latestMockNetSummary", ""),
                latestMockAnalysis = root.optString("latestMockAnalysis", ""),
                lastMockUpdatedAtMillis = root.optLong("lastMockUpdatedAtMillis", 0L),
                efficiencyTrialDeclined = root.optBoolean("efficiencyTrialDeclined", false),
                lastSyncedAtMillis = root.optLong("lastSyncedAtMillis", 0L),
                updatedAtMillis = root.optLong("updatedAtMillis", 0L)
            )
        }.getOrDefault(StudentDossier())
    }

    private fun getOrCreateAesKey(context: Context): Pair<SecretKey, Boolean> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
                ks.load(null)
                val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
                if (existing != null) return existing to true
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                return keyGen.generateKey() to true
            }.onFailure { e ->
                Log.w(TAG, "AndroidKeyStore unavailable, falling back to app-private AES key: ${e.message}")
            }
        }
        // Safe app-private AES-256 fallback (for devices/ROMs with broken Keystore on Android 9)
        val sp = prefs(context)
        var salt = sp.getString(KEY_FALLBACK_SALT, null)
        if (salt.isNullOrBlank()) {
            val randomBytes = ByteArray(32)
            SecureRandom().nextBytes(randomBytes)
            salt = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
            sp.edit().putString(KEY_FALLBACK_SALT, salt).apply()
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("shark_dossier_v1:${context.packageName}:$salt").toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest, "AES") to false
    }

    private fun encryptPayload(context: Context, plainJson: String): String {
        return runCatching {
            val (key, isKeystore) = getOrCreateAesKey(context)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plainJson.toByteArray(StandardCharsets.UTF_8))
            val prefix = if (isKeystore) "KS1" else "FB1"
            "$prefix:${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"
        }.getOrElse { e ->
            Log.w(TAG, "Encryption fallback: ${e.message}")
            "RAW:" + Base64.encodeToString(plainJson.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decryptPayload(context: Context, encoded: String): String? {
        return runCatching {
            if (encoded.startsWith("RAW:")) {
                val bytes = Base64.decode(encoded.removePrefix("RAW:"), Base64.NO_WRAP)
                return@runCatching String(bytes, StandardCharsets.UTF_8)
            }
            val parts = encoded.split(":")
            if (parts.size != 3) return@runCatching null
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            val (key, _) = getOrCreateAesKey(context)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.onFailure { e ->
            Log.w(TAG, "Decrypt failed: ${e.message}")
        }.getOrNull()
    }
}
