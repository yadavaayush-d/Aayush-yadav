package com.example.ui.creator

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CreatorAIScreen(
    viewModel: CreatorViewModel,
    onBack: () -> Unit
) {
    val activeTab by viewModel.activeTab.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedPlatformPreset by viewModel.selectedPlatformPreset.collectAsState()
    val feedbackMessage by viewModel.feedbackMessage.collectAsState()
    val openedProject by viewModel.openedProject.collectAsState()

    val contentGenState by viewModel.contentGenState.collectAsState()
    val youtubeScriptState by viewModel.youtubeScriptState.collectAsState()
    val shortsReelsState by viewModel.shortsReelsState.collectAsState()
    val titleGenState by viewModel.titleGenState.collectAsState()
    val captionGenState by viewModel.captionGenState.collectAsState()
    val hashtagState by viewModel.hashtagState.collectAsState()
    val contentIdeasState by viewModel.contentIdeasState.collectAsState()
    val blogWriterState by viewModel.blogWriterState.collectAsState()
    val rewriteState by viewModel.rewriteState.collectAsState()

    val creatorProjects by viewModel.creatorProjects.collectAsState()
    val savedContents by viewModel.savedContents.collectAsState()

    val context = LocalContext.current

    BackHandler {
        if (activeTab != CreatorTab.HOME) {
            viewModel.selectTab(CreatorTab.HOME)
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            CreatorHeader(
                selectedLanguage = selectedLanguage,
                onLanguageChange = { viewModel.selectLanguage(it) },
                onBack = {
                    if (activeTab != CreatorTab.HOME) {
                        viewModel.selectTab(CreatorTab.HOME)
                    } else {
                        onBack()
                    }
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("creator_ai_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            CreatorTabBar(
                activeTab = activeTab,
                onTabSelected = { viewModel.selectTab(it) }
            )

            if (feedbackMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("creator_feedback_banner"),
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
                            text = feedbackMessage!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        TextButton(onClick = { viewModel.clearFeedbackMessage() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            when (activeTab) {
                CreatorTab.HOME -> {
                    CreatorHomeView(
                        cards = viewModel.homeCards,
                        projectsCount = creatorProjects.size,
                        savedCount = savedContents.size,
                        onSelectCard = { viewModel.selectHomeCard(it) },
                        onSelectTab = { viewModel.selectTab(it) }
                    )
                }
                CreatorTab.CONTENT_GENERATOR -> {
                    ContentGeneratorView(
                        presetPlatform = selectedPlatformPreset,
                        selectedLanguage = selectedLanguage,
                        state = contentGenState,
                        onGenerate = { plat, type, top, aud, lang, tone, len, instr ->
                            viewModel.generateContent(plat, type, top, aud, lang, tone, len, instr)
                        },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { title, cType, plat, lang, content, projId ->
                            viewModel.saveToFavorites(title, cType, plat, lang, content, projId)
                        },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                CreatorTab.YOUTUBE_SCRIPT -> {
                    YouTubeScriptGeneratorView(
                        selectedLanguage = selectedLanguage,
                        state = youtubeScriptState,
                        onGenerate = { top, dur, lang, aud, style ->
                            viewModel.generateYouTubeScript(top, dur, lang, aud, style)
                        },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { title, cType, plat, lang, content, projId ->
                            viewModel.saveToFavorites(title, cType, plat, lang, content, projId)
                        },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                CreatorTab.SHORTS_REELS -> {
                    ShortsReelsGeneratorView(
                        presetPlatform = selectedPlatformPreset,
                        selectedLanguage = selectedLanguage,
                        state = shortsReelsState,
                        onGenerate = { top, dur, plat, lang, style ->
                            viewModel.generateShortsOrReels(top, dur, plat, lang, style)
                        },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { title, cType, plat, lang, content, projId ->
                            viewModel.saveToFavorites(title, cType, plat, lang, content, projId)
                        },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                CreatorTab.TITLE_GENERATOR -> {
                    TitleGeneratorView(
                        selectedLanguage = selectedLanguage,
                        state = titleGenState,
                        onGenerate = { top, plat, lang, cnt ->
                            viewModel.generateTitles(top, plat, lang, cnt)
                        },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { title, cType, plat, lang, content, projId ->
                            viewModel.saveToFavorites(title, cType, plat, lang, content, projId)
                        },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                CreatorTab.CAPTION_GENERATOR -> {
                    CaptionGeneratorView(
                        selectedLanguage = selectedLanguage,
                        state = captionGenState,
                        onGenerate = { top, plat, tone, lang, len ->
                            viewModel.generateCaption(top, plat, tone, lang, len)
                        },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { title, cType, plat, lang, content, projId ->
                            viewModel.saveToFavorites(title, cType, plat, lang, content, projId)
                        },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                CreatorTab.HASHTAG_ASSISTANT -> {
                    HashtagAssistantView(
                        selectedLanguage = selectedLanguage,
                        state = hashtagState,
                        onGenerate = { top, plat, lang ->
                            viewModel.suggestHashtags(top, plat, lang)
                        },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { title, cType, plat, lang, content, projId ->
                            viewModel.saveToFavorites(title, cType, plat, lang, content, projId)
                        },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                CreatorTab.CONTENT_IDEAS -> {
                    ContentIdeasView(
                        selectedLanguage = selectedLanguage,
                        state = contentIdeasState,
                        onGenerate = { niche, plat, aud, cnt, lang ->
                            viewModel.generateContentIdeas(niche, plat, aud, cnt, lang)
                        },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSaveIdea = { idea, plat, projId ->
                            viewModel.saveContentIdea(idea, plat, projId)
                        },
                        onSaveAll = { title, cType, plat, lang, content, projId ->
                            viewModel.saveToFavorites(title, cType, plat, lang, content, projId)
                        },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                CreatorTab.BLOG_WRITER -> {
                    BlogWriterView(
                        selectedLanguage = selectedLanguage,
                        state = blogWriterState,
                        onGenerate = { top, aud, lang, tone, len ->
                            viewModel.generateBlog(top, aud, lang, tone, len)
                        },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { title, cType, plat, lang, content, projId ->
                            viewModel.saveToFavorites(title, cType, plat, lang, content, projId)
                        },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                CreatorTab.CONTENT_REWRITER -> {
                    ContentRewriterView(
                        selectedLanguage = selectedLanguage,
                        state = rewriteState,
                        onRewrite = { txt, act, lang ->
                            viewModel.rewriteContent(txt, act, lang)
                        },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { title, cType, plat, lang, content, projId ->
                            viewModel.saveToFavorites(title, cType, plat, lang, content, projId)
                        },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                CreatorTab.HISTORY -> {
                    CreatorHistoryView(
                        projects = creatorProjects,
                        openedProject = openedProject,
                        onOpenProject = { viewModel.openProject(it) },
                        onCloseProject = { viewModel.closeOpenedProject() },
                        onRenameProject = { id, newTitle -> viewModel.renameProject(id, newTitle) },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onCopyProject = { viewModel.copyToClipboard(it, context) },
                        onRegenerateProject = { viewModel.regenerateProject(it) }
                    )
                }
                CreatorTab.SAVED -> {
                    CreatorSavedContentView(
                        savedItems = savedContents,
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onDeleteSaved = { viewModel.unsaveContent(it) }
                    )
                }
            }
        }
    }
}
