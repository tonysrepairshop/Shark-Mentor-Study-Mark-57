package com.example.sharkmentor.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.audio.MusicPlaybackState
import com.example.sharkmentor.core.audio.MusicPlayerHub
import com.example.sharkmentor.core.audio.MusicPlayerSkin
import com.example.sharkmentor.core.audio.MusicSourceKind
import com.example.sharkmentor.core.audio.ZenAudioManager
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

/**
 * Compact, resizable Music button / live mini capsule designed for the customizable Top Bar,
 * Note Editor toolbar, and PDF Reader toolbar.
 */
@Composable
fun TopBarMusicPill(
    playbackState: MusicPlaybackState,
    showBubble: Boolean = true,
    scaleFactor: Float = 1.0f,
    compactIconOnly: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isLive = playbackState.hasActiveSession
    val isPlaying = playbackState.isPlaying
    val iconSize = (16f * scaleFactor.coerceIn(0.82f, 1.22f)).dp
    val hPad = (if (compactIconOnly || !isLive) 8f else 10f) * scaleFactor.coerceIn(0.85f, 1.18f)
    val vPad = 5f * scaleFactor.coerceIn(0.85f, 1.18f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = when {
            !showBubble -> Color.Transparent
            isPlaying -> colorScheme.primary.copy(alpha = 0.16f)
            else -> colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = if (showBubble) {
            BorderStroke(
                1.dp,
                if (isPlaying) colorScheme.primary.copy(alpha = 0.45f)
                else colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        } else null,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = hPad.dp, vertical = vPad.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Album else Icons.Default.MusicNote,
                contentDescription = "Müzik Çalar",
                tint = if (isPlaying) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
            if (!compactIconOnly && isLive && playbackState.title.isNotBlank()) {
                Text(
                    text = playbackState.title,
                    fontSize = (11f * scaleFactor.coerceIn(0.85f, 1.15f)).sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPlaying) colorScheme.primary else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = (96f * scaleFactor).dp)
                )
            }
        }
    }
}

