package com.example.ui.creator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CreatorProjectEntity
import com.example.data.local.entity.SavedCreatorContentEntity
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AIServiceException
import com.example.domain.creator.BlogArticleResult
import com.example.domain.creator.CaptionResult
import com.example.domain.creator.ContentIdeaItem
import com.example.domain.creator.ContentIdeasResult
import com.example.domain.creator.CreatorHomeCardItem
import com.example.domain.creator.GeneratedContentResult
import com.example.domain.creator.HashtagResult
import com.example.domain.creator.RewriteResult
import com.example.domain.creator.ShortVideoScriptResult
import com.example.domain.creator.TitleSuggestionsResult
import com.example.domain.creator.YouTubeScriptResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CreatorTab(val label: String) {
    HOME("Creator Home"),
    CONTENT_GENERATOR("Content Generator"),
    YOUTUBE_SCRIPT("YouTube Script"),
    SHORTS_REELS("Shorts / Reels"),
    TITLE_GENERATOR("Titles"),
    CAPTION_GENERATOR("Captions"),
    HASHTAG_ASSISTANT("Hashtags"),
    CONTENT_IDEAS("Content Ideas"),
    BLOG_WRITER("Blog Writer"),
    CONTENT_REWRITER("Rewriter"),
    HISTORY("Creator History"),
    SAVED("Saved Content")
}

sealed class CreatorUiState<out T> {
    data object Idle : CreatorUiState<Nothing>()
    data object Loading : CreatorUiState<Nothing>()
    data class Success<out T>(val data: T) : CreatorUiState<T>()
    data class Error(
        val errorCode: String,
        val message: String
    ) : CreatorUiState<Nothing>()
}

