package com.example.sharkmentor.data.sync

import android.content.Context
import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import com.example.sharkmentor.service.sync.SyncWorker
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Pure value representation of a Firestore topic mastery entry used for
 * deterministic merging, single-document snapshot encoding, and unit testing.
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
 * Quota-safe, single-document Cloud Sync engine for Topic Mastery (`Konu Hakimiyeti` / `topic_goals`).
 *
 * Strictly adheres to Mark 47 `firestore.rules` (lines 230-232):
 * - Canonical packed snapshot path: `students/{uid}/meta/topic_mastery_snapshot`
 *   (owner-unconditional read/write; 1 SINGLE Firestore read / 1 SINGLE Firestore write for ALL 150-500+ topics!)
 *
 * Quota Protection Guarantees:
 * 1. **1 Write for Entire Topic Table:** Packing all topics into compact JSON inside 1 doc cuts bulk AI
 *    insert writes from 300 writes -> **1 write** (99.7% reduction; ~14 KB for 200 topics vs 1 MB doc limit).
 * 2. **SHA-256 Change Guard:** Computes deterministic SHA-256 over sorted canonical topics before writing.
 *    If unchanged -> **0 Firestore writes**.
 * 3. **2.5-Second Debounced Flush:** Rapid star-rating taps coalesce into **1 single write** instead of
 *    firing a write per tap. `flushPendingInBackground` ensures immediate flush on screen exit.
 * 4. **Zero Live Collection Listeners + 15-Min Pull Cooldown:** Room is the single source of truth; tab
 *    switches cause **0 Firestore reads**. Fresh reinstalls / logins restore ALL topics in **1 single read**.
 * 5. **One-Time Legacy Migration:** Only when `meta/topic_mastery_snapshot` does not yet exist AND the
 *    account hasn't migrated yet (24h guard), reads legacy `students/{uid}/topics` / `topics` ONCE,
 *    packs them into the single snapshot doc, and never scans those legacy collections again.
 */
object TopicCloudSyncManager {

    private const val TAG = "TopicCloudSync"
    private const val PREFS_NAME = "shark_topic_sync_quota_prefs"
    private const val KEY_LAST_HASH_PREFIX = "last_topics_sha256_"
    private const val KEY_LAST_PULL_PREFIX = "last_topics_pull_ms_"
    private const val KEY_LEGACY_MIGRATED_PREFIX = "legacy_topics_migrated_"
    private const val KEY_LEGACY_CHECKED_MS_PREFIX = "legacy_topics_checked_ms_"

    private const val SNAPSHOT_VERSION = 2
    private const val PULL_COOLDOWN_MS = 15L * 60L * 1000L // 15 minutes
    private const val LEGACY_CHECK_COOLDOWN_MS = 24L * 60L * 60L * 1000L // 24 hours
    private const val DEFAULT_DEBOUNCE_MS = 2_500L
    private const val LEGACY_QUERY_LIMIT = 400L

