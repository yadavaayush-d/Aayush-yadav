package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CreatorProjectEntity
import com.example.data.local.entity.SavedCreatorContentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreatorDao {
    // -------------------------------------------------------------
    // CreatorProjects (Section 11)
    // -------------------------------------------------------------

    @Query("SELECT * FROM creator_projects WHERE user_id = :userId ORDER BY updated_at DESC")
    fun getProjectsByUser(userId: String): Flow<List<CreatorProjectEntity>>

    @Query("SELECT * FROM creator_projects WHERE user_id = :userId ORDER BY updated_at DESC")
    suspend fun getProjectsByUserSync(userId: String): List<CreatorProjectEntity>

    @Query("SELECT * FROM creator_projects WHERE id = :projectId AND user_id = :userId LIMIT 1")
    suspend fun getProjectByIdAndUser(projectId: String, userId: String): CreatorProjectEntity?

    @Query("SELECT * FROM creator_projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectByIdRaw(projectId: String): CreatorProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: CreatorProjectEntity)

    @Update
    suspend fun updateProject(project: CreatorProjectEntity)

    @Query("UPDATE creator_projects SET title = :newTitle, updated_at = :updatedAt WHERE id = :projectId AND user_id = :userId")
    suspend fun renameProject(projectId: String, userId: String, newTitle: String, updatedAt: Long): Int

    @Query("DELETE FROM creator_projects WHERE id = :projectId AND user_id = :userId")
    suspend fun deleteProject(projectId: String, userId: String): Int

    // -------------------------------------------------------------
    // Saved / Favorite Creator Content (Section 12)
    // -------------------------------------------------------------

    @Query("SELECT * FROM saved_creator_content WHERE user_id = :userId ORDER BY created_at DESC")
    fun getSavedContentByUser(userId: String): Flow<List<SavedCreatorContentEntity>>

    @Query("SELECT * FROM saved_creator_content WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getSavedContentByUserSync(userId: String): List<SavedCreatorContentEntity>

    @Query("SELECT * FROM saved_creator_content WHERE id = :savedId AND user_id = :userId LIMIT 1")
    suspend fun getSavedItemByIdAndUser(savedId: String, userId: String): SavedCreatorContentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedContent(item: SavedCreatorContentEntity)

    @Query("DELETE FROM saved_creator_content WHERE id = :savedId AND user_id = :userId")
    suspend fun deleteSavedContent(savedId: String, userId: String): Int

    @Query("DELETE FROM saved_creator_content WHERE project_id = :projectId AND user_id = :userId")
    suspend fun deleteSavedByProjectId(projectId: String, userId: String): Int
}
