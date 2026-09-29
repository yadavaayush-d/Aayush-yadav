package com.example.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.CreatorProjectEntity
import com.example.data.local.entity.SavedCreatorContentEntity
import com.example.domain.creator.BlogArticleResult
import com.example.domain.creator.ContentIdeaItem
import com.example.domain.creator.ContentIdeasResult
import com.example.domain.creator.CreatorOptions
import com.example.domain.creator.HashtagResult
import com.example.domain.creator.RewriteResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// -------------------------------------------------------------
// 7. HASHTAG ASSISTANT VIEW
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HashtagAssistantView(
    selectedLanguage: String,
    state: CreatorUiState<HashtagResult>,
    onGenerate: (topic: String, platform: String, language: String) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRetry: () -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("Instagram") }
    var language by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_hashtag_assistant_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Hashtag Assistant",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Relevant hashtag suggestions for discovery — not guaranteed trending hashtags.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic / Niche") },
                    placeholder = { Text("e.g., Science Exam Prep & Study Motivation") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hashtag_topic_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorDropdownField(
                        label = "Platform",
                        selectedValue = platform,
                        options = CreatorOptions.PLATFORMS,
                        onValueSelected = { platform = it },
                        testTag = "hashtag_platform_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Language",
                        selectedValue = language,
                        options = CreatorOptions.LANGUAGES,
                        onValueSelected = { language = it },
                        testTag = "hashtag_language_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onGenerate(topic, platform, language) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hashtag_generate_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Suggest Hashtags")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is CreatorUiState.Idle -> {}
            is CreatorUiState.Loading -> CreatorLoadingView("Finding relevant hashtag suggestions...")
            is CreatorUiState.Error -> CreatorErrorCard(state.errorCode, state.message, onRetry)
            is CreatorUiState.Success -> {
                val res = state.data
                val joinedTags = res.hashtags.joinToString(" ")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hashtag_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Suggested Hashtags (${res.platform})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = res.disclaimerNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            res.hashtags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onCopy(joinedTags) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("copy_hashtags_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Hashtags")
                        }

                        CreatorResultActionToolbar(
                            onCopy = { onCopy(joinedTags) },
                            onRegenerate = { onGenerate(topic, platform, language) },
                            onSave = {
                                onSave("Hashtags: ${res.topic}", "hashtag_suggest", res.platform, res.language, joinedTags, res.projectId)
                            },
                            onDelete = { onDeleteProject(res.projectId) },
                            tagPrefix = "hashtag"
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 8. CONTENT IDEAS VIEW
// -------------------------------------------------------------
@Composable
fun ContentIdeasView(
    selectedLanguage: String,
    state: CreatorUiState<ContentIdeasResult>,
    onGenerate: (nicheOrTopic: String, platform: String, audience: String, count: Int, language: String) -> Unit,
    onCopy: (String) -> Unit,
    onSaveIdea: (ContentIdeaItem, String, String) -> Unit,
    onSaveAll: (String, String, String, String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRetry: () -> Unit
) {
    var niche by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("YouTube") }
    var audience by remember { mutableStateOf("Students & Creators") }
    var countText by remember { mutableStateOf("5") }
    var language by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_content_ideas_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Content Ideas Generator",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Each idea includes Title, Short Description, Suggested Format & Suggested Hook.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = niche,
                    onValueChange = { niche = it },
                    label = { Text("Niche / Topic") },
                    placeholder = { Text("e.g., Productivity & Exam Preparation for Students") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ideas_niche_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = audience,
                    onValueChange = { audience = it },
                    label = { Text("Target Audience") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ideas_audience_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorDropdownField(
                        label = "Platform",
                        selectedValue = platform,
                        options = CreatorOptions.PLATFORMS,
                        onValueSelected = { platform = it },
                        testTag = "ideas_platform_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Language",
                        selectedValue = language,
                        options = CreatorOptions.LANGUAGES,
                        onValueSelected = { language = it },
                        testTag = "ideas_language_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Ideas",
                        selectedValue = countText,
                        options = listOf("3", "5", "10"),
                        onValueSelected = { countText = it },
                        testTag = "ideas_count_dropdown",
                        modifier = Modifier.weight(0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onGenerate(niche, platform, audience, countText.toIntOrNull() ?: 5, language)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ideas_generate_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Content Ideas")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is CreatorUiState.Idle -> {}
            is CreatorUiState.Loading -> CreatorLoadingView("Brainstorming original content ideas...")
            is CreatorUiState.Error -> CreatorErrorCard(state.errorCode, state.message, onRetry)
            is CreatorUiState.Success -> {
                val res = state.data
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ideas_result_list")
                ) {
                    res.ideas.forEachIndexed { idx, idea ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag("idea_card_$idx"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "${idx + 1}. ${idea.title}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = idea.shortDescription,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Suggested Format: ${idea.suggestedFormat}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Suggested Hook: \"${idea.suggestedHook}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { onSaveIdea(idea, res.platform, res.projectId) },
                                        modifier = Modifier.testTag("save_idea_btn_$idx")
                                    ) {
                                        Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onCopy("${idea.title}\n${idea.shortDescription}\nFormat: ${idea.suggestedFormat}\nHook: ${idea.suggestedHook}")
                                        },
                                        modifier = Modifier.testTag("copy_idea_btn_$idx")
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy")
                                    }
                                }
                            }
                        }
                    }

                    CreatorResultActionToolbar(
                        onCopy = { onCopy(res.rawOutput) },
                        onRegenerate = {
                            onGenerate(niche, platform, audience, countText.toIntOrNull() ?: 5, language)
                        },
                        onSave = {
                            onSaveAll("Ideas: ${res.nicheOrTopic}", "content_ideas", res.platform, language, res.rawOutput, res.projectId)
                        },
                        onDelete = { onDeleteProject(res.projectId) },
                        tagPrefix = "ideas"
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 9. BLOG WRITER VIEW
// -------------------------------------------------------------
@Composable
fun BlogWriterView(
    selectedLanguage: String,
    state: CreatorUiState<BlogArticleResult>,
    onGenerate: (topic: String, audience: String, language: String, tone: String, approxLength: String) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRetry: () -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("Students & General Readers") }
    var language by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }
    var tone by remember { mutableStateOf("Educational") }
    var approxLength by remember { mutableStateOf(CreatorOptions.BLOG_LENGTHS[1]) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_blog_writer_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Blog Writer",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Generate original articles with Title, Introduction, Section Outline, Main Article & Conclusion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Blog Topic") },
                    placeholder = { Text("e.g., How active recall and spaced repetition transform learning") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blog_topic_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = audience,
                    onValueChange = { audience = it },
                    label = { Text("Target Audience") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blog_audience_input"),
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
                        testTag = "blog_language_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    CreatorDropdownField(
                        label = "Tone",
                        selectedValue = tone,
                        options = CreatorOptions.TONES,
                        onValueSelected = { tone = it },
                        testTag = "blog_tone_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                CreatorDropdownField(
                    label = "Approximate Length",
                    selectedValue = approxLength,
                    options = CreatorOptions.BLOG_LENGTHS,
                    onValueSelected = { approxLength = it },
                    testTag = "blog_length_dropdown"
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onGenerate(topic, audience, language, tone, approxLength) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blog_generate_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Blog Article")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is CreatorUiState.Idle -> {}
            is CreatorUiState.Loading -> CreatorLoadingView("Writing your original blog article...")
            is CreatorUiState.Error -> CreatorErrorCard(state.errorCode, state.message, onRetry)
            is CreatorUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blog_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = res.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CreatorSectionBox("Introduction", res.introduction)
                        CreatorSectionBox("Section Outline", res.sectionOutline)
                        CreatorSectionBox("Main Article", res.mainArticle)
                        CreatorSectionBox("Conclusion", res.conclusion)

                        CreatorResultActionToolbar(
                            onCopy = { onCopy(res.rawOutput) },
                            onRegenerate = { onGenerate(topic, audience, language, tone, approxLength) },
                            onSave = {
                                onSave(res.title, "blog_generate", "Blog", res.language, res.rawOutput, res.projectId)
                            },
                            onDelete = { onDeleteProject(res.projectId) },
                            tagPrefix = "blog"
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 10. CONTENT REWRITER VIEW
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentRewriterView(
    selectedLanguage: String,
    state: CreatorUiState<RewriteResult>,
    onRewrite: (userText: String, action: String, language: String) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRetry: () -> Unit
) {
    var userText by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf(CreatorOptions.REWRITE_ACTIONS.first()) }
    var language by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_rewriter_view")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Content Rewriter",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Paste your own text to simplify, improve clarity, adjust tone, translate, or summarize.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = userText,
                    onValueChange = { userText = it },
                    label = { Text("Your Text") },
                    placeholder = { Text("Paste or type the text you have permission to transform...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("rewrite_text_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Select Transformation Action:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CreatorOptions.REWRITE_ACTIONS.forEach { action ->
                        val isSelected = selectedAction == action
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedAction = action },
                            modifier = Modifier.testTag("rewrite_action_${action.replace(" ", "_")}")
                        ) {
                            Text(
                                text = action,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                CreatorDropdownField(
                    label = "Output Language",
                    selectedValue = language,
                    options = CreatorOptions.LANGUAGES,
                    onValueSelected = { language = it },
                    testTag = "rewrite_language_dropdown"
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onRewrite(userText, selectedAction, language) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rewrite_submit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transform ($selectedAction)")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state) {
            is CreatorUiState.Idle -> {}
            is CreatorUiState.Loading -> CreatorLoadingView("Transforming your text...")
            is CreatorUiState.Error -> CreatorErrorCard(state.errorCode, state.message, onRetry)
            is CreatorUiState.Success -> {
                val res = state.data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rewrite_result_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Rewritten Output (${res.action} • ${res.language})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = res.transformedText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("rewrite_result_text")
                        )

                        CreatorResultActionToolbar(
                            onCopy = { onCopy(res.transformedText) },
                            onRegenerate = { onRewrite(userText, selectedAction, language) },
                            onSave = {
                                onSave("Rewrite (${res.action})", "content_rewrite", "General Social Media", res.language, res.transformedText, res.projectId)
                            },
                            onDelete = { onDeleteProject(res.projectId) },
                            tagPrefix = "rewrite"
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 11. CREATOR HISTORY VIEW (CreatorProjects)
// -------------------------------------------------------------
@Composable
fun CreatorHistoryView(
    projects: List<CreatorProjectEntity>,
    openedProject: CreatorProjectEntity?,
    onOpenProject: (String) -> Unit,
    onCloseProject: () -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onCopyProject: (String) -> Unit,
    onRegenerateProject: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    var renameDialogProject by remember { mutableStateOf<CreatorProjectEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_history_view")
    ) {
        Text(
            text = "Creator History",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Your private Creator AI projects. Open, rename, copy, regenerate, or delete anytime.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (projects.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("creator_history_empty"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Creator projects yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Generate scripts, titles, captions, or blogs to automatically save them to your Creator History.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            projects.forEach { proj ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("creator_project_card_${proj.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = proj.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${proj.platform} • ${proj.language} • ${dateFormat.format(Date(proj.updatedAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        renameDialogProject = proj
                                        renameInput = proj.title
                                    },
                                    modifier = Modifier.testTag("rename_project_btn_${proj.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onDeleteProject(proj.id) },
                                    modifier = Modifier.testTag("delete_project_btn_${proj.id}")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = proj.output.take(180) + if (proj.output.length > 180) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onOpenProject(proj.id) },
                                modifier = Modifier.testTag("open_project_btn_${proj.id}")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open")
                            }
                            OutlinedButton(
                                onClick = { onCopyProject(proj.output) },
                                modifier = Modifier.testTag("copy_project_btn_${proj.id}")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy")
                            }
                            OutlinedButton(
                                onClick = { onRegenerateProject(proj.id) },
                                modifier = Modifier.testTag("regen_project_btn_${proj.id}")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regenerate")
                            }
                        }
                    }
                }
            }
        }
    }

    if (openedProject != null) {
        AlertDialog(
            onDismissRequest = onCloseProject,
            title = { Text(openedProject.title) },
            text = {
                Column {
                    Text(
                        text = "${openedProject.platform} • ${openedProject.language} • ${openedProject.projectType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = openedProject.output,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onCopyProject(openedProject.output) }) {
                    Text("Copy Output")
                }
            },
            dismissButton = {
                TextButton(onClick = onCloseProject) {
                    Text("Close")
                }
            }
        )
    }

    if (renameDialogProject != null) {
        val target = renameDialogProject!!
        AlertDialog(
            onDismissRequest = { renameDialogProject = null },
            title = { Text("Rename Creator Project") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_project_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameProject(target.id, renameInput)
                        renameDialogProject = null
                    },
                    modifier = Modifier.testTag("confirm_rename_project_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogProject = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// 12. SAVED CONTENT / FAVORITES VIEW
// -------------------------------------------------------------
@Composable
fun CreatorSavedContentView(
    savedItems: List<SavedCreatorContentEntity>,
    onCopy: (String) -> Unit,
    onDeleteSaved: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("creator_saved_content_view")
    ) {
        Text(
            text = "Saved Content & Favorites",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Your bookmarked ideas, scripts, titles, and captions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (savedItems.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("creator_saved_empty"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No saved content yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Save' on any generated script, caption, title, or content idea to keep it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            savedItems.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("saved_item_card_${item.id}"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${item.platform} • ${item.language}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.content,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onCopy(item.content) },
                                modifier = Modifier.testTag("copy_saved_btn_${item.id}")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy")
                            }
                            OutlinedButton(
                                onClick = { onDeleteSaved(item.id) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("unsave_btn_${item.id}")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
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
