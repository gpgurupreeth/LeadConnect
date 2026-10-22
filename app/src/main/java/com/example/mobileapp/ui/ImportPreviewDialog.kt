package com.example.mobileapp.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mobileapp.ui.theme.AppSpacing
import com.example.mobileapp.ui.theme.AppStatusColors
import com.example.mobileapp.utils.ContactImportParser
import com.example.mobileapp.utils.ImportSummary
import com.example.mobileapp.utils.ParsedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportPreviewDialog(
    defaultCountryCode: String,
    suppressedNumbers: Set<String>,
    onImportConfirmed: (List<ParsedContact>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Paste Text, 1 = Storage File (SAF)
    var countryCode by remember { mutableStateOf(defaultCountryCode) }
    var rawText by remember {
        mutableStateOf(
            "Rahul Sharma, 9876543210, Apex Retail, High priority lead\n" +
            "Priya Patel, +919876543211, Zenith Logistics, Interested in bulk delivery\n" +
            "Anand Verma, 9876543212, Quantum Tech, Schedule demonstration\n" +
            "Sneha Kulkarni, 9876543213, Horizon Media, Marketing opt-out lead\n" +
            "Bad Number, 12345, Corp Invalid, Too short\n" +
            "Duplicate Lead, 9876543210, Apex Retail, Duplicate phone"
        )
    }

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var importSummary by remember { mutableStateOf<ImportSummary?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(rawText, countryCode, selectedTab) {
        if (rawText.isNotBlank()) {
            isProcessing = true
            withContext(Dispatchers.Default) {
                val summary = ContactImportParser.parseCsv(
                    content = rawText,
                    defaultCountryCode = countryCode,
                    suppressedNumbers = suppressedNumbers
                )
                importSummary = summary
            }
            isProcessing = false
        } else {
            importSummary = null
        }
    }

    val csvFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                isProcessing = true
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val content = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    selectedFileName = uri.lastPathSegment ?: "selected_leads.csv"
                    rawText = content
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(AppSpacing.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.lg)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import Contacts & Verification",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "SAF CSV files, pasted lists, and automatic E.164 normalization",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppSpacing.buttonRadius),
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                    ) {
                        Text("Close", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                // Excel Conversion Guidance
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppStatusColors.infoBgLight),
                    shape = RoundedCornerShape(AppSpacing.buttonRadius)
                ) {
                    Row(
                        modifier = Modifier.padding(AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(AppSpacing.xs))
                        Text(
                            text = "Excel spreadsheets (.xlsx): Please save or export your file as CSV (.csv) before importing.",
                            fontSize = 11.sp,
                            color = AppStatusColors.infoLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                // Input Mode Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Paste Text List", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Browse Storage (.csv)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                // Default Country Code Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Default Country Code:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { countryCode = it },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(AppSpacing.buttonRadius),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.xs))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("Paste rows (Name, Phone, Company, Notes)", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(AppSpacing.buttonRadius)
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(AppSpacing.buttonRadius)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (selectedFileName != null) "Selected: $selectedFileName" else "No CSV file selected yet",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.sm))
                            Button(
                                onClick = { csvFileLauncher.launch("text/*") },
                                shape = RoundedCornerShape(AppSpacing.buttonRadius),
                                modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTouchTarget)
                            ) {
                                Text("Browse Device Files (SAF)")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                // Live Parsing Summary Cards
                importSummary?.let { summary ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppSpacing.buttonRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.sm),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            SummaryStatItem("Total", "${summary.totalProcessed}", MaterialTheme.colorScheme.onSurface)
                            SummaryStatItem("Valid", "${summary.validCount}", AppStatusColors.successLight)
                            SummaryStatItem("Duplicate", "${summary.duplicateCount}", AppStatusColors.warningLight)
                            SummaryStatItem("Invalid", "${summary.invalidCount}", AppStatusColors.errorLight)
                            SummaryStatItem("Suppressed", "${summary.suppressedCount}", AppStatusColors.suppressedLight)
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.sm))

                    Text(
                        text = "Record Verification (${summary.parsedContacts.size}):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.xs))

                    // Preview List with validation errors clearly shown
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        items(summary.parsedContacts) { contact ->
                            ContactValidationRow(contact)
                        }
                    }
                } ?: run {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator()
                        } else {
                            Text("No contacts to preview", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                // Bottom Confirmation Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppSpacing.buttonRadius),
                        modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTouchTarget)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    Button(
                        onClick = {
                            importSummary?.let { sum ->
                                onImportConfirmed(sum.parsedContacts)
                            }
                        },
                        enabled = (importSummary?.validCount ?: 0) > 0 && !isProcessing,
                        shape = RoundedCornerShape(AppSpacing.buttonRadius),
                        modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTouchTarget)
                    ) {
                        Text("Save ${importSummary?.validCount ?: 0} Leads to DB")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContactValidationRow(contact: ParsedContact) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppSpacing.xs),
        colors = CardDefaults.cardColors(
            containerColor = if (!contact.isValid) AppStatusColors.errorBgLight.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = contact.phoneNumber,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (contact.validationError != null) {
                    Text(
                        text = "⚠️ ${contact.validationError}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppStatusColors.errorLight
                    )
                }
            }

            // Status Badge
            val (badgeBg, badgeText, badgeColor) = when {
                !contact.isValid -> Triple(AppStatusColors.errorBgLight, "INVALID", AppStatusColors.errorLight)
                contact.isDuplicate -> Triple(AppStatusColors.warningBgLight, "DUPLICATE", AppStatusColors.warningLight)
                contact.isSuppressed -> Triple(AppStatusColors.suppressedBgLight, "DND KEPT", AppStatusColors.suppressedLight)
                else -> Triple(AppStatusColors.successBgLight, "VALID", AppStatusColors.successLight)
            }

            Surface(shape = RoundedCornerShape(4.dp), color = badgeBg) {
                Text(
                    text = badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
