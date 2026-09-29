package com.example.data.repository

import com.example.data.local.NexaDatabase
import com.example.data.local.entity.AIRequestEntity
import com.example.data.local.entity.AIUsageEntity
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.QuizQuestionEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.ai.AIModule
import com.example.domain.ai.AIOrchestrationGateway
import com.example.domain.ai.AIOrchestrationResult
import com.example.domain.ai.EntitlementCheckResult
import com.example.domain.ai.EntitlementManager
import com.example.domain.ai.OrchestrationRequest
import com.example.domain.study.AnswerEvaluation
import com.example.domain.study.DoubtSolution
import com.example.domain.study.PracticeQuestionItem
import com.example.domain.study.StudyMCQ
import com.example.domain.study.StudyProgressSummary
import com.example.domain.study.StudyPromptHelper
import com.example.domain.study.WeakTopicItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray
import java.util.UUID

class StudyAIRepository(
    private val database: NexaDatabase,
    private val gateway: AIOrchestrationGateway = AIOrchestrationGateway(),
    private val entitlementManager: EntitlementManager = EntitlementManager(),
    private val currentUserProvider: () -> UserEntity?
) {

    private fun getAuthenticatedUser(): UserEntity {
        return currentUserProvider() ?: throw SecurityException("User not authenticated.")
    }

    private suspend fun verifyEntitlementAndLog(user: UserEntity, requestType: String): Result<String> {
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
                    conversationId = "study_workspace",
                    module = "study",
                    requestType = requestType,
                    status = "limit_reached",
                    createdAt = System.currentTimeMillis()
                )
            )
            return Result.failure(IllegalStateException(entitlementCheck.message))
        }

        database.aiRequestDao().insertRequest(
            AIRequestEntity(
                id = requestId,
                userId = user.id,
                conversationId = "study_workspace",
                module = "study",
                requestType = requestType,
                status = "pending",
                createdAt = System.currentTimeMillis()
            )
        )
        return Result.success(requestId)
    }

    private suspend fun recordUsageSuccess(user: UserEntity, requestId: String) {
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
    }

    private suspend fun recordUsageFailure(requestId: String, status: String) {
        database.aiRequestDao().updateStatus(requestId, status)
    }

    suspend fun askDoubt(
        subject: String,
        chapter: String,
        topic: String,
        studentQuestion: String,
        attachedNotes: String? = null,
        language: String = "English"
    ): Result<DoubtSolution> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        if (studentQuestion.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your question."))
        }

        val requestType = "study_doubt"
        val reqIdResult = verifyEntitlementAndLog(user, requestType)
        if (reqIdResult.isFailure) {
            return Result.failure(reqIdResult.exceptionOrNull()!!)
        }
        val requestId = reqIdResult.getOrThrow()

        val prompt = StudyPromptHelper.buildDoubtPrompt(
            subject = subject,
            chapter = chapter,
            topic = topic,
            studentQuestion = studentQuestion,
            attachedNotesOrDoc = attachedNotes,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "study_doubt_${UUID.randomUUID()}",
            module = AIModule.STUDY_AI,
            prompt = prompt,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                val solution = StudyPromptHelper.parseDoubtResponse(res.content)

                // Persist activity in study_activities
                database.studyActivityDao().insertActivity(
                    StudyActivityEntity(
                        id = UUID.randomUUID().toString(),
                        userId = user.id,
                        activityType = "doubt",
                        title = studentQuestion.take(40) + if (studentQuestion.length > 40) "..." else "",
                        subject = subject,
                        chapter = chapter,
                        topic = topic,
                        content = res.content,
                        createdAt = System.currentTimeMillis()
                    )
                )

                Result.success(solution)
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(IllegalStateException(res.errorMessage))
            }
        }
    }

    suspend fun explainTopic(
        subject: String,
        chapter: String,
        topic: String,
        difficulty: String = "Medium",
        language: String = "English"
    ): Result<String> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        if (topic.isBlank()) {
            return Result.failure(IllegalArgumentException("Topic cannot be empty."))
        }

        val requestType = "explain_topic"
        val reqIdResult = verifyEntitlementAndLog(user, requestType)
        if (reqIdResult.isFailure) {
            return Result.failure(reqIdResult.exceptionOrNull()!!)
        }
        val requestId = reqIdResult.getOrThrow()

        val prompt = StudyPromptHelper.buildExplainTopicPrompt(
            subject = subject,
            chapter = chapter,
            topic = topic,
            difficulty = difficulty,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "study_explain_${UUID.randomUUID()}",
            module = AIModule.STUDY_AI,
            prompt = prompt,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                database.studyActivityDao().insertActivity(
                    StudyActivityEntity(
                        id = UUID.randomUUID().toString(),
                        userId = user.id,
                        activityType = "explain_topic",
                        title = "Explanation: $topic",
                        subject = subject,
                        chapter = chapter,
                        topic = topic,
                        content = res.content,
                        createdAt = System.currentTimeMillis()
                    )
                )
                Result.success(res.content)
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(IllegalStateException(res.errorMessage))
            }
        }
    }

    suspend fun generateMCQs(
        subject: String,
        chapter: String,
        topic: String,
        count: Int = 5,
        difficulty: String = "Medium",
        language: String = "English"
    ): Result<List<StudyMCQ>> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val requestType = "generate_mcq"
        val reqIdResult = verifyEntitlementAndLog(user, requestType)
        if (reqIdResult.isFailure) {
            return Result.failure(reqIdResult.exceptionOrNull()!!)
        }
        val requestId = reqIdResult.getOrThrow()

        val prompt = StudyPromptHelper.buildMCQPrompt(
            subject = subject,
            chapter = chapter,
            topic = topic,
            count = count,
            difficulty = difficulty,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "study_mcq_${UUID.randomUUID()}",
            module = AIModule.STUDY_AI,
            prompt = prompt,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                val mcqs = StudyPromptHelper.parseMCQs(res.content, fallbackTopic = topic, fallbackDifficulty = difficulty)

                database.studyActivityDao().insertActivity(
                    StudyActivityEntity(
                        id = UUID.randomUUID().toString(),
                        userId = user.id,
                        activityType = "mcq",
                        title = "MCQs: $topic ($count Questions)",
                        subject = subject,
                        chapter = chapter,
                        topic = topic,
                        content = res.content,
                        createdAt = System.currentTimeMillis()
                    )
                )

                Result.success(mcqs)
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(IllegalStateException(res.errorMessage))
            }
        }
    }

    suspend fun generateQuiz(
        subject: String,
        chapter: String,
        topic: String,
        count: Int = 5,
        difficulty: String = "Medium",
        language: String = "English"
    ): Result<List<StudyMCQ>> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val requestType = "generate_quiz"
        val reqIdResult = verifyEntitlementAndLog(user, requestType)
        if (reqIdResult.isFailure) {
            return Result.failure(reqIdResult.exceptionOrNull()!!)
        }
        val requestId = reqIdResult.getOrThrow()

        val prompt = StudyPromptHelper.buildMCQPrompt(
            subject = subject,
            chapter = chapter,
            topic = topic,
            count = count,
            difficulty = difficulty,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "study_quiz_${UUID.randomUUID()}",
            module = AIModule.STUDY_AI,
            prompt = prompt,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                val mcqs = StudyPromptHelper.parseMCQs(res.content, fallbackTopic = topic, fallbackDifficulty = difficulty)
                Result.success(mcqs)
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(IllegalStateException(res.errorMessage))
            }
        }
    }

    suspend fun submitQuizAttempt(
        subject: String,
        chapter: String,
        topic: String,
        totalQuestions: Int,
        correctAnswers: Int,
        timeTakenSeconds: Int,
        questions: List<StudyMCQ>,
        selectedAnswers: Map<Int, Int> // questionIndex -> selectedOptionIndex
    ): Result<QuizAttemptEntity> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val attemptId = UUID.randomUUID().toString()
        val score = (correctAnswers * 10).coerceAtLeast(0)
        val accuracy = if (totalQuestions > 0) (correctAnswers.toFloat() / totalQuestions.toFloat()) * 100f else 0f

        val attempt = QuizAttemptEntity(
            id = attemptId,
            userId = user.id,
            subject = subject,
            chapter = chapter,
            topic = topic,
            totalQuestions = totalQuestions,
            correctAnswers = correctAnswers,
            score = score,
            accuracy = accuracy,
            timeTakenSeconds = timeTakenSeconds,
            createdAt = System.currentTimeMillis()
        )

        database.quizDao().insertAttempt(attempt)

        val questionEntities = questions.mapIndexed { idx, q ->
            val selIdx = selectedAnswers[idx]
            val selText = if (selIdx != null && selIdx in q.options.indices) q.options[selIdx] else "Unanswered"
            val optionsJson = JSONArray(q.options).toString()

            QuizQuestionEntity(
                id = UUID.randomUUID().toString(),
                quizAttemptId = attemptId,
                userId = user.id,
                question = q.question,
                optionsJson = optionsJson,
                selectedAnswer = selText,
                correctAnswer = q.correctAnswer,
                explanation = q.explanation
            )
        }

        database.quizDao().insertQuestions(questionEntities)

        database.studyActivityDao().insertActivity(
            StudyActivityEntity(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                activityType = "quiz",
                title = "Quiz: $topic ($correctAnswers/$totalQuestions - ${accuracy.toInt()}%)",
                subject = subject,
                chapter = chapter,
                topic = topic,
                content = "Scored $correctAnswers out of $totalQuestions in ${timeTakenSeconds}s.",
                createdAt = System.currentTimeMillis()
            )
        )

        return Result.success(attempt)
    }

    suspend fun generatePracticeQuestions(
        subject: String,
        chapter: String,
        topic: String,
        difficulty: String = "Medium",
        language: String = "English"
    ): Result<String> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val requestType = "generate_practice"
        val reqIdResult = verifyEntitlementAndLog(user, requestType)
        if (reqIdResult.isFailure) {
            return Result.failure(reqIdResult.exceptionOrNull()!!)
        }
        val requestId = reqIdResult.getOrThrow()

        val prompt = StudyPromptHelper.buildPracticeQuestionsPrompt(
            subject = subject,
            chapter = chapter,
            topic = topic,
            difficulty = difficulty,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "study_practice_${UUID.randomUUID()}",
            module = AIModule.STUDY_AI,
            prompt = prompt,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                database.studyActivityDao().insertActivity(
                    StudyActivityEntity(
                        id = UUID.randomUUID().toString(),
                        userId = user.id,
                        activityType = "practice",
                        title = "Practice Sheet: $topic",
                        subject = subject,
                        chapter = chapter,
                        topic = topic,
                        content = res.content,
                        createdAt = System.currentTimeMillis()
                    )
                )
                Result.success(res.content)
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(IllegalStateException(res.errorMessage))
            }
        }
    }

    suspend fun explainAnswer(
        question: String,
        studentAnswer: String,
        subject: String,
        chapter: String,
        topic: String,
        language: String = "English"
    ): Result<AnswerEvaluation> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        if (studentAnswer.isBlank()) {
            return Result.failure(IllegalArgumentException("Please paste your answer or solution."))
        }

        val requestType = "explain_answer"
        val reqIdResult = verifyEntitlementAndLog(user, requestType)
        if (reqIdResult.isFailure) {
            return Result.failure(reqIdResult.exceptionOrNull()!!)
        }
        val requestId = reqIdResult.getOrThrow()

        val prompt = StudyPromptHelper.buildExplainAnswerPrompt(
            question = question,
            studentAnswer = studentAnswer,
            subject = subject,
            chapter = chapter,
            topic = topic,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "study_eval_${UUID.randomUUID()}",
            module = AIModule.STUDY_AI,
            prompt = prompt,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                val eval = StudyPromptHelper.parseAnswerEvaluation(res.content)
                database.studyActivityDao().insertActivity(
                    StudyActivityEntity(
                        id = UUID.randomUUID().toString(),
                        userId = user.id,
                        activityType = "explain_answer",
                        title = "Answer Feedback: $topic",
                        subject = subject,
                        chapter = chapter,
                        topic = topic,
                        content = res.content,
                        createdAt = System.currentTimeMillis()
                    )
                )
                Result.success(eval)
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(IllegalStateException(res.errorMessage))
            }
        }
    }

    suspend fun generateStudyPlan(
        classLevel: String,
        subjects: List<String>,
        examDate: String,
        availableTimePerDay: String,
        strongSubjects: String,
        weakTopics: String,
        preferredDays: String,
        language: String = "English"
    ): Result<String> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val requestType = "study_plan"
        val reqIdResult = verifyEntitlementAndLog(user, requestType)
        if (reqIdResult.isFailure) {
            return Result.failure(reqIdResult.exceptionOrNull()!!)
        }
        val requestId = reqIdResult.getOrThrow()

        val prompt = StudyPromptHelper.buildStudyPlanPrompt(
            classLevel = classLevel,
            subjects = subjects,
            examDate = examDate,
            availableTimePerDay = availableTimePerDay,
            strongSubjects = strongSubjects,
            weakTopics = weakTopics,
            preferredDays = preferredDays,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "study_plan_${UUID.randomUUID()}",
            module = AIModule.STUDY_AI,
            prompt = prompt,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                Result.success(res.content)
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(IllegalStateException(res.errorMessage))
            }
        }
    }

    suspend fun saveStudyPlan(
        title: String,
        classLevel: String,
        subjects: String,
        examDate: String,
        hoursPerDay: String,
        preferredDays: String,
        strongSubjects: String,
        weakTopics: String,
        planContent: String
    ): Result<StudyPlanEntity> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val plan = StudyPlanEntity(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            title = title.ifBlank { "CBSE $classLevel Study Plan" },
            classLevel = classLevel,
            subjects = subjects,
            examDate = examDate,
            hoursPerDay = hoursPerDay,
            preferredDays = preferredDays,
            strongSubjects = strongSubjects,
            weakTopics = weakTopics,
            planContent = planContent,
            createdAt = System.currentTimeMillis()
        )

        database.studyPlanDao().insertPlan(plan)

        database.studyActivityDao().insertActivity(
            StudyActivityEntity(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                activityType = "study_plan",
                title = plan.title,
                subject = subjects,
                chapter = "",
                topic = "Revision Plan",
                content = planContent,
                createdAt = System.currentTimeMillis()
            )
        )

        return Result.success(plan)
    }

    suspend fun summarizeMaterial(
        material: String,
        subject: String,
        chapter: String,
        topic: String,
        language: String = "English"
    ): Result<String> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        if (material.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter or paste the study material to summarize."))
        }

        val requestType = "summarize_material"
        val reqIdResult = verifyEntitlementAndLog(user, requestType)
        if (reqIdResult.isFailure) {
            return Result.failure(reqIdResult.exceptionOrNull()!!)
        }
        val requestId = reqIdResult.getOrThrow()

        val prompt = StudyPromptHelper.buildSummarizeMaterialPrompt(
            material = material,
            subject = subject,
            chapter = chapter,
            topic = topic,
            language = language
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "study_summary_${UUID.randomUUID()}",
            module = AIModule.STUDY_AI,
            prompt = prompt,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                database.studyActivityDao().insertActivity(
                    StudyActivityEntity(
                        id = UUID.randomUUID().toString(),
                        userId = user.id,
                        activityType = "summary",
                        title = "Summary: $topic",
                        subject = subject,
                        chapter = chapter,
                        topic = topic,
                        content = res.content,
                        createdAt = System.currentTimeMillis()
                    )
                )
                Result.success(res.content)
            }
            is AIOrchestrationResult.RateLimited -> {
                recordUsageFailure(requestId, "rate_limited")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.UsageLimitReached -> {
                recordUsageFailure(requestId, "limit_reached")
                Result.failure(IllegalStateException(res.message))
            }
            is AIOrchestrationResult.Error -> {
                recordUsageFailure(requestId, "failed")
                Result.failure(IllegalStateException(res.errorMessage))
            }
        }
    }

    suspend fun getStudyProgress(userId: String): StudyProgressSummary? {
        val user = currentUserProvider()
        if (user == null || user.id != userId) {
            // Strict authorization (Requirement 17)
            return null
        }

        val attempts = database.quizDao().getAttemptsByUserSync(userId)
        val sessionsCount = database.studyActivityDao().getActivityCountByUser(userId)

        if (attempts.isEmpty() && sessionsCount == 0) {
            return null
        }

        val totalAttempted = attempts.sumOf { it.totalQuestions }
        val totalCorrect = attempts.sumOf { it.correctAnswers }
        val accuracy = if (totalAttempted > 0) (totalCorrect.toFloat() / totalAttempted.toFloat()) * 100f else 0f
        val distinctTopics = attempts.map { it.topic }.distinct().count()

        // Calculate weak topics by grouping quiz attempts by subject + chapter + topic
        val topicGroups = attempts.groupBy { Triple(it.subject, it.chapter, it.topic) }
        val weakTopics = topicGroups.mapNotNull { (key, groupAttempts) ->
            val attCount = groupAttempts.sumOf { it.totalQuestions }
            val corrCount = groupAttempts.sumOf { it.correctAnswers }
            val acc = if (attCount > 0) (corrCount.toFloat() / attCount.toFloat()) * 100f else 0f
            // If accuracy is below 75% and student has attempted at least 2 questions
            if (acc < 75f && attCount >= 2) {
                WeakTopicItem(
                    subject = key.first,
                    chapter = key.second,
                    topic = key.third,
                    totalAttempted = attCount,
                    totalCorrect = corrCount,
                    accuracyPercentage = acc
                )
            } else {
                null
            }
        }.sortedBy { it.accuracyPercentage }

        return StudyProgressSummary(
            questionsAttempted = totalAttempted,
            questionsCorrect = totalCorrect,
            accuracyPercentage = accuracy,
            quizzesCompleted = attempts.size,
            studySessions = sessionsCount,
            topicsPracticed = distinctTopics,
            weakTopics = weakTopics
        )
    }

    fun getStudyActivities(userId: String): Flow<List<StudyActivityEntity>> {
        val user = currentUserProvider()
        if (user == null || user.id != userId) {
            return flowOf(emptyList())
        }
        return database.studyActivityDao().getActivitiesByUser(userId)
    }

    fun getQuizAttempts(userId: String): Flow<List<QuizAttemptEntity>> {
        val user = currentUserProvider()
        if (user == null || user.id != userId) {
            return flowOf(emptyList())
        }
        return database.quizDao().getAttemptsByUser(userId)
    }

    fun getSavedPlans(userId: String): Flow<List<StudyPlanEntity>> {
        val user = currentUserProvider()
        if (user == null || user.id != userId) {
            return flowOf(emptyList())
        }
        return database.studyPlanDao().getPlansByUser(userId)
    }

    suspend fun deleteStudyPlan(planId: String, userId: String) {
        val user = currentUserProvider() ?: return
        if (user.id != userId) return
        database.studyPlanDao().deletePlan(planId, userId)
    }
}
