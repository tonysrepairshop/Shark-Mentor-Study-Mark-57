package com.example.sharkmentor.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.example.sharkmentor.data.local.entity.ErrorQuestionEntity
import com.example.sharkmentor.data.local.entity.FocusSessionEntity
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TargetEntity

/**
 * Unified DAO for the offline-first cloud sync outbox pattern.
 *
 * Queries fetch all locally-mutated rows where [isSynced] == false.
 * After successful Firestore upload, the worker marks each row synced
 * and stamps the remote [cloudId].
 */
@Dao
interface SyncDao {

    // ── Pending Questions ────────────────────────────────────────────────

    @Query("SELECT * FROM error_questions WHERE isSynced = 0 ORDER BY lastModifiedMillis ASC LIMIT :batchSize")
    suspend fun getPendingQuestions(batchSize: Int = 50): List<ErrorQuestionEntity>

    @Query("SELECT COUNT(*) FROM error_questions WHERE isSynced = 0")
    suspend fun getPendingQuestionCount(): Int

    @Query("""
        UPDATE error_questions 
        SET isSynced = 1, cloudId = :cloudId, lastModifiedMillis = :now 
        WHERE id = :localId
    """)
    suspend fun markQuestionSynced(localId: Long, cloudId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE error_questions SET isSynced = 0, lastModifiedMillis = :now WHERE id = :localId")
    suspend fun markQuestionDirty(localId: Long, now: Long = System.currentTimeMillis())

    // ── Pending Focus Sessions ───────────────────────────────────────────

    @Query("SELECT * FROM focus_sessions WHERE isSynced = 0 ORDER BY lastModifiedMillis ASC LIMIT :batchSize")
    suspend fun getPendingSessions(batchSize: Int = 50): List<FocusSessionEntity>

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE isSynced = 0")
    suspend fun getPendingSessionCount(): Int

    @Query("""
        UPDATE focus_sessions 
        SET isSynced = 1, cloudId = :cloudId, lastModifiedMillis = :now 
        WHERE id = :localId
    """)
    suspend fun markSessionSynced(localId: Long, cloudId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE focus_sessions SET isSynced = 1 WHERE id = :id")
    suspend fun markSessionAsSynced(id: Long)

    @Query("SELECT * FROM focus_sessions WHERE isSynced = 0 ORDER BY lastModifiedMillis ASC LIMIT :batchSize")
    suspend fun getUnsyncedSessions(batchSize: Int = 50): List<FocusSessionEntity>

    @Query("UPDATE focus_sessions SET isSynced = 0, lastModifiedMillis = :now WHERE id = :localId")
    suspend fun markSessionDirty(localId: Long, now: Long = System.currentTimeMillis())

    // ── Pending Targets (Goals) ──────────────────────────────────────────

    @Query("SELECT * FROM targets WHERE isSynced = 0 ORDER BY lastModifiedMillis ASC LIMIT :batchSize")
    suspend fun getPendingTargets(batchSize: Int = 50): List<TargetEntity>

    @Query("SELECT COUNT(*) FROM targets WHERE isSynced = 0")
    suspend fun getPendingTargetCount(): Int

    @Query("""
        UPDATE targets 
        SET isSynced = 1, cloudId = :cloudId, lastModifiedMillis = :now 
        WHERE id = :localId
    """)
    suspend fun markTargetSynced(localId: Long, cloudId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE targets SET isSynced = 1 WHERE id = :id")
    suspend fun markTargetAsSynced(id: Long)

    @androidx.room.Transaction
    @Query("SELECT * FROM targets WHERE id IN (SELECT id FROM targets WHERE isSynced = 0 UNION SELECT targetId FROM tasks WHERE isSynced = 0)")
    suspend fun getUnsyncedPlansWithTasks(): List<com.example.sharkmentor.data.local.entity.TargetWithTasks>

    @Query("UPDATE targets SET isSynced = 0, lastModifiedMillis = :now WHERE id = :localId")
    suspend fun markTargetDirty(localId: Long, now: Long = System.currentTimeMillis())

    // ── Pending Plans (Tasks) ────────────────────────────────────────────

    @Query("SELECT * FROM tasks WHERE isSynced = 0")
    suspend fun getUnsyncedPlans(): List<com.example.sharkmentor.data.local.entity.TaskEntity>

    @Query("UPDATE tasks SET isSynced = 1 WHERE targetId = :targetId")
    suspend fun markTasksAsSyncedForTarget(targetId: Long)

    // ── Pending Topics ──────────────────────────────────────────────────

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertTopic(topic: com.example.sharkmentor.data.local.entity.TopicGoalEntity)

    @Query("DELETE FROM topic_goals WHERE id = :id")
    suspend fun deleteTopicPermanently(id: Long)

    @Query("SELECT * FROM topic_goals WHERE isSynced = 0 AND isDeleted = 0")
    suspend fun getUnsyncedTopics(): List<com.example.sharkmentor.data.local.entity.TopicGoalEntity>

    @Query("UPDATE topic_goals SET isSynced = 1 WHERE id = :id")
    suspend fun markTopicAsSynced(id: Long)

    @Query("UPDATE topic_goals SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markTopicsAsSynced(ids: List<Long>)

    // ── Pending Resources (Sources) ─────────────────────────────────────

    @Query("SELECT * FROM resources WHERE isSynced = 0 AND isDeleted = 0 ORDER BY lastModifiedMillis ASC LIMIT :batchSize")
    suspend fun getUnsyncedResources(batchSize: Int = 50): List<ResourceEntity>

    @Query("""
        UPDATE resources 
        SET isSynced = 1, cloudId = :cloudId, lastModifiedMillis = :now 
        WHERE id = :localId
    """)
    suspend fun markResourceSynced(localId: Long, cloudId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE resources SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markResourcesAsSynced(ids: List<Long>)

    @Query("UPDATE resources SET isSynced = 0, lastModifiedMillis = :now WHERE id = :localId")
    suspend fun markResourceDirty(localId: Long, now: Long = System.currentTimeMillis())

    // ── Bulk Institutional Tagging ───────────────────────────────────────

    @Query("UPDATE error_questions SET institutionId = :instId, assignedCoachId = :coachId, isSynced = 0 WHERE institutionId IS NULL")
    suspend fun tagAllQuestionsToInstitution(instId: String, coachId: String?)

    @Query("UPDATE focus_sessions SET institutionId = :instId, assignedCoachId = :coachId, isSynced = 0 WHERE institutionId IS NULL")
    suspend fun tagAllSessionsToInstitution(instId: String, coachId: String?)

    @Query("UPDATE targets SET institutionId = :instId, assignedCoachId = :coachId, isSynced = 0 WHERE institutionId IS NULL")
    suspend fun tagAllTargetsToInstitution(instId: String, coachId: String?)

    @androidx.room.Transaction
    suspend fun tagUnassignedWithInstitution(instId: String) {
        tagAllQuestionsToInstitution(instId, null)
        tagAllSessionsToInstitution(instId, null)
        tagAllTargetsToInstitution(instId, null)
    }

    // ── Aggregate Pending Count ──────────────────────────────────────────

    @Query("""
        SELECT 
            (SELECT COUNT(*) FROM error_questions WHERE isSynced = 0) +
            (SELECT COUNT(*) FROM focus_sessions  WHERE isSynced = 0) +
            (SELECT COUNT(*) FROM targets         WHERE isSynced = 0) +
            (SELECT COUNT(*) FROM tasks           WHERE isSynced = 0) +
            (SELECT COUNT(*) FROM resources       WHERE isSynced = 0 AND isDeleted = 0) +
            (SELECT COUNT(*) FROM topic_goals     WHERE isSynced = 0)
    """)
    suspend fun getTotalPendingSyncCount(): Int
}
