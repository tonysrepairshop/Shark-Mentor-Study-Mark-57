package com.example.sharkmentor.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.dao.SyncDao
import com.example.sharkmentor.data.local.entity.ErrorQuestionEntity
import com.example.sharkmentor.data.local.entity.FocusSessionEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TargetEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Two-way sync repository for SharkMentor's offline-first architecture.
 *
 * Firestore document paths:
 * - Free student:       `students/{uid}/questions/{docId}`
 * - Institutional:      `institutions/{instId}/students/{uid}/questions/{docId}`
 *
 * Image compression:
 * - Local image URIs are compressed to WebP (quality 75) before uploading to
 *   Firebase Storage under `images/{uid}/{uuid}.webp`.
 * - The returned download URL replaces the local URI in the Firestore document.
 */
class CloudSyncRepository(private val context: Context) {

    companion object {
        private const val TAG = "CloudSyncRepo"
        private const val WEBP_QUALITY = 75
        private const val BATCH_SIZE = 50
    }

    private val db = SharkMentorDatabase.getDatabase(context)
    private val syncDao: SyncDao = db.syncDao()
    private val institutionScheduleDao = db.institutionScheduleDao()
    private val netProjectionDao = db.netProjectionDao()
    private val monthlyExamNetDao = db.monthlyExamNetDao()
    private val trainingFolderDao = db.trainingFolderDao()
    private val trainingQuestionDao = db.trainingQuestionDao()

    private val firestore: FirebaseFirestore?
        get() = GoogleServicesHelper.getFirebaseFirestore(context)

    private val storage: FirebaseStorage?
        get() = GoogleServicesHelper.getFirebaseStorage(context)

    private val auth: FirebaseAuth?
        get() = GoogleServicesHelper.getFirebaseAuth(context)

    // ── Public Sync Entry Points ─────────────────────────────────────────

    /**
     * Executes a full sync cycle: uploads all pending local changes.
     * Returns the total number of items successfully synced.
     */
    suspend fun syncAll(): Int {
        val currentUser = try {
            GoogleServicesHelper.getCurrentUser(auth, context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read auth current user: ${e.message}")
            null
        }
        val uid = currentUser?.uid

        if (uid.isNullOrBlank() || uid == "student_default") {
            Log.i(TAG, "syncAll: No authenticated user logged in. Skipping cloud sync.")
            return 0
        }

        val syncedCount = coroutineScope {
            val counts = listOf(
                async { syncPendingQuestions(uid) },
                async { syncPendingSessions(uid) },
                async { syncPendingTargets(uid) },
                async { syncPendingResources(uid) },
                async { syncPendingTopics(uid) },
                async { syncPendingInstitutionSchedules(uid) },
                async { syncPendingSimulations(uid) },
                async {
                    val folders = syncPendingTrainingFolders(uid)
                    val questions = syncPendingTrainingQuestions(uid)
                    folders + questions
                }
            ).awaitAll()
            counts.sum()
        }

        Log.i(TAG, "syncAll complete: $syncedCount items synced")
        return syncedCount
    }

    // ── Questions ────────────────────────────────────────────────────────

    private suspend fun syncPendingQuestions(uid: String): Int {
        val fs = firestore ?: return 0
        val pending = syncDao.getPendingQuestions(BATCH_SIZE)
        if (pending.isEmpty()) return 0

        val userPrefs = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context)
        val defaultInstId = userPrefs.institutionId.takeIf { !it.isNullOrBlank() } ?: "INDIVIDUAL"

        var synced = 0
        for (question in pending) {
            try {
                val resolvedInstId = question.institutionId.takeIf { !it.isNullOrBlank() } ?: defaultInstId
                
                val questionImageUrl = uploadImageIfLocal(uid, question.questionImageUri)
                val solutionImageUrl = question.solutionImageUri?.let { uploadImageIfLocal(uid, it) }

                val docPath = getCollectionPath(resolvedInstId, uid, "questions")
                val docData = buildQuestionDocument(question, questionImageUrl, solutionImageUrl, resolvedInstId)

                val docRef = if (question.cloudId != null) {
                    fs.document("$docPath/${question.cloudId}")
                } else {
                    fs.collection(docPath).document()
                }

                docRef.set(docData, SetOptions.merge()).await()
                syncDao.markQuestionSynced(question.id, docRef.id)
                synced++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync question id=${question.id}: ${e.message}")
                // Leave isSynced=0 — will be retried on next cycle
            }
        }
        return synced
    }

