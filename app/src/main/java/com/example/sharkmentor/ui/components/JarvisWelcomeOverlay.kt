package com.example.sharkmentor.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharkmentor.data.weather.WeatherSettings
import com.example.sharkmentor.data.weather.WeatherUiState
import com.example.sharkmentor.data.weather.formatTemp
import com.example.sharkmentor.data.weather.weatherConditionLabel
import com.example.sharkmentor.ui.navigation.Screen
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class JarvisQuickAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val route: String
)

data class JarvisBriefing(
    val greeting: String,
    val statusBadge: String,
    val recommendation: String,
    val navigationPrompt: String,
    val actions: List<JarvisQuickAction>
)

/**
 * Pure-Kotlin briefing & time-of-day recommendation engine for the personal J.A.R.V.I.S. launch HUD.
 * Runs cleanly on the JVM for deterministic unit tests.
 */
object JarvisBriefingEngine {

    fun buildGreeting(hour: Int): String {
        val safeHour = ((hour % 24) + 24) % 24
        return when (safeHour) {
            in 5..11 -> "Günaydın efendim."
            in 12..17 -> "İyi günler efendim."
            in 18..21 -> "İyi akşamlar efendim."
            else -> "İyi geceler efendim."
        }
    }

    fun buildStatusBadge(hour: Int): String {
        val safeHour = ((hour % 24) + 24) % 24
        return when (safeHour) {
            in 5..11 -> "SABAH PROTOKOLÜ • ÇEVRİMİÇİ"
            in 12..17 -> "AKTİF OPERASYON • ÇEVRİMİÇİ"
            in 18..21 -> "AKŞAM ANALİZİ • ÇEVRİMİÇİ"
            else -> "GECE PROTOKOLÜ • ÇEVRİMİÇİ"
        }
    }

