package com.example.sharkmentor.data.sync

import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.dao.PlanDao
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.PlanFolderEntity
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Attaches real-time Firestore listeners to:
 *   - `users/{uid}/plan_folders`  -> upserts into Room via [PlanDao.insertFolder]
 *   - `users/{uid}/plans`         -> upserts into Room via [PlanDao.insertTasks]
 *
 * When a new folder arrives, emits its ID via [onNewFolderArrived].
 * The ViewModel observes this to auto-select the folder when none is selected.
 *
 * Usage (in PlanViewModel.init):
 *   planSyncListener.start()
 *   viewModelScope.launch {
 *       planSyncListener.onNewFolderArrived.collect { folderId ->
 *           if (_uiState.value.selectedFolder == null) selectFolderById(folderId)
 *       }
 *   }
 * Call stop() in ViewModel.onCleared().
 */
class PlanSyncListener(
    private val planDao: PlanDao,
    private val pendingDeletionDao: com.example.sharkmentor.data.local.dao.PendingDeletionDao? = null
) {
    private val TAG = "PlanSyncListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var folderRegistration: ListenerRegistration? = null
    private var taskRegistration: ListenerRegistration? = null

    /**
     * Emits the folder ID every time a new folder document arrives from Firestore.
     * Collectors can auto-select the folder when the UI has nothing selected.
     */
    private val _onNewFolderArrived = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val onNewFolderArrived: SharedFlow<String> = _onNewFolderArrived.asSharedFlow()

    // -------------------------------------------------------------------------

    fun start() {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
            Log.w(TAG, "FirebaseAuth currentUser is null - skipping plan sync listener (offline mode).")
            return
        }
        val uid = GoogleServicesHelper.getCurrentUser()?.uid
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "User not logged in - skipping plan sync listener.")
            return
        }

        val firestore = GoogleServicesHelper.getFirebaseFirestore()
        if (firestore == null) {
            Log.w(TAG, "Firestore unavailable - skipping plan sync listener.")
            return
        }

        // Folder listener
        folderRegistration = firestore
            .collection("users")
            .document(uid)
            .collection("plan_folders")
            .limit(40)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        folderRegistration?.remove()
                        folderRegistration = null
                        return@addSnapshotListener
                    }
                    Log.w(TAG, "plan_folders listener error (fallback to Room): ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                val folders = snapshot.documents.mapNotNull { doc ->
                    try {
                        PlanFolderEntity(
                            id = doc.id,
                            title = doc.getString("title") ?: "Koç Çalışma Programı",
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            endTimestamp = doc.getLong("endTimestamp")
                                ?: (System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000),
                            isArchived = doc.getBoolean("isArchived") ?: false,
                            source = doc.getString("source") ?: "COACH_ASSIGNED"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse folder ${doc.id}: ${e.message}")
                        null
                    }
                }

                scope.launch {
                    try {
                        val tombstones = runCatching {
                            pendingDeletionDao?.getAllPendingDeletions()?.map { it.entityId }?.toSet().orEmpty()
                        }.getOrDefault(emptySet())
                        val safeFolders = folders.filterNot { it.id in tombstones }
                        safeFolders.forEach { folder ->
                            planDao.insertFolder(folder)
                            _onNewFolderArrived.tryEmit(folder.id)
                        }
                        Log.d(TAG, "Upserted ${safeFolders.size} plan folders into Room (skipped ${folders.size - safeFolders.size} tombstoned).")
                    } catch (e: Exception) {
                        Log.e(TAG, "Room folder upsert failed: ${e.message}", e)
                    }
                }
            }

        // Task listener
        taskRegistration = firestore
            .collection("users")
            .document(uid)
            .collection("plans")
            .limit(150)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        taskRegistration?.remove()
                        taskRegistration = null
                        return@addSnapshotListener
                    }
                    Log.w(TAG, "plans listener error (fallback to Room): ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                val tasks = mutableListOf<PlanEntity>()
                var droppedOrphanCount = 0

                snapshot.documents.forEach { doc ->
                    try {
                        val rawFolderId = doc.getString("folderId") ?: ""

                        // GUARD: Never insert a task with a blank folderId.
                        // A blank folderId means the Coach/AI wrote tasks without creating
                        // the parent folder first — inserting would create an orphan that
                        // can never be displayed, selected, or deleted cleanly.
                        if (rawFolderId.isBlank() || rawFolderId == "default_folder") {
                            droppedOrphanCount++
                            Log.w(TAG, "Dropped orphan task ${doc.id}: folderId='$rawFolderId'")
                            return@forEach
                        }

                        tasks.add(
                            PlanEntity(
                                id             = doc.id,
                                folderId       = rawFolderId,
                                title          = doc.getString("title")
                                    ?: doc.getString("topic") ?: "Görev",
                                subject        = doc.getString("subject") ?: "Ders",
                                topic          = doc.getString("topic") ?: "",
                                dayOfWeek      = (doc.getLong("dayOfWeek") ?: 1L).toInt().coerceIn(1, 7),
                                scheduledTime  = (doc.getString("scheduledTime")
                                    ?: doc.getString("timeSlot")).orEmpty().ifBlank { "Esnek • 90 dk" },
                                targetQuestions = (doc.getLong("targetQuestions") ?: 30L)
                                    .toInt().let { if (it > 0) it else 30 },
                                isCompleted    = doc.getBoolean("isCompleted")
                                    ?: doc.getBoolean("completed") ?: false,
                                orderIndex     = (doc.getLong("orderIndex") ?: 0L).toInt(),
                                createdAt      = doc.getLong("createdAt") ?: System.currentTimeMillis()
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse task ${doc.id}: ${e.message}")
                    }
                }

                if (droppedOrphanCount > 0) {
                    Log.w(TAG, "Dropped $droppedOrphanCount orphan task(s) with blank/default folderId.")
                }

                scope.launch {
                    try {
                        val tombstones = runCatching {
                            pendingDeletionDao?.getAllPendingDeletions()?.map { it.entityId }?.toSet().orEmpty()
                        }.getOrDefault(emptySet())
                        val safeTasks = tasks.filterNot { it.id in tombstones || it.folderId in tombstones }
                        if (safeTasks.isNotEmpty()) {
                            planDao.insertTasks(safeTasks)
                            Log.d(TAG, "Upserted ${safeTasks.size} plan tasks into Room (skipped ${tasks.size - safeTasks.size} tombstoned).")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Room task upsert failed: ${e.message}", e)
                    }
                }
            }

        Log.d(TAG, "PlanSyncListener started for uid=$uid")
    }

    fun stop() {
        folderRegistration?.remove()
        taskRegistration?.remove()
        folderRegistration = null
        taskRegistration = null
        Log.d(TAG, "PlanSyncListener stopped.")
    }
}
