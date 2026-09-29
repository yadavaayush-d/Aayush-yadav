package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NexaDatabase
import com.example.data.repository.CreatorAIRepository
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AIAuthException
import com.example.domain.ai.AINetworkException
import com.example.domain.ai.AIOrchestrationGateway
import com.example.domain.ai.AIRateLimiter
import com.example.domain.ai.AIService
import com.example.domain.ai.AIServiceRequest
import com.example.domain.ai.AIServiceResponse
import com.example.domain.ai.EntitlementManager
import com.example.domain.creator.CreatorRequestType
import com.example.ui.creator.CreatorTab
import com.example.ui.creator.CreatorUiState
import com.example.ui.creator.CreatorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step4CreatorAIIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: NexaDatabase
    private lateinit var testService: FakeCreatorAIService
    private lateinit var gateway: AIOrchestrationGateway
    private lateinit var repository: NexaRepository
    private lateinit var creatorRepo: CreatorAIRepository

    class FakeCreatorAIService : AIService {
        var shouldThrowNetworkError = false
        var lastRequest: AIServiceRequest? = null

        override suspend fun sendMessage(request: AIServiceRequest): Result<AIServiceResponse> {
            lastRequest = request
            if (shouldThrowNetworkError) {
                return Result.failure(AINetworkException("Unable to reach AI server. Check your internet connection."))
            }

            val p = request.prompt
            val content = when (request.requestType) {
                CreatorRequestType.CONTENT_GENERATE.id -> {
                    "Start your morning right! Drink a glass of water, stretch for 5 minutes, and step into the sunlight for a healthier day."
                }
                CreatorRequestType.YOUTUBE_SCRIPT.id -> {
                    """
                    1. Title Ideas:
                    - How AI Works for Complete Beginners
                    - Artificial Intelligence Explained in 5 Minutes
                    - AI 101: A Student's Guide to Machine Learning
                    2. Hook:
                    Have you ever wondered how your phone recognizes your face or how recommendation engines know what you want to watch next?
                    3. Introduction:
                    Welcome back! Today we are breaking down how Artificial Intelligence works in simple terms for students.
                    4. Main Content:
                    - Point 1: Data Collection - AI learns from examples just like students learn from textbooks.
                    - Point 2: Pattern Recognition - Neural networks find patterns across thousands of examples.
                    - Point 3: Predictions - Once trained, the model makes informed predictions on new inputs.
                    5. Call to Action:
                    If this helped clarify AI for you, share your favorite AI tool in the comments and subscribe for more student tech guides!
                    6. Closing:
                    Keep learning, stay curious, and see you in the next video!
                    """.trimIndent()
                }
                CreatorRequestType.SHORTS_SCRIPT.id, CreatorRequestType.REELS_SCRIPT.id -> {
                    """
                    1. Hook:
                    Stop rereading your textbook for hours without remembering anything! Try these 3 smart study tips instead.
                    2. Scene / Beat Outline:
                    - Beat 1 (0-5s): Close heavy textbook and show frustration.
                    - Beat 2 (5-15s): Explain Active Recall and Spaced Repetition.
                    - Beat 3 (15-30s): Show 25-minute Pomodoro timer and wrap up.
                    3. Voiceover Script:
                    Agar aap bhi exam se pehle ratta maar ke thak gaye ho, toh yeh 3 study tips try karo: Active Recall, Spaced Repetition, aur 25-minute focus blocks!
                    4. On-Screen Text Suggestions:
                    - Tip #1: Active Recall (Test Yourself)
                    - Tip #2: Spaced Repetition
                    - Tip #3: 25-Min Pomodoro Focus
                    5. Caption:
                    3 practical study tips to study smarter every day! Save this reel for your next exam session.
                    6. Call to Action:
                    Comment which tip you are trying today and follow for daily study hacks!
                    """.trimIndent()
                }
                CreatorRequestType.TITLE_GENERATE.id -> {
                    """
                    1. Best Budget Smartphones Under 20,000: Full Student Buying Guide
                    2. 5 Value-for-Money Smartphones Tested for Camera and Battery Life
                    3. Budget Smartphone Comparison: Which One Should You Buy This Year?
                    4. Top Affordable Phones for Students and Daily Productivity
                    5. Before You Buy a Budget Phone: 5 Specs That Actually Matter
                    """.trimIndent()
                }
                CreatorRequestType.CAPTION_GENERATE.id -> {
                    """
                    1. Caption:
                    Progress doesn't happen overnight—it happens one rep, one walk, and one healthy choice at a time. Show up for yourself today.
                    2. Optional CTA:
                    What workout are you completing today? Drop a comment below!
                    """.trimIndent()
                }
                CreatorRequestType.HASHTAG_SUGGEST.id -> {
                    "#DigitalMarketing #ContentStrategy #SocialMediaTips #OnlineBusiness #MarketingEducation #CreatorGrowth #BrandBuilding #SmallBusinessMarketing"
                }
                CreatorRequestType.CONTENT_IDEAS.id -> {
                    """
                    IDEA 1:
                    Title: How the Internet Actually Travels Under the Ocean
                    Description: Explain submarine fiber optic cables in 45 seconds for tech beginners.
                    Format: YouTube Shorts (45s Explainer)
                    Hook: Did you know 99% of international internet data travels at the bottom of the ocean, not through satellites?

                    IDEA 2:
                    Title: RAM vs Storage Explained Using a Study Desk Analogy
                    Description: Use a physical desk vs bookshelf analogy so beginners immediately grasp computer memory.
                    Format: YouTube Shorts (30s Visual Analogy)
                    Hook: Confused between RAM and storage? Look at your study desk right now!

                    IDEA 3:
                    Title: What Happens When You Type a Website URL?
                    Description: Walk through DNS lookup and server response in simple beginner language.
                    Format: YouTube Shorts (60s Step-by-Step)
                    Hook: In less than half a second after you press Enter, your browser completes a global relay race.

                    IDEA 4:
                    Title: Open Source Software Explained for Students
                    Description: Show why Linux, Android, and VS Code are built by global communities.
                    Format: YouTube Shorts (45s Breakdown)
                    Hook: Some of the world's most powerful software is built for free by developers around the globe.

                    IDEA 5:
                    Title: 3 Keyboard Shortcuts Every Computer Beginner Should Know
                    Description: Demonstrate practical time-saving shortcuts for students.
                    Format: YouTube Shorts (30s Demo)
                    Hook: Stop reaching for your mouse every two seconds—try these 3 shortcuts instead!
                    """.trimIndent()
                }
                CreatorRequestType.BLOG_GENERATE.id -> {
                    """
                    1. Title:
                    Why Learning Coding Empowers Students in Every Career Path
                    2. Introduction:
                    Coding is no longer reserved only for software engineers. For students across science, arts, and commerce, programming builds structured problem-solving skills.
                    3. Section Outline:
                    - Section 1: Computational Thinking and Problem Solving
                    - Section 2: Automating Repetitive Academic Tasks
                    - Section 3: Career Flexibility and Creative Confidence
                    4. Main Article:
                    When students learn to code, they learn how to break down intimidating problems into manageable logical steps. Whether analyzing lab data in Python or building an interactive portfolio website, coding transforms passive technology consumers into active creators.
                    5. Conclusion:
                    Starting small with 20 minutes of coding practice a day can open doors to lifelong analytical thinking and career opportunities.
                    """.trimIndent()
                }
                CreatorRequestType.CONTENT_REWRITE.id -> {
                    "Regular exercise improves energy levels, sharpens focus, and supports long-term heart health."
                }
                else -> "Original creator output for: $p"
            }

            return Result.success(
                AIServiceResponse(
                    content = content,
                    tokensUsed = 140,
                    modelName = "gemini-2.5-flash",
                    latencyMs = 35L
                )
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NexaDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        testService = FakeCreatorAIService()
        gateway = AIOrchestrationGateway(
            aiService = testService,
            rateLimiter = AIRateLimiter(minIntervalMillis = 0L, maxRequestsPerMinute = 500)
        )
        repository = NexaRepository(
            database = database,
            gateway = gateway,
            entitlementManager = EntitlementManager(),
            context = context
        )
        creatorRepo = repository.creatorRepository

        runBlocking {
            repository.signUp(
                name = "Rohan Creator",
                email = "rohan.creator@nexa.ai",
                password = "password123",
                preferredLanguage = "English",
                userType = "Content Creator"
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun test1_contentGenerator_savesProjectAndUsesCreatorModule() = runBlocking {
        val result = creatorRepo.generateContent(
            platform = "Instagram",
            contentType = "Post",
            topic = "Healthy morning habits",
            targetAudience = "Young professionals",
            language = "English",
            tone = "Friendly",
            length = "Short",
            additionalInstructions = "Keep it practical"
        )

        assertTrue("Content generation should succeed", result.isSuccess)
        val gen = result.getOrNull()!!
        assertTrue(gen.content.contains("morning", ignoreCase = true))
        assertEquals("creator", testService.lastRequest?.module)
        assertEquals("content_generate", testService.lastRequest?.requestType)

        val userId = repository.currentUser.value!!.id
        val projects = creatorRepo.getCreatorProjectsSync(userId).getOrThrow()
        assertEquals(1, projects.size)
        assertEquals("Instagram", projects[0].platform)
        assertEquals("content_generate", projects[0].projectType)
    }

    @Test
    fun test2_youtubeScriptGenerator_returnsStructuredSections() = runBlocking {
        val result = creatorRepo.generateYouTubeScript(
            topic = "How AI works for beginners",
            duration = "5 minutes",
            language = "English",
            audience = "Students",
            style = "Simple"
        )

        assertTrue("YouTube script generation should succeed", result.isSuccess)
        val script = result.getOrNull()!!
        assertTrue("Should have title ideas", script.titleIdeas.isNotEmpty())
        assertTrue("Should have hook", script.hook.isNotBlank())
        assertTrue("Should have introduction", script.introduction.isNotBlank())
        assertTrue("Should have main content", script.mainContent.isNotBlank())
        assertTrue("Should have call to action", script.callToAction.isNotBlank())
        assertTrue("Should have closing", script.closing.isNotBlank())
        assertEquals("youtube_script", testService.lastRequest?.requestType)
    }

    @Test
    fun test3_shortsReelsGenerator_returnsShortFormStructure() = runBlocking {
        val result = creatorRepo.generateShortsOrReelsScript(
            topic = "3 study tips",
            duration = "30 seconds",
            platform = "Instagram Reels",
            language = "Hinglish",
            style = "Fast and engaging"
        )

        assertTrue("Shorts/Reels generation should succeed", result.isSuccess)
        val shorts = result.getOrNull()!!
        assertTrue(shorts.hook.isNotBlank())
        assertTrue(shorts.sceneBeatOutline.isNotBlank())
        assertTrue(shorts.voiceoverScript.isNotBlank())
        assertTrue(shorts.onScreenTextSuggestions.isNotBlank())
        assertTrue(shorts.caption.isNotBlank())
        assertTrue(shorts.cta.isNotBlank())
        assertEquals("reels_script", testService.lastRequest?.requestType)
    }

    @Test
    fun test4_titleGenerator_returns5OriginalTitles() = runBlocking {
        val result = creatorRepo.generateTitles(
            topic = "Budget smartphones",
            platform = "YouTube",
            language = "English",
            count = 5
        )

        assertTrue("Title generator should succeed", result.isSuccess)
        val titles = result.getOrNull()!!
        assertEquals(5, titles.titles.size)
        assertEquals("title_generate", testService.lastRequest?.requestType)
    }

    @Test
    fun test5_captionGenerator_returnsCaptionAndOptionalCta() = runBlocking {
        val result = creatorRepo.generateCaption(
            topic = "Fitness motivation",
            platform = "Instagram",
            tone = "Motivational",
            language = "English",
            length = "Medium"
        )

        assertTrue("Caption generator should succeed", result.isSuccess)
        val caption = result.getOrNull()!!
        assertTrue(caption.caption.isNotBlank())
        assertTrue(caption.cta.isNotBlank())
        assertEquals("caption_generate", testService.lastRequest?.requestType)
    }

    @Test
    fun test6_hashtagAssistant_returnsSuggestedHashtags() = runBlocking {
        val result = creatorRepo.suggestHashtags(
            topic = "Digital marketing",
            platform = "Instagram",
            language = "English"
        )

        assertTrue("Hashtag assistant should succeed", result.isSuccess)
        val hashtags = result.getOrNull()!!
        assertTrue(hashtags.hashtags.isNotEmpty())
        assertTrue(hashtags.hashtags.all { tag -> tag.startsWith("#") })
        assertEquals("hashtag_suggest", testService.lastRequest?.requestType)
    }

    @Test
    fun test7_contentIdeas_returns5StructuredIdeas() = runBlocking {
        val result = creatorRepo.generateContentIdeas(
            nicheOrTopic = "Tech education",
            platform = "YouTube Shorts",
            audience = "Beginners",
            count = 5,
            language = "English"
        )

        assertTrue("Content ideas generator should succeed", result.isSuccess)
        val ideasResult = result.getOrNull()!!
        assertEquals(5, ideasResult.ideas.size)
        ideasResult.ideas.forEach { idea ->
            assertTrue(idea.title.isNotBlank())
            assertTrue(idea.shortDescription.isNotBlank())
            assertTrue(idea.suggestedFormat.isNotBlank())
            assertTrue(idea.suggestedHook.isNotBlank())
        }
        assertEquals("content_ideas", testService.lastRequest?.requestType)
    }

    @Test
    fun test8_blogWriter_returnsStructuredArticle() = runBlocking {
        val result = creatorRepo.generateBlog(
            topic = "Benefits of learning coding",
            audience = "Students",
            language = "English",
            tone = "Educational",
            approxLength = "Medium (~800 words)"
        )

        assertTrue("Blog writer should succeed", result.isSuccess)
        val blog = result.getOrNull()!!
        assertTrue(blog.title.isNotBlank())
        assertTrue(blog.introduction.isNotBlank())
        assertTrue(blog.sectionOutline.isNotBlank())
        assertTrue(blog.mainArticle.isNotBlank())
        assertTrue(blog.conclusion.isNotBlank())
        assertEquals("blog_generate", testService.lastRequest?.requestType)
    }

    @Test
    fun test9_contentRewriter_transformsUserSuppliedText() = runBlocking {
        val sampleText = "Engaging in consistent physical activity on a daily basis has a multi-faceted positive impact on cardiovascular endurance and mental clarity."
        val result = creatorRepo.rewriteContent(
            userText = sampleText,
            action = "Simplify",
            language = "English"
        )

        assertTrue("Content rewriter should succeed", result.isSuccess)
        val rewrite = result.getOrNull()!!
        assertTrue(rewrite.transformedText.isNotBlank())
        assertEquals("content_rewrite", testService.lastRequest?.requestType)
    }

    @Test
    fun test10_projectHistoryAndOwnership_renameDeleteAndCrossUserSecurity() = runBlocking {
        val userA = repository.currentUser.value!!
        val gen = creatorRepo.generateContent(
            platform = "YouTube",
            contentType = "Description",
            topic = "Productivity Setup",
            targetAudience = "Students",
            language = "English",
            tone = "Friendly",
            length = "Short",
            additionalInstructions = ""
        ).getOrThrow()

        val projectId = gen.projectId

        // Rename project
        val renamed = creatorRepo.renameProject(projectId, "Updated Productivity Setup").getOrThrow()
        assertEquals("Updated Productivity Setup", renamed.title)

        // Switch to User B
        repository.signUp(
            name = "User B",
            email = "userb.creator@nexa.ai",
            password = "password123",
            preferredLanguage = "English",
            userType = "Student"
        )
        val userB = repository.currentUser.value!!
        assertFalse(userA.id == userB.id)

        // User B cannot see User A's projects
        val userBProjects = creatorRepo.getCreatorProjectsSync(userB.id).getOrThrow()
        assertTrue(userBProjects.isEmpty())

        // User B cannot query User A's project list
        val crossQueryAttempt = creatorRepo.getCreatorProjectsSync(userA.id)
        assertTrue(crossQueryAttempt.isFailure)

        // User B cannot open, rename, or delete User A's project
        val openAttempt = creatorRepo.openProject(projectId)
        assertTrue(openAttempt.isFailure)
        assertTrue(openAttempt.exceptionOrNull() is AIAuthException)

        val deleteAttempt = creatorRepo.deleteProject(projectId)
        assertTrue(deleteAttempt.isFailure)

        // Switch back to User A and delete project
        repository.login("rohan.creator@nexa.ai", "password123")
        val delSuccess = creatorRepo.deleteProject(projectId)
        assertTrue(delSuccess.isSuccess)
        assertTrue(creatorRepo.getCreatorProjectsSync(userA.id).getOrThrow().isEmpty())
    }

    @Test
    fun test11_savedContent_saveViewAndDelete() = runBlocking {
        val userA = repository.currentUser.value!!
        val saved = creatorRepo.saveContentItem(
            title = "Saved Morning Hook",
            contentType = "shorts_script",
            platform = "Instagram Reels",
            language = "English",
            content = "Hook: Never check your phone in the first 15 minutes after waking up!"
        ).getOrThrow()

        val savedList = creatorRepo.getSavedContentSync(userA.id).getOrThrow()
        assertEquals(1, savedList.size)
        assertEquals("Saved Morning Hook", savedList[0].title)

        // Unsave / delete
        val unsaveRes = creatorRepo.unsaveContentItem(saved.id)
        assertTrue(unsaveRes.isSuccess)
        assertTrue(creatorRepo.getSavedContentSync(userA.id).getOrThrow().isEmpty())
    }

    @Test
    fun test12_errorHandlingUsageTrackingAndViewModelFlow() = runBlocking {
        val viewModel = CreatorViewModel(repository)
        viewModel.selectTab(CreatorTab.TITLE_GENERATOR)
        assertEquals(CreatorTab.TITLE_GENERATOR, viewModel.activeTab.value)

        // Simulate network error and retry
        testService.shouldThrowNetworkError = true
        viewModel.generateTitles(topic = "AI Gadgets", platform = "YouTube", language = "English", count = 5)
        val errState = withTimeout(5000) {
            viewModel.titleGenState.filter { it is CreatorUiState.Error }.first()
        }
        assertTrue(errState is CreatorUiState.Error)

        testService.shouldThrowNetworkError = false
        viewModel.retryLastAction()
        val okState = withTimeout(5000) {
            viewModel.titleGenState.filter { it is CreatorUiState.Success<*> }.first()
        }
        assertTrue(okState is CreatorUiState.Success<*>)

        // Verify usage tracking & Free Tier limit enforcement
        val userId = repository.currentUser.value!!.id
        val requests = database.aiRequestDao().getAllRequests().first()
        assertTrue(requests.any { it.userId == userId && it.module == "creator" && it.status == "completed" })
    }
}
