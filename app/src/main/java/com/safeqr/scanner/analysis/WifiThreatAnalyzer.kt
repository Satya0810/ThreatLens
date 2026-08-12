package com.safeqr.scanner.analysis

import com.safeqr.scanner.data.model.SafetyStatus

/**
 * Advanced WiFi QR Code Threat Analysis Engine
 *
 * Performs deep security analysis on WiFi QR codes to detect
 * evil twins, rogue access points, encryption vulnerabilities,
 * SSID spoofing, credential exposure, and network configuration risks.
 */
object WifiThreatAnalyzer {

    // ══════════════════════════════════════════════════════════════════
    //  DATA MODELS
    // ══════════════════════════════════════════════════════════════════

    enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    enum class PasswordStrength { NONE, VERY_WEAK, WEAK, MODERATE, STRONG, EXCELLENT }

    data class WifiFlag(
        val id: String,
        val severity: RiskLevel,
        val emoji: String,
        val title: String,
        val description: String,
        val scorePenalty: Float
    )

    data class WifiAnalysisResult(
        val riskScore: Float,            // 0 (safe) → 100 (dangerous)
        val safetyStatus: SafetyStatus,
        val riskLevel: RiskLevel,
        val flags: List<WifiFlag>,
        val encryptionGrade: String,     // "A+" (WPA3), "A" (WPA2), "C" (WPA), "F" (WEP/Open)
        val encryptionName: String,      // "WPA3-SAE", "WPA2-AES", "WEP", "Open"
        val encryptionColor: Int,        // Color resource for the grade
        val passwordStrength: PasswordStrength,
        val passwordScore: Float,        // 0.0 → 1.0 (for UI meter)
        val isSpoofedSSID: Boolean,
        val summary: String,
        val recommendations: List<String>,
        // Extracted fields for display
        val ssid: String?,
        val securityType: String?,
        val isHidden: Boolean,
        val hasPassword: Boolean,
        val password: String? = null
    )

    // ══════════════════════════════════════════════════════════════════
    //  KNOWN HOTSPOT / EVIL TWIN DATABASES
    // ══════════════════════════════════════════════════════════════════

    /** Common public WiFi names that are frequently impersonated by evil twin attacks. */
    private val KNOWN_PUBLIC_HOTSPOTS = listOf(
        // Coffee Shops
        "starbucks", "starbucks wifi", "starbucks_wifi", "starbuckz",
        "costa coffee", "costa_wifi", "costa wifi",
        "cafe coffee day", "ccd wifi", "ccd_wifi",
        "barista", "barista_wifi",
        // Fast Food
        "mcdonalds", "mcdonald's", "mcdonalds wifi", "mcd free wifi",
        "burger king", "burger_king_wifi", "kfc wifi", "kfc_wifi",
        "subway wifi", "subway_wifi", "dominos wifi", "dominos_wifi",
        // Airports
        "airport", "airport wifi", "airport_wifi", "airport_free_wifi",
        "free airport wifi", "airport free internet", "airport_lounge",
        "terminal wifi", "terminal_wifi", "lounge wifi",
        // Hotels & Travel
        "hotel", "hotel wifi", "hotel_wifi", "hotel_guest", "guest_wifi",
        "hotel lobby", "hotel_lobby", "hotel_free", "resort_wifi",
        "oyo wifi", "oyo_wifi", "marriott", "marriott_wifi",
        "hilton", "hilton_wifi", "hyatt", "hyatt_wifi",
        // Shopping
        "mall wifi", "mall_wifi", "shopping_wifi", "free mall wifi",
        "reliance digital", "croma wifi", "croma_wifi",
        // Public Spaces
        "free wifi", "free_wifi", "free internet", "public wifi",
        "public_wifi", "open wifi", "open_wifi", "guest", "guest wifi",
        "visitors", "visitor_wifi", "public_internet",
        // Railway & Metro
        "railwire", "rail_wire", "railtel", "railtel_wifi",
        "metro wifi", "metro_wifi", "station wifi", "station_wifi",
        "irctc wifi", "irctc_wifi",
        // Telecom Hotspots
        "jio wifi", "jio_wifi", "jionet", "jio hotspot",
        "airtel wifi", "airtel_wifi", "airtel_hotspot",
        "bsnl wifi", "bsnl_wifi", "bsnl_hotspot",
        "vi wifi", "vi_wifi", "vodafone wifi", "vodafone_wifi",
        "idea wifi", "idea_wifi",
        // Corporate / Tech
        "google guest", "google_guest", "googlewifi", "google wifi",
        "microsoft wifi", "microsoft_wifi", "apple store",
        "apple_store_wifi", "facebook wifi", "meta wifi",
        // Libraries & Education
        "library wifi", "library_wifi", "university wifi",
        "college wifi", "campus wifi", "campus_wifi",
        "eduroam"
    )

