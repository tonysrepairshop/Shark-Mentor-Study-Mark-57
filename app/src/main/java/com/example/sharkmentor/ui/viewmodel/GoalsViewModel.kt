package com.example.sharkmentor.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.data.local.entity.ConcreteTargetEntity
import com.example.sharkmentor.data.local.dao.ConcreteTargetDao
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.service.sync.SyncWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

@Stable
data class SubjectFolderUiModel(
    val subjectName: String,
    val topics: List<TopicGoalEntity>,
    val averageLevel: Float,
    val completedCount: Int
)

@Immutable
data class GoalsUiState(
    val isLoading: Boolean = false,
    val concreteTarget: ConcreteTargetEntity? = null,
    val isAnalyzingTarget: Boolean = false,
    val groupedTopics: List<SubjectFolderUiModel> = emptyList(),
    val resources: List<ResourceEntity> = emptyList(),
    val isScanning: Boolean = false,
    val scanError: String? = null,
    val lastScannedUri: String? = null
)

class GoalsViewModel(application: Application) : AndroidViewModel(application) {

    private var dao: com.example.sharkmentor.data.local.dao.GoalsDao? = null
    private var targetDao: ConcreteTargetDao? = null
    @Volatile
    private var resourcesListener: ListenerRegistration? = null
    @Volatile
    private var activeListenersUid: String? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private val aiService = com.example.sharkmentor.core.ai.GeminiCoreService(application)

    private val _topicGoals = MutableStateFlow<List<TopicGoalEntity>>(emptyList())
    private val _resources = MutableStateFlow<List<ResourceEntity>>(emptyList())
    private val _concreteTarget = MutableStateFlow<ConcreteTargetEntity?>(null)
    private val _isScanning = MutableStateFlow(false)
    private val _scanError = MutableStateFlow<String?>(null)
    private val _lastScannedUri = MutableStateFlow<String?>(null)
    private val _isAnalyzingTarget = MutableStateFlow(false)

    private val _expandedSubjectNames = MutableStateFlow<Set<String>>(emptySet())
    val expandedSubjectNames: StateFlow<Set<String>> = _expandedSubjectNames.asStateFlow()

    fun toggleSubjectFolder(subjectName: String) {
        _expandedSubjectNames.update { currentSet ->
            if (currentSet.contains(subjectName)) {
                currentSet - subjectName
            } else {
                currentSet + subjectName
            }
        }
    }

    fun collapseAllFolders() {
        _expandedSubjectNames.value = emptySet()
    }

    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<GoalsUiState> = combine(
        _concreteTarget,
        _isAnalyzingTarget,
        _topicGoals,
        _resources,
        _isScanning,
        _scanError,
        _lastScannedUri,
        _isLoading
    ) { params ->
        val target = params[0] as com.example.sharkmentor.data.local.entity.ConcreteTargetEntity?
        val analyzing = params[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val topics = params[2] as List<com.example.sharkmentor.data.local.entity.TopicGoalEntity>
        @Suppress("UNCHECKED_CAST")
        val resources = params[3] as List<com.example.sharkmentor.data.local.entity.ResourceEntity>
        val scanning = params[4] as Boolean
        val error = params[5] as String?
        val uri = params[6] as String?
        val loading = params[7] as Boolean

        val grouped = topics.groupBy { it.subject }
            .map { (subject, t) ->
                val avg = if (t.isNotEmpty()) t.map { it.currentLevel }.average().toFloat() else 1f
                val completed = t.count { it.currentLevel >= 4 }
                SubjectFolderUiModel(
                    subjectName = subject,
                    topics = t,
                    averageLevel = avg,
                    completedCount = completed
                )
            }
            .sortedBy { it.subjectName }

        GoalsUiState(
            isLoading = loading,
            concreteTarget = target,
            isAnalyzingTarget = analyzing,
            groupedTopics = grouped,
            resources = resources,
            isScanning = scanning,
            scanError = error,
            lastScannedUri = uri
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GoalsUiState(isLoading = true)
    )

    fun clearScanError() {
        _scanError.value = null
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val database = SharkMentorDatabase.getDatabase(application)
            dao = database.goalsDao()
            targetDao = database.concreteTargetDao()

            launch {
                targetDao?.getTargetFlow()?.collect { _concreteTarget.value = it }
            }

            launch {
                dao?.getAllTopicGoalsFlow()?.collect {
                    _topicGoals.value = it
                    _isLoading.value = false
                }
            }

            launch {
                dao?.getAllResourcesFlow()?.collect {
                    _resources.value = it
                    _isLoading.value = false
                }
            }

            // Attach cloud listeners immediately if already signed in, and re-attach on auth changes
            val authInstance = runCatching { GoogleServicesHelper.getFirebaseAuth(getApplication()) }.getOrNull()
            val initialUid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
                ?: GoogleServicesHelper.getCurrentUser(authInstance, getApplication())?.uid
            if (!initialUid.isNullOrBlank() && initialUid != "student_default") {
                attachCloudSyncListeners(initialUid)
            }
            if (authInstance != null) {
                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    val nextUid = firebaseAuth.currentUser?.uid
                    if (!nextUid.isNullOrBlank() && nextUid != "student_default") {
                        if (activeListenersUid != nextUid) {
                            attachCloudSyncListeners(nextUid)
                        }
                    } else {
                        detachCloudSyncListeners()
                    }
                }
                authStateListener = listener
                runCatching { authInstance.addAuthStateListener(listener) }
            }
        }
    }

