package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LanguagePracticeSessionEntity
import com.example.data.local.entity.LanguageProjectEntity
import com.example.data.local.entity.SavedLanguageContentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageDao {

    // -------------------------------------------------------------
    // LanguageProjects (History)
    // -------------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: LanguageProjectEntity)

    @Update
    suspend fun updateProject(project: LanguageProjectEntity)

    @Query("SELECT * FROM language_projects WHERE user_id = :userId ORDER BY updated_at DESC")
    fun getProjectsByUser(userId: String): Flow<List<LanguageProjectEntity>>

    @Query("SELECT * FROM language_projects WHERE user_id = :userId ORDER BY updated_at DESC")
    suspend fun getProjectsByUserSync(userId: String): List<LanguageProjectEntity>

    @Query("SELECT * FROM language_projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectByIdRaw(projectId: String): LanguageProjectEntity?

    @Query("SELECT * FROM language_projects WHERE id = :projectId AND user_id = :userId LIMIT 1")
    suspend fun getProjectByIdForUser(projectId: String, userId: String): LanguageProjectEntity?

    @Query("DELETE FROM language_projects WHERE id = :projectId AND user_id = :userId")
    suspend fun deleteProject(projectId: String, userId: String): Int

    // -------------------------------------------------------------
    // SavedLanguageContent (Favorites / Saved Translations & Rewrites)
    // -------------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedContent(saved: SavedLanguageContentEntity)

    @Query("SELECT * FROM saved_language_content WHERE user_id = :userId ORDER BY created_at DESC")
    fun getSavedContentByUser(userId: String): Flow<List<SavedLanguageContentEntity>>

    @Query("SELECT * FROM saved_language_content WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getSavedContentByUserSync(userId: String): List<SavedLanguageContentEntity>

    @Query("SELECT * FROM saved_language_content WHERE id = :savedId LIMIT 1")
    suspend fun getSavedContentByIdRaw(savedId: String): SavedLanguageContentEntity?

    @Query(
        """
        SELECT * FROM saved_language_content 
        WHERE user_id = :userId 
          AND project_type = :projectType 
          AND input_text = :inputText 
          AND output_text = :outputText 
        LIMIT 1
        """
    )
    suspend fun findExistingSavedContent(
        userId: String,
        projectType: String,
        inputText: String,
        outputText: String
    ): SavedLanguageContentEntity?

    @Query("DELETE FROM saved_language_content WHERE id = :savedId AND user_id = :userId")
    suspend fun deleteSavedContent(savedId: String, userId: String): Int

    // -------------------------------------------------------------
    // LanguagePracticeSessions (Practice Progress Tracking)
    // -------------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPracticeSession(session: LanguagePracticeSessionEntity)

    @Update
    suspend fun updatePracticeSession(session: LanguagePracticeSessionEntity)

    @Query("SELECT * FROM language_practice_sessions WHERE user_id = :userId ORDER BY updated_at DESC")
    fun getPracticeSessionsByUser(userId: String): Flow<List<LanguagePracticeSessionEntity>>

    @Query("SELECT * FROM language_practice_sessions WHERE user_id = :userId ORDER BY updated_at DESC")
    suspend fun getPracticeSessionsByUserSync(userId: String): List<LanguagePracticeSessionEntity>

    @Query("SELECT * FROM language_practice_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getPracticeSessionByIdRaw(sessionId: String): LanguagePracticeSessionEntity?
}
