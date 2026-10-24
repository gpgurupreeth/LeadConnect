package com.example.mobileapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.mobileapp.data.local.entities.CampaignEntity
import com.example.mobileapp.ui.components.AppBackground

@Composable
fun MainApp(viewModel: AppViewModel) {
    val sections by viewModel.sectionsFlow.collectAsState(initial = emptyList())
    var selectedSectionId by remember { mutableStateOf<Long?>(null) }

    val activeSection = sections.find { it.id == selectedSectionId }

    AppBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (activeSection == null) {
                SectionsScreen(
                    viewModel = viewModel,
                    onOpenSection = { section ->
                        selectedSectionId = section.id
                    }
                )
            } else {
                SectionDetailScreen(
                    section = activeSection,
                    viewModel = viewModel,
                    onBack = {
                        selectedSectionId = null
                    }
                )
            }
        }
    }
}
