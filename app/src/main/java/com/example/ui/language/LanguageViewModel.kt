package com.example.ui.language

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.LanguageProjectEntity
import com.example.data.local.entity.SavedLanguageContentEntity
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AIServiceException
import com.example.domain.language.ConversationPracticeTurnResult
import com.example.domain.language.ExplainTextResult
import com.example.domain.language.GrammarCheckResult
import com.example.domain.language.LanguageDetectionResult
import com.example.domain.language.LanguageOptions
import com.example.domain.language.LanguagePracticeResult
import com.example.domain.language.LanguageRewriteResult
import com.example.domain.language.LanguageSummaryResult
import com.example.domain.language.PracticeExerciseItem
import com.example.domain.language.PracticeProgressStats
import com.example.domain.language.TranslationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LanguageTab(val label: String) {
    HOME("Language Home"),
    TRANSLATE("Translate"),
    REWRITE("Rewrite"),
    GRAMMAR_CHECK("Grammar Check"),
    SUMMARIZE("Summarize"),
    DETECT_LANGUAGE("Detect Language"),
    EXPLAIN_TEXT("Explain Text"),
    LANGUAGE_PRACTICE("Language Practice"),
    HISTORY("Language History"),
    SAVED("Saved Content")
}

sealed class LanguageUiState<out T> {
    data object Idle : LanguageUiState<Nothing>()
    data object Loading : LanguageUiState<Nothing>()
    data class Success<out T>(val data: T) : LanguageUiState<T>()
    data class Error(val errorCode: String, val message: String) : LanguageUiState<Nothing>()
}

