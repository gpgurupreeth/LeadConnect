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

enum class MessageStatus {
    /** Queued locally, not yet claimed for dispatch. */
    PENDING,

    /** Claimed for dispatch. A crash here must not be auto-retried. */
    SENDING,

    /** The proxy accepted the message. This is not proof of delivery. */
    SENT,

    DELIVERED,
    READ,

    /** The provider definitively rejected or did not send the message. */
    FAILED,

    /** The proxy/provider may have sent it. Verify manually before retrying. */
    UNKNOWN
}
