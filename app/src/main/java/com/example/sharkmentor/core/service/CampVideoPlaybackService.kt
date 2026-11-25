package com.example.sharkmentor.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.MutableContextWrapper
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.widget.FrameLayout
import com.example.sharkmentor.core.coaching.CampDayEpisode
import java.lang.ref.WeakReference
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.sharkmentor.MainActivity
import com.example.sharkmentor.R
import com.example.sharkmentor.core.util.AppIconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CampVideoPlaybackState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val isMinimized: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val activeUrl: String = ""
)

/**
 * Custom [WebView] that keeps HTML5 `<video>` / `<audio>` decoding active when the device screen
 * is locked or the app moves to the background while [allowBackgroundPlayback] is enabled.
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

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (allowBackgroundPlayback && visibility != View.VISIBLE) {
            super.onVisibilityChanged(changedView, View.VISIBLE)
        } else {
            super.onVisibilityChanged(changedView, visibility)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (allowBackgroundPlayback && !hasWindowFocus) {
            super.onWindowFocusChanged(true)
        } else {
            super.onWindowFocusChanged(hasWindowFocus)
        }
    }
}

/**
 * Coordinates background/lock-screen playback between [CampVideoStudyStudioDialog] and [CampVideoPlaybackService].
 */
object CampVideoPlaybackController {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(CampVideoPlaybackState())
    val state: StateFlow<CampVideoPlaybackState> = _state.asStateFlow()

    data class RestorableStudioPayload(
        val campTitle: String,
        val subject: String,
        val activeEpisode: CampDayEpisode,
        val allEpisodes: List<CampDayEpisode>,
        val resourceId: Long? = null
    )

    private const val HIDDEN_HOST_TAG = "shark_bg_video_hidden_host"

    private var contextWrapper: MutableContextWrapper? = null
    private var activeWebView: BackgroundPlayableWebView? = null
    private var hostActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var restorablePayload: RestorableStudioPayload? = null

    private val _restoreRequested = MutableStateFlow(false)
    val restoreRequested: StateFlow<Boolean> = _restoreRequested.asStateFlow()

    private val _isStudioOpen = MutableStateFlow(false)
    val isStudioOpen: StateFlow<Boolean> = _isStudioOpen.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun setStudioOpen(open: Boolean) {
        _isStudioOpen.value = open
        if (!open) _isInPipMode.value = false
    }

    fun setPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun registerStudioPayload(
        hostContext: Context,
        campTitle: String,
        subject: String,
        activeEpisode: CampDayEpisode,
        allEpisodes: List<CampDayEpisode>,
        resourceId: Long? = null
    ) {
        hostContext.findActivity()?.let { hostActivityRef = WeakReference(it) }
        restorablePayload = RestorableStudioPayload(
            campTitle = campTitle,
            subject = subject,
            activeEpisode = activeEpisode,
            allEpisodes = allEpisodes,
            resourceId = resourceId
        )
    }

    fun peekRestorablePayload(): RestorableStudioPayload? = restorablePayload

    fun requestRestoreStudio() {
        if (_state.value.isActive && restorablePayload != null) {
            _restoreRequested.value = true
        }
    }

    fun consumeRestoreRequest() {
        _restoreRequested.value = false
    }

