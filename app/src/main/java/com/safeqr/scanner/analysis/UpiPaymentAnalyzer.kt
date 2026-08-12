package com.safeqr.scanner.analysis

import android.content.Context
import com.safeqr.scanner.data.UpiGuardPreferences
import com.safeqr.scanner.data.model.SafetyStatus
import java.util.Calendar

/**
 * Advanced UPI / Banking QR Code Fraud Detection Engine
 *
 * Performs deep heuristic analysis on UPI payment QR codes to detect
 * scams, impersonation, social engineering, and structural anomalies.
 *
 * Inspired by ML-based UPI fraud detection research but implemented
 * as ThreatLens-native on-device analysis with zero network dependency.
 */
object UpiPaymentAnalyzer {

    // ══════════════════════════════════════════════════════════════════
    //  DATA MODELS
    // ══════════════════════════════════════════════════════════════════

    enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    data class UpiFlag(
        val id: String,
        val severity: RiskLevel,
        val emoji: String,
        val title: String,
        val description: String,
        val scorePenalty: Float
    )

    data class UpiAnalysisResult(
        val riskScore: Float,           // 0 (safe) → 100 (dangerous)
        val safetyStatus: SafetyStatus,
        val riskLevel: RiskLevel,
        val flags: List<UpiFlag>,
        val payeeVerified: Boolean,
        val vpaHandle: String?,
        val handleBankName: String?,
        val summary: String,
        val recommendations: List<String>,
        val mlConfidence: Float = 0f,    // ML model's fraud probability
        // Extracted UPI fields for display
        val payeeName: String?,
        val payeeVpa: String?,
        val amount: Double?,
        val transactionNote: String?,
        val currency: String?,
        val merchantCode: String?,
        // ── UPI Guard fields ──
        val isFirstTimePayee: Boolean = true,
        val previousPayCount: Int = 0,
        val dailyLimitStatus: UpiGuardPreferences.DailyLimitStatus? = null
    )

    // ══════════════════════════════════════════════════════════════════
    //  KNOWN UPI HANDLE DATABASE (50+ handles mapped to bank names)
    // ══════════════════════════════════════════════════════════════════

    val KNOWN_UPI_HANDLES: Map<String, String> = mapOf(
        // ── PhonePe ──
        "ybl" to "PhonePe (Yes Bank)",
        "ibl" to "PhonePe (ICICI Bank)",
        "axl" to "PhonePe (Axis Bank)",
        "sbi" to "PhonePe (SBI)",
        "phon" to "PhonePe",
        // ── Google Pay ──
        "okicici" to "Google Pay (ICICI)",
        "okhdfcbank" to "Google Pay (HDFC)",
        "okaxis" to "Google Pay (Axis)",
        "oksbi" to "Google Pay (SBI)",
        "gpay" to "Google Pay",
        // ── Paytm ──
        "paytm" to "Paytm",
        "ptyes" to "Paytm (Yes Bank)",
        "pthdfc" to "Paytm (HDFC)",
        "ptaxis" to "Paytm (Axis)",
        "ptsbi" to "Paytm (SBI)",
        // ── BHIM / Banks ──
        "upi" to "BHIM UPI",
        "bhim" to "BHIM UPI",
        "barodampay" to "Bank of Baroda",
        "unionbankofindia" to "Union Bank",
        "unionbank" to "Union Bank",
        "cboi" to "Central Bank of India",
        "csbpay" to "CSB Bank",
        "dbs" to "DBS Bank",
        "dlb" to "Dhanalakshmi Bank",
        "federal" to "Federal Bank",
        "freecharge" to "Freecharge",
        "hdfcbank" to "HDFC Bank",
        "hsbc" to "HSBC Bank",
        "icici" to "ICICI Bank",
        "idbi" to "IDBI Bank",
        "idfc" to "IDFC First Bank",
        "idfcbank" to "IDFC First Bank",
        "indianbank" to "Indian Bank",
        "indus" to "IndusInd Bank",
        "iob" to "Indian Overseas Bank",
        "jkb" to "J&K Bank",
        "kotak" to "Kotak Mahindra Bank",
        "kbl" to "Karnataka Bank",
        "kvb" to "Karur Vysya Bank",
        "lvb" to "Lakshmi Vilas Bank",
        "mahb" to "Bank of Maharashtra",
        "pnb" to "Punjab National Bank",
        "psb" to "Punjab & Sind Bank",
        "rbl" to "RBL Bank",
        "sbin" to "State Bank of India",
        "sc" to "Standard Chartered",
        "scb" to "Standard Chartered",
        "syndicate" to "Syndicate Bank",
        "tmb" to "Tamilnad Mercantile Bank",
        "ubi" to "United Bank of India",
        "uboi" to "Union Bank of India",
        "uco" to "UCO Bank",
        "vijb" to "Vijaya Bank",
        "yesbank" to "Yes Bank",
        "cnrb" to "Canara Bank",
        "canrabank" to "Canara Bank",
        "equitas" to "Equitas Small Finance Bank",
        // ── Wallets ──
        "apl" to "Amazon Pay",
        "rapl" to "Amazon Pay",
        "waicici" to "WhatsApp Pay (ICICI)",
        "wahdfcbank" to "WhatsApp Pay (HDFC)",
        "wasbi" to "WhatsApp Pay (SBI)",
        "waaxis" to "WhatsApp Pay (Axis)",
        "jupiteraxis" to "Jupiter (Axis)",
        "slice" to "Slice",
        "niyoicici" to "Niyo",
        "cred" to "CRED",
        "supermoneyicici" to "SuperMoney",
        "mobikwik" to "MobiKwik",
        "airtel" to "Airtel Payments Bank",
        "airtelmoney" to "Airtel Money",
        "jio" to "Jio Payments Bank",
        "postbank" to "India Post Payments Bank",
        "ippb" to "India Post Payments Bank"
    )

