package com.example.mobileapp.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["sectionId", "phoneNumber"], unique = true)]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sectionId: Long = 0L, // Belongs to a specific Section / CSV List
    val name: String,
    val phoneNumber: String,
    val company: String? = null,
    val tags: String? = null,
    val isSuppressed: Boolean = false,
    val callStatus: String = "NOT_CALLED", // NOT_CALLED, INTERESTED, CALLBACK, NO_ANSWER, NOT_INTERESTED
    val lastCallDurationSeconds: Int = 0,
    val lastCallNotes: String? = null,
    val lastCallTimestamp: Long? = null,
    val isCleared: Boolean = false,
    val isVerified: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String, // Section Name (e.g. "Real Estate Oct 24", "Doctors List")
    val genre: String? = null, // e.g. "Real Estate", "B2B Tech"
    val status: String = "ACTIVE",
    val totalContacts: Int = 0,
    val completedContacts: Int = 0,
    val description: String? = null,
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
    val campaignId: Long = 0L, // Matches sectionId
    val customerId: Long = 0L,
    val phoneNumber: String,
    val contactName: String = "",
    val callStartTime: Long = System.currentTimeMillis(),
    val callEndTime: Long? = null,
    val durationSeconds: Int = 0,
    val outcome: String, // INTERESTED, CALLBACK, NO_ANSWER, NOT_INTERESTED
    val notes: String? = null,
    val timestamp: Long = System.currentTimeMillis()
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
