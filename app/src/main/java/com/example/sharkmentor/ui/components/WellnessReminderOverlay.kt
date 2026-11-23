package com.example.sharkmentor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WellnessReminderType {
    HYDRATION,
    POSTURE
}

@Immutable
data class PostureMicroExercise(
    val id: Int,
    val title: String,
    val instruction: String,
    val durationBadge: String
)

@Immutable
data class WellnessReminderEvent(
    val id: Long,
    val type: WellnessReminderType,
    val elapsedMinutes: Int,
    val exercise: PostureMicroExercise? = null,
    val motivationText: String
)

/**
 * Global in-app event bus for smartwatch-style Hydration & Posture visual popups.
 * - Never pauses or stops active Focus Timers (only Video Studio listens to Posture to auto-pause video).
 * - Cycles sequentially through 8 distinct desk micro-exercises so consecutive posture reminders never repeat.
 */
object WellnessReminderBus {

    val POSTURE_EXERCISES: List<PostureMicroExercise> = listOf(
        PostureMicroExercise(
            id = 1,
            title = "Ayağa Kalk ve Esne",
            instruction = "Sandalyenden kalk, kollarını yukarı uzatıp 15 saniye sırtını geriye esnet.",
            durationBadge = "20 sn"
        ),
        PostureMicroExercise(
            id = 2,
            title = "Omuz Daireleri",
            instruction = "Omuzlarını yavaşça geriye doğru 10 kez dairesel çevirerek boyun gerginliğini bırak.",
            durationBadge = "20 sn"
        ),
        PostureMicroExercise(
            id = 3,
            title = "20-20-20 Göz Molası",
            instruction = "Ekrandan başını kaldır, 20 saniye boyunca uzağa bakarak gözlerini dinlendir.",
            durationBadge = "20 sn"
        ),
        PostureMicroExercise(
            id = 4,
            title = "Boyun Yan Esnetme",
            instruction = "Başını yavaşça sağa ve sola yatırıp her iki yönde 10 saniye bekle.",
            durationBadge = "25 sn"
        ),
        PostureMicroExercise(
            id = 5,
            title = "Bilek ve El Gevşetme",
            instruction = "Kalem ve klavye yorgunluğunu atmak için parmaklarını aç-kapa, bileklerini çevir.",
            durationBadge = "15 sn"
        ),
        PostureMicroExercise(
            id = 6,
            title = "Göğüs Kafesi Açma",
            instruction = "Ellerini sırtında birleştir, omuzlarını geriye çekip 3 derin nefes al.",
            durationBadge = "20 sn"
        ),
        PostureMicroExercise(
            id = 7,
            title = "Kısa Oda Yürüyüşü",
            instruction = "Odanın içinde 30 saniye yürüyerek kan dolaşımını ve odak enerjini tazele.",
            durationBadge = "30 sn"
        ),
        PostureMicroExercise(
            id = 8,
            title = "Sırt ve Bel Dengeleme",
            instruction = "Ayakta dik dur, belini nazikçe sağa ve sola döndürerek omurganı rahatlat.",
            durationBadge = "25 sn"
        )
    )

    private val HYDRATION_QUOTES = listOf(
        "Bir bardak su zihnini hızla tazeler ve dikkatini keskinleştirir.",
        "Harika gidiyorsun! Kısa bir su yudumu odak süreni daha verimli kılar.",
        "Beynin büyük kısmı sudan oluşur; birkaç yudum suyla enerjini yenile.",
        "Masadaki istikrarın harika — şimdi bir bardak suyla kendini ödüllendir."
    )

    private val postureCursor = AtomicInteger(0)
    private val hydrationCursor = AtomicInteger(0)

    private val _activeEvent = MutableStateFlow<WellnessReminderEvent?>(null)
    val activeEvent: StateFlow<WellnessReminderEvent?> = _activeEvent.asStateFlow()

    fun nextPostureExercise(): PostureMicroExercise {
        val idx = Math.floorMod(postureCursor.getAndIncrement(), POSTURE_EXERCISES.size)
        return POSTURE_EXERCISES[idx]
    }

    fun emitHydration(elapsedMinutes: Int) {
        val quoteIdx = Math.floorMod(hydrationCursor.getAndIncrement(), HYDRATION_QUOTES.size)
        _activeEvent.value = WellnessReminderEvent(
            id = System.currentTimeMillis(),
            type = WellnessReminderType.HYDRATION,
            elapsedMinutes = elapsedMinutes.coerceAtLeast(1),
            exercise = null,
            motivationText = HYDRATION_QUOTES[quoteIdx]
        )
    }

