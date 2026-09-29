package com.example.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.creator.CaptionResult
import com.example.domain.creator.CreatorOptions
import com.example.domain.creator.GeneratedContentResult
import com.example.domain.creator.ShortVideoScriptResult
import com.example.domain.creator.TitleSuggestionsResult
import com.example.domain.creator.YouTubeScriptResult

// -------------------------------------------------------------
// 2. CONTENT GENERATOR VIEW
// -------------------------------------------------------------
@Composable
fun ContentGeneratorView(
    presetPlatform: String,
    selectedLanguage: String,
    state: CreatorUiState<GeneratedContentResult>,
    onGenerate: (
        platform: String,
        contentType: String,
        topic: String,
        targetAudience: String,
        language: String,
        tone: String,
        length: String,
        additionalInstructions: String
    ) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRetry: () -> Unit
) {
    var platform by remember(presetPlatform) {
        mutableStateOf(if (presetPlatform in CreatorOptions.PLATFORMS) presetPlatform else "YouTube")
    }
    var contentType by remember { mutableStateOf(CreatorOptions.CONTENT_TYPES.first()) }
    var topic by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("Students & Creators") }
    var language by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }
    var tone by remember { mutableStateOf("Educational") }
    var length by remember { mutableStateOf("Medium") }
    var additionalInstructions by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_content_generator_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Content Generator",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorDropdownField(
                        label = "Platform",
                        selectedValue = platform,
                        options = CreatorOptions.PLATFORMS,
                        onValueSelected = { platform = it },
                        testTag = "cg_platform_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Content Type",
                        selectedValue = contentType,
                        options = CreatorOptions.CONTENT_TYPES,
                        onValueSelected = { contentType = it },
                        testTag = "cg_type_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    placeholder = { Text("e.g., 5 daily study habits for board exams") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cg_topic_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = audience,
                    onValueChange = { audience = it },
                    label = { Text("Target Audience") },
                    placeholder = { Text("e.g., High school students, young professionals") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cg_audience_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorDropdownField(
                        label = "Language",
                        selectedValue = language,
                        options = CreatorOptions.LANGUAGES,
                        onValueSelected = { language = it },
                        testTag = "cg_language_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Tone",
                        selectedValue = tone,
                        options = CreatorOptions.TONES,
                        onValueSelected = { tone = it },
                        testTag = "cg_tone_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Length",
                        selectedValue = length,
                        options = CreatorOptions.LENGTHS,
                        onValueSelected = { length = it },
                        testTag = "cg_length_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = additionalInstructions,
                    onValueChange = { additionalInstructions = it },
                    label = { Text("Additional Instructions (Optional)") },
                    placeholder = { Text("e.g., Include bullet points and a friendly question at the end") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cg_instructions_input"),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onGenerate(platform, contentType, topic, audience, language, tone, length, additionalInstructions)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_content_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Content")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is CreatorUiState.Idle -> {}
            is CreatorUiState.Loading -> CreatorLoadingView()
            is CreatorUiState.Error -> CreatorErrorCard(state.errorCode, state.message, onRetry)
            is CreatorUiState.Success -> {
                val data = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cg_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = data.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = data.content,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("cg_result_text")
                        )
                        CreatorResultActionToolbar(
                            onCopy = { onCopy(data.content) },
                            onRegenerate = {
                                onGenerate(platform, contentType, topic, audience, language, tone, length, additionalInstructions)
                            },
                            onSave = {
                                onSave(data.title, "content_generate", data.platform, data.language, data.content, data.projectId)
                            },
                            onDelete = { onDeleteProject(data.projectId) },
                            tagPrefix = "cg"
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. YOUTUBE SCRIPT GENERATOR VIEW
// -------------------------------------------------------------
@Composable
fun YouTubeScriptGeneratorView(
    selectedLanguage: String,
    state: CreatorUiState<YouTubeScriptResult>,
    onGenerate: (topic: String, duration: String, language: String, audience: String, style: String) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRetry: () -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("8-10 minutes") }
    var language by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }
    var audience by remember { mutableStateOf("Students & Learners") }
    var style by remember { mutableStateOf("Educational") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_youtube_script_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "YouTube Script Generator",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Structured scripts with Title Ideas, Hook, Intro, Main Content, CTA & Closing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Video Topic") },
                    placeholder = { Text("e.g., How to study effectively for Class 10") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("yt_topic_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorDropdownField(
                        label = "Video Duration",
                        selectedValue = duration,
                        options = CreatorOptions.YOUTUBE_DURATIONS,
                        onValueSelected = { duration = it },
                        testTag = "yt_duration_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Language",
                        selectedValue = language,
                        options = CreatorOptions.LANGUAGES,
                        onValueSelected = { language = it },
                        testTag = "yt_language_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = audience,
                        onValueChange = { audience = it },
                        label = { Text("Audience") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("yt_audience_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    CreatorDropdownField(
                        label = "Style",
                        selectedValue = style,
                        options = CreatorOptions.SCRIPT_STYLES,
                        onValueSelected = { style = it },
                        testTag = "yt_style_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onGenerate(topic, duration, language, audience, style) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("yt_generate_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate YouTube Script")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is CreatorUiState.Idle -> {}
            is CreatorUiState.Loading -> CreatorLoadingView("Writing your YouTube script...")
            is CreatorUiState.Error -> CreatorErrorCard(state.errorCode, state.message, onRetry)
            is CreatorUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("yt_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "YouTube Script (${res.duration} • ${res.language})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CreatorSectionBox("Title Ideas (Suggestions)", res.titleIdeas.joinToString("\n") { "• $it" })
                        CreatorSectionBox("Hook", res.hook)
                        CreatorSectionBox("Introduction", res.introduction)
                        CreatorSectionBox("Main Content", res.mainContent)
                        CreatorSectionBox("Call to Action", res.callToAction)
                        CreatorSectionBox("Closing", res.closing)

                        CreatorResultActionToolbar(
                            onCopy = { onCopy(res.rawOutput) },
                            onRegenerate = { onGenerate(topic, duration, language, audience, style) },
                            onSave = {
                                onSave("YouTube Script: ${res.topic}", "youtube_script", "YouTube", res.language, res.rawOutput, res.projectId)
                            },
                            onDelete = { onDeleteProject(res.projectId) },
                            tagPrefix = "yt"
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. SHORTS / REELS GENERATOR VIEW
// -------------------------------------------------------------
@Composable
fun ShortsReelsGeneratorView(
    presetPlatform: String,
    selectedLanguage: String,
    state: CreatorUiState<ShortVideoScriptResult>,
    onGenerate: (topic: String, duration: String, platform: String, language: String, style: String) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRetry: () -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60 seconds") }
    var platform by remember(presetPlatform) {
        mutableStateOf(
            if (presetPlatform in CreatorOptions.SHORT_VIDEO_PLATFORMS) presetPlatform else "YouTube Shorts"
        )
    }
    var language by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }
    var style by remember { mutableStateOf("Educational") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_shorts_reels_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Shorts / Reels Generator",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Generate Hook, Scene/Beat Outline, Voiceover, On-Screen Text, Caption & CTA.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Short Video Topic") },
                    placeholder = { Text("e.g., 3 Pomodoro tricks to double focus in 60 seconds") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shorts_topic_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorDropdownField(
                        label = "Platform",
                        selectedValue = platform,
                        options = CreatorOptions.SHORT_VIDEO_PLATFORMS,
                        onValueSelected = { platform = it },
                        testTag = "shorts_platform_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Duration",
                        selectedValue = duration,
                        options = CreatorOptions.SHORT_VIDEO_DURATIONS,
                        onValueSelected = { duration = it },
                        testTag = "shorts_duration_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorDropdownField(
                        label = "Language",
                        selectedValue = language,
                        options = CreatorOptions.LANGUAGES,
                        onValueSelected = { language = it },
                        testTag = "shorts_language_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Style",
                        selectedValue = style,
                        options = CreatorOptions.SCRIPT_STYLES,
                        onValueSelected = { style = it },
                        testTag = "shorts_style_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onGenerate(topic, duration, platform, language, style) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shorts_generate_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Short Script")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is CreatorUiState.Idle -> {}
            is CreatorUiState.Loading -> CreatorLoadingView("Creating your $platform script...")
            is CreatorUiState.Error -> CreatorErrorCard(state.errorCode, state.message, onRetry)
            is CreatorUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shorts_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${res.platform} (${res.duration} • ${res.language})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CreatorSectionBox("Hook", res.hook)
                        CreatorSectionBox("Scene / Beat Outline", res.sceneBeatOutline)
                        CreatorSectionBox("Voiceover Script", res.voiceoverScript)
                        CreatorSectionBox("On-Screen Text Suggestions", res.onScreenTextSuggestions)
                        CreatorSectionBox("Caption", res.caption)
                        CreatorSectionBox("CTA", res.cta)

                        CreatorResultActionToolbar(
                            onCopy = { onCopy(res.rawOutput) },
                            onRegenerate = { onGenerate(topic, duration, platform, language, style) },
                            onSave = {
                                onSave("${res.platform}: ${res.topic}", "shorts_script", res.platform, res.language, res.rawOutput, res.projectId)
                            },
                            onDelete = { onDeleteProject(res.projectId) },
                            tagPrefix = "shorts"
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 5. TITLE GENERATOR VIEW
// -------------------------------------------------------------
@Composable
fun TitleGeneratorView(
    selectedLanguage: String,
    state: CreatorUiState<TitleSuggestionsResult>,
    onGenerate: (topic: String, platform: String, language: String, count: Int) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRetry: () -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("YouTube") }
    var language by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }
    var countText by remember { mutableStateOf("5") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_title_generator_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Title Generator",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Generate original title ideas. Presented as creative suggestions, not guaranteed views.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    placeholder = { Text("e.g., How to study effectively for Class 10") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("title_topic_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorDropdownField(
                        label = "Platform",
                        selectedValue = platform,
                        options = CreatorOptions.PLATFORMS,
                        onValueSelected = { platform = it },
                        testTag = "title_platform_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Language",
                        selectedValue = language,
                        options = CreatorOptions.LANGUAGES,
                        onValueSelected = { language = it },
                        testTag = "title_language_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Count",
                        selectedValue = countText,
                        options = listOf("3", "5", "10"),
                        onValueSelected = { countText = it },
                        testTag = "title_count_dropdown",
                        modifier = Modifier.weight(0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onGenerate(topic, platform, language, countText.toIntOrNull() ?: 5)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("title_generate_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Titles")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is CreatorUiState.Idle -> {}
            is CreatorUiState.Loading -> CreatorLoadingView("Generating original title ideas...")
            is CreatorUiState.Error -> CreatorErrorCard(state.errorCode, state.message, onRetry)
            is CreatorUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("title_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Suggested Title Ideas (${res.platform})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        res.titles.forEachIndexed { index, titleItem ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("title_item_$index")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}. $titleItem",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onCopy(titleItem) },
                                        modifier = Modifier.testTag("copy_title_btn_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Title",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        CreatorResultActionToolbar(
                            onCopy = { onCopy(res.titles.joinToString("\n")) },
                            onRegenerate = { onGenerate(topic, platform, language, countText.toIntOrNull() ?: 5) },
                            onSave = {
                                onSave("Titles: ${res.topic}", "title_generate", res.platform, res.language, res.titles.joinToString("\n"), res.projectId)
                            },
                            onDelete = { onDeleteProject(res.projectId) },
                            tagPrefix = "titles"
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. CAPTION GENERATOR VIEW
// -------------------------------------------------------------
@Composable
fun CaptionGeneratorView(
    selectedLanguage: String,
    state: CreatorUiState<CaptionResult>,
    onGenerate: (topic: String, platform: String, tone: String, language: String, length: String) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRetry: () -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("Instagram") }
    var tone by remember { mutableStateOf("Friendly") }
    var language by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }
    var length by remember { mutableStateOf("Medium") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_caption_generator_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Caption Generator",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic / Post Context") },
                    placeholder = { Text("e.g., Behind-the-scenes of my morning study routine") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("caption_topic_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorDropdownField(
                        label = "Platform",
                        selectedValue = platform,
                        options = CreatorOptions.PLATFORMS,
                        onValueSelected = { platform = it },
                        testTag = "caption_platform_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Tone",
                        selectedValue = tone,
                        options = CreatorOptions.TONES,
                        onValueSelected = { tone = it },
                        testTag = "caption_tone_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorDropdownField(
                        label = "Language",
                        selectedValue = language,
                        options = CreatorOptions.LANGUAGES,
                        onValueSelected = { language = it },
                        testTag = "caption_language_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Length",
                        selectedValue = length,
                        options = CreatorOptions.LENGTHS,
                        onValueSelected = { length = it },
                        testTag = "caption_length_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onGenerate(topic, platform, tone, language, length) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("caption_generate_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Caption")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is CreatorUiState.Idle -> {}
            is CreatorUiState.Loading -> CreatorLoadingView("Writing social caption...")
            is CreatorUiState.Error -> CreatorErrorCard(state.errorCode, state.message, onRetry)
            is CreatorUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("caption_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Generated Caption (${res.platform})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CreatorSectionBox("Caption", res.caption)
                        if (res.cta.isNotBlank()) {
                            CreatorSectionBox("Optional CTA", res.cta)
                        }

                        CreatorResultActionToolbar(
                            onCopy = { onCopy(res.rawOutput) },
                            onRegenerate = { onGenerate(topic, platform, tone, language, length) },
                            onSave = {
                                onSave("Caption: ${res.topic}", "caption_generate", res.platform, res.language, res.rawOutput, res.projectId)
                            },
                            onDelete = { onDeleteProject(res.projectId) },
                            tagPrefix = "caption"
                        )
                    }
                }
            }
        }
    }
}
