package com.example.sharkmentor.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.sharkmentor.MainActivity
import com.example.sharkmentor.R
import com.example.sharkmentor.core.util.AppIconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

data class CampVideoPlaybackState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0
)

/**
 * Lightweight [WebView] subclass that keeps HTML5 `<video>` decoding alive when the user presses
 * the phone Home button or locks the screen while the Video Studio stays open.
 * Does NOT interfere with foreground playback or touch focus.
 */
class BackgroundPlayableWebView(context: Context) : WebView(context) {
    @Volatile
    var allowBackgroundPlayback: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (allowBackgroundPlayback && visibility != View.VISIBLE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }
}

/**
 * Zero-contention controller bridging the active [BackgroundPlayableWebView] and [CampVideoPlaybackService].
 *
 * Key Mark 73 fixes:
 * 1. Never spawns a competing Android MediaSession (which previously fought Chromium's internal
 *    WebView audio focus and caused an infinite auto-pause loop!).
 * 2. Starts [CampVideoPlaybackService] lazily ONLY when video actually begins playing (zero open lag).
 * 3. Emits a lightweight [closeRequestedEvents] signal when the user taps "Kapat" in the notification,
 *    avoiding root Dialog recompositions on every telemetry tick.
 */
object CampVideoPlaybackController {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(CampVideoPlaybackState())
    val state: StateFlow<CampVideoPlaybackState> = _state.asStateFlow()

    private val _closeRequestedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeRequestedEvents: SharedFlow<Unit> = _closeRequestedEvents.asSharedFlow()

    @Volatile
    private var webViewRef: WeakReference<WebView>? = null

    @Volatile
    private var serviceStartedForSession = false

    fun attachWebView(webView: WebView?) {
        webViewRef = webView?.let { WeakReference(it) }
    }

    fun prepareSessionMetadata(title: String, subtitle: String) {
        val prev = _state.value
        _state.value = prev.copy(
            isActive = true,
            title = title.ifBlank { prev.title.ifBlank { "Ders Videosu" } },
            subtitle = subtitle.ifBlank { prev.subtitle.ifBlank { "Kamp Video Stüdyosu" } }
        )
    }

    fun onPlaybackTelemetry(
        context: Context,
        isPlaying: Boolean,
        positionSeconds: Int,
        durationSeconds: Int,
        resolvedTitle: String
    ) {
        val cur = _state.value
        if (!cur.isActive) return
        val cleanTitle = resolvedTitle
            .removeSuffix(" - YouTube")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("YouTube", ignoreCase = true) }
            ?: cur.title

        val playStateChanged = cur.isPlaying != isPlaying
        val titleChanged = cur.title != cleanTitle
        _state.value = cur.copy(
            isPlaying = isPlaying,
            positionSeconds = positionSeconds.coerceAtLeast(0),
            durationSeconds = durationSeconds.coerceAtLeast(0),
            title = cleanTitle
        )

        // Lazy-start foreground media playback service ONLY once video actually plays
        if (isPlaying && !serviceStartedForSession) {
            serviceStartedForSession = true
            CampVideoPlaybackService.startOrSync(context.applicationContext)
        } else if (serviceStartedForSession && (playStateChanged || titleChanged)) {
            CampVideoPlaybackService.updateIfRunning(context.applicationContext)
        }
    }

    fun togglePlayPause(context: Context) {
        setPlaying(context, !_state.value.isPlaying)
    }

    fun setPlaying(context: Context, play: Boolean) {
        mainHandler.post {
            val wv = webViewRef?.get() ?: return@post
            val js = if (play) {
                "(function(){try{var v=document.querySelector('video');if(v){v.play();}}catch(e){}})();"
            } else {
                "(function(){try{var v=document.querySelector('video');if(v){v.pause();}}catch(e){}})();"
            }
            runCatching { wv.evaluateJavascript(js, null) }
        }
        if (_state.value.isActive) {
            _state.value = _state.value.copy(isPlaying = play)
            if (serviceStartedForSession) {
                CampVideoPlaybackService.updateIfRunning(context.applicationContext)
            }
        }
    }

    fun seekBySeconds(context: Context, deltaSeconds: Int) {
        mainHandler.post {
            val wv = webViewRef?.get() ?: return@post
            val js = """
                (function(){
                  try {
                    var v = document.querySelector('video');
                    if (v && !isNaN(v.currentTime)) {
                      var target = Math.max(0, v.currentTime + ($deltaSeconds));
                      if (isFinite(v.duration) && v.duration > 0) {
                        target = Math.min(v.duration, target);
                      }
                      v.currentTime = target;
                    }
                  } catch(e) {}
                })();
            """.trimIndent()
            runCatching { wv.evaluateJavascript(js, null) }
        }
        val cur = _state.value
        if (cur.isActive) {
            val nextPos = (cur.positionSeconds + deltaSeconds).coerceAtLeast(0).let { p ->
                if (cur.durationSeconds > 0) p.coerceAtMost(cur.durationSeconds) else p
            }
            _state.value = cur.copy(positionSeconds = nextPos)
        }
    }

    fun requestStopFromNotification(context: Context) {
        stopAndClear(context)
        _closeRequestedEvents.tryEmit(Unit)
    }

    fun stopAndClear(context: Context) {
        val wasStarted = serviceStartedForSession
        serviceStartedForSession = false
        webViewRef = null
        _state.value = CampVideoPlaybackState()
        if (wasStarted) {
            CampVideoPlaybackService.stop(context.applicationContext)
        }
    }
}

