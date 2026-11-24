package com.example.sharkmentor.ui.screens.leaguechat

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.data.AppThemeMode
import com.example.sharkmentor.core.data.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ChatWallpaperStyle(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String
) {
    SADE_TEMA(
        id = "sade_tema",
        title = "Aurora Işıltısı",
        subtitle = "Katmanlı parlak odak zemini",
        emoji = ""
    ),
    KARELI_MATEMATIK(
        id = "kareli_matematik",
        title = "Kareli Matematik Defteri",
        subtitle = "Canlı kareli defter & koordinat",
        emoji = ""
    ),
    NOKTALI_PLANLAYICI(
        id = "noktali_planlayici",
        title = "Noktalı Planlayıcı",
        subtitle = "Takımyıldız & nokta matrisi",
        emoji = ""
    ),
    GEOMETRIK_CIZIMLER(
        id = "geometrik_cizimler",
        title = "Geometrik Mimari",
        subtitle = "Dolgulu altıgen, üçgen & çizim",
        emoji = ""
    ),
    OKYANUS_DALGALARI(
        id = "okyanus_dalgalari",
        title = "Okyanus Dalgaları",
        subtitle = "Derin deniz dalgaları & damlalar",
        emoji = ""
    ),
    FORMUL_SEMBOLLER(
        id = "formul_semboller",
        title = "Formül & Bilim Tahtası",
        subtitle = "∑ ∫ π E=mc² canlı bilim tahtası",
        emoji = ""
    );

    companion object {
        fun fromId(id: String?): ChatWallpaperStyle =
            entries.firstOrNull { it.id == id } ?: OKYANUS_DALGALARI
    }
}

object ChatWallpaperPrefs {
    private const val PREFS_NAME = "shark_chat_prefs"
    private const val KEY_WALLPAPER_STYLE = "chat_wallpaper_style"

    private val _styleFlow = MutableStateFlow(ChatWallpaperStyle.OKYANUS_DALGALARI)
    private var initialized = false

    fun observeStyle(context: Context): StateFlow<ChatWallpaperStyle> {
        if (!initialized) {
            val savedId = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_WALLPAPER_STYLE, null)
            _styleFlow.value = ChatWallpaperStyle.fromId(savedId)
            initialized = true
        }
        return _styleFlow.asStateFlow()
    }

    fun setStyle(context: Context, style: ChatWallpaperStyle) {
        initialized = true
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WALLPAPER_STYLE, style.id)
            .apply()
        _styleFlow.value = style
    }
}

/**
 * Rich, zero-network vector chat wallpaper engine offering 6 vivid multi-layered study
 * atmospheres dynamically harmonized with the active [MaterialTheme.colorScheme]
 * (Dark/Light, all 8 ThemePalettes, and Zen Mode) while preserving sharp message readability.
 */
