package com.safeqr.scanner.analysis

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safeqr.scanner.data.remote.CloudSyncManager

/**
 * A background worker that runs autonomously once a day to pull the latest AI weights
 * from the Firebase Hive Mind and merge them with the local device's AI.
 */
class AIFederatedWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("AIFederatedWorker", "Starting autonomous Hive Mind sync...")
            
            // 1. Fetch the aggregated global weights from Firebase
            val globalWeights = CloudSyncManager.fetchGlobalWeights()
            
            // 2. Merge them with the local perceptron weights
            if (globalWeights.isNotEmpty()) {
                AILearningEngine.mergeGlobalWeights(applicationContext, globalWeights)
                Log.d("AIFederatedWorker", "Hive Mind sync completed successfully.")
            } else {
                Log.d("AIFederatedWorker", "No global weights found in Hive Mind.")
            }
            
            // 3. Fetch latest global scan datasets
            com.safeqr.scanner.data.remote.CloudDatasetManager.fetchAndCacheAll(applicationContext)
            Log.d("AIFederatedWorker", "Global scan datasets refreshed.")
            
            Result.success()
        } catch (e: Exception) {
            Log.e("AIFederatedWorker", "Failed to sync with Hive Mind: ${e.message}", e)
            Result.retry()
        }
    }
}
