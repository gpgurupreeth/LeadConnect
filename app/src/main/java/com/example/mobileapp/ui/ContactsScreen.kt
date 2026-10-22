package com.example.mobileapp.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.ui.theme.AppSpacing
import com.example.mobileapp.ui.theme.AppStatusColors

@Composable
fun ContactsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val customers by viewModel.customersFlow.collectAsState(initial = emptyList())
    val defaultCountryCode by viewModel.defaultCountryCode.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("ALL") } // ALL, ELIGIBLE, DND
    var showImportDialog by remember { mutableStateOf(false) }
    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }

    val eligibleCount = customers.count { !it.isSuppressed }
    val dndCount = customers.count { it.isSuppressed }

    val filteredCustomers = customers.filter { cust ->
        val matchesSearch = cust.name.contains(searchQuery, ignoreCase = true) ||
                cust.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                (cust.company?.contains(searchQuery, ignoreCase = true) == true)
        val matchesFilter = when (filterType) {
            "ELIGIBLE" -> !cust.isSuppressed
            "DND" -> cust.isSuppressed
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg)
    ) {
        Spacer(modifier = Modifier.height(AppSpacing.sm))

        // Screen Header & Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Customer Leads",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${customers.size} total leads ($eligibleCount active, $dndCount DND)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showImportDialog = true },
                shape = RoundedCornerShape(AppSpacing.buttonRadius),
                modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTouchTarget)
            ) {
                Text("Import Leads")
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.md))

        // Search Bar with leading icon
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name, number, company...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(AppSpacing.buttonRadius)
        )

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        // Filter and Quick Seed Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                FilterChip(
                    selected = filterType == "ALL",
                    onClick = { filterType = "ALL" },
                    label = { Text("All (${customers.size})", fontSize = 11.sp) },
                    shape = RoundedCornerShape(AppSpacing.pillRadius)
                )
                FilterChip(
                    selected = filterType == "ELIGIBLE",
                    onClick = { filterType = "ELIGIBLE" },
                    label = { Text("Eligible ($eligibleCount)", fontSize = 11.sp) },
                    shape = RoundedCornerShape(AppSpacing.pillRadius)
                )
                FilterChip(
                    selected = filterType == "DND",
                    onClick = { filterType = "DND" },
                    label = { Text("DND ($dndCount)", fontSize = 11.sp) },
                    shape = RoundedCornerShape(AppSpacing.pillRadius)
                )
            }

            OutlinedButton(
                onClick = {
                    viewModel.seedDemoContacts { count ->
                        Toast.makeText(context, "Seeded $count sample contacts", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(AppSpacing.buttonRadius),
                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
            ) {
                Text("Seed Sample", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.md))

        if (filteredCustomers.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(AppSpacing.cardRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching leads found" else "Lead database is empty",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.xs))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try searching with a different term or clear the filter." else "Click 'Import Leads' to parse a CSV file or 'Seed Sample' to generate test data.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                items(filteredCustomers, key = { it.id }) { customer ->
                    CustomerCard(
                        customer = customer,
                        onToggleDnd = { isDnd -> viewModel.toggleSuppression(customer, isDnd) },
                        onToggleConsent = { consent -> viewModel.toggleConsent(customer, consent) },
                        onDelete = { customerToDelete = customer }
                    )
                }
                item { Spacer(modifier = Modifier.height(AppSpacing.md)) }
            }
        }
    }

    // Delete Confirmation Dialog
    customerToDelete?.let { cust ->
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            title = { Text("Delete Contact", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text("Are you sure you want to remove ${cust.name} (${cust.phoneNumber}) from the database?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(cust)
                        customerToDelete = null
                        Toast.makeText(context, "Deleted ${cust.name}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.errorLight)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { customerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showImportDialog) {
        ImportPreviewDialog(
            defaultCountryCode = defaultCountryCode,
            suppressedNumbers = customers.filter { it.isSuppressed }.map { it.phoneNumber }.toSet(),
            onImportConfirmed = { parsedList ->
                viewModel.importContacts(parsedList) { saved ->
                    showImportDialog = false
                    Toast.makeText(context, "Successfully saved $saved contacts to Room DB", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showImportDialog = false }
        )
    }
}

@Composable
private fun CustomerCard(
    customer: CustomerEntity,
    onToggleDnd: (Boolean) -> Unit,
    onToggleConsent: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppSpacing.cardRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (customer.isSuppressed) AppStatusColors.suppressedBgLight.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customer.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (customer.isSuppressed) {
                        Spacer(modifier = Modifier.width(AppSpacing.xs))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AppStatusColors.suppressedBgLight
                        ) {
                            Text(
                                text = "DND BLOCKED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppStatusColors.suppressedLight,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (!customer.consentGiven) {
                        Spacer(modifier = Modifier.width(AppSpacing.xs))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AppStatusColors.warningBgLight
                        ) {
                            Text(
                                text = "NO WA CONSENT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppStatusColors.warningLight,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = customer.phoneNumber,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!customer.company.isNullOrBlank()) {
                    Text(
                        text = "${customer.company} ${if (!customer.tags.isNullOrBlank()) "• ${customer.tags}" else ""}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Consent, DND, and delete actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (customer.consentGiven) "WA: ON" else "WA: OFF",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (customer.consentGiven) AppStatusColors.successLight else AppStatusColors.warningLight
                    )
                    Switch(
                        checked = customer.consentGiven,
                        onCheckedChange = { onToggleConsent(it) },
                        modifier = Modifier.size(width = 42.dp, height = 28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(AppSpacing.xs))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (customer.isSuppressed) "DND: ON" else "DND: OFF",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (customer.isSuppressed) AppStatusColors.suppressedLight else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = customer.isSuppressed,
                        onCheckedChange = { onToggleDnd(it) },
                        modifier = Modifier.size(width = 42.dp, height = 28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(AppSpacing.xs))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Contact",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
