package com.example.sharkmentor.data.sync

import android.content.Context
import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.service.sync.SyncWorker
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Pure value representation of a Firestore topic mastery document used for
 * deterministic cross-collection merging & unit testing.
 */
data class RemoteTopicRecord(
    val docId: String,
    val localId: Long?,
    val subject: String,
    val topic: String,
    val currentLevel: Int,
    val targetLevel: Int,
    val notes: String,
    val isDeleted: Boolean,
    val updatedAtMillis: Long,
    val fromSubcollection: Boolean
)

/**
 * Pure reconciliation output computed from local Room rows + merged remote records.
 */
data class TopicReconcilePlan(
    val toInsert: List<TopicGoalEntity>,
    val toUpdate: List<TopicGoalEntity>,
    val toDeleteIds: List<Long>
)

/**
 * Canonical, offline-first Cloud Sync engine for Topic Mastery (`Konu Hakimiyeti` / `topic_goals`).
 *
 * Strictly adheres to Mark 47 `firestore.rules`:
 * - Primary canonical path: `students/{uid}/topics/{docId}` (owner-unconditional read/write)
 * - Mirror compatibility path: `topics/{docId}` with `studentId == uid` (Coach Web & legacy clients)
 *
 * Guarantees:
 * 1. Deterministic collision-free doc IDs derived from `(uid, normalizedSubject, normalizedTopic)`
 *    so Room auto-increment resets after reinstall NEVER overwrite unrelated cloud topics.
 * 2. Fast Firestore `WriteBatch` uploads (chunks of 200) so bulk AI topic creation (`INSERT_TOPICS_BATCH`)
 *    persists to Firebase immediately in <1 second instead of waiting for delayed/battery-gated workers.
 * 3. Seamless backward compatibility on pull: ALWAYS queries & merges BOTH `students/{uid}/topics`
 *    AND top-level `topics` (reading both legacy `${uid}_${localId}` and new `${uid}_t_<hash>` docs)
 *    with Last-Write-Wins deduplication by normalized `(subject, topic)`.
 * 4. Mutex-serialized Room writes so concurrent login pull, SyncWorker, and live listeners never duplicate rows.
 */
object TopicCloudSyncManager {

    private const val TAG = "TopicCloudSync"
    private const val BATCH_CHUNK_SIZE = 200
    private const val QUERY_LIMIT = 500L
    private val NON_ALNUM_TR = Regex("""[^a-z0-9çğıöşü]+""")
    private val MULTI_SPACE = Regex("""\s+""")
    private val syncMutex = Mutex()

    // ── Pure Deterministic Key / ID / Merge Logic (JVM Unit-Testable) ────────

    fun normalizeSegment(raw: String): String {
        return raw.trim()
            .lowercase(Locale("tr"))
            .replace(NON_ALNUM_TR, " ")
            .replace(MULTI_SPACE, " ")
            .trim()
    }

    fun normalizeTopicKey(subject: String, topic: String): String {
        return "${normalizeSegment(subject)}::${normalizeSegment(topic)}"
    }

    fun buildStableTopicDocId(uid: String, subject: String, topic: String): String {
        val canonicalKey = normalizeTopicKey(subject, topic)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalKey.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(24)
        return "${uid}_t_$hex"
    }

    fun buildTopicPayloadMap(
        uid: String,
        topic: TopicGoalEntity,
        docId: String,
        nowMillis: Long = topic.updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
    ): HashMap<String, Any?> {
        val clampedLevel = topic.currentLevel.coerceIn(0, 5)
        val clampedTarget = topic.targetLevel.coerceIn(1, 5)
        return hashMapOf(
            "id" to docId,
            "localId" to topic.id,
            "studentId" to uid,
            "userId" to uid,
            "subject" to topic.subject.trim(),
            "topic" to topic.topic.trim(),
            "topicName" to topic.topic.trim(),
            "masteryLevel" to clampedLevel,
            "currentLevel" to clampedLevel,
            "targetMasteryScore" to clampedTarget,
            "targetLevel" to clampedTarget,
            "notes" to topic.notes,
            "institutionId" to (topic.institutionId?.takeIf { it.isNotBlank() } ?: "INDIVIDUAL"),
            "isDeleted" to topic.isDeleted,
            "updatedAtMillis" to nowMillis
        )
    }