    // ══════════════════════════════════════════════════════════════════
    //  RAPID-FIRE DETECTION (in-memory timestamps)
    // ══════════════════════════════════════════════════════════════════

    private val recentUpiScanTimestamps = mutableListOf<Long>()
    private const val RAPID_FIRE_WINDOW_MS = 5 * 60 * 1000L // 5 minutes
    private const val RAPID_FIRE_THRESHOLD = 3

    private fun recordScanAndCheckRapidFire(): Int {
        val now = System.currentTimeMillis()
        // Clean old entries
        recentUpiScanTimestamps.removeAll { now - it > RAPID_FIRE_WINDOW_MS }
        recentUpiScanTimestamps.add(now)
        return recentUpiScanTimestamps.size
    }

    // ══════════════════════════════════════════════════════════════════
    //  MERCHANT CATEGORY CODE (MCC) DATABASE
    // ══════════════════════════════════════════════════════════════════

    data class MccCategory(val name: String, val riskLevel: RiskLevel, val emoji: String)

    private val RISKY_MCC_RANGES: Map<IntRange, MccCategory> = mapOf(
        (7800..7999) to MccCategory("Gambling / Lottery", RiskLevel.HIGH, "🎰"),
        (5967..5967) to MccCategory("Adult / Inbound Telemarketing", RiskLevel.HIGH, "🔞"),
        (6051..6051) to MccCategory("Crypto / Non-FI Money Orders", RiskLevel.MEDIUM, "₿"),
        (6211..6211) to MccCategory("Securities / Brokers", RiskLevel.MEDIUM, "📈"),
        (5962..5962) to MccCategory("Direct Marketing / Travel", RiskLevel.LOW, "✈️"),
        (5966..5966) to MccCategory("Direct Marketing / Outbound", RiskLevel.MEDIUM, "📞"),
        (7273..7273) to MccCategory("Dating / Escort Services", RiskLevel.HIGH, "💋"),
        (7995..7995) to MccCategory("Gambling / Betting", RiskLevel.HIGH, "🎲"),
        (5912..5912) to MccCategory("Drug / Pharmacy", RiskLevel.LOW, "💊")
    )

    private fun lookupMcc(mccStr: String): MccCategory? {
        val code = mccStr.toIntOrNull() ?: return null
        return RISKY_MCC_RANGES.entries.firstOrNull { code in it.key }?.value
    }

    // ══════════════════════════════════════════════════════════════════
    //  TRANSACTION NOTE NLP PATTERNS
    // ══════════════════════════════════════════════════════════════════

    private val SENSITIVE_INFO_PATTERNS = listOf(
        Regex("\\b\\d{9,18}\\b"),                        // Account numbers (9-18 digits)
        Regex("\\b[A-Z]{4}0[A-Z0-9]{6}\\b"),              // IFSC codes
        Regex("\\b(otp|pin|cvv|password|passcode)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b\\d{4,6}\\s*(is your|otp|code)\\b", RegexOption.IGNORE_CASE),
        Regex("(enter|share|send|give).{0,20}(otp|pin|cvv|password)", RegexOption.IGNORE_CASE)
    )

    private val REFUND_SCAM_KEYWORDS = listOf(
        "refund", "credited to", "your amount", "received from",
        "payment received", "money sent", "transfer successful",
        "amount credited", "cashback of", "bonus credited"
    )

