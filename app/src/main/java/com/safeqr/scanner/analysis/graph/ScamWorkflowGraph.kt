package com.safeqr.scanner.analysis.graph

import android.os.SystemClock
import com.safeqr.scanner.analysis.ingress.SmishingPatternMatcher
import com.safeqr.scanner.analysis.telemetry.DeviceThreatStateMonitor

/**
 * ScamWorkflowGraph — Heterogeneous Temporal Scam Graph (HTSG)
 *
 * Models victim-attacker interaction trajectories across time rather than
 * analyzing transactions in isolation. Fuses:
 * 1. Communication Ingress (Vector 1: SMS / WhatsApp panic pretexts)
 * 2. Device Telemetry (Vector 2: Active stranger phone call, remote desktop screen-sharing)
 * 3. Financial Intent (Vector 3: UPI VPA, amount, reverse UPI flags)
 */
object ScamWorkflowGraph {

    enum class NodeType {
        COMMUNICATION_INGRESS,
        DEVICE_ANOMALY,
        PAYMENT_INTENT
    }

    data class GraphNode(
        val id: String,
        val type: NodeType,
        val timestampMs: Long = System.currentTimeMillis(),
        val category: String = "",
        val details: Map<String, Any> = emptyMap()
    )

    data class WorkflowRiskProfile(
        val compositeScore: Float,
        val isWorkflowScam: Boolean,
        val detectedWorkflowType: String,
        val explainableReasons: List<String>,
        val activeVectorsCount: Int
    )

    private val slidingWindowMs: Long = 10 * 60 * 1000L // 10-Minute Sliding Memory Window
    private val nodes = mutableListOf<GraphNode>()

    @Synchronized
    fun recordIngressEvent(pretext: SmishingPatternMatcher.PretextDetectionResult) {
        pruneExpiredNodes()
        if (pretext.category != SmishingPatternMatcher.PretextCategory.BENIGN) {
            nodes.add(
                GraphNode(
                    id = "INGRESS_${System.currentTimeMillis()}",
                    type = NodeType.COMMUNICATION_INGRESS,
                    category = pretext.category.name,
                    details = mapOf(
                        "displayName" to pretext.category.displayName,
                        "explanation" to pretext.explanation
                    )
                )
            )
        }
    }

    @Synchronized
    fun getLatestPretext(): GraphNode? {
        pruneExpiredNodes()
        return nodes.lastOrNull { it.type == NodeType.COMMUNICATION_INGRESS }
    }

    @Synchronized
    fun recordDeviceState(state: DeviceThreatStateMonitor.DeviceThreatState) {
        pruneExpiredNodes()
        if (state.hasActiveEnvironmentalThreat) {
            nodes.add(
                GraphNode(
                    id = "DEV_${System.currentTimeMillis()}",
                    type = NodeType.DEVICE_ANOMALY,
                    details = mapOf(
                        "call_active" to state.isCallActive,
                        "screenshare_active" to state.isScreenSharingActive,
                        "accessibility_active" to state.hasSuspiciousAccessibilityService
                    )
                )
            )
        }
    }

    @Synchronized
    fun evaluatePaymentIntent(
        rawRiskScore: Float,
        amount: Double?,
        isReverseUpi: Boolean,
        currentState: DeviceThreatStateMonitor.DeviceThreatState
    ): WorkflowRiskProfile {
        pruneExpiredNodes()

        // Also record current device state
        recordDeviceState(currentState)

        val reasons = mutableListOf<String>()
        var fusedScore = rawRiskScore

        val hasIngress = nodes.any { it.type == NodeType.COMMUNICATION_INGRESS }
        val ingressNode = nodes.lastOrNull { it.type == NodeType.COMMUNICATION_INGRESS }
        val hasCall = currentState.isCallActive || nodes.any { 
            it.type == NodeType.DEVICE_ANOMALY && (it.details["call_active"] as? Boolean == true) 
        }
        val hasScreenShare = currentState.isScreenSharingActive || nodes.any { 
            it.type == NodeType.DEVICE_ANOMALY && (it.details["screenshare_active"] as? Boolean == true) 
        }

        var detectedType = "NORMAL_TRANSACTION"

        // 1. Digital Arrest Workflow: Active Call + Pretext + High Value Transfer
        if (hasCall && hasIngress) {
            fusedScore += 50f
            detectedType = "DIGITAL_ARREST_WORKFLOW"
            reasons.add("Active phone call detected concurrently with a recent legal/authority threat message (${ingressNode?.category}).")
            reasons.add("Scammers isolate victims on phone/video calls to prevent them from consulting family or bank staff.")
        } else if (hasCall && amount != null && amount >= 5000.0) {
            fusedScore += 35f
            reasons.add("High-value payment attempted during an ongoing phone call.")
        }

        // 2. Remote Takeover Workflow (ATS / AnyDesk)
        if (hasScreenShare) {
            fusedScore += 55f
            detectedType = "REMOTE_TAKEOVER_WORKFLOW"
            reasons.add("Active screen-sharing or remote desktop session detected. Scammers use AnyDesk/RustDesk to view credentials.")
        }

        // 3. Reverse-UPI Trap
        if (isReverseUpi) {
            fusedScore += 45f
            detectedType = "REVERSE_UPI_TRAP"
            reasons.add("Payment link promises a refund/cashback but is structurally configured to DEBIT your account.")
        }

        val activeVectors = listOfNotNull(
            if (hasIngress) "INGRESS_PRETEXT" else null,
            if (hasCall || hasScreenShare) "DEVICE_ANOMALY" else null,
            "PAYMENT_INTENT"
        )

        val finalScore = fusedScore.coerceIn(0f, 100f)
        return WorkflowRiskProfile(
            compositeScore = finalScore,
            isWorkflowScam = finalScore >= 75f,
            detectedWorkflowType = detectedType,
            explainableReasons = reasons,
            activeVectorsCount = activeVectors.size
        )
    }

    @Synchronized
    private fun pruneExpiredNodes() {
        val now = System.currentTimeMillis()
        nodes.removeAll { now - it.timestampMs > slidingWindowMs }
    }
}
