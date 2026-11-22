package com.example.sharkmentor.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharkmentor.core.badges.BadgeTier
import com.example.sharkmentor.core.badges.SharkBadgeCatalog
import com.example.sharkmentor.core.badges.SharkBadgeChip
import com.example.sharkmentor.core.badges.SharkBadgeDef
import com.example.sharkmentor.core.badges.SharkBadgeShowcaseRow
import com.example.sharkmentor.core.league.LeagueRankZone
import com.example.sharkmentor.core.league.SharkLeagueRules
import com.example.sharkmentor.core.util.SharkAvatar
import com.example.sharkmentor.core.util.SharkVisualKit
import com.example.sharkmentor.ui.screens.leaguechat.BadgeShowcaseSelectorSheet
import com.example.sharkmentor.ui.screens.leaguechat.BadgeTierDetailDialog
import com.example.sharkmentor.ui.screens.leaguechat.LeagueChatDestination
import com.example.sharkmentor.ui.screens.leaguechat.LeagueChatScreen
import com.example.sharkmentor.ui.screens.leaguechat.LeagueMemberProfileSheet
import com.example.sharkmentor.ui.viewmodel.IncomingStudyInvite
import com.example.sharkmentor.ui.viewmodel.LeagueEntry
import com.example.sharkmentor.ui.viewmodel.LeagueTab
import com.example.sharkmentor.ui.viewmodel.QuestionCardUi
import com.example.sharkmentor.ui.viewmodel.SharkLeagueUiState
import com.example.sharkmentor.ui.viewmodel.SharkLeagueViewModel
import com.example.sharkmentor.ui.viewmodel.SharkTier
import com.example.sharkmentor.ui.viewmodel.StudyGroupSummary
import com.example.sharkmentor.ui.viewmodel.nextThreshold
import com.example.sharkmentor.ui.viewmodel.previousThreshold
import com.google.firebase.auth.FirebaseAuth

private val PODIUM_GOLD_COLOR = Color(0xFFD97706)
private val PODIUM_SILVER_COLOR = Color(0xFF64748B)
private val PODIUM_BRONZE_COLOR = Color(0xFFB45309)

