package com.safeqr.scanner.analysis

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Autonomous Federated Learning AI Engine (Perceptron)
 * Learns from API aggregations using Gradient Descent.
 */
object AILearningEngine {
    private const val PREFS_NAME = "AIFederatedLearningPrefs"
    private const val WEIGHTS_KEY = "NeuralWeights"
    private const val LEARNING_RATE = 0.01f

    // Expanded heuristic and API weights based on Federated Learning input.
    private val defaultWeights = mapOf(
        "url_length" to 0.01f,
        "subdomain_count" to 0.05f,
        "suspicious_chars" to 0.1f,
        "url_entropy" to 0.1f,
        "homograph_attack" to 0.2f,
        // Heuristics
        "adult_content" to 0.2f,
        "piracy_content" to 0.2f,
        "brand_impersonation" to 0.3f,
        "transactional_content" to 0.15f,
        // Community
        "community_reports" to 0.4f,
        "community_appreciates" to -0.4f,
        // 12+ APIs
        "api_safebrowsing" to 0.8f,
        "api_virustotal" to 0.8f,
        "api_urlhaus" to 0.8f,
        "api_urlscan" to 0.5f,
        "api_cloudflare" to 0.5f,
        "api_abuseipdb" to 0.5f,
        "api_spamhaus" to 0.5f,
        "api_cleanbrowsing" to 0.5f,
        "api_symantec" to 0.5f,
        "api_talos" to 0.5f,
        "api_openphish" to 0.5f,
        "api_ipapi" to 0.3f,
        "api_ssllabs" to 0.2f,
        "api_whois" to 0.2f
    )

    private var currentWeights = mutableMapOf<String, Float>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedWeightsJson = prefs.getString(WEIGHTS_KEY, null)
        
