package com.example.sharkmentor.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.data.topbar.TopBarAnimationType
import com.example.sharkmentor.data.topbar.TopBarFontOption
import com.example.sharkmentor.data.topbar.TopBarPreferences
import com.example.sharkmentor.data.topbar.TopBarRefreshInterval
import com.example.sharkmentor.data.topbar.TopBarSettings
import com.example.sharkmentor.data.topbar.TopBarTextSource
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@Composable
fun TopBarControlHubContent(
    settings: TopBarSettings,
    onToggleHideBubbles: (Boolean) -> Unit,
    onToggleHideTopBarMenu: (Boolean) -> Unit,
    onToggleHideDuringFocus: (Boolean) -> Unit,
    onOpenWeatherSettings: () -> Unit,
    onOpenTextSettings: () -> Unit,
    onOpenCountdownSettings: () -> Unit,
    onToggleInAppSuggestions: ((Boolean) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionContainer(title = "Üst Bar Görünümü") {
            SwitchSettingRow(
                title = "Baloncukları Kapat",
                subtitle = "Üst bardaki kart çerçevelerini sadeleştirir",
                checked = settings.hideBubbles,
                onCheckedChange = onToggleHideBubbles
            )
            SectionDivider()
            SwitchSettingRow(
                title = "Üst Bar Menüsünü Gizle",
                subtitle = "Üst bar kontrol kısayolunu kapatır",
                checked = settings.hideTopBarMenu,
                onCheckedChange = onToggleHideTopBarMenu
            )
            SectionDivider()
            SwitchSettingRow(
                title = "Odak Sırasında Gizle",
                subtitle = "Odak oturumu başlarken üst barı otomatik kapatır",
                checked = settings.hideDuringFocus,
                onCheckedChange = onToggleHideDuringFocus
            )
            if (onToggleInAppSuggestions != null) {
                SectionDivider()
                SwitchSettingRow(
                    title = "Akıllı Öneriler",
                    subtitle = "Hava durumuna göre ambiyans ve hızlı kısayollar önerir",
                    checked = settings.inAppSuggestionsEnabled,
                    onCheckedChange = onToggleInAppSuggestions
                )
            }
        }

        SectionContainer(title = "İçerik & Modüller") {
            NavigationSettingRow(
                icon = Icons.Rounded.TextFields,
                title = "Üst Bar Yazıları & Sıklık",
                onClick = onOpenTextSettings
            )
            SectionDivider()
            NavigationSettingRow(
                icon = Icons.Rounded.WbSunny,
                title = "Hava Durumu",
                onClick = onOpenWeatherSettings
            )
            SectionDivider()
            NavigationSettingRow(
                icon = Icons.Rounded.Timer,
                title = "Sınav Geri Sayımı",
                onClick = onOpenCountdownSettings
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopBarTextSettingsContent(prefs: TopBarPreferences) {
    val settings by prefs.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showQuoteDialog by remember { mutableStateOf(false) }
    var topicInput by remember(settings.aiQuotesTopic) { mutableStateOf(settings.aiQuotesTopic) }
    var isLoadingQuotes by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionContainer(title = "İçerik Kaynağı") {
            TopBarTextSource.entries.forEachIndexed { index, source ->
                if (index > 0) SectionDivider()
                RadioOptionRow(
                    label = source.label,
                    selected = settings.textSource == source,
                    onClick = { prefs.setTextSource(source) }
                )
            }
        }

        // Wide Fixed-Interval Scale for Top Bar Text Refresh Frequency
        SectionContainer(title = "Yenileme Sıklığı (${settings.refreshInterval.label})") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Üst bardaki öneri ve motivasyon yazılarının sabit yenilenme aralığını seçin:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopBarRefreshInterval.entries.forEach { option ->
                        val isSelected = settings.refreshInterval == option
                        Surface(
                            onClick = { prefs.setRefreshInterval(option) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        if (settings.textSource == TopBarTextSource.CUSTOM_TEXTS) {
            SectionContainer(title = "Kendi Metinlerim") {
                val texts = settings.customTexts.ifEmpty { listOf("") }
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    texts.forEachIndexed { index, value ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { updated ->
                                    val mutable = texts.toMutableList()
                                    mutable[index] = updated
                                    prefs.setCustomTexts(mutable)
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Metin ${index + 1}") }
                            )
                            IconButton(
                                onClick = {
                                    if (texts.size > 1) {
                                        val mutable = texts.toMutableList()
                                        mutable.removeAt(index)
                                        prefs.setCustomTexts(mutable)
                                    } else {
                                        prefs.setCustomTexts(listOf(""))
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Metni Sil",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (texts.size < TopBarPreferences.MAX_CUSTOM_TEXTS) {
                        OutlinedButton(
                            onClick = {
                                prefs.setCustomTexts(texts + "")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Metin Ekle")
                        }
                    }
                }
            }
        }

        if (settings.textSource == TopBarTextSource.AI_QUOTES) {
            SectionContainer(title = "AI Motivasyon") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            quoteError = null
                            showQuoteDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI ile Söz Oluştur")
                    }

                    if (isLoadingQuotes) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Motivasyon sözleri hazırlanıyor...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!quoteError.isNullOrBlank()) {
                        Text(
                            text = quoteError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (settings.aiQuotes.isNotEmpty()) {
                        if (settings.aiQuotesTopic.isNotBlank()) {
                            Text(
                                text = "Konu: ${settings.aiQuotesTopic}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            settings.aiQuotes.forEachIndexed { idx, quote ->
                                Text(
                                    text = "${idx + 1}. $quote",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionContainer(title = "Animasyon") {
            TopBarAnimationType.entries.forEachIndexed { index, anim ->
                if (index > 0) SectionDivider()
                RadioOptionRow(
                    label = anim.label,
                    selected = settings.animationType == anim,
                    onClick = { prefs.setAnimationType(anim) }
                )
            }
        }

        SectionContainer(title = "Yazı Tipi") {
            TopBarFontOption.entries.forEachIndexed { index, font ->
                if (index > 0) SectionDivider()
                val previewFamily = when (font) {
                    TopBarFontOption.DEFAULT -> FontFamily.Default
                    TopBarFontOption.SANS_SERIF -> FontFamily.SansSerif
                    TopBarFontOption.SERIF -> FontFamily.Serif
                    TopBarFontOption.MONOSPACE -> FontFamily.Monospace
                }
                RadioOptionRow(
                    label = font.label,
                    selected = settings.fontOption == font,
                    fontFamily = previewFamily,
                    onClick = { prefs.setFontOption(font) }
                )
            }
        }

        SectionContainer(title = "Yazı Rengi") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val selectedColor = settings.customTextColorArgb?.let { Color(it) }
                    ?: MaterialTheme.colorScheme.onSurface

                HsvColorWheel(
                    selectedColor = selectedColor,
                    onColorSelected = { argb ->
                        prefs.setCustomTextColorArgb(argb)
                    },
                    modifier = Modifier.size(210.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(selectedColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Text(
                            text = if (settings.customTextColorArgb == null) {
                                "Tema Varsayılanı"
                            } else {
                                "Özel Renk Seçildi"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(
                        onClick = { prefs.setCustomTextColorArgb(null) },
                        enabled = settings.customTextColorArgb != null
                    ) {
                        Text("Tema Varsayılanı")
                    }
                }
            }
        }
    }

    if (showQuoteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isLoadingQuotes) showQuoteDialog = false
            },
            title = { Text("AI ile Söz Oluştur") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sevdiğiniz bir film, dizi veya kişinin adını yazın.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        singleLine = true,
                        enabled = !isLoadingQuotes,
                        label = { Text("Film, Dizi veya Kişi") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isLoadingQuotes) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = "10 motivasyon sözü getiriliyor...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = topicInput.isNotBlank() && !isLoadingQuotes,
                    onClick = {
                        val query = topicInput.trim()
                        isLoadingQuotes = true
                        quoteError = null
                        scope.launch {
                            val result = prefs.fetchMotivationalQuotes(query)
                            isLoadingQuotes = false
                            result.onSuccess {
                                showQuoteDialog = false
                            }.onFailure { err ->
                                quoteError = err.localizedMessage ?: "Sözler alınamadı."
                                showQuoteDialog = false
                            }
                        }
                    }
                ) {
                    Text("Oluştur")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isLoadingQuotes,
                    onClick = { showQuoteDialog = false }
                ) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
private fun HsvColorWheel(
    selectedColor: Color,
    onColorSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialHsv = remember(selectedColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(selectedColor.toArgbInt(), it) }
    }
    var hue by remember(selectedColor) { mutableStateOf(initialHsv[0]) }
    var saturation by remember(selectedColor) { mutableStateOf(initialHsv[1]) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val hueColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    }

    fun updateFromOffset(offset: Offset) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return
        val cx = canvasSize.width / 2f
        val cy = canvasSize.height / 2f
        val radius = min(cx, cy)
        if (radius <= 0f) return

        val dx = offset.x - cx
        val dy = offset.y - cy
        val distance = hypot(dx, dy).coerceAtMost(radius)
        val angleRad = atan2(dy, dx)
        val angleDeg = ((angleRad * 180.0 / PI) + 360.0) % 360.0

        val newHue = angleDeg.toFloat()
        val newSat = (distance / radius).coerceIn(0f, 1f)
        hue = newHue
        saturation = newSat

        val argbInt = AndroidColor.HSVToColor(floatArrayOf(newHue, newSat, 1f))
        onColorSelected(argbInt.toLong() and 0xFFFFFFFFL)
    }

    val outlineColor = MaterialTheme.colorScheme.outline
    val indicatorFill = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f)))

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset -> updateFromOffset(offset) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateFromOffset(change.position)
                }
            }
    ) {
        val radius = min(size.width, size.height) / 2f
        val centerOffset = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.sweepGradient(colors = hueColors, center = centerOffset),
            radius = radius,
            center = centerOffset
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = centerOffset,
                radius = radius
            ),
            radius = radius,
            center = centerOffset
        )
        drawCircle(
            color = outlineColor.copy(alpha = 0.35f),
            radius = radius,
            center = centerOffset,
            style = Stroke(width = 1.5.dp.toPx())
        )

        val rad = (hue * PI / 180.0)
        val markerDist = saturation * radius
        val markerX = centerOffset.x + (cos(rad) * markerDist).toFloat()
        val markerY = centerOffset.y + (sin(rad) * markerDist).toFloat()
        val markerCenter = Offset(markerX, markerY)

        drawCircle(
            color = indicatorFill,
            radius = 9.dp.toPx(),
            center = markerCenter
        )
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = markerCenter,
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

private fun Color.toArgbInt(): Int {
    val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
    val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

@Composable
private fun SectionContainer(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun NavigationSettingRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RadioOptionRow(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily = FontFamily.Default,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = fontFamily,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusWellnessSettingsContent(
    prefs: TopBarPreferences,
    onOpenReflectionTime: (() -> Unit)? = null
) {
    val settings by prefs.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (onOpenReflectionTime != null) {
            SectionContainer(title = "Günlük Değerlendirme") {
                NavigationSettingRow(
                    icon = Icons.Rounded.Timer,
                    title = "Gün Değerlendirmesi Saati",
                    onClick = onOpenReflectionTime
                )
            }
        }

        SectionContainer(title = "Odak Sağlık Hatırlatmaları") {
            SwitchSettingRow(
                title = "Su İçme Hatırlatması",
                subtitle = "Uzun oturumlarda belirli aralıklarla su hatırlatması verir",
                checked = settings.hydrationReminderEnabled,
                onCheckedChange = prefs::setHydrationReminderEnabled
            )
            if (settings.hydrationReminderEnabled) {
                SectionDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Aralık: Her ${settings.hydrationIntervalMinutes} dk",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 45, 60, 90).forEach { mins ->
                            val selected = settings.hydrationIntervalMinutes == mins
                            Surface(
                                onClick = { prefs.setHydrationIntervalMinutes(mins) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = "$mins dk",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            SectionDivider()

            SwitchSettingRow(
                title = "Duruş & Göz Dinlendirme",
                subtitle = "Uzun masa başı çalışmasında kısa duruş uyarısı verir",
                checked = settings.postureReminderEnabled,
                onCheckedChange = prefs::setPostureReminderEnabled
            )
            if (settings.postureReminderEnabled) {
                SectionDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Aralık: Her ${settings.postureIntervalMinutes} dk",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 45, 50, 60, 90).forEach { mins ->
                            val selected = settings.postureIntervalMinutes == mins
                            Surface(
                                onClick = { prefs.setPostureIntervalMinutes(mins) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = "$mins dk",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

