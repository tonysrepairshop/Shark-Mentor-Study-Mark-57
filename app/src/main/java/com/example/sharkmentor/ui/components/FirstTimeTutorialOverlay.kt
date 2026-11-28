package com.example.sharkmentor.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FirstTimeTutorialPrefs {
    private const val PREFS_NAME = "shark_first_time_tutorial_prefs"
    private const val KEY_ELIGIBLE = "key_eligible"
    private const val KEY_COMPLETED = "key_completed"

    private val _replayTrigger = MutableStateFlow(0)
    val replayTrigger: StateFlow<Int> = _replayTrigger.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markEligibleForNewUserOrGuest(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_COMPLETED, false)) {
            p.edit().putBoolean(KEY_ELIGIBLE, true).apply()
        }
    }

    fun shouldShowTutorial(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_ELIGIBLE, false) && !p.getBoolean(KEY_COMPLETED, false)
    }

    fun markCompleted(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_COMPLETED, true)
            .putBoolean(KEY_ELIGIBLE, false)
            .apply()
    }

    fun resetForReplay(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ELIGIBLE, true)
            .putBoolean(KEY_COMPLETED, false)
            .apply()
        _replayTrigger.value = _replayTrigger.value + 1
    }
}

enum class SuperpowerSlideId {
    VISUAL_AI_SOLVER,
    SHARK_STUDIO_LASSO,
    FSRS_ERROR_BANK,
    COACHING_FOCUS_LIBRARY
}

data class SuperpowerSlide(
    val id: SuperpowerSlideId,
    val badge: String,
    val title: String,
    val description: String,
    val primaryActionLabel: String,
    val secondaryTryLabel: String,
    val targetRoute: String,
    val aiPromptPayload: String? = null
)

object SuperpowerOnboardingCatalog {
    const val ROUTE_SHARK = "analytics"
    const val ROUTE_NOTES = "notes_screen"
    const val ROUTE_ERROR_BANK = "error_bank"
    const val ROUTE_FOCUS = "home"

    val slides: List<SuperpowerSlide> = listOf(
        SuperpowerSlide(
            id = SuperpowerSlideId.VISUAL_AI_SOLVER,
            badge = "/detayli • /simulasyon",
            title = "Görsel AI Çözücü & Simülasyon Lab",
            description = "Sadece metin değil: fonksiyon grafiği, geometri şekli ve fizik/kimya simülasyonu üreterek soruyu adım adım çözer.",
            primaryActionLabel = "İleri",
            secondaryTryLabel = "Şimdi Dene",
            targetRoute = ROUTE_SHARK,
            aiPromptPayload = "/detayli "
        ),
        SuperpowerSlide(
            id = SuperpowerSlideId.SHARK_STUDIO_LASSO,
            badge = "SharkStudio • Kement AI",
            title = "SharkStudio PDF & Kement AI",
            description = "PDF ve notlarının üzerinde kalemle çiz, soruyu kementle çevrele ve saniyeler içinde adım adım AI çözümü al.",
            primaryActionLabel = "İleri",
            secondaryTryLabel = "SharkStudio'yu Aç",
            targetRoute = ROUTE_NOTES
        ),
        SuperpowerSlide(
            id = SuperpowerSlideId.FSRS_ERROR_BANK,
            badge = "FSRS • 1-3-7 Tekrar",
            title = "FSRS Akıllı Hata Bankası",
            description = "Yanlış yaptığın soruları 1-3-7 unutma eğrisine göre tam zamanında önüne getirir; antrenmanda hareketli SharkAI yanındadır.",
            primaryActionLabel = "İleri",
            secondaryTryLabel = "Hata Bankasına Git",
            targetRoute = ROUTE_ERROR_BANK
        ),
        SuperpowerSlide(
            id = SuperpowerSlideId.COACHING_FOCUS_LIBRARY,
            badge = "Koçluk • Kitaplar • Odak",
            title = "Koçluk Yolu, Kitap Filtresi & Odak",
            description = "Kitaplarını rolüne göre sıralayıp filtrele, haftalık planını koçluk yoluyla kur ve odak sayacınla ritmini koru.",
            primaryActionLabel = "Hemen Başla",
            secondaryTryLabel = "Bu Özelliği Dene",
            targetRoute = ROUTE_FOCUS
        )
    )
}

