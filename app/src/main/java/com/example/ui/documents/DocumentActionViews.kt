package com.example.ui.documents

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentNoteEntity
import com.example.domain.documents.DocumentAnswerResult
import com.example.domain.documents.DocumentKeyPointsResult
import com.example.domain.documents.DocumentSearchResult
import com.example.domain.documents.DocumentSummaryResult
import com.example.ui.creator.CreatorDropdownField
import com.example.ui.creator.CreatorSectionBox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// -------------------------------------------------------------
// 2. DOCUMENT UPLOAD VIEW
// -------------------------------------------------------------
@Composable
fun DocumentUploadView(
    uploadState: UploadProgressState,
    onUploadBytes: (fileName: String, bytes: ByteArray, explicitType: String?) -> Unit,
    onUploadTextDocument: (fileName: String, content: String, format: String) -> Unit,
    onOpenUploadedDocument: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    var customFileName by remember { mutableStateOf("study_guide.pdf") }
    var customFormat by remember { mutableStateOf("PDF") }
    var customContent by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var resolvedName = "uploaded_document.pdf"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        resolvedName = cursor.getString(nameIndex) ?: resolvedName
                    }
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                onUploadBytes(resolvedName, bytes, null)
            } catch (_: Exception) {
                onUploadBytes("invalid_file.pdf", ByteArray(0), "PDF")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("document_upload_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Upload Supported Document",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Supported formats: PDF, TXT, DOCX (Max size: 15 MB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "text/plain",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_device_document_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select PDF, TXT, or DOCX from Device")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Or Import / Paste Document Content Directly:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customFileName,
                        onValueChange = { customFileName = it },
                        label = { Text("File Name") },
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("upload_filename_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    CreatorDropdownField(
                        label = "Format",
                        selectedValue = customFormat,
                        options = listOf("PDF", "TXT", "DOCX"),
                        onValueSelected = { fmt ->
                            customFormat = fmt
                            val base = customFileName.substringBeforeLast('.', customFileName)
                            customFileName = "$base.${fmt.lowercase()}"
                        },
                        testTag = "upload_format_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customContent,
                    onValueChange = { customContent = it },
                    label = { Text("Document Text Content") },
                    placeholder = { Text("Paste or enter document text to package and process...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("upload_content_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = {
                        onUploadTextDocument(customFileName, customContent, customFormat)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_direct_document_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload & Process Document")
                }
            }
        }

        if (uploadState.status.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_status_card"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uploadState.fileName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Type: ${uploadState.fileType} • Size: ${formatBytes(uploadState.fileSize)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DocumentStatusBadge(status = uploadState.status)
                    }

                    if (uploadState.status == "Uploading" || uploadState.status == "Processing") {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (uploadState.status == "Failed" && !uploadState.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uploadState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("upload_error_text")
                        )
                    }

                    if (uploadState.status == "Ready" && uploadState.uploadedDocument != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onOpenUploadedDocument(uploadState.uploadedDocument) },
                            modifier = Modifier.testTag("open_uploaded_doc_button")
                        ) {
                            Text("Summarize / Ask Questions")
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. MY DOCUMENTS LIST VIEW
// -------------------------------------------------------------
@Composable
fun MyDocumentsListView(
    documents: List<DocumentEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortRecentDesc: Boolean,
    onToggleSort: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onNavigateToUpload: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("my_documents_list_view")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Documents",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedButton(
                onClick = onToggleSort,
                modifier = Modifier.testTag("sort_documents_button")
            ) {
                Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (sortRecentDesc) "Recent First" else "Oldest First")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search documents") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("my_documents_search_input"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (documents.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("my_documents_empty_state"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No documents yet. Upload your first document to get started.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.testTag("empty_documents_message")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToUpload,
                        modifier = Modifier.testTag("empty_state_upload_btn")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Document")
                    }
                }
            }
        } else {
            documents.forEach { doc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("doc_item_card_${doc.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.fileName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${doc.fileType} • ${formatBytes(doc.fileSize)} • ${dateFormat.format(Date(doc.createdAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DocumentStatusBadge(status = doc.processingStatus)
                        }

                        if (doc.processingStatus == "Failed" && !doc.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = doc.errorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onOpenDocument(doc.id) },
                                modifier = Modifier.testTag("open_doc_btn_${doc.id}")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open")
                            }
                            OutlinedButton(
                                onClick = { onDeleteDocument(doc.id) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("delete_doc_btn_${doc.id}")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 8. DOCUMENT SUMMARY VIEW
// -------------------------------------------------------------
@Composable
fun DocumentSummaryView(
    selectedDocument: DocumentEntity?,
    state: DocumentUiState<DocumentSummaryResult>,
    onSummarize: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("document_summary_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Document Summary",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (selectedDocument != null) "Selected: ${selectedDocument.fileName}"
                    else "Select a Ready document above to generate an AI summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSummarize,
                    enabled = selectedDocument != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_doc_summary_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Summarize Document")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is DocumentUiState.Idle -> {}
            is DocumentUiState.Loading -> DocumentLoadingView("Summarizing '${selectedDocument?.fileName ?: "document"}'...")
            is DocumentUiState.Error -> DocumentErrorCard(state.errorCode, state.message, onRetry)
            is DocumentUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doc_summary_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        CreatorSectionBox("Short Summary", res.shortSummary)
                        if (res.mainPoints.isNotEmpty()) {
                            CreatorSectionBox("Main Points", res.mainPoints.joinToString("\n") { "• $it" })
                        }
                        if (res.importantSections.isNotEmpty()) {
                            CreatorSectionBox("Important Sections", res.importantSections.joinToString("\n") { "• $it" })
                        }
                        if (res.keyTerms.isNotEmpty()) {
                            CreatorSectionBox("Key Terms", res.keyTerms.joinToString("\n") { "• $it" })
                        }
                        if (res.actionItems.isNotEmpty()) {
                            CreatorSectionBox("Action Items", res.actionItems.joinToString("\n") { "• $it" })
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 9. ASK QUESTIONS ABOUT DOCUMENT VIEW
// -------------------------------------------------------------
@Composable
fun DocumentQuestionView(
    selectedDocument: DocumentEntity?,
    state: DocumentUiState<DocumentAnswerResult>,
    onAskQuestion: (String) -> Unit,
    onRetry: () -> Unit
) {
    var question by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("document_question_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ask this document",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Answers are strictly grounded in your document's extracted text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Your Question") },
                    placeholder = { Text("What are the main points of this document?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doc_question_input"),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onAskQuestion(question) },
                    enabled = selectedDocument != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ask_document_submit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.QuestionAnswer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ask Document")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is DocumentUiState.Idle -> {}
            is DocumentUiState.Loading -> DocumentLoadingView("Retrieving relevant sections & answering...")
            is DocumentUiState.Error -> DocumentErrorCard(state.errorCode, state.message, onRetry)
            is DocumentUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doc_answer_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Q: ${res.question}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = res.answer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("doc_answer_text")
                        )
                        if (res.citedSections.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Referenced Sections: ${res.citedSections.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 10. EXTRACT KEY POINTS VIEW
// -------------------------------------------------------------
@Composable
fun DocumentKeyPointsView(
    selectedDocument: DocumentEntity?,
    state: DocumentUiState<DocumentKeyPointsResult>,
    onExtract: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("document_keypoints_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Extract Key Points",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Extracts important points, names/terms, dates, numbers/data, and conclusions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onExtract,
                    enabled = selectedDocument != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("extract_keypoints_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Extract Key Points")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is DocumentUiState.Idle -> {}
            is DocumentUiState.Loading -> DocumentLoadingView("Extracting key points from document...")
            is DocumentUiState.Error -> DocumentErrorCard(state.errorCode, state.message, onRetry)
            is DocumentUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doc_keypoints_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (res.importantPoints.isNotEmpty()) {
                            CreatorSectionBox("Important Points", res.importantPoints.joinToString("\n") { "• $it" })
                        }
                        if (res.namesAndTerms.isNotEmpty()) {
                            CreatorSectionBox("Important Names / Terms", res.namesAndTerms.joinToString("\n") { "• $it" })
                        }
                        if (res.datesPresent.isNotEmpty()) {
                            CreatorSectionBox("Dates Present", res.datesPresent.joinToString("\n") { "• $it" })
                        }
                        if (res.numbersAndData.isNotEmpty()) {
                            CreatorSectionBox("Numbers / Data", res.numbersAndData.joinToString("\n") { "• $it" })
                        }
                        if (res.conclusions.isNotEmpty()) {
                            CreatorSectionBox("Conclusions", res.conclusions.joinToString("\n") { "• $it" })
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 11. FIND INFORMATION / SEARCH DOCUMENT VIEW
// -------------------------------------------------------------
@Composable
fun DocumentFindInfoView(
    selectedDocument: DocumentEntity?,
    state: DocumentUiState<DocumentSearchResult>,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("document_find_info_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Find Information in Document",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Query") },
                    placeholder = { Text("Enter keyword or phrase...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doc_find_query_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onSearch(searchQuery) },
                    enabled = selectedDocument != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doc_find_submit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search Document")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is DocumentUiState.Idle -> {}
            is DocumentUiState.Loading -> DocumentLoadingView("Searching document passages...")
            is DocumentUiState.Error -> DocumentErrorCard(state.errorCode, state.message, onRetry)
            is DocumentUiState.Success -> {
                val res = state.data
                if (res.matches.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doc_search_no_matches"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "No matching passages found for '${res.query}' in this document.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    res.matches.forEachIndexed { idx, match ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("doc_search_match_$idx"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = match.pageOrSection,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = match.snippet,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 12. DOCUMENT NOTES VIEW
// -------------------------------------------------------------
@Composable
fun DocumentNotesView(
    selectedDocument: DocumentEntity?,
    notes: List<DocumentNoteEntity>,
    onAddNote: (String) -> Unit,
    onEditNote: (String, String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var newNoteText by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<DocumentNoteEntity?>(null) }
    var editingText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("document_notes_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Document Notes",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newNoteText,
                    onValueChange = { newNoteText = it },
                    label = { Text("Add a note for this document") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doc_note_input"),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        onAddNote(newNoteText)
                        newNoteText = ""
                    },
                    enabled = selectedDocument != null && newNoteText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_doc_note_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Note")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        notes.forEach { note ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("doc_note_card_${note.id}"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.note,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        IconButton(
                            onClick = {
                                editingNote = note
                                editingText = note.note
                            },
                            modifier = Modifier.testTag("edit_doc_note_btn_${note.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Note", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onDeleteNote(note.id) },
                            modifier = Modifier.testTag("delete_doc_note_btn_${note.id}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Note",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingNote != null) {
        val target = editingNote!!
        AlertDialog(
            onDismissRequest = { editingNote = null },
            title = { Text("Edit Document Note") },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    label = { Text("Note") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_doc_note_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditNote(target.id, editingText)
                        editingNote = null
                    },
                    modifier = Modifier.testTag("confirm_edit_doc_note_btn")
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingNote = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
