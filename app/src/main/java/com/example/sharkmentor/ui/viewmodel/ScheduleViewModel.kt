package com.example.sharkmentor.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.InstitutionScheduleEntity
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.data.remote.ScheduleScannerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed class ScheduleUiState {
    object Loading : ScheduleUiState()
    data class Success(
        val isReadOnlyMode: Boolean,
        val schedules: List<InstitutionScheduleEntity>,
        val webScheduleText: String? = null,
        /** True when schedules were synced from a coach-assigned customScheduleData block */
        val isCoachSchedule: Boolean = false
    ) : ScheduleUiState()
    data class Error(val message: String) : ScheduleUiState()
}

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val scheduleDao = SharkMentorDatabase.getDatabase(application).institutionScheduleDao()
    private val prefs = UserPreferences.getInstance(application)
    private val scannerEngine = ScheduleScannerEngine()
    private val institutionSyncListener = com.example.sharkmentor.data.sync.InstitutionScheduleSyncListener(scheduleDao)

    private val _weeklySchedule = MutableStateFlow<WeeklyScheduleData?>(null)
    val weeklySchedule: StateFlow<WeeklyScheduleData?> = _weeklySchedule.asStateFlow()

    private var institutionScheduleListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        institutionSyncListener.start()
        loadSchedules()
    }

    override fun onCleared() {
        institutionScheduleListener?.remove()
        institutionScheduleListener = null
        institutionSyncListener.stop()
        super.onCleared()
    }

    private fun loadSchedules() {
        viewModelScope.launch {
            try {
                var isReadOnly = !prefs.isIndividual
                var instId: String? = null
                var webText: String? = null
                var hasCoachSchedule = false

                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    ?: com.example.sharkmentor.core.util.GoogleServicesHelper.getCurrentUser()
                val uid = currentUser?.uid
                val db = com.example.sharkmentor.core.util.GoogleServicesHelper.getFirebaseFirestore()
                if (currentUser != null && uid != null && db != null) {
                    try {
                        val doc = db.collection("students").document(uid).get().await()
                        val coachId = doc.getString("coachId")
                        val isCorporate = doc.getBoolean("isCorporate") ?: false
                        @Suppress("UNCHECKED_CAST")
                        val scheduleMap = doc.get("customScheduleData") as? Map<String, Any>
                        _weeklySchedule.value = scheduleMap?.let { parseScheduleFromMap(it) }
                        val hasCustomSchedule = scheduleMap != null
                        if (!coachId.isNullOrBlank() || isCorporate) {
                            if (hasCustomSchedule) {
                                isReadOnly = false
                                hasCoachSchedule = true
                                if (scheduleDao.getAllSchedules().none { it.id.startsWith("CUSTOM_SCHED_") }) {
                                    com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(getApplication())
                                }
                            } else {
                                isReadOnly = true
                                if (!coachId.isNullOrBlank()) {
                                    val coachDoc = db.collection("coaches").document(coachId).get().await()
                                    instId = coachDoc.getString("institutionId")
                                }
                            }
                        } else if (doc.exists()) {
                            isReadOnly = false
                        }
                    } catch (e: Exception) {
                        // Use default fallback
                    }
                }

                if (currentUser != null && instId != null && db != null) {
                    institutionScheduleListener?.remove()
                    runCatching {
                        institutionScheduleListener = db.collection("institutions").document(instId).addSnapshotListener { snap, err ->
                            if (err != null) return@addSnapshotListener
                            if (snap != null && snap.exists()) {
                                val text = snap.getString("weeklySchedule")
                                val currentState = _uiState.value
                                if (currentState is ScheduleUiState.Success) {
                                    _uiState.value = currentState.copy(webScheduleText = text)
                                } else {
                                    webText = text
                                }
                            }
                        }
                    }
                }

                scheduleDao.getAllSchedulesFlow().collect { schedules ->
                    val currentWebText = (_uiState.value as? ScheduleUiState.Success)?.webScheduleText ?: webText
                    val dynamicCoachSchedule = hasCoachSchedule || schedules.any { it.id.startsWith("CUSTOM_SCHED_") }
                    _uiState.value = ScheduleUiState.Success(
                        isReadOnlyMode = isReadOnly,
                        schedules = schedules,
                        isCoachSchedule = dynamicCoachSchedule,
                        webScheduleText = currentWebText
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ScheduleUiState.Error(e.localizedMessage ?: "Bilinmeyen hata")
            }
        }
    }

    private suspend fun markRemoteSchedulesDeleted(items: List<InstitutionScheduleEntity>) {
        val removable = items.filter { !it.id.startsWith("CUSTOM_SCHED_") }
        if (removable.isEmpty()) return
        val uid = com.example.sharkmentor.core.util.GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid ?: return
        val db = com.example.sharkmentor.core.util.GoogleServicesHelper.getFirebaseFirestore(getApplication()) ?: return
        val col = db.collection("users").document(uid).collection("institution_schedules")
        val now = System.currentTimeMillis()
        removable.forEach { item ->
            val docId = item.cloudId?.takeIf { it.isNotBlank() } ?: item.id
            runCatching {
                col.document(docId).set(
                    mapOf("isDeleted" to true, "lastModifiedMillis" to now),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }
        }
    }

    fun scanAndSaveSchedule(uri: Uri) {
        val previousState = _uiState.value
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            val result = scannerEngine.scanSchedule(getApplication(), uri)
            result.onSuccess { scannedTasks ->
                val oldNonCustom = scheduleDao.getAllSchedules().filter { !it.id.startsWith("CUSTOM_SCHED_") }
                val now = System.currentTimeMillis()
                val entities = scannedTasks.map { task ->
                    val times = task.scheduledTime.split("-").map { it.trim() }
                    val startTime = times.getOrNull(0) ?: "08:00"
                    val endTime = times.getOrNull(1) ?: "09:00"
                    val stableId = UUID.randomUUID().toString()

                    InstitutionScheduleEntity(
                        id = stableId,
                        dayOfWeek = task.dayOfWeek,
                        startTime = startTime,
                        endTime = endTime,
                        subject = task.subject,
                        type = if (prefs.isIndividual) "INDIVIDUAL" else "DERSHANE",
                        teacherName = task.teacherName,
                        period = task.period,
                        lastModifiedMillis = now,
                        cloudId = stableId
                    )
                }
                // Clear old individual schedules without touching coach-assigned custom schedules
                scheduleDao.replaceIndividualSchedules(entities)
                markRemoteSchedulesDeleted(oldNonCustom)
                com.example.sharkmentor.service.sync.SyncWorker.enqueueImmediateSync(getApplication())

            }.onFailure {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        getApplication(),
                        it.localizedMessage ?: "Tarama başarısız oldu.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                if (previousState is ScheduleUiState.Success) {
                    _uiState.value = previousState
                } else {
                    _uiState.value = ScheduleUiState.Error(it.localizedMessage ?: "Tarama başarısız oldu.")
                }
            }
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            try {
                val target = scheduleDao.getById(id)
                scheduleDao.deleteById(id)
                if (target != null) {
                    markRemoteSchedulesDeleted(listOf(target))
                }
            } catch (e: Exception) {
                // Ignore or handle
            }
        }
    }

    fun deleteSchedulesByDay(day: Int) {
        viewModelScope.launch {
            try {
                val dayItems = scheduleDao.getSchedulesForDay(day)
                scheduleDao.deleteByDay(day)
                markRemoteSchedulesDeleted(dayItems)
            } catch (e: Exception) {
                // Ignore or handle
            }
        }
    }

    fun clearAllIndividualSchedules() {
        viewModelScope.launch {
            try {
                val nonCustom = scheduleDao.getAllSchedules().filter { !it.id.startsWith("CUSTOM_SCHED_") }
                scheduleDao.deleteNonCustomSchedules()
                markRemoteSchedulesDeleted(nonCustom)
            } catch (e: Exception) {
                // Ignore or handle
            }
        }
    }
}