    fun emitPosture(elapsedMinutes: Int) {
        val exercise = nextPostureExercise()
        _activeEvent.value = WellnessReminderEvent(
            id = System.currentTimeMillis(),
            type = WellnessReminderType.POSTURE,
            elapsedMinutes = elapsedMinutes.coerceAtLeast(1),
            exercise = exercise,
            motivationText = "Bedenini kısa bir hareketle canlandır; verimli çalışmanın sırrı sağlıklı duruştur."
        )
    }

    fun dismissCurrent() {
        _activeEvent.value = null
    }
}

/**
 * Smartwatch-style theme-synchronized visual popup overlay for Hydration & Posture reminders.
 * - Dismisses instantly when the user taps ANYWHERE on screen.
 * - Never interrupts or pauses active Focus Timers.
 */
@Composable
fun WellnessReminderSmartwatchOverlay(
    modifier: Modifier = Modifier
) {
    val activeEvent by WellnessReminderBus.activeEvent.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = activeEvent != null,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.88f, animationSpec = tween(240)),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f, animationSpec = tween(180)),
        modifier = modifier.fillMaxSize()
    ) {
        val event = activeEvent ?: return@AnimatedVisibility
        val isHydration = event.type == WellnessReminderType.HYDRATION
        val accentColor = if (isHydration) {
            Color(0xFF0EA5E9) // Crisp hydration blue
        } else {
            colorScheme.primary
        }

        val infinite = rememberInfiniteTransition(label = "wellnessRingPulse")
        val ringPulse by infinite.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ringPulseAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.44f))
                .pointerInput(event.id) {
                    detectTapGestures(onTap = { WellnessReminderBus.dismissCurrent() })
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 336.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(36.dp),
                color = colorScheme.surface,
                border = BorderStroke(2.dp, accentColor.copy(alpha = 0.65f * ringPulse)),
                shadowElevation = 14.dp,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Smartwatch top status pill
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (isHydration) {
                                "Su Hatırlatması • ${event.elapsedMinutes} dk"
                            } else {
                                "Duruş & Hareket • ${event.elapsedMinutes} dk"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    // Smartwatch Circular Dial Illustration
                    Box(
                        modifier = Modifier.size(116.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isHydration) {
                            FilledWaterGlassCanvas(
                                accentColor = accentColor,
                                ringAlpha = ringPulse,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            StandUpFromChairCanvas(
                                accentColor = accentColor,
                                secondaryColor = colorScheme.onSurface,
                                ringAlpha = ringPulse,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Text(
                        text = if (isHydration) "Bir Bardak Su Zamanı" else "Kalk ve Kısa Bir Esne",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = event.motivationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Rotating Desk Micro-Exercise Card for Posture Reminders
                    event.exercise?.let { ex ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.58f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ex.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = accentColor.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = ex.durationBadge,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = ex.instruction,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Devam etmek için ekrana dokun",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

/**
 * Custom Canvas illustration: Filled glass of water inside a glowing smartwatch ring.
 */
@Composable
private fun FilledWaterGlassCanvas(
    accentColor: Color,
    ringAlpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = (minOf(w, h) / 2f) - 4.dp.toPx()
        val center = Offset(w / 2f, h / 2f)

        // Outer smartwatch ring
        drawCircle(
            color = accentColor.copy(alpha = 0.12f),
            radius = radius,
            center = center
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.65f * ringAlpha),
            radius = radius,
            center = center,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Tapered glass coordinates
        val topY = h * 0.27f
        val botY = h * 0.77f
        val topHalfW = w * 0.19f
        val botHalfW = w * 0.14f

        // Filled water interior (72% full)
        val waterTopY = topY + (botY - topY) * 0.24f
        val waterHalfTopW = topHalfW - (topHalfW - botHalfW) * 0.24f
        val waterPath = Path().apply {
            moveTo(center.x - waterHalfTopW + 3f, waterTopY)
            quadraticTo(center.x - waterHalfTopW * 0.35f, waterTopY - 6f, center.x, waterTopY)
            quadraticTo(center.x + waterHalfTopW * 0.35f, waterTopY + 6f, center.x + waterHalfTopW - 3f, waterTopY)
            lineTo(center.x + botHalfW - 3f, botY - 3f)
            lineTo(center.x - botHalfW + 3f, botY - 3f)
            close()
        }
        drawPath(
            path = waterPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF38BDF8), accentColor),
                startY = waterTopY,
                endY = botY
            )
        )

        // Glass outline
        val glassPath = Path().apply {
            moveTo(center.x - topHalfW, topY)
            lineTo(center.x - botHalfW, botY)
            lineTo(center.x + botHalfW, botY)
            lineTo(center.x + topHalfW, topY)
        }
        drawPath(
            path = glassPath,
            color = accentColor,
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Sparkle droplet highlight inside water
        drawCircle(
            color = Color.White.copy(alpha = 0.75f),
            radius = 3.dp.toPx(),
            center = Offset(center.x - botHalfW * 0.35f, botY - (botY - waterTopY) * 0.45f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = 1.8.dp.toPx(),
            center = Offset(center.x + botHalfW * 0.22f, botY - (botY - waterTopY) * 0.28f)
        )
    }
}

/**
 * Custom Canvas illustration: Person standing up from a chair & stretching arms upward inside a smartwatch ring.
 */
@Composable
private fun StandUpFromChairCanvas(
    accentColor: Color,
    secondaryColor: Color,
    ringAlpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = (minOf(w, h) / 2f) - 4.dp.toPx()
        val center = Offset(w / 2f, h / 2f)
        val strokeW = 3.2.dp.toPx()

        // Outer smartwatch activity ring
        drawCircle(
            color = accentColor.copy(alpha = 0.12f),
            radius = radius,
            center = center
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.65f * ringAlpha),
            radius = radius,
            center = center,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Chair (left side behind the standing figure)
        val chairColor = secondaryColor.copy(alpha = 0.45f)
        val chairBackX = w * 0.29f
        val chairSeatY = h * 0.61f
        val chairFrontX = w * 0.44f
        val floorY = h * 0.77f
        // Chair backrest + rear leg
        drawLine(chairColor, Offset(chairBackX, h * 0.43f), Offset(chairBackX, floorY), strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round)
        // Chair seat
        drawLine(chairColor, Offset(chairBackX, chairSeatY), Offset(chairFrontX, chairSeatY), strokeWidth = 2.8.dp.toPx(), cap = StrokeCap.Round)
        // Chair front leg
        drawLine(chairColor, Offset(chairFrontX, chairSeatY), Offset(chairFrontX, floorY), strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round)

        // Standing & stretching person (center-right)
        val personX = w * 0.56f
        val headCenter = Offset(personX, h * 0.31f)
        val neckY = h * 0.39f
        val hipY = h * 0.57f

        // Head
        drawCircle(color = accentColor, radius = 6.dp.toPx(), center = headCenter)
        // Torso (upright)
        drawLine(accentColor, Offset(personX, neckY), Offset(personX, hipY), strokeWidth = strokeW, cap = StrokeCap.Round)
        // Arms raised stretching upward in a cheerful V
        drawLine(accentColor, Offset(personX, neckY + 3f), Offset(personX - w * 0.13f, h * 0.25f), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(accentColor, Offset(personX, neckY + 3f), Offset(personX + w * 0.14f, h * 0.24f), strokeWidth = strokeW, cap = StrokeCap.Round)
        // Legs standing firmly on floor
        drawLine(accentColor, Offset(personX, hipY), Offset(personX - w * 0.08f, floorY), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(accentColor, Offset(personX, hipY), Offset(personX + w * 0.09f, floorY), strokeWidth = strokeW, cap = StrokeCap.Round)

        // Subtle upward motion arrow next to chair indicating standing up
        val arrowX = w * 0.38f
        drawLine(accentColor.copy(alpha = 0.7f), Offset(arrowX, h * 0.52f), Offset(arrowX, h * 0.38f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(accentColor.copy(alpha = 0.7f), Offset(arrowX, h * 0.38f), Offset(arrowX - 4.dp.toPx(), h * 0.42f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(accentColor.copy(alpha = 0.7f), Offset(arrowX, h * 0.38f), Offset(arrowX + 4.dp.toPx(), h * 0.42f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

        // Small rounded floor line
        drawRoundRect(
            color = secondaryColor.copy(alpha = 0.22f),
            topLeft = Offset(w * 0.24f, floorY + 1.5.dp.toPx()),
            size = Size(w * 0.52f, 2.dp.toPx()),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }
}
