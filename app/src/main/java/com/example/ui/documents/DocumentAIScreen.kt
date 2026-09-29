package com.example.ui.documents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DocumentAIScreen(
    viewModel: DocumentViewModel,
    onBack: () -> Unit
) {
    val activeTab by viewModel.activeTab.collectAsState()
    val selectedDocument by viewModel.selectedDocument.collectAsState()
    val filteredDocuments by viewModel.filteredDocuments.collectAsState()
    val searchFilterQuery by viewModel.searchFilterQuery.collectAsState()
    val sortRecentDesc by viewModel.sortRecentDescending.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()

    val summaryState by viewModel.summaryState.collectAsState()
    val questionState by viewModel.questionState.collectAsState()
    val keyPointsState by viewModel.keyPointsState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val notes by viewModel.selectedDocumentNotes.collectAsState()
    val feedbackBanner by viewModel.feedbackBanner.collectAsState()

    BackHandler {
        if (activeTab != DocumentTab.HOME) {
            viewModel.selectTab(DocumentTab.HOME)
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            DocumentHeader(
                onBack = {
                    if (activeTab != DocumentTab.HOME) {
                        viewModel.selectTab(DocumentTab.HOME)
                    } else {
                        onBack()
                    }
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("document_ai_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            DocumentTabBar(
                activeTab = activeTab,
                onTabSelected = { viewModel.selectTab(it) }
            )

            SelectedDocumentViewerCard(
                document = selectedDocument,
                onActionSelected = { viewModel.selectTab(it) },
                onDeleteDocument = { viewModel.deleteDocument(it) },
                onGoToUpload = { viewModel.selectTab(DocumentTab.UPLOAD) }
            )

            if (feedbackBanner != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("document_feedback_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = feedbackBanner!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        TextButton(onClick = { viewModel.clearFeedbackBanner() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            when (activeTab) {
                DocumentTab.HOME -> {
                    DocumentHomeView(
                        documentsCount = filteredDocuments.size,
                        onSelectTab = { viewModel.selectTab(it) }
                    )
                }
                DocumentTab.UPLOAD -> {
                    DocumentUploadView(
                        uploadState = uploadState,
                        onUploadBytes = { name, bytes, type -> viewModel.uploadDocumentBytes(name, bytes, type) },
                        onUploadTextDocument = { name, text, fmt -> viewModel.uploadTextBackedDocument(name, text, fmt) },
                        onOpenUploadedDocument = { doc ->
                            viewModel.openDocument(doc.id, DocumentTab.SUMMARIZE)
                        }
                    )
                }
                DocumentTab.MY_DOCUMENTS -> {
                    MyDocumentsListView(
                        documents = filteredDocuments,
                        searchQuery = searchFilterQuery,
                        onSearchQueryChange = { viewModel.updateSearchFilter(it) },
                        sortRecentDesc = sortRecentDesc,
                        onToggleSort = { viewModel.toggleSortOrder() },
                        onOpenDocument = { docId -> viewModel.openDocument(docId, DocumentTab.SUMMARIZE) },
                        onDeleteDocument = { docId -> viewModel.deleteDocument(docId) },
                        onNavigateToUpload = { viewModel.selectTab(DocumentTab.UPLOAD) }
                    )
                }
                DocumentTab.SUMMARIZE -> {
                    DocumentSummaryView(
                        selectedDocument = selectedDocument,
                        state = summaryState,
                        onSummarize = { viewModel.summarizeSelectedDocument() },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                DocumentTab.ASK_QUESTIONS -> {
                    DocumentQuestionView(
                        selectedDocument = selectedDocument,
                        state = questionState,
                        onAskQuestion = { viewModel.askQuestion(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                DocumentTab.KEY_POINTS -> {
                    DocumentKeyPointsView(
                        selectedDocument = selectedDocument,
                        state = keyPointsState,
                        onExtract = { viewModel.extractKeyPoints() },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                DocumentTab.FIND_INFO -> {
                    DocumentFindInfoView(
                        selectedDocument = selectedDocument,
                        state = searchState,
                        onSearch = { viewModel.searchSelectedDocument(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                DocumentTab.NOTES -> {
                    DocumentNotesView(
                        selectedDocument = selectedDocument,
                        notes = notes,
                        onAddNote = { viewModel.addNote(it) },
                        onEditNote = { id, txt -> viewModel.editNote(id, txt) },
                        onDeleteNote = { viewModel.deleteNote(it) }
                    )
                }
            }
        }
    }
}
