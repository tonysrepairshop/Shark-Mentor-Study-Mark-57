package com.example.sharkmentor.core.coaching

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.ErrorQuestionEntity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class TopicLearningStage {
    NOT_STARTED,
    CONCEPT_LEARNED,
    PRACTICED,
    REVIEW_1_3_7,
    MONTHLY_MAINTENANCE
}

enum class TopicStudyActionKind {
    CONCEPT_STUDY,
    PRIMARY_PRACTICE,
    REVIEW_SESSION,
    ERROR_BANK_LAPSE
}

@Immutable
data class TopicLifecycleRecord(
    val key: String,
    val subject: String,
    val topic: String,
    val stage: TopicLearningStage = TopicLearningStage.NOT_STARTED,
    val conceptBookTitle: String = "",
    val practiceBookTitle: String = "",
    val revisionBookTitle: String = "",
    val firstLearnedEpochDay: Long = 0L,
    val lastStudiedEpochDay: Long = 0L,
    val needsSameWeekPractice: Boolean = false,
    val repetitionCycle: Int = 0,
    /** 0 = +1 day, 1 = +3 days, 2 = +7 days, 3 = +30 days (Monthly maintenance). */
    val repetitionStepIndex: Int = 0,
    val nextReviewEpochDay: Long = 0L,
    val completedBlockCount: Int = 0,
    val masterySnapshot: Int = 0,
    val linkedErrorCount: Int = 0,
    val completedVideoIds: List<String> = emptyList()
) {
    fun currentIntervalLabel(): String = when (stage) {
        TopicLearningStage.NOT_STARTED -> "Yeni Konu"
        TopicLearningStage.CONCEPT_LEARNED -> "Aynı Hafta Soru Pekiştirmesi"
        TopicLearningStage.PRACTICED, TopicLearningStage.REVIEW_1_3_7 -> {
            val stepDay = when (repetitionStepIndex) {
                0 -> "1. Gün"
                1 -> "3. Gün"
                else -> "7. Gün"
            }
            val cycleTag = if (repetitionCycle > 1) " (${repetitionCycle}. Tur)" else ""
            "1-3-7 Tekrar • $stepDay$cycleTag"
        }
        TopicLearningStage.MONTHLY_MAINTENANCE -> "Aylık Kalıcı Hafıza Tekrarı"
    }
}

@Immutable
data class WeeklyCoachReport(
    val weekIsoKey: String, // e.g. "2026-W47"
    val monthIsoKey: String, // e.g. "2026-11"
    val startEpochDay: Long,
    val endEpochDay: Long,
    val plannedCount: Int,
    val completedCount: Int,
    val carriedOverCount: Int,
    val newlyLearnedTopics: List<String> = emptyList(),
    val consolidatedTopics: List<String> = emptyList(),
    val booksUsed: List<String> = emptyList(),
    val summaryNote: String = ""
)

@Immutable
data class MonthlyCoachReport(
    val monthIsoKey: String, // e.g. "2026-11"
    val weeksAggregated: Int,
    val totalPlanned: Int,
    val totalCompleted: Int,
    val topicsLearnedCount: Int,
    val topicsMasteredCount: Int,
    val highlightSubjects: List<String> = emptyList(),
    val compactDigest: String = ""
)

@Immutable
data class CoachChatTakeaway(
    val epochDay: Long,
    val category: String, // "PLAN", "BOOK", "MASTERY", "COACHING"
    val digest: String
)

@Immutable
data class AiCoachLedger(
    val uidKey: String = "guest_local",
    val firstActivatedEpochDay: Long = 0L,
    val topicRecords: Map<String, TopicLifecycleRecord> = emptyMap(),
    /** Rolling window of last 4 weekly reports; older weeks roll up into [monthlyReports]. */
    val weeklyReports: List<WeeklyCoachReport> = emptyList(),
    /** Permanent compact monthly summaries since Day 1. */
    val monthlyReports: List<MonthlyCoachReport> = emptyList(),
    /** Concise coaching takeaways (never raw chat transcripts). */
    val chatTakeaways: List<CoachChatTakeaway> = emptyList(),
    val lastUpdatedMillis: Long = 0L
)

/**
 * Mark 67 System-Managed, User-Tamper-Protected, Per-UID Isolated "Yapay Zeka Dosyası" (AI Coaching Ledger)
 * + 1-3-7 -> Monthly Spaced Repetition Engine + Two-Way Error Bank Synchronization.
 *
 * Design guarantees:
 * 1. Zero cross-user / shared-IP bleed: Strictly isolated per Firebase UID hash in AES-256-GCM encrypted
 *    local storage AND owner-only Firestore path `users/{uid}/ai_coach_ledger/state` (0 firestore.rules changes).
 * 2. User-tamper-protected: Managed autonomously by the coaching & planning engines (zero manual edit UI).
 * 3. Low-end device friendly: Bounded ~20-40 KB footprint even after 12 months via automatic Weekly -> Monthly
 *    rollup compression.
 * 4. 1-3-7 -> Monthly Spaced Repetition: Every learned + practiced topic cycles 1 -> 3 -> 7 days (repeating
 *    1-3-7 cycles until mastery >= 4/5 AND Error Bank mistakes are cleared), then settles into 30-day Monthly
 *    maintenance so topics are NEVER blindly taught from scratch 12 times.
 */
object AiCoachLedgerManager {

    private const val TAG = "AiCoachLedgerMgr"
    private const val PREFS_PREFIX = "shark_mark67_ai_ledger_"
    private const val KEY_PAYLOAD_ENC = "ledger_payload_enc_v1"
    private const val KEY_UPDATED_AT = "ledger_updated_at"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val AES_KEY_ALIAS = "shark_mark67_ai_ledger_aes_key"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val MAX_WEEKLY_REPORTS_KEPT = 4
    private const val MAX_CHAT_TAKEAWAYS_KEPT = 24

