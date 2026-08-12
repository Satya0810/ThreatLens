package com.safeqr.scanner.analysis

import android.content.Context
import android.util.Log
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * On-Device ML Engine for UPI Transaction Fraud Scoring
 *
 * Uses a single-layer perceptron with sigmoid activation (consistent with
 * ThreatLens's existing AILearningEngine architecture) but specialized for
 * UPI transaction patterns.
 *
 * Inspired by ML-based UPI fraud detection research (feature extraction,
 * probability-based risk scoring, temporal analysis) but implemented as a
 * fully on-device Kotlin solution with self-learning capabilities.
 *
 * 12 Features → Weighted Sum → Sigmoid → Fraud Probability [0.0 → 1.0]
 */
object UpiTransactionMLEngine {

    private const val TAG = "UpiTransactionML"
    private const val PREFS_NAME = "UpiMLPrefs"
    private const val WEIGHTS_KEY = "UpiNeuralWeights"
    private const val TRAINING_COUNT_KEY = "UpiTrainingCount"
    private const val LEARNING_RATE = 0.015f

    // ══════════════════════════════════════════════════════════════════
    //  DEFAULT WEIGHTS (tuned from domain knowledge)
    // ══════════════════════════════════════════════════════════════════

    private val defaultWeights = mapOf(
        // ── Amount Features ──
        "amount_normalized" to 0.15f,       // Higher amount = higher risk
        "amount_is_round" to 0.08f,         // Round amounts are slightly suspicious
        "amount_is_micro" to 0.20f,         // Micro-amounts = testing/probing
        "amount_is_excessive" to 0.35f,     // > 50k = very suspicious via QR
        // ── Temporal Features ──
        "time_risk_factor" to 0.12f,        // Late night = riskier
        // ── VPA Features ──
        "vpa_handle_known" to -0.30f,       // Known bank handle reduces risk
        "vpa_entropy" to 0.18f,             // Random VPA = suspicious
        "vpa_is_numeric" to 0.15f,          // Pure-number VPA = mule account
        // ── Payee Name NLP Features ──
        "name_has_urgency" to 0.25f,        // Social engineering signal
        "name_has_govt_keyword" to 0.40f,   // Govt impersonation = high risk
        "name_has_prize_keyword" to 0.30f,  // Prize scam signal
        "name_vpa_mismatch" to 0.25f        // Brand in name but not VPA
    )

    private var currentWeights = mutableMapOf<String, Float>()
    private var trainingCount = 0
    private var isInitialized = false

    // ══════════════════════════════════════════════════════════════════
    //  INITIALIZATION
    // ══════════════════════════════════════════════════════════════════

    fun init(context: Context) {
        if (isInitialized) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedWeightsJson = prefs.getString(WEIGHTS_KEY, null)
        trainingCount = prefs.getInt(TRAINING_COUNT_KEY, 0)

        if (savedWeightsJson != null) {
            try {
                val json = JSONObject(savedWeightsJson)
                val map = defaultWeights.toMutableMap()
                json.keys().forEach { key ->
                    map[key] = json.getDouble(key).toFloat()
                }
                currentWeights = map
            } catch (e: Exception) {
                currentWeights = defaultWeights.toMutableMap()
            }
        } else {
            currentWeights = defaultWeights.toMutableMap()
        }
        isInitialized = true
        Log.d(TAG, "UPI ML Engine initialized. Training count: $trainingCount")
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            currentWeights = defaultWeights.toMutableMap()
            isInitialized = true
        }
    }

    private fun saveWeights(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject(currentWeights as Map<*, *>).toString()
        prefs.edit()
            .putString(WEIGHTS_KEY, json)
            .putInt(TRAINING_COUNT_KEY, trainingCount)
            .apply()
    }

    fun getWeights(): Map<String, Float> = currentWeights.toMap()
    fun getTrainingCount(): Int = trainingCount

    // ══════════════════════════════════════════════════════════════════
    //  FEATURE EXTRACTION
    // ══════════════════════════════════════════════════════════════════

    /**
     * Extracts 12 numerical features from UPI QR data.
     * All features are normalized to [0.0, 1.0] range (or [-1.0, 1.0] for anti-features).
     */
    fun extractFeatures(actionData: Map<String, String>): Map<String, Float> {
        ensureInitialized()

        val features = mutableMapOf<String, Float>()
        val payeeName = (actionData["payeeName"] ?: "").lowercase()
        val payeeVpa = (actionData["payeeAddress"] ?: "").lowercase()
        val amountStr = actionData["amount"]
        val note = (actionData["note"] ?: "").lowercase()
        val amount = amountStr?.toDoubleOrNull()
        val combinedText = "$payeeName $note"

        // ── 1. Amount Features ──────────────────────────────────────
        // Normalized amount (0 → 0.0, 100000 → 1.0)
        features["amount_normalized"] = if (amount != null) {
            (amount / 100000.0).toFloat().coerceIn(0f, 1f)
        } else 0.5f // Unknown amount gets middle risk

        // Is round amount? (>= ₹1000 and divisible by 1000)
        features["amount_is_round"] = if (amount != null && amount >= 1000 && amount % 1000.0 == 0.0) {
            1.0f
        } else 0.0f

        // Is micro-amount? (₹1-₹5 probing)
        features["amount_is_micro"] = if (amount != null && amount in 0.01..5.0) {
            1.0f
        } else 0.0f

        // Is excessive? (> ₹50,000)
        features["amount_is_excessive"] = if (amount != null && amount > 50000) {
            ((amount - 50000) / 50000.0).toFloat().coerceIn(0f, 1f)
        } else 0.0f

        // ── 2. Temporal Feature ─────────────────────────────────────
        // Time risk: sine-cosine encoding of hour (peaks at 2-4 AM)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeRisk = when {
            hour in 0..5 -> 0.8f   // Late night / early morning = risky
            hour in 22..23 -> 0.6f // Late evening
            hour in 6..8 -> 0.3f   // Early morning
            else -> 0.1f            // Normal business hours = low risk
        }
        features["time_risk_factor"] = timeRisk

        // ── 3. VPA Features ─────────────────────────────────────────
        val vpaHandle = payeeVpa.substringAfter("@", "").trim()
        val vpaUsername = payeeVpa.substringBefore("@", "").trim()

        // Known handle?
        features["vpa_handle_known"] = if (UpiPaymentAnalyzer.KNOWN_UPI_HANDLES.containsKey(vpaHandle)) {
            1.0f // Known = REDUCES risk (negative weight)
        } else 0.0f

        // VPA entropy
        features["vpa_entropy"] = if (vpaUsername.isNotBlank()) {
            val entropy = calculateEntropy(vpaUsername)
            (entropy / 4.5f).toFloat().coerceIn(0f, 1f)
        } else 0.5f

        // Numeric-only VPA
        features["vpa_is_numeric"] = if (vpaUsername.isNotBlank() && vpaUsername.all { it.isDigit() } && vpaUsername.length >= 8) {
            1.0f
        } else 0.0f

        // ── 4. Payee Name NLP Features ──────────────────────────────
        // Urgency keywords
        val urgencyHits = UpiPaymentAnalyzer.run {
            listOf("urgent", "immediately", "fine", "penalty", "blocked", "suspended", "kyc", "last chance", "legal action", "arrest")
                .count { combinedText.contains(it) }
        }
        features["name_has_urgency"] = (urgencyHits / 3.0f).coerceIn(0f, 1f)

        // Government impersonation
        val govtHits = listOf("income tax", "rbi", "uidai", "aadhaar", "police", "court", "customs", "gst", "government", "ministry")
            .count { combinedText.contains(it) }
        features["name_has_govt_keyword"] = (govtHits / 2.0f).coerceIn(0f, 1f)

        // Prize/lottery keywords
        val prizeHits = listOf("winner", "prize", "lottery", "reward", "cashback", "claim", "congratulations", "giveaway")
            .count { combinedText.contains(it) }
        features["name_has_prize_keyword"] = (prizeHits / 2.0f).coerceIn(0f, 1f)

        // Brand-VPA mismatch
        val brandNames = listOf("amazon", "flipkart", "swiggy", "zomato", "paytm", "phonepe", "google", "uber", "ola")
        val matchedBrand = brandNames.find { payeeName.contains(it) }
        features["name_vpa_mismatch"] = if (matchedBrand != null && !payeeVpa.contains(matchedBrand.replace(" ", ""))) {
            1.0f
        } else 0.0f

        return features
    }

    // ══════════════════════════════════════════════════════════════════
    //  FORWARD PASS (PREDICTION)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Runs the perceptron forward pass.
     * @return Fraud probability [0.0 (safe) → 1.0 (fraud)]
     */
    fun predict(features: Map<String, Float>): Float {
        ensureInitialized()

        var weightedSum = 0.0f
        features.forEach { (key, value) ->
            val weight = currentWeights[key] ?: 0.0f
            weightedSum += weight * value
        }
        // Sigmoid activation
        return (1.0f / (1.0f + Math.exp(-weightedSum.toDouble()))).toFloat()
    }

    /**
     * Convenience method: extract features + predict in one call.
     */
    fun score(actionData: Map<String, String>): Float {
        val features = extractFeatures(actionData)
        return predict(features)
    }

    // ══════════════════════════════════════════════════════════════════
    //  BACKWARD PASS (SELF-LEARNING)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Trains the model using gradient descent.
     * Called automatically when the user takes action on a UPI QR:
     * - User taps "Pay" → targetLabel = 0.0 (user verified it as safe)
     * - User reports → targetLabel = 1.0 (user confirmed fraud)
     */
    suspend fun train(context: Context, actionData: Map<String, String>, targetLabel: Float) {
        withContext(Dispatchers.IO) {
            ensureInitialized()

            val features = extractFeatures(actionData)
            val prediction = predict(features)
            val error = targetLabel - prediction

            // Only train if error is significant
            if (Math.abs(error) > 0.05f) {
                Log.d(TAG, "UPI ML Training — Prediction: %.3f, Target: %.1f, Error: %.3f".format(prediction, targetLabel, error))

                features.forEach { (key, value) ->
                    val oldWeight = currentWeights[key] ?: 0.0f
                    val newWeight = oldWeight + (LEARNING_RATE * error * value)
                    currentWeights[key] = newWeight
                }

                trainingCount++
                saveWeights(context)

                // Federated sync (REMOVED FOR PRIVACY)

                Log.d(TAG, "UPI ML Training complete. Total training samples: $trainingCount")
            }
        }
    }

    /**
     * Merge global weights from federated cloud into local model.
     */
    fun mergeGlobalWeights(context: Context, globalWeights: Map<String, Float>) {
        if (globalWeights.isEmpty()) return
        ensureInitialized()

        Log.d(TAG, "Merging Global UPI ML Weights...")
        globalWeights.forEach { (key, globalWeight) ->
            if (currentWeights.containsKey(key)) {
                val localWeight = currentWeights[key] ?: defaultWeights[key] ?: 0.0f
                currentWeights[key] = (localWeight * 0.6f + globalWeight * 0.4f) // Favor local
            }
        }
        saveWeights(context)
    }

    // ══════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ══════════════════════════════════════════════════════════════════

    private fun calculateEntropy(input: String): Double {
        if (input.isEmpty()) return 0.0
        val charCounts = input.groupingBy { it }.eachCount()
        return charCounts.values.sumOf { count ->
            val p = count.toDouble() / input.length
            -p * (Math.log(p) / Math.log(2.0))
        }
    }

    /**
     * Reset model to default weights (for debugging/testing).
     */
    fun reset(context: Context) {
        currentWeights = defaultWeights.toMutableMap()
        trainingCount = 0
        saveWeights(context)
        Log.d(TAG, "UPI ML Engine reset to defaults")
    }
}
