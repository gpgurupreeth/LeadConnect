package com.example.mobileapp.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileapp.data.local.entities.MessageJobEntity
import com.example.mobileapp.ui.theme.AppSpacing
import com.example.mobileapp.ui.theme.AppStatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WhatsAppQueueScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val jobs by viewModel.messageJobsFlow.collectAsState(initial = emptyList())
    var isDispatching by remember { mutableStateOf(false) }
    var unknownJobToRetry by remember { mutableStateOf<MessageJobEntity?>(null) }

    val pendingCount = jobs.count { it.status == "PENDING" }
    val sendingCount = jobs.count { it.status == "SENDING" }
    val sentCount = jobs.count { it.status == "SENT" }
    val failedCount = jobs.count { it.status == "FAILED" }
    val unknownCount = jobs.count { it.status == "UNKNOWN" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg)
    ) {
        Spacer(modifier = Modifier.height(AppSpacing.sm))

        // Screen Header & Batch Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WhatsApp Dispatch Queue",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${jobs.size} jobs • $pendingCount pending • $sendingCount sending • " +
                        "$sentCount accepted • $unknownCount unknown",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    isDispatching = true
                    viewModel.dispatchMessageQueue { sent, failed, unknown ->
                        isDispatching = false
                        Toast.makeText(
                            context,
                            "Dispatch complete: $sent accepted, $failed failed/gated, $unknown unknown (verify manually)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                enabled = pendingCount > 0 && !isDispatching,
                shape = RoundedCornerShape(AppSpacing.buttonRadius),
                modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTouchTarget)
            ) {
                if (isDispatching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                    Text("Sending...")
                } else {
                    Text("Process Queue ($pendingCount)")
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        // Security & Provider Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppSpacing.cardRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔒", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(AppSpacing.sm))
                Column {
                    Text(
                        text = "Secure Backend Proxy Architecture",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Long-lived API keys are never stored on device. Requests authenticate via short-lived JWTs to the backend proxy.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        // Pipeline Metrics Row
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
                QueueMetricItem("Pending", "$pendingCount", AppStatusColors.warningLight)
                QueueMetricItem("Sending", "$sendingCount", AppStatusColors.warningLight)
                QueueMetricItem("Accepted", "$sentCount", AppStatusColors.successLight)
                QueueMetricItem("Failed", "$failedCount", AppStatusColors.errorLight)
                QueueMetricItem("Unknown", "$unknownCount", AppStatusColors.errorLight)
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.md))

        if (jobs.isEmpty()) {
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
                            text = "WhatsApp Queue is Empty",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.xs))
                        Text(
                            text = "Follow-ups appear here only when an agent explicitly requests one after confirming the call outcome, consent is recorded, and the recipient has not opted out. Sending is a separate manual queue action.",
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
                items(jobs, key = { it.id }) { job ->
                    CompactMessageJobCard(
                        job = job,
                        onRetry = {
                            if (job.status == "UNKNOWN") {
                                unknownJobToRetry = job
                            } else {
                                viewModel.retryMessageJob(job.jobId)
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(AppSpacing.md)) }
            }
        }
    }

    unknownJobToRetry?.let { job ->
        AlertDialog(
            onDismissRequest = { unknownJobToRetry = null },
            title = { Text("Verify Unknown Delivery", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "WhatsApp may have accepted the message for ${maskPhoneNumber(job.phoneNumber)}. " +
                        "Open the WhatsApp account on the test phone and confirm whether this exact " +
                        "follow-up is present. Only retry if it is definitely absent. A retry creates " +
                        "a new idempotency key.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.retryMessageJob(job.jobId, confirmUnknown = true)
                        unknownJobToRetry = null
                        Toast.makeText(
                            context,
                            "New idempotency key created for verified manual retry",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.errorLight)
                ) {
                    Text("Verified not delivered — retry")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { unknownJobToRetry = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CompactMessageJobCard(
    job: MessageJobEntity,
    onRetry: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppSpacing.cardRadius),
        colors = CardDefaults.cardColors(
            containerColor = when (job.status) {
                "FAILED", "UNKNOWN" -> AppStatusColors.errorBgLight.copy(alpha = 0.35f)
                "SENT" -> AppStatusColors.successBgLight.copy(alpha = 0.35f)
                "SENDING" -> AppStatusColors.warningBgLight.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = maskPhoneNumber(job.phoneNumber),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Template: ${job.templateName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusPill(job.status)
            }

            Spacer(modifier = Modifier.height(AppSpacing.xs))

            // Concise Failure or Last Attempt Summary
            if (!job.lastError.isNullOrBlank()) {
                val conciseError = if (job.lastError.contains("offline", ignoreCase = true) || job.lastError.contains("refused", ignoreCase = true)) {
                    "Proxy offline: run backend-proxy\\run-proxy.ps1 (port 8081)"
                } else if (job.lastError.contains("404", ignoreCase = true)) {
                    "Wrong server/port: use the backend proxy on 8081, not Apache on 8080"
                } else if (job.lastError.contains("401", ignoreCase = true)) {
                    "Proxy 401: Invalid or expired JWT session token"
                } else if (job.lastError.contains("gated", ignoreCase = true)) {
                    "Contract unverified: Configure proxy in Settings"
                } else if (job.lastError.contains("consent", ignoreCase = true)) {
                    "Consent not recorded: enable WA consent for this contact"
                } else if (job.status == "UNKNOWN") {
                    "Delivery unknown: verify in WhatsApp before any retry"
                } else {
                    job.lastError
                }

                Text(
                    text = "⚠️ $conciseError",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppStatusColors.errorLight,
                    maxLines = if (expanded) 10 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val dateStr = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(job.createdAt))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (job.status == "UNKNOWN") {
                        "$dateStr • No automatic retry"
                    } else {
                        "$dateStr • Attempts: ${job.retryCount}/3"
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val showRetry = job.status == "FAILED" ||
                        job.status == "UNKNOWN" ||
                        (job.status == "PENDING" && job.retryCount > 0)
                    if (showRetry) {
                        Text(
                            text = if (job.status == "UNKNOWN") "Verify & Retry" else "Retry",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (job.status == "UNKNOWN") {
                                AppStatusColors.errorLight
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier
                                .clickable { onRetry() }
                                .padding(horizontal = AppSpacing.xs, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                    }

                    Text(
                        text = if (expanded) "Hide Details ▲" else "View Details ▼",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            // Expandable Technical Diagnostics
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.sm)
                ) {
                    Surface(
                        shape = RoundedCornerShape(AppSpacing.xs),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(AppSpacing.sm)) {
                            Text(
                                text = "Idempotency Key (SHA-256):",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = job.jobId,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(AppSpacing.xs))
                            Text(
                                text = "Provider message ID: ${job.providerMessageId ?: "not returned"}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!job.lastError.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(AppSpacing.xs))
                                Text(
                                    text = "Raw Diagnostic Notice:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = job.lastError,
                                    fontSize = 10.sp,
                                    color = AppStatusColors.errorLight
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun maskPhoneNumber(phone: String): String {
    if (phone.length < 8) return phone
    val prefix = phone.take(5)
    val suffix = phone.takeLast(3)
    return "$prefix *** **$suffix"
}

@Composable
private fun StatusPill(status: String) {
    val (bg, color, text) = when (status) {
        "SENT" -> Triple(AppStatusColors.successBgLight, AppStatusColors.successLight, "ACCEPTED")
        "SENDING" -> Triple(AppStatusColors.warningBgLight, AppStatusColors.warningLight, "SENDING")
        "UNKNOWN" -> Triple(AppStatusColors.errorBgLight, AppStatusColors.errorLight, "UNKNOWN — VERIFY")
        "FAILED" -> Triple(AppStatusColors.errorBgLight, AppStatusColors.errorLight, "FAILED")
        else -> Triple(AppStatusColors.warningBgLight, AppStatusColors.warningLight, "PENDING")
    }

    Surface(shape = RoundedCornerShape(AppSpacing.pillRadius), color = bg) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs)
        )
    }
}

@Composable
private fun QueueMetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
