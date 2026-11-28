package com.example.sharkmentor.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharkmentor.core.ai.SharkTokenQuotaManager
import com.example.sharkmentor.core.audio.MusicPlayerHub
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.dashboard.HomeDashboardEngine
import com.example.sharkmentor.core.dashboard.HomeDashboardSnapshot
import com.example.sharkmentor.core.timer.TimerMode
import com.example.sharkmentor.core.timer.TimerPhase
import com.example.sharkmentor.core.timer.TimerState
import com.example.sharkmentor.domain.engine.DailyFocusEngine
import com.example.sharkmentor.core.util.TimeUtils
import com.example.sharkmentor.data.dashboard.DashboardLayoutPreferences
import com.example.sharkmentor.data.dashboard.DashboardWidgetPlacement
import com.example.sharkmentor.data.dashboard.DashboardWidgetSize
import com.example.sharkmentor.data.dashboard.DashboardWidgetType
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.data.repository.StreakRepository
import com.example.sharkmentor.ui.coaching.DailyGoalConfigDialog
import com.example.sharkmentor.ui.components.ThemedMusicPlayerPage
import com.example.sharkmentor.ui.navigation.Screen
import com.example.sharkmentor.ui.viewmodel.TimerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private data class DashboardCatalogItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val aiPrompt: String? = null
)

