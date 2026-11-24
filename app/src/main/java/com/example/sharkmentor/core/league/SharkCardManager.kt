package com.example.sharkmentor.core.league

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.core.badges.SharkBadgeCatalog
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.data.repository.StreakRepository
import com.example.sharkmentor.ui.viewmodel.SharkTier
import com.example.sharkmentor.ui.viewmodel.toTier
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class SharkCardSnapshot(
    val studentId: String = "",
    val displayName: String = "",
    val targetExam: String = "",
    val grade: String = "",
    val tierLabel: String = SharkTier.CAMGOZ.label,
    val archetypeTitle: String = "Odak Takipçisi",
    val weeklyFocusMinutes: Int = 0,
    val streakDays: Int = 0,
    val mentorPoints: Int = 0,
    val masteredTopicsCount: Int = 0,
    val resolvedErrorsCount: Int = 0,
    val strongestSubject: String = "",
    val isPublic: Boolean = true,
    val updatedAtMillis: Long = 0L
)

/**
 * Pure JVM-testable archetype resolver for Shark Card.
 * Returns concise, dignified Turkish titles with zero emojis.
 */
fun resolveSharkCardArchetype(
    weeklyFocusMinutes: Int,
    streakDays: Int,
    masteredTopicsCount: Int,
    resolvedErrorsCount: Int
): String = when {
    weeklyFocusMinutes >= 1800 && streakDays >= 14 -> "Megalodon Disiplini"
    streakDays >= 21 -> "Seri Muhafızı"
    weeklyFocusMinutes >= 1200 -> "Derin Odak"
    resolvedErrorsCount >= 25 -> "Hata Analisti"
    masteredTopicsCount >= 15 -> "Konu Ustası"
    weeklyFocusMinutes >= 300 || streakDays >= 5 -> "İstikrarlı Ritim"
    else -> "Yükselen Yüzgeç"
}

object SharkCardManager {

    private const val TAG = "SharkCardManager"
    private const val PREFS_NAME = "shark_card_prefs_v1"
    private const val KEY_CARD_JSON = "card_snapshot_json"
    private const val KEY_IS_PUBLIC = "card_is_public"
    private const val KEY_LAST_CLOUD_SIG = "last_cloud_sig"
    private const val KEY_LAST_CLOUD_AT = "last_cloud_at_ms"

    private const val WRITE_COOLDOWN_MS = 30_000L
    private const val PEER_CACHE_TTL_MS = 5L * 60L * 1000L

    private val gson = Gson()
    private data class CachedPeerCard(
        val isPublic: Boolean,
        val card: SharkCardSnapshot?,
        val fetchedAtMs: Long
    )
    private val peerCardCache = ConcurrentHashMap<String, CachedPeerCard>()

    @Volatile
    private var loaded = false

