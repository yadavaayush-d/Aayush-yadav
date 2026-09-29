package com.example.data.repository

import android.content.Context
import com.example.data.local.NexaDatabase
import com.example.data.local.entity.AIRequestEntity
import com.example.data.local.entity.AIUsageEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentNoteEntity
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
import com.example.domain.documents.DocumentAnswerResult
import com.example.domain.documents.DocumentConstants
import com.example.domain.documents.DocumentKeyPointsResult
import com.example.domain.documents.DocumentPermissionException
import com.example.domain.documents.DocumentProcessingException
import com.example.domain.documents.DocumentProcessor
import com.example.domain.documents.DocumentRequestType
import com.example.domain.documents.DocumentSearchResult
import com.example.domain.documents.DocumentSummaryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class DocumentAIRepository(
    private val database: NexaDatabase,
    private val gateway: AIOrchestrationGateway = AIOrchestrationGateway(),
    private val entitlementManager: EntitlementManager = EntitlementManager(),
    private val context: Context? = null,
    private val currentUserProvider: () -> UserEntity?
) {

    private fun getAuthenticatedUser(): UserEntity {
        return currentUserProvider() ?: throw AIAuthException("Please log in to use Document AI.")
    }

    private suspend fun verifyOwnershipAndGetDocument(
        documentId: String,
        requestingUserId: String? = null
    ): Result<DocumentEntity> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(
                DocumentPermissionException("Access denied: You do not have permission to access another user's document.")
            )
        }

        val rawDoc = database.documentDao().getDocumentByIdRaw(documentId)
            ?: return Result.failure(AIValidationException("Document not found."))

        if (rawDoc.userId != authUser.id) {
            return Result.failure(
                DocumentPermissionException("Access denied: You do not have permission to access another user's document.")
            )
        }

        return Result.success(rawDoc)
    }

    private suspend fun verifyEntitlementAndLog(
        user: UserEntity,
        requestType: DocumentRequestType
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
                        conversationId = "documents_workspace",
                        module = "documents",
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
                    conversationId = "documents_workspace",
                    module = "documents",
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

    // -------------------------------------------------------------
    // 2 & 5. DOCUMENT UPLOAD & PROCESSING (PDF, TXT, DOCX)
    // -------------------------------------------------------------
    suspend fun uploadDocument(
        fileName: String,
        rawBytes: ByteArray,
        explicitFileType: String? = null,
        onStatusProgress: ((String) -> Unit)? = null
    ): Result<DocumentEntity> {
        val user = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }

        val trimmedName = fileName.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(AIValidationException("File name cannot be empty."))
        }

        // 1. Verify supported file type (PDF, TXT, DOCX)
        val formatResult = DocumentProcessor.validateAndResolveFormat(trimmedName, explicitFileType)
        if (formatResult.isFailure) {
            return Result.failure(formatResult.exceptionOrNull()!!)
        }
        val resolvedType = formatResult.getOrThrow()

        // 2. Validate file size
        val sizeResult = DocumentProcessor.validateFileSize(rawBytes.size.toLong())
        if (sizeResult.isFailure) {
            return Result.failure(sizeResult.exceptionOrNull()!!)
        }

        val docId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val safeFileName = trimmedName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val internalStoragePath = "secure_store/${user.id}/${docId}_$safeFileName"

        // 3. Stage 1: Uploading
        onStatusProgress?.invoke(DocumentConstants.STATUS_UPLOADING)
        val initialEntity = DocumentEntity(
            id = docId,
            userId = user.id,
            fileName = trimmedName,
            fileType = resolvedType,
            fileSize = rawBytes.size.toLong(),
            storagePath = internalStoragePath,
            fileUrl = "local://documents/$docId",
            processingStatus = DocumentConstants.STATUS_UPLOADING,
            extractedTextStatus = DocumentConstants.EXTRACTED_PENDING,
            extractedText = "",
            pageCount = 0,
            createdAt = now,
            updatedAt = now
        )
        database.documentDao().insertDocument(initialEntity)

        // Write securely to app-private user directory if Context is provided
        context?.let { ctx ->
            try {
                val userDir = File(ctx.filesDir, "documents/${user.id}")
                if (!userDir.exists()) userDir.mkdirs()
                val destFile = File(userDir, "${docId}_$safeFileName")
                destFile.writeBytes(rawBytes)
            } catch (_: Exception) {
                // In-memory Room fallback remains intact
            }
        }

        // 4. Stage 2: Processing & Text Extraction
        onStatusProgress?.invoke(DocumentConstants.STATUS_PROCESSING)
        val processingEntity = initialEntity.copy(
            processingStatus = DocumentConstants.STATUS_PROCESSING,
            updatedAt = System.currentTimeMillis()
        )
        database.documentDao().updateDocument(processingEntity)

        val extractionResult = DocumentProcessor.extractTextFromBytes(resolvedType, rawBytes)
        if (extractionResult.isFailure) {
            val err = extractionResult.exceptionOrNull()
            val errorCode = (err as? AIServiceException)?.errorCode ?: "TEXT_EXTRACTION_FAILURE"
            val errorMsg = (err as? AIServiceException)?.userFriendlyMessage
                ?: (err?.message ?: "Failed to extract text from document.")

            val failedEntity = processingEntity.copy(
                processingStatus = DocumentConstants.STATUS_FAILED,
                extractedTextStatus = DocumentConstants.EXTRACTED_FAILED,
                errorCode = errorCode,
                errorMessage = errorMsg,
                updatedAt = System.currentTimeMillis()
            )
            database.documentDao().updateDocument(failedEntity)
            onStatusProgress?.invoke(DocumentConstants.STATUS_FAILED)
            return Result.failure(err ?: DocumentProcessingException(errorMsg))
        }

        val outcome = extractionResult.getOrThrow()

        // 5. Build and store retrieval chunks
        val chunks = DocumentProcessor.buildDocumentChunks(
            documentId = docId,
            userId = user.id,
            sections = outcome.sections
        )
        database.documentDao().insertChunks(chunks)

        // 6. Mark Document as Ready
        val readyEntity = processingEntity.copy(
            processingStatus = DocumentConstants.STATUS_READY,
            extractedTextStatus = DocumentConstants.EXTRACTED_READY,
            extractedText = outcome.fullText,
            pageCount = outcome.pageCount,
            errorCode = null,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )
        database.documentDao().updateDocument(readyEntity)
        onStatusProgress?.invoke(DocumentConstants.STATUS_READY)

        return Result.success(readyEntity)
    }

    /**
     * Convenience helper for uploading text-backed PDF, TXT, or DOCX documents.
     */
    suspend fun uploadTextBackedDocument(
        fileName: String,
        textContent: String,
        explicitFileType: String? = null,
        onStatusProgress: ((String) -> Unit)? = null
    ): Result<DocumentEntity> {
        val ext = (explicitFileType ?: fileName.substringAfterLast('.', "")).uppercase()
        val payloadBytes = if (ext == "PDF" && !textContent.trimStart().startsWith("%PDF")) {
            val escaped = textContent.lines().joinToString("\n") { line ->
                "(${line.replace("(", "\\(").replace(")", "\\)")}) Tj"
            }
            "%PDF-1.4\n1 0 obj\n<< /Type /Page >>\nstream\nBT\n$escaped\nET\nendstream\nendobj\n%%EOF"
                .toByteArray(StandardCharsets.UTF_8)
        } else {
            textContent.toByteArray(StandardCharsets.UTF_8)
        }
        return uploadDocument(fileName, payloadBytes, explicitFileType, onStatusProgress)
    }

    // -------------------------------------------------------------
    // 6 & 7. MY DOCUMENTS & DOCUMENT VIEWER (With Strict Ownership)
    // -------------------------------------------------------------
    fun getMyDocuments(userId: String): Flow<List<DocumentEntity>> {
        val authUser = currentUserProvider()
        if (authUser == null || authUser.id != userId) {
            return flowOf(emptyList())
        }
        return database.documentDao().getDocumentsByUser(userId)
    }

    suspend fun getMyDocumentsSync(userId: String): Result<List<DocumentEntity>> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }
        if (authUser.id != userId) {
            return Result.failure(DocumentPermissionException("Access denied: Cannot list another user's documents."))
        }
        return Result.success(database.documentDao().getDocumentsByUserSync(userId))
    }

    suspend fun openDocument(documentId: String, requestingUserId: String? = null): Result<DocumentEntity> {
        return verifyOwnershipAndGetDocument(documentId, requestingUserId)
    }

    // -------------------------------------------------------------
    // 8. DOCUMENT SUMMARY (document_summary)
    // -------------------------------------------------------------
    suspend fun summarizeDocument(
        documentId: String,
        requestingUserId: String? = null
    ): Result<DocumentSummaryResult> {
        val docResult = verifyOwnershipAndGetDocument(documentId, requestingUserId)
        if (docResult.isFailure) return Result.failure(docResult.exceptionOrNull()!!)
        val doc = docResult.getOrThrow()

        if (doc.processingStatus != DocumentConstants.STATUS_READY || doc.extractedText.isBlank()) {
            return Result.failure(
                DocumentProcessingException("This document is not ready for AI analysis (status: ${doc.processingStatus}).")
            )
        }

        val user = getAuthenticatedUser()
        val reqType = DocumentRequestType.DOCUMENT_SUMMARY
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val allChunks = database.documentDao().getChunksForDocument(doc.id, user.id)
        val selectedChunks = allChunks.take(6)

        val prompt = DocumentProcessor.buildSummaryPrompt(doc.fileName, selectedChunks)

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "doc_summary_${doc.id}",
            module = AIModule.DOCUMENT_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 2048,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                val parsed = DocumentProcessor.parseSummaryResult(doc.id, doc.fileName, res.content)
                Result.success(parsed)
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
    // 9. ASK QUESTIONS ABOUT DOCUMENT (document_question)
    // -------------------------------------------------------------
    suspend fun askDocumentQuestion(
        documentId: String,
        question: String,
        requestingUserId: String? = null
    ): Result<DocumentAnswerResult> {
        val docResult = verifyOwnershipAndGetDocument(documentId, requestingUserId)
        if (docResult.isFailure) return Result.failure(docResult.exceptionOrNull()!!)
        val doc = docResult.getOrThrow()

        if (question.isBlank()) {
            return Result.failure(AIValidationException("Please enter a question about the document."))
        }

        if (doc.processingStatus != DocumentConstants.STATUS_READY || doc.extractedText.isBlank()) {
            return Result.failure(
                DocumentProcessingException("This document is not ready for Q&A (status: ${doc.processingStatus}).")
            )
        }

        val user = getAuthenticatedUser()
        val reqType = DocumentRequestType.DOCUMENT_QUESTION
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val allChunks = database.documentDao().getChunksForDocument(doc.id, user.id)
        val hasOverlap = DocumentProcessor.hasRelevantKeywordOverlap(allChunks, question)
        val retrievedChunks = DocumentProcessor.retrieveContextForQuestion(allChunks, question, maxChunks = 5)
        val citedSections = retrievedChunks.map { it.pageOrSection }.distinct()

        val prompt = DocumentProcessor.buildQuestionPrompt(
            fileName = doc.fileName,
            question = question.trim(),
            retrievedChunks = retrievedChunks
        )

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "doc_qa_${doc.id}",
            module = AIModule.DOCUMENT_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 1536,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                val rawAnswer = res.content.trim()
                val aiIndicatesNotFound = rawAnswer.contains("couldn't find that information", ignoreCase = true) ||
                    rawAnswer.contains("cannot find that information", ignoreCase = true) ||
                    rawAnswer.contains("not mentioned in this document", ignoreCase = true) ||
                    rawAnswer.contains("not present in the document", ignoreCase = true)

                val finalNotFound = !hasOverlap || aiIndicatesNotFound
                val finalAnswerText = if (finalNotFound) {
                    DocumentConstants.NOT_FOUND_RESPONSE
                } else {
                    rawAnswer
                }

                Result.success(
                    DocumentAnswerResult(
                        documentId = doc.id,
                        question = question.trim(),
                        answer = finalAnswerText,
                        isFoundInDocument = !finalNotFound,
                        citedSections = if (finalNotFound) emptyList() else citedSections,
                        rawOutput = finalAnswerText
                    )
                )
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
    // 10. EXTRACT KEY POINTS (document_key_points)
    // -------------------------------------------------------------
    suspend fun extractKeyPoints(
        documentId: String,
        requestingUserId: String? = null
    ): Result<DocumentKeyPointsResult> {
        val docResult = verifyOwnershipAndGetDocument(documentId, requestingUserId)
        if (docResult.isFailure) return Result.failure(docResult.exceptionOrNull()!!)
        val doc = docResult.getOrThrow()

        if (doc.processingStatus != DocumentConstants.STATUS_READY || doc.extractedText.isBlank()) {
            return Result.failure(
                DocumentProcessingException("This document is not ready for key points extraction.")
            )
        }

        val user = getAuthenticatedUser()
        val reqType = DocumentRequestType.DOCUMENT_KEY_POINTS
        val reqIdResult = verifyEntitlementAndLog(user, reqType)
        if (reqIdResult.isFailure) return Result.failure(reqIdResult.exceptionOrNull()!!)
        val requestId = reqIdResult.getOrThrow()

        val allChunks = database.documentDao().getChunksForDocument(doc.id, user.id)
        val prompt = DocumentProcessor.buildKeyPointsPrompt(doc.fileName, allChunks.take(6))

        val orchReq = OrchestrationRequest(
            userId = user.id,
            conversationId = "doc_keypoints_${doc.id}",
            module = AIModule.DOCUMENT_AI,
            prompt = prompt,
            requestType = reqType.id,
            maxTokens = 2048,
            isAdmin = user.isAdmin
        )

        return when (val res = gateway.dispatchToOrchestrator(orchReq)) {
            is AIOrchestrationResult.Success -> {
                recordUsageSuccess(user, requestId)
                val parsed = DocumentProcessor.parseKeyPointsResult(doc.id, res.content)
                Result.success(parsed)
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
    // 11. FIND INFORMATION / SEARCH DOCUMENT (document_search)
    // -------------------------------------------------------------
    suspend fun searchDocument(
        documentId: String,
        query: String,
        requestingUserId: String? = null
    ): Result<DocumentSearchResult> {
        val docResult = verifyOwnershipAndGetDocument(documentId, requestingUserId)
        if (docResult.isFailure) return Result.failure(docResult.exceptionOrNull()!!)
        val doc = docResult.getOrThrow()

        if (query.isBlank()) {
            return Result.failure(AIValidationException("Please enter a search query."))
        }

        if (doc.processingStatus != DocumentConstants.STATUS_READY) {
            return Result.failure(DocumentProcessingException("Document is not ready for search."))
        }

        val user = getAuthenticatedUser()
        val chunks = database.documentDao().getChunksForDocument(doc.id, user.id)
        val matches = DocumentProcessor.searchChunks(chunks, query.trim())

        // Record search activity in AIRequests
        database.aiRequestDao().insertRequest(
            AIRequestEntity(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                conversationId = "doc_search_${doc.id}",
                module = "documents",
                requestType = DocumentRequestType.DOCUMENT_SEARCH.id,
                status = "completed",
                createdAt = System.currentTimeMillis()
            )
        )

        return Result.success(
            DocumentSearchResult(
                documentId = doc.id,
                query = query.trim(),
                matches = matches
            )
        )
    }

    // -------------------------------------------------------------
    // 12. DOCUMENT NOTES (Add, Edit, Delete, Observe with Ownership)
    // -------------------------------------------------------------
    fun getDocumentNotesFlow(documentId: String, userId: String): Flow<List<DocumentNoteEntity>> {
        val authUser = currentUserProvider()
        if (authUser == null || authUser.id != userId) {
            return flowOf(emptyList())
        }
        return database.documentDao().getNotesForDocumentFlow(documentId, userId)
    }

    suspend fun getDocumentNotesSync(documentId: String, requestingUserId: String? = null): Result<List<DocumentNoteEntity>> {
        val docResult = verifyOwnershipAndGetDocument(documentId, requestingUserId)
        if (docResult.isFailure) return Result.failure(docResult.exceptionOrNull()!!)
        val doc = docResult.getOrThrow()
        return Result.success(database.documentDao().getNotesForDocumentSync(doc.id, doc.userId))
    }

    suspend fun addDocumentNote(
        documentId: String,
        noteText: String,
        requestingUserId: String? = null
    ): Result<DocumentNoteEntity> {
        val docResult = verifyOwnershipAndGetDocument(documentId, requestingUserId)
        if (docResult.isFailure) return Result.failure(docResult.exceptionOrNull()!!)
        val doc = docResult.getOrThrow()

        if (noteText.isBlank()) {
            return Result.failure(AIValidationException("Note content cannot be empty."))
        }

        val now = System.currentTimeMillis()
        val note = DocumentNoteEntity(
            id = UUID.randomUUID().toString(),
            userId = doc.userId,
            documentId = doc.id,
            note = noteText.trim(),
            createdAt = now,
            updatedAt = now
        )
        database.documentDao().insertNote(note)
        return Result.success(note)
    }

    suspend fun editDocumentNote(
        noteId: String,
        updatedNoteText: String,
        requestingUserId: String? = null
    ): Result<DocumentNoteEntity> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }
        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(DocumentPermissionException("Access denied: Cannot edit another user's note."))
        }

        if (updatedNoteText.isBlank()) {
            return Result.failure(AIValidationException("Note content cannot be empty."))
        }

        val existing = database.documentDao().getNoteByIdRaw(noteId)
            ?: return Result.failure(AIValidationException("Note not found."))

        if (existing.userId != authUser.id) {
            return Result.failure(DocumentPermissionException("Access denied: Cannot edit another user's note."))
        }

        val updated = existing.copy(
            note = updatedNoteText.trim(),
            updatedAt = System.currentTimeMillis()
        )
        database.documentDao().updateNote(updated)
        return Result.success(updated)
    }

    suspend fun deleteDocumentNote(
        noteId: String,
        requestingUserId: String? = null
    ): Result<Unit> {
        val authUser = try {
            getAuthenticatedUser()
        } catch (e: AIServiceException) {
            return Result.failure(e)
        }
        val effectiveUserId = requestingUserId ?: authUser.id
        if (effectiveUserId != authUser.id) {
            return Result.failure(DocumentPermissionException("Access denied: Cannot delete another user's note."))
        }

        val existing = database.documentDao().getNoteByIdRaw(noteId)
            ?: return Result.failure(AIValidationException("Note not found."))

        if (existing.userId != authUser.id) {
            return Result.failure(DocumentPermissionException("Access denied: Cannot delete another user's note."))
        }

        database.documentDao().deleteNote(noteId, authUser.id)
        return Result.success(Unit)
    }

    // -------------------------------------------------------------
    // 19. DELETE DOCUMENT (Removes document, file, chunks, and notes)
    // -------------------------------------------------------------
    suspend fun deleteDocument(
        documentId: String,
        requestingUserId: String? = null
    ): Result<Unit> {
        val docResult = verifyOwnershipAndGetDocument(documentId, requestingUserId)
        if (docResult.isFailure) return Result.failure(docResult.exceptionOrNull()!!)
        val doc = docResult.getOrThrow()

        // 1. Remove stored file from app-private disk if present
        context?.let { ctx ->
            try {
                val userDir = File(ctx.filesDir, "documents/${doc.userId}")
                userDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("${doc.id}_")) {
                        file.delete()
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Remove all retrieval chunks and notes for this document
        database.documentDao().deleteChunksForDocument(doc.id, doc.userId)
        database.documentDao().deleteNotesForDocument(doc.id, doc.userId)

        // 3. Remove document record
        database.documentDao().deleteDocument(doc.id, doc.userId)

        return Result.success(Unit)
    }
}
