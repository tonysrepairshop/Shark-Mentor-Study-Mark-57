package com.example.sharkmentor.ui.screens.plan

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.PlanFolderEntity
import com.example.sharkmentor.data.remote.ScheduleScannerEngine
import com.example.sharkmentor.data.repository.PlanRepositoryImpl
import com.example.sharkmentor.data.sync.PlanSyncListener
import com.example.sharkmentor.domain.util.PlanOrganizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PlanUiState(
    val folders: List<PlanFolderEntity> = emptyList(),
    val selectedFolder: PlanFolderEntity? = null,
    val selectedDay: Int = java.time.LocalDate.now().dayOfWeek.value, // Auto-select current day
    val isJiggleMode: Boolean = false,
    val isScanning: Boolean = false,
    val scanError: String? = null,
    val isLoading: Boolean = true
)

class PlanViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PlanRepositoryImpl = PlanRepositoryImpl(
        SharkMentorDatabase.getDatabase(application).planDao(),
        SharkMentorDatabase.getDatabase(application).pendingDeletionDao()
    ),
    private val scannerEngine: ScheduleScannerEngine = ScheduleScannerEngine()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog.asStateFlow()

    // Add-task dialog
    private val _showAddTaskDialog = MutableStateFlow(false)
    val showAddTaskDialog: StateFlow<Boolean> = _showAddTaskDialog.asStateFlow()

    // PlanSyncListener with tombstone awareness
    private val planSyncListener = PlanSyncListener(
        SharkMentorDatabase.getDatabase(application).planDao(),
        SharkMentorDatabase.getDatabase(application).pendingDeletionDao()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentTasks: StateFlow<List<PlanEntity>> = _uiState
        .map { it.selectedFolder?.id }
        .distinctUntilChanged()
        .flatMapLatest { folderId ->
            if (folderId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.getTasksForFolder(folderId)
            }
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val currentDayProgress: StateFlow<Float> = combine(currentTasks, _uiState.map { it.selectedDay }.distinctUntilChanged()) { tasks, day ->
        val dayTasks = tasks.filter { it.dayOfWeek == day }
        if (dayTasks.isEmpty()) 0f
        else dayTasks.count { it.isCompleted }.toFloat() / dayTasks.size
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 0f)

    val overallPlanProgress: StateFlow<Float> = currentTasks.map { tasks ->
        if (tasks.isEmpty()) 0f
        else tasks.count { it.isCompleted }.toFloat() / tasks.size
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 0f)

    init {
        // 1. Observe local Room folders
        viewModelScope.launch(Dispatchers.IO) {
            repository.activeFolders.collect { folderList ->
                _uiState.update { current ->
                    val currentSelectedId = current.selectedFolder?.id
                    val newestFolder = folderList.maxByOrNull { it.createdAt }
                    val isNewFolderCreated = newestFolder != null && (current.selectedFolder == null || newestFolder.createdAt > (current.selectedFolder.createdAt + 1000L))
                    val updatedSelected = if (isNewFolderCreated) {
                        newestFolder
                    } else {
                        folderList.find { it.id == currentSelectedId } ?: newestFolder
                    }

                    current.copy(
                        folders = folderList,
                        selectedFolder = updatedSelected,
                        isLoading = false
                    )
                }
            }
        }

        // We will expose currentTasks directly via a separate StateFlow.
        // Ensure PlanUiState does not duplicate these tasks.
        
        // This StateFlow is observed separately by PlanScreen


        // 2. Start Firestore real-time listener (Coach -> Study sync)
        planSyncListener.start()

        // 3. When a Coach folder arrives:
        //    a) If nothing is selected -> auto-select immediately.
        //    b) If a folder IS selected but the new one is newer (Coach just assigned it) -> switch.
        //    c) Otherwise leave the student's current selection undisturbed.
        viewModelScope.launch(Dispatchers.IO) {
            planSyncListener.onNewFolderArrived.collect { folderId ->
                val current = _uiState.value
                val newFolder = current.folders.find { it.id == folderId }
                    ?: return@collect   // Not yet in Room flow — the folder collector will handle it

                val shouldSwitch = current.selectedFolder == null ||
                    newFolder.createdAt > (current.selectedFolder?.createdAt ?: 0L)

                if (shouldSwitch) {
                    selectFolder(newFolder)
                    Log.d("PlanViewModel", "Auto-selected arriving folder: ${newFolder.title}")
                }
            }
        }
    }

    override fun onCleared() {
        planSyncListener.stop()
        super.onCleared()
    }

    // Folder dialog

    fun openCreateFolderDialog() { _showCreateFolderDialog.value = true }
    fun closeCreateFolderDialog() { _showCreateFolderDialog.value = false }

    fun createNewFolder(title: String, durationDays: Int = 7) {
        if (title.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val end = now + (durationDays.toLong() * 24 * 60 * 60 * 1000)
            val newFolder = PlanFolderEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = now,
                endTimestamp = end
            )
            repository.insertFolder(newFolder)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            _showCreateFolderDialog.value = false
            // Automatically open the freshly created folder
            selectFolder(newFolder)
        }
    }

    // Add-task dialog

    fun openAddTaskDialog() {
        val currentFolder = _uiState.value.selectedFolder
        if (currentFolder == null) {
            // No folder exists: open folder creation dialog first.
            // After folder is created, openAddTaskDialog() will be called again from the UI.
            _showCreateFolderDialog.value = true
        } else {
            _showAddTaskDialog.value = true
        }
    }

    fun closeAddTaskDialog() { _showAddTaskDialog.value = false }

    /**
     * Adds a single task to the currently selected folder.
     * [folderId] is ALWAYS the selectedFolder.id — never blank or null.
     * Enforces targetQuestions >= 30.
     */
    fun addTaskToSelectedFolder(
        subject: String,
        topic: String,
        dayOfWeek: Int,
        scheduledTime: String,
        targetQuestions: Int
    ) {
        val folder = _uiState.value.selectedFolder ?: run {
            Log.e("PlanViewModel", "addTaskToSelectedFolder called with no selected folder — opening folder dialog instead.")
            _showCreateFolderDialog.value = true
            return
        }

        val task = PlanEntity(
            id = UUID.randomUUID().toString(),
            folderId = folder.id,          // NEVER blank
            title = topic.ifBlank { subject },
            subject = subject.ifBlank { "Ders" },
            topic = topic,
            dayOfWeek = dayOfWeek.coerceIn(1, 7),
            scheduledTime = scheduledTime.ifBlank { "Esnek • 90 dk" },
            targetQuestions = if (targetQuestions > 0) targetQuestions else 30,
            orderIndex = currentTasks.value.size,
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTasks(listOf(task))
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(getApplication())
        }

        _showAddTaskDialog.value = false
    }

    // Scanner

    fun scanScheduleImage(context: Context, imageUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanning = true, scanError = null) }
            val result = scannerEngine.scanSchedule(context, imageUri)
            _uiState.update { it.copy(isScanning = false) }
            result.onSuccess { dtoList ->
                val sdf = SimpleDateFormat("dd MMM", Locale("tr"))
                val now = System.currentTimeMillis()
                val folderTitle = "Tarama: ${sdf.format(Date(now))} Planı"

                val newFolder = repository.saveScannedSchedule(folderTitle, dtoList)
                com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
                com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(getApplication())
                selectFolder(newFolder)
            }.onFailure { err ->
                Log.e("PlanViewModel", "Scan error: ${err.message}")
                _uiState.update { it.copy(scanError = err.message ?: "Tarama başarısız oldu. Lütfen tekrar deneyin.") }
            }
        }
    }

    fun handleScannedSchedule(scannedTasks: List<PlanEntity>, folderTitle: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val newFolderId = repository.saveScannedSchedule(
                customTitle = folderTitle,
                tasks = scannedTasks
            )
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(getApplication())

            // Auto-select the newly created folder by passing its ID to selectFolder
            val newFolder = PlanFolderEntity(
                id = newFolderId,
                title = folderTitle ?: "Tarama",
                createdAt = System.currentTimeMillis(),
                endTimestamp = System.currentTimeMillis()
            )
            selectFolder(newFolder)
        }
    }

    // Folder / Task selection

    fun selectFolder(folder: PlanFolderEntity?) {
        _uiState.update { it.copy(selectedFolder = folder, isJiggleMode = false) }
    }

    fun selectFolderById(folderId: String) {
        val folder = _uiState.value.folders.find { it.id == folderId }
        selectFolder(folder)
    }

    /**
     * Called immediately after [PlanRepositoryImpl.assignOrSavePlanWithFolder] returns a
     * [Result.success] — switches the UI to the newly created folder so the student sees
     * the Coach-assigned plan right away without waiting for the Firestore snapshot.
     *
     * If the folder isn't in the Room flow yet (first launch race), it falls back gracefully;
     * [onNewFolderArrived] will catch it once the snapshot arrives.
     */
    fun selectFolderAfterAssignment(folderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Try immediately from the current in-memory list
            val folder = _uiState.value.folders.find { it.id == folderId }
            if (folder != null) {
                selectFolder(folder)
            } else {
                // Not in Room yet — wait for the next emission of activeFolders
                repository.activeFolders.collect { folderList ->
                    val arrived = folderList.find { it.id == folderId }
                    if (arrived != null) {
                        selectFolder(arrived)
                        return@collect
                    }
                }
            }
        }
    }

    fun selectDay(day: Int) {
        _uiState.update { it.copy(selectedDay = day) }
    }

    fun toggleJiggleMode(enabled: Boolean? = null) {
        _uiState.update { it.copy(isJiggleMode = enabled ?: !it.isJiggleMode) }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFolder(folderId)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(getApplication())
        }
    }

    fun deleteTask(task: PlanEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTask(task)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(getApplication())
        }
    }

    fun toggleTaskCompletion(task: PlanEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTaskCompletion(task.id, !task.isCompleted)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(getApplication())
        }
    }

    fun organizeCurrentFolder() {
        val currentTasksList = currentTasks.value
        if (currentTasksList.isEmpty()) return
        val organized = PlanOrganizer.organizePlans(currentTasksList)
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTasks(organized)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
        }
    }
}
