package com.example.sharkmentor.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.core.service.FocusTimerService
import com.example.sharkmentor.core.timer.TimerMode
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.core.timer.TimerState
import com.example.sharkmentor.core.timer.DndManager
import com.example.sharkmentor.data.focus.FocusSessionManager
import com.example.sharkmentor.core.util.SessionManager
import android.os.Build
import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.example.sharkmentor.data.topbar.FocusSessionState

/**
 * ViewModel for [com.example.sharkmentor.ui.screens.HomeScreen].
 *
 * Responsibilities:
 * 1. Bridge the UI to [FocusTimerService] via action intents.
 * 2. Collect [TimerState] from the service and provide it to the UI.
 * 3. Track the HorizontalPager's current page.
 * 4. Auto-scroll the pager to the BREAK page when a mandatory break is triggered
 *    (i.e. when [TimerState.timerPhase] transitions to [TimerPhase.MANDATORY_BREAK]).
 *
 * Settings mutations (Pomodoro duration, break duration, session reset) are
 * applied directly to [FocusTimerService]'s companion [StateFlow] — no service
 * intent is required, which avoids starting a zombie background service.
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    // ── Pager page (index into TimerMode.entries: 0=STOPWATCH, 1=BLOCK_90, …) ─
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val dndManager = DndManager(application)

    // ── Merged UI state ───────────────────────────────────────────────────────
    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    // ── init: collect timer state ─────────────────────────────────────────────
    init {
        FocusTimerService.timerStateFlow
            .onEach { state ->
                val previous = _timerState.value
                _timerState.value = state

                // Auto-scroll to the BREAK page the moment a mandatory break triggers
                // (transition from any non-MANDATORY_BREAK phase into MANDATORY_BREAK)
                val becameMandatoryBreak =
                    previous.timerPhase != TimerPhase.MANDATORY_BREAK &&
                    state.timerPhase     == TimerPhase.MANDATORY_BREAK
                if (becameMandatoryBreak) {
                    _currentPage.value = TimerMode.entries.indexOf(TimerMode.BREAK)
                }

                val wasActiveSession = previous.timerPhase != TimerPhase.IDLE
                val isActiveSession = state.timerPhase != TimerPhase.IDLE

                // Keep pager locked to the active timer mode whenever a session starts or changes mode
                val startedOrSwitchedMode = isActiveSession && (
                    !wasActiveSession || previous.currentMode != state.currentMode
                )
                if (startedOrSwitchedMode) {
                    val targetIdx = TimerMode.entries.indexOf(state.currentMode)
                    if (targetIdx >= 0) {
                        _currentPage.value = targetIdx
                    }
                }

                val isBreakPhase = state.timerPhase == TimerPhase.MANDATORY_BREAK || (state.currentMode == TimerMode.BREAK && state.timerPhase != TimerPhase.IDLE)
                if (isBreakPhase) {
                    SessionManager.setBreakActive(true)
                } else if (state.timerPhase == TimerPhase.FOCUSING) {
                    SessionManager.setTimerRunning(true)
                } else if (state.timerPhase == TimerPhase.IDLE) {
                    SessionManager.setTimerRunning(false)
                    FocusSessionState.setRunning(false)
                    SessionManager.setBreakActive(false)
                }

                if (!wasActiveSession && isActiveSession && state.currentMode != TimerMode.BREAK) {
                    val sessionDurationMinutes = if (state.currentMode == TimerMode.STOPWATCH) {
                        24 * 60
                    } else {
                        (state.displayMillis / 60000L).coerceAtLeast(1L).toInt()
                    }
                    val resolvedSubject = state.activeSubject.takeIf { it.isNotBlank() && it != "Genel" }
                        ?: runCatching {
                            com.example.sharkmentor.core.coaching.CoachingEngine.ensureInitialized(getApplication())
                            com.example.sharkmentor.core.coaching.CoachingEngine.buildFormattedFocusSubject()
                        }.getOrDefault("Genel")
                    startFocusSession(sessionDurationMinutes, resolvedSubject)
                } else if (wasActiveSession && !isActiveSession) {
                    stopOrCancelFocusSession()
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Timer control actions ─────────────────────────────────────────────────

    /**
     * Start (or restart) the timer in the given [mode].
     * Passes the current custom durations as extras so the service uses them
     * even after a fresh service instance is created.
     * MUST be called while the app is foregrounded.
     */
    fun startTimer(mode: TimerMode, subject: String = "") {
        val state = _timerState.value
        val resolvedSubject = subject.trim().takeIf { it.isNotBlank() }
            ?: runCatching {
                com.example.sharkmentor.core.coaching.CoachingEngine.ensureInitialized(getApplication())
                com.example.sharkmentor.core.coaching.CoachingEngine.buildFormattedFocusSubject()
            }.getOrDefault("Genel")

        // Optimistic UI update for immediate response
        _timerState.value = state.copy(
            currentMode = mode,
            timerPhase = TimerPhase.FOCUSING,
            activeSubject = resolvedSubject
        )

        val intent = Intent(getApplication(), FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_START
            putExtra(FocusTimerService.EXTRA_MODE,             mode.name)
            putExtra(FocusTimerService.EXTRA_POMODORO_MINUTES, state.customPomodoroDurationMinutes)
            putExtra(FocusTimerService.EXTRA_BREAK_MINUTES,    state.breakDurationMinutes)
            putExtra(FocusTimerService.EXTRA_SUBJECT,          resolvedSubject)
        }
        
        if (mode == TimerMode.BLOCK_90) {
            if (!dndManager.isDndPermissionGranted()) {
                dndManager.requestDndPermission()
            }
        }
        
        getApplication<Application>().startForegroundService(intent)
        FocusSessionState.setRunning(true)
    }

    /** Pause the running timer. No-op if not in FOCUSING phase. */
    fun pauseTimer() {
        val currentState = _timerState.value
        if (currentState.timerPhase == TimerPhase.FOCUSING) {
            _timerState.value = currentState.copy(timerPhase = TimerPhase.PAUSED)
        }
        sendCommand(FocusTimerService.ACTION_PAUSE)
        FocusSessionState.setRunning(false)
    }

    /** Resume a paused timer. No-op if not in PAUSED phase. */
    fun resumeTimer() {
        val currentState = _timerState.value
        if (currentState.timerPhase == TimerPhase.PAUSED) {
            _timerState.value = currentState.copy(timerPhase = TimerPhase.FOCUSING)
        }
        sendCommand(FocusTimerService.ACTION_RESUME)
        FocusSessionState.setRunning(true)
    }

    /** Reset the timer and stop the service; preserves mode and custom durations. */
    fun resetTimer() {
        val currentState = _timerState.value
        _timerState.value = currentState.copy(
            timerPhase = TimerPhase.IDLE,
            timeElapsedMillis = 0L,
            startTimestampElapsed = 0L,
            pausedAccumulatedMillis = 0L
        )
        sendCommand(FocusTimerService.ACTION_RESET)
        FocusSessionState.setRunning(false)
    }

    /** Stop the timer immediately and dismiss the notification. */
    fun stopTimer() { FocusSessionState.setRunning(false); sendCommand(FocusTimerService.ACTION_STOP) }

    /**
     * Confirm that the user is present during a STOPWATCH presence check.
     * Clears the 10-minute grace period and dismisses the alert notification via the service.
     */
    fun confirmPresence() = sendCommand(FocusTimerService.ACTION_CONFIRM_PRESENCE)

    // ── Settings mutations (direct companion writes — no service needed) ───────

    /**
     * Set the POMODORO session length. Clamped to 30..300 minutes.
     * Takes effect on the next [startTimer] call; does not interrupt an active session.
     */
    fun setCustomPomodoroDuration(minutes: Int) {
        FocusTimerService.updatePomodoroDuration(minutes)
    }

    /**
     * Set the BREAK duration. Clamped to 5..20 minutes.
     * Takes effect on the next BREAK start.
     */
    fun setBreakDuration(minutes: Int) {
        FocusTimerService.updateBreakDuration(minutes)
    }

    /**
     * Manually clear the consecutive-session counter and dismiss the mandatory-break flag.
     * Use when the user explicitly declines a mandatory break.
     */
    fun resetConsecutiveSessions() {
        FocusTimerService.resetSessions()
    }

    // ── Pager sync ────────────────────────────────────────────────────────────

    /**
     * Called by the HorizontalPager [LaunchedEffect] on every page swipe.
     * Does NOT start or stop the timer.
     */
    fun onPageChanged(page: Int) {
        _currentPage.value = page
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Sends a control command to the already-running [FocusTimerService] via
     * plain [startService].  Using startService (not startForegroundService) for
     * non-START actions is correct — the service is already in the foreground.
     */
    private fun sendCommand(action: String) {
        val intent = Intent(getApplication(), FocusTimerService::class.java).apply {
            this.action = action
        }
        getApplication<Application>().startService(intent)
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun startFocusSession(durationMinutes: Int, subject: String) {
        val context = getApplication<Application>()
        val sessionManager = FocusSessionManager(context)
        val cleanSubject = subject.trim().ifBlank { "Genel Odak" }
        sessionManager.startSession(durationMinutes, cleanSubject)

        com.example.sharkmentor.core.coaching.CoachingEngine.updateLivePresenceOfflineFirst(
            context = context,
            isStudying = true,
            subject = cleanSubject
        )

        // Notify SessionManager
        SessionManager.setTimerRunning(true)
        FocusSessionState.setRunning(true)
    }

    fun stopOrCancelFocusSession() {
        val context = getApplication<Application>()
        val sessionManager = FocusSessionManager(context)
        sessionManager.endSession()

        com.example.sharkmentor.core.coaching.CoachingEngine.updateLivePresenceOfflineFirst(
            context = context,
            isStudying = false
        )

        // Notify SessionManager
        SessionManager.setTimerRunning(false)
        FocusSessionState.setRunning(false)
    }

    override fun onCleared() {
        super.onCleared()
        val phase = FocusTimerService.timerStateFlow.value.timerPhase
        if (phase != TimerPhase.FOCUSING && phase != TimerPhase.MANDATORY_BREAK) {
            FocusSessionState.setRunning(false)
        }
    }
}
