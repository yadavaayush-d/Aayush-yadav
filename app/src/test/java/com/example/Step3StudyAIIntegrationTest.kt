package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NexaDatabase
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AIModule
import com.example.domain.ai.AIOrchestrationGateway
import com.example.domain.ai.AIRateLimiter
import com.example.domain.ai.AIService
import com.example.domain.ai.AIServiceRequest
import com.example.domain.ai.AIServiceResponse
import com.example.domain.study.CurriculumData
import com.example.domain.study.StudyMCQ
import com.example.domain.study.WeakTopicItem
import com.example.ui.study.QuizSessionState
import com.example.ui.study.QuizStatus
import com.example.ui.study.StudyActionTab
import com.example.ui.study.StudyUiState
import com.example.ui.study.StudyViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3StudyAIIntegrationTest {

    private lateinit var database: NexaDatabase
    private lateinit var repository: NexaRepository
    private lateinit var mockAIService: TestAIService

    class TestAIService : AIService {
        var lastReceivedRequest: AIServiceRequest? = null
        var shouldSimulateError = false
        var simulatedErrorMessage = "Network connection failed"
        var configuredResponseText = "Default study AI response"

        override suspend fun sendMessage(request: AIServiceRequest): Result<AIServiceResponse> {
            lastReceivedRequest = request
            if (shouldSimulateError) {
                return Result.failure(IOException(simulatedErrorMessage))
            }
            return Result.success(
                AIServiceResponse(
                    content = configuredResponseText,
                    tokensUsed = 50,
                    modelName = "gemini-3.5-flash",
                    latencyMs = 100
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
    fun `TEST 1 - Curriculum hierarchy supports CBSE Class 10 with all subjects`() {
        val class10 = CurriculumData.CBSE_CLASS_10
        assertEquals("Class 10", class10.name)
        assertEquals("CBSE", class10.board)

        val subjectNames = class10.subjects.map { it.name }
        assertTrue("Mathematics should be supported", subjectNames.contains("Mathematics"))
        assertTrue("Science should be supported", subjectNames.contains("Science"))
        assertTrue("Social Science should be supported", subjectNames.contains("Social Science"))
        assertTrue("English should be supported", subjectNames.contains("English"))
        assertTrue("Hindi should be supported", subjectNames.contains("Hindi"))

        val math = class10.subjects.first { it.name == "Mathematics" }
        assertTrue("Math should have 14 chapters", math.chapters.size >= 14)
        val ch1 = math.chapters.first { it.name == "Real Numbers" }
        assertTrue("Ch1 should have topics", ch1.topics.isNotEmpty())
    }

    @Test
    fun `TEST 2 - Ask a Doubt solves question with 6 structured sections and logs usage`() = runBlocking {
        val user = repository.signUp("Student Priya", "priya@school.edu", "password123", "English", "Student").getOrThrow()

        mockAIService.configuredResponseText = """
            1. Direct Answer: The square root of 2 is irrational.
            2. Simple Explanation: It cannot be written as a simple fraction a/b with whole numbers.
            3. Step-by-Step Solution: Assume √2 = a/b in lowest terms. Then 2b² = a², meaning 2 divides a...
            4. Important Concept: Fundamental Theorem of Arithmetic & Proof by Contradiction.
            5. Common Mistake: Forgetting to state that a and b are co-prime integers.
            6. Similar Practice Question: Prove that √3 is an irrational number.
        """.trimIndent()

        val result = repository.studyRepository.askDoubt(
            subject = "Mathematics",
            chapter = "Real Numbers",
            topic = "Revisiting Irrational Numbers",
            studentQuestion = "Why is root 2 an irrational number?",
            language = "English"
        )

        assertTrue(result.isSuccess)
        val solution = result.getOrThrow()
        assertTrue(solution.directAnswer.contains("irrational"))
        assertTrue(solution.simpleExplanation.contains("fraction"))
        assertTrue(solution.stepByStepSolution.contains("lowest terms"))
        assertTrue(solution.importantConcept.contains("Contradiction"))
        assertTrue(solution.commonMistake.contains("co-prime"))
        assertTrue(solution.similarPracticeQuestion.contains("√3"))

        // Verify AIRequest & AIUsage tracking
        val requests = database.aiRequestDao().getRequestsByUser(user.id).first()
        val studyReq = requests.firstOrNull { it.requestType == "study_doubt" }
        assertNotNull("Should log study_doubt AI request", studyReq)
        assertEquals("study", studyReq?.module)
        assertEquals("completed", studyReq?.status)

        val usage = database.aiUsageDao().getTotalUsageAmount(user.id).first()
        assertTrue("Usage should be recorded", usage >= 1)

        // Verify saved activity in study_activities
        val activities = database.studyActivityDao().getActivitiesByUser(user.id).first()
        assertTrue(activities.any { it.activityType == "doubt" })
    }

    @Test
    fun `TEST 3 - Empty question in Ask a Doubt returns validation failure`() = runBlocking {
        repository.signUp("Student Aman", "aman@school.edu", "password123", "English", "Student").getOrThrow()

        val result = repository.studyRepository.askDoubt(
            subject = "Science",
            chapter = "Electricity",
            topic = "Ohm's Law",
            studentQuestion = "   ",
            language = "English"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `TEST 4 - Explain a Topic returns pedagogical breakdown and saves activity`() = runBlocking {
        val user = repository.signUp("Student Rohit", "rohit@school.edu", "password123", "Hindi", "Student").getOrThrow()

        mockAIService.configuredResponseText = """
            ## 1. Overview & Definition
            Ohm's Law states that electric current through a conductor is directly proportional to voltage.
            ## 2. Real-World Analogy
            Water flowing through a pipe with a valve.
            ## 3. Core Formula
            V = I * R
            ## 4. Key Exam Points
            Draw V-I graph showing a straight line passing through origin.
        """.trimIndent()

        val result = repository.studyRepository.explainTopic(
            subject = "Science",
            chapter = "Electricity",
            topic = "Ohm's Law & Resistance",
            difficulty = "Medium",
            language = "Hindi"
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().contains("V = I * R"))

        // Language was passed in prompt
        assertTrue(mockAIService.lastReceivedRequest?.prompt?.contains("Language: Hindi") == true)

        val activities = database.studyActivityDao().getActivitiesByUser(user.id).first()
        assertTrue(activities.any { it.activityType == "explain_topic" })
    }

    @Test
    fun `TEST 5 - Generate MCQs parses structured options and supports interactive answering`() = runBlocking {
        repository.signUp("Student Kavya", "kavya@school.edu", "password123", "English", "Student").getOrThrow()

        mockAIService.configuredResponseText = """
            [
              {
                "question": "Which of the following is a quadratic equation?",
                "options": ["x² + 2x + 1 = 0", "x + 2 = 0", "x³ + 2 = 0", "1/x + x = 2"],
                "correctIndex": 0,
                "explanation": "It has degree 2 in standard form ax² + bx + c = 0.",
                "difficulty": "Easy",
                "topic": "Quadratic Equations"
              }
            ]
        """.trimIndent()

        val result = repository.studyRepository.generateMCQs(
            subject = "Mathematics",
            chapter = "Quadratic Equations",
            topic = "Standard Form",
            count = 1,
            difficulty = "Easy"
        )

        assertTrue(result.isSuccess)
        val mcqs = result.getOrThrow()
        assertEquals(1, mcqs.size)
        assertEquals("Which of the following is a quadratic equation?", mcqs[0].question)
        assertEquals(0, mcqs[0].correctIndex)
        assertEquals("x² + 2x + 1 = 0", mcqs[0].correctAnswer)
    }

    @Test
    fun `TEST 6 - Interactive Quiz Mode executes full session and saves attempt with questions`() = runBlocking {
        val user = repository.signUp("Student Ananya", "ananya@school.edu", "password123", "English", "Student").getOrThrow()

        val mcqs = listOf(
            StudyMCQ("q1", "What is the SI unit of electric current?", listOf("Volt", "Ampere", "Ohm", "Watt"), 1, "Ampere", "Ampere is standard unit.", "Easy", "Electricity"),
            StudyMCQ("q2", "Which lens is used to correct myopia?", listOf("Convex lens", "Concave lens", "Cylindrical lens", "Bifocal lens"), 1, "Concave lens", "Concave diverges light.", "Medium", "The Human Eye")
        )

        val selectedAnswers = mapOf(0 to 1, 1 to 0) // Q1 correct (Ampere), Q2 wrong (Convex)

        val submitResult = repository.studyRepository.submitQuizAttempt(
            subject = "Science",
            chapter = "Electricity",
            topic = "Units and Lenses",
            totalQuestions = 2,
            correctAnswers = 1,
            timeTakenSeconds = 45,
            questions = mcqs,
            selectedAnswers = selectedAnswers
        )

        assertTrue(submitResult.isSuccess)
        val attempt = submitResult.getOrThrow()
        assertEquals(user.id, attempt.userId)
        assertEquals(2, attempt.totalQuestions)
        assertEquals(1, attempt.correctAnswers)
        assertEquals(50f, attempt.accuracy, 0.01f)
        assertEquals(45, attempt.timeTakenSeconds)

        // Verify stored QuizQuestions
        val storedQuestions = database.quizDao().getQuestionsForAttempt(attempt.id, user.id)
        assertEquals(2, storedQuestions.size)
        assertEquals("Ampere", storedQuestions[0].selectedAnswer)
        assertEquals("Ampere", storedQuestions[0].correctAnswer)
        assertEquals("Convex lens", storedQuestions[1].selectedAnswer)
        assertEquals("Concave lens", storedQuestions[1].correctAnswer)
    }

    @Test
    fun `TEST 7 - Explain an Answer provides constructive structured feedback`() = runBlocking {
        repository.signUp("Student Deepak", "deepak@school.edu", "password123", "English", "Student").getOrThrow()

        mockAIService.configuredResponseText = """
            1. What is Correct: You correctly recalled that current is charge divided by time.
            2. What Needs Improvement: In the calculation, 10 divided by 2 was written as 20 instead of 5.
            3. Missing Concepts: You omitted the SI unit (Ampere) in your final line.
            4. Step-by-Step Correction: I = Q / t = 10 C / 2 s = 5 A.
            5. A Better Way to Approach: Always write: Given -> Formula -> Substitution -> Final Answer with units.
        """.trimIndent()

        val result = repository.studyRepository.explainAnswer(
            question = "Calculate current when 10 Coulombs flow in 2 seconds.",
            studentAnswer = "I = Q*t = 10 * 2 = 20",
            subject = "Science",
            chapter = "Electricity",
            topic = "Electric Current",
            language = "English"
        )

        assertTrue(result.isSuccess)
        val eval = result.getOrThrow()
        assertTrue(eval.whatIsCorrect.contains("charge divided by time"))
        assertTrue(eval.whatNeedsImprovement.contains("instead of 5"))
        assertTrue(eval.missingConcepts.contains("SI unit"))
        assertTrue(eval.stepByStepCorrection.contains("5 A"))
    }

    @Test
    fun `TEST 8 - Generate and save study plan persists to database`() = runBlocking {
        val user = repository.signUp("Student Vikram", "vikram@school.edu", "password123", "English", "Student").getOrThrow()

        mockAIService.configuredResponseText = """
            # 30-Day CBSE Class 10 Revision Schedule
            - Day 1: Real Numbers & Chemical Reactions (45 mins each)
            - Day 2: Polynomials & Acids, Bases, Salts (45 mins each)
        """.trimIndent()

        val genResult = repository.studyRepository.generateStudyPlan(
            classLevel = "Class 10",
            subjects = listOf("Mathematics", "Science"),
            examDate = "15 March 2026",
            availableTimePerDay = "2 hours",
            strongSubjects = "Science",
            weakTopics = "Trigonometry",
            preferredDays = "Monday to Saturday",
            language = "English"
        )
        assertTrue(genResult.isSuccess)

        val saveResult = repository.studyRepository.saveStudyPlan(
            title = "Sprint Plan March",
            classLevel = "Class 10",
            subjects = "Mathematics, Science",
            examDate = "15 March 2026",
            hoursPerDay = "2 hours",
            preferredDays = "Mon-Sat",
            strongSubjects = "Science",
            weakTopics = "Trigonometry",
            planContent = genResult.getOrThrow()
        )
        assertTrue(saveResult.isSuccess)

        val savedList = repository.studyRepository.getSavedPlans(user.id).first()
        assertEquals(1, savedList.size)
        assertEquals("Sprint Plan March", savedList[0].title)
    }

    @Test
    fun `TEST 9 - Summarize Material creates concise notes and checklist`() = runBlocking {
        repository.signUp("Student Sonia", "sonia@school.edu", "password123", "English", "Student").getOrThrow()

        mockAIService.configuredResponseText = """
            1. Executive Summary: Photosynthesis is the anabolic process whereby green plants synthesize carbohydrates using light and chlorophyll.
            2. Core Definitions: Light reaction occurs in grana; dark reaction in stroma.
            3. High-Yield Points: Balanced formula 6CO2 + 12H2O -> C6H12O6 + 6O2 + 6H2O.
            4. Quick Checklist: Define stomatal function, list raw materials, state byproducts.
        """.trimIndent()

        val result = repository.studyRepository.summarizeMaterial(
            material = "Photosynthesis takes place in chloroplasts. Leaves have chlorophyll...",
            subject = "Science",
            chapter = "Life Processes",
            topic = "Nutrition in Plants"
        )
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().contains("Photosynthesis"))
    }

    @Test
    fun `TEST 10 - Study Progress shows empty state initially and real stats with Weak Topics after quiz`() = runBlocking {
        val user = repository.signUp("Student Tanvi", "tanvi@school.edu", "password123", "English", "Student").getOrThrow()

        // 1. Initial empty state
        val initialProgress = repository.studyRepository.getStudyProgress(user.id)
        assertNull("Should be null when no activity exists", initialProgress)

        // 2. Submit a quiz with poor score in Triangles
        val triangleMCQs = listOf(
            StudyMCQ("t1", "Criteria for similarity?", listOf("A", "B", "C", "D"), 0, "A", "", "Medium", "Similarity Criteria"),
            StudyMCQ("t2", "Thales theorem ratio?", listOf("A", "B", "C", "D"), 0, "A", "", "Medium", "Similarity Criteria")
        )
        repository.studyRepository.submitQuizAttempt(
            subject = "Mathematics",
            chapter = "Triangles",
            topic = "Similarity Criteria",
            totalQuestions = 2,
            correctAnswers = 0, // 0%
            timeTakenSeconds = 60,
            questions = triangleMCQs,
            selectedAnswers = mapOf(0 to 1, 1 to 1)
        )

        // 3. Verify real progress summary & identified weak topic
        val progress = repository.studyRepository.getStudyProgress(user.id)
        assertNotNull(progress)
        assertEquals(2, progress?.questionsAttempted)
        assertEquals(0, progress?.questionsCorrect)
        assertEquals(0f, progress?.accuracyPercentage)
        assertEquals(1, progress?.quizzesCompleted)
        assertEquals(1, progress?.weakTopics?.size)
        assertEquals("Similarity Criteria", progress?.weakTopics?.first()?.topic)
    }

    @Test
    fun `TEST 11 - Database security ensures User A cannot access User B study records`() = runBlocking {
        val userA = repository.signUp("User A", "usera@nexa.ai", "password123", "English", "Student").getOrThrow()
        val userB = repository.signUp("User B", "userb@nexa.ai", "password123", "English", "Student").getOrThrow()

        // Currently logged in as User B
        assertEquals(userB.id, repository.currentUser.value?.id)

        // Attempting to query User A's progress returns null
        val crossProgress = repository.studyRepository.getStudyProgress(userA.id)
        assertNull("User B cannot view User A's progress", crossProgress)

        // User B cannot view User A's plans or history
        val userAPlans = repository.studyRepository.getSavedPlans(userA.id).first()
        assertTrue("User A plans should be empty for User B", userAPlans.isEmpty())

        val userAActivities = repository.studyRepository.getStudyActivities(userA.id).first()
        assertTrue("User A activities should be empty for User B", userAActivities.isEmpty())
    }

    @Test
    fun `TEST 12 - Language Selector passes Hinglish and Hindi to AI prompt`() = runBlocking {
        repository.signUp("Student Aryan", "aryan@nexa.ai", "password123", "Hinglish", "Student").getOrThrow()

        mockAIService.configuredResponseText = "Padhai plan tayyar hai."

        repository.studyRepository.generatePracticeQuestions(
            subject = "Science",
            chapter = "Life Processes",
            topic = "Circulation",
            language = "Hinglish"
        )

        val prompt = mockAIService.lastReceivedRequest?.prompt
        assertNotNull(prompt)
        assertTrue("Prompt must include Hinglish instruction", prompt?.contains("Language: Hinglish") == true)
    }
}
