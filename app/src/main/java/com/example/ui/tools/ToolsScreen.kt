package com.example.ui.tools

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.AIModule
import com.example.ui.components.StatusBadge

data class ToolItem(
    val module: AIModule,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun ToolsScreen(
    onSelectTool: (AIModule) -> Unit
) {
    // 9 AI Workspaces specified in Section 7:
    val tools = listOf(
        ToolItem(AIModule.STUDY_AI, Icons.Default.School, Color(0xFF6366F1)),
        ToolItem(AIModule.CREATOR_AI, Icons.Default.Brush, Color(0xFFA855F7)),
        ToolItem(AIModule.DOCUMENT_AI, Icons.Default.Description, Color(0xFF0284C7)),
        ToolItem(AIModule.LANGUAGE_AI, Icons.Default.AutoAwesome, Color(0xFF10B981)),
        ToolItem(AIModule.BUSINESS_AI, Icons.Default.BusinessCenter, Color(0xFFF59E0B)),
        ToolItem(AIModule.SKILL_PATH_AI, Icons.Default.TrendingUp, Color(0xFFEC4899)),
        ToolItem(AIModule.KISAN_AI, Icons.Default.Agriculture, Color(0xFF14B8A6)),
        ToolItem(AIModule.GHAR_MANAGER_AI, Icons.Default.Home, Color(0xFF8B5CF6)),
        ToolItem(AIModule.LOCAL_BIZ_AI, Icons.Default.Storefront, Color(0xFF64748B))
    )

    var previewModalTool by remember { mutableStateOf<AIModule?>(null) }
    var notificationSubscribed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Screen Header
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "Specialized AI Workspaces",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "One AI Brain powering dedicated domain engines",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "ACTIVE WORKSPACES",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(tools.filter { !it.module.isComingSoon }) { tool ->
                ToolCardItem(
                    tool = tool,
                    onClick = { onSelectTool(tool.module) }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "UPCOMING WORKSPACES (COMING SOON)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(tools.filter { it.module.isComingSoon }) { tool ->
                ToolCardItem(
                    tool = tool,
                    onClick = {
                        notificationSubscribed = false
                        previewModalTool = tool.module
                    }
                )
            }
        }
    }

    // Coming Soon Modal (Section 7)
    if (previewModalTool != null) {
        val tool = previewModalTool!!
        AlertDialog(
            onDismissRequest = { previewModalTool = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("${tool.title} (Coming Soon)")
            },
            text = {
                Column {
                    Text(
                        text = tool.subtitle,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This specialized workspace is under active development for upcoming releases. In accordance with Nexa AI principles, no fake functionality is shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (notificationSubscribed) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Notification reminder scheduled for launch.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        notificationSubscribed = true
                    }
                ) {
                    Text(if (notificationSubscribed) "Subscribed" else "Notify Me When Ready")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewModalTool = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ToolCardItem(
    tool: ToolItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(
                1.dp,
                if (tool.module.isComingSoon) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                else tool.accentColor.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            )
            .testTag("tool_card_${tool.module.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (tool.module.isComingSoon) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(tool.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = tool.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (tool.module.isComingSoon) {
                    StatusBadge(text = "Coming Soon", isComingSoon = true)
                } else {
                    StatusBadge(text = "Active", isActive = true)
                }
            }

            Column {
                Text(
                    text = tool.module.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tool.module.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
