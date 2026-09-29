package com.example.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.repository.NexaRepository
import com.example.domain.study.AnswerEvaluation
import com.example.domain.study.CurriculumChapter
import com.example.domain.study.CurriculumData
import com.example.domain.study.CurriculumSubject
import com.example.domain.study.CurriculumTopic
import com.example.domain.study.DoubtSolution
import com.example.domain.study.StudyMCQ
import com.example.domain.study.StudyProgressSummary
import com.example.domain.study.WeakTopicItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class StudyActionTab(val label: String, val iconName: String) {
    DOUBT("Ask Doubt", "HelpOutline"),
    EXPLAIN("Explain Topic", "School"),
    MCQ("MCQ Practice", "FormatListBulleted"),
    QUIZ("Quiz Mode", "Quiz"),
    PRACTICE("Practice Questions", "Assignment"),
    EXPLAIN_ANSWER("Explain Answer", "FactCheck"),
    STUDY_PLAN("Study Plan", "CalendarMonth"),
    SUMMARIZE("Summarize Notes", "Summarize"),
    PROGRESS("Progress & Weak Topics", "Analytics"),
    SAVED_PLANS("Saved Plans", "Bookmark"),
    HISTORY("Study History", "History")
}

sealed class StudyUiState<out T> {
    data object Idle : StudyUiState<Nothing>()
    data object Loading : StudyUiState<Nothing>()
    data class Success<out T>(val data: T) : StudyUiState<T>()
    data class Error(val message: String) : StudyUiState<Nothing>()
}

data class QuizSessionState(
    val status: QuizStatus = QuizStatus.NOT_STARTED,
    val questions: List<StudyMCQ> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val selectedAnswers: Map<Int, Int> = emptyMap(), // questionIndex -> optionIndex
    val elapsedSeconds: Int = 0,
    val lastAttempt: QuizAttemptEntity? = null
)

enum class QuizStatus {
    NOT_STARTED,
    LOADING,
    IN_PROGRESS,
    COMPLETED
}