    fun buildBriefing(
        hour: Int,
        dueErrorQuestions: Int = 0,
        weatherTempC: Double? = null,
        weatherCode: Int? = null
    ): JarvisBriefing {
        val safeHour = ((hour % 24) + 24) % 24
        val greeting = buildGreeting(safeHour)
        val statusBadge = buildStatusBadge(safeHour)

        val weatherNote = buildWeatherContextNote(weatherTempC, weatherCode)

        val coreAdvice = when {
            dueErrorQuestions >= 5 ->
                "Hata Bankanızda zamanı gelen $dueErrorQuestions tekrar sorusu birikmiş efendim. Önce bunları temizleyip ardından odak seansına geçmemizi öneririm."
            dueErrorQuestions in 1..4 ->
                "Hata Bankanızda $dueErrorQuestions tekrar sorusu hazır bekliyor efendim. Hızlı bir ısınma tekrarı ile günün verimini yükseltebiliriz."
            safeHour in 5..11 ->
                "Zihninizin en berrak olduğu sabah saatlerindeyiz efendim. En zorlayıcı dersten 45 dakikalık bir derin odak bloğuyla başlayalım mı?"
            safeHour in 12..17 ->
                "Günün en verimli çalışma penceresindeyiz efendim. Kaynak kitaplarınızdaki ilerlemeyi sürdürelim ya da takıldığınız bir soruyu görselli çözelim."
            safeHour in 18..21 ->
                "Akşam etüt saatlerine girdik efendim. Bugünkü notları gözden geçirip kısa bir soru tekrar bloğuyla günü güçlü kapatabiliriz."
            else ->
                "Gece sessizliği derin odak için ideal efendim. Gözlerinizi yormadan sakin bir tekrar ya da Zen nefes molası başlatmak ister misiniz?"
        }

        val fullRecommendation = if (weatherNote.isNotBlank()) {
            "$coreAdvice $weatherNote"
        } else {
            coreAdvice
        }

        val actions = buildList {
            if (dueErrorQuestions > 0) {
                add(
                    JarvisQuickAction(
                        id = "error_bank",
                        title = "Hata Bankasına Git",
                        subtitle = "$dueErrorQuestions tekrar sorusu bekliyor",
                        route = Screen.ErrorBank.route
                    )
                )
            }
            when (safeHour) {
                in 5..11 -> {
                    add(
                        JarvisQuickAction(
                            id = "focus",
                            title = "Odak Masasını Aç",
                            subtitle = "Sabah derin odak seansı başlat",
                            route = Screen.Focus.route
                        )
                    )
                    add(
                        JarvisQuickAction(
                            id = "plans",
                            title = "Günlük Planı Gör",
                            subtitle = "Bugünün etüt bloklarını kontrol et",
                            route = Screen.Dashboard.route
                        )
                    )
                    add(
                        JarvisQuickAction(
                            id = "jarvis_ai",
                            title = "J.A.R.V.I.S. ile Sohbet",
                            subtitle = "Soru, grafik veya strateji iste",
                            route = Screen.Shark.route
                        )
                    )
                }
                in 12..17 -> {
                    add(
                        JarvisQuickAction(
                            id = "books",
                            title = "Kitaplarımı Aç",
                            subtitle = "Kaynak ve konu ilerlemesine devam et",
                            route = Screen.Resources.route
                        )
                    )
                    add(
                        JarvisQuickAction(
                            id = "jarvis_ai",
                            title = "J.A.R.V.I.S. Görselli Çözüm",
                            subtitle = "Takıldığınız soruyu grafikli çözelim",
                            route = Screen.Shark.route
                        )
                    )
                    add(
                        JarvisQuickAction(
                            id = "focus",
                            title = "Odak Masasına Geç",
                            subtitle = "Kesintisiz etüt sayacını başlat",
                            route = Screen.Focus.route
                        )
                    )
                }
                in 18..21 -> {
                    add(
                        JarvisQuickAction(
                            id = "notes",
                            title = "Notlar & PDF'leri Aç",
                            subtitle = "Akşam konu tekrarı ve özetler",
                            route = Screen.Notes.route
                        )
                    )
                    add(
                        JarvisQuickAction(
                            id = "jarvis_ai",
                            title = "J.A.R.V.I.S. Analiz",
                            subtitle = "Günü özetle veya soru çöz",
                            route = Screen.Shark.route
                        )
                    )
                    add(
                        JarvisQuickAction(
                            id = "error_bank_evening",
                            title = "Hata Bankasını İncele",
                            subtitle = "Yanlış soruların üstünden geç",
                            route = Screen.ErrorBank.route
                        )
                    )
                }
                else -> {
                    add(
                        JarvisQuickAction(
                            id = "zen_room",
                            title = "Zen Odasına Geç",
                            subtitle = "Gece için sakin zihin protokolü",
                            route = "zen_room"
                        )
                    )
                    add(
                        JarvisQuickAction(
                            id = "focus",
                            title = "Gece Odak Seansı",
                            subtitle = "Sessiz ve kısa bir çalışma bloğu",
                            route = Screen.Focus.route
                        )
                    )
                    add(
                        JarvisQuickAction(
                            id = "jarvis_ai",
                            title = "J.A.R.V.I.S. ile Konuş",
                            subtitle = "Yarının planını birlikte hazırlayalım",
                            route = Screen.Shark.route
                        )
                    )
                }
            }
        }.distinctBy { it.route }.take(3)

        return JarvisBriefing(
            greeting = greeting,
            statusBadge = statusBadge,
            recommendation = fullRecommendation,
            navigationPrompt = "Nereden başlamak istersiniz efendim?",
            actions = actions
        )
    }

