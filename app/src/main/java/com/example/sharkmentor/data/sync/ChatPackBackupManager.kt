package com.example.sharkmentor.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class ChatPackMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = "text",
    val createdAtMillis: Long = 0L,
    val editedAtMillis: Long? = null,
    val isDeleted: Boolean = false
)

data class ChatPackRecord(
    val channelKey: String = "",
    val channelType: String = "group",
    val packIndex: Int = 0,
    val messageCount: Int = 0,
    val contentHash: String = "",
    val updatedAtMillis: Long = 0L,
    val messages: List<ChatPackMessage> = emptyList()
)

class ChatPackBackupManager(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        const val PACK_SIZE = 50
        private const val PREFS_NAME = "shark_mentor_chat_pack_cache"
        private const val KEY_CACHE_PREFIX = "chat_pack_cache_"
        private const val KEY_PACK_HASH_PREFIX = "chat_pack_hash_"
        private const val DEBOUNCE_MILLIS = 45_000L // Coalesce rapid chat bursts into 1 pack write per 45s
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lastSyncTimestamps = mutableMapOf<String, Long>()

    fun saveLocalCache(channelKey: String, messages: List<ChatPackMessage>) {
        if (channelKey.isBlank()) return
        val packsArray = JSONArray()
        val chunks = if (messages.isEmpty()) emptyList() else messages.chunked(PACK_SIZE)
        chunks.forEachIndexed { index, chunk ->
            val msgArray = encodeMessagesToJsonArray(chunk)
            val packObj = JSONObject().apply {
                put("channelKey", channelKey)
                put("packIndex", index)
                put("messageCount", chunk.size)
                put("contentHash", sha256(msgArray.toString()))
                put("updatedAtMillis", System.currentTimeMillis())
                put("messages", msgArray)
            }
            packsArray.put(packObj)
        }
        prefs.edit().putString("$KEY_CACHE_PREFIX$channelKey", packsArray.toString()).apply()
    }

    fun loadLocalCache(channelKey: String): List<ChatPackMessage> {
        if (channelKey.isBlank()) return emptyList()
        val raw = prefs.getString("$KEY_CACHE_PREFIX$channelKey", null) ?: return emptyList()
        return runCatching {
            val packsArray = JSONArray(raw)
            val result = mutableListOf<ChatPackMessage>()
            for (i in 0 until packsArray.length()) {
                val packObj = packsArray.optJSONObject(i) ?: continue
                val msgArray = packObj.optJSONArray("messages") ?: continue
                result.addAll(decodeMessagesFromJsonArray(msgArray))
            }
            result
        }.getOrDefault(emptyList())
    }

    suspend fun backupPacksToFirebase(
        uid: String,
        channelKey: String,
        channelType: String,
        messages: List<ChatPackMessage>
    ) {
        if (uid.isBlank() || channelKey.isBlank()) return
        saveLocalCache(channelKey, messages)

        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val lastWrite = lastSyncTimestamps[channelKey] ?: 0L
            if (now - lastWrite < DEBOUNCE_MILLIS) return@withContext

            val chunks = if (messages.isEmpty()) emptyList() else messages.chunked(PACK_SIZE)
            val collectionRef = firestore
                .collection("students")
                .document(uid)
                .collection("chat_packs")

            var wroteAny = false
            chunks.forEachIndexed { packIndex, chunk ->
                val msgArray = encodeMessagesToJsonArray(chunk)
                val messagesJson = msgArray.toString()
                val contentHash = sha256("$channelType|$packIndex|$messagesJson")
                val docId = "${channelKey}_pack_$packIndex"
                val hashKey = "$KEY_PACK_HASH_PREFIX${uid}_$docId"

                if (prefs.getString(hashKey, null) == contentHash) {
                    return@forEachIndexed
                }

                val record = ChatPackRecord(
                    channelKey = channelKey,
                    channelType = channelType,
                    packIndex = packIndex,
                    messageCount = chunk.size,
                    contentHash = contentHash,
                    updatedAtMillis = now,
                    messages = chunk
                )

                val payload = hashMapOf(
                    "channelKey" to record.channelKey,
                    "channelType" to record.channelType,
                    "packIndex" to record.packIndex,
                    "messageCount" to record.messageCount,
                    "contentHash" to record.contentHash,
                    "updatedAtMillis" to record.updatedAtMillis,
                    "messagesJson" to messagesJson
                )

                collectionRef.document(docId).set(payload, SetOptions.merge()).await()
                prefs.edit().putString(hashKey, contentHash).apply()
                wroteAny = true
            }

            if (wroteAny) {
                lastSyncTimestamps[channelKey] = now
            }
        }
    }

    suspend fun restorePacksFromFirebase(
        uid: String,
        channelKey: String
    ): List<ChatPackMessage> {
        if (uid.isBlank() || channelKey.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val snapshot = firestore
                .collection("students")
                .document(uid)
                .collection("chat_packs")
                .whereEqualTo("channelKey", channelKey)
                .get()
                .await()

            val sortedDocs = snapshot.documents.sortedBy {
                (it.getLong("packIndex") ?: 0L).toInt()
            }

            val restored = mutableListOf<ChatPackMessage>()
            for (doc in sortedDocs) {
                val messagesJson = doc.getString("messagesJson") ?: continue
                val msgArray = runCatching { JSONArray(messagesJson) }.getOrNull() ?: continue
                restored.addAll(decodeMessagesFromJsonArray(msgArray))
            }

            if (restored.isNotEmpty()) {
                saveLocalCache(channelKey, restored)
            }
            restored
        }
    }

    private fun encodeMessagesToJsonArray(messages: List<ChatPackMessage>): JSONArray {
        val array = JSONArray()
        for (msg in messages) {
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("senderId", msg.senderId)
                put("senderName", msg.senderName)
                put("text", msg.text)
                put("messageType", msg.messageType)
                put("createdAtMillis", msg.createdAtMillis)
                if (msg.editedAtMillis != null) {
                    put("editedAtMillis", msg.editedAtMillis)
                } else {
                    put("editedAtMillis", JSONObject.NULL)
                }
                put("isDeleted", msg.isDeleted)
            }
            array.put(obj)
        }
        return array
    }

    private fun decodeMessagesFromJsonArray(array: JSONArray): List<ChatPackMessage> {
        val list = mutableListOf<ChatPackMessage>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val editedAt = if (obj.isNull("editedAtMillis")) {
                null
            } else {
                obj.optLong("editedAtMillis")
            }
            list.add(
                ChatPackMessage(
                    id = obj.optString("id", ""),
                    senderId = obj.optString("senderId", ""),
                    senderName = obj.optString("senderName", ""),
                    text = obj.optString("text", ""),
                    messageType = obj.optString("messageType", "text"),
                    createdAtMillis = obj.optLong("createdAtMillis", 0L),
                    editedAtMillis = editedAt,
                    isDeleted = obj.optBoolean("isDeleted", false)
                )
            )
        }
        return list
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
