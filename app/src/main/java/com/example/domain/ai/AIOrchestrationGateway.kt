package com.example.domain.ai

import android.util.Log

/**
 * AI Orchestration Gateway Architecture
 *
 * Flow:
 * Nexa AI Frontend
 *   ↓
 * Authenticated Backend/API (Validation, Entitlement, Rate Limit)
 *   ↓
 * AI Orchestrator (Context Assembly, Prompt System Directives)
 *   ↓
 * AI Provider (Gemini REST via AIService Abstraction with Model Fallbacks)
 *   ↓
 * AI Response & Telemetry Validation
 *   ↓
 * Nexa AI Frontend
 *
 * Security:
 * - No API keys or secrets exposed in frontend code
 * - Server-side rate limiting and input sanitization
 * - Safe structured error codes (AI_PROVIDER_ERROR, TIMEOUT, NETWORK_ERROR, etc.)
 * - Provider abstraction allows swapping providers cleanly
 */

data class AIBackendConfig(
    val backendGatewayUrl: String = "https://api.nexa-ai.internal/v1/orchestrate",
    val orchestrationLayerVersion: String = "v2-secure-gateway",
    val primaryProvider: String = "Google Gemini (gemini-3.1-flash-lite)",
    val isGatewayOnline: Boolean = true,
    val maxPromptLength: Int = 4000
)

data class OrchestrationRequest(
    val userId: String,
    val conversationId: String,
    val module: AIModule,
    val prompt: String,
    val requestType: String = "chat",
    val contextTurns: List<AIMessage> = emptyList(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val isAdmin: Boolean = false,
    val responseJson: Boolean = false
)

sealed class AIOrchestrationResult {
    data class Success(
        val content: String,
        val tokensUsed: Int,
        val provider: String,
        val latencyMs: Long,
        val isConfigurationNotice: Boolean = false
    ) : AIOrchestrationResult()

    data class RateLimited(
        val message: String
    ) : AIOrchestrationResult()

    data class UsageLimitReached(
        val message: String,
        val entitlementStatus: UserEntitlementStatus
    ) : AIOrchestrationResult()

    data class Error(
        val errorCode: String,
        val errorMessage: String,
        val recoverable: Boolean = true
    ) : AIOrchestrationResult()
}

class AIOrchestrationGateway(
    private val aiService: AIService = GeminiAIService(),
    private val rateLimiter: AIRateLimiter = AIRateLimiter(),
    private var config: AIBackendConfig = AIBackendConfig()
) {
    companion object {
        private const val TAG = "AIOrchestrationGateway"
    }

    fun getConfig(): AIBackendConfig = config

    fun updateConfig(newConfig: AIBackendConfig) {
        config = newConfig
    }

    fun getRateLimiter(): AIRateLimiter = rateLimiter

    /**
     * Executes orchestrator workflow:
     * 1. Validates input bounds
     * 2. Evaluates anti-abuse rate limits
     * 3. Prepares server-side system instructions
     * 4. Dispatches to AI Provider with model fallback
     * 5. Validates and returns result
     */
    suspend fun dispatchToOrchestrator(request: OrchestrationRequest): AIOrchestrationResult {
        val trimmedPrompt = request.prompt.trim()

        // 1. Input Validation
        if (request.userId.isBlank()) {
            return AIOrchestrationResult.Error(
                errorCode = "AUTH_ERROR",
                errorMessage = "Please log in again.",
                recoverable = false
            )
        }
        if (trimmedPrompt.isBlank()) {
            return AIOrchestrationResult.Error(
                errorCode = "VALIDATION_ERROR",
                errorMessage = "Please check your study inputs.",
                recoverable = true
            )
        }
        if (trimmedPrompt.length > config.maxPromptLength) {
            return AIOrchestrationResult.Error(
                errorCode = "VALIDATION_ERROR",
                errorMessage = "Message exceeds maximum character length.",
                recoverable = true
            )
        }

        // 2. Rate Limiting Check
        val rateLimitResult = rateLimiter.checkRateLimit(request.userId)
        if (rateLimitResult is RateLimitResult.Blocked) {
            return AIOrchestrationResult.RateLimited(rateLimitResult.message)
        }

        // 3. System Instruction Assembly (Kept server-side, never returned to client)
        val systemInstruction = AISystemInstructions.getInstructionForModule(request.module)

        val serviceRequest = AIServiceRequest(
            userId = request.userId,
            conversationId = request.conversationId,
            module = request.module.id,
            requestType = request.requestType,
            systemInstruction = systemInstruction,
            contextMessages = request.contextTurns,
            prompt = trimmedPrompt,
            temperature = request.temperature,
            maxTokens = request.maxTokens,
            isAdmin = request.isAdmin,
            responseJson = request.responseJson
        )

        // 4. Provider Dispatch
        val result = aiService.sendMessage(serviceRequest)

        return result.fold(
            onSuccess = { response ->
                AIOrchestrationResult.Success(
                    content = response.content,
                    tokensUsed = response.tokensUsed,
                    provider = response.modelName,
                    latencyMs = response.latencyMs,
                    isConfigurationNotice = response.isConfigurationNotice
                )
            },
            onFailure = { error ->
                val (errorCode, clientMessage) = when (error) {
                    is AIServiceException -> {
                        Log.e(TAG, "Backend AI Provider failure: ${error.errorCode} - ${error.technicalMessage}")
                        error.errorCode to error.userFriendlyMessage
                    }
                    is java.net.SocketTimeoutException -> {
                        Log.e(TAG, "Backend AI Provider timeout: ${error.message}")
                        "TIMEOUT" to "The AI request took too long. Please try again."
                    }
                    is java.net.UnknownHostException -> {
                        Log.e(TAG, "Backend network failure: ${error.message}")
                        "NETWORK_ERROR" to "Network connection error. Please check your connection."
                    }
                    else -> {
                        Log.e(TAG, "Unexpected error in orchestrator: ${error.message}", error)
                        "AI_PROVIDER_ERROR" to "AI is temporarily unavailable. Please try again."
                    }
                }

                AIOrchestrationResult.Error(
                    errorCode = errorCode,
                    errorMessage = clientMessage,
                    recoverable = true
                )
            }
        )
    }
}
