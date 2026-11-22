package com.example.sharkmentor.service.sync

import android.content.Context
import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.InstitutionScheduleEntity
import com.example.sharkmentor.data.local.entity.MonthlyExamNetEntity
import com.example.sharkmentor.data.local.entity.NetProjectionEntity
import com.example.sharkmentor.data.local.entity.TargetEntity
import com.example.sharkmentor.data.local.entity.TargetPeriod
import com.example.sharkmentor.data.local.entity.TargetType
import com.example.sharkmentor.data.local.entity.TrainingFolderEntity
import com.example.sharkmentor.data.local.entity.TrainingQuestionEntity
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Real-time listener for the user's personal data.
 * Listens to institution_schedules, goals, ai_simulations, countdown_settings, training_folders, training_questions.
 */
class UserDataSyncListener(private val context: Context) {
    companion object {
        private const val TAG = "UserDataSyncListener"
    }

    private val db = SharkMentorDatabase.getDatabase(context)
    private val firestore: FirebaseFirestore?
        get() = GoogleServicesHelper.getFirebaseFirestore(context)

    private val currentUid: String?
        get() = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: GoogleServicesHelper.getFirebaseAuth(context)?.currentUser?.uid
        } catch (e: Exception) {
            null
        }

    private val listeners = mutableListOf<ListenerRegistration>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startListening() {
        stopListening() // ensure clean state
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
            Log.i(TAG, "User not authenticated – operating strictly in offline/Room-only mode.")
            return
        }
        val uid = currentUid
        if (uid.isNullOrBlank()) {
            Log.i(TAG, "UID unavailable – operating strictly in offline/Room-only mode.")
            return
        }
        val fs = firestore ?: return

        // 1. Institution Schedules
        val schedulesRef = fs.collection("users/$uid/institution_schedules").limit(150)
        listeners.add(schedulesRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) return@addSnapshotListener
                Log.w(TAG, "Listen failed for schedules: ${e.message}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            if (snapshot.metadata.isFromCache && snapshot.documents.isEmpty()) return@addSnapshotListener
            scope.launch {
                val scheduleDao = db.institutionScheduleDao()
                snapshot.documents.forEach { doc ->
                    try {
                        if (doc.getSafeBoolean("isDeleted") == true) {
                            scheduleDao.deleteById(doc.id)
                            return@forEach
                        }
                        val existing = scheduleDao.getById(doc.id)
                        if (existing != null && !existing.isSynced) {
                            return@forEach
                        }
                        val subject = doc.getSafeString("subject")?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
                        val entity = InstitutionScheduleEntity(
                            id = doc.id,
                            dayOfWeek = (doc.getSafeInt("dayOfWeek") ?: 1).coerceIn(1, 7),
                            startTime = doc.getSafeString("startTime")?.trim()?.takeIf { it.isNotBlank() } ?: "09:00",
                            endTime = doc.getSafeString("endTime")?.trim()?.takeIf { it.isNotBlank() } ?: "10:00",
                            subject = subject,
                            type = doc.getSafeString("type")?.trim()?.takeIf { it.isNotBlank() } ?: "DERSHANE",
                            teacherName = doc.getSafeString("teacherName") ?: "",
                            period = doc.getSafeInt("period") ?: doc.getSafeString("period")?.toIntOrNull() ?: 0,
                            isSynced = true,
                            isDeleted = false,
                            lastModifiedMillis = doc.getSafeLong("lastModifiedMillis") ?: System.currentTimeMillis(),
                            cloudId = doc.id
                        )
                        scheduleDao.insertAll(listOf(entity))
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error parsing schedule doc ${doc.id}", ex)
                    }
                }
            }
        })

        // 2. Goals
        val userPrefs = UserPreferences.getInstance(context)
        val defaultInstitution = userPrefs.institutionId.takeIf { !it.isNullOrBlank() } ?: "INDIVIDUAL"
        val goalsPath = if (defaultInstitution == "INDIVIDUAL") "students/$uid/goals" else "institutions/$defaultInstitution/students/$uid/goals"
        
        val goalsRef = fs.collection(goalsPath).limit(150)
        listeners.add(goalsRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) return@addSnapshotListener
                Log.w(TAG, "Listen failed for goals: ${e.message}")
                return@addSnapshotListener
            }
            scope.launch {
                snapshot?.documents?.forEach { doc ->
                    try {
                        val isDeleted = doc.getSafeBoolean("isDeleted") ?: false
                        val remoteId = doc.getSafeLong("localId") 
                            ?: doc.getSafeLong("id") 
                            ?: doc.id.toLongOrNull() 
                            ?: abs(doc.id.hashCode().toLong())

                        if (isDeleted) {
                            db.targetDao().purgeTarget(remoteId)
                            return@forEach
                        }

                        val remoteModified = doc.getSafeLong("lastModifiedMillis") ?: System.currentTimeMillis()
                        val existing = db.targetDao().getTargetById(remoteId)
                        if (existing != null && (!existing.isSynced || existing.lastModifiedMillis > remoteModified)) {
                            return@forEach
                        }

                        val entity = TargetEntity(
                            id = remoteId,
                            title = doc.getSafeString("title") ?: "",
                            description = doc.getSafeString("description") ?: "",
                            type = try { TargetType.valueOf(doc.getSafeString("type") ?: "SHORT_TERM") } catch(e:Exception){TargetType.SHORT_TERM},
                            period = try { TargetPeriod.valueOf(doc.getSafeString("period") ?: "WEEKLY") } catch(e:Exception){TargetPeriod.WEEKLY},
                            deadlineMillis = doc.getSafeLong("deadlineMillis") ?: System.currentTimeMillis(),
                            progress = (doc.getSafeInt("progress") ?: 0).toFloat(),
                            isCompleted = doc.getSafeBoolean("isCompleted") ?: false,
                            createdAt = doc.getSafeLong("createdAt") ?: doc.getSafeString("createdAt")?.toLongOrNull() ?: System.currentTimeMillis(),
                            isSynced = true,
                            isDeleted = false,
                            lastModifiedMillis = remoteModified,
                            cloudId = doc.id
                        )
                        db.targetDao().upsertTarget(entity)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error parsing goal doc ${doc.id}", ex)
                    }
                }
            }
        })

        // 3. AI Simulations
        val simRef = fs.collection("users/$uid/ai_simulations").limit(100)
        listeners.add(simRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) return@addSnapshotListener
                Log.w(TAG, "Listen failed for simulations: ${e.message}")
                return@addSnapshotListener
            }
            scope.launch {
                snapshot?.documents?.forEach { doc ->
                    try {
                        val type = doc.getSafeString("type")
                        if (type == "NET_PROJECTION") {
                            val entity = NetProjectionEntity(
                                id = doc.id.hashCode(),
                                tytCurrentNet = (doc.getSafeDouble("tytCurrentNet") ?: 0.0).toFloat(),
                                tytProjectedNet = (doc.getSafeDouble("tytProjectedNet") ?: 0.0).toFloat(),
                                tytTargetNet = (doc.getSafeDouble("tytTargetNet") ?: 0.0).toFloat(),
                                tytNetGap = (doc.getSafeDouble("tytNetGap") ?: 0.0).toFloat(),
                                aytCurrentNet = (doc.getSafeDouble("aytCurrentNet") ?: 0.0).toFloat(),
                                aytProjectedNet = (doc.getSafeDouble("aytProjectedNet") ?: 0.0).toFloat(),
                                aytTargetNet = (doc.getSafeDouble("aytTargetNet") ?: 0.0).toFloat(),
                                aytNetGap = (doc.getSafeDouble("aytNetGap") ?: 0.0).toFloat(),
                                priorityTopicsJson = doc.getSafeString("priorityTopicsJson") ?: "[]",
                                strategicSummary = doc.getSafeString("strategicSummary") ?: "",
                                lastUpdatedTimestamp = doc.getSafeLong("lastUpdatedTimestamp") ?: System.currentTimeMillis(),
                                isSynced = true,
                                isDeleted = false,
                                cloudId = doc.id
                            )
                            db.netProjectionDao().saveProjection(entity)
                        } else if (type == "MONTHLY_EXAM") {
                            val entity = MonthlyExamNetEntity(
                                id = doc.id.hashCode().toLong(),
                                yearMonth = doc.getSafeString("yearMonth") ?: "",
                                examType = doc.getSafeString("examType") ?: "",
                                netDetailsJson = doc.getSafeString("netDetailsJson") ?: "{}",
                                notes = doc.getSafeString("notes") ?: "",
                                createdAtMillis = doc.getSafeLong("createdAtMillis") ?: System.currentTimeMillis(),
                                lastModifiedMillis = doc.getSafeLong("lastModifiedMillis") ?: System.currentTimeMillis(),
                                isSynced = true,
                                isDeleted = false,
                                cloudId = doc.id
                            )
                            db.monthlyExamNetDao().insertNet(entity)
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error parsing simulation doc ${doc.id}", ex)
                    }
                }
            }
        })

        // 4. Countdown Settings
        val countdownRef = fs.document("users/$uid/countdown_settings/config")
        listeners.add(countdownRef.addSnapshotListener { doc, e ->
            if (e != null) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) return@addSnapshotListener
                Log.w(TAG, "Listen failed for countdown settings: ${e.message}")
                return@addSnapshotListener
            }
            if (doc != null && doc.exists()) {
                try {
                    com.example.sharkmentor.data.repository.CountdownRepository
                        .getInstance(context)
                        .applyRemoteSnapshot(doc)
                } catch (ex: Exception) {
                    Log.e(TAG, "Error parsing countdown doc", ex)
                }
            }
        })

        // 5. Training Folders
        val foldersRef = fs.collection("users/$uid/training_folders").limit(100)
        listeners.add(foldersRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) return@addSnapshotListener
                Log.w(TAG, "Listen failed for training folders: ${e.message}")
                return@addSnapshotListener
            }
            scope.launch {
                val localById = db.trainingFolderDao().getAll().associateBy { it.id }
                snapshot?.documents?.forEach { doc ->
                    try {
                        val remoteDeleted = doc.getSafeBoolean("isDeleted") ?: false
                        val remoteModified = doc.getSafeLong("lastModifiedMillis") ?: System.currentTimeMillis()
                        val existing = localById[doc.id]
                        if (existing != null && (!existing.isSynced || existing.lastModifiedMillis > remoteModified)) {
                            return@forEach
                        }
                        val entity = TrainingFolderEntity(
                            id = doc.id,
                            title = doc.getSafeString("title") ?: existing?.title ?: "",
                            createdAt = doc.getSafeLong("createdAt") ?: existing?.createdAt ?: System.currentTimeMillis(),
                            isSynced = true,
                            isDeleted = remoteDeleted,
                            lastModifiedMillis = remoteModified,
                            cloudId = doc.id
                        )
                        db.trainingFolderDao().insertFolder(entity)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error parsing training folder doc ${doc.id}", ex)
                    }
                }
            }
        })

        // 6. Training Questions
        val questionsRef = fs.collection("users/$uid/training_questions").limit(150)
        listeners.add(questionsRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) return@addSnapshotListener
                Log.w(TAG, "Listen failed for training questions: ${e.message}")
                return@addSnapshotListener
            }
            scope.launch {
                val localById = db.trainingQuestionDao().getAllQuestions().associateBy { it.id }
                snapshot?.documents?.forEach { doc ->
                    try {
                        val remoteDeleted = doc.getSafeBoolean("isDeleted") ?: false
                        val remoteModified = doc.getSafeLong("lastModifiedMillis") ?: System.currentTimeMillis()
                        val existing = localById[doc.id]
                        if (existing != null && (!existing.isSynced || existing.lastModifiedMillis > remoteModified)) {
                            return@forEach
                        }
                        val entity = TrainingQuestionEntity(
                            id = doc.id,
                            folderId = doc.getSafeString("folderId") ?: existing?.folderId ?: "",
                            questionText = doc.getSafeString("questionText") ?: existing?.questionText ?: "",
                            imageUri = doc.getSafeString("imageUri") ?: existing?.imageUri,
                            correctAnswer = doc.getSafeString("correctAnswer") ?: existing?.correctAnswer ?: "",
                            userAnswer = doc.getSafeString("userAnswer") ?: existing?.userAnswer ?: "",
                            isSolved = doc.getSafeBoolean("isSolved") ?: existing?.isSolved ?: false,
                            reviewCount = doc.getSafeInt("reviewCount") ?: existing?.reviewCount ?: 0,
                            isSynced = true,
                            isDeleted = remoteDeleted,
                            lastModifiedMillis = remoteModified,
                            cloudId = doc.id
                        )
                        db.trainingQuestionDao().insertQuestion(entity)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error parsing training question doc ${doc.id}", ex)
                    }
                }
            }
        })

        // 7. Notes
        val notesRef = fs.collection("users/$uid/notes").limit(150)
        listeners.add(notesRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) return@addSnapshotListener
                Log.w(TAG, "Listen failed for notes: ${e.message}")
                return@addSnapshotListener
            }
            scope.launch {
                snapshot?.documents?.forEach { doc ->
                    try {
                        if (doc.getSafeBoolean("isDeleted") == true) {
                            db.noteDao().deleteNoteById(doc.id)
                            return@forEach
                        }
                        val remoteUpdatedAt = doc.getSafeLong("updatedAt")
                            ?: doc.getSafeLong("lastModifiedMillis")
                            ?: System.currentTimeMillis()
                        val existing = db.noteDao().getNoteById(doc.id)
                        if (existing != null && (!existing.isSynced || existing.updatedAt > remoteUpdatedAt)) {
                            return@forEach
                        }
                        val entity = NoteEntity(
                            id = doc.id,
                            title = doc.getSafeString("title") ?: "",
                            content = doc.getSafeString("content") ?: "",
                            colorHex = doc.getSafeString("colorHex") ?: "#F8FAFC",
                            isPinned = doc.getSafeBoolean("isPinned") ?: doc.getSafeBoolean("pinned") ?: false,
                            createdAt = doc.getSafeLong("createdAt") ?: existing?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = remoteUpdatedAt,
                            isSynced = true
                        )
                        db.noteDao().upsertNote(entity)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error parsing note doc ${doc.id}", ex)
                    }
                }
            }
        })
        
        Log.i(TAG, "Started listening to user data (schedules, goals, sims, countdown, training, notes).")
    }

    fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
        Log.i(TAG, "Stopped listening to user data.")
    }
}

// ── DocumentSnapshot Safety Extensions ──────────────────────────────────────────

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

private fun DocumentSnapshot.getSafeDouble(field: String): Double? {
    val value = get(field) ?: return null
    return (value as? Number)?.toDouble()
        ?: value.toString().toDoubleOrNull()
}
