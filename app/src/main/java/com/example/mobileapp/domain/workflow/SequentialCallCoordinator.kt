package com.example.mobileapp.domain.workflow

import com.example.mobileapp.data.local.dao.AuditEventDao
import com.example.mobileapp.data.local.dao.CallAttemptDao
import com.example.mobileapp.data.local.dao.SuppressionDao
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CallAttemptEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.telecom.ICaller
import com.example.mobileapp.telecom.OutcomeClassifier

sealed class CallStepResult {
    data class Completed(val outcome: CallOutcome) : CallStepResult()
    data class AwaitingConfirmation(val attemptId: Long, val phoneNumber: String) : CallStepResult()
    data class Suppressed(val reason: String) : CallStepResult()
    data class SkippedAlreadyAttempted(val customerId: Long) : CallStepResult()
    data class Failed(val error: String) : CallStepResult()
}

/**
 * Coordinates sequential phone calling, recovery protection, and outcome confirmation.
 *
 * Invariants:
 * 1. Sequential calling: An active call must be completed and recorded before advancing.
 * 2. Crash / restart safety: Never redials a customer whose call attempt was already initiated in the campaign.
 * 3. Pre-dial suppression enforcement: Contacts on DND/suppression are strictly excluded.
 * 4. Outcome verification: Ambiguous states pause for agent confirmation to ensure accurate reporting.
 */
class SequentialCallCoordinator(
    private val caller: ICaller,
    private val callAttemptDao: CallAttemptDao,
    private val suppressionDao: SuppressionDao,
    private val auditEventDao: AuditEventDao
) {
    suspend fun processCustomerCall(
        campaignId: Long,
        customer: CustomerEntity
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

        // Persist attempt immediately
        val attemptId = callAttemptDao.insert(
            CallAttemptEntity(
                campaignId = campaignId,
                customerId = customer.id,
                phoneNumber = customer.phoneNumber,
                callStartTime = startTime,
                callEndTime = null,
                durationSeconds = 0,
                outcome = initialOutcome.name,
                notes = "Initial call placed, outcome pending confirmation"
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

    suspend fun confirmOutcome(
        attemptId: Long,
        confirmedOutcome: CallOutcome,
        notes: String? = null,
        durationSeconds: Int = 0
    ): CallStepResult {
        callAttemptDao.updateOutcome(
            attemptId = attemptId,
            outcome = confirmedOutcome.name,
            notes = notes ?: "Confirmed by agent",
            endTime = System.currentTimeMillis(),
            duration = durationSeconds
        )

        auditEventDao.insert(
            AuditEventEntity(
                eventType = "OUTCOME_CONFIRMED",
                details = "Attempt $attemptId confirmed as $confirmedOutcome"
            )
        )

        return CallStepResult.Completed(confirmedOutcome)
    }
}
