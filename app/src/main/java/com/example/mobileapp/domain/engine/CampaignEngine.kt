package com.example.mobileapp.domain.engine

import android.content.Context
import com.example.mobileapp.data.local.AppDatabase
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CampaignEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.domain.model.CampaignStatus
import com.example.mobileapp.domain.workflow.CallStepResult
import com.example.mobileapp.domain.workflow.CallingHoursConfig
import com.example.mobileapp.domain.workflow.SequentialCallCoordinator
import com.example.mobileapp.telecom.AndroidTelecomCaller
import com.example.mobileapp.telecom.DemoSimCaller
import com.example.mobileapp.telecom.ICaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class EngineExecutionState {
    object Idle : EngineExecutionState()
    object Running : EngineExecutionState()
    data class Calling(val customer: CustomerEntity) : EngineExecutionState()
    data class AwaitingConfirmation(val attemptId: Long, val customer: CustomerEntity) : EngineExecutionState()
    data class Paused(val reason: String) : EngineExecutionState()
    object Completed : EngineExecutionState()
    object Stopped : EngineExecutionState()
}

data class CampaignEngineStats(
    val totalContacts: Int = 0,
    val callsAttempted: Int = 0,
    val callsAnswered: Int = 0,
    val callsUnresolved: Int = 0,
    val callsSuppressed: Int = 0,
    val messagesQueued: Int = 0
)

class CampaignEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    private val customerDao = database.customerDao()
    private val campaignDao = database.campaignDao()
    private val callAttemptDao = database.callAttemptDao()
    private val messageJobDao = database.messageJobDao()
    private val suppressionDao = database.suppressionDao()
    private val auditEventDao = database.auditEventDao()

    private val realCaller = AndroidTelecomCaller(context)
    val demoCaller = DemoSimCaller()

    // Configurable Settings
    var isDemoMode = false
    var callingHoursConfig = CallingHoursConfig()
    var defaultCountryCode = "+91"

    val backendProxyClient = com.example.mobileapp.messaging.BackendProxyClient()

    private val _engineState = MutableStateFlow<EngineExecutionState>(EngineExecutionState.Idle)
    val engineState: StateFlow<EngineExecutionState> = _engineState.asStateFlow()

    private val _currentCampaign = MutableStateFlow<CampaignEntity?>(null)
    val currentCampaign: StateFlow<CampaignEntity?> = _currentCampaign.asStateFlow()

    private val _engineStats = MutableStateFlow(CampaignEngineStats())
    val engineStats: StateFlow<CampaignEngineStats> = _engineStats.asStateFlow()

    private var executionJob: Job? = null
    private var coordinator: SequentialCallCoordinator? = null

    init {
        // Check for active or paused campaign to recover on startup
        scope.launch(Dispatchers.IO) {
            val active = campaignDao.getActiveCampaign()
            if (active != null) {
                _currentCampaign.value = active
                refreshStats(active.id)
                if (active.status == CampaignStatus.PAUSED.name) {
                    _engineState.value = EngineExecutionState.Paused("Recovered paused campaign from persistence")
                }
            }
        }
    }

    private fun getActiveCaller(): ICaller {
        return if (isDemoMode) demoCaller else realCaller
    }

    private fun getCoordinator(): SequentialCallCoordinator {
        return SequentialCallCoordinator(
            caller = getActiveCaller(),
            callAttemptDao = callAttemptDao,
            messageJobDao = messageJobDao,
            suppressionDao = suppressionDao,
            auditEventDao = auditEventDao
        )
    }

    fun startCampaign(campaignId: Long) {
        executionJob?.cancel()
        executionJob = scope.launch(Dispatchers.IO) {
            val campaign = campaignDao.getCampaignById(campaignId) ?: return@launch
            _currentCampaign.value = campaign

            // Update status to RUNNING in database
            val updated = campaign.copy(
                status = CampaignStatus.RUNNING.name,
                updatedAt = System.currentTimeMillis()
            )
            campaignDao.update(updated)
            _currentCampaign.value = updated

            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "CAMPAIGN_STARTED",
                    details = "Campaign '${campaign.name}' (id: $campaignId) started. DemoMode=$isDemoMode"
                )
            )

            runCampaignLoop(campaignId)
        }
    }

    fun pauseCampaign(reason: String = "Paused by user") {
        scope.launch(Dispatchers.IO) {
            val campaign = _currentCampaign.value ?: return@launch
            val updated = campaign.copy(
                status = CampaignStatus.PAUSED.name,
                updatedAt = System.currentTimeMillis()
            )
            campaignDao.update(updated)
            _currentCampaign.value = updated

            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "CAMPAIGN_PAUSED",
                    details = "Campaign ${campaign.id} paused: $reason"
                )
            )

            _engineState.value = EngineExecutionState.Paused(reason)
        }
    }

    fun resumeCampaign() {
        val campaign = _currentCampaign.value ?: return
        startCampaign(campaign.id)
    }

    fun stopCampaign() {
        executionJob?.cancel()
        scope.launch(Dispatchers.IO) {
            val campaign = _currentCampaign.value ?: return@launch
            val updated = campaign.copy(
                status = CampaignStatus.CANCELLED.name,
                updatedAt = System.currentTimeMillis()
            )
            campaignDao.update(updated)
            _currentCampaign.value = updated

            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "CAMPAIGN_STOPPED",
                    details = "Campaign ${campaign.id} stopped by user"
                )
            )

            _engineState.value = EngineExecutionState.Stopped
        }
    }

    private suspend fun runCampaignLoop(campaignId: Long) {
        _engineState.value = EngineExecutionState.Running
        coordinator = getCoordinator()

        while (true) {
            // Guard 1: verify campaign is still RUNNING
            val campaign = campaignDao.getCampaignById(campaignId)
            if (campaign == null || campaign.status != CampaignStatus.RUNNING.name) {
                break
            }

            // Guard 2: check permitted calling hours
            if (!callingHoursConfig.isWithinAllowedHours()) {
                val reason = "Outside permitted calling hours (${callingHoursConfig.formattedRange()})"
                pauseCampaign(reason)
                break
            }

            // Guard 3: Fetch all eligible contacts and find the next one without a recorded attempt
            val eligible = customerDao.getEligibleCustomers()
            var nextCustomer: CustomerEntity? = null
            for (cust in eligible) {
                val alreadyAttempted = callAttemptDao.hasAttemptForCustomer(campaignId, cust.id)
                if (!alreadyAttempted) {
                    nextCustomer = cust
                    break
                }
            }

            if (nextCustomer == null) {
                // All contacts have been attempted! Mark campaign COMPLETED
                val completed = campaign.copy(
                    status = CampaignStatus.COMPLETED.name,
                    completedContacts = campaign.totalContacts,
                    updatedAt = System.currentTimeMillis()
                )
                campaignDao.update(completed)
                _currentCampaign.value = completed
                auditEventDao.insert(
                    AuditEventEntity(
                        eventType = "CAMPAIGN_COMPLETED",
                        details = "All eligible contacts processed for campaign $campaignId"
                    )
                )
                _engineState.value = EngineExecutionState.Completed
                break
            }

            // Check pre-dial suppression
            if (suppressionDao.isSuppressed(nextCustomer.phoneNumber)) {
                _engineStats.value = _engineStats.value.copy(
                    callsSuppressed = _engineStats.value.callsSuppressed + 1
                )
                auditEventDao.insert(
                    AuditEventEntity(
                        eventType = "CALL_SUPPRESSED",
                        details = "Skipping suppressed contact ${nextCustomer.phoneNumber}"
                    )
                )
                continue
            }

            // Place call
            _engineState.value = EngineExecutionState.Calling(nextCustomer)

            val stepResult = coordinator!!.processCustomerCall(
                campaignId = campaignId,
                customer = nextCustomer,
                templateName = campaign.messageTemplate
            )

            when (stepResult) {
                is CallStepResult.AwaitingConfirmation -> {
                    // Update attempted count
                    _engineStats.value = _engineStats.value.copy(
                        callsAttempted = _engineStats.value.callsAttempted + 1
                    )
                    // Halt loop and wait for user confirmation
                    _engineState.value = EngineExecutionState.AwaitingConfirmation(
                        attemptId = stepResult.attemptId,
                        customer = nextCustomer
                    )
                    // The loop terminates here; when agent confirms, resolveOutcomeAndProceed() re-triggers runCampaignLoop
                    return
                }
                is CallStepResult.Suppressed -> {
                    _engineStats.value = _engineStats.value.copy(
                        callsSuppressed = _engineStats.value.callsSuppressed + 1
                    )
                }
                is CallStepResult.SkippedAlreadyAttempted -> {
                    // Invariant check: skip and continue loop
                }
                is CallStepResult.Failed -> {
                    // Failed to dial (e.g. permission or telephony error)
                    pauseCampaign("Telephony error: ${stepResult.error}")
                    return
                }
                is CallStepResult.Completed -> {
                    // If immediate outcome was resolved
                }
            }
        }
    }

    /**
     * Called when the agent completes the manual outcome confirmation dialog.
     * Persists outcome, optionally queues WhatsApp follow-up, updates counters,
     * and automatically resumes the campaign loop for the next contact!
     */
    fun resolveOutcomeAndProceed(
        attemptId: Long,
        customer: CustomerEntity,
        outcome: CallOutcome,
        sendFollowUp: Boolean,
        notes: String? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val campaign = _currentCampaign.value ?: return@launch
            coordinator = getCoordinator()

            val stepResult = coordinator!!.confirmOutcomeAndQueueFollowUp(
                attemptId = attemptId,
                campaignId = campaign.id,
                customerId = customer.id,
                phoneNumber = customer.phoneNumber,
                confirmedOutcome = outcome,
                followUpEligible = sendFollowUp,
                consentGiven = customer.consentGiven,
                templateName = if (sendFollowUp) campaign.messageTemplate else null
            )
            val messageQueued = (stepResult as? CallStepResult.Completed)?.messageQueued == true

            // Update stats. A message is counted only when the explicit policy
            // actually queued it, not merely because a call ended.
            val isAnswered = outcome == CallOutcome.ANSWERED
            _engineStats.value = _engineStats.value.copy(
                callsAnswered = _engineStats.value.callsAnswered + (if (isAnswered) 1 else 0),
                callsUnresolved = _engineStats.value.callsUnresolved + (if (!isAnswered) 1 else 0),
                messagesQueued = _engineStats.value.messagesQueued + (if (messageQueued) 1 else 0)
            )

            // Update completed count in campaign entity
            val currentCompleted = campaign.completedContacts + 1
            val updated = campaign.copy(
                completedContacts = currentCompleted,
                updatedAt = System.currentTimeMillis()
            )
            campaignDao.update(updated)
            _currentCampaign.value = updated

            // Brief pacing pause (1.5 seconds) for safety before auto-dialing next contact
            _engineState.value = EngineExecutionState.Running
            delay(1500)

            // Continue campaign loop if still running
            val freshCampaign = campaignDao.getCampaignById(campaign.id)
            if (freshCampaign != null && freshCampaign.status == CampaignStatus.RUNNING.name) {
                runCampaignLoop(campaign.id)
            }
        }
    }

    /**
     * Asynchronously dispatches all pending WhatsApp messages using the isolated provider.
     */
    fun dispatchPendingMessages(onComplete: (sent: Int, failed: Int, unknown: Int) -> Unit) {
        scope.launch(Dispatchers.IO) {
            coordinator = getCoordinator()
            // Only PENDING jobs are eligible. SENDING, UNKNOWN, FAILED, and SENT
            // jobs are deliberately excluded from automatic dispatch.
            val pendingJobs = messageJobDao.getPendingJobsList("PENDING")
            var sent = 0
            var failed = 0
            var unknown = 0

            for (job in pendingJobs) {
                val result = coordinator!!.dispatchPendingMessageJob(job.jobId, backendProxyClient)
                when (result) {
                    is com.example.mobileapp.domain.workflow.MessageDispatchResult.Sent -> sent++
                    is com.example.mobileapp.domain.workflow.MessageDispatchResult.Unknown -> unknown++
                    else -> failed++
                }
            }

            withContext(Dispatchers.Main) {
                onComplete(sent, failed, unknown)
            }
        }
    }

    suspend fun verifyBackendProxy(): com.example.mobileapp.messaging.ProxyContractStatus {
        return backendProxyClient.verifyContract()
    }

    private suspend fun refreshStats(campaignId: Long) {
        val attempts = callAttemptDao.getLatestAttemptForCustomer(campaignId, 0)
        // Set initial stats based on campaign entity
        val campaign = campaignDao.getCampaignById(campaignId)
        if (campaign != null) {
            _engineStats.value = CampaignEngineStats(
                totalContacts = campaign.totalContacts,
                callsAttempted = campaign.completedContacts
            )
        }
    }
}
