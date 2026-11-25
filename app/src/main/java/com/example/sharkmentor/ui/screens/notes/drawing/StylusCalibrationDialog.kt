package com.example.sharkmentor.ui.screens.notes.drawing

import android.os.SystemClock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private data class CalibStroke(
    val points: List<Offset>,
    val isStylus: Boolean
)

/**
 * Compact interactive "Hello" Stylus Test & Synchronization window inside SharkStudio Settings.
 * Lets the user write "Hello" with their stylus to calibrate natural pressure and verify
 * live stylus side-button hold & double-tap events on their specific tablet/phone.
 */
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
    var lastBtnPressMs by remember { mutableLongStateOf(0L) }
    var prevBtnPressed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                    text = "Aşağıdaki kutuya kaleminle \"Hello\" yaz ve varsa kalem yan tuşuna basıp veya çift tıklayarak sinyali test et.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )

                // Interactive "Hello" Calibration Pad
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
                                    val btnPressed = event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed
                                    if (btnPressed && !prevBtnPressed) {
                                        val now = SystemClock.elapsedRealtime()
                                        if (now - lastBtnPressMs in 40L..380L) {
                                            sideButtonStatus = "Çift Tıklama Algılandı"
                                            lastBtnPressMs = 0L
                                        } else {
                                            sideButtonStatus = "Yan Tuş Basılı"
                                            lastBtnPressMs = now
                                        }
                                    } else if (!btnPressed && prevBtnPressed) {
                                        if (sideButtonStatus == "Yan Tuş Basılı") {
                                            sideButtonStatus = "Yan Tuş Algılandı"
                                        }
                                    }
                                    prevBtnPressed = btnPressed

                                    val change = event.changes.firstOrNull() ?: continue
                                    val isStylus = change.type == PointerType.Stylus || change.type == PointerType.Eraser
                                    if (isStylus) stylusDetected = true

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
                            text = "Hello",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Light,
                            fontStyle = FontStyle.Italic,
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
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryChip(
                        label = "Basınç",
                        value = if (livePressure > 0f) "%.2f".format(livePressure) else "—",
                        highlighted = pressureSamples > 0,
                        modifier = Modifier.weight(0.75f)
                    )
                    TelemetryChip(
                        label = "Yan Tuş",
                        value = sideButtonStatus,
                        highlighted = sideButtonStatus != "Bekleniyor",
                        modifier = Modifier.weight(1.15f)
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
                maxLines = 1
            )
        }
    }
}
