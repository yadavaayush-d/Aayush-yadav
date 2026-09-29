package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.QuizQuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: QuizAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuizQuestionEntity>)

    @Query("SELECT * FROM quiz_attempts WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAttemptsByUser(userId: String): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempts WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getAttemptsByUserSync(userId: String): List<QuizAttemptEntity>

    @Query("SELECT * FROM quiz_attempts WHERE id = :attemptId AND userId = :userId LIMIT 1")
    suspend fun getAttemptById(attemptId: String, userId: String): QuizAttemptEntity?

    @Query("SELECT * FROM quiz_questions WHERE quizAttemptId = :attemptId AND userId = :userId")
    suspend fun getQuestionsForAttempt(attemptId: String, userId: String): List<QuizQuestionEntity>

    @Query("SELECT * FROM quiz_questions WHERE userId = :userId")
    suspend fun getAllQuestionsByUser(userId: String): List<QuizQuestionEntity>

    @Query("DELETE FROM quiz_attempts WHERE id = :attemptId AND userId = :userId")
    suspend fun deleteAttempt(attemptId: String, userId: String)

    @Query("DELETE FROM quiz_questions WHERE quizAttemptId = :attemptId AND userId = :userId")
    suspend fun deleteQuestionsForAttempt(attemptId: String, userId: String)
}