    private val NON_ALNUM_TR = Regex("""[^a-z0-9çğıöşü]+""")
    private val MULTI_SPACE = Regex("""\s+""")
    private val syncMutex = Mutex()
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pendingDebounceJob: Job? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Pure Deterministic Serialization, Hashing & Merge Logic (JVM Unit-Testable) ──

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
        val hex = sha256Hex(canonicalKey).take(24)
        return "${uid}_t_$hex"
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Serializes active topics into ultra-compact deterministic JSON (~70-90 bytes/topic)
     * sorted by canonical `(subject, topic)` key so the content hash is 100% stable.
     */
    fun serializeActiveTopicsSnapshotJson(topics: List<TopicGoalEntity>): Pair<String, String> {
        val deduped = LinkedHashMap<String, TopicGoalEntity>()
        for (entity in topics) {
            if (entity.isDeleted) continue
            val cleanTopic = entity.topic.trim()
            if (cleanTopic.isBlank()) continue
            val cleanSubject = entity.subject.trim().ifBlank { "Genel" }
            val key = normalizeTopicKey(cleanSubject, cleanTopic)
            val prev = deduped[key]
            if (prev == null || entity.updatedAtMillis >= prev.updatedAtMillis) {
                deduped[key] = entity.copy(subject = cleanSubject, topic = cleanTopic)
            }
        }

        val sortedKeys = deduped.keys.sorted()
        val jsonArray = JsonArray()
        val hashInput = StringBuilder()

        for (key in sortedKeys) {
            val item = deduped[key] ?: continue
            val level = item.currentLevel.coerceIn(0, 5)
            val target = item.targetLevel.coerceIn(1, 5)
            val cleanNotes = item.notes.trim()
            val obj = JsonObject().apply {
                addProperty("s", item.subject)
                addProperty("t", item.topic)
                addProperty("l", level)
                if (target != 5) addProperty("g", target)
                if (cleanNotes.isNotEmpty()) addProperty("n", cleanNotes)
                addProperty("u", item.updatedAtMillis)
            }
            jsonArray.add(obj)
            hashInput.append(key)
                .append('|').append(level)
                .append('|').append(target)
                .append('|').append(cleanNotes)
                .append('\n')
        }

        val jsonStr = jsonArray.toString()
        val contentHash = sha256Hex(hashInput.toString())
        return jsonStr to contentHash
    }

    /**
     * Parses a packed `topicsJson` array from `students/{uid}/meta/topic_mastery_snapshot`.
     */
    fun parseSnapshotTopicsJson(
        uid: String,
        topicsJson: String?,
        fallbackUpdatedAtMillis: Long
    ): List<RemoteTopicRecord> {
        if (topicsJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JsonParser.parseString(topicsJson)
            if (!root.isJsonArray) return@runCatching emptyList()
            val arr = root.asJsonArray
            val out = ArrayList<RemoteTopicRecord>(arr.size())
            for (elem in arr) {
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject
                val rawSubj = obj.get("s")?.takeIf { !it.isJsonNull }?.asString ?: "Genel"
                val rawTopic = obj.get("t")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val subject = SyncWorker.sanitizeTurkishText(rawSubj).trim().ifBlank { "Genel" }
                val topic = SyncWorker.sanitizeTurkishText(rawTopic).trim()
                if (topic.isBlank()) continue
                val level = (obj.get("l")?.takeIf { !it.isJsonNull }?.asInt ?: 0).coerceIn(0, 5)
                val target = (obj.get("g")?.takeIf { !it.isJsonNull }?.asInt ?: 5).coerceIn(1, 5)
                val rawNotes = obj.get("n")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val notes = SyncWorker.sanitizeTurkishText(rawNotes).trim()
                val updatedAt = (obj.get("u")?.takeIf { !it.isJsonNull }?.asLong ?: fallbackUpdatedAtMillis)
                    .takeIf { it > 0L } ?: fallbackUpdatedAtMillis
                val stableId = buildStableTopicDocId(uid, subject, topic)
                out.add(
                    RemoteTopicRecord(
                        docId = stableId,
                        localId = null,
                        subject = subject,
                        topic = topic,
                        currentLevel = level,
                        targetLevel = target,
                        notes = notes,
                        isDeleted = false,
                        updatedAtMillis = updatedAt,
                        fromSubcollection = true
                    )
                )
            }
            out
        }.getOrElse { emptyList() }
    }

