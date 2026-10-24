package com.example.mobileapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileapp.data.local.AppDatabase
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CallAttemptEntity
import com.example.mobileapp.data.local.entities.CampaignEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.data.local.entities.SuppressionEntity
import com.example.mobileapp.telecom.AndroidTelecomCaller
import com.example.mobileapp.utils.ContactImportParser
import com.example.mobileapp.utils.ImportSummary
import com.example.mobileapp.utils.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val customerDao = db.customerDao()
    val campaignDao = db.campaignDao()
    val callAttemptDao = db.callAttemptDao()
    val suppressionDao = db.suppressionDao()
    val auditEventDao = db.auditEventDao()

    val realCaller = AndroidTelecomCaller(application)
    val isCallActive = realCaller.isCallActive
    val callFinishedEvent = realCaller.callFinishedEvent

    // Sections (Campaigns)
    val sectionsFlow: Flow<List<CampaignEntity>> = campaignDao.getAllCampaigns()

    private val _defaultCountryCode = MutableStateFlow("+91")
    val defaultCountryCode = _defaultCountryCode.asStateFlow()

    private val _activeCallingCustomer = MutableStateFlow<CustomerEntity?>(null)
    val activeCallingCustomer = _activeCallingCustomer.asStateFlow()

    private val _callStartTime = MutableStateFlow(0L)
    val callStartTime = _callStartTime.asStateFlow()

    val allHistoryFlow: Flow<List<CallAttemptEntity>> = callAttemptDao.getAllHistory()

    fun getCustomersForSection(sectionId: Long): Flow<List<CustomerEntity>> {
        return customerDao.getCustomersForSection(sectionId)
    }

    fun getAttemptsForSection(sectionId: Long): Flow<List<CallAttemptEntity>> {
        return callAttemptDao.getAttemptsForSection(sectionId)
    }

    /**
     * Places a direct cellular phone call using the device's physical SIM card.
     */
    fun dialCustomer(customer: CustomerEntity): Boolean {
        _activeCallingCustomer.value = customer
        _callStartTime.value = System.currentTimeMillis()
        return realCaller.placeCall(customer.phoneNumber)
    }

    /**
     * Records call outcome (INTERESTED, CALLBACK, NO_ANSWER, NOT_INTERESTED) with duration and notes.
     */
    fun recordCallOutcome(
        customer: CustomerEntity,
        outcome: String,
        notes: String? = null,
        explicitDurationSeconds: Int? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val startTime = _callStartTime.value.takeIf { it > 0 } ?: now
            val durationSeconds = explicitDurationSeconds ?: ((now - startTime) / 1000).toInt().coerceAtLeast(0)
            val isCleared = (outcome != "NO_ANSWER")

            // 1. Update customer record in this section
            customerDao.updateCallOutcome(
                customerId = customer.id,
                status = outcome,
                duration = durationSeconds,
                notes = notes,
                timestamp = now,
                isCleared = isCleared
            )

            // 2. Persist in call history for this section
            val attempt = CallAttemptEntity(
                campaignId = customer.sectionId,
                customerId = customer.id,
                phoneNumber = customer.phoneNumber,
                contactName = customer.name,
                callStartTime = startTime,
                callEndTime = now,
                durationSeconds = durationSeconds,
                outcome = outcome,
                notes = notes,
                timestamp = now
            )
            callAttemptDao.insert(attempt)

            // 3. Update section completed count
            val sectionLeads = customerDao.getCustomersForSectionList(customer.sectionId)
            val completed = sectionLeads.count { it.isCleared }
            campaignDao.updateCounts(customer.sectionId, sectionLeads.size, completed)

            // 4. If Not Interested, mark DND
            if (outcome == "NOT_INTERESTED") {
                suppressionDao.insert(
                    SuppressionEntity(
                        phoneNumber = customer.phoneNumber,
                        reason = "Customer marked Not Interested / DND by caller"
                    )
                )
                customerDao.updateSuppressionStatus(customer.phoneNumber, true)
            }

            _activeCallingCustomer.value = null
            _callStartTime.value = 0L
        }
    }

    fun cancelActiveCall() {
        _activeCallingCustomer.value = null
        _callStartTime.value = 0L
    }

    /**
     * Creates a new named section (e.g. "Real Estate", "B2B Tech") and imports verified leads into it.
     */
    fun createSectionAndImportCsv(
        sectionName: String,
        genre: String? = null,
        csvContent: String,
        onComplete: (sectionId: Long, summary: ImportSummary) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val suppressed = suppressionDao.getAllSuppressedNumbers().toSet()
            val summary = ContactImportParser.parseCsv(
                content = csvContent,
                defaultCountryCode = _defaultCountryCode.value,
                suppressedNumbers = suppressed
            )

            val section = CampaignEntity(
                name = sectionName.ifBlank { "Leads Batch ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())}" },
                genre = genre?.takeIf { it.isNotBlank() },
                status = "ACTIVE",
                totalContacts = summary.validCount,
                completedContacts = 0,
                createdAt = System.currentTimeMillis()
            )
            val sectionId = campaignDao.insert(section)

            val entities = summary.parsedContacts
                .filter { it.isValid && !it.isDuplicate }
                .map { parsed ->
                    val verified = PhoneNumberUtils.verify(parsed.phoneNumber, _defaultCountryCode.value)
                    CustomerEntity(
                        sectionId = sectionId,
                        name = parsed.name,
                        phoneNumber = verified.normalizedNumber,
                        company = parsed.company,
                        tags = parsed.notes,
                        isSuppressed = parsed.isSuppressed,
                        callStatus = "NOT_CALLED",
                        isCleared = false,
                        isVerified = verified.isValid
                    )
                }

            if (entities.isNotEmpty()) {
                customerDao.insertAll(entities)
            }

            campaignDao.updateCounts(sectionId, entities.size, 0)

            withContext(Dispatchers.Main) {
                onComplete(sectionId, summary)
            }
        }
    }

    /**
     * Deletes a single section and all leads belonging to it.
     */
    fun deleteSection(sectionId: Long, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            customerDao.deleteCustomersBySection(sectionId)
            callAttemptDao.deleteAttemptsBySection(sectionId)
            campaignDao.deleteCampaign(sectionId)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Deletes ALL sections and ALL leads in a single 1-tap delete option!
     */
    fun deleteAllSectionsAndLeads(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            customerDao.deleteAll()
            callAttemptDao.clearAllHistory()
            campaignDao.deleteAll()
            withContext(Dispatchers.Main) {
                onComplete()
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
                        reason = "User toggled DND manually"
                    )
                )
            } else {
                suppressionDao.delete(customer.phoneNumber)
            }
        }
    }

    /**
     * Adds an individual lead into a specific section with phone number verification.
     */
    fun addIndividualLead(
        sectionId: Long,
        name: String,
        phoneNumber: String,
        company: String?,
        notes: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmedName = name.trim()
            val trimmedPhone = phoneNumber.trim()

            if (trimmedName.isBlank()) {
                withContext(Dispatchers.Main) { onResult(false, "Lead name cannot be blank") }
                return@launch
            }

            val verification = PhoneNumberUtils.verify(trimmedPhone, _defaultCountryCode.value)
            if (!verification.isValid) {
                withContext(Dispatchers.Main) {
                    onResult(false, verification.issueReason ?: "Invalid phone number format")
                }
                return@launch
            }

            val normalized = verification.normalizedNumber
            val existing = customerDao.getCustomersForSectionList(sectionId)
            if (existing.any { it.phoneNumber == normalized }) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Lead with number $normalized already exists in this section")
                }
                return@launch
            }

            val isSuppressed = suppressionDao.isSuppressed(normalized)
            val newCustomer = CustomerEntity(
                sectionId = sectionId,
                name = trimmedName,
                phoneNumber = normalized,
                company = company?.trim()?.takeIf { it.isNotBlank() },
                lastCallNotes = notes?.trim()?.takeIf { it.isNotBlank() },
                callStatus = "NOT_CALLED",
                isCleared = false,
                isVerified = true,
                isSuppressed = isSuppressed
            )

            customerDao.insert(newCustomer)
            val updatedList = customerDao.getCustomersForSectionList(sectionId)
            val completedCount = updatedList.count { it.isCleared }
            campaignDao.updateCounts(sectionId, updatedList.size, completedCount)

            withContext(Dispatchers.Main) {
                onResult(true, "Lead '$trimmedName' added successfully!")
            }
        }
    }

    /**
     * Deletes an individual lead from a section.
     */
    fun deleteIndividualLead(lead: CustomerEntity, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            customerDao.deleteCustomerById(lead.id)
            val updatedList = customerDao.getCustomersForSectionList(lead.sectionId)
            val completedCount = updatedList.count { it.isCleared }
            campaignDao.updateCounts(lead.sectionId, updatedList.size, completedCount)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Deletes multiple selected leads from a section.
     */
    fun deleteMultipleLeads(leadIds: List<Long>, sectionId: Long, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            customerDao.deleteCustomersByIds(leadIds)
            val updatedList = customerDao.getCustomersForSectionList(sectionId)
            val completedCount = updatedList.count { it.isCleared }
            campaignDao.updateCounts(sectionId, updatedList.size, completedCount)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Deletes multiple selected sections and their leads.
     */
    fun deleteMultipleSections(sectionIds: List<Long>, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            sectionIds.forEach { sId ->
                customerDao.deleteCustomersBySection(sId)
                callAttemptDao.deleteAttemptsBySection(sId)
            }
            campaignDao.deleteCampaignsByIds(sectionIds)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Creates a proper formatted CSV/Excel spreadsheet file and shares it via Intent with FileProvider.
     */
    fun shareSectionCsvAsFile(context: Context, section: CampaignEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val leads = customerDao.getCustomersForSectionList(section.id)

                val sb = StringBuilder()
                sb.append("Lead Name,Phone Number,Call Status,Duration,Notes\n")

                for (c in leads) {
                    val durationFmt = formatDuration(c.lastCallDurationSeconds)
                    val leadName = c.name.replace("\"", "\"\"")
                    val leadPhone = c.phoneNumber.replace("\"", "\"\"")
                    val leadStatus = c.callStatus.replace("\"", "\"\"")
                    val leadNotes = (c.lastCallNotes ?: "").replace("\"", "\"\"")

                    sb.append("\"$leadName\",\"$leadPhone\",\"$leadStatus\",\"$durationFmt\",\"$leadNotes\"\n")
                }

                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val cleanFileName = section.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val file = File(exportDir, "${cleanFileName}_Leads.csv")

                file.writeText("\uFEFF" + sb.toString(), Charsets.UTF_8)

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/comma-separated-values"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "${section.name} - Leads Excel Export")
                    putExtra(Intent.EXTRA_TEXT, "Exported ${leads.size} leads from ${section.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(sendIntent, "Share Excel / CSV Sheet").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Generates CSV report for a specific section.
     */
    suspend fun exportSectionCsv(sectionId: Long): String = withContext(Dispatchers.IO) {
        val leads = customerDao.getCustomersForSectionList(sectionId)

        val sb = StringBuilder()
        sb.append("Lead Name,Phone Number,Call Status,Duration,Notes\n")

        for (c in leads) {
            val durationFmt = formatDuration(c.lastCallDurationSeconds)
            val cleanNotes = (c.lastCallNotes ?: "").replace("\"", "\"\"")
            val cleanName = c.name.replace("\"", "\"\"")
            val cleanPhone = c.phoneNumber.replace("\"", "\"\"")
            val cleanStatus = c.callStatus.replace("\"", "\"\"")
            sb.append("\"$cleanName\",\"$cleanPhone\",\"$cleanStatus\",\"$durationFmt\",\"$cleanNotes\"\n")
        }
        sb.toString()
    }

    companion object {
        fun formatDuration(seconds: Int): String {
            if (seconds <= 0) return "0s"
            val m = seconds / 60
            val s = seconds % 60
            return if (m > 0) "${m}m ${s}s" else "${s}s"
        }
    }
}
