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

class SequentialCallCoordinatorTest {

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
    fun `suppressed customer is skipped and audited without dialing`() = runBlocking {
        fakeSuppressionDao.suppressedNumbers.add("+919876543210")
        val customer = CustomerEntity(id = 1L, name = "Opted Out", phoneNumber = "+919876543210")

        val result = coordinator.processCustomerCall(1L, customer)

        assertTrue(result is CallStepResult.Suppressed)
        assertEquals(0, fakeCaller.callsPlaced.size)
        assertTrue(fakeAuditEventDao.events.any { it.eventType == "CALL_SUPPRESSED" })
    }

    @Test
    fun `placing call pauses in AwaitingConfirmation state to await human confirmation`() = runBlocking {
        val customer = CustomerEntity(id = 2L, name = "Valid Lead", phoneNumber = "+919876543211")

        val result = coordinator.processCustomerCall(1L, customer)

        assertTrue(result is CallStepResult.AwaitingConfirmation)
        assertEquals(1, fakeCaller.callsPlaced.size)
        assertEquals(1, fakeCallAttemptDao.attempts.size)
        assertEquals("UNKNOWN", fakeCallAttemptDao.attempts.first().outcome)
    }

    @Test
    fun `recovery guard prevents redialing customer whose call attempt was already initiated`() = runBlocking {
        val customer = CustomerEntity(id = 3L, name = "Interrupted Lead", phoneNumber = "+919876543212")

        fakeCallAttemptDao.attempts.add(
            CallAttemptEntity(
                campaignId = 1L,
                customerId = 3L,
                phoneNumber = customer.phoneNumber,
                callStartTime = System.currentTimeMillis() - 60000,
                durationSeconds = 0,
                outcome = CallOutcome.UNKNOWN.name
            )
        )

        val result = coordinator.processCustomerCall(1L, customer)

        assertTrue(result is CallStepResult.SkippedAlreadyAttempted)
        assertEquals(0, fakeCaller.callsPlaced.size)
        assertTrue(fakeAuditEventDao.events.any { it.eventType == "CALL_SKIPPED_ALREADY_ATTEMPTED" })
    }

    @Test
    fun `confirming outcome as answered updates attempt and logs audit event`() = runBlocking {
        fakeCallAttemptDao.attempts.add(
            CallAttemptEntity(
                id = 100L,
                campaignId = 1L,
                customerId = 2L,
                phoneNumber = "+919876543211",
                callStartTime = System.currentTimeMillis() - 45000,
                durationSeconds = 0,
                outcome = CallOutcome.UNKNOWN.name
            )
        )

        val result = coordinator.confirmOutcome(
            attemptId = 100L,
            confirmedOutcome = CallOutcome.ANSWERED,
            notes = "Spoke with lead; interested",
            durationSeconds = 45
        )

        assertTrue(result is CallStepResult.Completed)
        val completed = result as CallStepResult.Completed
        assertEquals(CallOutcome.ANSWERED, completed.outcome)

        val updated = fakeCallAttemptDao.attempts.first { it.id == 100L }
        assertEquals(CallOutcome.ANSWERED.name, updated.outcome)
        assertEquals(45, updated.durationSeconds)
        assertEquals("Spoke with lead; interested", updated.notes)
        assertTrue(fakeAuditEventDao.events.any { it.eventType == "OUTCOME_CONFIRMED" })
    }

    @Test
    fun `failed telephony placement returns CallStepResult Failed`() = runBlocking {
        fakeCaller.shouldFail = true
        val customer = CustomerEntity(id = 5L, name = "Failing Lead", phoneNumber = "+919876543255")

        val result = coordinator.processCustomerCall(1L, customer)

        assertTrue(result is CallStepResult.Failed)
        assertEquals(0, fakeCallAttemptDao.attempts.size)
    }

    // --- Fakes ---
    class FakeCaller : ICaller {
        var shouldFail = false
        val callsPlaced = mutableListOf<String>()
        override fun placeCall(phoneNumber: String): Boolean {
            if (shouldFail) return false
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
            val assignedId = if (attempt.id == 0L) (attempts.size + 1).toLong() else attempt.id
            attempts.add(attempt.copy(id = assignedId))
            return assignedId
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
