package com.example.sharkmentor.ui.screens.notes.drawing

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class CalibStroke(
    val points: List<Offset>,
    val isStylus: Boolean
)

/**
 * Wraps the Dialog's Android [Window.Callback] so raw stylus hover/touch [MotionEvent]s
 * (`BUTTON_STYLUS_PRIMARY`, `BUTTON_STYLUS_SECONDARY`, `ACTION_BUTTON_PRESS`) AND raw
 * Bluetooth/Android 14+ stylus [KeyEvent]s (`KEYCODE_STYLUS_BUTTON_*`, Honor Magic-Pencil
 * double-tap, Samsung S-Pen Pro Air Actions) are captured inside the Dialog window too.
 */
private class StylusInterceptWindowCallback(
    private val delegate: Window.Callback
) : Window.Callback by delegate {

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        StylusHardwareInputBridge.onRawMotionEvent(event)
        return delegate.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        StylusHardwareInputBridge.onRawMotionEvent(event)
        return delegate.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (StylusHardwareInputBridge.onRawKeyEvent(event)) {
            return true
        }
        return delegate.dispatchKeyEvent(event)
    }
}

/**
 * Compact interactive "SHARK" Stylus Test & Synchronization window inside SharkStudio Settings.
 * Lets the user write "SHARK" with their stylus to calibrate natural pressure and verify
 * live Samsung S-Pen / Honor Magic-Pencil side-button hold, single-tap & double-tap hardware signals.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StylusCalibrationDialog(
    onDismiss: () -> Unit,
    onCalibrated: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    val completedStrokes = remember { mutableStateListOf<CalibStroke>() }
    val activePoints = remember { mutableStateListOf<Offset>() }
    var activeIsStylus by remember { mutableStateOf(false) }

    var stylusDetected by remember { mutableStateOf(false) }
    var livePressure by remember { mutableFloatStateOf(0f) }
    var pressureSum by remember { mutableFloatStateOf(0f) }
    var pressureSamples by remember { mutableIntStateOf(0) }
    var sideButtonStatus by remember { mutableStateOf("Bekleniyor") }

    val hwStylusDetected by StylusHardwareInputBridge.isStylusDetected.collectAsStateWithLifecycle()
    val hwButtonHeld by StylusHardwareInputBridge.isStylusButtonHeld.collectAsStateWithLifecycle()
    val hwSignalLabel by StylusHardwareInputBridge.lastSignalLabel.collectAsStateWithLifecycle()

    LaunchedEffect(hwStylusDetected) {
        if (hwStylusDetected) stylusDetected = true
    }

    LaunchedEffect(hwButtonHeld, hwSignalLabel) {
        when {
            hwButtonHeld -> sideButtonStatus = hwSignalLabel.ifBlank { "Yan Tuş Basılı" }
            hwSignalLabel.isNotBlank() -> sideButtonStatus = hwSignalLabel
        }
    }

    LaunchedEffect(Unit) {
        StylusHardwareInputBridge.clickEvents.collect { click ->
            sideButtonStatus = when (click.type) {
                StylusHardwareClickType.DOUBLE_TAP -> "Çift Tıklama (${click.signalDescription})"
                StylusHardwareClickType.SINGLE_TAP -> "Tek Tıklama (${click.signalDescription})"
            }
        }
    }

    DisposableEffect(Unit) {
        StylusHardwareInputBridge.clearDiagnosticLabel()
        StylusHardwareInputBridge.registerActiveListener()
        onDispose {
            StylusHardwareInputBridge.unregisterActiveListener()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Attach native Window.Callback on the Dialog's own Window so hover & KeyEvent stylus buttons work!
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            val originalCallback = window?.callback
            if (window != null && originalCallback != null && originalCallback !is StylusInterceptWindowCallback) {
                window.callback = StylusInterceptWindowCallback(originalCallback)
            }
            onDispose {
                if (window != null && originalCallback != null && window.callback is StylusInterceptWindowCallback) {
                    window.callback = originalCallback
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colorScheme.surface,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "SharkStudio • Kalem Test & Senkron",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Aşağıdaki kutuya kaleminle \"SHARK\" yaz ve kalem yan tuşuna basıp / çift tıklayarak Samsung S-Pen ve Honor Magic-Pencil sinyalini canlı test et.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )

                // Interactive "SHARK" Calibration Pad
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull() ?: continue
                                    val isStylus = change.type == PointerType.Stylus || change.type == PointerType.Eraser
                                    if (isStylus) stylusDetected = true
                                    if (change.type == PointerType.Eraser && sideButtonStatus == "Bekleniyor") {
                                        sideButtonStatus = "Silgi Ucu / Yan Tuş Algılandı"
                                    }

                                    if (change.pressed) {
                                        val p = change.pressure.takeIf { it > 0f } ?: 0.55f
                                        livePressure = p.coerceIn(0f, 1.5f)
                                        if (isStylus) {
                                            pressureSum += p
                                            pressureSamples += 1
                                        }
                                        activeIsStylus = isStylus
                                        activePoints.add(change.position)
                                        change.consume()
                                    } else if (activePoints.isNotEmpty()) {
                                        completedStrokes.add(CalibStroke(activePoints.toList(), activeIsStylus))
                                        activePoints.clear()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (completedStrokes.isEmpty() && activePoints.isEmpty()) {
                        Text(
                            text = "SHARK",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Light,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 6.sp,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                        )
                    }

                    val stylusStrokeColor = colorScheme.primary
                    val touchStrokeColor = colorScheme.onSurface.copy(alpha = 0.72f)
                    val reusablePath = remember { Path() }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeStyle = Stroke(
                            width = 3.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        fun drawPoints(pts: List<Offset>, color: Color) {
                            if (pts.size < 2) {
                                pts.firstOrNull()?.let { drawCircle(color, radius = 2.6.dp.toPx(), center = it) }
                                return
                            }
                            reusablePath.reset()
                            reusablePath.moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) {
                                reusablePath.lineTo(pts[i].x, pts[i].y)
                            }
                            drawPath(
                                path = reusablePath,
                                color = color,
                                style = strokeStyle
                            )
                        }

                        completedStrokes.forEach { st ->
                            drawPoints(st.points, if (st.isStylus) stylusStrokeColor else touchStrokeColor)
                        }
                        if (activePoints.isNotEmpty()) {
                            drawPoints(activePoints, if (activeIsStylus) stylusStrokeColor else touchStrokeColor)
                        }
                    }

                    if (completedStrokes.isNotEmpty() || activePoints.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                completedStrokes.clear()
                                activePoints.clear()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Temizle",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Live Telemetry Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryChip(
                        label = "Giriş",
                        value = if (stylusDetected) "Kalem (Stylus)" else if (completedStrokes.isNotEmpty()) "Dokunmatik" else "Bekleniyor",
                        highlighted = stylusDetected,
                        modifier = Modifier.weight(0.95f)
                    )
                    TelemetryChip(
                        label = "Basınç",
                        value = if (livePressure > 0f) "%.2f".format(livePressure) else "—",
                        highlighted = pressureSamples > 0,
                        modifier = Modifier.weight(0.65f)
                    )
                    TelemetryChip(
                        label = "Yan Tuş",
                        value = sideButtonStatus,
                        highlighted = sideButtonStatus != "Bekleniyor",
                        modifier = Modifier.weight(1.4f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Vazgeç")
                    }
                    Button(
                        onClick = {
                            val avgPressure = if (pressureSamples > 0) pressureSum / pressureSamples else 0.55f
                            NoteDrawingPreferences.saveStylusCalibration(context, avgPressure)
                            onCalibrated()
                            onDismiss()
                        },
                        enabled = completedStrokes.isNotEmpty() || activePoints.isNotEmpty() || sideButtonStatus != "Bekleniyor",
                        modifier = Modifier.weight(1.35f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Senkronize Et")
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    label: String,
    value: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted) colorScheme.primary.copy(alpha = 0.13f) else colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (highlighted) colorScheme.primary.copy(alpha = 0.45f) else colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlighted) colorScheme.primary else colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