@Composable
fun ChatWallpaperCanvas(
    styleOverride: ChatWallpaperStyle? = null,
    isMiniaturePreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themePrefs = remember(context) {
        runCatching { ThemePreferences.initialize(context.applicationContext) }.getOrNull()
    }

    val themeModeFlow = remember(themePrefs) { themePrefs?.themeMode ?: MutableStateFlow(AppThemeMode.SYSTEM) }
    val zenModeFlow = remember(themePrefs) { themePrefs?.isZenMode ?: MutableStateFlow(false) }
    val selectedStyleFlow = remember(context) { ChatWallpaperPrefs.observeStyle(context) }

    val themeMode by themeModeFlow.collectAsStateWithLifecycle()
    val isZenMode by zenModeFlow.collectAsStateWithLifecycle()
    val savedStyle by selectedStyleFlow.collectAsStateWithLifecycle()
    val activeStyle = styleOverride ?: savedStyle

    val circadianModeFlow = remember(themePrefs) {
        themePrefs?.circadianScheduleMode ?: MutableStateFlow(com.example.sharkmentor.core.data.CircadianScheduleMode.LOCATION_SUNSET)
    }
    val circadianStartFlow = remember(themePrefs) { themePrefs?.circadianStartMinute ?: MutableStateFlow(20 * 60) }
    val circadianEndFlow = remember(themePrefs) { themePrefs?.circadianEndMinute ?: MutableStateFlow(7 * 60) }
    val circadianScheduleMode by circadianModeFlow.collectAsStateWithLifecycle()
    val circadianStartMin by circadianStartFlow.collectAsStateWithLifecycle()
    val circadianEndMin by circadianEndFlow.collectAsStateWithLifecycle()

    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.CIRCADIAN -> remember(themePrefs, circadianScheduleMode, circadianStartMin, circadianEndMin) {
            themePrefs?.isCircadianNightNow()
                ?: com.example.sharkmentor.core.data.isCircadianNightHour(java.time.LocalTime.now().hour)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val baseBg = colorScheme.background
    val surfaceBg = colorScheme.surface
    val primaryColor = colorScheme.primary
    val secondaryColor = if (isZenMode) colorScheme.onSurfaceVariant else colorScheme.secondary
    val tertiaryColor = if (isZenMode) colorScheme.onSurfaceVariant else colorScheme.tertiary
    val subtleColor = colorScheme.onSurfaceVariant

    // Rich multi-stop atmospheric base gradient (distinctly richer than flat background)
    val gradientColors = when {
        isZenMode -> listOf(
            baseBg,
            lerp(baseBg, surfaceBg, 0.55f),
            lerp(baseBg, subtleColor, 0.08f),
            baseBg
        )
        isDark -> listOf(
            lerp(baseBg, primaryColor, 0.14f),
            lerp(surfaceBg, secondaryColor, 0.10f),
            lerp(baseBg, tertiaryColor, 0.12f),
            lerp(baseBg, primaryColor, 0.08f)
        )
        else -> listOf(
            lerp(baseBg, primaryColor, 0.11f),
            lerp(colorScheme.surfaceContainer, secondaryColor, 0.09f),
            lerp(baseBg, tertiaryColor, 0.10f),
            lerp(baseBg, primaryColor, 0.07f)
        )
    }

    // Dramatically crisper visual contrast (~2.5x stronger than previous faint values)
    val primaryAlpha = when {
        isMiniaturePreview && isZenMode -> 0.28f
        isMiniaturePreview -> 0.42f
        isZenMode -> 0.14f
        isDark -> 0.28f
        else -> 0.24f
    }
    val secondaryAlpha = when {
        isMiniaturePreview && isZenMode -> 0.18f
        isMiniaturePreview -> 0.30f
        isZenMode -> 0.10f
        isDark -> 0.20f
        else -> 0.17f
    }
    val glowAlpha = when {
        isZenMode -> 0.09f
        isDark -> 0.24f
        else -> 0.18f
    }

    val symbolPaint = remember(primaryColor, primaryAlpha, isMiniaturePreview) {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            textAlign = Paint.Align.CENTER
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(gradientColors))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val motifPrimary = primaryColor.copy(alpha = primaryAlpha)
            val motifSecondary = secondaryColor.copy(alpha = secondaryAlpha)
            val motifTertiary = tertiaryColor.copy(alpha = secondaryAlpha * 1.15f)
            val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

            // Universal soft ambient spotlights so every style has rich depth
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent),
                    center = Offset(w * 0.85f, h * 0.14f),
                    radius = maxOf(w, h) * 0.56f
                ),
                radius = maxOf(w, h) * 0.56f,
                center = Offset(w * 0.85f, h * 0.14f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tertiaryColor.copy(alpha = glowAlpha * 0.82f), Color.Transparent),
                    center = Offset(w * 0.14f, h * 0.84f),
                    radius = maxOf(w, h) * 0.52f
                ),
                radius = maxOf(w, h) * 0.52f,
                center = Offset(w * 0.14f, h * 0.84f)
            )

            when (activeStyle) {
                ChatWallpaperStyle.SADE_TEMA -> {
                    // Rich aurora silk ribbons + glowing orbital halo rings
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                secondaryColor.copy(alpha = glowAlpha * 0.95f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.5f, h * 0.48f),
                            radius = w * 0.72f
                        ),
                        radius = w * 0.72f,
                        center = Offset(w * 0.5f, h * 0.48f)
                    )
                    // Translucent diagonal silk ribbon
                    val ribbon = Path().apply {
                        moveTo(-w * 0.1f, h * 0.28f)
                        cubicTo(w * 0.35f, h * 0.12f, w * 0.65f, h * 0.54f, w * 1.1f, h * 0.36f)
                        lineTo(w * 1.1f, h * 0.62f)
                        cubicTo(w * 0.65f, h * 0.78f, w * 0.35f, h * 0.36f, -w * 0.1f, h * 0.54f)
                        close()
                    }
                    drawPath(
                        path = ribbon,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = primaryAlpha * 0.34f),
                                tertiaryColor.copy(alpha = secondaryAlpha * 0.45f),
                                primaryColor.copy(alpha = primaryAlpha * 0.25f)
                            )
                        )
                    )
                    // Crisp sweeping aurora arcs
                    for (idx in 0..2) {
                        val r = w * (0.34f + idx * 0.18f)
                        drawCircle(
                            color = if (idx % 2 == 0) motifPrimary else motifTertiary,
                            radius = r,
                            center = Offset(w * 0.86f, h * 0.16f),
                            style = Stroke(width = if (isMiniaturePreview) 1.5f else 2.2f)
                        )
                    }
                    drawCircle(
                        color = motifSecondary,
                        radius = w * 0.32f,
                        center = Offset(w * 0.12f, h * 0.86f),
                        style = Stroke(width = if (isMiniaturePreview) 1.5f else 2.0f, pathEffect = dashedEffect)
                    )
                }

                ChatWallpaperStyle.KARELI_MATEMATIK -> {
                    // Vivid engineering / study math notebook with bold 5x5 grid, crimson/tertiary double margin & sine blueprint
                    val minorStep = if (isMiniaturePreview) 12.dp.toPx() else 22.dp.toPx()
                    var col = 0
                    var gx = 0f
                    while (gx <= w) {
                        val isMajor = (col % 5 == 0)
                        drawLine(
                            color = if (isMajor) primaryColor.copy(alpha = primaryAlpha * 1.15f)
                            else subtleColor.copy(alpha = secondaryAlpha * 0.72f),
                            start = Offset(gx, 0f),
                            end = Offset(gx, h),
                            strokeWidth = if (isMajor) 2.2f else 1.1f
                        )
                        gx += minorStep
                        col++
                    }
                    var row = 0
                    var gy = 0f
                    while (gy <= h) {
                        val isMajor = (row % 5 == 0)
                        drawLine(
                            color = if (isMajor) primaryColor.copy(alpha = primaryAlpha * 1.15f)
                            else subtleColor.copy(alpha = secondaryAlpha * 0.72f),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = if (isMajor) 2.2f else 1.1f
                        )
                        gy += minorStep
                        row++
                    }

                    // Double vertical notebook margin rule
                    val marginX = w * 0.10f
                    val marginColor = if (isZenMode) motifPrimary else tertiaryColor.copy(alpha = primaryAlpha * 1.3f)
                    drawLine(marginColor, Offset(marginX, 0f), Offset(marginX, h), strokeWidth = 2.8f)
                    drawLine(
                        marginColor.copy(alpha = primaryAlpha * 0.65f),
                        Offset(marginX + 6f, 0f),
                        Offset(marginX + 6f, h),
                        strokeWidth = 1.3f
                    )

                    // Subtle coordinate wave overlay across middle
                    val waveY = h * 0.52f
                    val waveAmp = if (isMiniaturePreview) 12f else 38f
                    val sinPath = Path().apply {
                        moveTo(0f, waveY)
                        var x = 0f
                        val s = if (isMiniaturePreview) 6f else 14f
                        while (x <= w + s) {
                            val y = waveY - (sin((x / w) * 2.0 * PI * 2.0) * waveAmp).toFloat()
                            lineTo(x, y)
                            x += s
                        }
                    }
                    drawPath(
                        path = sinPath,
                        color = primaryColor.copy(alpha = primaryAlpha * 1.1f),
                        style = Stroke(width = if (isMiniaturePreview) 1.8f else 2.8f, cap = StrokeCap.Round)
                    )
                }

                ChatWallpaperStyle.NOKTALI_PLANLAYICI -> {
                    // Rich bullet-journal constellation planner with glowing diamond anchor nodes & diagonal guides
                    val step = if (isMiniaturePreview) 13.dp.toPx() else 22.dp.toPx()
                    val baseR = if (isMiniaturePreview) 1.9f else 2.8f
                    val anchors = mutableListOf<Offset>()

                    var r = 0
                    var y = step * 0.5f
                    while (y < h) {
                        var c = 0
                        var x = step * 0.5f
                        while (x < w) {
                            val isAnchor = (r % 4 == 0 && c % 4 == 0)
                            if (isAnchor) {
                                anchors.add(Offset(x, y))
                                // Soft halo around anchor node
                                drawCircle(
                                    color = primaryColor.copy(alpha = primaryAlpha * 0.45f),
                                    radius = baseR * 3.4f,
                                    center = Offset(x, y)
                                )
                                // Crisp crosshair / diamond anchor
                                val arm = baseR * 2.2f
                                drawLine(motifPrimary, Offset(x - arm, y), Offset(x + arm, y), strokeWidth = 1.8f)
                                drawLine(motifPrimary, Offset(x, y - arm), Offset(x, y + arm), strokeWidth = 1.8f)
                                drawCircle(
                                    color = primaryColor.copy(alpha = (primaryAlpha * 1.45f).coerceAtMost(0.65f)),
                                    radius = baseR * 1.35f,
                                    center = Offset(x, y)
                                )
                            } else {
                                drawCircle(
                                    color = if ((r + c) % 2 == 0) motifSecondary else motifTertiary.copy(alpha = secondaryAlpha * 0.8f),
                                    radius = baseR,
                                    center = Offset(x, y)
                                )
                            }
                            x += step
                            c++
                        }
                        y += step
                        r++
                    }

                    // Delicate constellation lines connecting alternating anchor nodes
                    for (i in 0 until anchors.size - 1) {
                        if (i % 3 == 0) {
                            val a = anchors[i]
                            val b = anchors[(i + 1).coerceAtMost(anchors.lastIndex)]
                            if (kotlin.math.abs(a.y - b.y) < step * 5f && kotlin.math.abs(a.x - b.x) < step * 5f) {
                                drawLine(
                                    color = motifPrimary.copy(alpha = primaryAlpha * 0.55f),
                                    start = a,
                                    end = b,
                                    strokeWidth = 1.4f,
                                    pathEffect = dashedEffect
                                )
                            }
                        }
                    }
                }

                ChatWallpaperStyle.GEOMETRIK_CIZIMLER -> {
                    // Bold architectural blueprint: tinted translucent polygon fills + crisp nested geometry + glowing vertices
                    val cell = if (isMiniaturePreview) 46.dp.toPx() else 104.dp.toPx()
                    val strokeW = if (isMiniaturePreview) 1.8f else 2.5f
                    var row = 0
                    var cy = cell * 0.5f
                    while (cy < h + cell) {
                        var col = 0
                        var cx = cell * 0.45f + if (row % 2 == 1) cell * 0.45f else 0f
                        while (cx < w + cell) {
                            val shapeType = (row * 2 + col) % 3
                            when (shapeType) {
                                0 -> {
                                    // Inscribed glowing triangle inside compass circle
                                    val r = cell * 0.34f
                                    drawCircle(
                                        color = primaryColor.copy(alpha = primaryAlpha * 0.18f),
                                        radius = r,
                                        center = Offset(cx, cy)
                                    )
                                    drawCircle(
                                        color = motifSecondary,
                                        radius = r,
                                        center = Offset(cx, cy),
                                        style = Stroke(width = strokeW * 0.85f, pathEffect = dashedEffect)
                                    )
                                    val tri = Path().apply {
                                        for (i in 0 until 3) {
                                            val ang = (-PI / 2.0) + i * (2.0 * PI / 3.0)
                                            val px = cx + (cos(ang) * r).toFloat()
                                            val py = cy + (sin(ang) * r).toFloat()
                                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                                        }
                                        close()
                                    }
                                    drawPath(tri, color = primaryColor.copy(alpha = primaryAlpha * 0.24f))
                                    drawPath(tri, color = motifPrimary, style = Stroke(width = strokeW))
                                    for (i in 0 until 3) {
                                        val ang = (-PI / 2.0) + i * (2.0 * PI / 3.0)
                                        drawCircle(
                                            color = primaryColor.copy(alpha = (primaryAlpha * 1.4f).coerceAtMost(0.6f)),
                                            radius = if (isMiniaturePreview) 2.2f else 3.5f,
                                            center = Offset(
                                                cx + (cos(ang) * r).toFloat(),
                                                cy + (sin(ang) * r).toFloat()
                                            )
                                        )
                                    }
                                }
                                1 -> {
                                    // Nested isometric double hexagon
                                    val rOuter = cell * 0.33f
                                    val rInner = cell * 0.19f
                                    val hexOuter = Path().apply {
                                        for (i in 0 until 6) {
                                            val ang = i * (PI / 3.0)
                                            val px = cx + (cos(ang) * rOuter).toFloat()
                                            val py = cy + (sin(ang) * rOuter).toFloat()
                                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                                        }
                                        close()
                                    }
                                    val hexInner = Path().apply {
                                        for (i in 0 until 6) {
                                            val ang = i * (PI / 3.0) + PI / 6.0
                                            val px = cx + (cos(ang) * rInner).toFloat()
                                            val py = cy + (sin(ang) * rInner).toFloat()
                                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                                        }
                                        close()
                                    }
                                    drawPath(hexOuter, color = tertiaryColor.copy(alpha = secondaryAlpha * 0.24f))
                                    drawPath(hexOuter, color = motifPrimary, style = Stroke(width = strokeW))
                                    drawPath(hexInner, color = motifTertiary, style = Stroke(width = strokeW * 0.8f))
                                }
                                else -> {
                                    // Architectural right triangle with altitude & right-angle arc
                                    val s = cell * 0.29f
                                    val rightTri = Path().apply {
                                        moveTo(cx - s, cy + s)
                                        lineTo(cx - s, cy - s)
                                        lineTo(cx + s, cy + s)
                                        close()
                                    }
                                    drawPath(rightTri, color = secondaryColor.copy(alpha = secondaryAlpha * 0.24f))
                                    drawPath(rightTri, color = motifSecondary, style = Stroke(width = strokeW))
                                    drawArc(
                                        color = motifPrimary,
                                        startAngle = 270f,
                                        sweepAngle = 90f,
                                        useCenter = false,
                                        topLeft = Offset(cx - s - 13f, cy + s - 13f),
                                        size = Size(26f, 26f),
                                        style = Stroke(width = strokeW * 0.85f)
                                    )
                                }
                            }
                            cx += cell * 1.12f
                            col++
                        }
                        cy += cell * 0.96f
                        row++
                    }
                }

                ChatWallpaperStyle.OKYANUS_DALGALARI -> {
                    // Rich multi-layered filled translucent ocean wave ribbons + crisp luminous crests + rising water droplets
                    val waveCount = if (isMiniaturePreview) 4 else 6
                    for (waveIdx in 0 until waveCount) {
                        val ratio = waveIdx.toFloat() / waveCount.toFloat()
                        val baseY = h * (0.16f + ratio * 0.72f)
                        val amplitude = (if (isMiniaturePreview) 8f else 22f) + waveIdx * (if (isMiniaturePreview) 1.5f else 4f)
                        val crestPath = Path()
                        val fillPath = Path()
                        val step = if (isMiniaturePreview) 6f else 14f
                        var x = 0f
                        var first = true
                        while (x <= w + step) {
                            val angle = (x / w) * 2.0 * PI * (1.45 + (waveIdx % 3) * 0.35) + waveIdx * 0.95
                            val y = baseY + (sin(angle) * amplitude).toFloat()
                            if (first) {
                                crestPath.moveTo(x, y)
                                fillPath.moveTo(x, y)
                                first = false
                            } else {
                                crestPath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                            x += step
                        }
                        fillPath.lineTo(w + step, h)
                        fillPath.lineTo(0f, h)
                        fillPath.close()

                        val layerTint = when (waveIdx % 3) {
                            0 -> primaryColor
                            1 -> secondaryColor
                            else -> tertiaryColor
                        }
                        // Translucent ocean body fill under each wave crest
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    layerTint.copy(alpha = primaryAlpha * (0.28f + ratio * 0.24f)),
                                    Color.Transparent
                                ),
                                startY = baseY - amplitude,
                                endY = baseY + (if (isMiniaturePreview) 36f else 160f)
                            )
                        )
                        // Bright luminous wave crest stroke
                        drawPath(
                            path = crestPath,
                            color = layerTint.copy(alpha = (primaryAlpha * (1.05f + ratio * 0.35f)).coerceAtMost(0.55f)),
                            style = Stroke(
                                width = if (isMiniaturePreview) 2.0f else 3.2f,
                                cap = StrokeCap.Round
                            )
                        )
                    }

                    // Ascending ocean water bubbles / droplets
                    val bubbleSeeds = listOf(
                        Triple(0.14f, 0.22f, 1.0f),
                        Triple(0.26f, 0.44f, 0.65f),
                        Triple(0.76f, 0.28f, 1.15f),
                        Triple(0.84f, 0.60f, 0.8f),
                        Triple(0.42f, 0.74f, 0.9f),
                        Triple(0.64f, 0.86f, 0.7f),
                        Triple(0.18f, 0.82f, 1.1f)
                    )
                    val baseBubbleR = if (isMiniaturePreview) 4.dp.toPx() else 10.dp.toPx()
                    bubbleSeeds.forEachIndexed { i, (bx, by, scale) ->
                        val r = baseBubbleR * scale
                        val c = Offset(w * bx, h * by)
                        drawCircle(
                            color = primaryColor.copy(alpha = primaryAlpha * 0.22f),
                            radius = r,
                            center = c
                        )
                        drawCircle(
                            color = if (i % 2 == 0) motifPrimary else motifTertiary,
                            radius = r,
                            center = c,
                            style = Stroke(width = if (isMiniaturePreview) 1.3f else 2.0f)
                        )
                        // Specular bubble highlight dot
                        drawCircle(
                            color = Color.White.copy(alpha = primaryAlpha * 0.85f),
                            radius = r * 0.22f,
                            center = Offset(c.x - r * 0.32f, c.y - r * 0.32f)
                        )
                    }

                    // Sonar rings
                    drawCircle(
                        color = motifPrimary,
                        radius = w * 0.26f,
                        center = Offset(w * 0.88f, h * 0.14f),
                        style = Stroke(width = if (isMiniaturePreview) 1.5f else 2.2f)
                    )
                    drawCircle(
                        color = motifSecondary,
                        radius = w * 0.38f,
                        center = Offset(w * 0.88f, h * 0.14f),
                        style = Stroke(width = 1.4f, pathEffect = dashedEffect)
                    )
                }

                ChatWallpaperStyle.FORMUL_SEMBOLLER -> {
                    // Vivid science & mathematics study board with bold formulas, coordinate parabola & orbital rings
                    val symbols = listOf(
                        "∑", "∫f(x)", "π", "Δ", "√x", "∞",
                        "E=mc²", "θ", "λ", "α", "lim", "H₂O",
                        "F=ma", "log", "Ω", "sin θ"
                    )
                    val stepX = if (isMiniaturePreview) 36.dp.toPx() else 84.dp.toPx()
                    val stepY = if (isMiniaturePreview) 26.dp.toPx() else 78.dp.toPx()
                    val textSizePx = if (isMiniaturePreview) 11.sp.toPx() else 21.sp.toPx()
                    symbolPaint.textSize = textSizePx

                    // Coordinate axes & bold sweeping parabola curve behind formulas
                    val axisColor = motifSecondary.copy(alpha = secondaryAlpha * 0.8f)
                    drawLine(axisColor, Offset(w * 0.08f, h * 0.56f), Offset(w * 0.92f, h * 0.56f), strokeWidth = 1.6f, pathEffect = dashedEffect)
                    drawLine(axisColor, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.92f), strokeWidth = 1.6f, pathEffect = dashedEffect)

                    val para = Path().apply {
                        moveTo(w * 0.08f, h * 0.22f)
                        quadraticTo(w * 0.5f, h * 0.96f, w * 0.92f, h * 0.22f)
                    }
                    drawPath(
                        path = para,
                        color = primaryColor.copy(alpha = primaryAlpha * 0.9f),
                        style = Stroke(width = if (isMiniaturePreview) 1.8f else 2.8f, cap = StrokeCap.Round)
                    )

                    // Subtle orbital electron ellipses in top-right & bottom-left
                    drawOval(
                        color = motifTertiary,
                        topLeft = Offset(w * 0.64f, h * 0.08f),
                        size = Size(w * 0.30f, w * 0.12f),
                        style = Stroke(width = if (isMiniaturePreview) 1.3f else 2.0f)
                    )
                    drawOval(
                        color = motifPrimary,
                        topLeft = Offset(w * 0.06f, h * 0.76f),
                        size = Size(w * 0.28f, w * 0.11f),
                        style = Stroke(width = if (isMiniaturePreview) 1.3f else 2.0f)
                    )

                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        var r = 0
                        var y = stepY * 0.62f
                        while (y < h) {
                            var c = 0
                            var x = stepX * 0.48f + if (r % 2 == 1) stepX * 0.44f else 0f
                            while (x < w) {
                                val symIdx = (r * 5 + c * 3) % symbols.size
                                val sym = symbols[symIdx]
                                val colorChoice = when ((r + c) % 3) {
                                    0 -> primaryColor.copy(alpha = (primaryAlpha * 1.18f).coerceAtMost(0.55f))
                                    1 -> tertiaryColor.copy(alpha = (secondaryAlpha * 1.25f).coerceAtMost(0.45f))
                                    else -> secondaryColor.copy(alpha = (secondaryAlpha * 1.1f).coerceAtMost(0.42f))
                                }
                                symbolPaint.color = colorChoice.toArgb()
                                val angleDeg = (((r + c) % 5) - 2) * 8f
                                nc.save()
                                nc.rotate(angleDeg, x, y)
                                nc.drawText(sym, x, y, symbolPaint)
                                nc.restore()
                                x += stepX
                                c++
                            }
                            y += stepY
                            r++
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWallpaperPickerSheet(
    currentStyle: ChatWallpaperStyle,
    onSelectStyle: (ChatWallpaperStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sohbet Duvar Kağıdı",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Temana uyumlu 6 canlı desenden birini seç",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ChatWallpaperStyle.entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { style ->
                        val isSelected = (style == currentStyle)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onSelectStyle(style)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(74.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    ChatWallpaperCanvas(
                                        styleOverride = style,
                                        isMiniaturePreview = true,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isSelected) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Seçili",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = style.title,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = style.subtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
