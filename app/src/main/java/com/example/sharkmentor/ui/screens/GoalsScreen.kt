package com.example.sharkmentor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.ui.theme.ScoreGreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharkmentor.ui.viewmodel.GoalsViewModel
import com.example.sharkmentor.ui.viewmodel.SubjectFolderUiModel
import com.example.sharkmentor.ui.components.AddConcreteTargetDialog
import com.example.sharkmentor.core.coaching.AiCoachLedgerManager
import com.example.sharkmentor.core.coaching.BookCampManager
import com.example.sharkmentor.core.coaching.BookCoachingAssignment
import com.example.sharkmentor.core.coaching.BookCoachingRole
import com.example.sharkmentor.core.coaching.BookCoachingRoleManager
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.coaching.LibraryFilterEngine
import com.example.sharkmentor.core.coaching.LibraryFilterState
import com.example.sharkmentor.core.coaching.LibrarySortMode
import com.example.sharkmentor.core.coaching.LibraryStatusFilter
import com.example.sharkmentor.core.coaching.TopicStudyActionKind
import com.example.sharkmentor.ui.coaching.CampVideoStudyStudioDialog
import com.example.sharkmentor.ui.coaching.ResourceCampConfigDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalsScreen(
    modifier: Modifier = Modifier,
    currentModule: NavModule,
    isAddTopicDialogOpen: Boolean,
    onCloseTopicDialog: () -> Unit,
    isAddResourceDialogOpen: Boolean,
    onCloseResourceDialog: () -> Unit,
    aiImportBannerContent: @Composable () -> Unit = {},
    longTermTargetsListContent: @Composable () -> Unit = {},
    viewModel: GoalsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val folders = uiState.groupedTopics
    val resources = uiState.resources
    val context = LocalContext.current
    val allBookAssignments by BookCoachingRoleManager.assignmentsByResourceId.collectAsStateWithLifecycle()
    var libraryFilterState by remember { mutableStateOf(LibraryFilterState()) }
    var isLibraryFilterPanelOpen by remember { mutableStateOf(false) }

    LaunchedEffect(resources, allBookAssignments) {
        val pruned = LibraryFilterEngine.pruneStaleSelections(
            state = libraryFilterState,
            resources = resources,
            assignments = allBookAssignments
        )
        if (pruned != libraryFilterState) {
            libraryFilterState = pruned
        }
    }

    val availableLibrarySubjects = remember(resources, allBookAssignments) {
        LibraryFilterEngine.availableSubjects(resources, allBookAssignments)
    }
    val availableLibraryRoles = remember(resources, allBookAssignments, libraryFilterState.selectedSubjects) {
        LibraryFilterEngine.availableRoles(
            resources = resources,
            assignments = allBookAssignments,
            selectedSubjects = libraryFilterState.selectedSubjects
        )
    }
    val filteredResources = remember(resources, allBookAssignments, libraryFilterState) {
        LibraryFilterEngine.filterAndSort(
            resources = resources,
            assignments = allBookAssignments,
            state = libraryFilterState
        )
    }
    val isScanning = uiState.isScanning
    val scanError = uiState.scanError
    val lastScannedUri = uiState.lastScannedUri

    val target = uiState.concreteTarget
    val isAnalyzingTarget = uiState.isAnalyzingTarget
    var isTargetDialogOpen by remember { mutableStateOf(false) }
    var isTargetExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        CoachingEngine.refreshTargetAndLedgerAsync(context)
        BookCoachingRoleManager.ensureLoaded(context)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.extractTopicsFromDocument(context, uri.toString())
        }
    }
    val expandedStates by viewModel.expandedSubjectNames.collectAsStateWithLifecycle()
    var topicToDelete by remember { mutableStateOf<TopicGoalEntity?>(null) }
    var subjectToDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- HERO TARGET CARD ---
        if (currentModule == NavModule.GOALS_ROUTE) {
            item(key = "target_hero_card") {
            if (target == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight()
                        .clickable { isTargetDialogOpen = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Somut Hedefini Belirle - Üniversite, bölüm veya derece hedefini ekleyerek yol haritanı oluştur.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight()
                        .animateContentSize()
                        .clickable { isTargetExpanded = !isTargetExpanded },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = target!!.examType,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = target!!.targetTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            IconButton(onClick = { viewModel.deleteTarget() }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Hedefi Sil",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        if (isTargetExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Default.AutoGraph,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = target!!.aiAnalysisSummary.ifBlank { "Hedef detayları kaydedildi." },
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Hedef detaylarını görmek için dokun",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            }
            
            item(key = "long_term_targets") {
                longTermTargetsListContent()
            }
        }
        // --- CONTENT ---
        if (currentModule == NavModule.TOPIC_MASTERY) {
            if (uiState.isLoading) {
                item(key = "loading_topics") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        repeat(5) {
                            com.example.sharkmentor.ui.components.SkeletonItem()
                        }
                    }
                }
            } else if (folders.isEmpty()) {
                item(key = "empty_topics") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Henüz konu eklenmedi.\nSağ alttaki '+' butonunu kullanarak konu ekleyebilirsiniz.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(
                    items = folders,
                    key = { "folder_${it.subjectName}" },
                    contentType = { "subject_folder" }
                ) { folder ->
                    val isExpanded = expandedStates.contains(folder.subjectName)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .animateContentSize(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // FOLDER HEADER
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleSubjectFolder(folder.subjectName) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = folder.subjectName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${folder.topics.size} Konu • Ort. Seviye: ${String.format("%.1f", folder.averageLevel)}/5",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { subjectToDelete = folder.subjectName }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Dersi Sil",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Genişlet",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // FOLDER EXPANDED TOPICS
                            if (isExpanded) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    folder.topics.forEach { topic ->
                                        TopicItemRow(
                                            topic = topic,
                                            onLevelSelected = remember(topic) { { lvl -> viewModel.updateTopicGoal(topic.copy(currentLevel = lvl)) } },
                                            onDeleteClick = remember(topic) { { topicToDelete = topic } }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (currentModule == NavModule.LIBRARY) {
            if (resources.isNotEmpty()) {
                item(key = "library_role_guide_banner") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Kitaba dokunarak Konu/Video, İlerleme #1, Tekrar #1 veya Pasif rolünü belirleyebilirsin.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (resources.isNotEmpty()) {
                item(key = "library_search_filter_sort_bar") {
                    val activeCount = libraryFilterState.activeFilterCount
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = libraryFilterState.query,
                                onValueChange = { q -> libraryFilterState = libraryFilterState.copy(query = q) },
                                placeholder = { Text("Kitap, ders veya tür ara...", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (libraryFilterState.query.isNotEmpty()) {
                                        IconButton(onClick = { libraryFilterState = libraryFilterState.copy(query = "") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Temizle", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )

                            Surface(
                                onClick = { isLibraryFilterPanelOpen = !isLibraryFilterPanelOpen },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isLibraryFilterPanelOpen || activeCount > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isLibraryFilterPanelOpen || activeCount > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                    }
                                ),
                                modifier = Modifier.height(54.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    val fg = if (isLibraryFilterPanelOpen || activeCount > 0) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Filtrele",
                                        tint = fg,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = if (activeCount > 0) "Filtrele ($activeCount)" else "Filtrele",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = fg,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Compact active filter indicator strip when panel is collapsed
                        if (!isLibraryFilterPanelOpen && activeCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${filteredResources.size} / ${resources.size} kitap gösteriliyor",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Filtreleri Sıfırla",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            libraryFilterState = LibraryFilterState(query = libraryFilterState.query)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Collapsible Dynamic Multi-Select Filter Panel
                        AnimatedVisibility(
                            visible = isLibraryFilterPanelOpen,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Header + Reset
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Kitap Filtreleri (${filteredResources.size}/${resources.size})",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (libraryFilterState.hasAnyFilterOrSort) {
                                            Text(
                                                text = "Sıfırla",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { libraryFilterState = LibraryFilterState() }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // 1. Durum (Tümü / Devam Eden / Tamamlanan)
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Durum",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            LibraryStatusFilter.entries.forEach { statusOpt ->
                                                val selected = libraryFilterState.status == statusOpt
                                                LibraryFilterPillChip(
                                                    label = statusOpt.label,
                                                    selected = selected,
                                                    onClick = {
                                                        libraryFilterState = libraryFilterState.copy(status = statusOpt)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // 2. Dinamik Dersler (Multi-select — ONLY subjects actually present in student's library!)
                                    if (availableLibrarySubjects.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Dersler (Kütüphanendekiler)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                availableLibrarySubjects.forEach { subject ->
                                                    val selected = subject in libraryFilterState.selectedSubjects
                                                    LibraryFilterPillChip(
                                                        label = subject,
                                                        selected = selected,
                                                        onClick = {
                                                            val next = if (selected) {
                                                                libraryFilterState.selectedSubjects - subject
                                                            } else {
                                                                libraryFilterState.selectedSubjects + subject
                                                            }
                                                            libraryFilterState = libraryFilterState.copy(selectedSubjects = next)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 3. Dinamik Kitap Türü (Multi-select — ONLY BookCoachingRole types present!)
                                    if (availableLibraryRoles.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Kitap Türü",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                availableLibraryRoles.forEach { role ->
                                                    val selected = role in libraryFilterState.selectedRoles
                                                    val roleLabel = when (role) {
                                                        BookCoachingRole.CONCEPT_VIDEO -> "Konu Anlatım / Video"
                                                        BookCoachingRole.PRIMARY_PRACTICE -> "İlerleme / Soru"
                                                        BookCoachingRole.REVISION_BOOK -> "Tekrar Kitabı"
                                                        BookCoachingRole.MOCK_EXAM -> "Deneme"
                                                        BookCoachingRole.PASSIVE -> "Pasif"
                                                    }
                                                    LibraryFilterPillChip(
                                                        label = roleLabel,
                                                        selected = selected,
                                                        onClick = {
                                                            val next = if (selected) {
                                                                libraryFilterState.selectedRoles - role
                                                            } else {
                                                                libraryFilterState.selectedRoles + role
                                                            }
                                                            libraryFilterState = libraryFilterState.copy(selectedRoles = next)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 4. Sıralama
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Sıralama",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            LibrarySortMode.entries.forEach { mode ->
                                                val selected = libraryFilterState.sortMode == mode
                                                LibraryFilterPillChip(
                                                    label = mode.label,
                                                    selected = selected,
                                                    onClick = {
                                                        libraryFilterState = libraryFilterState.copy(sortMode = mode)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (filteredResources.isEmpty()) {
                    item(key = "library_empty_filtered_state") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Seçili filtrelere uyan kitap bulunamadı.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { libraryFilterState = LibraryFilterState() }) {
                                    Text("Filtreleri Temizle", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            items(
                items = filteredResources,
                key = { "resource_${it.id}" },
                contentType = { "resource_card" }
            ) { res ->
                ResourceCard(
                    resource = res,
                    onIncrement = remember(res.id) { { viewModel.incrementResource(res.id) } },
                    onDecrement = remember(res.id) { { viewModel.decrementResource(res.id) } },
                    onSetValue = remember(res.id) { { newValue -> viewModel.setResourceUnits(res, newValue) } },
                    onDelete = remember(res.id) { { viewModel.deleteResource(res) } }
                )
            }
        }
    }

    // TOPIC DELETE CONFIRMATION DIALOG
    topicToDelete?.let { topic ->
        AlertDialog(
            onDismissRequest = { topicToDelete = null },
            title = { Text("Konuyu Sil") },
            text = { Text("\"${topic.topic}\" konusunu silmek istediğinizden emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTopic(topic)
                        topicToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { topicToDelete = null }) {
                    Text("İptal")
                }
            }
        )
    }

    // SUBJECT FOLDER DELETE CONFIRMATION DIALOG
    subjectToDelete?.let { subject ->
        AlertDialog(
            onDismissRequest = { subjectToDelete = null },
            title = { Text("Ders Klasörünü Sil") },
            text = { Text("\"$subject\" dersine ait tüm konular silinecektir. Devam edilsin mi?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSubjectFolder(subject)
                        subjectToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Tümünü Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { subjectToDelete = null }) {
                    Text("İptal")
                }
            }
        )
    }

    if (isAddTopicDialogOpen) {
        AddTopicGoalDialog(
            onDismiss = onCloseTopicDialog,
            onSave = { subject, topic, targetLvl ->
                viewModel.insertTopicGoal(subject, topic, targetLvl)
                onCloseTopicDialog()
            }
        )
    }

    if (isAddResourceDialogOpen) {
        AddEditResourceDialog(
            onDismiss = onCloseResourceDialog,
            onSave = { title, subject, totalUnits, unitType ->
                viewModel.insertResource(title, subject, totalUnits, unitType)
                onCloseResourceDialog()
            }
        )
    }

    if (isTargetDialogOpen) {
        AddConcreteTargetDialog(
            onDismiss = { isTargetDialogOpen = false },
            onSave = { examType, title ->
                viewModel.saveTarget(examType, title)
                isTargetDialogOpen = false
            }
        )
    }

    if (isAnalyzingTarget) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Hedef Analiz Ediliyor") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("AI hedefinizi inceliyor, lütfen bekleyin...")
                }
            },
            confirmButton = {}
        )
    }
}



@Composable
fun TopicItemRow(
    topic: TopicGoalEntity,
    onLevelSelected: (Int) -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // If subject is part of the context or we just show the topic name
                Text(
                    text = topic.topic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 5-Level Clickable Dots
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..5).forEach { level ->
                    val isSelected = topic.currentLevel >= level
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                            .clickable {
                                val newLevel = if (topic.currentLevel == level) level - 1 else level
                                onLevelSelected(newLevel)
                            }
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}



@Composable
fun ResourceCard(
    resource: ResourceEntity,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetValue: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allBindings by BookCampManager.bindingsByResourceId.collectAsStateWithLifecycle()
    val allAssignments by BookCoachingRoleManager.assignmentsByResourceId.collectAsStateWithLifecycle()

    val campBinding = remember(resource, allBindings) {
        BookCampManager.resolveCampForResource(context, resource)
    }
    val roleAssignment = remember(resource, allAssignments, campBinding.hasVerifiedVideoSupport) {
        BookCoachingRoleManager.resolveAssignment(
            resource = resource,
            assignments = allAssignments,
            hasVerifiedVideo = campBinding.hasVerifiedVideoSupport
        )
    }
    val isPassive = roleAssignment.role == BookCoachingRole.PASSIVE
    val isVideoBlocked = roleAssignment.videoDisabled || isPassive

    LaunchedEffect(resource.id, resource.title, resource.subject, isVideoBlocked) {
        BookCampManager.ensureLoaded(context)
        BookCoachingRoleManager.ensureLoaded(context)
        if (!isVideoBlocked) {
            val current = BookCampManager.resolveCampForResource(context, resource)
            if (!current.videoCheckCompleted) {
                BookCampManager.discoverAndSyncRealCampForResource(context, resource)
            }
        }
    }

    var inputText by remember(resource.completedUnits) { mutableStateOf(resource.completedUnits.toString()) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRoleDialog by remember { mutableStateOf(false) }
    var showCampConfigDialog by remember { mutableStateOf(false) }
    var showCampStudioDialog by remember { mutableStateOf(false) }

    val progressFraction = if (resource.totalUnits > 0) {
        (resource.completedUnits.toFloat() / resource.totalUnits.toFloat()).coerceIn(0f, 1f)
    } else 0f

    if (showRoleDialog) {
        BookRoleAssignmentDialog(
            resource = resource,
            initialAssignment = roleAssignment,
            hasVerifiedVideo = campBinding.hasVerifiedVideoSupport,
            onSave = { selectedRole, selectedPriority, videoOff ->
                BookCoachingRoleManager.setAssignment(
                    context = context,
                    resource = resource,
                    role = selectedRole,
                    priorityOrder = selectedPriority,
                    videoDisabled = videoOff
                )
                AiCoachLedgerManager.recordChatTakeawayInBackground(
                    context = context,
                    category = "BOOK",
                    digest = "${resource.subject} - ${resource.title}: ${selectedRole.shortBadge} #$selectedPriority${if (videoOff) " (Videosuz)" else ""} olarak ayarlandı."
                )
                showRoleDialog = false
            },
            onOpenVideoConfig = { selectedRole, selectedPriority ->
                BookCoachingRoleManager.setAssignment(
                    context = context,
                    resource = resource,
                    role = selectedRole,
                    priorityOrder = selectedPriority,
                    videoDisabled = false
                )
                showRoleDialog = false
                showCampConfigDialog = true
            },
            onDismiss = { showRoleDialog = false }
        )
    }

    if (showCampConfigDialog) {
        ResourceCampConfigDialog(
            resource = resource,
            initialBinding = campBinding,
            onSaved = { },
            onOpenStudio = {
                showCampConfigDialog = false
                showCampStudioDialog = true
            },
            onDismiss = { showCampConfigDialog = false }
        )
    }

    if (showCampStudioDialog && campBinding.hasVerifiedVideoSupport && !isVideoBlocked) {
        val currentEp = campBinding.currentEpisode()
        CampVideoStudyStudioDialog(
            campTitle = campBinding.campTitle,
            subject = resource.subject,
            initialEpisode = currentEp,
            allEpisodes = campBinding.episodes.ifEmpty { listOf(currentEp) },
            resourceId = resource.id,
            onEpisodeChanged = { chosenEp ->
                BookCampManager.advanceCampDay(context, resource, targetDay = chosenEp.dayNumber)
            },
            onMarkCompleted = { completedEp ->
                BookCampManager.advanceCampDay(context, resource, targetDay = completedEp.dayNumber + 1)
                AiCoachLedgerManager.recordTopicStudyInBackground(
                    context = context,
                    subject = resource.subject,
                    topic = completedEp.mappedTopic.ifBlank { completedEp.topicTitle },
                    actionKind = if (completedEp.isQuestionSolvingVideo) {
                        TopicStudyActionKind.PRIMARY_PRACTICE
                    } else {
                        TopicStudyActionKind.CONCEPT_STUDY
                    },
                    bookTitle = resource.title,
                    completedVideoId = completedEp.videoId
                )
                onIncrement()
                showCampStudioDialog = false
            },
            onDismiss = { showCampStudioDialog = false }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = "İlerleme Gir",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${resource.title} (${resource.subject})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = {
                            val maxSuffix = if (resource.totalUnits > 0) " (Max: ${resource.totalUnits})" else ""
                            Text("Tamamlanan ${resource.unitType}$maxSuffix")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newValue = inputText.toIntOrNull() ?: resource.completedUnits
                        onSetValue(newValue)
                        showEditDialog = false
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Kaynağı Sil") },
            text = { Text("Bu kaynağı silmek istediğinize emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        BookCoachingRoleManager.removeAssignment(context, resource.id)
                        onDelete()
                    }
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPassive) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = if (isPassive) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Clickable to assign Book Role & Priority + Delete Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showRoleDialog = true }
                        .padding(end = 6.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = resource.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isPassive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = resource.subject,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPassive) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val badgeContainer = when (roleAssignment.role) {
                            BookCoachingRole.CONCEPT_VIDEO -> MaterialTheme.colorScheme.primaryContainer
                            BookCoachingRole.PRIMARY_PRACTICE -> MaterialTheme.colorScheme.secondaryContainer
                            BookCoachingRole.REVISION_BOOK -> MaterialTheme.colorScheme.tertiaryContainer
                            BookCoachingRole.MOCK_EXAM -> MaterialTheme.colorScheme.surfaceContainerHighest
                            BookCoachingRole.PASSIVE -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val badgeContent = when (roleAssignment.role) {
                            BookCoachingRole.CONCEPT_VIDEO -> MaterialTheme.colorScheme.onPrimaryContainer
                            BookCoachingRole.PRIMARY_PRACTICE -> MaterialTheme.colorScheme.onSecondaryContainer
                            BookCoachingRole.REVISION_BOOK -> MaterialTheme.colorScheme.onTertiaryContainer
                            BookCoachingRole.MOCK_EXAM -> MaterialTheme.colorScheme.onSurface
                            BookCoachingRole.PASSIVE -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            onClick = { showRoleDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = badgeContainer,
                            contentColor = badgeContent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Kitap Rolünü Ayarla",
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = roleAssignment.formattedBadge(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (roleAssignment.videoDisabled && !isPassive) {
                            Surface(
                                onClick = { showRoleDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VideocamOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "Sadece Kitap",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    if (resource.isAssignedByCoach) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            androidx.compose.ui.graphics.Color(0xFFFFCA28),
                                            androidx.compose.ui.graphics.Color(0xFFFF9800)
                                        )
                                    ),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Koç Görevi",
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Kaynağı Sil"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (resource.totalUnits > 0) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isPassive) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Modern Stepper Row with Clickable Numeric Input
            val canIncrement = resource.totalUnits <= 0 || resource.completedUnits < resource.totalUnits
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            inputText = resource.completedUnits.toString()
                            showEditDialog = true
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (resource.totalUnits > 0) {
                            "${resource.completedUnits} / ${resource.totalUnits} ${resource.unitType}"
                        } else {
                            "${resource.completedUnits} ${resource.unitType} çözüldü"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPassive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Sayıyı Düzenle",
                        tint = if (isPassive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Sleek Material 3 Stepper Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = onDecrement,
                        enabled = resource.completedUnits > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Azalt",
                            tint = if (resource.completedUnits > 0)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    IconButton(
                        onClick = onIncrement,
                        enabled = canIncrement,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Artır",
                            tint = if (canIncrement)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // ── Mark 67 Verified-Only Book Video Support Bar (Respects Video Opt-Out & Passive Role) ──
            if (!isVideoBlocked && campBinding.hasVerifiedVideoSupport) {
                Spacer(modifier = Modifier.height(10.dp))
                val currentEp = campBinding.currentEpisode()
                val headerLabel = if (
                    campBinding.instructor.isNotBlank() &&
                    !campBinding.campTitle.contains(campBinding.instructor, ignoreCase = true)
                ) {
                    "${campBinding.campTitle} (${campBinding.instructor})"
                } else {
                    campBinding.campTitle
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = headerLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${campBinding.currentDay}/${campBinding.totalDays}. Ders • ${currentEp.topicTitle}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = { showCampConfigDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Liste Ayarla",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Liste",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Surface(
                                onClick = { showCampStudioDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleOutline,
                                        contentDescription = "Stüdyoda İzle",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "İzle",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (!isVideoBlocked) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showRoleDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Rol & Sıra Ayarla",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { showCampConfigDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                campBinding.candidatePlaylists.isNotEmpty() ->
                                    "${campBinding.candidatePlaylists.take(3).size} Liste Önerisi • Seç"
                                campBinding.videoCheckCompleted ->
                                    "Video Listesi Bağla"
                                else ->
                                    "Liste Aranıyor..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (campBinding.candidatePlaylists.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (campBinding.candidatePlaylists.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookRoleAssignmentDialog(
    resource: ResourceEntity,
    initialAssignment: BookCoachingAssignment,
    hasVerifiedVideo: Boolean,
    onSave: (role: BookCoachingRole, priorityOrder: Int, videoDisabled: Boolean) -> Unit,
    onOpenVideoConfig: (role: BookCoachingRole, priorityOrder: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRole by remember(resource.id, initialAssignment.role) { mutableStateOf(initialAssignment.role) }
    var selectedPriority by remember(resource.id, initialAssignment.priorityOrder) {
        mutableStateOf(initialAssignment.priorityOrder.coerceIn(1, 5))
    }
    var videoDisabled by remember(resource.id, initialAssignment.videoDisabled) {
        mutableStateOf(initialAssignment.videoDisabled)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Kitap Koçluk Rolü",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${resource.title} • ${resource.subject}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Yapay zeka koçunun bu kitabı haftalık planda nasıl kullanacağını seç:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                BookCoachingRole.entries.forEach { role ->
                    val isSelected = selectedRole == role
                    Surface(
                        onClick = {
                            selectedRole = role
                            if (role == BookCoachingRole.PASSIVE) {
                                videoDisabled = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = role.displayTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = role.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                if (selectedRole != BookCoachingRole.PASSIVE) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Öncelik Sırası (${selectedRole.shortBadge})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (1..5).forEach { order ->
                            val picked = selectedPriority == order
                            Surface(
                                onClick = { selectedPriority = order },
                                shape = RoundedCornerShape(10.dp),
                                color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$order.",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Video Desteği Olmasın",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sadece kitap olarak ilerle, YouTube videosu önerme.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = videoDisabled,
                            onCheckedChange = { videoDisabled = it }
                        )
                    }

                    if (!videoDisabled) {
                        OutlinedButton(
                            onClick = { onOpenVideoConfig(selectedRole, selectedPriority) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasVerifiedVideo) "YouTube Oynatma Listesini Değiştir" else "YouTube Oynatma Listesi Seç (İlk 3)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        selectedRole,
                        selectedPriority,
                        videoDisabled || selectedRole == BookCoachingRole.PASSIVE
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTopicGoalDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konu Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Ders") })
                OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Konu") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(subject, topic, 5) }, enabled = subject.isNotBlank() && topic.isNotBlank()) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditResourceDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var totalUnits by remember { mutableStateOf("") }
    var unitType by remember { mutableStateOf("Test") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kaynak Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Kaynak Adı") })
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Ders") })
                OutlinedTextField(
                    value = totalUnits,
                    onValueChange = { totalUnits = it },
                    label = { Text("Toplam Miktar (Opsiyonel)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(value = unitType, onValueChange = { unitType = it }, label = { Text("Birim (Test, Sayfa, vb.)") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, subject, (totalUnits.toIntOrNull() ?: 0).coerceAtLeast(0), unitType) },
                enabled = title.isNotBlank() && subject.isNotBlank() && (totalUnits.isBlank() || totalUnits.toIntOrNull() != null)
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@Composable
private fun LibraryFilterPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

