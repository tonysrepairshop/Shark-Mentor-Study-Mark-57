package com.example.sharkmentor.core.service

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.sharkmentor.MainActivity
import com.example.sharkmentor.R
import com.example.sharkmentor.SharkMentorApplication
import com.example.sharkmentor.core.blocker.BlockerPreferences
import com.example.sharkmentor.core.blocker.UsageStatsHelper
import com.example.sharkmentor.core.sensor.AfkSensorManager
import com.example.sharkmentor.core.timer.FocusSessionRecorder
import com.example.sharkmentor.core.timer.TimerMode
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.core.timer.TimerState
import com.example.sharkmentor.core.timer.DndManager
import com.example.sharkmentor.core.util.AppIconManager
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.widget.WidgetUpdateManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.data.topbar.FocusSessionState

/**
 * Foreground service driving the SharkMentor timer engine.
 *
 * See the original KDoc for the full contract. This version adds:
 * - **BLOCK_90 lift nudge** — a high-priority motivational notification fires when the
 *   phone is lifted during a BLOCK_90 session, with a 3-minute per-lift cooldown.
 * - Strings updated: "Zorunlu Mola" → "Mola" throughout.
 */
class FocusTimerService : Service() {

    // ── Coroutine infrastructure ─────────────────────────────────────────────
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private var afkJob: Job? = null
    private var watchdogJob: Job? = null

    private lateinit var notificationManager: NotificationManager
    private lateinit var afkSensor: AfkSensorManager
    private lateinit var dndManager: DndManager

    // ── WakeLock ─────────────────────────────────────────────────────────────
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastWakeLockAcquireElapsedMs: Long = 0L

