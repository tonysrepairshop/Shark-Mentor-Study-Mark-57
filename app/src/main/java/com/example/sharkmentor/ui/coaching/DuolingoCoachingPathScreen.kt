package com.example.sharkmentor.ui.coaching

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.ai.MultiProviderAiClient
import com.example.sharkmentor.core.ai.UserAiPreferences
import com.example.sharkmentor.core.coaching.AppStudyMode
import com.example.sharkmentor.core.coaching.BookCampManager
import com.example.sharkmentor.core.coaching.CampDayEpisode
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.coaching.DailyGoalLedgerEntry
import com.example.sharkmentor.core.coaching.DailyGoalStatus
import com.example.sharkmentor.core.coaching.StudentTargetContext
import com.example.sharkmentor.core.coaching.WeeklyVarianceAdvice
import com.example.sharkmentor.core.service.FocusTimerService
import com.example.sharkmentor.core.timer.TimerMode
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.core.timer.TimerState
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.PlanFolderEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.domain.engine.DailyFocusEngine
import com.example.sharkmentor.ui.components.RichSolutionContent
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DuolingoNodeKind(val badgeLabel: String) {
    VIDEO_CAMP("1. Adım • Kamp & Konu"),
    PRACTICE_FOCUS("2. Adım • Soru & Odak"),
    ERROR_REVIEW("3. Adım • Mini Kontrol")
}

@Immutable
data class DuolingoPathNode(
    val id: String,
    val stepIndex: Int,
    val kind: DuolingoNodeKind,
    val subject: String,
    val topic: String,
    val subtitle: String,
    val campTitle: String,
    val campEpisode: CampDayEpisode,
    val linkedPlanTask: PlanEntity? = null,
    val linkedResource: ResourceEntity? = null,
    val targetQuestions: Int = 30,
    val isCompleted: Boolean = false
)

/**
 * Top Mode Switcher Pill (shown on HomeScreen when "Kapsamlı Destek" is enabled in Settings):
 * lets the student switch seamlessly between [AppStudyMode.FREE_TRACKING] ("Kronometre")
 * and [AppStudyMode.REAL_COACHING] ("Koçluk Yolu").
 */
