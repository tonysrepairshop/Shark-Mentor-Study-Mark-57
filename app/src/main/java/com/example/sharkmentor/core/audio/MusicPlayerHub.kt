package com.example.sharkmentor.core.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class MusicPlayerSkin(val id: String, val displayTitle: String) {
    DEFAULT("default", "Default"),
    MODERN_VINYL("modern_vinyl", "Modern Vinyl"),
    CLASSIC_VINYL("classic_vinyl", "Classic Vinyl"),
    CD("cd", "CD"),
    CASSETTE("cassette", "Cassette");

    companion object {
        fun fromId(raw: String?): MusicPlayerSkin =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: MODERN_VINYL
    }
}

enum class MusicSourceKind {
    NONE,
    EXTERNAL_MEDIA,
    ZEN_AUDIO
}

@Immutable
data class MusicQueueItem(
    val queueId: Long,
    val title: String,
    val subtitle: String = ""
)

@Immutable
data class MusicPlaybackState(
    val hasActiveSession: Boolean = false,
    val isPlaying: Boolean = false,
    val sourceKind: MusicSourceKind = MusicSourceKind.NONE,
    val sourcePackage: String = "",
    val sourceLabel: String = "",
    val title: String = "",
    val artist: String = "",
    val subtitleLine: String = "",
    val artwork: Bitmap? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastPositionUpdateElapsedMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val queueTitles: List<String> = emptyList(),
    val queueItems: List<MusicQueueItem> = emptyList()
) {
    fun estimateCurrentPositionMs(nowElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()): Long {
        if (durationMs <= 0L) return positionMs.coerceAtLeast(0L)
        if (!isPlaying || lastPositionUpdateElapsedMs <= 0L) {
            return positionMs.coerceIn(0L, durationMs)
        }
        val delta = ((nowElapsedRealtimeMs - lastPositionUpdateElapsedMs).coerceAtLeast(0L) * playbackSpeed).toLong()
        return (positionMs + delta).coerceIn(0L, durationMs)
    }
}

fun shouldShowLeftMusicScreen(
    isFeatureEnabled: Boolean,
    playbackState: MusicPlaybackState
): Boolean {
    return isFeatureEnabled && playbackState.hasActiveSession
}

fun formatMusicDurationLabel(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%02d:%02d".format(mins, secs)
}

/**
 * Lightweight Android NotificationListenerService used solely to observe active
 * MediaSessions from Spotify, YouTube Music, and system music players with zero network/API cost.
 */
class SharkMediaSessionListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MusicPlayerHub.onNotificationListenerConnected(applicationContext)
    }

    override fun onListenerDisconnected() {
        MusicPlayerHub.onNotificationListenerDisconnected()
        super.onListenerDisconnected()
    }
}

/**
 * Unified Music & Zen Audio hub powering the Left-of-HomeScreen Turntable/CD/Cassette deck.
 * Bridges Spotify (`com.spotify.music`), YouTube Music (`com.google.android.apps.youtube.music`),
 * system media players, and in-app [ZenAudioManager] with zero Firestore traffic.
 */
object MusicPlayerHub {

    const val PKG_SPOTIFY = "com.spotify.music"
    const val PKG_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

    private const val PREFS_NAME = "shark_music_player_prefs"
    private const val KEY_ENABLED = "music_player_enabled"
    private const val KEY_SKIN = "music_player_skin"
    private const val KEY_THEME_SYNC_PACK = "music_player_theme_sync_pack"
    private const val KEY_SPOTIFY_LINKED = "spotify_shortcut_linked"
    const val THEME_SYNC_PACK_COUNT = 3
    private const val KEY_YTM_LINKED = "ytm_shortcut_linked"

    private val hubScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var initialized = false
    private var appContextRef: Context? = null

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isHomePanelOpen = MutableStateFlow(false)
    val isHomePanelOpen: StateFlow<Boolean> = _isHomePanelOpen.asStateFlow()

    private val _isFloatingMiniOpen = MutableStateFlow(false)
    val isFloatingMiniOpen: StateFlow<Boolean> = _isFloatingMiniOpen.asStateFlow()

    fun toggleHomePanel() {
        _isHomePanelOpen.value = !_isHomePanelOpen.value
    }

    fun setHomePanelOpen(open: Boolean) {
        _isHomePanelOpen.value = open
    }

