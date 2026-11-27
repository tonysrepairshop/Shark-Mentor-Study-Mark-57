package com.example.sharkmentor.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.ai.SharkTokenQuotaManager
import com.example.sharkmentor.core.audio.ZenAudioManager
import com.example.sharkmentor.core.audio.ZenSound
import com.example.sharkmentor.core.data.AppThemeMode
import com.example.sharkmentor.core.data.BlueLightFilterMode
import com.example.sharkmentor.core.data.ThemePalette
import com.example.sharkmentor.core.data.ThemePreferences
import com.example.sharkmentor.core.theme.ThemeManager
import com.example.sharkmentor.core.util.AvatarHelper
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.core.util.SharkAvatar
import com.example.sharkmentor.data.controlpanel.ControlPanelModule
import com.example.sharkmentor.data.controlpanel.ControlPanelPreferences
import com.example.sharkmentor.data.controlpanel.ControlPanelSettings
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.data.repository.StreakRepository
import com.example.sharkmentor.ui.navigation.Screen
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ControlPanelContent(
    themeMode          : AppThemeMode,
    onThemeToggle      : (AppThemeMode) -> Unit,
    currentPalette     : ThemePalette,
    onPaletteChange    : (ThemePalette) -> Unit,
    onDismiss          : () -> Unit,
    currentRoute       : String?,
    onNavigate         : (String) -> Unit,
    dueQuestionsCount  : Int = 0,
    onToggleMusicPlayer: (() -> Unit)? = null,
    onLogout           : (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var showAboutDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        AvatarHelper.ensureLoaded(context)
        SharkTokenQuotaManager.ensureLoaded(context)
    }

    val panelPrefs = remember(context) { ControlPanelPreferences.getInstance(context) }
    val panelSettings by panelPrefs.settings.collectAsStateWithLifecycle()

    val themePrefs = remember(context) { ThemePreferences.initialize(context.applicationContext) }
    val blueLightMode by themePrefs.blueLightMode.collectAsStateWithLifecycle()
    val keepScreenOn by themePrefs.keepScreenOnInStopwatch.collectAsStateWithLifecycle()
    val isZenMode by ThemeManager.isZenMode.collectAsStateWithLifecycle()
    val currentZenSound by ZenAudioManager.currentSound.collectAsStateWithLifecycle()

    val userPrefs = remember(context) { UserPreferences.getInstance(context) }
    val syncedStudentName by userPrefs.studentNameFlow.collectAsStateWithLifecycle()
    val avatarState by AvatarHelper.avatarState.collectAsStateWithLifecycle()
    val quotaSnapshot by SharkTokenQuotaManager.snapshot.collectAsStateWithLifecycle()
    val isLinked by SessionManager.isLinkedToInstitution.collectAsStateWithLifecycle()

    // Live Daily Summary streams (today focus minutes + water streak)
    val (todayStartMs, todayEndMs) = remember {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        start to end
    }
    val todayFocusFlow = remember(context, todayStartMs, todayEndMs) {
        SharkMentorDatabase.getDatabase(context).sessionDao()
            .getTodayTotalFocusMinutes(todayStartMs, todayEndMs)
    }
    val todayFocusMinutesRaw by todayFocusFlow.collectAsStateWithLifecycle(initialValue = 0)
    val todayFocusMinutes = todayFocusMinutesRaw ?: 0

    val streakRepo = remember(context) { StreakRepository(context) }
    val streakState by streakRepo.streakState.collectAsStateWithLifecycle()

    val user = GoogleServicesHelper.getCurrentUser(context = context)
    val isGuest = user == null || user.isAnonymous
    val displayName = remember(syncedStudentName, user?.displayName, isGuest) {
        val localName = syncedStudentName?.trim()?.takeIf { it.isNotBlank() }
        val authName = user?.displayName?.trim()?.takeIf { it.isNotBlank() }
        when {
            !localName.isNullOrBlank() -> localName
            !authName.isNullOrBlank() -> authName
            isGuest -> "Misafir Öğrenci"
            else -> "Öğrenci Profili"
        }
    }

    if (showAboutDialog) {
        AboutAppDialog(onDismiss = { showAboutDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header with inline Edit button + Close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isEditMode) "Paneli Düzenle" else "Kontrol Paneli",
                color = colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { isEditMode = !isEditMode },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isEditMode) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(
                        1.dp,
                        if (isEditMode) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
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
                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Fixed Top Profile Mini Card
        Surface(
            onClick = { onNavigate(Screen.Profile.route) },
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                SharkAvatar(
                    avatarBase64 = avatarState.avatarBase64,
                    avatarPresetId = avatarState.avatarPresetId,
                    displayName = displayName,
                    size = 44.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Hesap Detayları >",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary
                        )
                        if (quotaSnapshot.isUnlimitedUnlocked) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFFFFB703).copy(alpha = 0.18f),
                                border = BorderStroke(0.8.dp, Color(0xFFFFB703).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "J.A.R.V.I.S.",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = Color(0xFFD97706),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isEditMode) {
            ControlPanelInlineEditor(
                settings = panelSettings,
                onToggle = { panelPrefs.toggleModule(it) },
                onMoveUp = { panelPrefs.moveModuleUp(it) },
                onMoveDown = { panelPrefs.moveModuleDown(it) },
                onReset = {
                    panelPrefs.resetToDefault()
                    Toast.makeText(context, "Kontrol Paneli varsayılana sıfırlandı.", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            val visibleModules = remember(panelSettings) { panelSettings.visibleModulesInOrder() }

            if (visibleModules.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Tüm modüller gizlendi. Sağ üstteki Düzenle butonundan istediğin modülleri açabilirsin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                visibleModules.forEach { module ->
                    when (module) {
                        ControlPanelModule.DAILY_SUMMARY -> {
                            DailySummaryModuleCard(
                                todayFocusMinutes = todayFocusMinutes,
                                streakDays = streakState.streakCount,
                                dueQuestionsCount = dueQuestionsCount,
                                onOpenErrorBank = { onNavigate(Screen.ErrorBank.route) },
                                onOpenFocus = { onNavigate(Screen.Focus.route) }
                            )
                        }

                        ControlPanelModule.STUDY_MACROS -> {
                            StudyMacrosModuleCard(
                                isDeepFocusActive = isZenMode && currentZenSound != ZenSound.NONE,
                                isNightModeActive = themeMode == AppThemeMode.DARK && blueLightMode != BlueLightFilterMode.OFF,
                                isNormalActive = !isZenMode && blueLightMode == BlueLightFilterMode.OFF && currentZenSound == ZenSound.NONE,
                                onDeepFocus = {
                                    ThemeManager.setZenMode(context, true)
                                    ZenAudioManager.playSound(context, ZenSound.RAIN)
                                    Toast.makeText(context, "Derin Odak aktif: Zen + Yağmur", Toast.LENGTH_SHORT).show()
                                },
                                onNightStudy = {
                                    onThemeToggle(AppThemeMode.DARK)
                                    themePrefs.setBlueLightMode(BlueLightFilterMode.MANUAL_ON)
                                    Toast.makeText(context, "Gece Çalışması aktif: Koyu + Göz Koruma", Toast.LENGTH_SHORT).show()
                                },
                                onNormalMode = {
                                    ThemeManager.setZenMode(context, false)
                                    themePrefs.setBlueLightMode(BlueLightFilterMode.OFF)
                                    ZenAudioManager.stopSound(context)
                                    Toast.makeText(context, "Normal Mod geri yüklendi", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        ControlPanelModule.QUICK_TILES -> {
                            QuickTilesModuleGrid(
                                isZenMode = isZenMode,
                                themeMode = themeMode,
                                blueLightMode = blueLightMode,
                                keepScreenOn = keepScreenOn,
                                onToggleZen = { ThemeManager.toggleZenMode(context) },
                                onCycleThemeMode = {
                                    val nextMode = when (themeMode) {
                                        AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
                                        AppThemeMode.LIGHT -> AppThemeMode.DARK
                                        AppThemeMode.DARK -> AppThemeMode.CIRCADIAN
                                        AppThemeMode.CIRCADIAN -> AppThemeMode.SYSTEM
                                    }
                                    onThemeToggle(nextMode)
                                },
                                onToggleBlueLight = {
                                    val nextBlue = if (blueLightMode == BlueLightFilterMode.OFF) {
                                        BlueLightFilterMode.MANUAL_ON
                                    } else {
                                        BlueLightFilterMode.OFF
                                    }
                                    themePrefs.setBlueLightMode(nextBlue)
                                },
                                onToggleKeepScreenOn = {
                                    themePrefs.setKeepScreenOnInStopwatch(!keepScreenOn)
                                }
                            )
                        }

                        ControlPanelModule.THEME_STRIP -> {
                            QuickThemeStripCard(
                                currentPalette = currentPalette,
                                onPaletteChange = onPaletteChange
                            )
                        }

                        ControlPanelModule.SHORTCUTS -> {
                            ControlPanelShortcutsBlock(
                                isLinked = isLinked,
                                dueQuestionsCount = dueQuestionsCount,
                                onNavigate = onNavigate,
                                onToggleMusicPlayer = onToggleMusicPlayer?.let { toggle ->
                                    {
                                        onDismiss()
                                        toggle()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f, fill = true))
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Fixed Bottom Logout + About
        DrawerActionRow(
            icon    = Icons.Default.Logout,
            label   = "Çıkış Yap",
            onClick = {
                if (onLogout != null) {
                    onLogout()
                } else {
                    GoogleServicesHelper.signOut()
                    AvatarHelper.clearLocalAvatar(context)
                    userPrefs.clearSessionProfile()
                    StreakRepository(context).clearLocalForLogout()
                    com.example.sharkmentor.data.repository.CountdownRepository.getInstance(context).clearLocalForLogout()
                    SessionManager.setLinkedToInstitution(context, false)
                    SessionManager.setUserMode(context, "study")
                    onNavigate(Screen.Login.route)
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showAboutDialog = true },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Uygulama Hakkında",
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun ControlPanelSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        fontSize = 10.5.sp,
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
    )
}

@Composable
private fun ControlPanelInlineEditor(
    settings: ControlPanelSettings,
    onToggle: (ControlPanelModule) -> Unit,
    onMoveUp: (ControlPanelModule) -> Unit,
    onMoveDown: (ControlPanelModule) -> Unit,
    onReset: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val ordered = remember(settings.moduleOrder) {
        ControlPanelModule.normalizeOrder(settings.moduleOrder)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Modülleri aç/kapat veya sırala",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onReset,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Varsayılan", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        ordered.forEachIndexed { idx, module ->
            val isVisible = settings.isModuleVisible(module)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isVisible) colorScheme.surfaceVariant.copy(alpha = 0.52f)
                else colorScheme.surfaceVariant.copy(alpha = 0.24f),
                border = BorderStroke(
                    1.dp,
                    if (isVisible) colorScheme.primary.copy(alpha = 0.28f)
                    else colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { onMoveUp(module) },
                            enabled = idx > 0,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Yukarı Taşı",
                                tint = if (idx > 0) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onMoveDown(module) },
                            enabled = idx < ordered.lastIndex,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Aşağı Taşı",
                                tint = if (idx < ordered.lastIndex) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = module.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isVisible) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = module.description,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Switch(
                        checked = isVisible,
                        onCheckedChange = { onToggle(module) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.onPrimary,
                            checkedTrackColor = colorScheme.primary,
                            uncheckedThumbColor = colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = colorScheme.surface
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DailySummaryModuleCard(
    todayFocusMinutes: Int,
    streakDays: Int,
    dueQuestionsCount: Int,
    onOpenErrorBank: () -> Unit,
    onOpenFocus: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val focusLabel = remember(todayFocusMinutes) {
        if (todayFocusMinutes >= 60) {
            val h = todayFocusMinutes / 60
            val m = todayFocusMinutes % 60
            if (m == 0) "${h}s" else "${h}s ${m}dk"
        } else {
            "${todayFocusMinutes} dk"
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ControlPanelSectionLabel("CANLI GÜNLÜK ÖZET")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryMetricTile(
                icon = Icons.Default.Timer,
                value = focusLabel,
                label = "Bugün Odak",
                accent = colorScheme.primary,
                onClick = onOpenFocus,
                modifier = Modifier.weight(1f)
            )
            SummaryMetricTile(
                icon = Icons.Default.WaterDrop,
                value = "$streakDays gün",
                label = "Su Serisi",
                accent = colorScheme.secondary,
                onClick = onOpenFocus,
                modifier = Modifier.weight(1f)
            )
            SummaryMetricTile(
                icon = Icons.Default.FitnessCenter,
                value = "$dueQuestionsCount",
                label = "Tekrar",
                accent = if (dueQuestionsCount > 0) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                onClick = onOpenErrorBank,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryMetricTile(
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                fontSize = 9.5.sp,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StudyMacrosModuleCard(
    isDeepFocusActive: Boolean,
    isNightModeActive: Boolean,
    isNormalActive: Boolean,
    onDeepFocus: () -> Unit,
    onNightStudy: () -> Unit,
    onNormalMode: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ControlPanelSectionLabel("TEK DOKUNUŞ MAKROLAR")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            MacroPillButton(
                icon = Icons.Default.Bolt,
                label = "Derin Odak",
                active = isDeepFocusActive,
                onClick = onDeepFocus,
                modifier = Modifier.weight(1f)
            )
            MacroPillButton(
                icon = Icons.Default.NightlightRound,
                label = "Gece",
                active = isNightModeActive,
                onClick = onNightStudy,
                modifier = Modifier.weight(1f)
            )
            MacroPillButton(
                icon = Icons.Default.WbSunny,
                label = "Normal",
                active = isNormalActive,
                onClick = onNormalMode,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MacroPillButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (active) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.52f),
        border = BorderStroke(
            1.dp,
            if (active) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) colorScheme.onPrimary else colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) colorScheme.onPrimary else colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickTilesModuleGrid(
    isZenMode: Boolean,
    themeMode: AppThemeMode,
    blueLightMode: BlueLightFilterMode,
    keepScreenOn: Boolean,
    onToggleZen: () -> Unit,
    onCycleThemeMode: () -> Unit,
    onToggleBlueLight: () -> Unit,
    onToggleKeepScreenOn: () -> Unit
) {
    val themeModeLabel = when (themeMode) {
        AppThemeMode.SYSTEM -> "Sistem"
        AppThemeMode.LIGHT -> "Açık"
        AppThemeMode.DARK -> "Koyu"
        AppThemeMode.CIRCADIAN -> "Sirkadiyen"
    }
    val themeModeIcon = when (themeMode) {
        AppThemeMode.LIGHT -> Icons.Default.WbSunny
        AppThemeMode.DARK -> Icons.Default.NightlightRound
        AppThemeMode.CIRCADIAN -> Icons.Default.AutoAwesome
        AppThemeMode.SYSTEM -> Icons.Default.Tune
    }
    val blueLightActive = blueLightMode != BlueLightFilterMode.OFF
    val blueLightLabel = when (blueLightMode) {
        BlueLightFilterMode.OFF -> "Kapalı"
        BlueLightFilterMode.MANUAL_ON -> "Açık"
        BlueLightFilterMode.LOCATION_SUNSET, BlueLightFilterMode.CUSTOM_TIME -> "Otomatik"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ControlPanelSectionLabel("HIZLI KONTROL KAROLARI")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlQuickTile(
                icon = Icons.Default.SelfImprovement,
                title = "Zen Modu",
                subtitle = if (isZenMode) "Açık" else "Kapalı",
                active = isZenMode,
                onClick = onToggleZen,
                modifier = Modifier.weight(1f)
            )
            ControlQuickTile(
                icon = themeModeIcon,
                title = "Tema Modu",
                subtitle = themeModeLabel,
                active = themeMode != AppThemeMode.SYSTEM,
                onClick = onCycleThemeMode,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlQuickTile(
                icon = Icons.Default.Visibility,
                title = "Göz Koruma",
                subtitle = blueLightLabel,
                active = blueLightActive,
                onClick = onToggleBlueLight,
                modifier = Modifier.weight(1f)
            )
            ControlQuickTile(
                icon = Icons.Default.Timer,
                title = "Ekran Açık",
                subtitle = if (keepScreenOn) "Açık" else "Kapalı",
                active = keepScreenOn,
                onClick = onToggleKeepScreenOn,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ControlQuickTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (active) colorScheme.primary.copy(alpha = 0.55f) else colorScheme.outlineVariant.copy(alpha = 0.42f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) colorScheme.primary else colorScheme.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = if (active) colorScheme.primary else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickThemeStripCard(
    currentPalette: ThemePalette,
    onPaletteChange: (ThemePalette) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val availablePalettes = remember {
        ThemePalette.entries.filter { it != ThemePalette.GALATASARAY || SpecialMemberConfig.isEmreBey() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ControlPanelSectionLabel("HIZLI TEMA PALETİ")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            items(availablePalettes, key = { it.name }) { palette ->
                val isSelected = palette == currentPalette
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPaletteChange(palette) }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(palette.bgHex))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.2.dp,
                                color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.65f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(palette.primaryHex))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = palette.title,
                        fontSize = 9.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPanelShortcutsBlock(
    isLinked: Boolean,
    dueQuestionsCount: Int,
    onNavigate: (String) -> Unit,
    onToggleMusicPlayer: (() -> Unit)?
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        ControlPanelSectionLabel("HIZLI KISAYOLLAR")
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.34f),
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.38f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                if (isLinked) {
                    DrawerActionRow(
                        icon = Icons.Default.Business,
                        label = "Kurum Bilgisi & Koç Bağlantısı",
                        onClick = { onNavigate(Screen.InstitutionInfo.route) }
                    )
                    DrawerActionRow(
                        icon = Icons.Default.Forum,
                        label = "Koçunla Mesajlaş",
                        onClick = { onNavigate(Screen.CoachChat.route) }
                    )
                }
                DrawerActionRow(
                    icon = Icons.Default.SelfImprovement,
                    label = "Zen Odası & Zihin Tazeleyici",
                    onClick = { onNavigate("zen_room") }
                )
                DrawerActionRow(
                    icon = Icons.Default.Speed,
                    label = "PACE / KEEP Sayaç",
                    onClick = { onNavigate(Screen.PaceKeep.route) }
                )
                DrawerActionRow(
                    icon = Icons.Default.FitnessCenter,
                    label = "Hata Bankası",
                    badge = if (dueQuestionsCount > 0) "$dueQuestionsCount" else null,
                    onClick = { onNavigate(Screen.ErrorBank.route) }
                )
                if (onToggleMusicPlayer != null) {
                    DrawerActionRow(
                        icon = Icons.Default.MusicNote,
                        label = "Müzik Oynatıcı",
                        onClick = onToggleMusicPlayer
                    )
                }
                DrawerActionRow(
                    icon = Icons.Default.Settings,
                    label = "Ayarlar",
                    onClick = { onNavigate("settings") }
                )
            }
        }
    }
}

@Composable
private fun DrawerActionRow(
    icon: ImageVector,
    label: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(11.dp))
            Text(
                text = label,
                color = colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!badge.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
