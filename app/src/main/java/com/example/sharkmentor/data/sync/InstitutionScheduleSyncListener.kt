package com.example.sharkmentor.data.sync

import android.util.Log
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.data.local.dao.InstitutionScheduleDao
import com.example.sharkmentor.data.local.entity.InstitutionScheduleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Quota-safe bounded synchronizer for `users/{uid}/institution_schedules`.
 * Uses a shared 15-minute cooldown one-shot pull (bypassed when local Room is empty)
 * instead of attaching duplicate live snapshot listeners across multiple ViewModels.
 */
class InstitutionScheduleSyncListener(
    private val dao: InstitutionScheduleDao
) {
    companion object {
        private const val TAG = "InstitutionScheduleSync"
        private const val PULL_COOLDOWN_MS = 15L * 60L * 1000L
        private const val SCHEDULE_QUERY_LIMIT = 120L

        @Volatile
        private var lastPullAtMs: Long = 0L

        @Volatile
        private var lastPullUid: String? = null

        private val inFlight = AtomicBoolean(false)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pullJob: Job? = null

    fun start(force: Boolean = false) {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
            return
        }
        val uid = GoogleServicesHelper.getCurrentUser()?.uid
        if (uid.isNullOrBlank()) {
            return
        }
        val firestore = GoogleServicesHelper.getFirebaseFirestore() ?: return

        pullJob?.cancel()
        pullJob = scope.launch {
            val now = System.currentTimeMillis()
            val localAll = runCatching { dao.getAllSchedules() }.getOrDefault(emptyList())
            val cooldownActive = !force &&
                localAll.isNotEmpty() &&
                lastPullUid == uid &&
                (now - lastPullAtMs) < PULL_COOLDOWN_MS
            if (cooldownActive) return@launch
            if (!inFlight.compareAndSet(false, true)) return@launch

            try {
                val snapshot = firestore
                    .collection("users")
                    .document(uid)
                    .collection("institution_schedules")
                    .limit(SCHEDULE_QUERY_LIMIT)
                    .get()
                    .await()

                if (snapshot.metadata.isFromCache && snapshot.documents.isEmpty()) {
                    return@launch
                }

                val deletedIds = mutableSetOf<String>()
                val activeEntities = mutableListOf<InstitutionScheduleEntity>()
                snapshot.documents.forEach { doc ->
                    try {
                        if (doc.getBoolean("isDeleted") == true) {
                            deletedIds.add(doc.id)
                            return@forEach
                        }
                        val subject = doc.getString("subject")?.trim().orEmpty()
                        if (subject.isBlank()) return@forEach

                        val startTime = doc.getString("startTime")?.trim().takeIf { !it.isNullOrBlank() } ?: "09:00"
                        val endTime = doc.getString("endTime")?.trim().takeIf { !it.isNullOrBlank() } ?: "10:00"
                        val type = doc.getString("type")?.trim().takeIf { !it.isNullOrBlank() } ?: "DERSHANE"

                        activeEntities.add(
                            InstitutionScheduleEntity(
                                id = doc.id,
                                dayOfWeek = (doc.getLong("dayOfWeek") ?: 1L).toInt().coerceIn(1, 7),
                                startTime = startTime,
                                endTime = endTime,
                                subject = subject,
                                type = type,
                                teacherName = doc.getString("teacherName") ?: "",
                                period = (doc.getLong("period") ?: 0L).toInt(),
                                isSynced = true,
                                lastModifiedMillis = doc.getLong("lastModifiedMillis") ?: System.currentTimeMillis(),
                                isDeleted = false,
                                cloudId = doc.id
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse document ${doc.id}: ${e.message}")
                    }
                }

                val isServerSnapshot = !snapshot.metadata.isFromCache
                deletedIds.forEach { dao.deleteById(it) }

                val currentLocal = dao.getAllSchedules()
                val unsyncedIds = currentLocal.filter { !it.isSynced }.map { it.id }.toSet()
                val unsyncedCloudIds = currentLocal.filter { !it.isSynced && !it.cloudId.isNullOrBlank() }
                    .mapNotNull { it.cloudId }
                    .toSet()

                val activeIds = activeEntities.map { it.id }.toSet()
                val toUpsert = activeEntities.filter { entity ->
                    entity.id !in unsyncedIds && entity.id !in unsyncedCloudIds
                }

                if (toUpsert.isNotEmpty()) {
                    currentLocal.filter { local ->
                        local.isSynced &&
                            !local.id.startsWith("CUSTOM_SCHED_") &&
                            local.cloudId != null &&
                            local.cloudId in activeIds &&
                            local.id != local.cloudId
                    }.forEach { dao.deleteById(it.id) }

                    dao.insertAll(toUpsert)
                }

                if (isServerSnapshot && activeEntities.isNotEmpty()) {
                    currentLocal.filter { local ->
                        local.isSynced &&
                            !local.id.startsWith("CUSTOM_SCHED_") &&
                            local.id !in activeIds &&
                            (local.cloudId == null || local.cloudId !in activeIds)
                    }.forEach { dao.deleteById(it.id) }
                }

                lastPullAtMs = System.currentTimeMillis()
                lastPullUid = uid
                Log.d(TAG, "Quota-safe pull reconciled ${toUpsert.size} institution schedule rows.")
            } catch (e: Exception) {
                Log.w(TAG, "Quota-safe schedule pull skipped (offline/error): ${e.message}")
            } finally {
                inFlight.set(false)
            }
        }
    }

    fun stop() {
        pullJob?.cancel()
        pullJob = null
    }
}