class CreatorViewModel(
    private val repository: NexaRepository
) : ViewModel() {

    val creatorRepo = repository.creatorRepository

    // 10 Creator Home Cards required by Section 1
    val homeCards: List<CreatorHomeCardItem> = listOf(
        CreatorHomeCardItem("youtube", "YouTube", "Full video scripts, hooks & CTAs", "PlayCircle", CreatorTab.YOUTUBE_SCRIPT.name, "YouTube"),
        CreatorHomeCardItem("youtube_shorts", "YouTube Shorts", "60s vertical scripts & beats", "Bolt", CreatorTab.SHORTS_REELS.name, "YouTube Shorts"),
        CreatorHomeCardItem("instagram", "Instagram", "Carousels, posts & stories", "PhotoCamera", CreatorTab.CONTENT_GENERATOR.name, "Instagram"),
        CreatorHomeCardItem("reels", "Reels", "Engaging Instagram Reels scripts", "Movie", CreatorTab.SHORTS_REELS.name, "Instagram Reels"),
        CreatorHomeCardItem("blog", "Blog", "Original articles & SEO outlines", "Article", CreatorTab.BLOG_WRITER.name, "Blog"),
        CreatorHomeCardItem("social_media", "Social Media", "Multi-platform social posts", "Share", CreatorTab.CONTENT_GENERATOR.name, "General Social Media"),
        CreatorHomeCardItem("content_ideas", "Content Ideas", "Niche ideas, formats & hooks", "Lightbulb", CreatorTab.CONTENT_IDEAS.name, "YouTube"),
        CreatorHomeCardItem("captions", "Captions", "Scroll-stopping captions & CTAs", "Subtitles", CreatorTab.CAPTION_GENERATOR.name, "Instagram"),
        CreatorHomeCardItem("hashtags", "Hashtags", "Relevant discovery hashtag sets", "Tag", CreatorTab.HASHTAG_ASSISTANT.name, "Instagram"),
        CreatorHomeCardItem("scripts", "Scripts", "Structured storytelling scripts", "Description", CreatorTab.YOUTUBE_SCRIPT.name, "YouTube")
    )

    private val _activeTab = MutableStateFlow(CreatorTab.HOME)
    val activeTab: StateFlow<CreatorTab> = _activeTab.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("English")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _selectedPlatformPreset = MutableStateFlow("YouTube")
    val selectedPlatformPreset: StateFlow<String> = _selectedPlatformPreset.asStateFlow()

    // Clipboard & Feedback Banner State
    private val _lastCopiedText = MutableStateFlow<String?>(null)
    val lastCopiedText: StateFlow<String?> = _lastCopiedText.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    // Opened Project in History
    private val _openedProject = MutableStateFlow<CreatorProjectEntity?>(null)
    val openedProject: StateFlow<CreatorProjectEntity?> = _openedProject.asStateFlow()

    // Retry callback for last failed action
    private var lastRetryBlock: (() -> Unit)? = null

    // Tool UI States
    private val _contentGenState = MutableStateFlow<CreatorUiState<GeneratedContentResult>>(CreatorUiState.Idle)
    val contentGenState: StateFlow<CreatorUiState<GeneratedContentResult>> = _contentGenState.asStateFlow()

    private val _youtubeScriptState = MutableStateFlow<CreatorUiState<YouTubeScriptResult>>(CreatorUiState.Idle)
    val youtubeScriptState: StateFlow<CreatorUiState<YouTubeScriptResult>> = _youtubeScriptState.asStateFlow()

    private val _shortsReelsState = MutableStateFlow<CreatorUiState<ShortVideoScriptResult>>(CreatorUiState.Idle)
    val shortsReelsState: StateFlow<CreatorUiState<ShortVideoScriptResult>> = _shortsReelsState.asStateFlow()

    private val _titleGenState = MutableStateFlow<CreatorUiState<TitleSuggestionsResult>>(CreatorUiState.Idle)
    val titleGenState: StateFlow<CreatorUiState<TitleSuggestionsResult>> = _titleGenState.asStateFlow()

    private val _captionGenState = MutableStateFlow<CreatorUiState<CaptionResult>>(CreatorUiState.Idle)
    val captionGenState: StateFlow<CreatorUiState<CaptionResult>> = _captionGenState.asStateFlow()

    private val _hashtagState = MutableStateFlow<CreatorUiState<HashtagResult>>(CreatorUiState.Idle)
    val hashtagState: StateFlow<CreatorUiState<HashtagResult>> = _hashtagState.asStateFlow()

    private val _contentIdeasState = MutableStateFlow<CreatorUiState<ContentIdeasResult>>(CreatorUiState.Idle)
    val contentIdeasState: StateFlow<CreatorUiState<ContentIdeasResult>> = _contentIdeasState.asStateFlow()

    private val _blogWriterState = MutableStateFlow<CreatorUiState<BlogArticleResult>>(CreatorUiState.Idle)
    val blogWriterState: StateFlow<CreatorUiState<BlogArticleResult>> = _blogWriterState.asStateFlow()

    private val _rewriteState = MutableStateFlow<CreatorUiState<RewriteResult>>(CreatorUiState.Idle)
    val rewriteState: StateFlow<CreatorUiState<RewriteResult>> = _rewriteState.asStateFlow()

    // Real-Time Database Flows for History & Saved Content
    val creatorProjects: StateFlow<List<CreatorProjectEntity>> = repository.currentUser
        .flatMapLatest { user ->
            if (user != null) creatorRepo.getCreatorProjects(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedContents: StateFlow<List<SavedCreatorContentEntity>> = repository.currentUser
        .flatMapLatest { user ->
            if (user != null) creatorRepo.getSavedContent(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -------------------------------------------------------------
    // Navigation & Presets
    // -------------------------------------------------------------
    fun selectTab(tab: CreatorTab) {
        _activeTab.value = tab
    }

    fun selectHomeCard(card: CreatorHomeCardItem) {
        card.presetPlatform?.let { _selectedPlatformPreset.value = it }
        val target = CreatorTab.entries.firstOrNull { it.name == card.targetTab } ?: CreatorTab.CONTENT_GENERATOR
        _activeTab.value = target
    }

    fun selectLanguage(language: String) {
        _selectedLanguage.value = language
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    fun copyToClipboard(text: String, context: Context? = null, label: String = "Creator AI Content") {
        if (text.isBlank()) return
        _lastCopiedText.value = text
        _feedbackMessage.value = "Copied to clipboard!"
        context?.let { ctx ->
            try {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
            } catch (_: Exception) {
            }
        }
    }

    fun retryLastAction() {
        lastRetryBlock?.invoke()
    }

    private fun mapError(error: Throwable): CreatorUiState.Error {
        return when (error) {
            is AIServiceException -> CreatorUiState.Error(error.errorCode, error.userFriendlyMessage)
            is SecurityException -> CreatorUiState.Error("AUTH_ERROR", "Please log in again.")
            is IllegalArgumentException -> CreatorUiState.Error("VALIDATION_ERROR", error.message ?: "Please check your inputs.")
            else -> CreatorUiState.Error("AI_PROVIDER_ERROR", "AI service is temporarily unavailable. Please try again.")
        }
    }

    // -------------------------------------------------------------
    // 2. CONTENT GENERATOR
    // -------------------------------------------------------------
    fun generateContent(
        platform: String,
        contentType: String,
        topic: String,
        targetAudience: String,
        language: String = _selectedLanguage.value,
        tone: String,
        length: String,
        additionalInstructions: String
    ) {
        lastRetryBlock = {
            generateContent(platform, contentType, topic, targetAudience, language, tone, length, additionalInstructions)
        }
        viewModelScope.launch {
            _contentGenState.value = CreatorUiState.Loading
            val res = creatorRepo.generateContent(
                platform = platform,
                contentType = contentType,
                topic = topic,
                targetAudience = targetAudience,
                language = language,
                tone = tone,
                length = length,
                additionalInstructions = additionalInstructions
            )
            _contentGenState.value = res.fold(
                onSuccess = { CreatorUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 3. YOUTUBE SCRIPT GENERATOR
    // -------------------------------------------------------------
    fun generateYouTubeScript(
        topic: String,
        duration: String,
        language: String = _selectedLanguage.value,
        audience: String,
        style: String
    ) {
        lastRetryBlock = {
            generateYouTubeScript(topic, duration, language, audience, style)
        }
        viewModelScope.launch {
            _youtubeScriptState.value = CreatorUiState.Loading
            val res = creatorRepo.generateYouTubeScript(
                topic = topic,
                duration = duration,
                language = language,
                audience = audience,
                style = style
            )
            _youtubeScriptState.value = res.fold(
                onSuccess = { CreatorUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 4. SHORTS / REELS GENERATOR
    // -------------------------------------------------------------
    fun generateShortsOrReels(
        topic: String,
        duration: String,
        platform: String,
        language: String = _selectedLanguage.value,
        style: String
    ) {
        lastRetryBlock = {
            generateShortsOrReels(topic, duration, platform, language, style)
        }
        viewModelScope.launch {
            _shortsReelsState.value = CreatorUiState.Loading
            val res = creatorRepo.generateShortsOrReelsScript(
                topic = topic,
                duration = duration,
                platform = platform,
                language = language,
                style = style
            )
            _shortsReelsState.value = res.fold(
                onSuccess = { CreatorUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 5. TITLE GENERATOR
    // -------------------------------------------------------------
    fun generateTitles(
        topic: String,
        platform: String,
        language: String = _selectedLanguage.value,
        count: Int
    ) {
        lastRetryBlock = {
            generateTitles(topic, platform, language, count)
        }
        viewModelScope.launch {
            _titleGenState.value = CreatorUiState.Loading
            val res = creatorRepo.generateTitles(
                topic = topic,
                platform = platform,
                language = language,
                count = count
            )
            _titleGenState.value = res.fold(
                onSuccess = { CreatorUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 6. CAPTION GENERATOR
    // -------------------------------------------------------------
    fun generateCaption(
        topic: String,
        platform: String,
        tone: String,
        language: String = _selectedLanguage.value,
        length: String
    ) {
        lastRetryBlock = {
            generateCaption(topic, platform, tone, language, length)
        }
        viewModelScope.launch {
            _captionGenState.value = CreatorUiState.Loading
            val res = creatorRepo.generateCaption(
                topic = topic,
                platform = platform,
                tone = tone,
                language = language,
                length = length
            )
            _captionGenState.value = res.fold(
                onSuccess = { CreatorUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 7. HASHTAG ASSISTANT
    // -------------------------------------------------------------
    fun suggestHashtags(
        topic: String,
        platform: String,
        language: String = _selectedLanguage.value
    ) {
        lastRetryBlock = {
            suggestHashtags(topic, platform, language)
        }
        viewModelScope.launch {
            _hashtagState.value = CreatorUiState.Loading
            val res = creatorRepo.suggestHashtags(
                topic = topic,
                platform = platform,
                language = language
            )
            _hashtagState.value = res.fold(
                onSuccess = { CreatorUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 8. CONTENT IDEAS
    // -------------------------------------------------------------
    fun generateContentIdeas(
        nicheOrTopic: String,
        platform: String,
        audience: String,
        count: Int,
        language: String = _selectedLanguage.value
    ) {
        lastRetryBlock = {
            generateContentIdeas(nicheOrTopic, platform, audience, count, language)
        }
        viewModelScope.launch {
            _contentIdeasState.value = CreatorUiState.Loading
            val res = creatorRepo.generateContentIdeas(
                nicheOrTopic = nicheOrTopic,
                platform = platform,
                audience = audience,
                count = count,
                language = language
            )
            _contentIdeasState.value = res.fold(
                onSuccess = { CreatorUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 9. BLOG WRITER
    // -------------------------------------------------------------
    fun generateBlog(
        topic: String,
        audience: String,
        language: String = _selectedLanguage.value,
        tone: String,
        approxLength: String
    ) {
        lastRetryBlock = {
            generateBlog(topic, audience, language, tone, approxLength)
        }
        viewModelScope.launch {
            _blogWriterState.value = CreatorUiState.Loading
            val res = creatorRepo.generateBlog(
                topic = topic,
                audience = audience,
                language = language,
                tone = tone,
                approxLength = approxLength
            )
            _blogWriterState.value = res.fold(
                onSuccess = { CreatorUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 10. CONTENT REWRITER
    // -------------------------------------------------------------
    fun rewriteContent(
        userText: String,
        action: String,
        language: String = _selectedLanguage.value
    ) {
        lastRetryBlock = {
            rewriteContent(userText, action, language)
        }
        viewModelScope.launch {
            _rewriteState.value = CreatorUiState.Loading
            val res = creatorRepo.rewriteContent(
                userText = userText,
                action = action,
                language = language
            )
            _rewriteState.value = res.fold(
                onSuccess = { CreatorUiState.Success(it) },
                onFailure = { mapError(it) }
            )
        }
    }

    // -------------------------------------------------------------
    // 11. CREATOR HISTORY / PROJECTS MANAGEMENT
    // -------------------------------------------------------------
    fun openProject(projectId: String) {
        viewModelScope.launch {
            val res = creatorRepo.openProject(projectId)
            res.fold(
                onSuccess = { _openedProject.value = it },
                onFailure = { _feedbackMessage.value = it.message ?: "Access denied" }
            )
        }
    }

    fun closeOpenedProject() {
        _openedProject.value = null
    }

    fun renameProject(projectId: String, newTitle: String) {
        viewModelScope.launch {
            val res = creatorRepo.renameProject(projectId, newTitle)
            res.fold(
                onSuccess = { updated ->
                    if (_openedProject.value?.id == projectId) {
                        _openedProject.value = updated
                    }
                    _feedbackMessage.value = "Project renamed!"
                },
                onFailure = { _feedbackMessage.value = it.message ?: "Could not rename project" }
            )
        }
    }

    fun deleteProject(projectId: String, onAfterDelete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val res = creatorRepo.deleteProject(projectId)
            res.fold(
                onSuccess = {
                    if (_openedProject.value?.id == projectId) {
                        _openedProject.value = null
                    }
                    _feedbackMessage.value = "Project deleted."
                    onAfterDelete?.invoke()
                },
                onFailure = { _feedbackMessage.value = it.message ?: "Could not delete project" }
            )
        }
    }

    fun regenerateProject(projectId: String) {
        viewModelScope.launch {
            _feedbackMessage.value = "Regenerating project..."
            val res = creatorRepo.regenerateProject(projectId)
            res.fold(
                onSuccess = { updated ->
                    _openedProject.value = updated
                    _feedbackMessage.value = "Project regenerated!"
                },
                onFailure = { _feedbackMessage.value = it.message ?: "Failed to regenerate project" }
            )
        }
    }

    // -------------------------------------------------------------
    // 12. FAVORITES / SAVED CONTENT MANAGEMENT
    // -------------------------------------------------------------
    fun saveToFavorites(
        title: String,
        contentType: String,
        platform: String,
        language: String,
        content: String,
        projectId: String? = null
    ) {
        viewModelScope.launch {
            val res = creatorRepo.saveContentItem(
                title = title,
                contentType = contentType,
                platform = platform,
                language = language,
                content = content,
                projectId = projectId
            )
            res.fold(
                onSuccess = { _feedbackMessage.value = "Saved to Favorites!" },
                onFailure = { _feedbackMessage.value = it.message ?: "Could not save content." }
            )
        }
    }

    fun saveContentIdea(idea: ContentIdeaItem, platform: String, projectId: String? = null) {
        val formatted = buildString {
            appendLine("Idea Title: ${idea.title}")
            appendLine("Format: ${idea.suggestedFormat}")
            appendLine("Hook: ${idea.suggestedHook}")
            appendLine("Description: ${idea.shortDescription}")
        }.trim()
        saveToFavorites(
            title = idea.title,
            contentType = "content_ideas",
            platform = platform,
            language = _selectedLanguage.value,
            content = formatted,
            projectId = projectId
        )
    }

    fun unsaveContent(savedId: String) {
        viewModelScope.launch {
            val res = creatorRepo.unsaveContentItem(savedId)
            res.fold(
                onSuccess = { _feedbackMessage.value = "Removed from Saved Content." },
                onFailure = { _feedbackMessage.value = it.message ?: "Could not remove item." }
            )
        }
    }
}
