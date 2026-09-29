package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    // -------------------------------------------------------------
    // Documents Table (Section 3, 4, 6, 19)
    // -------------------------------------------------------------
    @Query("SELECT * FROM documents WHERE user_id = :userId ORDER BY created_at DESC")
    fun getDocumentsByUser(userId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getDocumentsByUserSync(userId: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :documentId AND user_id = :userId LIMIT 1")
    suspend fun getDocumentByIdAndUser(documentId: String, userId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentByIdRaw(documentId: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity)

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :documentId AND user_id = :userId")
    suspend fun deleteDocument(documentId: String, userId: String): Int

    @Query("SELECT * FROM documents ORDER BY created_at DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    // -------------------------------------------------------------
    // Document Chunks / Retrieval Index (Section 11, 14, 19)
    // -------------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("SELECT * FROM document_chunks WHERE document_id = :documentId AND user_id = :userId ORDER BY chunk_index ASC")
    suspend fun getChunksForDocument(documentId: String, userId: String): List<DocumentChunkEntity>

    @Query("DELETE FROM document_chunks WHERE document_id = :documentId AND user_id = :userId")
    suspend fun deleteChunksForDocument(documentId: String, userId: String): Int

    // -------------------------------------------------------------
    // Document Notes (Section 12, 19)
    // -------------------------------------------------------------
    @Query("SELECT * FROM document_notes WHERE document_id = :documentId AND user_id = :userId ORDER BY updated_at DESC")
    fun getNotesForDocumentFlow(documentId: String, userId: String): Flow<List<DocumentNoteEntity>>

    @Query("SELECT * FROM document_notes WHERE document_id = :documentId AND user_id = :userId ORDER BY updated_at DESC")
    suspend fun getNotesForDocumentSync(documentId: String, userId: String): List<DocumentNoteEntity>

    @Query("SELECT * FROM document_notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteByIdRaw(noteId: String): DocumentNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: DocumentNoteEntity)

    @Update
    suspend fun updateNote(note: DocumentNoteEntity)

    @Query("DELETE FROM document_notes WHERE id = :noteId AND user_id = :userId")
    suspend fun deleteNote(noteId: String, userId: String): Int

    @Query("DELETE FROM document_notes WHERE document_id = :documentId AND user_id = :userId")
    suspend fun deleteNotesForDocument(documentId: String, userId: String): Int
}
