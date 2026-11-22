package com.example.sharkmentor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharkmentor.core.audio.ZenAudioManager
import com.example.sharkmentor.core.audio.ZenSound
import com.example.sharkmentor.core.data.AppThemeMode
import com.example.sharkmentor.core.data.ThemePalette
import com.example.sharkmentor.core.theme.ThemeManager
import com.example.sharkmentor.core.util.AvatarHelper
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.core.util.SharkAvatar
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.ui.navigation.Screen

@Composable
fun ControlPanelContent(
    themeMode      : AppThemeMode,
    onThemeToggle  : (AppThemeMode) -> Unit,
    currentPalette : ThemePalette,
    onPaletteChange: (ThemePalette) -> Unit,
    onDismiss      : () -> Unit,
    currentRoute   : String?,
    onNavigate     : (String) -> Unit,
    onLogout       : (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        AvatarHelper.ensureLoaded(context)
    }

    val userPrefs = remember(context) { UserPreferences.getInstance(context) }
    val syncedStudentName by userPrefs.studentNameFlow.collectAsStateWithLifecycle()
    val avatarState by AvatarHelper.avatarState.collectAsStateWithLifecycle()

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
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Menü", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // Profile Mini Card Header — single unified entry point to Profile & Account Details
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onNavigate(Screen.Profile.route) }
                .padding(vertical = 4.dp)
        ) {
            SharkAvatar(
                avatarBase64 = avatarState.avatarBase64,
                avatarPresetId = avatarState.avatarPresetId,
                displayName = displayName,
                size = 48.dp
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Hesap Detayları >",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Spacer(Modifier.height(8.dp))

        val isLinked by SessionManager.isLinkedToInstitution.collectAsStateWithLifecycle()

        if (isLinked) {
            DrawerActionRow(
                icon    = Icons.Default.Business,
                label   = "Kurum Bilgisi & Koç Bağlantısı",
                onClick = { onNavigate(Screen.InstitutionInfo.route) },
            )
            DrawerActionRow(
                icon    = Icons.Default.Forum,
                label   = "Koçunla Mesajlaş",
                onClick = { onNavigate(Screen.CoachChat.route) },
            )
        }

        DrawerActionRow(
            icon    = Icons.Default.SelfImprovement,
            label   = "Zen Odası & Zihin Tazeleyici",
            onClick = { onNavigate("zen_room") },
        )

        DrawerActionRow(
            icon    = Icons.Default.Speed,
            label   = "PACE / KEEP Sayaç",
            onClick = { onNavigate(Screen.PaceKeep.route) },
        )

        DrawerActionRow(
            icon    = Icons.Default.Settings,
            label   = "Ayarlar",
            onClick = { onNavigate("settings") },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Spacer(Modifier.height(8.dp))

        Spacer(Modifier.weight(1f))

        // Embedded Zen Controls in Navigation Drawer
        DrawerZenControlsSection()

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Spacer(Modifier.height(8.dp))

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
                    com.example.sharkmentor.data.repository.StreakRepository(context).clearLocalForLogout()
                    com.example.sharkmentor.data.repository.CountdownRepository.getInstance(context).clearLocalForLogout()
                    SessionManager.setLinkedToInstitution(context, false)
                    SessionManager.setUserMode(context, "study")
                    onNavigate(Screen.Login.route)
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        // Info Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showAboutDialog = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Uygulama Hakkında",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DrawerZenControlsSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isZenMode by ThemeManager.isZenMode.collectAsStateWithLifecycle()
    val currentSound by ZenAudioManager.currentSound.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // ZEN MODU TOGGLE ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Zen Modu",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Monokrom ve sıfır gecikme",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Switch(
                checked = isZenMode,
                onCheckedChange = { ThemeManager.toggleZenMode(context) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.onPrimary,
                    checkedTrackColor = colorScheme.primary,
                    checkedBorderColor = colorScheme.primary,
                    uncheckedThumbColor = colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = colorScheme.surfaceVariant,
                    uncheckedBorderColor = colorScheme.outline.copy(alpha = 0.6f)
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.55f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))

        // AMBIENCE SOUND SELECTOR
        Text(
            text = "Ambiyans Sesleri",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        val sounds = remember { ZenSound.entries.filter { it != ZenSound.NONE } }

        // 2 Rows x 3 Columns Matrix
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sounds.chunked(3).forEach { rowSounds ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowSounds.forEach { sound ->
                        val isSelected = currentSound == sound

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    color = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { ZenAudioManager.toggleSound(context, sound) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sound.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
    }
}
