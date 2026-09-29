package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [Index(value = ["user_id"])]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_type") val fileType: String, // "PDF", "TXT", "DOCX"
    @ColumnInfo(name = "file_size") val fileSize: Long = 0L,
    @ColumnInfo(name = "storage_path") val storagePath: String = "",
    @ColumnInfo(name = "file_url") val fileUrl: String = "",
    @ColumnInfo(name = "processing_status") val processingStatus: String, // "Uploading", "Processing", "Ready", "Failed"
    @ColumnInfo(name = "extracted_text_status") val extractedTextStatus: String = "pending", // "pending", "extracted", "failed"
    @ColumnInfo(name = "extracted_text") val extractedText: String = "",
    @ColumnInfo(name = "page_count") val pageCount: Int = 1,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
