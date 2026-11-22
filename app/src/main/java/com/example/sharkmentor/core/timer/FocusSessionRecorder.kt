package com.example.sharkmentor.core.timer

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.widget.WidgetUpdateManager
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.FocusSessionEntity
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.service.sync.SyncWorker
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single-row checkpoint & cloud recording engine for active focus sessions (especially `STOPWATCH`)
 * and external study tools (e.g., PACE, KEEP).
 *
 * Guarantees:
 * 1. Generates a unique [activeSessionId] (UUID) synchronously at session start and checkpoints state in SharedPreferences.
 * 2. Every **20 minutes** ([LOCAL_CHECKPOINT_INTERVAL_MS] = 1,200,000 ms), upserts the accumulated duration
 *    into Room via `SessionDao.upsertSession` on the **exact same row** (`isSynced = false`), never opening
 *    duplicate rows for the same session. Local persistence NEVER blocks on network I/O.
 * 3. Every **60 minutes** ([CLOUD_SYNC_INTERVAL_MS] = 3,600,000 ms), if internet is available, asynchronously
 *    attempts to sync the session to Firestore (`students/{uid}/sessions/{uuid}`) with a strict timeout.
 *    If offline or timed out, leaves `isSynced = false` locally so [SyncWorker] pushes automatically when connected,
 *    while 20-minute local saves continue completely uninterrupted.
 * 4. On service pause, stop, reset, or crash recovery on restart, finalizes the exact same session row without
 *    double-counting (`cloudSyncedSecondsByUuid` tracks incremental `totalFocusSeconds` deltas).
 */
object FocusSessionRecorder {

    private const val TAG = "FocusSessionRecorder"
    private const val PREFS_NAME = "shark_focus_session_checkpoint_prefs"

    /** 20 minutes in milliseconds (1,200,000 ms) for local Room single-row upsert. */
    const val LOCAL_CHECKPOINT_INTERVAL_MS: Long = 20L * 60L * 1_000L

    /** 60 minutes in milliseconds (3,600,000 ms) for cloud Firestore sync. */
    const val CLOUD_SYNC_INTERVAL_MS: Long = 60L * 60L * 1_000L

    /** Minimum duration (60 seconds) required to persist a session that hasn't hit a 20-min checkpoint. */
    const val MIN_RECORDABLE_DURATION_MS: Long = 60_000L

    /** If a paused session sits idle for >= 20 minutes (or crosses midnight) before resuming, seal it and start a fresh session row. */
    const val PAUSE_SPLIT_THRESHOLD_MS: Long = 20L * 60L * 1_000L

    /** Lightweight SharedPreferences checkpoint flush interval (15 seconds) for crash resilience. */
    private const val PREFS_HEARTBEAT_INTERVAL_MS: Long = 15_000L

    /** Hard timeout for Firestore writes so offline/flaky networks can NEVER stall recording. */
    private const val CLOUD_SYNC_TIMEOUT_MS: Long = 12_000L

    private const val KEY_ACTIVE_SESSION_UUID = "active_session_uuid"
    private const val KEY_ACTIVE_ROW_ID = "active_row_id"
    private const val KEY_MODE_NAME = "mode_name"
    private const val KEY_SUBJECT_NAME = "subject_name"
    private const val KEY_STARTED_AT_MILLIS = "started_at_millis"
    private const val KEY_LAST_ACTIVE_WALL_MILLIS = "last_active_wall_millis"
    private const val KEY_SESSION_ELAPSED_OFFSET_MS = "session_elapsed_offset_ms"
    private const val KEY_LAST_KNOWN_ELAPSED_MS = "last_known_elapsed_ms"
    private const val KEY_LAST_LOCAL_SAVE_ELAPSED_MS = "last_local_save_elapsed_ms"
    private const val KEY_LAST_CLOUD_SYNC_ELAPSED_MS = "last_cloud_sync_elapsed_ms"
    private const val KEY_LAST_CLOUD_SYNCED_SECONDS = "last_cloud_synced_seconds"
    private const val KEY_PHONE_LIFT_COUNT = "phone_lift_count"
    private const val KEY_SCREEN_ON_MILLIS = "screen_on_millis"
    private const val KEY_IS_FINALIZED = "is_finalized"

    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cloudSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Protects ONLY fast local SharedPreferences + Room operations. Never held across network calls. */
    private val localMutex = Mutex()

    /** Serializes cloud Firestore delta pushes independently from local checkpoints. */
    private val cloudMutex = Mutex()

    /** Tracks how many seconds of a given session UUID have already been incremented on Firestore. */
    private val cloudSyncedSecondsByUuid = ConcurrentHashMap<String, Long>()

    @Volatile
    private var activeSessionId: String? = null

    @Volatile
    private var activeRowId: Long = 0L

    @Volatile
    private var sessionElapsedOffsetMs: Long = 0L

    @Volatile
    private var lastActiveWallMillis: Long = 0L

    @Volatile
    private var lastPrefsHeartbeatElapsedMs: Long = 0L

    @Volatile
    private var lastLocalSaveElapsedMs: Long = 0L

    @Volatile
    private var lastCloudSyncElapsedMs: Long = 0L

    @Volatile
    private var lastCloudSyncedSeconds: Long = 0L

    @Volatile
    private var isFinalized: Boolean = true

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActiveSessionId(): String? = if (isFinalized) null else activeSessionId

    /**
     * Fast, non-blocking check for active validated internet connectivity.
     * Prevents Firestore SDK `.set().await()` from suspending indefinitely when offline.
     */
    internal fun isNetworkConnected(context: Context): Boolean {
        return runCatching {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)
    }

    /**
     * Pure helper for computing whether local 20-min or cloud 60-min checkpoints are due.
     */
    internal fun computeCheckpointTriggers(
        elapsedMillis: Long,
        lastHeartbeatMs: Long,
        lastLocalSaveMs: Long,
        lastCloudSyncMs: Long
    ): Triple<Boolean, Boolean, Boolean> {
        val shouldHeartbeat = (elapsedMillis - lastHeartbeatMs) >= PREFS_HEARTBEAT_INTERVAL_MS
        val shouldLocal20Min = (elapsedMillis - lastLocalSaveMs) >= LOCAL_CHECKPOINT_INTERVAL_MS
        val shouldCloud60Min = (elapsedMillis - lastCloudSyncMs) >= CLOUD_SYNC_INTERVAL_MS
        return Triple(shouldHeartbeat, shouldLocal20Min, shouldCloud60Min)
    }