    private fun buildQuestionDocument(
        question: ErrorQuestionEntity,
        questionImageUrl: String,
        solutionImageUrl: String?,
        resolvedInstId: String
    ): Map<String, Any?> = mapOf(
        "localId" to question.id,
        "subject" to question.subject,
        "topic" to question.topic,
        "questionImageUrl" to questionImageUrl,
        "solutionImageUrl" to solutionImageUrl,
        "stability" to question.stability,
        "difficulty" to question.difficulty,
        "reps" to question.reps,
        "lapses" to question.lapses,
        "state" to question.state,
        "lastReviewMillis" to question.lastReviewMillis,
        "dueDateMillis" to question.dueDateMillis,
        "isTwinGenerated" to question.isTwinGenerated,
        "twinQuestionJson" to question.twinQuestionJson,
        "createdAtMillis" to question.createdAtMillis,
        "lastModifiedMillis" to System.currentTimeMillis(),
        "institutionId" to resolvedInstId,
        "assignedCoachId" to question.assignedCoachId
    )

    // ── Focus Sessions ───────────────────────────────────────────────────

    private suspend fun syncPendingSessions(uid: String): Int {
        val fs = firestore ?: return 0
        val pending = syncDao.getPendingSessions(BATCH_SIZE)
        if (pending.isEmpty()) return 0

        val userPrefs = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context)
        val defaultInstId = userPrefs.institutionId.takeIf { !it.isNullOrBlank() } ?: "INDIVIDUAL"

