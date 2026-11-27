package com.example.sharkmentor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.ai.SharkQuotaSnapshot
import com.example.sharkmentor.core.ai.SharkTokenQuotaManager

/**
 * Antigravity-inspired minimal telemetry card showing live 5-hour (80K), weekly (450K),
 * and hourly visual/simulation (2/hr) usage quotas for the built-in fixed API key.
 */
@Composable
fun SharkQuotaUsageCard(
    hasCustomApiKey: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        SharkTokenQuotaManager.ensureLoaded(context)
    }
    val snap by SharkTokenQuotaManager.snapshot.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.36f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isUnlimitedActive = hasCustomApiKey || snap.isUnlimitedUnlocked
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isUnlimitedActive) Icons.Default.VerifiedUser else Icons.Default.Bolt,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (hasCustomApiKey) "Özel API Anahtarı Aktif" else "Sabit Model • Kullanım Sınırları",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colorScheme.primary.copy(alpha = 0.13f)
                ) {
                    Text(
                        text = if (isUnlimitedActive) "LİMİTSİZ" else snap.tier.id.uppercase(),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (hasCustomApiKey) {
                Text(
                    text = "Kendi API anahtarını kullandığın için 5 saatlik, haftalık veya saatlik sabit model sınırları uygulanmıyor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            } else if (snap.isUnlimitedUnlocked) {
                Text(
                    text = "Token limiti kaldırıldı. 5 saatlik, haftalık ve saatlik kullanım sınırları uygulanmıyor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            } else {
                QuotaProgressRow(
                    title = "5 Saatlik Kota",
                    valueLabel = "${SharkTokenQuotaManager.formatCompactTokens(snap.used5hTokens)} / ${SharkTokenQuotaManager.formatCompactTokens(snap.limit5hTokens)}",
                    resetLabel = if (snap.used5hTokens > 0L) "Yenileme: ${SharkTokenQuotaManager.formatRemainingDuration(snap.reset5hInMs)}" else "Dolu",
                    progress = snap.fiveHourProgress
                )
                QuotaProgressRow(
                    title = "Haftalık Kota",
                    valueLabel = "${SharkTokenQuotaManager.formatCompactTokens(snap.usedWeeklyTokens)} / ${SharkTokenQuotaManager.formatCompactTokens(snap.limitWeeklyTokens)}",
                    resetLabel = if (snap.usedWeeklyTokens > 0L) "Yenileme: ${SharkTokenQuotaManager.formatRemainingDuration(snap.resetWeeklyInMs)}" else "Dolu",
                    progress = snap.weeklyProgress
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HourlySlotPill(
                        label = "Görsel Soru (Saatlik)",
                        used = snap.usedHourlyVisual,
                        limit = snap.limitHourlyVisual,
                        resetMs = snap.resetVisualInMs,
                        modifier = Modifier.weight(1f)
                    )
                    HourlySlotPill(
                        label = "Simülasyon (Saatlik)",
                        used = snap.usedHourlySimulation,
                        limit = snap.limitHourlySimulation,
                        resetMs = snap.resetSimulationInMs,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun SharkQuotaLimitsDialog(
    hasCustomApiKey: Boolean,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surface,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kullanım Sınırları",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                SharkQuotaUsageCard(hasCustomApiKey = hasCustomApiKey)
            }
        }
    }
}

@Composable
private fun QuotaProgressRow(
    title: String,
    valueLabel: String,
    resetLabel: String,
    progress: Float
) {
    val colorScheme = MaterialTheme.colorScheme
    val barColor = when {
        progress >= 0.9f -> Color(0xFFEF4444)
        progress >= 0.7f -> Color(0xFFF59E0B)
        else -> colorScheme.primary
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Text(
                text = valueLabel,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(barColor.copy(alpha = 0.78f), barColor)
                        )
                    )
            )
        }
        Text(
            text = resetLabel,
            fontSize = 10.sp,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HourlySlotPill(
    label: String,
    used: Int,
    limit: Int,
    resetMs: Long,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val remaining = (limit - used).coerceAtLeast(0)
    val accent = if (remaining == 0) Color(0xFFEF4444) else colorScheme.primary
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = "$remaining / $limit Kalan",
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            if (used > 0 && resetMs > 0L) {
                Text(
                    text = SharkTokenQuotaManager.formatRemainingDuration(resetMs),
                    fontSize = 9.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