/**
 * Overlay modal sheet presenting the full 5-theme tactile turntable/CD/cassette Music Player
 * right above any screen (Home, Notes, PDF Reader, Plans, League) without leaving the current task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerModalSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        MusicPlayerHub.ensureInitialized(context)
        MusicPlayerHub.refreshPermissionsAndSessions(context)
    }

    val playbackState by MusicPlayerHub.playbackState.collectAsStateWithLifecycle()
    val selectedSkin by MusicPlayerHub.selectedSkin.collectAsStateWithLifecycle()
    val mediaAccessGranted by MusicPlayerHub.mediaAccessGranted.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        if (playbackState.hasActiveSession) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 500.dp, max = 610.dp)
                    .padding(bottom = 14.dp)
            ) {
                ThemedMusicPlayerPage(
                    playbackState = playbackState,
                    selectedSkin = selectedSkin,
                    onSelectSkin = { MusicPlayerHub.setSkin(context, it) },
                    onSlideToDesk = onDismiss,
                    showSlideToDeskPill = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Idle launcher inside modal sheet (open Spotify / YT Music / start Zen focus sound)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Müzik Çalar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(18.dp))
                    }
                }

                if (!mediaAccessGranted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.primary.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Çalan şarkıyı görmek için medya erişim izni gerekir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            OutlinedButton(
                                onClick = { MusicPlayerHub.openMediaAccessSettings(context) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("İzin Ver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { MusicPlayerHub.openMusicProvider(context, MusicPlayerHub.PKG_SPOTIFY) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Spotify'ı Aç", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { MusicPlayerHub.openMusicProvider(context, MusicPlayerHub.PKG_YOUTUBE_MUSIC) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("YouTube Music'i Aç", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Text(
                    text = "Çalar Teması",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MusicPlayerSkin.entries.forEach { skin ->
                        val chosen = skin == selectedSkin
                        Surface(
                            onClick = { MusicPlayerHub.setSkin(context, skin) },
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

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = "Hızlı Odak Sesi Başlat",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )

                val zenItems = remember { ZenSound.entries.filter { it != ZenSound.NONE } }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(zenItems, key = { it.id }) { sound ->
                        Surface(
                            onClick = { ZenAudioManager.playSound(context, sound) },
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sound.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Başlat",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedMusicPlayerPage(
    playbackState: MusicPlaybackState,
    selectedSkin: MusicPlayerSkin,
    onSelectSkin: (MusicPlayerSkin) -> Unit,
    onSlideToDesk: () -> Unit,
    showSlideToDeskPill: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSoundQueueSheet by remember { mutableStateOf(false) }

    // Zero-recomposition GPU rotation state: updated per VSync ONLY while playing
    var spinDegrees by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playbackState.isPlaying) {
        if (!playbackState.isPlaying) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            val dtSec = ((nowNanos - lastNanos).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
            lastNanos = nowNanos
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

    // Device media volume state for interactive CD rotary knob
    var cdVolumeFraction by remember(context) {
        mutableFloatStateOf(MusicPlayerHub.getDeviceVolumeFraction(context))
    }

    // Tactile tonearm angle (0° resting on vinyl groove while playing, -24° swung cleanly off record when paused)
    val tonearmSwingDeg by animateFloatAsState(
        targetValue = if (playbackState.isPlaying) 0f else -24f,
        animationSpec = tween(durationMillis = 420),
        label = "tonearm_swing"
    )

    val cardBrush = remember(selectedSkin) {
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
                colors = listOf(
                    Color(0xFF353538),
                    Color(0xFF252528),
                    Color(0xFF1B1B1E)
                )
            )
        }
    }

    if (showSoundQueueSheet) {
        MusicQuickSourceSheet(
            playbackState = playbackState,
            selectedSkin = selectedSkin,
            onSelectSkin = onSelectSkin,
            onDismiss = { showSoundQueueSheet = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Main Skinned Deck Card
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .weight(1f, fill = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cardBrush)
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Visual Stage (all mechanical controls are directly interactive!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when (selectedSkin) {
                        MusicPlayerSkin.DEFAULT -> DefaultAlbumStage(
                            artwork = playbackState.artwork,
                            skin = selectedSkin,
                            onTogglePlay = { MusicPlayerHub.togglePlayPause(context) }
                        )
                        MusicPlayerSkin.MODERN_VINYL -> VinylTurntableStage(
                            artwork = playbackState.artwork,
                            spinDegreesProvider = { spinDegrees },
                            tonearmSwingDeg = tonearmSwingDeg,
                            isModernBlue = true,
                            onTogglePlay = { MusicPlayerHub.togglePlayPause(context) }
                        )
                        MusicPlayerSkin.CLASSIC_VINYL -> VinylTurntableStage(
                            artwork = playbackState.artwork,
                            spinDegreesProvider = { spinDegrees },
                            tonearmSwingDeg = tonearmSwingDeg,
                            isModernBlue = false,
                            onTogglePlay = { MusicPlayerHub.togglePlayPause(context) }
                        )
                        MusicPlayerSkin.CD -> WallCdPlayerStage(
                            artwork = playbackState.artwork,
                            spinDegreesProvider = { spinDegrees },
                            isPlaying = playbackState.isPlaying,
                            volumeFraction = cdVolumeFraction,
                            onTogglePlay = { MusicPlayerHub.togglePlayPause(context) },
                            onVolumeChange = { newFrac ->
                                cdVolumeFraction = MusicPlayerHub.setDeviceVolumeFraction(context, newFrac)
                            }
                        )
                        MusicPlayerSkin.CASSETTE -> CassetteDeckStage(
                            artwork = playbackState.artwork,
                            spinDegreesProvider = { spinDegrees },
                            onTogglePlay = { MusicPlayerHub.togglePlayPause(context) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Track Metadata + Progress + Transport Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playbackState.title.ifBlank { "Müzik" },
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = playbackState.artist.ifBlank { playbackState.sourceLabel },
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                if (playbackState.sourceKind == MusicSourceKind.EXTERNAL_MEDIA) {
                                    MusicPlayerHub.openActiveSourceApp(context)
                                } else {
                                    showSoundQueueSheet = true
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (playbackState.sourceKind == MusicSourceKind.EXTERNAL_MEDIA) {
                                    Icons.AutoMirrored.Filled.OpenInNew
                                } else {
                                    Icons.Outlined.StarOutline
                                },
                                contentDescription = "Kaynağı Aç",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Text(
                        text = playbackState.subtitleLine.ifBlank { playbackState.sourceLabel },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Minimalist Progress Bar + Timestamps
                    val progressFraction = if (playbackState.durationMs > 0L) {
                        (livePositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        if (playbackState.isPlaying) 1f else 0f
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
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
                                drawLine(
                                    color = Color.White.copy(alpha = 0.28f),
                                    start = Offset(0f, centerY),
                                    end = Offset(size.width, centerY),
                                    strokeWidth = 2.6.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                val activeEndX = (size.width * progressFraction).coerceIn(0f, size.width)
                                drawLine(
                                    color = Color.White,
                                    start = Offset(0f, centerY),
                                    end = Offset(activeEndX, centerY),
                                    strokeWidth = 2.8.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 5.dp.toPx(),
                                    center = Offset(activeEndX.coerceIn(5.dp.toPx(), (size.width - 5.dp.toPx()).coerceAtLeast(5.dp.toPx())), centerY)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (playbackState.durationMs > 0L) formatMusicDurationLabel(livePositionMs) else "CANLI",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.58f)
                            )
                            Text(
                                text = if (playbackState.durationMs > 0L) formatMusicDurationLabel(playbackState.durationMs) else playbackState.sourceLabel,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.58f)
                            )
                        }
                    }

                    // Bottom Transport Row (Skin Switch | Prev | Play/Pause | Next | Queue & Sources)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val skins = MusicPlayerSkin.entries
                                val nextSkin = skins[(skins.indexOf(selectedSkin) + 1) % skins.size]
                                onSelectSkin(nextSkin)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = "Plak Teması Değiştir",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = { MusicPlayerHub.skipPrevious(context) }) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Önceki",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Surface(
                            onClick = { MusicPlayerHub.togglePlayPause(context) },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Duraklat" else "Oynat",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        IconButton(onClick = { MusicPlayerHub.skipNext(context) }) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Sonraki",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(onClick = { showSoundQueueSheet = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Sıradakiler & Tema",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showSlideToDeskPill) {
            Spacer(modifier = Modifier.height(8.dp))
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DefaultAlbumStage(
    artwork: Bitmap?,
    skin: MusicPlayerSkin,
    onTogglePlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
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
    onTogglePlay: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // Spinning Vinyl Platter + Center Art (tapping platter also toggles Play/Pause!)
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .graphicsLayer {
                    rotationZ = spinDegreesProvider()
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlay
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = center

                drawCircle(
                    color = if (isModernBlue) Color(0xFF8CC0F8).copy(alpha = 0.42f) else Color(0xFF4B4B50).copy(alpha = 0.55f),
                    radius = r,
                    center = c
                )

                drawCircle(
                    brush = if (isModernBlue) {
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

        // Tactile Top-Right Tonearm (Plak İğnesi) — tap directly on needle/arm to swing & Play/Pause!
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

            drawCircle(
                color = if (isModernBlue) Color(0xFF235BC4) else Color(0xFF1E1E22),
                radius = 15.dp.toPx(),
                center = pivot
            )
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

        // Dedicated generous hit-zone over the Tonearm & Stylus Head
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.34f)
                .fillMaxSize(0.76f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlay
                )
        )
    }
}

@Composable
private fun WallCdPlayerStage(
    artwork: Bitmap?,
    spinDegreesProvider: () -> Float,
    isPlaying: Boolean,
    volumeFraction: Float,
    onTogglePlay: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val switchProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "cd_pull_switch"
    )

    Surface(
        shape = RoundedCornerShape(34.dp),
        color = Color(0xFF2B293B),
        border = BorderStroke(2.dp, Color(0xFF3A374E)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .aspectRatio(1f)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Speaker Grilles + Animated Pull Switch + Rotary Volume Knob
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val dotColor = Color(0xFF56536E)
                val dotRadius = 1.8.dp.toPx()

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

                // Bottom-Left Animated Vertical Pull Switch (slides down while playing, up when paused)
                val switchTopLeft = Offset(w * 0.14f, h * 0.72f)
                val switchSize = Size(12.dp.toPx(), 44.dp.toPx())
                drawRoundRect(
                    color = Color(0xFF222030),
                    topLeft = switchTopLeft,
                    size = switchSize,
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                drawRoundRect(
                    color = if (isPlaying) Color(0xFF818CF8) else Color(0xFF47445E),
                    topLeft = switchTopLeft,
                    size = switchSize,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                val topThumbY = switchTopLeft.y + 8.dp.toPx()
                val bottomThumbY = switchTopLeft.y + switchSize.height - 8.dp.toPx()
                val thumbCenterY = topThumbY + (bottomThumbY - topThumbY) * switchProgress
                drawCircle(
                    color = if (isPlaying) Color(0xFFA5B4FC) else Color(0xFF6B6785),
                    radius = 5.dp.toPx(),
                    center = Offset(switchTopLeft.x + switchSize.width / 2f, thumbCenterY)
                )

                // Bottom-Right Interactive Rotary Volume Knob + Arc (0..100%)
                val knobCenter = Offset(w * 0.80f, h * 0.82f)
                val knobRadius = 14.dp.toPx()
                drawArc(
                    color = Color(0xFF45425C),
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = Offset(knobCenter.x - knobRadius * 1.45f, knobCenter.y - knobRadius * 1.45f),
                    size = Size(knobRadius * 2.9f, knobRadius * 2.9f),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFFA5B4FC),
                    startAngle = 140f,
                    sweepAngle = 260f * volumeFraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(knobCenter.x - knobRadius * 1.45f, knobCenter.y - knobRadius * 1.45f),
                    size = Size(knobRadius * 2.9f, knobRadius * 2.9f),
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(
                    color = Color(0xFF39364D),
                    radius = knobRadius,
                    center = knobCenter
                )
                drawCircle(
                    color = Color(0xFF6B6688),
                    radius = knobRadius,
                    center = knobCenter,
                    style = Stroke(width = 1.6.dp.toPx())
                )
                val indicatorDeg = 140.0 + (260.0 * volumeFraction.coerceIn(0f, 1f))
                val indicatorRad = indicatorDeg * PI / 180.0
                val indicatorDot = Offset(
                    knobCenter.x + (knobRadius * 0.62f * cos(indicatorRad)).toFloat(),
                    knobCenter.y + (knobRadius * 0.62f * sin(indicatorRad)).toFloat()
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.4.dp.toPx(),
                    center = indicatorDot
                )
            }

            // Central Recessed CD Well + Spinning Optical Disc (tap to Play/Pause)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.68f)
                    .clip(CircleShape)
                    .background(Color(0xFF1B1926))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlay
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

            // Interactive hit-target on Bottom-Left Vertical Pull Switch (Play/Pause)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 14.dp)
                    .size(width = 48.dp, height = 74.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlay
                    )
            )

            // Interactive hit-target on Bottom-Right Rotary Volume Knob (drag up/down or tap to adjust volume)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 12.dp)
                    .size(64.dp)
                    .pointerInput(volumeFraction) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = (-dragAmount / 140f)
                            onVolumeChange((volumeFraction + delta).coerceIn(0f, 1f))
                        }
                    }
                    .pointerInput(volumeFraction) {
                        detectTapGestures {
                            val nextStep = if (volumeFraction >= 0.92f) 0.25f else (volumeFraction + 0.18f).coerceAtMost(1f)
                            onVolumeChange(nextStep)
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
    onTogglePlay: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color(0xFF47474B),
        border = BorderStroke(2.dp, Color(0xFF5A5A5F)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .aspectRatio(1f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Central Chamfered Cassette Window + Artwork + Dual Spinning Reels (tap to Play/Pause)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp, start = 22.dp, end = 22.dp)
                    .fillMaxWidth()
                    .fillMaxSize(0.66f)
                    .clip(CassetteWindowShape)
                    .background(Color(0xFF18181B))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlay
                    )
            ) {
                AlbumOrAtmosphericArt(
                    artwork = artwork,
                    skin = MusicPlayerSkin.CASSETTE,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CassetteTapeReel(spinDegreesProvider = spinDegreesProvider, modifier = Modifier.size(54.dp))
                    CassetteTapeReel(spinDegreesProvider = spinDegreesProvider, modifier = Modifier.size(54.dp))
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

                drawScrew(Offset(w * 0.09f, h * 0.09f))
                drawScrew(Offset(w * 0.91f, h * 0.09f))

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

                drawScrew(Offset(w * 0.28f, h * 0.89f))
                drawScrew(Offset(w * 0.72f, h * 0.89f))

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

        drawCircle(color = Color(0xFF6B6B70).copy(alpha = 0.88f), radius = r, center = c)
        drawCircle(color = Color(0xFF9E9EA4), radius = r * 0.72f, center = c)

        for (i in 0 until 8) {
            val rad = (i * 45.0) * PI / 180.0
            val dotCenter = Offset(
                c.x + (r * 0.85f * cos(rad)).toFloat(),
                c.y + (r * 0.85f * sin(rad)).toFloat()
            )
            drawCircle(color = Color(0xFF323236), radius = 2.dp.toPx(), center = dotCenter)
        }

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
    playbackState: MusicPlaybackState,
    selectedSkin: MusicPlayerSkin,
    onSelectSkin: (MusicPlayerSkin) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeZen = ZenAudioManager.currentSound.value

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (playbackState.queueItems.isNotEmpty()) "Sıradakiler & Çalar Teması" else "Çalar Teması & Odak Sesleri",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            // 5 Skin Pills
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

            // Dynamic Upcoming Queue ("Sıradakiler") whenever active player (e.g. YouTube Music) publishes it
            if (playbackState.queueItems.isNotEmpty()) {
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    text = "Sıradakiler (${playbackState.queueItems.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 175.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(playbackState.queueItems, key = { "q_${it.queueId}_${it.title}" }) { item ->
                        Surface(
                            onClick = {
                                MusicPlayerHub.skipToQueueItem(item.queueId)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                            fontSize = 11.sp,
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
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
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
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "YouTube Music'i Aç",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }

            Text(
                text = "Odak Sesleri",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )

            val zenItems = remember { ZenSound.entries.filter { it != ZenSound.NONE } }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(zenItems, key = { it.id }) { sound ->
                    val isSelected = activeZen == sound
                    Surface(
                        onClick = {
                            ZenAudioManager.toggleSound(context, sound)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