    fun acquireOrCreateWebView(
        hostContext: Context,
        initializer: (BackgroundPlayableWebView) -> Unit
    ): BackgroundPlayableWebView {
        hostContext.findActivity()?.let { hostActivityRef = WeakReference(it) }
        val existing = activeWebView
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            contextWrapper?.baseContext = hostContext
            existing.allowBackgroundPlayback = true
            existing.onResume()
            existing.resumeTimers()
            return existing
        }
        val wrapper = MutableContextWrapper(hostContext)
        val created = BackgroundPlayableWebView(wrapper).apply {
            allowBackgroundPlayback = true
            initializer(this)
        }
        contextWrapper = wrapper
        activeWebView = created
        return created
    }

    fun currentWebView(): BackgroundPlayableWebView? = activeWebView

    fun startOrUpdateSession(
        context: Context,
        title: String,
        subtitle: String,
        url: String,
        isPlaying: Boolean,
        positionSeconds: Int = _state.value.positionSeconds,
        durationSeconds: Int = _state.value.durationSeconds
    ) {
        val prev = _state.value
        val next = prev.copy(
            isActive = true,
            isPlaying = isPlaying,
            title = title.ifBlank { prev.title.ifBlank { "Ders Videosu" } },
            subtitle = subtitle.ifBlank { prev.subtitle.ifBlank { "SharkPlayer" } },
            positionSeconds = positionSeconds.coerceAtLeast(0),
            durationSeconds = durationSeconds.coerceAtLeast(0),
            activeUrl = url.ifBlank { prev.activeUrl }
        )
        _state.value = next
        CampVideoPlaybackService.startOrSync(context.applicationContext)
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
        val changedPlayState = cur.isPlaying != isPlaying
        val changedTitle = cur.title != cleanTitle
        _state.value = cur.copy(
            isPlaying = isPlaying,
            positionSeconds = positionSeconds.coerceAtLeast(0),
            durationSeconds = durationSeconds.coerceAtLeast(0),
            title = cleanTitle
        )
        if (changedPlayState || changedTitle) {
            CampVideoPlaybackService.startOrSync(context.applicationContext)
        }
    }

    fun minimizeToBackground(context: Context) {
        val appCtx = context.applicationContext
        context.findActivity()?.let { hostActivityRef = WeakReference(it) }
        mainHandler.post {
            val wv = activeWebView ?: return@post
            val activity = hostActivityRef?.get()
            val decor = activity?.window?.decorView as? ViewGroup
            (wv.parent as? ViewGroup)?.removeView(wv)
            if (decor != null && !activity.isFinishing && !activity.isDestroyed) {
                // Keep WebView attached to a tiny invisible host inside decorView so Chromium
                // never suspends the HTML5 audio pipeline when the dialog closes or screen locks.
                var hiddenHost = decor.findViewWithTag<FrameLayout>(HIDDEN_HOST_TAG)
                if (hiddenHost == null) {
                    hiddenHost = FrameLayout(activity).apply {
                        tag = HIDDEN_HOST_TAG
                        alpha = 0.01f
                        isClickable = false
                        isFocusable = false
                    }
                    decor.addView(hiddenHost, FrameLayout.LayoutParams(2, 2))
                }
                hiddenHost.removeAllViews()
                hiddenHost.addView(wv, FrameLayout.LayoutParams(2, 2))
                contextWrapper?.baseContext = activity
            } else {
                contextWrapper?.baseContext = appCtx
            }
            wv.visibility = View.VISIBLE
            wv.allowBackgroundPlayback = true
            wv.onResume()
            wv.resumeTimers()
            if (_state.value.isPlaying) {
                runCatching {
                    wv.evaluateJavascript(
                        "(function(){try{window.__sharkUserPaused=false;var v=document.querySelector('video');if(v&&v.paused&&!v.ended){v.play();}}catch(e){}})();",
                        null
                    )
                }
            }
        }
        _state.value = _state.value.copy(isActive = true, isMinimized = true)
        CampVideoPlaybackService.startOrSync(appCtx)
    }

    fun markRestoredToForeground(hostContext: Context) {
        hostContext.findActivity()?.let { hostActivityRef = WeakReference(it) }
        contextWrapper?.baseContext = hostContext
        _restoreRequested.value = false
        _state.value = _state.value.copy(isMinimized = false)
    }

    fun resumeIfPlayingAfterScreenOff() {
        if (!_state.value.isActive || !_state.value.isPlaying) return
        mainHandler.postDelayed({
            if (!_state.value.isActive || !_state.value.isPlaying) return@postDelayed
            val wv = activeWebView ?: return@postDelayed
            wv.allowBackgroundPlayback = true
            wv.onResume()
            wv.resumeTimers()
            runCatching {
                wv.evaluateJavascript(
                    "(function(){try{if(!window.__sharkUserPaused){var v=document.querySelector('video');if(v&&v.paused&&!v.ended){v.play();}}}catch(e){}})();",
                    null
                )
            }
        }, 380L)
    }

    fun togglePlayPause(context: Context) {
        val targetPlay = !_state.value.isPlaying
        setPlaying(context, targetPlay)
    }

    fun setPlaying(context: Context, play: Boolean) {
        mainHandler.post {
            val wv = activeWebView ?: return@post
            wv.allowBackgroundPlayback = true
            wv.onResume()
            wv.resumeTimers()
            val js = if (play) {
                """
                (function(){
                  try {
                    window.__sharkUserPaused = false;
                    var mp = document.getElementById('movie_player');
                    if (mp && typeof mp.playVideo === 'function') { mp.playVideo(); }
                    var vids = document.querySelectorAll('video');
                    for (var i = 0; i < vids.length; i++) { vids[i].play(); }
                    if (window.__sharkSendTelemetryNow) { setTimeout(window.__sharkSendTelemetryNow, 200); }
                  } catch(e) {}
                })();
                """.trimIndent()
            } else {
                """
                (function(){
                  try {
                    window.__sharkUserPaused = true;
                    var mp = document.getElementById('movie_player');
                    if (mp && typeof mp.pauseVideo === 'function') { mp.pauseVideo(); }
                    var vids = document.querySelectorAll('video');
                    for (var i = 0; i < vids.length; i++) { vids[i].pause(); }
                    if (window.__sharkSendTelemetryNow) { setTimeout(window.__sharkSendTelemetryNow, 200); }
                  } catch(e) {}
                })();
                """.trimIndent()
            }
            runCatching { wv.evaluateJavascript(js, null) }
        }
        if (_state.value.isActive) {
            _state.value = _state.value.copy(isPlaying = play)
            CampVideoPlaybackService.startOrSync(context.applicationContext)
        }
    }

    fun seekBySeconds(context: Context, deltaSeconds: Int) {
        mainHandler.post {
            val wv = activeWebView ?: return@post
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
                    } else {
                      var mp = document.getElementById('movie_player');
                      if (mp && typeof mp.getCurrentTime === 'function' && typeof mp.seekTo === 'function') {
                        mp.seekTo(Math.max(0, mp.getCurrentTime() + ($deltaSeconds)), true);
                      }
                    }
                    if (window.__sharkSendTelemetryNow) { setTimeout(window.__sharkSendTelemetryNow, 200); }
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
            CampVideoPlaybackService.startOrSync(context.applicationContext)
        }
    }

    fun seekToSeconds(context: Context, targetSeconds: Int) {
        mainHandler.post {
            val wv = activeWebView ?: return@post
            val safe = targetSeconds.coerceAtLeast(0)
            val js = """
                (function(){
                  try {
                    var v = document.querySelector('video');
                    if (v) { v.currentTime = $safe; }
                    else {
                      var mp = document.getElementById('movie_player');
                      if (mp && typeof mp.seekTo === 'function') { mp.seekTo($safe, true); }
                    }
                    if (window.__sharkSendTelemetryNow) { setTimeout(window.__sharkSendTelemetryNow, 200); }
                  } catch(e) {}
                })();
            """.trimIndent()
            runCatching { wv.evaluateJavascript(js, null) }
        }
        val cur = _state.value
        if (cur.isActive) {
            _state.value = cur.copy(positionSeconds = targetSeconds.coerceAtLeast(0))
            CampVideoPlaybackService.startOrSync(context.applicationContext)
        }
    }

    fun releaseSessionOnly(context: Context) {
        val appCtx = context.applicationContext
        _restoreRequested.value = false
        _isStudioOpen.value = false
        _isInPipMode.value = false
        restorablePayload = null
        _state.value = CampVideoPlaybackState()
        mainHandler.post {
            val wv = activeWebView
            activeWebView = null
            contextWrapper?.baseContext = appCtx
            contextWrapper = null
            val decor = hostActivityRef?.get()?.window?.decorView as? ViewGroup
            decor?.findViewWithTag<FrameLayout>(HIDDEN_HOST_TAG)?.let { hiddenHost ->
                runCatching {
                    hiddenHost.removeAllViews()
                    decor.removeView(hiddenHost)
                }
            }
            if (wv != null) {
                runCatching {
                    wv.allowBackgroundPlayback = false
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.removeJavascriptInterface("SharkStudioBridge")
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.destroy()
                }
            }
        }
    }

    fun stopAndRelease(context: Context) {
        val appCtx = context.applicationContext
        releaseSessionOnly(appCtx)
        CampVideoPlaybackService.stop(appCtx)
    }
}

