package com.example.data.repository

import com.example.data.local.NexaDatabase
import com.example.data.local.entity.AIRequestEntity
import com.example.data.local.entity.AIUsageEntity
import com.example.data.local.entity.LanguagePracticeSessionEntity
import com.example.data.local.entity.LanguageProjectEntity
import com.example.data.local.entity.SavedLanguageContentEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.ai.AIAuthException
import com.example.domain.ai.AIDatabaseException
import com.example.domain.ai.AIModule
import com.example.domain.ai.AINetworkException
import com.example.domain.ai.AIOrchestrationGateway
import com.example.domain.ai.AIOrchestrationResult
import com.example.domain.ai.AIProviderUnavailableException
import com.example.domain.ai.AIServiceException
import com.example.domain.ai.AITimeoutException
import com.example.domain.ai.AIUsageLimitException
import com.example.domain.ai.AIValidationException
import com.example.domain.ai.EntitlementCheckResult
import com.example.domain.ai.EntitlementManager
import com.example.domain.ai.OrchestrationRequest
import com.example.domain.language.ConversationPracticeTurnResult
import com.example.domain.language.ExplainTextResult
import com.example.domain.language.GrammarCheckResult
import com.example.domain.language.LanguageConfigurationException
import com.example.domain.language.LanguageDetectionResult
import com.example.domain.language.LanguageEmptyTextException
import com.example.domain.language.LanguageInvalidInputException
import com.example.domain.language.LanguageOptions
import com.example.domain.language.LanguagePermissionException
import com.example.domain.language.LanguagePracticeResult
import com.example.domain.language.LanguagePromptHelper
import com.example.domain.language.LanguageRequestType
import com.example.domain.language.LanguageRewriteResult
import com.example.domain.language.LanguageSummaryResult
import com.example.domain.language.PracticeExerciseItem
import com.example.domain.language.PracticeProgressStats
import com.example.domain.language.TranslationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class LanguageAIRepository(
    private val database: NexaDatabase,
    private val gateway: AIOrchestrationGateway = AIOrchestrationGateway(),
    private val entitlementManager: EntitlementManager = EntitlementManager(),
    private val currentUserProvider: () -> UserEntity?
) {

    private fun getAuthenticatedUser(): UserEntity {
        return currentUserProvider() ?: throw AIAuthException("Please log in to use Language AI.")
    }

    private fun validateUserText(text: String): Result<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(LanguageEmptyTextException("Please enter text to process."))
        }
        if (trimmed.length > 4000) {
            return Result.failure(
                LanguageInvalidInputException("Input text exceeds the 4,000-character limit. Please shorten your text.")
            )
        }
        return Result.success(trimmed)
    }

    private suspend fun verifyEntitlementAndLog(
        user: UserEntity,
        requestType: LanguageRequestType
    ): Result<String> {
        return try {
            val currentPeriodStart = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val usedMessages = database.aiRequestDao().getUserCompletedRequestsCountSinceSync(user.id, currentPeriodStart)
            val renewalDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000

            val entitlementCheck = entitlementManager.evaluateEntitlement(
                planName = user.subscriptionPlan,
                usedMessagesThisPeriod = usedMessages,
                renewalDateEpochMs = renewalDate
            )

            val requestId = UUID.randomUUID().toString()

            if (entitlementCheck is EntitlementCheckResult.LimitReached) {
                database.aiRequestDao().insertRequest(
                    AIRequestEntity(
                        id = requestId,
                        userId = user.id,
                        conversationId = "language_workspace",
                        module = "language",
                        requestType = requestType.id,
                        status = "limit_reached",
                        createdAt = System.currentTimeMillis()
                    )
                )
                return Result.failure(AIUsageLimitException(entitlementCheck.message))
            }

            database.aiRequestDao().insertRequest(
                AIRequestEntity(
                    id = requestId,
                    userId = user.id,
                    conversationId = "language_workspace",
                    module = "language",
                    requestType = requestType.id,
                    status = "pending",
                    createdAt = System.currentTimeMillis()
                )
            )
            Result.success(requestId)
        } catch (e: AIServiceException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AIDatabaseException("Could not verify usage limits. Please try again."))
        }
    }

    private suspend fun recordUsageSuccess(user: UserEntity, requestId: String) {
        try {
            database.aiRequestDao().updateStatus(requestId, "completed")
            database.aiUsageDao().insertUsage(
                AIUsageEntity(
                    id = UUID.randomUUID().toString(),
                    userId = user.id,
                    requestId = requestId,
                    usageType = "messages",
                    usageAmount = 1,
                    createdAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
        }
    }

    private suspend fun recordUsageFailure(requestId: String, status: String) {
        try {
            database.aiRequestDao().updateStatus(requestId, status)
        } catch (_: Exception) {
        }
    }

    private fun mapOrchestrationError(res: AIOrchestrationResult): AIServiceException {
        return when (res) {
            is AIOrchestrationResult.RateLimited -> AIProviderUnavailableException(res.message)
            is AIOrchestrationResult.UsageLimitReached -> AIUsageLimitException(res.message)
            is AIOrchestrationResult.Error -> {
                when (res.errorCode) {
                    "AUTH_ERROR" -> AIAuthException(res.errorMessage)
                    "EMPTY_TEXT" -> LanguageEmptyTextException(res.errorMessage)
                    "INVALID_INPUT", "VALIDATION_ERROR" -> LanguageInvalidInputException(res.errorMessage)
                    "LANGUAGE_CONFIG_ERROR" -> LanguageConfigurationException(res.errorMessage)
                    "USAGE_LIMIT" -> AIUsageLimitException(res.errorMessage)
                    "NETWORK_ERROR" -> AINetworkException(res.errorMessage)
                    "TIMEOUT" -> AITimeoutException(res.errorMessage)
                    "DATABASE_ERROR" -> AIDatabaseException(res.errorMessage)
                    else -> AIProviderUnavailableException(res.errorMessage)
                }
            }
            is AIOrchestrationResult.Success -> AIProviderUnavailableException("Unexpected state")
        }
    }

    private suspend fun saveLanguageProjectInternal(
        userId: String,
        projectType: LanguageRequestType,
        sourceLanguage: String,
        targetLanguage: String,
        inputText: String,
        outputText: String
    ): LanguageProjectEntity {
        try {
            val now = System.currentTimeMillis()
            val entity = LanguageProjectEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                projectType = projectType.id,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                inputText = inputText,
                outputText = outputText,
                createdAt = now,
                updatedAt = now
            )
            database.languageDao().insertProject(entity)
            return entity
        } catch (e: Exception) {
            throw AIDatabaseException("Could not save Language AI project to history.")
        }
    }

    // -------------------------------------------------------------
    // 2 & 3. TRANSLATOR & HINGLISH SUPPORT (translate)
    // -------------------------------------------------------------
    suspend fun translateText(
        sourceLanguage: String = LanguageOptions.AUTO_DETECT,
        targetLanguage: String = "Hindi",
        text: String
    ): Result<TranslationResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val textValidation = validateUserText(text)
        if (textValidation.isFailure) return Result.failure(textValidation.exceptionOrNull()!!)
        val cleanText = textValidation.getOrThrow()

        val validSource = LanguagePromptHelper.validateLanguageSelection(sourceLanguage, allowAutoDetect = true)
        if (validSource.isFailure) return Result.failure(validSource.exceptionOrNull()!!)
        val validTarget = LanguagePromptHelper.validateLanguageSelection(targetLanguage, allowAutoDetect = false)
        if (validTarget.isFailure) return Result.failure(validTarget.exceptionOrNull()!!)

        val srcLang = validSource.getOrThrow()
        val tgtLang = validTarget.getOrThrow()

        val reqType = LanguageRequestType.TRANSLATE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = LanguagePromptHelper.buildTranslationPrompt(srcLang, tgtLang, cleanText)
        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "lang_translate_${UUID.randomUUID()}",
            module = AIModule.LANGUAGE_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1536,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val parsed = LanguagePromptHelper.parseTranslationOutput(
                        projectId = "",
                        sourceLanguage = srcLang,
                        targetLanguage = tgtLang,
                        inputText = cleanText,
                        raw = res.content
                    )
                    val proj = saveLanguageProjectInternal(
                        userId = user.id,
                        projectType = reqType,
                        sourceLanguage = srcLang,
                        targetLanguage = tgtLang,
                        inputText = cleanText,
                        outputText = parsed.translatedText
                    )
                    Result.success(parsed.copy(projectId = proj.id))
                } catch (e: AIServiceException) {
                    Result.failure(e)
                }
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(mapOrchestrationError(res))
            }
        }
    }

    // -------------------------------------------------------------
    // 4. REWRITE (rewrite)
    // -------------------------------------------------------------
    suspend fun rewriteText(
        text: String,
        styleOption: String = "Make simpler",
        language: String = "English"
    ): Result<LanguageRewriteResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val textValidation = validateUserText(text)
        if (textValidation.isFailure) return Result.failure(textValidation.exceptionOrNull()!!)
        val cleanText = textValidation.getOrThrow()

        val validLang = LanguagePromptHelper.validateLanguageSelection(language, allowAutoDetect = false)
        if (validLang.isFailure) return Result.failure(validLang.exceptionOrNull()!!)
        val resolvedLang = validLang.getOrThrow()

        val reqType = LanguageRequestType.REWRITE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = LanguagePromptHelper.buildRewritePrompt(cleanText, styleOption, resolvedLang)
        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "lang_rewrite_${UUID.randomUUID()}",
            module = AIModule.LANGUAGE_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1536,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveLanguageProjectInternal(
                        userId = user.id,
                        projectType = reqType,
                        sourceLanguage = resolvedLang,
                        targetLanguage = resolvedLang,
                        inputText = cleanText,
                        outputText = res.content.trim()
                    )
                    Result.success(
                        LanguageRewriteResult(
                            projectId = proj.id,
                            styleOption = styleOption,
                            language = resolvedLang,
                            inputText = cleanText,
                            rewrittenText = res.content.trim()
                        )
                    )
                } catch (e: AIServiceException) {
                    Result.failure(e)
                }
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(mapOrchestrationError(res))
            }
        }
    }

    // -------------------------------------------------------------
    // 5. GRAMMAR CHECK (grammar_check)
    // -------------------------------------------------------------
    suspend fun checkGrammar(
        text: String,
        language: String = "English"
    ): Result<GrammarCheckResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val textValidation = validateUserText(text)
        if (textValidation.isFailure) return Result.failure(textValidation.exceptionOrNull()!!)
        val cleanText = textValidation.getOrThrow()

        val validLang = LanguagePromptHelper.validateLanguageSelection(language, allowAutoDetect = false)
        if (validLang.isFailure) return Result.failure(validLang.exceptionOrNull()!!)
        val resolvedLang = validLang.getOrThrow()

        val reqType = LanguageRequestType.GRAMMAR_CHECK
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = LanguagePromptHelper.buildGrammarCheckPrompt(cleanText, resolvedLang)
        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "lang_grammar_${UUID.randomUUID()}",
            module = AIModule.LANGUAGE_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1536,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveLanguageProjectInternal(
                        userId = user.id,
                        projectType = reqType,
                        sourceLanguage = resolvedLang,
                        targetLanguage = resolvedLang,
                        inputText = cleanText,
                        outputText = res.content.trim()
                    )
                    val parsed = LanguagePromptHelper.parseGrammarCheckResult(
                        projectId = proj.id,
                        inputText = cleanText,
                        raw = res.content
                    )
                    Result.success(parsed)
                } catch (e: AIServiceException) {
                    Result.failure(e)
                }
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(mapOrchestrationError(res))
            }
        }
    }

    // -------------------------------------------------------------
    // 6. LANGUAGE DETECTION (detect_language)
    // -------------------------------------------------------------
    suspend fun detectLanguage(text: String): Result<LanguageDetectionResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val textValidation = validateUserText(text)
        if (textValidation.isFailure) return Result.failure(textValidation.exceptionOrNull()!!)
        val cleanText = textValidation.getOrThrow()

        val reqType = LanguageRequestType.DETECT_LANGUAGE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = LanguagePromptHelper.buildLanguageDetectionPrompt(cleanText)
        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "lang_detect_${UUID.randomUUID()}",
            module = AIModule.LANGUAGE_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1024,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val parsed = LanguagePromptHelper.parseLanguageDetectionResult(
                        projectId = "",
                        inputText = cleanText,
                        raw = res.content
                    )
                    val proj = saveLanguageProjectInternal(
                        userId = user.id,
                        projectType = reqType,
                        sourceLanguage = LanguageOptions.AUTO_DETECT,
                        targetLanguage = parsed.detectedLanguage,
                        inputText = cleanText,
                        outputText = res.content.trim()
                    )
                    Result.success(parsed.copy(projectId = proj.id))
                } catch (e: AIServiceException) {
                    Result.failure(e)
                }
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(mapOrchestrationError(res))
            }
        }
    }

    // -------------------------------------------------------------
    // 7. SUMMARIZE (summarize)
    // -------------------------------------------------------------
    suspend fun summarizeText(
        text: String,
        outputLanguage: String = "English",
        summaryLength: String = "Short"
    ): Result<LanguageSummaryResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val textValidation = validateUserText(text)
        if (textValidation.isFailure) return Result.failure(textValidation.exceptionOrNull()!!)
        val cleanText = textValidation.getOrThrow()

        val validLang = LanguagePromptHelper.validateLanguageSelection(outputLanguage, allowAutoDetect = false)
        if (validLang.isFailure) return Result.failure(validLang.exceptionOrNull()!!)
        val resolvedLang = validLang.getOrThrow()

        val reqType = LanguageRequestType.SUMMARIZE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = LanguagePromptHelper.buildSummarizePrompt(cleanText, resolvedLang, summaryLength)
        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "lang_summary_${UUID.randomUUID()}",
            module = AIModule.LANGUAGE_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1536,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveLanguageProjectInternal(
                        userId = user.id,
                        projectType = reqType,
                        sourceLanguage = LanguageOptions.AUTO_DETECT,
                        targetLanguage = resolvedLang,
                        inputText = cleanText,
                        outputText = res.content.trim()
                    )
                    Result.success(
                        LanguageSummaryResult(
                            projectId = proj.id,
                            outputLanguage = resolvedLang,
                            summaryLength = summaryLength,
                            inputText = cleanText,
                            summaryText = res.content.trim()
                        )
                    )
                } catch (e: AIServiceException) {
                    Result.failure(e)
                }
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(mapOrchestrationError(res))
            }
        }
    }

    // -------------------------------------------------------------
    // 8. EXPLAIN TEXT (explain_text)
    // -------------------------------------------------------------
    suspend fun explainText(
        text: String,
        explanationLanguage: String = "English",
        explanationLevel: String = "Simple"
    ): Result<ExplainTextResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val textValidation = validateUserText(text)
        if (textValidation.isFailure) return Result.failure(textValidation.exceptionOrNull()!!)
        val cleanText = textValidation.getOrThrow()

        val validLang = LanguagePromptHelper.validateLanguageSelection(explanationLanguage, allowAutoDetect = false)
        if (validLang.isFailure) return Result.failure(validLang.exceptionOrNull()!!)
        val resolvedLang = validLang.getOrThrow()

        val reqType = LanguageRequestType.EXPLAIN_TEXT
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = LanguagePromptHelper.buildExplainTextPrompt(cleanText, resolvedLang, explanationLevel)
        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "lang_explain_${UUID.randomUUID()}",
            module = AIModule.LANGUAGE_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1536,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveLanguageProjectInternal(
                        userId = user.id,
                        projectType = reqType,
                        sourceLanguage = LanguageOptions.AUTO_DETECT,
                        targetLanguage = resolvedLang,
                        inputText = cleanText,
                        outputText = res.content.trim()
                    )
                    val parsed = LanguagePromptHelper.parseExplainTextResult(
                        projectId = proj.id,
                        explanationLanguage = resolvedLang,
                        explanationLevel = explanationLevel,
                        inputText = cleanText,
                        raw = res.content
                    )
                    Result.success(parsed)
                } catch (e: AIServiceException) {
                    Result.failure(e)
                }
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(mapOrchestrationError(res))
            }
        }
    }

    // -------------------------------------------------------------
    // 9. LANGUAGE PRACTICE (language_practice) & PROGRESS TRACKING
    // -------------------------------------------------------------
    suspend fun generateLanguagePractice(
        languageToLearn: String = "English",
        level: String = "Beginner",
        topic: String = "Daily Greetings & Introductions"
    ): Result<LanguagePracticeResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val validLang = LanguagePromptHelper.validateLanguageSelection(languageToLearn, allowAutoDetect = false)
        if (validLang.isFailure) return Result.failure(validLang.exceptionOrNull()!!)
        val resolvedLang = validLang.getOrThrow()

        if (topic.isBlank()) {
            return Result.failure(LanguageEmptyTextException("Please specify a practice topic."))
        }

        val reqType = LanguageRequestType.LANGUAGE_PRACTICE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = LanguagePromptHelper.buildLanguagePracticePrompt(resolvedLang, level, topic.trim())
        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "lang_practice_${UUID.randomUUID()}",
            module = AIModule.LANGUAGE_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 2048,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val now = System.currentTimeMillis()
                    val sessionEntity = LanguagePracticeSessionEntity(
                        id = UUID.randomUUID().toString(),
                        userId = user.id,
                        languageToLearn = resolvedLang,
                        level = level,
                        topic = topic.trim(),
                        questionsAttempted = 0,
                        correctAnswers = 0,
                        createdAt = now,
                        updatedAt = now
                    )
                    database.languageDao().insertPracticeSession(sessionEntity)

                    val proj = saveLanguageProjectInternal(
                        userId = user.id,
                        projectType = reqType,
                        sourceLanguage = resolvedLang,
                        targetLanguage = resolvedLang,
                        inputText = "Practice: $resolvedLang ($level) - ${topic.trim()}",
                        outputText = res.content.trim()
                    )

                    val parsed = LanguagePromptHelper.parseLanguagePracticeResult(
                        projectId = proj.id,
                        sessionId = sessionEntity.id,
                        languageToLearn = resolvedLang,
                        level = level,
                        topic = topic.trim(),
                        raw = res.content
                    )
                    Result.success(parsed)
                } catch (e: AIServiceException) {
                    Result.failure(e)
                }
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(mapOrchestrationError(res))
            }
        }
    }

    /**
     * Checks a learner's answer to a practice exercise and updates tracked session progress.
     */
    suspend fun checkPracticeExerciseAnswer(
        sessionId: String,
        exercise: PracticeExerciseItem,
        userAnswer: String
    ): Result<Boolean> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        if (userAnswer.isBlank()) {
            return Result.failure(LanguageEmptyTextException("Please enter an answer to check."))
        }

        val session = database.languageDao().getPracticeSessionByIdRaw(sessionId)
            ?: return Result.failure(LanguageInvalidInputException("Practice session not found."))

        if (session.userId != user.id) {
            return Result.failure(LanguagePermissionException("Access denied: Cannot update another user's practice session."))
        }

        val normalizedUser = userAnswer.trim().lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "")
        val normalizedExpected = exercise.expectedAnswer.trim().lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "")

        val isCorrect = normalizedUser.isNotEmpty() && (
            normalizedUser == normalizedExpected ||
                normalizedExpected.contains(normalizedUser) ||
                normalizedUser.contains(normalizedExpected)
            )

        val updatedSession = session.copy(
            questionsAttempted = session.questionsAttempted + 1,
            correctAnswers = session.correctAnswers + if (isCorrect) 1 else 0,
            updatedAt = System.currentTimeMillis()
        )
        database.languageDao().updatePracticeSession(updatedSession)

        return Result.success(isCorrect)
    }

    fun getPracticeProgressFlow(userId: String): Flow<PracticeProgressStats> {
        val authUser = currentUserProvider()
        if (authUser == null || authUser.id != userId) {
            return flowOf(PracticeProgressStats(0, 0, 0))
        }
        return database.languageDao().getPracticeSessionsByUser(userId).map { sessions ->
            PracticeProgressStats(
                totalSessions = sessions.size,
                totalQuestionsAttempted = sessions.sumOf { it.questionsAttempted },
                totalCorrectAnswers = sessions.sumOf { it.correctAnswers }
            )
        }
    }

    suspend fun getPracticeProgressSync(userId: String): Result<PracticeProgressStats> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }
        if (authUser.id != userId) {
            return Result.failure(LanguagePermissionException("Access denied: Cannot view another user's practice stats."))
        }
        val sessions = database.languageDao().getPracticeSessionsByUserSync(userId)
        return Result.success(
            PracticeProgressStats(
                totalSessions = sessions.size,
                totalQuestionsAttempted = sessions.sumOf { it.questionsAttempted },
                totalCorrectAnswers = sessions.sumOf { it.correctAnswers }
            )
        )
    }

    // -------------------------------------------------------------
    // 10. CONVERSATION PRACTICE (conversation_practice)
    // -------------------------------------------------------------
    suspend fun practiceConversationTurn(
        languageToLearn: String = "English",
        level: String = "Beginner",
        topic: String = "Daily Greetings & Introductions",
        userMessage: String = ""
    ): Result<ConversationPracticeTurnResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val validLang = LanguagePromptHelper.validateLanguageSelection(languageToLearn, allowAutoDetect = false)
        if (validLang.isFailure) return Result.failure(validLang.exceptionOrNull()!!)
        val resolvedLang = validLang.getOrThrow()

        val reqType = LanguageRequestType.CONVERSATION_PRACTICE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = LanguagePromptHelper.buildConversationPracticePrompt(
            languageToLearn = resolvedLang,
            level = level,
            scenarioOrTopic = topic.trim(),
            userMessage = userMessage.trim()
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "lang_conv_${UUID.randomUUID()}",
            module = AIModule.LANGUAGE_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1024,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val parsed = LanguagePromptHelper.parseConversationPracticeResult(
                        projectId = "",
                        languageToLearn = resolvedLang,
                        userMessage = userMessage.trim().ifBlank { "[Start Learn $resolvedLang Conversation]" },
                        raw = res.content
                    )
                    val proj = saveLanguageProjectInternal(
                        userId = user.id,
                        projectType = reqType,
                        sourceLanguage = resolvedLang,
                        targetLanguage = resolvedLang,
                        inputText = parsed.userMessage,
                        outputText = parsed.aiReply
                    )
                    Result.success(parsed.copy(projectId = proj.id))
                } catch (e: AIServiceException) {
                    Result.failure(e)
                }
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(mapOrchestrationError(res))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(mapOrchestrationError(res))
            }
        }
    }

    // -------------------------------------------------------------
    // 11. LANGUAGE HISTORY (LanguageProjects with Strict Ownership)
    // -------------------------------------------------------------
    fun getLanguageHistory(userId: String): Flow<List<LanguageProjectEntity>> {
        val authUser = currentUserProvider()
        if (authUser == null || authUser.id != userId) {
            return flowOf(emptyList())
        }
        return database.languageDao().getProjectsByUser(userId)
    }

    suspend fun getLanguageHistorySync(userId: String): Result<List<LanguageProjectEntity>> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }
        if (authUser.id != userId) {
            return Result.failure(LanguagePermissionException("Access denied: Cannot view another user's Language AI history."))
        }
        return Result.success(database.languageDao().getProjectsByUserSync(userId))
    }

    suspend fun openLanguageProject(projectId: String, requestingUserId: String? = null): Result<LanguageProjectEntity> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }
        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(LanguagePermissionException("Access denied: Cannot open another user's Language project."))
        }

        val raw = database.languageDao().getProjectByIdRaw(projectId)
            ?: return Result.failure(LanguageInvalidInputException("Language project not found."))

        if (raw.userId != authUser.id) {
            return Result.failure(LanguagePermissionException("Access denied: You do not own this Language project."))
        }

        return Result.success(raw)
    }

    suspend fun deleteLanguageProject(projectId: String, requestingUserId: String? = null): Result<Unit> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }
        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(LanguagePermissionException("Access denied: Cannot delete another user's Language project."))
        }

        val raw = database.languageDao().getProjectByIdRaw(projectId)
            ?: return Result.failure(LanguageInvalidInputException("Language project not found."))

        if (raw.userId != authUser.id) {
            return Result.failure(LanguagePermissionException("Access denied: You do not own this Language project."))
        }

        database.languageDao().deleteProject(projectId, authUser.id)
        return Result.success(Unit)
    }

    // -------------------------------------------------------------
    // 12. SAVED CONTENT (Deduplicated Save, Unsave, Delete with Ownership)
    // -------------------------------------------------------------
    fun getSavedContent(userId: String): Flow<List<SavedLanguageContentEntity>> {
        val authUser = currentUserProvider()
        if (authUser == null || authUser.id != userId) {
            return flowOf(emptyList())
        }
        return database.languageDao().getSavedContentByUser(userId)
    }

    suspend fun getSavedContentSync(userId: String): Result<List<SavedLanguageContentEntity>> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }
        if (authUser.id != userId) {
            return Result.failure(LanguagePermissionException("Access denied: Cannot view another user's saved language content."))
        }
        return Result.success(database.languageDao().getSavedContentByUserSync(userId))
    }

    suspend fun saveLanguageContent(
        projectType: String,
        sourceLanguage: String,
        targetLanguage: String,
        inputText: String,
        outputText: String,
        projectId: String? = null
    ): Result<SavedLanguageContentEntity> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val cleanInput = inputText.trim()
        val cleanOutput = outputText.trim()
        if (cleanOutput.isEmpty()) {
            return Result.failure(LanguageEmptyTextException("Cannot save empty language output."))
        }

        return try {
            // Requirement 12: Do not duplicate content unnecessarily in the database
            val existing = database.languageDao().findExistingSavedContent(
                userId = authUser.id,
                projectType = projectType,
                inputText = cleanInput,
                outputText = cleanOutput
            )
            if (existing != null) {
                return Result.success(existing)
            }

            val saved = SavedLanguageContentEntity(
                id = UUID.randomUUID().toString(),
                userId = authUser.id,
                projectId = projectId,
                projectType = projectType,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                inputText = cleanInput,
                outputText = cleanOutput,
                createdAt = System.currentTimeMillis()
            )
            database.languageDao().insertSavedContent(saved)
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(AIDatabaseException("Could not save language content."))
        }
    }

    suspend fun unsaveLanguageContent(savedId: String, requestingUserId: String? = null): Result<Unit> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }
        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(LanguagePermissionException("Access denied: Cannot modify another user's saved language content."))
        }

        val existing = database.languageDao().getSavedContentByIdRaw(savedId)
            ?: return Result.failure(LanguageInvalidInputException("Saved item not found."))

        if (existing.userId != authUser.id) {
            return Result.failure(LanguagePermissionException("Access denied: You do not own this saved item."))
        }

        database.languageDao().deleteSavedContent(savedId, authUser.id)
        return Result.success(Unit)
    }
}
