package com.example.mobileapp

import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.telecom.OutcomeClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class OutcomeClassifierTest {

    @Test
    fun `when user confirms answered, returns ANSWERED regardless of duration`() {
        val result = OutcomeClassifier.classify(
            callDurationSeconds = 15,
            wasOffhook = true,
            userConfirmedAnswered = true
        )
        assertEquals(CallOutcome.ANSWERED, result)
    }

    @Test
    fun `when user confirms not answered, returns NO_ANSWER`() {
        val result = OutcomeClassifier.classify(
            callDurationSeconds = 0,
            wasOffhook = false,
            userConfirmedAnswered = false
        )
        assertEquals(CallOutcome.NO_ANSWER, result)
    }

    @Test
    fun `when call duration is positive without confirmation, returns UNKNOWN to prevent false assumptions`() {
        // Crucial requirement: Duration alone cannot prove human answered vs voicemail/IVR
        val result = OutcomeClassifier.classify(
            callDurationSeconds = 42,
            wasOffhook = true,
            userConfirmedAnswered = null
        )
        assertEquals(CallOutcome.UNKNOWN, result)
    }

    @Test
    fun `when call was not offhook without confirmation, returns UNKNOWN`() {
        // Android telephony cannot reliably distinguish busy tone vs rejection vs timeout
        val result = OutcomeClassifier.classify(
            callDurationSeconds = 0,
            wasOffhook = false,
            userConfirmedAnswered = null
        )
        assertEquals(CallOutcome.UNKNOWN, result)
    }

    @Test
    fun `when call duration is zero and was offhook, returns UNKNOWN`() {
        val result = OutcomeClassifier.classify(
            callDurationSeconds = 0,
            wasOffhook = true,
            userConfirmedAnswered = null
        )
        assertEquals(CallOutcome.UNKNOWN, result)
    }
}
