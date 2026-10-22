package com.example.mobileapp.messaging

import com.example.mobileapp.domain.model.MessageStatus

/**
 * Stub / gated implementation of [IMessagingProvider].
 *
 * NOTE: Production WhatsApp dispatch is gated behind this interface until the backend proxy
 * and WAHA session contract are independently verified. No undocumented or guessed endpoints
 * are invoked. The live Android path uses [BackendProxyClient], not this stub.
 */
class WaaKgStubProvider : IMessagingProvider {

    override suspend fun sendMessage(payload: MessagePayload): MessageSendResult {
        return MessageSendResult(
            success = false,
            error = "WAHA live endpoint gated. Backend proxy contract verification required.",
            outcome = DeliveryOutcome.NOT_SENT
        )
    }

    override suspend fun getDeliveryStatus(jobId: String): MessageStatus {
        return MessageStatus.PENDING
    }
}
