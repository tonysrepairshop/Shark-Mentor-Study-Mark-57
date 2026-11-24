package com.example.sharkmentor.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
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
            selectedSkin = selectedSkin,
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
                                modifier = stageModifier
                            )
                            MusicPlayerSkin.MODERN_VINYL -> VinylTurntableStage(
                                artwork = playbackState.artwork,
                                spinDegreesProvider = { spinDegrees },
                                tonearmSwingDeg = tonearmSwingDeg,
                                isModernBlue = true,
                                modifier = stageModifier
                            )
                            MusicPlayerSkin.CLASSIC_VINYL -> VinylTurntableStage(
                                artwork = playbackState.artwork,
                                spinDegreesProvider = { spinDegrees },
                                tonearmSwingDeg = tonearmSwingDeg,
                                isModernBlue = false,
                                modifier = stageModifier
                            )
                            MusicPlayerSkin.CD -> WallCdPlayerStage(
                                artwork = playbackState.artwork,
                                spinDegreesProvider = { spinDegrees },
                                isPlaying = playbackState.isPlaying,
                                modifier = stageModifier
                            )
                            MusicPlayerSkin.CASSETTE -> CassetteDeckStage(
                                artwork = playbackState.artwork,
                                spinDegreesProvider = { spinDegrees },
                                modifier = stageModifier
                            )
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
                                    text = playbackState.title.ifBlank { "Müzik" },
                                    fontSize = titleSize,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = playbackState.artist.ifBlank { playbackState.sourceLabel.ifBlank { "Kaynak Seç" } },
                                    fontSize = artistSize,
                                    color = Color.White.copy(alpha = 0.72f),
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
                                modifier = Modifier.size(if (ultraTight) 28.dp else 34.dp)
                            ) {
                                Icon(
                                    imageVector = if (playbackState.sourceKind == MusicSourceKind.EXTERNAL_MEDIA) {
                                        Icons.AutoMirrored.Filled.OpenInNew
                                    } else {
                                        Icons.Outlined.StarOutline
                                    },
                                    contentDescription = "Kaynağı Aç",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(if (ultraTight) 16.dp else 19.dp)
                                )
                            }
                        }

                        if (!ultraTight && playbackState.subtitleLine.isNotBlank()) {
                            Text(
                                text = playbackState.subtitleLine,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.28f),
                                        start = Offset(0f, centerY),
                                        end = Offset(size.width, centerY),
                                        strokeWidth = 2.4.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    val activeEndX = (size.width * progressFraction).coerceIn(0f, size.width)
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(0f, centerY),
                                        end = Offset(activeEndX, centerY),
                                        strokeWidth = 2.6.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawCircle(
                                        color = Color.White,
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
                                    text = if (playbackState.durationMs > 0L) formatMusicDurationLabel(livePositionMs) else "CANLI",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.62f)
                                )
                                Text(
                                    text = if (playbackState.durationMs > 0L) formatMusicDurationLabel(playbackState.durationMs) else playbackState.sourceLabel,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.62f)
                                )
                            }
                        }

                        // Bottom Transport Row (Skin Switch | Prev | Play/Pause Circle | Next | Queue)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val skins = MusicPlayerSkin.entries
                                    val nextSkin = skins[(skins.indexOf(selectedSkin) + 1) % skins.size]
                                    onSelectSkin(nextSkin)
                                },
                                modifier = Modifier.size(ctrlBtnSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = "Plak Teması Değiştir",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(sideIconSize)
                                )
                            }

                            IconButton(
                                onClick = { MusicPlayerHub.skipPrevious(context) },
                                modifier = Modifier.size(ctrlBtnSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Önceki",
                                    tint = Color.White,
                                    modifier = Modifier.size(skipIconSize)
                                )
                            }

                            Surface(
                                onClick = { MusicPlayerHub.togglePlayPause(context) },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier.size(playCircleSize)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playbackState.isPlaying) "Duraklat" else "Oynat",
                                        tint = Color.White,
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
                                    tint = Color.White,
                                    modifier = Modifier.size(skipIconSize)
                                )
                            }

                            IconButton(
                                onClick = { showSoundQueueSheet = true },
                                modifier = Modifier.size(ctrlBtnSize)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = "Ses Kaynağı & Tema",
                                    tint = Color.White.copy(alpha = 0.85f),
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp)),
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
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Spinning Vinyl Platter + Center Art
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
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
                    color = if (isModernBlue) Color(0xFF8CC0F8).copy(alpha = 0.42f) else Color(0xFF4B4B50).copy(alpha = 0.55f),
                    radius = r,
                    center = c
                )

                // Vinyl disc body
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

        // Top-right Tonearm Assembly (pivots smoothly when playing vs paused)
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
    }
}

