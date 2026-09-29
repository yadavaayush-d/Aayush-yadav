package com.example.data.repository

import com.example.data.local.NexaDatabase
import com.example.data.local.entity.AIRequestEntity
import com.example.data.local.entity.AIUsageEntity
import com.example.data.local.entity.CreatorProjectEntity
import com.example.data.local.entity.SavedCreatorContentEntity
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
import com.example.domain.creator.BlogArticleResult
import com.example.domain.creator.CaptionResult
import com.example.domain.creator.ContentIdeasResult
import com.example.domain.creator.CreatorPromptHelper
import com.example.domain.creator.CreatorRequestType
import com.example.domain.creator.GeneratedContentResult
import com.example.domain.creator.HashtagResult
import com.example.domain.creator.RewriteResult
import com.example.domain.creator.ShortVideoScriptResult
import com.example.domain.creator.TitleSuggestionsResult
import com.example.domain.creator.YouTubeScriptResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

class CreatorAIRepository(
    private val database: NexaDatabase,
    private val gateway: AIOrchestrationGateway = AIOrchestrationGateway(),
    private val entitlementManager: EntitlementManager = EntitlementManager(),
    private val currentUserProvider: () -> UserEntity?
) {

    private fun getAuthenticatedUser(): UserEntity {
        return currentUserProvider() ?: throw AIAuthException("Please log in to use Creator AI.")
    }

    private suspend fun verifyEntitlementAndLog(
        user: UserEntity,
        requestType: CreatorRequestType
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
                        conversationId = "creator_workspace",
                        module = "creator",
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
                    conversationId = "creator_workspace",
                    module = "creator",
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
            // Non-fatal telemetry update
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
                    "VALIDATION_ERROR" -> AIValidationException(res.errorMessage)
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

    private suspend fun saveCreatorProjectInternal(
        userId: String,
        title: String,
        projectType: CreatorRequestType,
        platform: String,
        language: String,
        prompt: String,
        output: String
    ): CreatorProjectEntity {
        try {
            val now = System.currentTimeMillis()
            val entity = CreatorProjectEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = title.trim().ifBlank { "${projectType.displayLabel} ($platform)" },
                projectType = projectType.id,
                platform = platform,
                language = language,
                prompt = prompt,
                output = output,
                createdAt = now,
                updatedAt = now
            )
            database.creatorDao().insertProject(entity)
            return entity
        } catch (e: Exception) {
            throw AIDatabaseException("Could not save your Creator project. Please try again.")
        }
    }

    // -------------------------------------------------------------
    // 2. CONTENT GENERATOR (content_generate)
    // -------------------------------------------------------------
    suspend fun generateContent(
        platform: String,
        contentType: String,
        topic: String,
        targetAudience: String = "",
        language: String = "English",
        tone: String = "Professional",
        length: String = "Medium",
        additionalInstructions: String = ""
    ): Result<GeneratedContentResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        if (topic.isBlank()) {
            return Result.failure(AIValidationException("Please enter a topic for your content."))
        }

        val reqType = CreatorRequestType.CONTENT_GENERATE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = CreatorPromptHelper.buildContentGeneratorPrompt(
            platform = platform,
            contentType = contentType,
            topic = topic.trim(),
            targetAudience = targetAudience.trim(),
            language = language,
            tone = tone,
            length = length,
            additionalInstructions = additionalInstructions.trim()
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "creator_content_${UUID.randomUUID()}",
            module = AIModule.CREATOR_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 2048,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveCreatorProjectInternal(
                        userId = user.id,
                        title = "${platform}: ${topic.trim().take(40)}",
                        projectType = reqType,
                        platform = platform,
                        language = language,
                        prompt = prompt,
                        output = res.content
                    )
                    Result.success(
                        GeneratedContentResult(
                            projectId = proj.id,
                            title = proj.title,
                            platform = platform,
                            contentType = contentType,
                            language = language,
                            tone = tone,
                            content = res.content
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
    // 3. YOUTUBE SCRIPT GENERATOR (youtube_script)
    // -------------------------------------------------------------
    suspend fun generateYouTubeScript(
        topic: String,
        duration: String = "8-10 minutes",
        language: String = "English",
        audience: String = "General viewers",
        style: String = "Educational"
    ): Result<YouTubeScriptResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        if (topic.isBlank()) {
            return Result.failure(AIValidationException("Please enter a video topic."))
        }

        val reqType = CreatorRequestType.YOUTUBE_SCRIPT
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = CreatorPromptHelper.buildYouTubeScriptPrompt(
            topic = topic.trim(),
            duration = duration,
            language = language,
            audience = audience.trim(),
            style = style
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "creator_yt_${UUID.randomUUID()}",
            module = AIModule.CREATOR_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 2048,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveCreatorProjectInternal(
                        userId = user.id,
                        title = "YouTube Script: ${topic.trim().take(40)}",
                        projectType = reqType,
                        platform = "YouTube",
                        language = language,
                        prompt = prompt,
                        output = res.content
                    )
                    val parsed = CreatorPromptHelper.parseYouTubeScript(
                        projectId = proj.id,
                        topic = topic.trim(),
                        duration = duration,
                        language = language,
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
    // 4. SHORTS / REELS GENERATOR (shorts_script / reels_script)
    // -------------------------------------------------------------
    suspend fun generateShortsOrReelsScript(
        topic: String,
        duration: String = "60 seconds",
        platform: String = "YouTube Shorts",
        language: String = "English",
        style: String = "Entertaining"
    ): Result<ShortVideoScriptResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        if (topic.isBlank()) {
            return Result.failure(AIValidationException("Please enter a short video topic."))
        }

        val reqType = if (platform.contains("Reel", ignoreCase = true) || platform.contains("Instagram", ignoreCase = true)) {
            CreatorRequestType.REELS_SCRIPT
        } else {
            CreatorRequestType.SHORTS_SCRIPT
        }

        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = CreatorPromptHelper.buildShortsReelsPrompt(
            topic = topic.trim(),
            duration = duration,
            platform = platform,
            language = language,
            style = style
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "creator_short_${UUID.randomUUID()}",
            module = AIModule.CREATOR_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1536,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveCreatorProjectInternal(
                        userId = user.id,
                        title = "$platform: ${topic.trim().take(36)}",
                        projectType = reqType,
                        platform = platform,
                        language = language,
                        prompt = prompt,
                        output = res.content
                    )
                    val parsed = CreatorPromptHelper.parseShortsReelsScript(
                        projectId = proj.id,
                        topic = topic.trim(),
                        duration = duration,
                        platform = platform,
                        language = language,
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
    // 5. TITLE GENERATOR (title_generate)
    // -------------------------------------------------------------
    suspend fun generateTitles(
        topic: String,
        platform: String = "YouTube",
        language: String = "English",
        count: Int = 5
    ): Result<TitleSuggestionsResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        if (topic.isBlank()) {
            return Result.failure(AIValidationException("Please enter a topic for title ideas."))
        }

        val safeCount = count.coerceIn(1, 20)
        val reqType = CreatorRequestType.TITLE_GENERATE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = CreatorPromptHelper.buildTitleGeneratorPrompt(
            topic = topic.trim(),
            platform = platform,
            language = language,
            count = safeCount
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "creator_titles_${UUID.randomUUID()}",
            module = AIModule.CREATOR_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1024,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveCreatorProjectInternal(
                        userId = user.id,
                        title = "Titles: ${topic.trim().take(40)}",
                        projectType = reqType,
                        platform = platform,
                        language = language,
                        prompt = prompt,
                        output = res.content
                    )
                    val parsed = CreatorPromptHelper.parseTitleSuggestions(
                        projectId = proj.id,
                        topic = topic.trim(),
                        platform = platform,
                        language = language,
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
    // 6. CAPTION GENERATOR (caption_generate)
    // -------------------------------------------------------------
    suspend fun generateCaption(
        topic: String,
        platform: String = "Instagram",
        tone: String = "Friendly",
        language: String = "English",
        length: String = "Medium"
    ): Result<CaptionResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        if (topic.isBlank()) {
            return Result.failure(AIValidationException("Please enter a topic for your caption."))
        }

        val reqType = CreatorRequestType.CAPTION_GENERATE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = CreatorPromptHelper.buildCaptionGeneratorPrompt(
            topic = topic.trim(),
            platform = platform,
            tone = tone,
            language = language,
            length = length
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "creator_caption_${UUID.randomUUID()}",
            module = AIModule.CREATOR_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1024,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveCreatorProjectInternal(
                        userId = user.id,
                        title = "$platform Caption: ${topic.trim().take(36)}",
                        projectType = reqType,
                        platform = platform,
                        language = language,
                        prompt = prompt,
                        output = res.content
                    )
                    val parsed = CreatorPromptHelper.parseCaptionResult(
                        projectId = proj.id,
                        topic = topic.trim(),
                        platform = platform,
                        language = language,
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
    // 7. HASHTAG ASSISTANT (hashtag_suggest)
    // -------------------------------------------------------------
    suspend fun suggestHashtags(
        topic: String,
        platform: String = "Instagram",
        language: String = "English"
    ): Result<HashtagResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        if (topic.isBlank()) {
            return Result.failure(AIValidationException("Please enter a topic for hashtag suggestions."))
        }

        val reqType = CreatorRequestType.HASHTAG_SUGGEST
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = CreatorPromptHelper.buildHashtagPrompt(
            topic = topic.trim(),
            platform = platform,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "creator_hashtags_${UUID.randomUUID()}",
            module = AIModule.CREATOR_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1024,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveCreatorProjectInternal(
                        userId = user.id,
                        title = "Hashtags: ${topic.trim().take(40)}",
                        projectType = reqType,
                        platform = platform,
                        language = language,
                        prompt = prompt,
                        output = res.content
                    )
                    val parsed = CreatorPromptHelper.parseHashtagResult(
                        projectId = proj.id,
                        topic = topic.trim(),
                        platform = platform,
                        language = language,
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
    // 8. CONTENT IDEAS (content_ideas)
    // -------------------------------------------------------------
    suspend fun generateContentIdeas(
        nicheOrTopic: String,
        platform: String = "YouTube",
        audience: String = "General audience",
        count: Int = 5,
        language: String = "English"
    ): Result<ContentIdeasResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        if (nicheOrTopic.isBlank()) {
            return Result.failure(AIValidationException("Please enter your niche or topic."))
        }

        val safeCount = count.coerceIn(1, 15)
        val reqType = CreatorRequestType.CONTENT_IDEAS
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = CreatorPromptHelper.buildContentIdeasPrompt(
            nicheOrTopic = nicheOrTopic.trim(),
            platform = platform,
            audience = audience.trim(),
            count = safeCount,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "creator_ideas_${UUID.randomUUID()}",
            module = AIModule.CREATOR_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 2048,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveCreatorProjectInternal(
                        userId = user.id,
                        title = "Ideas: ${nicheOrTopic.trim().take(40)}",
                        projectType = reqType,
                        platform = platform,
                        language = language,
                        prompt = prompt,
                        output = res.content
                    )
                    val parsed = CreatorPromptHelper.parseContentIdeasResult(
                        projectId = proj.id,
                        nicheOrTopic = nicheOrTopic.trim(),
                        platform = platform,
                        audience = audience.trim(),
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
    // 9. BLOG WRITER (blog_generate)
    // -------------------------------------------------------------
    suspend fun generateBlog(
        topic: String,
        audience: String = "General readers",
        language: String = "English",
        tone: String = "Educational",
        approxLength: String = "Medium (~800 words)"
    ): Result<BlogArticleResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        if (topic.isBlank()) {
            return Result.failure(AIValidationException("Please enter a blog topic."))
        }

        val reqType = CreatorRequestType.BLOG_GENERATE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = CreatorPromptHelper.buildBlogWriterPrompt(
            topic = topic.trim(),
            audience = audience.trim(),
            language = language,
            tone = tone,
            approxLength = approxLength
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "creator_blog_${UUID.randomUUID()}",
            module = AIModule.CREATOR_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 3072,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveCreatorProjectInternal(
                        userId = user.id,
                        title = "Blog: ${topic.trim().take(40)}",
                        projectType = reqType,
                        platform = "Blog",
                        language = language,
                        prompt = prompt,
                        output = res.content
                    )
                    val parsed = CreatorPromptHelper.parseBlogArticleResult(
                        projectId = proj.id,
                        topic = topic.trim(),
                        language = language,
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
    // 10. CONTENT REWRITER (content_rewrite)
    // -------------------------------------------------------------
    suspend fun rewriteContent(
        userText: String,
        action: String = "Improve clarity",
        language: String = "English"
    ): Result<RewriteResult> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        if (userText.isBlank()) {
            return Result.failure(AIValidationException("Please paste or enter text to transform."))
        }

        val reqType = CreatorRequestType.CONTENT_REWRITE
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val prompt = CreatorPromptHelper.buildRewritePrompt(
            userText = userText.trim(),
            action = action,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "creator_rewrite_${UUID.randomUUID()}",
            module = AIModule.CREATOR_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 2048,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                try {
                    val proj = saveCreatorProjectInternal(
                        userId = user.id,
                        title = "Rewrite ($action): ${userText.trim().take(32)}",
                        projectType = reqType,
                        platform = "General Social Media",
                        language = language,
                        prompt = prompt,
                        output = res.content
                    )
                    Result.success(
                        RewriteResult(
                            projectId = proj.id,
                            action = action,
                            language = language,
                            originalText = userText.trim(),
                            transformedText = res.content
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
    // 11. CONTENT HISTORY / CREATOR PROJECTS (With Strict Ownership Security)
    // -------------------------------------------------------------
    fun getCreatorProjects(userId: String): Flow<List<CreatorProjectEntity>> {
        val authUser = currentUserProvider()
        if (authUser == null || authUser.id != userId) {
            return flowOf(emptyList())
        }
        return database.creatorDao().getProjectsByUser(userId)
    }

    suspend fun getCreatorProjectsSync(userId: String): Result<List<CreatorProjectEntity>> {
        val authUser = currentUserProvider()
            ?: return Result.failure(AIAuthException("Please log in to view Creator History."))
        if (authUser.id != userId) {
            return Result.failure(AIAuthException("Access denied: Cannot view another user's Creator History."))
        }
        return Result.success(database.creatorDao().getProjectsByUserSync(userId))
    }

    suspend fun openProject(projectId: String, requestingUserId: String? = null): Result<CreatorProjectEntity> {
        val authUser = currentUserProvider()
            ?: return Result.failure(AIAuthException("Please log in to open a Creator project."))
        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(AIAuthException("Access denied: You do not have permission to access this project."))
        }

        val rawProject = database.creatorDao().getProjectByIdRaw(projectId)
            ?: return Result.failure(AIValidationException("Creator project not found."))

        if (rawProject.userId != authUser.id) {
            return Result.failure(AIAuthException("Access denied: You do not have permission to access another user's project."))
        }

        return Result.success(rawProject)
    }

    suspend fun renameProject(projectId: String, newTitle: String, requestingUserId: String? = null): Result<CreatorProjectEntity> {
        val authUser = currentUserProvider()
            ?: return Result.failure(AIAuthException("Please log in to rename a project."))
        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(AIAuthException("Access denied: Unauthorized project rename."))
        }
        if (newTitle.isBlank()) {
            return Result.failure(AIValidationException("Project title cannot be empty."))
        }

        val existing = database.creatorDao().getProjectByIdRaw(projectId)
            ?: return Result.failure(AIValidationException("Creator project not found."))
        if (existing.userId != authUser.id) {
            return Result.failure(AIAuthException("Access denied: You cannot rename another user's project."))
        }

        val updated = existing.copy(
            title = newTitle.trim(),
            updatedAt = System.currentTimeMillis()
        )
        database.creatorDao().updateProject(updated)
        return Result.success(updated)
    }

    suspend fun deleteProject(projectId: String, requestingUserId: String? = null): Result<Unit> {
        val authUser = currentUserProvider()
            ?: return Result.failure(AIAuthException("Please log in to delete a project."))
        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(AIAuthException("Access denied: Unauthorized project deletion."))
        }

        val existing = database.creatorDao().getProjectByIdRaw(projectId)
            ?: return Result.failure(AIValidationException("Creator project not found."))
        if (existing.userId != authUser.id) {
            return Result.failure(AIAuthException("Access denied: You cannot delete another user's project."))
        }

        database.creatorDao().deleteProject(projectId, authUser.id)
        return Result.success(Unit)
    }

    suspend fun regenerateProject(projectId: String, requestingUserId: String? = null): Result<CreatorProjectEntity> {
        val authUser = currentUserProvider()
            ?: return Result.failure(AIAuthException("Please log in to regenerate content."))
        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(AIAuthException("Access denied: Unauthorized project regeneration."))
        }

        val existing = database.creatorDao().getProjectByIdRaw(projectId)
            ?: return Result.failure(AIValidationException("Creator project not found."))
        if (existing.userId != authUser.id) {
            return Result.failure(AIAuthException("Access denied: You cannot regenerate another user's project."))
        }

        val reqType = CreatorRequestType.fromId(existing.projectType)
        val reqIdResult = verifyEntitlementAndLog(authUser, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val orchReq = OrchestrationRequest(
            userId = authUser.id,
            conversationId = "creator_regen_${UUID.randomUUID()}",
            module = AIModule.CREATOR_AI,
            prompt = existing.prompt,
            requestType = reqType.id,
            maxTokens = 2048,
            isAdmin = authUser.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(authUser, requestId)
                val updated = existing.copy(
                    output = res.content,
                    updatedAt = System.currentTimeMillis()
                )
                database.creatorDao().updateProject(updated)
                Result.success(updated)
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
    // 12. FAVORITES / SAVED CONTENT (With Strict Ownership Security)
    // -------------------------------------------------------------
    fun getSavedContent(userId: String): Flow<List<SavedCreatorContentEntity>> {
        val authUser = currentUserProvider()
        if (authUser == null || authUser.id != userId) {
            return flowOf(emptyList())
        }
        return database.creatorDao().getSavedContentByUser(userId)
    }

    suspend fun getSavedContentSync(userId: String): Result<List<SavedCreatorContentEntity>> {
        val authUser = currentUserProvider()
            ?: return Result.failure(AIAuthException("Please log in to view Saved Content."))
        if (authUser.id != userId) {
            return Result.failure(AIAuthException("Access denied: Cannot view another user's saved content."))
        }
        return Result.success(database.creatorDao().getSavedContentByUserSync(userId))
    }

    suspend fun saveContentItem(
        title: String,
        contentType: String,
        platform: String,
        language: String,
        content: String,
        projectId: String? = null
    ): Result<SavedCreatorContentEntity> {
        val authUser = currentUserProvider()
            ?: return Result.failure(AIAuthException("Please log in to save content."))
        if (content.isBlank()) {
            return Result.failure(AIValidationException("Cannot save empty content."))
        }

        return try {
            val item = SavedCreatorContentEntity(
                id = UUID.randomUUID().toString(),
                userId = authUser.id,
                projectId = projectId,
                title = title.trim().ifBlank { "Saved $contentType" },
                contentType = contentType,
                platform = platform,
                language = language,
                content = content.trim(),
                createdAt = System.currentTimeMillis()
            )
            database.creatorDao().insertSavedContent(item)
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(AIDatabaseException("Could not save content. Please try again."))
        }
    }

    suspend fun unsaveContentItem(savedId: String, requestingUserId: String? = null): Result<Unit> {
        val authUser = currentUserProvider()
            ?: return Result.failure(AIAuthException("Please log in to manage saved content."))
        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(AIAuthException("Access denied: Cannot modify another user's saved content."))
        }

        val deletedCount = database.creatorDao().deleteSavedContent(savedId, authUser.id)
        if (deletedCount == 0) {
            return Result.failure(AIAuthException("Access denied or item not found."))
        }
        return Result.success(Unit)
    }

    suspend fun unsaveByProjectId(projectId: String): Result<Unit> {
        val authUser = currentUserProvider()
            ?: return Result.failure(AIAuthException("Please log in to manage saved content."))
        database.creatorDao().deleteSavedByProjectId(projectId, authUser.id)
        return Result.success(Unit)
    }
}
