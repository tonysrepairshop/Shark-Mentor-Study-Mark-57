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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.sharkmentor.core.coaching.AiCoachLedger
import com.example.sharkmentor.core.coaching.AiCoachLedgerManager
import com.example.sharkmentor.core.coaching.AppStudyMode
import com.example.sharkmentor.core.coaching.BookCampManager
import com.example.sharkmentor.core.coaching.BookCoachingAssignment
import com.example.sharkmentor.core.coaching.BookCoachingRole
import com.example.sharkmentor.core.coaching.BookCoachingRoleManager
import com.example.sharkmentor.core.coaching.CampDayEpisode
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.coaching.DailyGoalLedgerEntry
import com.example.sharkmentor.core.coaching.DailyGoalStatus
import com.example.sharkmentor.core.coaching.DossierAiChannel
import com.example.sharkmentor.core.coaching.PlanProgressAuditor
import com.example.sharkmentor.core.coaching.PlanReadinessNavBus
import com.example.sharkmentor.core.coaching.PlanTaskAuditItem
import com.example.sharkmentor.core.coaching.ResourceCampBinding
import com.example.sharkmentor.core.coaching.StudentDossier
import com.example.sharkmentor.core.coaching.StudentDossierManager
import com.example.sharkmentor.core.coaching.StudentDossierNavBus
import com.example.sharkmentor.core.coaching.StudentTargetContext
import com.example.sharkmentor.core.coaching.TopicLifecycleRecord
import com.example.sharkmentor.core.coaching.TopicStudyActionKind
import com.example.sharkmentor.core.coaching.WeeklyVarianceAdvice
import com.example.sharkmentor.core.service.FocusTimerService
import com.example.sharkmentor.core.timer.TimerMode
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.core.timer.TimerState
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.ActivePlanEntity
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.PlanFolderEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.StudyTaskEntity
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.data.repository.PlanRepositoryImpl
import com.example.sharkmentor.domain.engine.DailyFocusEngine
import com.example.sharkmentor.service.sync.SyncWorker
import com.example.sharkmentor.ui.components.RichSolutionContent
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
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
                    listOf(4, 6, 8, 10, 12, 14).forEach { hr ->
                        val isSelected = !useAi && weekdayHoursText == hr.toString()
                        Surface(
                            onClick = {
                                useAi = false
                                weekdayHoursText = hr.toString()
                                weekendHoursText = (hr + 2).coerceAtMost(16).toString()
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
                    onSave(
                        wdMins.coerceIn(30, CoachingEngine.MAX_DAILY_GOAL_MINUTES),
                        weMins.coerceIn(30, CoachingEngine.MAX_DAILY_GOAL_MINUTES),
                        useAi
                    )
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
    onDismiss: () -> Unit,
    allowModeSelection: Boolean = false,
    onSelectMode: (TimerMode) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val selectableModes = remember {
        listOf(TimerMode.STOPWATCH, TimerMode.BLOCK_90, TimerMode.POMODORO)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (allowModeSelection) "Odak Modu & Etiket Seç" else "Çalışma Başlığı Seç",
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (allowModeSelection) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectableModes.forEach { candidate ->
                            val isChosen = candidate == mode
                            val shortLabel = when (candidate) {
                                TimerMode.STOPWATCH -> "Kronometre"
                                TimerMode.BLOCK_90 -> "90 dk"
                                TimerMode.POMODORO -> "Pomodoro"
                                else -> candidate.displayName
                            }
                            Surface(
                                onClick = { onSelectMode(candidate) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChosen) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(
                                    width = if (isChosen) 1.4.dp else 1.dp,
                                    color = if (isChosen) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isChosen) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isChosen) colorScheme.onPrimary else colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
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
            }
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
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(context) {
        CoachingEngine.ensureInitialized(context)
        CoachingEngine.refreshTargetAndLedgerAsync(context)
        StudentDossierManager.ensureLoaded(context)
    }

    val comprehensiveSupportEnabled by CoachingEngine.comprehensiveSupportEnabled.collectAsStateWithLifecycle()
    val targetContext by CoachingEngine.targetContext.collectAsStateWithLifecycle()
    val weekdayGoalMins by CoachingEngine.weekdayGoalMinutes.collectAsStateWithLifecycle()
    val weekendGoalMins by CoachingEngine.weekendGoalMinutes.collectAsStateWithLifecycle()
    val useAiGoal by CoachingEngine.useAiRecommendedGoal.collectAsStateWithLifecycle()
    val customFocusTitles by CoachingEngine.customFocusTitles.collectAsStateWithLifecycle()
    val selectedFocusTitle by CoachingEngine.selectedFocusTitle.collectAsStateWithLifecycle()
    val focusSubNote by CoachingEngine.focusSubNote.collectAsStateWithLifecycle()
    val studentDossier by StudentDossierManager.dossier.collectAsStateWithLifecycle()

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
    var showEthicalConsentDialog by remember { mutableStateOf(false) }
    var showDossierViewerDialog by remember { mutableStateOf(false) }
    var showDeleteDossierConfirm by remember { mutableStateOf(false) }
    var isSyncingDossier by remember { mutableStateOf(false) }

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

    // Ethical & Privacy Consent Gate before activating Real Coaching ("Kapsamlı Destek")
    if (showEthicalConsentDialog) {
        AlertDialog(
            onDismissRequest = { showEthicalConsentDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface,
            title = {
                Text(
                    text = "Öğrenci Dosyası ve Gizlilik Onayı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Kapsamlı Destek (Gerçek Koçluk) özelliğinin sana özel, tutarlı planlar üretebilmesi için cihazında şifreli bir Öğrenci Dosyası oluşturulur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "• Dosyada yalnızca hedeflerin, konu hakimiyetin, kütüphane kaynakların, çalışma saatlerin ve koçluk yanıtların tutulur.\n" +
                            "• Üçüncü taraf yapay zeka modellerine ADIN, E-POSTAN veya KİMLİK BİLGİLERİN ASLA gönderilmez; veriler tamamen anonim olarak yalnızca koçluk, planlama ve Zen Koç işlemlerinde kullanılır.\n" +
                            "• Dosyanı dilediğin an Ayarlar'dan görüntüleyebilir, düzenleyebilir veya kalıcı olarak silebilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        StudentDossierManager.setEthicalConsentAccepted(context, true)
                        CoachingEngine.setComprehensiveSupportEnabled(context, true)
                        showEthicalConsentDialog = false
                        scope.launch {
                            StudentDossierManager.syncFromDatabase(context, force = true)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Onayla ve Etkinleştir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEthicalConsentDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    if (showDeleteDossierConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteDossierConfirm = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colorScheme.surface,
            title = {
                Text(
                    text = "Öğrenci Dosyasını Kalıcı Sil?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.error
                )
            },
            text = {
                Text(
                    text = "Öğrenci Dosyandaki tüm koçluk yanıtları ve gözlemler cihazından kalıcı olarak silinecek ve Kapsamlı Destek kapatılacaktır. Devam etmek istiyor musun?",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        StudentDossierManager.deleteDossier(context)
                        showDeleteDossierConfirm = false
                        Toast.makeText(context, "Öğrenci Dosyası silindi", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Evet, Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDossierConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showDossierViewerDialog) {
        StudentDossierEditorDialog(
            dossier = studentDossier,
            onSaveChange = { qId, newVal, asInsight ->
                StudentDossierManager.updateSingleAnswerOrInsight(context, qId, newVal, asInsight)
                Toast.makeText(context, "Öğrenci Dosyası güncellendi", Toast.LENGTH_SHORT).show()
            },
            onRemoveInsight = { idx ->
                StudentDossierManager.removeInsightAt(context, idx)
            },
            onDismiss = { showDossierViewerDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Kapsamlı Destek Toggle Card (gated by Ethical Consent Dialog)
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
                        if (enabled && !studentDossier.consentAccepted) {
                            showEthicalConsentDialog = true
                        } else {
                            CoachingEngine.setComprehensiveSupportEnabled(context, enabled)
                        }
                    }
                )
            }
        }

        // 1b. Student Dossier Management Card ("Öğrenci Dosyası")
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f)),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Öğrenci Dosyası (Anonim & Şifreli)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        val statusDesc = if (studentDossier.isCompleted) {
                            "Aktif • ${studentDossier.topics.size} konu, ${studentDossier.books.size} kitap, ${studentDossier.answeredQuestionCount}/10 yanıt"
                        } else if (studentDossier.consentAccepted) {
                            "Onay verildi • Koçluk Yolu üzerinden 10 soruyu yanıtla"
                        } else {
                            "Henüz oluşturulmadı"
                        }
                        Text(
                            text = statusDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (studentDossier.isCompleted) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (studentDossier.isCompleted) "Hazır" else "Bekliyor",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (studentDossier.isCompleted) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Günde 2 kez otomatik senkronize edilir. Yalnızca koçluk, plan ekleme ve Zen Koç işlemlerinde adın/kimliğin olmadan paylaşılır.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDossierViewerDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Görüntüle / Düzenle", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            if (!isSyncingDossier) {
                                isSyncingDossier = true
                                scope.launch {
                                    val synced = StudentDossierManager.syncFromDatabase(context, force = true)
                                    isSyncingDossier = false
                                    Toast.makeText(
                                        context,
                                        "Eşitlendi (${synced.topics.size} konu, ${synced.books.size} kitap)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !isSyncingDossier,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (isSyncingDossier) "..." else "Eşitle", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (studentDossier.isCompleted || studentDossier.consentAccepted) {
                        TextButton(
                            onClick = { showDeleteDossierConfirm = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Sil", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorScheme.error)
                        }
                    }
                }
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

@Composable
private fun StudentDossierEditorDialog(
    dossier: StudentDossier,
    onSaveChange: (questionId: String?, newValue: String, asInsight: Boolean) -> Unit,
    onRemoveInsight: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var selectedQuestionId by remember { mutableStateOf<String?>(null) }
    var editInputText by remember { mutableStateOf("") }
    var pendingSaveConfirm by remember { mutableStateOf<Triple<String?, String, Boolean>?>(null) }

    pendingSaveConfirm?.let { (qId, textVal, isInsight) ->
        AlertDialog(
            onDismissRequest = { pendingSaveConfirm = null },
            shape = RoundedCornerShape(18.dp),
            containerColor = colorScheme.surface,
            title = { Text("Değişikliği Onayla", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Öğrenci Dosyana şu bilgi kaydedilecek:\n\n\"${textVal.take(180)}\"\n\nOnaylıyor musun?",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveChange(qId, textVal, isInsight)
                        pendingSaveConfirm = null
                        selectedQuestionId = null
                        editInputText = ""
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSaveConfirm = null }) {
                    Text("İptal")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = colorScheme.surface,
        title = {
            Text(
                text = "Öğrenci Dosyası Özeti",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Hedef: ${dossier.targetHeadline.ifBlank { dossier.examType.ifBlank { "Belirlenmedi" } }}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary
                )
                Text(
                    text = "Günlük Hedef: Hafta içi ${CoachingEngine.formatMinutesShort(dossier.weekdayGoalMinutes)} • Hafta sonu ${CoachingEngine.formatMinutesShort(dossier.weekendGoalMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Kayıtlı Veriler: ${dossier.topics.size} konu (${dossier.weakTopicsCount} zayıf) • ${dossier.books.size} kitap",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "Koçluk Yanıtların (Düzenlemek için dokun)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                StudentDossierManager.INTERVIEW_QUESTIONS.forEach { q ->
                    val ans = dossier.interviewAnswers[q.id].orEmpty()
                    Surface(
                        onClick = {
                            selectedQuestionId = q.id
                            editInputText = ans
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedQuestionId == q.id) colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text(
                                text = "${q.number}. ${q.prompt}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = ans.ifBlank { "Yanıtlanmadı" },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ans.isNotBlank()) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (dossier.coachingInsights.isNotEmpty()) {
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = "Koçluk Notları",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                    dossier.coachingInsights.forEachIndexed { idx, note ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { onRemoveInsight(idx) },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Kaldır", fontSize = 11.sp, color = colorScheme.error)
                            }
                        }
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                OutlinedTextField(
                    value = editInputText,
                    onValueChange = { editInputText = it },
                    label = {
                        Text(
                            if (selectedQuestionId != null) "Seçili Soruyu Güncelle" else "Yeni Koçluk Notu Ekle",
                            fontSize = 12.sp
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (editInputText.isNotBlank()) {
                            pendingSaveConfirm = Triple(selectedQuestionId, editInputText.trim(), selectedQuestionId == null)
                        }
                    },
                    enabled = editInputText.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedQuestionId != null) "Yanıtı Kaydet" else "Dosyaya Not Ekle")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

/**
 * Pure JVM-testable helper returning the other 6 ISO days (1=Mon..7=Sun) excluding [todayIso],
 * ordered chronologically starting from tomorrow and wrapping around the week.
 */
fun buildOtherPreviewDayOrder(todayIso: Int): List<Int> {
    val safeToday = todayIso.coerceIn(1, 7)
    return (1..6).map { offset -> ((safeToday - 1 + offset) % 7) + 1 }
}

/**
 * Pure JVM-testable horizontal bias in [-1f, 1f] for Duolingo winding S-curve map nodes.
 * Produces a smooth organic wave: center -> left -> center -> right -> center.
 */
fun computeMapNodeHorizontalBias(index: Int): Float = when (index % 4) {
    1 -> -0.58f
    3 -> 0.58f
    else -> 0f
}

/**
 * Full-Screen Duolingo-Style Today-Only Interactive Winding Map rendered when
 * [AppStudyMode.REAL_COACHING] ("Koçluk Yolu") is active.
 *
 * - Displays ONLY TODAY's milestones across the entire viewport as an immersive winding map.
 * - Other days of the week are strictly read-only previews via [OtherDaysPreviewDialog]
 *   (full weekly editing/detail remains in the Planlar tab).
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
    onNavigateToSharkAi: () -> Unit = {},
    onNavigateToPlans: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val db = remember(context) { SharkMentorDatabase.getDatabase(context) }

    val studentDossier by StudentDossierManager.dossier.collectAsStateWithLifecycle()
    val efficiencySuggestion by StudentDossierManager.efficiencySuggestion.collectAsStateWithLifecycle()

    LaunchedEffect(context) {
        StudentDossierManager.ensureLoaded(context)
        BookCampManager.ensureLoaded(context)
    }

    // ── Real Coaching Onboarding Gate: NEVER display fake/nonsense plans until Student Dossier is built! ──
    if (!studentDossier.isCompleted) {
        Column(
            modifier = modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StudyModeHeaderSwitcher(
                currentMode = AppStudyMode.REAL_COACHING,
                onModeChange = onSwitchMode
            )

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.2.dp, colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Text(
                        text = "Gerçek Koçluk İçin Önce Seni Tanıyalım",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Sana ezbere veya anlamsız bir program atamak istemiyoruz. Programının dengeli, kararlı ve gerçekten verimli olması için önce Shark AI üzerinden 10 kısa soruyu yanıtlayarak anonim Öğrenci Dosyanı oluşturalım.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surface.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Öğrenci Dosyanda Neler Yer Alır?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                            Text(
                                text = "• Hedef sınavın, sıralaman ve günlük çalışma saatin\n" +
                                    "• Konu hakimiyeti seviyelerin ve kütüphanendeki kitapların\n" +
                                    "• Verimli çalışma saatlerin, okul/kurs tempon ve tercihlerin\n" +
                                    "• Cihazında şifreli saklanır; adın/kimliğin olmadan anonim işlenir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            StudentDossierNavBus.requestOpenInterview()
                            onNavigateToSharkAi()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Shark AI'da Öğrenci Dosyamı Oluştur",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        return
    }

    // Main map is ALWAYS locked strictly to TODAY (no day-by-day split tabs!)
    val todayIso = remember {
        CoachingEngine.calendarDayToIso(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    }
    val otherPreviewDays = remember(todayIso) { buildOtherPreviewDayOrder(todayIso) }

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
    val bookAssignments by BookCoachingRoleManager.assignmentsByResourceId.collectAsStateWithLifecycle()
    val coachLedger by AiCoachLedgerManager.ledger.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        BookCampManager.ensureLoaded(context)
        BookCoachingRoleManager.ensureLoaded(context)
        AiCoachLedgerManager.ensureLoaded(context)
        AiCoachLedgerManager.syncWithErrorBank(context)
    }

    var completedNodeIds by remember { mutableStateOf(loadCompletedNodeIds(context)) }
    var activeStudioNode by remember { mutableStateOf<DuolingoPathNode?>(null) }
    var activePracticeNode by remember { mutableStateOf<DuolingoPathNode?>(null) }
    var activeMiniCheckNode by remember { mutableStateOf<DuolingoPathNode?>(null) }
    var isRebalancingWeek by remember { mutableStateOf(false) }
    var showPlanReadinessDialog by remember { mutableStateOf(false) }
    var isSundayCheckInPrompt by remember { mutableStateOf(false) }
    var showSundayBanner by remember { mutableStateOf(PlanReadinessNavBus.shouldShowSundayBanner(context)) }
    var showOtherDaysPreview by remember { mutableStateOf(false) }

    val triggerRebalanceNow: () -> Unit = {
        if (!isRebalancingWeek) {
            isRebalancingWeek = true
            scope.launch {
                val count = rebalanceWeeklyPlanWithAiCoach(
                    context = context,
                    db = db,
                    libraryResources = libraryResources,
                    activeResource = null,
                    topicGoals = topicGoals,
                    targetContext = targetContext
                )
                completedNodeIds = emptySet()
                saveCompletedNodeIds(context, emptySet())
                isRebalancingWeek = false
                showSundayBanner = false
                Toast.makeText(
                    context,
                    "Haftalık program kitap rollerine ve 1-3-7 tekrar takvimine göre kurgulandı ($count görev)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Quietly verify YouTube playlist ONLY for active CONCEPT_VIDEO resources that have NOT opted out of video
    LaunchedEffect(libraryResources, bookAssignments) {
        libraryResources
            .filter { !it.isDeleted }
            .forEach { res ->
                val binding = BookCampManager.resolveCampForResource(context, res)
                val assign = BookCoachingRoleManager.resolveAssignment(res, bookAssignments, binding.hasVerifiedVideoSupport)
                if (assign.role == BookCoachingRole.CONCEPT_VIDEO && !assign.videoDisabled && !binding.videoCheckCompleted) {
                    BookCampManager.discoverAndSyncRealCampForResource(context, res)
                }
            }
    }

    // Build TODAY-ONLY interactive map nodes
    val todayTasks = remember(allFolderTasks, todayIso) {
        allFolderTasks.filter { it.dayOfWeek == todayIso }
    }

    val todayNodes = remember(
        todayTasks,
        libraryResources,
        bookAssignments,
        coachLedger,
        topicGoals,
        campBindings,
        todayIso,
        completedNodeIds,
        targetContext
    ) {
        buildDuolingoDayNodes(
            context = context,
            dayIso = todayIso,
            dayTasks = todayTasks,
            libraryResources = libraryResources,
            bookAssignments = bookAssignments,
            coachLedger = coachLedger,
            topicGoals = topicGoals,
            targetContext = targetContext,
            completedNodeIds = completedNodeIds
        )
    }

    // Read-Only Other Days Preview Dialog (strictly preview — detailed management lives in Planlar)
    if (showOtherDaysPreview) {
        OtherDaysPreviewDialog(
            todayIso = todayIso,
            otherDayIsos = otherPreviewDays,
            allFolderTasks = allFolderTasks,
            libraryResources = libraryResources,
            bookAssignments = bookAssignments,
            coachLedger = coachLedger,
            topicGoals = topicGoals,
            targetContext = targetContext,
            completedNodeIds = completedNodeIds,
            onOpenPlansTab = {
                showOtherDaysPreview = false
                onNavigateToPlans()
            },
            onDismiss = { showOtherDaysPreview = false }
        )
    }

    // Pre-Plan & Sunday Topic Mastery + Book Progress Readiness Notification Card
    if (showPlanReadinessDialog) {
        PlanReadinessCheckInDialog(
            title = if (isSundayCheckInPrompt) "Pazar Günü Haftalık Kontrol" else "Plan Öncesi Güncellik Kontrolü",
            subtitle = if (isSundayCheckInPrompt) {
                "Haftanın son günü Pazar. Yeni haftanın esnek planı eksik görevlerini devredip sana tam uyum sağlasın diye konu hakimiyetini ve kitap ilerlemeni güncelle."
            } else {
                "Yeni planın eksik görevlerini devredip tamamlananları tekrar etmeden en sağlıklı şekilde kurgulanması için konu hakimiyetini ve kitaplarında çözdüğün noktaları güncelle."
            },
            confirmButtonLabel = "Güncel, Planı Kurgula",
            secondarySaveOnlyLabel = if (isSundayCheckInPrompt) "Sadece Kaydet" else null,
            onSaveOnly = if (isSundayCheckInPrompt) {
                {
                    showPlanReadinessDialog = false
                    showSundayBanner = false
                    Toast.makeText(context, "Konu hakimiyeti ve kitap verileri eşitlendi", Toast.LENGTH_SHORT).show()
                }
            } else null,
            onConfirmProceed = {
                showPlanReadinessDialog = false
                triggerRebalanceNow()
            },
            onDismiss = { showPlanReadinessDialog = false }
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
                AiCoachLedgerManager.recordTopicStudyInBackground(
                    context = context,
                    subject = node.subject,
                    topic = completedEp.mappedTopic.ifBlank { node.topic },
                    actionKind = if (completedEp.isQuestionSolvingVideo) {
                        TopicStudyActionKind.PRIMARY_PRACTICE
                    } else {
                        TopicStudyActionKind.CONCEPT_STUDY
                    },
                    bookTitle = node.linkedResource?.title.orEmpty(),
                    completedVideoId = completedEp.videoId
                )
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
                        val planRepo = PlanRepositoryImpl(db.planDao(), db.pendingDeletionDao())
                        planRepo.updateTaskCompletion(node.linkedPlanTask.id, true)
                    }
                    val rawTitle = "${node.linkedPlanTask?.topic.orEmpty()} ${node.topic} ${node.subtitle}"
                    val studyActionKind = when {
                        rawTitle.contains("1-3-7", ignoreCase = true) ||
                            rawTitle.contains("Tekrar", ignoreCase = true) ||
                            rawTitle.contains("Aylık", ignoreCase = true) ->
                            TopicStudyActionKind.REVIEW_SESSION
                        else -> TopicStudyActionKind.PRIMARY_PRACTICE
                    }
                    AiCoachLedgerManager.recordTopicStudyInBackground(
                        context = context,
                        subject = node.subject,
                        topic = node.topic,
                        actionKind = studyActionKind,
                        bookTitle = node.linkedResource?.title.orEmpty()
                    )
                    SyncWorker.enqueueImmediateSync(context)
                    StudentDossierManager.syncInBackground(context)
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
                if (node.topic.contains("Tekrar", ignoreCase = true) || node.topic.contains("1-3-7")) {
                    AiCoachLedgerManager.recordTopicStudyInBackground(
                        context = context,
                        subject = node.subject,
                        topic = node.topic,
                        actionKind = TopicStudyActionKind.REVIEW_SESSION,
                        bookTitle = node.linkedResource?.title.orEmpty()
                    )
                }
                activeMiniCheckNode = null
            },
            onDismiss = { activeMiniCheckNode = null }
        )
    }

    val completedCount = todayNodes.count { it.isCompleted }
    val totalNodes = todayNodes.size.coerceAtLeast(1)
    val pathProgressFraction by animateFloatAsState(
        targetValue = completedCount.toFloat() / totalNodes.toFloat(),
        label = "coaching_today_map_progress"
    )
    val firstIncompleteIndex = todayNodes.indexOfFirst { !it.isCompleted }.let {
        if (it == -1) todayNodes.lastIndex else it
    }
    val isMonochromeTheme =
        colorScheme.primary.red == colorScheme.primary.green &&
            colorScheme.primary.green == colorScheme.primary.blue &&
            colorScheme.secondary.red == colorScheme.secondary.green
    val completedAccent = if (isMonochromeTheme) colorScheme.primary else Color(0xFF10B981)
    val allDone = todayNodes.isNotEmpty() && completedCount == todayNodes.size

    val mapBackdropBrush = Brush.verticalGradient(
        colors = listOf(
            colorScheme.primaryContainer.copy(alpha = 0.16f),
            colorScheme.background,
            colorScheme.tertiaryContainer.copy(alpha = 0.12f),
            colorScheme.background
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(mapBackdropBrush),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 1. Compact Floating Top Bar (Mode Switcher + Today HUD) ──
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudyModeHeaderSwitcher(
                currentMode = AppStudyMode.REAL_COACHING,
                onModeChange = onSwitchMode
            )

            // Sleek Single-Row Today Mission HUD (keeps 80%+ of screen pure winding map!)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.2.dp,
                    if (allDone) completedAccent.copy(alpha = 0.5f) else colorScheme.primary.copy(alpha = 0.3f)
                ),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Bugün • ${CoachingEngine.isoDayLabelTr(todayIso)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (allDone) completedAccent.copy(alpha = 0.18f) else colorScheme.primary.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "$completedCount/${todayNodes.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (allDone) completedAccent else colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${goalStatus.formattedWorked()} / ${goalStatus.formattedTarget()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.primary,
                                    modifier = Modifier.clickable { onEditDailyGoal() }
                                )
                                if (targetContext.examType.isNotBlank()) {
                                    Text(
                                        text = "• ${targetContext.examType}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Read-only Preview Other Days button
                            Surface(
                                onClick = { showOtherDaysPreview = true },
                                shape = RoundedCornerShape(10.dp),
                                color = colorScheme.secondaryContainer.copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Diğer Günleri Önizle",
                                        tint = colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Önizle",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            // Rebalance / Plan Kurgula button
                            Surface(
                                onClick = {
                                    if (!isRebalancingWeek) {
                                        isSundayCheckInPrompt = false
                                        showPlanReadinessDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = colorScheme.primary.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Box(
                                    modifier = Modifier.padding(7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isRebalancingWeek) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.8.dp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = "Programı Kurgula",
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { pathProgressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (allDone) completedAccent else colorScheme.primary,
                        trackColor = colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Live Focus Cockpit appears ONLY while timer is actively running/paused
            AnimatedVisibility(
                visible = timerState.timerPhase != TimerPhase.IDLE,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.4.dp, colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                style = MaterialTheme.typography.titleMedium,
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

            // Optional Sunday Check-in Banner (only on Sunday if not dismissed)
            if (showSundayBanner) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pazar Kontrolü: Yeni haftayı kurgula",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    isSundayCheckInPrompt = true
                                    showPlanReadinessDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Güncelle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    PlanReadinessNavBus.dismissSundayBannerToday(context)
                                    showSundayBanner = false
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }

            // Optional Adaptive Efficiency Suggestion (dismissible)
            efficiencySuggestion?.let { suggestion ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = suggestion.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    StudentDossierManager.recordEfficiencyDecision(
                                        context = context,
                                        acceptedLowerHours = true,
                                        newWeekdayMins = suggestion.recommendedWeekdayMinutes,
                                        newWeekendMins = suggestion.recommendedWeekendMinutes
                                    )
                                    Toast.makeText(context, "8-9 saat verimlilik denemesi aktif", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Text("8-9 Saati Dene", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    StudentDossierManager.recordEfficiencyDecision(
                                        context = context,
                                        acceptedLowerHours = false
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Text("Hedefim Kalsın", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── 2. FULL-SCREEN TODAY-ONLY WINDING DUOLINGO MAP ──
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            todayNodes.forEachIndexed { idx, node ->
                val isCurrentActive = idx == firstIncompleteIndex && !node.isCompleted
                val currentBias = computeMapNodeHorizontalBias(idx)
                val nextBias = if (idx < todayNodes.lastIndex) {
                    computeMapNodeHorizontalBias(idx + 1)
                } else {
                    0f
                }

                FullScreenMapMilestoneStation(
                    node = node,
                    stepNumber = idx + 1,
                    horizontalBias = currentBias,
                    isCurrentStep = isCurrentActive,
                    completedAccent = completedAccent,
                    onClick = {
                        when (node.kind) {
                            DuolingoNodeKind.VIDEO_CAMP -> activeStudioNode = node
                            DuolingoNodeKind.PRACTICE_FOCUS -> activePracticeNode = node
                            DuolingoNodeKind.ERROR_REVIEW -> activeMiniCheckNode = node
                        }
                    }
                )

                // Smooth S-Curve Bezier Road Connector to the next milestone (or finish trophy)
                val roadDone = node.isCompleted
                val roadColor = when {
                    roadDone -> completedAccent
                    isCurrentActive -> colorScheme.primary.copy(alpha = 0.78f)
                    else -> colorScheme.outlineVariant.copy(alpha = 0.7f)
                }
                WindingMapBezierConnector(
                    fromBias = currentBias,
                    toBias = nextBias,
                    color = roadColor,
                    isSolid = roadDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )
            }

            // Finish Trophy Station at the end of Today's Map
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (allDone) completedAccent.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(
                    width = if (allDone) 1.8.dp else 1.dp,
                    color = if (allDone) completedAccent else colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth(0.86f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (allDone) completedAccent else colorScheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Günün Hedefi",
                                tint = if (allDone) Color.White else colorScheme.primary,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (allDone) "Günün Rotası Tamamlandı!" else "Günün Bitiş Noktası",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (allDone) completedAccent else colorScheme.onSurface
                        )
                        Text(
                            text = if (allDone) {
                                "Bugünkü tüm ${todayNodes.size} adımı tamamladın. Harika tempo!"
                            } else {
                                "$completedCount / ${todayNodes.size} adım tamamlandı • Kalan: ${todayNodes.size - completedCount}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtle bottom preview & Planlar quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showOtherDaysPreview = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Diğer Günleri Önizle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onNavigateToPlans,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Planlar Sekmesi", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(26.dp))
        }
    }
}

@Composable
private fun WindingMapBezierConnector(
    fromBias: Float,
    toBias: Float,
    color: Color,
    isSolid: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val maxShift = (size.width * 0.24f).coerceAtMost(92.dp.toPx())
        val startX = centerX + (fromBias * maxShift)
        val endX = centerX + (toBias * maxShift)
        val path = Path().apply {
            moveTo(startX, 0f)
            cubicTo(
                startX, size.height * 0.52f,
                endX, size.height * 0.48f,
                endX, size.height
            )
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = if (isSolid) 5.5.dp.toPx() else 4.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (isSolid) null else PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
            )
        )
    }
}

@Composable
private fun FullScreenMapMilestoneStation(
    node: DuolingoPathNode,
    stepNumber: Int,
    horizontalBias: Float,
    isCurrentStep: Boolean,
    completedAccent: Color,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val stepKindAccent = when (node.kind) {
        DuolingoNodeKind.VIDEO_CAMP -> colorScheme.primary
        DuolingoNodeKind.PRACTICE_FOCUS -> colorScheme.tertiary
        DuolingoNodeKind.ERROR_REVIEW -> colorScheme.secondary
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
    val shiftDp = (horizontalBias * 74f).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = shiftDp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Floating Duolingo-style "BAŞLA!" callout over the active next station
        if (isCurrentStep) {
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                color = nodeAccent,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = when (node.kind) {
                        DuolingoNodeKind.VIDEO_CAMP -> "SIRADA • İZLE"
                        DuolingoNodeKind.PRACTICE_FOCUS -> "SIRADA • BAŞLA"
                        DuolingoNodeKind.ERROR_REVIEW -> "SIRADA • KONTROL"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    color = stepKindOnAccent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // Iconic 3D-rimmed Circular Medallion Station
        Box(contentAlignment = Alignment.Center) {
            if (isCurrentStep) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(nodeAccent.copy(alpha = 0.16f))
                        .border(2.dp, nodeAccent.copy(alpha = 0.55f), CircleShape)
                )
            }
            // Subtle 3D bottom lip
            Box(
                modifier = Modifier
                    .offset(y = 4.dp)
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(nodeAccent.copy(alpha = if (node.isCompleted || isCurrentStep) 0.42f else 0.2f))
            )
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = when {
                    node.isCompleted -> completedAccent
                    isCurrentStep -> nodeAccent
                    else -> colorScheme.surface
                },
                border = BorderStroke(
                    width = if (isCurrentStep || node.isCompleted) 2.5.dp else 2.dp,
                    color = nodeAccent
                ),
                shadowElevation = if (isCurrentStep) 6.dp else 2.dp,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (node.isCompleted) Icons.Default.Check else nodeIcon,
                        contentDescription = node.topic,
                        tint = when {
                            node.isCompleted -> Color.White
                            isCurrentStep -> stepKindOnAccent
                            else -> nodeAccent
                        },
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            // Step number badge on top-right of medallion
            Surface(
                shape = CircleShape,
                color = colorScheme.surface,
                border = BorderStroke(1.2.dp, nodeAccent),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$stepNumber",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = nodeAccent
                    )
                }
            }
        }

        // Concise Map Label Pill right below the medallion
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            color = colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(
                width = if (isCurrentStep) 1.4.dp else 1.dp,
                color = if (isCurrentStep) nodeAccent else colorScheme.outlineVariant.copy(alpha = 0.55f)
            ),
            shadowElevation = if (isCurrentStep) 3.dp else 1.dp,
            modifier = Modifier.widthIn(min = 176.dp, max = 236.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = node.kind.badgeLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = nodeAccent,
                    maxLines = 1
                )
                Text(
                    text = "${node.subject} • ${node.topic}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = node.subtitle,
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Read-only Preview Dialog for the other 6 days of the week.
 * Keeps the main Coaching Path screen 100% focused on TODAY'S map while letting the student
 * quickly glance at upcoming days or jump directly into the detailed Planlar tab.
 */
@Composable
private fun OtherDaysPreviewDialog(
    todayIso: Int,
    otherDayIsos: List<Int>,
    allFolderTasks: List<PlanEntity>,
    libraryResources: List<ResourceEntity>,
    bookAssignments: Map<Long, BookCoachingAssignment>,
    coachLedger: AiCoachLedger,
    topicGoals: List<TopicGoalEntity>,
    targetContext: StudentTargetContext,
    completedNodeIds: Set<String>,
    onOpenPlansTab: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var previewDayIso by remember(otherDayIsos) { mutableIntStateOf(otherDayIsos.firstOrNull() ?: 1) }

    val previewTasks = remember(allFolderTasks, previewDayIso) {
        allFolderTasks.filter { it.dayOfWeek == previewDayIso }
    }
    val previewNodes = remember(
        previewTasks,
        libraryResources,
        bookAssignments,
        coachLedger,
        topicGoals,
        previewDayIso,
        completedNodeIds,
        targetContext
    ) {
        buildDuolingoDayNodes(
            context = context,
            dayIso = previewDayIso,
            dayTasks = previewTasks,
            libraryResources = libraryResources,
            bookAssignments = bookAssignments,
            coachLedger = coachLedger,
            topicGoals = topicGoals,
            targetContext = targetContext,
            completedNodeIds = completedNodeIds
        )
    }

    val tomorrowIso = otherDayIsos.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Diğer Günlerin Önizlemesi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Sadece önizleme • Detaylı düzenleme Planlar sekmesinde",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Other-6-days selector chips (starting with tomorrow)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    otherDayIsos.forEach { dayIso ->
                        val isSel = dayIso == previewDayIso
                        val shortName = CoachingEngine.isoDayLabelTr(dayIso).take(3)
                        val chipText = if (dayIso == tomorrowIso) "Yarın ($shortName)" else shortName
                        Surface(
                            onClick = { previewDayIso = dayIso },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(
                                1.dp,
                                if (isSel) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Text(
                                text = chipText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSel) colorScheme.onPrimary else colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${CoachingEngine.isoDayLabelTr(previewDayIso)} (${previewNodes.size} adım)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    previewNodes.forEachIndexed { idx, node ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.primary.copy(alpha = 0.14f),
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${idx + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.primary
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${node.subject} • ${node.topic}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${node.kind.badgeLabel} • ${node.subtitle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenPlansTab,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Planlar Sekmesine Git", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bugüne Dön")
            }
        }
    )
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
        StudentDossierManager.ensureLoaded(context)
        val compass = if (StudentDossierManager.shouldAttachDossier(DossierAiChannel.COACHING_DIRECT)) {
            StudentDossierManager.buildAnonymizedDossierPrompt()
        } else {
            CoachingEngine.buildAiSystemCompassPrompt(context)
        }
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

private fun resolveBookForTask(
    subject: String,
    rawTopic: String,
    taskTitle: String,
    libraryResources: List<ResourceEntity>,
    bookAssignments: Map<Long, BookCoachingAssignment>,
    verifiedVideoIds: Set<Long>
): Pair<ResourceEntity, BookCoachingAssignment>? {
    val activeForSubject = BookCoachingRoleManager.getActiveBooksForSubject(
        subject = subject,
        resources = libraryResources,
        assignments = bookAssignments,
        verifiedVideoResourceIds = verifiedVideoIds
    )
    if (activeForSubject.isEmpty()) return null

    // 1. Exact title prefix match (e.g. "Aydın AYT Kimya: Modern Atom Teorisi" or "Ferrum AYT Kimya: ...")
    val combinedText = "$rawTopic $taskTitle".trim()
    val prefixBeforeColon = rawTopic.substringBefore(":").trim()
    val directMatch = activeForSubject.firstOrNull { (res, _) ->
        (prefixBeforeColon.length >= 3 && res.title.equals(prefixBeforeColon, ignoreCase = true)) ||
            (res.title.length >= 4 && combinedText.contains(res.title, ignoreCase = true))
    }
    if (directMatch != null) return directMatch

    // 2. Role tag match
    val lower = combinedText.lowercase(Locale("tr"))
    val desiredRole = when {
        lower.contains("1-3-7") || lower.contains("tekrar") || lower.contains("aylık") || lower.contains("aylik") ->
            BookCoachingRole.REVISION_BOOK
        lower.contains("deneme") || lower.contains("branş") || lower.contains("brans") ->
            BookCoachingRole.MOCK_EXAM
        lower.contains("soru pekiştirme") || lower.contains("soru pekistirme") || lower.contains("[ilerleme]") ->
            BookCoachingRole.PRIMARY_PRACTICE
        lower.contains("konu anlat") || lower.contains("video") || lower.contains("kamp") ->
            BookCoachingRole.CONCEPT_VIDEO
        else -> BookCoachingRole.PRIMARY_PRACTICE
    }
    return activeForSubject.firstOrNull { (_, a) -> a.role == desiredRole }
        ?: activeForSubject.firstOrNull { (_, a) -> a.role == BookCoachingRole.PRIMARY_PRACTICE }
        ?: activeForSubject.firstOrNull()
}

private fun buildDuolingoDayNodes(
    context: Context,
    dayIso: Int,
    dayTasks: List<PlanEntity>,
    libraryResources: List<ResourceEntity>,
    bookAssignments: Map<Long, BookCoachingAssignment>,
    coachLedger: AiCoachLedger,
    topicGoals: List<TopicGoalEntity>,
    targetContext: StudentTargetContext,
    completedNodeIds: Set<String>
): List<DuolingoPathNode> {
    val nodes = mutableListOf<DuolingoPathNode>()
    val todayIso = CoachingEngine.calendarDayToIso(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    val dayOffsetFromToday = (dayIso - todayIso).coerceAtLeast(0)
    val allBindings = BookCampManager.bindingsByResourceId.value
    val verifiedIds = allBindings.filterValues { it.hasVerifiedVideoSupport }.keys

    // 1. Build day's blocks (from active weekly plan first; otherwise balanced fallback from 1-3-7 due reviews + active non-passive books)
    val rawBlocks: List<Triple<String, String, PlanEntity?>> = when {
        dayTasks.isNotEmpty() -> {
            dayTasks
                .sortedBy { it.orderIndex }
                .map { task ->
                    Triple(task.subject, task.topic.ifBlank { task.title }, task)
                }
        }
        else -> {
            val dueReviews = AiCoachLedgerManager.getDueReviewTopics(
                ledger = coachLedger,
                horizonEndEpochDay = LocalDate.now().plusDays(dayOffsetFromToday.toLong()).toEpochDay()
            )
            val fallbackList = mutableListOf<Triple<String, String, PlanEntity?>>()
            dueReviews.firstOrNull()?.let { rev ->
                val revBook = BookCoachingRoleManager.getRevisionBooksForSubject(
                    rev.subject, libraryResources, bookAssignments, verifiedIds
                ).firstOrNull()?.first
                val errTag = if (rev.linkedErrorCount > 0) " • Hata Bankası: ${rev.linkedErrorCount} soru" else ""
                val title = if (revBook != null) {
                    "${revBook.title}: ${rev.topic} [${rev.currentIntervalLabel()}$errTag]"
                } else {
                    "${rev.topic} [${rev.currentIntervalLabel()}$errTag]"
                }
                fallbackList.add(Triple(rev.subject, title, null))
            }

            val activeBooks = libraryResources
                .filter { !it.isDeleted }
                .map { res -> res to BookCoachingRoleManager.resolveAssignment(res, bookAssignments, res.id in verifiedIds) }
                .filter { (_, a) -> a.role != BookCoachingRole.PASSIVE }
                .sortedWith(compareBy<Pair<ResourceEntity, BookCoachingAssignment>> { it.second.role.ordinal }.thenBy { it.second.priorityOrder })

            if (activeBooks.isNotEmpty()) {
                val chosenPair = activeBooks[(dayIso - 1).coerceAtLeast(0) % activeBooks.size]
                val res = chosenPair.first
                val assign = chosenPair.second
                val binding = BookCampManager.resolveCampForResource(context, res, topicGoals)
                if (assign.role == BookCoachingRole.CONCEPT_VIDEO && !assign.videoDisabled && binding.hasVerifiedVideoSupport && binding.episodes.isNotEmpty()) {
                    val epDay = (binding.currentDay + dayOffsetFromToday).coerceIn(1, binding.episodes.size)
                    val ep = binding.episodes.firstOrNull { it.dayNumber == epDay } ?: binding.currentEpisode()
                    fallbackList.add(Triple(res.subject, "${res.title}: ${ep.topicTitle} [Konu Anlatım]", null))
                } else {
                    val subjectGoalTopics = topicGoals
                        .filter { !it.isDeleted && it.subject.equals(res.subject, ignoreCase = true) }
                        .sortedWith(compareBy<TopicGoalEntity> { it.currentLevel }.thenBy { BookCampManager.canonicalTopicIndex(it.subject, it.topic) })
                        .map { it.topic }
                    val baseTopics = subjectGoalTopics.ifEmpty {
                        BookCampManager.canonicalCurriculumForSubject(res.subject, res.title)
                    }
                    val unitIndex = (res.completedUnits + dayOffsetFromToday).coerceAtLeast(0)
                    val topicName = if (baseTopics.isNotEmpty()) {
                        baseTopics[unitIndex % baseTopics.size]
                    } else {
                        "${unitIndex + 1}. ${res.unitType}"
                    }
                    fallbackList.add(Triple(res.subject, "${res.title}: $topicName [${assign.role.shortBadge}]", null))
                }
            } else if (fallbackList.isEmpty()) {
                val defaultSubject = CoachingEngine.defaultFocusTitlesForExam(targetContext.examType).firstOrNull() ?: "TYT Matematik"
                val canonical = BookCampManager.canonicalCurriculumForSubject(defaultSubject)
                val topicForDay = if (canonical.isNotEmpty()) {
                    canonical[(dayIso - 1).coerceAtLeast(0) % canonical.size]
                } else {
                    "$defaultSubject Soru & Konu Çalışması"
                }
                fallbackList.add(Triple(defaultSubject, topicForDay, null))
            }
            fallbackList
        }
    }

    var stepCounter = 1
    rawBlocks.forEachIndexed { blockIdx, (subject, rawTopic, planTask) ->
        val matchedPair = resolveBookForTask(
            subject = subject,
            rawTopic = rawTopic,
            taskTitle = planTask?.title.orEmpty(),
            libraryResources = libraryResources,
            bookAssignments = bookAssignments,
            verifiedVideoIds = verifiedIds
        )
        val matchedResource = matchedPair?.first
        val matchedAssignment = matchedPair?.second
        val lowerTask = "$rawTopic ${planTask?.title.orEmpty()}".lowercase(Locale("tr"))
        val isExplicitPracticeOrReview = lowerTask.contains("soru pekiştirme") ||
            lowerTask.contains("soru pekistirme") ||
            lowerTask.contains("1-3-7") ||
            lowerTask.contains("tekrar") ||
            lowerTask.contains("deneme") ||
            matchedAssignment?.role == BookCoachingRole.PRIMARY_PRACTICE ||
            matchedAssignment?.role == BookCoachingRole.REVISION_BOOK ||
            matchedAssignment?.role == BookCoachingRole.MOCK_EXAM

        val isVideoAllowedForNode = matchedResource != null &&
            matchedAssignment != null &&
            !matchedAssignment.videoDisabled &&
            matchedAssignment.role != BookCoachingRole.PASSIVE &&
            !isExplicitPracticeOrReview

        val matchedBinding = if (isVideoAllowedForNode && matchedResource != null) {
            BookCampManager.resolveCampForResource(context, matchedResource, topicGoals)
        } else null

        val verifiedVideo: Pair<String, CampDayEpisode>? = if (
            isVideoAllowedForNode &&
            matchedBinding != null &&
            matchedBinding.hasVerifiedVideoSupport
        ) {
            val coreTopicForMatch = PlanProgressAuditor.extractCoreTopicName(rawTopic)
            val resolved = BookCampManager.resolveVerifiedCampEpisodeForTask(
                context = context,
                subject = subject,
                topic = coreTopicForMatch,
                taskTitle = planTask?.title.orEmpty(),
                dayHint = dayIso,
                libraryResources = listOfNotNull(matchedResource),
                preferredResource = matchedResource,
                studentTopicGoals = topicGoals
            )
            val ep = resolved?.second ?: matchedBinding.currentEpisode()
            if (ep.videoId.length == 11 || ep.playlistId.isNotBlank()) {
                matchedBinding.campTitle to ep
            } else null
        } else null

        val topic = if (matchedBinding?.isFromRealPlaylist == true && verifiedVideo != null) {
            verifiedVideo.second.topicTitle.ifBlank { rawTopic }
        } else {
            val coreOnly = PlanProgressAuditor.extractCoreTopicName(rawTopic)
            if (rawTopic.contains("[") || rawTopic.contains(":")) rawTopic else {
                BookCampManager.enforcePrerequisiteFoundationGate(
                    subject = subject,
                    requestedTopic = coreOnly,
                    studentTopicGoals = topicGoals
                ).effectiveTopic
            }
        }

        val baseKey = planTask?.id ?: "day_${dayIso}_block_${blockIdx}_${subject}"
        val fallbackEpisode = verifiedVideo?.second ?: CampDayEpisode(dayNumber = dayIso, topicTitle = topic)
        val effectiveCampTitle = verifiedVideo?.first ?: matchedResource?.title?.ifBlank { subject } ?: subject

        // Node 1 (ONLY when this is a video-enabled concept book with a real verified YouTube playlist!)
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

        // Node 2: Practice / Revision / Focus Step (ALWAYS displays the exact assigned book & role!)
        val practiceNodeId = "${baseKey}_practice"
        val qCount = planTask?.targetQuestions ?: 35
        val roleTag = matchedAssignment?.formattedBadge()?.let { "[$it] " }.orEmpty()
        val resourceSubtitle = if (matchedResource != null) {
            val progressStr = if (matchedResource.totalUnits > 0) {
                " (${matchedResource.completedUnits}/${matchedResource.totalUnits} ${matchedResource.unitType})"
            } else ""
            "$roleTag${matchedResource.title} • $qCount Soru$progressStr"
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

        // Node 3: Mini Control / Error Consolidation Step
        val reviewNodeId = "${baseKey}_review"
        val reviewSubtitle = if (matchedResource != null) {
            "${matchedResource.title} • Püf Noktaları & Hata Kontrolü"
        } else {
            "Püf Noktaları & Hata Bankası Kontrolü"
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

data class BalancedPlanTaskSpec(
    val dayOfWeek: Int,
    val subject: String,
    val topicLabel: String,
    val flexibleTimeLabel: String,
    val targetQuestions: Int,
    val isCarryOver: Boolean = false
)

private data class PendingSameWeekPractice(
    val subject: String,
    val topic: String,
    val bookTitle: String,
    val minDayPosition: Int
)

/**
 * Mark 67 Pedagogical Multi-Role 7-Day Plan Builder:
 * 1. STRICTLY EXCLUDES `PASSIVE` books from planning.
 * 2. Priority 1: Carries forward incomplete tasks from previous week FIRST (cumulative foundation protection).
 * 3. Priority 2: Schedules due `1-3-7` & `Aylık` spaced repetition reviews from [coachLedger] paired with
 *    each subject's `REVISION_BOOK` (#1, #2, #3...) and noting linked Error Bank question counts.
 * 4. Priority 3: Schedules pending same-week `PRIMARY_PRACTICE` reinforcement for topics already learned via concept/video.
 * 5. Priority 4: Teaches new unlearned topics using `#1 CONCEPT_VIDEO` AND automatically pairs a SAME-WEEK
 *    reinforcement block from `#1 PRIMARY_PRACTICE` 1–2 days later — never letting a video book flood the week!
 * 6. Uses ONLY flexible duration labels ("Esnek • 90 dk") — zero rigid clock ranges!
 */
fun buildBalancedMultiDayPlanSpecs(
    weekdayMinutes: Int,
    weekendMinutes: Int,
    libraryResources: List<ResourceEntity>,
    topicGoals: List<TopicGoalEntity>,
    examType: String,
    completedTasks: List<PlanTaskAuditItem> = emptyList(),
    incompleteCarryOverTasks: List<PlanTaskAuditItem> = emptyList(),
    startDayIso: Int = 1,
    bookAssignments: Map<Long, BookCoachingAssignment> = emptyMap(),
    coachLedger: AiCoachLedger = AiCoachLedger(),
    todayEpochDay: Long = LocalDate.now().toEpochDay(),
    verifiedVideoBindings: Map<Long, ResourceCampBinding> = emptyMap()
): List<BalancedPlanTaskSpec> {
    val safeWd = weekdayMinutes.coerceIn(60, CoachingEngine.MAX_DAILY_GOAL_MINUTES)
    val safeWe = weekendMinutes.coerceIn(60, CoachingEngine.MAX_DAILY_GOAL_MINUTES)
    val verifiedVideoIds = verifiedVideoBindings.filterValues { it.hasVerifiedVideoSupport }.keys

    // Filter out deleted and PASSIVE books completely!
    val activeBookPairs = libraryResources
        .filter { !it.isDeleted }
        .map { res ->
            res to BookCoachingRoleManager.resolveAssignment(res, bookAssignments, res.id in verifiedVideoIds)
        }
        .filter { (_, assign) -> assign.role != BookCoachingRole.PASSIVE }
    val activeResources = activeBookPairs.map { it.first }

    // Collect distinct subjects from incomplete carry-overs + due 1-3-7 reviews + active (non-passive) books + weak topics
    val subjectPool = LinkedHashSet<String>()
    incompleteCarryOverTasks.forEach { if (it.subject.isNotBlank()) subjectPool.add(it.subject.trim()) }
    val dueReviews = AiCoachLedgerManager.getDueReviewTopics(coachLedger, horizonEndEpochDay = todayEpochDay + 6L)
    dueReviews.forEach { if (it.subject.isNotBlank()) subjectPool.add(it.subject.trim()) }
    val pendingPracticeFromLedger = AiCoachLedgerManager.getTopicsNeedingSameWeekPractice(coachLedger)
    pendingPracticeFromLedger.forEach { if (it.subject.isNotBlank()) subjectPool.add(it.subject.trim()) }
    activeResources.forEach { if (it.subject.isNotBlank()) subjectPool.add(it.subject.trim()) }
    topicGoals
        .sortedBy { it.currentLevel }
        .forEach { if (it.subject.isNotBlank()) subjectPool.add(it.subject.trim()) }
    if (subjectPool.size < 3) {
        val upper = examType.uppercase(Locale.ROOT)
        val defaults = when {
            upper.contains("LGS") -> listOf("LGS Matematik", "LGS Fen Bilimleri", "LGS Türkçe", "LGS İnkılap")
            upper.contains("KPSS") -> listOf("KPSS Matematik", "KPSS Türkçe", "KPSS Tarih", "KPSS Coğrafya")
            upper.contains("TUS") -> listOf("TUS Anatomi", "TUS Biyokimya", "TUS Dahiliye", "TUS Patoloji")
            else -> listOf("TYT Matematik", "TYT Türkçe", "AYT Matematik", "AYT Fizik", "AYT Kimya", "AYT Biyoloji")
        }
        defaults.forEach { subjectPool.add(it) }
    }
    val subjects = subjectPool.toList()

    // Priority 1: Interleave incomplete carry-over tasks across subjects (canonical order inside each subject)
    val carryOverBuckets = incompleteCarryOverTasks
        .distinctBy { "${PlanProgressAuditor.normalizeTrKey(it.subject)}_${PlanProgressAuditor.normalizeTrKey(PlanProgressAuditor.extractCoreTopicName(it.topic))}" }
        .groupBy { it.subject.trim() }
        .values
        .map { bucket ->
            bucket.sortedBy {
                BookCampManager.canonicalTopicIndex(it.subject, PlanProgressAuditor.extractCoreTopicName(it.topic))
            }.toMutableList()
        }
    val carryOverQueue = ArrayDeque<PlanTaskAuditItem>()
    var hasCarryItems = true
    while (hasCarryItems) {
        hasCarryItems = false
        for (bucket in carryOverBuckets) {
            if (bucket.isNotEmpty()) {
                carryOverQueue.addLast(bucket.removeAt(0))
                hasCarryItems = true
            }
        }
    }
    val carryOverKeys = carryOverQueue.map {
        "${PlanProgressAuditor.normalizeTrKey(it.subject)}_${PlanProgressAuditor.normalizeTrKey(PlanProgressAuditor.extractCoreTopicName(it.topic))}"
    }.toHashSet()

    // Priority 2: Due 1-3-7 & Monthly Review Queue (assigned to target day position 0..6)
    val dueReviewByDayPos = HashMap<Int, ArrayDeque<TopicLifecycleRecord>>()
    val scheduledReviewKeys = HashSet<String>()
    dueReviews.forEach { rec ->
        val key = "${PlanProgressAuditor.normalizeTrKey(rec.subject)}_${PlanProgressAuditor.normalizeTrKey(rec.topic)}"
        if (!carryOverKeys.contains(key) && scheduledReviewKeys.add(key)) {
            val dayPos = (rec.nextReviewEpochDay - todayEpochDay).toInt().coerceIn(0, 6)
            dueReviewByDayPos.getOrPut(dayPos) { ArrayDeque() }.addLast(rec)
        }
    }

    // Priority 3: Same-Week Primary Practice Queue (seeded from ledger topics already learned via concept/video)
    val sameWeekPracticeQueue = mutableListOf<PendingSameWeekPractice>()
    pendingPracticeFromLedger.forEach { rec ->
        val primaryBook = BookCoachingRoleManager.getPrimaryPracticeBooksForSubject(
            rec.subject, activeResources, bookAssignments, verifiedVideoIds
        ).firstOrNull()?.first
        sameWeekPracticeQueue.add(
            PendingSameWeekPractice(
                subject = rec.subject,
                topic = rec.topic,
                bookTitle = primaryBook?.title.orEmpty(),
                minDayPosition = 0
            )
        )
    }

    // Priority 4: Fresh topic queues per subject (excluding carry-overs, due reviews, completed strong topics, AND already concept-learned topics in ledger!)
    val topicQueueBySubject = subjects.associateWith { subj ->
        val weakForSubj = topicGoals
            .filter { !it.isDeleted && it.subject.equals(subj, ignoreCase = true) && it.currentLevel <= 3 }
            .sortedWith(
                compareBy<TopicGoalEntity> { it.currentLevel }
                    .thenBy { BookCampManager.canonicalTopicIndex(subj, it.topic) }
            )
            .map { it.topic }
        val canonical = BookCampManager.canonicalCurriculumForSubject(subj)
        val mergedAll = (weakForSubj + canonical).distinct()
        val nonRepeated = mergedAll.filter { candidate ->
            val key = "${PlanProgressAuditor.normalizeTrKey(subj)}_${PlanProgressAuditor.normalizeTrKey(candidate)}"
            !carryOverKeys.contains(key) &&
                !scheduledReviewKeys.contains(key) &&
                !AiCoachLedgerManager.isTopicAlreadyConceptLearned(subj, candidate, coachLedger) &&
                !PlanProgressAuditor.isTopicAlreadyCompletedInPlan(
                    subject = subj,
                    topic = candidate,
                    completedTasks = completedTasks,
                    topicGoals = topicGoals
                )
        }
        when {
            nonRepeated.isNotEmpty() -> nonRepeated
            mergedAll.isNotEmpty() -> mergedAll.filterNot { candidate ->
                PlanProgressAuditor.isTopicAlreadyCompletedInPlan(subj, candidate, completedTasks, topicGoals)
            }.ifEmpty { mergedAll }
            else -> listOf("$subj Yeni Adım & Soru Çözümü", "$subj Pekiştirme", "$subj Genel Deneme")
        }
    }

    val cursorBySubject = HashMap<String, Int>()
    val conceptBlocksThisWeekBySubj = HashMap<String, Int>()
    val result = mutableListOf<BalancedPlanTaskSpec>()
    var globalSlot = 0
    val safeStartDay = startDayIso.coerceIn(1, 7)
    val orderedDays = (0..6).map { offset -> ((safeStartDay - 1 + offset) % 7) + 1 }

    for ((dayPos, day) in orderedDays.withIndex()) {
        val isWeekend = day == 6 || day == 7
        val dayTargetMins = if (isWeekend) safeWe else safeWd
        val blockCount = (dayTargetMins / 90).coerceIn(2, 11)
        val minsPerBlock = (dayTargetMins / blockCount).coerceIn(45, 120)
        val flexLabel = "Esnek • $minsPerBlock dk"

        for (slotIdx in 0 until blockCount) {
            // Tier 1: Incomplete carry-over tasks FIRST
            if (carryOverQueue.isNotEmpty()) {
                val missed = carryOverQueue.removeFirst()
                val rawCoreTopic = PlanProgressAuditor.extractCoreTopicName(missed.topic)
                val gatedTopic = BookCampManager.enforcePrerequisiteFoundationGate(
                    subject = missed.subject,
                    requestedTopic = rawCoreTopic,
                    studentTopicGoals = topicGoals
                ).effectiveTopic
                val matchingBook = BookCoachingRoleManager.getPrimaryPracticeBooksForSubject(
                    missed.subject, activeResources, bookAssignments, verifiedVideoIds
                ).firstOrNull()?.first
                    ?: BookCoachingRoleManager.getActiveBooksForSubject(
                        missed.subject, activeResources, bookAssignments, verifiedVideoIds
                    ).firstOrNull()?.first
                val label = when {
                    missed.topic.contains(":") -> missed.topic.trim()
                    matchingBook != null -> "${matchingBook.title}: $gatedTopic"
                    else -> "${missed.subject}: $gatedTopic"
                }
                val qCount = if (missed.targetQuestions >= 30) missed.targetQuestions else if (minsPerBlock >= 90) 40 else 30
                result.add(
                    BalancedPlanTaskSpec(
                        dayOfWeek = day,
                        subject = missed.subject,
                        topicLabel = label,
                        flexibleTimeLabel = flexLabel,
                        targetQuestions = qCount,
                        isCarryOver = true
                    )
                )
                globalSlot++
                continue
            }

            // Tier 2: Due 1-3-7 & Monthly Reviews scheduled for today (or carried forward from earlier dayPos if full)
            val readyReviewBucket = (0..dayPos).firstOrNull { pos -> dueReviewByDayPos[pos]?.isNotEmpty() == true }
            if (readyReviewBucket != null) {
                val rev = dueReviewByDayPos[readyReviewBucket]!!.removeFirst()
                val revisionBooks = BookCoachingRoleManager.getRevisionBooksForSubject(
                    rev.subject, activeResources, bookAssignments, verifiedVideoIds
                )
                val primaryBooks = BookCoachingRoleManager.getPrimaryPracticeBooksForSubject(
                    rev.subject, activeResources, bookAssignments, verifiedVideoIds
                )
                val chosenRevBook = when {
                    revisionBooks.isNotEmpty() -> {
                        val idx = (rev.repetitionCycle - 1).coerceAtLeast(0) % revisionBooks.size
                        revisionBooks[idx].first
                    }
                    primaryBooks.isNotEmpty() -> primaryBooks.first().first
                    else -> null
                }
                val errNote = if (rev.linkedErrorCount > 0) " • Hata Bankası: ${rev.linkedErrorCount} soru" else ""
                val tag = "[${rev.currentIntervalLabel()}$errNote]"
                val label = if (chosenRevBook != null) {
                    "${chosenRevBook.title}: ${rev.topic} $tag"
                } else {
                    "${rev.subject}: ${rev.topic} $tag"
                }
                val qCount = (25 + rev.linkedErrorCount).coerceIn(25, 45)
                result.add(
                    BalancedPlanTaskSpec(
                        dayOfWeek = day,
                        subject = rev.subject,
                        topicLabel = label,
                        flexibleTimeLabel = flexLabel,
                        targetQuestions = qCount,
                        isCarryOver = false
                    )
                )
                globalSlot++
                continue
            }

            // Tier 3: Same-Week Primary Practice Reinforcement (pairs concept learned earlier this week with #1 PRIMARY_PRACTICE book!)
            val readyPracticeIdx = sameWeekPracticeQueue.indexOfFirst { it.minDayPosition <= dayPos }
            if (readyPracticeIdx >= 0) {
                val pending = sameWeekPracticeQueue.removeAt(readyPracticeIdx)
                val label = if (pending.bookTitle.isNotBlank()) {
                    "${pending.bookTitle}: ${pending.topic} [Soru Pekiştirme]"
                } else {
                    "${pending.subject}: ${pending.topic} [Soru Pekiştirme]"
                }
                val qCount = if (minsPerBlock >= 90) 40 else 30
                result.add(
                    BalancedPlanTaskSpec(
                        dayOfWeek = day,
                        subject = pending.subject,
                        topicLabel = label,
                        flexibleTimeLabel = flexLabel,
                        targetQuestions = qCount,
                        isCarryOver = false
                    )
                )
                globalSlot++
                continue
            }

            // Tier 4: Balanced New Topic Progression across subjects using assigned Book Roles
            val subj = subjects[(globalSlot + day - 1) % subjects.size]
            globalSlot++
            val topicList = topicQueueBySubject[subj].orEmpty()
            val curIdx = cursorBySubject[subj] ?: 0
            cursorBySubject[subj] = curIdx + 1
            val rawTopic = topicList.getOrElse(curIdx % topicList.size.coerceAtLeast(1)) { "$subj Etüt" }
            val gatedTopic = BookCampManager.enforcePrerequisiteFoundationGate(
                subject = subj,
                requestedTopic = rawTopic,
                studentTopicGoals = topicGoals
            ).effectiveTopic

            val conceptBook = BookCoachingRoleManager.getConceptBookForSubject(
                subj, activeResources, bookAssignments, verifiedVideoIds
            )?.first
            val primaryBooks = BookCoachingRoleManager.getPrimaryPracticeBooksForSubject(
                subj, activeResources, bookAssignments, verifiedVideoIds
            ).map { it.first }
            val mockBooks = BookCoachingRoleManager.getBooksByRoleForSubject(
                subj, BookCoachingRole.MOCK_EXAM, activeResources, bookAssignments, verifiedVideoIds
            ).map { it.first }

            val conceptUsedCount = conceptBlocksThisWeekBySubj[subj] ?: 0
            val shouldScheduleWeekendMock = isWeekend && mockBooks.isNotEmpty() && slotIdx == blockCount - 1
            val shouldTeachNewConcept = conceptBook != null &&
                !shouldScheduleWeekendMock &&
                dayPos < 6 && // leave day 7 free for reinforcement/review so every concept gets same-week practice!
                (primaryBooks.isEmpty() || conceptUsedCount < 2)

            val label: String
            val qCount: Int
            when {
                shouldScheduleWeekendMock -> {
                    val mockBook = mockBooks[curIdx % mockBooks.size]
                    label = "${mockBook.title}: $subj Branş Denemesi [Deneme]"
                    qCount = 40
                }
                shouldTeachNewConcept && conceptBook != null -> {
                    conceptBlocksThisWeekBySubj[subj] = conceptUsedCount + 1
                    label = "${conceptBook.title}: $gatedTopic [Konu Anlatım]"
                    qCount = if (minsPerBlock >= 90) 25 else 20
                    // AUTOMATICALLY pair SAME-WEEK reinforcement questions from #1 PRIMARY_PRACTICE book 1-2 days later!
                    if (primaryBooks.isNotEmpty()) {
                        sameWeekPracticeQueue.add(
                            PendingSameWeekPractice(
                                subject = subj,
                                topic = gatedTopic,
                                bookTitle = primaryBooks.first().title,
                                minDayPosition = (dayPos + 1).coerceAtMost(6)
                            )
                        )
                    }
                }
                primaryBooks.isNotEmpty() -> {
                    val chosenPrimary = primaryBooks[curIdx % primaryBooks.size]
                    label = "${chosenPrimary.title}: $gatedTopic"
                    qCount = if (minsPerBlock >= 90) 40 else 30
                }
                else -> {
                    val fallbackBook = BookCoachingRoleManager.getActiveBooksForSubject(
                        subj, activeResources, bookAssignments, verifiedVideoIds
                    ).firstOrNull()?.first
                    label = if (fallbackBook != null) {
                        "${fallbackBook.title}: $gatedTopic"
                    } else {
                        "$subj: $gatedTopic"
                    }
                    qCount = if (minsPerBlock >= 90) 40 else 30
                }
            }

            result.add(
                BalancedPlanTaskSpec(
                    dayOfWeek = day,
                    subject = subj,
                    topicLabel = label,
                    flexibleTimeLabel = flexLabel,
                    targetQuestions = qCount,
                    isCarryOver = false
                )
            )
        }
    }
    return result.sortedBy { it.dayOfWeek }
}

/**
 * Rebuilds/rebalances the student's flexible 7-day study plan using the FRESHEST Room data:
 * - Audits all completed vs. incomplete tasks via [PlanProgressAuditor].
 * - Syncs Error Bank & updates the hidden per-UID [AiCoachLedgerManager] (weekly snapshot + monthly rollup).
 * - Applies each book's explicit [BookCoachingRole] & priority (`CONCEPT_VIDEO`, `PRIMARY_PRACTICE`, `REVISION_BOOK`, `MOCK_EXAM`, excluding `PASSIVE`).
 * - Weaves due `1-3-7` & `Aylık` spaced repetition reviews + same-week practice pairs automatically.
 * - Syncs atomically to `planDao`, `studyPlanDao`, Firestore (`PlanRepositoryImpl`), and encrypted `StudentDossierManager`.
 */
suspend fun rebalanceWeeklyPlanWithAiCoach(
    context: Context,
    db: SharkMentorDatabase = SharkMentorDatabase.getDatabase(context),
    libraryResources: List<ResourceEntity> = emptyList(),
    activeResource: ResourceEntity? = null,
    topicGoals: List<TopicGoalEntity> = emptyList(),
    targetContext: StudentTargetContext = CoachingEngine.targetContext.value
): Int = withContext(Dispatchers.IO) {
    CoachingEngine.ensureInitialized(context)
    BookCampManager.ensureLoaded(context)
    BookCoachingRoleManager.ensureLoaded(context)
    AiCoachLedgerManager.ensureLoaded(context)

    val planDao = db.planDao()
    val planRepo = PlanRepositoryImpl(planDao, db.pendingDeletionDao())

    // Always read the freshest topics, resources, and plan audit directly from Room (reflects inline check-in edits immediately!)
    val freshResources = runCatching { db.goalsDao().getAllResourcesRaw() }
        .getOrDefault(libraryResources)
        .ifEmpty { libraryResources }
    val freshTopics = runCatching { db.goalsDao().getAllTopicsRaw() }
        .getOrDefault(topicGoals)
        .ifEmpty { topicGoals }
    val todayIso = CoachingEngine.calendarDayToIso(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    val audit = PlanProgressAuditor.captureFromDatabase(context, todayIsoDay = todayIso)

    // Sync Error Bank & record weekly coaching snapshot + monthly archive rollup into hidden AI Coaching Ledger
    val assignments = BookCoachingRoleManager.assignmentsByResourceId.value
    val verifiedBindings = BookCampManager.bindingsByResourceId.value
    val verifiedIds = verifiedBindings.filterValues { it.hasVerifiedVideoSupport }.keys
    val activeBookTitles = freshResources
        .filter { !it.isDeleted }
        .filter { BookCoachingRoleManager.resolveAssignment(it, assignments, it.id in verifiedIds).role != BookCoachingRole.PASSIVE }
        .map { it.title }
    val updatedLedger = AiCoachLedgerManager.recordWeeklySnapshotAndRollup(
        context = context,
        audit = audit,
        booksUsed = activeBookTitles
    )

    val folders = planDao.getAllFoldersSync()
    val activeFolder = folders.firstOrNull() ?: PlanFolderEntity(
        id = UUID.randomUUID().toString(),
        title = "AI Koç Dengeli Haftalık Plan",
        source = "AI_PLANNER"
    ).also { planRepo.insertFolder(it) }

    val wdMins = CoachingEngine.weekdayGoalMinutes.value
    val weMins = CoachingEngine.weekendGoalMinutes.value
    val effectiveExamType = targetContext.examType.ifBlank {
        CoachingEngine.targetContext.value.examType.ifBlank { "YKS" }
    }

    // Start new week from Monday if rebuilding on Sunday (week-end), else cycle from todayIso
    val startDay = if (todayIso == 7) 1 else todayIso
    val specs = buildBalancedMultiDayPlanSpecs(
        weekdayMinutes = wdMins,
        weekendMinutes = weMins,
        libraryResources = freshResources,
        topicGoals = freshTopics,
        examType = effectiveExamType,
        completedTasks = audit.completedTasks,
        incompleteCarryOverTasks = audit.incompleteTasks,
        startDayIso = startDay,
        bookAssignments = assignments,
        coachLedger = updatedLedger,
        todayEpochDay = LocalDate.now().toEpochDay(),
        verifiedVideoBindings = verifiedBindings
    )

    val generated = specs.mapIndexed { idx, spec ->
        PlanEntity(
            id = UUID.randomUUID().toString(),
            folderId = activeFolder.id,
            title = spec.topicLabel,
            subject = spec.subject,
            topic = spec.topicLabel,
            dayOfWeek = spec.dayOfWeek,
            scheduledTime = spec.flexibleTimeLabel,
            targetQuestions = spec.targetQuestions,
            isCompleted = false,
            orderIndex = idx + 1
        )
    }

    // 1. Atomic Room + Firestore sync via PlanRepositoryImpl (replaces active folder tasks cleanly)
    planRepo.insertAiGeneratedPlan(activeFolder.id, generated)

    // 2. Mirror into StudyPlanDao so StudyPlanScreen & legacy views remain 100% synced
    val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    val activeStudyPlan = ActivePlanEntity(
        id = "CURRENT_ACTIVE_PLAN",
        title = activeFolder.title,
        strategyNote = if (audit.incompleteCount > 0) {
            "${audit.incompleteCount} eksik görev devredildi; kitap rolleri ve 1-3-7 tekrar takvimine göre esnek 7 günlük plan kurgulandı."
        } else {
            "Kitap rolleri, güncel konu hakimiyeti ve 1-3-7 tekrar takvimine göre esnek 7 günlük plan kurgulandı."
        },
        totalTargetQuestions = generated.sumOf { it.targetQuestions },
        updatedAtTimestamp = System.currentTimeMillis()
    )
    val studyTasks = generated.map { p ->
        StudyTaskEntity(
            localId = 0,
            planId = "CURRENT_ACTIVE_PLAN",
            dayName = dayNames.getOrElse(p.dayOfWeek - 1) { "Pazartesi" },
            dayIndex = (p.dayOfWeek - 1).coerceIn(0, 6),
            time = p.scheduledTime,
            subject = p.subject,
            topic = p.topic.ifBlank { p.title },
            goal = "${p.targetQuestions} Soru Hedefi",
            isCompleted = false
        )
    }
    runCatching { db.studyPlanDao().replaceActivePlanWithTasks(activeStudyPlan, studyTasks) }

    // 3. Refresh encrypted Student Dossier & enqueue cloud sync immediately
    PlanReadinessNavBus.markCheckInCompletedToday(context)
    runCatching { StudentDossierManager.syncFromDatabase(context, force = true) }
    CoachingEngine.refreshTargetAndLedgerAsync(context)
    SyncWorker.enqueueImmediateSync(context)

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