    /**
     * Merges topic records fetched from BOTH `students/{uid}/topics` and top-level `topics`
     * (including legacy `${uid}_${localId}` and new `${uid}_t_<hash>` IDs) into a single
     * deduplicated list keyed by normalized `(subject, topic)`.
     */
    fun mergeRemoteTopicRecords(records: List<RemoteTopicRecord>): List<RemoteTopicRecord> {
        if (records.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<String, RemoteTopicRecord>()
        for (candidate in records) {
            val cleanTopic = candidate.topic.trim()
            if (cleanTopic.isBlank()) continue
            val cleanSubject = candidate.subject.trim().ifBlank { "Genel" }
            val key = normalizeTopicKey(cleanSubject, cleanTopic)
            val existing = grouped[key]
            if (existing == null || shouldReplaceRemoteRecord(existing, candidate)) {
                grouped[key] = candidate.copy(subject = cleanSubject, topic = cleanTopic)
            }
        }
        return grouped.values.toList()
    }

    private fun shouldReplaceRemoteRecord(
        existing: RemoteTopicRecord,
        candidate: RemoteTopicRecord
    ): Boolean {
        if (candidate.updatedAtMillis != existing.updatedAtMillis) {
            return candidate.updatedAtMillis > existing.updatedAtMillis
        }
        // Tie-breaker 1: active document wins over deleted when timestamps match identically
        if (existing.isDeleted != candidate.isDeleted) {
            return !candidate.isDeleted
        }
        // Tie-breaker 2: higher mastery rating wins if legacy doc lacked an updatedAt timestamp
        if (candidate.currentLevel != existing.currentLevel) {
            return candidate.currentLevel > existing.currentLevel
        }
        // Tie-breaker 3: prefer canonical owner subcollection (`students/{uid}/topics`)
        if (existing.fromSubcollection != candidate.fromSubcollection) {
            return candidate.fromSubcollection
        }
        // Tie-breaker 4: prefer new stable doc ID format over legacy auto-increment ID
        return candidate.docId.contains("_t_") && !existing.docId.contains("_t_")
    }

    /**
     * Reconciles merged remote topic records with local Room rows without ever clobbering
     * newer unsynced local edits or duplicating existing `(subject, topic)` rows.
     */
    fun reconcileMergedTopicsWithLocal(
        localTopics: List<TopicGoalEntity>,
        mergedRemote: List<RemoteTopicRecord>,
        canSafelyPruneSyncedOrphans: Boolean
    ): TopicReconcilePlan {
        val localByKey = HashMap<String, TopicGoalEntity>(localTopics.size)
        for (local in localTopics) {
            val key = normalizeTopicKey(local.subject, local.topic)
            val prev = localByKey[key]
            if (prev == null || (!local.isDeleted && prev.isDeleted) || local.updatedAtMillis > prev.updatedAtMillis) {
                localByKey[key] = local
            }
        }

        val toInsert = mutableListOf<TopicGoalEntity>()
        val toUpdate = mutableListOf<TopicGoalEntity>()
        val toDeleteIds = LinkedHashSet<Long>()
        val activeRemoteKeys = HashSet<String>()

        for (remote in mergedRemote) {
            val key = normalizeTopicKey(remote.subject, remote.topic)
            val existing = localByKey[key]

            if (remote.isDeleted) {
                if (existing != null && !existing.isDeleted) {
                    val remoteNewerOrSynced = existing.isSynced || remote.updatedAtMillis > existing.updatedAtMillis
                    if (remoteNewerOrSynced) {
                        toDeleteIds.add(existing.id)
                    }
                }
                continue
            }

            activeRemoteKeys.add(key)

            if (existing != null) {
                // Respect pending local deletion unless remote was updated strictly after it
                if (existing.isDeleted && !existing.isSynced && existing.updatedAtMillis >= remote.updatedAtMillis) {
                    continue
                }
                // Respect pending local mastery edit unless remote is strictly newer
                if (!existing.isSynced && !existing.isDeleted && existing.updatedAtMillis >= remote.updatedAtMillis) {
                    continue
                }
                val resolvedUpdatedAt = maxOf(remote.updatedAtMillis, 1L)
                val next = existing.copy(
                    subject = remote.subject,
                    topic = remote.topic,
                    currentLevel = remote.currentLevel.coerceIn(0, 5),
                    targetLevel = remote.targetLevel.coerceIn(1, 5),
                    notes = remote.notes.ifBlank { existing.notes },
                    isSynced = true,
                    isDeleted = false,
                    updatedAtMillis = resolvedUpdatedAt
                )
                if (next != existing) {
                    toUpdate.add(next)
                }
            } else {
                toInsert.add(
                    TopicGoalEntity(
                        id = 0L,
                        subject = remote.subject,
                        topic = remote.topic,
                        currentLevel = remote.currentLevel.coerceIn(0, 5),
                        targetLevel = remote.targetLevel.coerceIn(1, 5),
                        notes = remote.notes,
                        isSynced = true,
                        isDeleted = false,
                        updatedAtMillis = remote.updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                )
            }
        }

        if (canSafelyPruneSyncedOrphans) {
            for (local in localTopics) {
                if (!local.isSynced || local.isDeleted) continue
                val key = normalizeTopicKey(local.subject, local.topic)
                if (key !in activeRemoteKeys) {
                    toDeleteIds.add(local.id)
                }
            }
        }

        return TopicReconcilePlan(
            toInsert = toInsert,
            toUpdate = toUpdate,
            toDeleteIds = toDeleteIds.toList()
        )
    }

    // ── Firestore I/O Operations ─────────────────────────────────────────────

    private fun resolveAuthenticatedUid(context: Context): String? {
        val uid = runCatching {
            GoogleServicesHelper.getCurrentUser(context = context)?.uid
        }.getOrNull()
        return uid?.takeIf { it.isNotBlank() && it != "student_default" && it != "local_student" }
    }

    private fun parseSnapshotDoc(
        doc: DocumentSnapshot,
        uid: String,
        fromSubcollection: Boolean
    ): RemoteTopicRecord? {
        val subject = SyncWorker.sanitizeTurkishText(
            doc.getSafeStr("subject") ?: doc.getSafeStr("subjectName") ?: "Genel"
        ).trim().ifBlank { "Genel" }
        val topic = SyncWorker.sanitizeTurkishText(
            doc.getSafeStr("topicName") ?: doc.getSafeStr("topic") ?: ""
        ).trim()
        if (topic.isBlank()) return null

        val level = (doc.getSafeIntVal("masteryLevel")
            ?: doc.getSafeIntVal("currentLevel")
            ?: doc.getSafeIntVal("masteryScore")
            ?: 0).coerceIn(0, 5)
        val target = (doc.getSafeIntVal("targetMasteryScore")
            ?: doc.getSafeIntVal("targetLevel")
            ?: 5).coerceIn(1, 5)
        val notes = SyncWorker.sanitizeTurkishText(doc.getSafeStr("notes") ?: "")
        val isDeleted = doc.getSafeBoolVal("isDeleted") ?: false
        val updatedAtMs = doc.getSafeLongVal("updatedAtMillis")
            ?: doc.getTimestamp("updatedAt")?.toDate()?.time
            ?: doc.getSafeLongVal("updatedAt")
            ?: 0L
        val localId = doc.getSafeLongVal("localId")
            ?: doc.getSafeLongVal("id")
            ?: doc.id.removePrefix("${uid}_").toLongOrNull()

        return RemoteTopicRecord(
            docId = doc.id,
            localId = localId,
            subject = subject,
            topic = topic,
            currentLevel = level,
            targetLevel = target,
            notes = notes,
            isDeleted = isDeleted,
            updatedAtMillis = updatedAtMs,
            fromSubcollection = fromSubcollection
        )
    }

    /**
     * Immediately uploads the supplied [topics] to Firestore using batched writes (200 docs/batch).
     * Canonical write goes to `students/{uid}/topics/{stableDocId}` first, then mirrors to
     * top-level `topics/{stableDocId}` independently for coach dashboard & legacy compatibility.
     */
    suspend fun pushTopicsNow(
        context: Context,
        topics: List<TopicGoalEntity>
    ): Int = withContext(Dispatchers.IO) {
        if (topics.isEmpty()) return@withContext 0
        val uid = resolveAuthenticatedUid(context) ?: return@withContext 0
        val fs = GoogleServicesHelper.getFirebaseFirestore(context) ?: return@withContext 0
        val db = SharkMentorDatabase.getDatabase(context)
        val activeTopics = topics.filter { !it.isDeleted && it.topic.isNotBlank() }
        if (activeTopics.isEmpty()) return@withContext 0

        var uploaded = 0
        val syncedIds = mutableListOf<Long>()

        for (chunk in activeTopics.chunked(BATCH_CHUNK_SIZE)) {
            val now = System.currentTimeMillis()
            val prepared = chunk.map { entity ->
                val docId = buildStableTopicDocId(uid, entity.subject, entity.topic)
                val ts = entity.updatedAtMillis.takeIf { it > 0L } ?: now
                val payload = buildTopicPayloadMap(uid, entity, docId, ts).apply {
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                Triple(entity, docId, payload)
            }

            // 1. Canonical owner subcollection (unconditionally permitted for student owner)
            val subOk = runCatching {
                val batch = fs.batch()
                for ((_, docId, payload) in prepared) {
                    val ref = fs.collection("students").document(uid)
                        .collection("topics").document(docId)
                    batch.set(ref, payload, SetOptions.merge())
                }
                batch.commit().await()
                true
            }.onFailure { e ->
                Log.w(TAG, "Subcollection batch push failed (${prepared.size} topics): ${e.message}")
            }.getOrDefault(false)

            // 2. Top-level mirror collection (Coach Web & legacy listener compatibility)
            val rootOk = runCatching {
                val batch = fs.batch()
                for ((_, docId, payload) in prepared) {
                    val ref = fs.collection("topics").document(docId)
                    batch.set(ref, payload, SetOptions.merge())
                }
                batch.commit().await()
                true
            }.onFailure { e ->
                Log.w(TAG, "Root mirror batch push failed (${prepared.size} topics): ${e.message}")
            }.getOrDefault(false)

            if (subOk || rootOk) {
                uploaded += prepared.size
                prepared.forEach { (entity, _, _) ->
                    if (entity.id > 0L) syncedIds.add(entity.id)
                }
            }
        }

        if (syncedIds.isNotEmpty()) {
            runCatching {
                syncedIds.chunked(400).forEach { idChunk ->
                    db.syncDao().markTopicsAsSynced(idChunk)
                }
            }
        }
        Log.d(TAG, "pushTopicsNow finished: uploaded=$uploaded/${activeTopics.size}")
        uploaded
    }

    /**
     * Flushes all locally pending deletions and unsynced topic inserts/updates to Firestore.
     */
    suspend fun pushPendingTopicsNow(context: Context): Int = withContext(Dispatchers.IO) {
        val db = SharkMentorDatabase.getDatabase(context)
        var total = 0

        val pendingDeleted = db.goalsDao().getPendingDeletedTopics()
        if (pendingDeleted.isNotEmpty()) {
            if (deleteTopicsInCloudNow(context, pendingDeleted)) {
                total += pendingDeleted.size
            }
        }

        val pendingUpserts = db.syncDao().getUnsyncedTopics()
        if (pendingUpserts.isNotEmpty()) {
            total += pushTopicsNow(context, pendingUpserts)
        }
        total
    }

    /**
     * Marks both the deterministic doc ID and any legacy `${uid}_${localId}` doc ID as deleted
     * across both `students/{uid}/topics` and top-level `topics`, then purges them locally on success.
     */
    suspend fun deleteTopicsInCloudNow(
        context: Context,
        topics: List<TopicGoalEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        if (topics.isEmpty()) return@withContext true
        val uid = resolveAuthenticatedUid(context) ?: return@withContext false
        val fs = GoogleServicesHelper.getFirebaseFirestore(context) ?: return@withContext false
        val db = SharkMentorDatabase.getDatabase(context)
        val now = System.currentTimeMillis()
        var allSucceeded = true

        for (chunk in topics.chunked(BATCH_CHUNK_SIZE / 2)) {
            val payloadPairs = chunk.flatMap { topic ->
                val stableId = buildStableTopicDocId(uid, topic.subject, topic.topic)
                val legacyId = "${uid}_${topic.id}"
                val ids = linkedSetOf(stableId, legacyId)
                ids.map { docId ->
                    docId to hashMapOf<String, Any?>(
                        "id" to docId,
                        "localId" to topic.id,
                        "studentId" to uid,
                        "userId" to uid,
                        "subject" to topic.subject,
                        "topic" to topic.topic,
                        "topicName" to topic.topic,
                        "isDeleted" to true,
                        "updatedAtMillis" to now,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                }
            }

            val subOk = runCatching {
                val batch = fs.batch()
                for ((docId, payload) in payloadPairs) {
                    val ref = fs.collection("students").document(uid)
                        .collection("topics").document(docId)
                    batch.set(ref, payload, SetOptions.merge())
                }
                batch.commit().await()
                true
            }.onFailure { e ->
                Log.w(TAG, "Subcollection batch delete failed: ${e.message}")
            }.getOrDefault(false)

            val rootOk = runCatching {
                val batch = fs.batch()
                for ((docId, payload) in payloadPairs) {
                    val ref = fs.collection("topics").document(docId)
                    batch.set(ref, payload, SetOptions.merge())
                }
                batch.commit().await()
                true
            }.onFailure { e ->
                Log.w(TAG, "Root mirror batch delete failed: ${e.message}")
            }.getOrDefault(false)

            if (subOk || rootOk) {
                chunk.forEach { db.goalsDao().deleteTopicPermanently(it.id) }
            } else {
                allSucceeded = false
            }
        }
        allSucceeded
    }

    /**
     * Full pull + merge from BOTH `students/{uid}/topics` and top-level `topics`.
     * Safe for fresh reinstalls, account re-login, manual "Şimdi Senkronize Et", and SyncWorker.
     *
     * Returns total active topic count in local Room after reconciliation, or -1 if unauthenticated/unavailable.
     */
    suspend fun pullAndMergeFromCloud(
        context: Context,
        forceServer: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        val uid = resolveAuthenticatedUid(context) ?: return@withContext -1
        val fs = GoogleServicesHelper.getFirebaseFirestore(context) ?: return@withContext -1

        // Push pending local mutations first so offline edits are never clobbered
        runCatching { pushPendingTopicsNow(context) }

        val preferredSource = if (forceServer) Source.SERVER else Source.DEFAULT

        suspend fun fetchQuery(query: com.google.firebase.firestore.Query): com.google.firebase.firestore.QuerySnapshot? {
            return runCatching {
                query.get(preferredSource).await()
            }.recoverCatching {
                if (preferredSource == Source.SERVER) {
                    query.get(Source.DEFAULT).await()
                } else {
                    throw it
                }
            }.getOrNull()
        }

        val subSnap = fetchQuery(
            fs.collection("students").document(uid)
                .collection("topics")
                .limit(QUERY_LIMIT)
        )
        val rootSnap = fetchQuery(
            fs.collection("topics")
                .whereEqualTo("studentId", uid)
                .limit(QUERY_LIMIT)
        )

        if (subSnap == null && rootSnap == null) {
            Log.w(TAG, "pullAndMergeFromCloud: both subcollection and root queries failed; retaining local topics.")
            return@withContext -1
        }

        val records = mutableListOf<RemoteTopicRecord>()
        subSnap?.documents?.forEach { doc ->
            parseSnapshotDoc(doc, uid, fromSubcollection = true)?.let { records.add(it) }
        }
        rootSnap?.documents?.forEach { doc ->
            parseSnapshotDoc(doc, uid, fromSubcollection = false)?.let { records.add(it) }
        }

        val merged = mergeRemoteTopicRecords(records)
        val canSafelyPrune = subSnap != null &&
            rootSnap != null &&
            !subSnap.metadata.isFromCache &&
            !rootSnap.metadata.isFromCache &&
            subSnap.size().toLong() < QUERY_LIMIT &&
            rootSnap.size().toLong() < QUERY_LIMIT

        applyMergedRecordsLocked(context, merged, canSafelyPrune)

        val activeCount = SharkMentorDatabase.getDatabase(context).goalsDao().getAllTopicsRaw().size
        Log.i(
            TAG,
            "pullAndMergeFromCloud complete: sub=${subSnap?.size() ?: -1}, root=${rootSnap?.size() ?: -1}, merged=${merged.size}, localActive=$activeCount"
        )
        activeCount
    }

    /**
     * Reconciles live Firestore snapshot updates from `GoalsViewModel` listeners into Room.
     * Never performs destructive orphan sweeps so partial/cached snapshots can never wipe topics.
     */
    suspend fun reconcileLiveSnapshot(
        context: Context,
        docs: List<DocumentSnapshot>,
        fromSubcollection: Boolean
    ) = withContext(Dispatchers.IO) {
        if (docs.isEmpty()) return@withContext
        val uid = resolveAuthenticatedUid(context) ?: return@withContext
        val records = docs.mapNotNull { parseSnapshotDoc(it, uid, fromSubcollection) }
        if (records.isEmpty()) return@withContext
        val merged = mergeRemoteTopicRecords(records)
        applyMergedRecordsLocked(context, merged, canSafelyPruneSyncedOrphans = false)
    }

    private suspend fun applyMergedRecordsLocked(
        context: Context,
        mergedRemote: List<RemoteTopicRecord>,
        canSafelyPruneSyncedOrphans: Boolean
    ) {
        syncMutex.withLock {
            val db = SharkMentorDatabase.getDatabase(context)
            val goalsDao = db.goalsDao()
            val localTopics = goalsDao.getAllTopicsIncludingDeletedRaw()
            val plan = reconcileMergedTopicsWithLocal(
                localTopics = localTopics,
                mergedRemote = mergedRemote,
                canSafelyPruneSyncedOrphans = canSafelyPruneSyncedOrphans
            )

            plan.toDeleteIds.forEach { id ->
                goalsDao.deleteTopicPermanently(id)
            }
            plan.toUpdate.forEach { entity ->
                goalsDao.updateTopicGoal(entity)
            }
            if (plan.toInsert.isNotEmpty()) {
                goalsDao.insertTopicGoals(plan.toInsert)
            }
        }
    }
}

private fun DocumentSnapshot.getSafeStr(field: String): String? {
    val value = get(field) ?: return null
    return value.toString()
}

private fun DocumentSnapshot.getSafeLongVal(field: String): Long? {
    val value = get(field) ?: return null
    return (value as? Number)?.toLong()
        ?: value.toString().toDoubleOrNull()?.toLong()
        ?: value.toString().toLongOrNull()
}

private fun DocumentSnapshot.getSafeIntVal(field: String): Int? {
    val value = get(field) ?: return null
    return (value as? Number)?.toInt()
        ?: value.toString().toDoubleOrNull()?.toInt()
        ?: value.toString().toIntOrNull()
}

private fun DocumentSnapshot.getSafeBoolVal(field: String): Boolean? {
    val value = get(field) ?: return null
    if (value is Boolean) return value
    return value.toString().toBooleanStrictOrNull()
}
