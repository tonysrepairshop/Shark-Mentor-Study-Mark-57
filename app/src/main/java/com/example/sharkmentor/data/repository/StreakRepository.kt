package com.example.sharkmentor.data.repository

import android.content.Context
import android.util.Log
import com.example.sharkmentor.core.badges.SharkBadgeCatalog
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

/**
 * Duolingo-style consecutive-day Streak Goal tiers ("Hedef Seri").
 * Longer consecutive streaks yield super-linearly larger League bonuses and Streak Freezes.
 */
enum class StreakGoalTier(
    val targetDays: Int,
    val title: String,
    val badgeTitle: String,
    val leaguePointsReward: Int,
    val weeklyLeagueBonusSeconds: Long,
    val freezeReward: Int
) {
    DAYS_7(
        targetDays = 7,
        title = "7 Günlük Seri",
        badgeTitle = "Haftalık Akıntı",
        leaguePointsReward = 150,
        weeklyLeagueBonusSeconds = 900L,   // +15 dk haftalık lig bonusu
        freezeReward = 1
    ),
    DAYS_14(
        targetDays = 14,
        title = "14 Günlük Seri",
        badgeTitle = "Dalga Ustası",
        leaguePointsReward = 350,
        weeklyLeagueBonusSeconds = 1800L,  // +30 dk haftalık lig bonusu
        freezeReward = 2
    ),
    DAYS_30(
        targetDays = 30,
        title = "30 Günlük Seri",
        badgeTitle = "Derin Deniz Şampiyonu",
        leaguePointsReward = 900,
        weeklyLeagueBonusSeconds = 3600L,  // +60 dk haftalık lig bonusu
        freezeReward = 3
    ),
    DAYS_50(
        targetDays = 50,
        title = "50 Günlük Seri",
        badgeTitle = "Efsane Megalodon",
        leaguePointsReward = 2000,
        weeklyLeagueBonusSeconds = 5400L,  // +90 dk haftalık lig bonusu
        freezeReward = 5
    );

    companion object {
        val ALL_TIERS: List<StreakGoalTier> = entries.sortedBy { it.targetDays }
        val VALID_TARGET_DAYS: Set<Int> = ALL_TIERS.map { it.targetDays }.toSet()

        fun fromTargetDays(days: Int): StreakGoalTier =
            ALL_TIERS.firstOrNull { it.targetDays == days } ?: DAYS_7

        fun sanitizeGoalDays(days: Int): Int =
            if (days in VALID_TARGET_DAYS) days else DAYS_7.targetDays

        fun nextRecommendedGoal(currentStreak: Int, claimedMilestones: Set<Int>): StreakGoalTier {
            return ALL_TIERS.firstOrNull { tier ->
                tier.targetDays !in claimedMilestones || currentStreak < tier.targetDays
            } ?: DAYS_50
        }
    }
}

data class StreakGoalEvaluationResult(
    val newlyUnlockedTiers: List<StreakGoalTier>,
    val totalLeaguePointsEarned: Int,
    val totalWeeklyBonusSecondsEarned: Long,
    val totalFreezesEarned: Int,
    val updatedClaimedMilestones: Set<Int>,
    val nextGoalDays: Int
)

/**
 * Pure JVM-testable evaluator for Streak Goal completion and progressive rewards.
 *
 * Rules:
 * - If the student's active goal (or any lower/selected tier up to the active goal) is reached
 *   by [currentStreak] and hasn't been claimed yet in [claimedMilestones], it is unlocked once.
 * - Longer goals scale non-linearly in both League Points and Streak Freezes.
 * - Strictly idempotent: claiming the same milestone twice returns empty rewards.
 * - Auto-advances [nextGoalDays] to the next unclaimed tier (`7 -> 14 -> 30 -> 50`).
 */
