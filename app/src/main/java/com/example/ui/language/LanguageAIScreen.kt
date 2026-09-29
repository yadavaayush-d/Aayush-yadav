package com.example.ui.language

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
fun LanguageAIScreen(
    viewModel: LanguageViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activeTab by viewModel.activeTab.collectAsState()
    val sourceLanguage by viewModel.sourceLanguage.collectAsState()
    val targetLanguage by viewModel.targetLanguage.collectAsState()
    val translatorInput by viewModel.translatorInput.collectAsState()

    val translateState by viewModel.translateState.collectAsState()
    val rewriteState by viewModel.rewriteState.collectAsState()
    val grammarState by viewModel.grammarState.collectAsState()
    val detectState by viewModel.detectState.collectAsState()
    val summarizeState by viewModel.summarizeState.collectAsState()
    val explainState by viewModel.explainState.collectAsState()
    val practiceState by viewModel.practiceState.collectAsState()
    val checkedExercises by viewModel.checkedExercises.collectAsState()
    val conversationTurns by viewModel.conversationTurns.collectAsState()
    val conversationLoading by viewModel.conversationLoading.collectAsState()
    val conversationError by viewModel.conversationError.collectAsState()

    val historyItems by viewModel.languageHistory.collectAsState()
    val savedItems by viewModel.savedContents.collectAsState()
    val practiceStats by viewModel.practiceStats.collectAsState()
    val openedProject by viewModel.openedProject.collectAsState()
    val feedbackBanner by viewModel.feedbackBanner.collectAsState()

    BackHandler {
        if (activeTab != LanguageTab.HOME) {
            viewModel.selectTab(LanguageTab.HOME)
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            LanguageHeader(
                onBack = {
                    if (activeTab != LanguageTab.HOME) {
                        viewModel.selectTab(LanguageTab.HOME)
                    } else {
                        onBack()
                    }
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("language_ai_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            LanguageTabBar(
                activeTab = activeTab,
                onTabSelected = { viewModel.selectTab(it) }
            )

            if (feedbackBanner != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("language_feedback_banner"),
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
                LanguageTab.HOME -> {
                    LanguageHomeView(
                        historyCount = historyItems.size,
                        savedCount = savedItems.size,
                        practiceStats = practiceStats,
                        onSelectTab = { viewModel.selectTab(it) }
                    )
                }
                LanguageTab.TRANSLATE -> {
                    TranslatorToolView(
                        sourceLanguage = sourceLanguage,
                        targetLanguage = targetLanguage,
                        inputText = translatorInput,
                        state = translateState,
                        onSourceLanguageChange = { viewModel.updateSourceLanguage(it) },
                        onTargetLanguageChange = { viewModel.updateTargetLanguage(it) },
                        onInputTextChange = { viewModel.updateTranslatorInput(it) },
                        onSwapLanguages = { viewModel.swapLanguages() },
                        onClear = { viewModel.clearTranslator() },
                        onTranslate = { viewModel.translate() },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { res ->
                            viewModel.saveContent(
                                projectType = "translate",
                                sourceLanguage = res.sourceLanguage,
                                targetLanguage = res.targetLanguage,
                                inputText = res.inputText,
                                outputText = res.translatedText,
                                projectId = res.projectId
                            )
                        },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                LanguageTab.REWRITE -> {
                    RewriteToolView(
                        state = rewriteState,
                        onRewrite = { txt, style, lang -> viewModel.rewrite(txt, style, lang) },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { res ->
                            viewModel.saveContent(
                                projectType = "rewrite",
                                sourceLanguage = res.language,
                                targetLanguage = res.language,
                                inputText = res.inputText,
                                outputText = res.rewrittenText,
                                projectId = res.projectId
                            )
                        },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                LanguageTab.GRAMMAR_CHECK -> {
                    GrammarCheckToolView(
                        state = grammarState,
                        onCheckGrammar = { txt, lang -> viewModel.checkGrammar(txt, lang) },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                LanguageTab.DETECT_LANGUAGE -> {
                    LanguageDetectionToolView(
                        state = detectState,
                        onDetect = { viewModel.detectLanguage(it) },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                LanguageTab.SUMMARIZE -> {
                    SummarizeToolView(
                        state = summarizeState,
                        onSummarize = { txt, lang, len -> viewModel.summarize(txt, lang, len) },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onSave = { res ->
                            viewModel.saveContent(
                                projectType = "summarize",
                                sourceLanguage = "Auto Detect",
                                targetLanguage = res.outputLanguage,
                                inputText = res.inputText,
                                outputText = res.summaryText,
                                projectId = res.projectId
                            )
                        },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                LanguageTab.EXPLAIN_TEXT -> {
                    ExplainTextToolView(
                        state = explainState,
                        onExplain = { txt, lang, lvl -> viewModel.explainText(txt, lang, lvl) },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                LanguageTab.LANGUAGE_PRACTICE -> {
                    LanguagePracticeToolView(
                        practiceState = practiceState,
                        practiceStats = practiceStats,
                        checkedExercises = checkedExercises,
                        conversationTurns = conversationTurns,
                        conversationLoading = conversationLoading,
                        conversationError = conversationError,
                        onGeneratePractice = { lang, lvl, t -> viewModel.generatePractice(lang, lvl, t) },
                        onCheckAnswer = { sessionId, ex, ans -> viewModel.checkExerciseAnswer(sessionId, ex, ans) },
                        onSendConversationTurn = { lang, lvl, t, msg -> viewModel.startOrReplyConversation(lang, lvl, t, msg) },
                        onRetry = { viewModel.retryLastAction() }
                    )
                }
                LanguageTab.HISTORY -> {
                    LanguageHistoryView(
                        historyItems = historyItems,
                        openedProject = openedProject,
                        onOpenProject = { viewModel.openProject(it) },
                        onCloseOpenedProject = { viewModel.closeOpenedProject() },
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onDeleteProject = { viewModel.deleteProject(it) }
                    )
                }
                LanguageTab.SAVED -> {
                    LanguageSavedContentView(
                        savedItems = savedItems,
                        onCopy = { viewModel.copyToClipboard(it, context) },
                        onDeleteSaved = { viewModel.unsaveContent(it) }
                    )
                }
            }
        }
    }
}
