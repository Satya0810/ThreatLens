package com.safeqr.scanner.analysis.ingress

/**
 * SmishingPatternMatcher — On-Device Social Engineering & Pretext Classifier
 *
 * Implements Cialdini's Persuasion Spectrum on sanitized incoming communication
 * (SMS, WhatsApp, Telegram notifications) to detect emerging financial fraud lures.
 */
object SmishingPatternMatcher {

    enum class PretextCategory(val displayName: String, val severityLevel: Int) {
        DIGITAL_ARREST("Digital Arrest / Law Enforcement Coercion", 3),
        ELECTRICITY_CUT("Utility Power Disconnection Scam", 2),
        KYC_SUSPENSION("Banking / KYC Account Suspension", 2),
        REVERSE_UPI("Reverse-UPI Cashback/Refund Lure", 3),
        TASK_SCAM("Part-Time Job / Staking Scam", 1),
        BENIGN("Standard Non-Threat Communication", 0)
    }

    data class PretextDetectionResult(
        val category: PretextCategory,
        val confidence: Float,
        val matchedKeywords: List<String>,
        val explanation: String,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val DIGITAL_ARREST_KEYWORDS = listOf(
        "cbi", "central bureau of investigation", "enforcement directorate", "ed officer",
        "narcotics", "ncb", "mumbai police", "delhi police", "cyber cell", "cyber crime",
        "arrest warrant", "court notice", "digital arrest", "customs parcel", "illegal parcel",
        "trai", "sim card block", "supreme court", "money laundering"
    )

    private val ELECTRICITY_CUT_KEYWORDS = listOf(
        "electricity", "power cut", "power disconnected", "power will be disconnected",
        "electricity office", "bill unpaid", "tonight at 9:30", "tonight at 8:30",
        "contact officer", "update bill", "electric officer", "electricity board"
    )

    private val KYC_SUSPENSION_KEYWORDS = listOf(
        "kyc pending", "kyc suspended", "account suspended", "account blocked",
        "pan not updated", "link pan", "link aadhaar", "yono blocked",
        "netbanking deactivated", "immediate update", "within 24 hours"
    )

    private val REVERSE_UPI_KEYWORDS = listOf(
        "click to receive", "scan to receive", "receive cashback", "claim refund",
        "lottery winner", "amount credited to you", "enter pin to accept",
        "olx buyer", "payment received claim"
    )

    private val TASK_SCAM_KEYWORDS = listOf(
        "part time job", "earn daily", "youtube like", "hotel review",
        "daily income", "work from home", "telegram task", "crypto staking"
    )

    /**
     * Evaluates sanitized text and returns the detected scam pretext.
     */
    fun evaluate(sanitizedText: String): PretextDetectionResult {
        val lower = sanitizedText.lowercase()

        // 1. Digital Arrest Check
        val matchedArrest = DIGITAL_ARREST_KEYWORDS.filter { lower.contains(it) }
        if (matchedArrest.size >= 2 || (matchedArrest.size == 1 && (lower.contains("arrest") || lower.contains("warrant") || lower.contains("parcel")))) {
            return PretextDetectionResult(
                category = PretextCategory.DIGITAL_ARREST,
                confidence = 0.94f,
                matchedKeywords = matchedArrest,
                explanation = "Presents false legal authority or arrest threats to coerce compliance."
            )
        }

        // 2. Reverse UPI Check
        val matchedReverseUpi = REVERSE_UPI_KEYWORDS.filter { lower.contains(it) }
        if (matchedReverseUpi.isNotEmpty()) {
            return PretextDetectionResult(
                category = PretextCategory.REVERSE_UPI,
                confidence = 0.91f,
                matchedKeywords = matchedReverseUpi,
                explanation = "Frames an outgoing transaction as an incoming refund or reward."
            )
        }

        // 3. Electricity Disconnection Check
        val matchedElectricity = ELECTRICITY_CUT_KEYWORDS.filter { lower.contains(it) }
        if (matchedElectricity.size >= 2 || (matchedElectricity.size == 1 && (lower.contains("power") || lower.contains("disconnected")))) {
            return PretextDetectionResult(
                category = PretextCategory.ELECTRICITY_CUT,
                confidence = 0.88f,
                matchedKeywords = matchedElectricity,
                explanation = "Creates false urgency by threatening power cutoff."
            )
        }

        // 4. KYC / Account Suspension Check
        val matchedKyc = KYC_SUSPENSION_KEYWORDS.filter { lower.contains(it) }
        if (matchedKyc.size >= 2) {
            return PretextDetectionResult(
                category = PretextCategory.KYC_SUSPENSION,
                confidence = 0.87f,
                matchedKeywords = matchedKyc,
                explanation = "Falsely threatens banking lockout to force urgent action."
            )
        }

        // 5. Task / Part-time Scam Check
        val matchedTask = TASK_SCAM_KEYWORDS.filter { lower.contains(it) }
        if (matchedTask.size >= 2) {
            return PretextDetectionResult(
                category = PretextCategory.TASK_SCAM,
                confidence = 0.82f,
                matchedKeywords = matchedTask,
                explanation = "Lures victim into fraudulent upfront deposit tasks."
            )
        }

        return PretextDetectionResult(
            category = PretextCategory.BENIGN,
            confidence = 0.10f,
            matchedKeywords = emptyList(),
            explanation = "Normal non-coercive message."
        )
    }
}
