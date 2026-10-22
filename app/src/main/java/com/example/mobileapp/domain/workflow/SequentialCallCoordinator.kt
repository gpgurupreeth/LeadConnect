package com.example.mobileapp.domain.workflow

import com.example.mobileapp.data.local.dao.AuditEventDao
import com.example.mobileapp.data.local.dao.CallAttemptDao
import com.example.mobileapp.data.local.dao.MessageJobDao
import com.example.mobileapp.data.local.dao.SuppressionDao
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CallAttemptEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.data.local.entities.MessageJobEntity
import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.domain.model.MessageStatus
import com.example.mobileapp.messaging.DeliveryOutcome
import com.example.mobileapp.messaging.IMessagingProvider
import com.example.mobileapp.messaging.MessagePayload
import com.example.mobileapp.telecom.ICaller
import com.example.mobileapp.telecom.OutcomeClassifier
import com.example.mobileapp.utils.IdempotencyUtils

sealed class CallStepResult {
    data class Completed(val outcome: CallOutcome, val messageQueued: Boolean) : CallStepResult()
    data class AwaitingConfirmation(val attemptId: Long, val phoneNumber: String) : CallStepResult()
    data class Suppressed(val reason: String) : CallStepResult()
    data class SkippedAlreadyAttempted(val customerId: Long) : CallStepResult()
    data class Failed(val error: String) : CallStepResult()
}

sealed class MessageDispatchResult {
    data class Sent(val jobId: String, val providerId: String?) : MessageDispatchResult()
    data class Suppressed(val jobId: String, val reason: String) : MessageDispatchResult()
    data class RetryScheduled(val jobId: String, val retryCount: Int, val error: String) : MessageDispatchResult()
    data class Failed(val jobId: String, val error: String) : MessageDispatchResult()
    /** The request may have reached the provider. Never auto-retry this result. */
    data class Unknown(val jobId: String, val error: String) : MessageDispatchResult()
}

/**
 * Coordinates the sequential call workflow, recovery protection, and isolated messaging.
 *
 * Invariants:
 * 1. Sequential calling: An active call must be completed and recorded before advancing.
 * 2. Crash / restart safety: Never redials a customer whose call attempt was already initiated in the campaign.
 * 3. Double suppression enforcement: Verified both prior to dialing AND prior to WhatsApp dispatch.
 * 4. Outcome safety: Ambiguous states default strictly to UNKNOWN and require explicit confirmation.
 * 5. Idempotent messaging: Follow-up jobs retain stable keys across restarts and retries.
 */
