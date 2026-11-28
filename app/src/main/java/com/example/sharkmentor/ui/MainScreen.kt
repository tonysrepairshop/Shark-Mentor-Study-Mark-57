package com.example.sharkmentor.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.example.sharkmentor.core.audio.MusicPlayerHub
import com.example.sharkmentor.core.service.CampVideoPlaybackController
import com.example.sharkmentor.ui.coaching.CampVideoStudyStudioDialog
import com.example.sharkmentor.ui.components.DraggableMiniMusicPlayerWindow
import kotlinx.coroutines.delay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sharkmentor.core.data.ThemePalette
import com.example.sharkmentor.core.data.ThemePreferences
import com.example.sharkmentor.core.theme.ThemeManager
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.ui.components.ControlPanelContent
import com.example.sharkmentor.data.weather.WeatherRepository
import com.example.sharkmentor.data.weather.WeatherSettings
import com.example.sharkmentor.data.weather.WeatherUiState
import com.example.sharkmentor.data.topbar.FocusSessionState
import com.example.sharkmentor.data.topbar.TopBarPreferences
import com.example.sharkmentor.data.topbar.TopBarSettings
import com.example.sharkmentor.data.topbar.TopBarModule
import androidx.compose.ui.graphics.graphicsLayer
import com.example.sharkmentor.ui.components.PendingAiPromptBus
import com.example.sharkmentor.ui.components.SpecialMemberConfig
import com.example.sharkmentor.ui.components.TopBarAiShortcutBox
import com.example.sharkmentor.ui.components.TopBarWeatherPill
import com.example.sharkmentor.ui.components.WeatherHourlyDialog
import com.example.sharkmentor.ui.navigation.Screen
import com.example.sharkmentor.ui.onboarding.OnboardingScreen
import com.example.sharkmentor.ui.reflection.DailyReflectionDialog
import com.example.sharkmentor.ui.screens.AiSettingsScreen
import com.example.sharkmentor.ui.screens.CountdownSettingsScreen
import com.example.sharkmentor.ui.screens.SharkScreen
import com.example.sharkmentor.ui.screens.DetailedAnalyticsScreen
import com.example.sharkmentor.ui.screens.ErrorBankScreen
import com.example.sharkmentor.ui.screens.ErrorTrainingScreen
import com.example.sharkmentor.ui.screens.HistoryScreen
import com.example.sharkmentor.ui.screens.HomeScreen
import com.example.sharkmentor.ui.screens.LoginScreen
import com.example.sharkmentor.ui.screens.NoteSettingsScreen
import com.example.sharkmentor.ui.screens.PlansScreen
import com.example.sharkmentor.ui.screens.ProfileScreen
import com.example.sharkmentor.ui.screens.SharkLeagueScreen
import com.example.sharkmentor.ui.screens.StudentCoachChatScreen
import com.example.sharkmentor.ui.screens.notes.NotesScreen
import com.example.sharkmentor.ui.screens.pacekeep.PaceKeepScreen
import com.example.sharkmentor.ui.theme.SharkMentorTheme
import com.example.sharkmentor.ui.viewmodel.ErrorBankViewModel
import com.example.sharkmentor.ui.zen.AiZenCoachScreen
import com.example.sharkmentor.ui.zen.ZenRoomScreen
import kotlinx.coroutines.launch


