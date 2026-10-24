package com.example.mobileapp

import com.example.mobileapp.data.local.dao.AuditEventDao
import com.example.mobileapp.data.local.dao.CallAttemptDao
import com.example.mobileapp.data.local.dao.SuppressionDao
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CallAttemptEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.data.local.entities.SuppressionEntity
import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.domain.workflow.CallStepResult
import com.example.mobileapp.domain.workflow.SequentialCallCoordinator
import com.example.mobileapp.telecom.ICaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecoveryAndIdempotencyTest {

    private lateinit var fakeCaller: FakeCaller
    private lateinit var fakeCallAttemptDao: FakeCallAttemptDao
    private lateinit var fakeSuppressionDao: FakeSuppressionDao
    private lateinit var fakeAuditEventDao: FakeAuditEventDao
    private lateinit var coordinator: SequentialCallCoordinator

    @Before
    fun setup() {
        fakeCaller = FakeCaller()
        fakeCallAttemptDao = FakeCallAttemptDao()
        fakeSuppressionDao = FakeSuppressionDao()
        fakeAuditEventDao = FakeAuditEventDao()

        coordinator = SequentialCallCoordinator(
            caller = fakeCaller,
            callAttemptDao = fakeCallAttemptDao,
            suppressionDao = fakeSuppressionDao,
            auditEventDao = fakeAuditEventDao
        )
    }

    @Test
    fun `process death recovery does not redial already initiated customer`() = runBlocking {
        val customer = CustomerEntity(id = 10L, name = "Crashed Call Customer", phoneNumber = "+919876540001")

        // Pre-populate state simulating in-flight call right before app killed
        fakeCallAttemptDao.attempts.add(
            CallAttemptEntity(
                campaignId = 5L,
                customerId = 10L,
                phoneNumber = customer.phoneNumber,
                callStartTime = System.currentTimeMillis() - 120000,
                durationSeconds = 0,
                outcome = CallOutcome.UNKNOWN.name
            )
        )

        // Recovery routine runs after app relaunch
        val result = coordinator.processCustomerCall(campaignId = 5L, customer = customer)

        assertTrue("Expected SkippedAlreadyAttempted on recovery", result is CallStepResult.SkippedAlreadyAttempted)
        assertEquals(0, fakeCaller.callsPlaced.size)
    }

    @Test
    fun `pre-dial suppression prevents calling suppressed customer`() = runBlocking {
        val customer = CustomerEntity(id = 12L, name = "Opted Out Lead", phoneNumber = "+919876540099")
        fakeSuppressionDao.suppressedNumbers.add(customer.phoneNumber)

        val result = coordinator.processCustomerCall(campaignId = 5L, customer = customer)

        assertTrue(result is CallStepResult.Suppressed)
        assertEquals(0, fakeCaller.callsPlaced.size)
    }

    // --- Fakes ---
    class FakeCaller : ICaller {
        val callsPlaced = mutableListOf<String>()
        override fun placeCall(phoneNumber: String): Boolean {
            callsPlaced.add(phoneNumber)
            return true
        }
    }

    class FakeCallAttemptDao : CallAttemptDao {
        val attempts = mutableListOf<CallAttemptEntity>()
        override fun getAllHistory(): Flow<List<CallAttemptEntity>> = emptyFlow()
        override fun getAttemptsForSection(sectionId: Long): Flow<List<CallAttemptEntity>> = emptyFlow()
        override suspend fun deleteAttemptsBySection(sectionId: Long) { attempts.removeAll { it.campaignId == sectionId } }
        override suspend fun clearAllHistory() { attempts.clear() }
        override fun getAttemptsForCampaign(campaignId: Long): Flow<List<CallAttemptEntity>> = emptyFlow()
        override fun getRecentAttempts(limit: Int): Flow<List<CallAttemptEntity>> = emptyFlow()
        override suspend fun hasAttemptForCustomer(campaignId: Long, customerId: Long): Boolean {
            return attempts.any { it.campaignId == campaignId && it.customerId == customerId }
        }
        override suspend fun getLatestAttemptForCustomer(campaignId: Long, customerId: Long): CallAttemptEntity? {
            return attempts.filter { it.campaignId == campaignId && it.customerId == customerId }.maxByOrNull { it.timestamp }
        }
        override suspend fun insert(attempt: CallAttemptEntity): Long {
            attempts.add(attempt)
            return attempts.size.toLong()
        }
        override suspend fun update(attempt: CallAttemptEntity) {
            val idx = attempts.indexOfFirst { it.id == attempt.id }
            if (idx != -1) attempts[idx] = attempt
        }
        override suspend fun updateOutcome(
            attemptId: Long,
            outcome: String,
            notes: String?,
            endTime: Long,
            duration: Int
        ) {
            val idx = attempts.indexOfFirst { it.id == attemptId }
            if (idx != -1) {
                attempts[idx] = attempts[idx].copy(
                    outcome = outcome,
                    notes = notes,
                    callEndTime = endTime,
                    durationSeconds = duration
                )
            }
        }
    }

    class FakeSuppressionDao : SuppressionDao {
        val suppressedNumbers = mutableSetOf<String>()
        override fun getAllSuppressions(): Flow<List<SuppressionEntity>> = emptyFlow()
        override suspend fun getAllSuppressedNumbers(): List<String> = suppressedNumbers.toList()
        override suspend fun isSuppressed(phoneNumber: String): Boolean = suppressedNumbers.contains(phoneNumber)
        override suspend fun insert(suppression: SuppressionEntity) { suppressedNumbers.add(suppression.phoneNumber) }
        override suspend fun delete(phoneNumber: String) { suppressedNumbers.remove(phoneNumber) }
    }

    class FakeAuditEventDao : AuditEventDao {
        val events = mutableListOf<AuditEventEntity>()
        override fun getRecentEvents(limit: Int): Flow<List<AuditEventEntity>> = emptyFlow()
        override suspend fun insert(event: AuditEventEntity): Long {
            events.add(event)
            return events.size.toLong()
        }
    }
}
