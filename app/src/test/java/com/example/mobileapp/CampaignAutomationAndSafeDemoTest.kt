package com.example.mobileapp

import com.example.mobileapp.data.local.dao.AuditEventDao
import com.example.mobileapp.data.local.dao.CallAttemptDao
import com.example.mobileapp.data.local.dao.SuppressionDao
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CallAttemptEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.domain.model.CampaignStateMachine
import com.example.mobileapp.domain.model.CampaignStatus
import com.example.mobileapp.domain.workflow.CallStepResult
import com.example.mobileapp.domain.workflow.SequentialCallCoordinator
import com.example.mobileapp.telecom.DemoSimCaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignAutomationAndSafeDemoTest {

    @Test
    fun `test DemoSimCaller simulates calls safely without cellular network`() {
        val demoCaller = DemoSimCaller()
        assertEquals(0, demoCaller.simulatedCallsCount)

        val success = demoCaller.placeCall("+919876543210")
        assertTrue(success)
        assertEquals(1, demoCaller.simulatedCallsCount)
        assertEquals("+919876543210", demoCaller.lastSimulatedNumber)
    }

    @Test
    fun `test campaign lifecycle state transitions for Pause Resume and Stop`() {
        // DRAFT -> RUNNING
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.DRAFT, CampaignStatus.RUNNING))

        // RUNNING -> PAUSED
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.RUNNING, CampaignStatus.PAUSED))

        // PAUSED -> RUNNING (Resume)
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.PAUSED, CampaignStatus.RUNNING))

        // RUNNING -> CANCELLED (Stop)
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.RUNNING, CampaignStatus.CANCELLED))

        // COMPLETED is terminal
        assertFalse(CampaignStateMachine.canTransition(CampaignStatus.COMPLETED, CampaignStatus.RUNNING))
    }

    @Test
    fun `test sequential progression with safe confirmation`() = runBlocking {
        val demoCaller = DemoSimCaller()
        val mockAttemptDao = MockCallAttemptDao()
        val mockSuppressionDao = MockSuppressionDao()
        val mockAuditDao = MockAuditEventDao()

        val coordinator = SequentialCallCoordinator(
            caller = demoCaller,
            callAttemptDao = mockAttemptDao,
            suppressionDao = mockSuppressionDao,
            auditEventDao = mockAuditDao
        )

        val lead = CustomerEntity(
            id = 1L,
            name = "Test Lead",
            phoneNumber = "+919876543210",
            company = "Test Co"
        )

        // Step 1: Initiate call
        val step1 = coordinator.processCustomerCall(
            campaignId = 100L,
            customer = lead
        )

        // Must pause for confirmation rather than auto-assuming outcome
        assertTrue(step1 is CallStepResult.AwaitingConfirmation)
        val awaiting = step1 as CallStepResult.AwaitingConfirmation
        assertEquals(1001L, awaiting.attemptId)
        assertEquals(1, demoCaller.simulatedCallsCount)

        // Initial attempt persisted as UNKNOWN
        assertEquals("UNKNOWN", mockAttemptDao.savedAttempts.first().outcome)

        // Step 2: Confirm human outcome
        val step2 = coordinator.confirmOutcome(
            attemptId = awaiting.attemptId,
            confirmedOutcome = CallOutcome.ANSWERED,
            notes = "Lead spoke for 45s",
            durationSeconds = 45
        )

        assertTrue(step2 is CallStepResult.Completed)
        val completed = step2 as CallStepResult.Completed
        assertEquals(CallOutcome.ANSWERED, completed.outcome)

        val updatedAttempt = mockAttemptDao.savedAttempts.first { it.id == awaiting.attemptId }
        assertEquals(CallOutcome.ANSWERED.name, updatedAttempt.outcome)
        assertEquals(45, updatedAttempt.durationSeconds)
        assertEquals("Lead spoke for 45s", updatedAttempt.notes)
    }

    @Test
    fun `test pre-dial suppression skips calling and triggers no simulated call`() = runBlocking {
        val demoCaller = DemoSimCaller()
        val mockAttemptDao = MockCallAttemptDao()
        val mockSuppressionDao = MockSuppressionDao()
        val mockAuditDao = MockAuditEventDao()

        mockSuppressionDao.suppressedNumbers.add("+919876543299")

        val coordinator = SequentialCallCoordinator(
            caller = demoCaller,
            callAttemptDao = mockAttemptDao,
            suppressionDao = mockSuppressionDao,
            auditEventDao = mockAuditDao
        )

        val suppressedLead = CustomerEntity(
            id = 2L,
            name = "Suppressed Lead",
            phoneNumber = "+919876543299",
            isSuppressed = true
        )

        val result = coordinator.processCustomerCall(
            campaignId = 100L,
            customer = suppressedLead
        )

        assertTrue(result is CallStepResult.Suppressed)
        assertEquals(0, demoCaller.simulatedCallsCount)
        assertEquals(0, mockAttemptDao.savedAttempts.size)
    }

    private class MockCallAttemptDao : CallAttemptDao {
        val savedAttempts = mutableListOf<CallAttemptEntity>()

        override fun getAllHistory(): Flow<List<CallAttemptEntity>> = emptyFlow()
        override fun getAttemptsForSection(sectionId: Long): Flow<List<CallAttemptEntity>> = emptyFlow()
        override suspend fun deleteAttemptsBySection(sectionId: Long) { savedAttempts.removeAll { it.campaignId == sectionId } }
        override suspend fun clearAllHistory() { savedAttempts.clear() }
        override fun getAttemptsForCampaign(campaignId: Long): Flow<List<CallAttemptEntity>> = emptyFlow()
        override fun getRecentAttempts(limit: Int): Flow<List<CallAttemptEntity>> = emptyFlow()
        override suspend fun hasAttemptForCustomer(campaignId: Long, customerId: Long): Boolean =
            savedAttempts.any { it.campaignId == campaignId && it.customerId == customerId }
        override suspend fun getLatestAttemptForCustomer(campaignId: Long, customerId: Long): CallAttemptEntity? =
            savedAttempts.lastOrNull { it.campaignId == campaignId && it.customerId == customerId }
        override suspend fun insert(attempt: CallAttemptEntity): Long {
            savedAttempts.add(attempt.copy(id = 1001L))
            return 1001L
        }
        override suspend fun update(attempt: CallAttemptEntity) {
            val idx = savedAttempts.indexOfFirst { it.id == attempt.id }
            if (idx != -1) savedAttempts[idx] = attempt
        }
        override suspend fun updateOutcome(
            attemptId: Long,
            outcome: String,
            notes: String?,
            endTime: Long,
            duration: Int
        ) {
            val idx = savedAttempts.indexOfFirst { it.id == attemptId }
            if (idx != -1) {
                savedAttempts[idx] = savedAttempts[idx].copy(
                    outcome = outcome,
                    notes = notes,
                    callEndTime = endTime,
                    durationSeconds = duration
                )
            }
        }
    }

    private class MockSuppressionDao : SuppressionDao {
        val suppressedNumbers = mutableSetOf<String>()
        override fun getAllSuppressions(): Flow<List<com.example.mobileapp.data.local.entities.SuppressionEntity>> = emptyFlow()
        override suspend fun getAllSuppressedNumbers(): List<String> = suppressedNumbers.toList()
        override suspend fun isSuppressed(phoneNumber: String): Boolean = suppressedNumbers.contains(phoneNumber)
        override suspend fun insert(suppression: com.example.mobileapp.data.local.entities.SuppressionEntity) {
            suppressedNumbers.add(suppression.phoneNumber)
        }
        override suspend fun delete(phoneNumber: String) {
            suppressedNumbers.remove(phoneNumber)
        }
    }

    private class MockAuditEventDao : AuditEventDao {
        val events = mutableListOf<AuditEventEntity>()
        override fun getRecentEvents(limit: Int): Flow<List<AuditEventEntity>> = emptyFlow()
        override suspend fun insert(event: AuditEventEntity): Long {
            events.add(event)
            return events.size.toLong()
        }
    }
}