/**
 * Root composable. Collects [ThemePreferences] flows so the theme and palette
 * respond instantly without restarting the Activity.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    openReflectionDialog: Boolean = false,
    importedNote: com.example.sharkmentor.ImportedNotePayload? = null,
    onImportedNoteConsumed: () -> Unit = {},
    targetRoute: String? = null,
    onTargetRouteConsumed: () -> Unit = {},
    viewModel: com.example.sharkmentor.ui.viewmodel.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    // ── Live theme state from ThemePreferences (SharedPreferences-backed) ──
    val themePrefs = ThemePreferences.instance
    val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle()
    val currentPalette by themePrefs.palette.collectAsStateWithLifecycle()

    val navController       = rememberNavController()
    var showSplash by rememberSaveable { mutableStateOf(importedNote == null && targetRoute == null) }
    var showReflectionDialog by rememberSaveable { mutableStateOf(openReflectionDialog) }
    var pendingImportedNote by remember { mutableStateOf(importedNote) }
    var pendingTargetRoute by remember { mutableStateOf(targetRoute) }
    var isNoteEditorOpen by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(importedNote) {
        if (importedNote != null) {
            pendingImportedNote = importedNote
            showSplash = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(targetRoute) {
        if (!targetRoute.isNullOrBlank()) {
            pendingTargetRoute = targetRoute
            showSplash = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingImportedNote, showSplash) {
        if (pendingImportedNote != null && !showSplash) {
            kotlinx.coroutines.yield()
            runCatching {
                navController.navigate(Screen.Notes.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingTargetRoute, showSplash) {
        val route = pendingTargetRoute
        if (!route.isNullOrBlank() && !showSplash) {
            kotlinx.coroutines.yield()
            runCatching {
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }
            pendingTargetRoute = null
            onTargetRouteConsumed()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val countdownText = uiState.targetCountdownText

    val weatherRepository = remember(context) { WeatherRepository.getInstance(context) }
    val weatherState by weatherRepository.weatherState.collectAsStateWithLifecycle()
    val weatherSettings by weatherRepository.settings.collectAsStateWithLifecycle()
    var showWeatherDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        weatherRepository.refreshWeather()
    }
    val topBarPreferences = remember(context) { TopBarPreferences.getInstance(context) }
    val topBarSettings by topBarPreferences.settings.collectAsStateWithLifecycle()
    val isAnyFocusRunning by FocusSessionState.isAnyFocusRunning.collectAsStateWithLifecycle()
    val liveTimerState by com.example.sharkmentor.core.service.FocusTimerService.timerStateFlow.collectAsStateWithLifecycle()
    val elapsedFocusMinutes = remember(liveTimerState.timeElapsedMillis / 60_000L, isAnyFocusRunning) {
        if (isAnyFocusRunning) (liveTimerState.timeElapsedMillis / 60_000L).toInt() else 0
    }
    val hasUnreadDm by com.example.sharkmentor.core.notification.SocialNotificationMonitor.hasUnreadDmFlow.collectAsStateWithLifecycle()
    var showFirstTimeTutorial by remember {
        mutableStateOf(com.example.sharkmentor.ui.components.FirstTimeTutorialPrefs.shouldShowTutorial(context))
    }
    val errorBankViewModel: ErrorBankViewModel = viewModel()
    val dueQuestionsCount by errorBankViewModel.dueQuestionsCount.collectAsStateWithLifecycle(initialValue = 0)
    val sundayCheckInRequested by com.example.sharkmentor.core.coaching.PlanReadinessNavBus.openSundayCheckInRequested.collectAsStateWithLifecycle()
    var showSundayCheckInDialog by remember { mutableStateOf(false) }
    LaunchedEffect(sundayCheckInRequested, showSplash) {
        if (sundayCheckInRequested && !showSplash) {
            com.example.sharkmentor.core.coaching.PlanReadinessNavBus.consumeOpenSundayCheckIn()
            showSundayCheckInDialog = true
        }
    }
    val primaryTabRoutes = remember {
        setOf(
            Screen.Focus.route,
            Screen.Dashboard.route,
            Screen.Notes.route,
            Screen.Resources.route,
            Screen.Shark.route,
            Screen.ErrorBank.route
        )
    }

    val performCleanLogout: () -> Unit = remember(context, navController) {
        {
            com.example.sharkmentor.core.coaching.CoachingEngine.updateLivePresenceOfflineFirst(
                context = context,
                isStudying = false
            )
            GoogleServicesHelper.signOut()
            com.example.sharkmentor.core.util.AvatarHelper.clearLocalAvatar(context)
            com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context).clearSessionProfile()
            com.example.sharkmentor.data.repository.StreakRepository(context).clearLocalForLogout()
            com.example.sharkmentor.data.repository.CountdownRepository.getInstance(context).clearLocalForLogout()
            com.example.sharkmentor.core.coaching.BookCoachingRoleManager.clearInMemoryForLogout()
            com.example.sharkmentor.core.coaching.AiCoachLedgerManager.clearInMemoryForLogout()
            com.example.sharkmentor.core.util.SessionManager.setLinkedToInstitution(context, false)
            com.example.sharkmentor.core.util.SessionManager.setUserMode(context, "study")
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Root SharkMentorTheme is applied once in MainActivity to avoid double theming & redundant SideEffects
    if (showSplash) {
        com.example.sharkmentor.ui.splash.SplashScreen(onTimeout = { showSplash = false })
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
        // Deterministic Boolean-controlled Right Drawer (replaces RTL ModalNavigationDrawer which
        // snapped open on window resizes, notification intents, and returning from Video Activity!)
        var isRightDrawerOpen by remember { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        LaunchedEffect(currentDestination?.route) {
            isRightDrawerOpen = false
        }

        val isNotesRoute = currentDestination?.route == Screen.Notes.route
        val activeNoteEditor = isNoteEditorOpen && isNotesRoute
        val isNotesContext = isNotesRoute || activeNoteEditor
        Box(modifier = Modifier.fillMaxSize()) {
                    val isPaceKeep = currentDestination?.route == Screen.PaceKeep.route
                    val topBarRoutes = remember {
                        setOf(
                            Screen.Focus.route,
                            Screen.Dashboard.route,
                            Screen.Notes.route,
                            Screen.Resources.route,
                            Screen.Shark.route,
                            Screen.ErrorBank.route,
                            "ai_zen_coach"
                        )
                    }
                    val currentRoute = currentDestination?.route
                    val shouldShowTopBar = (currentRoute in topBarRoutes) && !isPaceKeep && !activeNoteEditor
                    val isImeOpen = WindowInsets.isImeVisible

                    // Auto-hide bottom bar after inactivity when enabled in Settings
                    val mainThemePrefs = remember(context) { ThemePreferences.initialize(context.applicationContext) }
                    val autoHideBottomBar by mainThemePrefs.autoHideBottomBar.collectAsStateWithLifecycle()
                    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    var isBottomBarAutoHidden by remember { mutableStateOf(false) }

                    LaunchedEffect(autoHideBottomBar, lastInteractionMs, currentRoute) {
                        if (!autoHideBottomBar) {
                            isBottomBarAutoHidden = false
                        } else {
                            isBottomBarAutoHidden = false
                            delay(5500L)
                            isBottomBarAutoHidden = true
                        }
                    }

                    // Global Music Player Hub state (note icon visible on all tabs except Settings)
                    LaunchedEffect(context) {
                        MusicPlayerHub.ensureInitialized(context)
                    }
                    val musicPlayerEnabled by MusicPlayerHub.isEnabled.collectAsStateWithLifecycle()
                    val musicSelectedSkin by MusicPlayerHub.selectedSkin.collectAsStateWithLifecycle()
                    val musicPlaybackState by MusicPlayerHub.playbackState.collectAsStateWithLifecycle()
                    val isHomeMusicPanelOpen by MusicPlayerHub.isHomePanelOpen.collectAsStateWithLifecycle()
                    val isFloatingMiniOpen by MusicPlayerHub.isFloatingMiniOpen.collectAsStateWithLifecycle()

                    val excludedMusicRoutes = remember {
                        setOf(
                            "settings",
                            "ai_settings",
                            "note_settings",
                            "countdown_settings",
                            Screen.Login.route,
                            Screen.PaceKeep.route
                        )
                    }
                    val isMusicTopBarVisible = musicPlayerEnabled &&
                        topBarSettings.isModuleVisible(TopBarModule.MUSIC) &&
                        currentRoute != null &&
                        currentRoute !in excludedMusicRoutes
                    val isOnHomeTab = currentRoute == Screen.Focus.route
                    val isMusicButtonActive = if (isOnHomeTab) isHomeMusicPanelOpen else isFloatingMiniOpen
                    val onToggleMusicUi: () -> Unit = {
                        if (isOnHomeTab) {
                            MusicPlayerHub.toggleHomePanel()
                        } else {
                            MusicPlayerHub.toggleFloatingMini()
                        }
                    }

                    // Restorable background video studio (when tapping the lock-screen/shade notification)
                    val videoRestoreRequested by CampVideoPlaybackController.restoreRequested.collectAsStateWithLifecycle()
                    val activePreviewSimulation by com.example.sharkmentor.core.simulation.SimulationLabEngine.activePreviewSimulation.collectAsStateWithLifecycle()

                    val shouldShowBottomBar = (currentRoute in primaryTabRoutes) &&
                        !isImeOpen &&
                        !activeNoteEditor &&
                        !isBottomBarAutoHidden

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(autoHideBottomBar) {
                                if (!autoHideBottomBar) return@pointerInput
                                awaitEachGesture {
                                    awaitPointerEvent(pass = PointerEventPass.Initial)
                                    lastInteractionMs = System.currentTimeMillis()
                                    if (isBottomBarAutoHidden) {
                                        isBottomBarAutoHidden = false
                                    }
                                }
                            }
                            .background(
                                if (isPaceKeep) androidx.compose.ui.graphics.Color(0xFF071426)
                                else MaterialTheme.colorScheme.background
                            ),
                    ) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            containerColor = if (isPaceKeep) androidx.compose.ui.graphics.Color(0xFF071426) else MaterialTheme.colorScheme.background,
                            topBar = {
                                if (shouldShowTopBar) {
                                    SharkMentorTopBar(
                                        currentRoute = currentDestination?.route,
                                        countdown = countdownText,
                                        isCampus = uiState.isCampus,
                                        topBarSettings = topBarSettings,
                                        isAnyFocusRunning = isAnyFocusRunning,
                                        elapsedFocusMinutes = elapsedFocusMinutes,
                                        isPaceKeep = isPaceKeep,
                                        weatherState = weatherState,
                                        weatherSettings = weatherSettings,
                                        hasUnreadDm = hasUnreadDm,
                                        onWeatherClick = { showWeatherDialog = true },
                                        onOpenSharkAi = { navController.navigate(Screen.Shark.route) { launchSingleTop = true } },
                                        onStartZenSound = { sound, msg ->
                                            com.example.sharkmentor.core.audio.ZenAudioManager.playSound(context, sound)
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onNavigateRoute = { route ->
                                            runCatching {
                                                navController.navigate(route) { launchSingleTop = true }
                                            }
                                        },
                                        onWellnessAck = { msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onOpenDmInbox = {
                                            com.example.sharkmentor.ui.components.PendingLeagueNavigationBus.openDmInboxDirectly.value = true
                                            com.example.sharkmentor.core.notification.SocialNotificationMonitor.clearUnreadDmIndicator()
                                            navController.navigate(Screen.SharkLeague.route) { launchSingleTop = true }
                                        },
                                        showMusicButton = isMusicTopBarVisible,
                                        isMusicActive = isMusicButtonActive,
                                        isMusicPlaying = musicPlaybackState.isPlaying,
                                        onMusicClick = onToggleMusicUi,
                                        onCountdownClick = {
                                            navController.navigate("countdown_settings") {
                                                launchSingleTop = true
                                            }
                                        },
                                        onMenuClick = { isRightDrawerOpen = true }
                                    )
                                }
                            },
                            bottomBar = {
                                AnimatedVisibility(
                                    visible = shouldShowBottomBar,
                                    enter = slideInVertically(initialOffsetY = { it }),
                                    exit = slideOutVertically(targetOffsetY = { it })
                                ) {
                                    SharkMentorBottomBar(
                                        currentDestination = currentDestination,
                                        onTabSelected = { screen ->
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState    = true
                                            }
                                        },
                                        dueQuestionsCount = dueQuestionsCount
                                    )
                                }
                            },
                        ) { innerPadding ->
                            val effectiveTopPadding = if (shouldShowTopBar) innerPadding.calculateTopPadding() else 0.dp
                            val effectiveBottomPadding = if (shouldShowBottomBar) innerPadding.calculateBottomPadding() else 0.dp

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = effectiveTopPadding,
                                        bottom = effectiveBottomPadding
                                    )
                            ) {
                            NavHost(
                                navController    = navController,
                                startDestination = Screen.Focus.route,
                                modifier         = Modifier.fillMaxSize(),
                                    enterTransition  = { fadeIn() },
                                    exitTransition   = { fadeOut() },
                                ) {
                        composable(Screen.Focus.route) {
                            HomeScreen(
                                onNavigateToSharkAi = { navController.navigate(Screen.Shark.route) { launchSingleTop = true } },
                                onNavigateToPlans = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                                onNavigateToRoute = { route ->
                                    runCatching {
                                        navController.navigate(route) { launchSingleTop = true }
                                    }
                                },
                                onLaunchSharkAiWithPrompt = { prompt ->
                                    PendingAiPromptBus.pendingPrompt.value = prompt
                                    runCatching {
                                        navController.navigate(Screen.Shark.route) { launchSingleTop = true }
                                    }
                                }
                            )
                        }
                        composable(Screen.Dashboard.route)     { PlansScreen(onNavigateToSchedule = { navController.navigate(Screen.Schedule.route) { launchSingleTop = true } }) }
                        composable(Screen.Resources.route) {
                            var isTopicOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
                            var isResOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
                            com.example.sharkmentor.ui.screens.GoalsScreen(
                                currentModule = com.example.sharkmentor.ui.screens.NavModule.LIBRARY,
                                isAddTopicDialogOpen = isTopicOpen,
                                onCloseTopicDialog = { isTopicOpen = false },
                                isAddResourceDialogOpen = isResOpen,
                                onCloseResourceDialog = { isResOpen = false }
                            )
                        }
                        composable(Screen.Shark.route)     { 
                            SharkScreen(
                                onNavigateToDetailed = { navController.navigate(Screen.DetailedAnalytics.route) { launchSingleTop = true } },
                                onNavigateToLeague   = { navController.navigate(Screen.SharkLeague.route) { launchSingleTop = true } },
                                onNavigateToPlans    = { target ->
                                    when (target) {
                                        "targets" -> navController.navigate(Screen.Resources.route) { launchSingleTop = true }
                                        "study_desk" -> navController.navigate(Screen.Focus.route) { launchSingleTop = true }
                                        "plans" -> navController.navigate(Screen.Dashboard.route) { launchSingleTop = true }
                                        else -> navController.navigate(Screen.Dashboard.route) { launchSingleTop = true }
                                    }
                                }
                            ) 
                        }
                        composable(Screen.DetailedAnalytics.route) { DetailedAnalyticsScreen(onBackClick = { navController.popBackStack() }) }
                        composable(Screen.History.route)   { HistoryScreen(onBackClick = { navController.popBackStack() }) }
                        composable(Screen.CoachNotes.route) { com.example.sharkmentor.ui.screens.CoachNotesScreen(onBackClick = { navController.popBackStack() }) }
                        composable(Screen.CoachChat.route)  { StudentCoachChatScreen(onBackClick = { navController.popBackStack() }) }
                        composable(Screen.SharkLeague.route) { SharkLeagueScreen(onBackClick = { navController.popBackStack() }) }
                        composable(Screen.InstitutionInfo.route) {
                            com.example.sharkmentor.ui.screens.InstitutionInfoScreen(
                                onBackClick      = { navController.popBackStack() },
                                onNavigateToChat = { navController.navigate(Screen.CoachChat.route) { launchSingleTop = true } },
                                onNavigateToSchedule = { navController.navigate(Screen.Schedule.route) { launchSingleTop = true } }
                            )
                        }
                        composable(Screen.Schedule.route) {
                            com.example.sharkmentor.ui.screens.ScheduleScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Notes.route) {
                            NotesScreen(
                                showTopBar = false,
                                onBackClick = { navController.popBackStack() },
                                onOpenDrawer = { isRightDrawerOpen = true },
                                importedNote = pendingImportedNote,
                                onImportedNoteConsumed = {
                                    pendingImportedNote = null
                                    onImportedNoteConsumed()
                                },
                                onEditorStateChanged = { isNoteEditorOpen = it }
                            )
                        }
                        composable(Screen.ErrorBank.route) { 
                            ErrorBankScreen(
                                onStartTraining = { route -> navController.navigate(route) },
                                onNavigateToSharkAi = {
                                    navController.navigate(Screen.Shark.route) {
                                        launchSingleTop = true
                                    }
                                }
                            ) 
                        }
                        composable(
                            route = "${Screen.ErrorTraining.route}?subject={subject}&topic={topic}&folderId={folderId}",
                            arguments = listOf(
                                androidx.navigation.navArgument("subject") {
                                    type = androidx.navigation.NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                                androidx.navigation.navArgument("topic") {
                                    type = androidx.navigation.NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                                androidx.navigation.navArgument("folderId") {
                                    type = androidx.navigation.NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) {
                            ErrorTrainingScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("settings") {
                            com.example.sharkmentor.ui.screens.SettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onOpenAiSettings = {
                                    navController.navigate("ai_settings") {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenNoteSettings = {
                                    navController.navigate("note_settings") {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenAppBlocker = { navController.navigate("app_blocker") },
                                onOpenAnalysis = { navController.navigate("analysis") },
                                onOpenHistory = { navController.navigate("history") },
                                onOpenZenRoom = { navController.navigate("zen_room") },
                                onOpenCountdownSettings = { navController.navigate("countdown_settings") },
                                onAccountDeleted = {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("ai_settings") {
                            AiSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("note_settings") {
                            NoteSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("zen_room") {
                            ZenRoomScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.PaceKeep.route) {
                            PaceKeepScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("ai_zen_coach") {
                            AiZenCoachScreen()
                        }
                        composable("countdown_settings") {
                            CountdownSettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("statistics") {
                            DetailedAnalyticsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("app_blocker") {
                            com.example.sharkmentor.ui.screens.AppBlockerScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("analysis") {
                            DetailedAnalyticsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Profile.route) {
                            ProfileScreen(
                                onBackClick = { navController.popBackStack() },
                                onLogoutClick = performCleanLogout,
                                onLoginClick = {
                                    navController.navigate(Screen.Login.route)
                                }
                            )
                        }
                        composable(Screen.Login.route) {
                            LoginScreen(
                                onLoginSuccess = {
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        runCatching {
                                            com.example.sharkmentor.data.repository.StreakRepository(context)
                                                .pullAndMergeFromCloud()
                                        }
                                        runCatching {
                                            com.example.sharkmentor.data.repository.CountdownRepository.getInstance(context)
                                                .pullAndMergeFromCloud()
                                        }
                                        runCatching {
                                            com.example.sharkmentor.data.sync.TopicCloudSyncManager
                                                .pullAndMergeFromCloud(context, forceServer = true)
                                        }
                                        runCatching {
                                            com.example.sharkmentor.core.coaching.BookCoachingRoleManager.pullFromCloudIfNewer(context)
                                            com.example.sharkmentor.core.coaching.AiCoachLedgerManager.pullFromCloudIfNewer(context)
                                        }
                                        runCatching {
                                            com.example.sharkmentor.core.widget.WidgetUpdateManager.updateAllWidgets(context)
                                        }
                                        runCatching {
                                            com.example.sharkmentor.service.sync.SyncWorker.enqueueForceFullSync(context)
                                        }
                                    }
                                    navController.navigate(Screen.Focus.route) {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }

                            // Draggable Compact Mini Music Window on all tabs outside HomeScreen — stays visible in Notes & PDFs!
                            if (isMusicTopBarVisible && !isOnHomeTab && isFloatingMiniOpen) {
                                DraggableMiniMusicPlayerWindow(
                                    playbackState = musicPlaybackState,
                                    selectedSkin = musicSelectedSkin,
                                    onSelectSkin = { chosenSkin ->
                                        MusicPlayerHub.setSkin(context, chosenSkin)
                                    },
                                    onMinimize = {
                                        MusicPlayerHub.setFloatingMiniOpen(false)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            }
                }

                activePreviewSimulation?.let { previewSim ->
                    com.example.sharkmentor.ui.screens.simulation.SimulationViewerDialog(
                        simulation = previewSim,
                        onDismiss = {
                            com.example.sharkmentor.core.simulation.SimulationLabEngine.closeSimulationPreview()
                        }
                    )
                }

                if (videoRestoreRequested) {
                    val payload = remember(videoRestoreRequested) {
                        CampVideoPlaybackController.peekRestorablePayload()
                    }
                    if (payload != null) {
                        CampVideoStudyStudioDialog(
                            campTitle = payload.campTitle,
                            subject = payload.subject,
                            initialEpisode = payload.activeEpisode,
                            allEpisodes = payload.allEpisodes,
                            resourceId = payload.resourceId,
                            onDismiss = {
                                CampVideoPlaybackController.consumeRestoreRequest()
                            }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            CampVideoPlaybackController.consumeRestoreRequest()
                        }
                    }
                }

                if (showReflectionDialog) {
                    DailyReflectionDialog(
                        onDismiss = { showReflectionDialog = false },
                        onSubmit = { score, note, monthlyNet ->
                            viewModel.saveDailyReflection(score, note, monthlyNet)
                            showReflectionDialog = false
                        }
                    )
                }
                if (showWeatherDialog) {
                    WeatherHourlyDialog(
                        weatherState = weatherState,
                        settings = weatherSettings,
                        onRefresh = { scope.launch { weatherRepository.refreshWeather(force = true) } },
                        onDismiss = { showWeatherDialog = false }
                    )
                }

                if (showSundayCheckInDialog) {
                    com.example.sharkmentor.ui.coaching.PlanReadinessCheckInDialog(
                        title = "Pazar Günü Haftalık Kontrol",
                        subtitle = "Yeni haftanın planı esnek ve eksiksiz kurgulansın diye bu haftaki konu hakimiyetini ve kitap ilerlemeni güncelle.",
                        confirmButtonLabel = "Güncelle ve Yeni Haftayı Kurgula",
                        secondarySaveOnlyLabel = "Sadece Kaydet",
                        onOpenGoalsOrLibrary = {
                            showSundayCheckInDialog = false
                            navController.navigate(Screen.Resources.route) { launchSingleTop = true }
                        },
                        onSaveOnly = {
                            showSundayCheckInDialog = false
                            android.widget.Toast.makeText(
                                context,
                                "Konu hakimiyeti ve kitap durumun yeni haftaya kaydedildi.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        onConfirmProceed = {
                            showSundayCheckInDialog = false
                            scope.launch {
                                val count = runCatching {
                                    com.example.sharkmentor.ui.coaching.rebalanceWeeklyPlanWithAiCoach(context)
                                }.getOrDefault(0)
                                val msg = if (count > 0) {
                                    "Yeni haftanın planı eksiklerini devrederek $count blok halinde kurgulandı."
                                } else {
                                    "Güncel durumun kaydedildi."
                                }
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                if (count > 0) {
                                    navController.navigate(Screen.Dashboard.route) { launchSingleTop = true }
                                }
                            }
                        },
                        onDismiss = { showSundayCheckInDialog = false }
                    )
                }

                if (showFirstTimeTutorial) {
                    com.example.sharkmentor.ui.components.FirstTimeTutorialOverlay(
                        onDismiss = { showFirstTimeTutorial = false }
                    )
                }

                // Personal J.A.R.V.I.S. Holographic Launch Briefing HUD (shown once per launch ONLY when JARVIS is unlocked)
                LaunchedEffect(context) {
                    com.example.sharkmentor.core.ai.SharkTokenQuotaManager.ensureLoaded(context)
                }
                val jarvisQuotaSnapshot by com.example.sharkmentor.core.ai.SharkTokenQuotaManager.snapshot.collectAsStateWithLifecycle()
                var jarvisBriefingDismissed by remember { mutableStateOf(false) }
                LaunchedEffect(jarvisQuotaSnapshot.isUnlimitedUnlocked) {
                    if (!jarvisQuotaSnapshot.isUnlimitedUnlocked) {
                        jarvisBriefingDismissed = false
                    }
                }
                if (!showFirstTimeTutorial &&
                    jarvisQuotaSnapshot.isUnlimitedUnlocked &&
                    !jarvisBriefingDismissed &&
                    currentDestination?.route != Screen.Login.route
                ) {
                    com.example.sharkmentor.ui.components.JarvisLaunchBriefingOverlay(
                        weatherState = weatherState,
                        weatherSettings = weatherSettings,
                        dueQuestionsCount = dueQuestionsCount,
                        onNavigate = { targetRoute ->
                            if (currentDestination?.route != targetRoute) {
                                runCatching {
                                    navController.navigate(targetRoute) { launchSingleTop = true }
                                }
                            }
                        },
                        onDismiss = { jarvisBriefingDismissed = true }
                    )
                }

                com.example.sharkmentor.ui.components.WellnessReminderSmartwatchOverlay()
            }

            // Deterministic Right-Side Slide-Over Control Panel (NEVER opens spontaneously on resume/resize!)
            if (isRightDrawerOpen) {
                androidx.activity.compose.BackHandler { isRightDrawerOpen = false }
            }
            AnimatedVisibility(
                visible = isRightDrawerOpen,
                enter = fadeIn(androidx.compose.animation.core.tween(180)),
                exit = fadeOut(androidx.compose.animation.core.tween(160))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.44f))
                        .clickable { isRightDrawerOpen = false }
                )
            }
            AnimatedVisibility(
                visible = isRightDrawerOpen,
                enter = slideInHorizontally(androidx.compose.animation.core.tween(220)) { it },
                exit = slideOutHorizontally(androidx.compose.animation.core.tween(190)) { it },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(0.84f)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                    shadowElevation = 12.dp
                ) {
                    val drawerMusicEnabled by MusicPlayerHub.isEnabled.collectAsStateWithLifecycle()
                    ControlPanelContent(
                        themeMode           = themeMode,
                        onThemeToggle       = { themePrefs.setThemeMode(it) },
                        currentPalette      = currentPalette,
                        onPaletteChange     = { themePrefs.setPalette(it) },
                        onDismiss           = { isRightDrawerOpen = false },
                        currentRoute        = currentDestination?.route,
                        dueQuestionsCount   = dueQuestionsCount,
                        onToggleMusicPlayer = if (drawerMusicEnabled) {
                            {
                                if (currentDestination?.route == Screen.Focus.route) {
                                    MusicPlayerHub.toggleHomePanel()
                                } else {
                                    MusicPlayerHub.toggleFloatingMini()
                                }
                            }
                        } else null,
                        onNavigate          = { route ->
                            isRightDrawerOpen = false
                            if (currentDestination?.route != route) {
                                navController.navigate(route) { launchSingleTop = true }
                            }
                        },
                        onLogout            = {
                            isRightDrawerOpen = false
                            performCleanLogout()
                        }
                    )
                }
            }
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top App Bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharkMentorTopBar(
    currentRoute: String? = null,
    countdown: String,
    topBarSettings: TopBarSettings,
    isAnyFocusRunning: Boolean = false,
    elapsedFocusMinutes: Int = 0,
    isPaceKeep: Boolean = false,
    isCampus: Boolean,
    weatherState: WeatherUiState,
    weatherSettings: WeatherSettings,
    hasUnreadDm: Boolean = false,
    showMusicButton: Boolean = true,
    isMusicActive: Boolean = false,
    isMusicPlaying: Boolean = false,
    onMusicClick: () -> Unit = {},
    onWeatherClick: () -> Unit,
    onOpenSharkAi: () -> Unit,
    onStartZenSound: (com.example.sharkmentor.core.audio.ZenSound, String) -> Unit = { _, _ -> },
    onNavigateRoute: (String) -> Unit = {},
    onWellnessAck: (String) -> Unit = {},
    onOpenDmInbox: () -> Unit = {},
    onCountdownClick: () -> Unit = {},
    onMenuClick: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hideForFocus = topBarSettings.hideDuringFocus && (isAnyFocusRunning || isPaceKeep)
    val showTopBarWidgets = !topBarSettings.hideTopBarMenu && !hideForFocus
    val showBubbleFrame = !topBarSettings.hideBubbles

    TopAppBar(
        expandedHeight = 48.dp,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp)
            ) {
                // App Logo is always fixed on the left
                SharkMentorBranding(currentRoute = currentRoute, countdown = countdown, isCampus = isCampus)
                topBarSettings.moduleOrder.forEach { module ->
                    if (module == TopBarModule.BRANDING || !topBarSettings.isModuleVisible(module)) return@forEach
                    when (module) {
                        TopBarModule.BRANDING -> Unit
                        TopBarModule.MUSIC -> {
                            if (showMusicButton) {
                                Surface(
                                    onClick = onMusicClick,
                                    shape = CircleShape,
                                    color = if (isMusicActive || isMusicPlaying) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                    },
                                    contentColor = if (isMusicActive || isMusicPlaying) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isMusicActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = "SharkMusic",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        TopBarModule.WEATHER -> {
                            if (showTopBarWidgets && (isLandscape || !topBarSettings.isModuleVisible(TopBarModule.AI_BAR))) {
                                TopBarWeatherPill(
                                    weatherState = weatherState,
                                    settings = weatherSettings,
                                    showBubble = showBubbleFrame,
                                    onClick = onWeatherClick
                                )
                            }
                        }
                        TopBarModule.AI_BAR -> {
                            if (showTopBarWidgets && isLandscape) {
                                TopBarAiShortcutBox(
                                    onClick = { prompt ->
                                        PendingAiPromptBus.pendingPrompt.value = prompt
                                        onOpenSharkAi()
                                    },
                                    modifier = Modifier.weight(1f),
                                    topBarSettings = topBarSettings,
                                    showBubble = showBubbleFrame,
                                    weatherState = weatherState,
                                    isAnyFocusRunning = isAnyFocusRunning,
                                    elapsedFocusMinutes = elapsedFocusMinutes,
                                    onStartZenSound = onStartZenSound,
                                    onNavigateRoute = onNavigateRoute,
                                    onWellnessAck = onWellnessAck
                                )
                            }
                        }
                        TopBarModule.DM -> {
                            if (showTopBarWidgets) {
                                com.example.sharkmentor.ui.components.TopBarDmShortcutButton(
                                    hasUnread = hasUnreadDm,
                                    showBubble = showBubbleFrame,
                                    onClick = onOpenDmInbox
                                )
                            }
                        }
                        TopBarModule.COUNTDOWN -> {
                            if (showTopBarWidgets && countdown.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { onCountdownClick() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Event,
                                            contentDescription = "Hedef Sınav Geri Sayım",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = countdown,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menü",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
    )
}

@Composable
private fun SharkMentorBranding(
    currentRoute: String? = null,
    countdown: String,
    isCampus: Boolean
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val isLightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val studioRed = if (isLightSurface) Color(0xFFDC2626) else Color(0xFFF87171)

    when (currentRoute) {
        Screen.Shark.route -> {
            // Aurora Borealis flowing gradient + 3D extruded depth on "AI" (no STUDY subtitle!)
            val auroraTransition = rememberInfiniteTransition(label = "aurora_ai_brand")
            val auroraShift by auroraTransition.animateFloat(
                initialValue = 0f,
                targetValue = 180f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "aurora_shift"
            )
            val auroraColors = if (isLightSurface) {
                listOf(
                    Color(0xFF0284C7),
                    Color(0xFF059669),
                    Color(0xFF7C3AED),
                    Color(0xFF0EA5E9),
                    Color(0xFFDB2777)
                )
            } else {
                listOf(
                    Color(0xFF38BDF8),
                    Color(0xFF34D399),
                    Color(0xFFA855F7),
                    Color(0xFF22D3EE),
                    Color(0xFFF472B6)
                )
            }
            val auroraBrush = Brush.linearGradient(
                colors = auroraColors,
                start = Offset(auroraShift - 60f, 0f),
                end = Offset(auroraShift + 110f, 44f)
            )
            val depthFar = if (isLightSurface) Color(0xFF1E3A8A).copy(alpha = 0.42f) else Color(0xFF312E81).copy(alpha = 0.85f)
            val depthNear = if (isLightSurface) Color(0xFF0E7490).copy(alpha = 0.55f) else Color(0xFF0891B2).copy(alpha = 0.72f)
            val aiBaseStyle = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 0.8.sp,
                lineHeight = 19.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Shark",
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 19.sp),
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    maxLines = 1
                )
                Box(contentAlignment = Alignment.CenterStart) {
                    // 3D Extruded Back Layer 2
                    Text(
                        text = "AI",
                        style = aiBaseStyle,
                        color = depthFar,
                        modifier = Modifier.offset(x = 1.8.dp, y = 1.8.dp)
                    )
                    // 3D Extruded Mid Layer 1
                    Text(
                        text = "AI",
                        style = aiBaseStyle,
                        color = depthNear,
                        modifier = Modifier.offset(x = 0.9.dp, y = 0.9.dp)
                    )
                    // Foreground Aurora Borealis Gradient Layer
                    Text(
                        text = "AI",
                        style = aiBaseStyle.copy(
                            brush = auroraBrush,
                            shadow = Shadow(
                                color = auroraColors.first().copy(alpha = if (isLightSurface) 0.28f else 0.65f),
                                offset = Offset(0f, 0f),
                                blurRadius = 8f
                            )
                        )
                    )
                }
            }
        }

        Screen.Notes.route -> {
            // SharkStudio wordmark (no STUDY subtitle!)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = onSurface, fontWeight = FontWeight.Bold)) {
                            append("Shark")
                        }
                        withStyle(
                            SpanStyle(
                                color = studioRed,
                                fontFamily = FontFamily.Cursive,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        ) {
                            append("Studio")
                        }
                    },
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.width(IntrinsicSize.Max),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "SharkMentor",
                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 17.sp),
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (SpecialMemberConfig.isEmreBey()) "Emrw" else if (isCampus) "C A M P U S" else "S T U D Y",
                        fontSize = 9.sp,
                        letterSpacing = 3.sp,
                        lineHeight = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SharkMentorBottomBar(currentDestination: androidx.navigation.NavDestination?, onTabSelected: (Screen) -> Unit, dueQuestionsCount: Int = 0) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Screen.bottomBarItems.forEach { screen ->
                @Suppress("SENSELESS_COMPARISON")
                if (screen != null && screen.icon != null) {
                    val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = isSelected,
                        onClick  = { onTabSelected(screen) },
                        icon     = {
                            BadgedBox(badge = {
                                if (screen == Screen.ErrorBank && dueQuestionsCount > 0) {
                                    Badge(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .offset(x = (-2).dp, y = 2.dp),
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }) {
                                Icon(screen.icon, contentDescription = screen.label, modifier = Modifier.size(24.dp))
                            }
                        },
                        label           = { Text(screen.label, fontSize = 10.sp) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor   = MaterialTheme.colorScheme.primary,
                            indicatorColor      = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainScreenPreview() {
    SharkMentorTheme(palette = ThemePalette.SOOTY) {
        // Preview uses theme defaults; ThemePreferences is not available in preview
    }
}

