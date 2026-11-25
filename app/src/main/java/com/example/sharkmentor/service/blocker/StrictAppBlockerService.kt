package com.example.sharkmentor.service.blocker

import android.R
import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.sharkmentor.ui.screens.dopamine.DopamineLockOverlayActivity
import kotlinx.coroutines.*

class StrictAppBlockerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val blacklistedPackages = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",      // TikTok
        "com.google.android.youtube",
        "com.twitter.android",
        "com.facebook.katana",
        "com.netflix.mediaclient"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        startForeground(992, buildNotification())

        if (action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager

            while (isActive) {
                val time = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    time - 1000 * 3,
                    time
                )

                val foregroundApp = stats?.maxByOrNull { it.lastTimeUsed }?.packageName

                if (foregroundApp != null && blacklistedPackages.contains(foregroundApp)) {
                    // Trigger instant lock overlay
                    val lockIntent = Intent(applicationContext, DopamineLockOverlayActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("BLOCKED_PKG", foregroundApp)
                    }
                    startActivity(lockIntent)
                }

                delay(1200) // Poll every 1.2 seconds
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "dopamine_lock_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Dopamin Koruma Kalkanı", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Dopamin Orucu Aktif")
            .setContentText("Dikkat dağıtıcı uygulamalar engelleniyor.")
            .setSmallIcon(com.example.sharkmentor.core.util.AppIconManager.getActiveIcon(this).notificationSmallIconRes)
            .build()
    }

    companion object {
        const val ACTION_TOGGLE_PAUSE = "com.example.sharkmentor.action.TOGGLE_PAUSE"
        const val ACTION_STOP_SERVICE = "com.example.sharkmentor.action.STOP_SERVICE"
        const val ACTION_RESET_VERIFICATION = "com.example.sharkmentor.action.RESET_VERIFICATION"
    }
}
