package com.safeqr.scanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safeqr.scanner.data.LinkGuardPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
/**
 * LinkGuardService — System-Wide Link Protection.
 *
 * A NotificationListenerService that monitors incoming notifications from
 * messaging apps (SMS, WhatsApp, Telegram, etc.) for suspicious URLs.
 * When a potentially dangerous link is detected, it posts a ThreatLens
 * warning notification with an "Analyze in ThreatLens" action button.
 */
class LinkGuardService : NotificationListenerService() {

    companion object {
        private const val TAG = "LinkGuard"
        private const val CHANNEL_ID = "link_guard_channel"
        private const val CHANNEL_NAME = "Link Guard Alerts"

        // URL extraction regex — matches http(s) URLs and common shortened domains
        private val URL_REGEX = Regex(
            """(https?://[^\s<>"{}|\\^`\[\]]+)""",
            RegexOption.IGNORE_CASE
        )

    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val context = applicationContext
        if (!LinkGuardPreferences.isLinkGuardEnabled(context)) return

        val packageName = sbn.packageName ?: return
        if (!LinkGuardPreferences.isAppMonitored(context, packageName)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: return

        // Extract URLs from notification text
        val urls = URL_REGEX.findAll(text).map { it.value }.toList()
        if (urls.isEmpty()) return

        for (url in urls) {
            LinkGuardPreferences.incrementLinksScanned(context)
            val sourceName = getAppName(packageName)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = com.safeqr.scanner.data.local.ScanDatabase.getInstance(context)
                    val dao = db.scanDao()
                    val reportDao = db.reportDao()
                    val analyzer = com.safeqr.scanner.analysis.ThreatAnalyzer(context)
                    val repository = com.safeqr.scanner.data.repository.ScanRepository(dao, analyzer, reportDao)
                    val wsApiKey = com.safeqr.scanner.data.PreferencesManager.getWebshrinkerApiKey(context)

                    val scanResult = repository.analyzeScan(url, wsApiKey)
                    val isThreat = scanResult.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS || 
                                   scanResult.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.CAUTION

                    if (isThreat) {
                        LinkGuardPreferences.incrementThreatsBlocked(context)
                        LinkGuardPreferences.addDetectedLink(
                            context,
                            LinkGuardPreferences.DetectedLink(url, sourceName, System.currentTimeMillis(), true)
                        )

                        val riskDescription = scanResult.threatDetails.firstOrNull() ?: scanResult.siteCategory
                        val threatScore = ((100f - scanResult.overallScore) / 10f).toInt().coerceIn(1, 10)
                        postWarningNotification(url, sourceName, threatScore, riskDescription)
                        Log.d(TAG, "⚠️ Suspicious link detected from $sourceName: $url (score: $threatScore)")
                    } else {
                        LinkGuardPreferences.addDetectedLink(
                            context,
                            LinkGuardPreferences.DetectedLink(url, sourceName, System.currentTimeMillis(), false)
                        )
                    }

                    // Update tags to include LinkGuard and source
                    val currentTags = scanResult.tags.toMutableList()
                    if (!currentTags.contains("LinkGuard")) currentTags.add("LinkGuard")
                    if (!currentTags.contains(sourceName)) currentTags.add(sourceName)
                    repository.updateTags(url, currentTags)

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to analyze link from notification: ${e.message}")
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed on removal
    }


    private fun postWarningNotification(url: String, source: String, threatScore: Int, riskDescription: String?) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel (required for Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when suspicious links are detected in messages"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to open ThreatLens and analyze the URL
        val analyzeIntent = Intent(this, com.safeqr.scanner.MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, url.hashCode(), analyzeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val severityText = when {
            threatScore >= 7 -> "🚨 HIGH RISK"
            threatScore >= 4 -> "⚠️ MEDIUM RISK"
            else -> "ℹ️ SUSPICIOUS"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("$severityText Link Detected")
            .setContentText("A suspicious link from $source was intercepted")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("ThreatLens Link Guard detected a suspicious URL received via $source.\n\nRisk: ${riskDescription ?: "Potential security threat"}\n\n🔗 ${url.take(80)}${if (url.length > 80) "..." else ""}\n\nTap to analyze this link safely.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_search,
                "Analyze in ThreatLens",
                pendingIntent
            )
            .build()

        notificationManager.notify(url.hashCode(), notification)
    }

    private fun getAppName(packageName: String): String {
        return when {
            packageName.contains("messaging") || packageName.contains("mms") -> "SMS"
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("telegram") -> "Telegram"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("twitter") -> "X (Twitter)"
            packageName.contains("facebook") || packageName.contains("orca") -> "Messenger"
            else -> packageName.substringAfterLast(".")
        }
    }
}
