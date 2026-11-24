package com.example.sharkmentor.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sharkmentor.data.repository.StreakGoalTier
import com.example.sharkmentor.data.repository.StreakState

@Composable
fun StreakCelebrationDialog(
    streakCount: Int,
    wasFrozenToday: Boolean,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "water_drop_bounce")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 16.dp,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(50.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WaterDrop,
                        contentDescription = "Su Serisi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(64.dp)
                            .scale(scale)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (wasFrozenToday) "Su Serisi Korundu 💧" else "Su Serisi Devam Ediyor 💧",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$streakCount günlük su serisine ulaştınız!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Devam Et",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Duolingo-style Streak Goal ("Hedef Seri") selector & progress sheet.
 * Lets the student commit to 7 / 14 / 30 / 50 consecutive days and preview escalating
 * League bonus points + Streak Freeze rewards.
 */
@Composable
fun StreakGoalChallengeDialog(
    streakState: StreakState,
    onSelectGoalDays: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeTier = StreakGoalTier.fromTargetDays(streakState.streakGoalDays)
    val clampedProgress = streakState.streakCount.coerceAtMost(activeTier.targetDays)
    val progressFraction = (clampedProgress.toFloat() / activeTier.targetDays.toFloat()).coerceIn(0f, 1f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surface,
            contentColor = colorScheme.onSurface,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with current streak & freeze balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.WaterDrop,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Hedef Seri Meydan Okuması",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Mevcut Seri: ${streakState.streakCount} Gün",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AcUnit,
                                contentDescription = "Seri Dondurma",
                                tint = colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${streakState.streakFreezeAvailable}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Active Goal Progress Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Aktif Hedef: ${activeTier.targetDays} Gün",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "$clampedProgress / ${activeTier.targetDays} gün",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = colorScheme.primary,
                            trackColor = colorScheme.surfaceVariant
                        )
                        Text(
                            text = "Ödül: +${activeTier.leaguePointsReward} Lig Puanı (+${activeTier.weeklyLeagueBonusSeconds / 60} dk lig bonusu) ve +${activeTier.freezeReward} Seri Dondurma Hakkı",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Ardışık gün hedefini seç (seri uzadıkça ödüller katlanır):",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )

                // 4 Duolingo-style Tier Cards (7 / 14 / 30 / 50 gün)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreakGoalTier.ALL_TIERS.forEach { tier ->
                        val isSelected = tier.targetDays == streakState.streakGoalDays
                        val isClaimed = tier.targetDays in streakState.claimedGoalMilestones
                        Surface(
                            onClick = { onSelectGoalDays(tier.targetDays) },
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                isSelected -> colorScheme.primaryContainer.copy(alpha = 0.65f)
                                isClaimed -> colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                                else -> colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.6.dp else 1.dp,
                                color = when {
                                    isSelected -> colorScheme.primary
                                    isClaimed -> colorScheme.tertiary.copy(alpha = 0.55f)
                                    else -> colorScheme.outlineVariant.copy(alpha = 0.5f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = tier.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) colorScheme.primary else colorScheme.surface
                                        ) {
                                            Text(
                                                text = tier.badgeTitle,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "+${tier.leaguePointsReward} Lig Puanı • +${tier.weeklyLeagueBonusSeconds / 60} dk Bonus • +${tier.freezeReward} Seri Dondurma ❄️",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isClaimed) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Tamamlandı",
                                        tint = colorScheme.tertiary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = colorScheme.primary
                                    ) {
                                        Text(
                                            text = "Aktif",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tamam", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Celebration modal shown when a student unlocks a 7 / 14 / 30 / 50-day Streak Goal milestone.
 */
@Composable
fun StreakGoalRewardCelebrationDialog(
    unlockedTier: StreakGoalTier,
    nextGoalDays: Int,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surface,
            contentColor = colorScheme.onSurface,
            shadowElevation = 16.dp,
            border = BorderStroke(1.5.dp, colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primaryContainer,
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Hedef Seri Ödülü",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Text(
                    text = "${unlockedTier.targetDays} Günlük Seri Hedefi Tamam!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "\"${unlockedTier.badgeTitle}\" ödülün hesabına ve Shark Ligi'ne eklendi:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "+${unlockedTier.leaguePointsReward} Lig Puanı & +${unlockedTier.weeklyLeagueBonusSeconds / 60} dk Haftalık Lig Bonusu",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "+${unlockedTier.freezeReward} Seri Dondurma Hakkı ❄️",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.secondary
                        )
                    }
                }

                if (nextGoalDays > unlockedTier.targetDays) {
                    Text(
                        text = "Sıradaki hedefin otomatik olarak $nextGoalDays gün ayarlandı!",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Harika, Devam Et!", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