    /** Common default / weak passwords that are trivially guessable. */
    private val COMMON_PASSWORDS = listOf(
        "password", "12345678", "123456789", "1234567890",
        "00000000", "11111111", "88888888", "87654321",
        "qwerty123", "abc12345", "abcd1234", "admin123",
        "password1", "password123", "wifi1234", "internet",
        "welcome1", "letmein", "iloveyou", "sunshine",
        "princess", "football", "monkey123", "dragon123",
        "master123", "trustno1", "whatever", "access14",
        "changeme", "default", "guest123", "hello123",
        "test1234", "pass1234", "wifi", "wifipassword",
        "wifipass", "router123", "netgear1", "dlink123",
        "tplink123", "linksys1"
    )

    /** ISP/Carrier network names — suspicious in QR codes as they are managed networks. */
    private val ISP_NETWORKS = listOf(
        "jio", "airtel", "bsnl", "vodafone", "vi", "idea",
        "mtnl", "tata sky", "act fibernet", "hathway",
        "spectranet", "you broadband", "tikona", "railwire",
        "comcast", "xfinity", "att", "at&t", "t-mobile",
        "verizon", "spectrum", "cox", "bt wifi", "sky wifi"
    )

    /** Regex for detecting Cyrillic/Greek homoglyphs in SSID (evil twin technique). */
    private val HOMOGLYPH_REGEX = Regex("[\\u0370-\\u03FF\\u0400-\\u04FF]")

