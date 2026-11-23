package com.example.sharkmentor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.sharkmentor.data.local.entity.ResourceEntity
import com.example.sharkmentor.data.local.entity.TopicGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalsDao {
    @Query("SELECT * FROM topic_goals WHERE isDeleted = 0 ORDER BY subject, topic")
    fun getAllTopicGoalsFlow(): Flow<List<TopicGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopicGoal(topicGoal: TopicGoalEntity): Long

    @Update
    suspend fun updateTopicGoal(topicGoal: TopicGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopicGoals(topicGoals: List<TopicGoalEntity>): List<Long>

    @Query("UPDATE topic_goals SET isDeleted = 1, isSynced = 0, updatedAtMillis = :now WHERE id = :id")
    suspend fun softDeleteTopicGoal(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE topic_goals SET isDeleted = 1, isSynced = 0, updatedAtMillis = :now WHERE subject = :subjectName")
    suspend fun softDeleteBySubject(subjectName: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM topic_goals WHERE isDeleted = 0")
    fun getAllTopicsRaw(): List<TopicGoalEntity>

    @Query("SELECT * FROM topic_goals")
    fun getAllTopicsIncludingDeletedRaw(): List<TopicGoalEntity>

    @Query("SELECT * FROM resources WHERE isDeleted = 0")
    fun getAllResourcesRaw(): List<ResourceEntity>

    @Query("SELECT * FROM resources WHERE isDeleted = 0 ORDER BY subject, title")
    fun getAllResourcesFlow(): Flow<List<ResourceEntity>>

    @Upsert
    suspend fun upsertResource(resource: ResourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResource(resource: ResourceEntity): Long

    @Update
    suspend fun updateResource(resource: ResourceEntity)

    @Query("UPDATE resources SET completedUnits = CASE WHEN totalUnits > 0 THEN MIN(totalUnits, completedUnits + :step) ELSE completedUnits + :step END, isSynced = 0, lastModifiedMillis = :timestamp WHERE id = :id")
    suspend fun incrementProgress(id: Long, step: Int = 1, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE resources SET completedUnits = MAX(0, completedUnits - :step), isSynced = 0, lastModifiedMillis = :timestamp WHERE id = :id")
    suspend fun decrementProgress(id: Long, step: Int = 1, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE resources SET completedUnits = CASE WHEN totalUnits > 0 THEN MAX(0, MIN(totalUnits, :progress)) ELSE MAX(0, :progress) END, isSynced = 0, lastModifiedMillis = :timestamp WHERE id = :id")
    suspend fun setProgress(id: Long, progress: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE resources SET isDeleted = 1, isSynced = 0, lastModifiedMillis = :now WHERE id = :id")
    suspend fun softDeleteResource(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM resources WHERE isSynced = 0 AND isDeleted = 1")
    suspend fun getPendingDeletedResources(): List<ResourceEntity>

    @Query("DELETE FROM resources WHERE id = :id")
    suspend fun deleteResourcePermanently(id: Long)

    @Query("SELECT * FROM resources WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getResourceByCloudId(cloudId: String): ResourceEntity?

    @Query("SELECT * FROM resources WHERE id = :id LIMIT 1")
    suspend fun getResourceById(id: Long): ResourceEntity?

    @Query("SELECT * FROM topic_goals WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: Long): TopicGoalEntity?

    @Query("SELECT * FROM topic_goals WHERE isSynced = 0 AND isDeleted = 1")
    suspend fun getPendingDeletedTopics(): List<TopicGoalEntity>

    @Query("DELETE FROM topic_goals WHERE id = :id")
    suspend fun deleteTopicPermanently(id: Long)

    @Query("DELETE FROM resources WHERE id NOT IN (:remoteIds) AND isSynced = 1")
    suspend fun deleteOrphanedResources(remoteIds: List<Long>)

    @Query("DELETE FROM resources WHERE isSynced = 1")
    suspend fun clearAllResources()

    @Query("DELETE FROM topic_goals WHERE id NOT IN (:remoteIds) AND isSynced = 1")
    suspend fun deleteOrphanedTopics(remoteIds: List<Long>)

    @Query("DELETE FROM topic_goals WHERE isSynced = 1")
    suspend fun clearAllTopics()
}
