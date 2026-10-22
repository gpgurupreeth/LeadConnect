package com.example.mobileapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mobileapp.messaging.ProxyContractStatus
import com.example.mobileapp.ui.theme.AppSpacing
import com.example.mobileapp.ui.theme.AppStatusColors

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val defaultCountryCode by viewModel.defaultCountryCode.collectAsState()
    val proxyUrl by viewModel.proxyUrl.collectAsState()
    val proxyToken by viewModel.proxyToken.collectAsState()
    val contractStatus by viewModel.proxyContractStatus.collectAsState()

    var countryCodeInput by remember { mutableStateOf(defaultCountryCode) }
    var urlInput by remember { mutableStateOf(proxyUrl) }
    var tokenInput by remember { mutableStateOf(proxyToken) }

    var isVerifying by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    var hasCallPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasPhoneStatePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCallPermission = perms[Manifest.permission.CALL_PHONE] == true
        hasPhoneStatePermission = perms[Manifest.permission.READ_PHONE_STATE] == true
        Toast.makeText(context, "Telephony permissions updated", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        Spacer(modifier = Modifier.height(AppSpacing.xs))

        Text(
            text = "Settings & Configuration",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 1. Telephony & Calling Controls Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppSpacing.cardRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(AppSpacing.lg)) {
                Text(
                    text = "Telephony & Calling Engine",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(AppSpacing.md))

                // Safe Demo Mode Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Safe Demo Mode",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Simulates calls without consuming carrier airtime or placing real calls.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDemoMode,
                        onCheckedChange = { viewModel.setDemoMode(it) }
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.md))

                // Default Country Code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Default Country Code",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Automatically prepended to 10-digit input numbers",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = countryCodeInput,
                        onValueChange = {
                            countryCodeInput = it
                            viewModel.setDefaultCountryCode(it)
                        },
                        modifier = Modifier.width(90.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(AppSpacing.buttonRadius),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 2. Android Runtime Permissions Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppSpacing.cardRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(AppSpacing.lg)) {
                Text(
                    text = "Android Telephony Permissions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(AppSpacing.sm))

                PermissionStatusItem(
                    name = "CALL_PHONE (Direct SIM Dialing)",
                    description = "Required to initiate automated phone calls on employee device.",
                    granted = hasCallPermission
                )

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                PermissionStatusItem(
                    name = "READ_PHONE_STATE (Call Observation)",
                    description = "Required to monitor call duration and idle state transitions.",
                    granted = hasPhoneStatePermission
                )

                Spacer(modifier = Modifier.height(AppSpacing.md))

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_PHONE_STATE
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                    shape = RoundedCornerShape(AppSpacing.buttonRadius)
                ) {
                    Text("Grant / Update Telephony Permissions")
                }
            }
        }

        // 3. WhatsApp Backend Proxy & Verification Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppSpacing.cardRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(AppSpacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔒", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                    Text(
                        text = "WhatsApp Backend Proxy Architecture",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.xs))

                Text(
                    text = "Security Invariant: The Android client never stores WAHA master API keys. Requests authenticate using short-lived JWTs to the backend proxy.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(AppSpacing.md))

                // Proxy URL Input with Validation
                val isUrlValid = urlInput.startsWith("http://") || urlInput.startsWith("https://")

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Backend Proxy Base URL", fontSize = 12.sp) },
                    placeholder = { Text("e.g. http://10.0.2.2:8081 (emulator) or http://192.168.1.50:8081 (phone)") },
                    isError = !isUrlValid && urlInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(AppSpacing.buttonRadius),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                if (!isUrlValid && urlInput.isNotBlank()) {
                    Text(
                        text = "URL must start with http:// or https://",
                        fontSize = 10.sp,
                        color = AppStatusColors.errorLight
                    )
                }

                Text(
                    text = "Emulator: http://10.0.2.2:8081  •  Phone: http://<PC-LAN-IP>:8081  •  " +
                        "USB: adb reverse tcp:8081 tcp:8081 then http://127.0.0.1:8081",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                // Short-Lived JWT Input
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Short-Lived JWT Session Token", fontSize = 12.sp) },
                    placeholder = { Text("Enter session token issued by backend login") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(AppSpacing.buttonRadius),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(AppSpacing.md))

                // Save & Test Connection Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.saveBackendProxyConfig(urlInput.trim(), tokenInput.trim())
                            Toast.makeText(context, "Proxy configuration saved to Keystore", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                        shape = RoundedCornerShape(AppSpacing.buttonRadius)
                    ) {
                        Text("Save Config")
                    }

                    Button(
                        onClick = {
                            viewModel.saveBackendProxyConfig(urlInput.trim(), tokenInput.trim())
                            isVerifying = true
                            viewModel.verifyBackendProxyContract { status ->
                                isVerifying = false
                                when (status) {
                                    is ProxyContractStatus.Verified -> {
                                        val message = if (status.liveDispatchReady) {
                                            "Proxy verified: live dispatch ready"
                                        } else {
                                            "Proxy verified: WAHA dispatch is gated"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                    is ProxyContractStatus.Unverified -> Toast.makeText(context, "Verification failed: ${status.reason}", Toast.LENGTH_LONG).show()
                                    is ProxyContractStatus.Offline -> Toast.makeText(context, "Proxy is offline", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isVerifying && isUrlValid,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                        shape = RoundedCornerShape(AppSpacing.buttonRadius)
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(AppSpacing.xs))
                            Text("Testing...")
                        } else {
                            Text("Verify Contract")
                        }
                    }
                }

                // Verification Status Card & Actionable Instructions
                contractStatus?.let { status ->
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    when (status) {
                        is ProxyContractStatus.Verified -> {
                            val liveReady = status.liveDispatchReady
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppSpacing.buttonRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (liveReady) {
                                        AppStatusColors.successBgLight
                                    } else {
                                        AppStatusColors.warningBgLight
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(AppSpacing.md)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (liveReady) "🟢" else "🟡", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(AppSpacing.xs))
                                        Text(
                                            text = if (liveReady) {
                                                "Proxy Contract Verified: Live dispatch ready over HTTPS/HTTP with JWT."
                                            } else {
                                                "Proxy Reachable — WhatsApp Dispatch Gated"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (liveReady) {
                                                AppStatusColors.successLight
                                            } else {
                                                AppStatusColors.warningLight
                                            }
                                        )
                                    }
                                    if (!liveReady) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = status.note,
                                            fontSize = 10.sp,
                                            color = Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                        is ProxyContractStatus.Unverified -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppSpacing.buttonRadius),
                                colors = CardDefaults.cardColors(containerColor = AppStatusColors.warningBgLight)
                            ) {
                                Column(modifier = Modifier.padding(AppSpacing.md)) {
                                    Text(
                                        text = "⚠️ Configuration Incomplete: ${status.reason}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppStatusColors.warningLight
                                    )
                                    Text(
                                        text = status.actionableGuide,
                                        fontSize = 10.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                        is ProxyContractStatus.Offline -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppSpacing.buttonRadius),
                                colors = CardDefaults.cardColors(containerColor = AppStatusColors.errorBgLight)
                            ) {
                                Column(modifier = Modifier.padding(AppSpacing.md)) {
                                    Text(
                                        text = "🔴 Gateway Offline (${status.url})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppStatusColors.errorLight
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "The backend proxy is not reachable. On the PC, run:",
                                        fontSize = 10.sp,
                                        color = Color.DarkGray
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "backend-proxy\\run-proxy.ps1",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = AppStatusColors.errorLight,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Diagnostic & Database Management Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppSpacing.cardRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(AppSpacing.lg)) {
                Text(
                    text = "Data Management & Reset",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(AppSpacing.sm))

                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppSpacing.minTouchTarget),
                    shape = RoundedCornerShape(AppSpacing.buttonRadius),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppStatusColors.errorLight)
                ) {
                    Text("Clear All Contacts from Database")
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.xl))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Contacts?") },
            text = { Text("This will remove all contacts from the local Room database. Past campaign history will remain.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearAllContacts {
                            Toast.makeText(context, "Contacts database cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppStatusColors.errorLight)
                ) {
                    Text("Clear Database")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PermissionStatusItem(
    name: String,
    description: String,
    granted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(text = description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
            shape = RoundedCornerShape(AppSpacing.pillRadius),
            color = if (granted) AppStatusColors.successBgLight else AppStatusColors.errorBgLight
        ) {
            Text(
                text = if (granted) "GRANTED" else "MISSING",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (granted) AppStatusColors.successLight else AppStatusColors.errorLight,
                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs)
            )
        }
    }
}