@Composable
private fun SharkTier.themeAwareAccentColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary
    return when (this) {
        SharkTier.CAMGOZ -> MaterialTheme.colorScheme.onSurfaceVariant
        SharkTier.MAKO -> primary
        SharkTier.CEKICBAS -> secondary
        SharkTier.KAPLAN -> tertiary
        SharkTier.BUYUK_BEYAZ -> primary
        SharkTier.MEGALODON -> MaterialTheme.colorScheme.error
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharkLeagueScreen(
    onBackClick: () -> Unit,
    viewModel: SharkLeagueViewModel = viewModel(),
    onOpenQuestionSolve: ((QuestionCardUi) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
     var managingGroup by remember { mutableStateOf<StudyGroupSummary?>(null) }
    val clipboardManager = LocalClipboardManager.current

    var showCreateGroupDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinByCodeDialog by rememberSaveable { mutableStateOf(false) }
    var showBadgeShowcaseSheet by rememberSaveable { mutableStateOf(false) }
    var showNameEditDialog by rememberSaveable { mutableStateOf(false) }
    var showLeagueTiersSheet by rememberSaveable { mutableStateOf(false) }
    var sheetSelectedTierName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMemberForProfile by remember { mutableStateOf<LeagueEntry?>(null) }
    var inspectedMyShowcaseBadge by remember { mutableStateOf<SharkBadgeDef?>(null) }
    var activeChatDestination by remember { mutableStateOf<LeagueChatDestination?>(null) }

    fun openLeagueTiersExplorer(initialTier: SharkTier? = null) {
        val targetTier = initialTier ?: uiState.selectedTierFilter ?: uiState.myEntry?.tier ?: SharkTier.CAMGOZ
        sheetSelectedTierName = targetTier.name
        showLeagueTiersSheet = true
    }

    BackHandler(enabled = activeChatDestination != null) {
        activeChatDestination = null
        viewModel.loadLeague(preserveTab = true)
    }

    var openedDirectlyToDm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (com.example.sharkmentor.ui.components.PendingLeagueNavigationBus.openDmInboxDirectly.value) {
            com.example.sharkmentor.ui.components.PendingLeagueNavigationBus.openDmInboxDirectly.value = false
            com.example.sharkmentor.core.notification.SocialNotificationMonitor.clearUnreadDmIndicator()
            openedDirectlyToDm = true
            activeChatDestination = LeagueChatDestination.DmInbox
        }
    }

    if (activeChatDestination != null) {
        if (activeChatDestination is LeagueChatDestination.DmInbox || activeChatDestination is LeagueChatDestination.PeerDm) {
            LaunchedEffect(activeChatDestination) {
                com.example.sharkmentor.core.notification.SocialNotificationMonitor.clearUnreadDmIndicator()
            }
        }
        LeagueChatScreen(
            destination = activeChatDestination!!,
            myDisplayNameHint = uiState.myEntry?.rawName.orEmpty(),
            onBackClick = {
                if (openedDirectlyToDm && activeChatDestination is LeagueChatDestination.DmInbox) {
                    openedDirectlyToDm = false
                    activeChatDestination = null
                    onBackClick()
                } else {
                    activeChatDestination = null
                    viewModel.loadLeague(preserveTab = true)
                }
            },
            onOpenQuestionSolveScreen = onOpenQuestionSolve
        )
        return
    }

    LaunchedEffect(Unit) {
        viewModel.loadLeague(preserveTab = true)
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    fun copyToClipboard(label: String, code: String) {
        clipboardManager.setText(AnnotatedString(code))
        Toast.makeText(context, "Kopyalandı: $code", Toast.LENGTH_SHORT).show()
    }

    fun shareInviteText(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Paylaş"))
    }

    fun openActiveGroupChat() {
        val targetGroup = uiState.joinedGroups.firstOrNull { it.inviteCode == uiState.selectedGroupCode }
            ?: uiState.joinedGroups.firstOrNull()
        if (targetGroup != null) {
            activeChatDestination = LeagueChatDestination.GroupChat(
                groupCode = targetGroup.inviteCode,
                groupName = targetGroup.name
            )
        } else {
            Toast.makeText(
                context,
                "Sohbet için önce bir çalışma grubu oluştur veya grup koduyla katıl.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    expandedHeight = 52.dp,
                    title = {
                        Column {
                            Text(
                                text = "Shark Ligi",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = when {
                                    uiState.activeTab == LeagueTab.GROUPS_AND_FRIENDS && !uiState.groupName.isNullOrBlank() ->
                                        uiState.groupName.orEmpty()
                                    uiState.activeTab == LeagueTab.GROUPS_AND_FRIENDS ->
                                        "Gruplar & Arkadaşlar"
                                    uiState.useInstitutionFilter && uiState.institutionCode.isNotBlank() ->
                                        "Kurum Ligi (${uiState.institutionCode})"
                                    else ->
                                        "Genel Lig"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { activeChatDestination = LeagueChatDestination.DmInbox }) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Mesaj İstekleri",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                if (uiState.pendingDmRequestCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = uiState.pendingDmRequestCount.coerceAtMost(9).toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black
                                                ),
                                                color = MaterialTheme.colorScheme.onError
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.loadLeague(preserveTab = true) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Yenile",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Sekmeler: 1) Bireysel Genel Lig  2) Gruplarım & Arkadaşlarım
                TabRow(
                    selectedTabIndex = uiState.activeTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        if (uiState.activeTab.ordinal < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.activeTab.ordinal]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                ) {
                    LeagueTab.entries.forEach { tab ->
                        val selected = uiState.activeTab == tab
                        Tab(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            text = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            uiState.incomingStudyInvite?.let { invite ->
                IncomingStudyInviteBanner(
                    invite = invite,
                    onAcceptAndStartFocus = { viewModel.acceptIncomingInviteAndStartFocus(invite.id) },
                    onDismiss = { viewModel.dismissIncomingInvite(invite.id) }
                )
            }

            if (uiState.needsNameSetup && !uiState.isLoading) {
                InlineNameSetupCard(
                    isSaving = uiState.isActionInProgress,
                    onSaveName = { viewModel.updateStudentDisplayName(it) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> LoadingIndicator(Modifier.fillMaxSize())
                    uiState.error != null -> ErrorCard(
                        message = uiState.error!!,
                        onRetry = { viewModel.loadLeague(preserveTab = true) },
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> LeagueContent(
                        uiState = uiState,
                        onCopyCode = ::copyToClipboard,
                        onShareText = ::shareInviteText,
                        onOpenCreateGroup = { showCreateGroupDialog = true },
                        onOpenJoinByCode = { showJoinByCodeDialog = true },
                        onOpenGroupChat = ::openActiveGroupChat, onManageGroup = { managingGroup = it },
                        onOpenDmInbox = { activeChatDestination = LeagueChatDestination.DmInbox },
                        onOpenBadgeShowcase = { showBadgeShowcaseSheet = true },
                        onShowcaseBadgeClick = { inspectedMyShowcaseBadge = it },
                        onOpenEditName = { showNameEditDialog = true },
                        onOpenLeagueTiers = { tier -> openLeagueTiersExplorer(tier) },
                        onSelectTierFilter = { viewModel.selectTierFilter(it) },
                        onSelectGroupFilter = { viewModel.selectGroupFilter(it) },
                        onToggleInstitutionFilter = { viewModel.toggleInstitutionLeague(it) },
                        onNudgeMember = { viewModel.nudgeLeagueMember(it) },
                        onMemberClick = { selectedMemberForProfile = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showLeagueTiersSheet) {
        val currentSheetTier = SharkLeagueRules.parseTierOrNull(sheetSelectedTierName)
            ?: uiState.myEntry?.tier
            ?: SharkTier.CAMGOZ
        SharkLeagueTiersSheet(
            selectedTier = currentSheetTier,
            myTier = uiState.myEntry?.tier,
            entriesByTier = uiState.entriesByTier,
            tierCounts = uiState.tierCounts,
            onSelectTier = { sheetSelectedTierName = it.name },
            onApplyTierFilter = { tier ->
                viewModel.selectTierFilter(tier)
                showLeagueTiersSheet = false
            },
            onMemberClick = { member ->
                showLeagueTiersSheet = false
                selectedMemberForProfile = member
            },
            onDismiss = { showLeagueTiersSheet = false }
        )
    }

    if (showCreateGroupDialog) {
        CreateStudyGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreateGroup = { groupName ->
                showCreateGroupDialog = false
                viewModel.createStudyGroup(groupName)
            }
        )
    }

    if (showJoinByCodeDialog) {
        JoinByCodeDialog(
            myFriendCode = uiState.myFriendCode,
            onDismiss = { showJoinByCodeDialog = false },
            onJoinCode = { code ->
                showJoinByCodeDialog = false
                viewModel.joinByCode(code)
            }
        )
    }

    if (showNameEditDialog) {
        EditStudentNameDialog(
            currentName = uiState.myEntry?.rawName.orEmpty(),
            onDismiss = { showNameEditDialog = false },
            onSaveName = { updated ->
                showNameEditDialog = false
                viewModel.updateStudentDisplayName(updated)
            }
        )
    }

    if (showBadgeShowcaseSheet) {
        BadgeShowcaseSelectorSheet(
            unlockedBadgeIds = uiState.myUnlockedBadges,
            initialSelectedIds = uiState.myShowcaseBadges,
            badgeProgress = uiState.myBadgeProgress,
            mentorPoints = uiState.myMentorPoints,
            onDismiss = { showBadgeShowcaseSheet = false },
            onSaveShowcase = { selected ->
                viewModel.updateMyShowcaseBadges(selected)
            }
        )
    }
    managingGroup?.let { group -> ManageStudyGroupDialog(groupCode = group.inviteCode, currentName = group.name, creatorId = group.creatorId, currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(), members = group.memberIds.map { id -> id to (group.memberNames[id]?.takeIf { it.isNotBlank() } ?: id.take(8)) }, onDismiss = { managingGroup = null }, onSaveChanges = { newName, removedMemberIds -> viewModel.updateStudyGroup(group.inviteCode, newName, removedMemberIds); managingGroup = null }, onDeleteGroup = { viewModel.deleteStudyGroup(group.inviteCode); managingGroup = null }, onLeaveGroup = { viewModel.leaveStudyGroup(group.inviteCode); managingGroup = null }) }
    
    selectedMemberForProfile?.let { member ->
        LeagueMemberProfileSheet(
            entry = member,
            existingDmStatus = uiState.dmStatusByPeerId[member.studentId],
            isBlocked = uiState.blockedUserIds.contains(member.studentId),
            onDismiss = { selectedMemberForProfile = null }, isIncomingDmRequest = uiState.incomingPendingDmThreadByPeerId.containsKey(member.studentId), onAcceptDmRequest = { viewModel.respondToDmRequest(member.studentId, accept = true) }, onRejectDmRequest = { viewModel.respondToDmRequest(member.studentId, accept = false) },
            onSendDmRequest = { entry, note -> viewModel.sendDmRequest(entry, note) },
            onOpenAcceptedDm = { entry ->
                selectedMemberForProfile = null
                val peerRealName = entry.rawName.ifBlank {
                    entry.displayName.removePrefix("Sen (").removeSuffix(")")
                }
                activeChatDestination = LeagueChatDestination.PeerDm(
                    peerUid = entry.studentId,
                    peerName = peerRealName,
                    peerAvatarBase64 = entry.avatarBase64,
                    peerAvatarPresetId = entry.avatarPresetId
                )
            },
            onBlockUser = { entry, block -> viewModel.blockUser(entry, block) },
            onReportUser = { entry, reason, details -> viewModel.reportUser(entry, reason, details) }
        )
    }

    inspectedMyShowcaseBadge?.let { badge ->
        val rawProgressCount = uiState.myBadgeProgress[badge.id]
            ?: uiState.myEntry?.badgeProgress?.get(badge.id)
            ?: 0
        BadgeTierDetailDialog(
            badge = badge,
            rawCount = rawProgressCount,
            isLegacyUnlocked = uiState.myUnlockedBadges.contains(badge.id) ||
                uiState.myShowcaseBadges.contains(badge.id),
            onDismiss = { inspectedMyShowcaseBadge = null }
        )
    }
}

@Composable
private fun IncomingStudyInviteBanner(
    invite: IncomingStudyInvite,
    onAcceptAndStartFocus: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${invite.fromName} seni çalışmaya dürttü",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = invite.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onAcceptAndStartFocus,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Odak Başlat", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Kapat",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun InlineNameSetupCard(
    isSaving: Boolean,
    onSaveName: (String) -> Unit
) {
    var nameInput by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Adını Belirle",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Arkadaşların ve lig üyelerin seni \"Öğrenci\" yerine gerçek adınla görsün.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(36) },
                    label = { Text("Adın veya Takma Adın") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onSaveName(nameInput) },
                    enabled = nameInput.trim().length >= 2 && !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Kaydet", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EditStudentNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSaveName: (String) -> Unit
) {
    var nameInput by rememberSaveable(currentName) {
        mutableStateOf(if (currentName.equals("Öğrenci", ignoreCase = true)) "" else currentName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Adını Güncelle",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it.take(36) },
                label = { Text("Görünecek Adın") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSaveName(nameInput) },
                enabled = nameInput.trim().length >= 2
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
private fun LeagueContent(
    uiState: SharkLeagueUiState,
    onCopyCode: (String, String) -> Unit,
    onShareText: (String) -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenJoinByCode: () -> Unit,
    onOpenGroupChat: () -> Unit, onManageGroup: (StudyGroupSummary) -> Unit,
    onOpenDmInbox: () -> Unit,
    onOpenBadgeShowcase: () -> Unit,
    onShowcaseBadgeClick: (SharkBadgeDef) -> Unit,
    onOpenEditName: () -> Unit,
    onOpenLeagueTiers: (SharkTier?) -> Unit,
    onSelectTierFilter: (SharkTier?) -> Unit,
    onSelectGroupFilter: (String?) -> Unit,
    onToggleInstitutionFilter: (Boolean) -> Unit,
    onNudgeMember: (LeagueEntry) -> Unit,
    onMemberClick: (LeagueEntry) -> Unit,
    modifier: Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isGroupsTab = uiState.activeTab == LeagueTab.GROUPS_AND_FRIENDS
    val activeTierFilter = if (!isGroupsTab) uiState.selectedTierFilter else null

    val activeList = when {
        isGroupsTab -> uiState.friendsAndGroupMembers
        activeTierFilter != null -> uiState.entriesByTier[activeTierFilter].orEmpty()
        else -> uiState.leaderboard
    }
    val relegationStartRank = if (!isGroupsTab) {
        SharkLeagueRules.relegationStartRank(activeList.size)
    } else {
        null
    }

    val listHeaderTitle = when (uiState.activeTab) {
        LeagueTab.GLOBAL_INDIVIDUAL -> {
            when {
                activeTierFilter != null ->
                    "${activeTierFilter.label.uppercase()} LİGİ (${activeList.size})"
                uiState.useInstitutionFilter && uiState.institutionCode.isNotBlank() ->
                    "KURUM LİGİ (${uiState.institutionCode})"
                else ->
                    "GENEL LİG (${activeList.size})"
            }
        }
        LeagueTab.GROUPS_AND_FRIENDS -> {
            if (!uiState.groupName.isNullOrBlank()) {
                uiState.groupName.orEmpty().uppercase()
            } else {
                "ARKADAŞLAR & GRUPLAR"
            }
        }
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.myEntry?.let { me ->
                    MyTierCard(
                        entry = me,
                        myFriendCode = uiState.myFriendCode,
                        unlockedBadgeCount = uiState.myUnlockedBadges.size,
                        myTierRank = uiState.myTierRank,
                        myTierTotal = uiState.myTierTotal,
                        lastWeekOutcome = uiState.myLastWeekOutcome,
                        relegatedFromTier = uiState.myRelegatedFromTier,
                        onOpenLeagueTiers = { onOpenLeagueTiers(me.tier) },
                        onCopyFriendCode = { onCopyCode("Arkadaş kodun", uiState.myFriendCode) },
                        onShareFriendCode = {
                            onShareText(
                                "SharkMentor'da odak yarışına katıl. Arkadaş Kodum: ${uiState.myFriendCode}"
                            )
                        },
                        onEditBadgesClick = onOpenBadgeShowcase,
                        onShowcaseBadgeClick = onShowcaseBadgeClick,
                        onEditNameClick = onOpenEditName
                    )
                }
                SocialCodeActionCard(
                    myFriendCode = uiState.myFriendCode,
                    isGroupsTab = isGroupsTab,
                    hasJoinedGroup = uiState.joinedGroups.isNotEmpty(),
                    pendingDmCount = uiState.pendingDmRequestCount,
                    onOpenCreateGroup = onOpenCreateGroup,
                    onOpenJoinByCode = onOpenJoinByCode,
                    onCopyFriendCode = { onCopyCode("Arkadaş kodun", uiState.myFriendCode) },
                    onOpenGroupChat = onOpenGroupChat,
                    onOpenDmInbox = onOpenDmInbox
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isGroupsTab || uiState.institutionCode.isNotBlank()) {
                    item {
                        LeagueFilterSection(
                            uiState = uiState,
                            onOpenLeagueTiers = onOpenLeagueTiers,
                            onSelectTierFilter = onSelectTierFilter,
                            onSelectGroupFilter = onSelectGroupFilter,
                            onToggleInstitutionFilter = onToggleInstitutionFilter,
                            onCopyGroupCode = { code -> onCopyCode("Grup davet kodu", code) },
                            onShareGroupCode = { group ->
                                onShareText(
                                    "SharkMentor'da \"${group.name}\" çalışma grubuna katıl. 6 Haneli Grup Kodu: ${group.inviteCode}"
                                )
                            },
                            onOpenGroupChat = onOpenGroupChat, onManageGroup = onManageGroup
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = listHeaderTitle,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (activeList.isEmpty()) {
                    item {
                        EmptyLeagueStateCard(
                            isGroupsTab = isGroupsTab,
                            selectedTier = activeTierFilter,
                            onClearTierFilter = { onSelectTierFilter(null) },
                            onOpenCreateGroup = onOpenCreateGroup,
                            onOpenJoinByCode = onOpenJoinByCode
                        )
                    }
                } else {
                    itemsIndexed(activeList, key = { _, e -> e.studentId.ifBlank { e.displayName } }) { index, entry ->
                        val rank = index + 1
                        val rankZone = if (isGroupsTab) {
                            when (rank) {
                                1 -> LeagueRankZone.TOP_1
                                2 -> LeagueRankZone.TOP_2
                                3 -> LeagueRankZone.TOP_3
                                else -> LeagueRankZone.SAFE
                            }
                        } else {
                            SharkLeagueRules.resolveZoneForRank(
                                rank = rank,
                                totalInTier = activeList.size,
                                tier = activeTierFilter ?: entry.tier
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (relegationStartRank != null && rank == relegationStartRank) {
                                RelegationZoneDivider(tier = activeTierFilter)
                            }
                            LeaderboardRow(
                                rank = rank,
                                entry = entry,
                                rankZone = rankZone,
                                showNudgeButton = isGroupsTab && !entry.isCurrentUser,
                                onNudgeClick = { onNudgeMember(entry) },
                                onMemberClick = { onMemberClick(entry) }
                            )
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.myEntry?.let { me ->
                item {
                    MyTierCard(
                        entry = me,
                        myFriendCode = uiState.myFriendCode,
                        unlockedBadgeCount = uiState.myUnlockedBadges.size,
                        myTierRank = uiState.myTierRank,
                        myTierTotal = uiState.myTierTotal,
                        lastWeekOutcome = uiState.myLastWeekOutcome,
                        relegatedFromTier = uiState.myRelegatedFromTier,
                        onOpenLeagueTiers = { onOpenLeagueTiers(me.tier) },
                        onCopyFriendCode = { onCopyCode("Arkadaş kodun", uiState.myFriendCode) },
                        onShareFriendCode = {
                            onShareText(
                                "SharkMentor'da odak yarışına katıl. Arkadaş Kodum: ${uiState.myFriendCode}"
                            )
                        },
                        onEditBadgesClick = onOpenBadgeShowcase,
                        onShowcaseBadgeClick = onShowcaseBadgeClick,
                        onEditNameClick = onOpenEditName
                    )
                }
            }

            item {
                SocialCodeActionCard(
                    myFriendCode = uiState.myFriendCode,
                    isGroupsTab = isGroupsTab,
                    hasJoinedGroup = uiState.joinedGroups.isNotEmpty(),
                    pendingDmCount = uiState.pendingDmRequestCount,
                    onOpenCreateGroup = onOpenCreateGroup,
                    onOpenJoinByCode = onOpenJoinByCode,
                    onCopyFriendCode = { onCopyCode("Arkadaş kodun", uiState.myFriendCode) },
                    onOpenGroupChat = onOpenGroupChat,
                    onOpenDmInbox = onOpenDmInbox
                )
            }

            if (isGroupsTab || uiState.institutionCode.isNotBlank()) {
                item {
                    LeagueFilterSection(
                        uiState = uiState,
                        onOpenLeagueTiers = onOpenLeagueTiers,
                        onSelectTierFilter = onSelectTierFilter,
                        onSelectGroupFilter = onSelectGroupFilter,
                        onToggleInstitutionFilter = onToggleInstitutionFilter,
                        onCopyGroupCode = { code -> onCopyCode("Grup davet kodu", code) },
                        onShareGroupCode = { group ->
                            onShareText(
                                "SharkMentor'da \"${group.name}\" çalışma grubuna katıl. 6 Haneli Grup Kodu: ${group.inviteCode}"
                            )
                        },
                        onOpenGroupChat = onOpenGroupChat, onManageGroup = onManageGroup
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = listHeaderTitle,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.3.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }

            if (activeList.isEmpty()) {
                item {
                    EmptyLeagueStateCard(
                        isGroupsTab = isGroupsTab,
                        selectedTier = activeTierFilter,
                        onClearTierFilter = { onSelectTierFilter(null) },
                        onOpenCreateGroup = onOpenCreateGroup,
                        onOpenJoinByCode = onOpenJoinByCode
                    )
                }
            } else {
                itemsIndexed(activeList, key = { _, e -> e.studentId.ifBlank { e.displayName } }) { index, entry ->
                    val rank = index + 1
                    val rankZone = if (isGroupsTab) {
                        when (rank) {
                            1 -> LeagueRankZone.TOP_1
                            2 -> LeagueRankZone.TOP_2
                            3 -> LeagueRankZone.TOP_3
                            else -> LeagueRankZone.SAFE
                        }
                    } else {
                        SharkLeagueRules.resolveZoneForRank(
                            rank = rank,
                            totalInTier = activeList.size,
                            tier = activeTierFilter ?: entry.tier
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (relegationStartRank != null && rank == relegationStartRank) {
                            RelegationZoneDivider(tier = activeTierFilter)
                        }
                        LeaderboardRow(
                            rank = rank,
                            entry = entry,
                            rankZone = rankZone,
                            showNudgeButton = isGroupsTab && !entry.isCurrentUser,
                            onNudgeClick = { onNudgeMember(entry) },
                            onMemberClick = { onMemberClick(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeagueFilterSection(
    uiState: SharkLeagueUiState,
    onOpenLeagueTiers: (SharkTier?) -> Unit,
    onSelectTierFilter: (SharkTier?) -> Unit,
    onSelectGroupFilter: (String?) -> Unit,
    onToggleInstitutionFilter: (Boolean) -> Unit,
    onCopyGroupCode: (String) -> Unit,
    onShareGroupCode: (StudyGroupSummary) -> Unit,
    onOpenGroupChat: () -> Unit, onManageGroup: (StudyGroupSummary) -> Unit
) {
    if (uiState.activeTab == LeagueTab.GLOBAL_INDIVIDUAL) {
        if (uiState.institutionCode.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !uiState.useInstitutionFilter,
                    onClick = { onToggleInstitutionFilter(false) },
                    label = { Text("Genel Lig", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = uiState.useInstitutionFilter,
                    onClick = { onToggleInstitutionFilter(true) },
                    label = { Text("Kurum Ligi (${uiState.institutionCode})", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (uiState.joinedGroups.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedGroupCode == null,
                            onClick = { onSelectGroupFilter(null) },
                            label = { Text("Tümü") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    items(uiState.joinedGroups, key = { it.inviteCode }) { group ->
                        val selected = uiState.selectedGroupCode == group.inviteCode
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectGroupFilter(group.inviteCode) },
                            label = {
                                Text("${group.name} (${group.memberCount})")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                val selectedGroup = uiState.joinedGroups.firstOrNull { it.inviteCode == uiState.selectedGroupCode }
                if (selectedGroup != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedGroup.name} • ${selectedGroup.inviteCode}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = onOpenGroupChat) {
                                    Icon(
                                        imageVector = Icons.Default.Forum,
                                        contentDescription = "Grup Sohbeti",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onCopyGroupCode(selectedGroup.inviteCode) }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Kopyala",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onShareGroupCode(selectedGroup) }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Paylaş",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } 
IconButton(onClick = { onManageGroup(selectedGroup) }) { Icon(imageVector = Icons.Default.Edit, contentDescription = "Grubu Yönet", tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialCodeActionCard(
    myFriendCode: String,
    isGroupsTab: Boolean,
    hasJoinedGroup: Boolean,
    pendingDmCount: Int,
    onOpenCreateGroup: () -> Unit,
    onOpenJoinByCode: () -> Unit,
    onCopyFriendCode: () -> Unit,
    onOpenGroupChat: () -> Unit,
    onOpenDmInbox: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gruplar & Arkadaşlar",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (myFriendCode.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { onCopyFriendCode() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = myFriendCode,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Kopyala",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenJoinByCode,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GroupAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Kod Gir", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenCreateGroup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Grup Oluştur", fontWeight = FontWeight.Bold)
                }
            }

            if (isGroupsTab || hasJoinedGroup || pendingDmCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenGroupChat,
                        modifier = Modifier.weight(1.25f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Grup & Soru Sohbeti",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenDmInbox,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (pendingDmCount > 0) "İstekler ($pendingDmCount)" else "DM Kutusu",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyTierCard(
    entry: LeagueEntry,
    myFriendCode: String,
    unlockedBadgeCount: Int,
    myTierRank: Int = 0,
    myTierTotal: Int = 0,
    lastWeekOutcome: String? = null,
    relegatedFromTier: SharkTier? = null,
    onOpenLeagueTiers: () -> Unit = {},
    onCopyFriendCode: () -> Unit,
    onShareFriendCode: () -> Unit,
    onEditBadgesClick: () -> Unit,
    onShowcaseBadgeClick: (SharkBadgeDef) -> Unit = {},
    onEditNameClick: () -> Unit
) {
    val tier = entry.tier
    val accentColor = tier.themeAwareAccentColor()
    val focusSecs = entry.weeklyFocusSeconds.coerceAtLeast(0L)
    val currentSecs = entry.effectiveLeagueSeconds
    val cappedBonusMins = entry.cappedBonusSeconds / 60L
    val nextThresh = tier.nextThreshold()
    val prevThresh = tier.previousThreshold()
    val progressFrac = if (nextThresh != null && nextThresh > prevThresh) {
        ((currentSecs - prevThresh).toFloat() / (nextThresh - prevThresh).toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    val myRankZone = if (myTierRank > 0 && myTierTotal > 0) {
        SharkLeagueRules.resolveZoneForRank(myTierRank, myTierTotal, tier)
    } else {
        LeagueRankZone.SAFE
    }

    val hours = focusSecs / 3600
    val mins = (focusSecs % 3600) / 60
    val effHours = currentSecs / 3600
    val effMins = (currentSecs % 3600) / 60

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = SharkVisualKit.getTierBadge(tier)),
                        contentDescription = tier.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenLeagueTiers() }
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable { onEditNameClick() }
                        ) {
                            Text(
                                text = entry.rawName.ifBlank { "Öğrenci" },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Adı Düzenle",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenLeagueTiers() }
                                .padding(horizontal = 2.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = tier.label,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                color = accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (myFriendCode.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { onCopyFriendCode() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = myFriendCode,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Arkadaş Kodunu Kopyala",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onShareFriendCode,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Arkadaş Kodunu Paylaş",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (lastWeekOutcome == "RELEGATED" && relegatedFromTier != null) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Geçen hafta son 5 içinde yer aldığın için ${relegatedFromTier.label} liginden ${tier.label} ligine geçtin.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else if (lastWeekOutcome == "REPEATED_CAMGOZ") {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Geçen hafta son 5 içinde yer aldın; alt lig olmadığı için Camgöz ligini tekrar ediyorsun.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 3 Kıyaslama Metriği: Haftalık Odak, Günlük Ortalama, Aktif Seri
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricStatBox(
                    title = "Haftalık Odak",
                    value = "${hours}s ${mins}dk",
                    modifier = Modifier.weight(1f)
                )
                MetricStatBox(
                    title = "Günlük Ort.",
                    value = "${entry.dailyAverageMinutes} dk",
                    modifier = Modifier.weight(1f)
                )
                MetricStatBox(
                    title = "Aktif Seri",
                    value = "${entry.streakDays} gün",
                    highlight = entry.streakDays > 0,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (cappedBonusMins > 0L) {
                        "Lig Sıralama Süresi: ${effHours}s ${effMins}dk (Odak + $cappedBonusMins dk / maks. 150 dk bonus)"
                    } else {
                        "Lig Sıralama Formülü: Haftalık Odak + maks. 150 dk (9000 sn) soru bonusu"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Mentor Puanı & Kendi Vitrinim (Maks. 5 Rozet) Satırı
            val validShowcaseBadges = remember(entry.showcaseBadges) {
                entry.showcaseBadges
                    .mapNotNull { SharkBadgeCatalog.findById(it) }
                    .distinctBy { it.id }
                    .take(SharkBadgeCatalog.MAX_SHOWCASE_BADGES)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${entry.mentorPoints} Mentor Puanı • $unlockedBadgeCount/${SharkBadgeCatalog.ALL_BADGES.size} Rozet",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = onEditBadgesClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "Vitrini Düzenle (Maks. ${SharkBadgeCatalog.MAX_SHOWCASE_BADGES})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    if (validShowcaseBadges.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kendi Vitrinim",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Detay için dokun",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            validShowcaseBadges.forEach { badge ->
                                val progressInfo = badge.progressFor(
                                    rawCount = entry.badgeProgress[badge.id] ?: 0,
                                    isLegacyUnlocked = true
                                )
                                SharkBadgeChip(
                                    badge = badge,
                                    tier = progressInfo.unlockedTier ?: BadgeTier.BRONZE,
                                    compact = true,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onShowcaseBadgeClick(badge) }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Soru paylaşarak veya onaylı çözüm üreterek rozet kazanıp 5 rozet vitrininizi sergileyin.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Shark Rütbesi İlerleme Çubuğu
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Rütbe İlerlemesi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (nextThresh != null) {
                            val remSecs = (nextThresh - currentSecs).coerceAtLeast(0L)
                            val remH = remSecs / 3600
                            val remM = (remSecs % 3600) / 60
                            "Sonraki: ${remH}s ${remM}dk"
                        } else {
                            "En üst rütbe"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accentColor
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFrac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricStatBox(
    title: String,
    value: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RelegationZoneDivider(
    tier: SharkTier?,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.error
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "Lig düşme",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            ),
            color = lineColor
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.5.dp,
            color = lineColor
        )
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeagueEntry,
    rankZone: LeagueRankZone = LeagueRankZone.SAFE,
    showNudgeButton: Boolean,
    onNudgeClick: () -> Unit,
    onMemberClick: () -> Unit
) {
    val accentColor = entry.tier.themeAwareAccentColor()
    val hours = entry.weeklyFocusSeconds / 3600
    val mins = (entry.weeklyFocusSeconds % 3600) / 60
    val effHours = entry.effectiveLeagueSeconds / 3600
    val effMins = (entry.effectiveLeagueSeconds % 3600) / 60
    val bonusMins = entry.cappedBonusSeconds / 60L
    val nowMs = System.currentTimeMillis()
    val canNudge = entry.canBeNudged(nowMs)

    val podiumAccentColor: Color? = when (rankZone) {
        LeagueRankZone.TOP_1 -> PODIUM_GOLD_COLOR
        LeagueRankZone.TOP_2 -> PODIUM_SILVER_COLOR
        LeagueRankZone.TOP_3 -> PODIUM_BRONZE_COLOR
        else -> null
    }
    val podiumBadgeLabel: String? = when (rankZone) {
        LeagueRankZone.TOP_1 -> "1. LİDER"
        LeagueRankZone.TOP_2 -> "2. SIRA"
        LeagueRankZone.TOP_3 -> "3. SIRA"
        else -> null
    }
    val podiumIcon = when (rankZone) {
        LeagueRankZone.TOP_1 -> Icons.Default.EmojiEvents
        LeagueRankZone.TOP_2 -> Icons.Default.WorkspacePremium
        LeagueRankZone.TOP_3 -> Icons.Default.MilitaryTech
        else -> null
    }

    val validMemberShowcase = remember(entry.showcaseBadges) {
        entry.showcaseBadges
            .map { it.trim().substringBefore(":") }
            .filter { SharkBadgeCatalog.findById(it) != null }
            .distinct()
            .take(SharkBadgeCatalog.MAX_SHOWCASE_BADGES)
    }

    val containerColor = when {
        entry.isCurrentUser -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
        podiumAccentColor != null -> podiumAccentColor.copy(alpha = 0.08f)
        rankZone.isRelegationZone -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderWidth = when {
        rankZone == LeagueRankZone.TOP_1 || entry.isCurrentUser -> 1.8.dp
        rankZone.isTopThree -> 1.5.dp
        rankZone.isRelegationZone -> 1.2.dp
        else -> 1.dp
    }
    val borderColor = when {
        podiumAccentColor != null -> podiumAccentColor.copy(alpha = if (rankZone == LeagueRankZone.TOP_1) 0.9f else 0.7f)
        rankZone.isRelegationZone -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        entry.isCurrentUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(width = borderWidth, color = borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMemberClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sıralama Rozeti (İlk 3 için Altın/Gümüş/Bronz Podyum ikonu)
                Surface(
                    shape = CircleShape,
                    color = when {
                        podiumAccentColor != null -> podiumAccentColor.copy(alpha = 0.18f)
                        rankZone.isRelegationZone -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (podiumAccentColor != null) {
                        BorderStroke(1.dp, podiumAccentColor.copy(alpha = 0.6f))
                    } else null,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (podiumIcon != null && podiumAccentColor != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = podiumIcon,
                                contentDescription = podiumBadgeLabel,
                                tint = podiumAccentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "#$rank",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 10.sp
                                ),
                                color = podiumAccentColor
                            )
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "#$rank",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = if (rankZone.isRelegationZone) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                SharkAvatar(
                    avatarBase64 = entry.avatarBase64,
                    avatarPresetId = entry.avatarPresetId,
                    displayName = entry.rawName.ifBlank { entry.displayName },
                    size = 38.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = entry.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (entry.isCurrentUser || rankZone.isTopThree) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = if (entry.isCurrentUser) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (podiumBadgeLabel != null && podiumAccentColor != null) {
                            Surface(
                                color = podiumAccentColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = podiumBadgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = podiumAccentColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        } else if (rankZone.isRelegationZone) {
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (rankZone == LeagueRankZone.RELEGATION_REPEAT) "Tekrar" else "Düşme",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (entry.isStudyingNow) {
                            val activeTopicText = com.example.sharkmentor.core.coaching.CoachingEngine
                                .formatCleanLeagueSubject(entry.activeSubject.orEmpty())
                                .takeIf { it.isNotBlank() && !it.equals("Genel", ignoreCase = true) }
                            val badgeLabel = if (activeTopicText != null) {
                                "Masada • $activeTopicText"
                            } else {
                                "Masada • Odakta"
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = badgeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${entry.tier.label} • ${entry.mentorPoints} P",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.streakDays > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WaterDrop,
                                    contentDescription = "Su Serisi",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${entry.streakDays}g",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${effHours}s ${effMins}dk",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                        color = podiumAccentColor ?: accentColor
                    )
                    if (bonusMins > 0L) {
                        Text(
                            text = "Odak ${hours}s ${mins}dk + ${bonusMins}dk",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showNudgeButton) {
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = if (canNudge) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (canNudge) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.clickable { onNudgeClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Dürt",
                                    tint = if (canNudge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (canNudge) "Dürt" else entry.remainingNudgeCooldownLabel(nowMs),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (canNudge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (validMemberShowcase.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SharkBadgeShowcaseRow(
                    badgeIds = validMemberShowcase,
                    compact = true,
                    badgeProgress = entry.badgeProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharkLeagueTiersSheet(
    selectedTier: SharkTier,
    myTier: SharkTier?,
    entriesByTier: Map<SharkTier, List<LeagueEntry>>,
    tierCounts: Map<SharkTier, Int>,
    onSelectTier: (SharkTier) -> Unit,
    onApplyTierFilter: (SharkTier) -> Unit,
    onMemberClick: (LeagueEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedEntries = entriesByTier[selectedTier].orEmpty()
    val relegationStartRank = SharkLeagueRules.relegationStartRank(selectedEntries.size)
    val lowerTier = SharkLeagueRules.lowerTier(selectedTier)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Shark Ligleri (${SharkLeagueRules.TOTAL_LEAGUES} Lig)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Her ligdeki öğrencileri ve sıralama kurallarını incele",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Kural özeti: İlk 3 Podyum + Son 5 Düşme Limiti
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = PODIUM_GOLD_COLOR.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, PODIUM_GOLD_COLOR.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = PODIUM_GOLD_COLOR,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "İlk 3: Podyum",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PODIUM_GOLD_COLOR
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Son 5: Alt Lige Düşer",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 6 Lig Seçicisi (Megalodon -> Camgöz)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(SharkLeagueRules.LADDER_HIGH_TO_LOW, key = { it.name }) { tier ->
                    val isSelected = tier == selectedTier
                    val isMyTier = tier == myTier
                    val count = tierCounts[tier] ?: 0
                    val tierAccent = tier.themeAwareAccentColor()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.6.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            }
                        ),
                        modifier = Modifier
                            .width(122.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectTier(tier) }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(
                                        id = SharkVisualKit.getTierBadge(tier)
                                    ),
                                    contentDescription = tier.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                if (isMyTier) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Sen",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = tier.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else tierAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$count kişi • ${SharkLeagueRules.weeklyRequirementLabel(tier)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Seçili Lig Özet Kartı
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${selectedTier.label} Ligi (${selectedEntries.size} öğrenci)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (lowerTier != null) {
                                "Haftalık eşik: ${SharkLeagueRules.weeklyRequirementLabel(selectedTier)} • Son 5 → ${lowerTier.label}"
                            } else {
                                "Haftalık eşik: ${SharkLeagueRules.weeklyRequirementLabel(selectedTier)} • Alt lig yok (Son 5 tekrar)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Seçili Ligdeki Öğrenciler Listesi
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedEntries.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${selectedTier.label} liginde henüz öğrenci yok",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Bu lige yükselmek için hedef: ${SharkLeagueRules.weeklyRequirementLabel(selectedTier)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        selectedEntries,
                        key = { _, e -> "sheet_${selectedTier.name}_${e.studentId.ifBlank { e.displayName }}" }
                    ) { index, entry ->
                        val rank = index + 1
                        val rankZone = SharkLeagueRules.resolveZoneForRank(
                            rank = rank,
                            totalInTier = selectedEntries.size,
                            tier = selectedTier
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (relegationStartRank != null && rank == relegationStartRank) {
                                RelegationZoneDivider(tier = selectedTier)
                            }
                            LeaderboardRow(
                                rank = rank,
                                entry = entry,
                                rankZone = rankZone,
                                showNudgeButton = false,
                                onNudgeClick = {},
                                onMemberClick = { onMemberClick(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLeagueStateCard(
    isGroupsTab: Boolean,
    selectedTier: SharkTier? = null,
    onClearTierFilter: () -> Unit = {},
    onOpenCreateGroup: () -> Unit,
    onOpenJoinByCode: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when {
                    isGroupsTab -> "Grup veya arkadaş yok"
                    selectedTier != null -> "${selectedTier.label} liginde henüz öğrenci yok"
                    else -> "Bu hafta veri yok"
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (selectedTier != null && !isGroupsTab) {
                Text(
                    text = "Haftalık eşik: ${SharkLeagueRules.weeklyRequirementLabel(selectedTier)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = onClearTierFilter) {
                    Text("Tüm Ligleri Göster")
                }
            } else if (isGroupsTab) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenJoinByCode) {
                        Text("Kod Gir")
                    }
                    Button(onClick = onOpenCreateGroup) {
                        Text("Grup Oluştur")
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateStudyGroupDialog(
    onDismiss: () -> Unit,
    onCreateGroup: (String) -> Unit
) {
    var groupName by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Grup Oluştur",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it.take(40) },
                label = { Text("Grup Adı") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreateGroup(groupName) },
                enabled = groupName.trim().length >= 2
            ) {
                Text("Oluştur")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
private fun JoinByCodeDialog(
    myFriendCode: String,
    onDismiss: () -> Unit,
    onJoinCode: (String) -> Unit
) {
    var inputCode by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Kod ile Katıl",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (myFriendCode.isNotBlank()) {
                    Text(
                        text = "Kodun: $myFriendCode",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { inputCode = it.uppercase().take(12) },
                    label = { Text("Arkadaş / Grup Kodu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoinCode(inputCode) },
                enabled = inputCode.trim().length >= 4
            ) {
                Text("Katıl")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) {
                    Text("Tekrar Dene")
                }
            }
        }
    }
}