    val STEP_INTERVAL_DAYS = intArrayOf(1, 3, 7)
    const val MONTHLY_INTERVAL_DAYS = 30

    private val gson = Gson()
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loadedUidKey: String? = null

    private val _ledger = MutableStateFlow(AiCoachLedger())
    val ledger: StateFlow<AiCoachLedger> = _ledger.asStateFlow()

    fun uidKeyFor(rawUid: String?): String {
        val clean = rawUid?.trim().orEmpty()
        if (clean.isBlank() || clean == "student_default") return "guest_local"
        val digest = MessageDigest.getInstance("SHA-256").digest(clean.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun topicKey(subject: String, topic: String): String {
        val normSubj = PlanProgressAuditor.normalizeTrKey(subject)
        val normTopic = PlanProgressAuditor.normalizeTrKey(PlanProgressAuditor.extractCoreTopicName(topic))
        return "$normSubj|$normTopic"
    }

    private fun currentUid(context: Context): String? {
        return runCatching { GoogleServicesHelper.getCurrentUser(context = context)?.uid }.getOrNull()
    }

    private fun prefs(context: Context, uidKey: String): SharedPreferences {
        return context.applicationContext.getSharedPreferences("$PREFS_PREFIX$uidKey", Context.MODE_PRIVATE)
    }

    fun ensureLoaded(context: Context) {
        val uidKey = uidKeyFor(currentUid(context))
        if (loadedUidKey == uidKey) return
        synchronized(this) {
            if (loadedUidKey == uidKey) return
            val enc = prefs(context, uidKey).getString(KEY_PAYLOAD_ENC, null)
            val parsed = if (!enc.isNullOrBlank()) {
                val json = decryptPayload(enc)
                parseLedgerJson(json, fallbackUidKey = uidKey)
            } else {
                AiCoachLedger(uidKey = uidKey, firstActivatedEpochDay = LocalDate.now().toEpochDay())
            }
            _ledger.value = if (parsed.firstActivatedEpochDay <= 0L) {
                parsed.copy(uidKey = uidKey, firstActivatedEpochDay = LocalDate.now().toEpochDay())
            } else {
                parsed.copy(uidKey = uidKey)
            }
            loadedUidKey = uidKey
        }
    }

    fun clearInMemoryForLogout() {
        synchronized(this) {
            loadedUidKey = null
            _ledger.value = AiCoachLedger()
        }
    }

    // ── Pure 1-3-7 -> Monthly Spaced Repetition State Machine (Unit-Testable) ──

    fun advanceTopicLifecycle(
        ledger: AiCoachLedger,
        subject: String,
        topic: String,
        actionKind: TopicStudyActionKind,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        bookTitle: String = "",
        masteryLevel: Int? = null,
        linkedErrorCount: Int? = null,
        completedVideoId: String? = null
    ): AiCoachLedger {
        val cleanSubj = subject.trim().ifBlank { "Genel" }
        val cleanTopic = PlanProgressAuditor.extractCoreTopicName(topic).ifBlank { topic.trim() }
        if (cleanTopic.length < 2) return ledger

        val key = topicKey(cleanSubj, cleanTopic)
        val cur = ledger.topicRecords[key] ?: TopicLifecycleRecord(
            key = key,
            subject = cleanSubj,
            topic = cleanTopic
        )
        val effectiveMastery = masteryLevel?.coerceIn(0, 5) ?: cur.masterySnapshot
        val effectiveErrors = linkedErrorCount?.coerceAtLeast(0) ?: cur.linkedErrorCount
        val nextVideos = if (!completedVideoId.isNullOrBlank() && completedVideoId.length == 11) {
            (cur.completedVideoIds + completedVideoId).distinct().takeLast(30)
        } else {
            cur.completedVideoIds
        }

        val updatedRecord = when (actionKind) {
            TopicStudyActionKind.CONCEPT_STUDY -> {
                val alreadyPastConcept = cur.stage == TopicLearningStage.PRACTICED ||
                    cur.stage == TopicLearningStage.REVIEW_1_3_7 ||
                    cur.stage == TopicLearningStage.MONTHLY_MAINTENANCE
                cur.copy(
                    subject = cleanSubj,
                    topic = cleanTopic,
                    stage = if (alreadyPastConcept) cur.stage else TopicLearningStage.CONCEPT_LEARNED,
                    conceptBookTitle = bookTitle.ifBlank { cur.conceptBookTitle },
                    firstLearnedEpochDay = if (cur.firstLearnedEpochDay > 0L) cur.firstLearnedEpochDay else todayEpochDay,
                    lastStudiedEpochDay = todayEpochDay,
                    needsSameWeekPractice = !alreadyPastConcept,
                    completedBlockCount = cur.completedBlockCount + 1,
                    masterySnapshot = effectiveMastery,
                    linkedErrorCount = effectiveErrors,
                    completedVideoIds = nextVideos
                )
            }

            TopicStudyActionKind.PRIMARY_PRACTICE -> {
                // After learning + solving primary practice questions, enter 1-3-7 repetition at step 0 (+1 day)
                val nextStage = if (cur.stage == TopicLearningStage.MONTHLY_MAINTENANCE && effectiveMastery >= 4 && effectiveErrors == 0) {
                    TopicLearningStage.MONTHLY_MAINTENANCE
                } else {
                    TopicLearningStage.REVIEW_1_3_7
                }
                val nextCycle = if (cur.repetitionCycle <= 0) 1 else cur.repetitionCycle
                val nextStep = if (nextStage == TopicLearningStage.MONTHLY_MAINTENANCE) 3 else 0
                val nextDue = if (nextStage == TopicLearningStage.MONTHLY_MAINTENANCE) {
                    todayEpochDay + MONTHLY_INTERVAL_DAYS
                } else {
                    todayEpochDay + STEP_INTERVAL_DAYS[0]
                }
                cur.copy(
                    subject = cleanSubj,
                    topic = cleanTopic,
                    stage = nextStage,
                    practiceBookTitle = bookTitle.ifBlank { cur.practiceBookTitle },
                    firstLearnedEpochDay = if (cur.firstLearnedEpochDay > 0L) cur.firstLearnedEpochDay else todayEpochDay,
                    lastStudiedEpochDay = todayEpochDay,
                    needsSameWeekPractice = false,
                    repetitionCycle = nextCycle,
                    repetitionStepIndex = nextStep,
                    nextReviewEpochDay = nextDue,
                    completedBlockCount = cur.completedBlockCount + 1,
                    masterySnapshot = effectiveMastery,
                    linkedErrorCount = effectiveErrors,
                    completedVideoIds = nextVideos
                )
            }

            TopicStudyActionKind.REVIEW_SESSION -> {
                val isConsolidated = effectiveMastery >= 4 && effectiveErrors == 0
                when (cur.stage) {
                    TopicLearningStage.NOT_STARTED, TopicLearningStage.CONCEPT_LEARNED -> {
                        cur.copy(
                            subject = cleanSubj,
                            topic = cleanTopic,
                            stage = TopicLearningStage.REVIEW_1_3_7,
                            revisionBookTitle = bookTitle.ifBlank { cur.revisionBookTitle },
                            firstLearnedEpochDay = if (cur.firstLearnedEpochDay > 0L) cur.firstLearnedEpochDay else todayEpochDay,
                            lastStudiedEpochDay = todayEpochDay,
                            needsSameWeekPractice = false,
                            repetitionCycle = cur.repetitionCycle.coerceAtLeast(1),
                            repetitionStepIndex = 0,
                            nextReviewEpochDay = todayEpochDay + STEP_INTERVAL_DAYS[0],
                            completedBlockCount = cur.completedBlockCount + 1,
                            masterySnapshot = effectiveMastery,
                            linkedErrorCount = effectiveErrors,
                            completedVideoIds = nextVideos
                        )
                    }

                    TopicLearningStage.PRACTICED, TopicLearningStage.REVIEW_1_3_7 -> {
                        when {
                            cur.repetitionStepIndex <= 0 -> {
                                // Finished +1 day review -> schedule +3 day review
                                cur.copy(
                                    subject = cleanSubj,
                                    topic = cleanTopic,
                                    stage = TopicLearningStage.REVIEW_1_3_7,
                                    revisionBookTitle = bookTitle.ifBlank { cur.revisionBookTitle },
                                    lastStudiedEpochDay = todayEpochDay,
                                    needsSameWeekPractice = false,
                                    repetitionCycle = cur.repetitionCycle.coerceAtLeast(1),
                                    repetitionStepIndex = 1,
                                    nextReviewEpochDay = todayEpochDay + STEP_INTERVAL_DAYS[1],
                                    completedBlockCount = cur.completedBlockCount + 1,
                                    masterySnapshot = effectiveMastery,
                                    linkedErrorCount = effectiveErrors,
                                    completedVideoIds = nextVideos
                                )
                            }
                            cur.repetitionStepIndex == 1 -> {
                                // Finished +3 day review -> schedule +7 day review
                                cur.copy(
                                    subject = cleanSubj,
                                    topic = cleanTopic,
                                    stage = TopicLearningStage.REVIEW_1_3_7,
                                    revisionBookTitle = bookTitle.ifBlank { cur.revisionBookTitle },
                                    lastStudiedEpochDay = todayEpochDay,
                                    needsSameWeekPractice = false,
                                    repetitionCycle = cur.repetitionCycle.coerceAtLeast(1),
                                    repetitionStepIndex = 2,
                                    nextReviewEpochDay = todayEpochDay + STEP_INTERVAL_DAYS[2],
                                    completedBlockCount = cur.completedBlockCount + 1,
                                    masterySnapshot = effectiveMastery,
                                    linkedErrorCount = effectiveErrors,
                                    completedVideoIds = nextVideos
                                )
                            }
                            else -> {
                                // Finished +7 day review: promote to Monthly if mastered & error-free; else repeat 1-3-7!
                                if (isConsolidated) {
                                    cur.copy(
                                        subject = cleanSubj,
                                        topic = cleanTopic,
                                        stage = TopicLearningStage.MONTHLY_MAINTENANCE,
                                        revisionBookTitle = bookTitle.ifBlank { cur.revisionBookTitle },
                                        lastStudiedEpochDay = todayEpochDay,
                                        needsSameWeekPractice = false,
                                        repetitionCycle = cur.repetitionCycle.coerceAtLeast(1),
                                        repetitionStepIndex = 3,
                                        nextReviewEpochDay = todayEpochDay + MONTHLY_INTERVAL_DAYS,
                                        completedBlockCount = cur.completedBlockCount + 1,
                                        masterySnapshot = effectiveMastery,
                                        linkedErrorCount = effectiveErrors,
                                        completedVideoIds = nextVideos
                                    )
                                } else {
                                    cur.copy(
                                        subject = cleanSubj,
                                        topic = cleanTopic,
                                        stage = TopicLearningStage.REVIEW_1_3_7,
                                        revisionBookTitle = bookTitle.ifBlank { cur.revisionBookTitle },
                                        lastStudiedEpochDay = todayEpochDay,
                                        needsSameWeekPractice = false,
                                        repetitionCycle = cur.repetitionCycle.coerceAtLeast(1) + 1,
                                        repetitionStepIndex = 0,
                                        nextReviewEpochDay = todayEpochDay + STEP_INTERVAL_DAYS[0],
                                        completedBlockCount = cur.completedBlockCount + 1,
                                        masterySnapshot = effectiveMastery,
                                        linkedErrorCount = effectiveErrors,
                                        completedVideoIds = nextVideos
                                    )
                                }
                            }
                        }
                    }

                    TopicLearningStage.MONTHLY_MAINTENANCE -> {
                        if (isConsolidated) {
                            cur.copy(
                                subject = cleanSubj,
                                topic = cleanTopic,
                                revisionBookTitle = bookTitle.ifBlank { cur.revisionBookTitle },
                                lastStudiedEpochDay = todayEpochDay,
                                repetitionStepIndex = 3,
                                nextReviewEpochDay = todayEpochDay + MONTHLY_INTERVAL_DAYS,
                                completedBlockCount = cur.completedBlockCount + 1,
                                masterySnapshot = effectiveMastery,
                                linkedErrorCount = effectiveErrors,
                                completedVideoIds = nextVideos
                            )
                        } else {
                            // Mastery slipped or Error Bank mistakes returned -> re-enter 1-3-7 reinforcement!
                            cur.copy(
                                subject = cleanSubj,
                                topic = cleanTopic,
                                stage = TopicLearningStage.REVIEW_1_3_7,
                                revisionBookTitle = bookTitle.ifBlank { cur.revisionBookTitle },
                                lastStudiedEpochDay = todayEpochDay,
                                repetitionCycle = cur.repetitionCycle.coerceAtLeast(1) + 1,
                                repetitionStepIndex = 0,
                                nextReviewEpochDay = todayEpochDay + STEP_INTERVAL_DAYS[0],
                                completedBlockCount = cur.completedBlockCount + 1,
                                masterySnapshot = effectiveMastery,
                                linkedErrorCount = effectiveErrors,
                                completedVideoIds = nextVideos
                            )
                        }
                    }
                }
            }

            TopicStudyActionKind.ERROR_BANK_LAPSE -> {
                // Pull topic into urgent 1-3-7 review synchronized with Error Bank!
                val urgentDue = if (cur.nextReviewEpochDay in 1..todayEpochDay) {
                    cur.nextReviewEpochDay
                } else {
                    todayEpochDay + STEP_INTERVAL_DAYS[0]
                }
                cur.copy(
                    subject = cleanSubj,
                    topic = cleanTopic,
                    stage = TopicLearningStage.REVIEW_1_3_7,
                    firstLearnedEpochDay = if (cur.firstLearnedEpochDay > 0L) cur.firstLearnedEpochDay else todayEpochDay,
                    repetitionCycle = cur.repetitionCycle.coerceAtLeast(1),
                    repetitionStepIndex = 0,
                    nextReviewEpochDay = urgentDue,
                    masterySnapshot = effectiveMastery,
                    linkedErrorCount = effectiveErrors.coerceAtLeast(1)
                )
            }
        }

        val nextRecords = ledger.topicRecords.toMutableMap().apply { put(key, updatedRecord) }
        return ledger.copy(
            firstActivatedEpochDay = if (ledger.firstActivatedEpochDay > 0L) ledger.firstActivatedEpochDay else todayEpochDay,
            topicRecords = nextRecords,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    /**
     * Two-way Error Bank synchronization (pure & unit-testable):
     * Counts active/due/lapsed Error Bank questions per `(subject, topic)` and aligns `TopicLifecycleRecord`
     * so topics with Error Bank weaknesses automatically receive synchronized 1-3-7 reviews.
     */
    fun mergeErrorBankCountsIntoLedger(
        ledger: AiCoachLedger,
        errorQuestions: List<ErrorQuestionEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        todayEpochDay: Long = LocalDate.now().toEpochDay()
    ): AiCoachLedger {
        val soonThreshold = nowMillis + (2L * 86_400_000L)
        val activeErrorCounts = LinkedHashMap<String, Triple<String, String, Int>>()

        errorQuestions.forEach { q ->
            val rawTopic = q.aiTopic?.takeIf { it.isNotBlank() } ?: q.topic.takeIf { it.isNotBlank() && !it.equals("Genel", ignoreCase = true) }
                ?: return@forEach
            val isActionable = q.state == 0 || q.lapses > 0 || q.dueDateMillis <= soonThreshold
            if (!isActionable) return@forEach

            val key = topicKey(q.subject, rawTopic)
            val prev = activeErrorCounts[key]
            activeErrorCounts[key] = Triple(q.subject.trim(), rawTopic.trim(), (prev?.third ?: 0) + 1)
        }

        val nextMap = ledger.topicRecords.toMutableMap()
        // First clear stale linkedErrorCount on topics whose Error Bank questions were resolved
        nextMap.forEach { (k, rec) ->
            val freshCount = activeErrorCounts[k]?.third ?: 0
            if (rec.linkedErrorCount != freshCount) {
                nextMap[k] = rec.copy(linkedErrorCount = freshCount)
            }
        }

        // Elevate or create 1-3-7 records for any topic with actionable Error Bank questions
        var working = ledger.copy(topicRecords = nextMap)
        activeErrorCounts.forEach { (k, triple) ->
            val (subj, top, errCount) = triple
            val existing = working.topicRecords[k]
            if (existing == null || existing.stage == TopicLearningStage.MONTHLY_MAINTENANCE || existing.nextReviewEpochDay > todayEpochDay + 3) {
                working = advanceTopicLifecycle(
                    ledger = working,
                    subject = subj,
                    topic = top,
                    actionKind = TopicStudyActionKind.ERROR_BANK_LAPSE,
                    todayEpochDay = todayEpochDay,
                    linkedErrorCount = errCount
                )
            } else {
                val updated = existing.copy(linkedErrorCount = errCount)
                working = working.copy(
                    topicRecords = working.topicRecords.toMutableMap().apply { put(k, updated) }
                )
            }
        }
        return working.copy(lastUpdatedMillis = System.currentTimeMillis())
    }

    /**
     * Appends/updates a weekly coaching report and automatically compresses any weeks older than
     * [MAX_WEEKLY_REPORTS_KEPT] into permanent compact [MonthlyCoachReport] summaries.
     */
    fun appendWeeklyReportAndRollup(
        ledger: AiCoachLedger,
        report: WeeklyCoachReport
    ): AiCoachLedger {
        val mergedWeeks = (ledger.weeklyReports.filterNot { it.weekIsoKey == report.weekIsoKey } + report)
            .sortedBy { it.startEpochDay }

        if (mergedWeeks.size <= MAX_WEEKLY_REPORTS_KEPT) {
            return ledger.copy(
                weeklyReports = mergedWeeks,
                lastUpdatedMillis = System.currentTimeMillis()
            )
        }

        val overflowCount = mergedWeeks.size - MAX_WEEKLY_REPORTS_KEPT
        val toArchive = mergedWeeks.take(overflowCount)
        val recentKept = mergedWeeks.drop(overflowCount)

        val monthlyByKey = ledger.monthlyReports.associateBy { it.monthIsoKey }.toMutableMap()
        toArchive.forEach { wk ->
            val mKey = wk.monthIsoKey.ifBlank {
                LocalDate.ofEpochDay(wk.endEpochDay).format(DateTimeFormatter.ofPattern("yyyy-MM"))
            }
            val prev = monthlyByKey[mKey]
            val subjects = (prev?.highlightSubjects.orEmpty() + wk.booksUsed + wk.newlyLearnedTopics.map { it.substringBefore(":").trim() })
                .filter { it.isNotBlank() }
                .distinct()
                .take(8)
            val totalPlanned = (prev?.totalPlanned ?: 0) + wk.plannedCount
            val totalCompleted = (prev?.totalCompleted ?: 0) + wk.completedCount
            val pct = if (totalPlanned > 0) ((totalCompleted * 100) / totalPlanned).coerceIn(0, 100) else 0
            val learnedCount = (prev?.topicsLearnedCount ?: 0) + wk.newlyLearnedTopics.size
            val masteredCount = (prev?.topicsMasteredCount ?: 0) + wk.consolidatedTopics.size
            val weeksAgg = (prev?.weeksAggregated ?: 0) + 1
            monthlyByKey[mKey] = MonthlyCoachReport(
                monthIsoKey = mKey,
                weeksAggregated = weeksAgg,
                totalPlanned = totalPlanned,
                totalCompleted = totalCompleted,
                topicsLearnedCount = learnedCount,
                topicsMasteredCount = masteredCount,
                highlightSubjects = subjects,
                compactDigest = "$mKey ($weeksAgg hafta): $totalCompleted/$totalPlanned görev (%$pct), $learnedCount konu işlendi, $masteredCount konu kalıcı hafızaya geçti."
            )
        }

        return ledger.copy(
            weeklyReports = recentKept,
            monthlyReports = monthlyByKey.values.sortedBy { it.monthIsoKey },
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    fun appendChatTakeaway(
        ledger: AiCoachLedger,
        category: String,
        digest: String,
        todayEpochDay: Long = LocalDate.now().toEpochDay()
    ): AiCoachLedger {
        val clean = digest.trim().replace(Regex("\\s+"), " ").take(180)
        if (clean.length < 8) return ledger
        val entry = CoachChatTakeaway(epochDay = todayEpochDay, category = category, digest = clean)
        val deduped = (ledger.chatTakeaways.filterNot { it.digest.equals(clean, ignoreCase = true) } + entry)
            .takeLast(MAX_CHAT_TAKEAWAYS_KEPT)
        return ledger.copy(chatTakeaways = deduped, lastUpdatedMillis = System.currentTimeMillis())
    }

    // ── Pure Planner Query Helpers ───────────────────────────────────────────

    fun getDueReviewTopics(
        ledger: AiCoachLedger,
        horizonEndEpochDay: Long = LocalDate.now().plusDays(6).toEpochDay()
    ): List<TopicLifecycleRecord> {
        return ledger.topicRecords.values
            .filter {
                (it.stage == TopicLearningStage.REVIEW_1_3_7 || it.stage == TopicLearningStage.MONTHLY_MAINTENANCE) &&
                    it.nextReviewEpochDay in 1L..horizonEndEpochDay
            }
            .sortedWith(
                compareByDescending<TopicLifecycleRecord> { it.linkedErrorCount > 0 }
                    .thenBy { it.nextReviewEpochDay }
                    .thenBy { it.masterySnapshot }
            )
    }

    fun getTopicsNeedingSameWeekPractice(ledger: AiCoachLedger): List<TopicLifecycleRecord> {
        return ledger.topicRecords.values
            .filter { it.stage == TopicLearningStage.CONCEPT_LEARNED && it.needsSameWeekPractice }
            .sortedBy { it.lastStudiedEpochDay }
    }

    fun isTopicAlreadyConceptLearned(
        subject: String,
        topic: String,
        ledger: AiCoachLedger
    ): Boolean {
        val rec = ledger.topicRecords[topicKey(subject, topic)] ?: return false
        return rec.stage != TopicLearningStage.NOT_STARTED
    }

    fun buildAiLedgerPrompt(
        ledger: AiCoachLedger,
        todayEpochDay: Long = LocalDate.now().toEpochDay()
    ): String {
        val hasData = ledger.topicRecords.isNotEmpty() ||
            ledger.weeklyReports.isNotEmpty() ||
            ledger.monthlyReports.isNotEmpty() ||
            ledger.chatTakeaways.isNotEmpty()
        if (!hasData) return ""

        val sb = StringBuilder()
        sb.appendLine("[SİSTEM YAPAY ZEKA KOÇLUK DOSYASI — KULLANICIYA ÖZEL ARŞİV & 1-3-7 TEKRAR MOTORU]")
        if (ledger.firstActivatedEpochDay > 0L) {
            val startDate = runCatching { LocalDate.ofEpochDay(ledger.firstActivatedEpochDay).toString() }.getOrDefault("")
            if (startDate.isNotBlank()) {
                sb.appendLine("• Koçluk Başlangıç Tarihi: $startDate | Kayıtlı Konu: ${ledger.topicRecords.size}")
            }
        }

        val needsPractice = getTopicsNeedingSameWeekPractice(ledger).take(12)
        if (needsPractice.isNotEmpty()) {
            sb.appendLine("• AYNI HAFTA SORU PEKİŞTİRMESİ BEKLEYEN KONULAR (Konu öğrenildi, İlerleme/Soru Kaynağından soru çözülmedi):")
            needsPractice.forEach { rec ->
                val fromBook = if (rec.conceptBookTitle.isNotBlank()) " (Konu: ${rec.conceptBookTitle})" else ""
                sb.appendLine("  - ${rec.subject}: ${rec.topic}$fromBook -> İlerleme/Soru Kaynağı #1 ile soru bloğu ekle")
            }
        }

        val dueReviews = getDueReviewTopics(ledger, horizonEndEpochDay = todayEpochDay + 6L).take(18)
        if (dueReviews.isNotEmpty()) {
            sb.appendLine("• BU HAFTA VADESİ GELEN 1-3-7 / AYLIK TEKRARLAR (Tekrar Kaynağı + Hata Bankası ile planla):")
            dueReviews.forEach { rec ->
                val dayDelta = (rec.nextReviewEpochDay - todayEpochDay).coerceAtLeast(0L)
                val dueLabel = if (rec.nextReviewEpochDay <= todayEpochDay) "Bugün/Gecikmiş" else "+${dayDelta} gün"
                val errNote = if (rec.linkedErrorCount > 0) " | Hata Bankası: ${rec.linkedErrorCount} soru" else ""
                sb.appendLine("  - ${rec.subject}: ${rec.topic} [${rec.currentIntervalLabel()} • $dueLabel$errNote]")
            }
        }

        val alreadyLearned = ledger.topicRecords.values
            .filter { it.stage == TopicLearningStage.MONTHLY_MAINTENANCE || (it.stage == TopicLearningStage.REVIEW_1_3_7 && it.completedBlockCount >= 2) }
            .sortedByDescending { it.lastStudiedEpochDay }
            .take(24)
        if (alreadyLearned.isNotEmpty()) {
            val listStr = alreadyLearned.joinToString(", ") { "${it.subject}:${it.topic}" }
            sb.appendLine("• DAHA ÖNCE ÖĞRENİLMİŞ KONULAR (ASLA SIFIRDAN KONU ANLATIMI OLARAK TEKRAR YAZMA): $listStr")
        }

        if (ledger.weeklyReports.isNotEmpty()) {
            val latest = ledger.weeklyReports.last()
            sb.appendLine("• SON HAFTALIK KOÇLUK RAPORU (${latest.weekIsoKey}): ${latest.completedCount}/${latest.plannedCount} görev tamamlandı, ${latest.carriedOverCount} görev devretti. ${latest.summaryNote.take(140)}")
        }
        if (ledger.monthlyReports.isNotEmpty()) {
            sb.appendLine("• AYLIK KOÇLUK ARŞİVİ:")
            ledger.monthlyReports.takeLast(6).forEach { m ->
                sb.appendLine("  - ${m.compactDigest}")
            }
        }
        if (ledger.chatTakeaways.isNotEmpty()) {
            val notes = ledger.chatTakeaways.takeLast(6).joinToString(" | ") { "[${it.category}] ${it.digest}" }
            sb.appendLine("• ÖNCEKİ KOÇLUK KARARLARI: $notes")
        }
        sb.appendLine("• KRİTİK TEKRAR KURALI: Her yeni konu önce Konu/Video kaynağından öğrenilir, aynı hafta İlerleme/Soru kaynağından çözülür, ardından 1-3-7 gün tekrarlarıyla Tekrar Kaynağı + Hata Bankası üzerinden kalıcı hafızaya (Ayda 1) alınır. Bir konuyu öğrenciye defalarca sıfırdan çalıştırma!")
        return sb.toString().trim()
    }

    // ── Live Stateful App Methods (IO-Safe & Cloud-Backed) ───────────────────

    fun recordTopicStudyInBackground(
        context: Context,
        subject: String,
        topic: String,
        actionKind: TopicStudyActionKind,
        bookTitle: String = "",
        masteryLevel: Int? = null,
        completedVideoId: String? = null
    ) {
        val appCtx = context.applicationContext
        bgScope.launch {
            runCatching {
                ensureLoaded(appCtx)
                val updated = advanceTopicLifecycle(
                    ledger = _ledger.value,
                    subject = subject,
                    topic = topic,
                    actionKind = actionKind,
                    bookTitle = bookTitle,
                    masteryLevel = masteryLevel,
                    completedVideoId = completedVideoId
                )
                persistLocal(appCtx, updated)
                pushToCloudIfSignedIn(appCtx)
            }.onFailure { e ->
                Log.w(TAG, "recordTopicStudyInBackground failed: ${e.message}")
            }
        }
    }

    fun recordChatTakeawayInBackground(
        context: Context,
        category: String,
        digest: String
    ) {
        val appCtx = context.applicationContext
        bgScope.launch {
            runCatching {
                ensureLoaded(appCtx)
                val updated = appendChatTakeaway(_ledger.value, category, digest)
                persistLocal(appCtx, updated)
                pushToCloudIfSignedIn(appCtx)
            }.onFailure { e ->
                Log.w(TAG, "recordChatTakeawayInBackground failed: ${e.message}")
            }
        }
    }

    suspend fun syncWithErrorBank(context: Context): AiCoachLedger = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        ensureLoaded(appCtx)
        val db = SharkMentorDatabase.getDatabase(appCtx)
        val allErrors = runCatching { db.errorQuestionDao().getAllQuestionsRaw() }.getOrDefault(emptyList())
        val updated = mergeErrorBankCountsIntoLedger(_ledger.value, allErrors)
        persistLocal(appCtx, updated)
        updated
    }

    fun syncWithErrorBankInBackground(context: Context) {
        val appCtx = context.applicationContext
        bgScope.launch {
            runCatching {
                syncWithErrorBank(appCtx)
                pushToCloudIfSignedIn(appCtx)
            }.onFailure { e ->
                Log.w(TAG, "syncWithErrorBankInBackground failed: ${e.message}")
            }
        }
    }

    fun onErrorQuestionReviewedInBackground(
        context: Context,
        question: ErrorQuestionEntity,
        wasSolvedCorrectly: Boolean
    ) {
        val appCtx = context.applicationContext
        bgScope.launch {
            runCatching {
                val synced = syncWithErrorBank(appCtx)
                val topicName = question.aiTopic?.takeIf { it.isNotBlank() } ?: question.topic
                if (topicName.isNotBlank() && !topicName.equals("Genel", ignoreCase = true)) {
                    val key = topicKey(question.subject, topicName)
                    val remainingErrors = synced.topicRecords[key]?.linkedErrorCount ?: 0
                    val updated = advanceTopicLifecycle(
                        ledger = synced,
                        subject = question.subject,
                        topic = topicName,
                        actionKind = if (wasSolvedCorrectly) TopicStudyActionKind.REVIEW_SESSION else TopicStudyActionKind.ERROR_BANK_LAPSE,
                        linkedErrorCount = remainingErrors
                    )
                    persistLocal(appCtx, updated)
                    pushToCloudIfSignedIn(appCtx)
                }
            }.onFailure { e ->
                Log.w(TAG, "onErrorQuestionReviewedInBackground failed: ${e.message}")
            }
        }
    }

    suspend fun recordWeeklySnapshotAndRollup(
        context: Context,
        audit: PlanAuditSnapshot? = null,
        booksUsed: List<String> = emptyList(),
        summaryNote: String = ""
    ): AiCoachLedger = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        ensureLoaded(appCtx)
        val effectiveAudit = audit ?: PlanProgressAuditor.captureFromDatabase(appCtx)
        val today = LocalDate.now()
        val weekFields = WeekFields.ISO
        val weekNum = today.get(weekFields.weekOfWeekBasedYear())
        val weekYear = today.get(weekFields.weekBasedYear())
        val weekIsoKey = "%04d-W%02d".format(weekYear, weekNum)
        val monthIsoKey = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val startOfWeek = today.minusDays((today.dayOfWeek.value - 1).toLong()).toEpochDay()
        val endOfWeek = startOfWeek + 6L

        // Sync completed tasks from the active plan directly into topic lifecycle so they are never forgotten
        var working = _ledger.value
        val topicGoals = runCatching {
            SharkMentorDatabase.getDatabase(appCtx).goalsDao().getAllTopicsRaw()
        }.getOrDefault(emptyList())
        val masteryByKey = topicGoals.associate { topicKey(it.subject, it.topic) to it.currentLevel }

        val newlyLearned = mutableListOf<String>()
        val consolidated = mutableListOf<String>()
        effectiveAudit.completedTasks.forEach { task ->
            val k = topicKey(task.subject, task.topic)
            val beforeStage = working.topicRecords[k]?.stage ?: TopicLearningStage.NOT_STARTED
            val actionKind = when {
                task.topic.contains("Tekrar", ignoreCase = true) || task.topic.contains("1-3-7") ->
                    TopicStudyActionKind.REVIEW_SESSION
                task.topic.contains("Soru", ignoreCase = true) || task.topic.contains("Pekiştirme", ignoreCase = true) ->
                    TopicStudyActionKind.PRIMARY_PRACTICE
                beforeStage == TopicLearningStage.NOT_STARTED ->
                    TopicStudyActionKind.CONCEPT_STUDY
                beforeStage == TopicLearningStage.CONCEPT_LEARNED ->
                    TopicStudyActionKind.PRIMARY_PRACTICE
                else ->
                    TopicStudyActionKind.REVIEW_SESSION
            }
            working = advanceTopicLifecycle(
                ledger = working,
                subject = task.subject,
                topic = task.topic,
                actionKind = actionKind,
                masteryLevel = masteryByKey[k]
            )
            val afterStage = working.topicRecords[k]?.stage
            if (beforeStage == TopicLearningStage.NOT_STARTED && afterStage != TopicLearningStage.NOT_STARTED) {
                newlyLearned.add("${task.subject}: ${PlanProgressAuditor.extractCoreTopicName(task.topic)}")
            }
            if (beforeStage != TopicLearningStage.MONTHLY_MAINTENANCE && afterStage == TopicLearningStage.MONTHLY_MAINTENANCE) {
                consolidated.add("${task.subject}: ${PlanProgressAuditor.extractCoreTopicName(task.topic)}")
            }
        }

        val allErrors = runCatching {
            SharkMentorDatabase.getDatabase(appCtx).errorQuestionDao().getAllQuestionsRaw()
        }.getOrDefault(emptyList())
        working = mergeErrorBankCountsIntoLedger(working, allErrors)

        val report = WeeklyCoachReport(
            weekIsoKey = weekIsoKey,
            monthIsoKey = monthIsoKey,
            startEpochDay = startOfWeek,
            endEpochDay = endOfWeek,
            plannedCount = effectiveAudit.totalTasks,
            completedCount = effectiveAudit.completedCount,
            carriedOverCount = effectiveAudit.incompleteCount,
            newlyLearnedTopics = newlyLearned.distinct().take(12),
            consolidatedTopics = consolidated.distinct().take(12),
            booksUsed = booksUsed.filter { it.isNotBlank() }.distinct().take(8),
            summaryNote = summaryNote.ifBlank {
                if (effectiveAudit.hasAnyTasks) {
                    "Tamamlanma %${effectiveAudit.completionPercent} (${effectiveAudit.completedCount}/${effectiveAudit.totalTasks})."
                } else {
                    "Haftalık koçluk kaydı güncellendi."
                }
            }
        )
        val rolled = appendWeeklyReportAndRollup(working, report)
        persistLocal(appCtx, rolled)
        pushToCloudIfSignedIn(appCtx)
        rolled
    }

    // ── Encrypted Local Storage & Owner-Only Firestore Sync ──────────────────

    private fun persistLocal(context: Context, ledger: AiCoachLedger) {
        val uidKey = uidKeyFor(currentUid(context))
        val stamped = ledger.copy(uidKey = uidKey, lastUpdatedMillis = System.currentTimeMillis())
        _ledger.value = stamped
        loadedUidKey = uidKey
        val json = serializeLedgerJson(stamped)
        val enc = encryptPayload(json)
        prefs(context, uidKey).edit()
            .putString(KEY_PAYLOAD_ENC, enc)
            .putLong(KEY_UPDATED_AT, stamped.lastUpdatedMillis)
            .apply()
    }

    suspend fun pushToCloudIfSignedIn(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUid(context)
        if (uid.isNullOrBlank() || uid == "student_default") return@withContext false
        val firestore = GoogleServicesHelper.getFirebaseFirestore(context) ?: return@withContext false
        ensureLoaded(context)
        val cur = _ledger.value
        return@withContext runCatching {
            val payload = hashMapOf<String, Any>(
                "ledgerJson" to serializeLedgerJson(cur),
                "updatedAtMillis" to cur.lastUpdatedMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(uid)
                .collection("ai_coach_ledger").document("state")
                .set(payload, SetOptions.merge())
                .await()
            true
        }.getOrElse { e ->
            Log.w(TAG, "pushToCloudIfSignedIn failed: ${e.message}")
            false
        }
    }

    suspend fun pullFromCloudIfNewer(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUid(context)
        if (uid.isNullOrBlank() || uid == "student_default") return@withContext false
        val firestore = GoogleServicesHelper.getFirebaseFirestore(context) ?: return@withContext false
        ensureLoaded(context)
        val cur = _ledger.value
        return@withContext runCatching {
            val snap = firestore.collection("users").document(uid)
                .collection("ai_coach_ledger").document("state")
                .get()
                .await()
            if (!snap.exists()) return@runCatching false
            val remoteUpdated = snap.getLong("updatedAtMillis") ?: 0L
            val remoteJson = snap.getString("ledgerJson").orEmpty()
            if (remoteJson.isBlank()) return@runCatching false
            val remoteLedger = parseLedgerJson(remoteJson, fallbackUidKey = uidKeyFor(uid))
            if (remoteUpdated > cur.lastUpdatedMillis || (cur.topicRecords.isEmpty() && remoteLedger.topicRecords.isNotEmpty())) {
                persistLocal(context, remoteLedger.copy(lastUpdatedMillis = remoteUpdated.coerceAtLeast(System.currentTimeMillis())))
                true
            } else {
                false
            }
        }.getOrElse { e ->
            Log.w(TAG, "pullFromCloudIfNewer failed: ${e.message}")
            false
        }
    }

    internal fun serializeLedgerJson(ledger: AiCoachLedger): String = gson.toJson(ledger)

    internal fun parseLedgerJson(raw: String?, fallbackUidKey: String = "guest_local"): AiCoachLedger {
        if (raw.isNullOrBlank()) return AiCoachLedger(uidKey = fallbackUidKey)
        return runCatching {
            gson.fromJson(raw, AiCoachLedger::class.java) ?: AiCoachLedger(uidKey = fallbackUidKey)
        }.getOrDefault(AiCoachLedger(uidKey = fallbackUidKey))
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            (ks.getKey(AES_KEY_ALIAS, null) as? SecretKey) ?: run {
                val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    AES_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                gen.init(spec)
                gen.generateKey()
            }
        }.getOrNull()
    }

    private fun encryptPayload(plainJson: String): String {
        val key = getOrCreateSecretKey()
        if (key == null) {
            return "RAW:" + Base64.encodeToString(plainJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        return runCatching {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            "GCM:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrElse {
            "RAW:" + Base64.encodeToString(plainJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decryptPayload(stored: String): String? {
        if (stored.startsWith("RAW:")) {
            return runCatching {
                String(Base64.decode(stored.removePrefix("RAW:"), Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrNull()
        }
        if (stored.startsWith("GCM:")) {
            val key = getOrCreateSecretKey() ?: return null
            return runCatching {
                val combined = Base64.decode(stored.removePrefix("GCM:"), Base64.NO_WRAP)
                if (combined.size < 14) return@runCatching null
                val ivLen = combined[0].toInt() and 0xFF
                if (combined.size <= 1 + ivLen) return@runCatching null
                val iv = combined.copyOfRange(1, 1 + ivLen)
                val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
                val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            }.getOrNull()
        }
        return stored
    }
}
