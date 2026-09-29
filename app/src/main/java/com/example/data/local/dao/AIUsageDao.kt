package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AIUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIUsageDao {
    @Query("SELECT * FROM ai_usage WHERE user_id = :userId ORDER BY created_at DESC")
    fun getUsageByUser(userId: String): Flow<List<AIUsageEntity>>

    @Query("SELECT COALESCE(SUM(usage_amount), 0) FROM ai_usage WHERE user_id = :userId")
    fun getTotalUsageAmount(userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: AIUsageEntity)

    @Query("SELECT * FROM ai_usage ORDER BY created_at DESC")
    fun getAllUsage(): Flow<List<AIUsageEntity>>
}
