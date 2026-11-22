package com.example.sharkmentor.data.repository

import android.content.Context
import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.widget.WidgetUpdateManager
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.FocusSessionEntity
import com.example.sharkmentor.service.sync.SyncWorker
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class StreakState(
    val streakCount: Int = 0,
    val streakFreezeAvailable: Int = 0,
    val lastStreakDate: String = "",
    val wasFrozenToday: Boolean = false,
    val daysSinceLastStudy: Int = 0,
    val completedDates: Set<String> = emptySet(),
    val updatedAtMillis: Long = 0L
)

data class StreakMergeDecision(
    val merged: StreakState,
    val shouldUpdateLocal: Boolean,
    val shouldPushToCloud: Boolean
)

/**
 * Pure JVM-testable two-way Water Streak ("Su Serisi") merge resolver for multi-device sync.
 *
 * Guarantees:
 * - A fresh device / reinstall restores the user's exact streak, freezes, last date, and
 *   rolling 14-day completed-date history without resetting to 0.
 * - Cross-device concurrent study picks the most recent active streak date, never regresses
 *   streakCount on the same day, unions completedDates (last 14 days), and resolves freeze
 *   changes via updatedAtMillis.
 */
fun resolveStreakMerge(
    local: StreakState,
    remote: StreakState,
    today: LocalDate = LocalDate.now(),
    localPendingSync: Boolean = false
): StreakMergeDecision {
    val mergedDates = trimRecentIsoDates(local.completedDates + remote.completedDates)

    val localDate = parseIsoDateOrNull(local.lastStreakDate)
    val remoteDate = parseIsoDateOrNull(remote.lastStreakDate)

    val localHasData = local.streakCount > 0 || localDate != null || local.completedDates.isNotEmpty()
    val remoteHasData = remote.streakCount > 0 || remoteDate != null || remote.completedDates.isNotEmpty()

    if (!localHasData && !remoteHasData) {
        return StreakMergeDecision(
            merged = local,
            shouldUpdateLocal = false,
            shouldPushToCloud = false
        )
    }

    if (!localHasData && remoteHasData) {
        val merged = remote.copy(
            completedDates = mergedDates,
            daysSinceLastStudy = computeDaysSinceDate(remoteDate, today)
        )
        return StreakMergeDecision(
            merged = merged,
            shouldUpdateLocal = true,
            shouldPushToCloud = false
        )
    }

    if (localHasData && !remoteHasData) {
        val merged = local.copy(
            completedDates = mergedDates,
            daysSinceLastStudy = computeDaysSinceDate(localDate, today),
            updatedAtMillis = if (local.updatedAtMillis > 0L) local.updatedAtMillis else System.currentTimeMillis()
        )
        return StreakMergeDecision(
            merged = merged,
            shouldUpdateLocal = merged != local,
            shouldPushToCloud = true
        )
    }

    // Both local and remote have data
    val winner: StreakState = when {
        remoteDate != null && localDate == null -> remote
        localDate != null && remoteDate == null -> local
        remoteDate != null && localDate != null && remoteDate.isAfter(localDate) -> {
            // Remote has a strictly newer study day (e.g. Device A studied today, Device B yesterday)
            val resolvedCount = if (
                ChronoUnit.DAYS.between(localDate, remoteDate) == 1L &&
                remote.streakCount < local.streakCount + 1 &&
                local.streakCount > 0
            ) {
                // Protect against stale cloud count if Device A had missed a prior sync
                maxOf(remote.streakCount, local.streakCount + 1)
            } else {
                remote.streakCount
            }
            remote.copy(streakCount = resolvedCount)
        }
        remoteDate != null && localDate != null && localDate.isAfter(remoteDate) -> {
            val resolvedCount = if (
                ChronoUnit.DAYS.between(remoteDate, localDate) == 1L &&
                local.streakCount < remote.streakCount + 1 &&
                remote.streakCount > 0
            ) {
                maxOf(local.streakCount, remote.streakCount + 1)
            } else {
                local.streakCount
            }
            local.copy(streakCount = resolvedCount)
        }
        else -> {
            // Same lastStreakDate (or both null with legacy streakCount only)
            val bestCount = maxOf(local.streakCount, remote.streakCount)
            val bestFreeze = when {
                remote.updatedAtMillis > local.updatedAtMillis -> remote.streakFreezeAvailable
                local.updatedAtMillis > remote.updatedAtMillis -> local.streakFreezeAvailable
                else -> maxOf(local.streakFreezeAvailable, remote.streakFreezeAvailable)
            }
            val bestUpdated = maxOf(local.updatedAtMillis, remote.updatedAtMillis)
            local.copy(
                streakCount = bestCount,
                streakFreezeAvailable = bestFreeze,
                wasFrozenToday = local.wasFrozenToday || remote.wasFrozenToday,
                updatedAtMillis = bestUpdated
            )
        }
    }

    val winnerDate = parseIsoDateOrNull(winner.lastStreakDate)
    val finalDates = if (winner.lastStreakDate.isNotBlank()) {
        trimRecentIsoDates(mergedDates + winner.lastStreakDate)
    } else {
        mergedDates
    }
    val finalMerged = winner.copy(
        completedDates = finalDates,
        daysSinceLastStudy = computeDaysSinceDate(winnerDate, today),
        updatedAtMillis = maxOf(winner.updatedAtMillis, local.updatedAtMillis, remote.updatedAtMillis)
    )

    val localChanged = finalMerged.streakCount != local.streakCount ||
        finalMerged.streakFreezeAvailable != local.streakFreezeAvailable ||
        finalMerged.lastStreakDate != local.lastStreakDate ||
        finalMerged.completedDates != local.completedDates

    val cloudDiffers = finalMerged.streakCount != remote.streakCount ||
        finalMerged.streakFreezeAvailable != remote.streakFreezeAvailable ||
        finalMerged.lastStreakDate != remote.lastStreakDate ||
        finalMerged.completedDates != remote.completedDates

    return StreakMergeDecision(
        merged = finalMerged,
        shouldUpdateLocal = localChanged,
        shouldPushToCloud = cloudDiffers || localPendingSync
    )
}

