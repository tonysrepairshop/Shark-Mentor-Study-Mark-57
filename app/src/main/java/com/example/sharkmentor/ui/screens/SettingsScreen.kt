package com.example.sharkmentor.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.WbSunny
import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.example.sharkmentor.data.topbar.TopBarPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.TimePickerDialog
import androidx.compose.material.icons.filled.Album
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.example.sharkmentor.data.topbar.TopBarModule
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.audio.MusicPlayerHub
import com.example.sharkmentor.core.audio.MusicPlayerSkin
import com.example.sharkmentor.core.badges.SharkBadgeCatalog
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.data.AppThemeMode
import com.example.sharkmentor.core.data.BlueLightFilterMode
import com.example.sharkmentor.core.data.CircadianScheduleMode
import com.example.sharkmentor.core.data.ThemePalette
import com.example.sharkmentor.core.data.ThemePreferences
import com.example.sharkmentor.core.theme.formatMinuteOfDay
import com.example.sharkmentor.core.util.AppIconManager
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.util.SharkAppIcon
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.service.sync.SyncWorker
import com.example.sharkmentor.ui.components.ReflectionTimeDialog
import kotlinx.coroutines.CoroutineScope
import com.example.sharkmentor.ui.components.SpecialMemberConfig
import com.example.sharkmentor.data.weather.WeatherRepository
import com.example.sharkmentor.data.weather.GeocodingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onOpenNoteSettings: () -> Unit = {},
    onOpenAppBlocker: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenZenRoom: () -> Unit = {},
    onOpenCountdownSettings: () -> Unit = {},
    onAccountDeleted: () -> Unit = {}
) {
    var activeSubPage by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = activeSubPage != null) {
        activeSubPage = when (activeSubPage) {
            "topbar_weather", "topbar_texts" -> "topbar_hub"
            else -> null
        }
    }
    val context = LocalContext.current
    val topBarPrefs = remember(context) { TopBarPreferences.getInstance(context) }
    val topBarSettings by topBarPrefs.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showTimeDialog by remember { mutableStateOf(false) }
    var showBlockedUsersDialog by remember { mutableStateOf(false) }
    var blockedUsersMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoadingBlockedUsers by remember { mutableStateOf(false) }

    LaunchedEffect(showBlockedUsersDialog) {
        if (!showBlockedUsersDialog) return@LaunchedEffect
        blockedUsersMap = SharkBadgeCatalog.getBlockedUsersMap(context)
        isLoadingBlockedUsers = true
        withContext(Dispatchers.IO) {
            val auth = GoogleServicesHelper.getFirebaseAuth(context)
            val uid = GoogleServicesHelper.getCurrentUser(auth, context)?.uid
            val db = GoogleServicesHelper.getFirebaseFirestore(context)
            if (!uid.isNullOrBlank() && uid != "local_student" && uid != "student_default" && db != null) {
                runCatching {
                    val studentDoc = db.collection("students").document(uid).get().await()
                    val remoteBlocked = (studentDoc.get("blockedUserIds") as? List<*>)
                        ?.mapNotNull { it?.toString()?.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    if (remoteBlocked.isNotEmpty()) {
                        SharkBadgeCatalog.mergeBlockedUserIds(context, remoteBlocked)
                    }
                }
                runCatching {
                    val blocksSnap = db.collection("user_blocks").document(uid).collection("blocked").get().await()
                    val remoteIds = blocksSnap.documents.map { it.id.trim() }.filter { it.isNotBlank() }
                    if (remoteIds.isNotEmpty()) {
                        SharkBadgeCatalog.mergeBlockedUserIds(context, remoteIds)
                    }
                    blocksSnap.documents.forEach { doc ->
                        val blockedName = doc.getString("blockedName").orEmpty().trim()
                        if (blockedName.isNotBlank()) {
                            SharkBadgeCatalog.saveBlockedUserName(context, doc.id, blockedName)
                        }
                    }
                }
                val currentMap = SharkBadgeCatalog.getBlockedUsersMap(context)
                currentMap.forEach { (blockedUid, existingName) ->
                    if (existingName.isBlank()) {
                        val resolved = runCatching {
                            val st = db.collection("students").document(blockedUid).get().await()
                            (st.getString("name")
                                ?: st.getString("displayName")
                                ?: st.getString("fullName")
                                ?: "").trim()
                        }.getOrDefault("").ifBlank {
                            runCatching {
                                val lg = db.collection("institutions").document("GLOBAL_INDIVIDUAL")
                                    .collection("weeklyLeague").document(blockedUid).get().await()
                                (lg.getString("displayName") ?: "").trim()
                            }.getOrDefault("")
                        }
                        if (resolved.isNotBlank()) {
                            SharkBadgeCatalog.saveBlockedUserName(context, blockedUid, resolved)
                        }
                    }
                }
            }
        }
        blockedUsersMap = SharkBadgeCatalog.getBlockedUsersMap(context)
        isLoadingBlockedUsers = false
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val themePrefs = remember { ThemePreferences.instance }
    val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle()
    val currentPalette by themePrefs.palette.collectAsStateWithLifecycle()
    val circadianMode by themePrefs.circadianScheduleMode.collectAsStateWithLifecycle()
    val circadianStartMin by themePrefs.circadianStartMinute.collectAsStateWithLifecycle()
    val circadianEndMin by themePrefs.circadianEndMinute.collectAsStateWithLifecycle()
    val blueLightMode by themePrefs.blueLightMode.collectAsStateWithLifecycle()
    val blueLightIntensity by themePrefs.blueLightIntensity.collectAsStateWithLifecycle()
    val blueLightStartMin by themePrefs.blueLightStartMinute.collectAsStateWithLifecycle()
    val blueLightEndMin by themePrefs.blueLightEndMinute.collectAsStateWithLifecycle()
    val keepScreenOnStopwatch by themePrefs.keepScreenOnInStopwatch.collectAsStateWithLifecycle()
    val autoHideBottomBar by themePrefs.autoHideBottomBar.collectAsStateWithLifecycle()

    LaunchedEffect(context) {
        MusicPlayerHub.ensureInitialized(context)
    }
    DisposableEffect(lifecycleOwner, context) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                MusicPlayerHub.refreshPermissionsAndSessions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val musicPlayerEnabled by MusicPlayerHub.isEnabled.collectAsStateWithLifecycle()
    val musicSelectedSkin by MusicPlayerHub.selectedSkin.collectAsStateWithLifecycle()
    val mediaAccessGranted by MusicPlayerHub.mediaAccessGranted.collectAsStateWithLifecycle()

    val solarTimes = remember(themeMode, circadianMode, blueLightMode) {
        themePrefs.resolveTodaySolarTimes()
    }

    fun pickMinuteOfDay(initialMinute: Int, onPicked: (Int) -> Unit) {
        val h = ((initialMinute / 60) % 24 + 24) % 24
        val m = ((initialMinute % 60) + 60) % 60
        TimePickerDialog(
            context,
            { _, pickedHour, pickedMinute ->
                onPicked(pickedHour * 60 + pickedMinute)
            },
            h,
            m,
            true
        ).show()
    }

    if (showTimeDialog) {
        ReflectionTimeDialog(
            onDismiss = { showTimeDialog = false },
            onTimeSaved = { _, _ -> }
        )
    }

    if (showBlockedUsersDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBlockedUsersDialog = false },
            title = {
                Text(
                    text = "Engellenen Kullanıcılar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                when {
                    isLoadingBlockedUsers && blockedUsersMap.isEmpty() -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text("Yükleniyor...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    blockedUsersMap.isEmpty() -> {
                        Text(
                            text = "Engellediğiniz kullanıcı bulunmuyor.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            blockedUsersMap.entries.forEach { (blockedUid, storedName) ->
                                val displayName = storedName.ifBlank { "Kullanıcı (${blockedUid.take(6)})" }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val myUid = GoogleServicesHelper.getCurrentUser(
                                                        GoogleServicesHelper.getFirebaseAuth(context),
                                                        context
                                                    )?.uid ?: "local_student"
                                                    SharkBadgeCatalog.setUserBlocked(
                                                        context = context,
                                                        myUid = myUid,
                                                        targetUid = blockedUid,
                                                        blocked = false
                                                    )
                                                    blockedUsersMap = SharkBadgeCatalog.getBlockedUsersMap(context)
                                                    snackbarHostState.showSnackbar("$displayName engeli kaldırıldı.")
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Kaldır",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedUsersDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    var isSyncingNow by remember { mutableStateOf(false) }
    var showAppIconPicker by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                expandedHeight = 50.dp,
                title = {
                    Text(
                        when (activeSubPage) {
                            "coaching_support" -> "Koçluk & Çalışma"
                            "focus_wellness" -> "Hatırlatmalar"
                            "topbar_hub" -> "Üst Bar & Hava Durumu"
                            "topbar_weather" -> "Hava Durumu"
                            "topbar_texts" -> "Üst Bar Yazıları"
                            else -> "Ayarlar"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (activeSubPage) {
                                "topbar_weather", "topbar_texts" -> activeSubPage = "topbar_hub"
                                "topbar_hub", "coaching_support", "focus_wellness" -> activeSubPage = null
                                else -> onBackClick()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        when (activeSubPage) {
            null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Çalışma & Yapay Zeka
                    SettingsGroupCard(title = "Çalışma & Yapay Zeka") {
                        SettingRow(
                            icon = Icons.Default.Route,
                            label = "Koçluk & Çalışma Saatleri",
                            subtitle = "Günlük hedef ve çalışma başlıkları",
                            onClick = { activeSubPage = "coaching_support" }
                        )
                        SettingsCardDivider()
                        SettingRow(
                            icon = Icons.Default.AutoAwesome,
                            label = "Yapay Zeka & Video Stüdyosu",
                            subtitle = "Model seçimi ve reklam engelleyici",
                            onClick = onOpenAiSettings
                        )
                        SettingsCardDivider()
                        SettingRow(
                            icon = Icons.Default.EditNote,
                            label = "Not & Çizim",
                            subtitle = "Kalem davranışı ve not içi AI",
                            onClick = onOpenNoteSettings
                        )
                    }

                    // 2. Görünüm & Göz Koruma (Collapsed by default so Settings stays calm & scannable)
                    ExpandableSettingsGroupCard(
                        title = "Görünüm",
                        subtitle = "Tema, renk paleti, sirkadiyen saat ve uygulama simgesi",
                        initiallyExpanded = false
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ThemeSelectorSection(
                                themeMode = themeMode,
                                onThemeModeToggle = { mode ->
                                    if (com.example.sharkmentor.core.theme.ThemeManager.isZenMode.value) {
                                        com.example.sharkmentor.core.theme.ThemeManager.setZenMode(context, false)
                                    }
                                    themePrefs.setThemeMode(mode)
                                },
                                currentPalette = currentPalette,
                                onSelectPalette = { palette ->
                                    if (com.example.sharkmentor.core.theme.ThemeManager.isZenMode.value) {
                                        com.example.sharkmentor.core.theme.ThemeManager.setZenMode(context, false)
                                    }
                                    themePrefs.setPalette(palette)
                                }
                            )

                            // Configurable Circadian ("Döngü") Schedule Controls
                            if (themeMode == AppThemeMode.CIRCADIAN) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                CircadianScheduleMode.LOCATION_SUNSET to "Konuma Göre",
                                                CircadianScheduleMode.CUSTOM_TIME to "Özel Saat"
                                            ).forEach { (modeOpt, label) ->
                                                val chosen = circadianMode == modeOpt
                                                Surface(
                                                    onClick = { themePrefs.setCircadianScheduleMode(modeOpt) },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                                ) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 10.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (circadianMode == CircadianScheduleMode.LOCATION_SUNSET) {
                                            Text(
                                                text = "Gün batımı ${solarTimes.formatSunset()} • Gün doğumu ${solarTimes.formatSunrise()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        pickMinuteOfDay(circadianStartMin) { newStart ->
                                                            themePrefs.setCircadianCustomWindow(newStart, circadianEndMin)
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)
                                                ) {
                                                    Text("Gece: ${formatMinuteOfDay(circadianStartMin)}", fontSize = 11.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        pickMinuteOfDay(circadianEndMin) { newEnd ->
                                                            themePrefs.setCircadianCustomWindow(circadianStartMin, newEnd)
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)
                                                ) {
                                                    Text("Gündüz: ${formatMinuteOfDay(circadianEndMin)}", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        SettingsCardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAppIconPicker = !showAppIconPicker }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Uygulama Simgesi",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (showAppIconPicker) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (showAppIconPicker) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                            ) {
                                AppIconSelectorSection(snackbarHostState = snackbarHostState)
                            }
                        }

                        SettingsCardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { themePrefs.setKeepScreenOnInStopwatch(!keepScreenOnStopwatch) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                                Column {
                                    Text(
                                        text = "Kronometrede Ekran Açık Kalsın",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Yalnızca kronometre çalışırken aktif olur",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = keepScreenOnStopwatch,
                                onCheckedChange = { themePrefs.setKeepScreenOnInStopwatch(it) }
                            )
                        }
                        SettingsCardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { themePrefs.setAutoHideBottomBar(!autoHideBottomBar) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                                Column {
                                    Text(
                                        text = "Alt Barı Otomatik Gizle",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Ekrana dokunulmadığında alt çubuğu gizler, dokununca geri getirir",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = autoHideBottomBar,
                                onCheckedChange = { themePrefs.setAutoHideBottomBar(it) }
                            )
                        }

                    }

                    // 2.5 Sağlık & Ergonomi Klasörü (Göz Koruma + Su + Duruş Hatırlatıcıları)
                    ExpandableSettingsGroupCard(
                        title = "Sağlık & Ergonomi",
                        subtitle = "Göz koruma (mavi ışık filtresi), su ve duruş hatırlatıcıları",
                        initiallyExpanded = false
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NightlightRound,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Göz Koruma (Mavi Işık Filtresi)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    BlueLightFilterMode.OFF to "Kapalı",
                                    BlueLightFilterMode.MANUAL_ON to "Açık",
                                    BlueLightFilterMode.LOCATION_SUNSET to "Konum",
                                    BlueLightFilterMode.CUSTOM_TIME to "Saat"
                                ).forEachIndexed { idx, (modeItem, title) ->
                                    SegmentedButton(
                                        selected = blueLightMode == modeItem,
                                        onClick = { themePrefs.setBlueLightMode(modeItem) },
                                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 4)
                                    ) {
                                        Text(title, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }

                            if (blueLightMode != BlueLightFilterMode.OFF) {
                                if (blueLightMode == BlueLightFilterMode.LOCATION_SUNSET) {
                                    Text(
                                        text = "Aktif aralık: ${solarTimes.formatSunset()} – ${solarTimes.formatSunrise()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (blueLightMode == BlueLightFilterMode.CUSTOM_TIME) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                pickMinuteOfDay(blueLightStartMin) { newStart ->
                                                    themePrefs.setBlueLightCustomWindow(newStart, blueLightEndMin)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)
                                        ) {
                                            Text("Başlangıç: ${formatMinuteOfDay(blueLightStartMin)}", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pickMinuteOfDay(blueLightEndMin) { newEnd ->
                                                    themePrefs.setBlueLightCustomWindow(blueLightStartMin, newEnd)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)
                                        ) {
                                            Text("Bitiş: ${formatMinuteOfDay(blueLightEndMin)}", fontSize = 11.sp)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Sıcaklık",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = blueLightIntensity,
                                        onValueChange = { themePrefs.setBlueLightIntensity(it) },
                                        valueRange = 0.08f..0.48f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        SettingsCardDivider()
                        SettingRow(
                            icon = Icons.Default.WaterDrop,
                            label = "Su & Duruş Hatırlatıcıları",
                            subtitle = "Su içme, oturma duruşu ve gün değerlendirmesi",
                            onClick = { activeSubPage = "focus_wellness" }
                        )
                    }

                    // 3. Üst Bar & Müzik Stüdyosu (Customizable TopBar + Music Player unified!)
                    ExpandableSettingsGroupCard(
                        title = "Üst Bar & Müzik",
                        subtitle = "Üst bar modüllerini sırala, hava durumu/yazılar ve müzik teması",
                        initiallyExpanded = false
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Üst Bar Düzeni",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(
                                    onClick = {
                                        topBarPrefs.resetTopBarLayout()
                                        MusicPlayerHub.setEnabled(context, true)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Varsayılan", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Live Top Bar Order Preview Strip
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LazyRow(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val visibleOrdered = topBarSettings.moduleOrder.filter { it != TopBarModule.BRANDING && topBarSettings.isModuleVisible(it) }
                                    if (visibleOrdered.isEmpty()) {
                                        item {
                                            Text(
                                                text = "Üst bar modülleri gizli",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        items(visibleOrdered, key = { "preview_${it.name}" }) { mod ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = mod.label,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Per-module visibility, left/right reorder & size controls
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val editableModules = topBarSettings.moduleOrder.filter { it != TopBarModule.BRANDING }
                                editableModules.forEachIndexed { index, mod ->
                                    val isVisible = topBarSettings.isModuleVisible(mod)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = mod.label,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                IconButton(
                                                    onClick = { topBarPrefs.moveModule(mod, -1) },
                                                    enabled = index > 0,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowBack, contentDescription = "Sola Taşı", modifier = Modifier.size(15.dp))
                                                }
                                                IconButton(
                                                    onClick = { topBarPrefs.moveModule(mod, 1) },
                                                    enabled = index < editableModules.lastIndex,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowForward, contentDescription = "Sağa Taşı", modifier = Modifier.size(15.dp))
                                                }
                                                Switch(
                                                    checked = isVisible,
                                                    onCheckedChange = { checked ->
                                                        topBarPrefs.setModuleVisible(mod, checked)
                                                        if (mod == TopBarModule.MUSIC) {
                                                            MusicPlayerHub.setEnabled(context, checked)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            SettingRow(
                                icon = Icons.Default.Tune,
                                label = "Üst Bar Yazıları & Hava Durumu",
                                subtitle = "Şehir, animasyon, özel sözler ve sınav sayacı",
                                onClick = { activeSubPage = "topbar_hub" }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            // Media Access + Open Spotify / Open YouTube Music (clear wording!)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Medya Kontrol İzni",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (mediaAccessGranted) "Aktif • Çalan şarkı otomatik algılanır" else "Spotify ve YouTube Music kontrolü için gerekli",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (mediaAccessGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!mediaAccessGranted) {
                                    OutlinedButton(
                                        onClick = { MusicPlayerHub.openMediaAccessSettings(context) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("İzin Ver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Aktif",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        MusicPlayerHub.connectMusicProvider(context, MusicPlayerHub.PKG_SPOTIFY)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Spotify'ı Aç", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        MusicPlayerHub.connectMusicProvider(context, MusicPlayerHub.PKG_YOUTUBE_MUSIC)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("YouTube Music'i Aç", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Text(
                                text = "Müzik Oynatıcı Teması",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                items(MusicPlayerSkin.entries, key = { it.id }) { skin ->
                                    val isChosen = musicSelectedSkin == skin
                                    Surface(
                                        onClick = { MusicPlayerHub.setSkin(context, skin) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Text(
                                            text = skin.displayTitle,
                                            fontSize = 12.sp,
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isChosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Odak Araçları & Kayıtlar (Compact 2x2 Grid)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Odak Araçları & Kayıtlar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsQuickTile(
                                icon = Icons.Default.SelfImprovement,
                                label = "Zen Odası",
                                onClick = onOpenZenRoom,
                                modifier = Modifier.weight(1f)
                            )
                            SettingsQuickTile(
                                icon = Icons.Default.Block,
                                label = "Uygulama Engelleyici",
                                onClick = onOpenAppBlocker,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsQuickTile(
                                icon = Icons.Default.Analytics,
                                label = "Detaylı Analiz",
                                onClick = onOpenAnalysis,
                                modifier = Modifier.weight(1f)
                            )
                            SettingsQuickTile(
                                icon = Icons.Default.History,
                                label = "Çalışma Geçmişi",
                                onClick = onOpenHistory,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 5. Bulut & Hesap
                    SettingsGroupCard(title = "Bulut & Hesap") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSyncingNow) {
                                    isSyncingNow = true
                                    coroutineScope.launch {
                                        val restoredTopics = withContext(Dispatchers.IO) {
                                            runCatching { CoachingEngine.flushPendingPresenceIfConnected(context) }
                                            runCatching { com.example.sharkmentor.data.sync.CloudSyncRepository(context).syncAll() }
                                            val topicCount = runCatching {
                                                com.example.sharkmentor.data.sync.TopicCloudSyncManager
                                                    .pullAndMergeFromCloud(context, forceServer = true)
                                            }.getOrDefault(-1)
                                            runCatching {
                                                com.example.sharkmentor.core.coaching.BookCoachingRoleManager.pullFromCloudIfNewer(context)
                                                com.example.sharkmentor.core.coaching.BookCoachingRoleManager.pushToCloudIfSignedIn(context)
                                                com.example.sharkmentor.core.coaching.AiCoachLedgerManager.pullFromCloudIfNewer(context)
                                                com.example.sharkmentor.core.coaching.AiCoachLedgerManager.pushToCloudIfSignedIn(context)
                                            }
                                            runCatching { SyncWorker.enqueueForceFullSync(context) }
                                            topicCount
                                        }
                                        isSyncingNow = false
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val msg = if (restoredTopics >= 0) {
                                            "Senkronizasyon tamamlandı ($restoredTopics konu güncel)."
                                        } else {
                                            "Senkronizasyon başlatıldı."
                                        }
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (isSyncingNow) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSyncingNow) "Senkronize Ediliyor..." else "Verileri Şimdi Senkronize Et",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Konu hakimiyeti ve hedefleri bulutla eşitle",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        SettingsCardDivider()
                        SettingRow(
                            icon = Icons.Default.PersonOff,
                            label = "Engellenen Kullanıcılar",
                            onClick = { showBlockedUsersDialog = true }
                        )
                        SettingsCardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { executePermanentAccountDeletion(context, onAccountDeleted) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(21.dp)
                            )
                            Text(
                                text = "Hesabı Kalıcı Sil",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            "coaching_support" -> {
                com.example.sharkmentor.ui.coaching.ComprehensiveSupportSettingsScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            "focus_wellness" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    FocusWellnessSettingsContent(
                        prefs = topBarPrefs,
                        onOpenReflectionTime = { showTimeDialog = true }
                    )
                }
            }

            "topbar_hub" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    TopBarControlHubContent(
                        settings = topBarSettings,
                        onToggleHideBubbles = topBarPrefs::setHideBubbles,
                        onToggleHideTopBarMenu = topBarPrefs::setHideTopBarMenu,
                        onToggleHideDuringFocus = topBarPrefs::setHideDuringFocus,
                        onOpenWeatherSettings = { activeSubPage = "topbar_weather" },
                        onOpenTextSettings = { activeSubPage = "topbar_texts" },
                        onOpenCountdownSettings = { onOpenCountdownSettings() },
                        onToggleInAppSuggestions = topBarPrefs::setInAppSuggestionsEnabled
                    )
                }
            }

            "topbar_weather" -> {
                WeatherSettingsTab(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            "topbar_texts" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    TopBarTextSettingsContent(prefs = topBarPrefs)
                }
            }
        }
    }
}


@Composable
private fun ExpandableSettingsGroupCard(
    title: String,
    subtitle: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Daralt" else "Genişlet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsCardDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        modifier = Modifier.padding(horizontal = 14.dp)
    )
}

@Composable
private fun SettingsQuickTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ThemeSelectorSection(
    themeMode: AppThemeMode,
    onThemeModeToggle: (AppThemeMode) -> Unit,
    currentPalette: ThemePalette,
    onSelectPalette: (ThemePalette) -> Unit
) {
    Text(
        "Tema Modu",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = themeMode == AppThemeMode.SYSTEM,
            onClick = { onThemeModeToggle(AppThemeMode.SYSTEM) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
        ) {
            Text("Sistem", fontSize = 11.sp, maxLines = 1)
        }
        SegmentedButton(
            selected = themeMode == AppThemeMode.DARK,
            onClick = { onThemeModeToggle(AppThemeMode.DARK) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
        ) {
            Text("Koyu", fontSize = 11.sp, maxLines = 1)
        }
        SegmentedButton(
            selected = themeMode == AppThemeMode.LIGHT,
            onClick = { onThemeModeToggle(AppThemeMode.LIGHT) },
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
        ) {
            Text("Açık", fontSize = 11.sp, maxLines = 1)
        }
        SegmentedButton(
            selected = themeMode == AppThemeMode.CIRCADIAN,
            onClick = { onThemeModeToggle(AppThemeMode.CIRCADIAN) },
            shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
        ) {
            Text("Döngü", fontSize = 11.sp, maxLines = 1)
        }
    }

    Text(
        "Renk Paleti",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val palettes = remember {
        ThemePalette.entries.filter { it != ThemePalette.GALATASARAY || SpecialMemberConfig.isEmreBey() }
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(palettes, key = { it.name }) { palette ->
            val isSelected = currentPalette == palette
            val swatchBg = Color(palette.bgHex)
            val isLightSwatch = (0.2126f * swatchBg.red + 0.7152f * swatchBg.green + 0.0722f * swatchBg.blue) > 0.55f
            val labelColor = if (isLightSwatch) Color(0xFF181714) else Color.White
            Card(
                modifier = Modifier
                    .width(106.dp)
                    .clickable { onSelectPalette(palette) },
                colors = CardDefaults.cardColors(containerColor = swatchBg),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color(palette.primaryHex) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(9.dp)) {
                    Box(modifier = Modifier.size(16.dp).background(Color(palette.primaryHex), CircleShape))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        palette.title,
                        color = labelColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherSettingsTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val weatherRepository = remember { WeatherRepository.getInstance(context) }
    val weatherSettings by weatherRepository.settings.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        coroutineScope.launch { weatherRepository.refreshWeather(force = true) }
    }
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Otomatik Konum", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Switch(
                    checked = weatherSettings.autoLocation,
                    onCheckedChange = { enabled ->
                        weatherRepository.setAutoLocation(enabled)
                        if (enabled &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        ) {
                            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        } else {
                            coroutineScope.launch { weatherRepository.refreshWeather(force = true) }
                        }
                    }
                )
            }
        }
        if (!weatherSettings.autoLocation) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Manuel Yerleşim Seçimi", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Seçili: ${weatherSettings.manualCity}, ${weatherSettings.manualCountry}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Şehir ara") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    coroutineScope.launch {
                                        isSearching = true
                                        searchResults = weatherRepository.searchLocations(searchQuery.trim())
                                        isSearching = false
                                    }
                                }
                            },
                            enabled = !isSearching
                        ) {
                            Text("Ara")
                        }
                    }
                    searchResults.forEach { res ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    weatherRepository.setManualLocation(res.name, res.country, res.latitude, res.longitude)
                                    coroutineScope.launch { weatherRepository.refreshWeather(force = true) }
                                    searchResults = emptyList()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            val label = if (res.admin1.isNotBlank()) "${res.name}, ${res.admin1}, ${res.country}" else "${res.name}, ${res.country}"
                            Text(text = label, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Sıcaklık Birimi", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !weatherSettings.useFahrenheit,
                        onClick = { weatherRepository.setUseFahrenheit(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Santigrat (°C)", fontSize = 12.sp) }
                    SegmentedButton(
                        selected = weatherSettings.useFahrenheit,
                        onClick = { weatherRepository.setUseFahrenheit(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Fahrenhayt (°F)", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
    }
}

fun executePermanentAccountDeletion(context: Context, onComplete: () -> Unit) {
    val auth = GoogleServicesHelper.getFirebaseAuth(context)
    val user = GoogleServicesHelper.getCurrentUser(auth, context) ?: return
    val uid = user.uid
    val db = GoogleServicesHelper.getFirebaseFirestore(context)

    AlertDialog.Builder(context)
        .setTitle("Hesabı Kalıcı Sil")
        .setMessage("Tüm verileriniz kalıcı olarak silinecektir.")
        .setPositiveButton("Kalıcı Sil") { _, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SharkMentorDatabase.getDatabase(context).clearAllTables()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    db?.collection("students")?.document(uid)?.delete()?.await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                withContext(Dispatchers.Main) {
                    user.delete()
                        .addOnSuccessListener {
                            auth?.signOut()
                            com.example.sharkmentor.core.util.AvatarHelper.clearLocalAvatar(context)
                            com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context).clearSessionProfile()
                            com.example.sharkmentor.data.repository.StreakRepository(context).clearLocalForLogout()
                            com.example.sharkmentor.data.repository.CountdownRepository.getInstance(context).clearLocalForLogout()
                            com.example.sharkmentor.core.util.SessionManager.setLinkedToInstitution(context, false)
                            com.example.sharkmentor.core.util.SessionManager.setUserMode(context, "study")
                            Toast.makeText(context, "Hesap silindi", Toast.LENGTH_SHORT).show()
                            onComplete()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                context,
                                "Tekrar giriş yapın: ${e.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
        }
        .setNegativeButton("İptal", null)
        .show()
}

@Composable
fun AppIconItem(
    previewRes: Int,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(2.2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = previewRes),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
fun AppIconSelectorSection(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedIcon by AppIconManager.activeIconFlow.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AppIconManager.getActiveIcon(context) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(SharkAppIcon.entries, key = { it.name }) { item ->
            val isSelected = selectedIcon == item

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIconItem(
                    previewRes = item.previewDrawableRes,
                    contentDescription = item.title,
                    isSelected = isSelected,
                    onClick = {
                        if (selectedIcon != item) {
                            AppIconManager.setActiveIcon(context, item)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar("Simge ve açılış ekranı güncellendi. Ana ekran simgesi çıkışta uygulanacak.")
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

