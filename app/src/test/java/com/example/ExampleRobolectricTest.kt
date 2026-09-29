package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.ai.AIModule
import com.example.domain.ai.AIRateLimiter
import com.example.domain.ai.ContextManager
import com.example.domain.ai.DefaultAIRouterService
import com.example.domain.ai.EntitlementCheckResult
import com.example.domain.ai.EntitlementManager
import com.example.domain.ai.RateLimitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nexa AI", appName)
  }

  @Test
  fun `ai router classifies study queries correctly`() {
    val router = DefaultAIRouterService()
    val result = router.classifyIntent("Can you solve this physics calculus equation for me?")
    assertEquals(AIModule.STUDY_AI, result.recommendedModule)
  }

  @Test
  fun `ai router classifies creator queries correctly`() {
    val router = DefaultAIRouterService()
    val result = router.classifyIntent("Write a 60 second YouTube video script about artificial intelligence")
    assertEquals(AIModule.CREATOR_AI, result.recommendedModule)
  }

  @Test
  fun `ai router classifies document queries correctly`() {
    val router = DefaultAIRouterService()
    val result = router.classifyIntent("Summarize this financial PDF document report")
    assertEquals(AIModule.DOCUMENT_AI, result.recommendedModule)
  }

  @Test
  fun `entitlement manager enforces free tier limit`() {
    val manager = EntitlementManager()
    val renewal = System.currentTimeMillis() + 100000L
    val allowed = manager.evaluateEntitlement("Free Tier", 49, renewal)
    assertTrue(allowed is EntitlementCheckResult.Allowed)

    val limitReached = manager.evaluateEntitlement("Free Tier", 50, renewal)
    assertTrue(limitReached is EntitlementCheckResult.LimitReached)
  }

  @Test
  fun `rate limiter tracks and limits rapid burst requests`() {
    val rateLimiter = AIRateLimiter(minIntervalMillis = 1000L, maxRequestsPerMinute = 5)
    val userId = "test_user_rate_limit"

    val first = rateLimiter.checkRateLimit(userId)
    assertTrue(first is RateLimitResult.Allowed)

    // Immediate second call should be blocked by burst interval
    val burstBlocked = rateLimiter.checkRateLimit(userId)
    assertTrue(burstBlocked is RateLimitResult.Blocked)
  }

  @Test
  fun `context manager builds structured turns`() {
    val contextManager = ContextManager(maxRecentMessages = 4)
    val mockMessages = listOf(
      com.example.data.local.entity.MessageEntity("1", "c1", "user", "Hello", 100L),
      com.example.data.local.entity.MessageEntity("2", "c1", "assistant", "Hi there!", 200L)
    )
    val turns = contextManager.buildContext(mockMessages)
    assertEquals(2, turns.size)
    assertEquals("user", turns[0].role)
    assertEquals("assistant", turns[1].role)
  }
}
