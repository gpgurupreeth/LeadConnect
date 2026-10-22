package com.example.mobileapp.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.domain.engine.EngineExecutionState
import com.example.mobileapp.ui.theme.AppSpacing
import com.example.mobileapp.ui.theme.AppStatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onNavigateToTab: (Int) -> Unit // 0=Home, 1=Contacts, 2=Campaign, 3=Queue, 4=Settings
) {
    val context = LocalContext.current
    val customers by viewModel.customersFlow.collectAsState(initial = emptyList())
    val jobs by viewModel.messageJobsFlow.collectAsState(initial = emptyList())
    val auditEvents by viewModel.auditEventsFlow.collectAsState(initial = emptyList())
    val currentCampaign by viewModel.engine.currentCampaign.collectAsState()
    val engineState by viewModel.engine.engineState.collectAsState()
    val engineStats by viewModel.engine.engineStats.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()

    val pendingJobs = jobs.count { it.status == "PENDING" }
    val sentJobs = jobs.count { it.status == "SENT" }
    val failedJobs = jobs.count { it.status == "FAILED" }

    val totalContacts = currentCampaign?.totalContacts ?: customers.size
    val completedContacts = currentCampaign?.completedContacts ?: engineStats.callsAttempted
    val remainingContacts = (totalContacts - completedContacts).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        item { Spacer(modifier = Modifier.height(AppSpacing.xs)) }

        // 1. Calling Mode Indicator Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDemoMode) AppStatusColors.warningBgLight else AppStatusColors.successBgLight
                ),
                shape = RoundedCornerShape(AppSpacing.cardRadius)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDemoMode) "🛡️" else "📱",
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Column {
                            Text(
                                text = if (isDemoMode) "Safe Demo Mode Active" else "Live Cellular SIM Mode Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isDemoMode) AppStatusColors.warningLight else AppStatusColors.successLight
                            )
                            Text(
                                text = if (isDemoMode) "Simulates dialing safely without cellular airtime" else "Outgoing calls use physical SIM card on device",
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }

                    Switch(
                        checked = isDemoMode,
                        onCheckedChange = { viewModel.setDemoMode(it) }
                    )
                }
            }
        }

        // 2. Active Campaign Status & Primary Action Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppSpacing.cardRadius),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Campaign",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentCampaign?.name ?: "No Campaign Selected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(AppSpacing.pillRadius),
                            color = when (engineState) {
                                is EngineExecutionState.Running -> AppStatusColors.successBgLight
                                is EngineExecutionState.Calling -> AppStatusColors.infoBgLight
                                is EngineExecutionState.AwaitingConfirmation -> AppStatusColors.warningBgLight
                                is EngineExecutionState.Paused -> AppStatusColors.errorBgLight
                                is EngineExecutionState.Completed -> AppStatusColors.successBgLight
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = when (engineState) {
                                    is EngineExecutionState.Running -> "RUNNING"
                                    is EngineExecutionState.Calling -> "DIALING"
                                    is EngineExecutionState.AwaitingConfirmation -> "CONFIRM OUTCOME"
                                    is EngineExecutionState.Paused -> "PAUSED"
                                    is EngineExecutionState.Completed -> "COMPLETED"
                                    else -> "IDLE"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                                color = when (engineState) {
                                    is EngineExecutionState.Running -> AppStatusColors.successLight
                                    is EngineExecutionState.Calling -> AppStatusColors.infoLight
                                    is EngineExecutionState.AwaitingConfirmation -> AppStatusColors.warningLight
                                    is EngineExecutionState.Paused -> AppStatusColors.errorLight
                                    is EngineExecutionState.Completed -> AppStatusColors.successLight
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.md))

                    if (currentCampaign != null) {
                        val progress = if (totalContacts > 0) (completedContacts.toFloat() / totalContacts.toFloat()).coerceIn(0f, 1f) else 0f

                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(AppSpacing.xs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$completedContacts completed • $remainingContacts remaining",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.md))

                        // Dynamic Primary Action
                        when (engineState) {
                            is EngineExecutionState.Paused -> {
                                Button(
                                    onClick = { viewModel.engine.resumeCampaign() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(AppSpacing.buttonRadius)
                                ) {
                                    Text("Resume Campaign")
                                }
                            }
                            is EngineExecutionState.AwaitingConfirmation -> {
                                Button(
                                    onClick = { onNavigateToTab(2) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.warningLight),
                                    shape = RoundedCornerShape(AppSpacing.buttonRadius)
                                ) {
                                    Text("Confirm Pending Call Outcome")
                                }
                            }
                            is EngineExecutionState.Running, is EngineExecutionState.Calling -> {
                                Button(
                                    onClick = { onNavigateToTab(2) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                                    shape = RoundedCornerShape(AppSpacing.buttonRadius)
                                ) {
                                    Text("View Live Call Progress")
                                }
                            }
                            else -> {
                                Button(
                                    onClick = { onNavigateToTab(2) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                                    shape = RoundedCornerShape(AppSpacing.buttonRadius)
                                ) {
                                    Text("Open Campaign Runner")
                                }
                            }
                        }
                    } else {
                        // Empty campaign state
                        Text(
                            text = if (customers.isEmpty()) "No contacts in database yet. Seed or import contacts to begin." else "${customers.size} contacts available for outreach. Create a campaign to start calling.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(AppSpacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            if (customers.isEmpty()) {
                                Button(
                                    onClick = {
                                        viewModel.seedDemoContacts {
                                            Toast.makeText(context, "Seeded 5 sample leads", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                                    shape = RoundedCornerShape(AppSpacing.buttonRadius)
                                ) {
                                    Text("Seed Leads")
                                }
                                OutlinedButton(
                                    onClick = { onNavigateToTab(1) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                                    shape = RoundedCornerShape(AppSpacing.buttonRadius)
                                ) {
                                    Text("Import CSV")
                                }
                            } else {
                                Button(
                                    onClick = { onNavigateToTab(2) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                                    shape = RoundedCornerShape(AppSpacing.buttonRadius)
                                ) {
                                    Text("Launch New Campaign")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Telephony & Outcome Statistics Section
        item {
            Text(
                text = "Telephony & Outcomes",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Attempted",
                    value = "${engineStats.callsAttempted}",
                    color = MaterialTheme.colorScheme.primary
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Answered",
                    value = "${engineStats.callsAnswered}",
                    color = AppStatusColors.successLight
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Unresolved",
                    value = "${engineStats.callsUnresolved}",
                    color = AppStatusColors.warningLight
                )
            }
        }

        // 4. WhatsApp Dispatch Queue Summary Section
        item {
            Text(
                text = "WhatsApp Follow-Up Pipeline",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Pending",
                    value = "$pendingJobs",
                    color = AppStatusColors.warningLight
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Accepted / Sent",
                    value = "$sentJobs",
                    color = AppStatusColors.successLight
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Gated / Failed",
                    value = "$failedJobs",
                    color = AppStatusColors.errorLight
                )
            }
        }

        // 5. Recent Activity Audit Trail
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity Audit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (auditEvents.isNotEmpty()) {
                    Text(
                        text = "Last 6 events",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (auditEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppSpacing.cardRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recorded events yet. Activities appear here in real time.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(auditEvents.take(6)) { event ->
                CompactAuditItem(event)
            }
        }

        item { Spacer(modifier = Modifier.height(AppSpacing.lg)) }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(AppSpacing.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactAuditItem(event: AuditEventEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppSpacing.buttonRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.eventType.replace("_", " "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = event.details,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))
            Text(
                text = timeStr,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
