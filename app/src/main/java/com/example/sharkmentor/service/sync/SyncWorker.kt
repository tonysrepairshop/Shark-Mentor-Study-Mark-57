package com.example.sharkmentor.service.sync

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.room.withTransaction
import com.example.sharkmentor.R
import com.example.sharkmentor.core.notification.SharkNotificationEngine
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.dao.PlanFolderDao
import com.example.sharkmentor.data.local.entity.ActivePlanEntity
import com.example.sharkmentor.data.local.entity.InstitutionScheduleEntity
import com.example.sharkmentor.data.local.entity.PlanEntity
import com.example.sharkmentor.data.local.entity.PlanFolderEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.StudyTaskEntity
import com.example.sharkmentor.data.local.entity.TargetEntity
import com.example.sharkmentor.data.local.entity.TargetPeriod
import com.example.sharkmentor.data.local.entity.TargetType
import com.example.sharkmentor.data.local.entity.TaskEntity
import com.example.sharkmentor.data.sync.CloudSyncRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"

        fun sanitizeTurkishText(input: String?): String {
            if (input.isNullOrBlank()) return ""
            return input
                .replace("Ã‡", "Ç")
                .replace("Ã§", "ç")
                .replace("Ä±", "ı")
                .replace("Ä°", "İ")
                .replace("ÅŸ", "ş")
                .replace("Åž", "Ş")
                .replace("Ã¶", "ö")
                .replace("Ã–", "Ö")
                .replace("Ã¼", "ü")
                .replace("Ãœ", "Ü")
                .replace("ÄŸ", "ğ")
                .replace("Äž", "Ğ")
                .replace("Ãƒâ€¡", "Ç")
                .replace("ÃƒÂ§", "ç")
                .replace("Ã„Â±", "ı")
                .replace("Ã…ÅŸ", "ş")
                .replace("ÃƒÂ¶", "ö")
                .replace("Ãƒâ€“", "Ö")
                .replace("ÃƒÂ¼", "ü")
                .replace("Ã–", "Ö")
        }
        const val UNIQUE_PERIODIC_WORK = "sharkmentor_periodic_sync"
        const val UNIQUE_IMMEDIATE_WORK = "sharkmentor_immediate_sync"
        private const val PREFS_SYNC_QUOTA = "sync_quota_prefs"
        private const val KEY_LAST_PULL_MS = "last_inbound_pull_ms"
        private const val PULL_COOLDOWN_MS = 15L * 60L * 1000L // 15 minutes
        private const val LEGACY_FALLBACK_COOLDOWN_MS = 24L * 60L * 60L * 1000L // 24 hours
        private const val COLLECTION_QUERY_LIMIT = 100L
        const val INPUT_FORCE_PULL = "force_pull"

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    60,
                    TimeUnit.SECONDS
                )
                .addTag(UNIQUE_PERIODIC_WORK)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )

            Log.i(TAG, "Periodic sync enqueued (every 6 hours, network required)")
        }

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .addTag(UNIQUE_IMMEDIATE_WORK)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "sharkmentor_one_time_sync",
                ExistingWorkPolicy.KEEP,
                oneTimeRequest
            )
            Log.d(TAG, "Immediate plan sync enqueued with KEEP policy (15s debounce)")
        }

        fun enqueueForceFullSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInputData(androidx.work.workDataOf(INPUT_FORCE_PULL to true))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag(UNIQUE_IMMEDIATE_WORK)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "sharkmentor_force_full_sync",
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
            Log.i(TAG, "Force full sync enqueued with REPLACE policy")
        }

        fun cancelAllSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            WorkManager.getInstance(context).cancelAllWorkByTag(UNIQUE_IMMEDIATE_WORK)
            Log.i(TAG, "All sync work cancelled")
        }
    }

    private fun shouldRunLegacyFallback(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_SYNC_QUOTA, Context.MODE_PRIVATE)
        val lastChecked = prefs.getLong("legacy_check_$key", 0L)
        return System.currentTimeMillis() - lastChecked >= LEGACY_FALLBACK_COOLDOWN_MS
    }

    private fun markLegacyFallbackChecked(context: Context, key: String) {
        context.getSharedPreferences(PREFS_SYNC_QUOTA, Context.MODE_PRIVATE)
            .edit()
            .putLong("legacy_check_$key", System.currentTimeMillis())
            .apply()
    }

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val channelId = SharkNotificationEngine.CHANNEL_SYNC_BACKGROUND
        SharkNotificationEngine.init(applicationContext)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("SharkMentor Senkronize Ediliyor")
            .setContentText("Koç planları ve hedefler güncelleniyor...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setColor(applicationContext.getColor(R.color.shark_blue_primary))
            .setOngoing(true)
            .setSilent(true)
            .build()

        return androidx.work.ForegroundInfo(991, notification)
    }

    override suspend fun doWork(): Result {
        val syncTag = "SharkMentorSync"
        Log.d(syncTag, "Starting SyncWorker execution... (attempt #$runAttemptCount)")
        val context = applicationContext
        val db = com.example.sharkmentor.data.local.SharkMentorDatabase.getDatabase(context)
        val userPrefs = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context)
        val firestore = GoogleServicesHelper.getFirebaseFirestore(context)

        if (firestore == null) {
            Log.w(syncTag, "Firestore unavailable, skipping sync cycle.")
            return Result.success()
        }

        val currentUser = try {
            GoogleServicesHelper.getFirebaseAuth(context)?.currentUser
        } catch (e: Throwable) {
            null
        }

        if (currentUser == null || currentUser.uid.isBlank() || currentUser.uid == "student_default") {
            Log.i(syncTag, "No authenticated user logged in. Skipping remote cloud sync cycle.")
            return Result.success()
        }

        val uid = currentUser.uid

        return try {
            Log.d(syncTag, ">>> STARTING FULL SYNC CYCLE <<<")
            val defaultInstitution = userPrefs.institutionId.takeIf { !it.isNullOrBlank() } ?: "INDIVIDUAL"
            val studentName = sanitizeTurkishText(userPrefs.studentName ?: "Öğrenci")

            // Flush any queued offline live presence snapshot when network is available
            runCatching {
                com.example.sharkmentor.core.coaching.CoachingEngine.flushPendingPresenceIfConnected(context)
            }

            // 1a. Outbound Deletions FIRST (prevents deleted items from being restored by pulls)
            var uploadedCount = coroutineScope {
                listOf(
                    async { syncPendingDeletions(db, firestore, syncTag, defaultInstitution, uid) },
                    async { syncPendingDeletedTargets(db, firestore, syncTag, uid) },
                    async { syncPendingDeletedResources(db, firestore, syncTag, uid) },
                    async { syncPendingDeletedTopics(db, firestore, syncTag, uid) }
                ).awaitAll().sum()
            }

            // 1b. Outbound Upstream Pushes BEFORE pulls (prevents remote snapshots overwriting offline mutations)
            uploadedCount += coroutineScope {
                listOf(
                    async { syncLocalSessions(db, firestore, syncTag, defaultInstitution, studentName, uid) },
                    async { syncLocalPlans(db, firestore, syncTag, defaultInstitution, studentName, uid) },
                    async { syncLocalTopics(db, firestore, syncTag, uid) },
                    async { syncLocalResources(db, firestore, syncTag, uid) },
                    async {
                        runCatching {
                            com.example.sharkmentor.data.repository.StreakRepository(context).pushPendingToCloudIfNeeded()
                        }.getOrDefault(0)
                    },
                    async {
                        runCatching {
                            com.example.sharkmentor.data.repository.CountdownRepository.getInstance(context).pushPendingToCloudIfNeeded()
                        }.getOrDefault(0)
                    }
                ).awaitAll().sum()
            }

            // 1c. Remaining domain pushes (questions, schedules, simulations, training)
            try {
                val repository = CloudSyncRepository(context)
                uploadedCount += repository.syncAll()
            } catch (e: Exception) {
                Log.e(syncTag, "CloudSyncRepository fallback err", e)
            }

            // 2. Throttled Inbound Downstream Pulls (at most once every 15 minutes unless forced)
            val quotaPrefs = context.getSharedPreferences(PREFS_SYNC_QUOTA, Context.MODE_PRIVATE)
            val forcePull = inputData.getBoolean(INPUT_FORCE_PULL, false)
            val nowMs = System.currentTimeMillis()
            val lastPullMs = quotaPrefs.getLong(KEY_LAST_PULL_MS, 0L)
            val shouldPullRemote = forcePull || (nowMs - lastPullMs >= PULL_COOLDOWN_MS)

            if (shouldPullRemote) {
                val studentDoc = try {
                    firestore.collection("students").document(uid).get(Source.DEFAULT).await()
                } catch (e: Exception) {
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                    ) {
                        Log.w(syncTag, "Permission denied reading students/$uid")
                    } else {
                        Log.w(syncTag, "Err reading students/$uid: ${e.message}")
                    }
                    null
                }

                coroutineScope {
                    val streakAndCountdownJob = async {
                        runCatching {
                            com.example.sharkmentor.data.repository.StreakRepository(context)
                                .pullAndMergeFromCloud(prefetchedStudentDoc = studentDoc)
                        }.onFailure { Log.w(syncTag, "Err pull water streak: ${it.message}") }
                        runCatching {
                            com.example.sharkmentor.data.repository.CountdownRepository.getInstance(context)
                                .pullAndMergeFromCloud(prefetchedStudentDoc = studentDoc)
                        }.onFailure { Log.w(syncTag, "Err pull countdown: ${it.message}") }
                    }
                    val planAndScheduleJob = async {
                        try {
                            pullActiveStudyPlan(db, studentDoc, syncTag)
                        } catch (e: Exception) {
                            Log.w(syncTag, "Err pull activeStudyPlan: ${e.message}")
                        }
                        try {
                            ingestCustomScheduleData(db, studentDoc, syncTag, uid)
                        } catch (e: Exception) {
                            Log.e(syncTag, "Err pull customScheduleData", e)
                        }
                    }
                    val resourcesJob = async {
                        try { pullRemoteResources(db, firestore, syncTag, uid) } catch (e: Exception) { Log.e(syncTag, "Err pull res", e) }
                    }
                    val topicsJob = async {
                        try { pullRemoteTopics(db, firestore, syncTag, uid) } catch (e: Exception) { Log.e(syncTag, "Err pull topics", e) }
                    }
                    val goalsJob = async {
                        try { pullRemoteGoals(db, firestore, syncTag, uid) } catch (e: Exception) { Log.e(syncTag, "Err pull goals", e) }
                    }
                    awaitAll(streakAndCountdownJob, planAndScheduleJob, resourcesJob, topicsJob, goalsJob)
                }

                // 3. Assigned plans/targets pull (after target/plan tables finish above)
                try { pullRemoteAssignedPlans(db, firestore, syncTag, defaultInstitution, uid) } catch (e: Exception) { Log.e(syncTag, "Err pull plans", e) }

                quotaPrefs.edit().putLong(KEY_LAST_PULL_MS, System.currentTimeMillis()).apply()
            } else {
                Log.d(syncTag, "Inbound remote pull skipped (quota cooldown active; uploaded=$uploadedCount).")
            }

            Log.d(syncTag, ">>> SYNC CYCLE FINISHED (uploaded=$uploadedCount, pulled=$shouldPullRemote) <<<")
            Result.success()
        } catch (e: Exception) {
            Log.e(syncTag, "SyncWorker failed: ${e.message}", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    // ── 1. Ingest Active Study Plan & Custom Timetable Directly To Room ──
    private suspend fun pullActiveStudyPlan(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        studentDoc: DocumentSnapshot?,
        syncTag: String
    ) = withContext(Dispatchers.IO) {
        if (studentDoc == null || !studentDoc.exists()) return@withContext

        val rawPlan = studentDoc.get("activeStudyPlan") as? Map<*, *>
        val studyPlanDao = try { db.studyPlanDao() } catch (e: Exception) { null }

        if (studyPlanDao == null) {
            Log.w(syncTag, "studyPlanDao is not available in Room DB, skipping study plan ingestion.")
            return@withContext
        }

        if (rawPlan != null) {
            val title = sanitizeTurkishText(rawPlan["title"] as? String ?: "Çalışma Planı")
            val strategyNote = sanitizeTurkishText(rawPlan["strategyNote"] as? String ?: "")
            val totalQuestions = (rawPlan["totalTargetQuestions"] as? Number)?.toInt() ?: 0
            val updatedAt = studentDoc.getTimestamp("activeStudyPlanUpdatedAt")?.toDate()?.time
                ?: System.currentTimeMillis()

            val planEntity = ActivePlanEntity(
                id = "CURRENT_ACTIVE_PLAN",
                title = title,
                strategyNote = strategyNote,
                totalTargetQuestions = totalQuestions,
                updatedAtTimestamp = updatedAt
            )

            val rawDays = rawPlan["days"] as? List<*> ?: emptyList<Any>()
            val taskEntities = mutableListOf<StudyTaskEntity>()
            val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

            rawDays.forEachIndexed { defaultIdx, dayObj ->
                val dayMap = dayObj as? Map<*, *> ?: return@forEachIndexed
                val rawDayName = dayMap["day"] as? String
                val dayName = sanitizeTurkishText(if (rawDayName.isNullOrBlank()) "Gün ${defaultIdx + 1}" else rawDayName)
                val dayIdx = dayNames.indexOf(dayName).let { if (it == -1) defaultIdx else it }
                val tasks = dayMap["tasks"] as? List<*> ?: emptyList<Any>()

                tasks.forEach { taskObj ->
                    val taskMap = taskObj as? Map<*, *> ?: return@forEach
                    taskEntities.add(
                        StudyTaskEntity(
                            planId = "CURRENT_ACTIVE_PLAN",
                            dayName = dayName,
                            dayIndex = dayIdx,
                            time = taskMap["time"] as? String ?: "",
                            subject = sanitizeTurkishText(taskMap["subject"] as? String ?: "Ders"),
                            topic = sanitizeTurkishText(taskMap["topic"] as? String ?: ""),
                            goal = sanitizeTurkishText(taskMap["goal"] as? String ?: ""),
                            isCompleted = taskMap["isCompleted"] as? Boolean ?: false
                        )
                    )
                }
            }

            studyPlanDao.replaceActivePlanWithTasks(planEntity, taskEntities)
            Log.d(syncTag, "Successfully ingested active study plan and ${taskEntities.size} tasks into StudyPlanDao Room DB.")

            // --- BRIDGE TO LEGACY PlanDao FOR ANDROID UI ---
            try {
                val planFolderDao = db.planFolderDao()
                val planDao = db.planDao()

                var defaultFolderId = getFirstActiveFolderIdOrNull(planFolderDao)
                if (defaultFolderId == null) {
                    val newFolder = com.example.sharkmentor.data.local.entity.PlanFolderEntity(
                        id = "ACTIVE_PLAN_FOLDER",
                        title = "Aktif Çalışma Programı",
                        createdAt = System.currentTimeMillis(),
                        endTimestamp = System.currentTimeMillis() + (7L * 24 * 3600 * 1000),
                        isArchived = false
                    )
                    planFolderDao.insertFolder(newFolder)
                    defaultFolderId = "ACTIVE_PLAN_FOLDER"
                }

                val legacyPlanTasks = mutableListOf<com.example.sharkmentor.data.local.entity.PlanEntity>()

                rawDays.forEachIndexed { defaultIdx, dayObj ->
                    val dayMap = dayObj as? Map<*, *> ?: return@forEachIndexed
                    val rawDayName = dayMap["day"] as? String
                    val dayName = sanitizeTurkishText(if (rawDayName.isNullOrBlank()) "Gün ${defaultIdx + 1}" else rawDayName)
                    val dayIdx = dayNames.indexOf(dayName).let { if (it == -1) defaultIdx else it }
                    val tasks = dayMap["tasks"] as? List<*> ?: emptyList<Any>()

                    tasks.forEachIndexed { taskIdx, taskObj ->
                        val taskMap = taskObj as? Map<*, *> ?: return@forEachIndexed
                        val rawTaskTitle = taskMap["topic"] as? String ?: taskMap["goal"] as? String ?: "Görev"
                        legacyPlanTasks.add(
                            PlanEntity(
                                id = "ACTIVE_PLAN_TASK_${dayIdx}_${taskIdx}",
                                folderId = defaultFolderId,
                                title = sanitizeTurkishText(rawTaskTitle),
                                subject = sanitizeTurkishText(taskMap["subject"] as? String ?: "Genel"),
                                dayOfWeek = dayIdx + 1, // 1=Mon
                                scheduledTime = taskMap["time"] as? String ?: "",
                                targetQuestions = (taskMap["targetQuestions"] as? Number)?.toInt() ?: 30,
                                isCompleted = taskMap["isCompleted"] as? Boolean ?: false,
                                orderIndex = taskIdx
                            )
                        )
                    }
                }

                // Perform transactional wipe and insert to ensure the UI updates instantly
                db.withTransaction {
                    planDao.deleteTasksByFolder(defaultFolderId)
                    if (legacyPlanTasks.isNotEmpty()) {
                        planDao.insertTasks(legacyPlanTasks)
                    }
                }
                Log.d(syncTag, "Successfully bridged active study plan to legacy PlanDao (Folder: $defaultFolderId).")
            } catch (e: Exception) {
                Log.e(syncTag, "Failed to bridge active study plan to legacy PlanDao: ${e.message}", e)
            }
            // -----------------------------------------------
        } else {
            studyPlanDao.clearPlan()
            studyPlanDao.clearTasks()
            Log.d(syncTag, "Active study plan removed from Firestore, wiped locally.")
        }
    }

    private suspend fun ingestCustomScheduleData(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        studentDoc: DocumentSnapshot?,
        syncTag: String,
        uid: String
    ) = withContext(Dispatchers.IO) {
        if (studentDoc == null || !studentDoc.exists()) return@withContext
        val scheduleMap = studentDoc.get("customScheduleData") as? Map<*, *>
        val scheduleDao = db.institutionScheduleDao()
        val planDao2 = db.planDao()

        // Purge misrouted timetable items from planDao (one-time cleanup)
        planDao2.purgeMisroutedScheduleTasks()
        planDao2.purgeWeeklyScheduleFolder()

        if (scheduleMap != null) {
            val rawDays = scheduleMap["days"] as? List<*> ?: emptyList<Any>()
            val dayNames = listOf("Pazartesi", "Sal\u0131", "\u00c7ar\u015famba", "Per\u015fembe", "Cuma", "Cumartesi", "Pazar")
            val entitiesToInsert = mutableListOf<InstitutionScheduleEntity>()

            rawDays.forEachIndexed { defaultDayIdx, dayObj ->
                val dayMap = dayObj as? Map<*, *> ?: return@forEachIndexed
                val dayName = dayMap["day"] as? String ?: dayNames.getOrElse(defaultDayIdx) { "Pazartesi" }
                val dayOfWeek = dayNames.indexOfFirst { it.equals(dayName.trim(), ignoreCase = true) }
                    .let { if (it == -1) defaultDayIdx + 1 else it + 1 }
                val slots = (dayMap["classes"] ?: dayMap["slots"] ?: dayMap["tasks"]) as? List<*> ?: emptyList<Any>()

                slots.forEachIndexed { slotIdx, slotObj ->
                    val slotMap = slotObj as? Map<*, *> ?: return@forEachIndexed
                    val subject = sanitizeTurkishText(slotMap["subject"] as? String ?: "Ders")
                    val teacher = sanitizeTurkishText(slotMap["teacher"] as? String ?: "")
                    val period = (slotMap["period"] as? Number)?.toInt() ?: (slotIdx + 1)
                    val rawTime = slotMap["time"] as? String ?: slotMap["startTime"] as? String ?: "08:00"
                    val timeParts = rawTime.split("-").map { it.trim() }
                    val startTime = timeParts.getOrElse(0) { "08:00" }
                    val endTime = if (timeParts.size >= 2) timeParts[1] else try {
                        val (h, m) = startTime.split(":").map { it.toInt() }
                        val total = h * 60 + m + 45
                        "%02d:%02d".format(total / 60, total % 60)
                    } catch (e: Exception) { "09:00" }
                    val stableId = "CUSTOM_SCHED_${dayOfWeek}_${period}_${startTime.replace(":", "")}"
                    entitiesToInsert.add(
                        InstitutionScheduleEntity(
                            id = stableId,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            subject = subject,
                            type = "DERSHANE",
                            teacherName = teacher,
                            period = period,
                            isSynced = true
                        )
                    )
                }
            }
            // Replace ONLY CUSTOM_SCHED_* slots so user/institution schedules are preserved
            scheduleDao.replaceCustomSchedules(entitiesToInsert)
            Log.d(syncTag, "Synced ${entitiesToInsert.size} custom schedule slots for $uid.")
        } else if (!studentDoc.metadata.isFromCache) {
            scheduleDao.deleteCustomSchedules()
        }
    }

    // ── 2. Inbound Downstream Pull: Coach-Assigned Resources (With Differential Deletion) ──
    private suspend fun pullRemoteResources(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        uid: String
    ) = withContext(Dispatchers.IO) {
        if (uid.isBlank() || uid == "student_default") return@withContext
        try {
            var snapshot: com.google.firebase.firestore.QuerySnapshot? = null

            // 1. First check the student's subcollection (where Web saves it)
            try {
                snapshot = firestore.collection("students").document(uid)
                    .collection("resources")
                    .limit(COLLECTION_QUERY_LIMIT)
                    .get(Source.DEFAULT).await()
            } catch (e: Exception) {
                Log.w(syncTag, "Subcollection pull failed for students/$uid/resources: ${e.message}")
            }

            // 2. Fallback to legacy root collection if subcollection is empty (24h cooldown)
            if ((snapshot == null || snapshot.isEmpty) &&
                shouldRunLegacyFallback(applicationContext, "resources")
            ) {
                try {
                    val rootSnapshot = firestore.collection("resources")
                        .whereEqualTo("studentId", uid)
                        .limit(COLLECTION_QUERY_LIMIT)
                        .get(Source.DEFAULT).await()
                    if (rootSnapshot.isEmpty) {
                        markLegacyFallbackChecked(applicationContext, "resources")
                    }
                    if (snapshot == null || !rootSnapshot.isEmpty) {
                        snapshot = rootSnapshot
                    }
                } catch (e: Exception) {
                    Log.w(syncTag, "Legacy root collection pull failed for resources: ${e.message}")
                }
            }

            if (snapshot == null) {
                Log.w(syncTag, "pullRemoteResources: Cloud query returned null due to permission/network error. Retaining local resources.")
                return@withContext
            }

            val goalsDao = db.goalsDao()
            val resolvedDocs = snapshot.documents.map { doc ->
                val byCloudId = goalsDao.getResourceByCloudId(doc.id)?.id
                val resolvedId = byCloudId
                    ?: doc.getSafeLong("localId")
                    ?: doc.getSafeLong("id")
                    ?: doc.id.removePrefix("res_").removePrefix("${uid}_").toLongOrNull()
                    ?: abs(doc.id.hashCode().toLong())
                resolvedId to doc
            }

            val activeRemoteIds = resolvedDocs
                .filterNot { (_, doc) -> doc.getSafeBoolean("isDeleted") ?: false }
                .map { it.first }

            if (activeRemoteIds.isEmpty()) {
                goalsDao.clearAllResources()
                Log.d(syncTag, "No active remote resources found. Purged synced Room resources.")
            } else {
                goalsDao.deleteOrphanedResources(activeRemoteIds)
            }

            for ((remoteId, doc) in resolvedDocs) {
                val isDeleted = doc.getSafeBoolean("isDeleted") ?: false
                if (isDeleted) {
                    goalsDao.deleteResourcePermanently(remoteId)
                    continue
                }

                val existing = goalsDao.getResourceById(remoteId)
                if (existing != null && !existing.isSynced) {
                    continue
                }

                // Preserve totalUnits = 0 (unspecified) instead of inventing 100 or 1
                val totalTarget = (doc.getSafeInt("totalUnits")
                    ?: doc.getSafeInt("totalTarget")
                    ?: doc.getSafeInt("totalCount")
                    ?: 0).coerceAtLeast(0)
                val rawCompleted = (doc.getSafeInt("completedUnits")
                    ?: doc.getSafeInt("currentUnits")
                    ?: doc.getSafeInt("completedCount")
                    ?: 0).coerceAtLeast(0)
                val completedCount = if (totalTarget > 0) rawCompleted.coerceIn(0, totalTarget) else rawCompleted
                val unitType = doc.getSafeString("unitType")?.takeIf { it.isNotBlank() }
                    ?: doc.getSafeString("unit")?.takeIf { it.isNotBlank() }
                    ?: run {
                        val targetType = doc.getSafeString("targetType") ?: doc.getSafeString("resourceType") ?: ""
                        if (targetType.contains("QUESTION", ignoreCase = true) || targetType == "Soru") "Soru" else "Sayfa"
                    }
                val rawTitle = sanitizeTurkishText(doc.getSafeString("title") ?: "Kaynak Kitap")
                val publisher = sanitizeTurkishText(doc.getSafeString("publisher"))
                val finalTitle = if (publisher.isBlank()) rawTitle else "$publisher - $rawTitle"

                val remoteUpdated = doc.getTimestamp("updatedAt")?.toDate()?.time
                    ?: doc.getSafeLong("updatedAt")
                    ?: System.currentTimeMillis()

                val resourceEntity = ResourceEntity(
                    id = remoteId,
                    title = finalTitle,
                    subject = sanitizeTurkishText(doc.getSafeString("subject") ?: "Genel"),
                    unitType = unitType,
                    totalUnits = totalTarget,
                    completedUnits = completedCount,
                    updatedAtMillis = remoteUpdated,
                    colorHex = doc.getSafeString("colorHex") ?: "#3B82F6",
                    isAssignedByCoach = doc.getSafeBoolean("isAssignedByCoach")
                        ?: doc.getSafeBoolean("isCoachAssigned")
                        ?: true,
                    isDeleted = false,
                    isSynced = true,
                    cloudId = doc.id,
                    lastModifiedMillis = remoteUpdated
                )

                goalsDao.upsertResource(resourceEntity)
                Log.d(syncTag, "Successfully saved remote resource to Room: id=$remoteId, cloudId=${doc.id}")
            }
        } catch (e: Exception) {
            Log.e(syncTag, "Error in pullRemoteResources: ${e.message}", e)
        }
    }

    // ── 3. Inbound Downstream Pull: Coach-Assigned Topics ──
    private suspend fun pullRemoteTopics(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        uid: String
    ) = withContext(Dispatchers.IO) {
        if (uid.isBlank() || uid == "student_default") return@withContext
        try {
            var snapshot: com.google.firebase.firestore.QuerySnapshot? = null

            // 1. Try student's subcollection first
            try {
                snapshot = firestore.collection("students").document(uid)
                    .collection("topics")
                    .limit(COLLECTION_QUERY_LIMIT)
                    .get(Source.DEFAULT).await()
            } catch (e: Exception) {
                Log.w(syncTag, "Subcollection pull failed for students/$uid/topics: ${e.message}")
            }

            // 2. Fallback to legacy root collection if subcollection was empty (24h cooldown)
            if ((snapshot == null || snapshot.isEmpty) &&
                shouldRunLegacyFallback(applicationContext, "topics")
            ) {
                try {
                    val rootSnapshot = firestore.collection("topics")
                        .whereEqualTo("studentId", uid)
                        .limit(COLLECTION_QUERY_LIMIT)
                        .get(Source.DEFAULT).await()
                    if (rootSnapshot.isEmpty) {
                        markLegacyFallbackChecked(applicationContext, "topics")
                    }
                    if (snapshot == null || !rootSnapshot.isEmpty) {
                        snapshot = rootSnapshot
                    }
                } catch (e: Exception) {
                    Log.w(syncTag, "Legacy root collection pull failed for topics: ${e.message}")
                }
            }

            if (snapshot == null) {
                Log.w(syncTag, "pullRemoteTopics: Cloud topics query returned null. Retaining local topics.")
                return@withContext
            }

            val syncDao = db.syncDao()
            val goalsDao = db.goalsDao()

            val resolvedDocs = snapshot.documents.map { doc ->
                val resolvedId = doc.getSafeLong("localId")
                    ?: doc.getSafeLong("id")
                    ?: doc.id.removePrefix("${uid}_").toLongOrNull()
                    ?: abs(doc.id.hashCode().toLong())
                resolvedId to doc
            }

            val activeRemoteIds = resolvedDocs
                .filterNot { (_, doc) -> doc.getSafeBoolean("isDeleted") ?: false }
                .map { it.first }

            if (activeRemoteIds.isEmpty()) {
                goalsDao.clearAllTopics()
            } else {
                goalsDao.deleteOrphanedTopics(activeRemoteIds)
            }

            for ((remoteId, doc) in resolvedDocs) {
                val isDeleted = doc.getSafeBoolean("isDeleted") ?: false
                if (isDeleted) {
                    syncDao.deleteTopicPermanently(remoteId)
                    continue
                }

                val existing = goalsDao.getTopicById(remoteId)
                if (existing != null && !existing.isSynced) {
                    continue
                }

                val topicEntity = com.example.sharkmentor.data.local.entity.TopicGoalEntity(
                    id = remoteId,
                    subject = sanitizeTurkishText(doc.getSafeString("subject") ?: doc.getSafeString("subjectName") ?: "Genel"),
                    topic = sanitizeTurkishText(doc.getSafeString("topicName") ?: doc.getSafeString("topic") ?: "Konu"),
                    currentLevel = doc.getSafeInt("masteryLevel") ?: doc.getSafeInt("masteryScore") ?: 0,
                    targetLevel = doc.getSafeInt("targetMasteryScore") ?: 5,
                    notes = "",
                    isSynced = true,
                    isDeleted = false,
                    updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time
                        ?: doc.getSafeLong("updatedAt")
                        ?: System.currentTimeMillis()
                )

                syncDao.upsertTopic(topicEntity)
                Log.d(syncTag, "Successfully saved remote topic to Room: id=$remoteId, topic=${topicEntity.topic}")
            }
        } catch (e: Exception) {
            Log.e(syncTag, "Error in pullRemoteTopics: ${e.message}", e)
        }
    }

    // ── 4. Inbound Downstream Pull: Coach-Assigned Goals (targets/goals) ──
    private suspend fun pullRemoteGoals(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        uid: String
    ) = withContext(Dispatchers.IO) {
        if (uid.isBlank() || uid == "student_default") return@withContext
        try {
            var goalsSnapshot: com.google.firebase.firestore.QuerySnapshot? = null

            // 1. Try student's goals subcollection first
            try {
                goalsSnapshot = firestore.collection("students").document(uid)
                    .collection("goals")
                    .limit(COLLECTION_QUERY_LIMIT)
                    .get(Source.DEFAULT).await()
            } catch (e: Exception) {
                Log.w(syncTag, "Subcollection pull failed for students/$uid/goals: ${e.message}")
            }

            // 2. Fallback to top-level targets collection if subcollection was empty (24h cooldown)
            if ((goalsSnapshot == null || goalsSnapshot.isEmpty) &&
                shouldRunLegacyFallback(applicationContext, "goals")
            ) {
                try {
                    val rootSnapshot = firestore.collection("targets")
                        .whereEqualTo("studentId", uid)
                        .limit(COLLECTION_QUERY_LIMIT)
                        .get(Source.DEFAULT).await()
                    if (rootSnapshot.isEmpty) {
                        markLegacyFallbackChecked(applicationContext, "goals")
                    }
                    if (goalsSnapshot == null || !rootSnapshot.isEmpty) {
                        goalsSnapshot = rootSnapshot
                    }
                } catch (e: Exception) {
                    Log.w(syncTag, "Legacy root collection pull failed for targets: ${e.message}")
                }
            }

            if (goalsSnapshot == null) {
                Log.w(syncTag, "pullRemoteGoals: Cloud goals query returned null. Retaining local goals.")
                return@withContext
            }

            val targetDao = db.targetDao()
            val concreteTargetDao = db.concreteTargetDao()

            val resolvedGoalDocs = goalsSnapshot.documents.map { doc ->
                val remoteId = doc.getSafeLong("localId")
                    ?: doc.getSafeLong("id")
                    ?: doc.id.removePrefix("${uid}_").toLongOrNull()
                    ?: abs(doc.id.hashCode().toLong())
                remoteId to doc
            }
            val activeGoalIds = resolvedGoalDocs
                .filterNot { (_, doc) -> doc.getSafeBoolean("isDeleted") ?: false }
                .map { it.first }

            if (activeGoalIds.isNotEmpty()) {
                targetDao.deleteOrphanedTargets(activeGoalIds)
            } else {
                targetDao.clearAllTargets()
            }

            var latestMacroGoal: com.google.firebase.firestore.DocumentSnapshot? = null

            for ((remoteId, doc) in resolvedGoalDocs) {
                val isDeleted = doc.getSafeBoolean("isDeleted") ?: false
                if (isDeleted) {
                    targetDao.purgeTarget(remoteId)
                    continue
                }

                // Check if it's the latest macro goal
                val hasTargetUni = !doc.getString("targetUniversity").isNullOrBlank()
                val hasTargetDep = !doc.getString("targetDepartment").isNullOrBlank()
                val hasTargetRank = (doc.getLong("targetRanking") ?: 0L) > 0L
                if (hasTargetUni || hasTargetDep || hasTargetRank) {
                    val currentCreatedAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: doc.id.toLongOrNull() ?: 0L
                    val latestCreatedAt = latestMacroGoal?.getTimestamp("createdAt")?.toDate()?.time ?: latestMacroGoal?.id?.toLongOrNull() ?: -1L
                    if (currentCreatedAt > latestCreatedAt) {
                        latestMacroGoal = doc
                    }
                }

                val existingTarget = targetDao.getTargetById(remoteId)
                if (existingTarget != null && !existingTarget.isSynced) {
                    continue
                }

                val title = sanitizeTurkishText(doc.getSafeString("title") ?: "Hedef")
                val category = doc.getSafeString("category") ?: "CUSTOM"
                val isCompleted = doc.getSafeBoolean("isCompleted") ?: false

                val targetEntity = TargetEntity(
                    id = remoteId,
                    title = title,
                    description = sanitizeTurkishText(doc.getSafeString("description") ?: ""),
                    deadlineMillis = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000),
                    type = TargetType.LONG_TERM,
                    period = TargetPeriod.MONTHLY,
                    priority = "NORMAL",
                    category = category,
                    targetValue = null,
                    isCompleted = isCompleted,
                    isAssignedByCoach = true,
                    coachNote = doc.getSafeString("coachNote"),
                    date = "",
                    isSynced = true,
                    isDeleted = false,
                    cloudId = doc.id,
                    lastModifiedMillis = System.currentTimeMillis()
                )

                targetDao.upsertTarget(targetEntity)
                Log.d(syncTag, "Successfully saved remote goal to Room: id=$remoteId, title=$title")
            }

            // Bridge to ConcreteTargetDao (Macro Target)
            latestMacroGoal?.let { doc ->
                val title = sanitizeTurkishText(doc.getSafeString("title") ?: "Hedef")
                val targetRanking = doc.getSafeLong("targetRanking")?.toInt()
                val targetScoreOrRank = if (targetRanking != null && targetRanking > 0) "$targetRanking Sıralama" else ""
                val concreteTarget = com.example.sharkmentor.data.local.entity.ConcreteTargetEntity(
                    id = 1,
                    examType = "YKS",
                    targetTitle = title,
                    targetScoreOrRank = targetScoreOrRank,
                    aiAnalysisSummary = sanitizeTurkishText(doc.getSafeString("coachNote") ?: ""),
                    targetRanking = targetRanking,
                    targetUniversity = sanitizeTurkishText(doc.getSafeString("targetUniversity") ?: ""),
                    targetDepartment = sanitizeTurkishText(doc.getSafeString("targetDepartment") ?: ""),
                    updatedAtMillis = System.currentTimeMillis()
                )
                concreteTargetDao.saveTarget(concreteTarget)
                Log.d(syncTag, "Successfully bridged latest macro goal into ConcreteTargetDao")
            }
        } catch (e: Exception) {
            Log.e(syncTag, "Error in pullRemoteGoals: ${e.message}", e)
        }
    }

    // ── 5. Inbound Downstream Pull: Legacy Plans / Targets ─────────────────
    private suspend fun pullRemoteAssignedPlans(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        instId: String,
        uid: String
    ) = withContext(Dispatchers.IO) {
        val collectionsToPull = listOf("plans", "targets")
        val targetDao = db.targetDao()
        val defaultFolderId = getFirstActiveFolderIdOrNull(db.planFolderDao())

        for (colName in collectionsToPull) {
            if (!shouldRunLegacyFallback(applicationContext, "assigned_$colName")) {
                continue
            }
            try {
                val fourteenDaysAgo = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
                val snapshot = try {
                    firestore.collection(colName)
                        .whereEqualTo("studentId", uid)
                        .whereGreaterThanOrEqualTo("createdAt", fourteenDaysAgo)
                        .limit(COLLECTION_QUERY_LIMIT)
                        .get(Source.DEFAULT).await()
                } catch (e: Exception) {
                    firestore.collection(colName)
                        .whereEqualTo("studentId", uid)
                        .limit(COLLECTION_QUERY_LIMIT)
                        .get(Source.DEFAULT).await()
                }

                if (snapshot.isEmpty) {
                    markLegacyFallbackChecked(applicationContext, "assigned_$colName")
                    continue
                }

                Log.d(syncTag, "Pulling from collection '$colName': fetched ${snapshot.size()} documents. Processing in single batch...")

                val pendingDeletions = db.pendingDeletionDao().getAllPendingDeletions()
                val deletedEntityIds = pendingDeletions.map { it.entityId }.toSet()

                val entitiesToInsert = mutableListOf<PlanEntity>()

                for (doc in snapshot.documents) {
                    try {
                        val docStudentId = doc.getSafeString("studentId") ?: doc.getSafeString("uid")
                        if (docStudentId != null && docStudentId != uid) continue

                        val isDeleted = doc.getSafeBoolean("isDeleted") ?: false
                        val remoteId = doc.getSafeLong("localId")
                            ?: doc.getSafeLong("id")
                            ?: doc.id.removePrefix("${uid}_").toLongOrNull()
                            ?: abs(doc.id.hashCode().toLong())

                        val folderId = doc.getSafeString("folderId") ?: ""
                        val isDeletedLocally = deletedEntityIds.contains(doc.id) ||
                                deletedEntityIds.contains(remoteId.toString()) ||
                                (folderId.isNotBlank() && deletedEntityIds.contains(folderId))

                        if (isDeletedLocally) {
                            Log.d(syncTag, "Skipping remote document [$colName]: docId=${doc.id} (local tombstone exists)")
                            continue
                        }

                        if (isDeleted) {
                            targetDao.purgeTarget(remoteId)
                            try {
                                db.planDao().deleteTasksByFolder(doc.id)
                            } catch (_: Exception) {}
                            Log.d(syncTag, "Purged deleted tombstone [$colName]: ID=$remoteId / docId=${doc.id}")
                            continue
                        }

                        val title = sanitizeTurkishText(doc.getSafeString("title") ?: doc.getSafeString("planTitle") ?: "Koç Hedefi")

                        if (colName == "plans") {
                            val resolvedFolderId = defaultFolderId ?: continue
                            val rawPlan = PlanEntity(
                                id = doc.id,
                                folderId = doc.getSafeString("folderId") ?: "",
                                title = title,
                                subject = doc.getSafeString("subject") ?: doc.getSafeString("category") ?: "Ders",
                                dayOfWeek = doc.getSafeInt("dayOfWeek") ?: 0,
                                scheduledTime = doc.getSafeString("scheduledTime") ?: "",
                                targetQuestions = (doc.getSafeInt("targetQuestions") ?: doc.getSafeInt("targetValue")?.toInt() ?: 0).let { q -> if (q > 0) q else 30 },
                                isCompleted = doc.getSafeBoolean("isCompleted") ?: doc.getSafeBoolean("completed") ?: false,
                                orderIndex = doc.getSafeInt("orderIndex") ?: 0
                            )
                            val parsedPlan = parseLegacyPlanTitle(rawPlan, resolvedFolderId)
                            entitiesToInsert.add(parsedPlan)
                        }

                        val rawPeriod = doc.getSafeString("planType") ?: doc.getSafeString("period") ?: "SHORT_TERM"
                        val normalizedPeriod = if (colName == "targets") "LONG_TERM" else if (rawPeriod.contains("LONG", ignoreCase = true) || rawPeriod.contains("UZUN", ignoreCase = true)) "LONG_TERM" else "SHORT_TERM"

                        val (targetType, targetPeriod) = if (normalizedPeriod == "LONG_TERM") {
                            Pair(TargetType.LONG_TERM, TargetPeriod.MONTHLY)
                        } else {
                            Pair(TargetType.SHORT_TERM, TargetPeriod.WEEKLY)
                        }

                        val priority = doc.getSafeString("priority") ?: "NORMAL"
                        val dateStr = doc.getSafeString("date") ?: doc.getSafeString("planDate") ?: doc.getSafeString("deadline") ?: ""

                        val deadlineMillis = doc.getSafeLong("deadlineMillis") ?: try {
                            if (dateStr.isNotBlank()) {
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time
                                    ?: (System.currentTimeMillis() + 86400000L)
                            } else {
                                System.currentTimeMillis() + 86400000L
                            }
                        } catch (e: Exception) {
                            System.currentTimeMillis() + 86400000L
                        }

                        val targetEntity = TargetEntity(
                            id = remoteId,
                            title = title,
                            description = sanitizeTurkishText(doc.getSafeString("coachNote") ?: doc.getSafeString("description") ?: ""),
                            deadlineMillis = deadlineMillis,
                            type = targetType,
                            period = targetPeriod,
                            priority = priority,
                            category = doc.getSafeString("category") ?: "CUSTOM",
                            targetValue = doc.getSafeString("targetValue"),
                            isCompleted = doc.getSafeBoolean("isCompleted") ?: doc.getSafeBoolean("completed") ?: false,
                            isAssignedByCoach = doc.getSafeBoolean("isAssignedByCoach") ?: true,
                            coachNote = doc.getSafeString("coachNote") ?: doc.getSafeString("description"),
                            date = dateStr,
                            isSynced = true,
                            isDeleted = false,
                            cloudId = doc.id,
                            lastModifiedMillis = System.currentTimeMillis()
                        )

                        val taskEntities = mutableListOf<TaskEntity>()
                        val rawTasks = doc.get("tasks") as? List<*> ?: emptyList<Any>()
                        rawTasks.forEachIndexed { idx, item ->
                            if (item is Map<*, *>) {
                                val taskTitle = item["title"]?.toString() ?: ""
                                if (taskTitle.isNotBlank()) {
                                    val isTaskComp = when (val compVal = item["isCompleted"]) {
                                        is Boolean -> compVal
                                        else -> compVal?.toString()?.toBooleanStrictOrNull() ?: false
                                    }
                                    taskEntities.add(
                                        TaskEntity(
                                            id = (remoteId * 100) + idx + 1,
                                            targetId = remoteId,
                                            title = taskTitle,
                                            isCompleted = isTaskComp,
                                            orderIndex = idx,
                                            isSynced = true
                                        )
                                    )
                                }
                            }
                        }

                        if (colName == "targets") {
                            val existingTarget = targetDao.getTargetById(remoteId)
                            if (existingTarget == null || existingTarget.isSynced) {
                                targetDao.upsertTarget(targetEntity.copy(isSynced = true))
                                targetDao.deleteTasksByTargetId(remoteId)
                            }
                        } else {
                            targetDao.syncRemotePlan(targetEntity, taskEntities)
                        }
                    } catch (docErr: Exception) {
                        Log.e(syncTag, "Error parsing document ${doc.id} in '$colName': ${docErr.message}", docErr)
                    }
                }

                if (colName == "plans" && entitiesToInsert.isNotEmpty()) {
                    db.planDao().insertAll(entitiesToInsert)
                    Log.d(syncTag, "Batch insert completed for ${entitiesToInsert.size} plans.")
                }
            } catch (colErr: Exception) {
                Log.w(syncTag, "Could not query collection '$colName': ${colErr.message}")
            }
        }
    }

    // ── Outbound Deletions & Sync Helpers ──────────────────────────────────
    private suspend fun syncPendingDeletions(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        defaultInstitution: String,
        uid: String
    ): Int {
        var count = 0
        val pendingDeletionDao = db.pendingDeletionDao()
        val pendingDeletions = pendingDeletionDao.getAllPendingDeletions()
        for (deletion in pendingDeletions) {
            try {
                if (deletion.collectionName == "topics" || deletion.collectionName.startsWith("hedeflerim/")) {
                    val docIds = linkedSetOf("${uid}_${deletion.entityId}", deletion.entityId)
                    var anyDeleted = false
                    for (docId in docIds) {
                        runCatching {
                            firestore.collection("topics").document(docId).delete().await()
                            anyDeleted = true
                        }
                        runCatching {
                            firestore.collection("students").document(uid)
                                .collection("topics").document(docId).delete().await()
                            anyDeleted = true
                        }
                    }
                    if (anyDeleted) {
                        pendingDeletionDao.deleteById(deletion.id)
                        count++
                    }
                } else {
                    val fullDocPath = "${getCollectionPath(defaultInstitution, uid, deletion.collectionName)}/${deletion.entityId}"
                    firestore.document(fullDocPath).delete().await()
                    pendingDeletionDao.deleteById(deletion.id)
                    count++
                }
            } catch (e: Exception) {
                Log.e(syncTag, "Failed delete ${deletion.entityId}", e)
            }
        }
        return count
    }

    private suspend fun syncLocalSessions(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        defaultInstitution: String,
        studentName: String,
        uid: String
    ): Int {
        var count = 0
        val activeSessionUuid = com.example.sharkmentor.core.timer.FocusSessionRecorder.getActiveSessionId()
        val pendingSessions = db.syncDao().getUnsyncedSessions()
        if (pendingSessions.isEmpty()) return 0

        var touchedParent = false
        for (session in pendingSessions) {
            if (!activeSessionUuid.isNullOrBlank() && session.cloudId == activeSessionUuid) {
                continue
            }
            try {
                val instId = session.institutionId?.takeIf { it.isNotBlank() } ?: defaultInstitution
                val docId = session.cloudId?.takeIf { it.isNotBlank() } ?: session.id.toString()
                val sessionData = hashMapOf(
                    "id" to docId,
                    "cloudId" to docId,
                    "localId" to session.id,
                    "studentId" to uid,
                    "mode" to session.modeName,
                    "modeName" to session.modeName,
                    "durationSeconds" to session.durationSeconds,
                    "durationMinutes" to session.durationSeconds / 60,
                    "durationFormatted" to "${session.durationSeconds / 60} dk ${session.durationSeconds % 60} sn",
                    "afkScore" to session.afkScore,
                    "phoneLiftCount" to session.phoneLiftCount,
                    "screenOnMinutes" to session.screenOnMinutes,
                    "studentName" to studentName,
                    "institutionId" to instId,
                    "isIndividual" to (instId == "INDIVIDUAL"),
                    "timestamp" to session.completedAt,
                    "completedAt" to session.completedAt,
                    "isFinalized" to true,
                    "isDeleted" to false,
                    "syncedAt" to System.currentTimeMillis()
                )

                // Canonical primary session path (cuts Firestore writes by 70%)
                firestore.collection("students").document(uid)
                    .collection("sessions").document(docId)
                    .set(sessionData, SetOptions.merge()).await()

                if (!touchedParent) {
                    val parentStudentData = hashMapOf<String, Any>(
                        "id" to uid,
                        "name" to studentName,
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                    if (instId != "INDIVIDUAL") parentStudentData["coachCode"] = instId
                    firestore.collection("students").document(uid).set(parentStudentData, SetOptions.merge())
                    touchedParent = true
                }

                db.syncDao().markSessionSynced(session.id, docId)
                count++
            } catch (e: Exception) {
                Log.e(syncTag, "Session err", e)
            }
        }
        return count
    }

    private suspend fun syncPendingDeletedTargets(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        uid: String
    ): Int {
        var count = 0
        for (deleted in db.targetDao().getPendingDeletedTargets()) {
            try {
                val docId = deleted.cloudId?.takeIf { it.isNotBlank() } ?: "${uid}_${deleted.id}"
                val deleteData = mapOf("isDeleted" to true, "updatedAt" to FieldValue.serverTimestamp())
                try {
                    firestore.collection("plans").document(docId).set(deleteData, SetOptions.merge()).await()
                } catch (_: Exception) {}
                try {
                    firestore.collection("targets").document(docId).set(deleteData, SetOptions.merge()).await()
                } catch (_: Exception) {}
                try {
                    firestore.collection("students").document(uid).collection("plans").document(docId).set(deleteData, SetOptions.merge()).await()
                } catch (_: Exception) {}
                try {
                    firestore.collection("students").document(uid).collection("targets").document(docId).set(deleteData, SetOptions.merge()).await()
                } catch (_: Exception) {}
                db.targetDao().purgeTarget(deleted.id)
                count++
            } catch (e: Exception) {
                Log.e(syncTag, "Target del err", e)
            }
        }
        return count
    }

    private suspend fun syncPendingDeletedResources(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        uid: String
    ): Int {
        var count = 0
        for (deleted in db.goalsDao().getPendingDeletedResources()) {
            try {
                val docId = deleted.cloudId?.takeIf { it.isNotBlank() } ?: "${uid}_${deleted.id}"
                val deleteData = mapOf("isDeleted" to true, "updatedAt" to FieldValue.serverTimestamp())
                try {
                    firestore.collection("resources").document(docId).set(deleteData, SetOptions.merge()).await()
                } catch (_: Exception) {}
                try {
                    firestore.collection("students").document(uid).collection("resources").document(docId).set(deleteData, SetOptions.merge()).await()
                } catch (_: Exception) {}
                db.goalsDao().deleteResourcePermanently(deleted.id)
                count++
            } catch (e: Exception) {
                Log.e(syncTag, "Resource del err", e)
            }
        }
        return count
    }

    private suspend fun syncPendingDeletedTopics(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        uid: String
    ): Int {
        var count = 0
        for (deleted in db.goalsDao().getPendingDeletedTopics()) {
            try {
                val docId = "${uid}_${deleted.id}"
                val deleteData = mapOf("isDeleted" to true, "updatedAt" to FieldValue.serverTimestamp())
                var updatedRemote = false
                runCatching {
                    firestore.collection("topics").document(docId).set(deleteData, SetOptions.merge()).await()
                    updatedRemote = true
                }
                runCatching {
                    firestore.collection("students").document(uid)
                        .collection("topics").document(docId).set(deleteData, SetOptions.merge()).await()
                    updatedRemote = true
                }
                if (updatedRemote) {
                    db.goalsDao().deleteTopicPermanently(deleted.id)
                    count++
                }
            } catch (e: Exception) {
                Log.e(syncTag, "Topic del err", e)
            }
        }
        return count
    }

    private suspend fun syncLocalPlans(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        defaultInstitution: String,
        studentName: String,
        uid: String
    ): Int {
        var count = 0
        for (planWithTasks in db.syncDao().getUnsyncedPlansWithTasks()) {
            try {
                val plan = planWithTasks.target
                val tasks = planWithTasks.tasks
                val instId = plan.institutionId?.takeIf { it.isNotBlank() } ?: defaultInstitution
                val tasksList = tasks.map { mapOf("id" to it.id, "title" to it.title, "isCompleted" to it.isCompleted, "orderIndex" to it.orderIndex) }
                val colName = if (plan.type == TargetType.LONG_TERM) "targets" else "plans"
                val docId = plan.cloudId?.takeIf { it.isNotBlank() } ?: "${uid}_${plan.id}"

                val planData = hashMapOf(
                    "id" to docId, "localId" to plan.id, "studentId" to uid, "title" to plan.title, "date" to plan.date,
                    "deadlineMillis" to plan.deadlineMillis, "isCompleted" to plan.isCompleted, "completed" to plan.isCompleted,
                    "totalTasks" to tasks.size, "completedTasks" to tasks.count { it.isCompleted }, "studentName" to studentName,
                    "institutionId" to instId, "isIndividual" to (instId == "INDIVIDUAL"), "isCoachTask" to plan.isAssignedByCoach,
                    "isAssignedByCoach" to plan.isAssignedByCoach, "tasks" to tasksList, "isDeleted" to plan.isDeleted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                firestore.collection(colName).document(docId).set(planData, SetOptions.merge()).await()
                try {
                    firestore.collection("students").document(uid).collection(colName).document(docId).set(planData, SetOptions.merge()).await()
                } catch (_: Exception) {}

                if (plan.isDeleted) {
                    db.targetDao().purgeTargetAndTasks(plan.id)
                } else {
                    db.syncDao().markTargetSynced(plan.id, docId)
                    db.syncDao().markTasksAsSyncedForTarget(plan.id)
                }
                count++
            } catch (e: Exception) {
                Log.e(syncTag, "Plan err", e)
            }
        }
        return count
    }

    private suspend fun syncLocalTopics(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        uid: String
    ): Int {
        var count = 0
        val pendingTopics = db.syncDao().getUnsyncedTopics()
        val succeededIds = mutableListOf<Long>()
        for (topic in pendingTopics) {
            try {
                val docId = "${uid}_${topic.id}"
                val rootTopicData = hashMapOf<String, Any?>(
                    "id" to docId,
                    "localId" to topic.id,
                    "studentId" to uid,
                    "userId" to uid,
                    "subject" to topic.subject,
                    "topic" to topic.topic,
                    "topicName" to topic.topic,
                    "masteryLevel" to topic.currentLevel,
                    "targetMasteryScore" to topic.targetLevel,
                    "isDeleted" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("topics").document(docId).set(rootTopicData, SetOptions.merge()).await()
                runCatching {
                    firestore.collection("students").document(uid).collection("topics").document(docId)
                        .set(rootTopicData, SetOptions.merge()).await()
                }
                succeededIds.add(topic.id)
                count++
            } catch (e: Exception) {
                Log.e(syncTag, "Topic err", e)
            }
        }
        if (succeededIds.isNotEmpty()) db.syncDao().markTopicsAsSynced(succeededIds)
        return count
    }

    private suspend fun syncLocalResources(
        db: com.example.sharkmentor.data.local.SharkMentorDatabase,
        firestore: FirebaseFirestore,
        syncTag: String,
        uid: String
    ): Int {
        var count = 0
        val pendingResources = db.syncDao().getUnsyncedResources()
        for (resource in pendingResources) {
            try {
                val docId = resource.cloudId?.takeIf { it.isNotBlank() } ?: "${uid}_${resource.id}"
                val isCompleted = resource.totalUnits > 0 && resource.completedUnits >= resource.totalUnits
                val resourceData = hashMapOf<String, Any?>(
                    "id" to docId,
                    "localId" to resource.id,
                    "title" to resource.title,
                    "subject" to resource.subject,
                    "unitType" to resource.unitType,
                    "currentUnits" to resource.completedUnits,
                    "completedUnits" to resource.completedUnits,
                    "totalUnits" to resource.totalUnits,
                    "isCompleted" to isCompleted,
                    "completed" to isCompleted,
                    "colorHex" to (resource.colorHex ?: "#0ea5e9"),
                    "studentId" to uid,
                    "userId" to uid,
                    "origin" to "APP",
                    "isDeleted" to false,
                    "isAssignedByCoach" to resource.isAssignedByCoach,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                firestore.document("resources/$docId").set(resourceData, SetOptions.merge()).await()
                try {
                    firestore.document("students/$uid/resources/$docId").set(resourceData, SetOptions.merge()).await()
                } catch (_: Exception) {}
                db.syncDao().markResourceSynced(resource.id, docId)
                count++
            } catch (e: Exception) {
                Log.e(syncTag, "Res err", e)
            }
        }
        return count
    }

    private suspend fun getFirstActiveFolderIdOrNull(
        planFolderDao: PlanFolderDao
    ): String? {
        return planFolderDao.getFoldersCreatedAfter(0).firstOrNull()?.id
    }

    private fun parseLegacyPlanTitle(
        plan: PlanEntity,
        defaultFolderId: String
    ): PlanEntity {
        val title = sanitizeTurkishText(plan.title)

        var day = plan.dayOfWeek
        var time = plan.scheduledTime

        if (day !in 1..7) {
            day = when {
                title.contains("Pazartesi", ignoreCase = true) -> 1
                title.contains("Salı", ignoreCase = true) -> 2
                title.contains("Çarşamba", ignoreCase = true) -> 3
                title.contains("Perşembe", ignoreCase = true) -> 4
                title.contains("Cuma", ignoreCase = true) -> 5
                title.contains("Cumartesi", ignoreCase = true) -> 6
                title.contains("Pazar", ignoreCase = true) -> 7
                else -> 1
            }
        }

        val timeMatcher = Regex("(\\d{2}:\\d{2})").find(title)
        if (timeMatcher != null && (time.isBlank() || time == "09:00")) {
            time = timeMatcher.groupValues[1]
        }

        val folderId = if (plan.folderId.isBlank() || plan.folderId == "default_folder") {
            defaultFolderId
        } else {
            plan.folderId
        }

        return plan.copy(
            folderId = folderId,
            dayOfWeek = day,
            scheduledTime = if (time.isNotBlank()) time else "09:00"
        )
    }

    private fun getCollectionPath(instId: String, uid: String, collection: String): String {
        return if (instId == "INDIVIDUAL") {
            "students/$uid/$collection"
        } else {
            "institutions/$instId/students/$uid/$collection"
        }
    }
}

// ── DocumentSnapshot Safety Extensions ──────────────────────────────────────

private fun DocumentSnapshot.getSafeString(field: String): String? {
    val value = get(field) ?: return null
    return value.toString()
}

private fun DocumentSnapshot.getSafeLong(field: String): Long? {
    val value = get(field) ?: return null
    return (value as? Number)?.toLong()
        ?: value.toString().toDoubleOrNull()?.toLong()
        ?: value.toString().toLongOrNull()
}

private fun DocumentSnapshot.getSafeInt(field: String): Int? {
    val value = get(field) ?: return null
    return (value as? Number)?.toInt()
        ?: value.toString().toDoubleOrNull()?.toInt()
        ?: value.toString().toIntOrNull()
}

private fun DocumentSnapshot.getSafeBoolean(field: String): Boolean? {
    val value = get(field) ?: return null
    if (value is Boolean) return value
    return value.toString().toBooleanStrictOrNull()
}

