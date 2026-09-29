package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "language_practice_sessions",
    indices = [Index(value = ["user_id"])]
)
data class LanguagePracticeSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "language_to_learn")
    val languageToLearn: String,

    @ColumnInfo(name = "level")
    val level: String, // Beginner, Intermediate, Advanced

    @ColumnInfo(name = "topic")
    val topic: String,

    @ColumnInfo(name = "questions_attempted")
    val questionsAttempted: Int,

    @ColumnInfo(name = "correct_answers")
    val correctAnswers: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