    private fun buildWeatherContextNote(tempC: Double?, code: Int?): String {
        if (tempC == null || code == null) return ""
        return when {
            code in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99) ->
                "Dışarıda yağış var, içeride sıcak bir odak ortamı için harika bir an."
            code in setOf(71, 73, 75, 77, 85, 86) ->
                "Dışarıda kar yağışı hakim, masanızda kesintisiz ilerlemek için ideal bir atmosfer."
            tempC <= 5.0 ->
                "Hava oldukça serin, sıcak bir içecekle odak bloğunuza eşlik edebilirim."
            tempC >= 30.0 ->
                "Hava oldukça sıcak, çalışma aralarında su tüketiminizi aksatmamanızı öneririm."
            else -> ""
        }
    }
}

private fun iconForJarvisRoute(route: String): ImageVector = when (route) {
    Screen.ErrorBank.route -> Icons.Default.FitnessCenter
    Screen.Focus.route -> Icons.Default.Timer
    Screen.Dashboard.route -> Icons.Default.CalendarMonth
    Screen.Resources.route -> Icons.Default.LibraryBooks
    Screen.Notes.route -> Icons.Default.StickyNote2
    Screen.Shark.route -> Icons.Default.AutoAwesome
    "zen_room" -> Icons.Default.SelfImprovement
    else -> Icons.Default.AutoAwesome
}

/**
 * Animated 60fps Iron Man-inspired J.A.R.V.I.S. holographic core emblem:
 * counter-rotating golden-amber & cyan HUD telemetry rings, radial tick dial,
 * rotating neural hexagon lattice, orbiting data nodes, and pulsing energy core.
 */
