package com.example.sharkmentor.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

import com.example.sharkmentor.data.focus.FocusSessionManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            val sessionManager = FocusSessionManager(context)

            // Strictly check if there is an ongoing unexpired focus session
            if (sessionManager.isSessionActivelyRunning()) {
                val serviceIntent = Intent(context, AppBlockerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                // Timer was not running: do nothing, leave all apps unlocked
                sessionManager.endSession()
            }

            // Always re-schedule background coaching & reminder workers after reboot
            runCatching {
                com.example.sharkmentor.worker.StreakReminderWorker.schedule(context.applicationContext)
            }
            runCatching {
                com.example.sharkmentor.worker.StudentDossierSyncWorker.schedule(context.applicationContext)
            }
            runCatching {
                com.example.sharkmentor.worker.WeeklyMasteryCheckInWorker.schedule(context.applicationContext)
            }
        }
    }
}
