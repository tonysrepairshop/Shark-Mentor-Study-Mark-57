package com.example.sharkmentor.core.coaching

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.TargetType
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

@Immutable
data class PlanTaskAuditItem(
    val id: String,
    val dayOfWeek: Int,
    val subject: String,
    val topic: String,
    val scheduledTime: String,
    val targetQuestions: Int,
    val isCompleted: Boolean,
    val isOverdueOrDueToday: Boolean = false
)

@Immutable
data class PlanAuditSnapshot(
    val folderTitle: String = "",
    val totalTasks: Int = 0,
    val completedTasks: List<PlanTaskAuditItem> = emptyList(),
    val incompleteTasks: List<PlanTaskAuditItem> = emptyList(),
    val capturedAtMillis: Long = System.currentTimeMillis()
) {
    val completedCount: Int get() = completedTasks.size
    val incompleteCount: Int get() = incompleteTasks.size
    val completionPercent: Int
        get() = if (totalTasks <= 0) 0 else ((completedCount * 100f) / totalTasks.toFloat()).toInt().coerceIn(0, 100)
    val hasAnyTasks: Boolean get() = totalTasks > 0
}

/**
 * Navigation & state bus for Sunday Weekly Check-In notification deep links and check-in freshness tracking.
 */
object PlanReadinessNavBus {
    private const val PREFS_NAME = "shark_mark65_plan_readiness_prefs"
    private const val KEY_LAST_CHECKIN_DATE = "last_checkin_completed_iso_date"
    private const val KEY_SUNDAY_BANNER_DISMISSED_DATE = "sunday_banner_dismissed_iso_date"

    private val _openSundayCheckInRequested = MutableStateFlow(false)
    val openSundayCheckInRequested: StateFlow<Boolean> = _openSundayCheckInRequested.asStateFlow()

    fun requestOpenSundayCheckIn() {
        _openSundayCheckInRequested.value = true
    }

    fun consumeOpenSundayCheckIn() {
        _openSundayCheckInRequested.value = false
    }

    fun markCheckInCompletedToday(context: Context, date: LocalDate = LocalDate.now()) {
        val iso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CHECKIN_DATE, iso)
            .apply()
    }

    fun getLastCompletedCheckInDate(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CHECKIN_DATE, null)
    }

    fun isCheckInCompletedToday(context: Context, date: LocalDate = LocalDate.now()): Boolean {
        val iso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return getLastCompletedCheckInDate(context) == iso
    }

    fun dismissSundayBannerToday(context: Context, date: LocalDate = LocalDate.now()) {
        val iso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUNDAY_BANNER_DISMISSED_DATE, iso)
            .apply()
    }

    fun shouldShowSundayBanner(context: Context, date: LocalDate = LocalDate.now()): Boolean {
        if (date.dayOfWeek != DayOfWeek.SUNDAY) return false
        val iso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val completedToday = sp.getString(KEY_LAST_CHECKIN_DATE, null) == iso
        val dismissedToday = sp.getString(KEY_SUNDAY_BANNER_DISMISSED_DATE, null) == iso
        return !completedToday && !dismissedToday
    }
}

/**
 * Mark 65 Plan Progress Auditor & Cumulative Carry-Over Engine:
 * - Gives all AI coaching/planning engines structured visibility into COMPLETED vs. INCOMPLETE/MISSED tasks.
 * - Prevents cumulative learning gaps by carrying forward unfinished tasks first into newly generated plans.
 * - Prevents mindless repetition of already completed tasks unless mastery is still critically low (<= 2/5).
 * - Detects plan creation / rebuild intents in Shark AI chat to trigger pre-plan mastery & book check-in.
 */
object PlanProgressAuditor {

