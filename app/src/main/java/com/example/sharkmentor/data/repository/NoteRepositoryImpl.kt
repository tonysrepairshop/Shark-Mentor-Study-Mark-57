package com.example.sharkmentor.data.repository

import android.content.Context
import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.dao.NoteDao
import com.example.sharkmentor.data.local.entity.NoteEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class NoteRepositoryImpl(
    private val noteDao: NoteDao,
    private val context: Context
) : NoteRepository {

    companion object {
        private const val TAG = "NoteRepositoryImpl"
        private const val PREFS_NOTE_QUOTA = "note_sync_quota_prefs"
        private const val KEY_LAST_PULL_MS = "last_notes_pull_ms"
        private const val KEY_LAST_UID = "last_notes_pull_uid"
        private const val PULL_COOLDOWN_MS = 15L * 60L * 1000L // 15 minutes
        private const val NOTES_QUERY_LIMIT = 150L
        private const val CLOUD_WRITE_DEBOUNCE_MS = 12_000L // 12 seconds per note

        private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val pendingNotes = ConcurrentHashMap<String, NoteEntity>()
        private val pendingWriteJobs = ConcurrentHashMap<String, Job>()
        private val lastSyncedHashByNoteId = ConcurrentHashMap<String, String>()

        private fun computeNoteContentHash(note: NoteEntity): String {
            val payload = "${note.title}|${note.content}|${note.colorHex}|${note.isPinned}"
            return runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.toByteArray(Charsets.UTF_8))
                digest.joinToString("") { "%02x".format(it) }
            }.getOrElse { payload.hashCode().toString() }
        }

        /**
         * Immediately flushes any debounced note edits to Firestore (called on editor exit / onCleared)
         * so zero user edits are lost while eliminating keystroke write storms.
         */
        fun flushPendingCloudWrites(context: Context) {
            val appCtx = context.applicationContext
            val snapshot = pendingNotes.entries.toList()
            if (snapshot.isEmpty()) return
            sharedScope.launch {
                val fs = GoogleServicesHelper.getFirebaseFirestore(appCtx) ?: return@launch
                val uid = runCatching {
                    FirebaseAuth.getInstance().currentUser?.uid
                        ?: GoogleServicesHelper.getFirebaseAuth(appCtx)?.currentUser?.uid
                }.getOrNull() ?: return@launch
                snapshot.forEach { (noteId, note) ->
                    pendingWriteJobs.remove(noteId)?.cancel()
                    if (pendingNotes.remove(noteId, note)) {
                        pushNoteToFirestoreInternal(fs, uid, note)
                    }
                }
            }
        }

        private suspend fun pushNoteToFirestoreInternal(
            fs: com.google.firebase.firestore.FirebaseFirestore,
            uid: String,
            note: NoteEntity
        ) {
            val hash = computeNoteContentHash(note)
            val cacheKey = "${uid}_${note.id}"
            if (lastSyncedHashByNoteId[cacheKey] == hash) return
            val noteMap = mapOf(
                "id" to note.id,
                "title" to note.title,
                "content" to note.content,
                "colorHex" to note.colorHex,
                "isPinned" to note.isPinned,
                "pinned" to note.isPinned,
                "createdAt" to note.createdAt,
                "updatedAt" to note.updatedAt,
                "isSynced" to true
            )
            runCatching {
                fs.collection("users").document(uid)
                    .collection("notes").document(note.id)
                    .set(noteMap)
                    .await()
                lastSyncedHashByNoteId[cacheKey] = hash
            }.onFailure { e ->
                Log.w(TAG, "Firestore note sync failed (saved locally in Room): ${e.message}")
            }
        }
    }

    private val appContext = context.applicationContext
    private val firestore by lazy { GoogleServicesHelper.getFirebaseFirestore(appContext) }

    private val currentUid: String?
        get() = try {
            FirebaseAuth.getInstance().currentUser?.uid
                ?: GoogleServicesHelper.getFirebaseAuth(appContext)?.currentUser?.uid
        } catch (e: Exception) {
            null
        }

    /**
     * Quota-safe bounded one-shot pull (max 150 docs, 15-min cooldown, auto-bypassed if local Room is empty).
     * Eliminates the previous unlimited live snapshot listener that echoed every local edit and burned reads.
     */
    private fun pullRemoteNotesIfDue(force: Boolean = false) {
        val uid = currentUid ?: return
        val fs = firestore ?: return

        sharedScope.launch {
            runCatching {
                val prefs = appContext.getSharedPreferences(PREFS_NOTE_QUOTA, Context.MODE_PRIVATE)
                val lastPullMs = prefs.getLong(KEY_LAST_PULL_MS, 0L)
                val lastUid = prefs.getString(KEY_LAST_UID, null)
                val now = System.currentTimeMillis()
                val localEmpty = runCatching { noteDao.getNoteCount() == 0 }.getOrDefault(false)
                if (!force && !localEmpty && lastUid == uid && (now - lastPullMs) < PULL_COOLDOWN_MS) {
                    return@launch
                }

                val snapshot = fs.collection("users")
                    .document(uid)
                    .collection("notes")
                    .limit(NOTES_QUERY_LIMIT)
                    .get()
                    .await()

                snapshot.documents.forEach { doc ->
                    runCatching {
                        if (doc.getBoolean("isDeleted") == true) {
                            noteDao.deleteNoteById(doc.id)
                            return@runCatching
                        }
                        val remoteUpdatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                        val existing = noteDao.getNoteById(doc.id)
                        if (existing != null && (!existing.isSynced || existing.updatedAt > remoteUpdatedAt)) {
                            return@runCatching
                        }
                        val entity = NoteEntity(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            content = doc.getString("content") ?: "",
                            colorHex = doc.getString("colorHex") ?: "#F8FAFC",
                            isPinned = doc.getBoolean("isPinned") ?: doc.getBoolean("pinned") ?: false,
                            createdAt = doc.getLong("createdAt") ?: existing?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = remoteUpdatedAt,
                            isSynced = true
                        )
                        noteDao.upsertNote(entity)
                        lastSyncedHashByNoteId["${uid}_${entity.id}"] = computeNoteContentHash(entity)
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to parse remote note ${doc.id}: ${e.message}")
                    }
                }
                prefs.edit()
                    .putLong(KEY_LAST_PULL_MS, now)
                    .putString(KEY_LAST_UID, uid)
                    .apply()
            }.onFailure { e ->
                Log.w(TAG, "Quota-safe notes pull skipped (offline/error): ${e.message}")
            }
        }
    }

    override fun getAllNotesFlow(): Flow<List<NoteEntity>> {
        return noteDao.getAllNotesFlow()
            .onStart { pullRemoteNotesIfDue(force = false) }
            .catch { e ->
                Log.e(TAG, "Error observing notes from Room: ${e.message}", e)
                emit(emptyList())
            }
    }

    override suspend fun getNoteById(id: String): NoteEntity? {
        return withContext(Dispatchers.IO) {
            runCatching { noteDao.getNoteById(id) }.getOrNull()
        }
    }

    override suspend fun insertOrUpdateNote(note: NoteEntity) {
        withContext(Dispatchers.IO) {
            val uid = currentUid
            val updatedNote = note.copy(
                updatedAt = System.currentTimeMillis(),
                isSynced = uid != null
            )
            noteDao.upsertNote(updatedNote)

            if (FirebaseAuth.getInstance().currentUser == null || uid == null) {
                return@withContext
            }

            val contentHash = computeNoteContentHash(updatedNote)
            if (lastSyncedHashByNoteId["${uid}_${updatedNote.id}"] == contentHash) {
                return@withContext
            }

            // Coalesce rapid typing/editing into 1 Firestore write every 12s (flushed on editor close)
            pendingNotes[updatedNote.id] = updatedNote
            pendingWriteJobs[updatedNote.id]?.cancel()
            pendingWriteJobs[updatedNote.id] = sharedScope.launch {
                delay(CLOUD_WRITE_DEBOUNCE_MS)
                val latest = pendingNotes.remove(updatedNote.id) ?: return@launch
                pendingWriteJobs.remove(updatedNote.id)
                val fs = firestore ?: return@launch
                pushNoteToFirestoreInternal(fs, uid, latest)
            }
        }
    }

    override suspend fun saveNote(note: NoteEntity) {
        insertOrUpdateNote(note)
    }

    override suspend fun deleteNote(noteId: String) {
        withContext(Dispatchers.IO) {
            pendingWriteJobs.remove(noteId)?.cancel()
            pendingNotes.remove(noteId)
            noteDao.deleteNoteById(noteId)

            val uid = currentUid
            if (FirebaseAuth.getInstance().currentUser == null || uid == null) {
                return@withContext
            }
            lastSyncedHashByNoteId.remove("${uid}_$noteId")

            try {
                firestore?.collection("users")?.document(uid)
                    ?.collection("notes")?.document(noteId)
                    ?.delete()
                    ?.addOnFailureListener { e ->
                        Log.w(TAG, "Firestore note delete failed: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete note from Firestore: ${e.message}")
            }
        }
    }
}
