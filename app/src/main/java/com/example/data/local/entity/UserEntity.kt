package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    @ColumnInfo(name = "profile_image") val profileImage: String = "",
    @ColumnInfo(name = "preferred_language") val preferredLanguage: String = "English",
    @ColumnInfo(name = "user_type") val userType: String = "General User",
    @ColumnInfo(name = "subscription_plan") val subscriptionPlan: String = "Free Tier",
    @ColumnInfo(name = "is_admin") val isAdmin: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
