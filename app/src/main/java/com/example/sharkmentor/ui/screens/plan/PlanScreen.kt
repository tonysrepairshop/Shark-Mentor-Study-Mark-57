package com.example.sharkmentor.ui.screens.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharkmentor.core.coaching.BookCampManager
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.PlanFolderEntity
import com.example.sharkmentor.ui.coaching.CampVideoStudyStudioDialog
import com.example.sharkmentor.ui.components.FlushTextField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = viewModel(),
    onNavigateToSchedule: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val currentTasks by viewModel.currentTasks.collectAsState()
    val showCreateDialog by viewModel.showCreateFolderDialog.collectAsState()
    val showAddTaskDialog by viewModel.showAddTaskDialog.collectAsState()

    // Dialog State for Deletion & Pre-Plan Readiness Check-In
    var folderToDelete by remember { mutableStateOf<PlanFolderEntity?>(null) }
    var taskToDelete by remember { mutableStateOf<PlanEntity?>(null) }
    var showReadinessCheckIn by remember { mutableStateOf(false) }
    var isRebuildingPlan by remember { mutableStateOf(false) }

    if (showReadinessCheckIn) {
        com.example.sharkmentor.ui.coaching.PlanReadinessCheckInDialog(
            title = "Planı Yeniden Kurgulamadan Önce",
            subtitle = "Yeni planının eksik görevlerini öne alıp tamamladıklarını tekrar etmemesi için konu hakimiyetini ve kitaplarında çözdüğün noktaları son haline getir.",
            confirmButtonLabel = "Güncel, Planı Kurgula",
            onConfirmProceed = {
                showReadinessCheckIn = false
                if (!isRebuildingPlan) {
                    isRebuildingPlan = true
                    scope.launch {
                        val count = runCatching {
                            com.example.sharkmentor.ui.coaching.rebalanceWeeklyPlanWithAiCoach(context)
                        }.getOrDefault(0)
                        isRebuildingPlan = false
                        val msg = if (count > 0) {
                            "Planın eksiklerini devrederek $count blok halinde kurgulandı."
                        } else {
                            "Plan kurgulamak için önce konu veya kitap eklemelisin."
                        }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showReadinessCheckIn = false }
        )
    }

    // Deletion Dialogs
    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Plan Klasörünü Sil", fontWeight = FontWeight.Bold) },
            text = { Text("'${folderToDelete!!.title}' klasörü ve içindeki tüm görevler kalıcı olarak silinecek.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFolder(folderToDelete!!.id)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("Vazgeç") }
            }
        )
    }

    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Görevi Sil", fontWeight = FontWeight.Bold) },
            text = { Text("'${taskToDelete!!.subject} - ${taskToDelete!!.title}' görevi silinsin mi?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTask(taskToDelete!!)
                        taskToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) { Text("Vazgeç") }
            }
        )
    }

    // New Folder Dialog
    if (showCreateDialog) {
        CreateFolderDialog(
            onDismiss = { viewModel.closeCreateFolderDialog() },
            onConfirm = { title -> viewModel.createNewFolder(title) }
        )
    }

    // Add Task Dialog (only shown when a folder is selected)
    if (showAddTaskDialog) {
        AddTaskDialog(
            selectedDay = state.selectedDay,
            onDismiss = { viewModel.closeAddTaskDialog() },
            onConfirm = { subject, topic, day, time, questions ->
                viewModel.addTaskToSelectedFolder(subject, topic, day, time, questions)
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            if (state.selectedFolder != null) {
                FloatingActionButton(
                    onClick = { viewModel.openAddTaskDialog() },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            "Görev Ekle",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                val stages = listOf(
                    "Sabit ders programın ve kilitli saatlerin kontrol ediliyor...",
                    "Günün derslerine uygun akşam tekrar blokları belirleniyor...",
                    "Minimum 30 soru barajına göre hedefler dağıtılıyor...",
                    "7 günlük takvim derleniyor..."
                )
                com.example.sharkmentor.ui.components.DynamicThinkingCard(
                    stages = stages,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.9f)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Sleek Folder Chip & Actions Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                Surface(
                                    onClick = { viewModel.openCreateFolderDialog() },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Yeni Klasör",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            items(state.folders, key = { it.id }) { folder ->
                                val isSelected = state.selectedFolder?.id == folder.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = if (isSelected) 4.dp else 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.clickable { viewModel.selectFolder(folder) },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = folder.title,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .clickable { folderToDelete = folder },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Sil",
                                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Pre-Plan Readiness & Smart Rebuild Button
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = { if (!isRebuildingPlan) showReadinessCheckIn = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isRebuildingPlan) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.8.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = "Planı Kurgula",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = "Kurgula",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Schedule Navigation Button
                        IconButton(
                            onClick = onNavigateToSchedule,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Ders Programı",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (state.folders.isEmpty()) {
                        // Sleek Quick Start Empty State
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarMonth,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Haftalık Çalışma Planı",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = "Hedeflerine odaklanmak ve haftanı verimli geçirmek için hemen bir plan oluştur.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Button(
                                        onClick = { viewModel.createNewFolder("Bu Haftanın Planı") },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Bu Haftanın Planını Başlat", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }

                                    TextButton(
                                        onClick = { viewModel.openCreateFolderDialog() },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Farklı İsimle Klasör Aç", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    } else if (state.selectedFolder != null) {
                        val dayProgress by viewModel.currentDayProgress.collectAsState()
                        val overallProgress by viewModel.overallPlanProgress.collectAsState()
                        FolderDetailView(
                            selectedDay = state.selectedDay,
                            tasks = currentTasks.filter { it.dayOfWeek == state.selectedDay },
                            dayProgress = dayProgress,
                            overallProgress = overallProgress,
                            onSelectDay = { viewModel.selectDay(it) },
                            onDeleteTask = { taskToDelete = it },
                            onToggleTaskCompletion = { viewModel.toggleTaskCompletion(it) }
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Lütfen bir klasör seçin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                "Yeni Plan Klasörü",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                FlushTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Klasör Başlığı",
                    placeholder = "Örn: 2. Hafta Kampı"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title.ifBlank { "Yeni Haftalık Plan" }) },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Oluştur")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FolderDetailView(
    selectedDay: Int,
    tasks: List<PlanEntity>,
    dayProgress: Float,
    overallProgress: Float,
    onSelectDay: (Int) -> Unit,
    onDeleteTask: (PlanEntity) -> Unit,
    onToggleTaskCompletion: (PlanEntity) -> Unit
) {
    val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    val shortDays = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    val dayName = dayNames.getOrElse(selectedDay - 1) { "" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        // ITEM 1: SLEEK UNIFIED PROGRESS HEADER (Compact, non-intrusive)
        item(key = "progress_header") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Daily Progress
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "$dayName:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(dayProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { dayProgress },
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(4.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )

                    // Weekly Progress
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Haftalık:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(overallProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }

        // ITEM 2: SLEEK 7-DAY SELECTOR (All 7 days fit on screen without horizontal scroll)
        stickyHeader {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    shortDays.forEachIndexed { index, shortName ->
                        val dayNum = index + 1
                        val isSelected = dayNum == selectedDay
                        Surface(
                            onClick = { onSelectDay(dayNum) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shortName,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // ITEM 3: TASKS LIST WITH MAXIMUM VERTICAL SPACE
        if (tasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$dayName için planlanmış görev yok.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SpaciousPlanTaskCard(
                        task = task,
                        onDelete = { onDeleteTask(task) },
                        onToggleCompletion = { onToggleTaskCompletion(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun SpaciousPlanTaskCard(
    task: PlanEntity,
    onDelete: () -> Unit,
    onToggleCompletion: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        BookCampManager.ensureLoaded(context)
    }
    val campBindings by BookCampManager.bindingsByResourceId.collectAsState()
    val verifiedVideo = remember(task, campBindings) {
        BookCampManager.resolveVerifiedCampEpisodeForTask(
            context = context,
            subject = task.subject,
            topic = task.topic,
            taskTitle = task.title,
            dayHint = task.dayOfWeek
        )
    }
    var showCampStudio by remember { mutableStateOf(false) }

    if (showCampStudio && verifiedVideo != null) {
        val (campTitle, episode) = verifiedVideo
        val matchedBinding = remember(campBindings, task.subject, episode.playlistId) {
            campBindings.values.firstOrNull {
                it.hasVerifiedVideoSupport && (
                    (episode.playlistId.isNotBlank() && it.playlistId == episode.playlistId) ||
                        it.subject.contains(task.subject, ignoreCase = true) ||
                        task.subject.contains(it.subject, ignoreCase = true)
                    )
            }
        }
        CampVideoStudyStudioDialog(
            campTitle = campTitle,
            subject = task.subject,
            initialEpisode = episode,
            allEpisodes = matchedBinding?.episodes?.ifEmpty { listOf(episode) } ?: listOf(episode),
            onMarkCompleted = {
                if (!task.isCompleted) onToggleCompletion()
                showCampStudio = false
            },
            onDismiss = { showCampStudio = false }
        )
    }

    Surface(
        onClick = onToggleCompletion,
        shape = RoundedCornerShape(14.dp),
        color = if (task.isCompleted)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(
            1.dp,
            if (task.isCompleted)
                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Modern Checkmark Toggle
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.isCompleted) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .border(
                        1.5.dp,
                        if (task.isCompleted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Tamamlandı",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Time Chip & Lesson Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = task.scheduledTime.ifBlank { "Esnek Süre" },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = task.subject,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Topic Content
                val topicText = task.topic.ifBlank { task.title }
                Text(
                    text = topicText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.isCompleted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }

            if (verifiedVideo != null) {
                Spacer(modifier = Modifier.width(6.dp))

                // Only shown when verified real video support exists for this book/subject
                Surface(
                    onClick = { showCampStudio = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = "Ders Videosu",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "İzle",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Sil",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AddTaskDialog(
    selectedDay: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (subject: String, topic: String, dayOfWeek: Int, scheduledTime: String, targetQuestions: Int) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(selectedDay) }
    var time by remember { mutableStateOf("Esnek • 90 dk") }
    var questionsText by remember { mutableStateOf("30") }

    val dayNames = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                "Yeni Görev Ekle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Subject
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Ders *") },
                    placeholder = { Text("Matematik") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Topic
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Konu") },
                    placeholder = { Text("Türev / Paragraf") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Day-of-week selector (all 7 days neatly spaced)
                Text(
                    "Gün",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayNames.forEachIndexed { index, name ->
                        val d = index + 1
                        val isSelected = day == d
                        Surface(
                            onClick = { day = d },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Time
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Süre / Zaman") },
                    placeholder = { Text("Esnek • 90 dk") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Target questions
                OutlinedTextField(
                    value = questionsText,
                    onValueChange = { questionsText = it.filter { c -> c.isDigit() } },
                    label = { Text("Hedef Soru Sayısı (Min. 30)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val questions = questionsText.toIntOrNull()?.let { if (it > 0) it else 30 } ?: 30
                    onConfirm(subject, topic, day, time, questions)
                },
                enabled = subject.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Görevi Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
