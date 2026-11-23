package com.example.sharkmentor.data.repository

import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.dao.PlanDao
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.PlanFolderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.util.Log
import com.example.sharkmentor.data.local.dao.PendingDeletionDao
import com.example.sharkmentor.data.local.entity.PendingDeletionEntity
import com.example.sharkmentor.data.remote.PlanSyncManager
import com.example.sharkmentor.data.remote.ScannedTaskDto

class PlanRepositoryImpl(
    private val planDao: PlanDao,
    private val pendingDeletionDao: PendingDeletionDao? = null,
    private val syncManager: PlanSyncManager = PlanSyncManager(
        GoogleServicesHelper.getFirebaseFirestore(),
        GoogleServicesHelper.getFirebaseAuth(),
        planDao
    )
) {
    companion object {
        /**
         * Deduplication key for plan tasks. Includes normalized topic/title so multiple
         * flexible-duration blocks ("Esnek • 90 dk") of the same subject on the same day
         * with different topics are NEVER silently dropped.
         */
        fun buildPlanDistinctKey(
            dayOfWeek: Int,
            scheduledTime: String,
            subject: String,
            topic: String,
            title: String = ""
        ): String {
            val normSubject = subject.trim().lowercase(Locale("tr"))
            val normTopic = topic.ifBlank { title }.trim().lowercase(Locale("tr"))
            val normSlot = scheduledTime.trim().lowercase(Locale("tr"))
            return "${dayOfWeek}_${normSlot}_${normSubject}_${normTopic}"
        }
    }

    val activeFolders: Flow<List<PlanFolderEntity>> = planDao.getAllActiveFolders()

    fun getTasksForFolder(folderId: String): Flow<List<PlanEntity>> =
        planDao.getTasksForFolder(folderId)

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        val uid = GoogleServicesHelper.getCurrentUser()?.uid

        // 1. Delete associated tasks and folder locally in Room
        planDao.cascadeDeleteFolder(folderId)

        // 2. Mark tombstone so background SyncWorker never resurrects it
        pendingDeletionDao?.insert(
            PendingDeletionEntity(
                entityId = folderId,
                collectionName = "plan_folders"
            )
        )

        // 3. Delete from Firestore immediately
        if (!uid.isNullOrBlank()) {
            try {
                val firestore = GoogleServicesHelper.getFirebaseFirestore()
                if (firestore != null) {
                    val batch = firestore.batch()
                    val folderRef = firestore.collection("users").document(uid).collection("plan_folders").document(folderId)
                    batch.delete(folderRef)

                    val tasksSnapshot = firestore.collection("users").document(uid).collection("plans")
                        .whereEqualTo("folderId", folderId)
                        .get()
                        .await()

                    for (doc in tasksSnapshot.documents) {
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            } catch (e: Exception) {
                Log.e("PlanRepository", "Remote folder deletion failed: ${e.message}")
            }
        }
    }

    suspend fun deleteTask(task: PlanEntity) = withContext(Dispatchers.IO) {
        planDao.deleteTask(task)
        pendingDeletionDao?.insert(
            PendingDeletionEntity(
                entityId = task.id,
                collectionName = "plans"
            )
        )
        launch {
            syncManager.deleteTaskFromCloud(task.id)
        }
    }

    suspend fun updateTasks(tasks: List<PlanEntity>) = withContext(Dispatchers.IO) {
        planDao.insertTasks(tasks)
        launch {
            syncManager.syncTasksToCloud(tasks)
        }
    }

    suspend fun updateTaskCompletion(taskId: String, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        planDao.updateTaskCompletion(taskId, isCompleted)
        val uid = GoogleServicesHelper.getCurrentUser()?.uid
        if (!uid.isNullOrBlank()) {
            try {
                val firestore = GoogleServicesHelper.getFirebaseFirestore()
                if (firestore != null) {
                    firestore.collection("users").document(uid).collection("plans").document(taskId)
                        .set(
                            mapOf(
                                "isCompleted" to isCompleted,
                                "completed" to isCompleted
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        ).await()
                }
            } catch (e: Exception) {
                Log.e("PlanRepository", "Remote update task completion failed: ${e.message}")
            }
        }
    }

    suspend fun insertFolder(folder: PlanFolderEntity) = withContext(Dispatchers.IO) {
        planDao.insertFolder(folder)
        launch {
            syncManager.syncFolderToCloud(folder)
        }
    }

    suspend fun insertTasks(tasks: List<PlanEntity>) = withContext(Dispatchers.IO) {
        planDao.insertTasks(tasks)
        launch {
            syncManager.syncTasksToCloud(tasks)
        }
    }

    suspend fun insertAiGeneratedPlan(folderId: String, tasks: List<PlanEntity>) = withContext(Dispatchers.IO) {
        // 1. O klasördeki tüm eski/çakışan görevleri Room'dan temizle
        planDao.deleteTasksByFolder(folderId)

        // 2. Eğer soru sayısı 0 gelmişse 30'a zorla
        val cleanTasks = tasks.map { task ->
            task.copy(
                targetQuestions = if (task.targetQuestions > 0) task.targetQuestions else 30
            )
        }

        // 3. Yeni görevleri tertemiz ekle
        planDao.insertTasks(cleanTasks)
        
        launch {
            syncManager.syncTasksToCloud(cleanTasks)
        }
    }

    suspend fun createNewFolder(title: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val end = now + (7L * 24 * 60 * 60 * 1000)
        val newFolder = PlanFolderEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            endTimestamp = end
        )
        planDao.insertFolder(newFolder)
        launch {
            syncManager.syncFolderToCloud(newFolder)
        }
    }

    @JvmName("saveGeneratedPlanFromDtos")
    suspend fun saveGeneratedPlan(
        folderTitle: String = "AI Haftalık Plan",
        tasks: List<ScannedTaskDto>
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dayInMillis = 24 * 60 * 60 * 1000L

        // 1. Find or create the AI_PLANNER folder
        val existingFolder = planDao.getAllFoldersSync()
            .firstOrNull { it.source == "AI_PLANNER" && (now - it.createdAt) < dayInMillis }

        val folderId = existingFolder?.id ?: UUID.randomUUID().toString()
        val newFolder = if (existingFolder == null) {
            PlanFolderEntity(
                id = folderId,
                title = folderTitle,
                createdAt = now,
                endTimestamp = now + (7 * dayInMillis),
                source = "AI_PLANNER"
            )
        } else null

        // 2. Wipe old tasks
        planDao.deleteTasksByFolder(folderId)

        // 3. Map & Deduplicate (preserving distinct topics sharing the same flexible duration slot)
        val entities = tasks
            .sortedWith(compareBy({ it.dayOfWeek }, { it.scheduledTime }))
            .distinctBy {
                buildPlanDistinctKey(
                    dayOfWeek = it.dayOfWeek,
                    scheduledTime = it.scheduledTime.ifBlank { "Esnek • 90 dk" },
                    subject = it.subject,
                    topic = it.topic
                )
            }
            .mapIndexed { index, dto ->
                val cleanTopic = dto.topic.trim()
                val cleanSubject = dto.subject.trim().ifBlank { "Ders" }
                PlanEntity(
                    id = UUID.randomUUID().toString(),
                    folderId = folderId,
                    dayOfWeek = if (dto.dayOfWeek in 1..7) dto.dayOfWeek else 1,
                    scheduledTime = dto.scheduledTime.ifBlank { "Esnek • 90 dk" },
                    title = cleanTopic.ifBlank { cleanSubject },
                    subject = cleanSubject,
                    topic = cleanTopic,
                    targetQuestions = if (dto.targetQuestions > 0) dto.targetQuestions else 30,
                    orderIndex = index
                )
            }

        Log.d("PlanRepositoryImpl", "Populating tasks, size: ${entities.size}")

        if (newFolder != null) {
            planDao.insertPlanWithTasks(newFolder, entities)
        } else {
            planDao.insertTasks(entities)
        }

        // 4. Sync
        val folderToSync = planDao.getAllFoldersSync().firstOrNull { it.id == folderId }
        if (folderToSync != null) {
            launch { syncManager.syncFolderToCloud(folderToSync) }
        }
        launch { syncManager.syncTasksToCloud(entities) }

        folderId
    }

    @JvmName("saveGeneratedPlanFromEntities")
    suspend fun saveGeneratedPlan(
        folderTitle: String,
        tasks: List<PlanEntity>
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("d MMMM", Locale("tr"))
        val resolvedTitle = if (folderTitle.isNotBlank()) folderTitle else "${sdf.format(Date(now))} Programı"

        val newFolderId = UUID.randomUUID().toString()
        val newFolder = PlanFolderEntity(
            id = newFolderId,
            title = resolvedTitle,
            createdAt = now,
            endTimestamp = now + (7L * 24 * 60 * 60 * 1000),
            source = "AI_PLANNER"
        )

        val boundTasks = tasks
            .distinctBy {
                buildPlanDistinctKey(
                    dayOfWeek = it.dayOfWeek,
                    scheduledTime = it.scheduledTime,
                    subject = it.subject,
                    topic = it.topic,
                    title = it.title
                )
            }
            .mapIndexed { index, task ->
                task.copy(
                    id = if (task.id.isNotBlank()) task.id else UUID.randomUUID().toString(),
                    folderId = newFolderId,
                    scheduledTime = task.scheduledTime.ifBlank { "Esnek • 90 dk" },
                    orderIndex = index,
                    targetQuestions = if (task.targetQuestions > 0) task.targetQuestions else 30
                )
            }

        Log.d("PlanRepositoryImpl", "Populating tasks, size: ${boundTasks.size}")
        planDao.insertPlanWithTasks(newFolder, boundTasks)

        launch {
            syncManager.syncFolderToCloud(newFolder)
            syncManager.syncTasksToCloud(boundTasks)
        }

        newFolderId
    }

    suspend fun saveAiPlannerSchedule(tasks: List<ScannedTaskDto>): String {
        return saveGeneratedPlan("AI Haftalık Plan", tasks)
    }

    suspend fun saveScannedSchedule(
        folderTitle: String,
        tasks: List<ScannedTaskDto>
    ): PlanFolderEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val end = now + (7L * 24 * 60 * 60 * 1000)
        
        val folder = PlanFolderEntity(
            id = UUID.randomUUID().toString(),
            title = folderTitle,
            createdAt = now,
            endTimestamp = end,
            source = "AI_SCANNER"
        )
        planDao.insertFolder(folder)

        planDao.deleteTasksByFolder(folder.id)

        val entities = tasks
            .sortedWith(compareBy({ it.dayOfWeek }, { it.scheduledTime }))
            .mapIndexed { index, task ->
                val cleanTopic = task.topic.trim()
                val cleanSubject = task.subject.trim().ifBlank { "Ders" }
                PlanEntity(
                    id = UUID.randomUUID().toString(),
                    folderId = folder.id,
                    dayOfWeek = if (task.dayOfWeek in 1..7) task.dayOfWeek else 1,
                    scheduledTime = task.scheduledTime.ifBlank { "Esnek • 90 dk" },
                    title = cleanTopic.ifBlank { cleanSubject },
                    subject = cleanSubject,
                    topic = cleanTopic,
                    targetQuestions = if (task.targetQuestions > 0) task.targetQuestions else 30,
                    orderIndex = index
                )
            }

        planDao.insertAll(entities)
        launch {
            syncManager.syncFolderToCloud(folder)
            syncManager.syncTasksToCloud(entities)
        }
        folder
    }

    suspend fun createFolderWithTasks(
        folderTitle: String,
        tasks: List<ScannedTaskDto>
    ): String {
        return saveScannedSchedule(folderTitle, tasks).id
    }

    @JvmName("saveScannedScheduleEntities")
    suspend fun saveScannedSchedule(
        customTitle: String? = null,
        tasks: List<PlanEntity>
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("d MMMM", Locale("tr"))
        val folderTitle = customTitle ?: "Tarama: ${sdf.format(Date(now))}"

        val newFolderId = UUID.randomUUID().toString()
        val newFolder = PlanFolderEntity(
            id = newFolderId,
            title = folderTitle,
            createdAt = now,
            endTimestamp = now + (7L * 24 * 60 * 60 * 1000),
            source = "AI_SCANNER"
        )
        planDao.insertFolder(newFolder)

        val boundTasks = tasks.mapIndexed { index, task ->
            task.copy(
                id = if (task.id.isNotBlank()) task.id else UUID.randomUUID().toString(),
                folderId = newFolderId,
                scheduledTime = task.scheduledTime.ifBlank { "Esnek • 90 dk" },
                orderIndex = index,
                targetQuestions = if (task.targetQuestions > 0) task.targetQuestions else 30
            )
        }

        planDao.insertTasks(boundTasks)

        launch {
            syncManager.syncFolderToCloud(newFolder)
            syncManager.syncTasksToCloud(boundTasks)
        }

        newFolderId
    }

    suspend fun assignOrSavePlanWithFolder(
        studentUid: String,
        folderTitle: String,
        tasks: List<PlanEntity>,
        source: String = "COACH_ASSIGNED"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val folderId = UUID.randomUUID().toString()
            val resolvedTitle = folderTitle.ifBlank { "Haftalık Çalışma Programı" }

            val parentFolder = PlanFolderEntity(
                id           = folderId,
                title        = resolvedTitle,
                createdAt    = now,
                endTimestamp = now + (7L * 24 * 60 * 60 * 1000),
                source       = source
            )

            val boundTasks = tasks
                .distinctBy {
                    buildPlanDistinctKey(
                        dayOfWeek = it.dayOfWeek,
                        scheduledTime = it.scheduledTime,
                        subject = it.subject,
                        topic = it.topic,
                        title = it.title
                    )
                }
                .mapIndexed { index, task ->
                    task.copy(
                        id              = task.id.ifBlank { UUID.randomUUID().toString() },
                        folderId        = folderId,
                        scheduledTime   = task.scheduledTime.ifBlank { "Esnek • 90 dk" },
                        orderIndex      = index,
                        targetQuestions = if (task.targetQuestions > 0) task.targetQuestions else 30
                    )
                }

            Log.d("PlanRepositoryImpl", "Populating tasks, size: ${boundTasks.size}")
            planDao.insertPlanWithTasks(parentFolder, boundTasks)

            val firestore = GoogleServicesHelper.getFirebaseFirestore()
            if (firestore != null && studentUid.isNotBlank()) {
                try {
                    val batch = firestore.batch()

                    val folderRef = firestore
                        .collection("users").document(studentUid)
                        .collection("plan_folders").document(folderId)
                    batch.set(folderRef, parentFolder)

                    boundTasks.forEach { task ->
                        val taskRef = firestore
                            .collection("users").document(studentUid)
                            .collection("plans").document(task.id)
                        batch.set(taskRef, task)
                    }

                    batch.commit().await()
                    Log.d("PlanRepository",
                        "assignOrSavePlanWithFolder: committed folder=$folderId, tasks=${boundTasks.size}, source=$source")
                } catch (firestoreEx: Exception) {
                    Log.w("PlanRepository",
                        "Firestore batch failed (Room safe, will retry): ${firestoreEx.message}")
                }
            } else if (studentUid.isBlank()) {
                Log.w("PlanRepository", "assignOrSavePlanWithFolder: blank studentUid — Firestore sync skipped.")
            }

            Result.success(folderId)
        } catch (e: Exception) {
            Log.e("PlanRepository", "assignOrSavePlanWithFolder failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
