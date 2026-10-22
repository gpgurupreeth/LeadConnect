package com.example.mobileapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CallAttemptEntity
import com.example.mobileapp.data.local.entities.CampaignEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.data.local.entities.MessageJobEntity
import com.example.mobileapp.data.local.entities.SuppressionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY id DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE isSuppressed = 0 ORDER BY id ASC")
    suspend fun getEligibleCustomers(): List<CustomerEntity>

    @Query("SELECT COUNT(*) FROM customers")
    fun getCustomerCountFlow(): Flow<Int>

    @Query("SELECT phoneNumber FROM customers WHERE isSuppressed = 1")
    suspend fun getSuppressedCustomerNumbers(): List<String>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getCustomerByPhone(phoneNumber: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>): List<Long>

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("UPDATE customers SET isSuppressed = :suppressed WHERE phoneNumber = :phoneNumber")
    suspend fun updateSuppressionStatus(phoneNumber: String, suppressed: Boolean)

    @Query("UPDATE customers SET consentGiven = :consent WHERE id = :id")
    suspend fun updateConsentStatus(id: Long, consent: Boolean)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    fun getAllCampaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    suspend fun getCampaignById(id: Long): CampaignEntity?

    @Query("SELECT * FROM campaigns WHERE status IN ('RUNNING', 'PAUSED') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActiveCampaign(): CampaignEntity?

    @Query("SELECT * FROM campaigns WHERE status IN ('RUNNING', 'PAUSED') ORDER BY updatedAt DESC LIMIT 1")
    fun getActiveCampaignFlow(): Flow<CampaignEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(campaign: CampaignEntity): Long

    @Update
    suspend fun update(campaign: CampaignEntity)
}

@Dao
interface CallAttemptDao {
    @Query("SELECT * FROM call_attempts WHERE campaignId = :campaignId ORDER BY timestamp DESC")
    fun getAttemptsForCampaign(campaignId: Long): Flow<List<CallAttemptEntity>>

    @Query("SELECT * FROM call_attempts ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAttempts(limit: Int = 100): Flow<List<CallAttemptEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM call_attempts WHERE campaignId = :campaignId AND customerId = :customerId)")
    suspend fun hasAttemptForCustomer(campaignId: Long, customerId: Long): Boolean

    @Query("SELECT * FROM call_attempts WHERE campaignId = :campaignId AND customerId = :customerId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAttemptForCustomer(campaignId: Long, customerId: Long): CallAttemptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: CallAttemptEntity): Long

    @Update
    suspend fun update(attempt: CallAttemptEntity)

    @Query("UPDATE call_attempts SET outcome = :outcome, notes = :notes, followUpEligible = :followUpEligible, callEndTime = :endTime, durationSeconds = :duration WHERE id = :attemptId")
    suspend fun updateOutcome(attemptId: Long, outcome: String, notes: String?, followUpEligible: Boolean, endTime: Long, duration: Int)
}

@Dao
interface MessageJobDao {
    @Query("SELECT * FROM message_jobs ORDER BY createdAt DESC")
    fun getAllJobsFlow(): Flow<List<MessageJobEntity>>

    @Query("SELECT * FROM message_jobs WHERE status = :status ORDER BY createdAt ASC")
    fun getJobsByStatus(status: String): Flow<List<MessageJobEntity>>

    @Query("SELECT * FROM message_jobs WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getPendingJobsList(status: String = "PENDING"): List<MessageJobEntity>

    @Query("SELECT * FROM message_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getJobByJobId(jobId: String): MessageJobEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(job: MessageJobEntity): Long

    @Update
    suspend fun update(job: MessageJobEntity)
}

@Dao
interface AuditEventDao {
    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 100): Flow<List<AuditEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AuditEventEntity): Long
}

@Dao
interface SuppressionDao {
    @Query("SELECT * FROM suppressions")
    fun getAllSuppressions(): Flow<List<SuppressionEntity>>

    @Query("SELECT phoneNumber FROM suppressions")
    suspend fun getAllSuppressedNumbers(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM suppressions WHERE phoneNumber = :phoneNumber)")
    suspend fun isSuppressed(phoneNumber: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(suppression: SuppressionEntity)

    @Query("DELETE FROM suppressions WHERE phoneNumber = :phoneNumber")
    suspend fun delete(phoneNumber: String)
}
