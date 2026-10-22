package com.example.mobileapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mobileapp.data.local.dao.AuditEventDao
import com.example.mobileapp.data.local.dao.CallAttemptDao
import com.example.mobileapp.data.local.dao.CampaignDao
import com.example.mobileapp.data.local.dao.CustomerDao
import com.example.mobileapp.data.local.dao.MessageJobDao
import com.example.mobileapp.data.local.dao.SuppressionDao
import com.example.mobileapp.data.local.entities.AuditEventEntity
import com.example.mobileapp.data.local.entities.CallAttemptEntity
import com.example.mobileapp.data.local.entities.CampaignEntity
import com.example.mobileapp.data.local.entities.CustomerEntity
import com.example.mobileapp.data.local.entities.MessageJobEntity
import com.example.mobileapp.data.local.entities.SuppressionEntity

@Database(
    entities = [
        CustomerEntity::class,
        CampaignEntity::class,
        CallAttemptEntity::class,
        MessageJobEntity::class,
        AuditEventEntity::class,
        SuppressionEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun campaignDao(): CampaignDao
    abstract fun callAttemptDao(): CallAttemptDao
    abstract fun messageJobDao(): MessageJobDao
    abstract fun auditEventDao(): AuditEventDao
    abstract fun suppressionDao(): SuppressionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Adds consent provenance and nullable provider message IDs. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE customers ADD COLUMN consentGiven INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE message_jobs ADD COLUMN consentVerified INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE message_jobs ADD COLUMN providerMessageId TEXT"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sales_caller_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