/**
 * Mark 77 Full-Page Customizable Study OS Dashboard (`Panel` tab).
 *
 * - Uses the full available page width cleanly (responsive multi-column packing for SMALL tiles).
 * - Inline Edit mode lets the student add/hide widgets, resize any widget (`K / O / B`),
 *   and reorder freely — including an embedded live Kronometre & Sayaç widget.
 * - Tapping Music slides the compact Music Player in from the left and smoothly shrinks & aligns
 *   the entire Dashboard to the right side (matching the Focus Desk split-pane behavior).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    timerViewModel: TimerViewModel = viewModel(),
    onNavigateToRoute: (String) -> Unit = {},
    onLaunchSharkAiWithPrompt: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(context) {
        CoachingEngine.ensureInitialized(context)
        CoachingEngine.refreshTargetAndLedgerAsync(context)
        MusicPlayerHub.ensureInitialized(context)
        SharkTokenQuotaManager.ensureLoaded(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                MusicPlayerHub.refreshPermissionsAndSessions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val layoutPrefs = remember(context) { DashboardLayoutPreferences.getInstance(context) }
    val layoutState by layoutPrefs.layout.collectAsStateWithLifecycle()
    var isEditMode by remember { mutableStateOf(false) }
    var showAllToolsSheet by remember { mutableStateOf(false) }
    var showDailyGoalDialog by remember { mutableStateOf(false) }
    var showStreakGoalDialog by remember { mutableStateOf(false) }

    val timerState by timerViewModel.timerState.collectAsStateWithLifecycle()
    var selectedWidgetTimerMode by remember { mutableStateOf(TimerMode.STOPWATCH) }

    val musicPlayerEnabled by MusicPlayerHub.isEnabled.collectAsStateWithLifecycle()
    val musicSelectedSkin by MusicPlayerHub.selectedSkin.collectAsStateWithLifecycle()
    val musicPlaybackState by MusicPlayerHub.playbackState.collectAsStateWithLifecycle()
    val isHomeMusicPanelOpen by MusicPlayerHub.isHomePanelOpen.collectAsStateWithLifecycle()

    val targetContext by CoachingEngine.targetContext.collectAsStateWithLifecycle()
    val weekdayGoalMins by CoachingEngine.weekdayGoalMinutes.collectAsStateWithLifecycle()
    val weekendGoalMins by CoachingEngine.weekendGoalMinutes.collectAsStateWithLifecycle()
    val useAiGoal by CoachingEngine.useAiRecommendedGoal.collectAsStateWithLifecycle()

    val streakRepo = remember(context) { StreakRepository(context) }
    val streakState by streakRepo.streakState.collectAsStateWithLifecycle()

    val db = remember(context) { SharkMentorDatabase.getDatabase(context) }
    val todayBounds = remember { DailyFocusEngine.getTodayTimeBounds() }
    val todayDbMinutes by remember(db, todayBounds) {
        db.sessionDao().getTodayTotalFocusMinutes(todayBounds.first, todayBounds.second)
    }.collectAsStateWithLifecycle(initialValue = 0)
    val dueErrorCount by remember(db) { db.errorQuestionDao().getDueCountFlow() }
        .collectAsStateWithLifecycle(initialValue = 0)
    val allErrorQuestions by remember(db) { db.errorQuestionDao().getAllQuestions() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allNotes by remember(db) { db.noteDao().getAllNotesFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allResources by remember(db) { db.goalsDao().getAllResourcesFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allStudyTasks by remember(db) { db.studyPlanDao().observeAllTasks() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val latestNetProjection by remember(db) { db.netProjectionDao().getLatestProjection() }
        .collectAsStateWithLifecycle(initialValue = null)

    val userPrefs = remember(context) { UserPreferences.getInstance(context) }
    val syncedStudentName by userPrefs.studentNameFlow.collectAsStateWithLifecycle()
    val quotaSnapshot by SharkTokenQuotaManager.snapshot.collectAsStateWithLifecycle()

    val isTimerActiveNow = timerState.timerPhase == TimerPhase.FOCUSING ||
        timerState.timerPhase == TimerPhase.PAUSED ||
        timerState.timerPhase == TimerPhase.MANDATORY_BREAK

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

    val todayDayIndex = remember { LocalDate.now().dayOfWeek.value - 1 }
    val todayTasks = remember(allStudyTasks, todayDayIndex) {
        allStudyTasks.filter { it.dayIndex == todayDayIndex }
    }
    val currentHourNow = remember { LocalTime.now().hour }
    val snapshot = remember(
        currentHourNow,
        syncedStudentName,
        quotaSnapshot.isUnlimitedUnlocked,
        dailyGoalStatus.workedMinutes,
        dailyGoalStatus.targetMinutes,
        streakState.streakCount,
        streakState.streakGoalDays,
        dueErrorCount,
        allErrorQuestions.size,
        allNotes,
        allResources,
        todayTasks,
        latestNetProjection
    ) {
        HomeDashboardEngine.buildSnapshot(
            hour = currentHourNow,
            studentName = syncedStudentName,
            isJarvisActive = quotaSnapshot.isUnlimitedUnlocked,
            todayFocusMinutes = dailyGoalStatus.workedMinutes,
            todayTargetMinutes = dailyGoalStatus.targetMinutes,
            streakDays = streakState.streakCount,
            streakGoalDays = streakState.streakGoalDays,
            dueErrorCount = dueErrorCount,
            totalErrorCount = allErrorQuestions.size,
            notes = allNotes,
            resources = allResources,
            todayTasks = todayTasks,
            netProjection = latestNetProjection
        )
    }

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

    if (showStreakGoalDialog) {
        StreakGoalChallengeDialog(
            streakState = streakState,
            onSelectGoalDays = { targetDays ->
                val eval = streakRepo.setStreakGoalDays(targetDays)
                uiScope.launch(Dispatchers.IO) {
                    runCatching { com.example.sharkmentor.core.widget.WidgetUpdateManager.updateAllWidgets(context) }
                }
                if (eval.newlyUnlockedTiers.isEmpty()) {
                    Toast.makeText(context, "$targetDays günlük seri hedefin ayarlandı!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showStreakGoalDialog = false }
        )
    }

    if (showAllToolsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val catalogItems = remember {
            listOf(
                DashboardCatalogItem("Grafikli Çöz", "Fonksiyon & geometri SVG çözücü", Icons.Default.AutoAwesome, Screen.Shark.route, "/detayli "),
                DashboardCatalogItem("Simülasyon Lab", "İnteraktif fizik, kimya, biyoloji", Icons.Default.Bolt, Screen.Shark.route, "/simulasyon "),
                DashboardCatalogItem("Akıllı Plan Kur", "AI ile haftalık etüt programı", Icons.Default.CalendarMonth, Screen.Shark.route, "/plan "),
                DashboardCatalogItem("Odak Masası", "Pomodoro, Kronometre & 90 dk", Icons.Default.Timer, Screen.Focus.route),
                DashboardCatalogItem("Hata Bankası", "FSRS 1-3-7 tekrar & ikiz soru", Icons.Default.FitnessCenter, Screen.ErrorBank.route),
                DashboardCatalogItem("Stüdyo & PDF", "Kalem, Kement AI & El Yazısı→Metin", Icons.Default.StickyNote2, Screen.Notes.route),
                DashboardCatalogItem("Kütüphane", "Kitap filtreleme & video kampları", Icons.Default.LibraryBooks, Screen.Resources.route),
                DashboardCatalogItem("Detaylı Analiz", "Net tahmin sihirbazı & grafikler", Icons.Default.BarChart, Screen.DetailedAnalytics.route),
                DashboardCatalogItem("Shark Lig", "Haftalık lig ve arkadaş yarışı", Icons.Default.Star, Screen.SharkLeague.route),
                DashboardCatalogItem("PACE / KEEP", "Soru başına süre & hız sayacı", Icons.Default.Speed, Screen.PaceKeep.route),
                DashboardCatalogItem("Zen Odası", "Nefes, ambiyans & zihin tazeleyici", Icons.Default.SelfImprovement, "zen_room"),
                DashboardCatalogItem("Ders Programı", "Haftalık sabit okul/kurs saatleri", Icons.Default.Event, Screen.Schedule.route)
            )
        }
        ModalBottomSheet(
            onDismissRequest = { showAllToolsSheet = false },
            sheetState = sheetState,
            containerColor = colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tüm SharkMentor Araçları",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface
                )
                catalogItems.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { item ->
                            Surface(
                                onClick = {
                                    showAllToolsSheet = false
                                    if (item.aiPrompt != null) {
                                        onLaunchSharkAiWithPrompt(item.aiPrompt)
                                    } else {
                                        onNavigateToRoute(item.route)
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colorScheme.primary.copy(alpha = 0.14f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = null,
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Full-Page BoxWithConstraints with Split-Pane Left Music Player (shrinks & aligns Dashboard right on wide layouts!)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        val isWideLayout = maxWidth >= 600.dp
        val panelVisible = musicPlayerEnabled && isHomeMusicPanelOpen
        val panelWidth = if (isWideLayout) {
            (maxWidth * 0.30f).coerceIn(248.dp, 304.dp)
        } else {
            (maxWidth * 0.78f).coerceIn(256.dp, 312.dp)
        }

        val dashboardStartPadding by animateDpAsState(
            targetValue = if (isWideLayout && panelVisible) panelWidth + 18.dp else 0.dp,
            animationSpec = tween(durationMillis = 280),
            label = "dashboard_music_shift_start"
        )
        val dashboardEndPadding by animateDpAsState(
            targetValue = if (isWideLayout && panelVisible) 14.dp else 0.dp,
            animationSpec = tween(durationMillis = 280),
            label = "dashboard_music_shift_end"
        )

        val effectiveContentWidth = maxWidth - dashboardStartPadding - dashboardEndPadding
        val smallColumns = if (effectiveContentWidth >= 780.dp) 3 else 2
        val visualRows = remember(layoutState, smallColumns) {
            layoutState.buildVisualRows(maxSmallPerRow = smallColumns)
        }

        // Main Full-Page Scrollable Dashboard
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = dashboardStartPadding, end = dashboardEndPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Compact Top Dashboard Action Bar (Title + Music button + Edit Layout toggle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isEditMode) "Paneli Özelleştir" else "Komuta Paneli",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isEditMode) {
                        TextButton(
                            onClick = {
                                layoutPrefs.resetToDefault()
                                Toast.makeText(context, "Panel düzeni sıfırlandı.", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Varsayılan", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else if (musicPlayerEnabled) {
                        Surface(
                            onClick = { MusicPlayerHub.toggleHomePanel() },
                            shape = RoundedCornerShape(10.dp),
                            color = if (panelVisible || musicPlaybackState.isPlaying) {
                                colorScheme.secondaryContainer
                            } else {
                                colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                1.dp,
                                if (panelVisible || musicPlaybackState.isPlaying) colorScheme.secondary.copy(alpha = 0.5f)
                                else colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Müzik Oynatıcı",
                                    tint = if (panelVisible || musicPlaybackState.isPlaying) colorScheme.onSecondaryContainer else colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (panelVisible) "Gizle" else "Müzik",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (panelVisible || musicPlaybackState.isPlaying) colorScheme.onSecondaryContainer else colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { isEditMode = !isEditMode },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isEditMode) colorScheme.primary else colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (isEditMode) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (isEditMode) "Bitti" else "Düzenle",
                                tint = if (isEditMode) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isEditMode) "Bitti" else "Düzenle",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isEditMode) colorScheme.onPrimary else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isEditMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Her kartın üstünden boyutunu (K: Küçük • O: Orta • B: Büyük) değiştirebilir, ↑↓ ile yerini kaydırabilir veya gizleyip alttan ekleyebilirsin.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Render visible widget rows (packing SMALL tiles cleanly into 2 or 3 columns!)
            if (visualRows.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Panelde aktif widget kalmadı.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Düzenle modundan istediğin kartları ekleyebilir veya varsayılana dönebilirsin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { layoutPrefs.resetToDefault() }, shape = RoundedCornerShape(12.dp)) {
                            Text("Varsayılan Paneli Geri Yükle")
                        }
                    }
                }
            } else {
                val visibleOrder = layoutState.visiblePlacements
                visualRows.forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { placement ->
                            val visIdx = visibleOrder.indexOfFirst { it.type == placement.type }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isEditMode) {
                                    WidgetEditControlBar(
                                        placement = placement,
                                        canMoveUp = visIdx > 0,
                                        canMoveDown = visIdx >= 0 && visIdx < visibleOrder.lastIndex,
                                        onSelectSize = { layoutPrefs.setWidgetSize(placement.type, it) },
                                        onMoveUp = { layoutPrefs.moveWidgetUp(placement.type) },
                                        onMoveDown = { layoutPrefs.moveWidgetDown(placement.type) },
                                        onHide = { layoutPrefs.toggleWidget(placement.type) }
                                    )
                                }

                                Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                                    RenderDashboardWidget(
                                        placement = placement,
                                        snapshot = snapshot,
                                        timerState = timerState,
                                        selectedTimerMode = selectedWidgetTimerMode,
                                        onSelectTimerMode = { selectedWidgetTimerMode = it },
                                        onStartTimer = { mode ->
                                            timerViewModel.startTimer(mode, timerState.activeSubject.ifBlank { "Genel" })
                                        },
                                        onPauseTimer = timerViewModel::pauseTimer,
                                        onResumeTimer = timerViewModel::resumeTimer,
                                        onResetTimer = timerViewModel::resetTimer,
                                        isTimerActiveNow = isTimerActiveNow,
                                        onEditDailyGoal = { showDailyGoalDialog = true },
                                        onOpenStreakDialog = { showStreakGoalDialog = true },
                                        onOpenAllTools = { showAllToolsSheet = true },
                                        onNavigateToRoute = onNavigateToRoute,
                                        onLaunchSharkAiWithPrompt = onLaunchSharkAiWithPrompt
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Hidden Widgets Add-Back Tray in Edit Mode
            if (isEditMode && layoutState.hiddenPlacements.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Eklenebilir Widget'lar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    layoutState.hiddenPlacements.forEach { hidden ->
                        Surface(
                            onClick = { layoutPrefs.toggleWidget(hidden.type) },
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hidden.type.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = hidden.type.description,
                                        fontSize = 10.5.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = colorScheme.primary.copy(alpha = 0.14f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Ekle",
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Ekle",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        // Narrow phone scrim when left music drawer is open
        if (!isWideLayout) {
            AnimatedVisibility(
                visible = panelVisible,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f))
                        .clickable { MusicPlayerHub.setHomePanelOpen(false) }
                )
            }
        }

        // Compact Left Music Player Panel (slides in smoothly from the left without dominating screen!)
        AnimatedVisibility(
            visible = panelVisible,
            enter = slideInHorizontally(tween(280)) { -it } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(240)) { -it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
            ) {
                ThemedMusicPlayerPage(
                    playbackState = musicPlaybackState,
                    selectedSkin = musicSelectedSkin,
                    onSelectSkin = { chosenSkin -> MusicPlayerHub.setSkin(context, chosenSkin) },
                    compactMode = false,
                    showSlideToDeskHint = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun WidgetEditControlBar(
    placement: DashboardWidgetPlacement,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelectSize: (DashboardWidgetSize) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.32f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Size toggles: K | O | B
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                DashboardWidgetSize.entries.forEach { sz ->
                    val selected = placement.size == sz
                    Surface(
                        onClick = { onSelectSize(sz) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) colorScheme.primary else colorScheme.surface
                    ) {
                        Text(
                            text = sz.shortLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Yukarı",
                        tint = if (canMoveUp) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Aşağı",
                        tint = if (canMoveDown) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onHide, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Gizle",
                        tint = colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderDashboardWidget(
    placement: DashboardWidgetPlacement,
    snapshot: HomeDashboardSnapshot,
    timerState: TimerState,
    selectedTimerMode: TimerMode,
    onSelectTimerMode: (TimerMode) -> Unit,
    onStartTimer: (TimerMode) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onResetTimer: () -> Unit,
    isTimerActiveNow: Boolean,
    onEditDailyGoal: () -> Unit,
    onOpenStreakDialog: () -> Unit,
    onOpenAllTools: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onLaunchSharkAiWithPrompt: (String) -> Unit
) {
    val size = placement.size
    when (placement.type) {
        DashboardWidgetType.HERO_FOCUS -> HeroFocusWidgetCard(
            size = size,
            snapshot = snapshot,
            onEditDailyGoal = onEditDailyGoal,
            onOpenStreakDialog = onOpenStreakDialog,
            onOpenFocusDesk = { onNavigateToRoute(Screen.Focus.route) }
        )

        DashboardWidgetType.LIVE_TIMER -> EmbeddedLiveTimerWidgetCard(
            size = size,
            timerState = timerState,
            selectedMode = selectedTimerMode,
            isTimerActiveNow = isTimerActiveNow,
            onSelectMode = onSelectTimerMode,
            onStart = { onStartTimer(selectedTimerMode) },
            onPause = onPauseTimer,
            onResume = onResumeTimer,
            onReset = onResetTimer,
            onOpenFullDesk = { onNavigateToRoute(Screen.Focus.route) }
        )

        DashboardWidgetType.SHARK_AI -> SharkAiWidgetCard(
            size = size,
            onOpenShark = { onNavigateToRoute(Screen.Shark.route) },
            onLaunchPrompt = onLaunchSharkAiWithPrompt
        )

        DashboardWidgetType.ERROR_BANK -> ResizableBentoMetricCard(
            size = size,
            icon = Icons.Default.FitnessCenter,
            badge = snapshot.errorCardBadge,
            title = snapshot.errorCardTitle,
            subtitle = snapshot.errorCardSubtitle,
            accent = if (snapshot.errorCardHighlighted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            highlighted = snapshot.errorCardHighlighted,
            onClick = { onNavigateToRoute(Screen.ErrorBank.route) }
        )

        DashboardWidgetType.STUDIO_NOTES -> ResizableBentoMetricCard(
            size = size,
            icon = Icons.Default.StickyNote2,
            badge = snapshot.studioCardBadge,
            title = snapshot.studioCardTitle,
            subtitle = snapshot.studioCardSubtitle,
            accent = MaterialTheme.colorScheme.secondary,
            highlighted = false,
            onClick = { onNavigateToRoute(Screen.Notes.route) }
        )

        DashboardWidgetType.LIBRARY_BOOKS -> ResizableBentoMetricCard(
            size = size,
            icon = Icons.Default.LibraryBooks,
            badge = snapshot.libraryCardBadge,
            title = snapshot.libraryCardTitle,
            subtitle = snapshot.libraryCardSubtitle,
            accent = MaterialTheme.colorScheme.primary,
            highlighted = false,
            progressFraction = snapshot.libraryProgressFraction,
            onClick = { onNavigateToRoute(Screen.Resources.route) }
        )

        DashboardWidgetType.TODAY_PLAN -> ResizableBentoMetricCard(
            size = size,
            icon = Icons.Default.CalendarMonth,
            badge = snapshot.planCardBadge,
            title = snapshot.planCardTitle,
            subtitle = snapshot.planCardSubtitle,
            accent = MaterialTheme.colorScheme.tertiary,
            highlighted = false,
            onClick = { onNavigateToRoute(snapshot.planCardRoute) }
        )

        DashboardWidgetType.QUICK_TOOLS -> QuickToolsWidgetCard(
            size = size,
            onNavigateToRoute = onNavigateToRoute,
            onOpenAllTools = onOpenAllTools
        )
    }
}

@Composable
private fun HeroFocusWidgetCard(
    size: DashboardWidgetSize,
    snapshot: HomeDashboardSnapshot,
    onEditDailyGoal: () -> Unit,
    onOpenStreakDialog: () -> Unit,
    onOpenFocusDesk: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val animatedProgress by animateFloatAsState(
        targetValue = snapshot.focusProgressFraction,
        animationSpec = tween(600),
        label = "hero_widget_ring"
    )
    Surface(
        onClick = onOpenFocusDesk,
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.26f)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primaryContainer.copy(alpha = 0.4f),
                            colorScheme.surfaceContainer.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(if (size == DashboardWidgetSize.SMALL) 11.dp else 15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (size == DashboardWidgetSize.SMALL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onOpenStreakDialog,
                        shape = RoundedCornerShape(999.dp),
                        color = colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.WaterDrop, null, tint = colorScheme.primary, modifier = Modifier.size(11.dp))
                            Text("${snapshot.streakDays}g", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                        }
                    }
                    Text(
                        text = "%${snapshot.focusProgressPercent}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.primary
                    )
                }
                Text(
                    text = HomeDashboardEngine.formatDurationCompact(snapshot.todayFocusMinutes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Hedef: ${HomeDashboardEngine.formatDurationCompact(snapshot.todayTargetMinutes)}",
                    fontSize = 10.5.sp,
                    color = colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = colorScheme.primary,
                    trackColor = colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp))
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            onClick = onOpenStreakDialog,
                            shape = RoundedCornerShape(999.dp),
                            color = colorScheme.primary.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.WaterDrop, null, tint = colorScheme.primary, modifier = Modifier.size(12.dp))
                                Text("${snapshot.streakDays} gün seri", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                            }
                        }
                        Text(
                            text = snapshot.greetingTitle,
                            style = if (size == DashboardWidgetSize.LARGE) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = snapshot.greetingSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(if (size == DashboardWidgetSize.LARGE) 82.dp else 68.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onEditDailyGoal),
                        contentAlignment = Alignment.Center
                    ) {
                        val trackColor = colorScheme.outlineVariant.copy(alpha = 0.45f)
                        val progressColor = colorScheme.primary
                        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 6.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = (360f * animatedProgress).coerceAtLeast(if (snapshot.todayFocusMinutes > 0) 14f else 0f),
                                useCenter = false,
                                style = Stroke(width = 6.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = HomeDashboardEngine.formatDurationCompact(snapshot.todayFocusMinutes),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "/ ${HomeDashboardEngine.formatDurationCompact(snapshot.todayTargetMinutes)}",
                                fontSize = 8.5.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (size == DashboardWidgetSize.LARGE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenFocusDesk,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Odak Masasına Geç", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onEditDailyGoal,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Hedefi Düzenle", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbeddedLiveTimerWidgetCard(
    size: DashboardWidgetSize,
    timerState: TimerState,
    selectedMode: TimerMode,
    isTimerActiveNow: Boolean,
    onSelectMode: (TimerMode) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onOpenFullDesk: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val displayMode = if (isTimerActiveNow) timerState.currentMode else selectedMode
    val displayMillis = if (isTimerActiveNow) timerState.displayMillis else selectedMode.initialMillis
    val formattedClock = TimeUtils.formatAdaptiveTime(displayMillis)
    val isFocusing = timerState.timerPhase == TimerPhase.FOCUSING
    val isPaused = timerState.timerPhase == TimerPhase.PAUSED

    val modeChoices = remember { listOf(TimerMode.STOPWATCH, TimerMode.POMODORO, TimerMode.BLOCK_90) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isFocusing) colorScheme.primary.copy(alpha = 0.6f) else colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (size == DashboardWidgetSize.SMALL) 11.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (size == DashboardWidgetSize.SMALL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Timer, null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = displayMode.displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formattedClock,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface,
                    maxLines = 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            when {
                                isFocusing -> onPause()
                                isPaused -> onResume()
                                else -> onStart()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isFocusing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = when {
                                isFocusing -> "Duraklat"
                                isPaused -> "Devam"
                                else -> "Başlat"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    if (isTimerActiveNow) {
                        Surface(
                            onClick = onReset,
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.errorContainer
                        ) {
                            Box(modifier = Modifier.padding(7.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Stop, contentDescription = "Bitir", tint = colorScheme.onErrorContainer, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            } else {
                // MEDIUM & LARGE interactive timer widget
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Timer, null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Canlı Kronometre & Sayaç",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = onOpenFullDesk,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Tam Masa →", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!isTimerActiveNow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modeChoices.forEach { m ->
                            val selected = selectedMode == m
                            Surface(
                                onClick = { onSelectMode(m) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) colorScheme.primary.copy(alpha = 0.5f) else colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = m.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = formattedClock,
                            fontSize = if (size == DashboardWidgetSize.LARGE) 38.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface
                        )
                        if (isTimerActiveNow && timerState.activeSubject.isNotBlank()) {
                            Text(
                                text = "${displayMode.displayName} • ${timerState.activeSubject}",
                                fontSize = 11.sp,
                                color = colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                when {
                                    isFocusing -> onPause()
                                    isPaused -> onResume()
                                    else -> onStart()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                imageVector = if (isFocusing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isFocusing -> "Duraklat"
                                    isPaused -> "Devam"
                                    else -> "Başlat"
                                },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isTimerActiveNow) {
                            Surface(
                                onClick = onReset,
                                shape = RoundedCornerShape(12.dp),
                                color = colorScheme.errorContainer
                            ) {
                                Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Stop, contentDescription = "Bitir", tint = colorScheme.onErrorContainer, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharkAiWidgetCard(
    size: DashboardWidgetSize,
    onOpenShark: () -> Unit,
    onLaunchPrompt: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpenShark,
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.primaryContainer.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (size == DashboardWidgetSize.SMALL) 11.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = colorScheme.primary,
                    modifier = Modifier.size(if (size == DashboardWidgetSize.SMALL) 32.dp else 36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, tint = colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (size == DashboardWidgetSize.SMALL) "Shark AI" else "Shark • Görsel Çözücü & Simülasyon",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (size == DashboardWidgetSize.SMALL) "Grafikli /detayli & lab" else "Fonksiyon grafiği çizen çözücü, interaktif fizik-kimya labı ve plan koçu.",
                        fontSize = 11.sp,
                        lineHeight = 14.5.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (size != DashboardWidgetSize.SMALL) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }

            if (size == DashboardWidgetSize.SMALL) {
                Surface(
                    onClick = { onLaunchPrompt("/detayli ") },
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Grafikli Çöz (/detayli)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 6.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf(
                        Triple("Grafikli Çöz", "/detayli", "/detayli "),
                        Triple("Simülasyon", "/simulasyon", "/simulasyon "),
                        Triple("Plan Kur", "/plan", "/plan ")
                    ).forEach { (label, badge, prompt) ->
                        Surface(
                            onClick = { onLaunchPrompt(prompt) },
                            shape = RoundedCornerShape(11.dp),
                            color = colorScheme.surface.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = if (size == DashboardWidgetSize.LARGE) 10.dp else 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface, maxLines = 1)
                                Text(badge, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.primary, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizableBentoMetricCard(
    size: DashboardWidgetSize,
    icon: ImageVector,
    badge: String,
    title: String,
    subtitle: String,
    accent: Color,
    highlighted: Boolean,
    progressFraction: Float? = null,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) colorScheme.tertiaryContainer.copy(alpha = 0.45f) else colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (highlighted) accent.copy(alpha = 0.55f) else colorScheme.outlineVariant.copy(alpha = 0.52f)
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        if (size == DashboardWidgetSize.MEDIUM) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = accent.copy(alpha = 0.14f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(shape = RoundedCornerShape(999.dp), color = accent.copy(alpha = 0.14f)) {
                            Text(
                                text = badge,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        lineHeight = 14.5.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (progressFraction != null) {
                        LinearProgressIndicator(
                            progress = { progressFraction.coerceIn(0f, 1f) },
                            color = accent,
                            trackColor = colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp).clip(RoundedCornerShape(999.dp))
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = accent, modifier = Modifier.size(17.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (size == DashboardWidgetSize.LARGE) 16.dp else 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accent.copy(alpha = 0.14f),
                            modifier = Modifier.size(if (size == DashboardWidgetSize.LARGE) 38.dp else 32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = accent, modifier = Modifier.size(if (size == DashboardWidgetSize.LARGE) 20.dp else 17.dp))
                            }
                        }
                        Surface(shape = RoundedCornerShape(999.dp), color = accent.copy(alpha = 0.14f)) {
                            Text(
                                text = badge,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = title,
                            style = if (size == DashboardWidgetSize.LARGE) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            fontSize = if (size == DashboardWidgetSize.LARGE) 12.sp else 11.sp,
                            lineHeight = 15.5.sp,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = if (size == DashboardWidgetSize.LARGE) 4 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (progressFraction != null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        color = accent,
                        trackColor = colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToolsWidgetCard(
    size: DashboardWidgetSize,
    onNavigateToRoute: (String) -> Unit,
    onOpenAllTools: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val tools = remember {
        listOf(
            Triple("Detaylı Analiz", Icons.Default.BarChart, Screen.DetailedAnalytics.route),
            Triple("Shark Lig", Icons.Default.Star, Screen.SharkLeague.route),
            Triple("PACE / KEEP", Icons.Default.Speed, Screen.PaceKeep.route),
            Triple("Zen Odası", Icons.Default.SelfImprovement, "zen_room"),
            Triple("Ders Programı", Icons.Default.Event, Screen.Schedule.route),
            Triple("Geçmiş", Icons.Default.History, Screen.History.route)
        )
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Bolt, null, tint = colorScheme.primary, modifier = Modifier.size(15.dp))
                    Text(
                        text = if (size == DashboardWidgetSize.SMALL) "Araçlar" else "Hızlı Araçlar & Modüller",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }
                Surface(
                    onClick = onOpenAllTools,
                    shape = RoundedCornerShape(999.dp),
                    color = colorScheme.primary.copy(alpha = 0.13f)
                ) {
                    Text(
                        text = "Tümünü Gör",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (size == DashboardWidgetSize.LARGE) {
                tools.chunked(3).forEach { rowTools ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTools.forEach { (label, icon, route) ->
                            Surface(
                                onClick = { onNavigateToRoute(route) },
                                shape = RoundedCornerShape(12.dp),
                                color = colorScheme.surface,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(icon, null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(tools, key = { it.third }) { (label, icon, route) ->
                        Surface(
                            onClick = { onNavigateToRoute(route) },
                            shape = RoundedCornerShape(11.dp),
                            color = colorScheme.surface,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(icon, null, tint = colorScheme.primary, modifier = Modifier.size(14.dp))
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