@Composable
fun FirstTimeTutorialOverlay(
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit = {},
    onLaunchAiPrompt: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val slides = remember { SuperpowerOnboardingCatalog.slides }
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val finishAndDismiss: () -> Unit = {
        FirstTimeTutorialPrefs.markCompleted(context)
        onDismiss()
    }

    val trySlideFeature: (SuperpowerSlide) -> Unit = { slide ->
        FirstTimeTutorialPrefs.markCompleted(context)
        if (!slide.aiPromptPayload.isNullOrEmpty()) {
            PendingAiPromptBus.pendingPrompt.value = slide.aiPromptPayload
            onLaunchAiPrompt(slide.aiPromptPayload)
        }
        onNavigate(slide.targetRoute)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.scrim.copy(alpha = 0.68f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = colorScheme.surface,
            contentColor = colorScheme.onSurface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Top Header: Branded Tour Badge + Skip Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "Süper Güç Turu • ${pagerState.currentPage + 1}/${slides.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    TextButton(onClick = finishAndDismiss) {
                        Text(
                            text = "Atla",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4 Interactive Visual Slides
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { pageIndex ->
                    val slide = slides[pageIndex]
                    SuperpowerSlideContent(
                        slide = slide,
                        onTryFeature = { trySlideFeature(slide) }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Smooth Animated Page Indicator + Primary Next/Start CTA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        slides.indices.forEach { index ->
                            val isSelected = index == pagerState.currentPage
                            val pillWidth by animateDpAsState(
                                targetValue = if (isSelected) 22.dp else 7.dp,
                                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                                label = "onboarding_dot_width"
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = pillWidth, height = 7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) colorScheme.primary
                                        else colorScheme.outlineVariant
                                    )
                            )
                        }
                    }

                    val currentSlide = slides[pagerState.currentPage]
                    val isLastPage = pagerState.currentPage == slides.lastIndex
                    Button(
                        onClick = {
                            if (!isLastPage) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                finishAndDismiss()
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = currentSlide.primaryActionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isLastPage) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuperpowerSlideContent(
    slide: SuperpowerSlide,
    onTryFeature: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Interactive Mini Canvas Visual Showcase
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                when (slide.id) {
                    SuperpowerSlideId.VISUAL_AI_SOLVER -> VisualAiSolverPreview()
                    SuperpowerSlideId.SHARK_STUDIO_LASSO -> SharkStudioLassoPreview()
                    SuperpowerSlideId.FSRS_ERROR_BANK -> FsrsErrorBankPreview()
                    SuperpowerSlideId.COACHING_FOCUS_LIBRARY -> CoachingFocusLibraryPreview()
                }
            }
        }

        // Feature Badge Pill
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.secondaryContainer.copy(alpha = 0.72f)
        ) {
            Text(
                text = slide.badge,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = slide.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Text(
                text = slide.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }

        // Secondary "Try This Feature" Action
        OutlinedButton(
            onClick = onTryFeature,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = when (slide.id) {
                    SuperpowerSlideId.VISUAL_AI_SOLVER -> Icons.Default.AutoAwesome
                    SuperpowerSlideId.SHARK_STUDIO_LASSO -> Icons.Default.Draw
                    SuperpowerSlideId.FSRS_ERROR_BANK -> Icons.Default.Psychology
                    SuperpowerSlideId.COACHING_FOCUS_LIBRARY -> Icons.Default.Route
                },
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = slide.secondaryTryLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary
            )
        }
    }
}

@Composable
private fun VisualAiSolverPreview() {
    val colorScheme = MaterialTheme.colorScheme
    val primary = colorScheme.primary
    val tertiary = colorScheme.tertiary
    val gridColor = colorScheme.outlineVariant.copy(alpha = 0.55f)
    val axisColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Coordinate Grid
            for (i in 1..4) {
                val x = w * i / 5f
                drawLine(gridColor, Offset(x, 8f), Offset(x, h - 8f), strokeWidth = 1.dp.toPx())
            }
            for (j in 1..3) {
                val y = h * j / 4f
                drawLine(gridColor, Offset(8f, y), Offset(w - 8f, y), strokeWidth = 1.dp.toPx())
            }
            // X & Y Axes
            val originX = w * 0.45f
            val originY = h * 0.74f
            drawLine(axisColor, Offset(12f, originY), Offset(w - 12f, originY), strokeWidth = 1.5.dp.toPx())
            drawLine(axisColor, Offset(originX, 10f), Offset(originX, h - 10f), strokeWidth = 1.5.dp.toPx())

            // Smooth Parabola Curve f(x) = a(x - h)^2 + k
            val parabola = Path()
            val steps = 48
            for (i in 0..steps) {
                val t = -1f + 2f * (i / steps.toFloat())
                val px = originX + t * (w * 0.34f)
                val py = (originY + h * 0.10f) - (t * t) * (h * 0.64f)
                if (i == 0) parabola.moveTo(px, py) else parabola.lineTo(px, py)
            }
            drawPath(
                path = parabola,
                brush = Brush.horizontalGradient(listOf(primary, tertiary)),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Roots & Vertex Points
            drawCircle(tertiary, radius = 4.5.dp.toPx(), center = Offset(originX, originY + h * 0.10f))
            drawCircle(primary, radius = 4.5.dp.toPx(), center = Offset(originX - w * 0.135f, originY))
            drawCircle(primary, radius = 4.5.dp.toPx(), center = Offset(originX + w * 0.135f, originY))
        }

        Row(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.primary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = "/detayli",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.tertiary.copy(alpha = 0.18f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = colorScheme.tertiary, modifier = Modifier.size(11.dp))
                    Text(
                        text = "/simulasyon",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun SharkStudioLassoPreview() {
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "lasso_preview")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lasso_dash_phase"
    )

    val paperBg = colorScheme.surface
    val lineInk = colorScheme.outlineVariant.copy(alpha = 0.65f)
    val inkColor = colorScheme.onSurface.copy(alpha = 0.8f)
    val lassoColor = colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Mini PDF / Note Sheet
            drawRoundRect(
                color = paperBg,
                topLeft = Offset(10f, 6f),
                size = Size(w - 20f, h - 12f),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
            )
            // Simulated lines of text/problem
            for (row in 1..4) {
                val y = h * (0.20f + row * 0.16f)
                val endX = if (row == 4) w * 0.56f else w * 0.78f
                drawLine(lineInk, Offset(w * 0.12f, y), Offset(endX, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            // Mini Triangle Sketch inside problem
            val tri = Path().apply {
                moveTo(w * 0.68f, h * 0.34f)
                lineTo(w * 0.83f, h * 0.68f)
                lineTo(w * 0.58f, h * 0.68f)
                close()
            }
            drawPath(tri, color = inkColor, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))

            // Animated Dashed Lasso Loop surrounding the problem
            drawRoundRect(
                color = lassoColor,
                topLeft = Offset(w * 0.08f, h * 0.24f),
                size = Size(w * 0.82f, h * 0.56f),
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), phase = pulse)
                )
            )
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
        ) {
            Text(
                text = "Kementle Çözdür",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun FsrsErrorBankPreview() {
    val colorScheme = MaterialTheme.colorScheme
    val primary = colorScheme.primary
    val secondary = colorScheme.secondary
    val tertiary = colorScheme.tertiary
    val grid = colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawLine(grid, Offset(18f, h * 0.78f), Offset(w - 18f, h * 0.78f), strokeWidth = 1.5.dp.toPx())

            // Memory Retention Curve boosted at 1, 3, 7 day intervals
            val curve = Path().apply {
                moveTo(w * 0.08f, h * 0.26f)
                cubicTo(w * 0.18f, h * 0.30f, w * 0.23f, h * 0.56f, w * 0.30f, h * 0.58f)
                lineTo(w * 0.32f, h * 0.24f)
                cubicTo(w * 0.44f, h * 0.27f, w * 0.52f, h * 0.44f, w * 0.58f, h * 0.46f)
                lineTo(w * 0.60f, h * 0.20f)
                cubicTo(w * 0.74f, h * 0.22f, w * 0.84f, h * 0.30f, w * 0.92f, h * 0.32f)
            }
            drawPath(
                path = curve,
                brush = Brush.horizontalGradient(listOf(primary, secondary, tertiary)),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 1-3-7 Review Nodes
            listOf(
                Offset(w * 0.32f, h * 0.24f),
                Offset(w * 0.60f, h * 0.20f),
                Offset(w * 0.92f, h * 0.32f)
            ).forEach { node ->
                drawCircle(colorScheme.surface, radius = 6.dp.toPx(), center = node)
                drawCircle(primary, radius = 4.dp.toPx(), center = node)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("1. Gün", "3. Gün", "7. Gün").forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = tag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoachingFocusLibraryPreview() {
    val colorScheme = MaterialTheme.colorScheme
    val primary = colorScheme.primary
    val track = colorScheme.outlineVariant.copy(alpha = 0.45f)

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mini Focus Ring
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = primary,
                    startAngle = -90f,
                    sweepAngle = 265f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
                Text("45:00", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
            }
        }

        // Mini Book Progress Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colorScheme.surface,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                    Text(
                        text = "AYT Matematik",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "İlerleme #1 • %74",
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.74f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary)
                    )
                }
            }
        }
    }
}
