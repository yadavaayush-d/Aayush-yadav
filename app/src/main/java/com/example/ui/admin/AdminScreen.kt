package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AIRequestEntity
import com.example.data.local.entity.FeedbackEntity
import com.example.data.local.entity.UserEntity
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AIBackendConfig
import com.example.domain.ai.PlanTier
import com.example.ui.components.StatusBadge
import com.example.ui.theme.NexaPrimary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminScreen(
    repository: NexaRepository,
    onBack: () -> Unit
) {
    val currentUser by repository.currentUser.collectAsState()
    val scope = rememberCoroutineScope()

    val users by repository.getAllUsers().collectAsState(initial = emptyList())
    val conversations by repository.getAllConversations().collectAsState(initial = emptyList())
    val requests by repository.getAllRequests().collectAsState(initial = emptyList())
    val feedbackList by repository.getAllFeedback().collectAsState(initial = emptyList())

    val tabs = listOf("Aggregate Analytics", "Users", "AI Requests", "Feedback", "Orchestrator Config")
    var selectedTab by remember { mutableIntStateOf(0) }

    // Role-based authorization check
    if (currentUser?.isAdmin != true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Admin Authorization Required",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your current account (${currentUser?.email}) does not possess system administrator privileges.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { scope.launch { repository.loginAsAdmin() } }) {
                Text("Switch to Admin Profile")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBack) {
                Text("Return to App")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Bar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Admin Foundation",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Real Aggregate Metrics & Telemetry",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> AdminAggregateDashboardTab(
                users = users,
                requests = requests,
                activeUsersCount = (requests.map { it.userId } + conversations.map { it.userId }).distinct().size
            )
            1 -> AdminUsersTab(users)
            2 -> AdminRequestsTab(requests)
            3 -> AdminFeedbackTab(feedbackList)
            4 -> AdminConfigTab(repository)
        }
    }
}

/**
 * Requirement 19:
 * Aggregate metrics without exposing individual private conversation content by default.
 */
@Composable
fun AdminAggregateDashboardTab(
    users: List<UserEntity>,
    requests: List<AIRequestEntity>,
    activeUsersCount: Int
) {
    val totalUsers = users.size
    val totalRequests = requests.size
    val successfulRequests = requests.count { it.status == "completed" }
    val failedRequests = requests.count { it.status == "failed" || it.status == "rate_limited" || it.status == "limit_reached" }

    // Breakdown by Module
    val moduleCounts = requests.groupBy { it.module }.mapValues { it.value.size }
    // Breakdown by Plan
    val planCounts = users.groupBy { it.subscriptionPlan }.mapValues { it.value.size }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "AGGREGATE USAGE & SYSTEM METRICS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Row 1: Users & Active Users
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminMetricCard("Total Registered Users", totalUsers.toString(), Icons.Default.People, Modifier.weight(1f))
                AdminMetricCard("Active Users", activeUsersCount.toString(), Icons.Default.Assessment, Modifier.weight(1f))
            }
        }

        // Row 2: Total Requests, Success, Failed
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminMetricCard("Total AI Requests", totalRequests.toString(), Icons.Default.Chat, Modifier.weight(1f))
                AdminMetricCard("Successful Requests", successfulRequests.toString(), Icons.Default.CheckCircle, Modifier.weight(1f), tint = Color(0xFF10B981))
                AdminMetricCard("Failed / Limited", failedRequests.toString(), Icons.Default.ErrorOutline, Modifier.weight(1f), tint = MaterialTheme.colorScheme.error)
            }
        }

        // Section: Usage by Module
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Layers, contentDescription = null, tint = NexaPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Usage by Module",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (moduleCounts.isEmpty()) {
                        Text(
                            text = "No requests recorded yet across modules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        moduleCounts.forEach { (moduleName, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = moduleName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "$count requests",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = NexaPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Usage by Subscription Plan
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.PieChart, contentDescription = null, tint = NexaPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Users by Subscription Plan",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (planCounts.isEmpty()) {
                        Text(
                            text = "No user plan distributions recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        planCounts.forEach { (planName, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = planName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "$count users",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = NexaPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Privacy & Security Statement
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔒 Privacy & Data Protection Compliance",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Individual user conversation contents are isolated and never listed in bulk admin feeds.\n• Telemetry is aggregated strictly at the request-status, module, and quota levels.\n• Rate limiting and quota rules are enforced server-side.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AdminMetricCard(
    title: String,
    count: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = NexaPrimary
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = count, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AdminUsersTab(users: List<UserEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(users) { user ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.name, fontWeight = FontWeight.Bold)
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusBadge(text = user.subscriptionPlan)
                            StatusBadge(text = user.userType)
                            if (user.isAdmin) StatusBadge(text = "Admin", isActive = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminRequestsTab(requests: List<AIRequestEntity>) {
    val dateFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(requests) { req ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(req.module, fontWeight = FontWeight.Bold, color = NexaPrimary)
                        StatusBadge(
                            text = req.status,
                            isActive = req.status == "completed"
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type: ${req.requestType} • ID: ${req.id.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(Date(req.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun AdminFeedbackTab(feedbackList: List<FeedbackEntity>) {
    val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(feedbackList) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusBadge(text = item.category, isActive = true)
                        Text(dateFormat.format(Date(item.createdAt)), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(item.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun AdminConfigTab(repository: NexaRepository) {
    var config by remember { mutableStateOf(repository.gateway.getConfig()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Gateway Parameters", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Gateway Pipeline Online", fontWeight = FontWeight.Medium)
                            Text("Toggles request dispatching", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = config.isGatewayOnline,
                            onCheckedChange = {
                                val updated = config.copy(isGatewayOnline = it)
                                config = updated
                                repository.updateGatewayConfig(updated)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = config.backendGatewayUrl,
                        onValueChange = {
                            val updated = config.copy(backendGatewayUrl = it)
                            config = updated
                            repository.updateGatewayConfig(updated)
                        },
                        label = { Text("Backend Gateway URL") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = config.primaryProvider,
                        onValueChange = {
                            val updated = config.copy(primaryProvider = it)
                            config = updated
                            repository.updateGatewayConfig(updated)
                        },
                        label = { Text("Primary Provider Model") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
