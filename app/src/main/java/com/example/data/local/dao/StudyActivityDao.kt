package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.StudyActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyActivityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: StudyActivityEntity)

    @Query("SELECT * FROM study_activities WHERE userId = :userId ORDER BY createdAt DESC")
    fun getActivitiesByUser(userId: String): Flow<List<StudyActivityEntity>>

    @Query("SELECT * FROM study_activities WHERE userId = :userId AND activityType = :activityType ORDER BY createdAt DESC")
    fun getActivitiesByUserAndType(userId: String, activityType: String): Flow<List<StudyActivityEntity>>

    @Query("SELECT * FROM study_activities WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentActivitiesByUser(userId: String, limit: Int = 20): Flow<List<StudyActivityEntity>>

    @Query("SELECT COUNT(*) FROM study_activities WHERE userId = :userId")
    suspend fun getActivityCountByUser(userId: String): Int

    @Query("DELETE FROM study_activities WHERE id = :id AND userId = :userId")
    suspend fun deleteActivity(id: String, userId: String)
}