class StudyViewModel(
    private val repository: NexaRepository
) : ViewModel() {

    private val studyRepo = repository.studyRepository

    // Curriculum Selection State
    val availableClasses = CurriculumData.AVAILABLE_CLASSES
    private val _selectedClass = MutableStateFlow("Class 10")
    val selectedClass: StateFlow<String> = _selectedClass.asStateFlow()

    private val _subjects = MutableStateFlow(CurriculumData.CBSE_CLASS_10.subjects)
    val subjects: StateFlow<List<CurriculumSubject>> = _subjects.asStateFlow()

    private val _selectedSubject = MutableStateFlow(_subjects.value.first())
    val selectedSubject: StateFlow<CurriculumSubject> = _selectedSubject.asStateFlow()

    private val _selectedChapter = MutableStateFlow(_selectedSubject.value.chapters.first())
    val selectedChapter: StateFlow<CurriculumChapter> = _selectedChapter.asStateFlow()

    private val _selectedTopic = MutableStateFlow(_selectedChapter.value.topics.first())
    val selectedTopic: StateFlow<CurriculumTopic> = _selectedTopic.asStateFlow()

    private val _selectedDifficulty = MutableStateFlow("Medium")
    val selectedDifficulty: StateFlow<String> = _selectedDifficulty.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("English")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _activeTab = MutableStateFlow(StudyActionTab.DOUBT)
    val activeTab: StateFlow<StudyActionTab> = _activeTab.asStateFlow()

    // Action States
    private val _doubtState = MutableStateFlow<StudyUiState<DoubtSolution>>(StudyUiState.Idle)
    val doubtState: StateFlow<StudyUiState<DoubtSolution>> = _doubtState.asStateFlow()

    private val _explainTopicState = MutableStateFlow<StudyUiState<String>>(StudyUiState.Idle)
    val explainTopicState: StateFlow<StudyUiState<String>> = _explainTopicState.asStateFlow()

    private val _mcqState = MutableStateFlow<StudyUiState<List<StudyMCQ>>>(StudyUiState.Idle)
    val mcqState: StateFlow<StudyUiState<List<StudyMCQ>>> = _mcqState.asStateFlow()

    // MCQ interactive answering
    private val _mcqAnswers = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val mcqAnswers: StateFlow<Map<Int, Int>> = _mcqAnswers.asStateFlow()

    private val _mcqSubmitted = MutableStateFlow<Set<Int>>(emptySet())
    val mcqSubmitted: StateFlow<Set<Int>> = _mcqSubmitted.asStateFlow()

    // Interactive Quiz Session
    private val _quizSession = MutableStateFlow(QuizSessionState())
    val quizSession: StateFlow<QuizSessionState> = _quizSession.asStateFlow()
    private var quizTimerJob: Job? = null

    // Practice Questions
    private val _practiceState = MutableStateFlow<StudyUiState<String>>(StudyUiState.Idle)
    val practiceState: StateFlow<StudyUiState<String>> = _practiceState.asStateFlow()

    // Explain Answer
    private val _explainAnswerState = MutableStateFlow<StudyUiState<AnswerEvaluation>>(StudyUiState.Idle)
    val explainAnswerState: StateFlow<StudyUiState<AnswerEvaluation>> = _explainAnswerState.asStateFlow()

    // Study Plan
    private val _studyPlanState = MutableStateFlow<StudyUiState<String>>(StudyUiState.Idle)
    val studyPlanState: StateFlow<StudyUiState<String>> = _studyPlanState.asStateFlow()
    private val _planSaveMessage = MutableStateFlow<String?>(null)
    val planSaveMessage: StateFlow<String?> = _planSaveMessage.asStateFlow()

    // Summarize Material
    private val _summarizeState = MutableStateFlow<StudyUiState<String>>(StudyUiState.Idle)
    val summarizeState: StateFlow<StudyUiState<String>> = _summarizeState.asStateFlow()

    // Study Progress & Weak Topics
    private val _progressSummary = MutableStateFlow<StudyProgressSummary?>(null)
    val progressSummary: StateFlow<StudyProgressSummary?> = _progressSummary.asStateFlow()
    private val _isLoadingProgress = MutableStateFlow(false)
    val isLoadingProgress: StateFlow<Boolean> = _isLoadingProgress.asStateFlow()

    // Real-time Database Observables
    val studyActivities: StateFlow<List<StudyActivityEntity>> = repository.currentUser
        .flatMapLatest { user ->
            if (user != null) studyRepo.getStudyActivities(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quizAttempts: StateFlow<List<QuizAttemptEntity>> = repository.currentUser
        .flatMapLatest { user ->
            if (user != null) studyRepo.getQuizAttempts(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedPlans: StateFlow<List<StudyPlanEntity>> = repository.currentUser
        .flatMapLatest { user ->
            if (user != null) studyRepo.getSavedPlans(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshProgress()
    }

    // -------------------------------------------------------------
    // NAVIGATION & SELECTION HANDLERS
    // -------------------------------------------------------------

    fun selectTab(tab: StudyActionTab) {
        _activeTab.value = tab
        if (tab == StudyActionTab.PROGRESS) {
            refreshProgress()
        }
    }

    fun selectSubject(subject: CurriculumSubject) {
        _selectedSubject.value = subject
        val firstCh = subject.chapters.firstOrNull()
        if (firstCh != null) {
            _selectedChapter.value = firstCh
            val firstTop = firstCh.topics.firstOrNull()
            if (firstTop != null) {
                _selectedTopic.value = firstTop
            }
        }
    }

    fun selectChapter(chapter: CurriculumChapter) {
        _selectedChapter.value = chapter
        val firstTop = chapter.topics.firstOrNull()
        if (firstTop != null) {
            _selectedTopic.value = firstTop
        }
    }

    fun selectTopic(topic: CurriculumTopic) {
        _selectedTopic.value = topic
    }

    fun selectDifficulty(difficulty: String) {
        _selectedDifficulty.value = difficulty
    }

    fun selectLanguage(language: String) {
        _selectedLanguage.value = language
    }

    // -------------------------------------------------------------
    // 1. ASK A DOUBT
    // -------------------------------------------------------------

    fun askDoubt(question: String, attachedNotes: String? = null) {
        if (question.isBlank()) {
            _doubtState.value = StudyUiState.Error("Please enter your question.")
            return
        }
        viewModelScope.launch {
            _doubtState.value = StudyUiState.Loading
            val result = studyRepo.askDoubt(
                subject = _selectedSubject.value.name,
                chapter = _selectedChapter.value.name,
                topic = _selectedTopic.value.name,
                studentQuestion = question,
                attachedNotes = attachedNotes,
                language = _selectedLanguage.value
            )
            _doubtState.value = result.fold(
                onSuccess = { StudyUiState.Success(it) },
                onFailure = { StudyUiState.Error(it.localizedMessage ?: "Failed to get doubt answer.") }
            )
            refreshProgress()
        }
    }

    // -------------------------------------------------------------
    // 2. EXPLAIN A TOPIC
    // -------------------------------------------------------------

    fun explainCurrentTopic() {
        viewModelScope.launch {
            _explainTopicState.value = StudyUiState.Loading
            val result = studyRepo.explainTopic(
                subject = _selectedSubject.value.name,
                chapter = _selectedChapter.value.name,
                topic = _selectedTopic.value.name,
                difficulty = _selectedDifficulty.value,
                language = _selectedLanguage.value
            )
            _explainTopicState.value = result.fold(
                onSuccess = { StudyUiState.Success(it) },
                onFailure = { StudyUiState.Error(it.localizedMessage ?: "Failed to explain topic.") }
            )
            refreshProgress()
        }
    }

    // -------------------------------------------------------------
    // 3. GENERATE MCQS & INTERACTIVE SCORING
    // -------------------------------------------------------------

    fun generateMCQs(count: Int = 5) {
        viewModelScope.launch {
            _mcqState.value = StudyUiState.Loading
            _mcqAnswers.value = emptyMap()
            _mcqSubmitted.value = emptySet()
            val result = studyRepo.generateMCQs(
                subject = _selectedSubject.value.name,
                chapter = _selectedChapter.value.name,
                topic = _selectedTopic.value.name,
                count = count,
                difficulty = _selectedDifficulty.value,
                language = _selectedLanguage.value
            )
            _mcqState.value = result.fold(
                onSuccess = { StudyUiState.Success(it) },
                onFailure = { StudyUiState.Error(it.localizedMessage ?: "Failed to generate MCQs.") }
            )
            refreshProgress()
        }
    }

    fun selectMCQOption(questionIndex: Int, optionIndex: Int) {
        if (questionIndex !in _mcqSubmitted.value) {
            _mcqAnswers.value = _mcqAnswers.value + (questionIndex to optionIndex)
        }
    }

    fun submitMCQAnswer(questionIndex: Int) {
        if (_mcqAnswers.value.containsKey(questionIndex)) {
            _mcqSubmitted.value = _mcqSubmitted.value + questionIndex
        }
    }

    // -------------------------------------------------------------
    // 4. INTERACTIVE QUIZ MODE
    // -------------------------------------------------------------

    fun startQuiz(count: Int = 5) {
        quizTimerJob?.cancel()
        _quizSession.value = QuizSessionState(status = QuizStatus.LOADING)
        viewModelScope.launch {
            val result = studyRepo.generateQuiz(
                subject = _selectedSubject.value.name,
                chapter = _selectedChapter.value.name,
                topic = _selectedTopic.value.name,
                count = count,
                difficulty = _selectedDifficulty.value,
                language = _selectedLanguage.value
            )
            result.fold(
                onSuccess = { mcqs ->
                    _quizSession.value = QuizSessionState(
                        status = QuizStatus.IN_PROGRESS,
                        questions = mcqs,
                        currentQuestionIndex = 0,
                        selectedAnswers = emptyMap(),
                        elapsedSeconds = 0
                    )
                    startQuizTimer()
                },
                onFailure = {
                    _quizSession.value = QuizSessionState(status = QuizStatus.NOT_STARTED)
                }
            )
        }
    }

    private fun startQuizTimer() {
        quizTimerJob?.cancel()
        quizTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _quizSession.value = _quizSession.value.copy(
                    elapsedSeconds = _quizSession.value.elapsedSeconds + 1
                )
            }
        }
    }

    fun selectQuizOption(questionIndex: Int, optionIndex: Int) {
        val curr = _quizSession.value
        if (curr.status == QuizStatus.IN_PROGRESS) {
            _quizSession.value = curr.copy(
                selectedAnswers = curr.selectedAnswers + (questionIndex to optionIndex)
            )
        }
    }

    fun nextQuizQuestion() {
        val curr = _quizSession.value
        if (curr.currentQuestionIndex < curr.questions.size - 1) {
            _quizSession.value = curr.copy(currentQuestionIndex = curr.currentQuestionIndex + 1)
        }
    }

    fun prevQuizQuestion() {
        val curr = _quizSession.value
        if (curr.currentQuestionIndex > 0) {
            _quizSession.value = curr.copy(currentQuestionIndex = curr.currentQuestionIndex - 1)
        }
    }

    fun submitQuiz() {
        quizTimerJob?.cancel()
        val curr = _quizSession.value
        if (curr.status != QuizStatus.IN_PROGRESS) return

        viewModelScope.launch {
            var correctCount = 0
            curr.questions.forEachIndexed { idx, q ->
                val selectedOptIdx = curr.selectedAnswers[idx]
                if (selectedOptIdx != null && selectedOptIdx == q.correctIndex) {
                    correctCount++
                }
            }

            val result = studyRepo.submitQuizAttempt(
                subject = _selectedSubject.value.name,
                chapter = _selectedChapter.value.name,
                topic = _selectedTopic.value.name,
                totalQuestions = curr.questions.size,
                correctAnswers = correctCount,
                timeTakenSeconds = curr.elapsedSeconds,
                questions = curr.questions,
                selectedAnswers = curr.selectedAnswers
            )

            result.fold(
                onSuccess = { attempt ->
                    _quizSession.value = curr.copy(
                        status = QuizStatus.COMPLETED,
                        lastAttempt = attempt
                    )
                    refreshProgress()
                },
                onFailure = {
                    _quizSession.value = curr.copy(status = QuizStatus.COMPLETED)
                }
            )
        }
    }

    fun resetQuiz() {
        quizTimerJob?.cancel()
        _quizSession.value = QuizSessionState(status = QuizStatus.NOT_STARTED)
    }

    // -------------------------------------------------------------
    // 5. PRACTICE QUESTIONS
    // -------------------------------------------------------------

    fun generatePracticeQuestions() {
        viewModelScope.launch {
            _practiceState.value = StudyUiState.Loading
            val result = studyRepo.generatePracticeQuestions(
                subject = _selectedSubject.value.name,
                chapter = _selectedChapter.value.name,
                topic = _selectedTopic.value.name,
                difficulty = _selectedDifficulty.value,
                language = _selectedLanguage.value
            )
            _practiceState.value = result.fold(
                onSuccess = { StudyUiState.Success(it) },
                onFailure = { StudyUiState.Error(it.localizedMessage ?: "Failed to generate practice questions.") }
            )
            refreshProgress()
        }
    }

    // -------------------------------------------------------------
    // 6. EXPLAIN AN ANSWER
    // -------------------------------------------------------------

    fun explainStudentAnswer(question: String, studentAnswer: String) {
        if (studentAnswer.isBlank()) {
            _explainAnswerState.value = StudyUiState.Error("Please paste or type your answer.")
            return
        }
        viewModelScope.launch {
            _explainAnswerState.value = StudyUiState.Loading
            val result = studyRepo.explainAnswer(
                question = question,
                studentAnswer = studentAnswer,
                subject = _selectedSubject.value.name,
                chapter = _selectedChapter.value.name,
                topic = _selectedTopic.value.name,
                language = _selectedLanguage.value
            )
            _explainAnswerState.value = result.fold(
                onSuccess = { StudyUiState.Success(it) },
                onFailure = { StudyUiState.Error(it.localizedMessage ?: "Failed to evaluate answer.") }
            )
            refreshProgress()
        }
    }

    // -------------------------------------------------------------
    // 7. STUDY PLAN
    // -------------------------------------------------------------

    fun generateStudyPlan(
        examDate: String,
        availableHoursPerDay: String,
        strongSubjects: String,
        weakTopics: String,
        preferredDays: String
    ) {
        viewModelScope.launch {
            _studyPlanState.value = StudyUiState.Loading
            _planSaveMessage.value = null
            val result = studyRepo.generateStudyPlan(
                classLevel = _selectedClass.value,
                subjects = listOf(_selectedSubject.value.name),
                examDate = examDate,
                availableTimePerDay = availableHoursPerDay,
                strongSubjects = strongSubjects,
                weakTopics = weakTopics,
                preferredDays = preferredDays,
                language = _selectedLanguage.value
            )
            _studyPlanState.value = result.fold(
                onSuccess = { StudyUiState.Success(it) },
                onFailure = { StudyUiState.Error(it.localizedMessage ?: "Failed to generate study plan.") }
            )
        }
    }

    fun saveCurrentStudyPlan(
        title: String,
        examDate: String,
        hoursPerDay: String,
        preferredDays: String,
        strongSubjects: String,
        weakTopics: String,
        planContent: String
    ) {
        viewModelScope.launch {
            val result = studyRepo.saveStudyPlan(
                title = title.ifBlank { "CBSE Class 10 Revision Plan" },
                classLevel = _selectedClass.value,
                subjects = _selectedSubject.value.name,
                examDate = examDate,
                hoursPerDay = hoursPerDay,
                preferredDays = preferredDays,
                strongSubjects = strongSubjects,
                weakTopics = weakTopics,
                planContent = planContent
            )
            result.fold(
                onSuccess = {
                    _planSaveMessage.value = "Study plan successfully saved!"
                    refreshProgress()
                },
                onFailure = {
                    _planSaveMessage.value = "Failed to save: ${it.localizedMessage}"
                }
            )
        }
    }

    fun deleteSavedPlan(planId: String) {
        val user = repository.currentUser.value ?: return
        viewModelScope.launch {
            studyRepo.deleteStudyPlan(planId, user.id)
        }
    }

    // -------------------------------------------------------------
    // 8. SUMMARIZE MATERIAL
    // -------------------------------------------------------------

    fun summarizeMaterial(notes: String) {
        if (notes.isBlank()) {
            _summarizeState.value = StudyUiState.Error("Please enter notes or study material to summarize.")
            return
        }
        viewModelScope.launch {
            _summarizeState.value = StudyUiState.Loading
            val result = studyRepo.summarizeMaterial(
                material = notes,
                subject = _selectedSubject.value.name,
                chapter = _selectedChapter.value.name,
                topic = _selectedTopic.value.name,
                language = _selectedLanguage.value
            )
            _summarizeState.value = result.fold(
                onSuccess = { StudyUiState.Success(it) },
                onFailure = { StudyUiState.Error(it.localizedMessage ?: "Failed to summarize material.") }
            )
            refreshProgress()
        }
    }

    // -------------------------------------------------------------
    // 9. PROGRESS & WEAK TOPICS
    // -------------------------------------------------------------

    fun refreshProgress() {
        val user = repository.currentUser.value ?: return
        viewModelScope.launch {
            _isLoadingProgress.value = true
            _progressSummary.value = studyRepo.getStudyProgress(user.id)
            _isLoadingProgress.value = false
        }
    }

    fun selectWeakTopicForPractice(weakTopic: WeakTopicItem) {
        val foundSubj = _subjects.value.firstOrNull { it.name.equals(weakTopic.subject, ignoreCase = true) }
        if (foundSubj != null) {
            _selectedSubject.value = foundSubj
            val foundCh = foundSubj.chapters.firstOrNull { it.name.equals(weakTopic.chapter, ignoreCase = true) }
            if (foundCh != null) {
                _selectedChapter.value = foundCh
                val foundTop = foundCh.topics.firstOrNull { it.name.equals(weakTopic.topic, ignoreCase = true) }
                    ?: CurriculumTopic("custom", weakTopic.topic)
                _selectedTopic.value = foundTop
            }
        }
        // Jump to MCQ or Quiz
        selectTab(StudyActionTab.MCQ)
        generateMCQs(5)
    }

    override fun onCleared() {
        super.onCleared()
        quizTimerJob?.cancel()
    }
}
