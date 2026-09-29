package com.example.domain.ai

import java.util.concurrent.ConcurrentHashMap

sealed class RateLimitResult {
    object Allowed : RateLimitResult()
    data class Blocked(val message: String) : RateLimitResult()
}

/**
 * Server-side protection against abuse and rapid automated requests.
 * Enforces both a minimum burst interval between calls and a max window volume.
 */
class AIRateLimiter(
    private val minIntervalMillis: Long = 1500L, // 1.5 seconds minimum between requests
    private val maxRequestsPerMinute: Int = 15
) {
    private val userLastRequestTime = ConcurrentHashMap<String, Long>()
    private val userRecentRequests = ConcurrentHashMap<String, MutableList<Long>>()

    @Synchronized
    fun checkRateLimit(userId: String): RateLimitResult {
        val now = System.currentTimeMillis()

        // 1. Anti-burst rapid spam check
        val lastTime = userLastRequestTime[userId]
        if (lastTime != null && (now - lastTime) < minIntervalMillis) {
            return RateLimitResult.Blocked("Please wait a moment before sending another request.")
        }

        // 2. Rolling 60-second window
        val windowStart = now - 60_000L
        val timestamps = userRecentRequests.getOrPut(userId) { mutableListOf() }
        timestamps.removeAll { it < windowStart }

        if (timestamps.size >= maxRequestsPerMinute) {
            return RateLimitResult.Blocked("Rate limit exceeded ($maxRequestsPerMinute requests/min). Please slow down.")
        }

        // Allowed: record
        timestamps.add(now)
        userLastRequestTime[userId] = now
        return RateLimitResult.Allowed
    }

    fun resetForUser(userId: String) {
        userLastRequestTime.remove(userId)
        userRecentRequests.remove(userId)
    }

    fun clearAll() {
        userLastRequestTime.clear()
        userRecentRequests.clear()
    }
}
