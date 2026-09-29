package com.example.domain.language

import com.example.domain.ai.AIServiceException

/**
 * Step 6 — Language AI Request Types (Section 13)
 */
enum class LanguageRequestType(val id: String, val displayLabel: String) {
    TRANSLATE("translate", "Translate"),
    REWRITE("rewrite", "Rewrite"),
    GRAMMAR_CHECK("grammar_check", "Grammar Check"),
    SUMMARIZE("summarize", "Summarize"),
    DETECT_LANGUAGE("detect_language", "Detect Language"),
    EXPLAIN_TEXT("explain_text", "Explain Text"),
    LANGUAGE_PRACTICE("language_practice", "Language Practice"),
    CONVERSATION_PRACTICE("conversation_practice", "Conversation Practice");

    companion object {
        fun fromId(id: String): LanguageRequestType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TRANSLATE
        }
    }
}

object LanguageOptions {
    const val AUTO_DETECT = "Auto Detect"

    val SUPPORTED_LANGUAGES = listOf(
        "English",
        "Hindi",
        "Hinglish",
        "Bengali",
        "Marathi",
        "Gujarati",
        "Tamil",
        "Telugu",
        "Kannada",
        "Malayalam",
        "Punjabi",
        "Urdu",
        "Spanish",
        "French",
        "German",
        "Japanese",
        "Korean",
        "Arabic"
    )

    val SOURCE_LANGUAGES = listOf(AUTO_DETECT) + SUPPORTED_LANGUAGES

    val REWRITE_STYLES = listOf(
        "Make simpler",
        "Make professional",
        "Make friendly",
        "Make concise",
        "Make detailed",
        "Improve clarity",
        "Improve grammar"
    )

    val SUMMARY_LENGTHS = listOf(
        "Short",
        "Medium",
        "Detailed"
    )

    val EXPLANATION_LEVELS = listOf(
        "Simple",
        "Student",
        "Detailed"
    )

    val PRACTICE_LEVELS = listOf(
        "Beginner",
        "Intermediate",
        "Advanced"
    )

    val PRACTICE_TOPICS = listOf(
        "Daily Greetings & Introductions",
        "Travel & Directions",
        "School & Study Life",
        "Shopping & Dining",
        "Workplace & Professional Communication",
        "Health & Emergencies"
    )
}

// -------------------------------------------------------------
// Structured Language AI Exceptions (Section 17)
// -------------------------------------------------------------
class LanguageEmptyTextException(
    message: String = "Please enter text to process."
) : AIServiceException("EMPTY_TEXT", message, message)

class LanguageInvalidInputException(
    message: String = "Invalid input provided. Please check your text and try again."
) : AIServiceException("INVALID_INPUT", message, message)

class LanguageConfigurationException(
    message: String = "Invalid language selection. Please choose a supported language."
) : AIServiceException("LANGUAGE_CONFIG_ERROR", message, message)

class LanguagePermissionException(
    message: String = "Access denied: You do not have permission to access this language data."
) : AIServiceException("AUTH_ERROR", message, message)

// -------------------------------------------------------------
// Output Domain Models
// -------------------------------------------------------------
data class TranslationResult(
    val projectId: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val inputText: String,
    val translatedText: String,
    val notesOrNuances: String = "",
    val disclaimer: String = "AI translation provided for helpful communication; nuances may vary by context."
)

data class LanguageRewriteResult(
    val projectId: String,
    val styleOption: String,
    val language: String,
    val inputText: String,
    val rewrittenText: String
)

data class GrammarCheckResult(
    val projectId: String,
    val inputText: String,
    val correctedVersion: String,
    val grammarIssues: List<String>,
    val spellingIssues: List<String>,
    val explanations: List<String>,
    val rawOutput: String
)

data class LanguageDetectionResult(
    val projectId: String,
    val inputText: String,
    val detectedLanguage: String,
    val confidenceExplanation: String,
    val isAmbiguous: Boolean,
    val rawOutput: String
)

data class LanguageSummaryResult(
    val projectId: String,
    val outputLanguage: String,
    val summaryLength: String,
    val inputText: String,
    val summaryText: String
)

data class ExplainTextResult(
    val projectId: String,
    val explanationLanguage: String,
    val explanationLevel: String,
    val inputText: String,
    val overallMeaning: String,
    val keyWordsAndConcepts: List<String>,
    val stepByStepExplanation: String,
    val rawOutput: String
)

data class PracticeExerciseItem(
    val id: String,
    val category: String, // Vocabulary, Simple Question, Fill-in-the-blank, Translation
    val prompt: String,
    val expectedAnswer: String,
    val hintOrExplanation: String
)

data class LanguagePracticeResult(
    val projectId: String,
    val sessionId: String,
    val languageToLearn: String,
    val level: String,
    val topic: String,
    val vocabularyList: List<String>,
    val conversationStarter: String,
    val exercises: List<PracticeExerciseItem>,
    val disclaimer: String = "Practice exercises help build familiarity — continued real-world practice is recommended for fluency.",
    val rawOutput: String
)

data class ConversationPracticeTurnResult(
    val projectId: String,
    val languageToLearn: String,
    val userMessage: String,
    val aiReply: String,
    val correction: String,
    val naturalAlternative: String,
    val shortExplanation: String,
    val rawOutput: String
)

data class PracticeProgressStats(
    val totalSessions: Int,
    val totalQuestionsAttempted: Int,
    val totalCorrectAnswers: Int
)