    private val NON_ALNUM_REGEX = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    /**
     * Captures the latest unified plan completion audit from local Room tables (`planDao`, fallback `studyPlanDao`,
     * fallback `targetDao` short-term tasks).
     */
    suspend fun captureFromDatabase(
        context: Context,
        todayIsoDay: Int = CoachingEngine.calendarDayToIso(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    ): PlanAuditSnapshot = withContext(Dispatchers.IO) {
        val db = SharkMentorDatabase.getDatabase(context.applicationContext)

        // 1. Primary source: latest active PlanFolder & PlanEntity tasks
        val folders = runCatching { db.planDao().getAllFoldersSync() }.getOrDefault(emptyList())
        val latestFolder = folders.firstOrNull()
        if (latestFolder != null) {
            val tasks = runCatching {
                db.planDao().getTasksForFolder(latestFolder.id).firstOrNull().orEmpty()
            }.getOrDefault(emptyList())
            if (tasks.isNotEmpty()) {
                return@withContext auditPlanEntities(
                    folderTitle = latestFolder.title,
                    tasks = tasks,
                    todayIsoDay = todayIsoDay
                )
            }
        }

        // 2. Secondary source: ActivePlanEntity & StudyTaskEntity (StudyPlanDao)
        val activeStudyPlan = runCatching { db.studyPlanDao().observeActivePlan().firstOrNull() }.getOrNull()
        val studyTasks = runCatching { db.studyPlanDao().observeAllTasks().firstOrNull().orEmpty() }.getOrDefault(emptyList())
        if (studyTasks.isNotEmpty()) {
            val mapped = studyTasks.map { st ->
                val isoDay = (st.dayIndex + 1).coerceIn(1, 7)
                PlanTaskAuditItem(
                    id = "study_${st.localId}",
                    dayOfWeek = isoDay,
                    subject = st.subject.trim().ifBlank { "Ders" },
                    topic = st.topic.trim().ifBlank { st.subject.trim() },
                    scheduledTime = st.time.ifBlank { "Esnek • 90 dk" },
                    targetQuestions = Regex("""(\d+)""").find(st.goal)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 30,
                    isCompleted = st.isCompleted,
                    isOverdueOrDueToday = !st.isCompleted && isoDay <= todayIsoDay
                )
            }
            return@withContext buildSnapshotFromItems(
                folderTitle = activeStudyPlan?.title.orEmpty().ifBlank { "Aktif Çalışma Planı" },
                items = mapped
            )
        }

        // 3. Fallback source: SHORT_TERM TargetWithTasks
        val shortTargets = runCatching {
            db.targetDao().getTargetsWithTasksSync(TargetType.SHORT_TERM)
        }.getOrDefault(emptyList())
        if (shortTargets.isNotEmpty()) {
            val mapped = shortTargets.flatMap { twt ->
                val subj = twt.target.category?.takeIf { it.isNotBlank() } ?: twt.target.title
                if (twt.tasks.isEmpty()) {
                    listOf(
                        PlanTaskAuditItem(
                            id = "target_${twt.target.id}",
                            dayOfWeek = todayIsoDay,
                            subject = subj,
                            topic = twt.target.title,
                            scheduledTime = "Esnek • 90 dk",
                            targetQuestions = twt.target.targetQuestions.coerceAtLeast(30),
                            isCompleted = twt.target.isCompleted,
                            isOverdueOrDueToday = !twt.target.isCompleted
                        )
                    )
                } else {
                    twt.tasks.map { task ->
                        PlanTaskAuditItem(
                            id = "task_${task.id}",
                            dayOfWeek = todayIsoDay,
                            subject = subj,
                            topic = task.title,
                            scheduledTime = task.scheduledTime.ifBlank { "Esnek • 90 dk" },
                            targetQuestions = twt.target.targetQuestions.coerceAtLeast(30),
                            isCompleted = task.isCompleted,
                            isOverdueOrDueToday = !task.isCompleted
                        )
                    }
                }
            }
            return@withContext buildSnapshotFromItems(
                folderTitle = "Kısa Vadeli Görevler",
                items = mapped
            )
        }

        PlanAuditSnapshot()
    }

    /**
     * Pure audit builder for Room [PlanEntity] lists (fully unit-testable without Android context).
     */
    fun auditPlanEntities(
        folderTitle: String,
        tasks: List<PlanEntity>,
        todayIsoDay: Int = 7
    ): PlanAuditSnapshot {
        val items = tasks
            .sortedWith(compareBy<PlanEntity> { it.dayOfWeek }.thenBy { it.orderIndex })
            .map { t ->
                val cleanDay = t.dayOfWeek.coerceIn(1, 7)
                PlanTaskAuditItem(
                    id = t.id,
                    dayOfWeek = cleanDay,
                    subject = t.subject.trim().ifBlank { "Ders" },
                    topic = t.topic.trim().ifBlank { t.title.trim() }.ifBlank { t.subject.trim() },
                    scheduledTime = t.scheduledTime.ifBlank { "Esnek • 90 dk" },
                    targetQuestions = if (t.targetQuestions > 0) t.targetQuestions else 30,
                    isCompleted = t.isCompleted,
                    isOverdueOrDueToday = !t.isCompleted && cleanDay <= todayIsoDay
                )
            }
        return buildSnapshotFromItems(folderTitle = folderTitle, items = items)
    }

    private fun buildSnapshotFromItems(
        folderTitle: String,
        items: List<PlanTaskAuditItem>
    ): PlanAuditSnapshot {
        val completed = items.filter { it.isCompleted }
        val incomplete = items
            .filter { !it.isCompleted }
            .sortedWith(compareByDescending<PlanTaskAuditItem> { it.isOverdueOrDueToday }.thenBy { it.dayOfWeek })
        return PlanAuditSnapshot(
            folderTitle = folderTitle.trim(),
            totalTasks = items.size,
            completedTasks = completed,
            incompleteTasks = incomplete,
            capturedAtMillis = System.currentTimeMillis()
        )
    }

    /**
     * Builds the structured Turkish AI context block detailing Completed vs. Incomplete/Carry-Over plan tasks
     * so AI models NEVER assume a previous plan was 100% finished and NEVER repeat already completed tasks.
     */
    fun buildAiPlanAuditPrompt(snapshot: PlanAuditSnapshot): String {
        if (!snapshot.hasAnyTasks) return ""
        val sb = StringBuilder()
        sb.appendLine("[MEVCUT PLAN & GÖREV DENETİMİ — TAMAMLANAN VE EKSİK/DEVREDEN KAZANIMLAR]")
        if (snapshot.folderTitle.isNotBlank()) {
            sb.appendLine("• Aktif Plan: ${snapshot.folderTitle} | Tamamlanma: ${snapshot.completedCount}/${snapshot.totalTasks} görev (%${snapshot.completionPercent})")
        } else {
            sb.appendLine("• Tamamlanma Durumu: ${snapshot.completedCount}/${snapshot.totalTasks} görev (%${snapshot.completionPercent})")
        }

        if (snapshot.incompleteTasks.isNotEmpty()) {
            sb.appendLine("• EKSİK / TAMAMLANMAYAN GÖREVLER (${snapshot.incompleteCount} adet — YENİ PLANA ÖNCELİKLİ DEVREDİLECEK):")
            snapshot.incompleteTasks.take(28).forEach { item ->
                val dayLabel = CoachingEngine.isoDayLabelTr(item.dayOfWeek).take(3)
                val statusTag = if (item.isOverdueOrDueToday) "Eksik/Devreden" else "Bekleyen"
                sb.appendLine("  - [$dayLabel] ${item.subject}: ${item.topic} ($statusTag, ${item.targetQuestions} Soru)")
            }
            if (snapshot.incompleteCount > 28) {
                sb.appendLine("  - (+${snapshot.incompleteCount - 28} ek eksik görev daha)")
            }
        } else {
            sb.appendLine("• EKSİK GÖREV: Yok (Önceki plandaki tüm görevler tamamlanmış).")
        }

        if (snapshot.completedTasks.isNotEmpty()) {
            sb.appendLine("• TAMAMLANAN GÖREVLER (${snapshot.completedCount} adet — TEKRAR TEKRAR YAPTIRMA, ÜZERİNE İNŞA ET):")
            snapshot.completedTasks.take(28).forEach { item ->
                val dayLabel = CoachingEngine.isoDayLabelTr(item.dayOfWeek).take(3)
                sb.appendLine("  - [$dayLabel] ${item.subject}: ${item.topic} (Tamamlandı)")
            }
            if (snapshot.completedCount > 28) {
                sb.appendLine("  - (+${snapshot.completedCount - 28} ek tamamlanan görev)")
            }
        }

        sb.appendLine("• KRİTİK KÜMÜLATİF TEMEL & TEKRARSIZLIK KURALI:")
        sb.appendLine("  1. Öğrenme birikimlidir: EKSİK / TAMAMLANMAYAN görevlerin kazanımları havada kalmamalıdır. Yeni planı kurgularken önceki planın eksiksiz uygulandığını ASLA varsayma; kalan eksik görevleri yeni haftanın ilk günlerine öncelikli devret.")
        sb.appendLine("  2. TAMAMLANAN görevleri öğrenciye tekrar tekrar yazma! Tamamlanan konudan bir sonraki müfredat veya kitap adımına ilerle (yalnızca öğrencinin konu hakimiyeti hâlâ 0-2/5 ise kısa pekiştirme ver).")
        sb.appendLine("  3. Yeni planı öğrencinin anlık konu hakimiyeti, kitap ilerlemesi ve kalan görev yüküne tam uyumlu ve esnek (flexible) tasarla.")
        return sb.toString().trim()
    }

    /**
     * Checks whether a (subject, topic) candidate was already completed in the student's active/previous plan
     * and should NOT be repeated (unless the student's current Topic Mastery for that topic is still weak <= 2).
     */
    fun isTopicAlreadyCompletedInPlan(
        subject: String,
        topic: String,
        completedTasks: List<PlanTaskAuditItem>,
        topicGoals: List<TopicGoalEntity> = emptyList()
    ): Boolean {
        if (completedTasks.isEmpty()) return false
        val normSubj = normalizeTrKey(subject)
        val normTopic = normalizeTrKey(extractCoreTopicName(topic))
        if (normTopic.length < 3) return false

        val matchedInCompleted = completedTasks.any { done ->
            val doneSubj = normalizeTrKey(done.subject)
            val doneTopic = normalizeTrKey(extractCoreTopicName(done.topic))
            val subjCompatible = normSubj.isBlank() || doneSubj.isBlank() ||
                doneSubj.contains(normSubj) || normSubj.contains(doneSubj) ||
                coreSubjectToken(doneSubj) == coreSubjectToken(normSubj)
            val topicMatches = doneTopic == normTopic ||
                (doneTopic.length >= 4 && normTopic.length >= 4 && (doneTopic.contains(normTopic) || normTopic.contains(doneTopic)))
            subjCompatible && topicMatches
        }
        if (!matchedInCompleted) return false

        // If the student explicitly marked this topic's mastery as still weak (0-2/5), allow reinforcement!
        val matchingGoal = topicGoals.firstOrNull { g ->
            !g.isDeleted &&
                normalizeTrKey(extractCoreTopicName(g.topic)).let { gt ->
                    gt == normTopic || (gt.length >= 4 && (gt.contains(normTopic) || normTopic.contains(gt)))
                }
        }
        if (matchingGoal != null && matchingGoal.currentLevel <= 2) {
            return false
        }
        return true
    }

    /**
     * Detects whether a user prompt in Shark AI is asking to create, rebuild, reorganize, or refresh
     * a study plan / weekly schedule so we can display the Pre-Plan Topic Mastery & Book Check-In Card first.
     *
     * Carefully excludes:
     * - Single-question problem solving ("türev sorusunu çöz")
     * - Simple resource/topic progress edits ("3D kitabını 45. sayfaya işaretle", "türev seviyemi 4 yap")
     * - Dossier sync/edit commands ("öğrenci dosyamı güncelle")
     */
    fun isPlanRebuildOrCreateIntent(rawText: String): Boolean {
        val text = rawText.lowercase(Locale("tr")).trim()
        if (text.length < 6) return false
        if (StudentDossierManager.isDossierSyncCommand(text) || StudentDossierManager.isDossierEditCommand(text)) {
            return false
        }

        // Exclude pure book/topic logging & Agent curriculum tracking requests
        // (e.g. "konu takip çizelgesi hazırla", "hakimiyet puanı 0 başlasın", "ders klasörleri oluştur", "kütüphaneye kitap ekle")
        val hasExplicitStudySchedulePhrase = text.contains("haftalık plan") ||
            text.contains("haftalik plan") ||
            text.contains("günlük plan") ||
            text.contains("gunluk plan") ||
            text.contains("çalışma plan") ||
            text.contains("calisma plan") ||
            text.contains("çalışma program") ||
            text.contains("calisma program") ||
            text.contains("haftalık program") ||
            text.contains("haftalik program") ||
            text.contains("ders çalışma program") ||
            text.contains("ders calisma program")

        val isTopicTrackingOrAgentDataRequest = (
            text.contains("konu takip") ||
                text.contains("konu çizelge") ||
                text.contains("konu cizelge") ||
                text.contains("hakimiyet puan") ||
                text.contains("hakimiyet liste") ||
                text.contains("ders klasör") ||
                text.contains("ders klasor") ||
                text.contains("ortak konular") ||
                text.contains("müfredat liste") ||
                text.contains("mufredat liste") ||
                text.contains("kütüphaneye kitap") ||
                text.contains("kutuphaneye kitap") ||
                text.contains("hata bankas")
            ) && !hasExplicitStudySchedulePhrase
        if (isTopicTrackingOrAgentDataRequest) return false

        val isSingleItemEdit = (text.contains("sayfaya kadar") || text.contains("sayfadayım") ||
            text.contains("sayfadayim") || text.contains("teste kadar") ||
            text.contains("testteyim") || text.contains("seviyemi")) &&
            !text.contains("plan") && !text.contains("program")
        if (isSingleItemEdit) return false

        val mentionsPlanNoun = text.contains("plan") ||
            text.contains("program") ||
            text.contains("çalışma çizelge") ||
            text.contains("calisma cizelge") ||
            text.contains("çalışma rotası") ||
            text.contains("calisma rotasi")

        if (!mentionsPlanNoun) return false

        // Exclude pure questions about an existing plan without asking to generate/rebuild it
        val actionVerbs = listOf(
            "oluştur", "olustur",
            "hazırla", "hazirla",
            "yap", "yapar mısın", "yapar misin",
            "yenile", "yeniden",
            "kurgula", "kurgulat",
            "güncelle", "guncelle",
            "düzenle", "duzenle",
            "dengele", "revize",
            "baştan", "bastan",
            "çıkar", "cikar",
            "yaz", "kur", "ekle"
        )
        val hasActionVerb = actionVerbs.any { text.contains(it) }
        val isWeeklyOrStudyPlanPhrase = hasExplicitStudySchedulePhrase ||
            text.contains("yeni plan") ||
            text.contains("yeni program") ||
            text.contains("planımı yenile") ||
            text.contains("planimi yenile") ||
            text.contains("planı yenile") ||
            text.contains("plani yenile") ||
            text.contains("planı kurgula") ||
            text.contains("plani kurgula") ||
            text.contains("programı kurgula") ||
            text.contains("programi kurgula") ||
            text.contains("programımı güncelle") ||
            text.contains("programimi guncelle") ||
            text.contains("planımı güncelle") ||
            text.contains("planimi guncelle")

        // Exclude "ders programı tara" (school timetable scan) unless explicitly asking for çalışma planı
        val isSchoolTimetableOnly = (text.contains("okul ders program") || text.contains("dershane program")) &&
            !text.contains("çalışma") && !text.contains("calisma") && !text.contains("haftalık plan")
        if (isSchoolTimetableOnly) return false

        return hasActionVerb || isWeeklyOrStudyPlanPhrase
    }

    internal fun extractCoreTopicName(rawTopic: String): String {
        val trimmed = rawTopic.trim()
        // Handle "Book Title: Topic Name" or "Subject: Topic Name"
        return if (trimmed.contains(":")) {
            trimmed.substringAfter(":").trim().ifBlank { trimmed }
        } else {
            trimmed
        }
    }

    internal fun normalizeTrKey(raw: String): String {
        val folded = raw
            .replace('İ', 'i')
            .replace('I', 'i')
            .replace('ı', 'i')
            .replace('Ğ', 'g')
            .replace('ğ', 'g')
            .replace('Ü', 'u')
            .replace('ü', 'u')
            .replace('Ş', 's')
            .replace('ş', 's')
            .replace('Ö', 'o')
            .replace('ö', 'o')
            .replace('Ç', 'c')
            .replace('ç', 'c')
            .lowercase(Locale.ROOT)
        return NON_ALNUM_REGEX.replace(folded, " ")
            .let { MULTI_SPACE_REGEX.replace(it, " ") }
            .trim()
    }

    private fun coreSubjectToken(normSubj: String): String {
        return normSubj
            .removePrefix("tyt ")
            .removePrefix("ayt ")
            .removePrefix("lgs ")
            .removePrefix("kpss ")
            .removePrefix("tus ")
            .trim()
    }
}
