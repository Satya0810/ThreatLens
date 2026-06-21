package com.safeqr.scanner

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.safeqr.scanner.service.WeeklyDigestWorker
import java.util.concurrent.TimeUnit

class SafeQRApplication : Application() {

    companion object {
        private const val TAG = "ThreatLensApp"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load SQLCipher libs", e)
        }

        try {
            val weeklyWorkRequest = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "WeeklyDigestWork",
                ExistingPeriodicWorkPolicy.KEEP,
                weeklyWorkRequest
            )
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager init failed", e)
        }
    }
}
