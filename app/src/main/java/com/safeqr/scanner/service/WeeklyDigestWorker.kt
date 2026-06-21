package com.safeqr.scanner.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.firstOrNull
import com.safeqr.scanner.MainActivity
import com.safeqr.scanner.data.local.ScanDatabase

@android.annotation.SuppressLint("MissingPermission")
class WeeklyDigestWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val db = ScanDatabase.getInstance(context)
            val dao = db.scanDao()
            
            // Get last 7 days of history
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            
            // Get all scans as a list using firstOrNull on the Flow
            val scanList: List<com.safeqr.scanner.data.local.ScanEntity> = dao.getAllScans().firstOrNull() ?: emptyList()

            val weeklyScans = scanList.filter { it.timestamp >= sevenDaysAgo }
            
            if (weeklyScans.isEmpty()) {
                return Result.success()
            }

            val totalScans = weeklyScans.size
            val safeCount = weeklyScans.count { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.SAFE.name }
            val cautionCount = weeklyScans.count { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.CAUTION.name }
            val maliciousCount = weeklyScans.count { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS.name }

            val message = "You scanned $totalScans codes this week. " +
                    if (maliciousCount > 0) "We blocked $maliciousCount dangerous links!" 
                    else if (cautionCount > 0) "Stay cautious with $cautionCount flagged links."
                    else "All clear! $safeCount safe scans."

            sendNotification(message)
            return Result.success()
        } catch (e: Exception) {
            Log.e("WeeklyDigestWorker", "Error generating weekly digest", e)
            return Result.failure()
        }
    }

    private fun sendNotification(message: String) {
        val channelId = "weekly_digest_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Weekly Security Digest",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Weekly summary of your ThreatLens scans"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Fallback icon, ideally use R.drawable.ic_launcher_foreground
            .setContentTitle("ThreatLens Weekly Digest")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        try {
            notificationManager.notify(1001, notification)
        } catch (e: SecurityException) {
            // Ignore missing POST_NOTIFICATIONS permission
        }
    }
}