    private val _myCard = MutableStateFlow(SharkCardSnapshot())
    val myCard: StateFlow<SharkCardSnapshot> = _myCard.asStateFlow()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedPublic = prefs.getBoolean(KEY_IS_PUBLIC, true)
            val raw = prefs.getString(KEY_CARD_JSON, null)
            val parsed = if (!raw.isNullOrBlank()) {
                runCatching { gson.fromJson(raw, SharkCardSnapshot::class.java) }.getOrNull()
            } else null
            _myCard.value = (parsed ?: SharkCardSnapshot()).copy(isPublic = savedPublic)
            loaded = true
        }
    }

    /**
     * Recomputes the current user's Shark Card from live local Room/Preferences data
     * and syncs to `students/{uid}` ONLY if changed (respecting Firebase write quotas).
     */
    suspend fun refreshCard(
        context: Context,
        displayNameOverride: String? = null,
        gradeOverride: String? = null,
        targetExamOverride: String? = null,
        syncToCloud: Boolean = true
    ): SharkCardSnapshot = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isPublic = prefs.getBoolean(KEY_IS_PUBLIC, _myCard.value.isPublic)

        val userPrefs = UserPreferences.getInstance(appCtx)
        val authUser = GoogleServicesHelper.getFirebaseAuth(appCtx)?.currentUser
        val uid = authUser?.uid.orEmpty()

        val name = (displayNameOverride?.takeIf { it.isNotBlank() }
            ?: userPrefs.studentName?.takeIf { it.isNotBlank() }
            ?: authUser?.displayName?.takeIf { it.isNotBlank() }
            ?: "Öğrenci").trim()
        val grade = (gradeOverride?.takeIf { it.isNotBlank() }
            ?: userPrefs.studentGrade?.takeIf { it.isNotBlank() }
            ?: "").trim()
        val exam = (targetExamOverride?.takeIf { it.isNotBlank() }
            ?: userPrefs.studentTargetExam?.takeIf { it.isNotBlank() }
            ?: "").trim()

        val db = SharkMentorDatabase.getDatabase(appCtx)
        val startOfWeekMillis = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val weekSessions = runCatching {
            db.sessionDao().getSessionsSince(startOfWeekMillis)
        }.getOrDefault(emptyList())
        val weeklySeconds = weekSessions.sumOf { it.durationSeconds.coerceAtLeast(0L) }
        val weeklyMinutes = (weeklySeconds / 60L).toInt()
        val tier = weeklySeconds.toTier()

        val streakDays = runCatching {
            StreakRepository(appCtx).streakState.value.streakCount
        }.getOrDefault(0)
        val mentorPoints = runCatching {
            SharkBadgeCatalog.getMentorPoints(appCtx)
        }.getOrDefault(0)

        val allTopics = runCatching {
            db.goalsDao().getAllTopicsRaw()
        }.getOrDefault(emptyList())
        val masteredTopics = allTopics.count { it.currentLevel >= 4 }

        val strongestSubject = allTopics
            .groupBy { it.subject.trim() }
            .filterKeys { it.isNotBlank() }
            .maxByOrNull { (_, list) ->
                list.map { it.currentLevel }.average() + (list.count { it.currentLevel >= 4 } * 0.25)
            }
            ?.key
            .orEmpty()

        val resolvedErrors = runCatching {
            db.errorQuestionDao().getAllQuestionsRaw().count { it.reps > 0 }
        }.getOrDefault(0)

        val archetype = resolveSharkCardArchetype(
            weeklyFocusMinutes = weeklyMinutes,
            streakDays = streakDays,
            masteredTopicsCount = masteredTopics,
            resolvedErrorsCount = resolvedErrors
        )

        val now = System.currentTimeMillis()
        val fresh = SharkCardSnapshot(
            studentId = uid,
            displayName = name,
            targetExam = exam,
            grade = grade,
            tierLabel = tier.label,
            archetypeTitle = archetype,
            weeklyFocusMinutes = weeklyMinutes,
            streakDays = streakDays,
            mentorPoints = mentorPoints,
            masteredTopicsCount = masteredTopics,
            resolvedErrorsCount = resolvedErrors,
            strongestSubject = strongestSubject,
            isPublic = isPublic,
            updatedAtMillis = now
        )

        prefs.edit()
            .putString(KEY_CARD_JSON, gson.toJson(fresh))
            .putBoolean(KEY_IS_PUBLIC, isPublic)
            .apply()
        _myCard.value = fresh

        if (uid.isNotBlank()) {
            peerCardCache[uid] = CachedPeerCard(isPublic = isPublic, card = fresh, fetchedAtMs = now)
            if (syncToCloud) {
                pushCardToFirestoreGuarded(appCtx, uid, fresh, forcePrivacyToggle = false)
            }
        }
        fresh
    }

    suspend fun setPublicVisibility(
        context: Context,
        isPublic: Boolean
    ): SharkCardSnapshot = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_PUBLIC, isPublic).apply()

        val base = if (_myCard.value.updatedAtMillis == 0L) {
            refreshCard(appCtx, syncToCloud = false)
        } else {
            _myCard.value
        }
        val updated = base.copy(isPublic = isPublic, updatedAtMillis = System.currentTimeMillis())
        prefs.edit().putString(KEY_CARD_JSON, gson.toJson(updated)).apply()
        _myCard.value = updated

        val uid = GoogleServicesHelper.getFirebaseAuth(appCtx)?.currentUser?.uid.orEmpty()
        if (uid.isNotBlank()) {
            peerCardCache[uid] = CachedPeerCard(isPublic = isPublic, card = updated, fetchedAtMs = System.currentTimeMillis())
            pushCardToFirestoreGuarded(appCtx, uid, updated, forcePrivacyToggle = true)
        }
        updated
    }

    private suspend fun pushCardToFirestoreGuarded(
        context: Context,
        uid: String,
        card: SharkCardSnapshot,
        forcePrivacyToggle: Boolean
    ) {
        val db = GoogleServicesHelper.getFirebaseFirestore(context) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sig = buildString {
            append(card.isPublic).append('|')
            if (card.isPublic) {
                append(card.displayName).append('|')
                append(card.targetExam).append('|')
                append(card.grade).append('|')
                append(card.tierLabel).append('|')
                append(card.archetypeTitle).append('|')
                append(card.weeklyFocusMinutes).append('|')
                append(card.streakDays).append('|')
                append(card.mentorPoints).append('|')
                append(card.masteredTopicsCount).append('|')
                append(card.resolvedErrorsCount).append('|')
                append(card.strongestSubject)
            }
        }
        val prevSig = prefs.getString(KEY_LAST_CLOUD_SIG, null)
        val prevAt = prefs.getLong(KEY_LAST_CLOUD_AT, 0L)
        val now = System.currentTimeMillis()
        if (!forcePrivacyToggle) {
            if (sig == prevSig) return
            if (now - prevAt < WRITE_COOLDOWN_MS) return
        }

        val payload = hashMapOf<String, Any>(
            "sharkCardPublic" to card.isPublic
        )
        if (card.isPublic) {
            payload["sharkCard"] = mapOf(
                "displayName" to card.displayName,
                "targetExam" to card.targetExam,
                "grade" to card.grade,
                "tierLabel" to card.tierLabel,
                "archetypeTitle" to card.archetypeTitle,
                "weeklyFocusMinutes" to card.weeklyFocusMinutes,
                "streakDays" to card.streakDays,
                "mentorPoints" to card.mentorPoints,
                "masteredTopicsCount" to card.masteredTopicsCount,
                "resolvedErrorsCount" to card.resolvedErrorsCount,
                "strongestSubject" to card.strongestSubject,
                "updatedAtMillis" to card.updatedAtMillis
            )
        }

        runCatching {
            db.collection("students").document(uid)
                .set(payload, SetOptions.merge())
                .await()
            prefs.edit()
                .putString(KEY_LAST_CLOUD_SIG, sig)
                .putLong(KEY_LAST_CLOUD_AT, now)
                .apply()
        }.onFailure { e ->
            Log.w(TAG, "pushCardToFirestoreGuarded failed: ${e.message}")
        }
    }

    /**
     * Reads a League member's public Shark Card from `students/{studentId}` with a 5-min in-memory cache
     * so opening a member sheet never burns repeated reads.
     * Returns `null` if the member disabled public visibility (`sharkCardPublic == false`).
     */
    suspend fun fetchMemberPublicCardCached(
        context: Context,
        studentId: String
    ): Pair<Boolean, SharkCardSnapshot?> = withContext(Dispatchers.IO) {
        if (studentId.isBlank()) return@withContext false to null
        val now = System.currentTimeMillis()
        val cached = peerCardCache[studentId]
        if (cached != null && (now - cached.fetchedAtMs) < PEER_CACHE_TTL_MS) {
            return@withContext cached.isPublic to (if (cached.isPublic) cached.card else null)
        }

        val db = GoogleServicesHelper.getFirebaseFirestore(context) ?: return@withContext false to null
        val doc = runCatching {
            db.collection("students").document(studentId).get().await()
        }.getOrNull() ?: return@withContext false to null

        val parsed = extractCardFromStudentDoc(studentId, doc)
        peerCardCache[studentId] = CachedPeerCard(
            isPublic = parsed.first,
            card = parsed.second,
            fetchedAtMs = now
        )
        parsed
    }

    fun extractCardFromStudentDoc(
        studentId: String,
        doc: DocumentSnapshot
    ): Pair<Boolean, SharkCardSnapshot?> {
        if (!doc.exists()) return false to null
        val isPublic = doc.getBoolean("sharkCardPublic") ?: false
        if (!isPublic) return false to null

        @Suppress("UNCHECKED_CAST")
        val rawMap = doc.get("sharkCard") as? Map<String, Any?> ?: return false to null
        val weeklyMins = (rawMap["weeklyFocusMinutes"] as? Number)?.toInt() ?: 0
        val streak = (rawMap["streakDays"] as? Number)?.toInt() ?: 0
        val mentorPts = (rawMap["mentorPoints"] as? Number)?.toInt() ?: 0
        val mastered = (rawMap["masteredTopicsCount"] as? Number)?.toInt() ?: 0
        val resolvedErr = (rawMap["resolvedErrorsCount"] as? Number)?.toInt() ?: 0

        val snapshot = SharkCardSnapshot(
            studentId = studentId,
            displayName = (rawMap["displayName"] as? String).orEmpty().ifBlank {
                doc.getString("displayName") ?: doc.getString("name") ?: "Öğrenci"
            },
            targetExam = (rawMap["targetExam"] as? String).orEmpty(),
            grade = (rawMap["grade"] as? String).orEmpty(),
            tierLabel = (rawMap["tierLabel"] as? String).orEmpty().ifBlank { SharkTier.CAMGOZ.label },
            archetypeTitle = (rawMap["archetypeTitle"] as? String).orEmpty().ifBlank {
                resolveSharkCardArchetype(weeklyMins, streak, mastered, resolvedErr)
            },
            weeklyFocusMinutes = weeklyMins,
            streakDays = streak,
            mentorPoints = mentorPts,
            masteredTopicsCount = mastered,
            resolvedErrorsCount = resolvedErr,
            strongestSubject = (rawMap["strongestSubject"] as? String).orEmpty(),
            isPublic = true,
            updatedAtMillis = (rawMap["updatedAtMillis"] as? Number)?.toLong() ?: 0L
        )
        return true to snapshot
    }
}
