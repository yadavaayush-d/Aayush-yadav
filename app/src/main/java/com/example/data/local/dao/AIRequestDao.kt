package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AIRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIRequestDao {
    @Query("SELECT * FROM ai_requests WHERE user_id = :userId ORDER BY created_at DESC")
    fun getRequestsByUser(userId: String): Flow<List<AIRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: AIRequestEntity)

    @Query("UPDATE ai_requests SET status = :status WHERE id = :requestId")
    suspend fun updateStatus(requestId: String, status: String)

    @Query("SELECT * FROM ai_requests ORDER BY created_at DESC")
    fun getAllRequests(): Flow<List<AIRequestEntity>>

    @Query("SELECT COUNT(*) FROM ai_requests")
    fun getTotalRequestsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM ai_requests WHERE status = 'completed'")
    fun getSuccessfulRequestsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM ai_requests WHERE status = 'failed'")
    fun getFailedRequestsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM ai_requests WHERE user_id = :userId AND created_at >= :sinceTimestamp AND status = 'completed'")
    fun getUserCompletedRequestsCountSince(userId: String, sinceTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM ai_requests WHERE user_id = :userId AND created_at >= :sinceTimestamp AND status = 'completed'")
    suspend fun getUserCompletedRequestsCountSinceSync(userId: String, sinceTimestamp: Long): Int
}
