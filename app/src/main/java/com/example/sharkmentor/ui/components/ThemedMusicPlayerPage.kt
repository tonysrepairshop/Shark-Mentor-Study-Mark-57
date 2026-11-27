package com.example.sharkmentor.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharkmentor.core.audio.MusicPlaybackState
import com.example.sharkmentor.core.audio.MusicPlayerHub
import com.example.sharkmentor.core.audio.MusicPlayerSkin
import com.example.sharkmentor.core.audio.MusicQueueItem
import com.example.sharkmentor.core.audio.MusicSourceKind
import com.example.sharkmentor.core.audio.SHARK_MIX_PRESETS
import com.example.sharkmentor.core.audio.SharkMixPreset
import com.example.sharkmentor.core.audio.ZenAudioManager
import com.example.sharkmentor.core.data.ThemePalette
import com.example.sharkmentor.core.data.ThemePreferences
import com.example.sharkmentor.core.audio.ZenSound
import com.example.sharkmentor.core.audio.formatMusicDurationLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val CassetteWindowShape = GenericShape { size, _ ->
    val chamfer = size.width * 0.09f
    moveTo(chamfer, 0f)
    lineTo(size.width - chamfer, 0f)
    lineTo(size.width, chamfer)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    lineTo(0f, chamfer)
    close()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThemedMusicPlayerPage(
    playbackState: MusicPlaybackState,
    selectedSkin: MusicPlayerSkin,
    onSelectSkin: (MusicPlayerSkin) -> Unit,
    onSlideToDesk: () -> Unit = {},
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
    showSlideToDeskHint: Boolean = false
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showSoundQueueSheet by remember { mutableStateOf(false) }
    val appPaletteFlow = remember { runCatching { ThemePreferences.instance.palette }.getOrNull() }
    val appPalette = if (appPaletteFlow != null) {
        appPaletteFlow.collectAsState().value
    } else {
        ThemePalette.OCEAN
    }
    val syncPackIndex by MusicPlayerHub.themeSyncPackIndex.collectAsState()
    val syncPalette = remember(appPalette, syncPackIndex) {
        resolvePlayerSyncPalette(appPalette, syncPackIndex)
    }
    val onCardColor = syncPalette?.onCard ?: Color.White
    val playCircleColor = syncPalette?.playCircleColor ?: Color.White.copy(alpha = 0.22f)
    var syncBadgeText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(syncBadgeText) {
        if (syncBadgeText != null) {
            delay(1800L)
            syncBadgeText = null
        }
    }

    // Zero-recomposition GPU rotation state: updated per VSync ONLY while playing
    var spinDegrees by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playbackState.isPlaying) {
        if (!playbackState.isPlaying) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            val dtSec = ((nowNanos - lastNanos).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
            lastNanos = nowNanos
            // ~20 deg/sec smooth turntable rotation
            spinDegrees = (spinDegrees + dtSec * 20f) % 360f
        }
    }

    // Smooth 1s position clock for the seekbar
    var livePositionMs by remember(playbackState.positionMs, playbackState.lastPositionUpdateElapsedMs) {
        mutableLongStateOf(playbackState.estimateCurrentPositionMs())
    }
    LaunchedEffect(
        playbackState.isPlaying,
        playbackState.positionMs,
        playbackState.durationMs,
        playbackState.lastPositionUpdateElapsedMs
    ) {
        livePositionMs = playbackState.estimateCurrentPositionMs()
        if (playbackState.isPlaying && playbackState.durationMs > 0L) {
            while (isActive) {
                delay(1000L)
                livePositionMs = playbackState.estimateCurrentPositionMs()
            }
        }
    }

    val tonearmSwingDeg by animateFloatAsState(
        targetValue = if (playbackState.isPlaying) 0f else -14f,
        animationSpec = tween(durationMillis = 450),
        label = "tonearm_swing"
    )

    val cardBrush = remember(selectedSkin, syncPalette) {
        if (syncPalette != null) {
            Brush.verticalGradient(colors = syncPalette.cardGradient)
        } else {
            when (selectedSkin) {
                MusicPlayerSkin.DEFAULT -> Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF173A4E),
                        Color(0xFF3A181E),
                        Color(0xFF142D3E)
                    )
                )
                MusicPlayerSkin.MODERN_VINYL -> Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF5CA0EC),
                        Color(0xFF397BE2),
                        Color(0xFF215BC5)
                    )
                )
                MusicPlayerSkin.CLASSIC_VINYL -> Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF38383B),
                        Color(0xFF242427),
                        Color(0xFF19191C)
                    )
                )
                MusicPlayerSkin.CD -> Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF282638),
                        Color(0xFF201E2E),
                        Color(0xFF191824)
                    )
                )
                MusicPlayerSkin.CASSETTE -> Brush.verticalGradient(
                    colors = listOf(Color(0xFF353538), Color(0xFF252528), Color(0xFF1B1B1E))
                )
            }
        }
    }

    var deviceVolumeFraction by remember(context) {
        mutableFloatStateOf(MusicPlayerHub.getDeviceVolumeFraction(context))
    }

    if (showSoundQueueSheet) {
        MusicQuickSourceSheet(
            selectedSkin = selectedSkin,
            queueItems = playbackState.queueItems,
            onSelectSkin = onSelectSkin,
            onDismiss = { showSoundQueueSheet = false }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = if (compactMode) 0.dp else 6.dp,
                vertical = if (compactMode) 0.dp else 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        val tightHeight = compactMode || maxHeight < 430.dp
        val ultraTight = compactMode || maxHeight < 340.dp
        val cardCorner = if (compactMode) 22.dp else 26.dp
        val hPad = if (ultraTight) 12.dp else if (tightHeight) 16.dp else 20.dp
        val vPad = if (ultraTight) 10.dp else if (tightHeight) 12.dp else 18.dp
        val titleSize = if (ultraTight) 14.sp else if (tightHeight) 17.sp else 20.sp
        val artistSize = if (ultraTight) 11.sp else 12.5.sp
        val playCircleSize = if (ultraTight) 40.dp else if (tightHeight) 46.dp else 54.dp
        val playIconSize = if (ultraTight) 21.dp else if (tightHeight) 24.dp else 27.dp
        val skipIconSize = if (ultraTight) 21.dp else if (tightHeight) 24.dp else 27.dp
        val sideIconSize = if (ultraTight) 17.dp else 20.dp
        val ctrlBtnSize = if (ultraTight) 34.dp else if (tightHeight) 38.dp else 44.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Main Skinned Deck Card — strictly height-aware so it NEVER clips or squishes on tablet landscape
            Surface(
                shape = RoundedCornerShape(cardCorner),
                color = Color.Transparent,
                shadowElevation = if (compactMode) 0.dp else 8.dp,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cardBrush)
                        .padding(horizontal = hPad, vertical = vPad),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Visual Stage dynamically bounded by BOTH width AND height (zero clipping!)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val stageSide = (minOf(maxWidth, maxHeight) * 0.94f).coerceAtLeast(64.dp)
                        val stageModifier = Modifier.size(stageSide)
                        when (selectedSkin) {
                            MusicPlayerSkin.DEFAULT -> DefaultAlbumStage(
                                artwork = playbackState.artwork,
                                skin = selectedSkin,
                                syncPalette = syncPalette,
                                onTogglePlay = { MusicPlayerHub.togglePlayPause(context) },
                                modifier = stageModifier
                            )
                            MusicPlayerSkin.MODERN_VINYL -> VinylTurntableStage(
                                artwork = playbackState.artwork,
                                spinDegreesProvider = { spinDegrees },
                                tonearmSwingDeg = tonearmSwingDeg,
                                isModernBlue = true,
                                syncPalette = syncPalette,
                                onTogglePlay = { MusicPlayerHub.togglePlayPause(context) },
                                modifier = stageModifier
                            )
                            MusicPlayerSkin.CLASSIC_VINYL -> VinylTurntableStage(
                                artwork = playbackState.artwork,
                                spinDegreesProvider = { spinDegrees },
                                tonearmSwingDeg = tonearmSwingDeg,
                                isModernBlue = false,
                                syncPalette = syncPalette,
                                onTogglePlay = { MusicPlayerHub.togglePlayPause(context) },
                                modifier = stageModifier
                            )
                            MusicPlayerSkin.CD -> WallCdPlayerStage(
                                artwork = playbackState.artwork,
                                spinDegreesProvider = { spinDegrees },
                                isPlaying = playbackState.isPlaying,
                                volumeFraction = deviceVolumeFraction,
                                syncPalette = syncPalette,
                                onTogglePlay = { MusicPlayerHub.togglePlayPause(context) },
                                onVolumeChange = { newVol ->
                                    val clamped = newVol.coerceIn(0f, 1f)
                                    deviceVolumeFraction = clamped
                                    MusicPlayerHub.setDeviceVolumeFraction(context, clamped)
                                },
                                modifier = stageModifier
                            )
                            MusicPlayerSkin.CASSETTE -> CassetteDeckStage(
                                artwork = playbackState.artwork,
                                spinDegreesProvider = { spinDegrees },
                                syncPalette = syncPalette,
                                onTogglePlay = { MusicPlayerHub.togglePlayPause(context) },
                                modifier = stageModifier
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = syncBadgeText != null,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(260)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.68f),
                                border = BorderStroke(1.dp, (syncPalette?.accent ?: Color.White).copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = syncBadgeText.orEmpty(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (ultraTight) 6.dp else if (tightHeight) 8.dp else 12.dp))

                    // Track Metadata + Progress + Transport Controls
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(if (ultraTight) 4.dp else if (tightHeight) 6.dp else 9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playbackState.title.ifBlank { "SharkMusic" },
                                    fontSize = titleSize,
                                    fontWeight = FontWeight.Bold,
                                    color = onCardColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sourceSubtitle = playbackState.artist.ifBlank {
                                    playbackState.sourceLabel.ifBlank { "Kaynak Seç ▾" }
                                }
                                Text(
                                    text = sourceSubtitle,
                                    fontSize = artistSize,
                                    fontWeight = if (!playbackState.hasActiveSession) FontWeight.SemiBold else FontWeight.Normal,
                                    color = onCardColor.copy(alpha = if (!playbackState.hasActiveSession) 0.88f else 0.74f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showSoundQueueSheet = true }
                                        .padding(vertical = 2.dp)
                                )
                            }
                            if (playbackState.sourceKind == MusicSourceKind.EXTERNAL_MEDIA) {
                                IconButton(
                                    onClick = { MusicPlayerHub.openActiveSourceApp(context) },
                                    modifier = Modifier.size(if (ultraTight) 28.dp else 34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Kaynağı Aç",
                                        tint = onCardColor.copy(alpha = 0.85f),
                                        modifier = Modifier.size(if (ultraTight) 16.dp else 19.dp)
                                    )
                                }
                            }
                        }

                        // Minimalist Progress Bar + Timestamps
                        val progressFraction = if (playbackState.durationMs > 0L) {
                            (livePositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            if (playbackState.isPlaying) 1f else 0f
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (ultraTight) 14.dp else 16.dp)
                                    .pointerInput(playbackState.durationMs) {
                                        if (playbackState.durationMs > 0L) {
                                            detectTapGestures { offset ->
                                                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                MusicPlayerHub.seekToFraction(frac)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val centerY = size.height / 2f
                                    val activeTrackColor = syncPalette?.accent ?: onCardColor
                                    drawLine(
                                        color = onCardColor.copy(alpha = 0.28f),
                                        start = Offset(0f, centerY),
                                        end = Offset(size.width, centerY),
                                        strokeWidth = 2.4.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    val activeEndX = (size.width * progressFraction).coerceIn(0f, size.width)
                                    drawLine(
                                        color = activeTrackColor,
                                        start = Offset(0f, centerY),
                                        end = Offset(activeEndX, centerY),
                                        strokeWidth = 2.6.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawCircle(
                                        color = activeTrackColor,
                                        radius = 4.2.dp.toPx(),
                                        center = Offset(
                                            activeEndX.coerceIn(4.2.dp.toPx(), (size.width - 4.2.dp.toPx()).coerceAtLeast(4.2.dp.toPx())),
                                            centerY
                                        )
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (playbackState.durationMs > 0L) formatMusicDurationLabel(livePositionMs) else if (playbackState.hasActiveSession) "CANLI" else "00:00",
                                    fontSize = 10.sp,
                                    color = onCardColor.copy(alpha = 0.65f)
                                )
                                Text(
                                    text = if (playbackState.durationMs > 0L) formatMusicDurationLabel(playbackState.durationMs) else playbackState.sourceLabel.ifBlank { "SharkMusic" },
                                    fontSize = 10.sp,
                                    color = onCardColor.copy(alpha = 0.65f)
                                )
                            }
                        }

                        // Bottom Transport Row (Skin Switch + Long-Press Theme Sync | Prev | Play/Pause Circle | Next | Queue)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(ctrlBtnSize)
                                    .clip(CircleShape)
                                    .combinedClickable(
                                        onClick = {
                                            val skins = MusicPlayerSkin.entries
                                            val nextSkin = skins[(skins.indexOf(selectedSkin) + 1) % skins.size]
                                            onSelectSkin(nextSkin)
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val nextPack = MusicPlayerHub.cycleThemeSyncPack(context)
                                            val nextPalette = resolvePlayerSyncPalette(appPalette, nextPack)
                                            syncBadgeText = if (nextPalette == null) {
                                                "Orijinal Renkler"
                                            } else {
                                                "${appPalette.title} Senkron • $nextPack/3 (${nextPalette.packLabel})"
                                            }
                                        }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = "Plak Teması Değiştir (Uzun Bas: Renk Senkronu)",
                                    tint = if (syncPackIndex > 0) (syncPalette?.accent ?: onCardColor) else onCardColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(sideIconSize)
                                )
                                if (syncPackIndex > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(syncPalette?.accent ?: onCardColor)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { MusicPlayerHub.skipPrevious(context) },
                                modifier = Modifier.size(ctrlBtnSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Önceki",
                                    tint = onCardColor,
                                    modifier = Modifier.size(skipIconSize)
                                )
                            }

                            Surface(
                                onClick = { MusicPlayerHub.togglePlayPause(context) },
                                shape = CircleShape,
                                color = playCircleColor,
                                modifier = Modifier.size(playCircleSize)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playbackState.isPlaying) "Duraklat" else "Oynat",
                                        tint = onCardColor,
                                        modifier = Modifier.size(playIconSize)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { MusicPlayerHub.skipNext(context) },
                                modifier = Modifier.size(ctrlBtnSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Sonraki",
                                    tint = onCardColor,
                                    modifier = Modifier.size(skipIconSize)
                                )
                            }

                            IconButton(
                                onClick = { showSoundQueueSheet = true },
                                modifier = Modifier.size(ctrlBtnSize)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = "Kaynak & SharkMix",
                                    tint = onCardColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(sideIconSize)
                                )
                            }
                        }
                    }
                }
            }

            if (showSlideToDeskHint) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    onClick = onSlideToDesk,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Çalışma Masasına Kaydır →",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultAlbumStage(
    artwork: Bitmap?,
    skin: MusicPlayerSkin,
    syncPalette: PlayerSyncPalette? = null,
    onTogglePlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (syncPalette != null) {
                    Modifier.background(syncPalette.shellColor)
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTogglePlay
            ),
        contentAlignment = Alignment.Center
    ) {
        AlbumOrAtmosphericArt(artwork = artwork, skin = skin, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun VinylTurntableStage(
    artwork: Bitmap?,
    spinDegreesProvider: () -> Float,
    tonearmSwingDeg: Float,
    isModernBlue: Boolean,
    syncPalette: PlayerSyncPalette? = null,
    onTogglePlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val triggerToggle = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onTogglePlay()
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Spinning Vinyl Platter + Center Art (tap anywhere on the vinyl to play/pause)
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = triggerToggle
                )
                .graphicsLayer {
                    rotationZ = spinDegreesProvider()
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = center

                // Outer translucent / matte bezel ring
                drawCircle(
                    color = syncPalette?.accent?.copy(alpha = 0.42f)
                        ?: if (isModernBlue) Color(0xFF8CC0F8).copy(alpha = 0.42f) else Color(0xFF4B4B50).copy(alpha = 0.55f),
                    radius = r,
                    center = c
                )

                // Vinyl disc body
                drawCircle(
                    brush = if (syncPalette != null) {
                        Brush.sweepGradient(
                            colors = listOf(
                                syncPalette.vinylDark,
                                syncPalette.vinylLight,
                                syncPalette.vinylDark,
                                syncPalette.vinylLight,
                                syncPalette.vinylDark
                            ),
                            center = c
                        )
                    } else if (isModernBlue) {
                        Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF215AC4),
                                Color(0xFF5D9CEB),
                                Color(0xFF1F55BC),
                                Color(0xFF4E8FE5),
                                Color(0xFF215AC4)
                            ),
                            center = c
                        )
                    } else {
                        Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF141416),
                                Color(0xFF2D2D32),
                                Color(0xFF121214),
                                Color(0xFF28282D),
                                Color(0xFF141416)
                            ),
                            center = c
                        )
                    },
                    radius = r * 0.93f,
                    center = c
                )

                // Concentric micro-grooves
                val grooveColor = if (isModernBlue) {
                    Color(0xFF163F92).copy(alpha = 0.42f)
                } else {
                    Color.White.copy(alpha = 0.06f)
                }
                for (i in 1..9) {
                    val frac = 0.56f + (i * 0.038f)
                    drawCircle(
                        color = grooveColor,
                        radius = r * frac,
                        center = c,
                        style = Stroke(width = 1.1.dp.toPx())
                    )
                }
            }

            // Center Circular Artwork Label
            Box(
                modifier = Modifier
                    .fillMaxSize(0.54f)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AlbumOrAtmosphericArt(
                    artwork = artwork,
                    skin = if (isModernBlue) MusicPlayerSkin.MODERN_VINYL else MusicPlayerSkin.CLASSIC_VINYL,
                    modifier = Modifier.fillMaxSize()
                )
                if (isModernBlue) {
                    // Signature Modern Vinyl vertical center prism reflection
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.38f)
                            .fillMaxSize(0.56f)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF1D2B6F).copy(alpha = 0.55f),
                                        Color(0xFFD97BB6).copy(alpha = 0.25f),
                                        Color(0xFF1A255C).copy(alpha = 0.6f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // Top-right Tonearm Assembly (pivots smoothly when playing vs paused; tap directly to play/pause!)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.86f, 0.11f)
                    rotationZ = tonearmSwingDeg
                }
        ) {
            val w = size.width
            val h = size.height
            val pivot = Offset(w * 0.86f, h * 0.11f)

            // Pivot Outer Shadow / Halo
            drawCircle(
                color = if (isModernBlue) Color(0xFF235BC4) else Color(0xFF1E1E22),
                radius = 15.dp.toPx(),
                center = pivot
            )
            // White Pivot Ring
            drawCircle(
                color = Color.White,
                radius = 11.dp.toPx(),
                center = pivot
            )
            drawCircle(
                color = if (isModernBlue) Color(0xFF2C6CD6) else Color(0xFFD4D4D8),
                radius = 4.5.dp.toPx(),
                center = pivot
            )

            // Curved S-Tonearm Tube
            val armPath = Path().apply {
                moveTo(pivot.x - 1.dp.toPx(), pivot.y + 9.dp.toPx())
                cubicTo(
                    w * 0.83f, h * 0.28f,
                    w * 0.84f, h * 0.46f,
                    w * 0.77f, h * 0.62f
                )
            }
            drawPath(
                path = armPath,
                color = Color.White,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Stylus Headshell at end of tonearm
            val headCenter = Offset(w * 0.76f, h * 0.64f)
            rotate(degrees = 22f, pivot = headCenter) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(headCenter.x - 6.dp.toPx(), headCenter.y - 9.dp.toPx()),
                    size = Size(12.dp.toPx(), 18.dp.toPx()),
                    cornerRadius = CornerRadius(2.5.dp.toPx())
                )
                val slotColor = if (isModernBlue) Color(0xFF2D6CD7) else Color(0xFF424247)
                drawLine(
                    color = slotColor,
                    start = Offset(headCenter.x - 2.2.dp.toPx(), headCenter.y - 4.dp.toPx()),
                    end = Offset(headCenter.x - 2.2.dp.toPx(), headCenter.y + 5.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = slotColor,
                    start = Offset(headCenter.x + 2.2.dp.toPx(), headCenter.y - 4.dp.toPx()),
                    end = Offset(headCenter.x + 2.2.dp.toPx(), headCenter.y + 5.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        // Interactive tonearm hit-area: tapping the needle swings it onto/off the vinyl and toggles playback
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.36f)
                .fillMaxSize(0.70f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = triggerToggle
                )
        )
    }
}

