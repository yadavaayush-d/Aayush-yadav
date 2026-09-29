package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "study_plans",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["createdAt"])
    ]
)
data class StudyPlanEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val classLevel: String,
    val subjects: String,
    val examDate: String,
    val hoursPerDay: String,
    val preferredDays: String,
    val strongSubjects: String,
    val weakTopics: String,
    val planContent: String,
    val createdAt: Long = System.currentTimeMillis()
)
