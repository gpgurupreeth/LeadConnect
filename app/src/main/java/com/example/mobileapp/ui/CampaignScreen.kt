package com.example.mobileapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mobileapp.data.local.entities.CampaignEntity
import com.example.mobileapp.domain.engine.EngineExecutionState
import com.example.mobileapp.ui.theme.AppSpacing
import com.example.mobileapp.ui.theme.AppStatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CampaignScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active Runner, 1 = Create New, 2 = History

    val campaigns by viewModel.campaignsFlow.collectAsState(initial = emptyList())
    val customers by viewModel.customersFlow.collectAsState(initial = emptyList())
    val currentCampaign by viewModel.engine.currentCampaign.collectAsState()
    val engineState by viewModel.engine.engineState.collectAsState()
    val stats by viewModel.engine.engineStats.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()

    var showStopConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg)
    ) {
        Spacer(modifier = Modifier.height(AppSpacing.sm))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Active Runner", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("New Campaign", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("History (${campaigns.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) })
        }

        Spacer(modifier = Modifier.height(AppSpacing.md))

        when (selectedTab) {
            0 -> ActiveRunnerTab(
                campaign = currentCampaign,
                engineState = engineState,
                stats = stats,
                isDemoMode = isDemoMode,
                callingHoursRange = viewModel.engine.callingHoursConfig.formattedRange(),
                onStart = { currentCampaign?.let { viewModel.engine.startCampaign(it.id) } },
                onPause = { viewModel.engine.pauseCampaign() },
                onResume = { viewModel.engine.resumeCampaign() },
                onStopRequest = { showStopConfirmDialog = true },
                onCreateNewClick = { selectedTab = 1 }
            )
            1 -> CreateCampaignTab(
                eligibleCount = customers.count { !it.isSuppressed },
                isDemoMode = isDemoMode,
                onCreate = { name, template, startH, endH, enforce ->
                    viewModel.updateCallingHours(startH, endH, enforce)
                    viewModel.createCampaign(name, template) { id ->
                        viewModel.engine.startCampaign(id)
                        selectedTab = 0
                        Toast.makeText(context, "Campaign '$name' launched!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            2 -> CampaignHistoryTab(
                campaigns = campaigns,
                onSelectCampaign = { camp ->
                    viewModel.engine.startCampaign(camp.id)
                    selectedTab = 0
                }
            )
        }
    }

    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = { Text("Stop & Cancel Campaign?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently halt the active campaign loop. Progress will be recorded, but automatic dialing will stop.") },
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmDialog = false
                        viewModel.engine.stopCampaign()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.errorLight)
                ) {
                    Text("Stop Campaign")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStopConfirmDialog = false }) {
                    Text("Keep Running")
                }
            }
        )
    }
}

