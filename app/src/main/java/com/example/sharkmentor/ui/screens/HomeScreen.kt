package com.example.sharkmentor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharkmentor.R
import com.example.sharkmentor.core.coaching.AppStudyMode
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.timer.TimerMode
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.core.timer.TimerState
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.core.util.TimeUtils
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.domain.engine.DailyFocusEngine
import com.example.sharkmentor.ui.coaching.DailyGoalConfigDialog
import com.example.sharkmentor.ui.coaching.DuolingoCoachingPathView
import com.example.sharkmentor.ui.coaching.FocusTopicLaunchDialog
import com.example.sharkmentor.ui.coaching.StudyModeHeaderSwitcher
import com.example.sharkmentor.ui.theme.SharkMentorTheme
import com.example.sharkmentor.ui.viewmodel.AnalyticsViewModel
import com.example.sharkmentor.ui.viewmodel.TimerViewModel
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Top App Bar Branding
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SharkMentorBrandTitle(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SharkMentor",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "S T U D Y",
            fontSize = 9.sp,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    modifier           : Modifier           = Modifier,
    viewModel          : TimerViewModel     = viewModel(),
    analyticsViewModel : AnalyticsViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        CoachingEngine.ensureInitialized(context)
        CoachingEngine.refreshTargetAndLedgerAsync(context)
    }

    val timerState: TimerState by viewModel.timerState.collectAsStateWithLifecycle()
    val currentPage: Int by viewModel.currentPage.collectAsStateWithLifecycle()
    val analyticsState by analyticsViewModel.uiState.collectAsStateWithLifecycle()
    val dailyScore = analyticsState.dailyScore

    val comprehensiveSupportEnabled by CoachingEngine.comprehensiveSupportEnabled.collectAsStateWithLifecycle()
    val studyMode by CoachingEngine.studyMode.collectAsStateWithLifecycle()
    val targetContext by CoachingEngine.targetContext.collectAsStateWithLifecycle()
    val weekdayGoalMins by CoachingEngine.weekdayGoalMinutes.collectAsStateWithLifecycle()
    val weekendGoalMins by CoachingEngine.weekendGoalMinutes.collectAsStateWithLifecycle()
    val useAiGoal by CoachingEngine.useAiRecommendedGoal.collectAsStateWithLifecycle()
    val customFocusTitles by CoachingEngine.customFocusTitles.collectAsStateWithLifecycle()
    val selectedFocusTitle by CoachingEngine.selectedFocusTitle.collectAsStateWithLifecycle()
    val focusSubNote by CoachingEngine.focusSubNote.collectAsStateWithLifecycle()
    val recentLedger by CoachingEngine.recentLedger.collectAsStateWithLifecycle()
    val weeklyAdvice by CoachingEngine.weeklyVarianceAdvice.collectAsStateWithLifecycle()

    val todayBounds = remember { DailyFocusEngine.getTodayTimeBounds() }
    val todayDbMinutesFlow = remember(context, todayBounds) {
        SharkMentorDatabase.getDatabase(context)
            .sessionDao()
            .getTodayTotalFocusMinutes(todayBounds.first, todayBounds.second)
    }
    val todayDbMinutes by todayDbMinutesFlow.collectAsStateWithLifecycle(initialValue = 0)
    val activeRunningSeconds = if (
        (timerState.timerPhase == TimerPhase.FOCUSING || timerState.timerPhase == TimerPhase.PAUSED) &&
        timerState.currentMode != TimerMode.BREAK
    ) {
        (timerState.timeElapsedMillis / 1000L).coerceAtLeast(0L)
    } else 0L

    val todayTargetMinutes = remember(targetContext, weekdayGoalMins, weekendGoalMins, useAiGoal) {
        CoachingEngine.getTodayTargetMinutes(targetContext)
    }
    val dailyGoalStatus = remember(todayDbMinutes, activeRunningSeconds, todayTargetMinutes) {
        CoachingEngine.computeDailyGoalStatus(
            todayCompletedMinutes = todayDbMinutes ?: 0,
            activeRunningSeconds = activeRunningSeconds,
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

    val pagerState = rememberPagerState(pageCount = { TimerMode.entries.size })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { viewModel.onPageChanged(it) }
    }
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) pagerState.animateScrollToPage(currentPage)
    }

    val isMandatoryBreak = timerState.timerPhase == TimerPhase.MANDATORY_BREAK

    // ── Pre-launch Focus Topic Selection popup & Break confirmation state ─────
    var showBreakConfirmDialog by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<TimerMode?>(null) }
    var pendingFocusLaunchMode by remember { mutableStateOf<TimerMode?>(null) }

    val formattedActiveSubject = remember(selectedFocusTitle, focusSubNote) {
        CoachingEngine.buildFormattedFocusSubject(selectedFocusTitle, focusSubNote)
    }

    pendingFocusLaunchMode?.let { modeToStart ->
        FocusTopicLaunchDialog(
            mode = modeToStart,
            availableTitles = availableFocusTitles,
            customTitles = customFocusTitles,
            selectedTitle = selectedFocusTitle,
            subNote = focusSubNote,
            onSelectTitle = { CoachingEngine.selectFocusTitle(context, it) },
            onSubNoteChange = { CoachingEngine.setFocusSubNote(context, it) },
            onAddCustomTitle = { CoachingEngine.addCustomFocusTitle(context, it) },
            onRemoveCustomTitle = { CoachingEngine.removeCustomFocusTitle(context, it) },
            onConfirmStart = {
                val activeSubj = CoachingEngine.buildFormattedFocusSubject(selectedFocusTitle, focusSubNote)
                viewModel.startTimer(modeToStart, activeSubj)
                pendingFocusLaunchMode = null
            },
            onDismiss = {
                pendingFocusLaunchMode = null
            }
        )
    }

    if (showBreakConfirmDialog && pendingMode != null) {
        BreakConfirmDialog(
            onConfirm = {
                val chosenMode = pendingMode
                showBreakConfirmDialog = false
                pendingMode = null
                if (chosenMode != null) {
                    if (chosenMode == TimerMode.BREAK) {
                        viewModel.startTimer(chosenMode, formattedActiveSubject)
                    } else {
                        pendingFocusLaunchMode = chosenMode
                    }
                }
            },
            onDismiss = {
                showBreakConfirmDialog = false
                pendingMode = null
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        if (comprehensiveSupportEnabled && studyMode == AppStudyMode.REAL_COACHING) {
            DuolingoCoachingPathView(
                timerState = timerState,
                goalStatus = dailyGoalStatus,
                useAiGoal = useAiGoal,
                targetContext = targetContext,
                weeklyAdvice = weeklyAdvice,
                recentLedger = recentLedger,
                onSwitchMode = { CoachingEngine.setStudyMode(context, it) },
                onEditDailyGoal = { showDailyGoalDialog = true },
                onStartFocusWithSubject = { mode, subject ->
                    viewModel.startTimer(mode, subject)
                },
                onPauseFocus = viewModel::pauseTimer,
                onResumeFocus = viewModel::resumeTimer,
                onStopFocus = viewModel::resetTimer
            )
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (comprehensiveSupportEnabled) {
                    StudyModeHeaderSwitcher(
                        currentMode = AppStudyMode.FREE_TRACKING,
                        onModeChange = { CoachingEngine.setStudyMode(context, it) }
                    )
                }

                val isBreakActive by SessionManager.isBreakActive.collectAsStateWithLifecycle()

                // Mola Modu Banner
                AnimatedVisibility(
                    visible = isBreakActive,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Mola Modu",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Dinlenme süresi devam ediyor",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Break banner while mandatory break is running
                AnimatedVisibility(
                    visible = isMandatoryBreak,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                ) {
                    MolaActiveBanner(remainingMillis = timerState.displayMillis, modifier = Modifier.fillMaxWidth())
                }

                // 1. Odak Sayıcıları (Top of HomeScreen)
                ModeIndicatorDots(count = TimerMode.entries.size, currentPage = pagerState.currentPage)

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    key = { page -> TimerMode.entries[page].name },
                ) { page ->
                    val mode = TimerMode.entries[page]
                    if (mode == TimerMode.BREAK) {
                        BreakTimerPage(
                            timerState = timerState,
                            onSetBreakDuration = viewModel::setBreakDuration,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        TimerPage(
                            mode = mode,
                            timerState = timerState,
                            onConfirmPresence = viewModel::confirmPresence,
                            onSetPomodoroDuration = viewModel::setCustomPomodoroDuration,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                val selectedMode = TimerMode.entries[pagerState.currentPage]

                TimerControls(
                    mode = selectedMode,
                    timerState = timerState,
                    onStart = {
                        if (selectedMode == TimerMode.BREAK) {
                            viewModel.startTimer(selectedMode, formattedActiveSubject)
                        } else {
                            pendingFocusLaunchMode = selectedMode
                        }
                    },
                    onPause = viewModel::pauseTimer,
                    onResume = viewModel::resumeTimer,
                    onReset = viewModel::resetTimer,
                    onStartDuringBreak = {
                        pendingMode = selectedMode
                        showBreakConfirmDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )

                if (selectedMode == TimerMode.BLOCK_90 || selectedMode == TimerMode.POMODORO) {
                    ConsecutiveSessionsBadge(count = timerState.completedConsecutiveSessions)
                } else if (selectedMode == TimerMode.BREAK && timerState.isMandatoryBreakRequired) {
                    TextButton(
                        onClick = viewModel::resetConsecutiveSessions,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    ) {
                        Text(stringResource(R.string.btn_skip_session), fontSize = 12.sp)
                    }
                }

                // 2. Günlük Odak Skoru (Below Focus Counters)
                DailyFocusScoreCard(
                    dailyScore = dailyScore,
                    onRefreshScore = { analyticsViewModel.refreshDailyScore() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Break confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BreakConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        title = {
            Text(stringResource(R.string.dialog_break_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Text(
                stringResource(R.string.dialog_break_msg),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
            ) {
                Text(stringResource(R.string.dialog_break_start_session), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            ) {
                Text(
                    stringResource(R.string.dialog_break_continue),
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Active break banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MolaActiveBanner(remainingMillis: Long, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "break_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "break_pulse_alpha",
    )
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .alpha(pulseAlpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        stringResource(R.string.home_break_active),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.home_break_need_confirm),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                text = TimeUtils.formatAdaptiveTime(remainingMillis),
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode indicator dots
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeIndicatorDots(count: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val isSelected = index == currentPage
            val dotColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                tween(250),
                label = "dot_$index"
            )
            val dotSize by animateFloatAsState(if (isSelected) 10f else 6f, tween(250), label = "dot_size_$index")
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(dotSize.dp)
                    .background(dotColor, CircleShape)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Standard timer page — STOPWATCH uses StopwatchChronometer; others use ring
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TimerPage(
    mode                  : TimerMode,
    timerState            : TimerState,
    onConfirmPresence     : () -> Unit,
    onSetPomodoroDuration : (Int) -> Unit,
    modifier              : Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val maxTimerSize = (configuration.screenWidthDp.dp * 0.55f).coerceAtMost(260.dp)

    val isThisModeActive = timerState.currentMode == mode
    val isThisModeTicking = isThisModeActive && (timerState.timerPhase == TimerPhase.FOCUSING || timerState.isTicking)
    val isThisModePaused = isThisModeActive && timerState.timerPhase == TimerPhase.PAUSED

    var showDurationPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(mode.displayName, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

        if (mode == TimerMode.STOPWATCH) {
            val displayMillis = if (isThisModeActive) timerState.displayMillis else 0L
            StopwatchChronometer(
                displayMillis = displayMillis,
                isRunning = isThisModeTicking,
                modifier = Modifier.size(maxTimerSize),
            )
        } else {
            val displayMillis = if (isThisModeActive) timerState.displayMillis
            else if (mode == TimerMode.POMODORO) timerState.customPomodoroDurationMinutes * 60_000L
            else mode.initialMillis
            val displayProgress = if (isThisModeActive && isThisModeTicking) timerState.progress else 0f
            val animatedProgress by animateFloatAsState(displayProgress, tween(600), label = "ring_${mode.name}")
            val ringColor = when {
                isThisModeTicking -> MaterialTheme.colorScheme.primary
                isThisModePaused -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(maxTimerSize)) {
                CircularTimerRing(
                    progress = animatedProgress,
                    ringColor = ringColor,
                    secondaryColor = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeWidth = 10.dp,
                    isTicking = isThisModeTicking,
                    mode = mode,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    TimerText(
                        displayMillis = displayMillis,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.displaySmall,
                        fontSize = if (TimeUtils.formatAdaptiveTime(displayMillis).length > 5) 32.sp else 40.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = when {
                            isThisModeTicking -> if (mode == TimerMode.BREAK) "MOLA VAKTİ" else "ODAKLANIYOR"
                            isThisModePaused -> "DURAKLATILDI"
                            else -> "HAZIR"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = when {
                            isThisModeTicking -> ringColor
                            isThisModePaused -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )

                    // For Pomodoro: 4 minimalist cycle dots showing current session progress
                    if (mode == TimerMode.POMODORO) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val completedSessions = timerState.completedConsecutiveSessions % 4
                            for (i in 0 until 4) {
                                val isCompleted = i < completedSessions
                                val isCurrent = i == completedSessions && isThisModeTicking
                                Box(
                                    modifier = Modifier
                                        .size(if (isCurrent) 6.dp else 5.dp)
                                        .background(
                                            color = when {
                                                isCompleted -> MaterialTheme.colorScheme.primary
                                                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                            },
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (mode == TimerMode.STOPWATCH) {
            AnimatedVisibility(
                visible = isThisModeActive && timerState.isPresenceCheckActive,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                PresenceCheckBanner(
                    remainingMillis = timerState.presenceCheckRemainingMillis,
                    onConfirm = onConfirmPresence
                )
            }
        }

        if (mode == TimerMode.BLOCK_90 && (isThisModeTicking || isThisModePaused)) {
            AfkSensorBadge(isStationary = timerState.isAfkStationary, isActive = isThisModeTicking)
        }

        if (mode == TimerMode.POMODORO) {
            AssistChip(
                onClick = { if (!isThisModeTicking) showDurationPicker = true },
                label = {
                    Text(
                        stringResource(R.string.home_pomodoro_duration) + ": ${timerState.customPomodoroDurationMinutes} dk",
                        color = if (isThisModeTicking) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (isThisModeTicking) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                enabled = !isThisModeTicking,
            )
        }
    }

    if (showDurationPicker) {
        DurationPickerDialog(
            currentMinutes = timerState.customPomodoroDurationMinutes,
            label = stringResource(R.string.home_pomodoro_duration),
            range = 30..300,
            onDismiss = { showDurationPicker = false },
            onConfirm = { minutes ->
                onSetPomodoroDuration(minutes)
                showDurationPicker = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stopwatch digital chronometer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StopwatchChronometer(
    displayMillis: Long,
    isRunning    : Boolean,
    modifier     : Modifier = Modifier,
) {
    // Smooth 60fps continuous second sweep (360 degrees per 60 seconds = 0.006 deg/ms)
    val sweepAngleState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isRunning, displayMillis) {
        val baseAngle = ((displayMillis % 60_000L).toFloat() / 60_000f) * 360f
        if (!isRunning) {
            sweepAngleState.floatValue = baseAngle
        } else {
            if (kotlin.math.abs(sweepAngleState.floatValue - baseAngle) > 12f) {
                sweepAngleState.floatValue = baseAngle
            }
            var lastFrameMillis = withFrameMillis { it }
            while (isRunning) {
                val currentFrameMillis = withFrameMillis { it }
                val deltaMillis = (currentFrameMillis - lastFrameMillis).coerceAtLeast(0L)
                lastFrameMillis = currentFrameMillis
                sweepAngleState.floatValue = (sweepAngleState.floatValue + deltaMillis * 0.006f) % 360f
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val onBgColor = MaterialTheme.colorScheme.onBackground

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .drawBehind {
                val strokePx = 10.dp.toPx()
                val radius = (size.minDimension - strokePx) / 2f
                val arcTopLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2f, radius * 2f)

                // 1. Base minimalist track
                drawCircle(
                    color = trackColor.copy(alpha = 0.55f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokePx)
                )

                // 2. 12 Minimalist tick markers around the dial (every 5 seconds)
                for (i in 0 until 12) {
                    val tickAngleDeg = i * 30f - 90f
                    val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
                    val isCardinal = i % 3 == 0 // 12, 3, 6, 9 o'clock
                    val innerTickRadius = radius - if (isCardinal) strokePx * 0.7f else strokePx * 0.35f
                    val outerTickRadius = radius + if (isCardinal) strokePx * 0.7f else strokePx * 0.35f

                    val startX = center.x + innerTickRadius * Math.cos(tickAngleRad).toFloat()
                    val startY = center.y + innerTickRadius * Math.sin(tickAngleRad).toFloat()
                    val endX = center.x + outerTickRadius * Math.cos(tickAngleRad).toFloat()
                    val endY = center.y + outerTickRadius * Math.sin(tickAngleRad).toFloat()

                    val tickColor = if (i == 0) {
                        primaryColor.copy(alpha = 0.85f)
                    } else if (isCardinal) {
                        onBgColor.copy(alpha = 0.25f)
                    } else {
                        onBgColor.copy(alpha = 0.12f)
                    }

                    drawLine(
                        color = tickColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isCardinal) 2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 3. Active elapsed second arc
                val currentSweep = if (displayMillis > 0L || isRunning) {
                    sweepAngleState.floatValue.coerceAtLeast(0.5f)
                } else 0f

                val activeColor = if (isRunning) primaryColor else secondaryColor

                if (currentSweep > 0.5f) {
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // 4. Precision indicator bead at the tip of the sweep arc
                    val headAngleRad = Math.toRadians((currentSweep - 90f).toDouble())
                    val headX = center.x + radius * Math.cos(headAngleRad).toFloat()
                    val headY = center.y + radius * Math.sin(headAngleRad).toFloat()
                    val dotRadius = strokePx * 0.65f

                    drawCircle(
                        color = activeColor,
                        radius = dotRadius,
                        center = Offset(headX, headY)
                    )
                    drawCircle(
                        color = surfaceColor,
                        radius = dotRadius * 0.45f,
                        center = Offset(headX, headY)
                    )
                }
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TimerText(
                displayMillis = displayMillis,
                color = onBgColor,
                style = MaterialTheme.typography.displaySmall,
                fontSize = if (TimeUtils.formatAdaptiveTime(displayMillis).length > 5) 30.sp else 38.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isRunning) "KRONOMETRE" else if (displayMillis > 0L) "DURAKLATILDI" else "HAZIR",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (isRunning) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun TimerText(
    displayMillis: Long,
    color: Color,
    style: TextStyle,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    val formatted = remember(displayMillis) {
        TimeUtils.formatAdaptiveTime(displayMillis)
    }

    Text(
        text = formatted,
        color = color,
        style = style,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Break timer page
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BreakTimerPage(
    timerState         : TimerState,
    onSetBreakDuration : (Int) -> Unit,
    modifier           : Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val maxTimerSize = (configuration.screenWidthDp.dp * 0.55f).coerceAtMost(260.dp)

    val isBreakPhase = timerState.timerPhase == TimerPhase.MANDATORY_BREAK || timerState.currentMode == TimerMode.BREAK
    val isBreakActive = isBreakPhase && timerState.isTicking
    val isBreakPaused = timerState.currentMode == TimerMode.BREAK && timerState.timerPhase == TimerPhase.PAUSED

    val displayMillis = if (isBreakPhase) timerState.displayMillis else timerState.breakDurationMinutes * 60_000L
    val displayProgress = if (isBreakPhase) timerState.progress else 0f
    val animatedProgress by animateFloatAsState(displayProgress, tween(600), label = "break_ring")
    val ringColor = when {
        isBreakActive -> MaterialTheme.colorScheme.secondary
        isBreakPaused -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
    }

    var sliderValue by remember(timerState.breakDurationMinutes) { mutableFloatStateOf(timerState.breakDurationMinutes.toFloat()) }

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (timerState.isMandatoryBreakRequired) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.mode_break) + " — 2 Seans Tamamlandı",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(stringResource(R.string.mode_break), color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(maxTimerSize)) {
            CircularTimerRing(
                progress = animatedProgress,
                ringColor = ringColor,
                secondaryColor = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 10.dp,
                isTicking = isBreakActive,
                mode = TimerMode.BREAK,
                modifier = Modifier.fillMaxSize()
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimerText(
                    displayMillis = displayMillis,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.displaySmall,
                    fontSize = 38.sp
                )
            }
        }
        if (!isBreakActive && !isBreakPaused) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.home_break_duration) + " ${sliderValue.roundToInt()} dk",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSetBreakDuration(sliderValue.roundToInt()) },
                    valueRange = 5f..20f,
                    steps = 14,
                    modifier = Modifier.fillMaxWidth(0.8f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    ),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Presence check banner (STOPWATCH)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PresenceCheckBanner(remainingMillis: Long, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(18.dp)
                )
                Text(stringResource(R.string.home_presence_title), color = MaterialTheme.colorScheme.onError, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.home_presence_msg, TimeUtils.formatAdaptiveTime(remainingMillis)),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.btn_here_iam), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Consecutive sessions badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConsecutiveSessionsBadge(count: Int, max: Int = 2, modifier: Modifier = Modifier) {
    val filled = count.coerceIn(0, max)
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(max) { i ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        CircleShape
                    )
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.home_session_count, filled, max),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AFK sensor badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AfkSensorBadge(isStationary: Boolean, isActive: Boolean, modifier: Modifier = Modifier) {
    val dotColor by animateColorAsState(
        targetValue = when {
            !isActive -> MaterialTheme.colorScheme.onSurfaceVariant
            isStationary -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.secondary
        },
        animationSpec = tween(400),
        label = "afk_dot",
    )
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Text(
            "AFK: ${when { !isActive -> "Duraklatıldı"; isStationary -> "Hareketsiz"; else -> "Hareket" }}",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Timer controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TimerControls(
    mode               : TimerMode,
    timerState         : TimerState,
    onStart            : () -> Unit,
    onPause            : () -> Unit,
    onResume           : () -> Unit,
    onReset            : () -> Unit,
    onStartDuringBreak : () -> Unit,
    modifier           : Modifier = Modifier,
) {
    val phase = timerState.timerPhase
    val isThisModeActive = timerState.currentMode == mode
    val isMandatoryBreak = phase == TimerPhase.MANDATORY_BREAK
    val isBreakInterrupt = isMandatoryBreak && mode != TimerMode.BREAK

    Row(modifier = modifier.height(52.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            isBreakInterrupt -> {
                Button(
                    onClick = onStartDuringBreak,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_start), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            !isThisModeActive || phase == TimerPhase.IDLE -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == TimerMode.BREAK) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = if (mode == TimerMode.BREAK) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (mode == TimerMode.BREAK) stringResource(R.string.btn_start_break) else stringResource(R.string.btn_start),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            isMandatoryBreak && mode == TimerMode.BREAK -> {
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                ) {
                    Text(stringResource(R.string.home_break_active_btn), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            phase == TimerPhase.PAUSED -> {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == TimerMode.BREAK) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = if (mode == TimerMode.BREAK) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_resume), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                ) {
                    Icon(Icons.Default.Replay, null, tint = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_reset), color = MaterialTheme.colorScheme.onBackground)
                }
            }

            phase == TimerPhase.FOCUSING -> {
                Button(
                    onClick = onPause,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                ) {
                    Icon(Icons.Default.Pause, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_pause), fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_stop), fontWeight = FontWeight.SemiBold)
                }
            }

            else -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("BAŞLAT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas ring (countdown modes)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CircularTimerRing(
    progress: Float,
    ringColor: Color,
    secondaryColor: Color = ringColor,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    strokeWidth: Dp = 10.dp,
    isTicking: Boolean = false,
    mode: TimerMode? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_halo")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    Box(modifier = modifier.drawBehind {
        val strokePx = strokeWidth.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val centerPt = Offset(size.width / 2f, size.height / 2f)

        // 1. Subtle Outer Celestial Hairline Orbit (Swiss Horological Minimalist Depth)
        drawCircle(
            color = trackColor.copy(alpha = 0.35f),
            radius = radius + strokePx * 0.75f,
            center = centerPt,
            style = Stroke(width = 1.dp.toPx())
        )

        // 2. Base Minimalist Track
        drawCircle(
            color = trackColor.copy(alpha = 0.45f),
            radius = radius,
            center = centerPt,
            style = Stroke(width = strokePx)
        )

        // 3. Precision Radial Micro-Ticks
        // 12 ticks around dial (every 30 deg), cardinal ones (12, 3, 6, 9) slightly more prominent
        val numTicks = 12
        for (i in 0 until numTicks) {
            val tickAngleDeg = i * (360f / numTicks) - 90f
            val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
            val isCardinal = i % 3 == 0

            val tickInnerR = radius - (if (isCardinal) strokePx * 0.65f else strokePx * 0.35f)
            val tickOuterR = radius + (if (isCardinal) strokePx * 0.65f else strokePx * 0.35f)

            val startX = centerPt.x + tickInnerR * Math.cos(tickAngleRad).toFloat()
            val startY = centerPt.y + tickInnerR * Math.sin(tickAngleRad).toFloat()
            val endX = centerPt.x + tickOuterR * Math.cos(tickAngleRad).toFloat()
            val endY = centerPt.y + tickOuterR * Math.sin(tickAngleRad).toFloat()

            val tickColor = if (isCardinal) {
                trackColor.copy(alpha = 0.9f)
            } else {
                trackColor.copy(alpha = 0.45f)
            }

            drawLine(
                color = tickColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = if (isCardinal) 1.75.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 4. Subtle Inner Ambient Halo (pulsing when actively ticking)
        if (isTicking) {
            drawCircle(
                color = ringColor.copy(alpha = pulseAlpha),
                radius = radius - strokePx * 0.85f,
                center = centerPt,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // 5. Active Animated Progress Arc with Sweep Gradient
        if (progress > 0.001f) {
            val clampedProgress = progress.coerceIn(0f, 1f)
            val sweepAngle = clampedProgress * 360f
            val topLeft = Offset(centerPt.x - radius, centerPt.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            // Sweep Gradient for fluid, modern color flow
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to secondaryColor,
                    clampedProgress to ringColor,
                    center = centerPt
                ),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // 6. Glowing Precision Indicator Bead at the Tip
            val headAngleRad = Math.toRadians((sweepAngle - 90f).toDouble())
            val headX = centerPt.x + radius * Math.cos(headAngleRad).toFloat()
            val headY = centerPt.y + radius * Math.sin(headAngleRad).toFloat()

            // Outer soft glow
            drawCircle(
                color = ringColor.copy(alpha = 0.35f),
                radius = strokePx * 0.85f,
                center = Offset(headX, headY)
            )
            // Crisp luminous core bead
            drawCircle(
                color = Color.White,
                radius = strokePx * 0.35f,
                center = Offset(headX, headY)
            )
        }
    })
}

// ─────────────────────────────────────────────────────────────────────────────
// Duration picker dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DurationPickerDialog(
    currentMinutes: Int,
    label: String,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var inputText by remember(currentMinutes) { mutableStateOf(currentMinutes.toString()) }
    val parsedValue = inputText.toIntOrNull()
    val isError = parsedValue == null || parsedValue !in range

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text("$label Ayarla", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${range.first}–${range.last} dakika arasında girin:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it.filter { c -> c.isDigit() }.take(3) },
                    isError = isError,
                    singleLine = true,
                    label = { Text("Dakika") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = if (isError) {
                        { Text("${range.first}–${range.last} dk arası olmalı", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.onBackground,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedValue?.let { onConfirm(it.coerceIn(range.first, range.last)) } },
                enabled = !isError
            ) {
                Text(stringResource(R.string.btn_apply), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Daily score card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DailyFocusScoreCard(dailyScore: Float, onRefreshScore: () -> Unit = {}) {
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "homeRefreshRotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.score_daily_title),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(String.format("%.1f", dailyScore), color = MaterialTheme.colorScheme.primary, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Text("/ 10", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(
                        if (dailyScore >= 7f) "Günlük odak hedefiyle uyumlu ilerliyorsun." else "Daha fazla odaklanarak puanını artırabilirsin.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${(dailyScore * 10).toInt()}%", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(
                onClick = {
                    rotationAngle += 360f
                    onRefreshScore()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Puanı Yenile",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(animatedRotation)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A192F, heightDp = 820)
@Composable
private fun HomeScreenPreview() {
    SharkMentorTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.home_status_today),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            DailyFocusScoreCard(8.5f)
            ModeIndicatorDots(4, 0)
            StopwatchChronometer(displayMillis = 91 * 60 * 1000L, isRunning = true, modifier = Modifier.size(200.dp))
        }
    }
}

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "Shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E293B),
            Color(0xFF334155),
            Color(0xFF1E293B)
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
}
