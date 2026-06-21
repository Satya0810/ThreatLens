package com.safeqr.scanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.safeqr.scanner.data.model.DynamicQrEntity
import com.safeqr.scanner.data.model.SharedQrEvent
import com.safeqr.scanner.data.model.UserEntity

@Database(
    entities = [
        ScanEntity::class,
        com.safeqr.scanner.data.model.DynamicQrEntity::class,
        com.safeqr.scanner.data.model.UserEntity::class,
        com.safeqr.scanner.data.model.SharedQrEvent::class,
        com.safeqr.scanner.data.model.ReportEntity::class,
        com.safeqr.scanner.data.model.TicketEntity::class,
        com.safeqr.scanner.data.model.AttendanceLogEntity::class,
        com.safeqr.scanner.data.model.EventRoleEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class ScanDatabase : RoomDatabase() {

    abstract fun scanDao(): ScanDao
    abstract fun dynamicQrDao(): DynamicQrDao
    abstract fun userDao(): UserDao
    abstract fun sharedQrDao(): SharedQrDao
    abstract fun reportDao(): ReportDao
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: ScanDatabase? = null

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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
