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
import androidx.compose.material.icons.filled.Tune
import com.example.sharkmentor.data.topbar.TopBarPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Tune
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.badges.SharkBadgeCatalog
import com.example.sharkmentor.core.data.AppThemeMode
import com.example.sharkmentor.core.data.ThemePalette
import com.example.sharkmentor.core.data.ThemePreferences
import com.example.sharkmentor.core.util.AppIconManager
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.util.SharkAppIcon
import com.example.sharkmentor.data.local.SharkMentorDatabase
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
    BackHandler(enabled = activeSubPage != null) { activeSubPage = when (activeSubPage) { "topbar_weather", "topbar_texts" -> "topbar_hub"; "topbar_hub" -> null; else -> null } }
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

    val themePrefs = remember { ThemePreferences.instance }
    val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle()
    val currentPalette by themePrefs.palette.collectAsStateWithLifecycle()

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
                            Text("Engellenen kullanıcılar yükleniyor...", style = MaterialTheme.typography.bodyMedium)
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
                                                text = "Engeli Kaldır",
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
           
                TopAppBar(
                expandedHeight = 50.dp,
              title = { Text(when (activeSubPage) { "coaching_support" -> "Kapsamlı Destek & Çalışma"; "topbar_hub" -> "Üst Bar Kontrolleri"; "topbar_weather" -> "Hava Durumu"; "topbar_texts" -> "Üst Bar Yazıları"; else -> "Ayarlar" }) },  
                navigationIcon = {
                    IconButton(onClick = { when (activeSubPage) { "topbar_weather", "topbar_texts" -> activeSubPage = "topbar_hub"; "topbar_hub", "coaching_support" -> activeSubPage = null; else -> onBackClick() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
           )
        }
    ) { padding ->
               when (activeSubPage) { null -> {
                    Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
HorizontalDivider()

           AppIconSelectorSection(snackbarHostState = snackbarHostState)
                        HorizontalDivider()

            Text("Kapsamlı Destek & Çalışma Düzeni", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            SettingRow(
                icon = Icons.Default.Tune,
                label = "Kapsamlı Destek, Çalışma Saati & Başlıklar",
                onClick = { activeSubPage = "coaching_support" }
            )

            HorizontalDivider()

            Text("Yapay Zeka & API", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            SettingRow(
                icon = Icons.Default.Tune,
                label = "Yapay Zeka & API Ayarları",
                onClick = onOpenAiSettings
            )

            HorizontalDivider()

            Text("Notlar & Çizim", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            SettingRow(
                icon = Icons.Default.EditNote,
                label = "Not Özellikleri",
                onClick = onOpenNoteSettings
            )

            HorizontalDivider()

           Text("Üst Bar Kontrolleri", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
           SettingRow(icon = Icons.Default.Tune, label = "Üst Bar Kontrolleri", onClick = { activeSubPage = "topbar_hub" })

            HorizontalDivider()

            Text("Bildirim & Hedefler", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            SettingRow(
                icon = Icons.Default.AccessTime,
                label = "Gün Değerlendirmesi Saati",
                onClick = { showTimeDialog = true }
            )


            HorizontalDivider()

            Text("Odak Modülleri", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            SettingRow(
                icon = Icons.Default.SelfImprovement,
                label = "Zen Odası",
                onClick = onOpenZenRoom
            )

            SettingRow(
                icon = Icons.Default.Block,
                label = "Uygulama Engelleyici",
                onClick = onOpenAppBlocker
            )

            HorizontalDivider()

            Text("Sosyal & Güvenlik", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            SettingRow(
                icon = Icons.Default.PersonOff,
                label = "Engellenen Kullanıcılar",
                onClick = { showBlockedUsersDialog = true }
            )

            HorizontalDivider()

            Text("Analiz & Geçmiş", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            SettingRow(
                icon = Icons.Default.Analytics,
                label = "Detaylı Analiz",
                onClick = onOpenAnalysis
            )

            SettingRow(
                icon = Icons.Default.History,
                label = "Çalışma Geçmişi",
                onClick = onOpenHistory
            )

            HorizontalDivider()

            Text("Hesap", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)

            Button(
                onClick = { executePermanentAccountDeletion(context, onAccountDeleted) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hesabı Kalıcı Sil", fontWeight = FontWeight.Bold)
            }
        }
        } "coaching_support" -> {
            com.example.sharkmentor.ui.coaching.ComprehensiveSupportSettingsScreen(
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } "topbar_hub" -> { Box(modifier = Modifier.fillMaxSize().padding(padding)) { TopBarControlHubContent(settings = topBarSettings, onToggleHideBubbles = topBarPrefs::setHideBubbles, onToggleHideTopBarMenu = topBarPrefs::setHideTopBarMenu, onToggleHideDuringFocus = topBarPrefs::setHideDuringFocus, onOpenWeatherSettings = { activeSubPage = "topbar_weather" }, onOpenTextSettings = { activeSubPage = "topbar_texts" }, onOpenCountdownSettings = { onOpenCountdownSettings() }) } } "topbar_weather" -> {
            WeatherSettingsTab(modifier = Modifier.fillMaxSize().padding(padding))
                } "topbar_texts" -> { Box(modifier = Modifier.fillMaxSize().padding(padding)) { TopBarTextSettingsContent(prefs = topBarPrefs) } }
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
    Text("Görünüm & Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(4.dp))

    Text("Tema Modu", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(6.dp))

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = themeMode == AppThemeMode.SYSTEM,
            onClick = { onThemeModeToggle(AppThemeMode.SYSTEM) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
        ) {
            Text("Sistem", fontSize = 12.sp)
        }
        SegmentedButton(
            selected = themeMode == AppThemeMode.DARK,
            onClick = { onThemeModeToggle(AppThemeMode.DARK) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
        ) {
            Text("Koyu", fontSize = 12.sp)
        }
        SegmentedButton(
            selected = themeMode == AppThemeMode.LIGHT,
            onClick = { onThemeModeToggle(AppThemeMode.LIGHT) },
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
        ) {
            Text("Açık", fontSize = 12.sp)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text("Renk Paleti", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(6.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(ThemePalette.entries.filter { it != ThemePalette.GALATASARAY || SpecialMemberConfig.isEmreBey() }.toTypedArray()) { palette ->
            val isSelected = currentPalette == palette
            Card(
                modifier = Modifier
                    .width(120.dp)
                    .clickable { onSelectPalette(palette) },
                colors = CardDefaults.cardColors(containerColor = Color(palette.bgHex)),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color(palette.primaryHex) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Box(modifier = Modifier.size(18.dp).background(Color(palette.primaryHex), CircleShape))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(palette.title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(palette.desc, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> coroutineScope.launch { weatherRepository.refreshWeather(force = true) } }
    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
Text("Otomatik Konum", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
Switch(checked = weatherSettings.autoLocation, onCheckedChange = { enabled -> weatherRepository.setAutoLocation(enabled); if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) } else { coroutineScope.launch { weatherRepository.refreshWeather(force = true)  }} })
}
}
if (!weatherSettings.autoLocation) {
Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
Text("Manuel Yerleşim Seçimi", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
Text("Seçili: ${weatherSettings.manualCity}, ${weatherSettings.manualCountry}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Şehir ara") }, singleLine = true, modifier = Modifier.weight(1f))
Button(onClick = { if (searchQuery.isNotBlank()) { coroutineScope.launch { isSearching = true; searchResults = weatherRepository.searchLocations(searchQuery.trim()); isSearching = false } } }, enabled = !isSearching) { Text("Ara") }
}
searchResults.forEach { res ->
Surface(modifier = Modifier.fillMaxWidth().clickable { weatherRepository.setManualLocation(res.name, res.country, res.latitude, res.longitude); coroutineScope.launch { weatherRepository.refreshWeather(force = true) }; searchResults = emptyList() }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
val label = if (res.admin1.isNotBlank()) "${res.name}, ${res.admin1}, ${res.country}" else "${res.name}, ${res.country}"
Text(text = label, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}
}
}
}
}
Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
Text("Sıcaklık Birimi", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
SegmentedButton(selected = !weatherSettings.useFahrenheit, onClick = { weatherRepository.setUseFahrenheit(false) }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) { Text("Santigrat (°C)", fontSize = 12.sp) }
SegmentedButton(selected = weatherSettings.useFahrenheit, onClick = { weatherRepository.setUseFahrenheit(true) }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) { Text("Fahrenhayt (°F)", fontSize = 12.sp) }
}
}
}
}
}
@Composable
private fun SettingRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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

    Text(
        "Uygulama Simgesi",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(10.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(SharkAppIcon.entries.toTypedArray()) { item ->
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

