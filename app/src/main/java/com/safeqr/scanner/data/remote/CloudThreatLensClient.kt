package com.safeqr.scanner.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.annotations.SerializedName
import com.safeqr.scanner.analysis.graph.ScamWorkflowGraph
import com.safeqr.scanner.analysis.ingress.PiiSanitizer
import com.safeqr.scanner.analysis.telemetry.DeviceThreatStateMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * CloudThreatLensClient — Secure Sovereign Cloud AI Gateway
 *
 * Transmits Zero-PII sanitized telemetry to the Sovereign Cloud AI service
 * with a strict 400ms timeout budget. If the cloud is unreachable or times out,
 * ThreatLens gracefully falls back to the on-device conformal ML engine.
 */
object CloudThreatLensClient {

    private const val TAG = "CloudThreatLensClient"

    // 10.0.2.2 is Android Emulator's default gateway to the host machine's localhost:8000
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

    data class CloudThreatRequest(
        @SerializedName("sanitized_payee_address") val sanitizedPayeeAddress: String,
        @SerializedName("sanitized_payee_name") val sanitizedPayeeName: String = "",
        @SerializedName("amount") val amount: Double = 0.0,
        @SerializedName("note") val note: String = "",
        @SerializedName("mcc") val mcc: String = "",
        @SerializedName("has_active_call") val hasActiveCall: Boolean = false,
        @SerializedName("is_screen_shared") val isScreenShared: Boolean = false,
        @SerializedName("has_suspicious_accessibility") val hasSuspiciousAccessibility: Boolean = false,
        @SerializedName("latest_ingress_pretext_text") val latestIngressPretextText: String? = null,
        @SerializedName("pretext_category") val pretextCategory: String? = null,
        @SerializedName("pretext_delta_minutes") val pretextDeltaMinutes: Double? = null
    )

    data class CloudThreatResponse(
        @SerializedName("risk_score") val riskScore: Float,
        @SerializedName("fraud_probability") val fraudProbability: Float,
        @SerializedName("decision_tier") val decisionTier: String,
        @SerializedName("conformal_threshold") val conformalThreshold: Float = 0.01f,
        @SerializedName("guaranteed_max_false_alarm_rate") val guaranteedMaxFalseAlarmRate: Float = 0.002f,
        @SerializedName("detected_workflow_type") val detectedWorkflowType: String,
        @SerializedName("explainable_reasons") val explainableReasons: List<String> = emptyList(),
        @SerializedName("nlp_pretext_confidence") val nlpPretextConfidence: Float = 0.0f,
        @SerializedName("mule_network_risk") val muleNetworkRisk: Float = 0.0f,
        @SerializedName("inference_latency_ms") val inferenceLatencyMs: Float = 0.0f
    )

    data class CloudWebScrapedRequest(
        @SerializedName("url") val url: String = "",
        @SerializedName("title") val title: String = "",
        @SerializedName("meta_keywords") val metaKeywords: String = "",
        @SerializedName("meta_description") val metaDescription: String = "",
        @SerializedName("h1_h2_text") val h1H2Text: String = "",
        @SerializedName("body_text") val bodyText: String = ""
    )

    data class CloudWebScrapedResponse(
        @SerializedName("url") val url: String = "",
        @SerializedName("category") val category: String,
        @SerializedName("category_label") val categoryLabel: String = "",
        @SerializedName("threat_level") val threatLevel: String = "SAFE",
        @SerializedName("confidence") val confidence: Float = 0.0f,
        @SerializedName("is_threat") val isThreat: Boolean = false,
        @SerializedName("explainable_reasons") val explainableReasons: List<String> = emptyList(),
        @SerializedName("evaluation_latency_ms") val evaluationLatencyMs: Float = 0.0f
    )

    interface ThreatLensApiService {
        @POST("api/v1/analyze/workflow")
        suspend fun analyzeWorkflow(@Body request: CloudThreatRequest): CloudThreatResponse

