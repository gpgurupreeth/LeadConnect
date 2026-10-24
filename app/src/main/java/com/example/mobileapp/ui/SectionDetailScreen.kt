package com.example.mobileapp.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileapp.data.local.entities.CallAttemptEntity
import com.example.mobileapp.data.local.entities.CampaignEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.ui.components.AppPillTabBar
import com.example.mobileapp.ui.components.TabItem
import com.example.mobileapp.ui.theme.AppSpacing
import com.example.mobileapp.ui.theme.AppStatusColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionDetailScreen(
    section: CampaignEntity,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val leads by viewModel.getCustomersForSection(section.id).collectAsState(initial = emptyList())
    val history by viewModel.getAttemptsForSection(section.id).collectAsState(initial = emptyList())

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Dialer, 1: Leads, 2: History
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddLeadDialog by remember { mutableStateOf(false) }
    var leadToDelete by remember { mutableStateOf<CustomerEntity?>(null) }

    // Multi-Select & Delete for Leads (Screenshot 2)
    var isLeadsSelectMode by remember { mutableStateOf(false) }
    val selectedLeadIds = remember { mutableStateListOf<Long>() }
    var showDeleteSelectedLeadsDialog by remember { mutableStateOf(false) }

    // Section specific counts & mini dashboard metrics
    val pendingLeads = leads.filter { (it.callStatus == "NOT_CALLED" || it.callStatus.isBlank()) && !it.isCleared && !it.isSuppressed }
    val remainingCount = pendingLeads.size
    val noAnswerCount = leads.count { it.callStatus == "NO_ANSWER" }
    val answeredCount = leads.count { it.callStatus in listOf("INTERESTED", "CALLBACK", "ANSWERED", "NOT_INTERESTED", "WRONG_NUMBER") || (it.isCleared && it.callStatus != "NO_ANSWER") }

    // Dialer pointer
    var currentLeadIndex by remember { mutableIntStateOf(0) }
    val currentLead = pendingLeads.getOrNull(currentLeadIndex.coerceIn(0, (pendingLeads.size - 1).coerceAtLeast(0)))

    var noteText by remember { mutableStateOf("") }

    // Automatic Sequential Calling with Pause (Screenshot 3)
    var isAutoCallingActive by remember { mutableStateOf(false) }
    var isAutoCallingPaused by remember { mutableStateOf(false) }
    var autoCallCountdown by remember { mutableIntStateOf(0) }

    // 1. Auto-dial countdown timer
    LaunchedEffect(autoCallCountdown, isAutoCallingPaused, isAutoCallingActive) {
        if (isAutoCallingActive && !isAutoCallingPaused && autoCallCountdown > 0) {
            delay(1000L)
            autoCallCountdown -= 1
            if (autoCallCountdown == 0) {
                // Automatically dial the current pending lead
                val targetLead = pendingLeads.getOrNull(currentLeadIndex) ?: pendingLeads.firstOrNull()
                if (targetLead != null) {
                    val ok = viewModel.dialCustomer(targetLead)
                    if (ok) {
                        Toast.makeText(context, "Auto-Calling ${targetLead.name}...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    isAutoCallingActive = false
                    Toast.makeText(context, "All pending leads completed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 2. Telephony completion listener: when call ends, auto-advance and schedule next call
    LaunchedEffect(isAutoCallingActive, isAutoCallingPaused) {
        viewModel.callFinishedEvent.collect { obs ->
            if (isAutoCallingActive && !isAutoCallingPaused) {
                currentLead?.let { lead ->
                    if (!lead.isCleared && lead.callStatus == "NOT_CALLED") {
                        val outcome = if (obs.wasOffhook && obs.durationSeconds > 5) "ANSWERED" else "NO_ANSWER"
                        viewModel.recordCallOutcome(lead, outcome, "Auto-recorded call (${obs.durationSeconds}s)")
                    }
                }
                if (currentLeadIndex < pendingLeads.size - 1) {
                    currentLeadIndex++
                    autoCallCountdown = 5
                } else {
                    isAutoCallingActive = false
                    Toast.makeText(context, "All pending leads completed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. Top Minimalist Header Row (Matching Dribbble layout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Minimal Circular Back Button
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF09090B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.4).sp,
                        color = Color(0xFF09090B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!section.genre.isNullOrBlank()) {
                        Text(
                            text = section.genre,
                            fontSize = 11.5.sp,
                            color = Color(0xFF71717A),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Minimal Circular Share Button
                Surface(
                    onClick = { viewModel.shareSectionCsvAsFile(context, section) },
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Excel Sheet",
                            tint = Color(0xFF09090B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Minimal Circular Delete Button
                Surface(
                    onClick = { showDeleteConfirm = true },
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Section",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Floating Segmented Pill Tab Bar (Matching Format)
        AppPillTabBar(
            tabs = listOf(
                TabItem(title = "Dialer", iconPrefix = "📞"),
                TabItem(title = "Leads", count = leads.size, iconPrefix = "👥"),
                TabItem(title = "History", count = history.size, iconPrefix = "📊")
            ),
            selectedIndex = selectedSubTab,
            onTabSelected = {
                selectedSubTab = it
                if (it != 1) isLeadsSelectMode = false
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Section KPI Metric Strip Cards (Matching Format)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionMetricItem("Total Leads", "${leads.size}", Color.Black, Modifier.weight(1f))
            SectionMetricItem("Answered", "$answeredCount", AppStatusColors.successLight, Modifier.weight(1f))
            SectionMetricItem("No Answer", "$noAnswerCount", Color(0xFFD97706), Modifier.weight(1f))
            SectionMetricItem("Remaining", "$remainingCount", AppStatusColors.brandBlue, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Tab Body
        when (selectedSubTab) {
            0 -> {
                // DIALER SUB-VIEW
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (currentLead != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.5.dp, Color(0xFFBAE6FD)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(AppSpacing.pillRadius),
                                        color = Color(0xFFE0F2FE),
                                        border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                                    ) {
                                        Text(
                                            text = "Lead ${currentLeadIndex + 1} of ${pendingLeads.size}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppStatusColors.brandBlue,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(AppSpacing.pillRadius),
                                        color = if (currentLead.isVerified) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                                    ) {
                                        Text(
                                            text = if (currentLead.isVerified) "☀️ Number Verified" else "⚠️ Unverified",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentLead.isVerified) Color(0xFF16A34A) else Color(0xFFD97706),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    text = currentLead.name,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp,
                                    color = Color(0xFF09090B)
                                )

                                Text(
                                    text = currentLead.phoneNumber,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppStatusColors.brandBlue
                                )

                                if (!currentLead.company.isNullOrBlank()) {
                                    Text(
                                        text = "🏢 ${currentLead.company}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Auto Sequential Calling Control Banner (Clean Blue & White)
                                if (!isAutoCallingActive) {
                                    Surface(
                                        onClick = {
                                            isAutoCallingActive = true
                                            isAutoCallingPaused = false
                                            val ok = viewModel.dialCustomer(currentLead)
                                            if (ok) {
                                                Toast.makeText(context, "Auto-Calling ${currentLead.phoneNumber}...", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFFF0F9FF),
                                        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "⚡", fontSize = 15.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Auto Sequential Calling",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(AppSpacing.pillRadius),
                                                color = AppStatusColors.brandBlue
                                            ) {
                                                Text(
                                                    text = "START AUTO",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                } else if (!isAutoCallingPaused) {
                                    // Active & Running state with PAUSE button
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFFEFF6FF),
                                        border = BorderStroke(1.5.dp, AppStatusColors.brandBlue),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(text = "⚡", fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "AUTO-CALLING ACTIVE",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = AppStatusColors.brandBlue
                                                    )
                                                }
                                                Text(
                                                    text = if (autoCallCountdown > 0) "Next lead in ${autoCallCountdown}s..." else "Sequence progressing...",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF1E293B)
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // PAUSE BUTTON
                                                Button(
                                                    onClick = {
                                                        isAutoCallingPaused = true
                                                        Toast.makeText(context, "Auto-calling sequence PAUSED", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Text(
                                                        text = "⏸ Pause",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }

                                                // STOP BUTTON
                                                Surface(
                                                    onClick = {
                                                        isAutoCallingActive = false
                                                        isAutoCallingPaused = false
                                                        autoCallCountdown = 0
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFFDC2626),
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("⏹", fontSize = 13.sp, color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Active & PAUSED state with RESUME button
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(text = "⏸", fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "AUTO-CALLING PAUSED",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFF334155)
                                                    )
                                                }
                                                Text(
                                                    text = "Sequence paused. Tap Resume to continue.",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = Color(0xFF64748B)
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // RESUME BUTTON
                                                Button(
                                                    onClick = {
                                                        isAutoCallingPaused = false
                                                        Toast.makeText(context, "Resuming auto-calling...", Toast.LENGTH_SHORT).show()
                                                        val ok = viewModel.dialCustomer(currentLead)
                                                        if (ok) {
                                                            Toast.makeText(context, "Dialing ${currentLead.phoneNumber}...", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Text(
                                                        text = "▶ Resume",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }

                                                // STOP BUTTON
                                                Surface(
                                                    onClick = {
                                                        isAutoCallingActive = false
                                                        isAutoCallingPaused = false
                                                        autoCallCountdown = 0
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFFDC2626),
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("⏹", fontSize = 13.sp, color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Vibrant Call Action Button
                                Button(
                                    onClick = {
                                        val ok = viewModel.dialCustomer(currentLead)
                                        if (ok) {
                                            Toast.makeText(context, "Dialing ${currentLead.phoneNumber}...", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.brandBlue),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "Call",
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Call Now (${currentLead.phoneNumber})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Outcome Logging & Notes Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Log Call Outcome",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF09090B)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SectionOutcomeBtn(
                                        label = "Interested",
                                        color = AppStatusColors.successLight,
                                        bgColor = AppStatusColors.successBgLight,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        viewModel.recordCallOutcome(currentLead, "INTERESTED", noteText)
                                        noteText = ""
                                        if (isAutoCallingActive && !isAutoCallingPaused) {
                                            if (currentLeadIndex < pendingLeads.size - 1) currentLeadIndex++
                                            autoCallCountdown = 5
                                        }
                                    }

                                    SectionOutcomeBtn(
                                        label = "Call Back",
                                        color = AppStatusColors.warningLight,
                                        bgColor = AppStatusColors.warningBgLight,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        viewModel.recordCallOutcome(currentLead, "CALLBACK", noteText)
                                        noteText = ""
                                        if (isAutoCallingActive && !isAutoCallingPaused) {
                                            if (currentLeadIndex < pendingLeads.size - 1) currentLeadIndex++
                                            autoCallCountdown = 5
                                        }
                                    }

                                    SectionOutcomeBtn(
                                        label = "No Answer",
                                        color = Color(0xFFD97706),
                                        bgColor = Color(0xFFFEF3C7),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        viewModel.recordCallOutcome(currentLead, "NO_ANSWER", noteText)
                                        noteText = ""
                                        if (isAutoCallingActive && !isAutoCallingPaused) {
                                            if (currentLeadIndex < pendingLeads.size - 1) currentLeadIndex++
                                            autoCallCountdown = 5
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SectionOutcomeBtn(
                                        label = "Not Interested",
                                        color = AppStatusColors.errorLight,
                                        bgColor = AppStatusColors.errorBgLight,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        viewModel.recordCallOutcome(currentLead, "NOT_INTERESTED", noteText)
                                        noteText = ""
                                        if (isAutoCallingActive && !isAutoCallingPaused) {
                                            if (currentLeadIndex < pendingLeads.size - 1) currentLeadIndex++
                                            autoCallCountdown = 5
                                        }
                                    }

                                    SectionOutcomeBtn(
                                        label = "Wrong Number",
                                        color = Color(0xFF4B5563),
                                        bgColor = Color(0xFFF3F4F6),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        viewModel.recordCallOutcome(currentLead, "WRONG_NUMBER", noteText)
                                        noteText = ""
                                        if (isAutoCallingActive && !isAutoCallingPaused) {
                                            if (currentLeadIndex < pendingLeads.size - 1) currentLeadIndex++
                                            autoCallCountdown = 5
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = noteText,
                                    onValueChange = { noteText = it },
                                    label = { Text("Call Notes (e.g. Price quoted, callback at 4pm)", color = Color.Black) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    OutlinedButton(
                                        onClick = { if (currentLeadIndex > 0) currentLeadIndex-- },
                                        enabled = currentLeadIndex > 0,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("⮜ Prev Lead", color = Color.Black)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (currentLeadIndex < pendingLeads.size - 1) {
                                                currentLeadIndex++
                                                if (isAutoCallingActive && !isAutoCallingPaused) autoCallCountdown = 5
                                            }
                                        },
                                        enabled = currentLeadIndex < pendingLeads.size - 1,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Skip ⮞", color = Color.Black)
                                    }
                                }
                            }
                        }
                    } else {
                        // All leads cleared in this section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = AppSpacing.lg),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFF0EBE1))
                        ) {
                            Column(
                                modifier = Modifier.padding(AppSpacing.xxl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Done, contentDescription = "Done", tint = AppStatusColors.successLight, modifier = Modifier.size(52.dp))
                                Spacer(modifier = Modifier.height(AppSpacing.sm))
                                Text("🎉 All Leads in Section Completed!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("You have completed calling all ${leads.size} leads in '${section.name}'.", fontSize = 13.sp, color = Color.Black, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(60.dp))
                }
            }

            1 -> {
                // LEADS IN SECTION SUB-VIEW (with Add Lead & Select and Delete Functionality)
                var leadsFilter by remember { mutableStateOf("ALL") }
                val filteredSectionLeads = leads.filter { cust ->
                    when (leadsFilter) {
                        "NOT_CALLED" -> (cust.callStatus == "NOT_CALLED" || cust.callStatus.isBlank()) && !cust.isCleared && !cust.isSuppressed
                        "CLEARED" -> cust.callStatus in listOf("INTERESTED", "CALLBACK", "ANSWERED", "NOT_INTERESTED", "WRONG_NUMBER") || (cust.isCleared && cust.callStatus != "NO_ANSWER")
                        "NO_ANSWER" -> cust.callStatus == "NO_ANSWER"
                        "DND" -> cust.isSuppressed
                        else -> true
                    }
                }

                // Strictly sort: Top = Remaining leads, Middle = Unreceived calls, Bottom = Answered calls
                val sortedSectionLeads = remember(filteredSectionLeads) {
                    filteredSectionLeads.sortedWith(
                        compareBy<CustomerEntity> { cust ->
                            when {
                                (cust.callStatus == "NOT_CALLED" || cust.callStatus.isBlank()) && !cust.isCleared && !cust.isSuppressed -> 0 // Top: Remaining
                                cust.callStatus == "NO_ANSWER" -> 1 // Middle: Unreceived
                                else -> 2 // Bottom: Answered
                            }
                        }.thenBy { it.id }
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Filter Chips + Select & Delete Toggle + Add Lead Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isLeadsSelectMode) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                item { FilterChip(selected = leadsFilter == "ALL", onClick = { leadsFilter = "ALL" }, label = { Text("All (${leads.size})", fontSize = 11.sp, color = Color.Black) }) }
                                item { FilterChip(selected = leadsFilter == "NOT_CALLED", onClick = { leadsFilter = "NOT_CALLED" }, label = { Text("⏳ Remaining ($remainingCount)", fontSize = 11.sp, color = Color.Black) }) }
                                item { FilterChip(selected = leadsFilter == "CLEARED", onClick = { leadsFilter = "CLEARED" }, label = { Text("✅ Answered ($answeredCount)", fontSize = 11.sp, color = Color.Black) }) }
                                item { FilterChip(selected = leadsFilter == "NO_ANSWER", onClick = { leadsFilter = "NO_ANSWER" }, label = { Text("📵 Unreceived ($noAnswerCount)", fontSize = 11.sp, color = Color.Black) }) }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Select & Delete Toggle Button (Requirement 2 & Screenshot 2)
                            if (filteredSectionLeads.isNotEmpty()) {
                                Surface(
                                    onClick = {
                                        isLeadsSelectMode = true
                                        selectedLeadIds.clear()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = AppStatusColors.brandBlueLight,
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text("Select", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppStatusColors.brandBlue)
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // + Add Lead Button
                            Button(
                                onClick = { showAddLeadDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.brandBlue),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Lead", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        } else {
                            // Active Selection Mode Action Bar
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val allSelected = selectedLeadIds.size == filteredSectionLeads.size && filteredSectionLeads.isNotEmpty()
                                Checkbox(
                                    checked = allSelected,
                                    onCheckedChange = {
                                        if (allSelected) {
                                            selectedLeadIds.clear()
                                        } else {
                                            selectedLeadIds.clear()
                                            selectedLeadIds.addAll(filteredSectionLeads.map { it.id })
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = AppStatusColors.brandBlue)
                                )
                                Text(
                                    text = "${selectedLeadIds.size} Selected",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { showDeleteSelectedLeadsDialog = true },
                                    enabled = selectedLeadIds.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFDC2626),
                                        disabledContainerColor = Color(0xFFFCA5A5)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Delete (${selectedLeadIds.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Surface(
                                    onClick = {
                                        isLeadsSelectMode = false
                                        selectedLeadIds.clear()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF3F4F6),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF4B5563)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredSectionLeads.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No leads match this filter.", fontSize = 13.sp, color = Color.Black)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(sortedSectionLeads, key = { it.id }) { cust ->
                                val isSelected = selectedLeadIds.contains(cust.id)
                                SectionLeadItemCard(
                                    customer = cust,
                                    isSelectMode = isLeadsSelectMode,
                                    isSelected = isSelected,
                                    onToggleSelect = {
                                        if (isSelected) {
                                            selectedLeadIds.remove(cust.id)
                                        } else {
                                            selectedLeadIds.add(cust.id)
                                        }
                                    },
                                    onDial = {
                                        viewModel.dialCustomer(cust)
                                        selectedSubTab = 0
                                    },
                                    onToggleDnd = { isDnd -> viewModel.toggleSuppression(cust, isDnd) },
                                    onDelete = { leadToDelete = cust }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(60.dp))
                            }
                        }
                    }
                }
            }

            2 -> {
                // HISTORY IN SECTION SUB-VIEW
                Column(modifier = Modifier.fillMaxSize()) {
                    if (history.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No calls recorded yet for this section.", fontSize = 13.sp, color = Color.Black)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(history, key = { it.id }) { attempt ->
                                SectionHistoryCard(attempt = attempt)
                            }
                            item {
                                Spacer(modifier = Modifier.height(60.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Individual Lead Dialog
    if (showAddLeadDialog) {
        AddLeadDialog(
            sectionId = section.id,
            viewModel = viewModel,
            onDismiss = { showAddLeadDialog = false }
        )
    }

    // Delete Individual Lead Dialog
    leadToDelete?.let { lead ->
        AlertDialog(
            onDismissRequest = { leadToDelete = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color.White,
            title = { Text("Delete Lead '${lead.name}'?", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("Are you sure you want to delete ${lead.name} (${lead.phoneNumber}) from this section?", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteIndividualLead(lead) {
                            leadToDelete = null
                            Toast.makeText(context, "Lead deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Lead")
                }
            },
            dismissButton = {
                TextButton(onClick = { leadToDelete = null }) {
                    Text("Cancel", color = Color.Black)
                }
            }
        )
    }

    // Delete Section & All Leads Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color.White,
            title = { Text("Delete '${section.name}' Section?", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("This will permanently delete this section and all ${leads.size} leads inside it.", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSection(section.id) {
                            showDeleteConfirm = false
                            Toast.makeText(context, "Section deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Section")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color.Black)
                }
            }
        )
    }

    // Delete Multiple Selected Leads Dialog (Requirement 2 & Screenshot 2)
    if (showDeleteSelectedLeadsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedLeadsDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color.White,
            title = { Text("Delete ${selectedLeadIds.size} Selected Leads?", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("Are you sure you want to permanently delete the ${selectedLeadIds.size} selected leads from this section?", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedLeadIds.toList()
                        viewModel.deleteMultipleLeads(idsToDelete, section.id) {
                            showDeleteSelectedLeadsDialog = false
                            isLeadsSelectMode = false
                            selectedLeadIds.clear()
                            Toast.makeText(context, "${idsToDelete.size} leads deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Selected", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedLeadsDialog = false }) {
                    Text("Cancel", color = Color.Black)
                }
            }
        )
    }
}

@Composable
private fun SectionMetricItem(title: String, count: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.3).sp,
                color = color,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF71717A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionOutcomeBtn(label: String, color: Color, bgColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
        modifier = modifier.height(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = color)
        }
    }
}

@Composable
private fun SectionLeadItemCard(
    customer: CustomerEntity,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onDial: () -> Unit,
    onToggleDnd: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val (statusLabel, statusColor, statusBg) = when {
        customer.isSuppressed -> Triple("🔴 DND", AppStatusColors.suppressedLight, AppStatusColors.suppressedBgLight)
        customer.callStatus == "INTERESTED" -> Triple("⭐ INTERESTED", AppStatusColors.successLight, AppStatusColors.successBgLight)
        customer.callStatus == "CALLBACK" -> Triple("🟡 CALLBACK", AppStatusColors.warningLight, AppStatusColors.warningBgLight)
        customer.callStatus == "NO_ANSWER" -> Triple("📵 NO ANSWER", Color(0xFFD97706), Color(0xFFFEF3C7))
        customer.isCleared -> Triple("✅ CLEARED", AppStatusColors.successLight, AppStatusColors.successBgLight)
        else -> Triple("⏳ NOT CALLED", Color(0xFF71717A), Color(0xFFF4F4F5))
    }

    val cardBorder = if (isSelected) BorderStroke(2.dp, AppStatusColors.brandBlue) else BorderStroke(1.dp, Color(0xFFE2E8F0))
    val cardBg = if (isSelected) Color(0xFFF0F9FF) else Color.White
    val nameColor = Color(0xFF09090B)
    val phoneColor = AppStatusColors.brandBlue
    val subColor = Color(0xFF64748B)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectMode, onClick = onToggleSelect),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AppStatusColors.brandBlue,
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.2).sp,
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = customer.phoneNumber,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = phoneColor
                    )
                    if (!customer.company.isNullOrBlank()) {
                        Text(
                            text = customer.company,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = subColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!customer.lastCallNotes.isNullOrBlank()) {
                        Text(
                            text = "📝 ${customer.lastCallNotes}",
                            fontSize = 10.5.sp,
                            color = subColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(AppSpacing.pillRadius), color = statusBg) {
                        Text(
                            text = statusLabel,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (!isSelectMode) {
                        Spacer(modifier = Modifier.width(8.dp))

                        // Circular Delete Button
                        Surface(
                            onClick = onDelete,
                            shape = CircleShape,
                            color = Color(0xFFFEE2E2),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Lead",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDial,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppStatusColors.brandBlue
                    ),
                    shape = RoundedCornerShape(AppSpacing.pillRadius),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Dial",
                        modifier = Modifier.size(12.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Call Lead",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                TextButton(onClick = { onToggleDnd(!customer.isSuppressed) }) {
                    Text(
                        text = if (customer.isSuppressed) "Unblock DND" else "Mark DND",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (customer.isSuppressed) AppStatusColors.successLight else subColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHistoryCard(attempt: CallAttemptEntity) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(attempt.timestamp) { dateFormat.format(Date(attempt.timestamp)) }
    val durationText = AppViewModel.formatDuration(attempt.durationSeconds)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (attempt.contactName.isNotBlank()) attempt.contactName else attempt.phoneNumber,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF09090B)
                    )
                    Text(
                        text = "${attempt.phoneNumber} • $formattedTime",
                        fontSize = 11.5.sp,
                        color = Color(0xFF71717A)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(AppSpacing.pillRadius),
                    color = Color(0xFFF4F4F5),
                    border = BorderStroke(1.dp, Color(0xFFE4E4E7))
                ) {
                    Text(
                        text = "⏱️ $durationText",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF09090B),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            if (!attempt.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "📝 ${attempt.notes}", fontSize = 11.5.sp, color = Color(0xFF71717A))
            }
        }
    }
}

@Composable
private fun AddLeadDialog(
    sectionId: Long,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "➕ Add Lead Manually",
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Lead Name *", color = Color.Black) },
                    placeholder = { Text("e.g. Rahul Sharma") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *", color = Color.Black) },
                    placeholder = { Text("e.g. 9876543210") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company / Organization (Optional)", color = Color.Black) },
                    placeholder = { Text("e.g. Acme Tech") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)", color = Color.Black) },
                    placeholder = { Text("e.g. High priority buyer") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.addIndividualLead(
                        sectionId = sectionId,
                        name = name,
                        phoneNumber = phone,
                        company = company,
                        notes = notes
                    ) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (success) {
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.brandBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Lead", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Black)
            }
        }
    )
}
