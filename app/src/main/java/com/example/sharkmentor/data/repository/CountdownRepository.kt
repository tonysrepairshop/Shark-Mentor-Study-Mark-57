package com.example.sharkmentor.data.repository

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.service.sync.SyncWorker
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Immutable
data class ExamCountdownState(
    val title: String = "",
    val targetDateMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val isPendingSync: Boolean = false
) {
    val hasActiveCountdown: Boolean
        get() = targetDateMillis > 0L
}

data class CountdownMergeDecision(
    val merged: ExamCountdownState,
    val shouldUpdateLocal: Boolean,
    val shouldPushToCloud: Boolean
)

/**
 * Pure JVM-testable Last-Write-Wins (LWW) merger for multi-device Exam Countdown sync.
 */
fun resolveCountdownMerge(
    local: ExamCountdownState,
    remote: ExamCountdownState
): CountdownMergeDecision {
    val localHasData = local.targetDateMillis > 0L || local.title.isNotBlank()
    val remoteHasData = remote.targetDateMillis > 0L || remote.title.isNotBlank()

    // Case 1: Both have explicit timestamps -> strictly newer timestamp wins (even clears!)
    if (remote.updatedAtMillis > 0L && local.updatedAtMillis > 0L) {
        return when {
            remote.updatedAtMillis > local.updatedAtMillis -> {
                val merged = remote.copy(isPendingSync = false)
                CountdownMergeDecision(
                    merged = merged,
                    shouldUpdateLocal = merged != local.copy(isPendingSync = false) || local.isPendingSync,
                    shouldPushToCloud = false
                )
            }
            local.updatedAtMillis > remote.updatedAtMillis -> {
                CountdownMergeDecision(
                    merged = local,
                    shouldUpdateLocal = false,
                    shouldPushToCloud = true
                )
            }
            else -> {
                // Identical timestamp: prefer non-empty data if one side is empty; clear pending if matching
                val merged = if (remote.targetDateMillis > 0L && local.targetDateMillis <= 0L) {
                    remote.copy(isPendingSync = false)
                } else {
                    local.copy(isPendingSync = false)
                }
                val differsFromRemote = merged.targetDateMillis != remote.targetDateMillis ||
                    merged.title != remote.title
                CountdownMergeDecision(
                    merged = merged,
                    shouldUpdateLocal = merged != local,
                    shouldPushToCloud = differsFromRemote && local.isPendingSync
                )
            }
        }
    }

    // Case 2: Only remote has a timestamp -> remote wins unless local has an un-timestamped pending edit
    if (remote.updatedAtMillis > 0L && local.updatedAtMillis == 0L) {
        if (local.isPendingSync && localHasData) {
            return CountdownMergeDecision(
                merged = local.copy(updatedAtMillis = maxOf(remote.updatedAtMillis + 1L, System.currentTimeMillis())),
                shouldUpdateLocal = true,
                shouldPushToCloud = true
            )
        }
        val merged = remote.copy(isPendingSync = false)
        return CountdownMergeDecision(
            merged = merged,
            shouldUpdateLocal = merged != local,
            shouldPushToCloud = false
        )
    }

    // Case 3: Only local has a timestamp -> local wins & pushes to cloud
    if (local.updatedAtMillis > 0L && remote.updatedAtMillis == 0L) {
        // Exception: if local has timestamp only because of empty init while remote has real legacy date
        if (!localHasData && remote.targetDateMillis > 0L && !local.isPendingSync) {
            val merged = remote.copy(isPendingSync = false)
            return CountdownMergeDecision(
                merged = merged,
                shouldUpdateLocal = true,
                shouldPushToCloud = false
            )
        }
        return CountdownMergeDecision(
            merged = local,
            shouldUpdateLocal = false,
            shouldPushToCloud = true
        )
    }

    // Case 4: Both have 0L timestamps (legacy pre-Mark-61 data)
    return when {
        remote.targetDateMillis > 0L && local.targetDateMillis <= 0L -> {
            val merged = remote.copy(isPendingSync = false)
            CountdownMergeDecision(
                merged = merged,
                shouldUpdateLocal = true,
                shouldPushToCloud = false
            )
        }
        local.targetDateMillis > 0L && remote.targetDateMillis <= 0L -> {
            CountdownMergeDecision(
                merged = local.copy(isPendingSync = true),
                shouldUpdateLocal = !local.isPendingSync,
                shouldPushToCloud = true
            )
        }
        remote.title.isNotBlank() && local.title.isBlank() -> {
            val merged = local.copy(title = remote.title, isPendingSync = false)
            CountdownMergeDecision(
                merged = merged,
                shouldUpdateLocal = true,
                shouldPushToCloud = false
            )
        }
        local.isPendingSync && localHasData -> {
            CountdownMergeDecision(
                merged = local,
                shouldUpdateLocal = false,
                shouldPushToCloud = true
            )
        }
        else -> {
            CountdownMergeDecision(
                merged = local,
                shouldUpdateLocal = false,
                shouldPushToCloud = false
            )
        }
    }
}

