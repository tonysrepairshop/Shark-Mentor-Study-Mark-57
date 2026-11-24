package com.example.sharkmentor.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.entity.ErrorQuestionEntity
import com.example.sharkmentor.data.model.BadgeTier
import com.example.sharkmentor.data.model.ChatChannelType
import com.example.sharkmentor.data.model.DirectMessageThread
import com.example.sharkmentor.data.model.DmRequestStatus
import com.example.sharkmentor.data.model.LeagueChatMessage
import com.example.sharkmentor.data.model.LeagueChatRoom
import com.example.sharkmentor.data.model.MAX_DAILY_REWARDED_SOLUTIONS_PER_OWNER
import com.example.sharkmentor.data.model.MAX_REJECTED_ATTEMPTS_PER_QUESTION
import com.example.sharkmentor.data.model.MAX_SHOWCASE_BADGES
import com.example.sharkmentor.data.model.MAX_WEEKLY_QUESTION_BONUS_SECONDS
import com.example.sharkmentor.data.model.MentorStats
import com.example.sharkmentor.data.model.MessageType
import com.example.sharkmentor.data.model.ModerationReport
import com.example.sharkmentor.data.model.QUESTION_AUTO_APPROVE_WINDOW_MILLIS
import com.example.sharkmentor.data.model.QUESTION_REWARD_WINDOW_MILLIS
import com.example.sharkmentor.data.model.QUESTION_REWARD_WINDOW_MINUTES
import com.example.sharkmentor.data.model.QuestionAttachment
import com.example.sharkmentor.data.model.QuestionStatus
import com.example.sharkmentor.data.model.RewardTier
import com.example.sharkmentor.data.model.SolutionMethod
import com.example.sharkmentor.data.model.SolutionSubmission
import com.example.sharkmentor.data.model.SolutionVerifyStatus
import com.example.sharkmentor.data.model.StudentBadge
import com.example.sharkmentor.data.model.UserBlockRecord
import com.example.sharkmentor.data.model.calculateRewardTier
import com.example.sharkmentor.data.remote.CloudStorageManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Shark League Peer Learning & Question Chat:
 * - Collections:
 *   - `league_chats/{roomId}` (`GROUP`/`DM`, `mutedBy`, `themePresetId`)
 *   - `league_chats/{roomId}/messages/{messageId}/solutions/{solutionId}`
 *   - `user_blocks/{uid}/blocked/{targetUid}`
 *   - `moderation_reports/{reportId}`
 *   - `students/{uid}/badges/{badgeId}`
 * - Anti-Cheat:
 *   - `aiAccessedByUids: List<String>` (users who opened AI on a question cannot earn peer reward on it)
 *   - `rejectedSolverCounts: Map<String, Int>` (max 2 rejected attempts per question)
 *   - `method` (`TEXT | DRAWING | PHOTO | AI_FALLBACK`)
 *   - `autoApproveAt` (24-hour auto-approval deadline)
 *   - Weekly max +9000s bonus focus cap & max 2 rewarded solutions/day for the same question owner
 * - Badges & Showcase:
 *   - `showcaseBadgeIds` max 5 badges
 *   - Badge tiers: `BRONZE | SILVER | GOLD | MEGALODON`
 */
