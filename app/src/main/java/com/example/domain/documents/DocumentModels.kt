package com.example.domain.documents

import com.example.domain.ai.AIServiceException

enum class DocumentRequestType(val id: String, val displayLabel: String) {
    DOCUMENT_SUMMARY("document_summary", "Document Summary"),
    DOCUMENT_QUESTION("document_question", "Document Q&A"),
    DOCUMENT_KEY_POINTS("document_key_points", "Extract Key Points"),
    DOCUMENT_SEARCH("document_search", "Document Search"),
    DOCUMENT_EXTRACT("document_extract", "Document Text Extraction"),
    DOCUMENT_NOTES("document_notes", "Document Notes");

    companion object {
        fun fromId(id: String): DocumentRequestType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DOCUMENT_QUESTION
        }
    }
}

object DocumentConstants {
    val SUPPORTED_FORMATS = setOf("PDF", "TXT", "DOCX")
    const val MAX_FILE_SIZE_BYTES = 15L * 1024L * 1024L // 15 MB

    const val STATUS_UPLOADING = "Uploading"
    const val STATUS_PROCESSING = "Processing"
    const val STATUS_READY = "Ready"
    const val STATUS_FAILED = "Failed"

    const val EXTRACTED_PENDING = "pending"
    const val EXTRACTED_READY = "extracted"
    const val EXTRACTED_FAILED = "failed"

    const val NOT_FOUND_RESPONSE = "I couldn't find that information in this document."
}

// -------------------------------------------------------------
// Structured Document AI Exceptions (Section 18)
// -------------------------------------------------------------
class UnsupportedDocumentTypeException(
    message: String = "Unsupported file format. Supported formats are PDF, TXT, and DOCX."
) : AIServiceException("UNSUPPORTED_FILE_TYPE", message, message)

class DocumentTooLargeException(
    message: String = "File exceeds the maximum allowed size of 15 MB."
) : AIServiceException("FILE_TOO_LARGE", message, message)

class DocumentUploadException(
    message: String = "Document upload failed. Please check the file and try again."
) : AIServiceException("UPLOAD_FAILURE", message, message)

class DocumentProcessingException(
    message: String = "Document processing failed. The file may be corrupted."
) : AIServiceException("PROCESSING_FAILURE", message, message)

class DocumentTextExtractionException(
    message: String = "Could not extract readable text from this document."
) : AIServiceException("TEXT_EXTRACTION_FAILURE", message, message)

class DocumentPermissionException(
    message: String = "Access denied: You do not have permission to access this document."
) : AIServiceException("PERMISSION_ERROR", message, message)

// -------------------------------------------------------------
// Output Domain Models
// -------------------------------------------------------------
data class ExtractedPageSection(
    val pageOrSection: String,
    val text: String
)

data class ExtractionOutcome(
    val fullText: String,
    val pageCount: Int,
    val sections: List<ExtractedPageSection>
)

data class DocumentSummaryResult(
    val documentId: String,
    val fileName: String,
    val shortSummary: String,
    val mainPoints: List<String>,
    val importantSections: List<String>,
    val keyTerms: List<String>,
    val actionItems: List<String>,
    val rawOutput: String
)

data class DocumentAnswerResult(
    val documentId: String,
    val question: String,
    val answer: String,
    val isFoundInDocument: Boolean,
    val citedSections: List<String>,
    val rawOutput: String
)

data class DocumentKeyPointsResult(
    val documentId: String,
    val importantPoints: List<String>,
    val namesAndTerms: List<String>,
    val datesPresent: List<String>,
    val numbersAndData: List<String>,
    val conclusions: List<String>,
    val rawOutput: String
)

data class DocumentSearchMatch(
    val chunkIndex: Int,
    val pageOrSection: String,
    val snippet: String,
    val relevanceScore: Int
)

data class DocumentSearchResult(
    val documentId: String,
    val query: String,
    val matches: List<DocumentSearchMatch>
)