    /**
     * Merges topic records (from single snapshot or legacy collections) into a single
     * deduplicated list keyed by normalized `(subject, topic)` using deterministic LWW.
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
        if (existing.isDeleted != candidate.isDeleted) {
            return !candidate.isDeleted
        }
        if (candidate.currentLevel != existing.currentLevel) {
            return candidate.currentLevel > existing.currentLevel
        }
        if (existing.fromSubcollection != candidate.fromSubcollection) {
            return candidate.fromSubcollection
        }
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
                if (existing.isDeleted && !existing.isSynced && existing.updatedAtMillis >= remote.updatedAtMillis) {
                    continue
                }
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

    // ── Quota-Safe Single-Document Firestore I/O ─────────────────────────────

    private fun resolveAuthenticatedUid(context: Context): String? {
        val uid = runCatching {
            GoogleServicesHelper.getCurrentUser(context = context)?.uid
        }.getOrNull()
        return uid?.takeIf { it.isNotBlank() && it != "student_default" && it != "local_student" }
    }

    private fun snapshotDocRef(fs: com.google.firebase.firestore.FirebaseFirestore, uid: String) =
        fs.collection("students").document(uid)
            .collection("meta").document("topic_mastery_snapshot")

    private fun parseLegacyDoc(
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
     * Coalesces rapid star-rating / edit bursts into **1 SINGLE Firestore write**
     * after [delayMs] of inactivity (default 2.5 seconds).
     */
    fun scheduleDebouncedPush(context: Context, delayMs: Long = DEFAULT_DEBOUNCE_MS) {
        val appCtx = context.applicationContext
        pendingDebounceJob?.cancel()
        pendingDebounceJob = bgScope.launch {
            delay(delayMs)
            val pushed = pushSnapshotNow(appCtx, force = false)
            if (!pushed) {
                SyncWorker.enqueueImmediateSync(appCtx)
            }
        }
    }

    /**
     * Immediately flushes any pending debounced topic edits in the background
     * (e.g., when leaving `GoalsViewModel` or after bulk AI insert). Costs at most 1 write,
     * or 0 writes if SHA-256 hash matches the last uploaded snapshot.
     */
    fun flushPendingInBackground(context: Context) {
        val appCtx = context.applicationContext
        pendingDebounceJob?.cancel()
        pendingDebounceJob = null
        bgScope.launch {
            val db = SharkMentorDatabase.getDatabase(appCtx)
            val hasPending = db.syncDao().getUnsyncedTopics().isNotEmpty() ||
                db.goalsDao().getPendingDeletedTopics().isNotEmpty()
            if (hasPending) {
                val ok = pushSnapshotNow(appCtx, force = false)
                if (!ok) {
                    SyncWorker.enqueueImmediateSync(appCtx)
                }
            }
        }
    }

    /**
     * Packs ALL active local topics into **1 SINGLE Firestore document** (`students/{uid}/meta/topic_mastery_snapshot`)
     * guarded by a SHA-256 hash so unchanged state incurs **0 Firestore writes**.
     *
     * Returns `true` if cloud state is confirmed up-to-date (either skipped via SHA-256 or written cleanly).
     */
    suspend fun pushSnapshotNow(
        context: Context,
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val uid = resolveAuthenticatedUid(appCtx) ?: return@withContext false
        val fs = GoogleServicesHelper.getFirebaseFirestore(appCtx) ?: return@withContext false

        syncMutex.withLock {
            val db = SharkMentorDatabase.getDatabase(appCtx)
            val goalsDao = db.goalsDao()
            val allRows = goalsDao.getAllTopicsIncludingDeletedRaw()
            val activeRows = allRows.filter { !it.isDeleted && it.topic.isNotBlank() }
            val pendingDeleted = allRows.filter { it.isDeleted }
            val unsyncedActive = activeRows.filter { !it.isSynced }

            if (!force && unsyncedActive.isEmpty() && pendingDeleted.isEmpty()) {
                return@withLock true
            }

            val (topicsJson, contentHash) = serializeActiveTopicsSnapshotJson(activeRows)
            val quotaPrefs = prefs(appCtx)
            val lastHash = quotaPrefs.getString("$KEY_LAST_HASH_PREFIX$uid", null)

            if (!force && lastHash != null && lastHash == contentHash) {
                // Local content matches cloud byte-for-byte -> 0 Firestore writes!
                if (unsyncedActive.isNotEmpty()) {
                    unsyncedActive.map { it.id }.chunked(400).forEach { chunk ->
                        db.syncDao().markTopicsAsSynced(chunk)
                    }
                }
                pendingDeleted.forEach { goalsDao.deleteTopicPermanently(it.id) }
                Log.d(TAG, "pushSnapshotNow: SHA-256 unchanged -> 0 Firestore writes")
                return@withLock true
            }

            val now = System.currentTimeMillis()
            val payload = hashMapOf<String, Any>(
                "version" to SNAPSHOT_VERSION,
                "studentId" to uid,
                "contentHash" to contentHash,
                "count" to activeRows.size,
                "topicsJson" to topicsJson,
                "updatedAtMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val written = runCatching {
                snapshotDocRef(fs, uid).set(payload, SetOptions.merge()).await()
                true
            }.onFailure { e ->
                Log.w(TAG, "pushSnapshotNow failed (1-doc snapshot): ${e.message}")
            }.getOrDefault(false)

            if (written) {
                quotaPrefs.edit()
                    .putString("$KEY_LAST_HASH_PREFIX$uid", contentHash)
                    .putBoolean("$KEY_LEGACY_MIGRATED_PREFIX$uid", true)
                    .apply()
                if (unsyncedActive.isNotEmpty()) {
                    unsyncedActive.map { it.id }.chunked(400).forEach { chunk ->
                        db.syncDao().markTopicsAsSynced(chunk)
                    }
                }
                pendingDeleted.forEach { goalsDao.deleteTopicPermanently(it.id) }
                Log.i(TAG, "pushSnapshotNow: 1 Firestore write synced ${activeRows.size} topics (${topicsJson.length} chars)")
            }
            written
        }
    }

