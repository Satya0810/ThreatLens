package com.safeqr.scanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.safeqr.scanner.analysis.AIFederatedWorker
import com.safeqr.scanner.analysis.WebsiteCategorizer
import com.safeqr.scanner.data.remote.CloudDatasetManager
import com.safeqr.scanner.data.remote.DataSeeder
import com.safeqr.scanner.navigation.SafeQRNavigation
import com.safeqr.scanner.ui.theme.SafeQRScannerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Update AI weights and seed cloud datasets in the background
        lifecycleScope.launch {
            try {
                WebsiteCategorizer.updateWeightsFromCloud()
                DataSeeder.seedDatabaseIfEmpty()
                CloudDatasetManager.fetchAndCacheAll(this@MainActivity)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Firebase/Cloud Init failed. Falling back to local offline datasets.", e)
            }
        }
        
        // ── Autonomous Federated Learning Worker (Hive Mind Sync) ──
        val syncWorkRequest = PeriodicWorkRequestBuilder<AIFederatedWorker>(24, java.util.concurrent.TimeUnit.HOURS).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "HiveMindSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )

        val externalUrl = extractUrlFromIntent(intent)
        val openScanner = intent.getBooleanExtra("open_scanner", false)
        val shouldForceScan = openScanner || externalUrl != null

        setContent {
            SafeQRScannerTheme {
                SafeQRNavigation(externalUrl = externalUrl, forceScanRoute = shouldForceScan)
            }
        }
    }

    /**
     * Called when the activity is already running and a new intent arrives
     * (because of launchMode="singleTask"). Refreshes the UI with the new link.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val externalUrl = extractUrlFromIntent(intent)
        val openScanner = intent.getBooleanExtra("open_scanner", false)
        val shouldForceScan = openScanner || externalUrl != null
        if (shouldForceScan) {
            setContent {
                SafeQRScannerTheme {
                    SafeQRNavigation(externalUrl = externalUrl, forceScanRoute = shouldForceScan)
                }
            }
        }
    }

    /**
     * Extracts a URL from the incoming intent, supporting:
     * - ACTION_VIEW: URL from intent.data (clicked link)
     * - ACTION_SEND: URL from EXTRA_TEXT (shared link)
     */
    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null

        return when (intent.action) {
            // Link clicked → "Open with ThreatLens"
            Intent.ACTION_VIEW -> {
                intent.data?.toString()
            }

            // Link shared → "Share to ThreatLens"
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                // Extract URL from shared text (might contain extra text around it)
                if (sharedText != null) {
                    val urlRegex = Regex("(https?://[\\w\\-._~:/?#\\[\\]@!\$&'()*+,;=%]+)", RegexOption.IGNORE_CASE)
                    urlRegex.find(sharedText)?.value ?: sharedText.trim()
                } else null
            }

            else -> null
        }
    }
}