    fun toggleFloatingMini() {
        _isFloatingMiniOpen.value = !_isFloatingMiniOpen.value
    }

    fun setFloatingMiniOpen(open: Boolean) {
        _isFloatingMiniOpen.value = open
    }

    private val _selectedSkin = MutableStateFlow(MusicPlayerSkin.MODERN_VINYL)
    val selectedSkin: StateFlow<MusicPlayerSkin> = _selectedSkin.asStateFlow()

    // 0 = Original player skin colors, 1..3 = App Theme Sync Packs 1..3
    private val _themeSyncPackIndex = MutableStateFlow(0)
    val themeSyncPackIndex: StateFlow<Int> = _themeSyncPackIndex.asStateFlow()

    private val _mediaAccessGranted = MutableStateFlow(false)
    val mediaAccessGranted: StateFlow<Boolean> = _mediaAccessGranted.asStateFlow()

    private val _spotifyConnected = MutableStateFlow(false)
    val spotifyConnected: StateFlow<Boolean> = _spotifyConnected.asStateFlow()

    private val _ytMusicConnected = MutableStateFlow(false)
    val ytMusicConnected: StateFlow<Boolean> = _ytMusicConnected.asStateFlow()

    private val _playbackState = MutableStateFlow(MusicPlaybackState())
    val playbackState: StateFlow<MusicPlaybackState> = _playbackState.asStateFlow()