@Composable
fun StudyModeHeaderSwitcher(
    currentMode: AppStudyMode,
    onModeChange: (AppStudyMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppStudyMode.entries.forEach { mode ->
                val isSelected = currentMode == mode
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) colorScheme.primary else Color.Transparent,
                    label = "study_mode_bg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                    label = "study_mode_fg"
                )
                Surface(
                    onClick = { onModeChange(mode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = bgColor
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (mode == AppStudyMode.REAL_COACHING) Icons.Default.Route else Icons.Default.Timer,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (mode == AppStudyMode.REAL_COACHING) "Koçluk Yolu" else "Kronometre",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Daily Study Goal Progress & Live Variance (-/+) Card shown on both the Focus Timer screen
 * and the Duolingo Real Coaching Path.
 */
@Composable
fun DailyGoalProgressCard(
    goalStatus: DailyGoalStatus,
    useAiGoal: Boolean,
    onEditGoalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val animatedProgress by animateFloatAsState(
        targetValue = goalStatus.progressFraction,
        label = "daily_goal_progress"
    )
    val badgeColor = when {
        goalStatus.isOvertime -> colorScheme.primary
        goalStatus.isGoalReached -> Color(0xFF10B981)
        else -> colorScheme.secondary
    }

    Surface(
        onClick = onEditGoalClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (goalStatus.isGoalReached) colorScheme.primary.copy(alpha = 0.45f)
            else colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Günlük Çalışma Hedefi",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            if (useAiGoal) {
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = colorScheme.primary.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "AI Hedef",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${goalStatus.formattedWorked()} / ${goalStatus.formattedTarget()} • ${goalStatus.formattedDeltaDetail()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Live -/+ Delta Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = goalStatus.formattedDeltaBadge(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (goalStatus.isOvertime) colorScheme.primary else colorScheme.primary,
                trackColor = colorScheme.surface
            )
        }
    }
}

/**
 * Dialog for editing Weekday / Weekend Daily Study Goal or enabling AI Target-Based Goal.
 */
@Composable
fun DailyGoalConfigDialog(
    weekdayMinutes: Int,
    weekendMinutes: Int,
    useAiRecommendation: Boolean,
    targetContext: StudentTargetContext,
    onSave: (weekdayMins: Int, weekendMins: Int, useAi: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var useAi by remember(useAiRecommendation) { mutableStateOf(useAiRecommendation) }
    var weekdayHoursText by remember(weekdayMinutes) {
        mutableStateOf(String.format(java.util.Locale.US, "%.1f", weekdayMinutes / 60f).removeSuffix(".0"))
    }
    var weekendHoursText by remember(weekendMinutes) {
        mutableStateOf(String.format(java.util.Locale.US, "%.1f", weekendMinutes / 60f).removeSuffix(".0"))
    }

    val aiWeekday = remember(targetContext) { targetContext.recommendDailyMinutes(isWeekend = false) }
    val aiWeekend = remember(targetContext) { targetContext.recommendDailyMinutes(isWeekend = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Günlük Çalışma Hedefi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Hedefime Göre Belirlesin",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Hafta içi ${CoachingEngine.formatMinutesShort(aiWeekday)} • Hafta sonu ${CoachingEngine.formatMinutesShort(aiWeekend)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary
                            )
                        }
                        Switch(
                            checked = useAi,
                            onCheckedChange = { checked ->
                                useAi = checked
                                if (checked) {
                                    weekdayHoursText = String.format(java.util.Locale.US, "%.1f", aiWeekday / 60f).removeSuffix(".0")
                                    weekendHoursText = String.format(java.util.Locale.US, "%.1f", aiWeekend / 60f).removeSuffix(".0")
                                }
                            }
                        )
                    }
                }

                // Quick hour preset chips
                Text(
                    text = "Hızlı Seçim (Saat / Gün)",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2, 3, 4, 5, 6, 8).forEach { hr ->
                        val isSelected = !useAi && weekdayHoursText == hr.toString()
                        Surface(
                            onClick = {
                                useAi = false
                                weekdayHoursText = hr.toString()
                                weekendHoursText = (hr + 1).coerceAtMost(10).toString()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isSelected) colorScheme.primary else colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "${hr}s",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = weekdayHoursText,
                        onValueChange = {
                            useAi = false
                            weekdayHoursText = it
                        },
                        label = { Text("Hafta İçi (Saat)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weekendHoursText,
                        onValueChange = {
                            useAi = false
                            weekendHoursText = it
                        },
                        label = { Text("Hafta Sonu (Saat)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val wdMins = if (useAi) aiWeekday else ((weekdayHoursText.replace(",", ".").toFloatOrNull() ?: 4f) * 60f).toInt()
                    val weMins = if (useAi) aiWeekend else ((weekendHoursText.replace(",", ".").toFloatOrNull() ?: 5f) * 60f).toInt()
                    onSave(wdMins.coerceIn(30, 720), weMins.coerceIn(30, 720), useAi)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Kaydet", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

/**
 * Two-Layer Focus Title / Category & Sub-Note Selector shown on the Focus Timer screen.
 * Allows selecting preset exam titles (TYT, AYT, TUS, KPSS, Kitap Okuma, Deneme...), adding custom
 * titles, and entering an optional sub-note ("Türev Test 4") broadcast live to Shark League.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusTopicSelectorCard(
    availableTitles: List<String>,
    customTitles: List<String>,
    selectedTitle: String,
    subNote: String,
    onSelectTitle: (String) -> Unit,
    onSubNoteChange: (String) -> Unit,
    onAddCustomTitle: (String) -> Unit,
    onRemoveCustomTitle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var showAddTitleDialog by remember { mutableStateOf(false) }
    var newTitleInput by remember { mutableStateOf("") }

    if (showAddTitleDialog) {
        AlertDialog(
            onDismissRequest = { showAddTitleDialog = false },
            title = { Text("Yeni Odak Başlığı", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitleInput,
                    onValueChange = { newTitleInput = it },
                    label = { Text("Başlık Adı") },
                    placeholder = { Text("Örn: AYT Fizik, TUS, Kitap Okuma") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitleInput.isNotBlank()) {
                            onAddCustomTitle(newTitleInput.trim())
                            newTitleInput = ""
                            showAddTitleDialog = false
                        }
                    },
                    enabled = newTitleInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTitleDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Çalışma Başlığı",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = CoachingEngine.buildFormattedFocusSubject(selectedTitle, subNote),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Title Category Chips + "+ Başlık"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableTitles.forEach { title ->
                    val isSelected = selectedTitle.equals(title, ignoreCase = true)
                    val isCustom = customTitles.any { it.equals(title, ignoreCase = true) }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) colorScheme.primary else colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) colorScheme.primary else colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .combinedClickable(
                                onClick = { onSelectTitle(title) },
                                onLongClick = {
                                    if (isCustom) onRemoveCustomTitle(title)
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                            )
                            if (isCustom && isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Sil",
                                    tint = colorScheme.onPrimary.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clickable { onRemoveCustomTitle(title) }
                                )
                            }
                        }
                    }
                }

                // "+ Başlık Ekle" chip
                Surface(
                    onClick = { showAddTitleDialog = true },
                    shape = RoundedCornerShape(50),
                    color = colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Yeni Başlık",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Başlık Ekle",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }
                }
            }

            // Optional Sub-Note / Specific Task input
            OutlinedTextField(
                value = subNote,
                onValueChange = onSubNoteChange,
                placeholder = {
                    Text(
                        text = "Alt detay (Opsiyonel: Türev Test 4, 3. Gün Kampı...)",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Compact modal dialog popped up over [com.example.sharkmentor.ui.screens.HomeScreen] right when
 * the student taps Start on any focus mode (`Kronometre`, `90 Dk Blok`, `Pomodoro`).
 * Lets the student select or add a focus title and immediately start via "Odağı Başlat".
 */
@Composable
fun FocusTopicLaunchDialog(
    mode: TimerMode,
    availableTitles: List<String>,
    customTitles: List<String>,
    selectedTitle: String,
    subNote: String,
    onSelectTitle: (String) -> Unit,
    onSubNoteChange: (String) -> Unit,
    onAddCustomTitle: (String) -> Unit,
    onRemoveCustomTitle: (String) -> Unit,
    onConfirmStart: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Çalışma Başlığı Seç",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "${mode.displayName} oturumu için başlık belirleyin",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary
                )
            }
        },
        text = {
            FocusTopicSelectorCard(
                availableTitles = availableTitles,
                customTitles = customTitles,
                selectedTitle = selectedTitle,
                subNote = subNote,
                onSelectTitle = onSelectTitle,
                onSubNoteChange = onSubNoteChange,
                onAddCustomTitle = onAddCustomTitle,
                onRemoveCustomTitle = onRemoveCustomTitle
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmStart,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Odağı Başlat", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

/**
 * Dedicated Settings sub-page ("Kapsamlı Destek & Çalışma Ayarları") inside Ayarlar.
 * Houses:
 * 1. "Kapsamlı Destek" enable/disable toggle (adds the "Kronometre | Koçluk Yolu" bar to HomeScreen).
 * 2. Daily Study Hours ("Günlük Çalışma Saati Belirleme") & AI Target-based Goal settings.
 * 3. Focus Title / Category management ("Çalışma Başlığı Ayarları").
 */
@Composable
fun ComprehensiveSupportSettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(context) {
        CoachingEngine.ensureInitialized(context)
        CoachingEngine.refreshTargetAndLedgerAsync(context)
    }

    val comprehensiveSupportEnabled by CoachingEngine.comprehensiveSupportEnabled.collectAsStateWithLifecycle()
    val targetContext by CoachingEngine.targetContext.collectAsStateWithLifecycle()
    val weekdayGoalMins by CoachingEngine.weekdayGoalMinutes.collectAsStateWithLifecycle()
    val weekendGoalMins by CoachingEngine.weekendGoalMinutes.collectAsStateWithLifecycle()
    val useAiGoal by CoachingEngine.useAiRecommendedGoal.collectAsStateWithLifecycle()
    val customFocusTitles by CoachingEngine.customFocusTitles.collectAsStateWithLifecycle()
    val selectedFocusTitle by CoachingEngine.selectedFocusTitle.collectAsStateWithLifecycle()
    val focusSubNote by CoachingEngine.focusSubNote.collectAsStateWithLifecycle()

    val todayBounds = remember { DailyFocusEngine.getTodayTimeBounds() }
    val todayDbMinutesFlow = remember(context, todayBounds) {
        SharkMentorDatabase.getDatabase(context)
            .sessionDao()
            .getTodayTotalFocusMinutes(todayBounds.first, todayBounds.second)
    }
    val todayDbMinutes by todayDbMinutesFlow.collectAsStateWithLifecycle(initialValue = 0)
    val todayTargetMinutes = remember(targetContext, weekdayGoalMins, weekendGoalMins, useAiGoal) {
        CoachingEngine.getTodayTargetMinutes(targetContext)
    }
    val dailyGoalStatus = remember(todayDbMinutes, todayTargetMinutes) {
        CoachingEngine.computeDailyGoalStatus(
            todayCompletedMinutes = todayDbMinutes ?: 0,
            activeRunningSeconds = 0L,
            targetMinutes = todayTargetMinutes
        )
    }
    val availableFocusTitles = remember(targetContext.examType, customFocusTitles) {
        CoachingEngine.availableFocusTitles(targetContext.examType)
    }

    var showDailyGoalDialog by remember { mutableStateOf(false) }
    if (showDailyGoalDialog) {
        DailyGoalConfigDialog(
            weekdayMinutes = weekdayGoalMins,
            weekendMinutes = weekendGoalMins,
            useAiRecommendation = useAiGoal,
            targetContext = targetContext,
            onSave = { wd, we, ai ->
                CoachingEngine.setDailyGoals(context, wd, we, ai)
                showDailyGoalDialog = false
            },
            onDismiss = { showDailyGoalDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Kapsamlı Destek Toggle Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                1.dp,
                if (comprehensiveSupportEnabled) colorScheme.primary.copy(alpha = 0.55f)
                else colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Kapsamlı Destek",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Açıldığında ana ekrana Kronometre ve Koçluk Yolu geçiş barını ekler.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = comprehensiveSupportEnabled,
                    onCheckedChange = { enabled ->
                        CoachingEngine.setComprehensiveSupportEnabled(context, enabled)
                    }
                )
            }
        }

        // 2. Günlük Çalışma Saati Belirleme Section
        Text(
            text = "Günlük Çalışma Saati Belirleme",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary
        )
        DailyGoalProgressCard(
            goalStatus = dailyGoalStatus,
            useAiGoal = useAiGoal,
            onEditGoalClick = { showDailyGoalDialog = true }
        )
        OutlinedButton(
            onClick = { showDailyGoalDialog = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Hafta İçi (${CoachingEngine.formatMinutesShort(weekdayGoalMins)}) • Hafta Sonu (${CoachingEngine.formatMinutesShort(weekendGoalMins)}) Düzenle",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 3. Çalışma Başlığı Seçimi & Başlık Yönetimi Section
        Text(
            text = "Çalışma Başlığı Ayarları",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary
        )
        FocusTopicSelectorCard(
            availableTitles = availableFocusTitles,
            customTitles = customFocusTitles,
            selectedTitle = selectedFocusTitle,
            subNote = focusSubNote,
            onSelectTitle = { CoachingEngine.selectFocusTitle(context, it) },
            onSubNoteChange = { CoachingEngine.setFocusSubNote(context, it) },
            onAddCustomTitle = { CoachingEngine.addCustomFocusTitle(context, it) },
            onRemoveCustomTitle = { CoachingEngine.removeCustomFocusTitle(context, it) }
        )
    }
}

/**
 * Full-Screen Duolingo-Style Guided Daily Path & Comprehensive Support Cockpit
 * rendered when [AppStudyMode.REAL_COACHING] ("Koçluk Yolu") is active.
 */
@Composable
fun DuolingoCoachingPathView(
    timerState: TimerState,
    goalStatus: DailyGoalStatus,
    useAiGoal: Boolean,
    targetContext: StudentTargetContext,
    weeklyAdvice: WeeklyVarianceAdvice?,
    recentLedger: List<DailyGoalLedgerEntry>,
    onSwitchMode: (AppStudyMode) -> Unit,
    onEditDailyGoal: () -> Unit,
    onStartFocusWithSubject: (TimerMode, String) -> Unit,
    onPauseFocus: () -> Unit,
    onResumeFocus: () -> Unit,
    onStopFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val db = remember(context) { SharkMentorDatabase.getDatabase(context) }

    val todayIso = remember {
        CoachingEngine.calendarDayToIso(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    }
    var selectedDayIso by remember { mutableIntStateOf(todayIso) }

    // Observe active plan folder & tasks + library resources + topic mastery goals
    val activeFolders by db.planDao().getAllActiveFolders().collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedFolder = activeFolders.firstOrNull()
    val folderTasksFlow = remember(selectedFolder?.id) {
        if (selectedFolder != null) db.planDao().getTasksForFolder(selectedFolder.id)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val allFolderTasks by folderTasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val libraryResources by db.goalsDao().getAllResourcesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val topicGoals by db.goalsDao().getAllTopicGoalsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val campBindings by BookCampManager.bindingsByResourceId.collectAsStateWithLifecycle()

    // Single-resource focus filter so books are NOT interleaved/mixed together
    var selectedResourceId by remember { mutableStateOf<Long?>(null) }
    val activeSelectedResource = remember(libraryResources, selectedResourceId) {
        libraryResources.firstOrNull { it.id == selectedResourceId } ?: libraryResources.firstOrNull()
    }

    var completedNodeIds by remember { mutableStateOf(loadCompletedNodeIds(context)) }
    var activeStudioNode by remember { mutableStateOf<DuolingoPathNode?>(null) }
    var activePracticeNode by remember { mutableStateOf<DuolingoPathNode?>(null) }
    var activeMiniCheckNode by remember { mutableStateOf<DuolingoPathNode?>(null) }
    var isRebalancingWeek by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        BookCampManager.ensureLoaded(context)
    }

    LaunchedEffect(activeSelectedResource?.id, activeSelectedResource?.title, activeSelectedResource?.subject) {
        val res = activeSelectedResource ?: return@LaunchedEffect
        val currentBinding = BookCampManager.resolveCampForResource(context, res)
        if (!currentBinding.videoCheckCompleted) {
            BookCampManager.discoverAndSyncRealCampForResource(context, res)
        }
    }

    // Build daily Duolingo path nodes (shows Video step ONLY when real verified video exists)
    val dayTasks = remember(allFolderTasks, selectedDayIso) {
        allFolderTasks.filter { it.dayOfWeek == selectedDayIso }
    }

    val pathNodes = remember(
        dayTasks,
        libraryResources,
        activeSelectedResource,
        topicGoals,
        campBindings,
        selectedDayIso,
        completedNodeIds,
        targetContext
    ) {
        buildDuolingoDayNodes(
            context = context,
            dayIso = selectedDayIso,
            dayTasks = dayTasks,
            libraryResources = libraryResources,
            activeResource = activeSelectedResource,
            topicGoals = topicGoals,
            targetContext = targetContext,
            completedNodeIds = completedNodeIds
        )
    }

    // Dialog 1: Split-Screen Camp Video Study Studio
    activeStudioNode?.let { node ->
        val resourceBinding = node.linkedResource?.let { BookCampManager.resolveCampForResource(context, it) }
        CampVideoStudyStudioDialog(
            campTitle = node.campTitle,
            subject = node.subject,
            initialEpisode = node.campEpisode,
            allEpisodes = resourceBinding?.episodes?.ifEmpty { listOf(node.campEpisode) } ?: listOf(node.campEpisode),
            onEpisodeChanged = { chosenEp ->
                node.linkedResource?.let { res ->
                    BookCampManager.advanceCampDay(context, res, targetDay = chosenEp.dayNumber)
                }
            },
            onMarkCompleted = { completedEp ->
                val updated = completedNodeIds + node.id
                completedNodeIds = updated
                saveCompletedNodeIds(context, updated)
                node.linkedResource?.let { res ->
                    BookCampManager.advanceCampDay(context, res, targetDay = completedEp.dayNumber + 1)
                }
                activeStudioNode = null
            },
            onDismiss = { activeStudioNode = null }
        )
    }

    // Dialog 2: Practice Focus & Automatic Library Resource Unit Deduction
    activePracticeNode?.let { node ->
        PracticeNodeActionDialog(
            node = node,
            isTimerRunning = timerState.timerPhase == TimerPhase.FOCUSING,
            onStartFocus = { chosenMode ->
                val formattedSubject = CoachingEngine.buildFormattedFocusSubject(
                    title = node.subject,
                    subNote = node.topic.take(40)
                )
                CoachingEngine.selectFocusTitle(context, node.subject)
                CoachingEngine.setFocusSubNote(context, node.topic.take(40))
                onStartFocusWithSubject(chosenMode, formattedSubject)
                activePracticeNode = null
            },
            onCompleteAndDeductFromBook = { solvedUnits ->
                scope.launch(Dispatchers.IO) {
                    if (node.linkedResource != null && solvedUnits > 0) {
                        db.goalsDao().incrementProgress(node.linkedResource.id, step = solvedUnits)
                    }
                    if (node.linkedPlanTask != null) {
                        db.planDao().updateTaskCompletion(node.linkedPlanTask.id, true)
                    }
                    withContext(Dispatchers.Main) {
                        val updated = completedNodeIds + node.id
                        completedNodeIds = updated
                        saveCompletedNodeIds(context, updated)
                        activePracticeNode = null
                        Toast.makeText(
                            context,
                            if (node.linkedResource != null) {
                                "${node.linkedResource.title} kaynağından $solvedUnits ${node.linkedResource.unitType} tamamlandı"
                            } else {
                                "Görev tamamlandı"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = { activePracticeNode = null }
        )
    }

    // Dialog 3: Mini Topic Check & Error Consolidation
    activeMiniCheckNode?.let { node ->
        MiniTopicMasteryCheckDialog(
            node = node,
            onCompleted = {
                val updated = completedNodeIds + node.id
                completedNodeIds = updated
                saveCompletedNodeIds(context, updated)
                activeMiniCheckNode = null
            },
            onDismiss = { activeMiniCheckNode = null }
        )
    }

    Column(
        modifier = modifier
            .widthIn(max = 680.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Mode Switcher
        StudyModeHeaderSwitcher(
            currentMode = AppStudyMode.REAL_COACHING,
            onModeChange = onSwitchMode
        )

        // 2. Macro Target Compass Banner ("Ana Pusula")
        val compassBrush = Brush.horizontalGradient(
            colors = listOf(
                colorScheme.primaryContainer.copy(alpha = 0.78f),
                colorScheme.tertiaryContainer.copy(alpha = 0.50f),
                colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            border = BorderStroke(1.2.dp, colorScheme.primary.copy(alpha = 0.48f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(compassBrush)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ana Hedef Pusulası",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = targetContext.compassHeadline(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (targetContext.examType.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.primary.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = targetContext.examType,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 3. Live Active Focus Cockpit (when timer is actively running or paused)
        AnimatedVisibility(
            visible = timerState.timerPhase != TimerPhase.IDLE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface,
                border = BorderStroke(1.5.dp, colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val cleanCockpitSubj = CoachingEngine.formatCleanLeagueSubject(
                            timerState.activeSubject.ifBlank { CoachingEngine.buildFormattedFocusSubject() }
                        ).ifBlank { "Odak" }
                        Text(
                            text = "Masada • $cleanCockpitSubj",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = FocusTimerService.formatMillis(timerState.displayMillis),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isPaused = timerState.timerPhase == TimerPhase.PAUSED
                        FilledTonalIconButtonSmall(
                            icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            tint = colorScheme.primary,
                            onClick = { if (isPaused) onResumeFocus() else onPauseFocus() }
                        )
                        FilledTonalIconButtonSmall(
                            icon = Icons.Default.Stop,
                            tint = colorScheme.error,
                            onClick = onStopFocus
                        )
                    }
                }
            }
        }

        // 4. Daily Study Goal & Live Variance (-/+) Card
        DailyGoalProgressCard(
            goalStatus = goalStatus,
            useAiGoal = useAiGoal,
            onEditGoalClick = onEditDailyGoal
        )

        // 5. Adaptive AI Coach Weekly Variance Card ("Çarşamba az çalıştın, Perşembe çok...")
        if (weeklyAdvice != null || recentLedger.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "AI Koç Tempo & Sapma Analizi",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }

                        Surface(
                            onClick = {
                                if (isRebalancingWeek) return@Surface
                                isRebalancingWeek = true
                                scope.launch {
                                    val count = rebalanceWeeklyPlanWithAiCoach(
                                        context = context,
                                        db = db,
                                        libraryResources = libraryResources,
                                        activeResource = activeSelectedResource,
                                        topicGoals = topicGoals,
                                        targetContext = targetContext
                                    )
                                    isRebalancingWeek = false
                                    Toast.makeText(
                                        context,
                                        "Haftalık program müfredat sırası ve konu hakimiyetine göre dengelendi ($count görev)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.primary.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isRebalancingWeek) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.8.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = "Programı Dengele",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                            }
                        }
                    }

                    // 7-day mini bar strip
                    if (recentLedger.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            recentLedger.forEach { entry ->
                                val ratio = (entry.workedMinutes.toFloat() / entry.targetMinutes.coerceAtLeast(1).toFloat())
                                    .coerceIn(0.08f, 1.25f)
                                val barColor = when {
                                    entry.deltaMinutes >= 0 -> colorScheme.primary
                                    entry.workedMinutes > 0 -> colorScheme.secondary
                                    else -> colorScheme.outlineVariant
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = if (entry.workedMinutes > 0) "${entry.workedMinutes / 60}s" else "-",
                                        fontSize = 9.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.62f)
                                            .height((34f * (ratio / 1.25f)).coerceAtLeast(4f).dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(barColor)
                                    )
                                    Text(
                                        text = entry.dayLabelTr.take(3),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (weeklyAdvice != null) {
                        Text(
                            text = weeklyAdvice.conciseMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 5b. Active Book Banner & Selector (ALWAYS clearly displays which book is being progressed!)
        if (activeSelectedResource != null) {
            val activeBinding = remember(activeSelectedResource, campBindings) {
                BookCampManager.resolveCampForResource(context, activeSelectedResource)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.primary.copy(alpha = 0.14f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Book,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "İlerlenen Kitap • ${activeSelectedResource.subject}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Text(
                                    text = activeSelectedResource.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val bookModeSubtitle = when {
                                    activeBinding.hasVerifiedVideoSupport ->
                                        "Video Destekli • ${activeBinding.currentDay}/${activeBinding.totalDays}. Ders"
                                    !activeBinding.videoCheckCompleted ->
                                        "Video desteği kontrol ediliyor..."
                                    else ->
                                        "Kitap & Soru Odaklı İlerleme"
                                }
                                Text(
                                    text = bookModeSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (activeSelectedResource.totalUnits > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${activeSelectedResource.completedUnits}/${activeSelectedResource.totalUnits} ${activeSelectedResource.unitType}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (libraryResources.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            libraryResources.forEach { res ->
                                val isSelected = activeSelectedResource.id == res.id
                                Surface(
                                    onClick = { selectedResourceId = res.id },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (isSelected) colorScheme.primary else colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        text = "${res.title} (${res.subject})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. 7-Day Selector Strip for Duolingo Path
        val shortDays = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            shortDays.forEachIndexed { idx, label ->
                val dayNum = idx + 1
                val isSelected = selectedDayIso == dayNum
                val isToday = todayIso == dayNum
                Surface(
                    onClick = { selectedDayIso = dayNum },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isSelected -> colorScheme.primary
                        isToday -> colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else -> colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        width = if (isSelected || isToday) 1.3.dp else 1.dp,
                        color = when {
                            isSelected -> colorScheme.primary
                            isToday -> colorScheme.primary.copy(alpha = 0.6f)
                            else -> colorScheme.outlineVariant.copy(alpha = 0.35f)
                        }
                    ),
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isSelected -> colorScheme.onPrimary
                                isToday -> colorScheme.primary
                                else -> colorScheme.onSurface
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(if (isToday) 5.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> colorScheme.onPrimary
                                        isToday -> colorScheme.primary
                                        else -> Color.Transparent
                                    }
                                )
                        )
                    }
                }
            }
        }

        // 7. Duolingo-Style Winding Daily Node Path
        val completedCount = pathNodes.count { it.isCompleted }
        val totalNodes = pathNodes.size.coerceAtLeast(1)
        val pathProgressFraction by animateFloatAsState(
            targetValue = completedCount.toFloat() / totalNodes.toFloat(),
            label = "coaching_path_progress"
        )
        val firstIncompleteIndex = pathNodes.indexOfFirst { !it.isCompleted }.let {
            if (it == -1) pathNodes.lastIndex else it
        }
        val isMonochromeTheme =
            colorScheme.primary.red == colorScheme.primary.green &&
                colorScheme.primary.green == colorScheme.primary.blue &&
                colorScheme.secondary.red == colorScheme.secondary.green
        val completedAccent = if (isMonochromeTheme) colorScheme.primary else Color(0xFF10B981)
        val allDone = pathNodes.isNotEmpty() && completedCount == pathNodes.size
        val hasVerifiedVideoStep = pathNodes.any { it.kind == DuolingoNodeKind.VIDEO_CAMP }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colorScheme.surface,
            border = BorderStroke(
                1.2.dp,
                if (allDone) completedAccent.copy(alpha = 0.55f) else colorScheme.primary.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${CoachingEngine.isoDayLabelTr(selectedDayIso)} Yol Haritası",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface
                        )
                        val bookTag = activeSelectedResource?.title?.takeIf { it.isNotBlank() }?.let { "$it • " }.orEmpty()
                        Text(
                            text = if (hasVerifiedVideoStep) {
                                "${bookTag}Ders Videosu • Soru & Odak • Mini Kontrol"
                            } else {
                                "${bookTag}Soru & Odak • Mini Kontrol"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (allDone) completedAccent.copy(alpha = 0.16f) else colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(
                            1.dp,
                            if (allDone) completedAccent.copy(alpha = 0.45f) else colorScheme.primary.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = "$completedCount / ${pathNodes.size} Adım",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (allDone) completedAccent else colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { pathProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = if (allDone) completedAccent else colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                pathNodes.forEachIndexed { idx, node ->
                    val isCurrentActive = idx == firstIncompleteIndex && !node.isCompleted
                    val horizShiftDp = when (idx % 4) {
                        1 -> (-12).dp
                        3 -> 12.dp
                        else -> 0.dp
                    }

                    DuolingoPathNodeCard(
                        node = node,
                        isCurrentStep = isCurrentActive,
                        completedAccent = completedAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(x = horizShiftDp),
                        onClick = {
                            when (node.kind) {
                                DuolingoNodeKind.VIDEO_CAMP -> activeStudioNode = node
                                DuolingoNodeKind.PRACTICE_FOCUS -> activePracticeNode = node
                                DuolingoNodeKind.ERROR_REVIEW -> activeMiniCheckNode = node
                            }
                        }
                    )

                    if (idx < pathNodes.lastIndex) {
                        val lineDone = node.isCompleted
                        val connectorColor = when {
                            lineDone -> completedAccent
                            isCurrentActive -> colorScheme.primary.copy(alpha = 0.7f)
                            else -> colorScheme.outlineVariant.copy(alpha = 0.75f)
                        }
                        Canvas(
                            modifier = Modifier
                                .width(40.dp)
                                .height(26.dp)
                        ) {
                            val centerX = size.width / 2f
                            drawLine(
                                color = connectorColor,
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, size.height),
                                strokeWidth = if (lineDone || isCurrentActive) 6.5f else 5f,
                                cap = StrokeCap.Round,
                                pathEffect = if (lineDone) null else PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f)
                            )
                            drawCircle(
                                color = connectorColor,
                                radius = if (lineDone) 4.5f else 3.5f,
                                center = Offset(centerX, size.height / 2f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FilledTonalIconButtonSmall(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tint.copy(alpha = 0.15f),
        modifier = Modifier.size(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DuolingoPathNodeCard(
    node: DuolingoPathNode,
    isCurrentStep: Boolean,
    completedAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val stepKindAccent = when (node.kind) {
        DuolingoNodeKind.VIDEO_CAMP -> colorScheme.primary
        DuolingoNodeKind.PRACTICE_FOCUS -> colorScheme.tertiary
        DuolingoNodeKind.ERROR_REVIEW -> colorScheme.secondary
    }
    val stepKindContainer = when (node.kind) {
        DuolingoNodeKind.VIDEO_CAMP -> colorScheme.primaryContainer
        DuolingoNodeKind.PRACTICE_FOCUS -> colorScheme.tertiaryContainer
        DuolingoNodeKind.ERROR_REVIEW -> colorScheme.secondaryContainer
    }
    val stepKindOnAccent = when (node.kind) {
        DuolingoNodeKind.VIDEO_CAMP -> colorScheme.onPrimary
        DuolingoNodeKind.PRACTICE_FOCUS -> colorScheme.onTertiary
        DuolingoNodeKind.ERROR_REVIEW -> colorScheme.onSecondary
    }
    val nodeAccent = if (node.isCompleted) completedAccent else stepKindAccent
    val nodeIcon = when (node.kind) {
        DuolingoNodeKind.VIDEO_CAMP -> Icons.Default.PlayCircleOutline
        DuolingoNodeKind.PRACTICE_FOCUS -> Icons.Default.Book
        DuolingoNodeKind.ERROR_REVIEW -> Icons.Default.Quiz
    }

    val cardBackgroundBrush = when {
        node.isCompleted -> Brush.horizontalGradient(
            listOf(
                completedAccent.copy(alpha = 0.10f),
                colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        )
        isCurrentStep -> Brush.horizontalGradient(
            listOf(
                stepKindContainer.copy(alpha = 0.72f),
                stepKindContainer.copy(alpha = 0.28f),
                colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        )
        else -> Brush.horizontalGradient(
            listOf(
                stepKindContainer.copy(alpha = 0.26f),
                colorScheme.surfaceVariant.copy(alpha = 0.48f)
            )
        )
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = if (isCurrentStep) 1.8.dp else 1.dp,
            color = when {
                node.isCompleted -> completedAccent.copy(alpha = 0.55f)
                isCurrentStep -> nodeAccent
                else -> nodeAccent.copy(alpha = 0.32f)
            }
        ),
        shadowElevation = if (isCurrentStep) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBackgroundBrush)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // Subtle vertical color rail indicating step identity
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .clip(CircleShape)
                    .background(nodeAccent.copy(alpha = if (isCurrentStep || node.isCompleted) 0.95f else 0.55f))
            )

            // Circular Duolingo Node Badge
            Surface(
                shape = CircleShape,
                color = when {
                    node.isCompleted -> completedAccent
                    isCurrentStep -> nodeAccent
                    else -> nodeAccent.copy(alpha = 0.16f)
                },
                border = BorderStroke(
                    width = 2.dp,
                    color = nodeAccent
                ),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (node.isCompleted) Icons.Default.Check else nodeIcon,
                        contentDescription = null,
                        tint = when {
                            node.isCompleted -> Color.White
                            isCurrentStep -> stepKindOnAccent
                            else -> nodeAccent
                        },
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = nodeAccent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = node.kind.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = nodeAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isCurrentStep) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = nodeAccent
                        ) {
                            Text(
                                text = "Sırada",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = stepKindOnAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "${node.subject} • ${node.topic}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = node.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (node.isCompleted) completedAccent.copy(alpha = 0.16f) else nodeAccent
            ) {
                Text(
                    text = when {
                        node.isCompleted -> "Tamam"
                        node.kind == DuolingoNodeKind.VIDEO_CAMP -> "İzle"
                        node.kind == DuolingoNodeKind.PRACTICE_FOCUS -> "Başla"
                        else -> "Kontrol"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (node.isCompleted) completedAccent else stepKindOnAccent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PracticeNodeActionDialog(
    node: DuolingoPathNode,
    isTimerRunning: Boolean,
    onStartFocus: (TimerMode) -> Unit,
    onCompleteAndDeductFromBook: (solvedUnits: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val unitLabel = node.linkedResource?.unitType ?: "Test"
    var solvedUnitsText by remember { mutableStateOf("2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "${node.subject} • Soru & Odak Adımı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = node.topic,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (node.linkedResource != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Eşleşen Kütüphane Kaynağı: ${node.linkedResource.title}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Mevcut İlerleme: ${node.linkedResource.completedUnits} / ${node.linkedResource.totalUnits} ${node.linkedResource.unitType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary
                            )
                        }
                    }
                }

                // Quick Focus Launch Buttons
                Text(
                    text = "1. Odak Sayacıyla Masaya Otur",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onStartFocus(TimerMode.STOPWATCH) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kronometre", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onStartFocus(TimerMode.POMODORO) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pomodoro", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Complete & Auto-deduct from Library Book
                Text(
                    text = "2. Adımı Tamamla & Kaynaktan Düş",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                OutlinedTextField(
                    value = solvedUnitsText,
                    onValueChange = { solvedUnitsText = it.filter { c -> c.isDigit() } },
                    label = { Text("Çözülen $unitLabel Sayısı") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val units = (solvedUnitsText.toIntOrNull() ?: 1).coerceAtLeast(1)
                    onCompleteAndDeductFromBook(units)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Tamamla & Kaydet", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

@Composable
private fun MiniTopicMasteryCheckDialog(
    node: DuolingoPathNode,
    onCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var checkContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(node.id) {
        isLoading = true
        val compass = CoachingEngine.buildAiSystemCompassPrompt(context)
        val prompt = """
            $compass
            Ders: ${node.subject}
            Konu: ${node.topic}
            Öğrenci bu konunun video ve soru çözüm adımını yeni tamamladı.
            Bu konuyu pekiştirmek için:
            1) 3 maddelik en kritik sınav/ÖSYM püf noktasını yaz.
            2) 1 adet kısa kontrol sorusu ve hemen altında cevabını yaz.
            Kısa, net ve doğrudan yaz.
        """.trimIndent()

        val text = withContext(Dispatchers.IO) {
            runCatching {
                MultiProviderAiClient.executeUnifiedTextPrompt(
                    context = context,
                    systemPrompt = compass,
                    userPrompt = prompt,
                    temperature = 0.25f,
                    maxTokens = 1024
                )
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }

        checkContent = text ?: """
            • ${node.subject} - ${node.topic} temel tanım ve formüllerini gözden geçir.
            • Bugün çözdüğün testlerde boş veya yanlış bıraktığın soruları Hata Bankası'na ekle.
            • Yarınki adıma geçmeden önce 5 dakikalık hızlı özet tekrarı yap.
        """.trimIndent()
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "3. Adım • Günün Mini Kontrolü",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${node.subject} • ${node.topic}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pekiştirme özeti hazırlanıyor...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        SelectionContainer {
                            RichSolutionContent(
                                text = checkContent,
                                textColor = colorScheme.onSurface,
                                accentColor = colorScheme.primary,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCompleted,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Adımı Tamamla", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

private fun buildDuolingoDayNodes(
    context: Context,
    dayIso: Int,
    dayTasks: List<PlanEntity>,
    libraryResources: List<ResourceEntity>,
    activeResource: ResourceEntity?,
    topicGoals: List<TopicGoalEntity>,
    targetContext: StudentTargetContext,
    completedNodeIds: Set<String>
): List<DuolingoPathNode> {
    val nodes = mutableListOf<DuolingoPathNode>()
    val todayIso = CoachingEngine.calendarDayToIso(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    val dayOffsetFromToday = (dayIso - todayIso).coerceAtLeast(0)

    // 1. Build day's raw blocks without interleaving multiple library books together
    val rawBlocks: List<Triple<String, String, PlanEntity?>> = when {
        dayTasks.isNotEmpty() -> {
            dayTasks
                .sortedBy { BookCampManager.canonicalTopicIndex(it.subject, it.topic.ifBlank { it.title }) }
                .map { task ->
                    Triple(task.subject, task.topic.ifBlank { task.title }, task)
                }
        }
        activeResource != null -> {
            val binding = BookCampManager.resolveCampForResource(context, activeResource, topicGoals)
            if (binding.hasVerifiedVideoSupport && binding.episodes.isNotEmpty()) {
                val epDay = (binding.currentDay + dayOffsetFromToday).coerceIn(1, binding.episodes.size)
                val ep = binding.episodes.firstOrNull { it.dayNumber == epDay } ?: binding.currentEpisode()
                listOf(Triple(activeResource.subject, ep.topicTitle, null))
            } else {
                // Honest non-video book progression: pick real topic from student's goals or canonical subject sequence
                val subjectGoalTopics = topicGoals
                    .filter {
                        !it.isDeleted && (
                            it.subject.contains(activeResource.subject, ignoreCase = true) ||
                                activeResource.subject.contains(it.subject, ignoreCase = true)
                            )
                    }
                    .sortedWith(
                        compareBy<TopicGoalEntity> { it.currentLevel }
                            .thenBy { BookCampManager.canonicalTopicIndex(it.subject, it.topic) }
                    )
                    .map { it.topic }
                val baseTopics = subjectGoalTopics.ifEmpty {
                    BookCampManager.canonicalCurriculumForSubject(activeResource.subject, activeResource.title)
                }
                val unitIndex = (activeResource.completedUnits + dayOffsetFromToday).coerceAtLeast(0)
                val topicName = if (baseTopics.isNotEmpty()) {
                    baseTopics[unitIndex % baseTopics.size]
                } else {
                    "${activeResource.title} • ${unitIndex + 1}. ${activeResource.unitType}"
                }
                listOf(Triple(activeResource.subject, topicName, null))
            }
        }
        else -> {
            val defaultSubject = CoachingEngine.defaultFocusTitlesForExam(targetContext.examType).firstOrNull() ?: "TYT Matematik"
            val canonical = BookCampManager.canonicalCurriculumForSubject(defaultSubject)
            val topicForDay = if (canonical.isNotEmpty()) {
                canonical[(dayIso - 1).coerceAtLeast(0) % canonical.size]
            } else {
                "$defaultSubject Soru & Konu Çalışması"
            }
            listOf(Triple(defaultSubject, topicForDay, null))
        }
    }

    var stepCounter = 1
    rawBlocks.forEachIndexed { blockIdx, (subject, rawTopic, planTask) ->
        val matchedResource = if (activeResource != null &&
            (activeResource.subject.contains(subject, ignoreCase = true) || subject.contains(activeResource.subject, ignoreCase = true))
        ) {
            activeResource
        } else {
            libraryResources.firstOrNull {
                it.subject.contains(subject, ignoreCase = true) || subject.contains(it.subject, ignoreCase = true)
            }
        }

        val matchedBinding = matchedResource?.let { BookCampManager.resolveCampForResource(context, it, topicGoals) }
        val verifiedVideo: Pair<String, CampDayEpisode>? = when {
            matchedBinding != null && matchedBinding.hasVerifiedVideoSupport -> {
                val targetDay = if (planTask == null && matchedBinding.episodes.isNotEmpty()) {
                    (matchedBinding.currentDay + dayOffsetFromToday).coerceIn(1, matchedBinding.episodes.size)
                } else null
                val ep = if (targetDay != null) {
                    matchedBinding.episodes.firstOrNull { it.dayNumber == targetDay } ?: matchedBinding.currentEpisode()
                } else {
                    BookCampManager.resolveVerifiedCampEpisodeForTask(
                        context = context,
                        subject = subject,
                        topic = rawTopic,
                        taskTitle = planTask?.title.orEmpty(),
                        dayHint = dayIso,
                        libraryResources = libraryResources,
                        preferredResource = matchedResource,
                        studentTopicGoals = topicGoals
                    )?.second ?: matchedBinding.currentEpisode()
                }
                if (ep.videoId.length == 11 || ep.playlistId.isNotBlank()) {
                    matchedBinding.campTitle to ep
                } else null
            }
            else -> BookCampManager.resolveVerifiedCampEpisodeForTask(
                context = context,
                subject = subject,
                topic = rawTopic,
                taskTitle = planTask?.title.orEmpty(),
                dayHint = dayIso,
                libraryResources = libraryResources,
                preferredResource = matchedResource,
                studentTopicGoals = topicGoals
            )
        }

        // Preserve exact real playlist topic when coming from a real playlist; otherwise apply mastery gatekeeping
        val topic = if (matchedBinding?.isFromRealPlaylist == true && verifiedVideo != null) {
            verifiedVideo.second.topicTitle.ifBlank { rawTopic }
        } else {
            BookCampManager.enforcePrerequisiteFoundationGate(
                subject = subject,
                requestedTopic = rawTopic,
                studentTopicGoals = topicGoals
            ).effectiveTopic
        }

        val baseKey = planTask?.id ?: "day_${dayIso}_block_${blockIdx}_${subject}"
        val fallbackEpisode = verifiedVideo?.second ?: CampDayEpisode(dayNumber = dayIso, topicTitle = topic)
        val effectiveCampTitle = verifiedVideo?.first ?: matchedResource?.title?.ifBlank { subject } ?: subject

        // Node 1 (ONLY when verified real video support exists for this book/task!)
        if (verifiedVideo != null) {
            val (campTitle, episode) = verifiedVideo
            val videoNodeId = "${baseKey}_video"
            val bookPrefix = matchedResource?.title?.takeIf {
                it.isNotBlank() && !campTitle.contains(it, ignoreCase = true)
            }?.let { "$it • " }.orEmpty()
            nodes.add(
                DuolingoPathNode(
                    id = videoNodeId,
                    stepIndex = stepCounter++,
                    kind = DuolingoNodeKind.VIDEO_CAMP,
                    subject = subject,
                    topic = episode.topicTitle.ifBlank { topic },
                    subtitle = "$bookPrefix$campTitle • ${episode.dayNumber}. Ders Videosu",
                    campTitle = campTitle,
                    campEpisode = episode,
                    linkedPlanTask = planTask,
                    linkedResource = matchedResource,
                    isCompleted = completedNodeIds.contains(videoNodeId) || (planTask?.isCompleted == true)
                )
            )
        }

        // Practice Focus & Active Book Unit Step (ALWAYS clearly displays the book title!)
        val practiceNodeId = "${baseKey}_practice"
        val qCount = planTask?.targetQuestions ?: 35
        val resourceSubtitle = if (matchedResource != null) {
            val progressStr = if (matchedResource.totalUnits > 0) {
                " (${matchedResource.completedUnits}/${matchedResource.totalUnits} ${matchedResource.unitType})"
            } else ""
            "Kitap: ${matchedResource.title} • $qCount Soru$progressStr"
        } else {
            "$qCount Soru Çözümü & Odak Oturumu"
        }
        nodes.add(
            DuolingoPathNode(
                id = practiceNodeId,
                stepIndex = stepCounter++,
                kind = DuolingoNodeKind.PRACTICE_FOCUS,
                subject = subject,
                topic = topic,
                subtitle = resourceSubtitle,
                campTitle = effectiveCampTitle,
                campEpisode = fallbackEpisode,
                linkedPlanTask = planTask,
                linkedResource = matchedResource,
                targetQuestions = qCount,
                isCompleted = completedNodeIds.contains(practiceNodeId) || (planTask?.isCompleted == true)
            )
        )

        // Mini Control / Error Review Step
        val reviewNodeId = "${baseKey}_review"
        val reviewSubtitle = if (matchedResource != null) {
            "${matchedResource.title} • Püf Noktaları & Hata Kontrolü"
        } else {
            "ÖSYM Püf Noktaları & Hata Pekiştirme"
        }
        nodes.add(
            DuolingoPathNode(
                id = reviewNodeId,
                stepIndex = stepCounter++,
                kind = DuolingoNodeKind.ERROR_REVIEW,
                subject = subject,
                topic = topic,
                subtitle = reviewSubtitle,
                campTitle = effectiveCampTitle,
                campEpisode = fallbackEpisode,
                linkedPlanTask = planTask,
                linkedResource = matchedResource,
                isCompleted = completedNodeIds.contains(reviewNodeId) || (planTask?.isCompleted == true)
            )
        )
    }

    return nodes
}

private suspend fun rebalanceWeeklyPlanWithAiCoach(
    context: Context,
    db: SharkMentorDatabase,
    libraryResources: List<ResourceEntity>,
    activeResource: ResourceEntity?,
    topicGoals: List<TopicGoalEntity>,
    targetContext: StudentTargetContext
): Int = withContext(Dispatchers.IO) {
    val planDao = db.planDao()
    val folders = planDao.getAllFoldersSync()
    val activeFolder = folders.firstOrNull() ?: PlanFolderEntity(
        id = UUID.randomUUID().toString(),
        title = "AI Koç Dengeli Haftalık Plan"
    ).also { planDao.insertFolder(it) }

    val existingTasks = planDao.getTasksForFolder(activeFolder.id).firstOrNull().orEmpty()
    val uncompleted = existingTasks.filter { !it.isCompleted }

    if (uncompleted.isNotEmpty()) {
        val orderedUncompleted = uncompleted
            .map { task ->
                val gate = BookCampManager.enforcePrerequisiteFoundationGate(
                    subject = task.subject,
                    requestedTopic = task.topic.ifBlank { task.title },
                    studentTopicGoals = topicGoals
                )
                task.copy(
                    title = gate.effectiveTopic,
                    topic = gate.effectiveTopic
                )
            }
            .sortedWith(
                compareBy<PlanEntity> { it.subject }
                    .thenBy { BookCampManager.canonicalTopicIndex(it.subject, it.topic) }
            )

        val todayIso = CoachingEngine.calendarDayToIso(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
        val targetDays = (todayIso..7).toList().ifEmpty { (1..7).toList() }
        val rebalanced = orderedUncompleted.mapIndexed { idx, task ->
            val assignedDay = targetDays[idx % targetDays.size]
            task.copy(dayOfWeek = assignedDay, orderIndex = idx + 1)
        }
        planDao.insertTasks(rebalanced)
        return@withContext rebalanced.size
    }

    // If a single book is selected, use its REAL verified playlist if present;
    // otherwise schedule honest book practice blocks without fabricating fake video camps
    val primaryResource = activeResource ?: libraryResources.firstOrNull()
    if (primaryResource != null) {
        val binding = BookCampManager.resolveCampForResource(context, primaryResource, topicGoals)
        if (binding.hasVerifiedVideoSupport && binding.episodes.isNotEmpty()) {
            return@withContext BookCampManager.distributeCampToWeeklyPlan(context, binding, targetQuestionsPerDay = 40)
        }
        val canonicalTopics = BookCampManager.canonicalCurriculumForSubject(primaryResource.subject, primaryResource.title)
        val topics = if (canonicalTopics.isNotEmpty()) {
            BookCampManager.expandTopicsToDaysWithMastery(
                subject = primaryResource.subject,
                topics = canonicalTopics,
                totalDays = 7,
                studentTopicGoals = topicGoals
            )
        } else {
            (1..7).map { "${primaryResource.title} • ${primaryResource.completedUnits + it}. ${primaryResource.unitType}" }
        }
        val bookTasks = (1..7).map { day ->
            val t = topics.getOrElse(day - 1) { "${primaryResource.title} • $day. Bölüm" }
            val label = "${primaryResource.title}: $t"
            PlanEntity(
                id = UUID.randomUUID().toString(),
                folderId = activeFolder.id,
                title = label,
                subject = primaryResource.subject,
                topic = label,
                dayOfWeek = day,
                scheduledTime = "10:00 - 11:30",
                targetQuestions = 40,
                isCompleted = false,
                orderIndex = day
            )
        }
        planDao.insertTasks(bookTasks)
        return@withContext bookTasks.size
    }

    val primarySubject = CoachingEngine.defaultFocusTitlesForExam(targetContext.examType).firstOrNull() ?: "TYT Matematik"
    val canonicalTopics = BookCampManager.canonicalCurriculumForSubject(primarySubject)
    val weightedTopics = BookCampManager.expandTopicsToDaysWithMastery(
        subject = primarySubject,
        topics = canonicalTopics,
        totalDays = 7,
        studentTopicGoals = topicGoals
    )

    val generated = mutableListOf<PlanEntity>()
    for (day in 1..7) {
        val rawTopic = weightedTopics.getOrElse(day - 1) { "$primarySubject • $day. Gün" }
        val gatedTopic = BookCampManager.enforcePrerequisiteFoundationGate(
            subject = primarySubject,
            requestedTopic = rawTopic,
            studentTopicGoals = topicGoals
        ).effectiveTopic
        val topicLabel = "$primarySubject: $gatedTopic"
        generated.add(
            PlanEntity(
                id = UUID.randomUUID().toString(),
                folderId = activeFolder.id,
                title = topicLabel,
                subject = primarySubject,
                topic = topicLabel,
                dayOfWeek = day,
                scheduledTime = "10:00 - 11:30",
                targetQuestions = 40,
                isCompleted = false,
                orderIndex = day
            )
        )
    }
    planDao.insertTasks(generated)
    generated.size
}

private const val PATH_PREFS = "shark_mark57_duolingo_path_prefs"
private const val KEY_COMPLETED_NODES = "completed_node_ids"

private fun loadCompletedNodeIds(context: Context): Set<String> {
    val sp = context.applicationContext.getSharedPreferences(PATH_PREFS, Context.MODE_PRIVATE)
    return sp.getStringSet(KEY_COMPLETED_NODES, emptySet())?.toSet() ?: emptySet()
}

private fun saveCompletedNodeIds(context: Context, ids: Set<String>) {
    val sp = context.applicationContext.getSharedPreferences(PATH_PREFS, Context.MODE_PRIVATE)
    sp.edit().putStringSet(KEY_COMPLETED_NODES, ids).apply()
}
