package com.example.mobileapp.messaging

import com.example.mobileapp.domain.model.MessageStatus

data class MessagePayload(
    val jobId: String,
    val phoneNumber: String,
    val templateName: String,
    val parameters: Map<String, String>,
    // Explicit proof supplied by the local queue. Defaults to false so a
    // caller cannot accidentally dispatch without consent.
    val consent: Boolean = false,
    val optOut: Boolean = false
)

/**
 * Delivery outcome for one dispatch attempt.
 *
 * UNKNOWN means the request may have reached the provider. It must never be
 * retried automatically with the same job id.
 */
enum class DeliveryOutcome {
    ACCEPTED,
    NOT_SENT,
    UNKNOWN
}

data class MessageSendResult(
    val success: Boolean,
    val providerMessageId: String? = null,
    val error: String? = null,
    val outcome: DeliveryOutcome = DeliveryOutcome.UNKNOWN
)

interface IMessagingProvider {
    suspend fun sendMessage(payload: MessagePayload): MessageSendResult
    suspend fun getDeliveryStatus(jobId: String): MessageStatus
}