    private var activeSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var observedControllers: List<MediaController> = emptyList()
    private var primaryExternalController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            recomputeMergedState()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            recomputeMergedState()
        }

        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
            recomputeMergedState()
        }

        override fun onSessionDestroyed() {
            appContextRef?.let { refreshExternalControllers(it) }
        }
    }

    fun ensureInitialized(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        _mediaAccessGranted.value = checkNotificationListenerEnabled(appCtx)

        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    _isEnabled.value = prefs.getBoolean(KEY_ENABLED, true)
                    _selectedSkin.value = MusicPlayerSkin.fromId(prefs.getString(KEY_SKIN, MusicPlayerSkin.MODERN_VINYL.id))
                    _themeSyncPackIndex.value = prefs.getInt(KEY_THEME_SYNC_PACK, 0).coerceIn(0, THEME_SYNC_PACK_COUNT)
                    _spotifyConnected.value = prefs.getBoolean(KEY_SPOTIFY_LINKED, isPackageInstalled(appCtx, PKG_SPOTIFY))
                    _ytMusicConnected.value = prefs.getBoolean(KEY_YTM_LINKED, isPackageInstalled(appCtx, PKG_YOUTUBE_MUSIC))
                    initialized = true

                    hubScope.launch {
                        ZenAudioManager.currentSound.collectLatest {
                            recomputeMergedState()
                        }
                    }
                    hubScope.launch {
                        ZenAudioManager.isPlaying.collectLatest {
                            recomputeMergedState()
                        }
                    }
                    hubScope.launch {
                        ZenAudioManager.activeMixLayers.collectLatest {
                            recomputeMergedState()
                        }
                    }
                }
            }
        }

        if (_mediaAccessGranted.value) {
            registerActiveSessionsObserver(appCtx)
            refreshExternalControllers(appCtx)
        } else {
            recomputeMergedState()
        }
    }

    fun refreshPermissionsAndSessions(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        val granted = checkNotificationListenerEnabled(appCtx)
        _mediaAccessGranted.value = granted
        if (granted) {
            registerActiveSessionsObserver(appCtx)
            refreshExternalControllers(appCtx)
        } else {
            clearExternalControllers()
            recomputeMergedState()
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        _isEnabled.value = enabled
        if (enabled) {
            refreshPermissionsAndSessions(context)
        } else {
            _isHomePanelOpen.value = false
            _isFloatingMiniOpen.value = false
        }
    }

    fun setSkin(context: Context, skin: MusicPlayerSkin) {
        ensureInitialized(context)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIN, skin.id).apply()
        _selectedSkin.value = skin
    }

    fun setThemeSyncPack(context: Context, packIndex: Int) {
        ensureInitialized(context)
        val clamped = packIndex.coerceIn(0, THEME_SYNC_PACK_COUNT)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME_SYNC_PACK, clamped).apply()
        _themeSyncPackIndex.value = clamped
    }

    fun cycleThemeSyncPack(context: Context): Int {
        ensureInitialized(context)
        val next = (_themeSyncPackIndex.value + 1) % (THEME_SYNC_PACK_COUNT + 1)
        setThemeSyncPack(context, next)
        return next
    }

    fun checkNotificationListenerEnabled(context: Context): Boolean {
        val flat = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        }.getOrNull().orEmpty()
        return flat.contains(context.packageName)
    }

    fun openMediaAccessSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun isPackageInstalled(context: Context, pkg: String): Boolean {
        return runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }.getOrDefault(false)
    }

    fun connectMusicProvider(context: Context, packageName: String) {
        ensureInitialized(context)
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!_isEnabled.value) {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            _isEnabled.value = true
        }
        if (packageName == PKG_SPOTIFY) {
            prefs.edit().putBoolean(KEY_SPOTIFY_LINKED, true).apply()
            _spotifyConnected.value = true
        } else if (packageName == PKG_YOUTUBE_MUSIC) {
            prefs.edit().putBoolean(KEY_YTM_LINKED, true).apply()
            _ytMusicConnected.value = true
        }

        if (!checkNotificationListenerEnabled(appCtx)) {
            openMediaAccessSettings(context)
            return
        }

        refreshPermissionsAndSessions(appCtx)
        val launchIntent = appCtx.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launchIntent) }
        } else {
            val webFallback = if (packageName == PKG_SPOTIFY) {
                "https://open.spotify.com"
            } else {
                "https://music.youtube.com"
            }
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(webFallback)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

    fun openActiveSourceApp(context: Context) {
        val state = _playbackState.value
        if (state.sourceKind == MusicSourceKind.EXTERNAL_MEDIA && state.sourcePackage.isNotBlank()) {
            val intent = context.packageManager.getLaunchIntentForPackage(state.sourcePackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        }
    }

    internal fun onNotificationListenerConnected(context: Context) {
        _mediaAccessGranted.value = true
        registerActiveSessionsObserver(context)
        refreshExternalControllers(context)
    }

    internal fun onNotificationListenerDisconnected() {
        _mediaAccessGranted.value = false
        clearExternalControllers()
        recomputeMergedState()
    }

    private fun registerActiveSessionsObserver(context: Context) {
        if (activeSessionsListener != null) return
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return
        val comp = ComponentName(context, SharkMediaSessionListenerService::class.java)
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindControllers(context, controllers.orEmpty())
        }
        runCatching {
            msm.addOnActiveSessionsChangedListener(listener, comp, mainHandler)
            activeSessionsListener = listener
        }
    }

    private fun refreshExternalControllers(context: Context) {
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return
        val comp = ComponentName(context, SharkMediaSessionListenerService::class.java)
        val list = runCatching { msm.getActiveSessions(comp) }.getOrNull().orEmpty()
        bindControllers(context, list)
    }

    private fun clearExternalControllers() {
        observedControllers.forEach { ctrl ->
            runCatching { ctrl.unregisterCallback(controllerCallback) }
        }
        observedControllers = emptyList()
        primaryExternalController = null
    }

    private fun bindControllers(context: Context, rawList: List<MediaController>) {
        observedControllers.forEach { ctrl ->
            runCatching { ctrl.unregisterCallback(controllerCallback) }
        }
        val externalOnly = rawList.filter { it.packageName != context.packageName }
        externalOnly.forEach { ctrl ->
            runCatching { ctrl.registerCallback(controllerCallback, mainHandler) }
        }
        observedControllers = externalOnly
        recomputeMergedState()
    }

    private fun scoreController(ctrl: MediaController): Int {
        val pb = ctrl.playbackState?.state ?: PlaybackState.STATE_NONE
        val pkg = ctrl.packageName.orEmpty()
        val isPreferredApp = pkg == PKG_SPOTIFY || pkg == PKG_YOUTUBE_MUSIC || pkg.contains("music", ignoreCase = true) || pkg.contains("player", ignoreCase = true)
        val stateScore = when (pb) {
            PlaybackState.STATE_PLAYING -> 100
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> 75
            PlaybackState.STATE_PAUSED -> 40
            else -> 0
        }
        return stateScore + (if (isPreferredApp) 15 else 0)
    }

    private fun recomputeMergedState() {
        val bestExternal = observedControllers
            .filter { ctrl ->
                val st = ctrl.playbackState?.state ?: PlaybackState.STATE_NONE
                val hasTrack = !ctrl.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
                st == PlaybackState.STATE_PLAYING ||
                    st == PlaybackState.STATE_BUFFERING ||
                    (st == PlaybackState.STATE_PAUSED && hasTrack)
            }
            .maxByOrNull { scoreController(it) }

        primaryExternalController = bestExternal
        val extPlaying = bestExternal?.playbackState?.state == PlaybackState.STATE_PLAYING ||
            bestExternal?.playbackState?.state == PlaybackState.STATE_BUFFERING

        val zenSound = ZenAudioManager.currentSound.value
        val zenActive = zenSound != ZenSound.NONE
        val zenPlaying = zenActive && ZenAudioManager.isPlaying.value

        when {
            bestExternal != null && (extPlaying || !zenPlaying) -> {
                val meta = bestExternal.metadata
                val pb = bestExternal.playbackState
                val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?: "SharkMusic"
                val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?: friendlySourceName(bestExternal.packageName)
                val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() }
                val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                val duration = (meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
                val pos = (pb?.position ?: 0L).coerceAtLeast(0L)
                val lastUpdate = pb?.lastPositionUpdateTime?.takeIf { it > 0L } ?: SystemClock.elapsedRealtime()
                val speed = pb?.playbackSpeed?.takeIf { it > 0f } ?: 1f
                val srcName = friendlySourceName(bestExternal.packageName)

                val structuredQueue = extractUpcomingQueue(bestExternal)

                _playbackState.value = MusicPlaybackState(
                    hasActiveSession = true,
                    isPlaying = extPlaying,
                    sourceKind = MusicSourceKind.EXTERNAL_MEDIA,
                    sourcePackage = bestExternal.packageName,
                    sourceLabel = srcName,
                    title = title,
                    artist = artist,
                    subtitleLine = album?.let { "$srcName • $it" } ?: "$srcName • Odak Oturumu",
                    artwork = art,
                    positionMs = pos,
                    durationMs = duration,
                    lastPositionUpdateElapsedMs = lastUpdate,
                    playbackSpeed = speed,
                    queueTitles = structuredQueue.map { it.title },
                    queueItems = structuredQueue
                )
            }

            zenActive -> {
                val layerCount = ZenAudioManager.activeMixLayers.value.size
                _playbackState.value = MusicPlaybackState(
                    hasActiveSession = true,
                    isPlaying = zenPlaying,
                    sourceKind = MusicSourceKind.ZEN_AUDIO,
                    sourcePackage = appContextRef?.packageName.orEmpty(),
                    sourceLabel = "SharkMix",
                    title = ZenAudioManager.getActiveDisplayTitle(),
                    artist = ZenAudioManager.getActiveLayersSummary().ifBlank { "SharkMix Ortam Sesi" },
                    subtitleLine = if (layerCount > 1) "SharkMix • $layerCount Katman Aktif" else "SharkMix • Kesintisiz Ortam Sesi",
                    artwork = null,
                    positionMs = 0L,
                    durationMs = 0L,
                    lastPositionUpdateElapsedMs = SystemClock.elapsedRealtime(),
                    playbackSpeed = 1f
                )
            }

            else -> {
                _playbackState.value = MusicPlaybackState(hasActiveSession = false)
            }
        }
    }

    private fun extractUpcomingQueue(controller: MediaController): List<MusicQueueItem> {
        val rawQueue = runCatching { controller.queue }.getOrNull().orEmpty()
        if (rawQueue.isEmpty()) return emptyList()
        val activeId = controller.playbackState?.activeQueueItemId ?: MediaSession.QueueItem.UNKNOWN_ID.toLong()
        val activeIndex = if (activeId != MediaSession.QueueItem.UNKNOWN_ID.toLong()) {
            rawQueue.indexOfFirst { it.queueId == activeId }
        } else {
            -1
        }
        val slice = if (activeIndex >= 0 && activeIndex + 1 < rawQueue.size) {
            rawQueue.subList(activeIndex + 1, rawQueue.size)
        } else {
            rawQueue
        }
        return slice.mapNotNull { item ->
            val desc = item.description ?: return@mapNotNull null
            val qTitle = desc.title?.toString()?.trim().orEmpty()
            if (qTitle.isBlank()) return@mapNotNull null
            val qSub = desc.subtitle?.toString()?.trim().orEmpty()
            MusicQueueItem(queueId = item.queueId, title = qTitle, subtitle = qSub)
        }.take(12)
    }

    private fun friendlySourceName(pkg: String): String = when {
        pkg == PKG_SPOTIFY -> "Spotify"
        pkg == PKG_YOUTUBE_MUSIC -> "YouTube Music"
        pkg.contains("youtube", ignoreCase = true) -> "YouTube"
        pkg.contains("apple", ignoreCase = true) -> "Apple Music"
        pkg.contains("miui", ignoreCase = true) -> "Müzik"
        pkg.contains("samsung", ignoreCase = true) -> "Samsung Music"
        else -> "Medya Oynatıcı"
    }

    // ── Transport, Queue & Volume Controls ────────────────────────────────────

    fun openMusicProvider(context: Context, packageName: String) {
        connectMusicProvider(context, packageName)
    }

    fun skipToQueueItem(queueId: Long) {
        val ctrl = primaryExternalController ?: return
        runCatching { ctrl.transportControls.skipToQueueItem(queueId) }
    }

    fun getDeviceVolumeFraction(context: Context): Float {
        val am = context.getSystemService(AudioManager::class.java) ?: return 0.5f
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVol)
        return (curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
    }

    fun setDeviceVolumeFraction(context: Context, fraction: Float) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = (fraction.coerceIn(0f, 1f) * maxVol).toInt().coerceIn(0, maxVol)
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
    }

    fun togglePlayPause(context: Context) {
        val current = _playbackState.value
        when (current.sourceKind) {
            MusicSourceKind.EXTERNAL_MEDIA -> {
                val ctrl = primaryExternalController ?: return
                if (current.isPlaying) {
                    runCatching { ctrl.transportControls.pause() }
                } else {
                    runCatching { ctrl.transportControls.play() }
                }
            }
            MusicSourceKind.ZEN_AUDIO -> {
                if (current.isPlaying) {
                    ZenAudioManager.pauseSound(context)
                } else {
                    ZenAudioManager.resumeSound(context)
                }
            }
            MusicSourceKind.NONE -> Unit
        }
    }

    fun skipNext(context: Context) {
        val current = _playbackState.value
        when (current.sourceKind) {
            MusicSourceKind.EXTERNAL_MEDIA -> {
                runCatching { primaryExternalController?.transportControls?.skipToNext() }
            }
            MusicSourceKind.ZEN_AUDIO -> {
                if (ZenAudioManager.activePresetId.value != null || ZenAudioManager.activeMixLayers.value.size > 1) {
                    ZenAudioManager.cyclePreset(context, forward = true)
                } else {
                    val playable = ZenSound.entries.filter { it != ZenSound.NONE }
                    if (playable.isNotEmpty()) {
                        val idx = playable.indexOf(ZenAudioManager.currentSound.value).coerceAtLeast(0)
                        val next = playable[(idx + 1) % playable.size]
                        ZenAudioManager.playSound(context, next)
                    }
                }
            }
            MusicSourceKind.NONE -> Unit
        }
    }

    fun skipPrevious(context: Context) {
        val current = _playbackState.value
        when (current.sourceKind) {
            MusicSourceKind.EXTERNAL_MEDIA -> {
                runCatching { primaryExternalController?.transportControls?.skipToPrevious() }
            }
            MusicSourceKind.ZEN_AUDIO -> {
                if (ZenAudioManager.activePresetId.value != null || ZenAudioManager.activeMixLayers.value.size > 1) {
                    ZenAudioManager.cyclePreset(context, forward = false)
                } else {
                    val playable = ZenSound.entries.filter { it != ZenSound.NONE }
                    if (playable.isNotEmpty()) {
                        val idx = playable.indexOf(ZenAudioManager.currentSound.value).coerceAtLeast(0)
                        val prev = playable[(idx - 1 + playable.size) % playable.size]
                        ZenAudioManager.playSound(context, prev)
                    }
                }
            }
            MusicSourceKind.NONE -> Unit
        }
    }

    fun seekToFraction(fraction: Float) {
        val current = _playbackState.value
        if (current.sourceKind == MusicSourceKind.EXTERNAL_MEDIA && current.durationMs > 0L) {
            val targetMs = (current.durationMs * fraction.coerceIn(0f, 1f)).toLong()
            runCatching { primaryExternalController?.transportControls?.seekTo(targetMs) }
            _playbackState.value = current.copy(
                positionMs = targetMs,
                lastPositionUpdateElapsedMs = SystemClock.elapsedRealtime()
            )
        }
    }
}
