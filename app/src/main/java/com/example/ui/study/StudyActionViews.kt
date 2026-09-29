package com.example.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.domain.study.AnswerEvaluation
import com.example.domain.study.DoubtSolution
import com.example.domain.study.StudyProgressSummary
import com.example.domain.study.WeakTopicItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DoubtView(
    subjectName: String,
    chapterName: String,
    topicName: String,
    state: StudyUiState<DoubtSolution>,
    onAskDoubt: (String, String?) -> Unit
) {
    var questionText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var showAttachmentField by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("doubt_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ask a Doubt • $subjectName",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$chapterName > $topicName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("Your Question / Doubt") },
                    placeholder = { Text("e.g. Why is √2 irrational? or Solve 2x + 3y = 7...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_doubt_question"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3
                )

                if (showAttachmentField) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Optional Context / Question Diagram description") },
                        placeholder = { Text("Paste textbook excerpt or describe the question figure...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_doubt_notes"),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showAttachmentField = !showAttachmentField },
                        modifier = Modifier.testTag("btn_toggle_attach")
                    ) {
                        Icon(imageVector = Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showAttachmentField) "Hide Context" else "Attach Context")
                    }

                    Button(
                        onClick = { onAskDoubt(questionText, if (notesText.isNotBlank()) notesText else null) },
                        enabled = questionText.isNotBlank() && state !is StudyUiState.Loading,
                        modifier = Modifier.testTag("btn_ask_doubt")
                    ) {
                        Text("Ask Study AI")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is StudyUiState.Idle -> {}
            is StudyUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.testTag("doubt_loading"))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Solving doubt step-by-step with verified curriculum concepts...")
                    }
                }
            }
            is StudyUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is StudyUiState.Success -> {
                val sol = state.data
                DoubtSolutionCards(sol)
            }
        }
    }
}

@Composable
fun DoubtSolutionCards(sol: DoubtSolution) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("doubt_solution_container"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Direct Answer
        SectionCard(title = "1. Direct Answer", icon = Icons.Default.CheckCircle, color = Color(0xFF16A34A)) {
            Text(text = sol.directAnswer, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }

        // 2. Simple Explanation
        SectionCard(title = "2. Simple Explanation", icon = Icons.Default.School, color = Color(0xFF0284C7)) {
            Text(text = sol.simpleExplanation, style = MaterialTheme.typography.bodyMedium)
        }

        // 3. Step-by-Step Solution
        SectionCard(title = "3. Step-by-Step Solution", icon = Icons.Default.Assignment, color = Color(0xFF6366F1)) {
            Text(text = sol.stepByStepSolution, style = MaterialTheme.typography.bodyMedium)
        }

        // 4. Important Concept
        SectionCard(title = "4. Important Concept", icon = Icons.Default.Lightbulb, color = Color(0xFFD97706)) {
            Text(text = sol.importantConcept, style = MaterialTheme.typography.bodyMedium)
        }

        // 5. Common Mistake
        SectionCard(title = "5. Common Mistake", icon = Icons.Default.Warning, color = Color(0xFFDC2626)) {
            Text(text = sol.commonMistake, style = MaterialTheme.typography.bodyMedium)
        }

        // 6. Similar Practice Question
        SectionCard(title = "6. Similar Practice Question", icon = Icons.Default.HelpOutline, color = Color(0xFF9333EA)) {
            Text(text = sol.similarPracticeQuestion, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun ExplainTopicView(
    topicName: String,
    state: StudyUiState<String>,
    onExplain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("explain_topic_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Topic: $topicName",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Get a comprehensive pedagogical breakdown covering definitions, real-life analogies, formulas, and high-yield scoring tips.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onExplain,
                    enabled = state !is StudyUiState.Loading,
                    modifier = Modifier.testTag("btn_explain_topic")
                ) {
                    Text("Explain This Topic")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is StudyUiState.Idle -> {}
            is StudyUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.testTag("explain_loading"))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Drafting structured concept explanation...")
                    }
                }
            }
            is StudyUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is StudyUiState.Success -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explain_topic_result"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = state.data,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PracticeQuestionsView(
    topicName: String,
    state: StudyUiState<String>,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("practice_questions_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Generate Diverse Practice Questions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Generates Short answer, Long answer, Numerical, Assertion-Reason, Case-based, Competency-based, and Application-based questions with solutions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onGenerate,
                    enabled = state !is StudyUiState.Loading,
                    modifier = Modifier.testTag("btn_generate_practice")
                ) {
                    Text("Generate Practice Sheet")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is StudyUiState.Idle -> {}
            is StudyUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.testTag("practice_loading"))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Drafting curriculum-aligned practice questions...")
                    }
                }
            }
            is StudyUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is StudyUiState.Success -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("practice_questions_result"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = state.data,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExplainAnswerView(
    state: StudyUiState<AnswerEvaluation>,
    onExplain: (String, String) -> Unit
) {
    var questionInput by remember { mutableStateOf("") }
    var answerInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("explain_answer_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Explain an Answer / Solution Review",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Paste your answer to receive supportive, constructive pedagogical feedback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = questionInput,
                    onValueChange = { questionInput = it },
                    label = { Text("Original Question (Optional)") },
                    placeholder = { Text("e.g. State Ohm's law and derive V = IR") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_eval_question"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = answerInput,
                    onValueChange = { answerInput = it },
                    label = { Text("Your Answer / Solution") },
                    placeholder = { Text("Paste your solution here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_eval_answer"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 4
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onExplain(questionInput, answerInput) },
                    enabled = answerInput.isNotBlank() && state !is StudyUiState.Loading,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("btn_analyze_answer")
                ) {
                    Text("Analyze My Answer")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is StudyUiState.Idle -> {}
            is StudyUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.testTag("eval_loading"))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Analyzing answer for accuracy, missing concepts & presentation...")
                    }
                }
            }
            is StudyUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is StudyUiState.Success -> {
                val eval = state.data
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eval_result_container"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionCard(title = "1. What is Correct", icon = Icons.Default.CheckCircle, color = Color(0xFF16A34A)) {
                        Text(text = eval.whatIsCorrect)
                    }
                    SectionCard(title = "2. What Needs Improvement", icon = Icons.Default.Warning, color = Color(0xFFD97706)) {
                        Text(text = eval.whatNeedsImprovement)
                    }
                    SectionCard(title = "3. Missing Concepts", icon = Icons.Default.Lightbulb, color = Color(0xFF2563EB)) {
                        Text(text = eval.missingConcepts)
                    }
                    SectionCard(title = "4. Step-by-Step Correction", icon = Icons.Default.Assignment, color = Color(0xFF7C3AED)) {
                        Text(text = eval.stepByStepCorrection)
                    }
                    SectionCard(title = "5. A Better Way to Approach", icon = Icons.Default.School, color = Color(0xFF0D9488)) {
                        Text(text = eval.betterApproach)
                    }
                }
            }
        }
    }
}

