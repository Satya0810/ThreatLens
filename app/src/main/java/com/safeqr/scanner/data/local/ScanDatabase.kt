package com.safeqr.scanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.safeqr.scanner.data.model.DynamicQrEntity
import com.safeqr.scanner.data.model.SharedQrEvent
import com.safeqr.scanner.data.model.UserEntity
import com.safeqr.scanner.data.model.ThreatReportEntity
import com.safeqr.scanner.data.model.VerifiableCredentialEntity

@Database(
    entities = [
        ScanEntity::class,
        com.safeqr.scanner.data.model.DynamicQrEntity::class,
        com.safeqr.scanner.data.model.UserEntity::class,
        com.safeqr.scanner.data.model.SharedQrEvent::class,
        com.safeqr.scanner.data.model.ReportEntity::class,
        com.safeqr.scanner.data.model.TicketEntity::class,
        com.safeqr.scanner.data.model.AttendanceLogEntity::class,
        com.safeqr.scanner.data.model.EventRoleEntity::class,
        com.safeqr.scanner.data.model.EventEntity::class,
        ThreatReportEntity::class,
        VerifiableCredentialEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class ScanDatabase : RoomDatabase() {

    abstract fun scanDao(): ScanDao
    abstract fun dynamicQrDao(): DynamicQrDao
    abstract fun userDao(): UserDao
    abstract fun sharedQrDao(): SharedQrDao
    abstract fun reportDao(): ReportDao
    abstract fun eventDao(): EventDao
    abstract fun threatReportDao(): ThreatReportDao
    abstract fun verifiableCredentialDao(): VerifiableCredentialDao

    companion object {
        @Volatile
        private var INSTANCE: ScanDatabase? = null

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE scan_history ADD COLUMN upiAnalysisJson TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE scan_history ADD COLUMN wifiAnalysisJson TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create threat_reports table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `threat_reports` (
                        `reportId` TEXT NOT NULL,
                        `rawContent` TEXT NOT NULL,
                        `reportType` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `locationName` TEXT,
                        `photoUri` TEXT,
                        `reporterUserId` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isSynced` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`reportId`)
                    )
                """)

                // Create verifiable_credentials table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `verifiable_credentials` (
                        `vcId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `issuerName` TEXT NOT NULL,
                        `issuerId` TEXT NOT NULL,
                        `subjectName` TEXT NOT NULL,
                        `claims` TEXT NOT NULL,
                        `issuedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER,
                        `qrPayload` TEXT NOT NULL,
                        `isRevoked` INTEGER NOT NULL DEFAULT 0,
                        `createdByUserId` TEXT NOT NULL,
                        PRIMARY KEY(`vcId`)
                    )
                """)
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE scan_history ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE scan_history ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to existing tickets table
                database.execSQL("ALTER TABLE tickets ADD COLUMN attendeeName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE tickets ADD COLUMN ticketTier TEXT NOT NULL DEFAULT 'General'")
                
                // Create the new events table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `events` (
                        `eventId` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `venue` TEXT NOT NULL, 
                        `startTime` INTEGER, 
                        `endTime` INTEGER, 
                        `capacity` INTEGER NOT NULL, 
                        `bannerColor` INTEGER NOT NULL, 
                        `isActive` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `createdByUserId` TEXT NOT NULL, 
                        PRIMARY KEY(`eventId`)
                    )
                """)
                
                // Create attendance_logs table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `attendance_logs` (
                        `logId` TEXT NOT NULL,
                        `ticketId` TEXT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `scannedByUserId` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`logId`)
                    )
                """)
                
                // Create event_roles table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `event_roles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `role` TEXT NOT NULL
                    )
                """)
            }
        }

        fun getInstance(context: Context): ScanDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = com.safeqr.scanner.data.PreferencesManager.getDatabasePassphrase(context)
                val factory = net.sqlcipher.database.SupportFactory(passphrase)

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "safeqr_db"
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