class LanguageViewModel(
    private val repository: NexaRepository
) : ViewModel() {

    val langRepo = repository.languageRepository

    private val _activeTab = MutableStateFlow(LanguageTab.HOME)
    val activeTab: StateFlow<LanguageTab> = _activeTab.asStateFlow()

    // Translator language state
    private val _sourceLanguage = MutableStateFlow(LanguageOptions.AUTO_DETECT)
    val sourceLanguage: StateFlow<String> = _sourceLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow("Hindi")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    private val _translatorInput = MutableStateFlow("")
    val translatorInput: StateFlow<String> = _translatorInput.asStateFlow()

    // Tool UI states
    private val _translateState = MutableStateFlow<LanguageUiState<TranslationResult>>(LanguageUiState.Idle)
    val translateState: StateFlow<LanguageUiState<TranslationResult>> = _translateState.asStateFlow()

    private val _rewriteState = MutableStateFlow<LanguageUiState<LanguageRewriteResult>>(LanguageUiState.Idle)
    val rewriteState: StateFlow<LanguageUiState<LanguageRewriteResult>> = _rewriteState.asStateFlow()

    private val _grammarState = MutableStateFlow<LanguageUiState<GrammarCheckResult>>(LanguageUiState.Idle)
    val grammarState: StateFlow<LanguageUiState<GrammarCheckResult>> = _grammarState.asStateFlow()

    private val _detectState = MutableStateFlow<LanguageUiState<LanguageDetectionResult>>(LanguageUiState.Idle)
    val detectState: StateFlow<LanguageUiState<LanguageDetectionResult>> = _detectState.asStateFlow()

    private val _summarizeState = MutableStateFlow<LanguageUiState<LanguageSummaryResult>>(LanguageUiState.Idle)
    val summarizeState: StateFlow<LanguageUiState<LanguageSummaryResult>> = _summarizeState.asStateFlow()

    private val _explainState = MutableStateFlow<LanguageUiState<ExplainTextResult>>(LanguageUiState.Idle)
    val explainState: StateFlow<LanguageUiState<ExplainTextResult>> = _explainState.asStateFlow()

    private val _practiceState = MutableStateFlow<LanguageUiState<LanguagePracticeResult>>(LanguageUiState.Idle)
    val practiceState: StateFlow<LanguageUiState<LanguagePracticeResult>> = _practiceState.asStateFlow()

    private val _conversationTurns = MutableStateFlow<List<ConversationPracticeTurnResult>>(emptyList())
    val conversationTurns: StateFlow<List<ConversationPracticeTurnResult>> = _conversationTurns.asStateFlow()

    private val _conversationLoading = MutableStateFlow(false)
    val conversationLoading: StateFlow<Boolean> = _conversationLoading.asStateFlow()

    private val _conversationError = MutableStateFlow<String?>(null)
    val conversationError: StateFlow<String?> = _conversationError.asStateFlow()

    // Exercise answer check states (exerciseId -> Boolean correct/incorrect)
    private val _checkedExercises = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val checkedExercises: StateFlow<Map<String, Boolean>> = _checkedExercises.asStateFlow()

    // Opened project in History
    private val _openedProject = MutableStateFlow<LanguageProjectEntity?>(null)
    val openedProject: StateFlow<LanguageProjectEntity?> = _openedProject.asStateFlow()

    private val _feedbackBanner = MutableStateFlow<String?>(null)
    val feedbackBanner: StateFlow<String?> = _feedbackBanner.asStateFlow()

    private val _lastCopiedText = MutableStateFlow<String?>(null)
    val lastCopiedText: StateFlow<String?> = _lastCopiedText.asStateFlow()

    private var lastRetryAction: (() -> Unit)? = null

    val languageHistory: StateFlow<List<LanguageProjectEntity>> = repository.currentUser
        .flatMapLatest { user ->
            if (user != null) langRepo.getLanguageHistory(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedContents: StateFlow<List<SavedLanguageContentEntity>> = repository.currentUser
        .flatMapLatest { user ->
            if (user != null) langRepo.getSavedContent(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val practiceStats: StateFlow<PracticeProgressStats> = repository.currentUser
        .flatMapLatest { user ->
            if (user != null) langRepo.getPracticeProgressFlow(user.id) else flowOf(PracticeProgressStats(0, 0, 0))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PracticeProgressStats(0, 0, 0))

    fun selectTab(tab: LanguageTab) {
        _activeTab.value = tab
    }

    fun updateSourceLanguage(lang: String) {
        _sourceLanguage.value = lang
    }

    fun updateTargetLanguage(lang: String) {
        _targetLanguage.value = lang
    }

    fun updateTranslatorInput(text: String) {
        _translatorInput.value = text
    }

    fun clearTranslator() {
        _translatorInput.value = ""
        _translateState.value = LanguageUiState.Idle
    }

    fun swapLanguages() {
        val currentSrc = _sourceLanguage.value
        val currentTgt = _targetLanguage.value
        if (currentSrc == LanguageOptions.AUTO_DETECT) {
            _sourceLanguage.value = currentTgt
            _targetLanguage.value = if (currentTgt == "English") "Hindi" else "English"
        } else {
            _sourceLanguage.value = currentTgt
            _targetLanguage.value = currentSrc
        }
    }

    fun copyToClipboard(text: String, context: Context? = null, label: String = "Language AI Output") {
        if (text.isBlank()) return
        _lastCopiedText.value = text
        _feedbackBanner.value = "Copied to clipboard!"
        context?.let { ctx ->
            try {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
            } catch (_: Exception) {
            }
        }
    }

    fun clearFeedbackBanner() {
        _feedbackBanner.value = null
    }

    fun retryLastAction() {
        lastRetryAction?.invoke()
    }

    private fun mapError(err: Throwable): LanguageUiState.Error {
        return when (err) {
            is AIServiceException -> LanguageUiState.Error(err.errorCode, err.userFriendlyMessage)
            is SecurityException -> LanguageUiState.Error("AUTH_ERROR", "Access denied.")
            else -> LanguageUiState.Error("AI_PROVIDER_ERROR", "AI service is temporarily unavailable. Please try again.")
        }
    }

    // -------------------------------------------------------------
    // 2 & 3. TRANSLATE
    // -------------------------------------------------------------
    fun translate(
        text: String = _translatorInput.value,
        sourceLang: String = _sourceLanguage.value,
        targetLang: String = _targetLanguage.value
    ) {
        _translatorInput.value = text
        _sourceLanguage.value = sourceLang
        _targetLanguage.value = targetLang
        lastRetryAction = { translate(text, sourceLang, targetLang) }
        viewModelScope.launch {
            _translateState.value = LanguageUiState.Loading
            val res = langRepo.translateText(sourceLang, targetLang, text)
            _translateState.value = res.fold(
                onSuccess = { LanguageUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 4. REWRITE
    // -------------------------------------------------------------
    fun rewrite(text: String, styleOption: String, language: String = "English") {
        lastRetryAction = { rewrite(text, styleOption, language) }
        viewModelScope.launch {
            _rewriteState.value = LanguageUiState.Loading
            val res = langRepo.rewriteText(text, styleOption, language)
            _rewriteState.value = res.fold(
                onSuccess = { LanguageUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 5. GRAMMAR CHECK
    // -------------------------------------------------------------
    fun checkGrammar(text: String, language: String = "English") {
        lastRetryAction = { checkGrammar(text, language) }
        viewModelScope.launch {
            _grammarState.value = LanguageUiState.Loading
            val res = langRepo.checkGrammar(text, language)
            _grammarState.value = res.fold(
                onSuccess = { LanguageUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 6. LANGUAGE DETECTION
    // -------------------------------------------------------------
    fun detectLanguage(text: String) {
        lastRetryAction = { detectLanguage(text) }
        viewModelScope.launch {
            _detectState.value = LanguageUiState.Loading
            val res = langRepo.detectLanguage(text)
            _detectState.value = res.fold(
                onSuccess = { LanguageUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 7. SUMMARIZE
    // -------------------------------------------------------------
    fun summarize(text: String, outputLanguage: String, summaryLength: String) {
        lastRetryAction = { summarize(text, outputLanguage, summaryLength) }
        viewModelScope.launch {
            _summarizeState.value = LanguageUiState.Loading
            val res = langRepo.summarizeText(text, outputLanguage, summaryLength)
            _summarizeState.value = res.fold(
                onSuccess = { LanguageUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 8. EXPLAIN TEXT
    // -------------------------------------------------------------
    fun explainText(text: String, explanationLanguage: String, explanationLevel: String) {
        lastRetryAction = { explainText(text, explanationLanguage, explanationLevel) }
        viewModelScope.launch {
            _explainState.value = LanguageUiState.Loading
            val res = langRepo.explainText(text, explanationLanguage, explanationLevel)
            _explainState.value = res.fold(
                onSuccess = { LanguageUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 9. LANGUAGE PRACTICE & ANSWER CHECKING
    // -------------------------------------------------------------
    fun generatePractice(languageToLearn: String, level: String, topic: String) {
        lastRetryAction = { generatePractice(languageToLearn, level, topic) }
        viewModelScope.launch {
            _practiceState.value = LanguageUiState.Loading
            _checkedExercises.value = emptyMap()
            val res = langRepo.generateLanguagePractice(languageToLearn, level, topic)
            _practiceState.value = res.fold(
                onSuccess = { LanguageUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    fun checkExerciseAnswer(sessionId: String, exercise: PracticeExerciseItem, userAnswer: String) {
        viewModelScope.launch {
            val res = langRepo.checkPracticeExerciseAnswer(sessionId, exercise, userAnswer)
            res.fold(
                onSuccess = { isCorrect ->
                    _checkedExercises.value = _checkedExercises.value + (exercise.id to isCorrect)
                    _feedbackBanner.value = if (isCorrect) {
                        "Correct! Great job."
                    } else {
                        "Not quite. Expected: ${exercise.expectedAnswer}"
                    }
                },
                onFailure = { err ->
                    _feedbackBanner.value = err.message ?: "Could not check answer."
                }
            )
        }
    }

    // -------------------------------------------------------------
    // 10. CONVERSATION PRACTICE
    // -------------------------------------------------------------
    fun startOrReplyConversation(languageToLearn: String, level: String, topic: String, userMessage: String) {
        lastRetryAction = { startOrReplyConversation(languageToLearn, level, topic, userMessage) }
        viewModelScope.launch {
            _conversationLoading.value = true
            _conversationError.value = null
            val res = langRepo.practiceConversationTurn(languageToLearn, level, topic, userMessage)
            _conversationLoading.value = false
            res.fold(
                onSuccess = { turn ->
                    _conversationTurns.value = _conversationTurns.value + turn
                },
                onFailure = { err ->
                    _conversationError.value = (err as? AIServiceException)?.userFriendlyMessage
                        ?: (err.message ?: "Conversation practice unavailable.")
                }
            )
        }
    }

    fun resetConversationPractice() {
        _conversationTurns.value = emptyList()
        _conversationError.value = null
    }

    // -------------------------------------------------------------
    // 11 & 12. HISTORY & SAVED CONTENT
    // -------------------------------------------------------------
    fun openProject(projectId: String) {
        viewModelScope.launch {
            val res = langRepo.openLanguageProject(projectId)
            res.fold(
                onSuccess = { _openedProject.value = it },
                onFailure = { _feedbackBanner.value = it.message ?: "Access denied." }
            )
        }
    }

    fun closeOpenedProject() {
        _openedProject.value = null
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val res = langRepo.deleteLanguageProject(projectId)
            res.fold(
                onSuccess = {
                    if (_openedProject.value?.id == projectId) {
                        _openedProject.value = null
                    }
                    _feedbackBanner.value = "Removed from Language History."
                },
                onFailure = { _feedbackBanner.value = it.message ?: "Could not delete history item." }
            )
        }
    }

    fun saveContent(
        projectType: String,
        sourceLanguage: String,
        targetLanguage: String,
        inputText: String,
        outputText: String,
        projectId: String? = null
    ) {
        viewModelScope.launch {
            val res = langRepo.saveLanguageContent(
                projectType = projectType,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                inputText = inputText,
                outputText = outputText,
                projectId = projectId
            )
            res.fold(
                onSuccess = { _feedbackBanner.value = "Saved to Language Favorites!" },
                onFailure = { _feedbackBanner.value = it.message ?: "Could not save content." }
            )
        }
    }

    fun unsaveContent(savedId: String) {
        viewModelScope.launch {
            val res = langRepo.unsaveLanguageContent(savedId)
            res.fold(
                onSuccess = { _feedbackBanner.value = "Removed from Saved Content." },
                onFailure = { _feedbackBanner.value = it.message ?: "Could not remove saved item." }
            )
        }
    }
}
