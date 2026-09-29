package com.example.domain.ai

import java.io.IOException

data class AIStructuredError(
    val success: Boolean = false,
    val errorCode: String,
    val message: String
)

open class AIServiceException(
    val errorCode: String,
    val userFriendlyMessage: String,
    val technicalMessage: String = userFriendlyMessage,
    cause: Throwable? = null
) : IOException(userFriendlyMessage, cause) {
    fun toStructuredError(): AIStructuredError = AIStructuredError(
        success = false,
        errorCode = errorCode,
        message = userFriendlyMessage
    )
}

class AIAuthException(
    userFriendlyMessage: String = "Please log in again.",
    technicalMessage: String = userFriendlyMessage
) : AIServiceException("AUTH_ERROR", userFriendlyMessage, technicalMessage)

class AIValidationException(
    userFriendlyMessage: String = "Please check your study inputs.",
    technicalMessage: String = userFriendlyMessage
) : AIServiceException("VALIDATION_ERROR", userFriendlyMessage, technicalMessage)

class AIUsageLimitException(
    userFriendlyMessage: String = "You have reached your current AI usage limit.",
    technicalMessage: String = userFriendlyMessage
) : AIServiceException("USAGE_LIMIT", userFriendlyMessage, technicalMessage)

class AIDatabaseException(
    userFriendlyMessage: String = "Your answer could not be saved. Please try again.",
    technicalMessage: String = userFriendlyMessage,
    cause: Throwable? = null
) : AIServiceException("DATABASE_ERROR", userFriendlyMessage, technicalMessage, cause)

class AIProviderNotConfiguredException(
    userFriendlyMessage: String = "AI provider is not configured. Please contact the administrator.",
    technicalMessage: String = userFriendlyMessage
) : AIServiceException("AI_PROVIDER_ERROR", userFriendlyMessage, technicalMessage)

class AIProviderUnavailableException(
    userFriendlyMessage: String = "AI service is temporarily unavailable. Please try again.",
    technicalMessage: String = userFriendlyMessage,
    cause: Throwable? = null
) : AIServiceException("AI_PROVIDER_ERROR", userFriendlyMessage, technicalMessage, cause)

class AITimeoutException(
    userFriendlyMessage: String = "The AI request took too long. Please try again.",
    technicalMessage: String = userFriendlyMessage,
    cause: Throwable? = null
) : AIServiceException("TIMEOUT", userFriendlyMessage, technicalMessage, cause)

class AINetworkException(
    userFriendlyMessage: String = "Network connection error. Please check your connection.",
    technicalMessage: String = userFriendlyMessage,
    cause: Throwable? = null
) : AIServiceException("NETWORK_ERROR", userFriendlyMessage, technicalMessage, cause)

data class AIMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)

data class AIServiceRequest(
    val userId: String,
    val conversationId: String,
    val module: String = "general",
    val requestType: String = "chat",
    val systemInstruction: String,
    val contextMessages: List<AIMessage> = emptyList(),
    val prompt: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val isAdmin: Boolean = false,
    val responseJson: Boolean = false
)

data class AIServiceResponse(
    val content: String,
    val tokensUsed: Int,
    val modelName: String,
    val latencyMs: Long,
    val isConfigurationNotice: Boolean = false
)

interface AIService {
    suspend fun sendMessage(request: AIServiceRequest): Result<AIServiceResponse>

    // Conceptual future stubs for subsequent steps:
    suspend fun analyzeImage(userId: String, imageBase64: String, prompt: String): Result<AIServiceResponse> {
        return Result.failure(UnsupportedOperationException("analyzeImage will be implemented in a future step"))
    }

    suspend fun analyzeDocument(userId: String, documentText: String, query: String): Result<AIServiceResponse> {
        return Result.failure(UnsupportedOperationException("analyzeDocument will be implemented in a future step"))
    }

    suspend fun generateContent(userId: String, template: String, variables: Map<String, String>): Result<AIServiceResponse> {
        return Result.failure(UnsupportedOperationException("generateContent will be implemented in a future step"))
    }
}
