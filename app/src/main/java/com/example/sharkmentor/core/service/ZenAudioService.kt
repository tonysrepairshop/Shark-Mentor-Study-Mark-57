package com.example.sharkmentor.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.sharkmentor.MainActivity
import com.example.sharkmentor.R
import com.example.sharkmentor.core.audio.ZenAudioManager
import com.example.sharkmentor.core.audio.ZenSound
import com.example.sharkmentor.core.util.AppIconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ZenAudioService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationManager: NotificationManager
    @Volatile
    private var isForegroundStarted = false

    companion object {
        const val NOTIFICATION_ID = 2002
        const val CHANNEL_ID = "shark_zen_audio_channel"

        const val ACTION_PLAY = "com.example.sharkmentor.action.ZEN_PLAY"
        const val ACTION_PAUSE = "com.example.sharkmentor.action.ZEN_PAUSE"
        const val ACTION_RESUME = "com.example.sharkmentor.action.ZEN_RESUME"
        const val ACTION_STOP = "com.example.sharkmentor.action.ZEN_STOP"
        const val ACTION_UPDATE = "com.example.sharkmentor.action.ZEN_UPDATE"

        const val EXTRA_SOUND_ID = "extra_sound_id"

        private const val REQUEST_OPEN = 101
        private const val REQUEST_PAUSE = 102
        private const val REQUEST_RESUME = 103
        private const val REQUEST_STOP = 104

        fun start(context: Context, sound: ZenSound) {
            val intent = Intent(context, ZenAudioService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_SOUND_ID, sound.id)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                android.util.Log.e("ZenAudioService", "Error starting ZenAudioService", e)
            }
        }

        fun notifyStateChanged(context: Context) {
            if (ZenAudioManager.currentSound.value == ZenSound.NONE) {
                stop(context)
                return
            }
            val intent = Intent(context, ZenAudioService::class.java).apply {
                action = ACTION_UPDATE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("ZenAudioService", "Error updating ZenAudioService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ZenAudioService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("ZenAudioService", "Error stopping ZenAudioService", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        // Immediately enter foreground on creation to satisfy Android foreground service contractual guarantee
        val current = ZenAudioManager.currentSound.value
        val isPlaying = ZenAudioManager.isPlaying.value
        val initialNotification = buildNotification(if (current != ZenSound.NONE) current else ZenSound.RAIN, isPlaying)
        startForegroundCompat(initialNotification)

        serviceScope.launch {
            ZenAudioManager.currentSound.collectLatest { sound ->
                if (sound == ZenSound.NONE) {
                    if (isForegroundStarted) {
                        stopServiceGracefully()
                    }
                } else {
                    updateNotification()
                }
            }
        }

        serviceScope.launch {
            ZenAudioManager.isPlaying.collectLatest {
                if (ZenAudioManager.currentSound.value != ZenSound.NONE && isForegroundStarted) {
                    updateNotification()
                }
            }
        }

        serviceScope.launch {
            ZenAudioManager.activeMixLayers.collectLatest {
                if (ZenAudioManager.currentSound.value != ZenSound.NONE && isForegroundStarted) {
                    updateNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_UPDATE
        val soundId = intent?.getStringExtra(EXTRA_SOUND_ID)
        val soundFromExtra = soundId?.let { ZenSound.fromId(it) } ?: ZenSound.NONE
        val currentSound = if (soundFromExtra != ZenSound.NONE) soundFromExtra else ZenAudioManager.currentSound.value

        if (action == ACTION_STOP || (currentSound == ZenSound.NONE && action != ACTION_PLAY)) {
            stopServiceGracefully()
            if (action == ACTION_STOP) {
                ZenAudioManager.stopSound(null)
            }
            return START_NOT_STICKY
        }

        // Always promote/maintain foreground immediately upon starting command
        val isPlaying = ZenAudioManager.isPlaying.value
        val notification = buildNotification(currentSound, isPlaying)
        startForegroundCompat(notification)

        when (action) {
            ACTION_PLAY -> {
                // Audio playing initiated by ZenAudioManager
            }
            ACTION_PAUSE -> {
                ZenAudioManager.pauseSound(this)
            }
            ACTION_RESUME -> {
                ZenAudioManager.resumeSound(this)
            }
            ACTION_UPDATE -> {
                // State already refreshed above
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundStarted = true
        } catch (e: Exception) {
            android.util.Log.e("ZenAudioService", "startForegroundCompat failed", e)
        }
    }

    private fun updateNotification() {
        val sound = ZenAudioManager.currentSound.value
        if (sound == ZenSound.NONE) {
            if (isForegroundStarted) {
                stopServiceGracefully()
            }
            return
        }
        val isPlaying = ZenAudioManager.isPlaying.value
        val notification = buildNotification(sound, isPlaying)
        startForegroundCompat(notification)
    }

    private fun stopServiceGracefully() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            android.util.Log.e("ZenAudioService", "stopForeground error", e)
        }
        isForegroundStarted = false
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SharkMix Odak Sesleri",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Aktif odak ve ambiyans seslerini kontrol etmenizi sağlar"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(sound: ZenSound, isPlaying: Boolean): Notification {
        val title = ZenAudioManager.getActiveDisplayTitle().ifBlank { sound.displayName }
        val summary = ZenAudioManager.getActiveLayersSummary()
        val statusText = if (isPlaying) {
            if (summary.isNotBlank()) "$summary • SharkMusic" else "SharkMix çalıyor • SharkMusic"
        } else {
            "Duraklatıldı • SharkMix"
        }

        val openPi = PendingIntent.getActivity(
            this, REQUEST_OPEN,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activeShark = AppIconManager.getActiveIcon(this)
        val smallIconRes = if (activeShark.notificationSmallIconRes != 0) activeShark.notificationSmallIconRes else R.drawable.ic_notification_original

        val pauseResumeLabel = if (isPlaying) "Duraklat" else "Devam Et"
        val pauseResumeAction = if (isPlaying) ACTION_PAUSE else ACTION_RESUME
        val pauseResumeRequest = if (isPlaying) REQUEST_PAUSE else REQUEST_RESUME

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setColor(ContextCompat.getColor(this, R.color.shark_blue_primary))
            .setContentTitle(title)
            .setContentText(statusText)
            .setSubText("SharkMix • SharkMusic")
            .setContentIntent(openPi)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, pauseResumeLabel, servicePendingIntent(pauseResumeAction, pauseResumeRequest))
            .addAction(0, "Durdur", servicePendingIntent(ACTION_STOP, REQUEST_STOP))

        // Safely extract Bitmap for large icon from vector drawable
        try {
            val drawable = ContextCompat.getDrawable(this, activeShark.previewDrawableRes)
            if (drawable != null) {
                val bitmap = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceIn(48, 128),
                    drawable.intrinsicHeight.coerceIn(48, 128),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                builder.setLargeIcon(bitmap)
            }
        } catch (_: Exception) {}

        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ZenAudioService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
