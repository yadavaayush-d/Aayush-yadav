package com.example.ui.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.LanguageProjectEntity
import com.example.data.local.entity.SavedLanguageContentEntity
import com.example.domain.language.ConversationPracticeTurnResult
import com.example.domain.language.ExplainTextResult
import com.example.domain.language.LanguageOptions
import com.example.domain.language.LanguagePracticeResult
import com.example.domain.language.LanguageSummaryResult
import com.example.domain.language.PracticeExerciseItem
import com.example.domain.language.PracticeProgressStats

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummarizeToolView(
    state: LanguageUiState<LanguageSummaryResult>,
    onSummarize: (text: String, outputLanguage: String, length: String) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (LanguageSummaryResult) -> Unit,
    onRetry: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var outputLanguage by remember { mutableStateOf("English") }
    var summaryLength by remember { mutableStateOf("Short") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Multilingual Summarize",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Generate an original summary in your chosen language based strictly on your text.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LanguageDropdownSelector(
            label = "Output Language",
            selectedValue = outputLanguage,
            options = LanguageOptions.SUPPORTED_LANGUAGES,
            onValueSelected = { outputLanguage = it },
            testTag = "summarize_lang_selector"
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageOptions.SUMMARY_LENGTHS.forEach { len ->
                FilterChip(
                    selected = summaryLength == len,
                    onClick = { summaryLength = len },
                    label = { Text(len) }
                )
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Text to summarize") },
            placeholder = { Text("Paste paragraph or article text to summarize...") },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("summarize_input_field")
        )

        Button(
            onClick = { onSummarize(inputText, outputLanguage, summaryLength) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("summarize_submit_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Summarize ($summaryLength in $outputLanguage)")
        }

        when (state) {
            is LanguageUiState.Idle -> Unit
            is LanguageUiState.Loading -> LanguageLoadingCard("Generating $summaryLength summary in $outputLanguage...")
            is LanguageUiState.Error -> LanguageErrorCard(state.errorCode, state.message, onRetry)
            is LanguageUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("summarize_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${res.summaryLength} Summary • ${res.outputLanguage}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = res.summaryText, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onCopy(res.summaryText) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy")
                            }
                            OutlinedButton(onClick = { onSave(res) }) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExplainTextToolView(
    state: LanguageUiState<ExplainTextResult>,
    onExplain: (text: String, language: String, level: String) -> Unit,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var explanationLanguage by remember { mutableStateOf("English") }
    var explanationLevel by remember { mutableStateOf("Simple") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Explain This Text",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Break down difficult words, sentences, or concepts in understandable language.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LanguageDropdownSelector(
            label = "Explanation Language",
            selectedValue = explanationLanguage,
            options = LanguageOptions.SUPPORTED_LANGUAGES,
            onValueSelected = { explanationLanguage = it },
            testTag = "explain_lang_selector"
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageOptions.EXPLANATION_LEVELS.forEach { lvl ->
                FilterChip(
                    selected = explanationLevel == lvl,
                    onClick = { explanationLevel = lvl },
                    label = { Text(lvl) }
                )
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Text to explain") },
            placeholder = { Text("Paste a complex sentence, paragraph, or concept...") },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("explain_input_field")
        )

        Button(
            onClick = { onExplain(inputText, explanationLanguage, explanationLevel) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("explain_submit_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Explain This Text ($explanationLevel)")
        }

        when (state) {
            is LanguageUiState.Idle -> Unit
            is LanguageUiState.Loading -> LanguageLoadingCard("Explaining text in $explanationLanguage ($explanationLevel)...")
            is LanguageUiState.Error -> LanguageErrorCard(state.errorCode, state.message, onRetry)
            is LanguageUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explain_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Overall Meaning (${res.explanationLevel} • ${res.explanationLanguage})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = res.overallMeaning, style = MaterialTheme.typography.bodyLarge)

                        Text(
                            text = "Key Words & Concepts",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        res.keyWordsAndConcepts.forEach { word ->
                            Text("• $word", style = MaterialTheme.typography.bodySmall)
                        }

                        Text(
                            text = "Step-by-Step Breakdown",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(text = res.stepByStepExplanation, style = MaterialTheme.typography.bodyMedium)

                        OutlinedButton(onClick = { onCopy(res.rawOutput) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Explanation")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePracticeToolView(
    practiceState: LanguageUiState<LanguagePracticeResult>,
    practiceStats: PracticeProgressStats,
    checkedExercises: Map<String, Boolean>,
    conversationTurns: List<ConversationPracticeTurnResult>,
    conversationLoading: Boolean,
    conversationError: String?,
    onGeneratePractice: (language: String, level: String, topic: String) -> Unit,
    onCheckAnswer: (sessionId: String, exercise: PracticeExerciseItem, userAnswer: String) -> Unit,
    onSendConversationTurn: (language: String, level: String, topic: String, message: String) -> Unit,
    onRetry: () -> Unit
) {
    var languageToLearn by remember { mutableStateOf("English") }
    var selectedLevel by remember { mutableStateOf("Beginner") }
    var topic by remember { mutableStateOf("Daily Greetings & Introductions") }
    var userConversationInput by remember { mutableStateOf("") }
    val userExerciseAnswers = remember { mutableStateMapOf<String, String>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Language Practice & Conversation",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        // Tracked stats card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sessions: ${practiceStats.totalSessions}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Attempted: ${practiceStats.totalQuestionsAttempted}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Correct: ${practiceStats.totalCorrectAnswers}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        LanguageDropdownSelector(
            label = "Language to Learn",
            selectedValue = languageToLearn,
            options = LanguageOptions.SUPPORTED_LANGUAGES,
            onValueSelected = { languageToLearn = it },
            testTag = "practice_lang_selector"
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageOptions.PRACTICE_LEVELS.forEach { lvl ->
                FilterChip(
                    selected = selectedLevel == lvl,
                    onClick = { selectedLevel = lvl },
                    label = { Text(lvl) }
                )
            }
        }

        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("Practice Topic") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("practice_topic_field")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onGeneratePractice(languageToLearn, selectedLevel, topic) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("practice_generate_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Generate Exercises")
            }

            OutlinedButton(
                onClick = { onSendConversationTurn(languageToLearn, selectedLevel, topic, "") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("conversation_start_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Learn $languageToLearn Chat")
            }
        }

        // Practice Exercises Result
        when (practiceState) {
            is LanguageUiState.Idle -> Unit
            is LanguageUiState.Loading -> LanguageLoadingCard("Creating $selectedLevel $languageToLearn exercises...")
            is LanguageUiState.Error -> LanguageErrorCard(practiceState.errorCode, practiceState.message, onRetry)
            is LanguageUiState.Success -> {
                val res = practiceState.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("practice_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Vocabulary Practice (${res.languageToLearn} • ${res.level})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        res.vocabularyList.forEach { item ->
                            Text("• $item", style = MaterialTheme.typography.bodySmall)
                        }

                        Text(
                            text = "Dialogue Preview",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(text = res.conversationStarter, style = MaterialTheme.typography.bodySmall)

                        Text(
                            text = "Interactive Practice Questions",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        res.exercises.forEachIndexed { idx, ex ->
                            val answerVal = userExerciseAnswers[ex.id] ?: ""
                            val checkStatus = checkedExercises[ex.id]

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Q${idx + 1} (${ex.category}): ${ex.prompt}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    OutlinedTextField(
                                        value = answerVal,
                                        onValueChange = { userExerciseAnswers[ex.id] = it },
                                        label = { Text("Your answer") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("practice_answer_input_$idx")
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = { onCheckAnswer(res.sessionId, ex, answerVal) },
                                            modifier = Modifier.testTag("practice_check_button_$idx")
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Check Answer")
                                        }
                                        if (checkStatus != null) {
                                            Text(
                                                text = if (checkStatus) "Correct!" else "Expected: ${ex.expectedAnswer}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (checkStatus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = res.disclaimer,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section 10: Text-based Conversation Practice
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Conversation Practice: Learn $languageToLearn",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                if (conversationLoading) {
                    LanguageLoadingCard("AI tutor is replying...")
                }

                if (conversationError != null) {
                    Text(
                        text = conversationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                conversationTurns.forEach { turn ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "You: ${turn.userMessage}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "AI Tutor: ${turn.aiReply}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Correction: ${turn.correction}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Natural Alternative: ${turn.naturalAlternative}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Explanation: ${turn.shortExplanation}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = userConversationInput,
                        onValueChange = { userConversationInput = it },
                        placeholder = { Text("Reply in $languageToLearn...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("conversation_reply_input"),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (userConversationInput.isNotBlank()) {
                                onSendConversationTurn(languageToLearn, selectedLevel, topic, userConversationInput)
                                userConversationInput = ""
                            }
                        },
                        modifier = Modifier.testTag("conversation_send_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageHistoryView(
    historyItems: List<LanguageProjectEntity>,
    openedProject: LanguageProjectEntity?,
    onOpenProject: (String) -> Unit,
    onCloseOpenedProject: () -> Unit,
    onCopy: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Language History",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        if (openedProject != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${openedProject.projectType.uppercase()} (${openedProject.sourceLanguage} → ${openedProject.targetLanguage})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Input: ${openedProject.inputText}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Output: ${openedProject.outputText}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onCopy(openedProject.outputText) }) {
                            Text("Copy")
                        }
                        OutlinedButton(onClick = onCloseOpenedProject) {
                            Text("Close")
                        }
                    }
                }
            }
        }

        if (historyItems.isEmpty()) {
            Text(
                text = "No Language AI history yet. Your completed translations, rewrites, and grammar checks will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            historyItems.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("language_history_item_${item.id}"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${item.projectType.uppercase()} • ${item.sourceLanguage} → ${item.targetLanguage}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = item.inputText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Text(
                            text = item.outputText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 3
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { onOpenProject(item.id) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Open")
                            }
                            IconButton(onClick = { onCopy(item.outputText) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(onClick = { onDeleteProject(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSavedContentView(
    savedItems: List<SavedLanguageContentEntity>,
    onCopy: (String) -> Unit,
    onDeleteSaved: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Saved Translations & Content",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        if (savedItems.isEmpty()) {
            Text(
                text = "No saved language items yet. Save useful translations or rewrites to access them quickly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            savedItems.forEach { saved ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("language_saved_item_${saved.id}"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${saved.projectType.uppercase()} • ${saved.sourceLanguage} → ${saved.targetLanguage}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = saved.inputText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = saved.outputText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onCopy(saved.outputText) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy")
                            }
                            OutlinedButton(onClick = { onDeleteSaved(saved.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Unsave / Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