internal fun trimRecentIsoDates(dates: Iterable<String>, maxCount: Int = 14): Set<String> {
    return dates
        .map { it.trim() }
        .filter { parseIsoDateOrNull(it) != null }
        .distinct()
        .sortedDescending()
        .take(maxCount)
        .toSet()
}

private fun parseIsoDateOrNull(raw: String): LocalDate? {
    if (raw.isBlank()) return null
    return runCatching { LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
}

private fun computeDaysSinceDate(date: LocalDate?, today: LocalDate): Int {
    if (date == null) return 0
    return ChronoUnit.DAYS.between(date, today).toInt().coerceAtLeast(0)
}

class StreakRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val streakState: StateFlow<StreakState>
        get() = getOrInitSharedFlow(appContext).asStateFlow()

    init {
        // Refresh shared flow from disk on construction
        getOrInitSharedFlow(appContext).value = readStateFromPrefs()
    }

    private fun readStateFromPrefs(): StreakState {
        val lastDateStr = prefs.getString(KEY_LAST_DATE, "") ?: ""
        val storedDates = prefs.getStringSet(KEY_COMPLETED_DATES, emptySet())?.toSet().orEmpty()
        val combinedDates = if (lastDateStr.isNotBlank()) {
            trimRecentIsoDates(storedDates + lastDateStr)
        } else {
            trimRecentIsoDates(storedDates)
        }
        val daysSince = computeDaysSince(lastDateStr, LocalDate.now())
        return StreakState(
            streakCount = prefs.getInt(KEY_STREAK_COUNT, 0),
            streakFreezeAvailable = prefs.getInt(KEY_FREEZE_AVAILABLE, 0),
            lastStreakDate = lastDateStr,
            daysSinceLastStudy = daysSince,
            completedDates = combinedDates,
            updatedAtMillis = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    /**
     * Evaluates today's Water Streak ("Su Serisi") progress.
     * Criteria: at least 20 minutes of focus OR 20 logged questions today.
     * Returns true ONLY when today's water drop is newly secured.
     */
    fun evaluateStreak(completedMinutes: Int, questionsLogged: Int): Boolean {
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val state = readStateFromPrefs()
        getOrInitSharedFlow(appContext).value = state
        if (state.lastStreakDate == todayStr) {
            return false
        }

        if (completedMinutes >= MIN_DAILY_STREAK_MINUTES || questionsLogged >= MIN_DAILY_STREAK_QUESTIONS) {
            var newStreak = state.streakCount
            var newFreeze = state.streakFreezeAvailable
            var wasFrozen = false

            if (state.lastStreakDate.isNotBlank()) {
                val lastDate = parseIsoDateOrNull(state.lastStreakDate)
                if (lastDate != null) {
                    val daysBetween = ChronoUnit.DAYS.between(lastDate, today)
                    when {
                        daysBetween == 1L -> newStreak = (newStreak + 1).coerceAtLeast(1)
                        daysBetween > 1L -> {
                            val missedDays = daysBetween - 1
                            if (newFreeze >= missedDays) {
                                newFreeze -= missedDays.toInt()
                                newStreak = (newStreak + 1).coerceAtLeast(1)
                                wasFrozen = true
                            } else {
                                newStreak = 1
                                newFreeze = 0
                            }
                        }
                        daysBetween <= 0L -> newStreak = newStreak.coerceAtLeast(1)
                    }
                } else {
                    newStreak = 1
                }
            } else {
                newStreak = 1
            }

            // Earn a streak freeze automatically every 7 continuous days (capped at 3)
            if (newStreak > 0 && newStreak % 7 == 0 && newFreeze < 3) {
                newFreeze += 1
            }

            val newState = StreakState(
                streakCount = newStreak,
                streakFreezeAvailable = newFreeze,
                lastStreakDate = todayStr,
                wasFrozenToday = wasFrozen,
                daysSinceLastStudy = 0,
                completedDates = trimRecentIsoDates(state.completedDates + todayStr),
                updatedAtMillis = System.currentTimeMillis()
            )

            persistState(newState, markPendingSync = true, syncCloudNow = true)
            return true
        }

        return false
    }

    /**
     * Reconciles Water Streak against Room [FocusSessionEntity] history AND preserves synced
     * cloud streak / completed-date history on new devices where Room may not yet hold older days.
     */
    fun syncFromRoomSessions(
        allSessions: List<FocusSessionEntity>,
        liveTodayMinutes: Long = 0L
    ): StreakState {
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val zone = ZoneId.systemDefault()

        val minutesByDate = HashMap<LocalDate, Long>()
        for (session in allSessions) {
            val date = Instant.ofEpochMilli(session.completedAt).atZone(zone).toLocalDate()
            minutesByDate[date] = (minutesByDate[date] ?: 0L) + (session.durationSeconds / 60L)
        }
        if (liveTodayMinutes > 0L) {
            minutesByDate[today] = maxOf(minutesByDate[today] ?: 0L, liveTodayMinutes)
        }

        val roomCompletedDates = minutesByDate
            .filterValues { it >= MIN_DAILY_STREAK_MINUTES }
            .keys
            .sortedDescending()

        val existing = readStateFromPrefs()
        val existingIsoDates = existing.completedDates
        val combinedDateSet = HashSet<LocalDate>()
        roomCompletedDates.forEach { combinedDateSet.add(it) }
        existingIsoDates.mapNotNull { parseIsoDateOrNull(it) }.forEach { combinedDateSet.add(it) }

        val lastStudyDateFromSessions = roomCompletedDates.firstOrNull()

        // Compute continuous streak ending today or yesterday using combined Room + Cloud dates
        var computedStreak = 0
        if (combinedDateSet.isNotEmpty()) {
            var cursor = if (combinedDateSet.contains(today)) today else today.minusDays(1)
            while (combinedDateSet.contains(cursor)) {
                computedStreak += 1
                cursor = cursor.minusDays(1)
            }
        }

        val existingLastDate = parseIsoDateOrNull(existing.lastStreakDate)
        val todayCompletedInSessions = (minutesByDate[today] ?: 0L) >= MIN_DAILY_STREAK_MINUTES
        val newlyCompletedToday = todayCompletedInSessions && existing.lastStreakDate != todayStr

        val daysGapFromExisting = if (existingLastDate != null) {
            ChronoUnit.DAYS.between(existingLastDate, today)
        } else {
            Long.MAX_VALUE
        }
        val existingStillValid = existingLastDate != null &&
            daysGapFromExisting <= (1L + existing.streakFreezeAvailable.toLong())

        var resolvedFreeze = existing.streakFreezeAvailable
        var wasFrozen = existing.wasFrozenToday
        if (newlyCompletedToday && existingLastDate != null && daysGapFromExisting > 1L) {
            val missedDays = (daysGapFromExisting - 1L).toInt()
            if (resolvedFreeze >= missedDays) {
                resolvedFreeze -= missedDays
                wasFrozen = true
            } else {
                resolvedFreeze = 0
            }
        }

        val finalStreak = when {
            todayCompletedInSessions -> maxOf(
                computedStreak,
                if (existing.lastStreakDate == todayStr) existing.streakCount
                else if (existingStillValid) existing.streakCount + 1
                else 1
            )
            computedStreak > 0 -> maxOf(computedStreak, if (existingStillValid) existing.streakCount else 0)
            existingStillValid -> existing.streakCount
            else -> 0
        }

        if (newlyCompletedToday && finalStreak > 0 && finalStreak % 7 == 0 && resolvedFreeze < 3) {
            resolvedFreeze += 1
        }

        val finalLastDate: LocalDate? = listOfNotNull(
            if (todayCompletedInSessions) today else null,
            lastStudyDateFromSessions,
            existingLastDate
        ).maxOrNull()

        val finalLastDateStr = finalLastDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: ""
        val daysSince = if (finalLastDate != null) {
            ChronoUnit.DAYS.between(finalLastDate, today).toInt().coerceAtLeast(0)
        } else {
            val anyStudyDate = minutesByDate.keys.maxOrNull()
            if (anyStudyDate != null) {
                ChronoUnit.DAYS.between(anyStudyDate, today).toInt().coerceAtLeast(0)
            } else 0
        }

        val roomIsoDates = roomCompletedDates.map { it.format(DateTimeFormatter.ISO_LOCAL_DATE) }
        val mergedIsoDates = trimRecentIsoDates(
            existingIsoDates + roomIsoDates + listOfNotNull(finalLastDateStr.takeIf { it.isNotBlank() })
        )

        val changed = finalStreak != existing.streakCount ||
            resolvedFreeze != existing.streakFreezeAvailable ||
            finalLastDateStr != existing.lastStreakDate ||
            mergedIsoDates != existing.completedDates

        val resolvedState = StreakState(
            streakCount = finalStreak,
            streakFreezeAvailable = resolvedFreeze,
            lastStreakDate = finalLastDateStr,
            wasFrozenToday = wasFrozen,
            daysSinceLastStudy = daysSince,
            completedDates = mergedIsoDates,
            updatedAtMillis = if (changed) System.currentTimeMillis() else existing.updatedAtMillis
        )

        if (changed) {
            val shouldPush = newlyCompletedToday ||
                resolvedState.streakCount > existing.streakCount ||
                resolvedState.lastStreakDate > existing.lastStreakDate
            persistState(
                state = resolvedState,
                markPendingSync = shouldPush || prefs.getBoolean(KEY_PENDING_SYNC, false),
                syncCloudNow = shouldPush
            )
        } else {
            getOrInitSharedFlow(appContext).value = resolvedState
        }
        return resolvedState
    }

    /**
     * Convenience suspend helper called from FocusSessionRecorder when a checkpoint or
     * finalized focus session is persisted to Room.
     */
    suspend fun evaluateFromRoomAfterFocus(liveTodayMinutes: Long = 0L): Boolean {
        val before = readStateFromPrefs()
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val allSessions = runCatching {
            SharkMentorDatabase.getDatabase(appContext).sessionDao().getAllSessionsRaw()
        }.getOrDefault(emptyList())
        val after = syncFromRoomSessions(allSessions, liveTodayMinutes)
        return before.lastStreakDate != todayStr && after.lastStreakDate == todayStr
    }

    /**
     * Applies a live Firestore `students/{uid}` (or `users/{uid}`) snapshot without blocking the UI.
     * Returns true if local state changed (caller can refresh home widgets).
     */
    fun applyRemoteSnapshot(snapshot: DocumentSnapshot?): Boolean {
        if (snapshot == null || !snapshot.exists()) return false
        val remote = extractRemoteState(snapshot) ?: return false
        val local = readStateFromPrefs()
        val pending = prefs.getBoolean(KEY_PENDING_SYNC, false)
        val decision = resolveStreakMerge(local, remote, LocalDate.now(), localPendingSync = pending)

        if (decision.shouldUpdateLocal) {
            persistState(
                state = decision.merged,
                markPendingSync = decision.shouldPushToCloud,
                syncCloudNow = decision.shouldPushToCloud
            )
        } else if (decision.shouldPushToCloud) {
            syncToFirestoreFireAndForget(decision.merged)
        }
        return decision.shouldUpdateLocal
    }

    /**
     * Full two-way cloud synchronization:
     * 1. Reads Water Streak from `students/{uid}` (reusing [prefetchedStudentDoc] when available)
     *    with one-shot migration fallback to `users/{uid}` for pre-Mark-61 users.
     * 2. Merges local + remote losslessly via [resolveStreakMerge].
     * 3. Updates local storage & widgets if remote was newer, and pushes back to BOTH
     *    `students/{uid}` and `users/{uid}` if local was newer or pending.
     */
    suspend fun pullAndMergeFromCloud(
        prefetchedStudentDoc: DocumentSnapshot? = null
    ): StreakState = withContext(Dispatchers.IO) {
        val local = readStateFromPrefs()
        val pending = prefs.getBoolean(KEY_PENDING_SYNC, false)
        val user = GoogleServicesHelper.getCurrentUser(context = appContext)
            ?.takeUnless { it.isAnonymous }
            ?: return@withContext local
        val uid = user.uid.takeIf { it.isNotBlank() && it != "student_default" }
            ?: return@withContext local
        val db = GoogleServicesHelper.getFirebaseFirestore(appContext)
            ?: return@withContext local

        val studentDoc = prefetchedStudentDoc ?: runCatching {
            db.collection("students").document(uid).get().await()
        }.getOrNull()

        val studentRemote = studentDoc?.let { extractRemoteState(it) }
        var bestRemote = studentRemote

        // Legacy migration fallback: pre-Mark-61 StreakRepository only wrote to users/{uid}
        if (bestRemote == null || bestRemote.lastStreakDate.isBlank()) {
            val userDoc = runCatching {
                db.collection("users").document(uid).get().await()
            }.getOrNull()
            val userRemote = userDoc?.let { extractRemoteState(it) }
            if (userRemote != null) {
                bestRemote = if (bestRemote == null) {
                    userRemote
                } else {
                    resolveStreakMerge(bestRemote, userRemote, LocalDate.now()).merged
                }
            }
        }

        val effectiveRemote = bestRemote ?: StreakState()
        val decision = resolveStreakMerge(local, effectiveRemote, LocalDate.now(), localPendingSync = pending)
        val merged = decision.merged
        val needsStudentDocMigration = studentDoc != null &&
            (studentRemote == null || studentRemote.lastStreakDate.isBlank()) &&
            (merged.streakCount > 0 || merged.lastStreakDate.isNotBlank())

        if (decision.shouldUpdateLocal) {
            persistState(merged, markPendingSync = decision.shouldPushToCloud || needsStudentDocMigration, syncCloudNow = false)
            runCatching { WidgetUpdateManager.updateAllWidgets(appContext) }
        }

        if (decision.shouldPushToCloud || needsStudentDocMigration) {
            val pushed = pushStateToFirestoreSuspend(uid, merged)
            if (pushed) {
                prefs.edit().putBoolean(KEY_PENDING_SYNC, false).apply()
            }
        } else if (pending) {
            prefs.edit().putBoolean(KEY_PENDING_SYNC, false).apply()
        }

        merged
    }

    /**
     * Uploads any locally queued offline streak updates during [SyncWorker] outbound push phase.
     * Returns 1 if uploaded, 0 otherwise.
     */
    suspend fun pushPendingToCloudIfNeeded(): Int = withContext(Dispatchers.IO) {
        val pending = prefs.getBoolean(KEY_PENDING_SYNC, false)
        val state = readStateFromPrefs()
        if (!pending) return@withContext 0
        if (state.streakCount <= 0 && state.lastStreakDate.isBlank() && state.completedDates.isEmpty()) {
            prefs.edit().putBoolean(KEY_PENDING_SYNC, false).apply()
            return@withContext 0
        }
        val user = GoogleServicesHelper.getCurrentUser(context = appContext)
            ?.takeUnless { it.isAnonymous }
            ?: return@withContext 0
        val uid = user.uid.takeIf { it.isNotBlank() && it != "student_default" }
            ?: return@withContext 0

        val ok = pushStateToFirestoreSuspend(uid, state)
        if (ok) {
            prefs.edit().putBoolean(KEY_PENDING_SYNC, false).apply()
            1
        } else {
            0
        }
    }

    /**
     * Clears local Water Streak state when signing out so switching accounts never leaks streaks.
     */
    fun clearLocalForLogout() {
        prefs.edit()
            .remove(KEY_STREAK_COUNT)
            .remove(KEY_FREEZE_AVAILABLE)
            .remove(KEY_LAST_DATE)
            .remove(KEY_COMPLETED_DATES)
            .remove(KEY_UPDATED_AT)
            .remove(KEY_PENDING_SYNC)
            .apply()
        getOrInitSharedFlow(appContext).value = StreakState()
    }

    private fun persistState(
        state: StreakState,
        markPendingSync: Boolean,
        syncCloudNow: Boolean
    ) {
        val trimmedDates = trimRecentIsoDates(
            state.completedDates + listOfNotNull(state.lastStreakDate.takeIf { it.isNotBlank() })
        )
        val normalized = state.copy(completedDates = trimmedDates)
        prefs.edit()
            .putInt(KEY_STREAK_COUNT, normalized.streakCount)
            .putInt(KEY_FREEZE_AVAILABLE, normalized.streakFreezeAvailable)
            .putString(KEY_LAST_DATE, normalized.lastStreakDate)
            .putStringSet(KEY_COMPLETED_DATES, trimmedDates)
            .putLong(KEY_UPDATED_AT, normalized.updatedAtMillis)
            .putBoolean(KEY_PENDING_SYNC, markPendingSync)
            .apply()
        getOrInitSharedFlow(appContext).value = normalized
        if (syncCloudNow) {
            syncToFirestoreFireAndForget(normalized)
        }
    }

    private fun computeDaysSince(lastDateStr: String, today: LocalDate): Int {
        return computeDaysSinceDate(parseIsoDateOrNull(lastDateStr), today)
    }

    private fun buildCloudPayload(state: StreakState): Map<String, Any> {
        val ts = if (state.updatedAtMillis > 0L) state.updatedAtMillis else System.currentTimeMillis()
        val recentDates = trimRecentIsoDates(
            state.completedDates + listOfNotNull(state.lastStreakDate.takeIf { it.isNotBlank() })
        ).sortedDescending()
        return hashMapOf(
            "streakCount" to state.streakCount,
            "streakDays" to state.streakCount,
            "streakFreezeAvailable" to state.streakFreezeAvailable,
            "lastStreakDate" to state.lastStreakDate,
            "waterStreakCompletedDates" to recentDates,
            "streakUpdatedAt" to ts
        )
    }

    private fun syncToFirestoreFireAndForget(state: StreakState) {
        val user = GoogleServicesHelper.getCurrentUser(context = appContext)
            ?.takeUnless { it.isAnonymous }
            ?: return
        val uid = user.uid.takeIf { it.isNotBlank() && it != "student_default" } ?: return
        val db = GoogleServicesHelper.getFirebaseFirestore(appContext) ?: run {
            SyncWorker.enqueueImmediateSync(appContext)
            return
        }

        val data = buildCloudPayload(state)
        db.collection("students").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                val latestTs = prefs.getLong(KEY_UPDATED_AT, 0L)
                if (latestTs <= state.updatedAtMillis) {
                    prefs.edit().putBoolean(KEY_PENDING_SYNC, false).apply()
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to sync water streak to students/$uid (will retry via SyncWorker): ${e.message}")
                SyncWorker.enqueueImmediateSync(appContext)
            }

        db.collection("users").document(uid)
            .set(data, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to mirror water streak to users/$uid: ${e.message}")
            }
    }

    private suspend fun pushStateToFirestoreSuspend(uid: String, state: StreakState): Boolean {
        val db = GoogleServicesHelper.getFirebaseFirestore(appContext) ?: return false
        val data = buildCloudPayload(state)
        return try {
            db.collection("students").document(uid).set(data, SetOptions.merge()).await()
            runCatching {
                db.collection("users").document(uid).set(data, SetOptions.merge()).await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Suspended water streak push failed: ${e.message}")
            false
        }
    }

    private fun extractRemoteState(doc: DocumentSnapshot): StreakState? {
        val hasStreakField = doc.contains("streakCount") ||
            doc.contains("streakDays") ||
            doc.contains("lastStreakDate") ||
            doc.contains("streakFreezeAvailable") ||
            doc.contains("waterStreakCompletedDates")
        if (!hasStreakField) return null

        val countFromPrimary = readIntField(doc, "streakCount")
        val countFromLegacy = readIntField(doc, "streakDays")
        val count = maxOf(countFromPrimary ?: 0, countFromLegacy ?: 0).coerceAtLeast(0)
        val freeze = (readIntField(doc, "streakFreezeAvailable") ?: 0).coerceIn(0, 3)
        val lastDate = doc.getString("lastStreakDate")?.trim().orEmpty()
        val updatedAt = (readLongField(doc, "streakUpdatedAt") ?: 0L).coerceAtLeast(0L)
        val rawDates = (doc.get("waterStreakCompletedDates") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }
            .orEmpty()
        val dates = trimRecentIsoDates(rawDates + listOfNotNull(lastDate.takeIf { it.isNotBlank() }))

        if (count == 0 && freeze == 0 && lastDate.isBlank() && dates.isEmpty()) return null
        return StreakState(
            streakCount = count,
            streakFreezeAvailable = freeze,
            lastStreakDate = lastDate,
            daysSinceLastStudy = computeDaysSinceDate(parseIsoDateOrNull(lastDate), LocalDate.now()),
            completedDates = dates,
            updatedAtMillis = updatedAt
        )
    }

    private fun readIntField(doc: DocumentSnapshot, field: String): Int? {
        val raw = doc.get(field) ?: return null
        return (raw as? Number)?.toInt() ?: raw.toString().toIntOrNull()
    }

    private fun readLongField(doc: DocumentSnapshot, field: String): Long? {
        val raw = doc.get(field) ?: return null
        return (raw as? Number)?.toLong() ?: raw.toString().toLongOrNull()
    }

    companion object {
        private const val TAG = "StreakRepository"
        private const val PREFS_NAME = "streak_prefs"
        private const val KEY_STREAK_COUNT = "streakCount"
        private const val KEY_FREEZE_AVAILABLE = "streakFreezeAvailable"
        private const val KEY_LAST_DATE = "lastStreakDate"
        private const val KEY_COMPLETED_DATES = "waterStreakCompletedDates"
        private const val KEY_UPDATED_AT = "streakUpdatedAt"
        private const val KEY_PENDING_SYNC = "streakPendingSync"
        const val MIN_DAILY_STREAK_MINUTES = 20
        const val MIN_DAILY_STREAK_QUESTIONS = 20

        @Volatile
        private var sharedFlow: MutableStateFlow<StreakState>? = null

        private fun getOrInitSharedFlow(appContext: Context): MutableStateFlow<StreakState> {
            return sharedFlow ?: synchronized(this) {
                sharedFlow ?: MutableStateFlow(StreakState()).also { sharedFlow = it }
            }
        }
    }
}