@Composable
fun JarvisHolographicCoreEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 124.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_holo_core")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "jarvis_outer_ring"
    )
    val midAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "jarvis_mid_ring"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "jarvis_inner_hex"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jarvis_core_pulse"
    )

    val amberCore = Color(0xFFFFB703)
    val goldBright = Color(0xFFFFDDA1)
    val deepOrange = Color(0xFFFB8500)
    val holoCyan = Color(0xFF38BDF8)

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val center = Offset(cx, cy)
        val radius = this.size.minDimension / 2f

        // 1. Ambient Holographic Aura Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    amberCore.copy(alpha = 0.34f * (pulse / 1.12f)),
                    deepOrange.copy(alpha = 0.14f),
                    holoCyan.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 1.04f
            ),
            radius = radius * 1.04f,
            center = center
        )

        // 2. Outer Rotating Telemetry Tick Ring (36 radial ticks)
        val outerR = radius * 0.92f
        val tickCount = 36
        for (i in 0 until tickCount) {
            val deg = outerAngle + i * (360f / tickCount)
            val rad = deg * (PI / 180.0)
            val isMajor = i % 3 == 0
            val innerTickR = if (isMajor) outerR * 0.88f else outerR * 0.93f
            val start = Offset(
                cx + (cos(rad) * innerTickR).toFloat(),
                cy + (sin(rad) * innerTickR).toFloat()
            )
            val end = Offset(
                cx + (cos(rad) * outerR).toFloat(),
                cy + (sin(rad) * outerR).toFloat()
            )
            drawLine(
                color = if (isMajor) amberCore.copy(alpha = 0.85f) else holoCyan.copy(alpha = 0.42f),
                start = start,
                end = end,
                strokeWidth = if (isMajor) 2.2f else 1.2f,
                cap = StrokeCap.Round
            )
        }

        // 3. Outer Dashed HUD Arc Ring (rotating clockwise)
        val arcR1 = radius * 0.80f
        drawCircle(
            color = holoCyan.copy(alpha = 0.25f),
            radius = arcR1,
            center = center,
            style = Stroke(
                width = 1.6f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), outerAngle)
            )
        )
        for (seg in 0 until 3) {
            drawArc(
                color = amberCore.copy(alpha = 0.88f),
                startAngle = outerAngle + seg * 120f,
                sweepAngle = 52f,
                useCenter = false,
                topLeft = Offset(cx - arcR1, cy - arcR1),
                size = Size(arcR1 * 2f, arcR1 * 2f),
                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
            )
        }

        // 4. Counter-Rotating Mid Hologram Segments (amber + cyan)
        val arcR2 = radius * 0.64f
        for (seg in 0 until 4) {
            drawArc(
                color = if (seg % 2 == 0) goldBright.copy(alpha = 0.85f) else holoCyan.copy(alpha = 0.72f),
                startAngle = midAngle + seg * 90f,
                sweepAngle = 44f,
                useCenter = false,
                topLeft = Offset(cx - arcR2, cy - arcR2),
                size = Size(arcR2 * 2f, arcR2 * 2f),
                style = Stroke(width = 2.6f, cap = StrokeCap.Round)
            )
        }

        // 5. Orbiting Telemetry Nodes on Mid Ring
        for (node in 0 until 3) {
            val deg = midAngle * 1.4f + node * 120f
            val rad = deg * (PI / 180.0)
            val nodePos = Offset(
                cx + (cos(rad) * arcR2).toFloat(),
                cy + (sin(rad) * arcR2).toFloat()
            )
            drawCircle(
                color = if (node == 0) holoCyan else goldBright,
                radius = 3.6f,
                center = nodePos
            )
        }

        // 6. Inner Rotating Neural Hexagon Lattice
        val hexR = radius * 0.44f
        val hexPath = Path()
        val hexVertices = Array(6) { i ->
            val rad = (innerAngle + i * 60f) * (PI / 180.0)
            Offset(
                cx + (cos(rad) * hexR).toFloat(),
                cy + (sin(rad) * hexR).toFloat()
            )
        }
        hexPath.moveTo(hexVertices[0].x, hexVertices[0].y)
        for (i in 1 until 6) hexPath.lineTo(hexVertices[i].x, hexVertices[i].y)
        hexPath.close()

        drawPath(
            path = hexPath,
            color = amberCore.copy(alpha = 0.78f),
            style = Stroke(width = 2.0f)
        )
        // Neural filaments connecting vertices through center
        for (i in 0 until 3) {
            drawLine(
                color = goldBright.copy(alpha = 0.32f),
                start = hexVertices[i],
                end = hexVertices[i + 3],
                strokeWidth = 1.2f
            )
        }

        // 7. Pulsing Central J.A.R.V.I.S. Energy Core
        val coreR = radius * 0.25f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFFBEB),
                    goldBright,
                    amberCore,
                    deepOrange.copy(alpha = 0.15f)
                ),
                center = center,
                radius = coreR * 1.35f
            ),
            radius = coreR * 1.35f,
            center = center
        )
        drawCircle(
            color = Color(0xFFFFFBEB).copy(alpha = 0.92f),
            radius = radius * 0.10f,
            center = center
        )
    }
}

/**
 * Full-screen translucent holographic launch briefing shown ONCE per app launch
 * exclusively when the personal `JARVIS` code is unlocked (reverted completely by `ULTRON`).
 */
