package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "study_activities",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["activityType"]),
        Index(value = ["createdAt"])
    ]
)
data class StudyActivityEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val activityType: String, // doubt, explain_topic, mcq, practice, quiz, explain_answer, study_plan, summary
    val title: String,
    val subject: String,
    val chapter: String,
    val topic: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
