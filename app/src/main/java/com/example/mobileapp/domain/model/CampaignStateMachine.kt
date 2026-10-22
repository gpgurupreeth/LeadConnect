package com.example.mobileapp.domain.model

object CampaignStateMachine {
    fun canTransition(from: CampaignStatus, to: CampaignStatus): Boolean {
        if (from == to) return true
        return when (from) {
            CampaignStatus.DRAFT -> to == CampaignStatus.RUNNING || to == CampaignStatus.CANCELLED
            CampaignStatus.RUNNING -> to == CampaignStatus.PAUSED || to == CampaignStatus.COMPLETED || to == CampaignStatus.CANCELLED
            CampaignStatus.PAUSED -> to == CampaignStatus.RUNNING || to == CampaignStatus.CANCELLED
            CampaignStatus.COMPLETED -> false // Terminal state
            CampaignStatus.CANCELLED -> false // Terminal state
        }
    }
}
