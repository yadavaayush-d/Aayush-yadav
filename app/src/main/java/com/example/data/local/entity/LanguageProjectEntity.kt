package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "language_projects",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["project_type"]),
        Index(value = ["created_at"])
    ]
)
data class LanguageProjectEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "project_type")
    val projectType: String, // translate, rewrite, grammar_check, summarize, detect_language, explain_text, language_practice, conversation_practice

    @ColumnInfo(name = "source_language")
    val sourceLanguage: String,

    @ColumnInfo(name = "target_language")
    val targetLanguage: String,

    @ColumnInfo(name = "input_text")
    val inputText: String,

    @ColumnInfo(name = "output_text")
    val outputText: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