@Composable
fun JarvisLaunchBriefingOverlay(
    weatherState: WeatherUiState,
    weatherSettings: WeatherSettings,
    dueQuestionsCount: Int,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    var nowTime by remember { mutableStateOf(LocalTime.now()) }
    var nowDate by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowTime = LocalTime.now()
            nowDate = LocalDate.now()
            delay(1000L)
        }
    }

    val turkishLocale = remember { Locale("tr", "TR") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", turkishLocale) }
    val secondsFormatter = remember { DateTimeFormatter.ofPattern("ss", turkishLocale) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM EEEE", turkishLocale) }

    val formattedTime = remember(nowTime.hour, nowTime.minute) { nowTime.format(timeFormatter) }
    val formattedSeconds = remember(nowTime.second) { nowTime.format(secondsFormatter) }
    val formattedDate = remember(nowDate) {
        nowDate.format(dateFormatter).replaceFirstChar { it.titlecase(turkishLocale) }
    }

    val briefing = remember(
        nowTime.hour,
        dueQuestionsCount,
        weatherState.currentTempCelsius,
        weatherState.currentWeatherCode
    ) {
        JarvisBriefingEngine.buildBriefing(
            hour = nowTime.hour,
            dueErrorQuestions = dueQuestionsCount,
            weatherTempC = weatherState.currentTempCelsius,
            weatherCode = weatherState.currentWeatherCode
        )
    }

    val hudBgTop = Color(0xFF060C17)
    val hudBgMid = Color(0xFF0A1424)
    val hudAmber = Color(0xFFFFB703)
    val hudAmberSoft = Color(0xFFFFDDA1)
    val hudCyan = Color(0xFF38BDF8)
    val hudTextPrimary = Color(0xFFF8FAFC)
    val hudTextMuted = Color(0xFF94A3B8)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        hudBgTop.copy(alpha = 0.94f),
                        hudBgMid.copy(alpha = 0.96f),
                        Color(0xFF040811).copy(alpha = 0.97f)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 20.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0B1628).copy(alpha = 0.92f),
            border = BorderStroke(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        hudAmber.copy(alpha = 0.58f),
                        hudCyan.copy(alpha = 0.35f),
                        hudAmber.copy(alpha = 0.48f)
                    )
                )
            ),
            shadowElevation = 20.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top HUD Status Bar + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = hudAmber.copy(alpha = 0.13f),
                        border = BorderStroke(1.dp, hudAmber.copy(alpha = 0.42f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(hudAmber, CircleShape)
                            )
                            Text(
                                text = briefing.statusBadge,
                                color = hudAmberSoft,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.0.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = hudTextMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Animated Rotating Holographic J.A.R.V.I.S. Core Emblem
                JarvisHolographicCoreEmblem(size = 122.dp)

                Text(
                    text = "J.A.R.V.I.S.",
                    color = hudAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Live Clock + Date
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formattedTime,
                        color = hudTextPrimary,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = ":$formattedSeconds",
                        color = hudCyan.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }

                Text(
                    text = formattedDate,
                    color = hudTextMuted,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Live Weather Pill Directly Underneath the Clock
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = hudCyan.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, hudCyan.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = weatherIconForCode(weatherState.currentWeatherCode),
                            contentDescription = null,
                            tint = hudCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        val weatherLabel = remember(weatherState, weatherSettings.useFahrenheit) {
                            val city = weatherState.city.ifBlank { "Konum" }
                            val temp = weatherState.currentTempCelsius
                            if (temp != null) {
                                val tempStr = formatTemp(temp, weatherSettings.useFahrenheit)
                                val cond = weatherConditionLabel(weatherState.currentWeatherCode)
                                "$city • $tempStr • $cond"
                            } else {
                                "$city • Hava durumu güncelleniyor..."
                            }
                        }
                        Text(
                            text = weatherLabel,
                            color = hudTextPrimary.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time-of-day Greeting & Contextual Recommendation Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF102038).copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, hudAmber.copy(alpha = 0.24f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = briefing.greeting,
                            color = hudAmberSoft,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = briefing.recommendation,
                            color = hudTextPrimary.copy(alpha = 0.88f),
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive In-App Navigation Prompt
                Text(
                    text = briefing.navigationPrompt,
                    color = hudCyan.copy(alpha = 0.92f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Start
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    briefing.actions.forEachIndexed { index, action ->
                        val accent = if (index == 0) hudAmber else hudCyan
                        Surface(
                            onClick = {
                                onDismiss()
                                onNavigate(action.route)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = accent.copy(alpha = 0.11f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.36f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = accent.copy(alpha = 0.18f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = iconForJarvisRoute(action.route),
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = action.title,
                                        color = hudTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = action.subtitle,
                                        color = hudTextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dismiss / Proceed Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = hudAmber,
                        contentColor = Color(0xFF0A101D)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ana Ekrana Geç",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}