/**
 * Ultra-lightweight `mediaPlayback` Foreground Service modeled directly after [ZenAudioService].
 * Provides shade & lock-screen notification controls (`-10 sn`, `Oynat/Duraklat`, `+10 sn`, `Kapat`)
 * and holds a partial CPU wake-lock ONLY while playing so lock-screen playback stays uninterrupted
 * without ever conflicting with Chromium WebView's own audio focus!
 */
class CampVideoPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var isForegroundStarted = false

    companion object {
        const val NOTIFICATION_ID = 2009
        const val CHANNEL_ID = "shark_camp_video_media_channel"

        const val ACTION_START = "com.example.sharkmentor.action.CAMP_VIDEO_START"
        const val ACTION_UPDATE = "com.example.sharkmentor.action.CAMP_VIDEO_UPDATE"
        const val ACTION_TOGGLE_PLAY = "com.example.sharkmentor.action.CAMP_VIDEO_TOGGLE"
        const val ACTION_REWIND_10 = "com.example.sharkmentor.action.CAMP_VIDEO_REW_10"
        const val ACTION_FORWARD_10 = "com.example.sharkmentor.action.CAMP_VIDEO_FF_10"
        const val ACTION_STOP = "com.example.sharkmentor.action.CAMP_VIDEO_STOP"

        private const val REQ_OPEN = 310
        private const val REQ_REW = 311
        private const val REQ_TOGGLE = 312
        private const val REQ_FF = 313
        private const val REQ_STOP = 314

        fun startOrSync(context: Context) {
            if (!CampVideoPlaybackController.state.value.isActive) return
            val intent = Intent(context, CampVideoPlaybackService::class.java).apply {
                action = ACTION_START
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun updateIfRunning(context: Context) {
            if (!CampVideoPlaybackController.state.value.isActive) return
            val intent = Intent(context, CampVideoPlaybackService::class.java).apply {
                action = ACTION_UPDATE
            }
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CampVideoPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        val initialState = CampVideoPlaybackController.state.value
        startForegroundCompat(buildNotification(initialState))

        serviceScope.launch {
            var lastTitle = initialState.title
            var lastPlaying = initialState.isPlaying
            CampVideoPlaybackController.state.collectLatest { st ->
                if (!st.isActive) {
                    stopServiceCleanly()
                } else {
                    updateWakeLock(st.isPlaying)
                    if (isForegroundStarted && (st.title != lastTitle || st.isPlaying != lastPlaying)) {
                        lastTitle = st.title
                        lastPlaying = st.isPlaying
                        notificationManager.notify(NOTIFICATION_ID, buildNotification(st))
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_UPDATE) {
            ACTION_STOP -> {
                CampVideoPlaybackController.requestStopFromNotification(applicationContext)
                stopServiceCleanly()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PLAY -> {
                CampVideoPlaybackController.togglePlayPause(applicationContext)
            }
            ACTION_REWIND_10 -> {
                CampVideoPlaybackController.seekBySeconds(applicationContext, -10)
            }
            ACTION_FORWARD_10 -> {
                CampVideoPlaybackController.seekBySeconds(applicationContext, 10)
            }
        }

        val current = CampVideoPlaybackController.state.value
        if (!current.isActive) {
            stopServiceCleanly()
            return START_NOT_STICKY
        }

        updateWakeLock(current.isPlaying)
        if (!isForegroundStarted) {
            startForegroundCompat(buildNotification(current))
        } else {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(current))
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(st: CampVideoPlaybackState): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            REQ_OPEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activeShark = AppIconManager.getActiveIcon(this)
        val smallIconRes = if (activeShark.notificationSmallIconRes != 0) {
            activeShark.notificationSmallIconRes
        } else {
            R.drawable.ic_notification_original
        }

        val statusPrefix = if (st.isPlaying) "Oynatılıyor" else "Duraklatıldı"
        val sub = st.subtitle.ifBlank { "Kamp Video Stüdyosu" }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setColor(ContextCompat.getColor(this, R.color.shark_blue_primary))
            .setContentTitle(st.title.ifBlank { "Ders Videosu" })
            .setContentText("$statusPrefix • $sub")
            .setContentIntent(openPending)
            .setDeleteIntent(servicePendingIntent(ACTION_STOP, REQ_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(st.isPlaying)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_media_rew,
                "-10 sn",
                servicePendingIntent(ACTION_REWIND_10, REQ_REW)
            )
            .addAction(
                if (st.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (st.isPlaying) "Duraklat" else "Oynat",
                servicePendingIntent(ACTION_TOGGLE_PLAY, REQ_TOGGLE)
            )
            .addAction(
                android.R.drawable.ic_media_ff,
                "+10 sn",
                servicePendingIntent(ACTION_FORWARD_10, REQ_FF)
            )
            .build()
    }

    private fun servicePendingIntent(actionName: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, CampVideoPlaybackService::class.java).apply {
            action = actionName
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
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
        }
    }

    private fun updateWakeLock(shouldHold: Boolean) {
        if (shouldHold) {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(PowerManager::class.java) ?: return
            val wl = wakeLock ?: pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SharkMentor:CampVideoAudioLock"
            ).apply {
                setReferenceCounted(false)
            }.also { wakeLock = it }
            runCatching { wl.acquire(4 * 60 * 60 * 1000L) }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private fun stopServiceCleanly() {
        releaseWakeLock()
        if (isForegroundStarted) {
            runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            isForegroundStarted = false
        }
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ders Videosu Oynatıcı",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Arka plan ders videosu kontrolleri"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