@Composable
fun StudyPlanView(
    state: StudyUiState<String>,
    planSaveMessage: String?,
    onGenerate: (examDate: String, hours: String, strong: String, weak: String, days: String) -> Unit,
    onSavePlan: (title: String, examDate: String, hours: String, strong: String, weak: String, days: String, content: String) -> Unit
) {
    var examDate by remember { mutableStateOf("In 30 days") }
    var hoursPerDay by remember { mutableStateOf("2.5 hours") }
    var strongSubjects by remember { mutableStateOf("Science, English") }
    var weakTopics by remember { mutableStateOf("Trigonometry, Electricity") }
    var preferredDays by remember { mutableStateOf("All days (Monday - Sunday)") }
    var planTitle by remember { mutableStateOf("Board Exam Sprint Plan") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("study_plan_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Personalized Study Plan Generator",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Generate a realistic daily study routine aligned with CBSE syllabus. Does not claim guaranteed exam marks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = examDate,
                    onValueChange = { examDate = it },
                    label = { Text("Exam Date / Timeline") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_plan_date"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = hoursPerDay,
                    onValueChange = { hoursPerDay = it },
                    label = { Text("Available Study Time per Day") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_plan_hours"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = strongSubjects,
                    onValueChange = { strongSubjects = it },
                    label = { Text("Strong Subjects") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_plan_strong"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = weakTopics,
                    onValueChange = { weakTopics = it },
                    label = { Text("Topics Needing More Practice") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_plan_weak"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = preferredDays,
                    onValueChange = { preferredDays = it },
                    label = { Text("Preferred Study Days") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_plan_days"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onGenerate(examDate, hoursPerDay, strongSubjects, weakTopics, preferredDays) },
                    enabled = state !is StudyUiState.Loading,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("btn_generate_plan")
                ) {
                    Text("Generate Study Plan")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!planSaveMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = planSaveMessage,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        when (state) {
            is StudyUiState.Idle -> {}
            is StudyUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.testTag("plan_loading"))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Constructing personalized timetable and revision blocks...")
                    }
                }
            }
            is StudyUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is StudyUiState.Success -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("study_plan_result_card"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Generated Plan",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Button(
                                onClick = {
                                    onSavePlan(planTitle, examDate, hoursPerDay, strongSubjects, weakTopics, preferredDays, state.data)
                                },
                                modifier = Modifier.testTag("btn_save_plan")
                            ) {
                                Text("Save Plan")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.data,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummarizeMaterialView(
    state: StudyUiState<String>,
    onSummarize: (String) -> Unit
) {
    var materialText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("summarize_material_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Summarize Study Notes / Material",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Paste chapter excerpts, class notes, or revision handouts to generate executive summaries, formulas, and checklists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = materialText,
                    onValueChange = { materialText = it },
                    label = { Text("Study Material / Notes") },
                    placeholder = { Text("Paste your notes or text here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_summary_material"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 4
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onSummarize(materialText) },
                    enabled = materialText.isNotBlank() && state !is StudyUiState.Loading,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("btn_summarize_material")
                ) {
                    Text("Summarize Notes")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is StudyUiState.Idle -> {}
            is StudyUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.testTag("summary_loading"))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Extracting core concepts and high-yield points...")
                    }
                }
            }
            is StudyUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is StudyUiState.Success -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("summary_result_card"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = state.data,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudyProgressView(
    summary: StudyProgressSummary?,
    isLoading: Boolean,
    onPracticeWeakTopic: (WeakTopicItem) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("study_progress_view")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Study Progress & Analytics",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            IconButton(onClick = onRefresh, modifier = Modifier.testTag("btn_refresh_progress")) {
                Icon(imageVector = Icons.Default.Bookmark, contentDescription = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (summary == null || (summary.questionsAttempted == 0 && summary.studySessions == 0)) {
            // Requirement 9: If no data, show "No study activity yet."
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("no_study_activity_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No study activity yet.",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Take a quiz or ask a doubt to start tracking your real accuracy and weak topics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Real stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(title = "Questions Attempted", value = "${summary.questionsAttempted}", modifier = Modifier.weight(1f))
                StatCard(title = "Accuracy", value = "${summary.accuracyPercentage.toInt()}%", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(title = "Questions Correct", value = "${summary.questionsCorrect}", modifier = Modifier.weight(1f))
                StatCard(title = "Quizzes Completed", value = "${summary.quizzesCompleted}", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(title = "Study Sessions", value = "${summary.studySessions}", modifier = Modifier.weight(1f))
                StatCard(title = "Topics Practiced", value = "${summary.topicsPracticed}", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Requirement 10: WEAK TOPICS ("Topics to Practice")
            Text(
                text = "Topics to Practice",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (summary.weakTopics.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Great job! All practiced topics show solid accuracy (≥75%). Take more quizzes to uncover specific areas to improve.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                summary.weakTopics.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("weak_topic_${item.topic.replace(" ", "_")}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.topic,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "${item.subject} • ${item.chapter}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Accuracy: ${item.accuracyPercentage.toInt()}% (${item.totalCorrect}/${item.totalAttempted})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Button(
                                onClick = { onPracticeWeakTopic(item) },
                                modifier = Modifier.testTag("btn_practice_weak_${item.topic.take(8)}")
                            ) {
                                Text("Practice")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SavedPlansView(
    plans: List<StudyPlanEntity>,
    onDeletePlan: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("saved_plans_view")
    ) {
        Text(
            text = "Saved Study Plans (${plans.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (plans.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "No saved plans yet. Generate a study plan and tap 'Save Plan' to store it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            plans.forEach { plan ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("saved_plan_card_${plan.id}"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = plan.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeletePlan(plan.id) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Text(
                            text = "Subjects: ${plan.subjects} • Timeline: ${plan.examDate} • ${plan.hoursPerDay}/day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Saved on ${dateFormat.format(Date(plan.createdAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = plan.planContent,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudyHistoryView(
    activities: List<StudyActivityEntity>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("study_history_view")
    ) {
        Text(
            text = "Study History & Recent Activities (${activities.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (activities.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "No study history records yet. Doubts, quizzes, explanations, and practice sessions will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            activities.forEach { act ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = act.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = act.activityType.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (act.subject.isNotBlank()) {
                            Text(
                                text = "${act.subject} • ${act.topic}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = dateFormat.format(Date(act.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
