package com.example.sharkmentor.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.SharkMentorApplication
import com.example.sharkmentor.data.local.entity.TargetEntity
import com.example.sharkmentor.data.local.entity.TargetType
import com.example.sharkmentor.data.repository.PlanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.example.sharkmentor.core.util.GoogleServicesHelper

@Immutable
data class MainUiState(
    val targetCountdownText: String = "Hesaplanıyor...",
    val isCampus: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var studentBrandingListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var observedUid: String? = null
    private var fallbackCheckedForUid: String? = null
    private var firebaseAuthRef: com.google.firebase.auth.FirebaseAuth? = null
    private val authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
        val uid = auth.currentUser?.takeUnless { it.isAnonymous }?.uid
        attachStudentListenerIfNeeded(uid)
    }

    private val countdownRepo = com.example.sharkmentor.data.repository.CountdownRepository.getInstance(application)
    private val streakRepo = com.example.sharkmentor.data.repository.StreakRepository(application)
    private val prefs = application.getSharedPreferences("sharkmentor_prefs", Context.MODE_PRIVATE)
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "target_date_millis" || key == "target_exam_title") {
            refreshCountdownText()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        refreshCountdownText()

        viewModelScope.launch {
            countdownRepo.stateFlow.collect {
                refreshCountdownText()
            }
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                delay(60_000L) // Update every minute
                refreshCountdownText()
            }
        }

        // Defer Firebase Auth, Firestore listeners & notification setup off Main Thread (zero cold-start ANR on low-RAM devices)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                com.example.sharkmentor.core.notification.SharkNotificationEngine.init(application)
                val auth = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance() }.getOrNull()
                    ?: GoogleServicesHelper.getFirebaseAuth(application)
                firebaseAuthRef = auth
                runCatching { auth?.addAuthStateListener(authStateListener) }

                val currentUser = auth?.currentUser
                    ?: GoogleServicesHelper.getCurrentUser(auth, application)
                attachStudentListenerIfNeeded(currentUser?.takeUnless { it.isAnonymous }?.uid)
            }
        }
    }

    private fun attachStudentListenerIfNeeded(uid: String?) {
        if (uid == observedUid) return
        studentBrandingListener?.remove()
        studentBrandingListener = null
        observedUid = uid
        if (uid.isNullOrBlank()) {
            fallbackCheckedForUid = null
            _uiState.value = _uiState.value.copy(isCampus = false)
            return
        }

        val appContext = getApplication<Application>()
        val userPrefs = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(appContext)
        com.example.sharkmentor.core.notification.SocialNotificationMonitor.start(appContext)
        val db = GoogleServicesHelper.getFirebaseFirestore(appContext) ?: return

        runCatching {
            studentBrandingListener = db.collection("students").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Task 6.4: Silently ignore PERMISSION_DENIED or offline errors
                        return@addSnapshotListener
                    }
                    if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val coachId = snapshot.getString("coachId")
                    val instId = snapshot.getString("institutionId")
                    val userMode = snapshot.getString("userMode")
                    val hasInstitution = !coachId.isNullOrBlank() || !instId.isNullOrBlank() || userMode == "campus"
                    _uiState.value = _uiState.value.copy(isCampus = hasInstitution)

                    val nameInDb = listOf("name", "displayName", "fullName")
                        .firstNotNullOfOrNull { field ->
                            snapshot.getString(field)?.trim()?.takeIf { it.length >= 2 }
                        }
                    val gradeInDb = listOf("grade", "gradeLevel", "sinif")
                        .firstNotNullOfOrNull { field ->
                            snapshot.getString(field)?.trim()?.takeIf { it.isNotBlank() }
                        }
                    val examInDb = listOf("targetExam", "targetExamTitle")
                        .firstNotNullOfOrNull { field ->
                            snapshot.getString(field)?.trim()?.takeIf { it.isNotBlank() }
                        }

                    userPrefs.updateProfileDetails(
                        name = nameInDb,
                        grade = gradeInDb,
                        targetExam = examInDb
                    )

                    // Real-time two-way LWW cloud sync for Exam Countdown ("Geri Sayım Aracı")
                    if (countdownRepo.applyRemoteSnapshot(snapshot)) {
                        refreshCountdownText()
                    }

                    // Real-time two-way cloud sync for Water Streak ("Su Serisi")
                    if (streakRepo.applyRemoteSnapshot(snapshot)) {
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching {
                                com.example.sharkmentor.core.widget.WidgetUpdateManager.updateAllWidgets(appContext)
                            }
                        }
                    }

                    // Live sync profile photo / preset avatar for Side Panel & Profile
                    val hasAvatarFields = snapshot.contains("avatarBase64") || snapshot.contains("avatarPresetId")
                    if (hasAvatarFields) {
                        val remoteBase64 = snapshot.getString("avatarBase64").orEmpty().trim()
                        val remotePreset = if (remoteBase64.isNotBlank()) "" else snapshot.getString("avatarPresetId").orEmpty().trim()
                        if (remoteBase64.isNotBlank() || remotePreset.isNotBlank() || !snapshot.metadata.isFromCache) {
                            com.example.sharkmentor.core.util.AvatarHelper.saveLocalAvatar(
                                context = appContext,
                                base64 = remoteBase64,
                                presetId = remotePreset
                            )
                        }
                    }

                    // One-shot full reconciliation & legacy users/{uid} migration for this session
                    if (fallbackCheckedForUid != uid) {
                        fallbackCheckedForUid = uid
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching {
                                countdownRepo.pullAndMergeFromCloud(prefetchedStudentDoc = snapshot)
                            }
                            runCatching {
                                streakRepo.pullAndMergeFromCloud(prefetchedStudentDoc = snapshot)
                            }
                        }
                        if (gradeInDb.isNullOrBlank() || examInDb.isNullOrBlank()) {
                            db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
                                if (userDoc != null && userDoc.exists()) {
                                    val fallbackGrade = gradeInDb ?: listOf("grade", "gradeLevel", "sinif")
                                        .firstNotNullOfOrNull { f -> userDoc.getString(f)?.trim()?.takeIf { it.isNotBlank() } }
                                    val fallbackExam = examInDb ?: listOf("targetExam", "targetExamTitle")
                                        .firstNotNullOfOrNull { f -> userDoc.getString(f)?.trim()?.takeIf { it.isNotBlank() } }
                                    userPrefs.updateProfileDetails(
                                        grade = fallbackGrade,
                                        targetExam = fallbackExam
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun refreshCountdownText() {
        val state = countdownRepo.stateFlow.value
        val rawTitle = state.title.trim()
            .ifBlank { prefs.getString("target_exam_title", null)?.trim().orEmpty() }
            .ifBlank {
                com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(getApplication())
                    .studentTargetExam?.trim().orEmpty()
            }
        val examTitle = if (rawTitle.isBlank()) "YKS" else rawTitle
        val targetMillis = if (state.targetDateMillis > 0L) {
            state.targetDateMillis
        } else {
            prefs.getLong("target_date_millis", 0L)
        }
        if (targetMillis <= 0L) {
            _uiState.value = _uiState.value.copy(targetCountdownText = "")
            return
        }

        val diffMillis = targetMillis - System.currentTimeMillis()
        val formatted = if (diffMillis <= 0) {
            "$examTitle: Süre Doldu"
        } else {
            val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
            if (days > 0) {
                "$examTitle • ${days} Gün"
            } else {
                val hours = TimeUnit.MILLISECONDS.toHours(diffMillis).coerceAtLeast(1)
                "$examTitle • ${hours} Saat"
            }
        }
        _uiState.value = _uiState.value.copy(targetCountdownText = formatted)
    }

    override fun onCleared() {
        runCatching { firebaseAuthRef?.removeAuthStateListener(authStateListener) }
        studentBrandingListener?.remove()
        studentBrandingListener = null
        super.onCleared()
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefsListener) }
    }

    fun saveDailyReflection(score: Int, note: String, monthlyNet: String? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.example.sharkmentor.data.local.SharkMentorDatabase.getDatabase(getApplication())
            val dao = db.dailyReflectionDao()
            val cal = java.util.Calendar.getInstance()
            val dateStr = String.format("%04d-%02d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
            
            dao.insertReflection(
                com.example.sharkmentor.data.local.entity.DailyReflectionEntity(
                    dateString = dateStr,
                    productivityScore = score,
                    reflectionNote = note
                )
            )

            if (!monthlyNet.isNullOrBlank()) {
                val monthStr = String.format("%04d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
                db.monthlyExamNetDao().insertNet(
                    com.example.sharkmentor.data.local.entity.MonthlyExamNetEntity(
                        yearMonth = monthStr,
                        examType = "Genel",
                        netDetailsJson = monthlyNet,
                        notes = "Daily Reflection Auto-Save"
                    )
                )
            }
        }
    }
}