    // ══════════════════════════════════════════════════════════════════
    //  CORE ANALYSIS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Performs deep security analysis on a WiFi QR code.
     * @param actionData The parsed WiFi fields from QrDataParser
     */
    fun analyze(actionData: Map<String, String>): WifiAnalysisResult {
        val flags = mutableListOf<WifiFlag>()
        val recommendations = mutableListOf<String>()
        var riskScore = 0f

        val ssid = actionData["ssid"]
        val password = actionData["password"]
        val securityType = (actionData["type"] ?: if (!password.isNullOrBlank()) "WPA2" else "NOPASS").uppercase().trim()
        val isHidden = actionData["hidden"] == "true" || actionData["hidden"] == "1" || actionData["H"] == "true" || actionData["H"] == "1"
        val hasPassword = !password.isNullOrBlank()

        val ssidLower = (ssid ?: "").lowercase().trim()

        // ══════════════════════════════════════════════════════════════
        //  1. ENCRYPTION ANALYSIS
        // ══════════════════════════════════════════════════════════════

        val encryptionGrade: String
        val encryptionName: String
        val encryptionColor: Int

        when {
            securityType.contains("SAE") || securityType.contains("WPA3") -> {
                encryptionGrade = "A+"
                encryptionName = "WPA3-SAE"
                encryptionColor = 0xFF10B981.toInt() // SafeGreen
                // No penalty — this is the best
            }
            securityType.contains("WPA2") || securityType == "WPA" || securityType.contains("WPA") -> {
                encryptionGrade = "A"
                encryptionName = if (securityType.contains("WPA3")) "WPA3" else if (securityType.contains("WPA2")) "WPA2-AES" else "WPA/WPA2"
                encryptionColor = 0xFF10B981.toInt()
                // Standard secure encryption
            }
            securityType == "WPA" -> {
                encryptionGrade = "C"
                encryptionName = "WPA-TKIP"
                encryptionColor = 0xFFF59E0B.toInt() // CautionAmber
                flags.add(WifiFlag(
                    id = "ENCRYPTION_WPA_OLD",
                    severity = RiskLevel.MEDIUM,
                    emoji = "🔓",
                    title = "Legacy WPA Encryption",
                    description = "WPA (TKIP) is deprecated and vulnerable to attacks. WPA2/WPA3 is strongly recommended.",
                    scorePenalty = 15f
                ))
                riskScore += 15f
                recommendations.add("Ask the network owner to upgrade to WPA2 or WPA3 encryption.")
            }
            securityType.contains("WEP") -> {
                encryptionGrade = "F"
                encryptionName = "WEP (Broken)"
                encryptionColor = 0xFFEF4444.toInt() // MaliciousRed
                flags.add(WifiFlag(
                    id = "ENCRYPTION_WEP",
                    severity = RiskLevel.CRITICAL,
                    emoji = "🔴",
                    title = "WEP Encryption — Critically Broken",
                    description = "WEP can be cracked in under 5 minutes with freely available tools. Any data sent on this network can be intercepted.",
                    scorePenalty = 40f
                ))
                riskScore += 40f
                recommendations.add("⚠️ DO NOT transmit sensitive data on WEP networks. Request WPA2/WPA3 upgrade immediately.")
            }
            securityType.contains("NOPASS") || securityType.contains("NONE") || securityType.isBlank() -> {
                encryptionGrade = "F"
                encryptionName = "Open (No Encryption)"
                encryptionColor = 0xFFEF4444.toInt()
                flags.add(WifiFlag(
                    id = "ENCRYPTION_OPEN",
                    severity = RiskLevel.CRITICAL,
                    emoji = "🚨",
                    title = "Open Network — No Encryption",
                    description = "All traffic on this network is sent in plaintext. Anyone nearby can intercept your passwords, emails, and browsing activity.",
                    scorePenalty = 45f
                ))
                riskScore += 45f
                recommendations.add("⚠️ Use a VPN if you must connect. Never access banking or enter passwords on open networks.")
                recommendations.add("Enable HTTPS-only mode in your browser for basic protection.")
            }
            else -> {
                encryptionGrade = "B"
                encryptionName = securityType
                encryptionColor = 0xFF3B82F6.toInt() // PrimaryBlue
            }
        }

        // ══════════════════════════════════════════════════════════════
        //  2. SSID SPOOFING & EVIL TWIN DETECTION
        // ══════════════════════════════════════════════════════════════

        var isSpoofedSSID = false

        // Known public hotspot impersonation
        if (ssidLower.isNotBlank()) {
            val matchedHotspot = KNOWN_PUBLIC_HOTSPOTS.find { hotspot ->
                ssidLower == hotspot || ssidLower.replace("_", " ") == hotspot || ssidLower.replace(" ", "_") == hotspot
            }
            if (matchedHotspot != null) {
                isSpoofedSSID = true
                flags.add(WifiFlag(
                    id = "EVIL_TWIN_PUBLIC",
                    severity = RiskLevel.HIGH,
                    emoji = "👿",
                    title = "Potential Evil Twin Attack",
                    description = "SSID '$ssid' matches a known public hotspot name. QR codes distributing public WiFi credentials are a classic evil twin attack vector.",
                    scorePenalty = 30f
                ))
                riskScore += 30f
                recommendations.add("⚠️ This looks like a public WiFi name. Verify with staff that this QR is legitimate before connecting.")
            }

            // ISP/Carrier network impersonation
            val matchedISP = ISP_NETWORKS.find { isp -> ssidLower.contains(isp) }
            if (matchedISP != null && matchedHotspot == null) {
                isSpoofedSSID = true
                flags.add(WifiFlag(
                    id = "EVIL_TWIN_ISP",
                    severity = RiskLevel.HIGH,
                    emoji = "📡",
                    title = "ISP Network Name on QR",
                    description = "SSID contains '$matchedISP' — ISP-managed networks don't typically share credentials via QR codes. This may be an impersonation.",
                    scorePenalty = 25f
                ))
                riskScore += 25f
                recommendations.add("ISP networks authenticate differently. Verify this is not an impersonation attempt.")
            }

            // Homoglyph attack detection (Cyrillic/Greek chars in SSID)
            if (HOMOGLYPH_REGEX.containsMatchIn(ssid ?: "")) {
                isSpoofedSSID = true
                flags.add(WifiFlag(
                    id = "SSID_HOMOGLYPH",
                    severity = RiskLevel.CRITICAL,
                    emoji = "🎭",
                    title = "Homoglyph Attack in SSID",
                    description = "The network name contains lookalike characters (e.g., Cyrillic 'а' instead of Latin 'a'). This is a deliberate spoofing technique.",
                    scorePenalty = 40f
                ))
                riskScore += 40f
                recommendations.add("⚠️ This SSID uses deceptive characters to impersonate another network. DO NOT connect.")
            }

            // Suspiciously long SSID
            if ((ssid?.length ?: 0) > 32) {
                flags.add(WifiFlag(
                    id = "SSID_TOO_LONG",
                    severity = RiskLevel.MEDIUM,
                    emoji = "📏",
                    title = "Unusually Long SSID",
                    description = "SSID exceeds the 32-character IEEE 802.11 limit. This may cause connection issues or indicate a crafted attack payload.",
                    scorePenalty = 10f
                ))
                riskScore += 10f
            }

            // SSID with suspicious characters (potential injection)
            val suspiciousChars = listOf("<", ">", "&", "\"", "'", ";", "\\", "\n", "\r", "\t")
            if (suspiciousChars.any { ssid?.contains(it) == true }) {
                flags.add(WifiFlag(
                    id = "SSID_INJECTION",
                    severity = RiskLevel.HIGH,
                    emoji = "💉",
                    title = "Suspicious Characters in SSID",
                    description = "Network name contains special characters that could be used for injection attacks against captive portals or network managers.",
                    scorePenalty = 20f
                ))
                riskScore += 20f
            }

            // "Free" keyword in SSID — social engineering bait
            if (ssidLower.contains("free") && !isSpoofedSSID) {
                flags.add(WifiFlag(
                    id = "SSID_FREE_BAIT",
                    severity = RiskLevel.MEDIUM,
                    emoji = "🎣",
                    title = "\"Free\" WiFi Bait",
                    description = "SSIDs containing 'free' are commonly used as honeypots to attract victims for man-in-the-middle attacks.",
                    scorePenalty = 12f
                ))
                riskScore += 12f
                recommendations.add("'Free WiFi' SSIDs are a common social engineering tactic. Verify the source.")
            }
        }

        // ══════════════════════════════════════════════════════════════
        //  3. PASSWORD / CREDENTIAL SECURITY
        // ══════════════════════════════════════════════════════════════

        var passwordStrength = PasswordStrength.NONE
        var passwordScore = 0f

        if (hasPassword && password != null) {
            // Common password check
            if (COMMON_PASSWORDS.any { password.lowercase() == it }) {
                flags.add(WifiFlag(
                    id = "PASSWORD_COMMON",
                    severity = RiskLevel.HIGH,
                    emoji = "🔑",
                    title = "Common/Default Password",
                    description = "'$password' is a widely known default password. Attackers try these first.",
                    scorePenalty = 0f
                ))
                passwordStrength = PasswordStrength.VERY_WEAK
                passwordScore = 0.1f
                recommendations.add("This is a commonly used password. Change it to a unique, strong passphrase.")
            } else {
                // Strength analysis
                val analysis = analyzePasswordStrength(password)
                passwordStrength = analysis.first
                passwordScore = analysis.second

                when (passwordStrength) {
                    PasswordStrength.VERY_WEAK -> {
                        flags.add(WifiFlag(
                            id = "PASSWORD_VERY_WEAK",
                            severity = RiskLevel.HIGH,
                            emoji = "🔓",
                            title = "Very Weak Password",
                            description = "Password is only ${password.length} characters with low complexity. Can be brute-forced in minutes.",
                            scorePenalty = 0f
                        ))
                    }
                    PasswordStrength.WEAK -> {
                        flags.add(WifiFlag(
                            id = "PASSWORD_WEAK",
                            severity = RiskLevel.MEDIUM,
                            emoji = "⚠️",
                            title = "Weak Password",
                            description = "Password lacks sufficient length or complexity. Vulnerable to dictionary and brute-force attacks.",
                            scorePenalty = 0f
                        ))
                    }
                    PasswordStrength.MODERATE -> {
                        // Acceptable, minor note
                    }
                    PasswordStrength.STRONG, PasswordStrength.EXCELLENT -> {
                        // Good — no penalty
                    }
                    else -> {}
                }
            }

            // Password exposure warning (always shown for QR-shared passwords)
            flags.add(WifiFlag(
                id = "CREDENTIAL_EXPOSURE",
                severity = RiskLevel.LOW,
                emoji = "👁️",
                title = "Password Visible in QR",
                description = "Anyone who scans this QR code can see the WiFi password. Share this QR only with trusted people.",
                scorePenalty = 3f
            ))
            riskScore += 3f

        } else if (encryptionGrade != "F") {
            // Has encryption but no password in QR — unusual
            if (!securityType.contains("NOPASS") && !securityType.contains("NONE") && securityType.isNotBlank()) {
                flags.add(WifiFlag(
                    id = "NO_PASSWORD_WITH_ENCRYPTION",
                    severity = RiskLevel.MEDIUM,
                    emoji = "❓",
                    title = "Encrypted Network, No Password",
                    description = "The QR specifies $encryptionName encryption but doesn't include a password. You'll need to enter it manually.",
                    scorePenalty = 5f
                ))
                riskScore += 5f
            }
        }

        // ══════════════════════════════════════════════════════════════
        //  4. NETWORK CONFIGURATION RISKS
        // ══════════════════════════════════════════════════════════════

        // Hidden network privacy risk
        if (isHidden) {
            flags.add(WifiFlag(
                id = "HIDDEN_NETWORK",
                severity = RiskLevel.MEDIUM,
                emoji = "🫥",
                title = "Hidden Network (Privacy Risk)",
                description = "Connecting to hidden networks forces your device to continuously broadcast probe requests, which can be tracked to monitor your location.",
                scorePenalty = 8f
            ))
            riskScore += 8f
            recommendations.add("Hidden SSIDs don't improve security — they actually reduce your privacy. Consider making the network visible.")
        }

        // Missing SSID
        if (ssid.isNullOrBlank()) {
            flags.add(WifiFlag(
                id = "NO_SSID",
                severity = RiskLevel.HIGH,
                emoji = "🚫",
                title = "No Network Name",
                description = "The QR code doesn't specify a network name (SSID). This is structurally invalid.",
                scorePenalty = 25f
            ))
            riskScore += 25f
        }

        // ══════════════════════════════════════════════════════════════
        //  CALCULATE FINAL RESULTS
        // ══════════════════════════════════════════════════════════════

        riskScore = riskScore.coerceIn(0f, 100f)

        val riskLevel = when {
            riskScore >= 65f -> RiskLevel.CRITICAL
            riskScore >= 40f -> RiskLevel.HIGH
            riskScore >= 18f -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val safetyStatus = when {
            riskScore >= 55f -> SafetyStatus.MALICIOUS
            riskScore >= 20f -> SafetyStatus.CAUTION
            else -> SafetyStatus.SAFE
        }

        val summary = buildSummary(flags, encryptionGrade, encryptionName, riskLevel, riskScore, isSpoofedSSID)

        // Positive recommendation if clean
        if (flags.none { it.severity == RiskLevel.HIGH || it.severity == RiskLevel.CRITICAL }) {
            recommendations.add(0, "This WiFi network has good security configuration. Safe to connect.")
        }

        return WifiAnalysisResult(
            riskScore = riskScore,
            safetyStatus = safetyStatus,
            riskLevel = riskLevel,
            flags = flags.sortedByDescending { it.scorePenalty },
            encryptionGrade = encryptionGrade,
            encryptionName = encryptionName,
            encryptionColor = encryptionColor,
            passwordStrength = passwordStrength,
            passwordScore = passwordScore,
            isSpoofedSSID = isSpoofedSSID,
            summary = summary,
            recommendations = recommendations,
            ssid = ssid,
            securityType = securityType,
            isHidden = isHidden,
            hasPassword = hasPassword,
            password = password
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Analyzes password strength based on length, character classes, and entropy.
     * @return Pair of (PasswordStrength, score 0.0–1.0)
     */
    private fun analyzePasswordStrength(password: String): Pair<PasswordStrength, Float> {
        var score = 0f

        // Length scoring
        score += when {
            password.length >= 20 -> 0.35f
            password.length >= 14 -> 0.28f
            password.length >= 10 -> 0.20f
            password.length >= 8 -> 0.12f
            else -> 0.05f
        }

        // Character class diversity
        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        val classCount = listOf(hasLower, hasUpper, hasDigit, hasSpecial).count { it }

        score += when (classCount) {
            4 -> 0.30f
            3 -> 0.20f
            2 -> 0.12f
            else -> 0.05f
        }

        // Entropy bonus
        val entropy = calculateEntropy(password)
        score += (entropy / 5.0f).toFloat().coerceAtMost(0.25f)

        // Repetition penalty
        val repeatingPattern = password.length >= 3 && password.all { it == password[0] }
        if (repeatingPattern) score *= 0.3f

        // Sequential penalty (12345678, abcdefgh)
        val isSequential = password.zipWithNext().all { (a, b) -> b.code - a.code == 1 }
        if (isSequential && password.length >= 6) score *= 0.4f

        score = score.coerceIn(0f, 1f)

        val strength = when {
            score >= 0.75f -> PasswordStrength.EXCELLENT
            score >= 0.55f -> PasswordStrength.STRONG
            score >= 0.35f -> PasswordStrength.MODERATE
            score >= 0.18f -> PasswordStrength.WEAK
            else -> PasswordStrength.VERY_WEAK
        }

        return Pair(strength, score)
    }

    private fun calculateEntropy(input: String): Double {
        if (input.isEmpty()) return 0.0
        val charCounts = input.groupingBy { it }.eachCount()
        return charCounts.values.sumOf { count ->
            val p = count.toDouble() / input.length
            -p * (Math.log(p) / Math.log(2.0))
        }
    }

    private fun buildSummary(
        flags: List<WifiFlag>,
        grade: String,
        encName: String,
        riskLevel: RiskLevel,
        riskScore: Float,
        isSpoofed: Boolean
    ): String = buildString {
        append("📶 WiFi Security Analysis\n\n")

        val riskEmoji = when (riskLevel) {
            RiskLevel.CRITICAL -> "🚨"
            RiskLevel.HIGH -> "🔴"
            RiskLevel.MEDIUM -> "🟡"
            RiskLevel.LOW -> "🟢"
        }
        append("$riskEmoji Risk Level: ${riskLevel.name} (Score: ${String.format("%.0f", riskScore)}/100)\n")
        append("🛡️ Encryption: $encName (Grade: $grade)\n\n")

        if (isSpoofed) {
            append("⚠️ POSSIBLE EVIL TWIN / SPOOFED NETWORK\n\n")
        }

        if (flags.isNotEmpty()) {
            append("🔍 Security Issues (${flags.size}):\n")
            flags.sortedByDescending { it.scorePenalty }.forEach { flag ->
                append("${flag.emoji} ${flag.title}\n")
            }
        } else {
            append("✅ No security issues detected.\n")
        }
    }
}