    private fun detachCloudSyncListeners() {
        resourcesListener?.remove()
        resourcesListener = null
        activeListenersUid = null
    }

    private fun attachCloudSyncListeners(uid: String) {
        detachCloudSyncListeners()
        activeListenersUid = uid
        val appContext = getApplication<Application>()
        val fs = GoogleServicesHelper.getFirebaseFirestore(appContext) ?: return

        // Quota-safe 1-doc Topic Mastery sync (0 reads within 15-min cooldown; 1 read on reinstall/cooldown expiry)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                com.example.sharkmentor.data.sync.TopicCloudSyncManager
                    .pullAndMergeFromCloud(appContext, forceServer = false)
            }
        }

        // Live Resources Listener (Kütüphane)
        runCatching {
            resourcesListener = fs.collection("resources")
                .whereEqualTo("studentId", uid)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w("ResourceSync", "Kaynak dinleme hatası (offline): ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val goalsDao = dao ?: return@launch
                            snapshots.documents.forEach { doc ->
                                runCatching {
                                    val existingByCloud = goalsDao.getResourceByCloudId(doc.id)
                                    val resolvedId = existingByCloud?.id
                                        ?: doc.getLong("localId")
                                        ?: doc.getLong("id")
                                        ?: doc.id.removePrefix("res_").removePrefix("${uid}_").toLongOrNull()
                                        ?: abs(doc.id.hashCode().toLong())
                                    val isDeleted = doc.getBoolean("isDeleted") ?: false
                                    if (isDeleted) {
                                        goalsDao.deleteResourcePermanently(resolvedId)
                                        return@runCatching
                                    }
                                    val existing = existingByCloud ?: goalsDao.getResourceById(resolvedId)
                                    if (existing != null && !existing.isSynced) {
                                        // Preserve pending local offline mutations until SyncWorker uploads them
                                        return@runCatching
                                    }
                                    val totalUnits = (doc.getLong("totalUnits")?.toInt()
                                        ?: doc.getLong("totalTarget")?.toInt()
                                        ?: 0).coerceAtLeast(0)
                                    val rawCompleted = (doc.getLong("completedUnits")?.toInt()
                                        ?: doc.getLong("currentUnits")?.toInt()
                                        ?: 0).coerceAtLeast(0)
                                    val completedUnits = if (totalUnits > 0) rawCompleted.coerceIn(0, totalUnits) else rawCompleted
                                    val remoteUpdatedMs = doc.getTimestamp("updatedAt")?.toDate()?.time
                                        ?: System.currentTimeMillis()
                                    val entity = ResourceEntity(
                                        id = resolvedId,
                                        title = doc.getString("title") ?: existing?.title ?: "",
                                        subject = doc.getString("subject") ?: existing?.subject ?: "Genel",
                                        totalUnits = totalUnits,
                                        completedUnits = completedUnits,
                                        unitType = doc.getString("unitType") ?: doc.getString("unit") ?: existing?.unitType ?: "Sayfa",
                                        updatedAtMillis = remoteUpdatedMs,
                                        colorHex = doc.getString("colorHex") ?: existing?.colorHex ?: "#0ea5e9",
                                        cloudId = doc.id,
                                        isSynced = true,
                                        lastModifiedMillis = remoteUpdatedMs,
                                        isAssignedByCoach = doc.getBoolean("isAssignedByCoach")
                                            ?: doc.getBoolean("isCoachAssigned")
                                            ?: (doc.getString("assignedBy") == "coach")
                                    )
                                    goalsDao.upsertResource(entity)
                                }
                            }
                            _isLoading.value = false
                        }
                    }
                }
        }.onFailure { e ->
            Log.w("ResourceSync", "Failed to attach resources listener: ${e.message}")
        }
    }

    // ── Direct Firestore Sync Helpers ──────────────────────────────────────────

    private suspend fun syncResourceToFirestore(resource: ResourceEntity): Boolean {
        val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid ?: return false
        val db = GoogleServicesHelper.getFirebaseFirestore(getApplication()) ?: return false
        val docId = resource.cloudId?.takeIf { it.isNotBlank() } ?: "${uid}_${resource.id}"
        val totalUnits = resource.totalUnits.coerceAtLeast(0)
        val clamped = if (totalUnits > 0) resource.completedUnits.coerceIn(0, totalUnits) else resource.completedUnits.coerceAtLeast(0)
        val isCompleted = totalUnits > 0 && clamped >= totalUnits

        val resourceData = hashMapOf<String, Any?>(
            "id"                to docId,
            "localId"           to resource.id,
            "title"             to resource.title,
            "subject"           to resource.subject,
            "unitType"          to resource.unitType,
            "currentUnits"      to clamped,
            "completedUnits"    to clamped,
            "isCompleted"       to isCompleted,
            "completed"         to isCompleted,
            "totalUnits"        to totalUnits,
            "totalTarget"       to totalUnits,
            "colorHex"          to (resource.colorHex ?: "#0ea5e9"),
            "studentId"         to uid,
            "userId"            to uid,
            "origin"            to "APP",
            "isDeleted"         to false,
            "isAssignedByCoach" to resource.isAssignedByCoach,
            "updatedAt"         to FieldValue.serverTimestamp()
        )

        return runCatching {
            db.collection("resources").document(docId).set(resourceData, SetOptions.merge()).await()
            runCatching {
                db.collection("students").document(uid).collection("resources").document(docId)
                    .set(resourceData, SetOptions.merge()).await()
            }
            dao?.updateResource(resource.copy(cloudId = docId, isSynced = true))
            true
        }.getOrElse { e ->
            Log.e("ResourceSync", "Güncelleme hatası: ${e.message}", e)
            false
        }
    }

    fun updateTopicGoal(topicGoal: TopicGoalEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = topicGoal.copy(
                isSynced = false,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao?.updateTopicGoal(updated)
            val context = getApplication<android.app.Application>()
            // Coalesce rapid mastery taps into 1 SINGLE Firestore document write (2.5s debounce)
            com.example.sharkmentor.data.sync.TopicCloudSyncManager.scheduleDebouncedPush(context)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(context)
        }
    }

    fun insertTopicGoal(subject: String, topic: String, targetLevel: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<android.app.Application>()
            val institutionId = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context).institutionId ?: "INDIVIDUAL"
            val now = System.currentTimeMillis()
            val entity = TopicGoalEntity(
                subject = subject,
                topic = topic,
                targetLevel = targetLevel,
                currentLevel = 1,
                isSynced = false,
                institutionId = institutionId,
                updatedAtMillis = now
            )
            dao?.insertTopicGoal(entity)
            com.example.sharkmentor.data.sync.TopicCloudSyncManager.scheduleDebouncedPush(context)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(context)
        }
    }

    fun deleteTopic(topic: TopicGoalEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<android.app.Application>()
            dao?.softDeleteTopicGoal(topic.id)
            com.example.sharkmentor.data.sync.TopicCloudSyncManager.scheduleDebouncedPush(context)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(context)
        }
    }

    fun deleteSubjectFolder(subjectName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<android.app.Application>()
            dao?.softDeleteBySubject(subjectName)
            com.example.sharkmentor.data.sync.TopicCloudSyncManager.scheduleDebouncedPush(context)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(context)
        }
    }

    fun updateResource(resource: ResourceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = resource.copy(
                isSynced = false,
                lastModifiedMillis = System.currentTimeMillis()
            )
            dao?.updateResource(updated)
            val synced = syncResourceToFirestore(updated)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            if (!synced) {
                SyncWorker.enqueueImmediateSync(getApplication())
            }
        }
    }

    fun incrementResource(resourceId: Long, step: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            val resource = dao?.getResourceById(resourceId)
                ?: _resources.value.find { it.id == resourceId }
                ?: return@launch
            val now = System.currentTimeMillis()
            val rawNext = (resource.completedUnits + step).coerceAtLeast(0)
            val nextUnits = if (resource.totalUnits > 0) rawNext.coerceIn(0, resource.totalUnits) else rawNext
            val updated = resource.copy(
                completedUnits = nextUnits,
                isSynced = false,
                updatedAtMillis = now,
                lastModifiedMillis = now
            )
            dao?.updateResource(updated)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            if (!syncResourceToFirestore(updated)) {
                SyncWorker.enqueueImmediateSync(getApplication())
            }
        }
    }

    fun decrementResource(resourceId: Long, step: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            val resource = dao?.getResourceById(resourceId)
                ?: _resources.value.find { it.id == resourceId }
                ?: return@launch
            val now = System.currentTimeMillis()
            val rawNext = (resource.completedUnits - step).coerceAtLeast(0)
            val nextUnits = if (resource.totalUnits > 0) rawNext.coerceIn(0, resource.totalUnits) else rawNext
            val updated = resource.copy(
                completedUnits = nextUnits,
                isSynced = false,
                updatedAtMillis = now,
                lastModifiedMillis = now
            )
            dao?.updateResource(updated)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            if (!syncResourceToFirestore(updated)) {
                SyncWorker.enqueueImmediateSync(getApplication())
            }
        }
    }

    fun setResourceUnits(resource: ResourceEntity, newUnits: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val rawTarget = newUnits.coerceAtLeast(0)
            val targetVal = if (resource.totalUnits > 0) rawTarget.coerceIn(0, resource.totalUnits) else rawTarget
            val updated = resource.copy(
                completedUnits = targetVal,
                isSynced = false,
                updatedAtMillis = now,
                lastModifiedMillis = now
            )
            dao?.updateResource(updated)
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            if (!syncResourceToFirestore(updated)) {
                SyncWorker.enqueueImmediateSync(getApplication())
            }
        }
    }

    fun deleteResource(resource: ResourceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao?.softDeleteResource(resource.id)
            val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
            val db = GoogleServicesHelper.getFirebaseFirestore(getApplication())
            val docId = resource.cloudId?.takeIf { it.isNotBlank() }
                ?: if (!uid.isNullOrBlank()) "${uid}_${resource.id}" else resource.id.toString()

            var deletedRemote = false
            if (db != null && docId.isNotBlank()) {
                val deletePayload = mapOf("isDeleted" to true, "updatedAt" to FieldValue.serverTimestamp())
                deletedRemote = runCatching {
                    db.collection("resources").document(docId).set(deletePayload, SetOptions.merge()).await()
                    if (!uid.isNullOrBlank()) {
                        runCatching {
                            db.collection("students").document(uid).collection("resources").document(docId)
                                .set(deletePayload, SetOptions.merge()).await()
                        }
                    }
                    Log.d("ResourceSync", "Kaynak Firestore'da silindi olarak işaretlendi: $docId")
                    true
                }.getOrElse { e ->
                    Log.e("ResourceSync", "Silme hatası: ${e.message}", e)
                    false
                }
            }
            if (deletedRemote) {
                dao?.deleteResourcePermanently(resource.id)
            } else {
                SyncWorker.enqueueImmediateSync(getApplication())
            }
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
        }
    }

    fun insertResource(title: String, subject: String, totalUnits: Int, unitType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<android.app.Application>()
            val institutionId = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context).institutionId ?: "INDIVIDUAL"
            val entity = ResourceEntity(
                title = title,
                subject = subject,
                totalUnits = totalUnits,
                unitType = unitType,
                institutionId = institutionId,
                isSynced = false
            )
            val insertedId = dao?.insertResource(entity) ?: 0L
            val entityWithId = if (insertedId > 0L) entity.copy(id = insertedId) else entity
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(context)
            if (insertedId > 0L) {
                if (!syncResourceToFirestore(entityWithId)) {
                    SyncWorker.enqueueImmediateSync(context)
                }
            } else {
                SyncWorker.enqueueImmediateSync(context)
            }
        }
    }

    fun extractTopicsFromDocument(context: android.content.Context, documentUri: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isScanning.value = true
            _scanError.value = null
            _lastScannedUri.value = documentUri

            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                aiService.extractTopicsFromDocument(documentUri)
            }

            result.onSuccess { topicPairs ->
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val institutionId = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context).institutionId ?: "INDIVIDUAL"
                    val now = System.currentTimeMillis()
                    val entities = topicPairs.map { (subject, topic) ->
                        TopicGoalEntity(
                            subject = subject,
                            topic = topic,
                            currentLevel = 1,
                            targetLevel = 5,
                            isSynced = false,
                            institutionId = institutionId,
                            updatedAtMillis = now
                        )
                    }
                    dao?.insertTopicGoals(entities)
                    val pushed = com.example.sharkmentor.data.sync.TopicCloudSyncManager
                        .pushSnapshotNow(context)
                    com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(context)
                    if (!pushed) {
                        SyncWorker.enqueueImmediateSync(context)
                    }
                }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "${topicPairs.size} adet konu başarıyla eklendi.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { error ->
                _scanError.value = error.localizedMessage ?: "Bilinmeyen hata"
            }
            _isScanning.value = false
        }
    }

    fun saveTarget(
        examType: String, 
        targetTitle: String,
        targetRanking: Int? = null,
        targetUniversity: String? = null,
        targetDepartment: String? = null
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isAnalyzingTarget.value = true
            
            val aiResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
                aiService.analyzeConcreteTarget(examType, targetTitle, "")
            }
            
            val analysisSummary = aiResult.getOrNull() ?: "AI analizi şu an kullanılamıyor. Hedefiniz kaydedildi."
            
            val finalTargetScoreOrRank = if (targetRanking != null && targetRanking > 0) {
                "$targetRanking Sıralama"
            } else {
                "Hedef Belirlenmedi"
            }
            
            val entity = ConcreteTargetEntity(
                examType = examType,
                targetTitle = targetTitle,
                targetScoreOrRank = finalTargetScoreOrRank,
                aiAnalysisSummary = analysisSummary,
                targetRanking = targetRanking,
                targetUniversity = targetUniversity,
                targetDepartment = targetDepartment
            )
            
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                targetDao?.saveTarget(entity)
                
                val db = GoogleServicesHelper.getFirebaseFirestore(getApplication())
                val uid = GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid
                if (uid != null && db != null) {
                    val targetData = hashMapOf<String, Any?>()
                    targetData["targetRanking"] = if (targetRanking != null && targetRanking > 0) targetRanking else null
                    targetData["targetUniversity"] = targetUniversity
                    targetData["targetDepartment"] = targetDepartment
                    targetData["targetTitle"] = targetTitle
                    targetData["targetExamType"] = examType
                    targetData["targetAnalysisSummary"] = analysisSummary
                    
                    runCatching {
                        db.collection("students").document(uid).set(targetData, SetOptions.merge()).await()
                    }
                    runCatching {
                        db.collection("users").document(uid).set(targetData, SetOptions.merge()).await()
                    }
                }
                com.example.sharkmentor.core.coaching.CoachingEngine.refreshTargetAndLedgerAsync(getApplication())
                com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            }
            
            _isAnalyzingTarget.value = false
        }
    }

    fun deleteTarget() {
        viewModelScope.launch(Dispatchers.IO) {
            targetDao?.clearTarget()
            com.example.sharkmentor.core.coaching.CoachingEngine.refreshTargetAndLedgerAsync(getApplication())
            com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
        }
    }

    fun reanalyzeConcreteTarget() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTarget = targetDao?.getTargetRaw() ?: return@launch
            _isAnalyzingTarget.value = true

            val result = aiService.analyzeConcreteTarget(
                examType = currentTarget.examType,
                targetName = currentTarget.targetTitle,
                targetDetail = currentTarget.targetScoreOrRank
            )

            result.onSuccess { summary ->
                targetDao?.saveTarget(
                    currentTarget.copy(
                        aiAnalysisSummary = summary,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
                com.example.sharkmentor.core.coaching.CoachingEngine.refreshTargetAndLedgerAsync(getApplication())
                com.example.sharkmentor.core.coaching.StudentDossierManager.syncInBackground(getApplication())
            }.onFailure {
                // Keep existing target data, clear analysis error flag
            }

            _isAnalyzingTarget.value = false
        }
    }

    override fun onCleared() {
        com.example.sharkmentor.data.sync.TopicCloudSyncManager.flushPendingInBackground(getApplication())
        authStateListener?.let { listener ->
            runCatching { GoogleServicesHelper.getFirebaseAuth(getApplication())?.removeAuthStateListener(listener) }
        }
        authStateListener = null
        detachCloudSyncListeners()
        super.onCleared()
    }
}

