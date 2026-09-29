package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quiz_questions",
    indices = [
        Index(value = ["quizAttemptId"]),
        Index(value = ["userId"])
    ]
)
data class QuizQuestionEntity(
    @PrimaryKey
    val id: String,
    val quizAttemptId: String,
    val userId: String,
    val question: String,
    val optionsJson: String,
    val selectedAnswer: String,
    val correctAnswer: String,
    val explanation: String
)