fun evaluateStreakGoalMilestone(
    currentStreak: Int,
    selectedGoalDays: Int,
    claimedMilestones: Set<Int>
): StreakGoalEvaluationResult {
    val safeGoal = StreakGoalTier.sanitizeGoalDays(selectedGoalDays)
    val safeClaimed = claimedMilestones.filter { it in StreakGoalTier.VALID_TARGET_DAYS }.toSet()
    if (currentStreak <= 0) {
        return StreakGoalEvaluationResult(
            newlyUnlockedTiers = emptyList(),
            totalLeaguePointsEarned = 0,
            totalWeeklyBonusSecondsEarned = 0L,
            totalFreezesEarned = 0,
            updatedClaimedMilestones = safeClaimed,
            nextGoalDays = safeGoal
        )
    }

    // Unlock the active selected goal (and any crossed lower tier not yet claimed) once reached
    val unlocked = StreakGoalTier.ALL_TIERS.filter { tier ->
        tier.targetDays <= safeGoal &&
            currentStreak >= tier.targetDays &&
            tier.targetDays !in safeClaimed
    }

    if (unlocked.isEmpty()) {
        return StreakGoalEvaluationResult(
            newlyUnlockedTiers = emptyList(),
            totalLeaguePointsEarned = 0,
            totalWeeklyBonusSecondsEarned = 0L,
            totalFreezesEarned = 0,
            updatedClaimedMilestones = safeClaimed,
            nextGoalDays = safeGoal
        )
    }

    val updatedClaimed = safeClaimed + unlocked.map { it.targetDays }
    val nextGoal = StreakGoalTier.ALL_TIERS
        .firstOrNull { it.targetDays > safeGoal && it.targetDays !in updatedClaimed }
        ?.targetDays
        ?: StreakGoalTier.ALL_TIERS.firstOrNull { it.targetDays !in updatedClaimed }?.targetDays
        ?: safeGoal

    return StreakGoalEvaluationResult(
        newlyUnlockedTiers = unlocked,
        totalLeaguePointsEarned = unlocked.sumOf { it.leaguePointsReward },
        totalWeeklyBonusSecondsEarned = unlocked.sumOf { it.weeklyLeagueBonusSeconds },
        totalFreezesEarned = unlocked.sumOf { it.freezeReward },
        updatedClaimedMilestones = updatedClaimed,
        nextGoalDays = nextGoal
    )
}