/**
 * Single source of truth for the Exam Countdown tool ("Geri Sayım Aracı").
 *
 * - Keeps legacy `"sharkmentor_prefs"` (`target_date_millis`, `target_exam_title`) AND
 *   [UserPreferences] (`countdownTargetDateMillis`, `countdownExamTitle`, `countdownUpdatedAt`)
 *   100% synchronized.
 * - Offline-first: persists immediately locally with a pending flag and syncs to Firestore
 *   (`students/{uid}`, `users/{uid}`, and `users/{uid}/countdown_settings/config`).
 * - Multi-device restore: pulls and merges via Last-Write-Wins timestamp resolution whenever
 *   a user logs in on a new device, updates from another device, or runs cloud sync.
 */
class CountdownRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val userPrefs = UserPreferences.getInstance(appContext)

    private val _stateFlow = MutableStateFlow(readStateFromPrefs())
    val stateFlow: StateFlow<ExamCountdownState> = _stateFlow.asStateFlow()

    private fun readStateFromPrefs(): ExamCountdownState {
        val rawMillis = prefs.getLong(KEY_TARGET_DATE_MILLIS, 0L)
            .takeIf { it > 0L }
            ?: userPrefs.countdownTargetDateMillis
        val rawTitle = prefs.getString(KEY_TARGET_EXAM_TITLE, null)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: userPrefs.countdownExamTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: ""
        val updatedAt = maxOf(
            prefs.getLong(KEY_COUNTDOWN_UPDATED_AT, 0L),
            userPrefs.countdownUpdatedAt
        )
        val pending = prefs.getBoolean(KEY_COUNTDOWN_PENDING_SYNC, false)
        return ExamCountdownState(
            title = rawTitle,
            targetDateMillis = rawMillis,
            updatedAtMillis = updatedAt,
            isPendingSync = pending
        )
    }

    private fun persistLocal(state: ExamCountdownState, syncProfileExamTitle: Boolean = false) {
        val cleanTitle = state.title.trim()
        prefs.edit()
            .putLong(KEY_TARGET_DATE_MILLIS, state.targetDateMillis)
            .putString(KEY_TARGET_EXAM_TITLE, cleanTitle)
            .putLong(KEY_COUNTDOWN_UPDATED_AT, state.updatedAtMillis)
            .putBoolean(KEY_COUNTDOWN_PENDING_SYNC, state.isPendingSync)
            .apply()

        userPrefs.countdownExamTitle = cleanTitle.ifBlank { null }
        userPrefs.countdownTargetDateMillis = state.targetDateMillis
        userPrefs.countdownUpdatedAt = state.updatedAtMillis
        if (syncProfileExamTitle && cleanTitle.isNotBlank() && cleanTitle != DEFAULT_TITLE) {
            userPrefs.updateStudentTargetExam(cleanTitle)
        }

        _stateFlow.value = state.copy(title = cleanTitle)
    }

    /**
     * Saves the exam countdown title and target date locally and pushes to Firestore immediately
     * (with offline retry queued through [SyncWorker] if offline).
     */
    fun saveCountdown(title: String, targetDateMillis: Long) {
        val cleanTitle = title.trim().ifBlank { DEFAULT_TITLE }
        val now = System.currentTimeMillis()
        val newState = ExamCountdownState(
            title = cleanTitle,
            targetDateMillis = targetDateMillis.coerceAtLeast(0L),
            updatedAtMillis = now,
            isPendingSync = true
        )
        persistLocal(newState, syncProfileExamTitle = true)
        pushToCloudFireAndForget(newState)
    }

    /**
     * Clears the active countdown locally and synchronizes the removal across all devices.
     */
    fun clearCountdown() {
        val now = System.currentTimeMillis()
        val cleared = ExamCountdownState(
            title = "",
            targetDateMillis = 0L,
            updatedAtMillis = now,
            isPendingSync = true
        )
        persistLocal(cleared, syncProfileExamTitle = false)
        pushToCloudFireAndForget(cleared)
    }

    /**
     * Updates only the title if a target date is already set, or stores the preferred title.
     */
    fun updateTitle(title: String) {
        val current = readStateFromPrefs()
        val cleanTitle = title.trim()
        val now = System.currentTimeMillis()
        val updated = current.copy(
            title = cleanTitle,
            updatedAtMillis = now,
            isPendingSync = true
        )
        persistLocal(updated, syncProfileExamTitle = true)
        pushToCloudFireAndForget(updated)
    }

    /**
     * Applies a real-time Firestore `students/{uid}` (or `users/{uid}` / `countdown_settings/config`)
     * snapshot via LWW merge without blocking the calling thread.
     */
    fun applyRemoteSnapshot(snapshot: DocumentSnapshot?): Boolean {
        if (snapshot == null || !snapshot.exists()) return false
        val remote = extractRemoteState(snapshot) ?: return false
        val local = readStateFromPrefs()
        val decision = resolveCountdownMerge(local, remote)

        if (decision.shouldUpdateLocal) {
            persistLocal(decision.merged, syncProfileExamTitle = decision.merged.title.isNotBlank())
        }
        if (decision.shouldPushToCloud) {
            pushToCloudFireAndForget(decision.merged)
        }
        return decision.shouldUpdateLocal
    }

    /**
     * Suspends and performs a complete two-way cloud sync (push pending edits + pull remote updates).
     * Reuses [prefetchedStudentDoc] when provided by [SyncWorker] to avoid redundant Firestore reads.
     */
    suspend fun pullAndMergeFromCloud(
        prefetchedStudentDoc: DocumentSnapshot? = null
    ): ExamCountdownState = withContext(Dispatchers.IO) {
        val current = readStateFromPrefs()
        val user = GoogleServicesHelper.getCurrentUser(context = appContext)
            ?.takeUnless { it.isAnonymous }
            ?: return@withContext current
        val uid = user.uid.takeIf { it.isNotBlank() && it != "student_default" }
            ?: return@withContext current
        val db = GoogleServicesHelper.getFirebaseFirestore(appContext)
            ?: return@withContext current

        val studentDoc = prefetchedStudentDoc ?: runCatching {
            db.collection("students").document(uid).get().await()
        }.getOrNull()

        val studentRemote = studentDoc?.let { extractRemoteState(it) }
        var bestRemote = studentRemote

        // Fallback to users/{uid}/countdown_settings/config and users/{uid} if studentDoc lacks countdown date/timestamp
        if (bestRemote == null || (bestRemote.targetDateMillis <= 0L && bestRemote.updatedAtMillis == 0L)) {
            val configDoc = runCatching {
                db.document("users/$uid/countdown_settings/config").get().await()
            }.getOrNull()
            val configRemote = configDoc?.let { extractRemoteState(it) }
            if (configRemote != null) {
                bestRemote = pickNewerRemote(bestRemote, configRemote)
            }

            if (bestRemote == null || (bestRemote.targetDateMillis <= 0L && bestRemote.updatedAtMillis == 0L)) {
                val userDoc = runCatching {
                    db.collection("users").document(uid).get().await()
                }.getOrNull()
                val userRemote = userDoc?.let { extractRemoteState(it) }
                if (userRemote != null) {
                    bestRemote = pickNewerRemote(bestRemote, userRemote)
                }
            }
        }

        val effectiveRemote = bestRemote ?: ExamCountdownState()
        val decision = resolveCountdownMerge(current, effectiveRemote)
        var finalState = decision.merged
        val needsStudentDocMigration = studentDoc != null &&
            (studentRemote == null || studentRemote.updatedAtMillis == 0L) &&
            finalState.targetDateMillis > 0L

        if (needsStudentDocMigration && finalState.updatedAtMillis == 0L) {
            finalState = finalState.copy(updatedAtMillis = System.currentTimeMillis())
        }

        if (decision.shouldUpdateLocal || needsStudentDocMigration) {
            persistLocal(finalState, syncProfileExamTitle = finalState.title.isNotBlank())
        }

        if (decision.shouldPushToCloud || finalState.isPendingSync || needsStudentDocMigration) {
            val pushed = pushStateToCloudSuspend(uid, finalState)
            if (pushed) {
                finalState = finalState.copy(isPendingSync = false)
                persistLocal(finalState, syncProfileExamTitle = false)
            }
        }

        finalState
    }

    /**
     * Uploads any locally queued offline countdown change during [SyncWorker] outbound push phase.
     * Returns 1 if uploaded, 0 otherwise.
     */
    suspend fun pushPendingToCloudIfNeeded(): Int = withContext(Dispatchers.IO) {
        val current = readStateFromPrefs()
        if (!current.isPendingSync) return@withContext 0
        val user = GoogleServicesHelper.getCurrentUser(context = appContext)
            ?.takeUnless { it.isAnonymous }
            ?: return@withContext 0
        val uid = user.uid.takeIf { it.isNotBlank() && it != "student_default" }
            ?: return@withContext 0

        val ok = pushStateToCloudSuspend(uid, current)
        if (ok) {
            persistLocal(current.copy(isPendingSync = false), syncProfileExamTitle = false)
            1
        } else {
            0
        }
    }

    /**
     * Clears local countdown state when the user logs out so switching accounts never leaks data.
     */
    fun clearLocalForLogout() {
        prefs.edit()
            .remove(KEY_TARGET_DATE_MILLIS)
            .remove(KEY_TARGET_EXAM_TITLE)
            .remove(KEY_COUNTDOWN_UPDATED_AT)
            .remove(KEY_COUNTDOWN_PENDING_SYNC)
            .apply()
        userPrefs.countdownTargetDateMillis = 0L
        userPrefs.countdownExamTitle = null
        userPrefs.countdownUpdatedAt = 0L
        _stateFlow.value = ExamCountdownState()
    }

    private fun pushToCloudFireAndForget(state: ExamCountdownState) {
        val user = GoogleServicesHelper.getCurrentUser(context = appContext)
            ?.takeUnless { it.isAnonymous }
            ?: return
        val uid = user.uid.takeIf { it.isNotBlank() && it != "student_default" } ?: return
        val db = GoogleServicesHelper.getFirebaseFirestore(appContext) ?: run {
            SyncWorker.enqueueImmediateSync(appContext)
            return
        }

        val (studentPayload, userPayload, configPayload) = buildCloudPayloads(state)
        db.collection("students").document(uid)
            .set(studentPayload, SetOptions.merge())
            .addOnSuccessListener {
                // Only clear pending flag if no newer edit happened locally in the meantime
                val latest = readStateFromPrefs()
                if (latest.updatedAtMillis <= state.updatedAtMillis && latest.isPendingSync) {
                    persistLocal(latest.copy(isPendingSync = false), syncProfileExamTitle = false)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Immediate countdown push failed (will retry via SyncWorker): ${e.message}")
                SyncWorker.enqueueImmediateSync(appContext)
            }

        runCatching {
            db.collection("users").document(uid).set(userPayload, SetOptions.merge())
        }
        runCatching {
            db.document("users/$uid/countdown_settings/config").set(configPayload, SetOptions.merge())
        }
    }

    private suspend fun pushStateToCloudSuspend(uid: String, state: ExamCountdownState): Boolean {
        val db = GoogleServicesHelper.getFirebaseFirestore(appContext) ?: return false
        val (studentPayload, userPayload, configPayload) = buildCloudPayloads(state)
        return try {
            db.collection("students").document(uid)
                .set(studentPayload, SetOptions.merge())
                .await()
            runCatching {
                db.collection("users").document(uid).set(userPayload, SetOptions.merge()).await()
            }
            runCatching {
                db.document("users/$uid/countdown_settings/config")
                    .set(configPayload, SetOptions.merge())
                    .await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Suspended countdown push failed: ${e.message}")
            false
        }
    }

    private fun buildCloudPayloads(
        state: ExamCountdownState
    ): Triple<Map<String, Any>, Map<String, Any>, Map<String, Any>> {
        val ts = if (state.updatedAtMillis > 0L) state.updatedAtMillis else System.currentTimeMillis()
        val cleanTitle = state.title.trim()

        val studentPayload = hashMapOf<String, Any>(
            "targetExamTitle" to cleanTitle,
            "targetExamDate" to state.targetDateMillis,
            "countdownUpdatedAt" to ts
        )
        if (cleanTitle.isNotBlank() && cleanTitle != DEFAULT_TITLE) {
            studentPayload["targetExam"] = cleanTitle
        }

        val userPayload = hashMapOf<String, Any>(
            "targetExamTitle" to cleanTitle,
            "targetExamDate" to state.targetDateMillis,
            "countdownUpdatedAt" to ts
        )
        if (cleanTitle.isNotBlank() && cleanTitle != DEFAULT_TITLE) {
            userPayload["targetExam"] = cleanTitle
        }

        val configPayload = hashMapOf<String, Any>(
            "examTitle" to cleanTitle,
            "targetDateMillis" to state.targetDateMillis,
            "updatedAt" to ts
        )
        return Triple(studentPayload, userPayload, configPayload)
    }

    private fun extractRemoteState(doc: DocumentSnapshot): ExamCountdownState? {
        val hasCountdownFields = doc.contains("targetExamDate") ||
            doc.contains("targetDateMillis") ||
            doc.contains("countdownUpdatedAt") ||
            doc.contains("targetExamTitle") ||
            doc.contains("examTitle") ||
            doc.contains("targetExam")
        if (!hasCountdownFields) return null

        val targetMillis = readLongField(doc, "targetExamDate")
            ?: readLongField(doc, "targetDateMillis")
            ?: 0L
        val title = listOf("targetExamTitle", "examTitle", "targetExam")
            .firstNotNullOfOrNull { key ->
                doc.getString(key)?.trim()?.takeIf { it.isNotBlank() }
            }.orEmpty()
        val updatedAt = readLongField(doc, "countdownUpdatedAt")
            ?: readLongField(doc, "updatedAt")
            ?: 0L

        if (targetMillis <= 0L && title.isBlank() && updatedAt <= 0L) return null
        return ExamCountdownState(
            title = title,
            targetDateMillis = targetMillis.coerceAtLeast(0L),
            updatedAtMillis = updatedAt.coerceAtLeast(0L),
            isPendingSync = false
        )
    }

    private fun pickNewerRemote(
        first: ExamCountdownState?,
        second: ExamCountdownState
    ): ExamCountdownState {
        if (first == null) return second
        return when {
            second.updatedAtMillis > first.updatedAtMillis -> second
            first.updatedAtMillis > second.updatedAtMillis -> first
            second.targetDateMillis > 0L && first.targetDateMillis <= 0L -> second
            else -> first
        }
    }

    private fun readLongField(doc: DocumentSnapshot, field: String): Long? {
        val raw = doc.get(field) ?: return null
        return (raw as? Number)?.toLong()
            ?: raw.toString().toDoubleOrNull()?.toLong()
            ?: raw.toString().toLongOrNull()
    }

    companion object {
        private const val TAG = "CountdownRepository"
        private const val PREFS_NAME = "sharkmentor_prefs"
        private const val KEY_TARGET_DATE_MILLIS = "target_date_millis"
        private const val KEY_TARGET_EXAM_TITLE = "target_exam_title"
        private const val KEY_COUNTDOWN_UPDATED_AT = "countdown_updated_at"
        private const val KEY_COUNTDOWN_PENDING_SYNC = "countdown_pending_sync"
        const val DEFAULT_TITLE = "Hedef Sınav"

        @Volatile
        private var INSTANCE: CountdownRepository? = null

        fun getInstance(context: Context): CountdownRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CountdownRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
