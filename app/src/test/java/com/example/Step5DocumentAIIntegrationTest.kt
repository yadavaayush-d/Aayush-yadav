package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NexaDatabase
import com.example.data.repository.DocumentAIRepository
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AINetworkException
import com.example.domain.ai.AIOrchestrationGateway
import com.example.domain.ai.AIRateLimiter
import com.example.domain.ai.AIService
import com.example.domain.ai.AIServiceRequest
import com.example.domain.ai.AIServiceResponse
import com.example.domain.ai.EntitlementManager
import com.example.domain.documents.DocumentConstants
import com.example.domain.documents.DocumentPermissionException
import com.example.domain.documents.DocumentRequestType
import com.example.domain.documents.UnsupportedDocumentTypeException
import com.example.ui.documents.DocumentTab
import com.example.ui.documents.DocumentUiState
import com.example.ui.documents.DocumentViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step5DocumentAIIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: NexaDatabase
    private lateinit var testService: FakeDocumentAIService
    private lateinit var gateway: AIOrchestrationGateway
    private lateinit var repository: NexaRepository
    private lateinit var docRepo: DocumentAIRepository

    class FakeDocumentAIService : AIService {
        var shouldThrowNetworkError = false
        var lastRequest: AIServiceRequest? = null

        override suspend fun sendMessage(request: AIServiceRequest): Result<AIServiceResponse> {
            lastRequest = request
            if (shouldThrowNetworkError) {
                return Result.failure(AINetworkException("Unable to reach AI server. Check your internet connection."))
            }

            val p = request.prompt
            val content = when (request.requestType) {
                DocumentRequestType.DOCUMENT_SUMMARY.id -> {
                    """
                    1. Short Summary:
                    Nexa AI is an all-in-one AI assistant platform designed for study, content creation, document understanding, and multilingual workflows.
                    2. Main Points:
                    - Unified AI architecture supporting Study AI, Creator AI, Document AI, and Language AI.
                    - Secure server-side AI routing with strict per-user document ownership.
                    3. Important Sections:
                    - Section 1: Overview of Nexa AI core modules and capabilities.
                    4. Key Terms:
                    - Nexa AI
                    - Document AI
                    - Creator AI
                    - Study AI
                    5. Action Items:
                    - Upload documents to summarize, search, and extract notes.
                    """.trimIndent()
                }
                DocumentRequestType.DOCUMENT_QUESTION.id -> {
                    if (p.contains("stock price in 2035", ignoreCase = true)) {
                        DocumentConstants.NOT_FOUND_RESPONSE
                    } else {
                        "Based on [Section 1], Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages."
                    }
                }
                DocumentRequestType.DOCUMENT_KEY_POINTS.id -> {
                    """
                    1. Important Points:
                    - Nexa AI provides an integrated workspace for study, content creation, documents, and languages.
                    - Users can upload PDF, TXT, and DOCX documents for analysis.
                    2. Important Names/Terms:
                    - Nexa AI
                    - Study AI
                    - Creator AI
                    - Document AI
                    3. Dates:
                    - No specific dates present
                    4. Numbers/Data:
                    - 4 core active workspaces mentioned
                    5. Conclusions:
                    - Nexa AI streamlines academic, creative, and document workflows in one platform.
                    """.trimIndent()
                }
                else -> "Processed document response for: ${request.requestType}"
            }

            return Result.success(
                AIServiceResponse(
                    content = content,
                    tokensUsed = 170,
                    modelName = "gemini-2.5-flash",
                    latencyMs = 40L
                )
            )
        }
    }

    private fun buildValidDocxArchiveBytes(paragraphText: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry("word/document.xml")
            zos.putNextEntry(entry)
            val xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body>
                        <w:p>
                            <w:r>
                                <w:t>$paragraphText</w:t>
                            </w:r>
                        </w:p>
                    </w:body>
                </w:document>
            """.trimIndent()
            zos.write(xml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NexaDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        testService = FakeDocumentAIService()
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
        docRepo = repository.documentRepository

        runBlocking {
            repository.signUp(
                name = "Ananya Researcher",
                email = "ananya.docs@nexa.ai",
                password = "password123",
                preferredLanguage = "English",
                userType = "Student"
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    // -------------------------------------------------------------
    // Test 1 — Upload TXT Document
    // -------------------------------------------------------------
    @Test
    fun test1_uploadTxtDocument_succeedsAndMarksReady() = runBlocking {
        val sampleText = "Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages."
        val statuses = mutableListOf<String>()

        val result = docRepo.uploadDocument(
            fileName = "nexa_overview.txt",
            rawBytes = sampleText.toByteArray(StandardCharsets.UTF_8),
            explicitFileType = "TXT",
            onStatusProgress = { statuses.add(it) }
        )

        assertTrue("TXT upload should succeed", result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals("Ready", doc.processingStatus)
        assertEquals("extracted", doc.extractedTextStatus)
        assertEquals("TXT", doc.fileType)
        assertEquals(sampleText, doc.extractedText)
        assertTrue("Statuses should progress through Uploading, Processing, Ready", statuses.containsAll(listOf("Uploading", "Processing", "Ready")))

        val currentUser = repository.currentUser.value!!
        assertEquals(currentUser.id, doc.userId)

        val myDocs = docRepo.getMyDocumentsSync(currentUser.id).getOrThrow()
        assertEquals(1, myDocs.size)
        assertEquals("nexa_overview.txt", myDocs[0].fileName)
    }

    // -------------------------------------------------------------
    // Test 2 — Upload PDF Document
    // -------------------------------------------------------------
    @Test
    fun test2_uploadPdfDocument_extractsTextAndMarksReady() = runBlocking {
        val pdfBytes = """
            %PDF-1.4
            1 0 obj
            << /Type /Page >>
            stream
            BT
            (Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages.) Tj
            ET
            endstream
            endobj
            %%EOF
        """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)

        val result = docRepo.uploadDocument(
            fileName = "research_paper.pdf",
            rawBytes = pdfBytes,
            explicitFileType = "PDF"
        )

        assertTrue("PDF upload should succeed", result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals("PDF", doc.fileType)
        assertEquals("Ready", doc.processingStatus)
        assertEquals("extracted", doc.extractedTextStatus)
        assertTrue(doc.extractedText.contains("Nexa AI is an all-in-one AI assistant platform"))
        assertTrue(doc.pageCount >= 1)
    }

    // -------------------------------------------------------------
    // Test 3 — Upload DOCX Document
    // -------------------------------------------------------------
    @Test
    fun test3_uploadDocxDocument_extractsXmlTextAndMarksReady() = runBlocking {
        val sampleText = "Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages."
        val docxBytes = buildValidDocxArchiveBytes(sampleText)

        val result = docRepo.uploadDocument(
            fileName = "project_spec.docx",
            rawBytes = docxBytes,
            explicitFileType = "DOCX"
        )

        assertTrue("DOCX upload should succeed", result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals("DOCX", doc.fileType)
        assertEquals("Ready", doc.processingStatus)
        assertEquals("extracted", doc.extractedTextStatus)
        assertEquals(sampleText, doc.extractedText)
    }

    // -------------------------------------------------------------
    // Test 4 — Invalid File Type & Unreadable File Validation
    // -------------------------------------------------------------
    @Test
    fun test4_invalidFileType_isRejectedWithClearErrorAndNoBrokenRecord() = runBlocking {
        val userId = repository.currentUser.value!!.id
        val invalidBytes = "MZ binary executable content".toByteArray(StandardCharsets.UTF_8)

        val result = docRepo.uploadDocument(
            fileName = "installer.exe",
            rawBytes = invalidBytes
        )

        assertTrue("Unsupported file format must be rejected", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is UnsupportedDocumentTypeException)
        assertTrue(exception!!.message!!.contains("Only PDF, TXT, and DOCX files are supported"))

        // Verify no broken record was created in My Documents
        val docs = docRepo.getMyDocumentsSync(userId).getOrThrow()
        assertTrue("No database record should be created for rejected file format", docs.isEmpty())
    }

    // -------------------------------------------------------------
    // Test 5 — Summarize Document
    // -------------------------------------------------------------
    @Test
    fun test5_summarizeDocument_returnsStructuredSummaryUsingDocumentsModule() = runBlocking {
        val sampleText = "Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages."
        val doc = docRepo.uploadTextBackedDocument(
            fileName = "nexa_summary_test.txt",
            textContent = sampleText,
            explicitFileType = "TXT"
        ).getOrThrow()

        val summaryResult = docRepo.summarizeDocument(doc.id)
        assertTrue("Summarize should succeed", summaryResult.isSuccess)
        val summary = summaryResult.getOrThrow()

        assertTrue(summary.shortSummary.isNotBlank())
        assertTrue(summary.mainPoints.isNotEmpty())
        assertTrue(summary.importantSections.isNotEmpty())
        assertTrue(summary.keyTerms.isNotEmpty())
        assertEquals("documents", testService.lastRequest?.module)
        assertEquals("document_summary", testService.lastRequest?.requestType)
    }

    // -------------------------------------------------------------
    // Test 6 — Ask Question Present in Document
    // -------------------------------------------------------------
    @Test
    fun test6_askQuestionPresentInDocument_returnsGroundedAnswerWithCitation() = runBlocking {
        val sampleText = "Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages."
        val doc = docRepo.uploadTextBackedDocument(
            fileName = "nexa_qa_present.txt",
            textContent = sampleText,
            explicitFileType = "TXT"
        ).getOrThrow()

        val answerResult = docRepo.askDocumentQuestion(
            documentId = doc.id,
            question = "What is Nexa AI mainly about?"
        )

        assertTrue("Ask question should succeed", answerResult.isSuccess)
        val answer = answerResult.getOrThrow()
        assertTrue(answer.isFoundInDocument)
        assertTrue(answer.answer.contains("all-in-one AI assistant platform", ignoreCase = true))
        assertTrue(answer.citedSections.isNotEmpty())
        assertEquals("documents", testService.lastRequest?.module)
        assertEquals("document_question", testService.lastRequest?.requestType)
    }

    // -------------------------------------------------------------
    // Test 7 — Ask Question Not Present in Document
    // -------------------------------------------------------------
    @Test
    fun test7_askQuestionNotPresentInDocument_returnsClearNotFoundMessage() = runBlocking {
        val sampleText = "Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages."
        val doc = docRepo.uploadTextBackedDocument(
            fileName = "nexa_qa_missing.txt",
            textContent = sampleText,
            explicitFileType = "TXT"
        ).getOrThrow()

        val answerResult = docRepo.askDocumentQuestion(
            documentId = doc.id,
            question = "What is the company's stock price in 2035?"
        )

        assertTrue("Ask question should succeed cleanly", answerResult.isSuccess)
        val answer = answerResult.getOrThrow()
        assertFalse("Should mark as not found in document", answer.isFoundInDocument)
        assertEquals(DocumentConstants.NOT_FOUND_RESPONSE, answer.answer)
        assertTrue("Should not cite fake sections when not found", answer.citedSections.isEmpty())
    }

    // -------------------------------------------------------------
    // Test 8 — Extract Key Points
    // -------------------------------------------------------------
    @Test
    fun test8_extractKeyPoints_returnsStructuredPointsFromDocument() = runBlocking {
        val sampleText = "Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages."
        val doc = docRepo.uploadTextBackedDocument(
            fileName = "nexa_keypoints.txt",
            textContent = sampleText,
            explicitFileType = "TXT"
        ).getOrThrow()

        val kpResult = docRepo.extractKeyPoints(doc.id)
        assertTrue("Extract key points should succeed", kpResult.isSuccess)
        val kp = kpResult.getOrThrow()
        assertTrue(kp.importantPoints.isNotEmpty())
        assertTrue(kp.namesAndTerms.isNotEmpty())
        assertTrue(kp.conclusions.isNotEmpty())
        assertEquals("documents", testService.lastRequest?.module)
        assertEquals("document_key_points", testService.lastRequest?.requestType)
    }

    // -------------------------------------------------------------
    // Test 9 — Find Information (Search Document)
    // -------------------------------------------------------------
    @Test
    fun test9_findInformation_searchesDocumentChunksAndReturnsMatchingSnippet() = runBlocking {
        val sampleText = "Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages."
        val doc = docRepo.uploadTextBackedDocument(
            fileName = "nexa_search.txt",
            textContent = sampleText,
            explicitFileType = "TXT"
        ).getOrThrow()

        val searchResult = docRepo.searchDocument(doc.id, "study")
        assertTrue("Document search should succeed", searchResult.isSuccess)
        val found = searchResult.getOrThrow()
        assertEquals("study", found.query)
        assertTrue("Should return matching section/snippet", found.matches.isNotEmpty())
        assertTrue(found.matches[0].snippet.contains("study", ignoreCase = true))
        assertEquals("Section 1", found.matches[0].pageOrSection)
    }

    // -------------------------------------------------------------
    // Test 10 — Document Notes (Add, Query, Edit, Delete)
    // -------------------------------------------------------------
    @Test
    fun test10_documentNotes_addRefreshEditAndDeletePersistProperly() = runBlocking {
        val sampleText = "Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages."
        val doc = docRepo.uploadTextBackedDocument(
            fileName = "nexa_notes.txt",
            textContent = sampleText,
            explicitFileType = "TXT"
        ).getOrThrow()

        // 1. Add a note
        val createdNote = docRepo.addDocumentNote(doc.id, "Important exam reference on Section 1").getOrThrow()
        assertEquals("Important exam reference on Section 1", createdNote.note)

        // 2. Refresh / re-query notes
        val notesAfterAdd = docRepo.getDocumentNotesSync(doc.id).getOrThrow()
        assertEquals(1, notesAfterAdd.size)
        assertEquals(createdNote.id, notesAfterAdd[0].id)

        // 3. Edit the note
        val editedNote = docRepo.editDocumentNote(createdNote.id, "Updated note: Review Section 1 and Section 2").getOrThrow()
        assertEquals("Updated note: Review Section 1 and Section 2", editedNote.note)

        val notesAfterEdit = docRepo.getDocumentNotesSync(doc.id).getOrThrow()
        assertEquals("Updated note: Review Section 1 and Section 2", notesAfterEdit[0].note)

        // 4. Delete the note
        val deleteRes = docRepo.deleteDocumentNote(createdNote.id)
        assertTrue(deleteRes.isSuccess)

        val notesAfterDelete = docRepo.getDocumentNotesSync(doc.id).getOrThrow()
        assertTrue("Note should be removed after deletion", notesAfterDelete.isEmpty())
    }

    // -------------------------------------------------------------
    // Test 11 — Ownership & Security Test (User B cannot access User A's documents)
    // -------------------------------------------------------------
    @Test
    fun test11_ownershipAndSecurity_userBCannotAccessModifyOrDeleteUserADocument() = runBlocking {
        val userA = repository.currentUser.value!!
        val sampleText = "Confidential research notes belonging exclusively to User A."
        val docA = docRepo.uploadTextBackedDocument(
            fileName = "user_a_private.txt",
            textContent = sampleText,
            explicitFileType = "TXT"
        ).getOrThrow()
        val noteA = docRepo.addDocumentNote(docA.id, "User A private note").getOrThrow()

        // Register and sign in as User B
        repository.signUp(
            name = "User B",
            email = "userb.docs@nexa.ai",
            password = "password123",
            preferredLanguage = "English",
            userType = "Professional"
        )
        val userB = repository.currentUser.value!!
        assertFalse(userA.id == userB.id)

        // 1. User B cannot view User A's document list
        val listAttempt = docRepo.getMyDocumentsSync(userA.id)
        assertTrue(listAttempt.isFailure)
        assertTrue(listAttempt.exceptionOrNull() is DocumentPermissionException)
        val flowAttempt = docRepo.getMyDocuments(userA.id).first()
        assertTrue(flowAttempt.isEmpty())

        // 2. User B cannot open User A's document or read extracted text
        val openAttempt = docRepo.openDocument(docA.id)
        assertTrue(openAttempt.isFailure)
        assertTrue(openAttempt.exceptionOrNull() is DocumentPermissionException)

        // 3. User B cannot ask questions about or summarize User A's document
        val qaAttempt = docRepo.askDocumentQuestion(docA.id, "What is in this document?")
        assertTrue(qaAttempt.isFailure)
        assertTrue(qaAttempt.exceptionOrNull() is DocumentPermissionException)

        val summaryAttempt = docRepo.summarizeDocument(docA.id)
        assertTrue(summaryAttempt.isFailure)
        assertTrue(summaryAttempt.exceptionOrNull() is DocumentPermissionException)

        // 4. User B cannot read or edit User A's notes
        val notesAttempt = docRepo.getDocumentNotesSync(docA.id)
        assertTrue(notesAttempt.isFailure)
        val editNoteAttempt = docRepo.editDocumentNote(noteA.id, "Hacked note")
        assertTrue(editNoteAttempt.isFailure)

        // 5. User B cannot delete User A's document
        val deleteAttempt = docRepo.deleteDocument(docA.id)
        assertTrue(deleteAttempt.isFailure)
        assertTrue(deleteAttempt.exceptionOrNull() is DocumentPermissionException)

        // Verify User A's document is still intact when User A logs back in
        repository.login("ananya.docs@nexa.ai", "password123")
        val intactDoc = docRepo.openDocument(docA.id).getOrNull()
        assertNotNull("User A's document must remain intact", intactDoc)
        assertEquals(sampleText, intactDoc?.extractedText)
    }

    // -------------------------------------------------------------
    // Test 12 — Delete Document Test
    // -------------------------------------------------------------
    @Test
    fun test12_deleteDocument_removesDocumentChunksAndNotesCompletely() = runBlocking {
        val userA = repository.currentUser.value!!
        val sampleText = "Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages."
        val doc = docRepo.uploadTextBackedDocument(
            fileName = "to_be_deleted.txt",
            textContent = sampleText,
            explicitFileType = "TXT"
        ).getOrThrow()
        docRepo.addDocumentNote(doc.id, "Note that should also be deleted")

        // Verify chunks and notes exist prior to deletion
        assertTrue(database.documentDao().getChunksForDocument(doc.id, userA.id).isNotEmpty())
        assertTrue(database.documentDao().getNotesForDocumentSync(doc.id, userA.id).isNotEmpty())

        // Delete document
        val delResult = docRepo.deleteDocument(doc.id)
        assertTrue("Document deletion should succeed", delResult.isSuccess)

        // Verify document disappears from My Documents
        val remainingDocs = docRepo.getMyDocumentsSync(userA.id).getOrThrow()
        assertTrue("My Documents should be empty after deleting document", remainingDocs.isEmpty())

        // Verify chunks, notes, and raw record are gone
        assertNull(database.documentDao().getDocumentByIdRaw(doc.id))
        assertTrue(database.documentDao().getChunksForDocument(doc.id, userA.id).isEmpty())
        assertTrue(database.documentDao().getNotesForDocumentSync(doc.id, userA.id).isEmpty())
    }

    // -------------------------------------------------------------
    // Test 13 — ViewModel & Usage Tracking End-to-End Verification
    // -------------------------------------------------------------
    @Test
    fun test13_documentViewModel_endToEndWorkflowAndUsageTracking() = runBlocking {
        val viewModel = DocumentViewModel(repository)

        // Upload document via ViewModel
        viewModel.uploadTextBackedDocument(
            fileName = "handbook.txt",
            textContent = "Nexa AI is an all-in-one AI assistant platform for study, content creation, documents, and languages.",
            explicitType = "TXT"
        )

        val uploaded = withTimeout(5000) {
            viewModel.selectedDocument.filter { it != null }.first()
        }
        assertNotNull("Selected document should be populated after upload", uploaded)
        assertEquals("Ready", viewModel.uploadState.value.status)

        // Trigger Summarize via ViewModel
        viewModel.selectTab(DocumentTab.SUMMARIZE)
        viewModel.summarizeSelectedDocument()
        val summaryState = withTimeout(5000) {
            viewModel.summaryState.filter { it is DocumentUiState.Success }.first()
        }
        assertTrue(summaryState is DocumentUiState.Success)

        // Trigger Ask Question via ViewModel
        viewModel.selectTab(DocumentTab.ASK_QUESTIONS)
        viewModel.askQuestion("What is Nexa AI mainly about?")
        val qaState = withTimeout(5000) {
            viewModel.questionState.filter { it is DocumentUiState.Success }.first()
        }
        assertTrue(qaState is DocumentUiState.Success)

        // Verify AIRequests and AIUsage logged with module = "documents"
        val userId = repository.currentUser.value!!.id
        val allRequests = database.aiRequestDao().getAllRequests().first()
        val docRequests = allRequests.filter { it.userId == userId && it.module == "documents" }
        assertTrue("Should log Document AI requests in AIRequests table", docRequests.size >= 2)

        val totalUsage = repository.getTotalUsage(userId).first()
        assertTrue("Should increment AIUsage count for Document AI operations", totalUsage >= 2)
    }
}
