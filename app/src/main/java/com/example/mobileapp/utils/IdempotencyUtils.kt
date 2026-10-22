package com.example.mobileapp.utils

import java.security.MessageDigest
import java.util.UUID

object IdempotencyUtils {
    /**
     * Generates a deterministic idempotency key for a message job based on campaign, customer, and attempt.
     * Prevents duplicate message sending on retries or repeated queue invocations.
     */
    fun generateJobId(campaignId: Long, customerId: Long, attemptId: Long): String {
        val raw = "msg_c${campaignId}_u${customerId}_a${attemptId}"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a fresh key for a manual retry after an UNKNOWN delivery outcome.
     * The old key may be locked as unknown on the server, so it must never be reused.
     * The result stays within the backend's 128-character jobId limit.
     */
    fun generateManualRetryJobId(originalJobId: String): String {
        val suffix = UUID.randomUUID().toString()
        return "${originalJobId.take(80)}-r-$suffix"
    }
}
