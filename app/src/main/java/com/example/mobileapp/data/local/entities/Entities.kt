package com.example.mobileapp.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["phoneNumber"], unique = true)]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val company: String? = null,
    val tags: String? = null,
    val isSuppressed: Boolean = false,
    // Explicit opt-in proof for WhatsApp follow-ups. Defaults to false so a
    // newly imported contact can never receive a message until an agent
    // records consent and a campaign is explicitly run.
    val consentGiven: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val status: String,
    val totalContacts: Int = 0,
    val completedContacts: Int = 0,
    val messageTemplate: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "call_attempts",
    indices = [Index(value = ["campaignId"]), Index(value = ["customerId"])]
)
data class CallAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val campaignId: Long,
    val customerId: Long,
    val phoneNumber: String,
    val callStartTime: Long,
    val callEndTime: Long? = null,
    val durationSeconds: Int = 0,
    val outcome: String,
    val notes: String? = null,
    val followUpEligible: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "message_jobs",
    indices = [Index(value = ["jobId"], unique = true), Index(value = ["status"])]
)
data class MessageJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jobId: String,
    val campaignId: Long,
    val customerId: Long,
    val phoneNumber: String,
    val templateName: String,
    val parametersJson: String,
    val status: String,
    val retryCount: Int = 0,
    val nextRetryTime: Long? = null,
    val lastError: String? = null,
    // Consent was verified when the job was queued and is re-checked at
    // dispatch. A job without this proof cannot be sent.
    val consentVerified: Boolean = false,
    // Nullable: WAHA may accept a send without returning a provider message ID.
    val providerMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_events")
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventType: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "suppressions")
data class SuppressionEntity(
    @PrimaryKey
    val phoneNumber: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)
