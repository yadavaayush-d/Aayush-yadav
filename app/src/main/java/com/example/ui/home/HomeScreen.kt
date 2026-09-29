package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.UserEntity
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AIModule
import com.example.ui.components.NexaBrandLogo
import com.example.ui.components.StatusBadge
import com.example.ui.theme.NexaCardGradientEnd
import com.example.ui.theme.NexaCardGradientStart
import com.example.ui.theme.NexaPrimary
import com.example.ui.theme.NexaSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    repository: NexaRepository,
    onNavigateToChat: (AIModule?, String?) -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val currentUser by repository.currentUser.collectAsState()
    val conversations by (currentUser?.let { repository.getConversations(it.id) }
        ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    var quickInputText by remember { mutableStateOf("") }
    var activeActionDialog by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. HEADER & PERSONAL GREETING (Section 4)
        item {
            HomeHeaderSection(
                user = currentUser
            )
        }

        // 2. MAIN AI INPUT & ACTION BUTTONS
        item {
            MainAiInputCard(
                inputText = quickInputText,
                onInputTextChange = { quickInputText = it },
                onAskAi = {
                    if (quickInputText.isNotBlank()) {
                        val classified = repository.router.classifyIntent(quickInputText)
                        onNavigateToChat(classified.recommendedModule, quickInputText)
                        quickInputText = ""
                    } else {
                        onNavigateToChat(AIModule.GENERAL_AI, null)
                    }
                },
                onActionTrigger = { actionType ->
                    activeActionDialog = actionType
                }
            )
        }

        // 3. QUICK AI TOOLS
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick AI Tools",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onNavigateToTools) {
                        Text("View All")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2x2 Grid of Quick Tools
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickToolCard(
                        title = "Study AI",
                        subtitle = "Learn, solve and practice.",
                        icon = Icons.Default.School,
                        accentColor = Color(0xFF6366F1),
                        modifier = Modifier.weight(1f).testTag("quick_tool_study"),
                        onClick = { onNavigateToChat(AIModule.STUDY_AI, null) }
                    )
                    QuickToolCard(
                        title = "Creator AI",
                        subtitle = "Create scripts and content.",
                        icon = Icons.Default.Brush,
                        accentColor = Color(0xFFA855F7),
                        modifier = Modifier.weight(1f).testTag("quick_tool_creator"),
                        onClick = { onNavigateToChat(AIModule.CREATOR_AI, null) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickToolCard(
                        title = "Document AI",
                        subtitle = "Understand PDFs and documents.",
                        icon = Icons.Default.Description,
                        accentColor = Color(0xFF0284C7),
                        modifier = Modifier.weight(1f).testTag("quick_tool_documents"),
                        onClick = { onNavigateToChat(AIModule.DOCUMENT_AI, null) }
                    )
                    QuickToolCard(
                        title = "Language AI",
                        subtitle = "Translate and learn languages.",
                        icon = Icons.Default.AutoAwesome,
                        accentColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f).testTag("quick_tool_language"),
                        onClick = { onNavigateToChat(AIModule.LANGUAGE_AI, null) }
                    )
                }
            }
        }

        // 4. RECENT ACTIVITY SECTION
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (conversations.isNotEmpty()) {
                        TextButton(onClick = onNavigateToHistory) {
                            Text("See All")
                        }
                    }
                }

                if (conversations.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "No recent sessions yet",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Start asking Nexa AI to build your activity history.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    conversations.take(3).forEach { conv ->
                        RecentActivityRow(
                            conversation = conv,
                            onClick = {
                                val mod = AIModule.entries.firstOrNull { it.title.equals(conv.module, ignoreCase = true) } ?: AIModule.GENERAL_AI
                                onNavigateToChat(mod, null)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // 5. COMING SOON SECTION (Section 4)
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = "Coming Soon",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    item {
                        ComingSoonCard(
                            name = "Business AI",
                            desc = "Workflows & customer analytics",
                            icon = Icons.Default.BusinessCenter
                        )
                    }
                    item {
                        ComingSoonCard(
                            name = "Skill AI",
                            desc = "Personalized learning paths",
                            icon = Icons.Default.TrendingUp
                        )
                    }
                    item {
                        ComingSoonCard(
                            name = "Farming AI",
                            desc = "Agronomy & weather telemetry",
                            icon = Icons.Default.Agriculture
                        )
                    }
                    item {
                        ComingSoonCard(
                            name = "Family AI",
                            desc = "Household tasks & coordination",
                            icon = Icons.Default.Home
                        )
                    }
                    item {
                        ComingSoonCard(
                            name = "Local Business AI",
                            desc = "Merchant marketing & CRM",
                            icon = Icons.Default.Storefront
                        )
                    }
                }
            }
        }
    }

    // Modal dialogs for Voice, Camera, and Upload integrations
    if (activeActionDialog != null) {
        val action = activeActionDialog!!
        AlertDialog(
            onDismissRequest = { activeActionDialog = null },
            icon = {
                Icon(
                    imageVector = when (action) {
                        "Voice" -> Icons.Default.Mic
                        "Camera" -> Icons.Default.CameraAlt
                        else -> Icons.Default.UploadFile
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("$action Integration Point")
            },
            text = {
                Text(
                    when (action) {
                        "Voice" -> "Nexa Voice Audio Streamer is prepared. Connected to audio speech pipeline for real-time natural language query dispatching."
                        "Camera" -> "Nexa Document & Vision OCR Scanner integration point. Ready to stream visual payloads to Document AI backend."
                        else -> "Nexa Secure Document Ingestion Gateway. Ready to parse PDF, Word, and text documents into localized semantic vectors."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeActionDialog = null
                        onNavigateToChat(
                            if (action == "Upload") AIModule.DOCUMENT_AI else AIModule.GENERAL_AI,
                            "[$action Input attached]"
                        )
                    }
                ) {
                    Text("Proceed to Chat")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeActionDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun HomeHeaderSection(
    user: UserEntity?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaBrandLogo(size = 36)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Nexa AI",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "One AI. Many Possibilities.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (user != null) {
                StatusBadge(text = user.userType, isActive = true)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Hello, ${user?.name?.ifBlank { "Explorer" } ?: "Explorer"}",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun MainAiInputCard(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onAskAi: () -> Unit,
    onActionTrigger: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                placeholder = {
                    Text(
                        "What can I help you with?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_main_ai_input"),
                shape = RoundedCornerShape(14.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Actions: Ask AI, Voice, Camera, Upload (Section 4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { onActionTrigger("Voice") },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .testTag("action_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { onActionTrigger("Camera") },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .testTag("action_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { onActionTrigger("Upload") },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .testTag("action_upload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Upload",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Button(
                    onClick = onAskAi,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("action_ask_ai_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ask AI")
                }
            }
        }
    }
}

@Composable
fun QuickToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .height(136.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun RecentActivityRow(
    conversation: ConversationEntity,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val dateString = remember(conversation.updatedAt) {
        dateFormat.format(Date(conversation.updatedAt))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "${conversation.module} • $dateString",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ComingSoonCard(
    name: String,
    desc: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier
            .width(170.dp)
            .height(130.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                StatusBadge(text = "Soon", isComingSoon = true)
            }
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