    /**
     * Determines whether resuming a paused session should seal the previous study block and start a
     * new session row so separate study sessions across the day/night are never merged into one record.
     */
    internal fun shouldSplitSessionAfterPause(
        lastActiveWallMs: Long,
        resumeWallMs: Long
    ): Boolean {
        if (lastActiveWallMs <= 0L || resumeWallMs <= lastActiveWallMs) return false
        if ((resumeWallMs - lastActiveWallMs) >= PAUSE_SPLIT_THRESHOLD_MS) return true
        val c1 = java.util.Calendar.getInstance().apply { timeInMillis = lastActiveWallMs }
        val c2 = java.util.Calendar.getInstance().apply { timeInMillis = resumeWallMs }
        return c1.get(java.util.Calendar.YEAR) != c2.get(java.util.Calendar.YEAR) ||
            c1.get(java.util.Calendar.DAY_OF_YEAR) != c2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /**
     * Resolves the real wall-clock completion timestamp for a session so an offline session studied earlier
     * is never timestamped at 03:00 AM when the service dies or Wi-Fi reconnects hours later.
     */
    internal fun resolveActualCompletedAtMillis(
        startedAtWallMs: Long,
        lastActiveWallMs: Long,
        segmentDurationMs: Long,
        nowWallMs: Long
    ): Long {
        if (lastActiveWallMs > 0L && (nowWallMs - lastActiveWallMs) > 60_000L) {
            return lastActiveWallMs
        }
        if (startedAtWallMs > 0L && segmentDurationMs > 0L) {
            val estimatedEnd = startedAtWallMs + segmentDurationMs
            if ((nowWallMs - estimatedEnd) > 120_000L) {
                return if (lastActiveWallMs > 0L) minOf(lastActiveWallMs, estimatedEnd) else estimatedEnd
            }
        }
        return if (lastActiveWallMs > 0L) lastActiveWallMs else nowWallMs
    }

    /**
     * Dispatches Firestore sync on an independent scope without ever holding [localMutex].
     */
    private fun dispatchCloudSyncAsync(
        appContext: Context,
        sessionEntity: FocusSessionEntity,
        sessionUuid: String,
        subjectName: String,
        fallbackPreviouslySyncedSeconds: Long,
        isFinal: Boolean
    ) {
        if (!isNetworkConnected(appContext)) {
            SyncWorker.enqueueImmediateSync(appContext)
            Log.i(
                TAG,
                "Offline during cloud sync (uuid=$sessionUuid, final=$isFinal, sec=${sessionEntity.durationSeconds}); kept local isSynced=false & queued SyncWorker"
            )
            return
        }

        cloudSyncScope.launch {
            cloudMutex.withLock {
                val alreadySyncedSec = maxOf(
                    cloudSyncedSecondsByUuid[sessionUuid] ?: 0L,
                    fallbackPreviouslySyncedSeconds
                )
                val syncedOk = runCatching {
                    withTimeoutOrNull(CLOUD_SYNC_TIMEOUT_MS) {
                        syncSessionToFirestoreInternal(
                            context = appContext,
                            sessionEntity = sessionEntity,
                            sessionUuid = sessionUuid,
                            subjectName = subjectName,
                            previouslySyncedSeconds = alreadySyncedSec,
                            isFinal = isFinal
                        )
                    } ?: false
                }.onFailure {
                    Log.w(TAG, "Cloud sync failed (uuid=$sessionUuid, final=$isFinal): ${it.message}")
                }.getOrDefault(false)

                if (syncedOk) {
                    val syncedSec = maxOf(alreadySyncedSec, sessionEntity.durationSeconds)
                    cloudSyncedSecondsByUuid[sessionUuid] = syncedSec
                    if (activeSessionId == sessionUuid) {
                        lastCloudSyncedSeconds = syncedSec
                        prefs(appContext).edit()
                            .putLong(KEY_LAST_CLOUD_SYNCED_SECONDS, syncedSec)
                            .apply()
                    }
                    if (isFinal) {
                        runCatching {
                            SharkMentorDatabase.getDatabase(appContext)
                                .syncDao()
                                .markSessionSynced(sessionEntity.id, sessionUuid)
                        }
                        cloudSyncedSecondsByUuid.remove(sessionUuid)
                    }
                    Log.i(
                        TAG,
                        "Cloud sync succeeded: uuid=$sessionUuid, final=$isFinal, durationSec=${sessionEntity.durationSeconds}"
                    )
                } else {
                    SyncWorker.enqueueImmediateSync(appContext)
                    Log.w(
                        TAG,
                        "Cloud sync deferred (timeout/error): uuid=$sessionUuid, final=$isFinal; 20-min local loop unaffected"
                    )
                }
            }
        }
    }

    /**
     * Recovers and finalizes any orphaned session left in SharedPreferences after an unexpected
     * process death or crash, ensuring the same Room row is updated with its actual last active timestamp
     * (never the later app restart or Wi-Fi connection time).
     */
    fun recoverOrphanedSessionIfNeeded(context: Context, isTimerCurrentlyActive: Boolean) {
        if (isTimerCurrentlyActive || !isFinalized) return
        val appContext = context.applicationContext
        recorderScope.launch {
            var recoveredEntity: FocusSessionEntity? = null
            var recoveredUuid = ""
            var recoveredSubject = "Genel"
            var recoveredSyncedSec = 0L

            localMutex.withLock {
                if (!isFinalized) return@withLock
                val sp = prefs(appContext)
                val wasFinalized = sp.getBoolean(KEY_IS_FINALIZED, true)
                val orphanUuid = sp.getString(KEY_ACTIVE_SESSION_UUID, null)
                if (wasFinalized || orphanUuid.isNullOrBlank()) return@withLock
                if (orphanUuid == activeSessionId) return@withLock

                val orphanRowId = sp.getLong(KEY_ACTIVE_ROW_ID, 0L)
                val orphanElapsedMs = sp.getLong(KEY_LAST_KNOWN_ELAPSED_MS, 0L)
                val orphanStartedAtMs = sp.getLong(KEY_STARTED_AT_MILLIS, 0L)
                val orphanLastActiveWallMs = sp.getLong(KEY_LAST_ACTIVE_WALL_MILLIS, 0L)
                val orphanMode = sp.getString(KEY_MODE_NAME, TimerMode.STOPWATCH.displayName)
                    ?: TimerMode.STOPWATCH.displayName
                val orphanSubject = sp.getString(KEY_SUBJECT_NAME, "Genel") ?: "Genel"
                val orphanLifts = sp.getInt(KEY_PHONE_LIFT_COUNT, 0)
                val orphanScreenOnMs = sp.getLong(KEY_SCREEN_ON_MILLIS, 0L)
                val orphanSyncedSec = sp.getLong(KEY_LAST_CLOUD_SYNCED_SECONDS, 0L)

                val actualCompletedAt = resolveActualCompletedAtMillis(
                    startedAtWallMs = orphanStartedAtMs,
                    lastActiveWallMs = orphanLastActiveWallMs,
                    segmentDurationMs = orphanElapsedMs,
                    nowWallMs = System.currentTimeMillis()
                )

                Log.w(
                    TAG,
                    "Recovering orphaned focus session uuid=$orphanUuid, rowId=$orphanRowId, elapsedMs=$orphanElapsedMs, completedAt=$actualCompletedAt"
                )

                if (orphanElapsedMs >= MIN_RECORDABLE_DURATION_MS || orphanRowId > 0L) {
                    val savedRow = upsertSingleSessionRowInternal(
                        context = appContext,
                        sessionUuid = orphanUuid,
                        existingRowId = orphanRowId,
                        modeName = orphanMode,
                        durationMillis = orphanElapsedMs,
                        phoneLiftCount = orphanLifts,
                        screenOnMillis = orphanScreenOnMs,
                        markSynced = false,
                        completedAtMillis = actualCompletedAt
                    )
                    evaluateWaterStreakAndRefreshWidgets(appContext)
                    recoveredEntity = savedRow
                    recoveredUuid = orphanUuid
                    recoveredSubject = orphanSubject
                    recoveredSyncedSec = orphanSyncedSec
                }

                clearCheckpointPrefs(sp)
            }

            recoveredEntity?.let { entity ->
                dispatchCloudSyncAsync(
                    appContext = appContext,
                    sessionEntity = entity,
                    sessionUuid = recoveredUuid,
                    subjectName = recoveredSubject,
                    fallbackPreviouslySyncedSeconds = recoveredSyncedSec,
                    isFinal = true
                )
            }
        }
    }

    /**
     * Starts a new focus session, generating a unique UUID and initializing in-memory state synchronously
     * so early ticks are never lost. If a previous session was still open, finalizes it locally at its
     * original last active timestamp first.
     */
    fun startSession(
        context: Context,
        modeName: String,
        subjectName: String = "Genel"
    ): String {
        val appContext = context.applicationContext
        val newUuid = UUID.randomUUID().toString()
        val nowWall = System.currentTimeMillis()

        // Synchronously activate the new session before async I/O so no early ticks are dropped
        activeSessionId = newUuid
        activeRowId = 0L
        sessionElapsedOffsetMs = 0L
        lastActiveWallMillis = nowWall
        lastPrefsHeartbeatElapsedMs = 0L
        lastLocalSaveElapsedMs = 0L
        lastCloudSyncElapsedMs = 0L
        lastCloudSyncedSeconds = 0L
        isFinalized = false

        recorderScope.launch {
            var prevEntityToSync: FocusSessionEntity? = null
            var prevUuidToSync = ""
            var prevSubjectToSync = subjectName
            var prevSyncedSecToSync = 0L

            localMutex.withLock {
                val sp = prefs(appContext)
                // If an unfinalized session was left open, finalize it cleanly in Room at its real timestamp first
                val prevFinalized = sp.getBoolean(KEY_IS_FINALIZED, true)
                val prevUuid = sp.getString(KEY_ACTIVE_SESSION_UUID, null)
                val prevElapsed = sp.getLong(KEY_LAST_KNOWN_ELAPSED_MS, 0L)
                val prevRowId = sp.getLong(KEY_ACTIVE_ROW_ID, 0L)
                if (!prevFinalized && !prevUuid.isNullOrBlank() && prevUuid != newUuid &&
                    (prevElapsed >= MIN_RECORDABLE_DURATION_MS || prevRowId > 0L)
                ) {
                    val prevMode = sp.getString(KEY_MODE_NAME, modeName) ?: modeName
                    val prevSubject = sp.getString(KEY_SUBJECT_NAME, subjectName) ?: subjectName
                    val prevLifts = sp.getInt(KEY_PHONE_LIFT_COUNT, 0)
                    val prevScreenOn = sp.getLong(KEY_SCREEN_ON_MILLIS, 0L)
                    val prevSyncedSec = sp.getLong(KEY_LAST_CLOUD_SYNCED_SECONDS, 0L)
                    val prevStartedAt = sp.getLong(KEY_STARTED_AT_MILLIS, 0L)
                    val prevLastActive = sp.getLong(KEY_LAST_ACTIVE_WALL_MILLIS, 0L)
                    val prevCompletedAt = resolveActualCompletedAtMillis(
                        startedAtWallMs = prevStartedAt,
                        lastActiveWallMs = prevLastActive,
                        segmentDurationMs = prevElapsed,
                        nowWallMs = nowWall
                    )

                    val savedPrev = upsertSingleSessionRowInternal(
                        context = appContext,
                        sessionUuid = prevUuid,
                        existingRowId = prevRowId,
                        modeName = prevMode,
                        durationMillis = prevElapsed,
                        phoneLiftCount = prevLifts,
                        screenOnMillis = prevScreenOn,
                        markSynced = false,
                        completedAtMillis = prevCompletedAt
                    )
                    evaluateWaterStreakAndRefreshWidgets(appContext)
                    prevEntityToSync = savedPrev
                    prevUuidToSync = prevUuid
                    prevSubjectToSync = prevSubject
                    prevSyncedSecToSync = prevSyncedSec
                }

                // Only write new session prefs if this session is still the active one
                if (activeSessionId == newUuid && !isFinalized) {
                    sp.edit()
                        .putString(KEY_ACTIVE_SESSION_UUID, newUuid)
                        .putLong(KEY_ACTIVE_ROW_ID, 0L)
                        .putString(KEY_MODE_NAME, modeName)
                        .putString(KEY_SUBJECT_NAME, subjectName)
                        .putLong(KEY_STARTED_AT_MILLIS, nowWall)
                        .putLong(KEY_LAST_ACTIVE_WALL_MILLIS, nowWall)
                        .putLong(KEY_SESSION_ELAPSED_OFFSET_MS, 0L)
                        .putLong(KEY_LAST_KNOWN_ELAPSED_MS, 0L)
                        .putLong(KEY_LAST_LOCAL_SAVE_ELAPSED_MS, 0L)
                        .putLong(KEY_LAST_CLOUD_SYNC_ELAPSED_MS, 0L)
                        .putLong(KEY_LAST_CLOUD_SYNCED_SECONDS, 0L)
                        .putInt(KEY_PHONE_LIFT_COUNT, 0)
                        .putLong(KEY_SCREEN_ON_MILLIS, 0L)
                        .putBoolean(KEY_IS_FINALIZED, false)
                        .apply()

                    Log.d(TAG, "Started new focus session uuid=$newUuid, mode=$modeName, subject=$subjectName")
                }
            }

            prevEntityToSync?.let { entity ->
                dispatchCloudSyncAsync(
                    appContext = appContext,
                    sessionEntity = entity,
                    sessionUuid = prevUuidToSync,
                    subjectName = prevSubjectToSync,
                    fallbackPreviouslySyncedSeconds = prevSyncedSecToSync,
                    isFinal = true
                )
            }
        }
        return newUuid
    }

    /**
     * Called when a paused timer is resumed. If the session sat paused for >= [PAUSE_SPLIT_THRESHOLD_MS]
     * (20 minutes) or across a midnight boundary, seals the earlier study block as a finished session
     * at its original pause timestamp and opens a fresh session row so multiple offline study blocks
     * are never merged into one combined record.
     */
    fun onResumeSession(
        context: Context,
        currentTotalElapsedMillis: Long,
        modeName: String,
        subjectName: String,
        phoneLiftCount: Int,
        screenOnMillis: Long
    ) {
        if (isFinalized) return
        val appContext = context.applicationContext
        val nowWall = System.currentTimeMillis()
        recorderScope.launch {
            var sealedEntityToSync: FocusSessionEntity? = null
            var sealedUuidToSync = ""
            var sealedSubjectToSync = subjectName
            var sealedPrevSyncedSec = 0L

            localMutex.withLock {
                if (isFinalized) return@withLock
                val sp = prefs(appContext)
                val prevLastActive = lastActiveWallMillis.takeIf { it > 0L }
                    ?: sp.getLong(KEY_LAST_ACTIVE_WALL_MILLIS, 0L)
                if (!shouldSplitSessionAfterPause(prevLastActive, nowWall)) {
                    lastActiveWallMillis = nowWall
                    sp.edit().putLong(KEY_LAST_ACTIVE_WALL_MILLIS, nowWall).apply()
                    return@withLock
                }

                // Seal the previous study block at its real pause timestamp
                val prevUuid = activeSessionId
                    ?: sp.getString(KEY_ACTIVE_SESSION_UUID, null)
                val prevRowId = activeRowId.takeIf { it > 0L } ?: sp.getLong(KEY_ACTIVE_ROW_ID, 0L)
                val prevOffset = maxOf(sessionElapsedOffsetMs, sp.getLong(KEY_SESSION_ELAPSED_OFFSET_MS, 0L))
                val prevSegmentElapsed = maxOf(
                    (currentTotalElapsedMillis - prevOffset).coerceAtLeast(0L),
                    sp.getLong(KEY_LAST_KNOWN_ELAPSED_MS, 0L)
                )
                val prevSyncedSec = maxOf(
                    lastCloudSyncedSeconds,
                    sp.getLong(KEY_LAST_CLOUD_SYNCED_SECONDS, 0L)
                )

                if (!prevUuid.isNullOrBlank() && (prevSegmentElapsed >= MIN_RECORDABLE_DURATION_MS || prevRowId > 0L)) {
                    val savedPrev = upsertSingleSessionRowInternal(
                        context = appContext,
                        sessionUuid = prevUuid,
                        existingRowId = prevRowId,
                        modeName = modeName,
                        durationMillis = prevSegmentElapsed,
                        phoneLiftCount = phoneLiftCount,
                        screenOnMillis = screenOnMillis,
                        markSynced = false,
                        completedAtMillis = prevLastActive.takeIf { it > 0L } ?: nowWall
                    )
                    evaluateWaterStreakAndRefreshWidgets(appContext)
                    sealedEntityToSync = savedPrev
                    sealedUuidToSync = prevUuid
                    sealedSubjectToSync = sp.getString(KEY_SUBJECT_NAME, subjectName) ?: subjectName
                    sealedPrevSyncedSec = prevSyncedSec
                }

                // Start a fresh session UUID & row for the new resumed study block
                val newUuid = UUID.randomUUID().toString()
                val newOffset = currentTotalElapsedMillis.coerceAtLeast(0L)
                activeSessionId = newUuid
                activeRowId = 0L
                sessionElapsedOffsetMs = newOffset
                lastActiveWallMillis = nowWall
                lastPrefsHeartbeatElapsedMs = 0L
                lastLocalSaveElapsedMs = 0L
                lastCloudSyncElapsedMs = 0L
                lastCloudSyncedSeconds = 0L

                sp.edit()
                    .putString(KEY_ACTIVE_SESSION_UUID, newUuid)
                    .putLong(KEY_ACTIVE_ROW_ID, 0L)
                    .putString(KEY_MODE_NAME, modeName)
                    .putString(KEY_SUBJECT_NAME, subjectName)
                    .putLong(KEY_STARTED_AT_MILLIS, nowWall)
                    .putLong(KEY_LAST_ACTIVE_WALL_MILLIS, nowWall)
                    .putLong(KEY_SESSION_ELAPSED_OFFSET_MS, newOffset)
                    .putLong(KEY_LAST_KNOWN_ELAPSED_MS, 0L)
                    .putLong(KEY_LAST_LOCAL_SAVE_ELAPSED_MS, 0L)
                    .putLong(KEY_LAST_CLOUD_SYNC_ELAPSED_MS, 0L)
                    .putLong(KEY_LAST_CLOUD_SYNCED_SECONDS, 0L)
                    .putInt(KEY_PHONE_LIFT_COUNT, phoneLiftCount)
                    .putLong(KEY_SCREEN_ON_MILLIS, screenOnMillis)
                    .putBoolean(KEY_IS_FINALIZED, false)
                    .apply()

                Log.i(
                    TAG,
                    "Split paused session after long idle gap: sealed prevUuid=$prevUuid (${prevSegmentElapsed / 60000}m at $prevLastActive), started newUuid=$newUuid"
                )
            }

            sealedEntityToSync?.let { entity ->
                dispatchCloudSyncAsync(
                    appContext = appContext,
                    sessionEntity = entity,
                    sessionUuid = sealedUuidToSync,
                    subjectName = sealedSubjectToSync,
                    fallbackPreviouslySyncedSeconds = sealedPrevSyncedSec,
                    isFinal = true
                )
            }
        }
    }

    /**
     * Called on each timer tick while focusing.
     * - Flushes lightweight crash-recovery checkpoint every 15 seconds.
     * - Upserts the single Room row every **20 minutes** (`1_200_000 ms`) with `isSynced = false` (never blocked by network).
     * - Asynchronously attempts Firestore sync every **60 minutes** (`3_600_000 ms`) when online;
     *   if offline, leaves `isSynced = false` for [SyncWorker] while 20-min local saves continue undisturbed.
     */
    fun onTick(
        context: Context,
        elapsedMillis: Long,
        modeName: String,
        subjectName: String,
        phoneLiftCount: Int,
        screenOnMillis: Long
    ) {
        if (elapsedMillis <= 0L || isFinalized) return

        val segmentElapsedMs = (elapsedMillis - sessionElapsedOffsetMs).coerceAtLeast(1_000L)
        val nowWall = System.currentTimeMillis()
        lastActiveWallMillis = nowWall

        val (shouldFlushHeartbeat, shouldSaveLocal20Min, shouldSyncCloud60Min) = computeCheckpointTriggers(
            elapsedMillis = segmentElapsedMs,
            lastHeartbeatMs = lastPrefsHeartbeatElapsedMs,
            lastLocalSaveMs = lastLocalSaveElapsedMs,
            lastCloudSyncMs = lastCloudSyncElapsedMs
        )

        if (!shouldFlushHeartbeat && !shouldSaveLocal20Min && !shouldSyncCloud60Min) {
            return
        }

        val appContext = context.applicationContext
        val capturedSessionUuid = activeSessionId
        if (shouldFlushHeartbeat) {
            lastPrefsHeartbeatElapsedMs = segmentElapsedMs
        }
        val nextLocalBucketMs = if (shouldSaveLocal20Min) {
            ((segmentElapsedMs / LOCAL_CHECKPOINT_INTERVAL_MS) * LOCAL_CHECKPOINT_INTERVAL_MS).also {
                lastLocalSaveElapsedMs = it
            }
        } else {
            lastLocalSaveElapsedMs
        }
        val nextCloudBucketMs = if (shouldSyncCloud60Min) {
            ((segmentElapsedMs / CLOUD_SYNC_INTERVAL_MS) * CLOUD_SYNC_INTERVAL_MS).also {
                lastCloudSyncElapsedMs = it
            }
        } else {
            lastCloudSyncElapsedMs
        }

        recorderScope.launch {
            var entityForCloudSync: FocusSessionEntity? = null
            var uuidForCloudSync = ""
            var prevSyncedSecForCloud = 0L

            localMutex.withLock {
                if (isFinalized) return@withLock
                if (capturedSessionUuid != null && activeSessionId != null && capturedSessionUuid != activeSessionId) {
                    return@withLock
                }
                val sp = prefs(appContext)
                val uuid = activeSessionId
                    ?: sp.getString(KEY_ACTIVE_SESSION_UUID, null)
                    ?: UUID.randomUUID().toString().also { activeSessionId = it }

                // 1. Always update SharedPreferences checkpoint (fast local write)
                sp.edit()
                    .putString(KEY_ACTIVE_SESSION_UUID, uuid)
                    .putString(KEY_MODE_NAME, modeName)
                    .putString(KEY_SUBJECT_NAME, subjectName)
                    .putLong(KEY_LAST_ACTIVE_WALL_MILLIS, nowWall)
                    .putLong(KEY_LAST_KNOWN_ELAPSED_MS, segmentElapsedMs)
                    .putInt(KEY_PHONE_LIFT_COUNT, phoneLiftCount)
                    .putLong(KEY_SCREEN_ON_MILLIS, screenOnMillis)
                    .putBoolean(KEY_IS_FINALIZED, false)
                    .apply()

                // 2. Every 20 minutes (or at 60-minute boundary): Upsert the SAME Room row locally
                if (shouldSaveLocal20Min || shouldSyncCloud60Min) {
                    val savedEntity = upsertSingleSessionRowInternal(
                        context = appContext,
                        sessionUuid = uuid,
                        existingRowId = activeRowId.takeIf { it > 0L } ?: sp.getLong(KEY_ACTIVE_ROW_ID, 0L),
                        modeName = modeName,
                        durationMillis = segmentElapsedMs,
                        phoneLiftCount = phoneLiftCount,
                        screenOnMillis = screenOnMillis,
                        markSynced = false,
                        completedAtMillis = nowWall
                    )
                    activeRowId = savedEntity.id
                    sp.edit()
                        .putLong(KEY_ACTIVE_ROW_ID, savedEntity.id)
                        .putLong(KEY_LAST_LOCAL_SAVE_ELAPSED_MS, nextLocalBucketMs)
                        .putLong(KEY_LAST_CLOUD_SYNC_ELAPSED_MS, nextCloudBucketMs)
                        .apply()

                    Log.i(
                        TAG,
                        "20-min local checkpoint saved (single-row upsert): rowId=${savedEntity.id}, uuid=$uuid, durationSec=${savedEntity.durationSeconds}"
                    )
                    evaluateWaterStreakAndRefreshWidgets(appContext)

                    if (shouldSyncCloud60Min) {
                        entityForCloudSync = savedEntity
                        uuidForCloudSync = uuid
                        prevSyncedSecForCloud = maxOf(
                            lastCloudSyncedSeconds,
                            sp.getLong(KEY_LAST_CLOUD_SYNCED_SECONDS, 0L)
                        )
                    }
                }
            }

            // 3. Every 60 minutes: Dispatch cloud sync OUTSIDE localMutex so local checkpoints NEVER stall
            entityForCloudSync?.let { savedEntity ->
                dispatchCloudSyncAsync(
                    appContext = appContext,
                    sessionEntity = savedEntity,
                    sessionUuid = uuidForCloudSync,
                    subjectName = subjectName,
                    fallbackPreviouslySyncedSeconds = prevSyncedSecForCloud,
                    isFinal = false
                )
            }
        }
    }

    /**
     * Called when the user pauses the timer so the exact paused elapsed time and wall-clock pause
     * timestamp are persisted in both SharedPreferences and Room without finalizing the session.
     */
    fun onPauseCheckpoint(
        context: Context,
        elapsedMillis: Long,
        modeName: String,
        subjectName: String,
        phoneLiftCount: Int,
        screenOnMillis: Long,
        overrideCompletedAtMillis: Long = 0L
    ) {
        val appContext = context.applicationContext
        val pauseWallMs = if (overrideCompletedAtMillis > 0L) overrideCompletedAtMillis else System.currentTimeMillis()
        lastActiveWallMillis = pauseWallMs
        recorderScope.launch {
            localMutex.withLock {
                if (isFinalized) return@withLock
                val sp = prefs(appContext)
                val offset = maxOf(sessionElapsedOffsetMs, sp.getLong(KEY_SESSION_ELAPSED_OFFSET_MS, 0L))
                val segmentElapsed = (elapsedMillis - offset).coerceAtLeast(0L)
                val effectiveElapsed = maxOf(segmentElapsed, sp.getLong(KEY_LAST_KNOWN_ELAPSED_MS, 0L))
                val uuid = activeSessionId
                    ?: sp.getString(KEY_ACTIVE_SESSION_UUID, null)
                    ?: if (effectiveElapsed >= MIN_RECORDABLE_DURATION_MS) {
                        UUID.randomUUID().toString().also { activeSessionId = it }
                    } else null

                sp.edit()
                    .apply {
                        if (uuid != null) putString(KEY_ACTIVE_SESSION_UUID, uuid)
                    }
                    .putLong(KEY_LAST_ACTIVE_WALL_MILLIS, pauseWallMs)
                    .putLong(KEY_LAST_KNOWN_ELAPSED_MS, effectiveElapsed)
                    .putString(KEY_MODE_NAME, modeName)
                    .putString(KEY_SUBJECT_NAME, subjectName)
                    .putInt(KEY_PHONE_LIFT_COUNT, phoneLiftCount)
                    .putLong(KEY_SCREEN_ON_MILLIS, screenOnMillis)
                    .putBoolean(KEY_IS_FINALIZED, false)
                    .apply()

                if (uuid != null && effectiveElapsed >= MIN_RECORDABLE_DURATION_MS) {
                    runCatching {
                        val rowId = activeRowId.takeIf { it > 0L } ?: sp.getLong(KEY_ACTIVE_ROW_ID, 0L)
                        val saved = upsertSingleSessionRowInternal(
                            context = appContext,
                            sessionUuid = uuid,
                            existingRowId = rowId,
                            modeName = modeName,
                            durationMillis = effectiveElapsed,
                            phoneLiftCount = phoneLiftCount,
                            screenOnMillis = screenOnMillis,
                            markSynced = false,
                            completedAtMillis = pauseWallMs
                        )
                        activeRowId = saved.id
                        sp.edit().putLong(KEY_ACTIVE_ROW_ID, saved.id).apply()
                        evaluateWaterStreakAndRefreshWidgets(appContext)
                    }.onFailure { Log.e(TAG, "Pause checkpoint Room upsert failed", it) }
                }
            }
        }
    }

    /**
     * Finalizes the active session upon Stop, Reset, Pomodoro/Block completion, or Service destroy.
     * Updates the exact same Room row (`activeRowId` / `cloudId = activeSessionId`) quickly inside
     * [localMutex] with its actual study timestamp, invokes [onComplete] as soon as Room persistence
     * succeeds, and dispatches cloud sync asynchronously.
     */
    fun finalizeSession(
        context: Context,
        elapsedMillis: Long,
        modeName: String,
        subjectName: String = "Genel",
        phoneLiftCount: Int = 0,
        screenOnMillis: Long = 0L,
        onComplete: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val nowWall = System.currentTimeMillis()
        recorderScope.launch {
            var finalEntityToSync: FocusSessionEntity? = null
            var finalUuidToSync = ""
            var finalPrevSyncedSec = 0L

            try {
                localMutex.withLock {
                    val sp = prefs(appContext)
                    val alreadyFinalized = isFinalized && sp.getBoolean(KEY_IS_FINALIZED, true)
                    if (alreadyFinalized) {
                        return@withLock
                    }

                    val uuid = activeSessionId
                        ?: sp.getString(KEY_ACTIVE_SESSION_UUID, null)
                        ?: UUID.randomUUID().toString()
                    val existingRowId = activeRowId.takeIf { it > 0L } ?: sp.getLong(KEY_ACTIVE_ROW_ID, 0L)
                    val offset = maxOf(sessionElapsedOffsetMs, sp.getLong(KEY_SESSION_ELAPSED_OFFSET_MS, 0L))
                    val segmentElapsed = (elapsedMillis - offset).coerceAtLeast(0L)
                    val effectiveElapsedMs = maxOf(segmentElapsed, sp.getLong(KEY_LAST_KNOWN_ELAPSED_MS, 0L))
                    val startedAtWall = sp.getLong(KEY_STARTED_AT_MILLIS, 0L)
                    val savedLastActiveWall = lastActiveWallMillis.takeIf { it > 0L }
                        ?: sp.getLong(KEY_LAST_ACTIVE_WALL_MILLIS, 0L)
                    val actualCompletedAt = resolveActualCompletedAtMillis(
                        startedAtWallMs = startedAtWall,
                        lastActiveWallMs = savedLastActiveWall,
                        segmentDurationMs = effectiveElapsedMs,
                        nowWallMs = nowWall
                    )
                    val prevSyncedSec = maxOf(
                        lastCloudSyncedSeconds,
                        sp.getLong(KEY_LAST_CLOUD_SYNCED_SECONDS, 0L),
                        cloudSyncedSecondsByUuid[uuid] ?: 0L
                    )

                    // Mark finalized immediately inside localMutex to prevent concurrent duplicate finalizes
                    isFinalized = true
                    activeSessionId = null
                    activeRowId = 0L
                    sessionElapsedOffsetMs = 0L
                    lastActiveWallMillis = 0L
                    lastPrefsHeartbeatElapsedMs = 0L
                    lastLocalSaveElapsedMs = 0L
                    lastCloudSyncElapsedMs = 0L
                    lastCloudSyncedSeconds = 0L
                    clearCheckpointPrefs(sp)

                    if (effectiveElapsedMs < MIN_RECORDABLE_DURATION_MS && existingRowId <= 0L) {
                        Log.d(
                            TAG,
                            "Session shorter than 60s (${effectiveElapsedMs}ms) with no checkpoint row; skipping save."
                        )
                        return@withLock
                    }

                    val finalEntity = upsertSingleSessionRowInternal(
                        context = appContext,
                        sessionUuid = uuid,
                        existingRowId = existingRowId,
                        modeName = modeName,
                        durationMillis = effectiveElapsedMs,
                        phoneLiftCount = phoneLiftCount,
                        screenOnMillis = screenOnMillis,
                        markSynced = false,
                        completedAtMillis = actualCompletedAt
                    )

                    Log.i(
                        TAG,
                        "Finalized session in Room (single-row upsert): rowId=${finalEntity.id}, uuid=$uuid, totalSec=${finalEntity.durationSeconds}, completedAt=$actualCompletedAt"
                    )
                    evaluateWaterStreakAndRefreshWidgets(appContext)

                    finalEntityToSync = finalEntity
                    finalUuidToSync = uuid
                    finalPrevSyncedSec = prevSyncedSec
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error during finalizeSession local persistence", t)
            } finally {
                // Local Room write is complete — let FocusTimerService finish cleanup cleanly without waiting on network
                onComplete?.invoke()
            }

            finalEntityToSync?.let { entity ->
                dispatchCloudSyncAsync(
                    appContext = appContext,
                    sessionEntity = entity,
                    sessionUuid = finalUuidToSync,
                    subjectName = subjectName,
                    fallbackPreviouslySyncedSeconds = finalPrevSyncedSec,
                    isFinal = true
                )
            }
        }
    }

    /**
     * Public helper for external study tools (e.g. PACE, KEEP, PDF Reader, Flashcards) to record
     * focus duration into Room (`focus_sessions`) and canonical Firestore (`students/{uid}/sessions`)
     * without double-counting.
     */
    suspend fun recordExternalToolSession(
        context: Context,
        toolName: String,
        durationSeconds: Long,
        subjectName: String = "Genel",
        sessionUuid: String = UUID.randomUUID().toString(),
        previouslySyncedSeconds: Long = 0L,
        isFinal: Boolean = true
    ): Long = withContext(Dispatchers.IO) {
        if (durationSeconds <= 0L) return@withContext 0L
        val appContext = context.applicationContext
        val savedEntity = localMutex.withLock {
            val sessionDao = SharkMentorDatabase.getDatabase(appContext).sessionDao()
            val existing = sessionDao.getSessionByCloudId(sessionUuid)
            val normalizedModeName = if (toolName.isBlank()) "Harici Araç" else toolName.trim()
            upsertSingleSessionRowInternal(
                context = appContext,
                sessionUuid = sessionUuid,
                existingRowId = existing?.id ?: 0L,
                modeName = normalizedModeName,
                durationMillis = durationSeconds * 1_000L,
                phoneLiftCount = existing?.phoneLiftCount ?: 0,
                screenOnMillis = ((existing?.screenOnMinutes ?: 0) * 60_000L),
                markSynced = false
            ).also {
                evaluateWaterStreakAndRefreshWidgets(appContext)
            }
        }
        dispatchCloudSyncAsync(
            appContext = appContext,
            sessionEntity = savedEntity,
            sessionUuid = sessionUuid,
            subjectName = subjectName,
            fallbackPreviouslySyncedSeconds = previouslySyncedSeconds,
            isFinal = isFinal
        )
        savedEntity.id
    }

    /**
     * Fire-and-forget convenience wrapper for external tools (PACE / KEEP) calling from UI or callbacks.
     */
    fun recordExternalToolSessionAsync(
        context: Context,
        toolName: String,
        durationSeconds: Long,
        subjectName: String = "Genel",
        sessionUuid: String = UUID.randomUUID().toString()
    ) {
        val appContext = context.applicationContext
        recorderScope.launch {
            recordExternalToolSession(
                context = appContext,
                toolName = toolName,
                durationSeconds = durationSeconds,
                subjectName = subjectName,
                sessionUuid = sessionUuid,
                previouslySyncedSeconds = 0L,
                isFinal = true
            )
        }
    }

    private suspend fun evaluateWaterStreakAndRefreshWidgets(appContext: Context) {
        runCatching {
            val streakRepo = com.example.sharkmentor.data.repository.StreakRepository(appContext)
            val newlyEarnedToday = streakRepo.evaluateFromRoomAfterFocus()
            if (newlyEarnedToday) {
                val state = streakRepo.streakState.value
                com.example.sharkmentor.worker.StreakReminderWorker.showWaterDropSecuredNotification(
                    context = appContext,
                    streakCount = state.streakCount,
                    todayMinutes = com.example.sharkmentor.data.repository.StreakRepository.MIN_DAILY_STREAK_MINUTES
                )
            }
        }.onFailure {
            Log.w(TAG, "Water streak evaluation warning: ${it.message}")
        }
        runCatching { WidgetUpdateManager.updateAllWidgets(appContext) }
    }

    // ── Internal Single-Row Room Upsert & Firestore Sync ─────────────────────

    private suspend fun upsertSingleSessionRowInternal(
        context: Context,
        sessionUuid: String,
        existingRowId: Long,
        modeName: String,
        durationMillis: Long,
        phoneLiftCount: Int,
        screenOnMillis: Long,
        markSynced: Boolean,
        completedAtMillis: Long = System.currentTimeMillis()
    ): FocusSessionEntity {
        val durationSeconds = (durationMillis / 1_000L).coerceAtLeast(1L)
        val screenOnMinutes = (screenOnMillis / 60_000L).toInt()
        val afkScore = (10.0f - (phoneLiftCount * 2.0f)).coerceAtLeast(0.0f)
        val now = System.currentTimeMillis()
        val effectiveCompletedAt = if (completedAtMillis > 0L) completedAtMillis else now
        val institutionId = UserPreferences.getInstance(context).institutionId

        val db = SharkMentorDatabase.getDatabase(context)
        val sessionDao = db.sessionDao()

        val currentEntity = when {
            existingRowId > 0L -> sessionDao.getSessionById(existingRowId)
            else -> sessionDao.getSessionByCloudId(sessionUuid)
        }

        val entityToSave = FocusSessionEntity(
            id = currentEntity?.id ?: existingRowId.coerceAtLeast(0L),
            modeName = modeName,
            durationSeconds = maxOf(durationSeconds, currentEntity?.durationSeconds ?: 0L),
            completedAt = effectiveCompletedAt,
            afkScore = afkScore,
            phoneLiftCount = phoneLiftCount,
            screenOnMinutes = screenOnMinutes,
            isSynced = markSynced,
            lastModifiedMillis = now,
            cloudId = sessionUuid,
            institutionId = institutionId,
            assignedCoachId = currentEntity?.assignedCoachId
        )

        val rowId = sessionDao.upsertSession(entityToSave)
        val finalRowId = if (entityToSave.id > 0L) entityToSave.id else rowId
        return entityToSave.copy(id = finalRowId)
    }

    /**
     * Syncs a session document to canonical Firestore path `students/{uid}/sessions/{sessionUuid}`
     * and updates the student summary delta (2 writes total instead of 4 redundant writes) so
     * 60-minute checkpoints and finalization stay quota-safe and never double-count.
     * IMPORTANT: `completedAt` and `timestamp` both use `sessionEntity.completedAt` so offline
     * sessions synced hours later when Wi-Fi connects keep their real study time instead of the Wi-Fi sync time.
     */
    private suspend fun syncSessionToFirestoreInternal(
        context: Context,
        sessionEntity: FocusSessionEntity,
        sessionUuid: String,
        subjectName: String,
        previouslySyncedSeconds: Long,
        isFinal: Boolean
    ): Boolean {
        return try {
            val currentUserId = GoogleServicesHelper.getCurrentUser(context = context)?.uid
            if (currentUserId.isNullOrBlank() || currentUserId == "student_default") {
                return false
            }
            val db = GoogleServicesHelper.getFirebaseFirestore(context) ?: return false
            val userPrefs = UserPreferences.getInstance(context)
            val instId = sessionEntity.institutionId?.takeIf { it.isNotBlank() }
                ?: userPrefs.institutionId?.takeIf { it.isNotBlank() }
                ?: "INDIVIDUAL"
            val studentName = SyncWorker.sanitizeTurkishText(userPrefs.studentName ?: "Öğrenci")

            val durationSec = sessionEntity.durationSeconds
            val minutes = durationSec / 60L
            val seconds = durationSec % 60L
            val formattedDuration = if (minutes > 0L) "${minutes} dk ${seconds} sn" else "${seconds} sn"

            val sessionData = hashMapOf<String, Any?>(
                "id" to sessionUuid,
                "cloudId" to sessionUuid,
                "localId" to sessionEntity.id,
                "studentId" to currentUserId,
                "studentName" to studentName,
                "mode" to sessionEntity.modeName,
                "modeName" to sessionEntity.modeName,
                "subject" to subjectName,
                "durationSeconds" to durationSec,
                "durationMinutes" to minutes,
                "durationFormatted" to formattedDuration,
                "afkScore" to sessionEntity.afkScore,
                "phoneLiftCount" to sessionEntity.phoneLiftCount,
                "screenOnMinutes" to sessionEntity.screenOnMinutes,
                "institutionId" to instId,
                "isIndividual" to (instId == "INDIVIDUAL"),
                "isFinalized" to isFinal,
                "isDeleted" to false,
                "timestamp" to sessionEntity.completedAt,
                "completedAt" to sessionEntity.completedAt,
                "lastModifiedMillis" to sessionEntity.lastModifiedMillis,
                "syncedAt" to System.currentTimeMillis()
            )

            // 1. Canonical session document: students/{uid}/sessions/{sessionUuid}
            db.collection("students").document(currentUserId)
                .collection("sessions").document(sessionUuid)
                .set(sessionData, SetOptions.merge())
                .await()

            // 2. Increment totalFocusSeconds only by the unsynced delta to prevent double-counting
            val deltaSeconds = (durationSec - previouslySyncedSeconds).coerceAtLeast(0L)
            val studentUpdate = hashMapOf<String, Any>(
                "id" to currentUserId,
                "name" to studentName,
                "lastActiveAt" to FieldValue.serverTimestamp()
            )
            if (deltaSeconds > 0L) {
                studentUpdate["totalFocusSeconds"] = FieldValue.increment(deltaSeconds)
            }
            if (instId != "INDIVIDUAL") {
                studentUpdate["coachCode"] = instId
            }
            db.collection("students").document(currentUserId)
                .set(studentUpdate, SetOptions.merge())
                .await()

            true
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore session sync failed (offline or network issue): ${e.message}")
            false
        }
    }

    private fun clearCheckpointPrefs(sp: SharedPreferences) {
        sp.edit()
            .putBoolean(KEY_IS_FINALIZED, true)
            .remove(KEY_ACTIVE_SESSION_UUID)
            .remove(KEY_ACTIVE_ROW_ID)
            .remove(KEY_LAST_ACTIVE_WALL_MILLIS)
            .remove(KEY_SESSION_ELAPSED_OFFSET_MS)
            .remove(KEY_LAST_KNOWN_ELAPSED_MS)
            .remove(KEY_LAST_LOCAL_SAVE_ELAPSED_MS)
            .remove(KEY_LAST_CLOUD_SYNC_ELAPSED_MS)
            .remove(KEY_LAST_CLOUD_SYNCED_SECONDS)
            .apply()
    }
}
