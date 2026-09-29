package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NexaDatabase
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AIMessage
import com.example.domain.ai.AIModule
import com.example.domain.ai.AIOrchestrationGateway
import com.example.domain.ai.AIRateLimiter
import com.example.domain.ai.AIService
import com.example.domain.ai.AIServiceRequest
import com.example.domain.ai.AIServiceResponse
import com.example.domain.ai.ContextManager
import com.example.domain.ai.GeminiAIService
import com.example.domain.ai.OrchestrationRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step2AIChatIntegrationTest {

    private lateinit var database: NexaDatabase
    private lateinit var repository: NexaRepository
    private lateinit var mockAIService: TestAIService

    class TestAIService : AIService {
        var lastReceivedRequest: AIServiceRequest? = null
        var shouldSimulateError = false
        var simulatedErrorMessage = "Network connection failed"
        var configuredResponseText = "I am Nexa AI, your unified AI assistant capable of study tutoring, content creation, and document analysis."

        override suspend fun sendMessage(request: AIServiceRequest): Result<AIServiceResponse> {
            lastReceivedRequest = request
            if (shouldSimulateError) {
                return Result.failure(IOException(simulatedErrorMessage))
            }
            return Result.success(
                AIServiceResponse(
                    content = configuredResponseText,
                    tokensUsed = 42,
                    modelName = "gemini-3.5-flash",
                    latencyMs = 120
                )
            )
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NexaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mockAIService = TestAIService()
        val rateLimiter = AIRateLimiter(minIntervalMillis = 0L, maxRequestsPerMinute = 1000)
        val gateway = AIOrchestrationGateway(aiService = mockAIService, rateLimiter = rateLimiter)
        repository = NexaRepository(
            database = database,
            gateway = gateway
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `TEST 1 and 2 - Send Hello message and verify conversation persistence`() = runBlocking {
        // 1. User logs in
        val user = repository.signUp("Test User", "test@example.com", "password123", "English", "Student").getOrThrow()
        assertEquals(user.id, repository.currentUser.value?.id)

        // 2. User opens chat and creates conversation
        val convId = repository.createConversation(user.id, "General Session", AIModule.GENERAL_AI.title)

        // 3. User sends "Hello, what can you do?"
        val sendResult = repository.sendMessage(convId, "Hello, what can you do?", AIModule.GENERAL_AI)
        assertTrue(sendResult.isSuccess)

        // 4. Verify AI provider was invoked with user prompt
        assertNotNull(mockAIService.lastReceivedRequest)
        assertEquals("Hello, what can you do?", mockAIService.lastReceivedRequest?.prompt)

        // 5. Verify conversation and both messages saved in database
        val messages = repository.getMessages(convId).first()
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("Hello, what can you do?", messages[0].content)
        assertEquals("assistant", messages[1].role)
        assertEquals(mockAIService.configuredResponseText, messages[1].content)

        // Verify active conversation remains available
        assertEquals(convId, repository.activeConversationId.value)
    }

    @Test
    fun `TEST 3 and 4 - Logout, Login again, and retrieve conversation from History`() = runBlocking {
        // 1. User 1 logs in and chats
        val user = repository.signUp("Alice", "alice@example.com", "pass123", "English", "Student").getOrThrow()
        val convId = repository.createConversation(user.id, "Alice Chat", AIModule.STUDY_AI.title)
        repository.sendMessage(convId, "Hello, what can you do?", AIModule.STUDY_AI)

        // 2. User logs out
        repository.logout()
        assertEquals(null, repository.currentUser.value)
        assertEquals(null, repository.activeConversationId.value)

        // 3. User logs in again
        val loggedIn = repository.login("alice@example.com", "pass123").getOrThrow()
        assertEquals(user.id, loggedIn.id)

        // 4. Verify conversation appears in History
        val historyList = repository.getConversations(loggedIn.id).first()
        assertTrue("Previous conversation should be present in history", historyList.any { it.id == convId })

        // 5. Open conversation from History and verify messages are fully visible
        val messages = repository.getMessages(convId).first()
        assertEquals(2, messages.size)
        assertEquals("Hello, what can you do?", messages[0].content)
        assertEquals("assistant", messages[1].role)
    }

    @Test
    fun `TEST 5 - Send second message and verify multi-turn conversation context is passed`() = runBlocking {
        val user = repository.signUp("Bob", "bob@example.com", "pass123", "English", "Creator").getOrThrow()
        val convId = repository.createConversation(user.id, "Multi-turn", AIModule.CREATOR_AI.title)

        // Message 1
        repository.sendMessage(convId, "Hello, what can you do?", AIModule.CREATOR_AI)
        assertEquals(0, mockAIService.lastReceivedRequest?.contextMessages?.size)

        // Message 2 in same conversation
        repository.sendMessage(convId, "Can you help me write a title?", AIModule.CREATOR_AI)

        // Verify that prior turns were passed to the AI provider
        val context = mockAIService.lastReceivedRequest?.contextMessages
        assertNotNull(context)
        assertEquals(2, context?.size)
        assertEquals("user", context?.get(0)?.role)
        assertEquals("Hello, what can you do?", context?.get(0)?.content)
        assertEquals("assistant", context?.get(1)?.role)
        assertEquals("Can you help me write a title?", mockAIService.lastReceivedRequest?.prompt)

        // Verify all 4 messages are persisted in DB
        val allMessages = repository.getMessages(convId).first()
        assertEquals(4, allMessages.size)
    }

    @Test
    fun `TEST 6 - Error handling returns friendly message without technical leaks`() = runBlocking {
        val user = repository.signUp("Eve", "eve@example.com", "pass123", "English", "General").getOrThrow()
        val convId = repository.createConversation(user.id, "Error Test", AIModule.GENERAL_AI.title)

        // Simulate provider error
        mockAIService.shouldSimulateError = true
        mockAIService.simulatedErrorMessage = "HTTP 500 Internal Server Error at com.google.api.client.googleapis.services..."

        val result = repository.sendMessage(convId, "Trigger error", AIModule.GENERAL_AI)
        assertTrue(result.isFailure)

        val errorMsg = result.exceptionOrNull()?.message ?: ""
        // Must show friendly error message
        assertEquals("AI is temporarily unavailable. Please try again.", errorMsg)

        // Must NOT expose raw server stack traces or technical details
        assertFalse(errorMsg.contains("Internal Server Error"))
        assertFalse(errorMsg.contains("com.google"))
        assertFalse(errorMsg.contains("Exception"))
        assertFalse(errorMsg.contains("API_KEY"))
    }

    @Test
    fun `TEST - Missing API key returns clear setup error for developer or admin`() = runBlocking {
        // Test GeminiAIService with unconfigured key
        val unconfiguredService = GeminiAIService(explicitApiKey = "")

        val adminReq = AIServiceRequest(
            userId = "admin1",
            conversationId = "c1",
            systemInstruction = "",
            prompt = "Hello",
            isAdmin = true
        )
        val adminResult = unconfiguredService.sendMessage(adminReq)
        assertTrue(adminResult.isFailure)
        val adminMsg = adminResult.exceptionOrNull()?.message ?: ""
        assertTrue(adminMsg.contains("AI provider is not configured"))
        assertTrue(adminMsg.contains("GEMINI_API_KEY"))

        val normalUserReq = AIServiceRequest(
            userId = "u1",
            conversationId = "c1",
            systemInstruction = "",
            prompt = "Hello",
            isAdmin = false
        )
        val userResult = unconfiguredService.sendMessage(normalUserReq)
        assertTrue(userResult.isFailure)
        val userMsg = userResult.exceptionOrNull()?.message ?: ""
        assertTrue(userMsg.contains("AI provider is not configured"))
        assertFalse(userMsg.contains("GEMINI_API_KEY")) // Do not expose config keys to standard users
    }
}
