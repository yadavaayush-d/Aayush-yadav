package com.example.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.NexaRepository
import com.example.domain.ai.PlanTier
import com.example.ui.components.StatusBadge
import com.example.ui.theme.NexaPrimary
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    repository: NexaRepository,
    onNavigateToSettings: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()

    val entitlementStatus by (currentUser?.let { repository.getUsageEntitlementStatus(it.id) }
        ?: flowOf(null)).collectAsState(initial = null)
    val subscription by (currentUser?.let { repository.getSubscription(it.id) }
        ?: flowOf(null)).collectAsState(initial = null)

    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var showDataControlsDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Top App Bar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "Profile & Account",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Manage your preferences, plans, and security",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Profile Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(NexaPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = currentUser?.name?.split(" ")
                            ?.mapNotNull { it.firstOrNull()?.toString() }
                            ?.take(2)?.joinToString("") ?: "N"
                        Text(
                            text = initials.uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = currentUser?.name ?: "User",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = currentUser?.email ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusBadge(text = currentUser?.userType ?: "General User", isActive = true)
                            StatusBadge(text = currentUser?.preferredLanguage ?: "English")
                            if (currentUser?.isAdmin == true) {
                                StatusBadge(text = "Admin", isActive = true)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))

                // Plan & Usage Meter (Requirements 9 & 10)
                val currentPlan = entitlementStatus?.planTier?.displayName ?: currentUser?.subscriptionPlan ?: "Free Tier"
                val used = entitlementStatus?.usedThisPeriod ?: 0
                val limit = entitlementStatus?.monthlyMessageLimit ?: 50
                val remaining = entitlementStatus?.remainingThisPeriod ?: (limit - used).coerceAtLeast(0)
                val progress = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f

                val resetDateMs = subscription?.renewalDate ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val resetDateString = dateFormat.format(Date(resetDateMs))
                val daysUntilReset = ((resetDateMs - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).coerceAtLeast(0)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CURRENT PLAN",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentPlan,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = NexaPrimary
                        )
                    }
                    OutlinedButton(
                        onClick = { showSubscriptionDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("upgrade_plan_button")
                    ) {
                        Text("Upgrade")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = if (progress >= 1f) MaterialTheme.colorScheme.error else NexaPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AI Usage: $used / $limit messages used",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$remaining remaining",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (remaining == 0) MaterialTheme.colorScheme.error else NexaPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Billing cycle resets on $resetDateString ($daysUntilReset days remaining)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Action Rows
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                ProfileMenuRow(
                    icon = Icons.Default.Edit,
                    title = "Edit Profile",
                    onClick = onNavigateToSettings
                )
                ProfileMenuRow(
                    icon = Icons.Default.CreditCard,
                    title = "Manage Subscriptions",
                    onClick = { showSubscriptionDialog = true }
                )
                ProfileMenuRow(
                    icon = Icons.Default.Storage,
                    title = "Data Controls & Privacy",
                    onClick = { showDataControlsDialog = true }
                )
                ProfileMenuRow(
                    icon = Icons.Default.Help,
                    title = "Help & Support",
                    onClick = { showHelpDialog = true }
                )
                ProfileMenuRow(
                    icon = Icons.Default.Security,
                    title = "Privacy Policy",
                    onClick = { showPrivacyDialog = true }
                )

                if (currentUser?.isAdmin == true) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                    ProfileMenuRow(
                        icon = Icons.Default.AdminPanelSettings,
                        title = "Admin Foundation",
                        subtitle = "Telemetry, Metrics & Engine Controls",
                        onClick = onNavigateToAdmin,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                ProfileMenuRow(
                    icon = Icons.Default.Logout,
                    title = "Sign Out",
                    onClick = onLogout,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // Subscription & Plan Upgrade Dialog
    if (showSubscriptionDialog) {
        AlertDialog(
            onDismissRequest = { showSubscriptionDialog = false },
            title = { Text("Nexa AI Plans & Entitlements") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose your plan tier to unlock additional AI message capacity and higher rate limits:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    PlanOptionCard(
                        title = "Free Tier",
                        limits = "50 messages / month • 10 req/min",
                        isSelected = entitlementStatus?.planTier == PlanTier.FREE,
                        onSelect = {
                            scope.launch {
                                repository.upgradeSubscriptionPlan(PlanTier.FREE)
                                showSubscriptionDialog = false
                                Toast.makeText(context, "Active plan set to Free Tier", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    PlanOptionCard(
                        title = "Plus Brain",
                        limits = "500 messages / month • 30 req/min",
                        isSelected = entitlementStatus?.planTier == PlanTier.PLUS,
                        onSelect = {
                            scope.launch {
                                repository.upgradeSubscriptionPlan(PlanTier.PLUS)
                                showSubscriptionDialog = false
                                Toast.makeText(context, "Upgraded to Plus Brain!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    PlanOptionCard(
                        title = "Pro Brain",
                        limits = "2,500 messages / month • 60 req/min",
                        isSelected = entitlementStatus?.planTier == PlanTier.PRO,
                        onSelect = {
                            scope.launch {
                                repository.upgradeSubscriptionPlan(PlanTier.PRO)
                                showSubscriptionDialog = false
                                Toast.makeText(context, "Upgraded to Pro Brain!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubscriptionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Data Controls Dialog
    if (showDataControlsDialog) {
        AlertDialog(
            onDismissRequest = { showDataControlsDialog = false },
            title = { Text("Data Controls & Privacy") },
            text = {
                Text("• Conversations are encrypted and stored in local Room DB.\n• Zero API keys are stored on the client.\n• Model training opt-out is enabled by default.\n• You can delete your chats at any time from the chat menu or history.")
            },
            confirmButton = {
                TextButton(onClick = { showDataControlsDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help & Documentation") },
            text = {
                Text("Nexa AI: One Brain, Multiple Specialized Workspaces.\n\nTo start a chat, select a workspace chip (Study, Creator, Documents, Language) or start in General AI.\n\nAll AI requests pass through our secure backend orchestrator.")
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy") },
            text = {
                Text("We strictly respect user privacy. We do not sell your personal data or conversation records. AI processing is conducted securely via our backend orchestrator with no client-side secret exposure.")
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun PlanOptionCard(
    title: String,
    limits: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) NexaPrimary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, NexaPrimary) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) NexaPrimary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = limits,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                StatusBadge(text = "Active", isActive = true)
            }
        }
    }
}

@Composable
fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
