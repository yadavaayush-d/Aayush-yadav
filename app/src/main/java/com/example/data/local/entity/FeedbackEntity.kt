package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feedback",
    indices = [Index(value = ["user_id"])]
)
data class FeedbackEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val category: String, // "Bug", "Feature Request", "AI Quality", "Other"
    val message: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
