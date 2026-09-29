package com.example.domain.study

data class StudyMCQ(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val correctAnswer: String,
    val explanation: String,
    val difficulty: String,
    val topic: String
)

data class PracticeQuestionItem(
    val id: String,
    val questionType: String, // "Short Answer", "Long Answer", "Numerical", "Assertion-Reason", "Case-based", "Competency-based", "Application-based"
    val topic: String,
    val question: String,
    val solutionGuide: String,
    val marksOrDifficulty: String
)

data class DoubtSolution(
    val directAnswer: String,
    val simpleExplanation: String,
    val stepByStepSolution: String,
    val importantConcept: String,
    val commonMistake: String,
    val similarPracticeQuestion: String,
    val rawText: String = ""
)

data class AnswerEvaluation(
    val whatIsCorrect: String,
    val whatNeedsImprovement: String,
    val missingConcepts: String,
    val stepByStepCorrection: String,
    val betterApproach: String,
    val rawText: String = ""
)

data class StudyPlanDayTask(
    val dayOrDate: String,
    val subject: String,
    val topic: String,
    val practiceActivity: String,
    val revisionActivity: String,
    val suggestedDuration: String
)

data class StudyPlanResult(
    val title: String,
    val overview: String,
    val tasks: List<StudyPlanDayTask>,
    val rawMarkdown: String
)

data class WeakTopicItem(
    val subject: String,
    val chapter: String,
    val topic: String,
    val totalAttempted: Int,
    val totalCorrect: Int,
    val accuracyPercentage: Float
)

data class StudyProgressSummary(
    val questionsAttempted: Int,
    val questionsCorrect: Int,
    val accuracyPercentage: Float,
    val quizzesCompleted: Int,
    val studySessions: Int,
    val topicsPracticed: Int,
    val weakTopics: List<WeakTopicItem>
)
