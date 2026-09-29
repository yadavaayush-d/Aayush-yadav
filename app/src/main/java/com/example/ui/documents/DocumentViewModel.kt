package com.example.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentNoteEntity
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AIServiceException
import com.example.domain.documents.DocumentAnswerResult
import com.example.domain.documents.DocumentKeyPointsResult
import com.example.domain.documents.DocumentSearchResult
import com.example.domain.documents.DocumentSummaryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DocumentTab(val label: String) {
    HOME("Document Home"),
    UPLOAD("Upload Document"),
    MY_DOCUMENTS("My Documents"),
    SUMMARIZE("Summarize"),
    ASK_QUESTIONS("Ask Questions"),
    KEY_POINTS("Extract Key Points"),
    FIND_INFO("Find Information"),
    NOTES("Document Notes")
}

sealed class DocumentUiState<out T> {
    data object Idle : DocumentUiState<Nothing>()
    data object Loading : DocumentUiState<Nothing>()
    data class Success<out T>(val data: T) : DocumentUiState<T>()
    data class Error(val errorCode: String, val message: String) : DocumentUiState<Nothing>()
}

data class UploadProgressState(
    val fileName: String = "",
    val fileType: String = "",
    val fileSize: Long = 0L,
    val status: String = "", // "", "Uploading", "Processing", "Ready", "Failed"
    val errorMessage: String? = null,
    val uploadedDocument: DocumentEntity? = null
)

