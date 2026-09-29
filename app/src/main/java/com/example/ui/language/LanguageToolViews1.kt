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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.language.GrammarCheckResult
import com.example.domain.language.LanguageDetectionResult
import com.example.domain.language.LanguageOptions
import com.example.domain.language.LanguageRewriteResult
import com.example.domain.language.TranslationResult

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslatorToolView(
    sourceLanguage: String,
    targetLanguage: String,
    inputText: String,
    state: LanguageUiState<TranslationResult>,
    onSourceLanguageChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onInputTextChange: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onClear: () -> Unit,
    onTranslate: () -> Unit,
    onCopy: (String) -> Unit,
    onSave: (TranslationResult) -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Translator",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Supports 18+ Indian and international languages, Auto Detect, and Romanized Hindi (Hinglish).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Source -> Swap -> Target Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageDropdownSelector(
                label = "Source Language",
                selectedValue = sourceLanguage,
                options = LanguageOptions.SOURCE_LANGUAGES,
                onValueSelected = onSourceLanguageChange,
                modifier = Modifier.weight(1f),
                testTag = "translator_source_lang_selector"
            )

            FilledTonalIconButton(
                onClick = onSwapLanguages,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("translator_swap_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Swap Languages"
                )
            }

            LanguageDropdownSelector(
                label = "Target Language",
                selectedValue = targetLanguage,
                options = LanguageOptions.SUPPORTED_LANGUAGES,
                onValueSelected = onTargetLanguageChange,
                modifier = Modifier.weight(1f),
                testTag = "translator_target_lang_selector"
            )
        }

        // Quick Hinglish / Multilingual presets
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AssistChip(
                onClick = {
                    onSourceLanguageChange("Hinglish")
                    onTargetLanguageChange("Hindi")
                    onInputTextChange("mujhe kal school jana hai")
                },
                label = { Text("Hinglish → Hindi") }
            )
            AssistChip(
                onClick = {
                    onSourceLanguageChange("Hinglish")
                    onTargetLanguageChange("English")
                    onInputTextChange("mujhe padhna hai")
                },
                label = { Text("Hinglish → English") }
            )
            AssistChip(
                onClick = {
                    onSourceLanguageChange("English")
                    onTargetLanguageChange("Hindi")
                    onInputTextChange("Good morning, how are you?")
                },
                label = { Text("English → Hindi") }
            )
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = onInputTextChange,
            label = { Text("Enter text to translate") },
            placeholder = { Text("Type or paste text in any language or Hinglish...") },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("translator_input_field")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onTranslate,
                modifier = Modifier
                    .weight(1f)
                    .testTag("translator_submit_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Translate")
            }

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.testTag("translator_clear_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }
        }

        when (state) {
            is LanguageUiState.Idle -> Unit
            is LanguageUiState.Loading -> LanguageLoadingCard("Translating from $sourceLanguage to $targetLanguage...")
            is LanguageUiState.Error -> LanguageErrorCard(state.errorCode, state.message, onRetry)
            is LanguageUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("translator_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${res.sourceLanguage} → ${res.targetLanguage}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = res.translatedText,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (res.notesOrNuances.isNotBlank()) {
                            Text(
                                text = "Notes: ${res.notesOrNuances}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = res.disclaimer,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onCopy(res.translatedText) },
                                modifier = Modifier.testTag("translator_copy_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy")
                            }
                            OutlinedButton(
                                onClick = { onSave(res) },
                                modifier = Modifier.testTag("translator_save_button")
                            ) {
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
fun RewriteToolView(
    state: LanguageUiState<LanguageRewriteResult>,
    onRewrite: (text: String, styleOption: String, language: String) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (LanguageRewriteResult) -> Unit,
    onRetry: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(LanguageOptions.REWRITE_STYLES.first()) }
    var selectedLanguage by remember { mutableStateOf("English") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Rewrite Text",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Transform your own writing while preserving its original meaning.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LanguageDropdownSelector(
            label = "Output Language",
            selectedValue = selectedLanguage,
            options = LanguageOptions.SUPPORTED_LANGUAGES,
            onValueSelected = { selectedLanguage = it },
            testTag = "rewrite_lang_selector"
        )

        Text(
            text = "Select Rewrite Option",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LanguageOptions.REWRITE_STYLES.forEach { option ->
                FilterChip(
                    selected = selectedStyle == option,
                    onClick = { selectedStyle = option },
                    label = { Text(option) }
                )
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Your Text") },
            placeholder = { Text("Paste or write the text you want to rewrite...") },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rewrite_input_field")
        )

        Button(
            onClick = { onRewrite(inputText, selectedStyle, selectedLanguage) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rewrite_submit_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Rewrite ($selectedStyle)")
        }

        when (state) {
            is LanguageUiState.Idle -> Unit
            is LanguageUiState.Loading -> LanguageLoadingCard("Rewriting text ($selectedStyle)...")
            is LanguageUiState.Error -> LanguageErrorCard(state.errorCode, state.message, onRetry)
            is LanguageUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rewrite_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${res.styleOption} • ${res.language}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = res.rewrittenText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onCopy(res.rewrittenText) }) {
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

@Composable
fun GrammarCheckToolView(
    state: LanguageUiState<GrammarCheckResult>,
    onCheckGrammar: (text: String, language: String) -> Unit,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("English") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Grammar Check",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Check grammar and spelling with clear explanations while keeping your personal style.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LanguageDropdownSelector(
            label = "Language",
            selectedValue = selectedLanguage,
            options = LanguageOptions.SUPPORTED_LANGUAGES,
            onValueSelected = { selectedLanguage = it },
            testTag = "grammar_lang_selector"
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Text to check") },
            placeholder = { Text("Enter sentences or paragraphs to check grammar and spelling...") },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("grammar_input_field")
        )

        Button(
            onClick = { onCheckGrammar(inputText, selectedLanguage) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("grammar_submit_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Spellcheck, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Run Grammar Check")
        }

        when (state) {
            is LanguageUiState.Idle -> Unit
            is LanguageUiState.Loading -> LanguageLoadingCard("Checking grammar and spelling...")
            is LanguageUiState.Error -> LanguageErrorCard(state.errorCode, state.message, onRetry)
            is LanguageUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grammar_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Corrected Version",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = res.correctedVersion,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                        )

                        Text(
                            text = "Grammar Issues",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        res.grammarIssues.forEach { issue ->
                            Text("• $issue", style = MaterialTheme.typography.bodySmall)
                        }

                        Text(
                            text = "Spelling Issues",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        res.spellingIssues.forEach { issue ->
                            Text("• $issue", style = MaterialTheme.typography.bodySmall)
                        }

                        Text(
                            text = "Explanation of Important Corrections",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        res.explanations.forEach { exp ->
                            Text("• $exp", style = MaterialTheme.typography.bodySmall)
                        }

                        OutlinedButton(onClick = { onCopy(res.correctedVersion) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Corrected Text")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageDetectionToolView(
    state: LanguageUiState<LanguageDetectionResult>,
    onDetect: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Detect Language",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Identify the language and script of any text, with qualified notes when short or ambiguous.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Text to analyze") },
            placeholder = { Text("Paste text to detect its language...") },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("detect_input_field")
        )

        Button(
            onClick = { onDetect(inputText) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("detect_submit_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Detect Language")
        }

        when (state) {
            is LanguageUiState.Idle -> Unit
            is LanguageUiState.Loading -> LanguageLoadingCard("Analyzing linguistic markers...")
            is LanguageUiState.Error -> LanguageErrorCard(state.errorCode, state.message, onRetry)
            is LanguageUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detect_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Detected Language: ${res.detectedLanguage}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = res.confidenceExplanation,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (res.isAmbiguous) {
                            Text(
                                text = "Note: This sample shows cross-language or Romanized ambiguity and is not presented as a 100% guaranteed match.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { onCopy("${res.detectedLanguage}: ${res.confidenceExplanation}") }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Result")
                        }
                    }
                }
            }
        }
    }
}
