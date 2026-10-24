package com.example.mobileapp.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.example.mobileapp.utils.ImportSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SectionsScreen(
    viewModel: AppViewModel,
    onOpenSection: (CampaignEntity) -> Unit
) {
    val context = LocalContext.current
    val sections by viewModel.sectionsFlow.collectAsState(initial = emptyList())
    val allHistory by viewModel.allHistoryFlow.collectAsState(initial = emptyList())

    var homeTab by remember { mutableIntStateOf(0) } // 0: Sections, 1: Global Call History
    var showCreateDialog by remember { mutableStateOf(false) }
    var sectionToDelete by remember { mutableStateOf<CampaignEntity?>(null) }
    var isSelectMode by remember { mutableStateOf(false) }
    val selectedSectionIds = remember { mutableStateListOf<Long>() }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

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
            Column {
                Text(
                    text = "LeadConnect",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.6).sp,
                    color = Color(0xFF09090B)
                )
                Text(
                    text = "Sales Calling Automation",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF71717A)
                )
            }

            // Dribbble-style Circular + Button
            Surface(
                onClick = { showCreateDialog = true },
                shape = CircleShape,
                color = AppStatusColors.brandBlue,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Import CSV",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Floating Pill Tab Bar
        AppPillTabBar(
            tabs = listOf(
                TabItem(title = "Sections", count = sections.size, iconPrefix = "📁"),
                TabItem(title = "All Call History", count = allHistory.size, iconPrefix = "📜")
            ),
            selectedIndex = homeTab,
            onTabSelected = {
                homeTab = it
                if (it != 0) isSelectMode = false
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Tab Body
        Box(modifier = Modifier.fillMaxSize()) {
            if (homeTab == 0) {
                // SECTIONS TAB
                if (sections.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppSpacing.lg),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF0EBE1)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = AppStatusColors.brandBlueLight,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.List,
                                        contentDescription = "List",
                                        tint = AppStatusColors.brandBlue,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(AppSpacing.md))
                            Text(
                                text = "No Categories or Sections Yet",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.xs))
                            Text(
                                text = "Import your first CSV file to create a dedicated section with custom genres (Real Estate, Healthcare, Tech Leads, etc.).",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = Color(0xFF4B5563)
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.lg))
                            Button(
                                onClick = { showCreateDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.brandBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import CSV File", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Select & Delete Action Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isSelectMode) {
                                Text(
                                    text = "Your Lead Sections (${sections.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF374151)
                                )

                                Surface(
                                    onClick = {
                                        isSelectMode = true
                                        selectedSectionIds.clear()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFEDD5),
                                    border = BorderStroke(1.dp, Color(0xFFFDBA74)),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = "Select & Delete",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEA580C)
                                        )
                                    }
                                }
                            } else {
                                // In selection mode
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val allSelected = selectedSectionIds.size == sections.size
                                    Checkbox(
                                        checked = allSelected,
                                        onCheckedChange = {
                                            if (allSelected) {
                                                selectedSectionIds.clear()
                                            } else {
                                                selectedSectionIds.clear()
                                                selectedSectionIds.addAll(sections.map { it.id })
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFEA580C))
                                    )
                                    Text(
                                        text = "${selectedSectionIds.size} Selected",
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
                                        onClick = { showDeleteSelectedDialog = true },
                                        enabled = selectedSectionIds.isNotEmpty(),
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
                                            text = "Delete (${selectedSectionIds.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    Surface(
                                        onClick = {
                                            isSelectMode = false
                                            selectedSectionIds.clear()
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

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(sections, key = { it.id }) { section ->
                                val isSelected = selectedSectionIds.contains(section.id)
                                SectionCard(
                                    section = section,
                                    isSelectMode = isSelectMode,
                                    isSelected = isSelected,
                                    onToggleSelect = {
                                        if (isSelected) {
                                            selectedSectionIds.remove(section.id)
                                        } else {
                                            selectedSectionIds.add(section.id)
                                        }
                                    },
                                    onClick = {
                                        if (isSelectMode) {
                                            if (isSelected) selectedSectionIds.remove(section.id)
                                            else selectedSectionIds.add(section.id)
                                        } else {
                                            onOpenSection(section)
                                        }
                                    },
                                    onDelete = { sectionToDelete = section }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(60.dp))
                            }
                        }
                    }
                }
            } else {
                // GLOBAL CALL HISTORY TAB
                if (allHistory.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppSpacing.lg),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF0EBE1)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📜 No Calls Placed Yet",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.xs))
                            Text(
                                text = "Call attempts across all your sections will be recorded here with duration, section badge, and status.",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }
                } else {
                    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allHistory, key = { it.id }) { attempt ->
                            val sectionMatch = sections.find { it.id == attempt.campaignId }
                            GlobalHistoryCard(
                                attempt = attempt,
                                sectionName = sectionMatch?.name ?: "Unknown Section",
                                sectionGenre = sectionMatch?.genre,
                                dateFormat = dateFormat,
                                onDial = {
                                    val dummyCustomer = CustomerEntity(
                                        id = attempt.customerId,
                                        sectionId = attempt.campaignId,
                                        name = attempt.contactName,
                                        phoneNumber = attempt.phoneNumber
                                    )
                                    viewModel.dialCustomer(dummyCustomer)
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(60.dp))
                        }
                    }
                }
            }
        }
    }

    // Create Section Dialog with File Picker
    if (showCreateDialog) {
        CreateSectionDialog(
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false }
        )
    }

    // Delete Single Section Dialog
    sectionToDelete?.let { sec ->
        AlertDialog(
            onDismissRequest = { sectionToDelete = null },
            title = { Text("Delete '${sec.name}'?", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("This will delete this category and all its leads.", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSection(sec.id) {
                            sectionToDelete = null
                            Toast.makeText(context, "Section deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Section")
                }
            },
            dismissButton = {
                TextButton(onClick = { sectionToDelete = null }) {
                    Text("Cancel", color = Color.Black)
                }
            }
        )
    }

    // Delete Multiple Selected Sections Dialog
    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Delete ${selectedSectionIds.size} Selected Sections?", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("This will permanently delete the selected ${selectedSectionIds.size} sections and all their imported leads.", color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedSectionIds.toList()
                        viewModel.deleteMultipleSections(idsToDelete) {
                            showDeleteSelectedDialog = false
                            isSelectMode = false
                            selectedSectionIds.clear()
                            Toast.makeText(context, "${idsToDelete.size} sections deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Selected", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Cancel", color = Color.Black)
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    section: CampaignEntity,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (section.totalContacts > 0) {
        (section.completedContacts.toFloat() / section.totalContacts.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val cardBorder = if (isSelected) BorderStroke(2.dp, AppStatusColors.brandBlue) else BorderStroke(1.dp, Color(0xFFE2E8F0))
    val cardBg = if (isSelected) Color(0xFFF0F9FF) else Color.White
    val titleColor = Color(0xFF09090B)
    val subtitleColor = Color(0xFF64748B)
    val progressColor = AppStatusColors.brandBlue
    val progressTrack = Color(0xFFE0F2FE)
    val actionColor = AppStatusColors.brandBlue

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                        text = section.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!section.genre.isNullOrBlank()) {
                        Text(
                            text = section.genre,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = subtitleColor
                        )
                    }
                }

                if (!isSelectMode) {
                    // Minimal Circular Delete Button
                    Surface(
                        onClick = onDelete,
                        shape = CircleShape,
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Section",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${section.completedContacts} of ${section.totalContacts} calls",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = subtitleColor
                )
                Text(
                    text = "${(progress * 100).toInt()}% Done",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Minimalist Linear Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = progressColor,
                trackColor = progressTrack
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Open Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open Section & Call Leads ➔",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = actionColor
                )
            }
        }
    }
}

@Composable
private fun GlobalHistoryCard(
    attempt: CallAttemptEntity,
    sectionName: String,
    sectionGenre: String?,
    dateFormat: SimpleDateFormat,
    onDial: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0EBE1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Section badge tag
                Surface(
                    shape = RoundedCornerShape(AppSpacing.pillRadius),
                    color = AppStatusColors.brandBlueLight,
                    border = BorderStroke(0.5.dp, AppStatusColors.brandBlue)
                ) {
                    Text(
                        text = "📁 $sectionName" + if (!sectionGenre.isNullOrBlank()) " • $sectionGenre" else "",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = attempt.contactName.ifBlank { "Lead" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = attempt.phoneNumber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4B5563)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (badgeText, badgeBg, badgeFg) = when (attempt.outcome) {
                        "INTERESTED" -> Triple("⭐ Interested", AppStatusColors.successBgLight, AppStatusColors.successLight)
                        "CALLBACK" -> Triple("📞 Call Back", AppStatusColors.warningBgLight, AppStatusColors.warningLight)
                        "NO_ANSWER" -> Triple("⏳ No Answer", Color(0xFFFEF3C7), Color(0xFFD97706))
                        "NOT_INTERESTED" -> Triple("⛔ Not Interested", AppStatusColors.errorBgLight, AppStatusColors.errorLight)
                        "WRONG_NUMBER" -> Triple("❌ Wrong Number", Color(0xFFF3F4F6), Color(0xFF4B5563))
                        else -> Triple(attempt.outcome, Color(0xFFF3F4F6), Color.Black)
                    }

                    Surface(
                        shape = RoundedCornerShape(AppSpacing.pillRadius),
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "⏱️ ${AppViewModel.formatDuration(attempt.durationSeconds)}",
                        fontSize = 11.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = dateFormat.format(Date(attempt.timestamp)),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                if (!attempt.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "📝 ${attempt.notes}",
                        fontSize = 11.sp,
                        color = Color.Black
                    )
                }
            }

            // Quick Call Button
            Surface(
                onClick = onDial,
                shape = CircleShape,
                color = AppStatusColors.brandBlueLight,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Redial",
                        tint = AppStatusColors.brandBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateSectionDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var sectionName by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var csvText by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var importSummary by remember { mutableStateOf<ImportSummary?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        selectedFileName = cursor.getString(nameIndex)
                    }
                }

                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: ""

                csvText = content

                if (sectionName.isBlank() && selectedFileName != null) {
                    val clean = selectedFileName!!.substringBeforeLast(".").replace("_", " ").replace("-", " ")
                    sectionName = clean
                }

                val lineCount = content.lines().filter { line -> line.isNotBlank() }.size
                Toast.makeText(context, "Loaded $lineCount rows from ${selectedFileName ?: "file"}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "📁 Import CSV & Create Section",
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (importSummary == null) {
                    OutlinedTextField(
                        value = sectionName,
                        onValueChange = { sectionName = it },
                        label = { Text("Section / Category Name", color = Color.Black) },
                        placeholder = { Text("e.g. Bangalore Real Estate Leads") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))

                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Genre / Industry (Optional)", color = Color.Black) },
                        placeholder = { Text("e.g. Real Estate / B2B Tech") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.md))

                    // Prominent, High-Contrast CSV File Picker Button
                    Card(
                        onClick = { filePickerLauncher.launch("*/*") },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedFileName != null) Color(0xFFF0FDF4) else AppStatusColors.brandBlue
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            if (selectedFileName != null) Color(0xFF22C55E) else Color(0xFF0369A1)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (selectedFileName != null) Icons.Default.Done else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (selectedFileName != null) Color(0xFF15803D) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedFileName != null) "CSV Selected: $selectedFileName" else "Select .CSV File from Device",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = if (selectedFileName != null) Color(0xFF15803D) else Color.White
                            )
                        }
                    }
                    if (selectedFileName != null) {
                        Text(
                            text = "Tap above if you want to select a different CSV file.",
                            fontSize = 11.sp,
                            color = Color(0xFF71717A),
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.sm))

                    Text(
                        text = "Or Paste Leads / CSV Content directly:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = csvText,
                        onValueChange = { csvText = it },
                        placeholder = { Text("Name, Phone, Company\nJohn Doe, 9876543210, ABC Corp\nJane Smith, 9123456789, XYZ Ltd") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    val s = importSummary!!
                    Text(
                        text = "Leads Verification Summary",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppStatusColors.brandBlue
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Column(modifier = Modifier.padding(AppSpacing.md)) {
                            Text("✅ Valid Leads Ready: ${s.validCount}", fontWeight = FontWeight.Bold, color = AppStatusColors.successLight, fontSize = 13.sp)
                            Text("⚠️ Duplicates Filtered: ${s.duplicateCount}", color = AppStatusColors.warningLight, fontSize = 13.sp)
                            Text("❌ Invalid / Missing Phone: ${s.invalidCount}", color = AppStatusColors.errorLight, fontSize = 13.sp)
                            Text("Total Rows Processed: ${s.totalProcessed}", fontSize = 12.sp, color = Color.Black)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (importSummary == null) {
                Button(
                    onClick = {
                        if (sectionName.isBlank()) {
                            Toast.makeText(context, "Please enter a section name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (csvText.isBlank()) {
                            Toast.makeText(context, "Please select a CSV file or paste content", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.createSectionAndImportCsv(sectionName, genre, csvText) { _, summary ->
                            importSummary = summary
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.brandBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Verify & Create Section")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.brandBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done (${importSummary?.validCount ?: 0} Leads Added)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Black)
            }
        }
    )
}
