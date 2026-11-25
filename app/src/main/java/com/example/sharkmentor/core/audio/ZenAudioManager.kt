package com.example.sharkmentor.core.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import com.example.sharkmentor.core.service.ZenAudioService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class ZenSound(val id: String, val displayName: String, val rawResName: String, val emoji: String = "") {
    NONE("none", "Kapalı", "", ""),
    RAIN("rain", "Yağmur", "zen_rain", ""),
    FOREST("forest", "Orman", "zen_forest", ""),
    OCEAN("ocean", "Okyanus", "zen_ocean", ""),
    TRAIN("train", "Tren Yolculuğu", "zen_train", ""),
    CAFE("cafe", "Kafe Uğultusu", "zen_cafe", ""),
    FIRE("fire", "Kamp Ateşi", "zen_fire", ""),
    AIRPLANE("airplane", "Uçak Kabini", "zen_airplane", ""),
    WATERFALL("waterfall", "Şelale", "zen_waterfall", ""),
    CHICAGO_WALK("chicago_walk", "Chicago'da Yürüyüş", "zen_chicago_walk", ""),
    LONDON_RAIN("london_rain", "Londra'da Yağmur", "zen_london_rain", ""),
    NORTHWEST_RAIN("northwest_rain", "Northwest'te Yağmur", "zen_northwest_rain", "");

    companion object {
        fun fromId(id: String?): ZenSound {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
        }
    }
}

data class SharkMixPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val layers: Map<ZenSound, Float>
)

val SHARK_MIX_PRESETS: List<SharkMixPreset> = listOf(
    SharkMixPreset(
        id = "rainy_cafe",
        title = "Yağmurlu Kafe",
        subtitle = "Yağmur %65 • Kafe %45",
        layers = linkedMapOf(ZenSound.RAIN to 0.65f, ZenSound.CAFE to 0.45f)
    ),
    SharkMixPreset(
        id = "night_train",
        title = "Gece Treni",
        subtitle = "Tren %70 • Kamp Ateşi %35",
        layers = linkedMapOf(ZenSound.TRAIN to 0.70f, ZenSound.FIRE to 0.35f)
    ),
    SharkMixPreset(
        id = "deep_forest",
        title = "Derin Orman",
        subtitle = "Orman %60 • Şelale %50",
        layers = linkedMapOf(ZenSound.FOREST to 0.60f, ZenSound.WATERFALL to 0.50f)
    ),
    SharkMixPreset(
        id = "storm_flight",
        title = "Fırtınalı Uçuş",
        subtitle = "Uçak %60 • Londra Yağmuru %40",
        layers = linkedMapOf(ZenSound.AIRPLANE to 0.60f, ZenSound.LONDON_RAIN to 0.40f)
    ),
    SharkMixPreset(
        id = "ocean_fire",
        title = "Okyanus & Ateş",
        subtitle = "Okyanus %65 • Kamp Ateşi %40",
        layers = linkedMapOf(ZenSound.OCEAN to 0.65f, ZenSound.FIRE to 0.40f)
    )
)

object ZenAudioManager {
    private const val TAG = "SHARK_ZEN_AUDIO"
    const val MAX_MIX_LAYERS = 3

    private val _currentSound = MutableStateFlow(ZenSound.NONE)
    val currentSound: StateFlow<ZenSound> = _currentSound.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeMixLayers = MutableStateFlow<Map<ZenSound, Float>>(emptyMap())
    val activeMixLayers: StateFlow<Map<ZenSound, Float>> = _activeMixLayers.asStateFlow()

    private val _activePresetId = MutableStateFlow<String?>(null)
    val activePresetId: StateFlow<String?> = _activePresetId.asStateFlow()

    private val mixPlayers = LinkedHashMap<ZenSound, MediaPlayer>()

    fun getActiveDisplayTitle(): String {
        val presetId = _activePresetId.value
        if (presetId != null) {
            SHARK_MIX_PRESETS.firstOrNull { it.id == presetId }?.let { return it.title }
        }
        val layers = _activeMixLayers.value
        return when {
            layers.size > 1 -> "Özel SharkMix (${layers.size} Ses)"
            layers.size == 1 -> layers.keys.first().displayName
            _currentSound.value != ZenSound.NONE -> _currentSound.value.displayName
            else -> "SharkMix"
        }
    }

    fun getActiveLayersSummary(): String {
        val layers = _activeMixLayers.value
        if (layers.isEmpty()) {
            return if (_currentSound.value != ZenSound.NONE) "SharkMix Ortam Sesi" else ""
        }
        if (layers.size == 1) {
            val (sound, vol) = layers.entries.first()
            return "SharkMix • ${sound.displayName} %${(vol * 100).toInt()}"
        }
        return layers.entries.joinToString(" • ") { (sound, vol) ->
            "${sound.displayName} %${(vol * 100).toInt()}"
        }
    }

    private fun resolveRawResId(context: Context, sound: ZenSound): Int {
        if (sound == ZenSound.NONE || sound.rawResName.isBlank()) return 0
        var resId = context.resources.getIdentifier(sound.rawResName, "raw", context.packageName)
        if (resId == 0 && sound == ZenSound.OCEAN) {
            resId = context.resources.getIdentifier("zen_ocen", "raw", context.packageName)
        }
        return resId
    }

