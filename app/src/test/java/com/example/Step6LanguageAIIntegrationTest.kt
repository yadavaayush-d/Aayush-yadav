package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NexaDatabase
import com.example.data.repository.LanguageAIRepository
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AINetworkException
import com.example.domain.ai.AIOrchestrationGateway
import com.example.domain.ai.AIRateLimiter
import com.example.domain.ai.AIService
import com.example.domain.ai.AIServiceRequest
import com.example.domain.ai.AIServiceResponse
import com.example.domain.ai.EntitlementManager
import com.example.domain.language.LanguageConfigurationException
import com.example.domain.language.LanguageEmptyTextException
import com.example.domain.language.LanguageInvalidInputException
import com.example.domain.language.LanguageOptions
import com.example.domain.language.LanguagePermissionException
import com.example.domain.language.LanguageRequestType
import com.example.ui.language.LanguageTab
import com.example.ui.language.LanguageUiState
import com.example.ui.language.LanguageViewModel
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Step6LanguageAIIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: NexaDatabase
    private lateinit var repository: NexaRepository
    private lateinit var fakeAIService: FakeLanguageAIService
    private lateinit var langRepo: LanguageAIRepository

    class FakeLanguageAIService : AIService {
        var shouldThrowNetworkError = false
        var lastRequest: AIServiceRequest? = null

        override suspend fun sendMessage(request: AIServiceRequest): Result<AIServiceResponse> {
            lastRequest = request
            if (shouldThrowNetworkError) {
                return Result.failure(AINetworkException("Simulated network timeout talking to Language AI."))
            }

            val prompt = request.prompt
            val content = when {
                prompt.contains("Translate the following user-provided text", ignoreCase = true) &&
                    prompt.contains("How are you today?", ignoreCase = true) -> {
                    """
                    1. Translation: आप आज कैसे हैं?
                    2. Notes: Formal and polite Hindi greeting ("Aap aaj kaise hain?").
                    """.trimIndent()
                }
                prompt.contains("Translate the following user-provided text", ignoreCase = true) &&
                    prompt.contains("kal mera exam hai", ignoreCase = true) &&
                    prompt.contains("to English", ignoreCase = true) -> {
                    """
                    1. Translation: I have my exam tomorrow.
                    2. Notes: Translated from Romanized Hindi (Hinglish); "kal" here refers to tomorrow based on future context.
                    """.trimIndent()
                }
                prompt.contains("Translate the following user-provided text", ignoreCase = true) &&
                    prompt.contains("mujhe kal school jana hai", ignoreCase = true) &&
                    prompt.contains("to Hindi", ignoreCase = true) -> {
                    """
                    1. Translation: मुझे कल स्कूल जाना है।
                    2. Notes: Converted Romanized Hindi (Hinglish) into Devanagari Hindi script.
                    """.trimIndent()
                }
                prompt.contains("Rewrite the following user-provided text", ignoreCase = true) -> {
                    "I would like to request leave for tomorrow as I have urgent personal commitments to attend to at home."
                }
                prompt.contains("Perform a careful grammar and spelling check", ignoreCase = true) -> {
                    """
                    1. Corrected Version: She goes to school every day.
                    2. Grammar Issues:
                    - Subject-verb agreement error: Third-person singular subject "She" requires "goes" instead of "go".
                    3. Spelling Issues:
                    - "everyday" (adjective meaning commonplace) should be written as two words "every day" (adverbial phrase of frequency).
                    4. Explanation of Corrections:
                    - Changed "go" to "goes" to match the singular pronoun "She" in simple present tense, and separated "everyday" into "every day" to indicate daily frequency.
                    """.trimIndent()
                }
                prompt.contains("Detect the language of the following user-provided text", ignoreCase = true) &&
                    prompt.contains("Bonjour tout le monde", ignoreCase = true) -> {
                    """
                    1. Detected Language: French
                    2. Confidence & Explanation: High confidence. "Bonjour tout le monde" is a standard French greeting meaning "Hello everyone".
                    3. Ambiguous: No
                    """.trimIndent()
                }
                prompt.contains("Detect the language of the following user-provided text", ignoreCase = true) &&
                    prompt.contains("no", ignoreCase = true) -> {
                    """
                    1. Detected Language: English
                    2. Confidence & Explanation: Uncertain / ambiguous single-word input. "no" appears identically in English, Spanish, Italian, and Romanized scripts.
                    3. Ambiguous: Yes
                    """.trimIndent()
                }
                prompt.contains("Summarize the following user-provided text in Hindi", ignoreCase = true) -> {
                    "सौर ऊर्जा एक स्वच्छ और नवीकरणीय ऊर्जा स्रोत है जो बिजली के बिलों को कम करने और पर्यावरण की रक्षा करने में मदद करता है।"
                }
                prompt.contains("Explain the following user-provided text clearly in Hindi", ignoreCase = true) -> {
                    """
                    1. Overall Meaning: इस वाक्य का अर्थ है कि कठिन परिश्रम और धैर्य से ही स्थायी सफलता मिलती है।
                    2. Difficult Words & Concepts:
                    - Perseverance (दृढ़ता / लगन): कठिनाइयों के बावजूद प्रयास जारी रखना।
                    - Resilience (लचीलापन): असफलता से उबरने की क्षमता।
                    3. Step-by-Step Breakdown: पहले भाग में निरंतर प्रयास का महत्व बताया गया है और दूसरे भाग में इसके परिणाम को समझाया गया है।
                    """.trimIndent()
                }
                prompt.contains("Create an interactive language learning practice set", ignoreCase = true) -> {
                    """
                    1. Vocabulary Practice:
                    - Airport: हवाई अड्डा (Place where flights depart and arrive)
                    - Ticket: टिकट (Boarding pass or travel document)
                    - Platform: प्लेटफ़ॉर्म (Train boarding area)
                    - Luggage: सामान (Travel bags and suitcases)

                    2. Conversation Practice:
                    Traveler: Excuse me, could you please tell me which platform the express train to Mumbai departs from?
                    Station Agent: Certainly! It departs from Platform 4 in ten minutes.

                    3. Exercises:
                    Q1 [Vocabulary]: What word refers to the bags and suitcases you carry while traveling? | ANSWER: Luggage | HINT: Starts with L and means travel bags.
                    Q2 [Simple Question]: How do you politely ask someone where the airport is in English? | ANSWER: Could you tell me where the airport is | HINT: Use "Could you tell me..." for polite inquiries.
                    Q3 [Fill-in-the-blank]: Please show your boarding ___ at the gate. | ANSWER: pass | HINT: A two-word phrase: boarding pass.
                    Q4 [Translation Exercise]: Translate "मेरा टिकट कहाँ है?" into English. | ANSWER: Where is my ticket | HINT: "कहाँ" means "Where" and "मेरा टिकट" means "my ticket".
                    """.trimIndent()
                }
                prompt.contains("educational language-learning conversation", ignoreCase = true) -> {
                    """
                    1. AI Message: Hello! It is wonderful to practice English with you today. Where would you like to travel next, and how do you usually prepare for a trip?
                    2. Correction: I want to practice speaking English for my upcoming trip.
                    3. Suggested Natural Alternative: I'd love to practice my spoken English before my next trip.
                    4. Short Explanation: "Practice speaking" uses the gerund (-ing form) after the verb "practice" in natural English.
                    """.trimIndent()
                }
                else -> {
                    """
                    1. Translation: Translated output text.
                    2. Notes: None
                    """.trimIndent()
                }
            }

            return Result.success(
                AIServiceResponse(
                    content = content,
                    tokensUsed = 110,
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

        fakeAIService = FakeLanguageAIService()
        val gateway = AIOrchestrationGateway(
            aiService = fakeAIService,
            rateLimiter = AIRateLimiter(minIntervalMillis = 0L, maxRequestsPerMinute = 500)
        )
        val entitlementManager = EntitlementManager()

        repository = NexaRepository(
            database = database,
            gateway = gateway,
            entitlementManager = entitlementManager,
            context = context
        )
        langRepo = repository.languageRepository

        runBlocking {
            repository.signUp(
                name = "Ananya Language",
                email = "ananya.lang@nexa.ai",
                password = "Password123",
                preferredLanguage = "English",
                userType = "Student"
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------
    // Test 1 — Translation ("How are you today?" -> Hindi, Copy, Swap, AIRequests log)
    // -------------------------------------------------------------
    @Test
    fun test1_translation_hindiAndCopyAndSwapAndAuditLogging() = runBlocking {
        val viewModel = LanguageViewModel(repository)

        viewModel.updateSourceLanguage("English")
        viewModel.updateTargetLanguage("Hindi")
        viewModel.updateTranslatorInput("How are you today?")

        viewModel.translate(
            text = "How are you today?",
            sourceLang = "English",
            targetLang = "Hindi"
        )

        val state = withTimeout(3000) {
            viewModel.translateState.filter { it is LanguageUiState.Success || it is LanguageUiState.Error }.first()
        }
        assertTrue("Expected Translation Success state", state is LanguageUiState.Success)
        val result = (state as LanguageUiState.Success).data

        assertEquals("English", result.sourceLanguage)
        assertEquals("Hindi", result.targetLanguage)
        assertEquals("आप आज कैसे हैं?", result.translatedText)
        assertTrue(result.disclaimer.isNotBlank())
        assertEquals("language", fakeAIService.lastRequest?.module)
        assertEquals("translate", fakeAIService.lastRequest?.requestType)

        // Verify Copy works
        viewModel.copyToClipboard(result.translatedText, context, "Translation")
        assertEquals("आप आज कैसे हैं?", viewModel.lastCopiedText.value)
        assertEquals("Copied to clipboard!", viewModel.feedbackBanner.value)

        // Verify Swap Languages works
        viewModel.swapLanguages()
        assertEquals("Hindi", viewModel.sourceLanguage.value)
        assertEquals("English", viewModel.targetLanguage.value)

        // Verify Clear works
        viewModel.clearTranslator()
        assertEquals("", viewModel.translatorInput.value)
        assertTrue(viewModel.translateState.value is LanguageUiState.Idle)

        // Verify logged in AIRequests with module = "language" and request_type = "translate"
        val user = repository.currentUser.value!!
        val aiRequests = database.aiRequestDao().getAllRequests().first()
        val translateReq = aiRequests.firstOrNull { it.userId == user.id && it.requestType == "translate" }
        assertNotNull("Expected translate request in AIRequests table", translateReq)
        assertEquals("language", translateReq!!.module)
        assertEquals("completed", translateReq.status)
    }

    // -------------------------------------------------------------
    // Test 2 — Hinglish Translation ("kal mera exam hai" -> English & Hindi script)
    // -------------------------------------------------------------
    @Test
    fun test2_hinglishTranslation_toEnglishAndHindiScript() = runBlocking {
        val toEnglishResult = langRepo.translateText(
            sourceLanguage = "Hinglish",
            targetLanguage = "English",
            text = "kal mera exam hai"
        )
        assertTrue(toEnglishResult.isSuccess)
        val englishData = toEnglishResult.getOrThrow()
        assertEquals("Hinglish", englishData.sourceLanguage)
        assertEquals("English", englishData.targetLanguage)
        assertEquals("I have my exam tomorrow.", englishData.translatedText)
        assertTrue(englishData.notesOrNuances.contains("Hinglish", ignoreCase = true))

        val toHindiScriptResult = langRepo.translateText(
            sourceLanguage = "Hinglish",
            targetLanguage = "Hindi",
            text = "mujhe kal school jana hai"
        )
        assertTrue(toHindiScriptResult.isSuccess)
        val hindiData = toHindiScriptResult.getOrThrow()
        assertEquals("मुझे कल स्कूल जाना है।", hindiData.translatedText)
    }

    // -------------------------------------------------------------
    // Test 3 — Rewrite ("i want leave tomorrow because i have work at home" -> Make professional)
    // -------------------------------------------------------------
    @Test
    fun test3_rewrite_makeProfessional() = runBlocking {
        val input = "i want leave tomorrow because i have work at home"
        val result = langRepo.rewriteText(
            text = input,
            styleOption = "Make professional",
            language = "English"
        )

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Make professional", data.styleOption)
        assertEquals("English", data.language)
        assertEquals(input, data.inputText)
        assertTrue(data.rewrittenText.contains("request leave for tomorrow", ignoreCase = true))

        val aiRequests = database.aiRequestDao().getAllRequests().first()
        assertTrue(aiRequests.any { it.module == "language" && it.requestType == "rewrite" && it.status == "completed" })
    }

    // -------------------------------------------------------------
    // Test 4 — Grammar Check ("She go to school everyday")
    // -------------------------------------------------------------
    @Test
    fun test4_grammarCheck_returnsCorrectedSentenceAndExplanations() = runBlocking {
        val input = "She go to school everyday"
        val result = langRepo.checkGrammar(
            text = input,
            language = "English"
        )

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("She goes to school every day.", data.correctedVersion)
        assertTrue(data.grammarIssues.any { it.contains("goes", ignoreCase = true) })
        assertTrue(data.spellingIssues.any { it.contains("every day", ignoreCase = true) })
        assertTrue(data.explanations.isNotEmpty())

        val aiRequests = database.aiRequestDao().getAllRequests().first()
        assertTrue(aiRequests.any { it.module == "language" && it.requestType == "grammar_check" && it.status == "completed" })
    }

    // -------------------------------------------------------------
    // Test 5 — Language Detection ("Bonjour tout le monde" -> French + Ambiguity handling)
    // -------------------------------------------------------------
    @Test
    fun test5_languageDetection_frenchAndAmbiguousHandling() = runBlocking {
        val frenchRes = langRepo.detectLanguage("Bonjour tout le monde")
        assertTrue(frenchRes.isSuccess)
        val frenchData = frenchRes.getOrThrow()
        assertEquals("French", frenchData.detectedLanguage)
        assertFalse(frenchData.isAmbiguous)
        assertTrue(frenchData.confidenceExplanation.contains("French", ignoreCase = true))

        val ambiguousRes = langRepo.detectLanguage("no")
        assertTrue(ambiguousRes.isSuccess)
        val ambiguousData = ambiguousRes.getOrThrow()
        assertTrue("Short cross-language word should be flagged as ambiguous", ambiguousData.isAmbiguous)
        assertTrue(ambiguousData.confidenceExplanation.contains("ambiguous", ignoreCase = true))

        val aiRequests = database.aiRequestDao().getAllRequests().first()
        assertTrue(aiRequests.any { it.module == "language" && it.requestType == "detect_language" && it.status == "completed" })
    }

    // -------------------------------------------------------------
    // Test 6 — Summarize (Paragraph of text -> Hindi, Short)
    // -------------------------------------------------------------
    @Test
    fun test6_summarize_multilingualHindiSummary() = runBlocking {
        val paragraph = """
            Solar energy is a clean and renewable source of power that converts sunlight into electricity.
            By adopting rooftop solar panels, households and businesses can reduce their electricity bills
            significantly while lowering carbon emissions and protecting the environment for future generations.
        """.trimIndent()

        val result = langRepo.summarizeText(
            text = paragraph,
            outputLanguage = "Hindi",
            summaryLength = "Short"
        )

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Hindi", data.outputLanguage)
        assertEquals("Short", data.summaryLength)
        assertTrue(data.summaryText.contains("सौर ऊर्जा"))

        val aiRequests = database.aiRequestDao().getAllRequests().first()
        assertTrue(aiRequests.any { it.module == "language" && it.requestType == "summarize" && it.status == "completed" })
    }

    // -------------------------------------------------------------
    // Test 7 — Explain Text (Difficult sentence -> Hindi, Student level)
    // -------------------------------------------------------------
    @Test
    fun test7_explainText_hindiExplanation() = runBlocking {
        val sentence = "Perseverance and resilience are the foundational pillars of enduring achievement."
        val result = langRepo.explainText(
            text = sentence,
            explanationLanguage = "Hindi",
            explanationLevel = "Student"
        )

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Hindi", data.explanationLanguage)
        assertEquals("Student", data.explanationLevel)
        assertTrue(data.overallMeaning.contains("कठिन परिश्रम"))
        assertTrue(data.keyWordsAndConcepts.isNotEmpty())
        assertTrue(data.stepByStepExplanation.isNotBlank())

        val aiRequests = database.aiRequestDao().getAllRequests().first()
        assertTrue(aiRequests.any { it.module == "language" && it.requestType == "explain_text" && it.status == "completed" })
    }

    // -------------------------------------------------------------
    // Test 8 — Language Practice & Progress Tracking (English, Beginner, Travel & Directions)
    // -------------------------------------------------------------
    @Test
    fun test8_languagePractice_exercisesAndProgressTracking() = runBlocking {
        val user = repository.currentUser.value!!
        val practiceRes = langRepo.generateLanguagePractice(
            languageToLearn = "English",
            level = "Beginner",
            topic = "Travel & Directions"
        )

        assertTrue(practiceRes.isSuccess)
        val data = practiceRes.getOrThrow()
        assertEquals("English", data.languageToLearn)
        assertEquals("Beginner", data.level)
        assertEquals("Travel & Directions", data.topic)
        assertTrue(data.vocabularyList.isNotEmpty())
        assertTrue(data.conversationStarter.isNotBlank())
        assertEquals(4, data.exercises.size)

        // Check an answer (Correct)
        val ex1 = data.exercises[0]
        val check1 = langRepo.checkPracticeExerciseAnswer(data.sessionId, ex1, "Luggage")
        assertTrue(check1.isSuccess)
        assertEquals(true, check1.getOrThrow())

        // Check a second answer (Incorrect)
        val ex2 = data.exercises[1]
        val check2 = langRepo.checkPracticeExerciseAnswer(data.sessionId, ex2, "Completely wrong answer")
        assertTrue(check2.isSuccess)
        assertEquals(false, check2.getOrThrow())

        // Verify progress stats persisted in LanguagePracticeSessions table
        val statsRes = langRepo.getPracticeProgressSync(user.id)
        assertTrue(statsRes.isSuccess)
        val stats = statsRes.getOrThrow()
        assertEquals(1, stats.totalSessions)
        assertEquals(2, stats.totalQuestionsAttempted)
        assertEquals(1, stats.totalCorrectAnswers)
    }

    // -------------------------------------------------------------
    // Test 9 — Conversation Practice (Start & Reply with gentle corrections)
    // -------------------------------------------------------------
    @Test
    fun test9_conversationPractice_replyAndGentleCorrections() = runBlocking {
        val convRes = langRepo.practiceConversationTurn(
            languageToLearn = "English",
            level = "Beginner",
            topic = "Travel & Directions",
            userMessage = "I want practice speaking English for my trip."
        )

        assertTrue(convRes.isSuccess)
        val turn = convRes.getOrThrow()
        assertEquals("English", turn.languageToLearn)
        assertTrue(turn.aiReply.contains("wonderful to practice English", ignoreCase = true))
        assertTrue(turn.correction.contains("practice speaking", ignoreCase = true))
        assertTrue(turn.naturalAlternative.isNotBlank())
        assertTrue(turn.shortExplanation.isNotBlank())

        val aiRequests = database.aiRequestDao().getAllRequests().first()
        assertTrue(aiRequests.any { it.module == "language" && it.requestType == "conversation_practice" && it.status == "completed" })
    }

    // -------------------------------------------------------------
    // Test 10 — Save, Deduplication, History & Cross-User Ownership Enforcement
    // -------------------------------------------------------------
    @Test
    fun test10_saveAndHistoryAndCrossUserOwnershipSecurity() = runBlocking {
        val userA = repository.currentUser.value!!

        // Generate a translation for User A so it is recorded in LanguageProjects
        val transRes = langRepo.translateText("English", "Hindi", "How are you today?")
        assertTrue(transRes.isSuccess)
        val projectId = transRes.getOrThrow().projectId

        // Verify Language History for User A
        val historyA = langRepo.getLanguageHistorySync(userA.id).getOrThrow()
        assertTrue(historyA.any { it.id == projectId })

        // Save translation for User A & verify deduplication when saving identical item twice
        val save1 = langRepo.saveLanguageContent(
            projectType = LanguageRequestType.TRANSLATE.id,
            sourceLanguage = "English",
            targetLanguage = "Hindi",
            inputText = "How are you today?",
            outputText = "आप आज कैसे हैं?",
            projectId = projectId
        )
        assertTrue(save1.isSuccess)
        val savedId = save1.getOrThrow().id

        val save2 = langRepo.saveLanguageContent(
            projectType = LanguageRequestType.TRANSLATE.id,
            sourceLanguage = "English",
            targetLanguage = "Hindi",
            inputText = "How are you today?",
            outputText = "आप आज कैसे हैं?",
            projectId = projectId
        )
        assertTrue(save2.isSuccess)
        assertEquals("Saving identical content should deduplicate and return existing ID", savedId, save2.getOrThrow().id)

        val savedListA = langRepo.getSavedContentSync(userA.id).getOrThrow()
        assertEquals(1, savedListA.size)

        // Register & switch to User B
        val regB = repository.signUp(
            name = "User B",
            email = "userb_lang@nexa.ai",
            password = "Password123",
            preferredLanguage = "English",
            userType = "Professional"
        )
        assertTrue(regB.isSuccess)
        val userB = repository.currentUser.value!!
        assertFalse(userA.id == userB.id)

        // User B must NOT be able to view User A's history, open User A's project, or delete User A's project
        val historyCross = langRepo.getLanguageHistorySync(userA.id)
        assertTrue(historyCross.isFailure)
        assertTrue(historyCross.exceptionOrNull() is LanguagePermissionException)

        val openCross = langRepo.openLanguageProject(projectId)
        assertTrue(openCross.isFailure)
        assertTrue(openCross.exceptionOrNull() is LanguagePermissionException)

        val deleteCross = langRepo.deleteLanguageProject(projectId)
        assertTrue(deleteCross.isFailure)
        assertTrue(deleteCross.exceptionOrNull() is LanguagePermissionException)

        // User B must NOT be able to view or delete User A's saved language content
        val savedCross = langRepo.getSavedContentSync(userA.id)
        assertTrue(savedCross.isFailure)
        assertTrue(savedCross.exceptionOrNull() is LanguagePermissionException)

        val unsaveCross = langRepo.unsaveLanguageContent(savedId)
        assertTrue(unsaveCross.isFailure)
        assertTrue(unsaveCross.exceptionOrNull() is LanguagePermissionException)

        // Switch back to User A and verify unsave & project delete work for the legitimate owner
        repository.login("ananya.lang@nexa.ai", "Password123")
        assertEquals(userA.id, repository.currentUser.value?.id)

        val unsaveOwner = langRepo.unsaveLanguageContent(savedId)
        assertTrue(unsaveOwner.isSuccess)
        assertTrue(langRepo.getSavedContentSync(userA.id).getOrThrow().isEmpty())

        val deleteOwner = langRepo.deleteLanguageProject(projectId)
        assertTrue(deleteOwner.isSuccess)
        assertFalse(langRepo.getLanguageHistorySync(userA.id).getOrThrow().any { it.id == projectId })
    }

    // -------------------------------------------------------------
    // Test 11 — Error Handling, Validation, Usage Limits & ViewModel Recovery
    // -------------------------------------------------------------
    @Test
    fun test11_errorHandlingValidationAndViewModelRecovery() = runBlocking {
        // 1. Empty text
        val emptyRes = langRepo.translateText("English", "Hindi", "   ")
        assertTrue(emptyRes.isFailure)
        assertTrue(emptyRes.exceptionOrNull() is LanguageEmptyTextException)

        // 2. Invalid language selection
        val badLangRes = langRepo.translateText("Klingon", "Hindi", "Hello")
        assertTrue(badLangRes.isFailure)
        assertTrue(badLangRes.exceptionOrNull() is LanguageConfigurationException)

        // 3. Auto Detect as target language is rejected
        val autoTargetRes = langRepo.translateText("English", LanguageOptions.AUTO_DETECT, "Hello")
        assertTrue(autoTargetRes.isFailure)
        assertTrue(autoTargetRes.exceptionOrNull() is LanguageConfigurationException)

        // 4. Input exceeding 4000 characters
        val hugeInput = "a".repeat(4005)
        val hugeRes = langRepo.summarizeText(hugeInput, "English", "Short")
        assertTrue(hugeRes.isFailure)
        assertTrue(hugeRes.exceptionOrNull() is LanguageInvalidInputException)

        // 5. Network failure & ViewModel retry recovery
        val viewModel = LanguageViewModel(repository)
        viewModel.selectTab(LanguageTab.GRAMMAR_CHECK)
        assertEquals(LanguageTab.GRAMMAR_CHECK, viewModel.activeTab.value)

        fakeAIService.shouldThrowNetworkError = true
        viewModel.checkGrammar("She go to school everyday", "English")

        val errState = withTimeout(3000) {
            viewModel.grammarState.filter { it is LanguageUiState.Error || it is LanguageUiState.Success }.first()
        }
        assertTrue(errState is LanguageUiState.Error)
        assertEquals("NETWORK_ERROR", (errState as LanguageUiState.Error).errorCode)

        // Recover when network is restored and retryLastAction() is called
        fakeAIService.shouldThrowNetworkError = false
        viewModel.retryLastAction()

        val recoveredState = withTimeout(3000) {
            viewModel.grammarState.filter { it is LanguageUiState.Success }.first()
        }
        assertTrue(recoveredState is LanguageUiState.Success)
        assertEquals(
            "She goes to school every day.",
            (recoveredState as LanguageUiState.Success).data.correctedVersion
        )
    }
}
