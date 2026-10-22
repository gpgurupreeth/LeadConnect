package com.example.mobileapp.domain.workflow

import com.example.mobileapp.domain.model.CallOutcome

/**
 * Explicit, testable rules for WhatsApp follow-up eligibility.
 *
 * The call workflow never queues a message merely because a call ended. An
 * agent must request the follow-up, the outcome must be confirmed, the
 * recipient must have recorded consent, and the recipient must not be
 * suppressed or opted out.
 */
sealed class FollowUpDecision {
    data class Eligible(val reason: String = "Eligible for an explicitly requested follow-up") : FollowUpDecision()
    data class NotRequested(val reason: String) : FollowUpDecision()
    data class Blocked(val reason: String) : FollowUpDecision()
}

object FollowUpPolicy {
    private val eligibleOutcomes = setOf(
        CallOutcome.ANSWERED,
        CallOutcome.NO_ANSWER,
        CallOutcome.BUSY,
        CallOutcome.REJECTED
    )

    fun evaluate(
        outcome: CallOutcome,
        agentRequested: Boolean,
        consentGiven: Boolean,
        optedOut: Boolean,
        templateName: String?
    ): FollowUpDecision {
        if (!agentRequested) {
            return FollowUpDecision.NotRequested("The agent did not request a WhatsApp follow-up")
        }
        if (outcome == CallOutcome.UNKNOWN) {
            return FollowUpDecision.Blocked("Confirm the call outcome before queueing a message")
        }
        if (optedOut) {
            return FollowUpDecision.Blocked("Customer is suppressed or has opted out of WhatsApp")
        }
        if (!consentGiven) {
            return FollowUpDecision.Blocked("WhatsApp consent is not recorded for this customer")
        }
        if (templateName.isNullOrBlank()) {
            return FollowUpDecision.Blocked("No follow-up template is configured")
        }
        if (outcome !in eligibleOutcomes) {
            return FollowUpDecision.Blocked("This call outcome is not eligible for a follow-up")
        }
        return FollowUpDecision.Eligible()
    }

    fun canQueue(
        outcome: CallOutcome,
        agentRequested: Boolean,
        consentGiven: Boolean,
        optedOut: Boolean,
        templateName: String?
    ): Boolean = evaluate(outcome, agentRequested, consentGiven, optedOut, templateName) is FollowUpDecision.Eligible
}
