package com.example.mobileapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileapp.domain.engine.EngineExecutionState
import com.example.mobileapp.ui.theme.AppSpacing
import com.example.mobileapp.ui.theme.AppStatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: AppViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val engineState by viewModel.engine.engineState.collectAsState()
    val currentCampaign by viewModel.engine.currentCampaign.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = AppSpacing.sm)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sales Caller & WhatsApp",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Automated SIM Automation Engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        // Mode Badge in Header
                        Surface(
                            shape = RoundedCornerShape(AppSpacing.pillRadius),
                            color = if (isDemoMode) AppStatusColors.warningBgLight else AppStatusColors.successBgLight,
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = if (isDemoMode) "🛡️ DEMO" else "📱 LIVE SIM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDemoMode) AppStatusColors.warningLight else AppStatusColors.successLight,
                                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                val navItems = listOf(
                    Triple(0, "Home", Icons.Default.Home),
                    Triple(1, "Contacts", Icons.Default.Person),
                    Triple(2, "Campaign", Icons.Default.Phone),
                    Triple(3, "Queue", Icons.Default.Send),
                    Triple(4, "Settings", Icons.Default.Settings)
                )

                navItems.forEach { (index, label, icon) ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(viewModel = viewModel, onNavigateToTab = { selectedTab = it })
                1 -> ContactsScreen(viewModel = viewModel)
                2 -> CampaignScreen(viewModel = viewModel)
                3 -> WhatsAppQueueScreen(viewModel = viewModel)
                4 -> SettingsScreen(viewModel = viewModel)
            }

            // Global Outcome Confirmation Dialog
            if (engineState is EngineExecutionState.AwaitingConfirmation) {
                val state = engineState as EngineExecutionState.AwaitingConfirmation
                OutcomeConfirmationDialog(
                    attemptId = state.attemptId,
                    customer = state.customer,
                    templateName = currentCampaign?.messageTemplate,
                    onConfirm = { outcome, sendWhatsApp, notes ->
                        viewModel.resolveOutcome(
                            attemptId = state.attemptId,
                            customer = state.customer,
                            outcome = outcome,
                            sendWhatsApp = sendWhatsApp,
                            notes = notes
                        )
                    },
                    onDismiss = {
                        viewModel.engine.pauseCampaign("Agent paused at confirmation step")
                    }
                )
            }
        }
    }
}
