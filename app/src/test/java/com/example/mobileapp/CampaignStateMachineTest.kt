package com.example.mobileapp

import com.example.mobileapp.domain.model.CampaignStatus
import com.example.mobileapp.domain.model.CampaignStateMachine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignStateMachineTest {

    @Test
    fun `draft campaign can transition to running or cancelled`() {
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.DRAFT, CampaignStatus.RUNNING))
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.DRAFT, CampaignStatus.CANCELLED))
        assertFalse(CampaignStateMachine.canTransition(CampaignStatus.DRAFT, CampaignStatus.COMPLETED))
    }

    @Test
    fun `running campaign can pause, complete, or cancel`() {
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.RUNNING, CampaignStatus.PAUSED))
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.RUNNING, CampaignStatus.COMPLETED))
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.RUNNING, CampaignStatus.CANCELLED))
        assertFalse(CampaignStateMachine.canTransition(CampaignStatus.RUNNING, CampaignStatus.DRAFT))
    }

    @Test
    fun `paused campaign can resume or cancel`() {
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.PAUSED, CampaignStatus.RUNNING))
        assertTrue(CampaignStateMachine.canTransition(CampaignStatus.PAUSED, CampaignStatus.CANCELLED))
        assertFalse(CampaignStateMachine.canTransition(CampaignStatus.PAUSED, CampaignStatus.COMPLETED))
    }

    @Test
    fun `completed campaign is terminal`() {
        assertFalse(CampaignStateMachine.canTransition(CampaignStatus.COMPLETED, CampaignStatus.RUNNING))
        assertFalse(CampaignStateMachine.canTransition(CampaignStatus.COMPLETED, CampaignStatus.PAUSED))
        assertFalse(CampaignStateMachine.canTransition(CampaignStatus.COMPLETED, CampaignStatus.DRAFT))
    }

    @Test
    fun `cancelled campaign is terminal`() {
        assertFalse(CampaignStateMachine.canTransition(CampaignStatus.CANCELLED, CampaignStatus.RUNNING))
        assertFalse(CampaignStateMachine.canTransition(CampaignStatus.CANCELLED, CampaignStatus.DRAFT))
    }
}
