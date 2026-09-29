package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_usage",
    indices = [Index(value = ["user_id"]), Index(value = ["request_id"])]
)
data class AIUsageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "usage_type") val usageType: String, // "tokens", "queries", "pages"
    @ColumnInfo(name = "usage_amount") val usageAmount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
