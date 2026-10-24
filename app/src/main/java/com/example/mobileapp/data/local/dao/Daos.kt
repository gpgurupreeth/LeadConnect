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
import com.example.mobileapp.data.local.entities.SuppressionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY id DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE sectionId = :sectionId ORDER BY id ASC")
    fun getCustomersForSection(sectionId: Long): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE sectionId = :sectionId ORDER BY id ASC")
    suspend fun getCustomersForSectionList(sectionId: Long): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE isSuppressed = 0 ORDER BY id ASC")
    suspend fun getEligibleCustomers(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE sectionId = :sectionId AND isCleared = 0 AND isSuppressed = 0 ORDER BY id ASC")
    fun getPendingCustomersFlow(sectionId: Long): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE sectionId = :sectionId AND isCleared = 1 ORDER BY lastCallTimestamp DESC")
    fun getClearedCustomersFlow(sectionId: Long): Flow<List<CustomerEntity>>

    @Query("SELECT COUNT(*) FROM customers WHERE sectionId = :sectionId")
    fun getSectionLeadsCount(sectionId: Long): Flow<Int>

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

    @Query("UPDATE customers SET callStatus = :status, lastCallDurationSeconds = :duration, lastCallNotes = :notes, lastCallTimestamp = :timestamp, isCleared = :isCleared WHERE id = :customerId")
    suspend fun updateCallOutcome(customerId: Long, status: String, duration: Int, notes: String?, timestamp: Long, isCleared: Boolean)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: Long)

    @Query("DELETE FROM customers WHERE sectionId = :sectionId")
    suspend fun deleteCustomersBySection(sectionId: Long)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    fun getAllCampaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    suspend fun getCampaignById(id: Long): CampaignEntity?

    @Query("SELECT * FROM campaigns WHERE status IN ('RUNNING', 'PAUSED') ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveCampaign(): CampaignEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(campaign: CampaignEntity): Long

    @Update
    suspend fun update(campaign: CampaignEntity)

    @Query("UPDATE campaigns SET totalContacts = :total, completedContacts = :completed WHERE id = :id")
    suspend fun updateCounts(id: Long, total: Int, completed: Int)

    @Query("DELETE FROM campaigns WHERE id = :id")
    suspend fun deleteCampaign(id: Long)

    @Query("DELETE FROM campaigns")
    suspend fun deleteAll()
}

@Dao
interface CallAttemptDao {
    @Query("SELECT * FROM call_attempts ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CallAttemptEntity>>

    @Query("SELECT * FROM call_attempts WHERE campaignId = :sectionId ORDER BY timestamp DESC")
    fun getAttemptsForSection(sectionId: Long): Flow<List<CallAttemptEntity>>

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

    @Query("UPDATE call_attempts SET outcome = :outcome, notes = :notes, callEndTime = :endTime, durationSeconds = :duration WHERE id = :attemptId")
    suspend fun updateOutcome(attemptId: Long, outcome: String, notes: String?, endTime: Long, duration: Int)

    @Query("DELETE FROM call_attempts WHERE campaignId = :sectionId")
    suspend fun deleteAttemptsBySection(sectionId: Long)

    @Query("DELETE FROM call_attempts")
    suspend fun clearAllHistory()
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
