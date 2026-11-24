package com.example.sharkmentor.ui.screens.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharkmentor.core.util.AvatarHelper
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.model.Meeting
import com.example.sharkmentor.data.repository.MeetingRepository
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.sharkmentor.worker.MeetingReminderWorker

data class ProfileAvatarState(
    val avatarBase64: String = "",
    val avatarPresetId: String = "",
    val isUpdating: Boolean = false
)

class ProfileViewModel @JvmOverloads constructor(
    application: Application,
    private val meetingRepository: MeetingRepository = MeetingRepository()
) : AndroidViewModel(application) {

    private val currentUid get() = runCatching {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }.getOrNull() ?: GoogleServicesHelper.getCurrentUser(context = getApplication())?.uid ?: ""

    private val _avatarState = MutableStateFlow(
        ProfileAvatarState(
            avatarBase64 = AvatarHelper.getSavedAvatarBase64(application),
            avatarPresetId = AvatarHelper.getSavedAvatarPresetId(application)
        )
    )
    val avatarState: StateFlow<ProfileAvatarState> = _avatarState.asStateFlow()

    private val _avatarMessage = MutableSharedFlow<String>()
    val avatarMessage = _avatarMessage.asSharedFlow()

    init {
        AvatarHelper.ensureLoaded(application)
        viewModelScope.launch {
            AvatarHelper.avatarState.collect { local ->
                _avatarState.value = _avatarState.value.copy(
                    avatarBase64 = local.avatarBase64,
                    avatarPresetId = local.avatarPresetId
                )
            }
        }
    }

    // Live list of student's meetings
    val studentMeetings: StateFlow<List<Meeting>> = callbackFlow {
        val currentUser = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }.getOrNull()
        val uid = currentUid
        if (currentUser == null || uid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val db = GoogleServicesHelper.getFirebaseFirestore(getApplication())
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = runCatching {
            db.collection("meetings")
                .whereEqualTo("studentId", uid)
                .limit(30)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snap?.documents?.mapNotNull { doc ->
                        doc.toObject(Meeting::class.java)?.copy(id = doc.id)
                    }?.sortedByDescending { it.appointmentTimestamp.seconds } ?: emptyList()
                    trySend(list)
                }
        }.getOrNull()
        awaitClose { registration?.remove() }
    }.catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _bookingMessage = MutableSharedFlow<String>()
    val bookingMessage = _bookingMessage.asSharedFlow()

    fun syncAvatarFromCloud(
        remoteBase64: String?,
        remotePresetId: String?,
        hasCloudFields: Boolean
    ) {
        if (!hasCloudFields) return
        val ctx = getApplication<Application>()
        val cleanBase64 = remoteBase64.orEmpty()
        val cleanPreset = if (cleanBase64.isNotBlank()) "" else remotePresetId.orEmpty()
        AvatarHelper.saveLocalAvatar(ctx, base64 = cleanBase64, presetId = cleanPreset)
        _avatarState.value = _avatarState.value.copy(
            avatarBase64 = cleanBase64,
            avatarPresetId = cleanPreset
        )
    }

    fun selectGalleryAvatar(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _avatarState.value = _avatarState.value.copy(isUpdating = true)
            val encoded = AvatarHelper.encodeUriToAvatarBase64(ctx, uri)
            if (!encoded.isNullOrBlank()) {
                AvatarHelper.saveLocalAvatar(ctx, base64 = encoded, presetId = "")
                _avatarState.value = ProfileAvatarState(
                    avatarBase64 = encoded,
                    avatarPresetId = "",
                    isUpdating = false
                )
                persistAvatarToFirestore(base64 = encoded, presetId = "")
                _avatarMessage.emit("Profil fotoğrafı güncellendi.")
            } else {
                _avatarState.value = _avatarState.value.copy(isUpdating = false)
                _avatarMessage.emit("Görsel işlenemedi, lütfen başka bir fotoğraf seçin.")
            }
        }
    }

    fun selectPresetAvatar(presetId: String) {
        val ctx = getApplication<Application>()
        AvatarHelper.saveLocalAvatar(ctx, base64 = "", presetId = presetId)
        _avatarState.value = ProfileAvatarState(
            avatarBase64 = "",
            avatarPresetId = presetId,
            isUpdating = false
        )
        persistAvatarToFirestore(base64 = "", presetId = presetId)
        viewModelScope.launch {
            _avatarMessage.emit("Profil avatarı güncellendi.")
        }
    }

    fun resetAvatar() {
        val ctx = getApplication<Application>()
        AvatarHelper.clearLocalAvatar(ctx)
        _avatarState.value = ProfileAvatarState(
            avatarBase64 = "",
            avatarPresetId = "",
            isUpdating = false
        )
        persistAvatarToFirestore(base64 = "", presetId = "")
        viewModelScope.launch {
            _avatarMessage.emit("Profil görseli varsayılana sıfırlandı.")
        }
    }

    private fun persistAvatarToFirestore(base64: String, presetId: String) {
        val ctx = getApplication<Application>()
        val uid = currentUid
        if (uid.isBlank() || uid == "local_student") return
        val db = GoogleServicesHelper.getFirebaseFirestore(ctx) ?: return
        val payload = mapOf(
            "avatarBase64" to base64,
            "avatarPresetId" to presetId
        )
        db.collection("students").document(uid).set(payload, SetOptions.merge())
        db.collection("institutions").document("GLOBAL_INDIVIDUAL")
            .collection("weeklyLeague").document(uid)
            .set(payload, SetOptions.merge())
    }

    fun updateUserMode(mode: String) {
        val ctx = getApplication<Application>()
        SessionManager.setUserMode(ctx, mode)
        val uid = currentUid
        if (uid.isNotEmpty()) {
            val db = GoogleServicesHelper.getFirebaseFirestore(ctx)
            db?.collection("students")?.document(uid)?.update("userMode", mode)
        }
    }

    fun unlinkCoach(onComplete: (Boolean, String?) -> Unit) {
        val ctx = getApplication<Application>()
        val uid = currentUid
        if (uid.isEmpty()) {
            onComplete(false, "Kullanıcı bulunamadı")
            return
        }
        SessionManager.setLinkedToInstitution(ctx, false)
        SessionManager.setUserMode(ctx, "study")
        
        val db = GoogleServicesHelper.getFirebaseFirestore(ctx)
        if (db != null) {
            db.collection("students").document(uid).update(
                mapOf(
                    "coachCode" to null,
                    "institutionCode" to null,
                    "coachId" to null,
                    "assignedCoachId" to null,
                    "assignedCoachName" to null,
                    "branchCode" to null,
                    "institutionName" to null,
                    "isInstitutional" to false,
                    "userMode" to "study"
                )
            ).addOnSuccessListener {
                onComplete(true, null)
            }.addOnFailureListener { e ->
                onComplete(false, e.localizedMessage)
            }
        } else {
            onComplete(false, "Veritabanına ulaşılamadı")
        }
    }

    fun bookMeeting(
        coachId: String,
        institutionId: String?,
        studentName: String,
        appointmentDate: Date,
        requestNote: String?
    ) {
        viewModelScope.launch {
            if (coachId.isBlank()) {
                _bookingMessage.emit("Randevu oluşturulamadı: Geçerli bir koç ID bulunamadı.")
                return@launch
            }
            val result = meetingRepository.createMeetingRequest(
                coachId = coachId,
                institutionId = institutionId,
                studentName = studentName,
                appointmentDate = appointmentDate,
                requestNote = requestNote
            )
            result.onSuccess {
                _bookingMessage.emit("Görüşme randevunuz otomatik olarak onaylandı ve koçunuza iletildi.")
                val dateStr = SimpleDateFormat("d MMMM yyyy, EEEE - HH:mm", Locale("tr")).format(appointmentDate)
                MeetingReminderWorker.scheduleReminder(
                    context = getApplication(),
                    appointmentTimestampMillis = appointmentDate.time,
                    studentName = studentName,
                    dateStr = dateStr
                )
            }.onFailure { err ->
                _bookingMessage.emit("Randevu oluşturulamadı: ${err.localizedMessage}")
            }
        }
    }
}