    // ══════════════════════════════════════════════════════════════════
    //  FRAUD KEYWORD DATABASES
    // ══════════════════════════════════════════════════════════════════

    private val GOVT_IMPERSONATION_KEYWORDS = listOf(
        "income tax", "incometax", "it department", "it dept",
        "rbi", "reserve bank", "uidai", "aadhaar", "aadhar",
        "police", "cyber cell", "cybercrime", "court", "tribunal",
        "customs", "excise", "gst", "tax department", "govt",
        "government", "ministry", "municipal", "corporation",
        "electricity board", "water board", "transport",
        "passport", "immigration", "enforcement directorate",
        "cbi", "nia", "sebi", "insurance regulatory", "irdai",
        "epfo", "provident fund", "labour department"
    )

    private val BANK_IMPERSONATION_KEYWORDS = listOf(
        "sbi official", "sbi support", "sbi helpdesk", "sbi help",
        "hdfc official", "hdfc support", "hdfc helpdesk",
        "icici official", "icici support", "icici helpdesk",
        "axis official", "axis support", "axis helpdesk",
        "kotak official", "pnb official", "bob official",
        "bank manager", "bank officer", "branch manager",
        "customer care", "customer support", "bank helpline",
        "account verification", "kyc verification", "kyc update",
        "account suspended", "account blocked", "account frozen"
    )

    private val URGENCY_KEYWORDS = listOf(
        "urgent", "urgently", "immediately", "instant", "asap",
        "fine", "penalty", "challan", "fee", "charge",
        "blocked", "suspended", "frozen", "deactivated", "closed",
        "kyc", "link aadhaar", "link aadhar", "verify now",
        "last chance", "deadline", "expire", "expiring", "within 24",
        "legal action", "arrest", "warrant", "fir", "complaint",
        "refund", "cashback processing", "settlement",
        "pay now or", "pay immediately", "failure to pay",
        "mandatory", "compulsory", "required payment"
    )

    private val PRIZE_SCAM_KEYWORDS = listOf(
        "winner", "won", "congratulations", "congrats",
        "prize", "lottery", "lucky draw", "lucky winner",
        "reward", "bonus", "cashback", "cash back",
        "gift card", "gift voucher", "free", "claim",
        "selected", "chosen", "special offer", "exclusive",
        "giveaway", "give away", "spin", "jackpot"
    )

    private val KNOWN_BRAND_NAMES = listOf(
        "amazon", "flipkart", "myntra", "swiggy", "zomato",
        "paytm", "phonepe", "google pay", "gpay", "whatsapp",
        "uber", "ola", "rapido", "dunzo", "bigbasket",
        "jiomart", "reliance", "tata", "netflix", "hotstar",
        "airtel", "vodafone", "jio", "bsnl", "vi"
    )