@Composable
private fun ActiveRunnerTab(
    campaign: CampaignEntity?,
    engineState: EngineExecutionState,
    stats: com.example.mobileapp.domain.engine.CampaignEngineStats,
    isDemoMode: Boolean,
    callingHoursRange: String,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStopRequest: () -> Unit,
    onCreateNewClick: () -> Unit
) {
    val context = LocalContext.current
    var hasCallPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCallPermission = granted
    }

    if (campaign == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(AppSpacing.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No Campaign In Progress", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        "Set up an outreach campaign with WhatsApp follow-ups to begin sequential SIM dialing.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    Button(
                        onClick = onCreateNewClick,
                        shape = RoundedCornerShape(AppSpacing.buttonRadius),
                        modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTouchTarget)
                    ) {
                        Text("Create New Campaign")
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        // High-Visibility Mode Pill
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDemoMode) AppStatusColors.warningBgLight else AppStatusColors.successBgLight
                ),
                shape = RoundedCornerShape(AppSpacing.cardRadius)
            ) {
                Row(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = if (isDemoMode) "🛡️" else "📱", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDemoMode) "SAFE DEMO MODE ACTIVE" else "LIVE SIM CELLULAR CALLING",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDemoMode) AppStatusColors.warningLight else AppStatusColors.successLight
                        )
                        Text(
                            text = if (isDemoMode) "Simulates telecom states safely without carrier costs" else "Dials through device SIM. Speak with lead personally.",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }

        // Permission & Compliance Warning Banner (if live calling without permissions)
        if (!isDemoMode && !hasCallPermission) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppStatusColors.errorBgLight),
                    shape = RoundedCornerShape(AppSpacing.buttonRadius)
                ) {
                    Row(
                        modifier = Modifier.padding(AppSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Telephony Permission Required", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppStatusColors.errorLight)
                            Text("Grant CALL_PHONE permission to automate SIM dialing without opening dialpad.", fontSize = 11.sp, color = Color.DarkGray)
                        }
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CALL_PHONE) },
                            colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.errorLight),
                            shape = RoundedCornerShape(AppSpacing.buttonRadius)
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }
        }

        // Calling Hours Notice Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(AppSpacing.buttonRadius)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⏰", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                    Text(
                        text = "Permitted Calling Hours Window: $callingHoursRange",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Campaign Progress Card
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
                                text = campaign.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Follow-Up Template: ${campaign.messageTemplate ?: "None"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RunnerStateBadge(engineState)
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.md))

                    val total = if (campaign.totalContacts > 0) campaign.totalContacts else 1
                    val progress = (campaign.completedContacts.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${campaign.completedContacts} of ${campaign.totalContacts} leads contacted",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Live Telemetry Metric Grid
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppSpacing.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricBox("Attempted", "${stats.callsAttempted}", MaterialTheme.colorScheme.primary)
                    MetricBox("Answered", "${stats.callsAnswered}", AppStatusColors.successLight)
                    MetricBox("Unresolved", "${stats.callsUnresolved}", AppStatusColors.warningLight)
                    MetricBox("Suppressed", "${stats.callsSuppressed}", AppStatusColors.suppressedLight)
                    MetricBox("WA Queued", "${stats.messagesQueued}", AppStatusColors.infoLight)
                }
            }
        }

        // Active In-Call Workflow Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppSpacing.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    Text(
                        text = "ACTIVE LEAD WORKFLOW",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))

                    when (engineState) {
                        is EngineExecutionState.Calling -> {
                            Text(
                                text = "Dialing: ${engineState.customer.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${engineState.customer.phoneNumber} • ${engineState.customer.company ?: "Direct Lead"}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.xs))
                            Text(
                                text = "Simulating or dialing call. Speak with lead personally.",
                                fontSize = 12.sp,
                                color = AppStatusColors.successLight
                            )
                        }
                        is EngineExecutionState.AwaitingConfirmation -> {
                            Text(
                                text = "Awaiting Outcome: ${engineState.customer.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AppStatusColors.warningLight
                            )
                            Text(
                                text = "${engineState.customer.phoneNumber}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.xs))
                            Text(
                                text = "Modal confirmation dialog open. Select outcome to automatically advance.",
                                fontSize = 12.sp,
                                color = AppStatusColors.warningLight
                            )
                        }
                        is EngineExecutionState.Paused -> {
                            Text(
                                text = "Campaign Paused",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = AppStatusColors.errorLight
                            )
                            Text(
                                text = "Reason: ${engineState.reason}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is EngineExecutionState.Completed -> {
                            Text(
                                text = "Campaign Finished! 🎉",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = AppStatusColors.successLight
                            )
                            Text(
                                text = "All eligible non-suppressed contacts have been processed.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            Text(
                                text = "Ready to start sequential SIM dialing.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Campaign Control Buttons (Start, Pause, Resume, Stop)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                when (engineState) {
                    is EngineExecutionState.Running, is EngineExecutionState.Calling, is EngineExecutionState.AwaitingConfirmation -> {
                        Button(
                            onClick = onPause,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                            colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.warningLight),
                            shape = RoundedCornerShape(AppSpacing.buttonRadius)
                        ) {
                            Text("Pause Campaign")
                        }
                    }
                    is EngineExecutionState.Paused -> {
                        Button(
                            onClick = onResume,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                            colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.successLight),
                            shape = RoundedCornerShape(AppSpacing.buttonRadius)
                        ) {
                            Text("Resume Campaign")
                        }
                    }
                    else -> {
                        Button(
                            onClick = onStart,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                            enabled = engineState !is EngineExecutionState.Completed,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(AppSpacing.buttonRadius)
                        ) {
                            Text("Start Campaign")
                        }
                    }
                }

                OutlinedButton(
                    onClick = onStopRequest,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                    shape = RoundedCornerShape(AppSpacing.buttonRadius),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppStatusColors.errorLight)
                ) {
                    Text("Stop / Cancel")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(AppSpacing.lg)) }
    }
}

