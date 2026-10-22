package com.example.mobileapp

import com.example.mobileapp.utils.IdempotencyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageJobIdempotencyTest {

    @Test
    fun `generateJobId is deterministic for same campaign, customer and attempt`() {
        val id1 = IdempotencyUtils.generateJobId(campaignId = 1L, customerId = 100L, attemptId = 5L)
        val id2 = IdempotencyUtils.generateJobId(campaignId = 1L, customerId = 100L, attemptId = 5L)
        assertEquals(id1, id2)
    }

    @Test
    fun `generateJobId produces distinct keys for different attempts or customers`() {
        val idAttempt1 = IdempotencyUtils.generateJobId(campaignId = 1L, customerId = 100L, attemptId = 1L)
        val idAttempt2 = IdempotencyUtils.generateJobId(campaignId = 1L, customerId = 100L, attemptId = 2L)
        val idCustomer2 = IdempotencyUtils.generateJobId(campaignId = 1L, customerId = 101L, attemptId = 1L)

        assertNotEquals(idAttempt1, idAttempt2)
        assertNotEquals(idAttempt1, idCustomer2)
    }

    @Test
    fun `generateManualRetryJobId creates a fresh bounded key for unknown delivery`() {
        val original = IdempotencyUtils.generateJobId(campaignId = 1L, customerId = 100L, attemptId = 5L)
        val retry1 = IdempotencyUtils.generateManualRetryJobId(original)
        val retry2 = IdempotencyUtils.generateManualRetryJobId(original)

        assertNotEquals(original, retry1)
        assertNotEquals(retry1, retry2)
        assertEquals(64 + 3 + 36, retry1.length)
        assertTrue(retry1.length <= 128)
        assertTrue(retry1.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{5,199}$")))
    }
}