/**
 * Foreground `mediaPlayback` service providing lock-screen + shade YouTube-style media controls
 * (`10 sn Geri`, `Oynat/Duraklat`, `10 sn İleri`, `Kapat`) and keeping video audio alive when locked.
 */
class CampVideoPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManager
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    @Volatile
    private var isForegroundStarted = false

    companion object {
        const val NOTIFICATION_ID = 2009
        const val CHANNEL_ID = "shark_camp_video_media_channel"

        const val ACTION_SYNC = "com.example.sharkmentor.action.CAMP_VIDEO_SYNC"
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
            val state = CampVideoPlaybackController.state.value
            if (!state.isActive) {
                stop(context)
                return
            }
            val intent = Intent(context, CampVideoPlaybackService::class.java).apply {
                action = ACTION_SYNC
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
            }
            runCatching {
                context.stopService(Intent(context, CampVideoPlaybackService::class.java))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        initMediaSession()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    CampVideoPlaybackController.resumeIfPlayingAfterScreenOff()
                }
            }
        }
        screenOffReceiver = receiver
        runCatching {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }

        val initialState = CampVideoPlaybackController.state.value
        startForegroundCompat(buildMediaNotification(initialState))

        serviceScope.launch {
            CampVideoPlaybackController.state.collectLatest { st ->
                if (!st.isActive) {
                    stopServiceCleanly()
                } else {
                    updateWakeLock(st.isPlaying)
                    updateMediaSessionState(st)
                    if (isForegroundStarted) {
                        notificationManager.notify(NOTIFICATION_ID, buildMediaNotification(st))
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SYNC
        if (action == ACTION_STOP) {
            CampVideoPlaybackController.releaseSessionOnly(applicationContext)
            stopServiceCleanly()
            return START_NOT_STICKY
        }
        if (!isForegroundStarted) {
            startForegroundCompat(buildMediaNotification(CampVideoPlaybackController.state.value))
        }
        when (action) {
            ACTION_STOP -> {
                CampVideoPlaybackController.releaseSessionOnly(applicationContext)
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
        updateMediaSessionState(current)
        startForegroundCompat(buildMediaNotification(current))
        return START_NOT_STICKY
    }

    private fun initMediaSession() {
        val session = MediaSession(this, "SharkCampVideoSession")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                CampVideoPlaybackController.setPlaying(applicationContext, true)
            }

            override fun onPause() {
                CampVideoPlaybackController.setPlaying(applicationContext, false)
            }

            override fun onFastForward() {
                CampVideoPlaybackController.seekBySeconds(applicationContext, 10)
            }

            override fun onRewind() {
                CampVideoPlaybackController.seekBySeconds(applicationContext, -10)
            }

            override fun onSeekTo(pos: Long) {
                CampVideoPlaybackController.seekToSeconds(applicationContext, (pos / 1000L).toInt())
            }

            override fun onStop() {
                CampVideoPlaybackController.stopAndRelease(applicationContext)
                stopServiceCleanly()
            }
        })
        session.isActive = true
        mediaSession = session
    }

    private fun updateMediaSessionState(st: CampVideoPlaybackState) {
        val session = mediaSession ?: return
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, st.title.ifBlank { "Ders Videosu" })
            .putString(MediaMetadata.METADATA_KEY_ARTIST, st.subtitle.ifBlank { "SharkMentor Video" })
        if (st.durationSeconds > 0) {
            metadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, st.durationSeconds * 1000L)
        }
        session.setMetadata(metadataBuilder.build())

        val pbState = if (st.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_REWIND or
            PlaybackState.ACTION_FAST_FORWARD or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_STOP
        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(pbState, st.positionSeconds * 1000L, if (st.isPlaying) 1.0f else 0f)
            .build()
        session.setPlaybackState(playbackState)
    }

    private fun buildMediaNotification(st: CampVideoPlaybackState): Notification {
        val openIntent = Intent(this, com.example.sharkmentor.ui.coaching.CampVideoPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_RESTORE_CAMP_VIDEO", true)
        }
        val openPending = PendingIntent.getActivity(
            this,
            REQ_OPEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewPending = servicePendingIntent(ACTION_REWIND_10, REQ_REW)
        val togglePending = servicePendingIntent(ACTION_TOGGLE_PLAY, REQ_TOGGLE)
        val ffPending = servicePendingIntent(ACTION_FORWARD_10, REQ_FF)
        val stopPending = servicePendingIntent(ACTION_STOP, REQ_STOP)

        val rewAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_rew),
            "-10 sn",
            rewPending
        ).build()

        val playPauseAction = Notification.Action.Builder(
            Icon.createWithResource(
                this,
                if (st.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            ),
            if (st.isPlaying) "Duraklat" else "Oynat",
            togglePending
        ).build()

        val ffAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_ff),
            "+10 sn",
            ffPending
        ).build()

        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Kapat",
            stopPending
        ).build()

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let { token ->
            mediaStyle.setMediaSession(token)
        }

        val activeShark = AppIconManager.getActiveIcon(this)
        val smallIconRes = if (activeShark.notificationSmallIconRes != 0) {
            activeShark.notificationSmallIconRes
        } else {
            R.drawable.ic_notification_original
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(st.title.ifBlank { "Ders Videosu" })
            .setContentText(st.subtitle.ifBlank { "SharkPlayer" })
            .setContentIntent(openPending)
            .setDeleteIntent(stopPending)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(st.isPlaying)
            .setOnlyAlertOnce(true)
            .setStyle(mediaStyle)
            .addAction(rewAction)
            .addAction(playPauseAction)
            .addAction(ffAction)
            .addAction(stopAction)
            .build()
    }

    private fun servicePendingIntent(actionName: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, CampVideoPlaybackService::class.java).apply {
            action = actionName
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (actionName != ACTION_STOP && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
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
        serviceScope.cancel()
        releaseWakeLock()
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        }
        isForegroundStarted = false
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
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
        screenOffReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenOffReceiver = null
        releaseWakeLock()
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