        if (savedWeightsJson != null) {
            try {
                val json = JSONObject(savedWeightsJson)
                val map = defaultWeights.toMutableMap() // Start with all current defaults
                json.keys().forEach { key ->
                    // Only override if it's a known key, or we just keep all keys
                    map[key] = json.getDouble(key).toFloat()
                }
                currentWeights = map
            } catch (e: Exception) {
                currentWeights = defaultWeights.toMutableMap()
            }
        } else {
            currentWeights = defaultWeights.toMutableMap()
        }
    }

    private fun saveWeights(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject(currentWeights as Map<*, *>).toString()
        prefs.edit().putString(WEIGHTS_KEY, json).apply()
    }

    fun getWeights(): Map<String, Float> = currentWeights

    /**
     * Extracts numerical features from the ScanResult for the Neural Network.
     * Incorporates all 12 APIs, heuristics, and community feedback.
     */
    fun extractFeatures(result: com.safeqr.scanner.data.model.ScanResult): Map<String, Float> {
        val url = result.expandedUrl ?: result.rawContent
        val lower = url.lowercase()
        val features = mutableMapOf<String, Float>()
        
        // Normalize length to a 0.0 - 1.0 scale
        features["url_length"] = (url.length / 200f).coerceAtMost(1.0f)
        
        // Count subdomains
        val domain = url.substringAfter("://").substringBefore("/")
        val dots = domain.count { it == '.' }
        features["subdomain_count"] = (dots / 5f).coerceAtMost(1.0f)
        
        // Suspicious characters
        val suspiciousChars = url.count { it == '-' || it == '_' || it == '@' || it == '%' }
        features["suspicious_chars"] = (suspiciousChars / 10f).coerceAtMost(1.0f)
        
        // URL Entropy (Simplified measurement of randomness/obfuscation)
        val charCounts = url.groupingBy { it }.eachCount()
        val entropy = charCounts.values.map { it.toDouble() / url.length }.sumOf { -it * (Math.log(it) / Math.log(2.0)) }
        features["url_entropy"] = (entropy / 5.0f).toFloat().coerceAtMost(1.0f)

        // Homograph Attack detection (checking for Cyrillic/Greek lookalikes)
        val hasHomographs = url.any { it.code in 0x0400..0x04FF || it.code in 0x0370..0x03FF }
        features["homograph_attack"] = if (hasHomographs) 1.0f else 0.0f
        
        // Heuristic matches
        features["adult_content"] = if (result.isAdultContent) 1.0f else 0.0f
        features["transactional_content"] = if (result.isTransaction) 1.0f else 0.0f
        features["brand_impersonation"] = if (result.isBrandImpersonation) 1.0f else 0.0f
        features["piracy_content"] = if (result.threatDetails.any { it.contains("piracy", ignoreCase = true) }) 1.0f else 0.0f
        
        // Community feedback
        features["community_reports"] = (result.communityReportsCount / 10f).coerceAtMost(1.0f)
        features["community_appreciates"] = (result.positiveDetails.size / 10f).coerceAtMost(1.0f)
        
        // API Verdicts
        val details = result.threatDetails.joinToString(" ")
        features["api_safebrowsing"] = if (details.contains("Safe Browsing")) 1.0f else 0.0f
        features["api_virustotal"] = if (details.contains("VirusTotal")) 1.0f else 0.0f
        features["api_urlhaus"] = if (details.contains("URLhaus")) 1.0f else 0.0f
        features["api_urlscan"] = if (details.contains("URLScan.io")) 1.0f else 0.0f
        features["api_cloudflare"] = if (details.contains("Cloudflare Radar")) 1.0f else 0.0f
        features["api_abuseipdb"] = if (details.contains("AbuseIPDB")) 1.0f else 0.0f
        features["api_spamhaus"] = if (details.contains("Spamhaus DBL")) 1.0f else 0.0f
        features["api_cleanbrowsing"] = if (details.contains("CleanBrowsing")) 1.0f else 0.0f
        features["api_symantec"] = if (details.contains("Symantec WebPulse")) 1.0f else 0.0f
        features["api_talos"] = if (details.contains("Cisco Talos")) 1.0f else 0.0f
        features["api_openphish"] = if (details.contains("OpenPhish")) 1.0f else 0.0f
        features["api_ipapi"] = if (details.contains("ip-api.com")) 1.0f else 0.0f
        features["api_ssllabs"] = if (details.contains("SSL Labs")) 1.0f else 0.0f
        features["api_whois"] = if (details.contains("WhoisXML")) 1.0f else 0.0f

        return features
    }

    /**
     * Forward Pass: Offline AI Prediction (0.0 = Safe, 1.0 = Malicious)
     */
    fun predict(features: Map<String, Float>): Float {
        var score = 0.0f
        features.forEach { (key, value) ->
            val weight = currentWeights[key] ?: 0.0f
            score += weight * value
        }
        // Sigmoid activation function
        return (1.0f / (1.0f + Math.exp(-score.toDouble()))).toFloat()
    }

    /**
     * Backward Pass: Train the model using Gradient Descent based on the API's Master Score.
     * @param targetLabel 1.0f if APIs said it's malicious, 0.0f if APIs said it's safe.
     */
    suspend fun train(context: Context, result: com.safeqr.scanner.data.model.ScanResult, targetLabel: Float) {
        withContext(Dispatchers.IO) {
            val features = extractFeatures(result)
            val prediction = predict(features)
            
            // Error = Target - Prediction
            val error = targetLabel - prediction
            
            // Only train if the error is significant
            if (Math.abs(error) > 0.1f) {
                Log.d("AILearningEngine", "Training triggered! Prediction: $prediction, Target: $targetLabel, Error: $error")
                
                // Update weights using Gradient Descent
                features.forEach { (key, value) ->
                    val oldWeight = currentWeights[key] ?: 0.0f
                    // W_new = W_old + LearningRate * Error * FeatureValue
                    val newWeight = oldWeight + (LEARNING_RATE * error * value)
                    currentWeights[key] = newWeight
                }
                
                saveWeights(context)
                
                // Autonomously push to the Hive Mind
                com.safeqr.scanner.data.remote.CloudSyncManager.reportLearnedWeights(currentWeights)
            }
        }
    }
    
    /**
     * Merge weights from the Global Hive Mind into our local model (Federated Learning Average).
     */
    fun mergeGlobalWeights(context: Context, globalWeights: Map<String, Float>) {
        if (globalWeights.isEmpty()) return
        
        Log.d("AILearningEngine", "Merging Global Hive Mind Weights...")
        globalWeights.forEach { (key, globalWeight) ->
            val localWeight = currentWeights[key] ?: defaultWeights[key] ?: 0.0f
            // Average them (50% local, 50% global)
            currentWeights[key] = (localWeight + globalWeight) / 2.0f
        }
        saveWeights(context)
    }
}
