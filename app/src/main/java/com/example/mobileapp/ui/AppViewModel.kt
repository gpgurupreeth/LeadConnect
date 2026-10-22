package com.example.mobileapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileapp.data.local.AppDatabase
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CampaignEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.data.local.entities.SuppressionEntity
import com.example.mobileapp.domain.engine.CampaignEngine
import com.example.mobileapp.domain.model.CallOutcome
import com.example.mobileapp.domain.model.CampaignStatus
import com.example.mobileapp.domain.workflow.CallingHoursConfig
import com.example.mobileapp.messaging.ProxyContractStatus
import com.example.mobileapp.security.SecureStorage
import com.example.mobileapp.utils.ParsedContact
import com.example.mobileapp.utils.IdempotencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val customerDao = db.customerDao()
    val campaignDao = db.campaignDao()
    val callAttemptDao = db.callAttemptDao()
    val messageJobDao = db.messageJobDao()
    val suppressionDao = db.suppressionDao()
    val auditEventDao = db.auditEventDao()

    val secureStorage = SecureStorage(application)
    val engine = CampaignEngine(application, db, viewModelScope)

    val customersFlow: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val campaignsFlow: Flow<List<CampaignEntity>> = campaignDao.getAllCampaigns()
    val messageJobsFlow = messageJobDao.getAllJobsFlow()
    val auditEventsFlow = auditEventDao.getRecentEvents(50)

    private val _isDemoMode = MutableStateFlow(true)
    val isDemoMode = _isDemoMode.asStateFlow()

    private val _defaultCountryCode = MutableStateFlow("+91")
    val defaultCountryCode = _defaultCountryCode.asStateFlow()

    private val _proxyUrl = MutableStateFlow(secureStorage.getBackendUrl())
    val proxyUrl = _proxyUrl.asStateFlow()

    private val _proxyToken = MutableStateFlow(secureStorage.getBackendToken() ?: "")
    val proxyToken = _proxyToken.asStateFlow()

    private val _proxyContractStatus = MutableStateFlow<ProxyContractStatus?>(null)
    val proxyContractStatus = _proxyContractStatus.asStateFlow()

    init {
        engine.isDemoMode = _isDemoMode.value
        engine.defaultCountryCode = _defaultCountryCode.value
        engine.backendProxyClient.baseUrl = _proxyUrl.value
        engine.backendProxyClient.sessionToken = _proxyToken.value.takeIf { it.isNotBlank() }
    }

    fun setDemoMode(enabled: Boolean) {
        _isDemoMode.value = enabled
        engine.isDemoMode = enabled
    }

    fun setDefaultCountryCode(code: String) {
        _defaultCountryCode.value = code
        engine.defaultCountryCode = code
    }

    fun updateCallingHours(startHour: Int, endHour: Int, enforce: Boolean) {
        engine.callingHoursConfig = CallingHoursConfig(
            startHour = startHour,
            endHour = endHour,
            enforceCallingHours = enforce
        )
    }

    fun saveBackendProxyConfig(url: String, token: String) {
        secureStorage.saveBackendUrl(url)
        secureStorage.saveBackendToken(token)
        _proxyUrl.value = url
        _proxyToken.value = token
        engine.backendProxyClient.baseUrl = url
        engine.backendProxyClient.sessionToken = token.takeIf { it.isNotBlank() }
        _proxyContractStatus.value = null // reset verification on config change
    }

    fun verifyBackendProxyContract(onResult: (ProxyContractStatus) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = engine.verifyBackendProxy()
            _proxyContractStatus.value = status
            withContext(Dispatchers.Main) {
                onResult(status)
            }
        }
    }

    fun retryMessageJob(jobId: String, confirmUnknown: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val job = messageJobDao.getJobByJobId(jobId) ?: return@launch

            if (job.status == "SENDING") {
                auditEventDao.insert(
                    AuditEventEntity(
                        eventType = "MESSAGE_JOB_RETRY_BLOCKED",
                        details = "Job $jobId is SENDING and cannot be retried automatically."
                    )
                )
                return@launch
            }

            if (job.status == "UNKNOWN" && !confirmUnknown) {
                auditEventDao.insert(
                    AuditEventEntity(
                        eventType = "MESSAGE_JOB_RETRY_BLOCKED",
                        details = "Unknown delivery for job $jobId requires explicit manual verification."
                    )
                )
                return@launch
            }

            // A definitive not-sent failure may reuse the same idempotency key.
            // An unknown delivery needs a new key after the agent verifies the
            // WhatsApp account, because the server may have locked the old key.
            val newJobId = if (job.status == "UNKNOWN") {
                IdempotencyUtils.generateManualRetryJobId(job.jobId)
            } else {
                job.jobId
            }

            val updated = job.copy(
                jobId = newJobId,
                status = "PENDING",
                retryCount = 0,
                lastError = null,
                nextRetryTime = null,
                providerMessageId = null,
                updatedAt = System.currentTimeMillis()
            )
            messageJobDao.update(updated)
            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "MESSAGE_JOB_RETRY_REQUESTED",
                    details = if (newJobId == jobId) {
                        "Agent manually queued job $jobId for retry with the same idempotency key"
                    } else {
                        "Agent verified unknown delivery for $jobId and created new key $newJobId"
                    }
                )
            )
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            customerDao.updateSuppressionStatus(customer.phoneNumber, false)
            suppressionDao.delete(customer.phoneNumber)
            // Remove from customer table
            val all = customerDao.getEligibleCustomers() // or direct delete
            customerDao.deleteAll()
            // re-insert remaining
            customerDao.insertAll(all.filter { it.id != customer.id })
            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "CUSTOMER_DELETED",
                    details = "Customer ${customer.name} (${customer.phoneNumber}) deleted"
                )
            )
        }
    }

    fun importContacts(
        parsedContacts: List<ParsedContact>,
        onComplete: (savedCount: Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val suppressedInDb = suppressionDao.getAllSuppressedNumbers().toSet()
            val existingSuppressedCust = customerDao.getSuppressedCustomerNumbers().toSet()
            val allSuppressed = suppressedInDb + existingSuppressedCust

            val entitiesToSave = parsedContacts
                .filter { it.isValid && !it.isDuplicate }
                .map { parsed ->
                    val shouldSuppress = parsed.isSuppressed || allSuppressed.contains(parsed.phoneNumber)
                    CustomerEntity(
                        name = parsed.name,
                        phoneNumber = parsed.phoneNumber,
                        company = parsed.company,
                        tags = parsed.notes,
                        isSuppressed = shouldSuppress
                    )
                }

            customerDao.insertAll(entitiesToSave)

            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "CONTACTS_IMPORTED",
                    details = "Imported ${entitiesToSave.size} contacts. (${entitiesToSave.count { it.isSuppressed }} suppressed)"
                )
            )

            withContext(Dispatchers.Main) {
                onComplete(entitiesToSave.size)
            }
        }
    }

    fun toggleSuppression(customer: CustomerEntity, isSuppressed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            customerDao.updateSuppressionStatus(customer.phoneNumber, isSuppressed)
            if (isSuppressed) {
                suppressionDao.insert(
                    SuppressionEntity(
                        phoneNumber = customer.phoneNumber,
                        reason = "User toggled DND in contacts list"
                    )
                )
                auditEventDao.insert(
                    AuditEventEntity(
                        eventType = "SUPPRESSION_ADDED",
                        details = "Added ${customer.phoneNumber} to DND list"
                    )
                )
            } else {
                suppressionDao.delete(customer.phoneNumber)
                auditEventDao.insert(
                    AuditEventEntity(
                        eventType = "SUPPRESSION_REMOVED",
                        details = "Removed ${customer.phoneNumber} from DND list"
                    )
                )
            }
        }
    }

    fun toggleConsent(customer: CustomerEntity, consentGiven: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            customerDao.updateConsentStatus(customer.id, consentGiven)
            auditEventDao.insert(
                AuditEventEntity(
                    eventType = if (consentGiven) "WHATSAPP_CONSENT_RECORDED" else "WHATSAPP_CONSENT_WITHDRAWN",
                    details = "WhatsApp consent for customer ${customer.id} set to $consentGiven"
                )
            )

            if (!consentGiven) {
                // Consent withdrawal must stop anything still waiting to send.
                val pending = messageJobDao.getPendingJobsList("PENDING")
                    .filter { it.customerId == customer.id }
                pending.forEach { job ->
                    messageJobDao.update(
                        job.copy(
                            status = "FAILED",
                            lastError = "WhatsApp consent was withdrawn before dispatch",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun createCampaign(
        name: String,
        templateName: String?,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val eligibleCount = customerDao.getEligibleCustomers().size
            val campaign = CampaignEntity(
                name = name,
                status = CampaignStatus.DRAFT.name,
                totalContacts = eligibleCount,
                completedContacts = 0,
                messageTemplate = templateName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val id = campaignDao.insert(campaign)
            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "CAMPAIGN_CREATED",
                    details = "Campaign '$name' created with $eligibleCount eligible contacts"
                )
            )
            withContext(Dispatchers.Main) {
                onCreated(id)
            }
        }
    }

    fun seedDemoContacts(onComplete: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val sampleContacts = listOf(
                CustomerEntity(name = "Rahul Sharma", phoneNumber = "+919876543210", company = "Apex Retail", tags = "Sample, Retail"),
                CustomerEntity(name = "Priya Patel", phoneNumber = "+919876543211", company = "Zenith Logistics", tags = "Sample, Logistics"),
                CustomerEntity(name = "Anand Verma", phoneNumber = "+919876543212", company = "Quantum Tech", tags = "Sample, Tech"),
                CustomerEntity(name = "Sneha Kulkarni", phoneNumber = "+919876543213", company = "Horizon Media", tags = "Sample, Media", isSuppressed = true),
                CustomerEntity(name = "Vikram Rao", phoneNumber = "+919876543214", company = "Apex Solar", tags = "Sample, Solar")
            )
            customerDao.insertAll(sampleContacts)
            suppressionDao.insert(
                SuppressionEntity(phoneNumber = "+919876543213", reason = "DND opt-out sample")
            )
            auditEventDao.insert(
                AuditEventEntity(
                    eventType = "SAMPLE_CONTACTS_SEEDED",
                    details = "Seeded 5 sample contacts for safe evaluation"
                )
            )
            withContext(Dispatchers.Main) {
                onComplete(sampleContacts.size)
            }
        }
    }

    fun clearAllContacts(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            customerDao.deleteAll()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun resolveOutcome(
        attemptId: Long,
        customer: CustomerEntity,
        outcome: CallOutcome,
        sendWhatsApp: Boolean,
        notes: String? = null
    ) {
        engine.resolveOutcomeAndProceed(
            attemptId = attemptId,
            customer = customer,
            outcome = outcome,
            sendFollowUp = sendWhatsApp,
            notes = notes
        )
    }

    fun dispatchMessageQueue(onResult: (sent: Int, failed: Int, unknown: Int) -> Unit) {
        engine.dispatchPendingMessages(onResult)
    }
}