        @POST("api/v1/analyze/webpage")
        suspend fun analyzeWebpage(@Body request: CloudWebScrapedRequest): CloudWebScrapedResponse
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(400, TimeUnit.MILLISECONDS)
        .readTimeout(400, TimeUnit.MILLISECONDS)
        .writeTimeout(400, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val apiService: ThreatLensApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ThreatLensApiService::class.java)
    }

    /**
     * Executes cloud AI inference with Zero-PII sanitization and strict 400ms timeout.
     * Returns null if cloud service is unavailable, triggering instant local fallback.
     */
    suspend fun analyzeWorkflow(
        actionData: Map<String, String>,
        context: Context
    ): CloudThreatResponse? = withContext(Dispatchers.IO) {
        try {
            val rawPayeeAddress = actionData["payeeAddress"] ?: ""
            val rawPayeeName = actionData["payeeName"] ?: ""
            val rawNote = actionData["note"] ?: ""
            val amountStr = actionData["amount"]
            val amount = amountStr?.toDoubleOrNull() ?: 0.0
            val mcc = actionData["mc"] ?: ""

            // 1. Mandatory Client-Side PII Scrubbing
            val sanitizedPayeeAddress = PiiSanitizer.sanitize(rawPayeeAddress)
            val sanitizedPayeeName = PiiSanitizer.sanitize(rawPayeeName)
            val sanitizedNote = PiiSanitizer.sanitize(rawNote)

            // 2. Real-Time Device Telemetry
            val telemetry = DeviceThreatStateMonitor.captureCurrentState(context)

            // 3. Temporal Ingress Context
            val latestPretext = ScamWorkflowGraph.getLatestPretext()
            val pretextCategory = latestPretext?.category
            val pretextDeltaMins = if (latestPretext != null) {
                (System.currentTimeMillis() - latestPretext.timestampMs) / 60000.0
            } else null

            val request = CloudThreatRequest(
                sanitizedPayeeAddress = sanitizedPayeeAddress,
                sanitizedPayeeName = sanitizedPayeeName,
                amount = amount,
                note = sanitizedNote,
                mcc = mcc,
                hasActiveCall = telemetry.isCallActive,
                isScreenShared = telemetry.isScreenSharingActive,
                hasSuspiciousAccessibility = telemetry.hasSuspiciousAccessibilityService,
                latestIngressPretextText = (latestPretext?.details?.get("explanation") as? String)?.let { PiiSanitizer.sanitize(it) },
                pretextCategory = pretextCategory,
                pretextDeltaMinutes = pretextDeltaMins
            )

            val startTime = System.currentTimeMillis()
            val response = apiService.analyzeWorkflow(request)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Cloud AI responded in ${elapsed}ms -> Tier: ${response.decisionTier}, Risk: ${response.riskScore}")
            response

        } catch (e: Exception) {
            // Expected when offline or server unreachable; trigger silent on-device fallback
            Log.w(TAG, "Cloud AI unavailable or timed out (>400ms). Falling back to local ML engine: ${e.message}")
            null
        }
    }

    /**
     * Executes Cloud AI Webpage Categorization with a strict 400ms timeout budget.
     * Returns null if cloud service is unavailable, triggering instant local fallback.
     */
    suspend fun analyzeWebpage(
        url: String,
        title: String = "",
        metaKeywords: String = "",
        metaDescription: String = "",
        h1H2Text: String = "",
        bodyText: String = ""
    ): CloudWebScrapedResponse? = withContext(Dispatchers.IO) {
        try {
            val req = CloudWebScrapedRequest(
                url = url,
                title = title,
                metaKeywords = metaKeywords,
                metaDescription = metaDescription,
                h1H2Text = h1H2Text,
                bodyText = if (bodyText.length > 4000) bodyText.substring(0, 4000) else bodyText
            )
            val startTime = System.currentTimeMillis()
            val response = apiService.analyzeWebpage(req)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Cloud AI Webpage Categorizer responded in ${elapsed}ms -> Cat: ${response.category}, Threat: ${response.threatLevel}")
            response
        } catch (e: Exception) {
            Log.w(TAG, "Cloud Webpage Categorizer unavailable or timed out (>400ms): ${e.message}")
            null
        }
    }
}
