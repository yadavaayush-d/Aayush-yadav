package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quiz_attempts",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["subject"]),
        Index(value = ["createdAt"])
    ]
)
data class QuizAttemptEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val subject: String,
    val chapter: String,
    val topic: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val score: Int,
    val accuracy: Float,
    val timeTakenSeconds: Int,
    val createdAt: Long = System.currentTimeMillis()
)