class DocumentViewModel(
    private val repository: NexaRepository
) : ViewModel() {

    val docRepo = repository.documentRepository

    private val _activeTab = MutableStateFlow(DocumentTab.HOME)
    val activeTab: StateFlow<DocumentTab> = _activeTab.asStateFlow()

    private val _selectedDocument = MutableStateFlow<DocumentEntity?>(null)
    val selectedDocument: StateFlow<DocumentEntity?> = _selectedDocument.asStateFlow()

    private val _searchFilterQuery = MutableStateFlow("")
    val searchFilterQuery: StateFlow<String> = _searchFilterQuery.asStateFlow()

    private val _sortRecentDescending = MutableStateFlow(true)
    val sortRecentDescending: StateFlow<Boolean> = _sortRecentDescending.asStateFlow()

    private val _uploadState = MutableStateFlow(UploadProgressState())
    val uploadState: StateFlow<UploadProgressState> = _uploadState.asStateFlow()

    private val _summaryState = MutableStateFlow<DocumentUiState<DocumentSummaryResult>>(DocumentUiState.Idle)
    val summaryState: StateFlow<DocumentUiState<DocumentSummaryResult>> = _summaryState.asStateFlow()

    private val _questionState = MutableStateFlow<DocumentUiState<DocumentAnswerResult>>(DocumentUiState.Idle)
    val questionState: StateFlow<DocumentUiState<DocumentAnswerResult>> = _questionState.asStateFlow()

    private val _keyPointsState = MutableStateFlow<DocumentUiState<DocumentKeyPointsResult>>(DocumentUiState.Idle)
    val keyPointsState: StateFlow<DocumentUiState<DocumentKeyPointsResult>> = _keyPointsState.asStateFlow()

    private val _searchState = MutableStateFlow<DocumentUiState<DocumentSearchResult>>(DocumentUiState.Idle)
    val searchState: StateFlow<DocumentUiState<DocumentSearchResult>> = _searchState.asStateFlow()

    private val _feedbackBanner = MutableStateFlow<String?>(null)
    val feedbackBanner: StateFlow<String?> = _feedbackBanner.asStateFlow()

    private var lastRetryAction: (() -> Unit)? = null

    private val rawDocumentsFlow = repository.currentUser.flatMapLatest { user ->
        if (user != null) docRepo.getMyDocuments(user.id) else flowOf(emptyList())
    }

    val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
        rawDocumentsFlow,
        _searchFilterQuery,
        _sortRecentDescending
    ) { docs, query, desc ->
        val filtered = if (query.isBlank()) {
            docs
        } else {
            val q = query.trim().lowercase()
            docs.filter {
                it.fileName.lowercase().contains(q) || it.fileType.lowercase().contains(q)
            }
        }
        if (desc) {
            filtered.sortedByDescending { it.createdAt }
        } else {
            filtered.sortedBy { it.createdAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedDocumentNotes: StateFlow<List<DocumentNoteEntity>> = combine(
        repository.currentUser,
        _selectedDocument
    ) { user, doc ->
        user to doc
    }.flatMapLatest { (user, doc) ->
        if (user != null && doc != null) {
            docRepo.getDocumentNotesFlow(doc.id, user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: DocumentTab) {
        _activeTab.value = tab
    }

    fun updateSearchFilter(query: String) {
        _searchFilterQuery.value = query
    }

    fun toggleSortOrder() {
        _sortRecentDescending.value = !_sortRecentDescending.value
    }

    fun clearFeedbackBanner() {
        _feedbackBanner.value = null
    }

    fun retryLastAction() {
        lastRetryAction?.invoke()
    }

    private fun mapError(error: Throwable): DocumentUiState.Error {
        return when (error) {
            is AIServiceException -> DocumentUiState.Error(error.errorCode, error.userFriendlyMessage)
            is SecurityException -> DocumentUiState.Error("PERMISSION_ERROR", "Access denied.")
            else -> DocumentUiState.Error("AI_PROVIDER_ERROR", "AI service is temporarily unavailable. Please try again.")
        }
    }

    // -------------------------------------------------------------
    // Upload & Processing
    // -------------------------------------------------------------
    fun uploadDocumentBytes(
        fileName: String,
        rawBytes: ByteArray,
        explicitType: String? = null
    ) {
        val ext = explicitType ?: fileName.substringAfterLast('.', "").uppercase()
        _uploadState.value = UploadProgressState(
            fileName = fileName,
            fileType = ext,
            fileSize = rawBytes.size.toLong(),
            status = "Uploading"
        )

        viewModelScope.launch {
            val res = docRepo.uploadDocument(
                fileName = fileName,
                rawBytes = rawBytes,
                explicitFileType = explicitType,
                onStatusProgress = { stage ->
                    _uploadState.value = _uploadState.value.copy(status = stage)
                }
            )
            res.fold(
                onSuccess = { doc ->
                    _uploadState.value = UploadProgressState(
                        fileName = doc.fileName,
                        fileType = doc.fileType,
                        fileSize = doc.fileSize,
                        status = doc.processingStatus,
                        uploadedDocument = doc
                    )
                    _selectedDocument.value = doc
                    _feedbackBanner.value = "'${doc.fileName}' processed and ready!"
                },
                onFailure = { err ->
                    val friendly = (err as? AIServiceException)?.userFriendlyMessage
                        ?: (err.message ?: "Upload failed.")
                    _uploadState.value = _uploadState.value.copy(
                        status = "Failed",
                        errorMessage = friendly
                    )
                }
            )
        }
    }

    fun uploadTextBackedDocument(
        fileName: String,
        textContent: String,
        explicitType: String
    ) {
        _uploadState.value = UploadProgressState(
            fileName = fileName,
            fileType = explicitType,
            fileSize = textContent.toByteArray().size.toLong(),
            status = "Uploading"
        )

        viewModelScope.launch {
            val res = docRepo.uploadTextBackedDocument(
                fileName = fileName,
                textContent = textContent,
                explicitFileType = explicitType,
                onStatusProgress = { stage ->
                    _uploadState.value = _uploadState.value.copy(status = stage)
                }
            )
            res.fold(
                onSuccess = { doc ->
                    _uploadState.value = UploadProgressState(
                        fileName = doc.fileName,
                        fileType = doc.fileType,
                        fileSize = doc.fileSize,
                        status = doc.processingStatus,
                        uploadedDocument = doc
                    )
                    _selectedDocument.value = doc
                    _feedbackBanner.value = "'${doc.fileName}' uploaded and ready!"
                },
                onFailure = { err ->
                    val friendly = (err as? AIServiceException)?.userFriendlyMessage
                        ?: (err.message ?: "Document upload failed.")
                    _uploadState.value = _uploadState.value.copy(
                        status = "Failed",
                        errorMessage = friendly
                    )
                }
            )
        }
    }

    // -------------------------------------------------------------
    // Open & Select Document
    // -------------------------------------------------------------
    fun openDocument(documentId: String, targetTab: DocumentTab? = null) {
        viewModelScope.launch {
            val res = docRepo.openDocument(documentId)
            res.fold(
                onSuccess = { doc ->
                    _selectedDocument.value = doc
                    // Reset previous document's transient AI outputs when switching documents
                    _summaryState.value = DocumentUiState.Idle
                    _questionState.value = DocumentUiState.Idle
                    _keyPointsState.value = DocumentUiState.Idle
                    _searchState.value = DocumentUiState.Idle
                    if (targetTab != null) {
                        _activeTab.value = targetTab
                    }
                },
                onFailure = { err ->
                    _feedbackBanner.value = (err as? AIServiceException)?.userFriendlyMessage
                        ?: (err.message ?: "Access denied")
                }
            )
        }
    }

    // -------------------------------------------------------------
    // Summarize Document
    // -------------------------------------------------------------
    fun summarizeSelectedDocument() {
        val doc = _selectedDocument.value
        if (doc == null) {
            _summaryState.value = DocumentUiState.Error("VALIDATION_ERROR", "Please select or upload a document first.")
            return
        }
        lastRetryAction = { summarizeSelectedDocument() }
        viewModelScope.launch {
            _summaryState.value = DocumentUiState.Loading
            val res = docRepo.summarizeDocument(doc.id)
            _summaryState.value = res.fold(
                onSuccess = { DocumentUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // Ask Question About Document
    // -------------------------------------------------------------
    fun askQuestion(question: String) {
        val doc = _selectedDocument.value
        if (doc == null) {
            _questionState.value = DocumentUiState.Error("VALIDATION_ERROR", "Please select or upload a document first.")
            return
        }
        lastRetryAction = { askQuestion(question) }
        viewModelScope.launch {
            _questionState.value = DocumentUiState.Loading
            val res = docRepo.askDocumentQuestion(doc.id, question)
            _questionState.value = res.fold(
                onSuccess = { DocumentUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // Extract Key Points
    // -------------------------------------------------------------
    fun extractKeyPoints() {
        val doc = _selectedDocument.value
        if (doc == null) {
            _keyPointsState.value = DocumentUiState.Error("VALIDATION_ERROR", "Please select or upload a document first.")
            return
        }
        lastRetryAction = { extractKeyPoints() }
        viewModelScope.launch {
            _keyPointsState.value = DocumentUiState.Loading
            val res = docRepo.extractKeyPoints(doc.id)
            _keyPointsState.value = res.fold(
                onSuccess = { DocumentUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // Search Document (Find Information)
    // -------------------------------------------------------------
    fun searchSelectedDocument(query: String) {
        val doc = _selectedDocument.value
        if (doc == null) {
            _searchState.value = DocumentUiState.Error("VALIDATION_ERROR", "Please select or upload a document first.")
            return
        }
        lastRetryAction = { searchSelectedDocument(query) }
        viewModelScope.launch {
            _searchState.value = DocumentUiState.Loading
            val res = docRepo.searchDocument(doc.id, query)
            _searchState.value = res.fold(
                onSuccess = { DocumentUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // Document Notes (Add, Edit, Delete)
    // -------------------------------------------------------------
    fun addNote(noteText: String) {
        val doc = _selectedDocument.value ?: return
        viewModelScope.launch {
            val res = docRepo.addDocumentNote(doc.id, noteText)
            res.fold(
                onSuccess = { _feedbackBanner.value = "Note saved!" },
                onFailure = { _feedbackBanner.value = it.message ?: "Failed to save note." }
            )
        }
    }

    fun editNote(noteId: String, updatedText: String) {
        viewModelScope.launch {
            val res = docRepo.editDocumentNote(noteId, updatedText)
            res.fold(
                onSuccess = { _feedbackBanner.value = "Note updated!" },
                onFailure = { _feedbackBanner.value = it.message ?: "Failed to update note." }
            )
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            val res = docRepo.deleteDocumentNote(noteId)
            res.fold(
                onSuccess = { _feedbackBanner.value = "Note deleted." },
                onFailure = { _feedbackBanner.value = it.message ?: "Failed to delete note." }
            )
        }
    }

    // -------------------------------------------------------------
    // Delete Document
    // -------------------------------------------------------------
    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            val res = docRepo.deleteDocument(documentId)
            res.fold(
                onSuccess = {
                    if (_selectedDocument.value?.id == documentId) {
                        _selectedDocument.value = null
                        _summaryState.value = DocumentUiState.Idle
                        _questionState.value = DocumentUiState.Idle
                        _keyPointsState.value = DocumentUiState.Idle
                        _searchState.value = DocumentUiState.Idle
                    }
                    _feedbackBanner.value = "Document deleted."
                },
                onFailure = { _feedbackBanner.value = it.message ?: "Could not delete document." }
            )
        }
    }
}
