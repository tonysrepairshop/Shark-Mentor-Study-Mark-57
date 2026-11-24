package com.example.sharkmentor.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.sharkmentor.MainActivity
import com.example.sharkmentor.core.service.FocusTimerService
import com.example.sharkmentor.core.timer.TimerMode
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.core.widget.WidgetUpdateManager
import com.example.sharkmentor.data.focus.FocusSessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class ToggleTimerActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val currentState = FocusTimerService.timerStateFlow.value
        val sessionManager = FocusSessionManager(context)

        when (currentState.timerPhase) {
            TimerPhase.FOCUSING -> {
                val pauseIntent = Intent(context, FocusTimerService::class.java).apply {
                    action = FocusTimerService.ACTION_PAUSE
                }
                runCatching { context.startService(pauseIntent) }
                withTimeoutOrNull(600L) {
                    FocusTimerService.timerStateFlow.first { it.timerPhase == TimerPhase.PAUSED }
                }
            }

            TimerPhase.PAUSED -> {
                val resumeIntent = Intent(context, FocusTimerService::class.java).apply {
                    action = FocusTimerService.ACTION_RESUME
                }
                runCatching { context.startService(resumeIntent) }
                withTimeoutOrNull(600L) {
                    FocusTimerService.timerStateFlow.first { it.timerPhase == TimerPhase.FOCUSING }
                }
            }

            TimerPhase.MANDATORY_BREAK -> {
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_TARGET_ROUTE", "home")
                }
                runCatching { context.startActivity(openIntent) }
            }

            TimerPhase.IDLE -> {
                // Open HomeScreen's Mode + Focus Tag/Label picker instead of silently starting "Genel Odak"
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_TARGET_ROUTE", "home")
                    putExtra("EXTRA_START_FOCUS", true)
                }
                runCatching { context.startActivity(openIntent) }
            }
        }

        WidgetUpdateManager.updateAllWidgets(context)
    }
}
