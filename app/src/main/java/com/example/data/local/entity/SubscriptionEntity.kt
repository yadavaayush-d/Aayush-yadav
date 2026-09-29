package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscriptions",
    indices = [Index(value = ["user_id"])]
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val plan: String, // "Free Tier", "Pro Brain", "Enterprise"
    val status: String, // "active", "trial", "cancelled"
    @ColumnInfo(name = "start_date") val startDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "renewal_date") val renewalDate: Long = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
)