@Composable
private fun WallCdPlayerStage(
    artwork: Bitmap?,
    spinDegreesProvider: () -> Float,
    isPlaying: Boolean,
    volumeFraction: Float = 0.5f,
    syncPalette: PlayerSyncPalette? = null,
    onTogglePlay: () -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val switchProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "cd_pull_switch"
    )
    val latestVolume by androidx.compose.runtime.rememberUpdatedState(volumeFraction.coerceIn(0f, 1f))
    val latestOnVolumeChange by androidx.compose.runtime.rememberUpdatedState(onVolumeChange)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = syncPalette?.shellColor ?: Color(0xFF2B293B),
        border = BorderStroke(2.dp, syncPalette?.shellBorder ?: Color(0xFF3A374E)),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Speaker Grilles + Animated Pull Switch + Live Rotary Volume Knob
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val dotColor = syncPalette?.accent?.copy(alpha = 0.5f) ?: Color(0xFF56536E)
                val accentColor = syncPalette?.accent ?: Color(0xFF38BDF8)
                val dotRadius = 1.8.dp.toPx()

                // Left & Right Dot-Matrix Speaker Grilles
                val rows = 13
                val cols = 4
                val startY = h * 0.34f
                val stepY = (h * 0.24f) / rows
                val stepX = 5.2.dp.toPx()

                for (r in 0 until rows) {
                    val stagger = if (r % 2 == 1) stepX * 0.45f else 0f
                    val y = startY + r * stepY
                    for (c in 0 until cols) {
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(w * 0.085f + c * stepX + stagger, y)
                        )
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(w * 0.835f + c * stepX + stagger, y)
                        )
                    }
                }

                // Bottom-Left Animated Vertical Pull Switch (slides smoothly ON/OFF)
                val switchTopLeft = Offset(w * 0.14f, h * 0.72f)
                val switchSize = Size(10.dp.toPx(), 42.dp.toPx())
                drawRoundRect(
                    color = Color(0xFF222030),
                    topLeft = switchTopLeft,
                    size = switchSize,
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                drawRoundRect(
                    color = if (isPlaying) accentColor.copy(alpha = 0.75f) else Color(0xFF47445E),
                    topLeft = switchTopLeft,
                    size = switchSize,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx())
                )
                val minThumbY = switchTopLeft.y + 7.dp.toPx()
                val maxThumbY = switchTopLeft.y + switchSize.height - 7.dp.toPx()
                val thumbCenterY = minThumbY + (maxThumbY - minThumbY) * switchProgress
                drawCircle(
                    color = if (isPlaying) accentColor else Color(0xFF635F7E),
                    radius = 4.6.dp.toPx(),
                    center = Offset(switchTopLeft.x + switchSize.width / 2f, thumbCenterY)
                )

                // Bottom-Right Live Rotary Volume Knob + Glowing Progress Arc
                val knobCenter = Offset(w * 0.80f, h * 0.82f)
                val knobRadius = 13.5.dp.toPx()
                val arcTopLeft = Offset(knobCenter.x - knobRadius * 1.45f, knobCenter.y - knobRadius * 1.45f)
                val arcSize = Size(knobRadius * 2.9f, knobRadius * 2.9f)
                // Background track arc
                drawArc(
                    color = Color(0xFF55526E).copy(alpha = 0.55f),
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
                // Active volume sweep arc
                drawArc(
                    color = accentColor,
                    startAngle = 140f,
                    sweepAngle = 260f * latestVolume,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(
                    color = Color(0xFF39364D),
                    radius = knobRadius,
                    center = knobCenter
                )
                drawCircle(
                    color = Color(0xFF6B678A),
                    radius = knobRadius,
                    center = knobCenter,
                    style = Stroke(width = 1.6.dp.toPx())
                )
                // Rotating indicator dot on the knob face
                val indicatorDeg = 140.0 + (260.0 * latestVolume.toDouble())
                val indicatorRad = indicatorDeg * PI / 180.0
                val dotDist = knobRadius * 0.62f
                val indicatorCenter = Offset(
                    x = knobCenter.x + (dotDist * cos(indicatorRad)).toFloat(),
                    y = knobCenter.y + (dotDist * sin(indicatorRad)).toFloat()
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.4.dp.toPx(),
                    center = indicatorCenter
                )
            }

            // Central Recessed CD Well + Spinning Optical Disc (tap disc to play/pause)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.68f)
                    .clip(CircleShape)
                    .background(Color(0xFF1B1926))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTogglePlay()
                        }
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = spinDegreesProvider()
                        }
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumOrAtmosphericArt(
                        artwork = artwork,
                        skin = MusicPlayerSkin.CD,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Subtle optical CD outer rim & spindle ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.22f),
                            radius = r - 1.dp.toPx(),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFF282638).copy(alpha = 0.65f),
                            radius = r * 0.16f
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.45f),
                            radius = r * 0.16f,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }

            // Interactive Bottom-Left Pull-Switch hit-target (slides switch & toggles Play/Pause)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 10.dp)
                    .size(width = 56.dp, height = 68.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTogglePlay()
                        }
                    )
            )

            // Interactive Bottom-Right Rotary Volume Knob hit-target (drag or tap to adjust STREAM_MUSIC)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
                    .size(64.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = (-dragAmount.y + dragAmount.x) / 140f
                            latestOnVolumeChange((latestVolume + delta).coerceIn(0f, 1f))
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val step = if (offset.x >= size.width / 2f) 0.10f else -0.10f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnVolumeChange((latestVolume + step).coerceIn(0f, 1f))
                        }
                    }
            )
        }
    }
}

