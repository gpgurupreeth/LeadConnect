package com.example.mobileapp.domain.model

enum class CallOutcome {
    ANSWERED,
    NO_ANSWER,
    BUSY,
    REJECTED,
    UNKNOWN
}

enum class CampaignStatus {
    DRAFT,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED
}