data class StreakState(
    val streakCount: Int = 0,
    val streakFreezeAvailable: Int = 0,
    val lastStreakDate: String = "",
    val wasFrozenToday: Boolean = false,
    val daysSinceLastStudy: Int = 0,
    val completedDates: Set<String> = emptySet(),
    val updatedAtMillis: Long = 0L,
    val streakGoalDays: Int = StreakGoalTier.DAYS_7.targetDays,
    val claimedGoalMilestones: Set<Int> = emptySet(),
    val lastRewardedGoalDays: Int = 0
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
 * - A fresh device / reinstall restores the user's exact streak, freezes, last date,
 *   active streak goal (`7/14/30/50`), claimed milestones, and rolling 14-day completed-date history.
 * - Cross-device concurrent study picks the most recent active streak date, never regresses
 *   streakCount on the same day, unions completedDates & claimedGoalMilestones, and resolves
 *   goal/freeze changes via updatedAtMillis.
 */
fun resolveStreakMerge(
    local: StreakState,
    remote: StreakState,
    today: LocalDate = LocalDate.now(),
    localPendingSync: Boolean = false
): StreakMergeDecision {
    val mergedDates = trimRecentIsoDates(local.completedDates + remote.completedDates)
    val mergedClaimed = (local.claimedGoalMilestones + remote.claimedGoalMilestones)
        .filter { it in StreakGoalTier.VALID_TARGET_DAYS }
        .toSet()
    val resolvedGoalDays = when {
        remote.updatedAtMillis > local.updatedAtMillis -> StreakGoalTier.sanitizeGoalDays(remote.streakGoalDays)
        local.updatedAtMillis > remote.updatedAtMillis -> StreakGoalTier.sanitizeGoalDays(local.streakGoalDays)
        else -> maxOf(
            StreakGoalTier.sanitizeGoalDays(local.streakGoalDays),
            StreakGoalTier.sanitizeGoalDays(remote.streakGoalDays)
        )
    }

    val localDate = parseIsoDateOrNull(local.lastStreakDate)
    val remoteDate = parseIsoDateOrNull(remote.lastStreakDate)

    val localHasData = local.streakCount > 0 || localDate != null || local.completedDates.isNotEmpty() ||
        local.claimedGoalMilestones.isNotEmpty() || local.streakGoalDays != StreakGoalTier.DAYS_7.targetDays
    val remoteHasData = remote.streakCount > 0 || remoteDate != null || remote.completedDates.isNotEmpty() ||
        remote.claimedGoalMilestones.isNotEmpty() || remote.streakGoalDays != StreakGoalTier.DAYS_7.targetDays

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
            daysSinceLastStudy = computeDaysSinceDate(remoteDate, today),
            streakGoalDays = StreakGoalTier.sanitizeGoalDays(remote.streakGoalDays),
            claimedGoalMilestones = mergedClaimed,
            lastRewardedGoalDays = local.lastRewardedGoalDays
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
            streakGoalDays = StreakGoalTier.sanitizeGoalDays(local.streakGoalDays),
            claimedGoalMilestones = mergedClaimed,
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
            val resolvedCount = if (
                ChronoUnit.DAYS.between(localDate, remoteDate) == 1L &&
                remote.streakCount < local.streakCount + 1 &&
                local.streakCount > 0
            ) {
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
        updatedAtMillis = maxOf(winner.updatedAtMillis, local.updatedAtMillis, remote.updatedAtMillis),
        streakGoalDays = resolvedGoalDays,
        claimedGoalMilestones = mergedClaimed,
        lastRewardedGoalDays = local.lastRewardedGoalDays
    )

    val localChanged = finalMerged.streakCount != local.streakCount ||
        finalMerged.streakFreezeAvailable != local.streakFreezeAvailable ||
        finalMerged.lastStreakDate != local.lastStreakDate ||
        finalMerged.completedDates != local.completedDates ||
        finalMerged.streakGoalDays != local.streakGoalDays ||
        finalMerged.claimedGoalMilestones != local.claimedGoalMilestones

    val cloudDiffers = finalMerged.streakCount != remote.streakCount ||
        finalMerged.streakFreezeAvailable != remote.streakFreezeAvailable ||
        finalMerged.lastStreakDate != remote.lastStreakDate ||
        finalMerged.completedDates != remote.completedDates ||
        finalMerged.streakGoalDays != remote.streakGoalDays ||
        finalMerged.claimedGoalMilestones != remote.claimedGoalMilestones

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
        val goalDays = StreakGoalTier.sanitizeGoalDays(
            prefs.getInt(KEY_GOAL_DAYS, StreakGoalTier.DAYS_7.targetDays)
        )
        val claimedSet = prefs.getStringSet(KEY_CLAIMED_MILESTONES, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .filter { it in StreakGoalTier.VALID_TARGET_DAYS }
            .toSet()
        return StreakState(
            streakCount = prefs.getInt(KEY_STREAK_COUNT, 0),
            streakFreezeAvailable = prefs.getInt(KEY_FREEZE_AVAILABLE, 0).coerceIn(0, MAX_STREAK_FREEZES),
            lastStreakDate = lastDateStr,
            daysSinceLastStudy = daysSince,
            completedDates = combinedDates,
            updatedAtMillis = prefs.getLong(KEY_UPDATED_AT, 0L),
            streakGoalDays = goalDays,
            claimedGoalMilestones = claimedSet,
            lastRewardedGoalDays = prefs.getInt(KEY_LAST_REWARDED_GOAL, 0)
        )
    }

    /**
     * Allows the student to pick their active Duolingo-style Streak Goal (`7`, `14`, `30`, or `50` gün).
     * If their current consecutive streak already satisfies the newly picked unclaimed goal,
     * immediately awards the tiered League Points + Streak Freezes once (idempotent).
     */
    fun setStreakGoalDays(targetDays: Int): StreakGoalEvaluationResult {
        val safeTarget = StreakGoalTier.sanitizeGoalDays(targetDays)
        val current = readStateFromPrefs()
        val eval = evaluateStreakGoalMilestone(
            currentStreak = current.streakCount,
            selectedGoalDays = safeTarget,
            claimedMilestones = current.claimedGoalMilestones
        )
        val newFreeze = (current.streakFreezeAvailable + eval.totalFreezesEarned)
            .coerceIn(0, MAX_STREAK_FREEZES)
        val rewardedGoal = eval.newlyUnlockedTiers.maxOfOrNull { it.targetDays } ?: current.lastRewardedGoalDays
        val updatedState = current.copy(
            streakFreezeAvailable = newFreeze,
            streakGoalDays = if (eval.newlyUnlockedTiers.isNotEmpty()) eval.nextGoalDays else safeTarget,
            claimedGoalMilestones = eval.updatedClaimedMilestones,
            lastRewardedGoalDays = rewardedGoal,
            updatedAtMillis = System.currentTimeMillis()
        )
        if (eval.newlyUnlockedTiers.isNotEmpty()) {
            grantLeagueGoalRewards(eval)
        }
        persistState(updatedState, markPendingSync = true, syncCloudNow = true)
        return eval
    }

    fun acknowledgeGoalReward() {
        val current = readStateFromPrefs()
        if (current.lastRewardedGoalDays == 0) return
        prefs.edit().putInt(KEY_LAST_REWARDED_GOAL, 0).apply()
        getOrInitSharedFlow(appContext).value = current.copy(lastRewardedGoalDays = 0)
    }

    private fun grantLeagueGoalRewards(eval: StreakGoalEvaluationResult) {
        if (eval.totalLeaguePointsEarned <= 0 && eval.totalWeeklyBonusSecondsEarned <= 0L) return
        runCatching {
            if (eval.totalLeaguePointsEarned > 0) {
                SharkBadgeCatalog.addMentorPoints(appContext, eval.totalLeaguePointsEarned)
            }
            if (eval.totalWeeklyBonusSecondsEarned > 0L) {
                SharkBadgeCatalog.addWeeklyBonusSeconds(appContext, eval.totalWeeklyBonusSecondsEarned)
            }
            val uid = GoogleServicesHelper.getCurrentUser(context = appContext)
                ?.takeUnless { it.isAnonymous }
                ?.uid
            if (!uid.isNullOrBlank() && uid != "student_default") {
                SharkBadgeCatalog.syncShowcaseAndBadgesToCloud(
                    context = appContext,
                    uid = uid,
                    forceImmediate = true
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed crediting streak goal league rewards: ${e.message}")
        }
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
            var streakWasBroken = false

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
                                streakWasBroken = true
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

            // Base automatic freeze bonus every 7 continuous days if not covered by a goal milestone
            val activeClaimed = if (streakWasBroken) emptySet() else state.claimedGoalMilestones
            val activeGoal = if (streakWasBroken) StreakGoalTier.DAYS_7.targetDays else state.streakGoalDays
            val goalEval = evaluateStreakGoalMilestone(
                currentStreak = newStreak,
                selectedGoalDays = activeGoal,
                claimedMilestones = activeClaimed
            )

            if (goalEval.newlyUnlockedTiers.isNotEmpty()) {
                newFreeze = (newFreeze + goalEval.totalFreezesEarned).coerceIn(0, MAX_STREAK_FREEZES)
                grantLeagueGoalRewards(goalEval)
            } else if (newStreak > 0 && newStreak % 7 == 0 && newFreeze < MAX_STREAK_FREEZES) {
                newFreeze += 1
            }

            val rewardedGoal = goalEval.newlyUnlockedTiers.maxOfOrNull { it.targetDays } ?: state.lastRewardedGoalDays
            val newState = StreakState(
                streakCount = newStreak,
                streakFreezeAvailable = newFreeze,
                lastStreakDate = todayStr,
                wasFrozenToday = wasFrozen,
                daysSinceLastStudy = 0,
                completedDates = trimRecentIsoDates(state.completedDates + todayStr),
                updatedAtMillis = System.currentTimeMillis(),
                streakGoalDays = goalEval.nextGoalDays,
                claimedGoalMilestones = goalEval.updatedClaimedMilestones,
                lastRewardedGoalDays = rewardedGoal
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
        var streakWasBroken = false
        if (newlyCompletedToday && existingLastDate != null && daysGapFromExisting > 1L) {
            val missedDays = (daysGapFromExisting - 1L).toInt()
            if (resolvedFreeze >= missedDays) {
                resolvedFreeze -= missedDays
                wasFrozen = true
            } else {
                resolvedFreeze = 0
                streakWasBroken = true
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

        val activeClaimed = if (streakWasBroken || finalStreak == 0) emptySet() else existing.claimedGoalMilestones
        val activeGoal = if (streakWasBroken) StreakGoalTier.DAYS_7.targetDays else existing.streakGoalDays
        val goalEval = evaluateStreakGoalMilestone(
            currentStreak = finalStreak,
            selectedGoalDays = activeGoal,
            claimedMilestones = activeClaimed
        )

        if (goalEval.newlyUnlockedTiers.isNotEmpty()) {
            resolvedFreeze = (resolvedFreeze + goalEval.totalFreezesEarned).coerceIn(0, MAX_STREAK_FREEZES)
            grantLeagueGoalRewards(goalEval)
        } else if (newlyCompletedToday && finalStreak > 0 && finalStreak % 7 == 0 && resolvedFreeze < MAX_STREAK_FREEZES) {
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

        val rewardedGoal = goalEval.newlyUnlockedTiers.maxOfOrNull { it.targetDays } ?: existing.lastRewardedGoalDays
        val changed = finalStreak != existing.streakCount ||
            resolvedFreeze != existing.streakFreezeAvailable ||
            finalLastDateStr != existing.lastStreakDate ||
            mergedIsoDates != existing.completedDates ||
            goalEval.nextGoalDays != existing.streakGoalDays ||
            goalEval.updatedClaimedMilestones != existing.claimedGoalMilestones ||
            rewardedGoal != existing.lastRewardedGoalDays

        val resolvedState = StreakState(
            streakCount = finalStreak,
            streakFreezeAvailable = resolvedFreeze,
            lastStreakDate = finalLastDateStr,
            wasFrozenToday = wasFrozen,
            daysSinceLastStudy = daysSince,
            completedDates = mergedIsoDates,
            updatedAtMillis = if (changed) System.currentTimeMillis() else existing.updatedAtMillis,
            streakGoalDays = goalEval.nextGoalDays,
            claimedGoalMilestones = goalEval.updatedClaimedMilestones,
            lastRewardedGoalDays = rewardedGoal
        )

        if (changed) {
            val shouldPush = newlyCompletedToday ||
                goalEval.newlyUnlockedTiers.isNotEmpty() ||
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
     * Applies a live Firestore `students/{uid}` snapshot without blocking the UI.
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
     * 1. Reads Water Streak & Streak Goal from canonical `students/{uid}` (reusing [prefetchedStudentDoc]
     *    when available) with one-shot migration fallback to `users/{uid}` for pre-Mark-61 users.
     * 2. Merges local + remote losslessly via [resolveStreakMerge].
     * 3. Updates local storage & widgets if remote was newer, and pushes ONLY to canonical `students/{uid}`
     *    (50% fewer Firestore writes than dual-writing users/{uid}).
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
        if (state.streakCount <= 0 &&
            state.lastStreakDate.isBlank() &&
            state.completedDates.isEmpty() &&
            state.claimedGoalMilestones.isEmpty() &&
            state.streakGoalDays == StreakGoalTier.DAYS_7.targetDays
        ) {
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
            .remove(KEY_GOAL_DAYS)
            .remove(KEY_CLAIMED_MILESTONES)
            .remove(KEY_LAST_REWARDED_GOAL)
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
        val safeClaimed = state.claimedGoalMilestones.filter { it in StreakGoalTier.VALID_TARGET_DAYS }.toSet()
        val normalized = state.copy(
            streakFreezeAvailable = state.streakFreezeAvailable.coerceIn(0, MAX_STREAK_FREEZES),
            completedDates = trimmedDates,
            streakGoalDays = StreakGoalTier.sanitizeGoalDays(state.streakGoalDays),
            claimedGoalMilestones = safeClaimed
        )
        prefs.edit()
            .putInt(KEY_STREAK_COUNT, normalized.streakCount)
            .putInt(KEY_FREEZE_AVAILABLE, normalized.streakFreezeAvailable)
            .putString(KEY_LAST_DATE, normalized.lastStreakDate)
            .putStringSet(KEY_COMPLETED_DATES, trimmedDates)
            .putLong(KEY_UPDATED_AT, normalized.updatedAtMillis)
            .putInt(KEY_GOAL_DAYS, normalized.streakGoalDays)
            .putStringSet(KEY_CLAIMED_MILESTONES, safeClaimed.map { it.toString() }.toSet())
            .putInt(KEY_LAST_REWARDED_GOAL, normalized.lastRewardedGoalDays)
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
        val claimedSorted = state.claimedGoalMilestones
            .filter { it in StreakGoalTier.VALID_TARGET_DAYS }
            .sorted()
        return hashMapOf(
            "streakCount" to state.streakCount,
            "streakDays" to state.streakCount,
            "streakFreezeAvailable" to state.streakFreezeAvailable.coerceIn(0, MAX_STREAK_FREEZES),
            "lastStreakDate" to state.lastStreakDate,
            "waterStreakCompletedDates" to recentDates,
            "streakGoalDays" to StreakGoalTier.sanitizeGoalDays(state.streakGoalDays),
            "claimedStreakGoalMilestones" to claimedSorted,
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

        // Single canonical write to students/{uid} (50% fewer Firestore writes; no redundant users/{uid} mirror)
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
    }

    private suspend fun pushStateToFirestoreSuspend(uid: String, state: StreakState): Boolean {
        val db = GoogleServicesHelper.getFirebaseFirestore(appContext) ?: return false
        val data = buildCloudPayload(state)
        return try {
            db.collection("students").document(uid).set(data, SetOptions.merge()).await()
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
            doc.contains("waterStreakCompletedDates") ||
            doc.contains("streakGoalDays") ||
            doc.contains("claimedStreakGoalMilestones")
        if (!hasStreakField) return null

        val countFromPrimary = readIntField(doc, "streakCount")
        val countFromLegacy = readIntField(doc, "streakDays")
        val count = maxOf(countFromPrimary ?: 0, countFromLegacy ?: 0).coerceAtLeast(0)
        val freeze = (readIntField(doc, "streakFreezeAvailable") ?: 0).coerceIn(0, MAX_STREAK_FREEZES)
        val lastDate = doc.getString("lastStreakDate")?.trim().orEmpty()
        val updatedAt = (readLongField(doc, "streakUpdatedAt") ?: 0L).coerceAtLeast(0L)
        val rawDates = (doc.get("waterStreakCompletedDates") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }
            .orEmpty()
        val dates = trimRecentIsoDates(rawDates + listOfNotNull(lastDate.takeIf { it.isNotBlank() }))
        val goalDays = StreakGoalTier.sanitizeGoalDays(
            readIntField(doc, "streakGoalDays") ?: StreakGoalTier.DAYS_7.targetDays
        )
        val claimed = (doc.get("claimedStreakGoalMilestones") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull() }
            ?.filter { it in StreakGoalTier.VALID_TARGET_DAYS }
            ?.toSet()
            .orEmpty()

        if (count == 0 && freeze == 0 && lastDate.isBlank() && dates.isEmpty() && claimed.isEmpty() &&
            goalDays == StreakGoalTier.DAYS_7.targetDays
        ) {
            return null
        }
        return StreakState(
            streakCount = count,
            streakFreezeAvailable = freeze,
            lastStreakDate = lastDate,
            daysSinceLastStudy = computeDaysSinceDate(parseIsoDateOrNull(lastDate), LocalDate.now()),
            completedDates = dates,
            updatedAtMillis = updatedAt,
            streakGoalDays = goalDays,
            claimedGoalMilestones = claimed
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
        private const val KEY_GOAL_DAYS = "streakGoalDays"
        private const val KEY_CLAIMED_MILESTONES = "claimedStreakGoalMilestones"
        private const val KEY_LAST_REWARDED_GOAL = "lastRewardedStreakGoalDays"
        const val MIN_DAILY_STREAK_MINUTES = 20
        const val MIN_DAILY_STREAK_QUESTIONS = 20
        const val MAX_STREAK_FREEZES = 15

        @Volatile
        private var sharedFlow: MutableStateFlow<StreakState>? = null

        private fun getOrInitSharedFlow(appContext: Context): MutableStateFlow<StreakState> {
            return sharedFlow ?: synchronized(this) {
                sharedFlow ?: MutableStateFlow(StreakState()).also { sharedFlow = it }
            }
        }
    }
}
