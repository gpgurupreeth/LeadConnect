package com.example.mobileapp

import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.domain.workflow.FollowUpDecision
import com.example.mobileapp.domain.workflow.FollowUpPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpPolicyTest {

    @Test
    fun `eligible only when agent requested consent recorded and not opted out`() {
        val decision = FollowUpPolicy.evaluate(
            outcome = CallOutcome.ANSWERED,
            agentRequested = true,
            consentGiven = true,
            optedOut = false,
            templateName = "follow_up"
        )
        assertTrue(decision is FollowUpDecision.Eligible)
    }

    @Test
    fun `completed call alone never grants follow-up eligibility`() {
        val decision = FollowUpPolicy.evaluate(
            outcome = CallOutcome.ANSWERED,
            agentRequested = false,
            consentGiven = true,
            optedOut = false,
            templateName = "follow_up"
        )
        assertTrue(decision is FollowUpDecision.NotRequested)
        assertFalse(
            FollowUpPolicy.canQueue(
                CallOutcome.ANSWERED,
                agentRequested = false,
                consentGiven = true,
                optedOut = false,
                templateName = "follow_up"
            )
        )
    }

    @Test
    fun `missing consent blocks follow-up`() {
        val decision = FollowUpPolicy.evaluate(
            outcome = CallOutcome.NO_ANSWER,
            agentRequested = true,
            consentGiven = false,
            optedOut = false,
            templateName = "follow_up"
        )
        assertTrue(decision is FollowUpDecision.Blocked)
        assertTrue((decision as FollowUpDecision.Blocked).reason.contains("consent", ignoreCase = true))
    }

    @Test
    fun `opt-out blocks follow-up even with consent`() {
        val decision = FollowUpPolicy.evaluate(
            outcome = CallOutcome.BUSY,
            agentRequested = true,
            consentGiven = true,
            optedOut = true,
            templateName = "follow_up"
        )
        assertTrue(decision is FollowUpDecision.Blocked)
        assertTrue((decision as FollowUpDecision.Blocked).reason.contains("opted out", ignoreCase = true))
    }

    @Test
    fun `unconfirmed unknown outcome blocks follow-up`() {
        val decision = FollowUpPolicy.evaluate(
            outcome = CallOutcome.UNKNOWN,
            agentRequested = true,
            consentGiven = true,
            optedOut = false,
            templateName = "follow_up"
        )
        assertTrue(decision is FollowUpDecision.Blocked)
        assertTrue((decision as FollowUpDecision.Blocked).reason.contains("Confirm", ignoreCase = true))
    }

    @Test
    fun `missing template blocks follow-up`() {
        val decision = FollowUpPolicy.evaluate(
            outcome = CallOutcome.REJECTED,
            agentRequested = true,
            consentGiven = true,
            optedOut = false,
            templateName = " "
        )
        assertTrue(decision is FollowUpDecision.Blocked)
    }
}