    /**
     * Convenience wrapper for SyncWorker / CloudSyncRepository: pushes all pending local topic
     * mutations (inserts, mastery updates, deletions) in **at most 1 Firestore write**.
     */
    suspend fun pushPendingTopicsNow(context: Context): Int = withContext(Dispatchers.IO) {
        val db = SharkMentorDatabase.getDatabase(context)
        val pendingCount = db.syncDao().getUnsyncedTopics().size +
            db.goalsDao().getPendingDeletedTopics().size
        if (pendingCount == 0) return@withContext 0
        if (pushSnapshotNow(context, force = false)) pendingCount else 0
    }

    /**
     * Pulls Topic Mastery from Firestore using **1 SINGLE READ** (`students/{uid}/meta/topic_mastery_snapshot`).
     *
     * Quota protections:
     * - Respects 15-minute cooldown unless [forceServer] == true OR local Room currently has 0 topics
     *   (fresh reinstall / empty state).
     * - Only if the single snapshot document does NOT exist yet AND this account hasn't been migrated
     *   in the last 24h, runs a ONE-TIME legacy recovery from `students/{uid}/topics` / `topics`,
     *   immediately packs recovered topics into the single snapshot doc (`1 write`), and marks
     *   `legacy_topics_migrated` so those legacy collections are never queried again.
     *
     * Returns total active topics in Room, or -1 if unauthenticated/unavailable.
     */
    suspend fun pullAndMergeFromCloud(
        context: Context,
        forceServer: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val uid = resolveAuthenticatedUid(appCtx) ?: return@withContext -1
        val fs = GoogleServicesHelper.getFirebaseFirestore(appCtx) ?: return@withContext -1
        val db = SharkMentorDatabase.getDatabase(appCtx)
        val quotaPrefs = prefs(appCtx)

        val localActiveBefore = db.goalsDao().getAllTopicsRaw().size
        val now = System.currentTimeMillis()
        val lastPullMs = quotaPrefs.getLong("$KEY_LAST_PULL_PREFIX$uid", 0L)
        val needsEmptyRestore = localActiveBefore == 0

        if (!forceServer && !needsEmptyRestore && (now - lastPullMs) < PULL_COOLDOWN_MS) {
            Log.d(TAG, "pullAndMergeFromCloud skipped (15-min quota cooldown; localActive=$localActiveBefore)")
            return@withContext localActiveBefore
        }

        // Flush pending local edits first (at most 1 write, 0 if unchanged)
        runCatching { pushPendingTopicsNow(appCtx) }

        val source = if (forceServer) Source.SERVER else Source.DEFAULT
        val snapDoc = runCatching {
            snapshotDocRef(fs, uid).get(source).await()
        }.recoverCatching {
            if (source == Source.SERVER) snapshotDocRef(fs, uid).get(Source.DEFAULT).await() else throw it
        }.getOrNull()

        if (snapDoc == null) {
            Log.w(TAG, "pullAndMergeFromCloud: snapshot fetch failed (offline/error); retaining local topics.")
            return@withContext -1
        }

        if (snapDoc.exists() && snapDoc.contains("topicsJson")) {
            val remoteHash = snapDoc.getSafeStr("contentHash").orEmpty()
            val remoteUpdatedMs = snapDoc.getSafeLongVal("updatedAtMillis")
                ?: snapDoc.getTimestamp("updatedAt")?.toDate()?.time
                ?: now
            val topicsJson = snapDoc.getSafeStr("topicsJson")
            val records = parseSnapshotTopicsJson(uid, topicsJson, remoteUpdatedMs)
            val merged = mergeRemoteTopicRecords(records)
            val canPrune = !snapDoc.metadata.isFromCache

            applyMergedRecordsLocked(appCtx, merged, canSafelyPruneSyncedOrphans = canPrune)

            val editor = quotaPrefs.edit()
                .putLong("$KEY_LAST_PULL_PREFIX$uid", System.currentTimeMillis())
                .putBoolean("$KEY_LEGACY_MIGRATED_PREFIX$uid", true)
            if (remoteHash.isNotBlank()) {
                editor.putString("$KEY_LAST_HASH_PREFIX$uid", remoteHash)
            }
            editor.apply()

            val activeAfter = db.goalsDao().getAllTopicsRaw().size
            Log.i(TAG, "pullAndMergeFromCloud: 1 Firestore read restored/synced $activeAfter topics.")
            return@withContext activeAfter
        }

        // ── One-Time Legacy Migration (ONLY if snapshot doc does not exist yet!) ──
        val alreadyMigrated = quotaPrefs.getBoolean("$KEY_LEGACY_MIGRATED_PREFIX$uid", false)
        val lastLegacyCheckMs = quotaPrefs.getLong("$KEY_LEGACY_CHECKED_MS_PREFIX$uid", 0L)
        val canRunLegacyRecovery = !alreadyMigrated &&
            (forceServer || (now - lastLegacyCheckMs) >= LEGACY_CHECK_COOLDOWN_MS)

        if (canRunLegacyRecovery) {
            val legacyRecords = mutableListOf<RemoteTopicRecord>()
            runCatching {
                val subSnap = fs.collection("students").document(uid)
                    .collection("topics")
                    .limit(LEGACY_QUERY_LIMIT)
                    .get(source).await()
                subSnap.documents.forEach { d ->
                    parseLegacyDoc(d, uid, fromSubcollection = true)?.let { legacyRecords.add(it) }
                }
            }
            if (legacyRecords.none { !it.isDeleted }) {
                runCatching {
                    val rootSnap = fs.collection("topics")
                        .whereEqualTo("studentId", uid)
                        .limit(LEGACY_QUERY_LIMIT)
                        .get(source).await()
                    rootSnap.documents.forEach { d ->
                        parseLegacyDoc(d, uid, fromSubcollection = false)?.let { legacyRecords.add(it) }
                    }
                }
            }

            quotaPrefs.edit().putLong("$KEY_LEGACY_CHECKED_MS_PREFIX$uid", System.currentTimeMillis()).apply()

            val mergedLegacy = mergeRemoteTopicRecords(legacyRecords)
            val activeLegacy = mergedLegacy.filter { !it.isDeleted }
            if (activeLegacy.isNotEmpty()) {
                applyMergedRecordsLocked(appCtx, mergedLegacy, canSafelyPruneSyncedOrphans = false)
                // Pack recovered legacy topics into the new 1-doc snapshot so legacy collections are never scanned again!
                pushSnapshotNow(appCtx, force = true)
            } else {
                quotaPrefs.edit().putBoolean("$KEY_LEGACY_MIGRATED_PREFIX$uid", true).apply()
            }
        }

        quotaPrefs.edit().putLong("$KEY_LAST_PULL_PREFIX$uid", System.currentTimeMillis()).apply()
        db.goalsDao().getAllTopicsRaw().size
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
