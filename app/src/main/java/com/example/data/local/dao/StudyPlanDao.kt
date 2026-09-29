package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.StudyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyPlanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: StudyPlanEntity)

    @Query("SELECT * FROM study_plans WHERE userId = :userId ORDER BY createdAt DESC")
    fun getPlansByUser(userId: String): Flow<List<StudyPlanEntity>>

    @Query("SELECT * FROM study_plans WHERE id = :planId AND userId = :userId LIMIT 1")
    suspend fun getPlanById(planId: String, userId: String): StudyPlanEntity?

    @Query("DELETE FROM study_plans WHERE id = :planId AND userId = :userId")
    suspend fun deletePlan(planId: String, userId: String)
}
