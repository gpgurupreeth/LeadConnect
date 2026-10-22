package com.example.mobileapp

import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CallAttemptEntity
import com.example.mobileapp.data.local.entities.CampaignEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.data.local.entities.MessageJobEntity
import com.example.mobileapp.data.local.entities.SuppressionEntity
import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.domain.model.CampaignStatus
import com.example.mobileapp.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseEntitiesTest {

    @Test
    fun `test CustomerEntity instantiation and properties`() {
        val customer = CustomerEntity(
            id = 1L,
            name = "Test Lead",
            phoneNumber = "+919876543210",
            company = "Acme Corp",
            tags = "vip,q4",
            isSuppressed = false
        )
        assertEquals(1L, customer.id)
        assertEquals("Test Lead", customer.name)
        assertEquals("+919876543210", customer.phoneNumber)
        assertEquals("Acme Corp", customer.company)
        assertFalse(customer.isSuppressed)
        assertFalse(customer.consentGiven)
    }

    @Test
    fun `test CampaignEntity instantiation and status`() {
        val campaign = CampaignEntity(
            id = 10L,
            name = "Q4 Outreach",
            status = CampaignStatus.RUNNING.name,
            totalContacts = 100,
            completedContacts = 15,
            messageTemplate = "welcome_followup"
        )
        assertEquals(10L, campaign.id)
        assertEquals("Q4 Outreach", campaign.name)
        assertEquals(CampaignStatus.RUNNING.name, campaign.status)
        assertEquals(100, campaign.totalContacts)
    }

    @Test
    fun `test CallAttemptEntity instantiation and outcome safety`() {
        val attempt = CallAttemptEntity(
            id = 101L,
            campaignId = 10L,
            customerId = 1L,
            phoneNumber = "+919876543210",
            callStartTime = System.currentTimeMillis() - 30000,
            callEndTime = System.currentTimeMillis(),
            durationSeconds = 30,
            outcome = CallOutcome.UNKNOWN.name,
            notes = "Agent confirmation pending",
            followUpEligible = false
        )
        assertEquals(CallOutcome.UNKNOWN.name, attempt.outcome)
        assertFalse(attempt.followUpEligible)
    }

    @Test
    fun `test MessageJobEntity idempotency and pending status`() {
        val job = MessageJobEntity(
            id = 501L,
            jobId = "deterministic_sha256_hash",
            campaignId = 10L,
            customerId = 1L,
            phoneNumber = "+919876543210",
            templateName = "welcome_template",
            parametersJson = """{"name":"Test Lead"}""",
            status = MessageStatus.PENDING.name,
            retryCount = 0
        )
        assertEquals("deterministic_sha256_hash", job.jobId)
        assertEquals(MessageStatus.PENDING.name, job.status)
        assertEquals(0, job.retryCount)
        assertFalse(job.consentVerified)
        assertNull(job.providerMessageId)
    }

    @Test
    fun `test AuditEventEntity instantiation`() {
        val event = AuditEventEntity(
            id = 1L,
            eventType = "CAMPAIGN_STARTED",
            details = "Campaign 10 started by user"
        )
        assertEquals("CAMPAIGN_STARTED", event.eventType)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `test SuppressionEntity opt-out entry`() {
        val suppression = SuppressionEntity(
            phoneNumber = "+919876543210",
            reason = "Customer requested DND opt-out"
        )
        assertEquals("+919876543210", suppression.phoneNumber)
        assertTrue(suppression.reason.contains("DND"))
    }
}
