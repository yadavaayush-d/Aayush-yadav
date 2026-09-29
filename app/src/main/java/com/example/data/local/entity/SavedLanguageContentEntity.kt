package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_language_content",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["project_id"])
    ]
)
data class SavedLanguageContentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "project_id")
    val projectId: String? = null,

    @ColumnInfo(name = "project_type")
    val projectType: String,

    @ColumnInfo(name = "source_language")
    val sourceLanguage: String,

    @ColumnInfo(name = "target_language")
    val targetLanguage: String,

    @ColumnInfo(name = "input_text")
    val inputText: String,

    @ColumnInfo(name = "output_text")
    val outputText: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