data class WeeklyScheduleData(
    val weeklyGoal: String?,
    val totalTargetQuestions: Int?,
    val days: List<ScheduleDayData>
)

data class ScheduleDayData(
    val day: String,
    val blocks: List<ScheduleBlockData>
)

data class ScheduleBlockData(
    val time: String,
    val subject: String,
    val topic: String?,
    val targetQuestions: Int?
)

fun parseScheduleFromMap(map: Map<String, Any>): WeeklyScheduleData {
    val goal = map["weeklyGoal"] as? String
    val totalQ = (map["totalTargetQuestions"] as? Number)?.toInt()
    
    val daysList = map["days"] as? List<Map<String, Any>> ?: emptyList()
    val parsedDays = daysList.map { dayMap ->
        val dayName = dayMap["day"] as? String ?: ""
        val blocksList = dayMap["blocks"] as? List<Map<String, Any>> ?: emptyList()
        val parsedBlocks = blocksList.map { blockMap ->
            ScheduleBlockData(
                time = blockMap["time"] as? String ?: "",
                subject = blockMap["subject"] as? String ?: "",
                topic = blockMap["topic"] as? String,
                targetQuestions = (blockMap["targetQuestions"] as? Number)?.toInt()
            )
        }
        ScheduleDayData(day = dayName, blocks = parsedBlocks)
    }
    
    return WeeklyScheduleData(weeklyGoal = goal, totalTargetQuestions = totalQ, days = parsedDays)
}
