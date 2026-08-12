package com.safeqr.scanner.data.remote

object DynamicLinkManager {

    // Simulated Cloud Database of Dynamic Links
    // Maps short code (e.g. "dyn_123") to DynamicLinkRecord
    private val dynamicLinks = mutableMapOf<String, DynamicLinkRecord>()

    data class DynamicLinkRecord(
        val shortCode: String,
        val destinationUrl: String,
        var isKilled: Boolean = false,
        var allowedGeoRegion: String? = null, // e.g. "US", "IN", null means global
        var passwordHash: String? = null,
        var alternateUrls: List<String>? = null,
        var scanCount: Int = 0,
        // ── Self-Destruct Fields ────────────────────────────────────────
        var expiresAt: Long? = null,          // Timestamp after which link is dead
        var maxScans: Int? = null,            // Max allowed scans before self-destruct
        var activeFrom: Long? = null          // Timestamp before which link is not yet active
    ) {
        /** Returns true if the link has expired or exhausted its scans. */
        val isSelfDestructed: Boolean
            get() {
                if (isKilled) return true
                if (expiresAt != null && System.currentTimeMillis() > expiresAt!!) return true
                if (maxScans != null && scanCount >= maxScans!!) return true
                return false
            }

        /** Returns remaining scans, or null if unlimited. */
        val remainingScans: Int?
            get() = maxScans?.let { (it - scanCount).coerceAtLeast(0) }

        /** Returns remaining time in millis until expiry, or null if no expiry set. */
        val remainingTimeMs: Long?
            get() = expiresAt?.let { (it - System.currentTimeMillis()).coerceAtLeast(0) }

        /** Returns true if the link is not yet active (scheduled for future). */
        val isNotYetActive: Boolean
            get() = activeFrom != null && System.currentTimeMillis() < activeFrom!!
    }

    fun createLink(
        shortCode: String, 
        destinationUrl: String, 
        allowedGeoRegion: String? = null,
        passwordHash: String? = null,
        alternateUrls: List<String>? = null,
        expiresAt: Long? = null,
        maxScans: Int? = null,
        activeFrom: Long? = null
    ) {
        dynamicLinks[shortCode] = DynamicLinkRecord(
            shortCode = shortCode,
            destinationUrl = destinationUrl,
            isKilled = false,
            allowedGeoRegion = allowedGeoRegion,
            passwordHash = passwordHash,
            alternateUrls = alternateUrls,
            expiresAt = expiresAt,
            maxScans = maxScans,
            activeFrom = activeFrom
        )
    }

    fun killLink(shortCode: String) {
        dynamicLinks[shortCode]?.isKilled = true
    }

    /**
     * Updates the self-destruct settings of an existing link.
     */
    fun updateSelfDestruct(shortCode: String, expiresAt: Long? = null, maxScans: Int? = null, activeFrom: Long? = null) {
        dynamicLinks[shortCode]?.let { record ->
            record.expiresAt = expiresAt
            record.maxScans = maxScans
            record.activeFrom = activeFrom
        }
    }

    /**
     * Gets the lifecycle status of a dynamic link for display purposes.
     */
    fun getLinkStatus(shortCode: String): DynamicLinkRecord? {
        return dynamicLinks[shortCode]
    }

    // Resolves a link based on the user's current geo region and optional password
    // Returns the destinationUrl if allowed, or throws an Exception if killed/geo-blocked/expired
    fun resolveLink(shortCode: String, userRegionCode: String, passwordAttempt: String? = null): String {
        val record = dynamicLinks[shortCode] 
            ?: throw Exception("Link not found or does not exist.")

        if (record.isKilled) {
            throw Exception("LINK_KILLED: This dynamic link has been permanently terminated by the creator.")
        }

        // ── Self-Destruct Checks ────────────────────────────────────────
        if (record.isNotYetActive) {
            val activeFrom = record.activeFrom!!
            val timeUntilActive = activeFrom - System.currentTimeMillis()
            val hoursLeft = (timeUntilActive / (1000 * 60 * 60)).toInt()
            val minsLeft = ((timeUntilActive / (1000 * 60)) % 60).toInt()
            throw Exception("LINK_NOT_ACTIVE_YET: This link will activate in ${hoursLeft}h ${minsLeft}m.")
        }

        if (record.expiresAt != null && System.currentTimeMillis() > record.expiresAt!!) {
            throw Exception("LINK_EXPIRED: This dynamic link has expired and self-destructed. ⏰")
        }

        if (record.maxScans != null && record.scanCount >= record.maxScans!!) {
            throw Exception("LINK_EXHAUSTED: This link has reached its maximum scan limit (${record.maxScans}) and self-destructed. 💥")
        }

        // ── Geo Restriction ─────────────────────────────────────────────
        if (record.allowedGeoRegion != null && record.allowedGeoRegion != userRegionCode) {
            throw Exception("GEO_BLOCKED: This link is restricted and cannot be accessed from your current region ($userRegionCode).")
        }
        
        // ── Password Protection ─────────────────────────────────────────
        if (record.passwordHash != null) {
            if (passwordAttempt == null) {
                return "threatlens://pin-portal?shortCode=$shortCode"
            }
            val hashAttempt = hashPassword(passwordAttempt)
            if (hashAttempt != record.passwordHash) {
                throw Exception("INVALID_PASSWORD: The provided PIN/password is incorrect.")
            }
        }

        // Multi-URL Rotation Logic
        record.scanCount++
        val allUrls = mutableListOf(record.destinationUrl)
        if (record.alternateUrls != null) {
            allUrls.addAll(record.alternateUrls!!)
        }
        
        val targetUrl = allUrls[(record.scanCount - 1) % allUrls.size]
        return targetUrl
    }
    
    fun hashPassword(password: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    
    // Check if URL is a dynamic link
    fun isDynamicLink(url: String): Boolean {
        return url.startsWith("threatlens://dyn/")
    }
    
    // Extract shortcode from threatlens://dyn/{shortCode}
    fun extractShortCode(url: String): String? {
        if (!isDynamicLink(url)) return null
        return url.substringAfter("threatlens://dyn/")
    }
}

