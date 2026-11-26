package com.example.sharkmentor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.ui.screens.notes.drawing.NoteDrawingPreferences
import com.example.sharkmentor.ui.screens.notes.drawing.StylusCalibrationDialog
import com.example.sharkmentor.ui.screens.notes.drawing.StylusDoubleTapAction
import com.example.sharkmentor.ui.screens.notes.drawing.StylusHoldAction
import com.example.sharkmentor.ui.screens.notes.drawing.StylusSingleTapAction
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        NoteDrawingPreferences.ensureLoaded(context)
    }

    val userAiPrefs = remember(context) { UserAiPreferences.initialize(context) }
    val notesAiEnabled by userAiPrefs.notesAiEnabled.collectAsStateWithLifecycle()
    val drawingAiEnabled by userAiPrefs.drawingAiEnabled.collectAsStateWithLifecycle()
    val pressureEnabled by NoteDrawingPreferences.pressureSensitivityEnabled.collectAsStateWithLifecycle()
    val pressureStrength by NoteDrawingPreferences.pressureSensitivityStrength.collectAsStateWithLifecycle()
    val scribbleEnabled by NoteDrawingPreferences.scribbleToEraseEnabled.collectAsStateWithLifecycle()
    val autoShapeEnabled by NoteDrawingPreferences.autoShapeRecognitionEnabled.collectAsStateWithLifecycle()
    val stylusOnlyEnabled by NoteDrawingPreferences.stylusOnlyModeEnabled.collectAsStateWithLifecycle()
    val showFloatingTimer by NoteDrawingPreferences.showFloatingFocusTimer.collectAsStateWithLifecycle()
    val stylusButtonsEnabled by NoteDrawingPreferences.stylusButtonsEnabled.collectAsStateWithLifecycle()
    val stylusHoldAction by NoteDrawingPreferences.stylusHoldAction.collectAsStateWithLifecycle()
    val stylusSingleTapAction by NoteDrawingPreferences.stylusSingleTapAction.collectAsStateWithLifecycle()
    val stylusDoubleTapAction by NoteDrawingPreferences.stylusDoubleTapAction.collectAsStateWithLifecycle()
    val stylusCalibrated by NoteDrawingPreferences.stylusCalibrated.collectAsStateWithLifecycle()
    var showStylusCalibrationDialog by remember { mutableStateOf(false) }

    if (showStylusCalibrationDialog) {
        com.example.sharkmentor.ui.screens.notes.drawing.StylusCalibrationDialog(
            onDismiss = { showStylusCalibrationDialog = false },
            onCalibrated = {
                android.widget.Toast.makeText(context, "Kalem senkronize edildi", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary
    val onSurfaceColor = colorScheme.onSurface

    val strengthLabel = when {
        pressureStrength < 0.85f -> "Düşük"
        pressureStrength < 1.12f -> "Normal"
        pressureStrength < 1.45f -> "Yüksek"
        else -> "Çok Yüksek"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 50.dp,
                title = { Text("SharkStudio Ayarları") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SharkStudio • SharkAI",
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    NoteFeatureSwitchHeader(
                        icon = Icons.Default.AutoAwesome,
                        title = "SharkStudio & PDF • SharkAI",
                        subtitle = "Not ve PDF üst barındaki AI asistanını açar veya gizler.",
                        checked = notesAiEnabled,
                        onCheckedChange = { userAiPrefs.setNotesAiEnabled(context, it) }
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    NoteFeatureSwitchHeader(
                        icon = Icons.Default.AutoFixHigh,
                        title = "Kement Çözümü • SharkAI",
                        subtitle = "Kementle seçilen soru veya görsellerde AI'ya Sor kısayolunu açar veya gizler.",
                        checked = drawingAiEnabled,
                        onCheckedChange = { userAiPrefs.setDrawingAiEnabled(context, it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Kalem ve Çizim Davranışı",
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor
            )

            // 1. Basınç Hassasiyeti
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NoteFeatureSwitchHeader(
                        icon = Icons.Default.Brush,
                        title = "Basınç Hassasiyeti",
                        subtitle = "Kalem baskısına göre çizgi kalınlığını dinamik ayarlar.",
                        checked = pressureEnabled,
                        onCheckedChange = { enabled ->
                            NoteDrawingPreferences.setPressureSensitivityEnabled(context, enabled)
                        }
                    )

                    AnimatedVisibility(visible = pressureEnabled) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Hassasiyet Seviyesi",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = onSurfaceColor.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = "$strengthLabel (%${(pressureStrength * 100).roundToInt()})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }

                            Slider(
                                value = pressureStrength,
                                onValueChange = { value ->
                                    NoteDrawingPreferences.setPressureSensitivityStrength(context, value)
                                },
                                valueRange = 0.6f..1.8f,
                                colors = SliderDefaults.colors(
                                    thumbColor = primaryColor,
                                    activeTrackColor = primaryColor
                                )
                            )

                            // Live stroke taper preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colorScheme.surface)
                                    .border(
                                        1.dp,
                                        colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val segments = 32
                                    val startX = 6f
                                    val endX = (size.width - 6f).coerceAtLeast(startX + 12f)
                                    val midY = size.height * 0.5f
                                    val amp = (size.height * 0.28f).coerceAtLeast(3f)
                                    val baseW = 5.5f

                                    var prevX = startX
                                    var prevY = midY
                                    for (i in 1..segments) {
                                        val t = i.toFloat() / segments.toFloat()
                                        val x = startX + (endX - startX) * t
                                        val y = midY - sin(t * PI.toFloat() * 2f) * amp
                                        val bell = sin(t * PI.toFloat()).coerceIn(0.05f, 1.0f)
                                        val modulated = (0.85f + (bell - 0.5f) * 1.15f * pressureStrength)
                                            .coerceIn(0.08f, 1.95f)
                                        val segW = (baseW * (0.16f + 1.35f * modulated))
                                            .coerceIn(1.2f, size.height * 0.85f)
                                        drawLine(
                                            color = primaryColor,
                                            start = Offset(prevX, prevY),
                                            end = Offset(x, y),
                                            strokeWidth = segW,
                                            cap = StrokeCap.Round
                                        )
                                        prevX = x
                                        prevY = y
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Yalnızca Kalem Modu
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    NoteFeatureSwitchHeader(
                        icon = Icons.Default.Edit,
                        title = "Yalnızca Kalem",
                        subtitle = "Çizim modunda parmak dokunuşlarını yoksayar, yalnızca stylus kalemi algılar.",
                        checked = stylusOnlyEnabled,
                        onCheckedChange = { enabled ->
                            NoteDrawingPreferences.setStylusOnlyModeEnabled(context, enabled)
                        }
                    )
                }
            }

            // 3. Karalayarak Silme
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    NoteFeatureSwitchHeader(
                        icon = Icons.Default.Gesture,
                        title = "Karalayarak Silme",
                        subtitle = "Hızlı zikzak karalama ile temas eden çizgileri siler.",
                        checked = scribbleEnabled,
                        onCheckedChange = { enabled ->
                            NoteDrawingPreferences.setScribbleToEraseEnabled(context, enabled)
                        }
                    )
                }
            }

            // 3.5 Kalem Yan Tuş Kısayolları & Hello Test/Senkronizasyon
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NoteFeatureSwitchHeader(
                        icon = Icons.Default.TouchApp,
                        title = "Kalem Yan Tuş Kısayolları",
                        subtitle = "Tablet veya telefon kalemindeki yan tuşa basılı tutma ve çift tıklama kısayollarını yönetir.",
                        checked = stylusButtonsEnabled,
                        onCheckedChange = { enabled ->
                            NoteDrawingPreferences.setStylusButtonsEnabled(context, enabled)
                        }
                    )

                    AnimatedVisibility(visible = stylusButtonsEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Text(
                                text = "Basılı Tutarken",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StylusHoldAction.entries.forEach { action ->
                                    val selected = stylusHoldAction == action
                                    Surface(
                                        onClick = { NoteDrawingPreferences.setStylusHoldAction(context, action) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) primaryColor else colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) primaryColor else colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = action.label,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) colorScheme.onPrimary else colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Tek Tıklama",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                StylusSingleTapAction.entries.forEach { action ->
                                    val selected = stylusSingleTapAction == action
                                    Surface(
                                        onClick = { NoteDrawingPreferences.setStylusSingleTapAction(context, action) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) primaryColor else colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) primaryColor else colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = action.label,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Çift Tıklama",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StylusDoubleTapAction.entries.forEach { action ->
                                    val selected = stylusDoubleTapAction == action
                                    Surface(
                                        onClick = { NoteDrawingPreferences.setStylusDoubleTapAction(context, action) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) primaryColor else colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) primaryColor else colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = action.label,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Surface(
                        onClick = { showStylusCalibrationDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = primaryColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Draw,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Kalem Test & Senkronizasyon (Hello)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (stylusCalibrated) "Kalem basınç ve tuş profili senkronize" else "'Hello' yazarak kalemini test edip senkronize et",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (stylusCalibrated) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Senkronize",
                                    tint = primaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Otomatik Şekil Algılama
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    NoteFeatureSwitchHeader(
                        icon = Icons.Default.AutoFixHigh,
                        title = "Otomatik Şekil Algılama",
                        subtitle = "Çizimin sonunda kalemi kısa süre sabit tutunca düz çizgi, ok, üçgen, dikdörtgen veya daireye dönüştürür; bırakmadan sürükleyerek boyutlandırabilirsin.",
                        checked = autoShapeEnabled,
                        onCheckedChange = { enabled ->
                            NoteDrawingPreferences.setAutoShapeRecognitionEnabled(context, enabled)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Çalışma Ekranı",
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor
            )

            // 5. Not & PDF Canlı Odak Sayacı
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    NoteFeatureSwitchHeader(
                        icon = Icons.Default.Timer,
                        title = "Odak Sayacı Göstergesi",
                        subtitle = "Notlar ve PDF üzerinde çalışan odak sayacını taşınabilir pencere olarak gösterir.",
                        checked = showFloatingTimer,
                        onCheckedChange = { enabled ->
                            NoteDrawingPreferences.setShowFloatingFocusTimer(context, enabled)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Tüm ayarlar SharkStudio ve PDF ekranlarında anlık senkronize çalışır.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun NoteFeatureSwitchHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colorScheme.primary
            )
        )
    }
}
