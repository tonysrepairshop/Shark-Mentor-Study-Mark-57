package com.example.sharkmentor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sharkmentor.core.league.SharkCardSnapshot
import com.example.sharkmentor.core.util.SharkAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SharkStatsCardView(
    card: SharkCardSnapshot,
    avatarBase64: String = "",
    avatarPresetId: String = "",
    showOwnerControls: Boolean = false,
    isRefreshing: Boolean = false,
    onRefreshClick: (() -> Unit)? = null,
    onTogglePublic: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val hours = card.weeklyFocusMinutes / 60
    val mins = card.weeklyFocusMinutes % 60
    val focusFormatted = if (hours > 0) "${hours}s ${mins}dk" else "${mins}dk"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top bar: SHARK KARTI label + optional Refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SHARK KARTI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = card.tierLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                if (showOwnerControls && onRefreshClick != null) {
                    Surface(
                        onClick = { if (!isRefreshing) onRefreshClick() },
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Yenile",
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isRefreshing) "Yenileniyor" else "Yenile",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Identity Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SharkAvatar(
                    avatarBase64 = avatarBase64,
                    avatarPresetId = avatarPresetId,
                    displayName = card.displayName.ifBlank { "Öğrenci" },
                    size = 48.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.displayName.ifBlank { "Öğrenci" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metaParts = listOfNotNull(
                        card.archetypeTitle.takeIf { it.isNotBlank() },
                        card.targetExam.takeIf { it.isNotBlank() },
                        card.grade.takeIf { it.isNotBlank() }
                    )
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2x2 Minimalist Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SharkCardMetricTile(
                    label = "Haftalık Odak",
                    value = focusFormatted,
                    modifier = Modifier.weight(1f)
                )
                SharkCardMetricTile(
                    label = "Aktif Seri",
                    value = "${card.streakDays} gün",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SharkCardMetricTile(
                    label = "Hakim Konu",
                    value = card.masteredTopicsCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                SharkCardMetricTile(
                    label = "Mentor Puanı",
                    value = "${card.mentorPoints} P",
                    modifier = Modifier.weight(1f)
                )
            }

            if (card.strongestSubject.isNotBlank() || card.resolvedErrorsCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (card.strongestSubject.isNotBlank()) {
                        Text(
                            text = "Öne Çıkan Ders: ${card.strongestSubject}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (card.resolvedErrorsCount > 0) {
                        Text(
                            text = "${card.resolvedErrorsCount} hata tekrarlandı",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showOwnerControls && onTogglePublic != null) {
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ligde Herkese Açık",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                        val updatedStr = if (card.updatedAtMillis > 0L) {
                            SimpleDateFormat("dd MMM HH:mm", Locale("tr", "TR"))
                                .format(Date(card.updatedAtMillis))
                        } else ""
                        Text(
                            text = if (card.isPublic) {
                                if (updatedStr.isNotBlank()) "Görünür • Son güncelleme $updatedStr" else "Lig üyeleri kartını görebilir"
                            } else {
                                "Gizli • Yalnızca sen görebilirsin"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = card.isPublic,
                        onCheckedChange = onTogglePublic
                    )
                }
            }
        }
    }
}

@Composable
private fun SharkCardMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.32f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SharkCardPreviewDialog(
    card: SharkCardSnapshot,
    avatarBase64: String = "",
    avatarPresetId: String = "",
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.background,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                SharkStatsCardView(
                    card = card,
                    avatarBase64 = avatarBase64,
                    avatarPresetId = avatarPresetId,
                    showOwnerControls = false
                )
            }
        }
    }
}
