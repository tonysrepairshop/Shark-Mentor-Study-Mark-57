package com.example.sharkmentor.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.sharkmentor.MainActivity
import com.example.sharkmentor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-scoped background execution & completion notifier for long-running
 * SharkStudio AI tasks (Podcast synthesis, Flashcard deck generation, and Note/PDF
 * Studio Assistant responses). Keeps work alive even if the user navigates away or
 * backgrounds the app, and notifies with the unified Shark notification icon once done.
 */
object SharkStudioBackgroundHelper {
    const val CHANNEL_ID = "shark_studio_ai_done_channel"
    private const val CHANNEL_NAME = "SharkStudio & SharkAI Tamamlananlar"

    val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun notifyTaskCompleted(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ) {
        val appCtx = context.applicationContext
        runCatching {
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val existing = nm.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Podcast, flash kart ve SharkAI Not/PDF asistanı tamamlandığında bildirim gönderir."
                    }
                    nm.createNotificationChannel(channel)
                }
            }

            val openIntent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                appCtx,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val activeShark = AppIconManager.getActiveIcon(appCtx)
            val smallIconRes = if (activeShark.notificationSmallIconRes != 0) {
                activeShark.notificationSmallIconRes
            } else {
                R.drawable.ic_notification_shark
            }

            val notif = NotificationCompat.Builder(appCtx, CHANNEL_ID)
                .setSmallIcon(smallIconRes)
                .setColor(ContextCompat.getColor(appCtx, R.color.shark_blue_primary))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSubText("SharkStudio CoreLab")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            nm.notify(notificationId, notif)
        }
    }
}