    private fun createPlayer(context: Context, resId: Int, initialVolume: Float = 1.0f): MediaPlayer? {
        return try {
            val clamped = initialVolume.coerceIn(0.05f, 1.0f)
            MediaPlayer.create(context, resId)?.apply {
                setVolume(clamped, clamped)
                isLooping = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "[ZenAudio] Player oluşturma hatası", e)
            null
        }
    }

    private fun releaseAllPlayersOnly() {
        mixPlayers.values.forEach { player ->
            try {
                if (player.isPlaying) player.pause()
                player.stop()
                player.reset()
                player.release()
            } catch (_: Exception) {}
        }
        mixPlayers.clear()
    }

    fun toggleSound(context: Context, sound: ZenSound) {
        if (_activeMixLayers.value.size == 1 && _activeMixLayers.value.containsKey(sound)) {
            if (_isPlaying.value) {
                stopSound(context)
            } else {
                resumeSound(context)
            }
        } else {
            playSound(context, sound)
        }
    }

    fun playSound(context: Context, sound: ZenSound) {
        if (sound == ZenSound.NONE) {
            stopSound(context)
            return
        }

        releaseAllPlayersOnly()
        val resId = resolveRawResId(context, sound)
        if (resId == 0) {
            Log.w(TAG, "[ZenAudio] Ses dosyası bulunamadı: res/raw/${sound.rawResName}")
            stopSound(context)
            return
        }

        val player = createPlayer(context, resId, 1.0f)
        if (player != null) {
            try {
                player.start()
                mixPlayers[sound] = player
                _activePresetId.value = null
                _activeMixLayers.value = linkedMapOf(sound to 1.0f)
                _currentSound.value = sound
                _isPlaying.value = true
                try {
                    ZenAudioService.start(context, sound)
                } catch (se: Exception) {
                    Log.w(TAG, "[ZenAudio] ZenAudioService başlatılamadı: ${se.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[ZenAudio] Çalma hatası", e)
                stopSound(context)
            }
        } else {
            stopSound(context)
        }
    }

    fun applyPreset(context: Context, preset: SharkMixPreset) {
        releaseAllPlayersOnly()
        val startedLayers = LinkedHashMap<ZenSound, Float>()
        preset.layers.entries.take(MAX_MIX_LAYERS).forEach { (sound, volume) ->
            val resId = resolveRawResId(context, sound)
            if (resId != 0) {
                val clamped = volume.coerceIn(0.05f, 1.0f)
                val player = createPlayer(context, resId, clamped)
                if (player != null) {
                    try {
                        player.start()
                        mixPlayers[sound] = player
                        startedLayers[sound] = clamped
                    } catch (e: Exception) {
                        Log.e(TAG, "[SharkMix] Katman başlatılamadı: ${sound.displayName}", e)
                        runCatching { player.release() }
                    }
                }
            }
        }

        if (startedLayers.isEmpty()) {
            stopSound(context)
            return
        }

        _activePresetId.value = preset.id
        _activeMixLayers.value = startedLayers
        val primarySound = startedLayers.keys.first()
        _currentSound.value = primarySound
        _isPlaying.value = true
        try {
            ZenAudioService.start(context, primarySound)
        } catch (se: Exception) {
            Log.w(TAG, "[SharkMix] ZenAudioService başlatılamadı: ${se.message}")
        }
    }

    /**
     * Toggles a single sound layer in SharkMix (up to [MAX_MIX_LAYERS] simultaneous layers).
     * Returns false only if the user tries to add a 4th layer.
     */
    fun toggleMixLayer(context: Context, sound: ZenSound, defaultVolume: Float = 0.65f): Boolean {
        if (sound == ZenSound.NONE) {
            stopSound(context)
            return true
        }
        val currentLayers = LinkedHashMap(_activeMixLayers.value)
        if (currentLayers.containsKey(sound)) {
            mixPlayers.remove(sound)?.let { player ->
                runCatching {
                    if (player.isPlaying) player.pause()
                    player.stop()
                    player.reset()
                    player.release()
                }
            }
            currentLayers.remove(sound)
            if (currentLayers.isEmpty()) {
                stopSound(context)
            } else {
                _activePresetId.value = null
                _activeMixLayers.value = currentLayers
                _currentSound.value = currentLayers.keys.first()
                ZenAudioService.notifyStateChanged(context)
            }
            return true
        }

        if (currentLayers.size >= MAX_MIX_LAYERS) {
            return false
        }

        val resId = resolveRawResId(context, sound)
        if (resId == 0) return false

        val clamped = defaultVolume.coerceIn(0.05f, 1.0f)
        val player = createPlayer(context, resId, clamped) ?: return false
        return try {
            // If existing layers were paused, resume them together so everything stays in sync
            if (!_isPlaying.value && mixPlayers.isNotEmpty()) {
                mixPlayers.values.forEach { existing ->
                    runCatching { if (!existing.isPlaying) existing.start() }
                }
            }
            player.start()
            mixPlayers[sound] = player
            currentLayers[sound] = clamped
            _activePresetId.value = null
            _activeMixLayers.value = currentLayers
            _currentSound.value = currentLayers.keys.first()
            _isPlaying.value = true
            try {
                ZenAudioService.start(context, _currentSound.value)
            } catch (se: Exception) {
                Log.w(TAG, "[SharkMix] ZenAudioService başlatılamadı: ${se.message}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "[SharkMix] Katman ekleme hatası", e)
            runCatching { player.release() }
            false
        }
    }

    fun setLayerVolume(sound: ZenSound, volume: Float) {
        val clamped = volume.coerceIn(0.05f, 1.0f)
        mixPlayers[sound]?.let { player ->
            runCatching { player.setVolume(clamped, clamped) }
        }
        val currentLayers = LinkedHashMap(_activeMixLayers.value)
        if (currentLayers.containsKey(sound)) {
            currentLayers[sound] = clamped
            _activeMixLayers.value = currentLayers
        }
    }

    fun cyclePreset(context: Context, forward: Boolean) {
        if (SHARK_MIX_PRESETS.isEmpty()) return
        val currentIdx = SHARK_MIX_PRESETS.indexOfFirst { it.id == _activePresetId.value }
        val nextIdx = if (currentIdx == -1) {
            0
        } else if (forward) {
            (currentIdx + 1) % SHARK_MIX_PRESETS.size
        } else {
            (currentIdx - 1 + SHARK_MIX_PRESETS.size) % SHARK_MIX_PRESETS.size
        }
        applyPreset(context, SHARK_MIX_PRESETS[nextIdx])
    }

    fun pauseSound(context: Context? = null) {
        if (!_isPlaying.value) return
        mixPlayers.values.forEach { player ->
            try {
                if (player.isPlaying) player.pause()
            } catch (e: Exception) {
                Log.e(TAG, "[ZenAudio] Pause error", e)
            }
        }
        _isPlaying.value = false
        context?.let { ZenAudioService.notifyStateChanged(it) }
    }

    fun resumeSound(context: Context) {
        if (mixPlayers.isNotEmpty()) {
            var resumedAny = false
            mixPlayers.values.forEach { player ->
                try {
                    if (!player.isPlaying) player.start()
                    resumedAny = true
                } catch (e: Exception) {
                    Log.e(TAG, "[ZenAudio] Resume error", e)
                }
            }
            if (resumedAny) {
                _isPlaying.value = true
                ZenAudioService.notifyStateChanged(context)
                return
            }
        }

        val presetId = _activePresetId.value
        val preset = presetId?.let { id -> SHARK_MIX_PRESETS.firstOrNull { it.id == id } }
        if (preset != null) {
            applyPreset(context, preset)
        } else if (_activeMixLayers.value.isNotEmpty()) {
            val savedLayers = LinkedHashMap(_activeMixLayers.value)
            releaseAllPlayersOnly()
            val restarted = LinkedHashMap<ZenSound, Float>()
            savedLayers.forEach { (sound, vol) ->
                val resId = resolveRawResId(context, sound)
                if (resId != 0) {
                    createPlayer(context, resId, vol)?.let { p ->
                        runCatching {
                            p.start()
                            mixPlayers[sound] = p
                            restarted[sound] = vol
                        }
                    }
                }
            }
            if (restarted.isNotEmpty()) {
                _activeMixLayers.value = restarted
                _currentSound.value = restarted.keys.first()
                _isPlaying.value = true
                ZenAudioService.start(context, _currentSound.value)
            }
        } else if (_currentSound.value != ZenSound.NONE) {
            playSound(context, _currentSound.value)
        }
    }

    fun stopSound(context: Context? = null) {
        _currentSound.value = ZenSound.NONE
        _activePresetId.value = null
        _activeMixLayers.value = emptyMap()
        _isPlaying.value = false

        releaseAllPlayersOnly()

        context?.let {
            val audioManager = it.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
            try {
                ZenAudioService.stop(it)
            } catch (_: Exception) {}
        }
        stopAudio()
    }

    private val audioLock = Any()
    private var audioTrack: AudioTrack? = null

    suspend fun playSound(audioData: ShortArray) = withContext(Dispatchers.Default) {
        synchronized(audioLock) {
            val track = audioTrack ?: return@withContext
            if (track.state != AudioTrack.STATE_INITIALIZED || track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                return@withContext
            }

            try {
                var written = 0
                while (written < audioData.size && isActive) {
                    val result = track.write(audioData, written, audioData.size - written)
                    if (result <= 0) break
                    written += result
                }
            } catch (e: IllegalStateException) {
                Log.w("ZenAudioManager", "AudioTrack was released during write: ${e.message}")
            } catch (e: Exception) {
                Log.e("ZenAudioManager", "Audio playback error", e)
            }
        }
    }

    fun stopAudio() {
        synchronized(audioLock) {
            try {
                audioTrack?.apply {
                    if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                        stop()
                    }
                    release()
                }
            } catch (e: Exception) {
                Log.w("ZenAudioManager", "Error stopping track", e)
            } finally {
                audioTrack = null
            }
        }
    }
}
