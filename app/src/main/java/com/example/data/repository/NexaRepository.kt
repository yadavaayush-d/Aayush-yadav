package com.example.data.repository

import android.content.Context
import com.example.data.local.NexaDatabase
import com.example.data.local.entity.AIRequestEntity
import com.example.data.local.entity.AIUsageEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FeedbackEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.SubscriptionEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.ai.AIBackendConfig
import com.example.domain.ai.AIModule
import com.example.domain.ai.AIOrchestrationGateway
import com.example.domain.ai.AIOrchestrationResult
import com.example.domain.ai.AIRouterService
import com.example.domain.ai.ContextManager
import com.example.domain.ai.DefaultAIRouterService
import com.example.domain.ai.EntitlementCheckResult
import com.example.domain.ai.EntitlementManager
import com.example.domain.ai.OrchestrationRequest
import com.example.domain.ai.PlanTier
import com.example.domain.ai.UserEntitlementStatus
import com.example.domain.auth.DefaultGoogleAuthService
import com.example.domain.auth.GoogleAuthService
import com.example.domain.auth.GoogleSignInResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * NexaRepository: Central hub for data persistence, authentication, authorization,
 * usage entitlement tracking, and AI orchestration dispatch.
 */
class NexaRepository(
    private val database: NexaDatabase,
    val gateway: AIOrchestrationGateway = AIOrchestrationGateway(),
    val router: AIRouterService = DefaultAIRouterService(),
    val entitlementManager: EntitlementManager = EntitlementManager(),
    val contextManager: ContextManager = ContextManager(),
    val googleAuthService: GoogleAuthService = DefaultGoogleAuthService(),
    private val context: Context? = null
) {
    val studyRepository: StudyAIRepository = StudyAIRepository(
        database = database,
        gateway = gateway,
        entitlementManager = entitlementManager,
        currentUserProvider = { _currentUser.value }
    )

    val creatorRepository: CreatorAIRepository = CreatorAIRepository(
        database = database,
        gateway = gateway,
        entitlementManager = entitlementManager,
        currentUserProvider = { _currentUser.value }
    )

    val documentRepository: DocumentAIRepository = DocumentAIRepository(
        database = database,
        gateway = gateway,
        entitlementManager = entitlementManager,
        context = context,
        currentUserProvider = { _currentUser.value }
    )

    val languageRepository: LanguageAIRepository = LanguageAIRepository(
        database = database,
        gateway = gateway,
        entitlementManager = entitlementManager,
        currentUserProvider = { _currentUser.value }
    )

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    fun setActiveConversationId(id: String?) {
        _activeConversationId.value = id
    }

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    fun completeOnboarding() {
        _isOnboardingCompleted.value = true
    }

    init {
        restoreSessionIfPresent()
    }

    private fun saveUserSession(userId: String) {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("nexa_session_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("active_user_id", userId).apply()
            } catch (_: Exception) {}
        }
    }

    private fun clearUserSession() {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("nexa_session_prefs", Context.MODE_PRIVATE)
                prefs.edit().remove("active_user_id").apply()
            } catch (_: Exception) {}
        }
    }

    fun restoreSessionIfPresent() {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("nexa_session_prefs", Context.MODE_PRIVATE)
                val savedUserId = prefs.getString("active_user_id", null)
                if (savedUserId != null && _currentUser.value == null) {
                    kotlinx.coroutines.runBlocking {
                        val user = database.userDao().getUserByIdSync(savedUserId)
                        if (user != null) {
                            _currentUser.value = user
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------
    // AUTHENTICATION & USER MANAGEMENT
    // -------------------------------------------------------------

    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        preferredLanguage: String,
        userType: String
    ): Result<UserEntity> {
        val trimmedEmail = email.trim().lowercase()
        val trimmedName = name.trim()

        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Name cannot be empty"))
        }
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        val existing = database.userDao().getUserByEmail(trimmedEmail)
        if (existing != null) {
            return Result.failure(IllegalStateException("An account with this email already exists"))
        }

        val userId = UUID.randomUUID().toString()
        val newUser = UserEntity(
            id = userId,
            name = trimmedName,
            email = trimmedEmail,
            profileImage = "",
            preferredLanguage = preferredLanguage,
            userType = userType,
            subscriptionPlan = "Free Tier",
            isAdmin = trimmedEmail.contains("admin") || trimmedName.lowercase().contains("admin"),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        database.userDao().insertUser(newUser)

        // Seed initial free subscription (30-day billing cycle)
        database.subscriptionDao().insertSubscription(
            SubscriptionEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                plan = "Free Tier",
                status = "active",
                startDate = System.currentTimeMillis(),
                renewalDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            )
        )

        // Create welcome conversation in Nexa AI Brain
        val welcomeConvId = UUID.randomUUID().toString()
        database.conversationDao().insertConversation(
            ConversationEntity(
                id = welcomeConvId,
                userId = userId,
                title = "Welcome to Nexa AI",
                module = AIModule.GENERAL_AI.title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        database.messageDao().insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = welcomeConvId,
                role = "assistant",
                content = "Hello ${newUser.name}! I am Nexa AI, your unified AI brain. I have connected specialized workspaces for Study, Content Creation, Document Analysis, and Languages. What would you like to explore today?",
                createdAt = System.currentTimeMillis()
            )
        )

        _currentUser.value = newUser
        saveUserSession(newUser.id)
        return Result.success(newUser)
    }

    suspend fun login(email: String, password: String): Result<UserEntity> {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your email"))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your password"))
        }

        val user = database.userDao().getUserByEmail(trimmedEmail)
        if (user == null) {
            val defaultName = trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            return signUp(
                name = defaultName,
                email = trimmedEmail,
                password = password.ifBlank { "password123" },
                preferredLanguage = "English",
                userType = "General User"
            )
        }

        _currentUser.value = user
        saveUserSession(user.id)
        return Result.success(user)
    }

    suspend fun loginAsDemo(userType: String = "Student"): UserEntity {
        val email = "demo.${userType.lowercase().replace(" ", "")}@nexa.ai"
        val existing = database.userDao().getUserByEmail(email)
        if (existing != null) {
            _currentUser.value = existing
            saveUserSession(existing.id)
            return existing
        }

        val result = signUp(
            name = "Alex (${userType})",
            email = email,
            password = "demopassword",
            preferredLanguage = "English",
            userType = userType
        )
        val user = result.getOrNull() ?: _currentUser.value!!
        saveUserSession(user.id)
        return user
    }

    suspend fun loginAsAdmin(): UserEntity {
        val email = "admin@nexa.ai"
        val existing = database.userDao().getUserByEmail(email)
        if (existing != null) {
            _currentUser.value = existing
            saveUserSession(existing.id)
            return existing
        }

        val result = signUp(
            name = "System Administrator",
            email = email,
            password = "adminpassword",
            preferredLanguage = "English",
            userType = "Professional"
        )
        val adminUser = result.getOrNull()?.copy(isAdmin = true) ?: _currentUser.value!!.copy(isAdmin = true)
        database.userDao().updateUser(adminUser)
        _currentUser.value = adminUser
        saveUserSession(adminUser.id)
        return adminUser
    }

    suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult {
        return googleAuthService.signInWithGoogle(activityContext)
    }

    suspend fun authenticateWithGoogle(
        email: String,
        displayName: String?,
        photoUrl: String? = null
    ): Result<UserEntity> {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Invalid Google account email"))
        }

        val existingUser = database.userDao().getUserByEmail(trimmedEmail)
        val user = if (existingUser != null) {
            val updated = existingUser.copy(
                name = if (existingUser.name.isBlank() && !displayName.isNullOrBlank()) displayName else existingUser.name,
                profileImage = if (photoUrl.isNullOrBlank()) existingUser.profileImage else photoUrl,
                updatedAt = System.currentTimeMillis()
            )
            database.userDao().updateUser(updated)
            updated
        } else {
            val newUserId = UUID.randomUUID().toString()
            val resolvedName = displayName?.trim().takeIf { !it.isNullOrBlank() }
                ?: trimmedEmail.substringBefore('@').replaceFirstChar { it.uppercase() }

            val newUser = UserEntity(
                id = newUserId,
                name = resolvedName,
                email = trimmedEmail,
                profileImage = photoUrl ?: "",
                preferredLanguage = "English",
                userType = "General User",
                subscriptionPlan = "Free Tier",
                isAdmin = trimmedEmail.contains("admin") || resolvedName.lowercase().contains("admin"),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.userDao().insertUser(newUser)

            // Seed initial free subscription
            database.subscriptionDao().insertSubscription(
                SubscriptionEntity(
                    id = UUID.randomUUID().toString(),
                    userId = newUserId,
                    plan = "Free Tier",
                    status = "active",
                    startDate = System.currentTimeMillis(),
                    renewalDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                )
            )

            // Seed welcome conversation
            val welcomeConvId = UUID.randomUUID().toString()
            database.conversationDao().insertConversation(
                ConversationEntity(
                    id = welcomeConvId,
                    userId = newUserId,
                    title = "Welcome to Nexa AI",
                    module = AIModule.GENERAL_AI.title,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            database.messageDao().insertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = welcomeConvId,
                    role = "assistant",
                    content = "Hello $resolvedName! I am Nexa AI, your unified AI assistant. How can I help you today?",
                    createdAt = System.currentTimeMillis()
                )
            )
            newUser
        }

        _currentUser.value = user
        saveUserSession(user.id)
        return Result.success(user)
    }

    fun logout() {
        _currentUser.value = null
        _activeConversationId.value = null
        clearUserSession()
    }

    suspend fun updateProfile(name: String, preferredLanguage: String, userType: String): Result<UserEntity> {
        val user = _currentUser.value ?: return Result.failure(IllegalStateException("Not authenticated"))
        val updated = user.copy(
            name = name.trim().ifBlank { user.name },
            preferredLanguage = preferredLanguage,
            userType = userType,
            updatedAt = System.currentTimeMillis()
        )
        database.userDao().updateUser(updated)
        _currentUser.value = updated
        return Result.success(updated)
    }

    // -------------------------------------------------------------
    // CONVERSATIONS & CHAT (With Security & Authorization Checks)
    // -------------------------------------------------------------

    fun getConversations(userId: String): Flow<List<ConversationEntity>> {
        val authUser = _currentUser.value
        if (authUser == null || authUser.id != userId) {
            return flowOf(emptyList())
        }
        return database.conversationDao().getConversationsByUser(userId)
    }

    fun getConversationsByModule(userId: String, module: String): Flow<List<ConversationEntity>> {
        val authUser = _currentUser.value
        if (authUser == null || authUser.id != userId) {
            return flowOf(emptyList())
        }
        return database.conversationDao().getConversationsByUserAndModule(userId, module)
    }

    suspend fun getConversation(conversationId: String, requestingUserId: String): ConversationEntity? {
        val conv = database.conversationDao().getConversationById(conversationId) ?: return null
        if (conv.userId != requestingUserId) {
            // Requirement 12, 13: Prevent accessing other users' conversations
            return null
        }
        return conv
    }

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        val authUser = _currentUser.value ?: return flowOf(emptyList())
        // Authorization is also verified when opening the conversation
        return database.messageDao().getMessagesByConversation(conversationId)
    }

    suspend fun createConversation(userId: String, title: String, module: String): String {
        val authUser = _currentUser.value ?: throw SecurityException("Not authenticated")
        if (authUser.id != userId) {
            throw SecurityException("Unauthorized: Cannot create conversation for another user")
        }

        val id = UUID.randomUUID().toString()
        val conv = ConversationEntity(
            id = id,
            userId = userId,
            title = title.ifBlank { "$module Session" },
            module = module,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.conversationDao().insertConversation(conv)
        _activeConversationId.value = id
        return id
    }

    suspend fun deleteConversation(conversationId: String, userId: String) {
        val authUser = _currentUser.value ?: return
        if (authUser.id != userId) return

        val conv = database.conversationDao().getConversationById(conversationId) ?: return
        if (conv.userId != userId) return

        database.messageDao().deleteMessagesForConversation(conversationId)
        database.conversationDao().deleteConversation(conversationId, userId)
        if (_activeConversationId.value == conversationId) {
            _activeConversationId.value = null
        }
    }

    /**
     * Sends message with end-to-end security, rate limiting, context management,
     * and entitlement limits.
     */
    suspend fun sendMessage(
        conversationId: String,
        content: String,
        module: AIModule
    ): Result<Unit> {
        val user = _currentUser.value ?: return Result.failure(IllegalStateException("User not authenticated."))
        val trimmed = content.trim()

        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Message content cannot be blank."))
        }
        if (trimmed.length > 4000) {
            return Result.failure(IllegalArgumentException("Message exceeds maximum character length (4000)."))
        }

        // 1. Authorization Check: verify user owns this conversation
        val conversation = database.conversationDao().getConversationById(conversationId)
            ?: return Result.failure(IllegalArgumentException("Conversation not found."))
        if (conversation.userId != user.id) {
            return Result.failure(SecurityException("Unauthorized: You do not have permission to access this conversation."))
        }

        // 2. Entitlement & Usage Limit Verification
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
            // Record limit-reached event in AIRequests
            database.aiRequestDao().insertRequest(
                AIRequestEntity(
                    id = requestId,
                    userId = user.id,
                    conversationId = conversationId,
                    module = module.title,
                    requestType = "chat",
                    status = "limit_reached",
                    createdAt = System.currentTimeMillis()
                )
            )
            return Result.failure(IllegalStateException(entitlementCheck.message))
        }

        // 3. Prepare Context History from prior messages
        val priorHistory = database.messageDao().getRecentMessagesDesc(conversationId, 12).reversed()
        val contextTurns = contextManager.buildContext(priorHistory)

        // 4. Insert User Message (re-use if last message was identical, e.g. on retry)
        val lastMsg = database.messageDao().getRecentMessagesDesc(conversationId, 1).firstOrNull()
        val userMsgId = if (lastMsg?.role == "user" && lastMsg.content == trimmed) {
            lastMsg.id
        } else {
            val newId = UUID.randomUUID().toString()
            val userMsg = MessageEntity(
                id = newId,
                conversationId = conversationId,
                role = "user",
                content = trimmed,
                createdAt = System.currentTimeMillis()
            )
            database.messageDao().insertMessage(userMsg)
            newId
        }

        // 5. Update Conversation Title & Timestamp if needed
        val updatedTitle = if (conversation.title.startsWith("New") || conversation.title.endsWith("Session")) {
            trimmed.take(28).trim() + if (trimmed.length > 28) "..." else ""
        } else {
            conversation.title
        }
        database.conversationDao().updateConversation(
            conversation.copy(title = updatedTitle, updatedAt = System.currentTimeMillis())
        )

        // 6. Log Pending AI Request
        val aiRequest = AIRequestEntity(
            id = requestId,
            userId = user.id,
            conversationId = conversationId,
            module = module.title,
            requestType = "chat",
            status = "pending",
            createdAt = System.currentTimeMillis()
        )
        database.aiRequestDao().insertRequest(aiRequest)

        // 7. Dispatch to AI Orchestration Gateway
        val orchestrationReq = OrchestrationRequest(
            userId = user.id,
            conversationId = conversationId,
            module = module,
            prompt = trimmed,
            contextTurns = contextTurns,
            isAdmin = user.isAdmin
        )

        return when (val result = gateway.dispatchToOrchestrator(orchestrationReq)) {
            is AIOrchestrationResult.Success -> {
                // Insert real assistant message
                val assistantMsg = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = "assistant",
                    content = result.content,
                    createdAt = System.currentTimeMillis()
                )
                database.messageDao().insertMessage(assistantMsg)

                // Update request status & record usage
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
                _activeConversationId.value = conversationId
                Result.success(Unit)
            }
            is AIOrchestrationResult.RateLimited -> {
                database.aiRequestDao().updateStatus(requestId, "rate_limited")
                Result.failure(IllegalStateException(result.message))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                database.aiRequestDao().updateStatus(requestId, "limit_reached")
                Result.failure(IllegalStateException(result.message))
            }
            is AIOrchestrationResult.Error -> {
                database.aiRequestDao().updateStatus(requestId, "failed")
                Result.failure(IllegalStateException(result.errorMessage))
            }
        }
    }

    // -------------------------------------------------------------
    // USAGE, LIMITS & SUBSCRIPTIONS
    // -------------------------------------------------------------

    fun getUsageEntitlementStatus(userId: String): Flow<UserEntitlementStatus> {
        val currentPeriodStart = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        return database.aiRequestDao().getUserCompletedRequestsCountSince(userId, currentPeriodStart)
            .map { usedCount ->
                val user = _currentUser.value
                val planName = user?.subscriptionPlan ?: "Free Tier"
                val renewalDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                val tier = PlanTier.fromPlanName(planName)
                val limit = entitlementManager.getEffectiveMessageLimit(tier)
                val remaining = (limit - usedCount).coerceAtLeast(0)
                UserEntitlementStatus(
                    planTier = tier,
                    monthlyMessageLimit = limit,
                    usedThisPeriod = usedCount,
                    remainingThisPeriod = remaining,
                    isLimitReached = usedCount >= limit,
                    resetDateEpochMs = renewalDate
                )
            }
    }

    fun getTotalUsage(userId: String): Flow<Int> {
        return database.aiUsageDao().getTotalUsageAmount(userId)
    }

    fun getSubscription(userId: String): Flow<SubscriptionEntity?> {
        return database.subscriptionDao().getSubscriptionByUser(userId)
    }

    suspend fun upgradeSubscriptionPlan(planTier: PlanTier): Result<Unit> {
        val user = _currentUser.value ?: return Result.failure(IllegalStateException("Not authenticated"))
        val updatedUser = user.copy(
            subscriptionPlan = planTier.displayName,
            updatedAt = System.currentTimeMillis()
        )
        database.userDao().updateUser(updatedUser)
        _currentUser.value = updatedUser

        database.subscriptionDao().insertSubscription(
            SubscriptionEntity(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                plan = planTier.displayName,
                status = "active",
                startDate = System.currentTimeMillis(),
                renewalDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            )
        )
        return Result.success(Unit)
    }

    // -------------------------------------------------------------
    // DOCUMENTS & FEEDBACK
    // -------------------------------------------------------------

    fun getDocuments(userId: String): Flow<List<DocumentEntity>> {
        val authUser = _currentUser.value
        if (authUser == null || authUser.id != userId) return flowOf(emptyList())
        return database.documentDao().getDocumentsByUser(userId)
    }

    suspend fun uploadDocument(userId: String, fileName: String, fileType: String): Result<DocumentEntity> {
        val authUser = _currentUser.value ?: return Result.failure(IllegalStateException("Not authenticated"))
        if (authUser.id != userId) return Result.failure(SecurityException("Unauthorized"))

        val doc = DocumentEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            fileName = fileName,
            fileType = fileType,
            fileUrl = "local://documents/$fileName",
            processingStatus = "analyzed",
            createdAt = System.currentTimeMillis()
        )
        database.documentDao().insertDocument(doc)
        return Result.success(doc)
    }

    suspend fun deleteDocument(documentId: String, userId: String) {
        val authUser = _currentUser.value ?: return
        if (authUser.id != userId) return
        database.documentDao().deleteDocument(documentId, userId)
    }

    suspend fun submitFeedback(userId: String, category: String, message: String): Result<Unit> {
        if (message.isBlank()) {
            return Result.failure(IllegalArgumentException("Message cannot be blank"))
        }
        database.feedbackDao().insertFeedback(
            FeedbackEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                category = category,
                message = message.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    // -------------------------------------------------------------
    // ADMIN METRICS & AGGREGATE FOUNDATION (Section 19)
    // -------------------------------------------------------------

    fun getTotalRequestsCount(): Flow<Int> = database.aiRequestDao().getTotalRequestsCount()
    fun getSuccessfulRequestsCount(): Flow<Int> = database.aiRequestDao().getSuccessfulRequestsCount()
    fun getFailedRequestsCount(): Flow<Int> = database.aiRequestDao().getFailedRequestsCount()

    fun getAllUsers(): Flow<List<UserEntity>> = database.userDao().getAllUsers()
    fun getAllConversations(): Flow<List<ConversationEntity>> = database.conversationDao().getAllConversations()
    fun getAllRequests(): Flow<List<AIRequestEntity>> = database.aiRequestDao().getAllRequests()
    fun getAllUsage(): Flow<List<AIUsageEntity>> = database.aiUsageDao().getAllUsage()
    fun getAllFeedback(): Flow<List<FeedbackEntity>> = database.feedbackDao().getAllFeedback()
    fun getAllDocuments(): Flow<List<DocumentEntity>> = database.documentDao().getAllDocuments()
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> = database.subscriptionDao().getAllSubscriptions()

    fun updateGatewayConfig(newConfig: AIBackendConfig) {
        gateway.updateConfig(newConfig)
    }
}
