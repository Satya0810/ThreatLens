package com.safeqr.scanner.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await

object CloudDatasetManager {
    private const val TAG = "CloudDatasetManager"
    private const val PREFS_NAME = "cloud_datasets_prefs"

    private var threatData: ThreatAnalyzerData? = null
    private var categorizerData: WebsiteCategorizerData? = null
    private var heuristicData: HeuristicCheckerData? = null
    private var sandboxData: SandboxBrowserData? = null

    fun getThreatAnalyzerData(): ThreatAnalyzerData {
        return threatData ?: ThreatAnalyzerData()
    }

    fun getWebsiteCategorizerData(): WebsiteCategorizerData {
        return categorizerData ?: WebsiteCategorizerData()
    }

    fun getHeuristicCheckerData(): HeuristicCheckerData {
        return heuristicData ?: HeuristicCheckerData()
    }

    fun getSandboxBrowserData(): SandboxBrowserData {
        return sandboxData ?: SandboxBrowserData()
    }

    suspend fun fetchAndCacheAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()

        try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("app_config").document("datasets").get().await()
            if (doc.exists()) {
                val json = gson.toJson(doc.data)
                prefs.edit().putString("datasets_json", json).apply()
                loadFromCache(context)
                Log.d(TAG, "Successfully fetched and cached cloud datasets.")
            } else {
                Log.d(TAG, "Cloud datasets document does not exist.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch cloud datasets: ${e.message}", e)
        }
    }

    fun loadFromCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("datasets_json", null)
        if (json != null) {
            try {
                val gson = Gson()
                // Parse the umbrella object
                val wrapper = gson.fromJson(json, CloudDatasetsWrapper::class.java)
                threatData = wrapper.threatAnalyzerData
                categorizerData = wrapper.websiteCategorizerData
                heuristicData = wrapper.heuristicCheckerData
                sandboxData = wrapper.sandboxBrowserData
                Log.d(TAG, "Loaded cloud datasets from local cache.")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing cached datasets", e)
            }
        }
    }
}

data class CloudDatasetsWrapper(
    val threatAnalyzerData: ThreatAnalyzerData? = null,
    val websiteCategorizerData: WebsiteCategorizerData? = null,
    val heuristicCheckerData: HeuristicCheckerData? = null,
    val sandboxBrowserData: SandboxBrowserData? = null
)
