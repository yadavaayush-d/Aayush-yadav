package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_creator_content",
    indices = [Index(value = ["user_id"]), Index(value = ["project_id"])]
)
data class SavedCreatorContentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "project_id") val projectId: String? = null,
    val title: String,
    @ColumnInfo(name = "content_type") val contentType: String,
    val platform: String,
    val language: String,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