        val activeSessionUuid = com.example.sharkmentor.core.timer.FocusSessionRecorder.getActiveSessionId()
        var synced = 0
        for (session in pending) {
            if (!activeSessionUuid.isNullOrBlank() && session.cloudId == activeSessionUuid) {
                continue
            }
            try {
                val resolvedInstId = session.institutionId.takeIf { !it.isNullOrBlank() } ?: defaultInstId
                val docId = session.cloudId?.takeIf { it.isNotBlank() } ?: session.id.toString()
                val docData = buildSessionDocument(session, resolvedInstId)

                // Canonical primary session path (eliminates 3x duplicate writes per session)
                fs.collection("students").document(uid).collection("sessions").document(docId)
                    .set(docData, SetOptions.merge()).await()

                if (resolvedInstId != "INDIVIDUAL") {
                    runCatching {
                        val instPath = getCollectionPath(resolvedInstId, uid, "sessions")
                        fs.document("$instPath/$docId").set(docData, SetOptions.merge()).await()
                    }
                }

                syncDao.markSessionSynced(session.id, docId)
                synced++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync session id=${session.id}: ${e.message}")
            }
        }
        return synced
    }

    private fun buildSessionDocument(session: FocusSessionEntity, resolvedInstId: String): Map<String, Any?> {
        val totalSeconds = session.durationSeconds
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val formattedDuration = if (minutes > 0) "${minutes} dk ${seconds} sn" else "${seconds} sn"

        return mapOf(
            "localId" to session.id,
            "modeName" to session.modeName,
            "durationSeconds" to totalSeconds,
            "durationMinutes" to minutes,
            "durationFormatted" to formattedDuration,
            "completedAt" to session.completedAt,
            "afkScore" to session.afkScore,
            "phoneLiftCount" to session.phoneLiftCount,
            "screenOnMinutes" to session.screenOnMinutes,
            "lastModifiedMillis" to System.currentTimeMillis(),
            "institutionId" to resolvedInstId,
            "assignedCoachId" to session.assignedCoachId
        )
    }

    // ── Targets (Goals) ──────────────────────────────────────────────────

    private suspend fun syncPendingTargets(uid: String): Int {
        val fs = firestore ?: return 0
        val pending = syncDao.getPendingTargets(BATCH_SIZE)
        if (pending.isEmpty()) return 0

        val userPrefs = com.example.sharkmentor.data.local.preferences.UserPreferences.getInstance(context)
        val defaultInstId = userPrefs.institutionId.takeIf { !it.isNullOrBlank() } ?: "INDIVIDUAL"

        var synced = 0
        for (target in pending) {
            try {
                val resolvedInstId = target.institutionId.takeIf { !it.isNullOrBlank() } ?: defaultInstId
                
                val docPath = getCollectionPath(resolvedInstId, uid, "goals")
                val docData = buildTargetDocument(target, resolvedInstId)

                val docRef = if (target.cloudId != null) {
                    fs.document("$docPath/${target.cloudId}")
                } else {
                    fs.collection(docPath).document()
                }

                docRef.set(docData, SetOptions.merge()).await()
                syncDao.markTargetSynced(target.id, docRef.id)
                synced++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync target id=${target.id}: ${e.message}")
            }
        }
        return synced
    }

    private fun buildTargetDocument(target: TargetEntity, resolvedInstId: String): Map<String, Any?> = mapOf(
        "localId" to target.id,
        "title" to target.title,
        "description" to target.description,
        "type" to target.type.name,
        "period" to target.period.name,
        "deadlineMillis" to target.deadlineMillis,
        "progress" to target.progress,
        "isCompleted" to target.isCompleted,
        "createdAt" to target.createdAt,
        "lastModifiedMillis" to System.currentTimeMillis(),
        "institutionId" to resolvedInstId,
        "assignedCoachId" to target.assignedCoachId
    )

    // ── Resources (Kaynaklarım) ─────────────────────────────────────────

    private suspend fun syncPendingResources(uid: String): Int {
        val fs = firestore ?: return 0
        val pending = syncDao.getUnsyncedResources(BATCH_SIZE)
        if (pending.isEmpty()) return 0

        Log.d(TAG, "Uploading ${pending.size} pending resources to Firestore...")
        var synced = 0
        for (resource in pending) {
            try {
                val docId = resource.cloudId?.takeIf { it.isNotBlank() } ?: "${uid}_${resource.id}"
                val docData = buildResourceDocument(uid, resource, docId)

                fs.collection("resources").document(docId)
                    .set(docData, SetOptions.merge())
                    .await()
                runCatching {
                    fs.collection("students").document(uid).collection("resources").document(docId)
                        .set(docData, SetOptions.merge())
                        .await()
                }

                syncDao.markResourceSynced(resource.id, docId)
                synced++
                Log.d(TAG, "Synced resource id=${resource.id} -> resources/$docId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync resource id=${resource.id}: ${e.message}")
            }
        }
        return synced
    }

    private fun buildResourceDocument(
        uid: String,
        resource: ResourceEntity,
        docId: String
    ): Map<String, Any?> = mapOf(
        "id"                to docId,
        "localId"           to resource.id,
        "studentId"         to uid,
        "userId"            to uid,
        "title"             to resource.title,
        "subject"           to resource.subject,
        "unitType"          to resource.unitType,
        "currentUnits"      to resource.completedUnits,
        "completedUnits"    to resource.completedUnits,
        "totalUnits"        to resource.totalUnits,
        "isCompleted"       to (resource.totalUnits > 0 && resource.completedUnits >= resource.totalUnits),
        "colorHex"          to (resource.colorHex ?: "#0ea5e9"),
        "origin"            to "APP",
        "isAssignedByCoach" to resource.isAssignedByCoach,
        "isDeleted"         to false,
        "updatedAt"         to FieldValue.serverTimestamp()
    )

    // ── Topics (Konularım) ──────────────────────────────────────────

    private suspend fun syncPendingTopics(uid: String): Int {
        if (uid.isBlank() || uid == "student_default") return 0
        return TopicCloudSyncManager.pushPendingTopicsNow(context)
    }

    // ── New Entities (Phase 1) ──────────────────────────────────────────

    private suspend fun syncPendingInstitutionSchedules(uid: String): Int {
        val fs = firestore ?: return 0
        val pending = institutionScheduleDao.getUnsyncedSchedules()
        if (pending.isEmpty()) return 0

        var synced = 0
        val docPath = "users/$uid/institution_schedules"
        for (schedule in pending) {
            try {
                val docId = schedule.cloudId?.takeIf { it.isNotBlank() } ?: schedule.id
                val docData = mapOf(
                    "dayOfWeek" to schedule.dayOfWeek,
                    "startTime" to schedule.startTime,
                    "endTime" to schedule.endTime,
                    "subject" to schedule.subject,
                    "type" to schedule.type,
                    "teacherName" to schedule.teacherName,
                    "period" to schedule.period,
                    "isDeleted" to schedule.isDeleted,
                    "lastModifiedMillis" to schedule.lastModifiedMillis
                )

                val docRef = fs.collection(docPath).document(docId)
                docRef.set(docData, SetOptions.merge()).await()
                institutionScheduleDao.markSynced(schedule.id, docRef.id)
                synced++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync institution schedule id=${schedule.id}: ${e.message}")
            }
        }
        return synced
    }

    private suspend fun syncPendingSimulations(uid: String): Int {
        val fs = firestore ?: return 0
        var synced = 0
        val docPath = "users/$uid/ai_simulations"

        val pendingProjections = netProjectionDao.getUnsyncedProjections()
        for (projection in pendingProjections) {
            try {
                val docData = mapOf(
                    "type" to "NET_PROJECTION",
                    "tytCurrentNet" to projection.tytCurrentNet,
                    "tytProjectedNet" to projection.tytProjectedNet,
                    "tytTargetNet" to projection.tytTargetNet,
                    "tytNetGap" to projection.tytNetGap,
                    "aytCurrentNet" to projection.aytCurrentNet,
                    "aytProjectedNet" to projection.aytProjectedNet,
                    "aytTargetNet" to projection.aytTargetNet,
                    "aytNetGap" to projection.aytNetGap,
                    "priorityTopicsJson" to projection.priorityTopicsJson,
                    "strategicSummary" to projection.strategicSummary,
                    "lastUpdatedTimestamp" to projection.lastUpdatedTimestamp
                )

                val docRef = if (projection.cloudId != null) {
                    fs.document("$docPath/${projection.cloudId}")
                } else {
                    fs.collection(docPath).document()
                }

                docRef.set(docData, SetOptions.merge()).await()
                netProjectionDao.markSynced(projection.id, docRef.id)
                synced++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync projection id=${projection.id}: ${e.message}")
            }
        }

        val pendingExams = monthlyExamNetDao.getUnsyncedExams()
        for (exam in pendingExams) {
            try {
                val docData = mapOf(
                    "type" to "MONTHLY_EXAM",
                    "yearMonth" to exam.yearMonth,
                    "examType" to exam.examType,
                    "netDetailsJson" to exam.netDetailsJson,
                    "notes" to exam.notes,
                    "createdAtMillis" to exam.createdAtMillis,
                    "lastModifiedMillis" to exam.lastModifiedMillis
                )

                val docRef = if (exam.cloudId != null) {
                    fs.document("$docPath/${exam.cloudId}")
                } else {
                    fs.collection(docPath).document()
                }

                docRef.set(docData, SetOptions.merge()).await()
                monthlyExamNetDao.markSynced(exam.id, docRef.id)
                synced++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync exam id=${exam.id}: ${e.message}")
            }
        }
        return synced
    }

    private suspend fun syncPendingTrainingFolders(uid: String): Int {
        val fs = firestore ?: return 0
        val pending = trainingFolderDao.getUnsyncedFolders()
        if (pending.isEmpty()) return 0

        var synced = 0
        val docPath = "users/$uid/training_folders"
        for (folder in pending) {
            try {
                val docData = mapOf(
                    "id" to folder.id,
                    "title" to folder.title,
                    "createdAt" to folder.createdAt,
                    "lastModifiedMillis" to folder.lastModifiedMillis
                )

                val docRef = if (folder.cloudId != null) {
                    fs.document("$docPath/${folder.cloudId}")
                } else {
                    fs.collection(docPath).document(folder.id)
                }

                docRef.set(docData, SetOptions.merge()).await()
                trainingFolderDao.markSynced(folder.id, docRef.id)
                synced++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync training folder id=${folder.id}: ${e.message}")
            }
        }
        return synced
    }

    private suspend fun syncPendingTrainingQuestions(uid: String): Int {
        val fs = firestore ?: return 0
        val pending = trainingQuestionDao.getUnsyncedQuestions()
        if (pending.isEmpty()) return 0

        var synced = 0
        val docPath = "users/$uid/training_questions"
        for (question in pending) {
            try {
                val questionImageUrl = question.imageUri?.let { uploadImageIfLocal(uid, it) }

                val docData = mapOf(
                    "id" to question.id,
                    "folderId" to question.folderId,
                    "questionText" to question.questionText,
                    "imageUri" to questionImageUrl,
                    "correctAnswer" to question.correctAnswer,
                    "userAnswer" to question.userAnswer,
                    "isSolved" to question.isSolved,
                    "reviewCount" to question.reviewCount,
                    "lastModifiedMillis" to question.lastModifiedMillis
                )

                val docRef = if (question.cloudId != null) {
                    fs.document("$docPath/${question.cloudId}")
                } else {
                    fs.collection(docPath).document(question.id)
                }

                docRef.set(docData, SetOptions.merge()).await()
                trainingQuestionDao.markSynced(question.id, docRef.id)
                synced++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync training question id=${question.id}: ${e.message}")
            }
        }
        return synced
    }

    // ── Image Upload ─────────────────────────────────────────────────────

    /**
     * If [uri] is a local content:// or file:// URI, compresses to WebP and uploads
     * to Firebase Storage. Returns the download URL.
     * If [uri] is already an https:// URL, returns it as-is (idempotent re-sync).
     */
    private suspend fun uploadImageIfLocal(uid: String, uri: String): String {
        val trimmed = uri.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) return trimmed

        return withContext(Dispatchers.IO) {
            val bitmap = when {
                trimmed.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(trimmed))?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                trimmed.startsWith("file://") -> {
                    val file = java.io.File(trimmed.removePrefix("file://"))
                    if (file.exists() && file.length() > 0L) BitmapFactory.decodeFile(file.absolutePath) else null
                }
                else -> {
                    val file = java.io.File(trimmed)
                    if (file.exists() && file.length() > 0L) BitmapFactory.decodeFile(file.absolutePath) else null
                }
            } ?: return@withContext ""

            // Composite onto opaque white canvas so transparent Lasso cutouts never turn black
            val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(flattened)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            bitmap.recycle()

            val baos = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            flattened.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, baos)
            val imageBytes = baos.toByteArray()
            flattened.recycle()

            val st = storage ?: throw IllegalStateException("FirebaseStorage unavailable")
            val fileName = "${UUID.randomUUID()}.webp"
            val storageRef = st.reference.child("images/$uid/$fileName")

            storageRef.putBytes(imageBytes).await()
            storageRef.downloadUrl.await().toString()
        }
    }

    // ── Path Helpers ─────────────────────────────────────────────────────

    /**
     * Returns the correct Firestore collection path based on whether the user
     * belongs to an institution or is a free student.
     *
     * - Institutional: `institutions/{instId}/students/{uid}/{collection}`
     * - Free student:  `students/{uid}/{collection}`
     */
    private fun getCollectionPath(resolvedInstId: String, uid: String, collection: String): String {
        return if (resolvedInstId == "INDIVIDUAL") {
            "students/$uid/$collection"
        } else {
            "institutions/$resolvedInstId/students/$uid/$collection"
        }
    }

    // ── Diagnostics ──────────────────────────────────────────────────────

    suspend fun getPendingSyncCount(): Int = syncDao.getTotalPendingSyncCount()
}
