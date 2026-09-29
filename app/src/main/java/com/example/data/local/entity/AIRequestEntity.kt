package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_requests",
    indices = [Index(value = ["user_id"]), Index(value = ["conversation_id"])]
)
data class AIRequestEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val module: String,
    @ColumnInfo(name = "request_type") val requestType: String, // "chat", "document_summary", "translation", etc.
    val status: String, // "completed", "pending", "failed", "backend_ready"
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
