package com.example.sharkmentor.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sharkmentor.core.audio.ZenSound
import com.example.sharkmentor.data.topbar.TopBarAnimationType
import com.example.sharkmentor.data.topbar.TopBarFontOption
import com.example.sharkmentor.data.topbar.TopBarSettings
import com.example.sharkmentor.data.topbar.TopBarTextSource
import com.example.sharkmentor.data.topbar.capitalizeTurkishSentence
import com.example.sharkmentor.data.weather.HourlyForecastItem
import com.example.sharkmentor.data.weather.WeatherSettings
import com.example.sharkmentor.data.weather.WeatherUiState
import com.example.sharkmentor.data.weather.formatTemp
import com.example.sharkmentor.data.weather.weatherConditionLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun weatherIconForCode(code: Int): ImageVector {
    return when (code) {
        0, 1 -> Icons.Default.WbSunny
        2, 3 -> Icons.Default.Cloud
        45, 48 -> Icons.Default.Grain
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Default.WaterDrop
        71, 73, 75, 77, 85, 86 -> Icons.Default.AcUnit
        95, 96, 99 -> Icons.Default.Thunderstorm
        else -> Icons.Default.Cloud
    }
}

/**
 * Slices hourly items to strictly show 2 hours before [now] through 10 hours after [now]
 * in chronological order (13 hourly slots max), supporting midnight rollover seamlessly.
 */
internal fun filterWeatherHourlyWindow(
    hourly: List<HourlyForecastItem>,
    now: LocalDateTime = LocalDateTime.now()
): List<HourlyForecastItem> {
    if (hourly.isEmpty()) return emptyList()
    val currentHourStart = now.truncatedTo(ChronoUnit.HOURS)
    val startWindow = currentHourStart.minusHours(2)
    val endWindow = currentHourStart.plusHours(10)

    val byIso = hourly.mapNotNull { item ->
        val parsed = runCatching {
            if (item.isoTime.isNotBlank()) LocalDateTime.parse(item.isoTime.take(16)) else null
        }.getOrNull()
        if (parsed != null && !parsed.isBefore(startWindow) && !parsed.isAfter(endWindow)) {
            parsed to item
        } else null
    }.sortedBy { it.first }.map { it.second }

    if (byIso.isNotEmpty()) return byIso

    // Legacy cache fallback (hourLabel only, 00:00..23:00): order chronologically -2..+10
    val nowHour = now.hour
    val orderedHours = (-2..10).map { offset -> (nowHour + offset + 24) % 24 }
    val byHour = hourly.associateBy { it.hourLabel.substringBefore(":").trim().toIntOrNull() }
    val legacyResult = orderedHours.mapNotNull { h -> byHour[h] }
    return legacyResult.ifEmpty { hourly.take(13) }
}