class SequentialCallCoordinator(
    private val caller: ICaller,
    private val callAttemptDao: CallAttemptDao,
    private val messageJobDao: MessageJobDao,
    private val suppressionDao: SuppressionDao,
    private val auditEventDao: AuditEventDao
) {
    suspend fun processCustomerCall(
        campaignId: Long,
        customer: CustomerEntity,
        templateName: String?
    ): CallStepResult {
        // 1. Recovery invariant: If customer was already called/initiated in this campaign, skip to prevent redialing
        if (callAttemptDao.hasAttemptForCustomer(campaignId, customer.id)) {
            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "CALL_SKIPPED_ALREADY_ATTEMPTED",
                    details = "Customer ${customer.id} already has a recorded attempt in campaign $campaignId"
                )
            )
            return CallStepResult.SkippedAlreadyAttempted(customer.id)
        }

        // 2. Pre-dial suppression check
        if (suppressionDao.isSuppressed(customer.phoneNumber)) {
            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "CALL_SUPPRESSED",
                    details = "Customer ${customer.phoneNumber} is in suppression list."
                )
            )
            return CallStepResult.Suppressed("Customer is in suppression list")
        }

        // 3. Place call
        val startTime = System.currentTimeMillis()
        val callInitiated = caller.placeCall(customer.phoneNumber)
        if (!callInitiated) {
            return CallStepResult.Failed("Failed to place call via telephony")
        }

        // Default initial outcome is UNKNOWN until verified
        val initialOutcome = OutcomeClassifier.classify(
            callDurationSeconds = 0,
            wasOffhook = false,
            userConfirmedAnswered = null
        )

        // Persist attempt immediately before any follow-up
        val attemptId = callAttemptDao.insert(
            CallAttemptEntity(
                campaignId = campaignId,
                customerId = customer.id,
                phoneNumber = customer.phoneNumber,
                callStartTime = startTime,
                callEndTime = null,
                durationSeconds = 0,
                outcome = initialOutcome.name,
                notes = "Initial call placed, outcome pending confirmation",
                followUpEligible = false
            )
        )

        auditEventDao.insert(
            AuditEventEntity(
                eventType = "CALL_INITIATED",
                details = "Call attempt $attemptId initiated for customer ${customer.id}"
            )
        )

        // Ambiguous states pause for manual agent confirmation
        return CallStepResult.AwaitingConfirmation(
            attemptId = attemptId,
            phoneNumber = customer.phoneNumber
        )
    }

    suspend fun confirmOutcomeAndQueueFollowUp(
        attemptId: Long,
        campaignId: Long,
        customerId: Long,
        phoneNumber: String,
        confirmedOutcome: CallOutcome,
        followUpEligible: Boolean,
        consentGiven: Boolean,
        templateName: String?
    ): CallStepResult {
        var messageQueued = false

        val optedOut = suppressionDao.isSuppressed(phoneNumber)
        val decision = FollowUpPolicy.evaluate(
            outcome = confirmedOutcome,
            agentRequested = followUpEligible,
            consentGiven = consentGiven,
            optedOut = optedOut,
            templateName = templateName
        )
        val eligible = decision is FollowUpDecision.Eligible

        callAttemptDao.updateOutcome(
            attemptId = attemptId,
            outcome = confirmedOutcome.name,
            notes = "Confirmed by agent",
            followUpEligible = eligible,
            endTime = System.currentTimeMillis(),
            duration = 0
        )

        auditEventDao.insert(
            AuditEventEntity(
                eventType = "OUTCOME_CONFIRMED",
                details = "Attempt $attemptId confirmed as $confirmedOutcome"
            )
        )

        if (followUpEligible && !eligible) {
            val reason = (decision as? FollowUpDecision.Blocked)?.reason
                ?: (decision as? FollowUpDecision.NotRequested)?.reason
                ?: "Follow-up is not eligible"
            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "MESSAGE_JOB_BLOCKED",
                    details = "Follow-up for attempt $attemptId was not queued: $reason"
                )
            )
        }

        // Queue only when the explicit policy allows it. This is deliberately
        // separate from the SIM call workflow: a completed call never queues
        // a message by itself.
        if (eligible) {
            val jobId = IdempotencyUtils.generateJobId(campaignId, customerId, attemptId)
            val job = MessageJobEntity(
                jobId = jobId,
                campaignId = campaignId,
                customerId = customerId,
                phoneNumber = phoneNumber,
                templateName = templateName!!,
                parametersJson = "{}",
                status = MessageStatus.PENDING.name,
                retryCount = 0,
                consentVerified = true
            )
            if (messageJobDao.insert(job) != -1L) {
                messageQueued = true
                auditEventDao.insert(
                    AuditEventEntity(
                        eventType = "MESSAGE_JOB_QUEUED",
                        details = "Job $jobId queued for attempt $attemptId"
                    )
                )
            }
        }

        return CallStepResult.Completed(confirmedOutcome, messageQueued)
    }

    /**
     * Dispatches a pending message job asynchronously.
     * Enforces second-line suppression verification right before network send.
     */
    suspend fun dispatchPendingMessageJob(
        jobId: String,
        messagingProvider: IMessagingProvider
    ): MessageDispatchResult {
        val job = messageJobDao.getJobByJobId(jobId)
            ?: return MessageDispatchResult.Failed(jobId, "Job $jobId not found")

        if (job.status == MessageStatus.UNKNOWN.name) {
            return MessageDispatchResult.Unknown(
                jobId,
                "A previous attempt has an unknown delivery outcome. Verify in WhatsApp before retrying."
            )
        }
        if (job.status == MessageStatus.SENDING.name) {
            return MessageDispatchResult.Failed(
                jobId,
                "Job is already marked SENDING. Do not auto-retry an in-flight dispatch."
            )
        }

        // Guard: explicit consent provenance must exist on the job.
        if (!job.consentVerified) {
            val updated = job.copy(
                status = MessageStatus.FAILED.name,
                lastError = "WhatsApp consent was not recorded when this job was queued",
                updatedAt = System.currentTimeMillis()
            )
            messageJobDao.update(updated)
            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "MESSAGE_DISPATCH_BLOCKED_NO_CONSENT",
                    details = "Job $jobId blocked because consentVerified=false"
                )
            )
            return MessageDispatchResult.Failed(jobId, "WhatsApp consent is not recorded")
        }

        // Guard: check suppression right before dispatch.
        if (suppressionDao.isSuppressed(job.phoneNumber)) {
            val updated = job.copy(
                status = MessageStatus.FAILED.name,
                lastError = "Suppressed prior to dispatch",
                updatedAt = System.currentTimeMillis()
            )
            messageJobDao.update(updated)
            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "MESSAGE_DISPATCH_SUPPRESSED",
                    details = "Job $jobId cancelled because ${job.phoneNumber} opted out"
                )
            )
            return MessageDispatchResult.Suppressed(jobId, "Phone number is suppressed")
        }

        // Persist SENDING before the network call. If the process dies here,
        // the job is not silently picked up again as PENDING.
        messageJobDao.update(
            job.copy(
                status = MessageStatus.SENDING.name,
                lastError = null,
                updatedAt = System.currentTimeMillis()
            )
        )

        val payload = MessagePayload(
            jobId = job.jobId,
            phoneNumber = job.phoneNumber,
            templateName = job.templateName,
            parameters = emptyMap(),
            consent = true,
            optOut = false
        )
        val result = messagingProvider.sendMessage(payload)

        return when {
            result.success -> {
                val updated = job.copy(
                    status = MessageStatus.SENT.name,
                    providerMessageId = result.providerMessageId,
                    lastError = null,
                    updatedAt = System.currentTimeMillis()
                )
                messageJobDao.update(updated)
                auditEventDao.insert(
                    AuditEventEntity(
                        eventType = "MESSAGE_ACCEPTED",
                        details = "Job $jobId accepted by proxy; provider ID ${result.providerMessageId ?: "not returned"}"
                    )
                )
                MessageDispatchResult.Sent(jobId, result.providerMessageId)
            }

            result.outcome == DeliveryOutcome.UNKNOWN -> {
                val error = result.error ?: (
                    "Delivery outcome is unknown. Verify in WhatsApp before retrying."
                    )
                val updated = job.copy(
                    status = MessageStatus.UNKNOWN.name,
                    lastError = error,
                    nextRetryTime = null,
                    updatedAt = System.currentTimeMillis()
                )
                messageJobDao.update(updated)
                auditEventDao.insert(
                    AuditEventEntity(
                        eventType = "MESSAGE_DISPATCH_UNKNOWN",
                        details = "Job $jobId has an unknown delivery outcome; automatic retry disabled"
                    )
                )
                MessageDispatchResult.Unknown(jobId, error)
            }

            else -> {
                val newRetry = job.retryCount + 1
                val terminal = newRetry >= 3
                val updated = job.copy(
                    retryCount = newRetry,
                    status = if (terminal) MessageStatus.FAILED.name else MessageStatus.PENDING.name,
                    lastError = result.error,
                    updatedAt = System.currentTimeMillis()
                )
                messageJobDao.update(updated)
                auditEventDao.insert(
                    AuditEventEntity(
                        eventType = if (terminal) "MESSAGE_DISPATCH_FAILED" else "MESSAGE_DISPATCH_RETRY_SCHEDULED",
                        details = "Job $jobId failed attempt $newRetry: ${result.error}"
                    )
                )
                if (terminal) {
                    MessageDispatchResult.Failed(jobId, result.error ?: "Definitive dispatch failure")
                } else {
                    MessageDispatchResult.RetryScheduled(jobId, newRetry, result.error ?: "Unknown error")
                }
            }
        }
    }
}
