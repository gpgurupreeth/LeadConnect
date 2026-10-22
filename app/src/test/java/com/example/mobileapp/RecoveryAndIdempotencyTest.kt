package com.example.mobileapp

import com.example.mobileapp.data.local.dao.AuditEventDao
import com.example.mobileapp.data.local.dao.CallAttemptDao
import com.example.mobileapp.data.local.dao.MessageJobDao
import com.example.mobileapp.data.local.dao.SuppressionDao
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CallAttemptEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.data.local.entities.MessageJobEntity
import com.example.mobileapp.data.local.entities.SuppressionEntity
import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.domain.model.MessageStatus
import com.example.mobileapp.domain.workflow.CallStepResult
import com.example.mobileapp.domain.workflow.MessageDispatchResult
import com.example.mobileapp.domain.workflow.SequentialCallCoordinator
import com.example.mobileapp.messaging.IMessagingProvider
import com.example.mobileapp.messaging.MessagePayload
import com.example.mobileapp.messaging.MessageSendResult
import com.example.mobileapp.telecom.ICaller
import com.example.mobileapp.utils.IdempotencyUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecoveryAndIdempotencyTest {

    private lateinit var fakeCaller: FakeCaller
    private lateinit var fakeCallAttemptDao: FakeCallAttemptDao
    private lateinit var fakeMessageJobDao: FakeMessageJobDao
    private lateinit var fakeSuppressionDao: FakeSuppressionDao
    private lateinit var fakeAuditEventDao: FakeAuditEventDao
    private lateinit var fakeMessagingProvider: FakeMessagingProvider
    private lateinit var coordinator: SequentialCallCoordinator

    @Before
    fun setup() {
        fakeCaller = FakeCaller()
        fakeCallAttemptDao = FakeCallAttemptDao()
        fakeMessageJobDao = FakeMessageJobDao()
        fakeSuppressionDao = FakeSuppressionDao()
        fakeAuditEventDao = FakeAuditEventDao()
        fakeMessagingProvider = FakeMessagingProvider()

        coordinator = SequentialCallCoordinator(
            caller = fakeCaller,
            callAttemptDao = fakeCallAttemptDao,
            messageJobDao = fakeMessageJobDao,
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
        val result = coordinator.processCustomerCall(campaignId = 5L, customer = customer, templateName = "template_a")

        assertTrue("Expected SkippedAlreadyAttempted on recovery", result is CallStepResult.SkippedAlreadyAttempted)
        assertEquals(0, fakeCaller.callsPlaced.size)
    }

    @Test
    fun `recovery resumes pending message jobs safely after phone restart`() = runBlocking {
        // Jobs persisted in DB before device powered down
        val job1 = MessageJobEntity(
            jobId = "key_c5_u1_a1",
            campaignId = 5L,
            customerId = 1L,
            phoneNumber = "+919876540010",
            templateName = "followup",
            parametersJson = "{}",
            status = MessageStatus.PENDING.name,
            consentVerified = true
        )
        fakeMessageJobDao.jobs.add(job1)

        val pending = fakeMessageJobDao.getPendingJobsList(MessageStatus.PENDING.name)
        assertEquals(1, pending.size)

        // Process through coordinator
        val dispatchResult = coordinator.dispatchPendingMessageJob(job1.jobId, fakeMessagingProvider)
        assertTrue(dispatchResult is MessageDispatchResult.Sent)
        assertEquals(MessageStatus.SENT.name, fakeMessageJobDao.jobs.first().status)
    }

    @Test
    fun `idempotency key is invariant across network retries`() = runBlocking {
        val keyAttempt1 = IdempotencyUtils.generateJobId(campaignId = 5L, customerId = 1L, attemptId = 50L)
        val keyAttempt1Retry = IdempotencyUtils.generateJobId(campaignId = 5L, customerId = 1L, attemptId = 50L)
        assertEquals(keyAttempt1, keyAttempt1Retry)

        // Distinct call attempt produces distinct key
        val keyAttempt2 = IdempotencyUtils.generateJobId(campaignId = 5L, customerId = 1L, attemptId = 51L)
        assertNotEquals(keyAttempt1, keyAttempt2)
    }

    @Test
    fun `double suppression prevents message dispatch when customer opts out after call`() = runBlocking {
        val job = MessageJobEntity(
            jobId = "key_c5_u2_a2",
            campaignId = 5L,
            customerId = 2L,
            phoneNumber = "+919876540020",
            templateName = "followup",
            parametersJson = "{}",
            status = MessageStatus.PENDING.name,
            consentVerified = true
        )
        fakeMessageJobDao.jobs.add(job)

        // Customer requests opt-out before message worker dispatches
        fakeSuppressionDao.suppressedNumbers.add("+919876540020")

        val result = coordinator.dispatchPendingMessageJob(job.jobId, fakeMessagingProvider)
        assertTrue(result is MessageDispatchResult.Suppressed)
        assertEquals(0, fakeMessagingProvider.sentPayloads.size)
        assertEquals(MessageStatus.FAILED.name, fakeMessageJobDao.jobs.first().status)
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
            followUpEligible: Boolean,
            endTime: Long,
            duration: Int
        ) {
            val idx = attempts.indexOfFirst { it.id == attemptId }
            if (idx != -1) {
                attempts[idx] = attempts[idx].copy(
                    outcome = outcome,
                    notes = notes,
                    followUpEligible = followUpEligible,
                    callEndTime = endTime,
                    durationSeconds = duration
                )
            }
        }
    }

    class FakeMessageJobDao : MessageJobDao {
        val jobs = mutableListOf<MessageJobEntity>()
        override fun getAllJobsFlow(): Flow<List<MessageJobEntity>> = emptyFlow()
        override fun getJobsByStatus(status: String): Flow<List<MessageJobEntity>> = emptyFlow()
        override suspend fun getPendingJobsList(status: String): List<MessageJobEntity> {
            return jobs.filter { it.status == status }
        }
        override suspend fun getJobByJobId(jobId: String): MessageJobEntity? = jobs.find { it.jobId == jobId }
        override suspend fun insert(job: MessageJobEntity): Long {
            if (jobs.any { it.jobId == job.jobId }) return -1L
            jobs.add(job)
            return jobs.size.toLong()
        }
        override suspend fun update(job: MessageJobEntity) {
            val index = jobs.indexOfFirst { it.jobId == job.jobId }
            if (index != -1) jobs[index] = job
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

    class FakeMessagingProvider : IMessagingProvider {
        val sentPayloads = mutableListOf<MessagePayload>()
        override suspend fun sendMessage(payload: MessagePayload): MessageSendResult {
            sentPayloads.add(payload)
            return MessageSendResult(success = true, providerMessageId = "wa_${payload.jobId}")
        }
        override suspend fun getDeliveryStatus(jobId: String): MessageStatus = MessageStatus.SENT
    }
}
