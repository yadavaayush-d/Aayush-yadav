package com.example.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun StudyAIScreen(
    viewModel: StudyViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val selectedClass by viewModel.selectedClass.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val selectedSubject by viewModel.selectedSubject.collectAsState()
    val selectedChapter by viewModel.selectedChapter.collectAsState()
    val selectedTopic by viewModel.selectedTopic.collectAsState()
    val selectedDifficulty by viewModel.selectedDifficulty.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()

    val doubtState by viewModel.doubtState.collectAsState()
    val explainTopicState by viewModel.explainTopicState.collectAsState()
    val mcqState by viewModel.mcqState.collectAsState()
    val mcqAnswers by viewModel.mcqAnswers.collectAsState()
    val mcqSubmitted by viewModel.mcqSubmitted.collectAsState()
    val quizSession by viewModel.quizSession.collectAsState()
    val practiceState by viewModel.practiceState.collectAsState()
    val explainAnswerState by viewModel.explainAnswerState.collectAsState()
    val studyPlanState by viewModel.studyPlanState.collectAsState()
    val planSaveMessage by viewModel.planSaveMessage.collectAsState()
    val summarizeState by viewModel.summarizeState.collectAsState()
    val progressSummary by viewModel.progressSummary.collectAsState()
    val isLoadingProgress by viewModel.isLoadingProgress.collectAsState()
    val studyActivities by viewModel.studyActivities.collectAsState()
    val savedPlans by viewModel.savedPlans.collectAsState()

    Scaffold(
        topBar = {
            StudyHeader(
                selectedLanguage = selectedLanguage,
                onLanguageChange = { viewModel.selectLanguage(it) }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("study_ai_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // Curriculum selector
            CurriculumSelectorCard(
                subjects = subjects,
                selectedSubject = selectedSubject,
                onSelectSubject = { viewModel.selectSubject(it) },
                selectedChapter = selectedChapter,
                onSelectChapter = { viewModel.selectChapter(it) },
                selectedTopic = selectedTopic,
                onSelectTopic = { viewModel.selectTopic(it) },
                selectedDifficulty = selectedDifficulty,
                onSelectDifficulty = { viewModel.selectDifficulty(it) }
            )

            // Tabs for all actions
            StudyActionTabBar(
                activeTab = activeTab,
                onTabSelected = { viewModel.selectTab(it) }
            )

            // Action View Switcher
            when (activeTab) {
                StudyActionTab.DOUBT -> {
                    DoubtView(
                        subjectName = selectedSubject.name,
                        chapterName = selectedChapter.name,
                        topicName = selectedTopic.name,
                        state = doubtState,
                        onAskDoubt = { question, notes -> viewModel.askDoubt(question, notes) }
                    )
                }
                StudyActionTab.EXPLAIN -> {
                    ExplainTopicView(
                        topicName = selectedTopic.name,
                        state = explainTopicState,
                        onExplain = { viewModel.explainCurrentTopic() }
                    )
                }
                StudyActionTab.MCQ -> {
                    InteractiveMCQView(
                        mcqState = mcqState,
                        selectedAnswers = mcqAnswers,
                        submittedQuestions = mcqSubmitted,
                        onSelectOption = { qIdx, optIdx -> viewModel.selectMCQOption(qIdx, optIdx) },
                        onSubmitAnswer = { qIdx -> viewModel.submitMCQAnswer(qIdx) },
                        onGenerateMore = { viewModel.generateMCQs(5) }
                    )
                }
                StudyActionTab.QUIZ -> {
                    InteractiveQuizView(
                        session = quizSession,
                        onStartQuiz = { viewModel.startQuiz(5) },
                        onSelectOption = { qIdx, optIdx -> viewModel.selectQuizOption(qIdx, optIdx) },
                        onNextQuestion = { viewModel.nextQuizQuestion() },
                        onPrevQuestion = { viewModel.prevQuizQuestion() },
                        onSubmitQuiz = { viewModel.submitQuiz() },
                        onResetQuiz = { viewModel.resetQuiz() }
                    )
                }
                StudyActionTab.PRACTICE -> {
                    PracticeQuestionsView(
                        topicName = selectedTopic.name,
                        state = practiceState,
                        onGenerate = { viewModel.generatePracticeQuestions() }
                    )
                }
                StudyActionTab.EXPLAIN_ANSWER -> {
                    ExplainAnswerView(
                        state = explainAnswerState,
                        onExplain = { q, ans -> viewModel.explainStudentAnswer(q, ans) }
                    )
                }
                StudyActionTab.STUDY_PLAN -> {
                    StudyPlanView(
                        state = studyPlanState,
                        planSaveMessage = planSaveMessage,
                        onGenerate = { date, hrs, str, weak, days ->
                            viewModel.generateStudyPlan(date, hrs, str, weak, days)
                        },
                        onSavePlan = { title, date, hrs, str, weak, days, content ->
                            viewModel.saveCurrentStudyPlan(title, date, hrs, str, weak, days, content)
                        }
                    )
                }
                StudyActionTab.SUMMARIZE -> {
                    SummarizeMaterialView(
                        state = summarizeState,
                        onSummarize = { notes -> viewModel.summarizeMaterial(notes) }
                    )
                }
                StudyActionTab.PROGRESS -> {
                    StudyProgressView(
                        summary = progressSummary,
                        isLoading = isLoadingProgress,
                        onPracticeWeakTopic = { weakTopic -> viewModel.selectWeakTopicForPractice(weakTopic) },
                        onRefresh = { viewModel.refreshProgress() }
                    )
                }
                StudyActionTab.SAVED_PLANS -> {
                    SavedPlansView(
                        plans = savedPlans,
                        onDeletePlan = { planId -> viewModel.deleteSavedPlan(planId) }
                    )
                }
                StudyActionTab.HISTORY -> {
                    StudyHistoryView(activities = studyActivities)
                }
            }
        }
    }
}