@Composable
private fun CassetteDeckStage(
    artwork: Bitmap?,
    spinDegreesProvider: () -> Float,
    syncPalette: PlayerSyncPalette? = null,
    onTogglePlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = syncPalette?.shellColor ?: Color(0xFF47474B),
        border = BorderStroke(2.dp, syncPalette?.shellBorder ?: Color(0xFF5A5A5F)),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val topPad = (maxHeight * 0.08f).coerceIn(8.dp, 24.dp)
            val sidePad = (maxWidth * 0.075f).coerceIn(8.dp, 22.dp)
            val reelSize = (minOf(maxWidth, maxHeight) * 0.21f).coerceIn(22.dp, 54.dp)
            val reelInset = (maxWidth * 0.08f).coerceIn(10.dp, 24.dp)

            // Central Chamfered Cassette Window + Artwork + Dual Spinning Toothed Reels (tap reels to play/pause)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPad, start = sidePad, end = sidePad)
                    .fillMaxWidth()
                    .fillMaxSize(0.66f)
                    .clip(CassetteWindowShape)
                    .background(Color(0xFF18181B))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTogglePlay()
                        }
                    )
            ) {
                AlbumOrAtmosphericArt(
                    artwork = artwork,
                    skin = MusicPlayerSkin.CASSETTE,
                    modifier = Modifier.fillMaxSize()
                )

                // Left & Right Synchronously Spinning Cassette Tape Reels
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = reelInset),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CassetteTapeReel(spinDegreesProvider = spinDegreesProvider, modifier = Modifier.size(reelSize))
                    CassetteTapeReel(spinDegreesProvider = spinDegreesProvider, modifier = Modifier.size(reelSize))
                }
            }

            // Cassette Shell Screws + Bottom Trapezoid Head Shield
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                fun drawScrew(center: Offset) {
                    drawCircle(color = Color(0xFF37373B), radius = 6.5.dp.toPx(), center = center)
                    drawCircle(
                        color = Color(0xFF58585E),
                        radius = 6.5.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    val arm = 3.2.dp.toPx()
                    drawLine(
                        color = Color(0xFF252528),
                        start = Offset(center.x - arm, center.y),
                        end = Offset(center.x + arm, center.y),
                        strokeWidth = 1.4.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF252528),
                        start = Offset(center.x, center.y - arm),
                        end = Offset(center.x, center.y + arm),
                        strokeWidth = 1.4.dp.toPx()
                    )
                }

                // Top corner screws
                drawScrew(Offset(w * 0.09f, h * 0.09f))
                drawScrew(Offset(w * 0.91f, h * 0.09f))

                // Bottom Trapezoid Head Shield
                val trapPath = Path().apply {
                    moveTo(w * 0.14f, h)
                    lineTo(w * 0.24f, h * 0.78f)
                    lineTo(w * 0.76f, h * 0.78f)
                    lineTo(w * 0.86f, h)
                    close()
                }
                drawPath(path = trapPath, color = Color(0xFF424246))
                drawPath(
                    path = trapPath,
                    color = Color(0xFF56565B),
                    style = Stroke(width = 1.6.dp.toPx())
                )

                // Bottom screws inside trapezoid
                drawScrew(Offset(w * 0.28f, h * 0.89f))
                drawScrew(Offset(w * 0.72f, h * 0.89f))

                // Dual dark tape-head openings
                val slotW = w * 0.13f
                val slotH = h * 0.065f
                val slotY = h * 0.855f
                drawRoundRect(
                    color = Color(0xFF1F1F22),
                    topLeft = Offset(w * 0.35f, slotY),
                    size = Size(slotW, slotH),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF1F1F22),
                    topLeft = Offset(w * 0.52f, slotY),
                    size = Size(slotW, slotH),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun CassetteTapeReel(
    spinDegreesProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = spinDegreesProvider()
        }
    ) {
        val r = size.minDimension / 2f
        val c = center

        // Outer metallic grey hub ring
        drawCircle(color = Color(0xFF6B6B70).copy(alpha = 0.88f), radius = r, center = c)
        drawCircle(color = Color(0xFF9E9EA4), radius = r * 0.72f, center = c)

        // 8 outer sprocket dots
        for (i in 0 until 8) {
            val rad = (i * 45.0) * PI / 180.0
            val dotCenter = Offset(
                c.x + (r * 0.85f * cos(rad)).toFloat(),
                c.y + (r * 0.85f * sin(rad)).toFloat()
            )
            drawCircle(color = Color(0xFF323236), radius = 2.dp.toPx(), center = dotCenter)
        }

        // Dark center spindle hole + 6 white/silver drive teeth
        drawCircle(color = Color(0xFF1E1E22), radius = r * 0.48f, center = c)
        for (i in 0 until 6) {
            val deg = i * 60f
            rotate(degrees = deg, pivot = c) {
                drawRoundRect(
                    color = Color(0xFFD4D4D8),
                    topLeft = Offset(c.x - 2.dp.toPx(), c.y - r * 0.52f),
                    size = Size(4.dp.toPx(), 5.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun AlbumOrAtmosphericArt(
    artwork: Bitmap?,
    skin: MusicPlayerSkin,
    modifier: Modifier = Modifier
) {
    if (artwork != null && !artwork.isRecycled) {
        val imgBitmap = remember(artwork) { artwork.asImageBitmap() }
        Image(
            bitmap = imgBitmap,
            contentDescription = "Albüm Kapağı",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        // Rich procedural cover artwork tailored per skin when stream/Zen has no bitmap cover
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            when (skin) {
                MusicPlayerSkin.MODERN_VINYL -> {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0A4D68),
                                Color(0xFF3A4F9A),
                                Color(0xFFE893CF),
                                Color(0xFF6F1D57)
                            )
                        )
                    )
                    // Desert dune silhouette horizon
                    val dune = Path().apply {
                        moveTo(0f, h * 0.65f)
                        quadraticBezierTo(w * 0.35f, h * 0.54f, w * 0.65f, h * 0.68f)
                        quadraticBezierTo(w * 0.85f, h * 0.74f, w, h * 0.60f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(dune, Color(0xFF4A154B).copy(alpha = 0.72f))
                }
                MusicPlayerSkin.CASSETTE -> {
                    drawRect(color = Color(0xFF141418))
                    // Vertical spectrum prism beam in center
                    val spectrumColors = listOf(
                        Color.Transparent,
                        Color(0xFFDC2626).copy(alpha = 0.85f),
                        Color(0xFFFACC15).copy(alpha = 0.92f),
                        Color(0xFF22C55E).copy(alpha = 0.80f),
                        Color(0xFF3B82F6).copy(alpha = 0.85f),
                        Color.Transparent
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = spectrumColors,
                            startX = w * 0.28f,
                            endX = w * 0.72f
                        )
                    )
                }
                MusicPlayerSkin.CLASSIC_VINYL -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3F3F46),
                                Color(0xFF18181B),
                                Color(0xFF09090B)
                            )
                        )
                    )
                    drawCircle(
                        color = Color(0xFF4ADE80).copy(alpha = 0.35f),
                        radius = size.minDimension * 0.28f,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
                else -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFEF4444).copy(alpha = 0.85f),
                                Color(0xFF0E4D64),
                                Color(0xFF091E2C)
                            ),
                            center = Offset(w * 0.52f, h * 0.52f),
                            radius = size.maxDimension * 0.65f
                        )
                    )
                }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicQuickSourceSheet(
    selectedSkin: MusicPlayerSkin,
    queueItems: List<MusicQueueItem> = emptyList(),
    onSelectSkin: (MusicPlayerSkin) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeLayers by ZenAudioManager.activeMixLayers.collectAsState()
    val activePresetId by ZenAudioManager.activePresetId.collectAsState()
    val syncPackIndex by MusicPlayerHub.themeSyncPackIndex.collectAsState()
    var maxLayerNotice by remember { mutableStateOf(false) }

    LaunchedEffect(maxLayerNotice) {
        if (maxLayerNotice) {
            delay(2000L)
            maxLayerNotice = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (queueItems.isNotEmpty()) "SharkMusic • Sıradakiler & SharkMix" else "SharkMusic • Kaynak & SharkMix",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                if (activeLayers.isNotEmpty()) {
                    Surface(
                        onClick = { ZenAudioManager.stopSound(context) },
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.errorContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "Durdur",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 5 Core Skin Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MusicPlayerSkin.entries.forEach { skin ->
                    val chosen = skin == selectedSkin
                    Surface(
                        onClick = { onSelectSkin(skin) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (chosen) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (skin) {
                                    MusicPlayerSkin.MODERN_VINYL -> "Modern"
                                    MusicPlayerSkin.CLASSIC_VINYL -> "Classic"
                                    else -> skin.displayTitle
                                },
                                fontSize = 11.sp,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                color = if (chosen) colorScheme.onPrimary else colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Text(
                text = if (syncPackIndex == 0) {
                    "Renk Senkronu: Orijinal (Sol alt plak tuşuna uzun basarak tema paketlerini gez)"
                } else {
                    "Renk Senkronu: Uygulama Teması Paket $syncPackIndex/3 (Uzun basarak değiştir)"
                },
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant
            )

            // Dynamic Tappable "Sıradakiler" Queue (shown automatically whenever active media player exposes queue)
            if (queueItems.isNotEmpty()) {
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    text = "Sıradakiler (${queueItems.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(queueItems, key = { "q_${it.queueId}_${it.title}" }) { item ->
                        Surface(
                            onClick = {
                                MusicPlayerHub.skipToQueueItem(item.queueId)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.42f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.subtitle.isNotBlank()) {
                                        Text(
                                            text = item.subtitle,
                                            fontSize = 10.5.sp,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Çal",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = {
                        onDismiss()
                        MusicPlayerHub.openMusicProvider(context, MusicPlayerHub.PKG_SPOTIFY)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Spotify'ı Aç",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    onClick = {
                        onDismiss()
                        MusicPlayerHub.openMusicProvider(context, MusicPlayerHub.PKG_YOUTUBE_MUSIC)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "YouTube Music'i Aç",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }

            // SharkMix Ready Presets
            Text(
                text = "SharkMix • Hazır Ortam Reçeteleri",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SHARK_MIX_PRESETS, key = { it.id }) { preset ->
                    val isPresetActive = activePresetId == preset.id
                    Surface(
                        onClick = { ZenAudioManager.applyPreset(context, preset) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPresetActive) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (isPresetActive) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = preset.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPresetActive) colorScheme.onPrimary else colorScheme.onSurface
                            )
                            Text(
                                text = preset.subtitle,
                                fontSize = 10.sp,
                                color = if (isPresetActive) colorScheme.onPrimary.copy(alpha = 0.82f) else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SharkMix • Canlı Mikser (${activeLayers.size}/${ZenAudioManager.MAX_MIX_LAYERS} Katman)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                if (maxLayerNotice) {
                    Text(
                        text = "En fazla 3 ses karıştırılabilir",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.error
                    )
                }
            }

            val zenItems = remember { ZenSound.entries.filter { it != ZenSound.NONE } }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(zenItems, key = { it.id }) { sound ->
                    val layerVolume = activeLayers[sound]
                    val isSelected = layerVolume != null
                    Surface(
                        onClick = {
                            val ok = ZenAudioManager.toggleMixLayer(context, sound)
                            if (!ok) maxLayerNotice = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sound.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "%${((layerVolume ?: 0.65f) * 100).roundToInt()}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            if (isSelected && layerVolume != null) {
                                Slider(
                                    value = layerVolume,
                                    onValueChange = { ZenAudioManager.setLayerVolume(sound, it) },
                                    valueRange = 0.05f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colorScheme.primary,
                                        activeTrackColor = colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Compact, draggable floating mini music window shown on non-Home tabs (Notes, PDFs, AI, Error Bank, etc.).
 * Preserves the exact active player theme (Default / Modern Vinyl / Classic Vinyl / CD / Cassette)
 * and minimizes cleanly without stopping playback.
 */
@Composable
fun DraggableMiniMusicPlayerWindow(
    playbackState: MusicPlaybackState,
    selectedSkin: MusicPlayerSkin,
    onSelectSkin: (MusicPlayerSkin) -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val winWidthDp = (maxWidth * 0.56f).coerceIn(224.dp, 256.dp)
        val winHeightDp = (maxHeight * 0.56f).coerceIn(252.dp, 296.dp)
        val containerW = with(density) { maxWidth.toPx() }
        val containerH = with(density) { maxHeight.toPx() }
        val winW = with(density) { winWidthDp.toPx() }
        val winH = with(density) { winHeightDp.toPx() }
        val edgePad = with(density) { 10.dp.toPx() }
        val initialTop = with(density) { 52.dp.toPx() }

        var offsetX by remember { mutableFloatStateOf(edgePad) }
        var offsetY by remember { mutableFloatStateOf(initialTop) }

        LaunchedEffect(containerW, containerH) {
            val maxX = (containerW - winW - edgePad).coerceAtLeast(edgePad)
            val maxY = (containerH - winH - edgePad).coerceAtLeast(edgePad)
            offsetX = offsetX.coerceIn(edgePad, maxX)
            offsetY = offsetY.coerceIn(edgePad, maxY)
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF191B22),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(width = winWidthDp, height = winHeightDp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Draggable Top Handle Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .pointerInput(containerW, containerH, winW, winH) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val maxX = (containerW - winW - edgePad).coerceAtLeast(edgePad)
                                val maxY = (containerH - winH - edgePad).coerceAtLeast(edgePad)
                                offsetX = (offsetX + dragAmount.x).coerceIn(edgePad, maxX)
                                offsetY = (offsetY + dragAmount.y).coerceIn(edgePad, maxY)
                            }
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Taşı",
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "SharkMusic • ${selectedSkin.displayTitle}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Küçült",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                ThemedMusicPlayerPage(
                    playbackState = playbackState,
                    selectedSkin = selectedSkin,
                    onSelectSkin = onSelectSkin,
                    compactMode = true,
                    showSlideToDeskHint = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}