@Composable
fun TopBarWeatherPill(
    weatherState: WeatherUiState,
    settings: WeatherSettings,
    onClick: () -> Unit,
    showBubble: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tempLabel = weatherState.currentTempCelsius?.let {
        formatTemp(it, settings.useFahrenheit)
    } ?: if (weatherState.isLoading) "..." else "--"

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (showBubble) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (showBubble) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (showBubble) 10.dp else 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = weatherIconForCode(weatherState.currentWeatherCode),
                contentDescription = weatherConditionLabel(weatherState.currentWeatherCode),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = tempLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun WeatherHourlyDialog(
    weatherState: WeatherUiState,
    settings: WeatherSettings,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentTempText = weatherState.currentTempCelsius?.let {
        formatTemp(it, settings.useFahrenheit)
    } ?: "--"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${weatherState.city}, ${weatherState.country}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${weatherConditionLabel(weatherState.currentWeatherCode)} • $currentTempText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !weatherState.isLoading
                ) {
                    if (weatherState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Yenile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!weatherState.errorMessage.isNullOrBlank()) {
                    Text(
                        text = weatherState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (weatherState.hourly.isEmpty()) {
                    Text(
                        text = "Saatlik tahmin verisi henüz yüklenmedi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Saatlik Tahmin",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val nowSnapshot = remember { LocalDateTime.now() }
                    val currentHourLabel = remember(nowSnapshot) {
                        "%02d:00".format(nowSnapshot.hour)
                    }
                    val filteredHourly = remember(weatherState.hourly, nowSnapshot) {
                        filterWeatherHourlyWindow(weatherState.hourly, nowSnapshot)
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredHourly) { item ->
                            val isNow = item.hourLabel == currentHourLabel
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isNow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    width = if (isNow) 1.5.dp else 1.dp,
                                    color = if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(72.dp)
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.hourLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = weatherIconForCode(item.weatherCode),
                                        contentDescription = weatherConditionLabel(item.weatherCode),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = formatTemp(item.tempCelsius, settings.useFahrenheit),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Kapat",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

object PendingAiPromptBus {
    val pendingPrompt = MutableStateFlow<String?>(null)
}

object PendingLeagueNavigationBus {
    val openDmInboxDirectly = MutableStateFlow(false)
}

sealed interface TopBarSuggestionAction {
    data class OpenAiPrompt(val prompt: String) : TopBarSuggestionAction
    data class StartZenSound(val sound: ZenSound, val feedbackMessage: String) : TopBarSuggestionAction
    data class OpenRoute(val route: String) : TopBarSuggestionAction
    data class WellnessReminder(val feedbackMessage: String) : TopBarSuggestionAction
}

data class TopBarSuggestionEntry(
    val text: String,
    val action: TopBarSuggestionAction
)

fun isRainyWeatherCode(code: Int): Boolean =
    code in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)

fun isSnowyOrColdWeather(code: Int, tempCelsius: Double?): Boolean =
    code in setOf(71, 73, 75, 77, 85, 86) || (tempCelsius != null && tempCelsius <= 9.0)

/**
 * Pure builder combining AI prompt suggestions with actionable in-app contextual suggestions
 * (weather-matched Zen ambient sounds, Error Bank / Zen Room shortcuts, and long-study hydration/posture cues).
 */
fun buildSmartTopBarSuggestions(
    weatherState: WeatherUiState?,
    isAnyFocusRunning: Boolean,
    elapsedFocusMinutes: Int,
    settings: TopBarSettings
): List<TopBarSuggestionEntry> {
    val result = mutableListOf<TopBarSuggestionEntry>()

    // 1. Long-study wellness reminders (Hydration & Posture) prioritized during active long sessions
    if (isAnyFocusRunning) {
        if (settings.hydrationReminderEnabled && elapsedFocusMinutes >= settings.hydrationIntervalMinutes) {
            result.add(
                TopBarSuggestionEntry(
                    text = capitalizeTurkishSentence("${elapsedFocusMinutes} dk oldu, bir bardak su içmeyi unutma"),
                    action = TopBarSuggestionAction.WellnessReminder("Su molası kaydedildi, zihnin tazelendi.")
                )
            )
        }
        if (settings.postureReminderEnabled && elapsedFocusMinutes >= settings.postureIntervalMinutes) {
            result.add(
                TopBarSuggestionEntry(
                    text = capitalizeTurkishSentence("Duruşunu kontrol et, sırtını dikleştirip omuzlarını gevşet"),
                    action = TopBarSuggestionAction.WellnessReminder("Dik duruş kontrol edildi, verimli çalışmalar.")
                )
            )
        }
    }

    // 2. Contextual in-app suggestions (Weather -> Ambient Sound, Quick In-App Actions)
    if (settings.inAppSuggestionsEnabled) {
        if (weatherState != null) {
            val code = weatherState.currentWeatherCode
            val temp = weatherState.currentTempCelsius
            when {
                isRainyWeatherCode(code) -> {
                    result.add(
                        TopBarSuggestionEntry(
                            text = capitalizeTurkishSentence("Hava yağmurlu, odak için yağmur sesi açalım mı?"),
                            action = TopBarSuggestionAction.StartZenSound(ZenSound.RAIN, "Yağmur sesi başlatıldı")
                        )
                    )
                    result.add(
                        TopBarSuggestionEntry(
                            text = capitalizeTurkishSentence("Yağmurlu güne özel Londra yağmuru sesi aç"),
                            action = TopBarSuggestionAction.StartZenSound(ZenSound.LONDON_RAIN, "Londra yağmuru sesi başlatıldı")
                        )
                    )
                }
                isSnowyOrColdWeather(code, temp) -> {
                    result.add(
                        TopBarSuggestionEntry(
                            text = capitalizeTurkishSentence("Serin havada kamp ateşi sesiyle odaklanalım mı?"),
                            action = TopBarSuggestionAction.StartZenSound(ZenSound.FIRE, "Kamp ateşi sesi başlatıldı")
                        )
                    )
                }
                else -> {
                    result.add(
                        TopBarSuggestionEntry(
                            text = capitalizeTurkishSentence("Derin odak için kafe uğultusu açalım mı?"),
                            action = TopBarSuggestionAction.StartZenSound(ZenSound.CAFE, "Kafe uğultusu başlatıldı")
                        )
                    )
                    result.add(
                        TopBarSuggestionEntry(
                            text = capitalizeTurkishSentence("Sakin odak için orman ambiyansı başlat"),
                            action = TopBarSuggestionAction.StartZenSound(ZenSound.FOREST, "Orman sesi başlatıldı")
                        )
                    )
                }
            }
        }
        result.add(
            TopBarSuggestionEntry(
                text = capitalizeTurkishSentence("Hata Bankası tekrarlarını gözden geçir"),
                action = TopBarSuggestionAction.OpenRoute("error_bank")
            )
        )
        result.add(
            TopBarSuggestionEntry(
                text = capitalizeTurkishSentence("Zen Odası ambiyans seslerini aç"),
                action = TopBarSuggestionAction.OpenRoute("zen_room")
            )
        )
    }

    // 3. AI prompt suggestions (all capitalized cleanly in Turkish)
    val aiPrompts = listOf(
        "Bugün ne planlayalım?",
        "Deneme analizini yapalım mı?",
        "Zayıf konuları çıkaralım",
        "25 dk odak seansı kuralım",
        "Haftalık programını güncelleyelim",
        "Bugünkü hedeflerini belirle",
        "Yanlış sorularını özetleyelim",
        "Tekrar listeni gözden geçirelim",
        "Hangi dersten başlayalım?",
        "Net artış stratejini kuralım",
        "Sınav öncesi hızlı tekrar?",
        "Günlük çalışma ritmini ölçelim",
        "Öncelikli konuları sıralayalım",
        "Shark AI ile yol haritanı çiz"
    )
    aiPrompts.forEach { p ->
        val clean = capitalizeTurkishSentence(p)
        result.add(TopBarSuggestionEntry(clean, TopBarSuggestionAction.OpenAiPrompt(clean)))
    }

    return result
}

@Composable
fun TopBarAiShortcutBox(
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    topBarSettings: TopBarSettings = TopBarSettings(),
    showBubble: Boolean = true,
    weatherState: WeatherUiState? = null,
    isAnyFocusRunning: Boolean = false,
    elapsedFocusMinutes: Int = 0,
    onStartZenSound: ((ZenSound, String) -> Unit)? = null,
    onNavigateRoute: ((String) -> Unit)? = null,
    onWellnessAck: ((String) -> Unit)? = null
) {
    val smartSuggestions = remember(
        weatherState?.currentWeatherCode,
        weatherState?.currentTempCelsius,
        isAnyFocusRunning,
        elapsedFocusMinutes / 5, // coarse bucket to prevent per-second recomposition
        topBarSettings.inAppSuggestionsEnabled,
        topBarSettings.hydrationReminderEnabled,
        topBarSettings.hydrationIntervalMinutes,
        topBarSettings.postureReminderEnabled,
        topBarSettings.postureIntervalMinutes
    ) {
        buildSmartTopBarSuggestions(
            weatherState = weatherState,
            isAnyFocusRunning = isAnyFocusRunning,
            elapsedFocusMinutes = elapsedFocusMinutes,
            settings = topBarSettings
        )
    }

    val activeEntries: List<TopBarSuggestionEntry> = remember(
        topBarSettings.textSource,
        topBarSettings.customTexts,
        topBarSettings.aiQuotes,
        smartSuggestions
    ) {
        when (topBarSettings.textSource) {
            TopBarTextSource.AI_SUGGESTIONS -> smartSuggestions
            TopBarTextSource.CUSTOM_TEXTS ->
                topBarSettings.customTexts
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { TopBarSuggestionEntry(it, TopBarSuggestionAction.OpenAiPrompt(it)) }
                    .ifEmpty { smartSuggestions }
            TopBarTextSource.AI_QUOTES ->
                topBarSettings.aiQuotes
                    .map { capitalizeTurkishSentence(it) }
                    .filter { it.isNotEmpty() }
                    .map { TopBarSuggestionEntry(it, TopBarSuggestionAction.OpenAiPrompt(it)) }
                    .ifEmpty { smartSuggestions }
        }
    }

    val activeTexts = remember(activeEntries) { activeEntries.map { it.text } }
    val intervalMs = topBarSettings.refreshInterval.durationMs.coerceAtLeast(3_000L)

    val fontFamily = when (topBarSettings.fontOption) {
        TopBarFontOption.DEFAULT -> null
        TopBarFontOption.SANS_SERIF -> FontFamily.SansSerif
        TopBarFontOption.SERIF -> FontFamily.Serif
        TopBarFontOption.MONOSPACE -> FontFamily.Monospace
    }

    val textColor = topBarSettings.customTextColorArgb?.let { Color(it.toInt()) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

    var currentIndex by remember(activeTexts, topBarSettings.textSource) { mutableIntStateOf(0) }
    var visibleText by remember { mutableStateOf("") }
    var showCursor by remember { mutableStateOf(true) }
    val flipRotation = remember { Animatable(0f) }

    LaunchedEffect(topBarSettings.animationType, activeTexts, intervalMs) {
        if (activeTexts.isEmpty()) return@LaunchedEffect
        currentIndex = currentIndex.coerceIn(0, activeTexts.lastIndex)
        when (topBarSettings.animationType) {
            TopBarAnimationType.NONE -> {
                showCursor = false
                while (true) {
                    visibleText = activeTexts[currentIndex.coerceIn(0, activeTexts.lastIndex)]
                    delay(intervalMs)
                    if (activeTexts.size > 1) {
                        currentIndex = (currentIndex + 1) % activeTexts.size
                    }
                }
            }
            TopBarAnimationType.TYPEWRITER -> {
                while (true) {
                    val fullText = activeTexts[currentIndex.coerceIn(0, activeTexts.lastIndex)]
                    showCursor = true
                    for (i in 1..fullText.length) {
                        visibleText = fullText.substring(0, i)
                        delay(42L)
                    }
                    // Hold text visible for the user-configured refresh interval
                    val holdMs = (intervalMs - (fullText.length * 64L)).coerceAtLeast(1_500L)
                    val blinkCycles = (holdMs / 450L).toInt().coerceAtLeast(2)
                    repeat(blinkCycles) {
                        showCursor = !showCursor
                        delay(450L)
                    }
                    showCursor = true
                    for (i in fullText.length - 1 downTo 0) {
                        visibleText = fullText.substring(0, i)
                        delay(20L)
                    }
                    delay(220L)
                    if (activeTexts.size > 1) {
                        currentIndex = (currentIndex + 1) % activeTexts.size
                    }
                }
            }
            TopBarAnimationType.FLIP_BOX -> {
                flipRotation.snapTo(0f)
                while (true) {
                    delay(intervalMs)
                    if (activeTexts.size > 1) {
                        flipRotation.animateTo(90f, animationSpec = tween(220))
                        currentIndex = (currentIndex + 1) % activeTexts.size
                        flipRotation.snapTo(-90f)
                        flipRotation.animateTo(0f, animationSpec = tween(220))
                    }
                }
            }
            TopBarAnimationType.FADE,
            TopBarAnimationType.SLIDE_VERTICAL,
            TopBarAnimationType.SCALE_FADE -> {
                while (true) {
                    delay(intervalMs)
                    if (activeTexts.size > 1) {
                        currentIndex = (currentIndex + 1) % activeTexts.size
                    }
                }
            }
        }
    }

    val safeIndex = currentIndex.coerceIn(0, activeEntries.lastIndex)
    val currentEntry = activeEntries[safeIndex]
    val currentText = currentEntry.text
    val cursor = if (showCursor) "|" else ""
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = fontFamily)
    fun formatForSource(raw: String): String =
        if (topBarSettings.textSource == TopBarTextSource.AI_SUGGESTIONS) "\"$raw\"" else raw

    val displayText = formatForSource(currentText)
    val horizontalPad = if (showBubble) 10.dp else 4.dp

    Surface(
        modifier = modifier
            .let { baseModifier ->
                if (topBarSettings.animationType == TopBarAnimationType.FLIP_BOX) {
                    baseModifier.graphicsLayer {
                        rotationX = flipRotation.value
                        cameraDistance = 12f * density
                    }
                } else {
                    baseModifier
                }
            }
            .clickable {
                if (topBarSettings.textSource == TopBarTextSource.AI_SUGGESTIONS) {
                    when (val act = currentEntry.action) {
                        is TopBarSuggestionAction.OpenAiPrompt -> onClick(act.prompt)
                        is TopBarSuggestionAction.StartZenSound -> {
                            onStartZenSound?.invoke(act.sound, act.feedbackMessage)
                                ?: onClick(currentText)
                        }
                        is TopBarSuggestionAction.OpenRoute -> {
                            onNavigateRoute?.invoke(act.route) ?: onClick(currentText)
                        }
                        is TopBarSuggestionAction.WellnessReminder -> {
                            onWellnessAck?.invoke(act.feedbackMessage)
                        }
                    }
                } else if (activeTexts.isNotEmpty()) {
                    currentIndex = (safeIndex + 1) % activeTexts.size
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = if (showBubble) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (showBubble) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null
    ) {
        when (topBarSettings.animationType) {
            TopBarAnimationType.NONE -> {
                Text(
                    text = displayText,
                    style = textStyle,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPad, vertical = 6.dp)
                )
            }
            TopBarAnimationType.TYPEWRITER -> {
                val typewriterText = if (topBarSettings.textSource == TopBarTextSource.AI_SUGGESTIONS) {
                    "\"$visibleText\"$cursor"
                } else {
                    "$visibleText$cursor"
                }
                Text(
                    text = typewriterText,
                    style = textStyle,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPad, vertical = 6.dp)
                )
            }
            TopBarAnimationType.FLIP_BOX -> {
                Text(
                    text = displayText,
                    style = textStyle,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPad, vertical = 6.dp)
                )
            }
            TopBarAnimationType.FADE -> {
                AnimatedContent(
                    targetState = safeIndex,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(550)) togetherWith fadeOut(animationSpec = tween(400))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds(),
                    label = "top_bar_fade"
                ) { targetIdx ->
                    val itemText = formatForSource(activeTexts[targetIdx.coerceIn(0, activeTexts.lastIndex)])
                    Text(
                        text = itemText,
                        style = textStyle,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPad, vertical = 6.dp)
                    )
                }
            }
            TopBarAnimationType.SLIDE_VERTICAL -> {
                AnimatedContent(
                    targetState = safeIndex,
                    transitionSpec = {
                        (slideInVertically(animationSpec = tween(480)) { fullHeight -> fullHeight } +
                            fadeIn(animationSpec = tween(360))) togetherWith
                            (slideOutVertically(animationSpec = tween(480)) { fullHeight -> -fullHeight } +
                                fadeOut(animationSpec = tween(320)))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds(),
                    label = "top_bar_slide_vertical"
                ) { targetIdx ->
                    val itemText = formatForSource(activeTexts[targetIdx.coerceIn(0, activeTexts.lastIndex)])
                    Text(
                        text = itemText,
                        style = textStyle,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPad, vertical = 6.dp)
                    )
                }
            }
            TopBarAnimationType.SCALE_FADE -> {
                AnimatedContent(
                    targetState = safeIndex,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.78f, animationSpec = tween(450)) +
                            fadeIn(animationSpec = tween(380))) togetherWith
                            (scaleOut(targetScale = 1.14f, animationSpec = tween(350)) +
                                fadeOut(animationSpec = tween(320)))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds(),
                    label = "top_bar_scale_fade"
                ) { targetIdx ->
                    val itemText = formatForSource(activeTexts[targetIdx.coerceIn(0, activeTexts.lastIndex)])
                    Text(
                        text = itemText,
                        style = textStyle,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPad, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TopBarDmShortcutButton(
    hasUnread: Boolean,
    showBubble: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (showBubble) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else Color.Transparent,
        border = if (showBubble) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(horizontal = if (showBubble) 8.dp else 4.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "DM Kutusu",
                tint = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .background(Color(0xFFEF4444), CircleShape)
                )
            }
        }
    }
}