    private fun acquireWakeLock(forceRenew: Boolean = false) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!forceRenew && wakeLock?.isHeld == true && (nowElapsed - lastWakeLockAcquireElapsedMs) < WAKE_LOCK_RENEW_INTERVAL_MS) {
            return
        }
        val pm = getSystemService(PowerManager::class.java)
        val existing = wakeLock
        if (existing != null) {
            runCatching {
                if (existing.isHeld) existing.release()
                existing.acquire(WAKE_LOCK_TIMEOUT_MS)
                lastWakeLockAcquireElapsedMs = nowElapsed
                return
            }
        }
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SharkMentor::FocusTimerWakeLock"
        ).also {
            it.acquire(WAKE_LOCK_TIMEOUT_MS)
            lastWakeLockAcquireElapsedMs = nowElapsed
        }
    }

    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
        wakeLock = null
        lastWakeLockAcquireElapsedMs = 0L
    }

    // ── Screen tracking ──────────────────────────────────────────────────────
    private var totalScreenOnMillis: Long = 0L
    private var lastScreenOnTimestamp: Long = 0L
    private var isScreenReceiverRegistered: Boolean = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON  -> lastScreenOnTimestamp = System.currentTimeMillis()
                Intent.ACTION_SCREEN_OFF -> {
                    if (lastScreenOnTimestamp > 0) {
                        totalScreenOnMillis += System.currentTimeMillis() - lastScreenOnTimestamp
                        lastScreenOnTimestamp = 0L
                    }
                }
            }
        }
    }

    private fun registerScreenReceiverSafe() {
        if (isScreenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(screenStateReceiver, filter)
            }
            isScreenReceiverRegistered = true
        }
    }

    private fun unregisterScreenReceiverSafe() {
        if (!isScreenReceiverRegistered) return
        runCatching { unregisterReceiver(screenStateReceiver) }
        isScreenReceiverRegistered = false
    }

    private fun computeAccurateElapsedMillis(state: TimerState): Long {
        return if (state.timerPhase == TimerPhase.FOCUSING && state.startTimestampElapsed > 0L) {
            val delta = (SystemClock.elapsedRealtime() - state.startTimestampElapsed).coerceAtLeast(0L)
            maxOf(state.timeElapsedMillis, state.pausedAccumulatedMillis + delta)
        } else {
            maxOf(state.timeElapsedMillis, state.pausedAccumulatedMillis)
        }
    }

    // ── Lift nudge state ─────────────────────────────────────────────────────
    /** [SystemClock.elapsedRealtime] of the last nudge notification posted. */
    private var lastNudgeTimestamp: Long = 0L

    /** Phone lift count at the last AFK collector emission — used to detect increases. */
    private var previousLiftCount: Int = 0

    private fun currentScreenOnMillis(): Long {
        return if (lastScreenOnTimestamp > 0L) {
            totalScreenOnMillis + (System.currentTimeMillis() - lastScreenOnTimestamp).coerceAtLeast(0L)
        } else {
            totalScreenOnMillis
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannels()
        afkSensor = AfkSensorManager(applicationContext as Application)
        dndManager = DndManager(applicationContext)

        // Recover any unfinalized session from a previous crash/process death without double-counting
        FocusSessionRecorder.recoverOrphanedSessionIfNeeded(
            context = applicationContext,
            isTimerCurrentlyActive = _timerStateFlow.value.timerPhase != TimerPhase.IDLE
        )

        serviceScope.launch {
            var previousPhase = TimerPhase.IDLE
            _timerStateFlow.collect { state ->
                val isFocusing = state.timerPhase == TimerPhase.FOCUSING
                val wasFocusing = previousPhase == TimerPhase.FOCUSING
                FocusSessionState.setRunning(isFocusing || state.timerPhase == TimerPhase.MANDATORY_BREAK)

                if (isFocusing && !wasFocusing && state.currentMode == TimerMode.BLOCK_90) {
                    dndManager.enableDeepFocusDnd()
                } else if (!isFocusing && wasFocusing) {
                    dndManager.disableDnd()
                }
                if (state.timerPhase != previousPhase) {
                    WidgetUpdateManager.updateAllWidgets(applicationContext)
                }
                previousPhase = state.timerPhase
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START            -> {
                val mode = TimerMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: TimerMode.STOPWATCH.name)
                val pomodoroMinutes = intent.getIntExtra(EXTRA_POMODORO_MINUTES, -1).takeIf { it > 0 }
                val breakMinutes    = intent.getIntExtra(EXTRA_BREAK_MINUTES, -1).takeIf { it > 0 }
                val subject         = intent.getStringExtra(EXTRA_SUBJECT) ?: "Genel"
                handleStart(mode, pomodoroMinutes, breakMinutes, subject)
            }
            ACTION_PAUSE            -> handlePause()
            ACTION_RESUME           -> handleResume()
            ACTION_STOP             -> handleStop()
            ACTION_RESET            -> handleReset()
            ACTION_CONFIRM_PRESENCE -> handleConfirmPresence()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dismissAllNotifications() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(NOTIFICATION_ID)
            notificationManager.cancel(ALERT_NOTIFICATION_ID)
            notificationManager.cancel(NUDGE_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e("FocusTimerService", "Error dismissing notifications", e)
        }
    }

    override fun onDestroy() {
        val current = _timerStateFlow.value
        if (current.timerPhase != TimerPhase.IDLE && current.currentMode != TimerMode.BREAK) {
            val accurateElapsed = computeAccurateElapsedMillis(current)
            FocusSessionRecorder.finalizeSession(
                context = applicationContext,
                elapsedMillis = accurateElapsed,
                modeName = current.currentMode.displayName,
                subjectName = activeSubjectName,
                phoneLiftCount = current.phoneLiftCount,
                screenOnMillis = currentScreenOnMillis()
            )
        }
        updatePresenceInFirestore(false)
        SessionManager.setTimerRunning(false) // Service killed — always clear the flag
        FocusSessionState.setRunning(false)
        tickerJob?.cancel()
        tickerJob = null
        afkJob?.cancel()
        afkJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        afkSensor.stop()
        dndManager.disableDnd()
        releaseWakeLock()
        unregisterScreenReceiverSafe()
        dismissAllNotifications()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Live Presence Sync ──────────────────────────────────────────────────
    private var activeSubjectName: String = "Genel"
    private var activeSessionUuid: String? = null

    private fun updatePresenceInFirestore(isStudying: Boolean, subject: String = activeSubjectName) {
        try {
            com.example.sharkmentor.core.coaching.CoachingEngine.updateLivePresenceOfflineFirst(
                context = applicationContext,
                isStudying = isStudying,
                subject = subject
            )
        } catch (e: Throwable) {
            Log.w("FocusTimerService", "Presence update failed: ${e.message}")
        }
    }

    // ── Timer control handlers ───────────────────────────────────────────────

    private fun handleStart(mode: TimerMode, intentPomodoro: Int?, intentBreak: Int?, subject: String = "Genel") {
        val resolvedSubject = subject.trim().takeIf { it.isNotBlank() && it != "Genel" }
            ?: runCatching {
                com.example.sharkmentor.core.coaching.CoachingEngine.ensureInitialized(applicationContext)
                com.example.sharkmentor.core.coaching.CoachingEngine.buildFormattedFocusSubject()
            }.getOrDefault(subject.ifBlank { "Genel" })
        activeSubjectName = resolvedSubject
        if (mode != TimerMode.BREAK) {
            activeSessionUuid = FocusSessionRecorder.startSession(
                context = applicationContext,
                modeName = mode.displayName,
                subjectName = activeSubjectName
            )
        } else {
            activeSessionUuid = null
        }
        updatePresenceInFirestore(mode != TimerMode.BREAK, activeSubjectName)
        val current = _timerStateFlow.value

        totalScreenOnMillis = 0L
        lastScreenOnTimestamp = if (getSystemService(PowerManager::class.java).isInteractive)
            System.currentTimeMillis() else 0L

        registerScreenReceiverSafe()

        val pomodoroMin = intentPomodoro?.coerceIn(30, 300) ?: current.customPomodoroDurationMinutes
        val breakMin    = intentBreak?.coerceIn(5, 20)     ?: current.breakDurationMinutes

        val totalDuration = when (mode) {
            TimerMode.POMODORO -> pomodoroMin * 60_000L
            TimerMode.BREAK    -> breakMin    * 60_000L
            else               -> mode.initialMillis
        }

        val now = SystemClock.elapsedRealtime()
        _timerStateFlow.value = TimerState(
            timeElapsedMillis             = 0L,
            totalDurationMillis           = totalDuration,
            timerPhase                    = TimerPhase.FOCUSING,
            startTimestampElapsed         = now,
            pausedAccumulatedMillis       = 0L,
            currentMode                   = mode,
            customPomodoroDurationMinutes = pomodoroMin,
            breakDurationMinutes          = breakMin,
            completedConsecutiveSessions  = when (mode) {
                TimerMode.BREAK, TimerMode.STOPWATCH -> 0
                else -> current.completedConsecutiveSessions
            },
            isMandatoryBreakRequired = mode == TimerMode.BREAK && current.isMandatoryBreakRequired,
            activeSubject            = activeSubjectName,
        )
        startForegroundCompat(buildNotification(_timerStateFlow.value))
        acquireWakeLock()
        SessionManager.setTimerRunning(true) // Timer is actively ticking — engage blocker
        startTicker()
        startWatchdog()

        // Reset nudge state for the new session
        lastNudgeTimestamp = 0L
        previousLiftCount  = 0

        afkSensor.resetLiftCount()
        afkSensor.start()

        afkJob?.cancel()
        afkJob = serviceScope.launch {
            combine(afkSensor.isStationary, afkSensor.phoneLiftCount) { isStationary, lifts ->
                isStationary to lifts
            }.collect { (isStationary, lifts) ->
                val s = _timerStateFlow.value
                if (s.timerPhase == TimerPhase.FOCUSING) {
                    _timerStateFlow.value = s.copy(
                        isAfkStationary = isStationary,
                        phoneLiftCount  = lifts
                    )
                    // ── BLOCK_90 lift nudge with 2-minute debounce ────────────
                    if (s.currentMode == TimerMode.BLOCK_90 && lifts > previousLiftCount) {
                        val nowElapsed = SystemClock.elapsedRealtime()
                        if (nowElapsed - lastNudgeTimestamp >= 2 * 60 * 1000L) { // 2-minute cooldown
                            lastNudgeTimestamp = nowElapsed
                            postLiftNudgeNotification()
                        }
                    }
                }
                previousLiftCount = lifts
            }
        }
    }

    private fun handlePause() {
        val current = _timerStateFlow.value
        if (current.timerPhase != TimerPhase.FOCUSING) return
        updatePresenceInFirestore(false)
        SessionManager.setTimerRunning(false) // Paused — allow app switching
        tickerJob?.cancel()
        watchdogJob?.cancel()
        afkSensor.stop()
        releaseWakeLock()

        val now     = SystemClock.elapsedRealtime()
        val elapsed = current.pausedAccumulatedMillis + (now - current.startTimestampElapsed)
        if (current.currentMode != TimerMode.BREAK) {
            FocusSessionRecorder.onPauseCheckpoint(
                context = applicationContext,
                elapsedMillis = elapsed,
                modeName = current.currentMode.displayName,
                subjectName = activeSubjectName,
                phoneLiftCount = current.phoneLiftCount,
                screenOnMillis = currentScreenOnMillis()
            )
        }
        val paused  = current.copy(
            timerPhase              = TimerPhase.PAUSED,
            timeElapsedMillis       = elapsed,
            pausedAccumulatedMillis = elapsed,
            startTimestampElapsed   = 0L,
        )
        _timerStateFlow.value = paused
        notificationManager.notify(NOTIFICATION_ID, buildNotification(paused))
    }

    private fun handleResume() {
        val current = _timerStateFlow.value
        if (current.timerPhase != TimerPhase.PAUSED) return

        if (current.currentMode != TimerMode.BREAK) {
            FocusSessionRecorder.onResumeSession(
                context = applicationContext,
                currentTotalElapsedMillis = current.pausedAccumulatedMillis,
                modeName = current.currentMode.displayName,
                subjectName = activeSubjectName,
                phoneLiftCount = current.phoneLiftCount,
                screenOnMillis = currentScreenOnMillis()
            )
        }
        updatePresenceInFirestore(true, activeSubjectName)
        val now     = SystemClock.elapsedRealtime()
        val resumed = current.copy(
            timerPhase            = TimerPhase.FOCUSING,
            startTimestampElapsed = now,
        )
        _timerStateFlow.value = resumed
        afkSensor.start()
        acquireWakeLock()
        SessionManager.setTimerRunning(true) // Resumed — re-engage blocker
        startTicker()
        startWatchdog()
        notificationManager.notify(NOTIFICATION_ID, buildNotification(resumed))
    }

    private fun handleStop() {
        updatePresenceInFirestore(false)
        SessionManager.setTimerRunning(false) // Session stopped
        FocusSessionState.setRunning(false)
        tickerJob?.cancel()
        tickerJob = null
        afkJob?.cancel()
        afkJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        afkSensor.stop()
        releaseWakeLock()
        unregisterScreenReceiverSafe()

        val current = _timerStateFlow.value
        val accurateElapsed = computeAccurateElapsedMillis(current)
        if (lastScreenOnTimestamp > 0) {
            totalScreenOnMillis += System.currentTimeMillis() - lastScreenOnTimestamp
            lastScreenOnTimestamp = 0L
        }
        val shouldPersistSession = current.timerPhase != TimerPhase.IDLE && current.currentMode != TimerMode.BREAK
        val capturedScreenOn = totalScreenOnMillis
        _timerStateFlow.value = TimerState(
            timerPhase                    = TimerPhase.IDLE,
            customPomodoroDurationMinutes = current.customPomodoroDurationMinutes,
            breakDurationMinutes          = current.breakDurationMinutes,
            completedConsecutiveSessions  = current.completedConsecutiveSessions,
        )
        if (shouldPersistSession) {
            saveSessionToRoom(
                modeName = current.currentMode.displayName,
                durationMillis = accurateElapsed,
                phoneLiftCount = current.phoneLiftCount,
                screenOnMillis = capturedScreenOn,
                onComplete = {
                    dismissAllNotifications()
                    stopSelf()
                }
            )
        } else {
            dismissAllNotifications()
            stopSelf()
        }
    }

    private fun handleReset() {
        updatePresenceInFirestore(false)
        SessionManager.setTimerRunning(false) // Session reset
        FocusSessionState.setRunning(false)
        tickerJob?.cancel()
        tickerJob = null
        afkJob?.cancel()
        afkJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        afkSensor.stop()
        releaseWakeLock()
        unregisterScreenReceiverSafe()

        val current = _timerStateFlow.value
        val accurateElapsed = computeAccurateElapsedMillis(current)
        if (lastScreenOnTimestamp > 0) {
            totalScreenOnMillis += System.currentTimeMillis() - lastScreenOnTimestamp
            lastScreenOnTimestamp = 0L
        }
        val shouldPersistSession = current.timerPhase != TimerPhase.IDLE && current.currentMode != TimerMode.BREAK
        val capturedScreenOn = totalScreenOnMillis
        val mode = current.currentMode
        _timerStateFlow.value = TimerState(
            timerPhase                    = TimerPhase.IDLE,
            totalDurationMillis           = when (mode) {
                TimerMode.POMODORO -> current.customPomodoroDurationMinutes * 60_000L
                TimerMode.BREAK    -> current.breakDurationMinutes * 60_000L
                else               -> mode.initialMillis
            },
            currentMode                   = mode,
            customPomodoroDurationMinutes = current.customPomodoroDurationMinutes,
            breakDurationMinutes          = current.breakDurationMinutes,
            completedConsecutiveSessions  = current.completedConsecutiveSessions,
        )
        if (shouldPersistSession) {
            saveSessionToRoom(
                modeName = current.currentMode.displayName,
                durationMillis = accurateElapsed,
                phoneLiftCount = current.phoneLiftCount,
                screenOnMillis = capturedScreenOn,
                onComplete = {
                    dismissAllNotifications()
                    stopSelf()
                }
            )
        } else {
            dismissAllNotifications()
            stopSelf()
        }
    }

    private fun handleConfirmPresence() {
        val current = _timerStateFlow.value
        if (!current.isPresenceCheckActive) return
        val confirmed = current.copy(
            isPresenceCheckActive        = false,
            presenceCheckRemainingMillis = 0L,
        )
        _timerStateFlow.value = confirmed
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(confirmed))
    }

    // ── Ticker ───────────────────────────────────────────────────────────────

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var lastTime = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1000L)
                val now = SystemClock.elapsedRealtime()
                if (now - lastTime >= 1000L) {
                    lastTime = now
                    onTick()
                }
            }
        }
    }
    
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive && _timerStateFlow.value.timerPhase == TimerPhase.FOCUSING) {
                delay(500)
                val blacklist = BlockerPreferences.instance.blacklist.value
                if (blacklist.isNotEmpty()) {
                    val foregroundPackage = UsageStatsHelper.getForegroundPackage(this@FocusTimerService)
                    if (foregroundPackage != null && blacklist.contains(foregroundPackage) && foregroundPackage != packageName) {
                        // 1. Immediately send to Home Screen to collapse the blocked app (Bypasses BAL)
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)

                        // 2. Instantly pop BlockOverlayActivity
                        val blockIntent = Intent(this@FocusTimerService, com.example.sharkmentor.core.blocker.BlockOverlayActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("BLOCKED_PACKAGE", foregroundPackage)
                        }
                        startActivity(blockIntent)
                    }
                }
            }
        }
    }

    private fun onTick() {
        val current = _timerStateFlow.value
        if (!current.isTicking) return

        if (current.timerPhase == TimerPhase.FOCUSING) {
            acquireWakeLock()
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val newElapsed = when {
            current.startTimestampElapsed > 0L ->
                current.pausedAccumulatedMillis + (nowElapsed - current.startTimestampElapsed)
            else -> current.timeElapsedMillis + TICK_MS
        }

        if (current.timerPhase == TimerPhase.FOCUSING && current.currentMode != TimerMode.BREAK) {
            FocusSessionRecorder.onTick(
                context = applicationContext,
                elapsedMillis = newElapsed,
                modeName = current.currentMode.displayName,
                subjectName = activeSubjectName,
                phoneLiftCount = current.phoneLiftCount,
                screenOnMillis = currentScreenOnMillis()
            )
            if ((newElapsed / 15_000L) > (current.timeElapsedMillis / 15_000L)) {
                com.example.sharkmentor.core.coaching.CoachingEngine.flushPendingPresenceIfConnected(applicationContext)
            }
        }

        val newState: TimerState = when (current.timerPhase) {
            TimerPhase.FOCUSING -> when (current.currentMode) {
                TimerMode.STOPWATCH -> handleStopwatchTick(current, newElapsed) ?: return
                TimerMode.BLOCK_90,
                TimerMode.POMODORO  -> handleFocusTick(current, newElapsed) ?: return
                TimerMode.BREAK     -> handleBreakTick(current, newElapsed) ?: return
            }
            TimerPhase.MANDATORY_BREAK -> handleBreakTick(current, newElapsed) ?: return
            else -> return
        }

        _timerStateFlow.value = newState
        notificationManager.notify(NOTIFICATION_ID, buildNotification(newState))
    }

    // ── Mode-specific tick handlers ──────────────────────────────────────────

    private fun handleStopwatchTick(current: TimerState, newElapsed: Long): TimerState? {
        val updatedElapsed = current.copy(timeElapsedMillis = newElapsed)
        val justCrossedInterval = newElapsed > 0L &&
            (newElapsed / ACTIVE_PRESENCE_INTERVAL_MS) > (current.timeElapsedMillis / ACTIVE_PRESENCE_INTERVAL_MS)

        if (justCrossedInterval && !current.isPresenceCheckActive) {
            vibrate()
            postPresenceAlertNotification()
            val triggered = updatedElapsed.copy(
                isPresenceCheckActive        = true,
                presenceCheckRemainingMillis = PRESENCE_GRACE_MS,
            )
            _timerStateFlow.value = triggered
            notificationManager.notify(NOTIFICATION_ID, buildNotification(triggered))
            return null
        }

        if (current.isPresenceCheckActive) {
            val realTickDeltaMs = (newElapsed - current.timeElapsedMillis).coerceAtLeast(TICK_MS)
            val newGrace = (current.presenceCheckRemainingMillis - realTickDeltaMs).coerceAtLeast(0L)
            if (newGrace == 0L) {
                tickerJob?.cancel()
                watchdogJob?.cancel()
                afkSensor.stop()
                releaseWakeLock()
                updatePresenceInFirestore(false)
                SessionManager.setTimerRunning(false)

                val triggerBucket = (current.timeElapsedMillis / ACTIVE_PRESENCE_INTERVAL_MS).coerceAtLeast(1L)
                val maxUnconfirmedElapsed = (triggerBucket * ACTIVE_PRESENCE_INTERVAL_MS) + PRESENCE_GRACE_MS
                val cappedElapsed = minOf(newElapsed, maxUnconfirmedElapsed)
                val expiredAgoMs = (newElapsed - cappedElapsed).coerceAtLeast(0L)
                val actualPauseWallMs = (System.currentTimeMillis() - expiredAgoMs).coerceAtLeast(1L)

                FocusSessionRecorder.onPauseCheckpoint(
                    context = applicationContext,
                    elapsedMillis = cappedElapsed,
                    modeName = current.currentMode.displayName,
                    subjectName = activeSubjectName,
                    phoneLiftCount = current.phoneLiftCount,
                    screenOnMillis = currentScreenOnMillis(),
                    overrideCompletedAtMillis = actualPauseWallMs
                )
                val autoPaused = updatedElapsed.copy(
                    timerPhase                   = TimerPhase.PAUSED,
                    timeElapsedMillis            = cappedElapsed,
                    pausedAccumulatedMillis      = cappedElapsed,
                    startTimestampElapsed        = 0L,
                    isPresenceCheckActive        = false,
                    presenceCheckRemainingMillis = 0L,
                )
                _timerStateFlow.value = autoPaused
                notificationManager.cancel(ALERT_NOTIFICATION_ID)
                notificationManager.notify(NOTIFICATION_ID, buildNotification(autoPaused))
                return null
            }
            return updatedElapsed.copy(presenceCheckRemainingMillis = newGrace)
        }
        return updatedElapsed
    }

    private fun handleFocusTick(current: TimerState, newElapsed: Long): TimerState? {
        if (newElapsed < current.totalDurationMillis) return current.copy(timeElapsedMillis = newElapsed)

        vibrate()
        val newSessions = current.completedConsecutiveSessions + 1

        if (lastScreenOnTimestamp > 0) {
            totalScreenOnMillis += System.currentTimeMillis() - lastScreenOnTimestamp
            lastScreenOnTimestamp = 0L
        }
        val capturedScreenOn = totalScreenOnMillis
        totalScreenOnMillis = 0L
        afkSensor.stop()
        afkJob?.cancel()
        unregisterScreenReceiverSafe()

        return if (newSessions >= MANDATORY_BREAK_AFTER_SESSIONS) {
            saveSessionToRoom(current.currentMode.displayName, current.totalDurationMillis, current.phoneLiftCount, capturedScreenOn)
            val breakMs = current.breakDurationMinutes * 60_000L
            val now     = SystemClock.elapsedRealtime()
            val breakState = current.copy(
                timeElapsedMillis            = 0L,
                totalDurationMillis          = breakMs,
                timerPhase                   = TimerPhase.MANDATORY_BREAK,
                startTimestampElapsed        = now,
                pausedAccumulatedMillis      = 0L,
                currentMode                  = TimerMode.BREAK,
                completedConsecutiveSessions = 0,
                isMandatoryBreakRequired     = true,
                isPresenceCheckActive        = false,
                presenceCheckRemainingMillis = 0L,
                phoneLiftCount               = 0,
            )
            _timerStateFlow.value = breakState
            postBreakStartNotification()
            notificationManager.notify(NOTIFICATION_ID, buildNotification(breakState))
            null
        } else {
            afkSensor.stop()
            releaseWakeLock()
            SessionManager.setTimerRunning(false) // Focus session naturally completed
            FocusSessionState.setRunning(false)
            val done = current.copy(
                timeElapsedMillis            = current.totalDurationMillis,
                timerPhase                   = TimerPhase.IDLE,
                startTimestampElapsed        = 0L,
                completedConsecutiveSessions = newSessions,
            )
            _timerStateFlow.value = done
            tickerJob?.cancel()
            watchdogJob?.cancel()
            saveSessionToRoom(
                modeName = current.currentMode.displayName,
                durationMillis = current.totalDurationMillis,
                phoneLiftCount = current.phoneLiftCount,
                screenOnMillis = capturedScreenOn,
                onComplete = {
                    dismissAllNotifications()
                    stopSelf()
                }
            )
            null
        }
    }

    private fun handleBreakTick(current: TimerState, newElapsed: Long): TimerState? {
        if (newElapsed < current.totalDurationMillis) return current.copy(timeElapsedMillis = newElapsed)

        vibrate()
        releaseWakeLock()
        SessionManager.setTimerRunning(false) // Break over — session fully complete
        val done = current.copy(
            timeElapsedMillis            = current.totalDurationMillis,
            timerPhase                   = TimerPhase.IDLE,
            startTimestampElapsed        = 0L,
            pausedAccumulatedMillis      = 0L,
            isMandatoryBreakRequired     = false,
            completedConsecutiveSessions = 0,
        )
        _timerStateFlow.value = done
        postBreakCompleteNotification()
        tickerJob?.cancel()
        watchdogJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
        return null
    }

    // ── Vibrator ─────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0L, 150L, 100L, 150L, 100L, 300L), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
        } else {
            (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(effect)
        }
    }

    // ── Room persistence ─────────────────────────────────────────────────────

    private fun syncFocusSessionToFirestore(durationSeconds: Long, mode: String, subject: String) {
        try {
            val currentUserId = GoogleServicesHelper.getCurrentUser(context = this)?.uid ?: return
            val db = GoogleServicesHelper.getFirebaseFirestore(this) ?: return

            val sessionData = hashMapOf(
                "durationSeconds" to durationSeconds,
                "mode" to mode, // "Pomodoro", "Stopwatch", "DeepWork"
                "subject" to subject,
                "completedAt" to FieldValue.serverTimestamp()
            )

            db.collection("students").document(currentUserId)
                .collection("focus_sessions")
                .add(sessionData)
                .addOnSuccessListener {
                    Log.d("SharkSync", "Odak seansı Firestore'a kaydedildi ($durationSeconds sn).")
                    
                    db.collection("students").document(currentUserId).update(
                        "totalFocusSeconds", FieldValue.increment(durationSeconds),
                        "lastActiveAt", FieldValue.serverTimestamp()
                    )
                }
                .addOnFailureListener { e ->
                    Log.e("SharkSync", "Odak seansı Firestore'a gönderilemedi: ${e.message}", e)
                }
        } catch (e: Throwable) {
            Log.w("SharkSync", "Odak seansı senkronizasyon hatası: ${e.message}")
        }
    }

    private fun saveSessionToRoom(modeName: String, durationMillis: Long, phoneLiftCount: Int, screenOnMillis: Long, onComplete: (() -> Unit)? = null) {
        FocusSessionRecorder.finalizeSession(
            context = applicationContext,
            elapsedMillis = durationMillis,
            modeName = modeName,
            subjectName = activeSubjectName,
            phoneLiftCount = phoneLiftCount,
            screenOnMillis = screenOnMillis,
            onComplete = {
                com.example.sharkmentor.core.coaching.CoachingEngine.refreshTargetAndLedgerAsync(applicationContext)
                onComplete?.invoke()
            }
        )
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus Timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "SharkMentor aktif odak oturumu bildirimi"
                setShowBadge(false)
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT_ID, "Odak Uyarıları", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Varlık kontrolü, seans tamamlama ve mola geçiş bildirimleri"
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_NUDGE_ID, "Odak Dürtüleri", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "BLOCK_90 sırasında telefon kaldırıldığında gösterilen motivasyon bildirimleri"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(state: TimerState): Notification {
        val timeText  = formatMillis(state.displayMillis)
        val isBreak   = state.timerPhase == TimerPhase.MANDATORY_BREAK || state.currentMode == TimerMode.BREAK
        val modeLabel = if (isBreak) getString(R.string.mode_break) else state.currentMode.displayName
        val statusLabel = when { isBreak -> "Mola"; state.isPaused -> "Duraklatıldı"; else -> "Aktif" }
        val title      = getString(if (isBreak) R.string.service_title_break else R.string.service_title_focus)
        val presenceSuffix = if (state.isPresenceCheckActive) " · Varlık Kontrolü: ${formatMillis(state.presenceCheckRemainingMillis)}" else ""

        val openPi = PendingIntent.getActivity(
            this, REQUEST_OPEN,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val activeShark = AppIconManager.getActiveIcon(this)
        val largeIconBitmap = BitmapFactory.decodeResource(resources, activeShark.iconRes)
        val smallIconRes = if (activeShark.notificationSmallIconRes != 0) activeShark.notificationSmallIconRes else R.drawable.ic_notification_original

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setLargeIcon(largeIconBitmap)
            .setColor(getColor(R.color.shark_blue_primary))
            .setContentTitle(title)
            .setContentText("$statusLabel • $timeText — $modeLabel$presenceSuffix")
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (state.isPresenceCheckActive) {
                    addAction(0, getString(R.string.btn_here_iam), servicePendingIntent(ACTION_CONFIRM_PRESENCE, REQUEST_CONFIRM_PRESENCE))
                }
                if (state.timerPhase != TimerPhase.MANDATORY_BREAK) {
                    val pauseLabel = getString(if (state.isPaused) R.string.btn_resume else R.string.btn_pause)
                    val pauseAction = if (state.isPaused) ACTION_RESUME else ACTION_PAUSE
                    addAction(0, pauseLabel, servicePendingIntent(pauseAction, REQUEST_PAUSE_RESUME))
                }
            }
            .addAction(0, getString(R.string.btn_stop), servicePendingIntent(ACTION_STOP, REQUEST_STOP))
            .build()
    }

    private fun postPresenceAlertNotification() {
        val activeShark = AppIconManager.getActiveIcon(this)
        val largeIconBitmap = BitmapFactory.decodeResource(resources, activeShark.iconRes)
        val smallIconRes = if (activeShark.notificationSmallIconRes != 0) activeShark.notificationSmallIconRes else R.drawable.ic_notification_original

        notificationManager.notify(ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
                .setSmallIcon(smallIconRes)
                .setLargeIcon(largeIconBitmap)
                .setColor(getColor(R.color.shark_blue_primary))
                .setContentTitle(getString(R.string.service_alert_presence_title))
                .setContentText(getString(R.string.service_alert_presence_msg))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, getString(R.string.btn_here_iam), servicePendingIntent(ACTION_CONFIRM_PRESENCE, REQUEST_CONFIRM_PRESENCE))
                .build()
        )
    }

    private fun postBreakStartNotification() {
        val activeShark = AppIconManager.getActiveIcon(this)
        val largeIconBitmap = BitmapFactory.decodeResource(resources, activeShark.iconRes)
        val smallIconRes = if (activeShark.notificationSmallIconRes != 0) activeShark.notificationSmallIconRes else R.drawable.ic_notification_original

        notificationManager.notify(ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
                .setSmallIcon(smallIconRes)
                .setLargeIcon(largeIconBitmap)
                .setColor(getColor(R.color.shark_blue_primary))
                .setContentTitle(getString(R.string.service_alert_break_ready_title))
                .setContentText(getString(R.string.service_alert_break_ready_msg))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun postBreakCompleteNotification() {
        val activeShark = AppIconManager.getActiveIcon(this)
        val largeIconBitmap = BitmapFactory.decodeResource(resources, activeShark.iconRes)
        val smallIconRes = if (activeShark.notificationSmallIconRes != 0) activeShark.notificationSmallIconRes else R.drawable.ic_notification_original

        notificationManager.notify(ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
                .setSmallIcon(smallIconRes)
                .setLargeIcon(largeIconBitmap)
                .setColor(getColor(R.color.shark_blue_primary))
                .setContentTitle(getString(R.string.service_alert_break_done_title))
                .setContentText(getString(R.string.service_alert_break_done_msg))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    /**
     * Posts a randomised motivational heads-up notification when the user lifts their
     * phone during a BLOCK_90 session. Respects [NUDGE_COOLDOWN_MS] debounce.
     */
    private fun postLiftNudgeNotification() {
        val activeShark = AppIconManager.getActiveIcon(this)
        val largeIconBitmap = BitmapFactory.decodeResource(resources, activeShark.iconRes)
        val smallIconRes = if (activeShark.notificationSmallIconRes != 0) activeShark.notificationSmallIconRes else R.drawable.ic_notification_original

        val messages = listOf(
            getString(R.string.nudge_msg_1),
            getString(R.string.nudge_msg_2),
            getString(R.string.nudge_msg_3),
            getString(R.string.nudge_msg_4),
        )
        val message = messages.random()
        notificationManager.notify(NUDGE_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_NUDGE_ID)
                .setSmallIcon(smallIconRes)
                .setLargeIcon(largeIconBitmap)
                .setColor(getColor(R.color.shark_blue_primary))
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, FocusTimerService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        // Intent actions
        const val ACTION_START            = "com.example.sharkmentor.action.START"
        const val ACTION_PAUSE            = "com.example.sharkmentor.action.PAUSE"
        const val ACTION_RESUME           = "com.example.sharkmentor.action.RESUME"
        const val ACTION_STOP             = "com.example.sharkmentor.action.STOP"
        const val ACTION_RESET            = "com.example.sharkmentor.action.RESET"
        const val ACTION_CONFIRM_PRESENCE = "com.example.sharkmentor.action.CONFIRM_PRESENCE"

        // Intent extras
        const val EXTRA_MODE             = "extra_mode"
        const val EXTRA_POMODORO_MINUTES = "extra_pomodoro_minutes"
        const val EXTRA_BREAK_MINUTES    = "extra_break_minutes"
        const val EXTRA_SUBJECT          = "extra_subject"
        const val EXTRA_SHOW_BLOCK_OVERLAY = "extra_show_block_overlay"

        // Notification channels / IDs
        private const val CHANNEL_ID       = "shark_focus_channel"
        private const val CHANNEL_ALERT_ID = "shark_alert_channel"
        private const val CHANNEL_NUDGE_ID = "shark_nudge_channel"
        private const val NOTIFICATION_ID       = 1001
        private const val ALERT_NOTIFICATION_ID  = 1002
        private const val NUDGE_NOTIFICATION_ID  = 1003

        // PendingIntent request codes
        private const val REQUEST_OPEN             = 100
        private const val REQUEST_PAUSE_RESUME     = 101
        private const val REQUEST_STOP             = 102
        private const val REQUEST_CONFIRM_PRESENCE = 103

        // Timer constants
        private const val TICK_MS = 1_000L
        private const val MANDATORY_BREAK_AFTER_SESSIONS = 2
        private const val PRESENCE_CHECK_INTERVAL_MS = 90L * 60L * 1_000L
        private val ACTIVE_PRESENCE_INTERVAL_MS = PRESENCE_CHECK_INTERVAL_MS
        private const val PRESENCE_GRACE_MS = 10L * 60L * 1_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60L * 60L * 1_000L
        private const val WAKE_LOCK_RENEW_INTERVAL_MS = 30L * 60L * 1_000L

        /** 3-minute cooldown between lift nudge notifications during BLOCK_90. */
        private const val NUDGE_COOLDOWN_MS = 3L * 60L * 1_000L

        // Shared state
        private val _timerStateFlow = MutableStateFlow(TimerState())
        val timerStateFlow: StateFlow<TimerState> = _timerStateFlow.asStateFlow()

        // Direct-write settings helpers
        fun updatePomodoroDuration(minutes: Int) {
            _timerStateFlow.value = _timerStateFlow.value.copy(customPomodoroDurationMinutes = minutes.coerceIn(30, 300))
        }
        fun updateBreakDuration(minutes: Int) {
            _timerStateFlow.value = _timerStateFlow.value.copy(breakDurationMinutes = minutes.coerceIn(5, 20))
        }
        fun resetSessions() {
            _timerStateFlow.value = _timerStateFlow.value.copy(completedConsecutiveSessions = 0, isMandatoryBreakRequired = false)
        }

        fun formatMillis(millis: Long): String {
            val totalSec = (millis / 1_000L).coerceAtLeast(0L)
            val h = totalSec / 3_600L
            val m = (totalSec % 3_600L) / 60L
            val s = totalSec % 60L
            return if (h > 0L) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }

        /**
         * Helper for external tools (e.g., PACE, KEEP, PDF Reader) to record focus duration
         * into Room and Firestore without double-counting.
         */
        fun recordExternalFocusSession(
            context: Context,
            toolName: String,
            durationSeconds: Long,
            subjectName: String = "Genel"
        ) {
            FocusSessionRecorder.recordExternalToolSessionAsync(
                context = context,
                toolName = toolName,
                durationSeconds = durationSeconds,
                subjectName = subjectName
            )
        }
    }
}