@Singleton
class LeagueChatRepository @Inject constructor(
    private val cloudStorageManager: CloudStorageManager = CloudStorageManager()
) {
    companion object {
        private const val TAG = "LeagueChatRepository"
        const val CHAT_HISTORY_LIMIT = 40L
        private const val GLOBAL_LEAGUE_BUCKET = "GLOBAL_INDIVIDUAL"
        private const val LEAGUE_CHATS_COLLECTION = "league_chats"
    }

    private val firestore: FirebaseFirestore?
        get() = GoogleServicesHelper.getFirebaseFirestore()

    private val auth: FirebaseAuth?
        get() = GoogleServicesHelper.getFirebaseAuth()

    private val currentUid: String?
        get() = GoogleServicesHelper.getCurrentUser(auth)?.uid

    private fun roomDocRef(db: FirebaseFirestore, roomId: String): DocumentReference =
        db.collection(LEAGUE_CHATS_COLLECTION).document(roomId)

    private fun roomMessagesRef(db: FirebaseFirestore, roomId: String): CollectionReference =
        roomDocRef(db, roomId).collection("messages")

    private fun legacyMessagesRef(db: FirebaseFirestore, channelId: String, isGroup: Boolean): CollectionReference {
        val parentCollection = if (isGroup) "study_groups" else "direct_messages"
        return db.collection(parentCollection).document(channelId).collection("messages")
    }

    private fun buildDeterministicDmThreadId(uidA: String, uidB: String): String {
        return if (uidA <= uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"
    }

    private fun currentDayKeyUtcPlus3(nowMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT+03:00")
        }
        return sdf.format(Date(nowMillis))
    }

    // ── Room Configuration (`league_chats/{roomId}`) ─────────────────────────

    suspend fun ensureLeagueChatRoom(
        roomId: String,
        isGroup: Boolean,
        title: String = "",
        participantIds: List<String> = emptyList(),
        themePresetId: Int = 0
    ): Result<LeagueChatRoom> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val uid = currentUid.orEmpty()
            require(roomId.isNotBlank()) { "Oda kimliği boş olamaz." }

            val ref = roomDocRef(db, roomId)
            val snap = ref.get().await()
            val now = System.currentTimeMillis()
            val type = if (isGroup) ChatChannelType.GROUP else ChatChannelType.DM
            val mergedParticipants = (participantIds + listOfNotNull(uid.takeIf { it.isNotBlank() })).distinct()

            if (!snap.exists()) {
                val room = LeagueChatRoom(
                    roomId = roomId,
                    type = type,
                    title = title.ifBlank { if (isGroup) "Çalışma Grubu #$roomId" else "Özel Mesaj" },
                    groupCode = if (isGroup) roomId else null,
                    participantIds = mergedParticipants,
                    mutedBy = emptyList(),
                    themePresetId = themePresetId,
                    dmStatus = DmRequestStatus.ACCEPTED,
                    createdAtMillis = now,
                    lastMessageAtMillis = now
                )
                ref.set(room.toMap(), SetOptions.merge()).await()
                room
            } else {
                LeagueChatRoom.fromMap(snap.id, snap.data.orEmpty())
            }
        }
    }

    suspend fun setRoomMuted(roomId: String, muted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val uid = currentUid ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")
            val updateVal = if (muted) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
            roomDocRef(db, roomId).set(
                mapOf(
                    "roomId" to roomId,
                    "mutedBy" to updateVal,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            Unit
        }
    }

    suspend fun setRoomThemePreset(roomId: String, themePresetId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            roomDocRef(db, roomId).set(
                mapOf(
                    "roomId" to roomId,
                    "themePresetId" to themePresetId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            Unit
        }
    }

    fun observeChatRoom(roomId: String): Flow<LeagueChatRoom?> = callbackFlow {
        val db = firestore
        if (db == null || roomId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration: ListenerRegistration? = runCatching {
            roomDocRef(db, roomId).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(LeagueChatRoom.fromMap(snapshot.id, snapshot.data.orEmpty()))
                } else {
                    trySend(null)
                }
            }
        }.getOrNull()
        awaitClose { registration?.remove() }
    }.catch { emit(null) }

    // ── 1. Real-Time Group & DM Chat Listeners (`limitToLast(40)`) ───────────

    fun observeMessages(channelId: String, isGroup: Boolean = true): Flow<List<LeagueChatMessage>> = callbackFlow {
        val db = firestore
        if (db == null || channelId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration: ListenerRegistration? = runCatching {
            roomMessagesRef(db, channelId)
                .orderBy("createdAtMillis", Query.Direction.ASCENDING)
                .limitToLast(CHAT_HISTORY_LIMIT)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "observeMessages($channelId, isGroup=$isGroup) error: ${error.message}")
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val messages = snapshot?.documents?.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        LeagueChatMessage.fromMap(doc.id, data)
                    }.orEmpty()
                    trySend(messages)
                }
        }.onFailure { e ->
            Log.w(TAG, "Failed to attach observeMessages listener: ${e.message}")
            trySend(emptyList())
        }.getOrNull()

        awaitClose { registration?.remove() }
    }.catch { e ->
        Log.w(TAG, "observeMessages flow catch: ${e.message}")
        emit(emptyList())
    }

    fun observeGroupMessages(groupId: String): Flow<List<LeagueChatMessage>> =
        observeMessages(channelId = groupId, isGroup = true)

    fun observeDirectMessages(threadId: String): Flow<List<LeagueChatMessage>> =
        observeMessages(channelId = threadId, isGroup = false)

    fun observeQuestionSolutions(
        roomId: String,
        questionMessageId: String
    ): Flow<List<SolutionSubmission>> = callbackFlow {
        val db = firestore
        if (db == null || roomId.isBlank() || questionMessageId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration: ListenerRegistration? = runCatching {
            roomMessagesRef(db, roomId)
                .document(questionMessageId)
                .collection("solutions")
                .orderBy("submittedAtMillis", Query.Direction.ASCENDING)
                .limit(40)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        SolutionSubmission.fromMap(doc.data)
                    }.orEmpty()
                    trySend(list)
                }
        }.getOrNull()

        awaitClose { registration?.remove() }
    }.catch { emit(emptyList()) }

    // ── 2. Send Text Message ─────────────────────────────────────────────────

    suspend fun sendTextMessage(
        channelId: String,
        isGroup: Boolean,
        text: String,
        senderName: String? = null
    ): Result<LeagueChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val uid = currentUid ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")
            val trimmed = text.trim()
            require(trimmed.isNotEmpty()) { "Mesaj metni boş olamaz." }
            require(channelId.isNotBlank()) { "Sohbet kanalı geçersiz." }

            val roomRef = roomDocRef(db, channelId)
            val roomSnap = roomRef.get().await()
            if (!isGroup && roomSnap.exists()) {
                val dmStatus = roomSnap.getString("dmStatus") ?: roomSnap.getString("status").orEmpty()
                if (dmStatus == DmRequestStatus.REJECTED.name) {
                    throw IllegalStateException("Bu mesaj isteği reddedilmiş.")
                }
            }

            val profileSnap = runCatching { db.collection("students").document(uid).get().await() }.getOrNull()
            val resolvedName = senderName?.takeIf { it.isNotBlank() }
                ?: profileSnap?.getString("name")?.takeIf { it.isNotBlank() }
                ?: profileSnap?.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: "Öğrenci"
            val avatarPresetId = (profileSnap?.getLong("avatarPresetId") ?: 0L).toInt()
            val showcaseBadges = ((profileSnap?.get("showcaseBadgeIds") as? List<*>)
                ?: (profileSnap?.get("showcaseBadges") as? List<*>))
                ?.mapNotNull { it as? String }
                ?.take(MAX_SHOWCASE_BADGES)
                .orEmpty()

            val now = System.currentTimeMillis()
            val msgRef = roomMessagesRef(db, channelId).document()
            val message = LeagueChatMessage(
                id = msgRef.id,
                channelId = channelId,
                channelType = if (isGroup) ChatChannelType.GROUP else ChatChannelType.DM,
                senderUid = uid,
                senderName = resolvedName,
                senderAvatarPresetId = avatarPresetId,
                senderShowcaseBadges = showcaseBadges,
                messageType = MessageType.TEXT,
                text = trimmed,
                createdAtMillis = now
            )

            msgRef.set(message.toMap()).await()
            runCatching {
                legacyMessagesRef(db, channelId, isGroup).document(msgRef.id).set(message.toMap()).await()
            }

            roomRef.set(
                mapOf(
                    "roomId" to channelId,
                    "type" to if (isGroup) ChatChannelType.GROUP.name else ChatChannelType.DM.name,
                    "lastMessagePreview" to trimmed.take(120),
                    "lastMessageAtMillis" to now,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            if (!isGroup) {
                runCatching {
                    db.collection("direct_messages").document(channelId).set(
                        mapOf(
                            "lastMessagePreview" to trimmed.take(120),
                            "lastMessageAtMillis" to now,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                }
            }

            message
        }
    }

    // ── 3. Share Error Question to Chat & Anti-Cheat AI Access Tracking ──────

    suspend fun recordAiAccessedOnQuestion(
        roomId: String,
        questionMessageId: String,
        isGroup: Boolean = true,
        uid: String? = currentUid
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val effectiveUid = uid?.takeIf { it.isNotBlank() }
                ?: currentUid
                ?: throw IllegalStateException("Kullanıcı oturumu bulunamadı.")
            val qRef = roomMessagesRef(db, roomId).document(questionMessageId)
            db.runTransaction { tx ->
                val snap = tx.get(qRef)
                if (!snap.exists()) return@runTransaction
                @Suppress("UNCHECKED_CAST")
                val qAttachment = QuestionAttachment.fromMap(
                    snap.get("questionAttachment") as? Map<String, Any?>
                ) ?: return@runTransaction
                if (effectiveUid !in qAttachment.aiAccessedByUids) {
                    val updated = qAttachment.copy(
                        aiAccessedByUids = (qAttachment.aiAccessedByUids + effectiveUid).distinct()
                    )
                    tx.update(qRef, "questionAttachment", updated.toMap())
                }
            }.await()
            runCatching {
                val legacyRef = legacyMessagesRef(db, roomId, isGroup).document(questionMessageId)
                val legacySnap = legacyRef.get().await()
                if (legacySnap.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val qAtt = QuestionAttachment.fromMap(
                        legacySnap.get("questionAttachment") as? Map<String, Any?>
                    )
                    if (qAtt != null && effectiveUid !in qAtt.aiAccessedByUids) {
                        legacyRef.update(
                            "questionAttachment",
                            qAtt.copy(aiAccessedByUids = (qAtt.aiAccessedByUids + effectiveUid).distinct()).toMap()
                        ).await()
                    }
                }
            }
            Unit
        }
    }

    suspend fun shareErrorQuestionToChat(
        context: Context,
        channelId: String,
        isGroup: Boolean,
        errorQuestion: ErrorQuestionEntity,
        noteText: String = "",
        senderName: String? = null
    ): Result<LeagueChatMessage> {
        val parsedUri = Uri.parse(errorQuestion.questionImageUri)
        return shareErrorQuestionToChat(
            context = context,
            channelId = channelId,
            isGroup = isGroup,
            questionImageUri = parsedUri,
            subject = errorQuestion.subject,
            topic = errorQuestion.aiTopic?.takeIf { it.isNotBlank() } ?: errorQuestion.topic,
            noteText = noteText,
            localErrorQuestionId = errorQuestion.id,
            senderName = senderName
        )
    }

    suspend fun shareErrorQuestionToChat(
        context: Context,
        channelId: String,
        isGroup: Boolean,
        questionImageUri: Uri,
        subject: String,
        topic: String,
        noteText: String = "",
        localErrorQuestionId: Long? = null,
        senderName: String? = null
    ): Result<LeagueChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val uid = currentUid ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")
            require(channelId.isNotBlank()) { "Sohbet kanalı geçersiz." }

            val now = System.currentTimeMillis()
            val questionId = "q_${now}_${UUID.randomUUID().toString().take(6)}"

            val uriStr = questionImageUri.toString()
            val uploadedUrl = if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                uriStr
            } else {
                cloudStorageManager.uploadLeagueQuestionImage(
                    context = context,
                    localUri = questionImageUri,
                    channelId = channelId,
                    questionId = questionId
                ) ?: throw IllegalStateException("Soru görseli buluta yüklenemedi.")
            }

            val profileSnap = runCatching { db.collection("students").document(uid).get().await() }.getOrNull()
            val resolvedName = senderName?.takeIf { it.isNotBlank() }
                ?: profileSnap?.getString("name")?.takeIf { it.isNotBlank() }
                ?: profileSnap?.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: "Öğrenci"
            val avatarPresetId = (profileSnap?.getLong("avatarPresetId") ?: 0L).toInt()
            val showcaseBadges = ((profileSnap?.get("showcaseBadgeIds") as? List<*>)
                ?: (profileSnap?.get("showcaseBadges") as? List<*>))
                ?.mapNotNull { it as? String }
                ?.take(MAX_SHOWCASE_BADGES)
                .orEmpty()

            val attachment = QuestionAttachment(
                questionId = questionId,
                localErrorQuestionId = localErrorQuestionId,
                ownerUid = uid,
                ownerName = resolvedName,
                subject = subject.ifBlank { "Genel" },
                topic = topic.ifBlank { "Genel" },
                questionImageUrl = uploadedUrl,
                noteText = noteText.trim(),
                sharedAtMillis = now,
                expiresAtMillis = now + QUESTION_REWARD_WINDOW_MILLIS,
                autoApproveAt = now + QUESTION_AUTO_APPROVE_WINDOW_MILLIS,
                durationMinutes = QUESTION_REWARD_WINDOW_MINUTES.toInt(),
                status = QuestionStatus.OPEN,
                solutionsCount = 0,
                aiAccessedByUids = emptyList(),
                rejectedSolverCounts = emptyMap()
            )

            val msgRef = roomMessagesRef(db, channelId).document()
            val displaySummary = buildString {
                append("[${attachment.subject} • ${attachment.topic}] Soru paylaşıldı")
                if (noteText.isNotBlank()) {
                    append(": ").append(noteText.trim())
                }
            }

            val message = LeagueChatMessage(
                id = msgRef.id,
                channelId = channelId,
                channelType = if (isGroup) ChatChannelType.GROUP else ChatChannelType.DM,
                senderUid = uid,
                senderName = resolvedName,
                senderAvatarPresetId = avatarPresetId,
                senderShowcaseBadges = showcaseBadges,
                messageType = MessageType.QUESTION_CARD,
                text = displaySummary,
                createdAtMillis = now,
                questionAttachment = attachment
            )

            msgRef.set(message.toMap()).await()
            runCatching {
                legacyMessagesRef(db, channelId, isGroup).document(msgRef.id).set(message.toMap()).await()
            }

            roomDocRef(db, channelId).set(
                mapOf(
                    "roomId" to channelId,
                    "type" to if (isGroup) ChatChannelType.GROUP.name else ChatChannelType.DM.name,
                    "lastMessagePreview" to displaySummary.take(120),
                    "lastMessageAtMillis" to now,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            message
        }
    }

    // ── 4. Submit Question Solution (Escrow + Anti-Cheat Validation) ─────────

    suspend fun submitQuestionSolution(
        context: Context? = null,
        channelId: String,
        isGroup: Boolean,
        questionMessageId: String,
        annotatedBitmap: Bitmap? = null,
        annotatedImageUri: Uri? = null,
        explanationText: String = "",
        method: SolutionMethod? = null,
        solverName: String? = null
    ): Result<LeagueChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val uid = currentUid ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")
            require(channelId.isNotBlank() && questionMessageId.isNotBlank()) {
                "Kanal veya soru kimliği eksik."
            }

            val roomMsgsCol = roomMessagesRef(db, channelId)
            val questionDocRef = roomMsgsCol.document(questionMessageId)
            var questionSnap = questionDocRef.get().await()
            if (!questionSnap.exists()) {
                val legacySnap = legacyMessagesRef(db, channelId, isGroup).document(questionMessageId).get().await()
                if (legacySnap.exists()) {
                    questionDocRef.set(legacySnap.data.orEmpty()).await()
                    questionSnap = legacySnap
                } else {
                    throw IllegalStateException("Soru mesajı bulunamadı.")
                }
            }

            val questionMsg = LeagueChatMessage.fromMap(
                questionSnap.id,
                questionSnap.data.orEmpty()
            )
            val qAttachment = questionMsg.questionAttachment
                ?: throw IllegalStateException("Bu mesaj geçerli bir soru kartı içermiyor.")

            if (qAttachment.ownerUid == uid) {
                throw IllegalStateException("Kendi paylaştığınız soruya ödüllü çözüm gönderemezsiniz.")
            }
            if (uid in qAttachment.aiAccessedByUids) {
                throw IllegalStateException("Bu soru için Shark AI ipucu/çözümü açtığınızdan ödüllü akran çözümü gönderemezsiniz.")
            }
            val rejectedCountForSolver = qAttachment.rejectedSolverCounts[uid] ?: 0
            if (rejectedCountForSolver >= MAX_REJECTED_ATTEMPTS_PER_QUESTION) {
                throw IllegalStateException("Bu soruda en fazla $MAX_REJECTED_ATTEMPTS_PER_QUESTION reddedilen çözüm hakkınızı doldurdunuz.")
            }
            if (qAttachment.status.isClosed) {
                throw IllegalStateException("Bu soru zaten çözülmüş ve kapatılmış.")
            }

            val now = System.currentTimeMillis()
            val solutionId = "sol_${now}_${UUID.randomUUID().toString().take(6)}"

            val resolvedMethod = method ?: when {
                annotatedBitmap != null -> SolutionMethod.DRAWING
                annotatedImageUri != null -> SolutionMethod.PHOTO
                else -> SolutionMethod.TEXT
            }

            val annotatedUrl = when {
                annotatedBitmap != null && !annotatedBitmap.isRecycled -> {
                    cloudStorageManager.uploadLeagueSolutionBitmap(
                        bitmap = annotatedBitmap,
                        channelId = channelId,
                        questionMessageId = questionMessageId,
                        solutionId = solutionId
                    ) ?: throw IllegalStateException("Çizimli çözüm görseli yüklenemedi.")
                }
                annotatedImageUri != null -> {
                    val uriStr = annotatedImageUri.toString()
                    if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                        uriStr
                    } else {
                        val ctx = context ?: throw IllegalStateException("Görsel yüklemek için Context gerekli.")
                        cloudStorageManager.uploadLeagueSolutionImage(
                            context = ctx,
                            localUri = annotatedImageUri,
                            channelId = channelId,
                            questionMessageId = questionMessageId,
                            solutionId = solutionId
                        ) ?: throw IllegalStateException("Çözüm görseli yüklenemedi.")
                    }
                }
                else -> qAttachment.questionImageUrl
            }

            val profileSnap = runCatching { db.collection("students").document(uid).get().await() }.getOrNull()
            val resolvedSolverName = solverName?.takeIf { it.isNotBlank() }
                ?: profileSnap?.getString("name")?.takeIf { it.isNotBlank() }
                ?: profileSnap?.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: "Arkadaşın"
            val avatarPresetId = (profileSnap?.getLong("avatarPresetId") ?: 0L).toInt()
            val showcaseBadges = ((profileSnap?.get("showcaseBadgeIds") as? List<*>)
                ?: (profileSnap?.get("showcaseBadges") as? List<*>))
                ?.mapNotNull { it as? String }
                ?.take(MAX_SHOWCASE_BADGES)
                .orEmpty()

            val rewardTier = calculateRewardTier(
                sharedAtMillis = qAttachment.sharedAtMillis,
                submittedAtMillis = now
            )

            val autoApproveDeadline = now + QUESTION_AUTO_APPROVE_WINDOW_MILLIS
            val submission = SolutionSubmission(
                solutionId = solutionId,
                questionMessageId = questionMessageId,
                questionOwnerUid = qAttachment.ownerUid,
                solverUid = uid,
                solverName = resolvedSolverName,
                method = resolvedMethod,
                annotatedImageUrl = annotatedUrl,
                explanationText = explanationText.trim(),
                submittedAtMillis = now,
                autoApproveAt = autoApproveDeadline,
                rewardTier = rewardTier,
                escrowPoints = rewardTier.mentorPoints,
                potentialBonusSeconds = rewardTier.bonusFocusSeconds,
                verifyStatus = SolutionVerifyStatus.PENDING_ESCROW
            )

            val solutionMsgRef = roomMsgsCol.document(solutionId)
            val summaryText = buildString {
                append("$resolvedSolverName çözüm gönderdi (${rewardTier.windowLabel})")
                if (explanationText.isNotBlank()) {
                    append(": ").append(explanationText.trim())
                }
            }

            val solutionChatMessage = LeagueChatMessage(
                id = solutionId,
                channelId = channelId,
                channelType = if (isGroup) ChatChannelType.GROUP else ChatChannelType.DM,
                senderUid = uid,
                senderName = resolvedSolverName,
                senderAvatarPresetId = avatarPresetId,
                senderShowcaseBadges = showcaseBadges,
                messageType = MessageType.SOLUTION_CARD,
                text = summaryText,
                createdAtMillis = now,
                solutionSubmission = submission,
                replyToQuestionMessageId = questionMessageId
            )

            val studentRef = db.collection("students").document(uid)
            db.runTransaction { tx ->
                val studentSnap = tx.get(studentRef)
                val currentPending = (studentSnap.getLong("pendingMentorPoints") ?: 0L).toInt()
                val updatedPending = (currentPending + submission.escrowPoints).coerceAtLeast(0)

                val latestQSnap = tx.get(questionDocRef)
                @Suppress("UNCHECKED_CAST")
                val latestQMap = latestQSnap.get("questionAttachment") as? Map<String, Any?>
                val latestQ = QuestionAttachment.fromMap(latestQMap) ?: qAttachment
                val updatedQ = latestQ.copy(
                    solutionsCount = latestQ.solutionsCount + 1,
                    autoApproveAt = minOf(latestQ.autoApproveAt, autoApproveDeadline)
                )

                tx.set(solutionMsgRef, solutionChatMessage.toMap())
                tx.set(
                    questionDocRef.collection("solutions").document(solutionId),
                    submission.toMap()
                )
                tx.update(questionDocRef, "questionAttachment", updatedQ.toMap())
                tx.set(
                    studentRef,
                    mapOf(
                        "pendingMentorPoints" to updatedPending,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }.await()

            runCatching {
                val legacyCol = legacyMessagesRef(db, channelId, isGroup)
                legacyCol.document(solutionId).set(solutionChatMessage.toMap()).await()
                legacyCol.document(questionMessageId)
                    .collection("solutions")
                    .document(solutionId)
                    .set(submission.toMap())
                    .await()
            }

            solutionChatMessage
        }
    }

    // ── 5. Verify Question Solution (Approve / Reject) ───────────────────────

    suspend fun verifyQuestionSolution(
        channelId: String,
        isGroup: Boolean,
        questionMessageId: String,
        solutionId: String,
        approved: Boolean
    ): Result<SolutionSubmission> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val callerUid = currentUid ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")
            require(channelId.isNotBlank() && questionMessageId.isNotBlank() && solutionId.isNotBlank()) {
                "Eksik doğrulama parametresi."
            }

            val roomMsgsCol = roomMessagesRef(db, channelId)
            val questionDocRef = roomMsgsCol.document(questionMessageId)
            val solutionMsgRef = roomMsgsCol.document(solutionId)
            val subSolRef = questionDocRef.collection("solutions").document(solutionId)

            val now = System.currentTimeMillis()
            val todayKey = currentDayKeyUtcPlus3(now)

            val updatedSubmission = db.runTransaction { tx ->
                val qSnap = tx.get(questionDocRef)
                if (!qSnap.exists()) {
                    throw IllegalStateException("Soru mesajı bulunamadı.")
                }
                @Suppress("UNCHECKED_CAST")
                val qAttachment = QuestionAttachment.fromMap(
                    qSnap.get("questionAttachment") as? Map<String, Any?>
                ) ?: throw IllegalStateException("Soru verisi okunamadı.")

                if (qAttachment.ownerUid.isNotBlank() && qAttachment.ownerUid != callerUid) {
                    throw IllegalStateException("Sadece soruyu paylaşan öğrenci çözümü onaylayabilir veya reddedebilir.")
                }

                val solMsgSnap = tx.get(solutionMsgRef)
                val subSolSnap = tx.get(subSolRef)

                @Suppress("UNCHECKED_CAST")
                val currentSub = when {
                    subSolSnap.exists() -> SolutionSubmission.fromMap(subSolSnap.data)
                    solMsgSnap.exists() -> SolutionSubmission.fromMap(
                        solMsgSnap.get("solutionSubmission") as? Map<String, Any?>
                    )
                    else -> null
                } ?: throw IllegalStateException("Çözüm kaydı bulunamadı.")

                if (currentSub.verifyStatus != SolutionVerifyStatus.PENDING_ESCROW) {
                    return@runTransaction currentSub
                }

                val solverRef = db.collection("students").document(currentSub.solverUid)
                val solverSnap = tx.get(solverRef)
                val globalLeagueRef = db.collection("institutions")
                    .document(GLOBAL_LEAGUE_BUCKET)
                    .collection("weeklyLeague")
                    .document(currentSub.solverUid)
                val globalLeagueSnap = tx.get(globalLeagueRef)

                val currentPending = (solverSnap.getLong("pendingMentorPoints") ?: 0L).toInt()
                val nextPending = (currentPending - currentSub.escrowPoints).coerceAtLeast(0)

                if (approved) {
                    val pairCounterField = "dailyOwnerReward_${todayKey}_${qAttachment.ownerUid}"
                    val solvedTodayForOwner = (solverSnap.getLong(pairCounterField) ?: 0L).toInt()
                    val withinDailyOwnerLimit = solvedTodayForOwner < MAX_DAILY_REWARDED_SOLUTIONS_PER_OWNER &&
                        currentSub.solverUid !in qAttachment.aiAccessedByUids

                    val currentMentorPoints = (solverSnap.getLong("mentorPoints") ?: 0L).toInt()
                    val currentWeeklyBonusSec = (solverSnap.getLong("weeklyQuestionBonusSeconds") ?: 0L)
                        .coerceIn(0L, MAX_WEEKLY_QUESTION_BONUS_SECONDS)
                    val currentSolvedTotal = (solverSnap.getLong("solvedTotalCount") ?: 0L).toInt()
                    val currentGoldenCount = (solverSnap.getLong("goldenWindowSolveCount") ?: 0L).toInt()
                    val currentWeeklyFocusSec = solverSnap.getLong("weeklyFocusSeconds") ?: 0L

                    val awardedPts = if (withinDailyOwnerLimit) currentSub.escrowPoints else 0
                    val remainingBonusCapacity = (MAX_WEEKLY_QUESTION_BONUS_SECONDS - currentWeeklyBonusSec)
                        .coerceAtLeast(0L)
                    val awardedBonusSec = if (withinDailyOwnerLimit) {
                        minOf(currentSub.potentialBonusSeconds, remainingBonusCapacity)
                    } else {
                        0L
                    }

                    val nextMentorPoints = currentMentorPoints + awardedPts
                    val nextWeeklyBonusSec = (currentWeeklyBonusSec + awardedBonusSec)
                        .coerceIn(0L, MAX_WEEKLY_QUESTION_BONUS_SECONDS)
                    val nextSolvedTotal = currentSolvedTotal + 1
                    val nextGoldenCount = if (currentSub.rewardTier == RewardTier.GOLD_100) {
                        currentGoldenCount + 1
                    } else {
                        currentGoldenCount
                    }
                    val nextBadgeTier = BadgeTier.fromPoints(nextMentorPoints)

                    val existingBadges = (solverSnap.get("unlockedBadges") as? List<*>)
                        ?.mapNotNull { it as? String }
                        ?.toMutableSet()
                        ?: mutableSetOf()
                    if (nextSolvedTotal >= 1) existingBadges.add("BRONZE_FIRST_SOLVE")
                    if (nextGoldenCount >= 1) existingBadges.add("SILVER_GOLDEN_RESCUER")
                    if (nextSolvedTotal >= 10) existingBadges.add("GOLD_MENTOR_10")
                    if (nextMentorPoints >= BadgeTier.MEGALODON.minMentorPoints) {
                        existingBadges.add("MEGALODON_MENTOR")
                    }

                    val existingShowcase = ((solverSnap.get("showcaseBadgeIds") as? List<*>)
                        ?: (solverSnap.get("showcaseBadges") as? List<*>))
                        ?.mapNotNull { it as? String }
                        ?.filter { it in existingBadges }
                        ?.distinct()
                        ?.take(MAX_SHOWCASE_BADGES)
                        .orEmpty()
                        .ifEmpty { existingBadges.take(MAX_SHOWCASE_BADGES) }

                    val finalizedSub = currentSub.copy(
                        verifyStatus = SolutionVerifyStatus.APPROVED,
                        verifiedAtMillis = now,
                        awardedPoints = awardedPts,
                        awardedBonusSeconds = awardedBonusSec
                    )

                    val finalizedQuestion = qAttachment.copy(
                        status = QuestionStatus.SOLVED_APPROVED,
                        approvedSolutionId = solutionId,
                        approvedSolverUid = currentSub.solverUid,
                        approvedSolverName = currentSub.solverName
                    )

                    tx.update(questionDocRef, "questionAttachment", finalizedQuestion.toMap())
                    if (solMsgSnap.exists()) {
                        tx.update(solutionMsgRef, "solutionSubmission", finalizedSub.toMap())
                    }
                    tx.set(subSolRef, finalizedSub.toMap(), SetOptions.merge())

                    val studentUpdates = mutableMapOf<String, Any>(
                        "mentorPoints" to nextMentorPoints,
                        "pendingMentorPoints" to nextPending,
                        "weeklyQuestionBonusSeconds" to nextWeeklyBonusSec,
                        "solvedTotalCount" to nextSolvedTotal,
                        "goldenWindowSolveCount" to nextGoldenCount,
                        "unlockedBadges" to existingBadges.toList(),
                        "showcaseBadgeIds" to existingShowcase,
                        "showcaseBadges" to existingShowcase,
                        "badgeTier" to nextBadgeTier.name,
                        "weeklyFocusSeconds" to (currentWeeklyFocusSec + awardedBonusSec),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (withinDailyOwnerLimit && awardedPts > 0) {
                        studentUpdates[pairCounterField] = solvedTodayForOwner + 1
                    }
                    tx.set(solverRef, studentUpdates, SetOptions.merge())

                    // Upsert badge document in students/{solverUid}/badges/{badgeTier}
                    val badgeDocRef = solverRef.collection("badges").document(nextBadgeTier.name)
                    val badgeDoc = StudentBadge(
                        badgeId = nextBadgeTier.name,
                        title = nextBadgeTier.displayName,
                        description = "Mentor Puanı: $nextMentorPoints • Çözülen Soru: $nextSolvedTotal",
                        tier = nextBadgeTier,
                        progress = nextMentorPoints,
                        target = nextBadgeTier.minMentorPoints,
                        unlocked = true,
                        unlockedAtMillis = now
                    )
                    tx.set(badgeDocRef, badgeDoc.toMap(), SetOptions.merge())

                    // Apply approved bonus to institutions/GLOBAL_INDIVIDUAL/weeklyLeague/{solverUid}
                    val leagueWeeklySec = if (globalLeagueSnap.exists()) {
                        globalLeagueSnap.getLong("weeklyFocusSeconds") ?: currentWeeklyFocusSec
                    } else {
                        currentWeeklyFocusSec
                    }
                    tx.set(
                        globalLeagueRef,
                        mapOf(
                            "studentId" to currentSub.solverUid,
                            "displayName" to currentSub.solverName,
                            "weeklyFocusSeconds" to (leagueWeeklySec + awardedBonusSec),
                            "mentorPoints" to nextMentorPoints,
                            "weeklyQuestionBonusSeconds" to nextWeeklyBonusSec,
                            "showcaseBadgeIds" to existingShowcase,
                            "showcaseBadges" to existingShowcase,
                            "badgeTier" to nextBadgeTier.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )

                    finalizedSub
                } else {
                    // Rejected: remove escrow points, increment rejectedSolverCounts[solverUid], reopen question
                    val finalizedSub = currentSub.copy(
                        verifyStatus = SolutionVerifyStatus.REJECTED,
                        verifiedAtMillis = now,
                        awardedPoints = 0,
                        awardedBonusSeconds = 0L
                    )

                    val prevRejectedForSolver = qAttachment.rejectedSolverCounts[currentSub.solverUid] ?: 0
                    val updatedRejectedMap = qAttachment.rejectedSolverCounts.toMutableMap().apply {
                        put(currentSub.solverUid, prevRejectedForSolver + 1)
                    }

                    val reopenedQuestion = qAttachment.copy(
                        status = QuestionStatus.OPEN,
                        approvedSolutionId = null,
                        approvedSolverUid = null,
                        approvedSolverName = null,
                        rejectedSolverCounts = updatedRejectedMap
                    )

                    tx.update(questionDocRef, "questionAttachment", reopenedQuestion.toMap())
                    if (solMsgSnap.exists()) {
                        tx.update(solutionMsgRef, "solutionSubmission", finalizedSub.toMap())
                    }
                    tx.set(subSolRef, finalizedSub.toMap(), SetOptions.merge())

                    tx.set(
                        solverRef,
                        mapOf(
                            "pendingMentorPoints" to nextPending,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )

                    finalizedSub
                }
            }.await()

            updatedSubmission
        }
    }

    // ── 6. Request-Approved Direct Messages (DM) ─────────────────────────────

    suspend fun sendDmRequest(
        targetUid: String,
        targetName: String,
        initialMessage: String = "",
        senderName: String? = null
    ): Result<DirectMessageThread> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val uid = currentUid ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")
            require(targetUid.isNotBlank() && targetUid != uid) {
                "Geçerli bir alıcı seçilmelidir."
            }

            // Check if blocked by either side
            val myBlockSnap = db.collection("user_blocks").document(uid)
                .collection("blocked").document(targetUid).get().await()
            val theirBlockSnap = db.collection("user_blocks").document(targetUid)
                .collection("blocked").document(uid).get().await()
            if (myBlockSnap.exists() || theirBlockSnap.exists()) {
                throw IllegalStateException("Bu kullanıcı ile mesajlaşma engellenmiş.")
            }

            val profileSnap = runCatching { db.collection("students").document(uid).get().await() }.getOrNull()
            val resolvedSenderName = senderName?.takeIf { it.isNotBlank() }
                ?: profileSnap?.getString("name")?.takeIf { it.isNotBlank() }
                ?: profileSnap?.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: "Öğrenci"

            val threadId = buildDeterministicDmThreadId(uid, targetUid)
            val roomRef = roomDocRef(db, threadId)
            val existingSnap = roomRef.get().await()
            val now = System.currentTimeMillis()
            val preview = initialMessage.trim().ifBlank { "Çalışma mesajı isteği gönderildi." }

            val existingStatus = (existingSnap.getString("dmStatus") ?: existingSnap.getString("status"))
                ?.let { runCatching { DmRequestStatus.valueOf(it) }.getOrNull() }

            val effectiveStatus = if (existingStatus == DmRequestStatus.ACCEPTED) {
                DmRequestStatus.ACCEPTED
            } else {
                DmRequestStatus.PENDING
            }

            val thread = DirectMessageThread(
                threadId = threadId,
                participantIds = listOf(uid, targetUid).sorted(),
                participantNames = mapOf(
                    uid to resolvedSenderName,
                    targetUid to targetName.ifBlank { "Öğrenci" }
                ),
                requesterUid = uid,
                requesterName = resolvedSenderName,
                receiverUid = targetUid,
                receiverName = targetName.ifBlank { "Öğrenci" },
                status = effectiveStatus,
                lastMessagePreview = preview.take(120),
                lastMessageAtMillis = now,
                createdAtMillis = existingSnap.getLong("createdAtMillis") ?: now
            )

            roomRef.set(thread.toMap(), SetOptions.merge()).await()
            runCatching {
                db.collection("direct_messages").document(threadId).set(thread.toMap(), SetOptions.merge()).await()
            }

            if (initialMessage.isNotBlank()) {
                val msgRef = roomMessagesRef(db, threadId).document()
                val firstMsg = LeagueChatMessage(
                    id = msgRef.id,
                    channelId = threadId,
                    channelType = ChatChannelType.DM,
                    senderUid = uid,
                    senderName = resolvedSenderName,
                    messageType = MessageType.TEXT,
                    text = initialMessage.trim(),
                    createdAtMillis = now
                )
                msgRef.set(firstMsg.toMap()).await()
            }

            thread
        }
    }

    suspend fun respondToDmRequest(
        threadId: String,
        accept: Boolean
    ): Result<DirectMessageThread> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val uid = currentUid ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")
            require(threadId.isNotBlank()) { "DM kanal kimliği boş olamaz." }

            val roomRef = roomDocRef(db, threadId)
            val snap = roomRef.get().await()
            val legacyRef = db.collection("direct_messages").document(threadId)
            val effectiveSnap = if (snap.exists()) snap else legacyRef.get().await()
            if (!effectiveSnap.exists()) {
                throw IllegalStateException("DM isteği bulunamadı.")
            }

            val currentThread = DirectMessageThread.fromMap(effectiveSnap.id, effectiveSnap.data.orEmpty())
            if (currentThread.receiverUid.isNotBlank() &&
                currentThread.receiverUid != uid &&
                uid !in currentThread.participantIds
            ) {
                throw IllegalStateException("Bu DM isteğini yanıtlama yetkiniz yok.")
            }

            val nextStatus = if (accept) DmRequestStatus.ACCEPTED else DmRequestStatus.REJECTED
            val now = System.currentTimeMillis()
            val statusUpdate = mapOf(
                "status" to nextStatus.name,
                "dmStatus" to nextStatus.name,
                "lastMessageAtMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            roomRef.set(statusUpdate, SetOptions.merge()).await()
            runCatching { legacyRef.set(statusUpdate, SetOptions.merge()).await() }

            currentThread.copy(status = nextStatus, lastMessageAtMillis = now)
        }
    }

    fun observeDmThreads(): Flow<List<DirectMessageThread>> = callbackFlow {
        val db = firestore
        val uid = currentUid
        if (db == null || uid.isNullOrBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration: ListenerRegistration? = runCatching {
            db.collection(LEAGUE_CHATS_COLLECTION)
                .whereArrayContains("participantIds", uid)
                .limit(40)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "observeDmThreads error: ${error.message}")
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val threads = snapshot?.documents
                        ?.mapNotNull { doc ->
                            val data = doc.data ?: return@mapNotNull null
                            val typeStr = (data["type"] as? String).orEmpty()
                            if (typeStr.isNotBlank() &&
                                typeStr != ChatChannelType.DM.name &&
                                typeStr != ChatChannelType.DIRECT_MESSAGE.name
                            ) {
                                return@mapNotNull null
                            }
                            DirectMessageThread.fromMap(doc.id, data)
                        }
                        ?.sortedByDescending { it.lastMessageAtMillis }
                        .orEmpty()
                    trySend(threads)
                }
        }.onFailure { e ->
            Log.w(TAG, "Failed to attach observeDmThreads listener: ${e.message}")
            trySend(emptyList())
        }.getOrNull()

        awaitClose { registration?.remove() }
    }.catch { e ->
        Log.w(TAG, "observeDmThreads flow catch: ${e.message}")
        emit(emptyList())
    }

    // ── 7. User Blocking (`user_blocks/{uid}/blocked/{targetUid}`) & Moderation (`moderation_reports/{reportId}`) ─

    suspend fun blockUser(
        targetUid: String,
        targetName: String = "",
        reason: String = ""
    ): Result<UserBlockRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val uid = currentUid ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")
            require(targetUid.isNotBlank() && targetUid != uid) { "Geçersiz kullanıcı." }

            val record = UserBlockRecord(
                blockerUid = uid,
                blockedUid = targetUid,
                blockedName = targetName,
                reason = reason.trim(),
                createdAtMillis = System.currentTimeMillis()
            )
            db.collection("user_blocks")
                .document(uid)
                .collection("blocked")
                .document(targetUid)
                .set(record.toMap(), SetOptions.merge())
                .await()
            record
        }
    }

    suspend fun unblockUser(targetUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val uid = currentUid ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")
            db.collection("user_blocks")
                .document(uid)
                .collection("blocked")
                .document(targetUid)
                .delete()
                .await()
            Unit
        }
    }

    fun observeBlockedUserIds(uid: String? = currentUid): Flow<Set<String>> = callbackFlow {
        val db = firestore
        val effectiveUid = uid?.takeIf { it.isNotBlank() } ?: currentUid
        if (db == null || effectiveUid.isNullOrBlank()) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        val registration: ListenerRegistration? = runCatching {
            db.collection("user_blocks")
                .document(effectiveUid)
                .collection("blocked")
                .limit(80)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptySet())
                        return@addSnapshotListener
                    }
                    val ids = snapshot?.documents?.map { it.id }?.toSet().orEmpty()
                    trySend(ids)
                }
        }.getOrNull()
        awaitClose { registration?.remove() }
    }.catch { emit(emptySet()) }

    suspend fun reportMessageOrUser(
        roomId: String,
        reportedUid: String,
        messageId: String? = null,
        reason: String,
        details: String = ""
    ): Result<ModerationReport> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val uid = currentUid ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")
            val reportRef = db.collection("moderation_reports").document()
            val report = ModerationReport(
                reportId = reportRef.id,
                reporterUid = uid,
                reportedUid = reportedUid,
                roomId = roomId,
                messageId = messageId,
                reason = reason.trim().ifBlank { "Uygunsuz İçerik" },
                details = details.trim(),
                status = "OPEN",
                createdAtMillis = System.currentTimeMillis()
            )
            reportRef.set(report.toMap()).await()
            report
        }
    }

    // ── 8. Mentor Stats, Student Badges (`students/{uid}/badges/{badgeId}`) & Showcase Badges (Max 5) ─

    suspend fun updateShowcaseBadges(
        uid: String? = currentUid,
        badgeIds: List<String>
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestore ?: throw IllegalStateException("Firestore bağlantısı kurulamadı.")
            val effectiveUid = uid?.takeIf { it.isNotBlank() }
                ?: currentUid
                ?: throw IllegalStateException("Oturum açmış kullanıcı bulunamadı.")

            val trimmed = badgeIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SHOWCASE_BADGES)

            db.collection("students").document(effectiveUid).set(
                mapOf(
                    "showcaseBadgeIds" to trimmed,
                    "showcaseBadges" to trimmed,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            runCatching {
                db.collection("institutions")
                    .document(GLOBAL_LEAGUE_BUCKET)
                    .collection("weeklyLeague")
                    .document(effectiveUid)
                    .set(
                        mapOf(
                            "showcaseBadgeIds" to trimmed,
                            "showcaseBadges" to trimmed
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }

            trimmed
        }
    }

    fun observeStudentBadges(uid: String? = currentUid): Flow<List<StudentBadge>> = callbackFlow {
        val db = firestore
        val effectiveUid = uid?.takeIf { it.isNotBlank() } ?: currentUid
        if (db == null || effectiveUid.isNullOrBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration: ListenerRegistration? = runCatching {
            db.collection("students")
                .document(effectiveUid)
                .collection("badges")
                .limit(80)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val badges = snapshot?.documents?.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        StudentBadge.fromMap(doc.id, data)
                    }.orEmpty()
                    trySend(badges)
                }
        }.getOrNull()

        awaitClose { registration?.remove() }
    }.catch { emit(emptyList()) }

    fun observeMentorStats(uid: String? = currentUid): Flow<MentorStats> = callbackFlow {
        val db = firestore
        val effectiveUid = uid?.takeIf { it.isNotBlank() } ?: currentUid
        if (db == null || effectiveUid.isNullOrBlank()) {
            trySend(MentorStats())
            close()
            return@callbackFlow
        }

        val registration: ListenerRegistration? = runCatching {
            db.collection("students").document(effectiveUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "observeMentorStats error: ${error.message}")
                        trySend(MentorStats())
                        return@addSnapshotListener
                    }
                    val stats = MentorStats.fromMap(snapshot?.data)
                    trySend(stats)
                }
        }.onFailure {
            trySend(MentorStats())
        }.getOrNull()

        awaitClose { registration?.remove() }
    }.catch {
        emit(MentorStats())
    }
}