@Composable
private fun WallCdPlayerStage(
    artwork: Bitmap?,
    spinDegreesProvider: () -> Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF2B293B),
        border = BorderStroke(2.dp, Color(0xFF3A374E)),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Speaker Grilles + Pull Switch + Volume Knob
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val dotColor = Color(0xFF56536E)
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
                        // Left grille
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(w * 0.085f + c * stepX + stagger, y)
                        )
                        // Right grille
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(w * 0.835f + c * stepX + stagger, y)
                        )
                    }
                }

                // Bottom-Left Vertical Pull Switch
                val switchTopLeft = Offset(w * 0.14f, h * 0.72f)
                val switchSize = Size(10.dp.toPx(), 42.dp.toPx())
                drawRoundRect(
                    color = Color(0xFF222030),
                    topLeft = switchTopLeft,
                    size = switchSize,
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF47445E),
                    topLeft = switchTopLeft,
                    size = switchSize,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx())
                )
                val thumbCenterY = if (isPlaying) switchTopLeft.y + switchSize.height - 6.dp.toPx() else switchTopLeft.y + 7.dp.toPx()
                drawCircle(
                    color = Color(0xFF635F7E),
                    radius = 4.2.dp.toPx(),
                    center = Offset(switchTopLeft.x + switchSize.width / 2f, thumbCenterY)
                )

                // Bottom-Right Rotary Volume Knob + Arc
                val knobCenter = Offset(w * 0.80f, h * 0.82f)
                val knobRadius = 13.dp.toPx()
                drawArc(
                    color = Color(0xFF55526E),
                    startAngle = 150f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(knobCenter.x - knobRadius * 1.45f, knobCenter.y - knobRadius * 1.45f),
                    size = Size(knobRadius * 2.9f, knobRadius * 2.9f),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(
                    color = Color(0xFF39364D),
                    radius = knobRadius,
                    center = knobCenter
                )
                drawCircle(
                    color = Color(0xFF5D5978),
                    radius = knobRadius,
                    center = knobCenter,
                    style = Stroke(width = 1.6.dp.toPx())
                )
            }

            // Central Recessed CD Well + Spinning Optical Disc
            Box(
                modifier = Modifier
                    .fillMaxSize(0.68f)
                    .clip(CircleShape)
                    .background(Color(0xFF1B1926))
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
        }
    }
}

@Composable
private fun CassetteDeckStage(
    artwork: Bitmap?,
    spinDegreesProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF47474B),
        border = BorderStroke(2.dp, Color(0xFF5A5A5F)),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val topPad = (maxHeight * 0.08f).coerceIn(8.dp, 24.dp)
            val sidePad = (maxWidth * 0.075f).coerceIn(8.dp, 22.dp)
            val reelSize = (minOf(maxWidth, maxHeight) * 0.21f).coerceIn(22.dp, 54.dp)
            val reelInset = (maxWidth * 0.08f).coerceIn(10.dp, 24.dp)

            // Central Chamfered Cassette Window + Artwork + Dual Spinning Toothed Reels
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPad, start = sidePad, end = sidePad)
                    .fillMaxWidth()
                    .fillMaxSize(0.66f)
                    .clip(CassetteWindowShape)
                    .background(Color(0xFF18181B))
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
                text = "Çalar Teması & Odak Sesleri",
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
                        Text(
                            text = when (skin) {
                                MusicPlayerSkin.MODERN_VINYL -> "Modern"
                                MusicPlayerSkin.CLASSIC_VINYL -> "Classic"
                                else -> skin.displayTitle
                            },
                            fontSize = 11.sp,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                            color = if (chosen) colorScheme.onPrimary else colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .align(Alignment.CenterVertically)
                        )
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
                        MusicPlayerHub.connectMusicProvider(context, MusicPlayerHub.PKG_SPOTIFY)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "Spotify Aç",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                Surface(
                    onClick = {
                        onDismiss()
                        MusicPlayerHub.connectMusicProvider(context, MusicPlayerHub.PKG_YOUTUBE_MUSIC)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "YouTube Music Aç",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
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
                    .height(190.dp),
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
                            text = selectedSkin.displayTitle,
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
