package com.example.mobileapp.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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

    // Section specific counts
    val pendingLeads = leads.filter { !it.isCleared && !it.isSuppressed }
    val clearedLeads = leads.filter { it.isCleared }
    val answeredCount = leads.count { it.callStatus in listOf("INTERESTED", "CALLBACK", "ANSWERED") }
    val noAnswerCount = leads.count { it.callStatus == "NO_ANSWER" }

    // Dialer pointer
    var currentLeadIndex by remember { mutableIntStateOf(0) }
    val currentLead = pendingLeads.getOrNull(currentLeadIndex.coerceIn(0, (pendingLeads.size - 1).coerceAtLeast(0)))

    var noteText by remember { mutableStateOf("") }
    var countdownSeconds by remember { mutableIntStateOf(0) }
    var isCountdownPaused by remember { mutableStateOf(false) }

    // Auto-advance countdown
    LaunchedEffect(countdownSeconds, isCountdownPaused) {
        if (countdownSeconds > 0 && !isCountdownPaused) {
            delay(1000L)
            countdownSeconds -= 1
            if (countdownSeconds == 0) {
                if (pendingLeads.isNotEmpty()) {
                    currentLeadIndex = (currentLeadIndex + 1).coerceAtMost(pendingLeads.size - 1)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
    ) {
        // 1. Top Executive Floating Header Card (Matching Format)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFF0EBE1)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Soft Peach Back Button
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFEDD5),
                        border = BorderStroke(1.dp, Color(0xFFFDBA74)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFFEA580C),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Back",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFEA580C)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = section.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp,
                            color = Color(0xFF0A0A0A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!section.genre.isNullOrBlank()) {
                            Text(
                                text = "🏷️ ${section.genre}",
                                fontSize = 11.sp,
                                color = Color(0xFFEA580C),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Soft Orange Share/Export Excel Button
                    Surface(
                        onClick = { viewModel.shareSectionCsvAsFile(context, section) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFEDD5),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Excel Sheet",
                                tint = Color(0xFFEA580C),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Soft Red Delete Section Button
                    Surface(
                        onClick = { showDeleteConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.size(38.dp)
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
            onTabSelected = { selectedSubTab = it }
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
            SectionMetricItem("Remaining", "${pendingLeads.size}", Color(0xFFEA580C), Modifier.weight(1f))
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
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFF0EBE1)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(AppSpacing.pillRadius),
                                        color = Color(0xFFFFEDD5),
                                        border = BorderStroke(0.5.dp, Color(0xFFEA580C))
                                    ) {
                                        Text(
                                            text = "Lead ${currentLeadIndex + 1} of ${pendingLeads.size}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEA580C),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(AppSpacing.pillRadius),
                                        color = if (currentLead.isVerified) AppStatusColors.successBgLight else Color(0xFFFEF3C7)
                                    ) {
                                        Text(
                                            text = if (currentLead.isVerified) "✅ Number Verified" else "⚠️ Unverified",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentLead.isVerified) AppStatusColors.successLight else Color(0xFFD97706),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = currentLead.name,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp,
                                    color = Color(0xFF0A0A0A)
                                )

                                Text(
                                    text = currentLead.phoneNumber,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEA580C)
                                )

                                if (!currentLead.company.isNullOrBlank()) {
                                    Text(
                                        text = "🏢 ${currentLead.company}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF4B5563)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Vibrant Orange Call Now Button (Matching Format)
                                Button(
                                    onClick = {
                                        val ok = viewModel.dialCustomer(currentLead)
                                        if (ok) {
                                            Toast.makeText(context, "Dialing ${currentLead.phoneNumber}...", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
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

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Log Call Outcome:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.height(6.dp))

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
                                        countdownSeconds = 3
                                    }

                                    SectionOutcomeBtn(
                                        label = "Call Back",
                                        color = AppStatusColors.warningLight,
                                        bgColor = AppStatusColors.warningBgLight,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        viewModel.recordCallOutcome(currentLead, "CALLBACK", noteText)
                                        noteText = ""
                                        countdownSeconds = 3
                                    }

                                    SectionOutcomeBtn(
                                        label = "No Answer",
                                        color = Color(0xFFD97706),
                                        bgColor = Color(0xFFFEF3C7),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        viewModel.recordCallOutcome(currentLead, "NO_ANSWER", noteText)
                                        noteText = ""
                                        countdownSeconds = 3
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
                                        countdownSeconds = 3
                                    }

                                    SectionOutcomeBtn(
                                        label = "Wrong Number",
                                        color = Color(0xFF4B5563),
                                        bgColor = Color(0xFFF3F4F6),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        viewModel.recordCallOutcome(currentLead, "WRONG_NUMBER", noteText)
                                        noteText = ""
                                        countdownSeconds = 3
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
                                        onClick = { if (currentLeadIndex < pendingLeads.size - 1) currentLeadIndex++ },
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
                // LEADS IN SECTION SUB-VIEW (with Add Lead & Individual Delete)
                var leadsFilter by remember { mutableStateOf("ALL") }
                val filteredSectionLeads = leads.filter { cust ->
                    when (leadsFilter) {
                        "NOT_CALLED" -> cust.callStatus == "NOT_CALLED" && !cust.isSuppressed
                        "CLEARED" -> cust.isCleared
                        "NO_ANSWER" -> cust.callStatus == "NO_ANSWER"
                        "DND" -> cust.isSuppressed
                        else -> true
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Filter Chips + Add Lead Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item { FilterChip(selected = leadsFilter == "ALL", onClick = { leadsFilter = "ALL" }, label = { Text("All (${leads.size})", fontSize = 11.sp, color = Color.Black) }) }
                            item { FilterChip(selected = leadsFilter == "NOT_CALLED", onClick = { leadsFilter = "NOT_CALLED" }, label = { Text("⏳ Remaining (${pendingLeads.size})", fontSize = 11.sp, color = Color.Black) }) }
                            item { FilterChip(selected = leadsFilter == "CLEARED", onClick = { leadsFilter = "CLEARED" }, label = { Text("✅ Done (${clearedLeads.size})", fontSize = 11.sp, color = Color.Black) }) }
                            item { FilterChip(selected = leadsFilter == "NO_ANSWER", onClick = { leadsFilter = "NO_ANSWER" }, label = { Text("📵 Missed ($noAnswerCount)", fontSize = 11.sp, color = Color.Black) }) }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // + Add Lead Button
                        Button(
                            onClick = { showAddLeadDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Lead", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (filteredSectionLeads.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No leads match this filter.", fontSize = 13.sp, color = Color.Black)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredSectionLeads, key = { it.id }) { cust ->
                                SectionLeadItemCard(
                                    customer = cust,
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
}

@Composable
private fun SectionMetricItem(title: String, count: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0EBE1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = count, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }
    }
}

@Composable
private fun SectionOutcomeBtn(label: String, color: Color, bgColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier.defaultMinSize(minHeight = 42.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(AppSpacing.xs)) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SectionLeadItemCard(
    customer: CustomerEntity,
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
        else -> Triple("⏳ NOT CALLED", Color.Black, Color(0xFFF3F4F6))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0EBE1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = customer.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp, color = Color(0xFF0A0A0A))
                    Text(text = customer.phoneNumber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEA580C))
                    if (!customer.company.isNullOrBlank()) {
                        Text(text = "🏢 ${customer.company}", fontSize = 11.sp, fontWeight = FontWeight.Normal, color = Color(0xFF4B5563))
                    }
                    if (!customer.lastCallNotes.isNullOrBlank()) {
                        Text(text = "📝 ${customer.lastCallNotes}", fontSize = 10.sp, color = Color.Gray)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(AppSpacing.pillRadius), color = statusBg) {
                        Text(
                            text = statusLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    // Soft Red Square Delete Button
                    Surface(
                        onClick = onDelete,
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Lead",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDial,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Dial", modifier = Modifier.size(12.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call Lead", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                TextButton(onClick = { onToggleDnd(!customer.isSuppressed) }) {
                    Text(
                        text = if (customer.isSuppressed) "Unblock DND" else "Mark DND",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (customer.isSuppressed) AppStatusColors.successLight else AppStatusColors.suppressedLight
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0EBE1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = if (attempt.contactName.isNotBlank()) attempt.contactName else attempt.phoneNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(text = "${attempt.phoneNumber} • $formattedTime", fontSize = 11.sp, color = Color(0xFF4B5563))
                }
                Surface(
                    shape = RoundedCornerShape(AppSpacing.pillRadius),
                    color = Color(0xFFFFEDD5),
                    border = BorderStroke(0.5.dp, Color(0xFFEA580C))
                ) {
                    Text(text = "⏱️ $durationText", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            if (!attempt.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "📝 ${attempt.notes}", fontSize = 11.sp, color = Color.Black)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
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
