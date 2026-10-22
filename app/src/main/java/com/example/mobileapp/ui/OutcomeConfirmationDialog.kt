package com.example.mobileapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.domain.workflow.FollowUpDecision
import com.example.mobileapp.domain.workflow.FollowUpPolicy
import com.example.mobileapp.ui.theme.AppSpacing
import com.example.mobileapp.ui.theme.AppStatusColors

@Composable
fun OutcomeConfirmationDialog(
    attemptId: Long,
    customer: CustomerEntity,
    templateName: String?,
    onConfirm: (outcome: CallOutcome, sendWhatsApp: Boolean, notes: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOutcome by remember { mutableStateOf(CallOutcome.UNKNOWN) }
    // Opt-in is explicit. A completed call never queues a message by default.
    var sendWhatsApp by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val followUpDecision = FollowUpPolicy.evaluate(
        outcome = selectedOutcome,
        agentRequested = true,
        consentGiven = customer.consentGiven,
        optedOut = customer.isSuppressed,
        templateName = templateName
    )
    val canQueueFollowUp = followUpDecision is FollowUpDecision.Eligible
    val shouldSendWhatsApp = sendWhatsApp && canQueueFollowUp

    AlertDialog(
        onDismissRequest = { /* Must choose an action */ },
        shape = RoundedCornerShape(AppSpacing.cardRadius),
        title = {
            Text(
                text = "Verify Call Outcome",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Active Customer Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppSpacing.buttonRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.md)) {
                        Text(
                            text = customer.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = customer.phoneNumber,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!customer.company.isNullOrBlank()) {
                            Text(
                                text = customer.company,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.md))

                Text(
                    text = "Select what occurred during this call:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))

                // Outcome Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    FilterChip(
                        selected = selectedOutcome == CallOutcome.ANSWERED,
                        onClick = { selectedOutcome = CallOutcome.ANSWERED },
                        label = { Text("Answered", fontSize = 11.sp) },
                        shape = RoundedCornerShape(AppSpacing.pillRadius)
                    )
                    FilterChip(
                        selected = selectedOutcome == CallOutcome.NO_ANSWER,
                        onClick = { selectedOutcome = CallOutcome.NO_ANSWER },
                        label = { Text("No Answer", fontSize = 11.sp) },
                        shape = RoundedCornerShape(AppSpacing.pillRadius)
                    )
                    FilterChip(
                        selected = selectedOutcome == CallOutcome.REJECTED || selectedOutcome == CallOutcome.BUSY,
                        onClick = { selectedOutcome = CallOutcome.REJECTED },
                        label = { Text("Declined", fontSize = 11.sp) },
                        shape = RoundedCornerShape(AppSpacing.pillRadius)
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Call Notes / Follow-up Details (Optional)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    shape = RoundedCornerShape(AppSpacing.buttonRadius),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                if (selectedOutcome != CallOutcome.UNKNOWN) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = shouldSendWhatsApp,
                            onCheckedChange = { sendWhatsApp = it },
                            enabled = canQueueFollowUp
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.xs))
                        Text(
                            text = "Queue WhatsApp follow-up (${templateName ?: "no template"})",
                            fontSize = 12.sp,
                            color = if (canQueueFollowUp) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    if (!canQueueFollowUp) {
                        val reason = when (followUpDecision) {
                            is FollowUpDecision.Blocked -> followUpDecision.reason
                            is FollowUpDecision.NotRequested -> followUpDecision.reason
                            is FollowUpDecision.Eligible -> ""
                        }
                        Text(
                            text = "Follow-up blocked: $reason",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppStatusColors.errorLight
                        )
                    } else {
                        Text(
                            text = "Consent recorded. The message stays in the queue until an agent processes it; it is never sent automatically when the call ends.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Safety Rule: Ambiguous call states require explicit human confirmation.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppStatusColors.errorLight
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(
                    text = if (customer.consentGiven) {
                        "WhatsApp consent: recorded"
                    } else {
                        "WhatsApp consent: not recorded — follow-up is blocked until an agent records consent in Contacts."
                    },
                    fontSize = 10.sp,
                    color = if (customer.consentGiven) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        AppStatusColors.errorLight
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedOutcome, shouldSendWhatsApp, notes.takeIf { it.isNotBlank() })
                },
                enabled = selectedOutcome != CallOutcome.UNKNOWN,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(AppSpacing.buttonRadius),
                modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTouchTarget)
            ) {
                Text("Confirm & Next Lead")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(AppSpacing.buttonRadius),
                modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTouchTarget)
            ) {
                Text("Pause Campaign")
            }
        }
    )
}
