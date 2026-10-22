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
import com.example.mobileapp.messaging.DeliveryOutcome
import com.example.mobileapp.messaging.IMessagingProvider
import com.example.mobileapp.messaging.MessagePayload
import com.example.mobileapp.messaging.MessageSendResult
import com.example.mobileapp.telecom.ICaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SequentialCallCoordinatorTest {

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
    fun `suppressed customer is skipped and audited without dialing`() = runBlocking {
        fakeSuppressionDao.suppressedNumbers.add("+919876543210")
        val customer = CustomerEntity(id = 1L, name = "Opted Out", phoneNumber = "+919876543210")

        val result = coordinator.processCustomerCall(1L, customer, "template_1")

        assertTrue(result is CallStepResult.Suppressed)
        assertEquals(0, fakeCaller.callsPlaced.size)
        assertTrue(fakeAuditEventDao.events.any { it.eventType == "CALL_SUPPRESSED" })
    }

    @Test
    fun `placing call pauses in AwaitingConfirmation state to prevent premature message sending`() = runBlocking {
        val customer = CustomerEntity(id = 2L, name = "Valid Lead", phoneNumber = "+919876543211")

        val result = coordinator.processCustomerCall(1L, customer, "template_1")

        assertTrue(result is CallStepResult.AwaitingConfirmation)
        assertEquals(1, fakeCaller.callsPlaced.size)
        assertEquals(1, fakeCallAttemptDao.attempts.size)
        // Message is NOT sent or queued yet
        assertEquals(0, fakeMessageJobDao.jobs.size)
    }

    @Test
    fun `recovery guard prevents redialing customer whose call attempt was already initiated`() = runBlocking {
        val customer = CustomerEntity(id = 3L, name = "Interrupted Lead", phoneNumber = "+919876543212")

        // Simulate that an attempt was initiated before app process crashed
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

        // Attempt to process again following crash recovery
        val result = coordinator.processCustomerCall(1L, customer, "template_1")

        assertTrue(result is CallStepResult.SkippedAlreadyAttempted)
        assertEquals(0, fakeCaller.callsPlaced.size)
        assertTrue(fakeAuditEventDao.events.any { it.eventType == "CALL_SKIPPED_ALREADY_ATTEMPTED" })
    }

    @Test
    fun `confirming outcome as answered queues follow-up without blocking call thread`() = runBlocking {
        val result = coordinator.confirmOutcomeAndQueueFollowUp(
            attemptId = 100L,
            campaignId = 1L,
            customerId = 2L,
            phoneNumber = "+919876543211",
            confirmedOutcome = CallOutcome.ANSWERED,
            followUpEligible = true,
            consentGiven = true,
            templateName = "welcome_followup"
        )

        assertTrue(result is CallStepResult.Completed)
        val completed = result as CallStepResult.Completed
        assertEquals(CallOutcome.ANSWERED, completed.outcome)
        assertTrue(completed.messageQueued)
        assertEquals(1, fakeMessageJobDao.jobs.size)
        assertEquals("welcome_followup", fakeMessageJobDao.jobs.first().templateName)
    }

    @Test
    fun `suppression enforced right before message dispatch cancels transmission`() = runBlocking {
        // Enqueue a job
        coordinator.confirmOutcomeAndQueueFollowUp(
            attemptId = 101L,
            campaignId = 1L,
            customerId = 4L,
            phoneNumber = "+919876543299",
            confirmedOutcome = CallOutcome.ANSWERED,
            followUpEligible = true,
            consentGiven = true,
            templateName = "promo_template"
        )
        val job = fakeMessageJobDao.jobs.first()

        // Customer opts out after call but before worker dispatches message
        fakeSuppressionDao.suppressedNumbers.add("+919876543299")

        val dispatchResult = coordinator.dispatchPendingMessageJob(job.jobId, fakeMessagingProvider)

        assertTrue(dispatchResult is MessageDispatchResult.Suppressed)
        assertEquals(0, fakeMessagingProvider.sentPayloads.size) // No external network dispatch
        val updatedJob = fakeMessageJobDao.jobs.first { it.jobId == job.jobId }
        assertEquals(MessageStatus.FAILED.name, updatedJob.status)
        assertTrue(fakeAuditEventDao.events.any { it.eventType == "MESSAGE_DISPATCH_SUPPRESSED" })
    }

    @Test
    fun `message job idempotency is preserved across retries without creating new keys`() = runBlocking {
        coordinator.confirmOutcomeAndQueueFollowUp(
            attemptId = 102L,
            campaignId = 1L,
            customerId = 5L,
            phoneNumber = "+919876543255",
            confirmedOutcome = CallOutcome.ANSWERED,
            followUpEligible = true,
            consentGiven = true,
            templateName = "promo_template"
        )
        val originalJob = fakeMessageJobDao.jobs.first()

        // Simulate provider transient failure
        fakeMessagingProvider.shouldFail = true
        val retry1 = coordinator.dispatchPendingMessageJob(originalJob.jobId, fakeMessagingProvider)

        assertTrue(retry1 is MessageDispatchResult.RetryScheduled)
        val retriedJob = fakeMessageJobDao.getJobByJobId(originalJob.jobId)!!
        assertEquals(originalJob.jobId, retriedJob.jobId) // Exact same idempotency key retained
        assertEquals(1, retriedJob.retryCount)

        // Genuinely distinct follow-up from a different attempt produces a distinct key
        coordinator.confirmOutcomeAndQueueFollowUp(
            attemptId = 103L,
            campaignId = 1L,
            customerId = 5L,
            phoneNumber = "+919876543255",
            confirmedOutcome = CallOutcome.ANSWERED,
            followUpEligible = true,
            consentGiven = true,
            templateName = "second_followup"
        )
        val newJob = fakeMessageJobDao.jobs.last()
        assertNotEquals(originalJob.jobId, newJob.jobId)
    }

    @Test
    fun `confirmed call without consent does not queue a follow-up`() = runBlocking {
        val result = coordinator.confirmOutcomeAndQueueFollowUp(
            attemptId = 104L,
            campaignId = 1L,
            customerId = 6L,
            phoneNumber = "+919876543266",
            confirmedOutcome = CallOutcome.ANSWERED,
            followUpEligible = true,
            consentGiven = false,
            templateName = "promo_template"
        )

        assertTrue(result is CallStepResult.Completed)
        assertFalse((result as CallStepResult.Completed).messageQueued)
        assertEquals(0, fakeMessageJobDao.jobs.size)
        assertTrue(fakeAuditEventDao.events.any { it.eventType == "MESSAGE_JOB_BLOCKED" })
    }

    @Test
    fun `opted out customer does not queue a follow-up even with consent`() = runBlocking {
        fakeSuppressionDao.suppressedNumbers.add("+919876543277")
        val result = coordinator.confirmOutcomeAndQueueFollowUp(
            attemptId = 105L,
            campaignId = 1L,
            customerId = 7L,
            phoneNumber = "+919876543277",
            confirmedOutcome = CallOutcome.NO_ANSWER,
            followUpEligible = true,
            consentGiven = true,
            templateName = "promo_template"
        )

        assertTrue(result is CallStepResult.Completed)
        assertFalse((result as CallStepResult.Completed).messageQueued)
        assertEquals(0, fakeMessageJobDao.jobs.size)
    }

    @Test
    fun `dispatch refuses a legacy job without verified consent`() = runBlocking {
        val job = MessageJobEntity(
            jobId = "legacy_job_without_consent",
            campaignId = 1L,
            customerId = 8L,
            phoneNumber = "+919876543288",
            templateName = "promo_template",
            parametersJson = "{}",
            status = MessageStatus.PENDING.name,
            consentVerified = false
        )
        fakeMessageJobDao.jobs.add(job)

        val result = coordinator.dispatchPendingMessageJob(job.jobId, fakeMessagingProvider)

        assertTrue(result is MessageDispatchResult.Failed)
        assertEquals(0, fakeMessagingProvider.attemptCount)
        assertEquals(MessageStatus.FAILED.name, fakeMessageJobDao.jobs.first().status)
        assertTrue(
            fakeAuditEventDao.events.any { it.eventType == "MESSAGE_DISPATCH_BLOCKED_NO_CONSENT" }
        )
    }

    @Test
    fun `unknown delivery marks job unknown and blocks automatic retry`() = runBlocking {
        val job = MessageJobEntity(
            jobId = "job_unknown_outcome",
            campaignId = 1L,
            customerId = 9L,
            phoneNumber = "+919876543299",
            templateName = "promo_template",
            parametersJson = "{}",
            status = MessageStatus.PENDING.name,
            consentVerified = true
        )
        fakeMessageJobDao.jobs.add(job)
        fakeMessagingProvider.shouldReturnUnknown = true

        val first = coordinator.dispatchPendingMessageJob(job.jobId, fakeMessagingProvider)
        val second = coordinator.dispatchPendingMessageJob(job.jobId, fakeMessagingProvider)

        assertTrue(first is MessageDispatchResult.Unknown)
        assertTrue(second is MessageDispatchResult.Unknown)
        assertEquals(1, fakeMessagingProvider.attemptCount)
        assertEquals(MessageStatus.UNKNOWN.name, fakeMessageJobDao.jobs.first().status)
        assertTrue(fakeAuditEventDao.events.any { it.eventType == "MESSAGE_DISPATCH_UNKNOWN" })
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
            if (index != -1) {
                jobs[index] = job
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

    class FakeMessagingProvider : IMessagingProvider {
        val sentPayloads = mutableListOf<MessagePayload>()
        var shouldFail = false
        var shouldReturnUnknown = false
        var attemptCount = 0

        override suspend fun sendMessage(payload: MessagePayload): MessageSendResult {
            attemptCount++
            if (shouldReturnUnknown) {
                return MessageSendResult(
                    success = false,
                    error = "Provider timeout after request was written; outcome unknown",
                    outcome = DeliveryOutcome.UNKNOWN
                )
            }
            return if (shouldFail) {
                MessageSendResult(
                    success = false,
                    error = "Definitive provider rejection",
                    outcome = DeliveryOutcome.NOT_SENT
                )
            } else {
                sentPayloads.add(payload)
                MessageSendResult(
                    success = true,
                    providerMessageId = "wa_msg_${sentPayloads.size}",
                    outcome = DeliveryOutcome.ACCEPTED
                )
            }
        }

        override suspend fun getDeliveryStatus(jobId: String): MessageStatus = MessageStatus.SENT
    }
}