    // ══════════════════════════════════════════════════════════════════
    //  CORE ANALYSIS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Performs deep fraud analysis on a UPI QR code.
     * @param rawContent The raw `upi://pay?...` string
     * @param actionData The parsed actionData map from QrDataParser
     */
    fun analyze(
        rawContent: String,
        actionData: Map<String, String>,
        context: Context? = null
    ): UpiAnalysisResult {
        val flags = mutableListOf<UpiFlag>()
        val recommendations = mutableListOf<String>()
        var riskScore = 0f

        // ── Extract fields ──────────────────────────────────────────
        val payeeName = actionData["payeeName"]
        val payeeVpa = actionData["payeeAddress"]
        val amountStr = actionData["amount"]
        val note = actionData["note"]
        val currency = actionData["currency"]
        val merchantCode = actionData["merchantCode"]
        val mode = actionData["mode"]

        val amount = amountStr?.toDoubleOrNull()

        // ── 1. VPA FORMAT VALIDATION ────────────────────────────────
        val vpaHandle = payeeVpa?.substringAfter("@", "")?.lowercase()?.trim()
        val vpaUsername = payeeVpa?.substringBefore("@", "")?.trim()
        var payeeVerified = false
        var handleBankName: String? = null

        if (payeeVpa.isNullOrBlank()) {
            flags.add(UpiFlag(
                id = "VPA_MISSING",
                severity = RiskLevel.CRITICAL,
                emoji = "🚨",
                title = "No VPA Address",
                description = "QR code has no payee VPA — this is structurally invalid and highly suspicious",
                scorePenalty = 40f
            ))
            riskScore += 40f
            recommendations.add("This QR code is malformed. Do NOT pay.")
        } else if (!payeeVpa.contains("@") || vpaHandle.isNullOrBlank()) {
            flags.add(UpiFlag(
                id = "VPA_INVALID_FORMAT",
                severity = RiskLevel.HIGH,
                emoji = "⚠️",
                title = "Invalid VPA Format",
                description = "VPA '$payeeVpa' does not follow the standard name@handle format",
                scorePenalty = 30f
            ))
            riskScore += 30f
            recommendations.add("Verify this payment address independently before paying.")
        } else {
            // Check against known handles
            handleBankName = KNOWN_UPI_HANDLES[vpaHandle]
            if (handleBankName != null) {
                payeeVerified = true
            } else {
                flags.add(UpiFlag(
                    id = "VPA_UNKNOWN_HANDLE",
                    severity = RiskLevel.MEDIUM,
                    emoji = "🔍",
                    title = "Unknown UPI Handle",
                    description = "@$vpaHandle is not a recognized bank/wallet handle",
                    scorePenalty = 15f
                ))
                riskScore += 15f
                recommendations.add("The UPI handle '@$vpaHandle' is not from a major bank. Verify the payee.")
            }
        }

        // ── 2. VPA USERNAME ANALYSIS ────────────────────────────────
        if (!vpaUsername.isNullOrBlank()) {
            // Check for overly random/gibberish usernames
            val entropy = calculateEntropy(vpaUsername)
            if (entropy > 3.5 && vpaUsername.length > 8) {
                flags.add(UpiFlag(
                    id = "VPA_RANDOM_USERNAME",
                    severity = RiskLevel.MEDIUM,
                    emoji = "🎲",
                    title = "Random-Looking VPA",
                    description = "Username '$vpaUsername' appears auto-generated or random",
                    scorePenalty = 0f
                ))
            }

            // Check for numeric-only VPA (often throwaway/mule accounts)
            if (vpaUsername.all { it.isDigit() } && vpaUsername.length >= 8) {
                flags.add(UpiFlag(
                    id = "VPA_NUMERIC_ONLY",
                    severity = RiskLevel.MEDIUM,
                    emoji = "🔢",
                    title = "Numeric-Only VPA",
                    description = "Pure number-based VPA addresses are often linked to mule accounts",
                    scorePenalty = 0f
                ))
            }
        }

        // ── 3. AMOUNT HEURISTICS ────────────────────────────────────
        if (amount != null) {
            if (amount > 100000) {
                flags.add(UpiFlag(
                    id = "AMOUNT_VERY_HIGH",
                    severity = RiskLevel.CRITICAL,
                    emoji = "💰",
                    title = "Very High Amount: ₹${String.format("%,.0f", amount)}",
                    description = "Amounts over ₹1,00,000 are extremely unusual for QR payments",
                    scorePenalty = 35f
                ))
                riskScore += 35f
                recommendations.add("₹${String.format("%,.0f", amount)} is an extremely high amount. Triple-check the payee.")
            } else if (amount > 50000) {
                flags.add(UpiFlag(
                    id = "AMOUNT_HIGH",
                    severity = RiskLevel.HIGH,
                    emoji = "💸",
                    title = "High Amount: ₹${String.format("%,.0f", amount)}",
                    description = "Amounts over ₹50,000 carry elevated risk via QR code",
                    scorePenalty = 20f
                ))
                riskScore += 20f
                recommendations.add("High-value QR payments are risky. Verify the payee identity first.")
            } else if (amount > 10000) {
                flags.add(UpiFlag(
                    id = "AMOUNT_ELEVATED",
                    severity = RiskLevel.MEDIUM,
                    emoji = "💳",
                    title = "Elevated Amount: ₹${String.format("%,.0f", amount)}",
                    description = "Amount over ₹10,000 — exercise caution",
                    scorePenalty = 0f
                ))
            }

            // Round amount check
            if (amount >= 1000 && amount % 1000.0 == 0.0 && amount >= 10000) {
                flags.add(UpiFlag(
                    id = "AMOUNT_SUSPICIOUSLY_ROUND",
                    severity = RiskLevel.LOW,
                    emoji = "🔵",
                    title = "Suspiciously Round Amount",
                    description = "₹${String.format("%,.0f", amount)} is a perfectly round number — common in scam QRs",
                    scorePenalty = 0f
                ))
            }

            // Micro-amount probing
            if (amount in 0.01..5.0) {
                flags.add(UpiFlag(
                    id = "AMOUNT_MICRO_PROBE",
                    severity = RiskLevel.HIGH,
                    emoji = "🔬",
                    title = "Micro-Amount Probe: ₹${String.format("%.0f", amount)}",
                    description = "Very small amounts (₹1–₹5) are often used to test if your UPI is active before a larger scam",
                    scorePenalty = 20f
                ))
                riskScore += 20f
                recommendations.add("Micro-payments are often used to verify your UPI is active. Be alert for follow-up scam calls.")
            }
        } else if (amountStr == null) {
            // No amount specified — open QR
            flags.add(UpiFlag(
                id = "AMOUNT_OPEN",
                severity = RiskLevel.MEDIUM,
                emoji = "❓",
                title = "Open Amount QR",
                description = "No fixed amount — you'll be asked to enter it manually. Verify the correct amount with the payee.",
                scorePenalty = 0f
            ))
            recommendations.add("This QR has no fixed amount. Double-check the amount you enter.")
        }

        // ── 4. PAYEE NAME ANALYSIS ──────────────────────────────────
        val nameForAnalysis = (payeeName ?: "").lowercase().trim()
        val noteForAnalysis = (note ?: "").lowercase().trim()
        val combinedText = "$nameForAnalysis $noteForAnalysis"

        // Government impersonation
        val govtMatches = GOVT_IMPERSONATION_KEYWORDS.filter { combinedText.contains(it) }
        if (govtMatches.isNotEmpty()) {
            flags.add(UpiFlag(
                id = "GOVT_IMPERSONATION",
                severity = RiskLevel.CRITICAL,
                emoji = "🏛️",
                title = "Government Impersonation Detected",
                description = "Keywords found: ${govtMatches.take(3).joinToString(", ")}. Government agencies NEVER collect payments via random UPI QR codes.",
                scorePenalty = 40f
            ))
            riskScore += 40f
            recommendations.add("⚠️ Government agencies NEVER request UPI payments via QR codes. This is a SCAM.")
        }

        // Bank impersonation
        val bankMatches = BANK_IMPERSONATION_KEYWORDS.filter { combinedText.contains(it) }
        if (bankMatches.isNotEmpty()) {
            flags.add(UpiFlag(
                id = "BANK_IMPERSONATION",
                severity = RiskLevel.CRITICAL,
                emoji = "🏦",
                title = "Bank Impersonation Detected",
                description = "Keywords found: ${bankMatches.take(3).joinToString(", ")}. Banks never ask for QR payments for KYC or account issues.",
                scorePenalty = 35f
            ))
            riskScore += 35f
            recommendations.add("⚠️ Banks NEVER send QR codes for KYC, account verification, or support. Contact your bank directly.")
        }

        // Urgency/fear tactics
        val urgencyMatches = URGENCY_KEYWORDS.filter { combinedText.contains(it) }
        if (urgencyMatches.isNotEmpty()) {
            val severity = if (urgencyMatches.size >= 2) RiskLevel.HIGH else RiskLevel.MEDIUM
            val penalty = if (urgencyMatches.size >= 2) 25f else 15f
            flags.add(UpiFlag(
                id = "URGENCY_TACTICS",
                severity = severity,
                emoji = "⏰",
                title = "Urgency/Fear Tactics Detected",
                description = "Pressure keywords: ${urgencyMatches.take(3).joinToString(", ")}. Scammers create urgency to prevent you from thinking clearly.",
                scorePenalty = penalty
            ))
            riskScore += penalty
            recommendations.add("Scammers use urgency to pressure you. Take your time and verify independently.")
        }

        // Prize/lottery scam
        val prizeMatches = PRIZE_SCAM_KEYWORDS.filter { combinedText.contains(it) }
        if (prizeMatches.isNotEmpty()) {
            flags.add(UpiFlag(
                id = "PRIZE_SCAM",
                severity = RiskLevel.HIGH,
                emoji = "🎰",
                title = "Prize/Lottery Scam Pattern",
                description = "Keywords: ${prizeMatches.take(3).joinToString(", ")}. Legitimate prizes never require you to PAY money.",
                scorePenalty = 30f
            ))
            riskScore += 30f
            recommendations.add("If you need to PAY to receive a prize, it's a scam. Legitimate rewards are never collected via UPI.")
        }

        // ── 5. NAME vs VPA MISMATCH ─────────────────────────────────
        if (!payeeName.isNullOrBlank() && !payeeVpa.isNullOrBlank()) {
            val matchedBrand = KNOWN_BRAND_NAMES.find { nameForAnalysis.contains(it) }
            if (matchedBrand != null) {
                val vpaLower = payeeVpa.lowercase()
                val brandInVpa = vpaLower.contains(matchedBrand.replace(" ", ""))
                if (!brandInVpa) {
                    flags.add(UpiFlag(
                        id = "NAME_VPA_MISMATCH",
                        severity = RiskLevel.HIGH,
                        emoji = "🎭",
                        title = "Brand Name Mismatch",
                        description = "Payee claims to be '$matchedBrand' but VPA ($payeeVpa) doesn't match. Likely impersonation.",
                        scorePenalty = 25f
                    ))
                    riskScore += 25f
                    recommendations.add("The payee name contains '$matchedBrand' but the VPA doesn't match. Verify directly with $matchedBrand.")
                }
            }
        }

        // ── 6. COLLECT REQUEST DETECTION ────────────────────────────
        if (mode?.lowercase() == "01" || mode?.lowercase() == "collect") {
            flags.add(UpiFlag(
                id = "COLLECT_REQUEST",
                severity = RiskLevel.HIGH,
                emoji = "📥",
                title = "Collect Request QR",
                description = "This QR initiates a COLLECT request — the payee is asking to PULL money from your account",
                scorePenalty = 20f
            ))
            riskScore += 20f
            recommendations.add("This is a collect request. Money will be debited from YOUR account. Only approve if you initiated this.")
        }

        // ── 7. CURRENCY VALIDATION ──────────────────────────────────
        if (!currency.isNullOrBlank() && currency.uppercase() != "INR") {
            flags.add(UpiFlag(
                id = "CURRENCY_MISMATCH",
                severity = RiskLevel.MEDIUM,
                emoji = "💱",
                title = "Non-INR Currency: $currency",
                description = "UPI transactions in India should use INR. Currency '$currency' is unexpected.",
                scorePenalty = 10f
            ))
            riskScore += 10f
        }

        // ── 8. MISSING PAYEE NAME ───────────────────────────────────
        if (payeeName.isNullOrBlank()) {
            flags.add(UpiFlag(
                id = "NO_PAYEE_NAME",
                severity = RiskLevel.MEDIUM,
                emoji = "👤",
                title = "No Payee Name",
                description = "Legitimate merchants always include their business name in the QR code",
                scorePenalty = 10f
            ))
            riskScore += 10f
            recommendations.add("The QR has no payee name. Verify who you're paying before proceeding.")
        }

        // ── 9. RAPID-FIRE DETECTION ─────────────────────────────────
        val recentCount = recordScanAndCheckRapidFire()
        if (recentCount >= RAPID_FIRE_THRESHOLD) {
            flags.add(UpiFlag(
                id = "RAPID_FIRE_PAYMENT",
                severity = RiskLevel.HIGH,
                emoji = "⚡",
                title = "Rapid-Fire UPI Scans ($recentCount in 5 min)",
                description = "Multiple UPI QR codes scanned in quick succession. This is common in social engineering attacks where scammers pressure victims with repeated payment requests.",
                scorePenalty = 25f
            ))
            riskScore += 25f
            recommendations.add("You've scanned $recentCount UPI QRs in 5 minutes. Slow down and verify each payment independently.")
        }

        // ── 10. MCC VALIDATION ──────────────────────────────────────
        if (!merchantCode.isNullOrBlank()) {
            val mccCategory = lookupMcc(merchantCode)
            if (mccCategory != null) {
                val mccPenalty = when (mccCategory.riskLevel) {
                    RiskLevel.HIGH -> 20f
                    RiskLevel.CRITICAL -> 30f
                    else -> 0f
                }
                if (mccPenalty > 0f) {
                    flags.add(UpiFlag(
                        id = "MCC_RISKY_CATEGORY",
                        severity = mccCategory.riskLevel,
                        emoji = mccCategory.emoji,
                        title = "Risky Merchant: ${mccCategory.name}",
                        description = "MCC $merchantCode maps to '${mccCategory.name}' — a category associated with higher fraud risk.",
                        scorePenalty = mccPenalty
                    ))
                    riskScore += mccPenalty
                    recommendations.add("This payment is categorized as '${mccCategory.name}'. Exercise extra caution.")
                }
            }
            // Personal VPA with merchant code = suspicious
            if (vpaUsername != null && vpaUsername.all { it.isDigit() || it.isLetter() } && vpaUsername.length <= 10 && !nameForAnalysis.contains("merchant") && !nameForAnalysis.contains("shop") && !nameForAnalysis.contains("store")) {
                // Only flag if the name looks personal but has MCC
                val personalPatterns = listOf(Regex("^[a-z]+\\d{0,4}$"), Regex("^\\d{10}$"))
                if (personalPatterns.any { it.matches(vpaUsername.lowercase()) }) {
                    flags.add(UpiFlag(
                        id = "MCC_PERSONAL_MISMATCH",
                        severity = RiskLevel.MEDIUM,
                        emoji = "🏪",
                        title = "Merchant Code on Personal VPA",
                        description = "Personal-looking VPA '$payeeVpa' has a merchant code ($merchantCode) — unusual for individual accounts.",
                        scorePenalty = 10f
                    ))
                    riskScore += 10f
                }
            }
        }

        // ── 11. SMART TRANSACTION NOTE NLP ──────────────────────────
        if (!noteForAnalysis.isBlank()) {
            // Check for sensitive info requests
            val sensitiveMatches = SENSITIVE_INFO_PATTERNS.filter { it.containsMatchIn(noteForAnalysis) }
            if (sensitiveMatches.isNotEmpty()) {
                flags.add(UpiFlag(
                    id = "NOTE_SENSITIVE_INFO",
                    severity = RiskLevel.CRITICAL,
                    emoji = "🔑",
                    title = "Sensitive Info in Transaction Note",
                    description = "The note contains requests for OTP, PIN, password, or account numbers. Legitimate transactions NEVER include these.",
                    scorePenalty = 35f
                ))
                riskScore += 35f
                recommendations.add("⚠️ NEVER share OTP, PIN, or account numbers via UPI transaction notes. This is a SCAM.")
            }

            // Check for refund-disguised collect scams
            val refundMatches = REFUND_SCAM_KEYWORDS.filter { noteForAnalysis.contains(it) }
            if (refundMatches.isNotEmpty()) {
                flags.add(UpiFlag(
                    id = "NOTE_REFUND_SCAM",
                    severity = RiskLevel.HIGH,
                    emoji = "🔄",
                    title = "Refund Scam Pattern in Note",
                    description = "Note mentions '${refundMatches.first()}' — scammers disguise collect requests as refunds. You will LOSE money, not receive it.",
                    scorePenalty = 25f
                ))
                riskScore += 25f
                recommendations.add("If someone says you'll 'receive' money but asks you to scan a QR, it's a collect request scam.")
            }
        }

        // ── 12. TIME-OF-DAY ENHANCED WARNING ────────────────────────
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isLateNight = hour in 23..23 || hour in 0..5
        val lateNightEnabled = context?.let { UpiGuardPreferences.isLateNightWarningEnabled(it) } ?: true
        if (isLateNight && lateNightEnabled && riskScore >= 20f) {
            flags.add(UpiFlag(
                id = "LATE_NIGHT_HIGH_RISK",
                severity = RiskLevel.HIGH,
                emoji = "🌙",
                title = "Late-Night High-Risk Payment",
                description = "UPI payments between 11 PM – 6 AM with existing risk indicators are 3× more likely to be fraudulent.",
                scorePenalty = 10f
            ))
            riskScore += 10f
            recommendations.add("Late-night UPI scams are common. Wait until morning and verify with someone you trust.")
        }

        // ── 13. VPA REPUTATION CHECK ────────────────────────────────
        var isFirstTimePayee = true
        var previousPayCount = 0
        if (context != null && !payeeVpa.isNullOrBlank()) {
            val vpaRecord = UpiGuardPreferences.getVpaHistory(context, payeeVpa)
            if (vpaRecord != null) {
                isFirstTimePayee = false
                previousPayCount = vpaRecord.payCount
                if (vpaRecord.isBlocked) {
                    flags.add(UpiFlag(
                        id = "VPA_PREVIOUSLY_REPORTED",
                        severity = RiskLevel.CRITICAL,
                        emoji = "🚫",
                        title = "Previously Reported VPA",
                        description = "You previously reported '$payeeVpa' as fraudulent. This VPA is in your blocklist.",
                        scorePenalty = 40f
                    ))
                    riskScore += 40f
                    recommendations.add("This VPA is in your blocklist. Do NOT proceed with this payment.")
                } else if (vpaRecord.payCount > 0) {
                    // Trusted payee — reduce risk
                    val trustBonus = (vpaRecord.payCount * 5f).coerceAtMost(15f)
                    riskScore = (riskScore - trustBonus).coerceAtLeast(0f)
                    flags.add(UpiFlag(
                        id = "VPA_TRUSTED_PAYEE",
                        severity = RiskLevel.LOW,
                        emoji = "✅",
                        title = "Trusted Payee (${vpaRecord.payCount} previous payments)",
                        description = "You've successfully paid this VPA ${vpaRecord.payCount} time(s) before. Risk reduced.",
                        scorePenalty = -trustBonus
                    ))
                }
            } else {
                flags.add(UpiFlag(
                    id = "VPA_FIRST_TIME",
                    severity = RiskLevel.LOW,
                    emoji = "⚡",
                    title = "First-Time Payee",
                    description = "This is your first transaction with '$payeeVpa'. Verify the payee's identity before paying.",
                    scorePenalty = 0f
                ))
                recommendations.add("First-time payee detected. Double-check the VPA before paying.")
            }
        }

        // ── 14. DAILY LIMIT CHECK ───────────────────────────────────
        var dailyLimitStatus: UpiGuardPreferences.DailyLimitStatus? = null
        if (context != null) {
            dailyLimitStatus = UpiGuardPreferences.checkDailyLimit(context, amount)
            if (dailyLimitStatus.wouldExceed) {
                flags.add(UpiFlag(
                    id = "DAILY_LIMIT_EXCEEDED",
                    severity = RiskLevel.HIGH,
                    emoji = "🚧",
                    title = "Daily Limit Would Be Exceeded",
                    description = "This ₹${amount?.let { String.format("%,.0f", it) } ?: "?"} payment would push your daily total past your ₹${String.format("%,.0f", dailyLimitStatus.dailyLimit)} limit. (Spent today: ₹${String.format("%,.0f", dailyLimitStatus.spentToday)})",
                    scorePenalty = 15f
                ))
                riskScore += 15f
                recommendations.add("You've set a daily UPI limit. This payment exceeds it. Review before proceeding.")
            }
        }

        // ══════════════════════════════════════════════════════════════
        //  CALCULATE FINAL RESULTS
        // ══════════════════════════════════════════════════════════════

        riskScore = riskScore.coerceIn(0f, 100f)

        val riskLevel = when {
            riskScore >= 70f -> RiskLevel.CRITICAL
            riskScore >= 45f -> RiskLevel.HIGH
            riskScore >= 20f -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val safetyStatus = when {
            riskScore >= 60f -> SafetyStatus.MALICIOUS
            riskScore >= 25f -> SafetyStatus.CAUTION
            else -> SafetyStatus.SAFE
        }

        // Build summary
        val summary = buildSummary(flags, payeeVerified, handleBankName, riskLevel, riskScore)

        // If no flags, add positive recommendation
        if (flags.isEmpty()) {
            recommendations.add("No fraud indicators detected. This appears to be a standard UPI payment.")
        }

        return UpiAnalysisResult(
            riskScore = riskScore,
            safetyStatus = safetyStatus,
            riskLevel = riskLevel,
            flags = flags.sortedByDescending { it.scorePenalty },
            payeeVerified = payeeVerified,
            vpaHandle = vpaHandle,
            handleBankName = handleBankName,
            summary = summary,
            recommendations = recommendations,
            payeeName = payeeName,
            payeeVpa = payeeVpa,
            amount = amount,
            transactionNote = note,
            currency = currency ?: "INR",
            merchantCode = merchantCode,
            isFirstTimePayee = isFirstTimePayee,
            previousPayCount = previousPayCount,
            dailyLimitStatus = dailyLimitStatus
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  HELPER FUNCTIONS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Shannon entropy — measures randomness of a string.
     * Higher entropy = more random/suspicious for a VPA username.
     */
    private fun calculateEntropy(input: String): Double {
        if (input.isEmpty()) return 0.0
        val charCounts = input.groupingBy { it }.eachCount()
        return charCounts.values.sumOf { count ->
            val p = count.toDouble() / input.length
            -p * (Math.log(p) / Math.log(2.0))
        }
    }

    private fun buildSummary(
        flags: List<UpiFlag>,
        payeeVerified: Boolean,
        handleBankName: String?,
        riskLevel: RiskLevel,
        riskScore: Float
    ): String = buildString {
        append("🏧 UPI Payment Fraud Analysis\n\n")

        // Risk Overview
        val riskEmoji = when (riskLevel) {
            RiskLevel.CRITICAL -> "🚨"
            RiskLevel.HIGH -> "🔴"
            RiskLevel.MEDIUM -> "🟡"
            RiskLevel.LOW -> "🟢"
        }
        append("$riskEmoji Risk Level: ${riskLevel.name} (Score: ${String.format("%.0f", riskScore)}/100)\n\n")

        // VPA Trust
        if (payeeVerified && handleBankName != null) {
            append("✅ VPA Handle Verified: $handleBankName\n")
        } else {
            append("⚠️ VPA Handle: Not from a recognized bank/wallet\n")
        }

        append("\n")

        // Flags
        if (flags.isNotEmpty()) {
            append("🔍 Detected Issues (${flags.size}):\n")
            flags.sortedByDescending { it.scorePenalty }.forEach { flag ->
                append("${flag.emoji} ${flag.title}\n")
            }
        } else {
            append("✅ No fraud indicators detected.\n")
        }
    }
}