@Composable
private fun CreateCampaignTab(
    eligibleCount: Int,
    isDemoMode: Boolean,
    onCreate: (name: String, template: String, startH: Int, endH: Int, enforce: Boolean) -> Unit
) {
    var campaignName by remember { mutableStateOf("Q4 Outbound Sales Outreach") }
    var selectedTemplate by remember { mutableStateOf("welcome_followup") }
    var startHour by remember { mutableIntStateOf(9) }
    var endHour by remember { mutableIntStateOf(18) }
    var enforceCallingHours by remember { mutableStateOf(true) }
    var maxRetries by remember { mutableIntStateOf(1) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        item {
            Text(
                text = "Configure New Campaign",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            OutlinedTextField(
                value = campaignName,
                onValueChange = { campaignName = it },
                label = { Text("Campaign Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(AppSpacing.buttonRadius)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppSpacing.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(AppSpacing.md)) {
                    Text("Audience Eligibility", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "$eligibleCount eligible leads in database will be queued. Contacts on DND list are excluded.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // WhatsApp Follow-up Template Selection
        item {
            Text("Automated WhatsApp Follow-Up", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                FilterChip(
                    selected = selectedTemplate == "welcome_followup",
                    onClick = { selectedTemplate = "welcome_followup" },
                    label = { Text("Welcome", fontSize = 11.sp) },
                    shape = RoundedCornerShape(AppSpacing.pillRadius)
                )
                FilterChip(
                    selected = selectedTemplate == "thank_you_meeting",
                    onClick = { selectedTemplate = "thank_you_meeting" },
                    label = { Text("Meeting Thanks", fontSize = 11.sp) },
                    shape = RoundedCornerShape(AppSpacing.pillRadius)
                )
                FilterChip(
                    selected = selectedTemplate == "missed_call_followup",
                    onClick = { selectedTemplate = "missed_call_followup" },
                    label = { Text("Missed Call", fontSize = 11.sp) },
                    shape = RoundedCornerShape(AppSpacing.pillRadius)
                )
            }
        }

        // Permitted Calling Hours
        item {
            Text("Permitted Calling Hours", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = enforceCallingHours,
                    onCheckedChange = { enforceCallingHours = it }
                )
                Spacer(modifier = Modifier.width(AppSpacing.xs))
                Text("Enforce Allowed Window (09:00 - 18:00)", fontSize = 12.sp)
            }
        }

        // Retry Policy
        item {
            Text("Retry Policy for Unanswered Calls", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                FilterChip(selected = maxRetries == 1, onClick = { maxRetries = 1 }, label = { Text("1 Attempt", fontSize = 11.sp) }, shape = RoundedCornerShape(AppSpacing.pillRadius))
                FilterChip(selected = maxRetries == 2, onClick = { maxRetries = 2 }, label = { Text("2 Retries", fontSize = 11.sp) }, shape = RoundedCornerShape(AppSpacing.pillRadius))
                FilterChip(selected = maxRetries == 3, onClick = { maxRetries = 3 }, label = { Text("3 Retries", fontSize = 11.sp) }, shape = RoundedCornerShape(AppSpacing.pillRadius))
            }
        }

        item {
            Spacer(modifier = Modifier.height(AppSpacing.sm))
            Button(
                onClick = {
                    onCreate(campaignName, selectedTemplate, startHour, endHour, enforceCallingHours)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                enabled = campaignName.isNotBlank() && eligibleCount > 0,
                shape = RoundedCornerShape(AppSpacing.buttonRadius)
            ) {
                Text("Launch Campaign ($eligibleCount Leads)")
            }
            Spacer(modifier = Modifier.height(AppSpacing.lg))
        }
    }
}

@Composable
private fun CampaignHistoryTab(
    campaigns: List<CampaignEntity>,
    onSelectCampaign: (CampaignEntity) -> Unit
) {
    if (campaigns.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No past campaigns in Room database", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        items(campaigns) { camp ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppSpacing.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(camp.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "Status: ${camp.status} • Completed: ${camp.completedContacts}/${camp.totalContacts}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(camp.createdAt))
                        Text(text = "Created: $dateStr", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }

                    Button(
                        onClick = { onSelectCampaign(camp) },
                        shape = RoundedCornerShape(AppSpacing.buttonRadius),
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                    ) {
                        Text("Open", fontSize = 12.sp)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(AppSpacing.md)) }
    }
}

@Composable
private fun MetricBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RunnerStateBadge(state: EngineExecutionState) {
    val (bg, text, textColor) = when (state) {
        is EngineExecutionState.Running -> Triple(AppStatusColors.successBgLight, "RUNNING", AppStatusColors.successLight)
        is EngineExecutionState.Calling -> Triple(AppStatusColors.infoBgLight, "DIALING", AppStatusColors.infoLight)
        is EngineExecutionState.AwaitingConfirmation -> Triple(AppStatusColors.warningBgLight, "CONFIRM OUTCOME", AppStatusColors.warningLight)
        is EngineExecutionState.Paused -> Triple(AppStatusColors.errorBgLight, "PAUSED", AppStatusColors.errorLight)
        is EngineExecutionState.Completed -> Triple(AppStatusColors.successBgLight, "COMPLETED", AppStatusColors.successLight)
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, "IDLE", MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(shape = RoundedCornerShape(AppSpacing.pillRadius), color = bg) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
        )
    }
}
