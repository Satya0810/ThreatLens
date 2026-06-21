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
        var scanCount: Int = 0
    )

    fun createLink(
        shortCode: String, 
        destinationUrl: String, 
        allowedGeoRegion: String? = null,
        passwordHash: String? = null,
        alternateUrls: List<String>? = null
    ) {
        dynamicLinks[shortCode] = DynamicLinkRecord(
            shortCode = shortCode,
            destinationUrl = destinationUrl,
            isKilled = false,
            allowedGeoRegion = allowedGeoRegion,
            passwordHash = passwordHash,
            alternateUrls = alternateUrls
        )
    }

    fun killLink(shortCode: String) {
        dynamicLinks[shortCode]?.isKilled = true
    }

    // Resolves a link based on the user's current geo region and optional password
    // Returns the destinationUrl if allowed, or throws an Exception if killed/geo-blocked
    fun resolveLink(shortCode: String, userRegionCode: String, passwordAttempt: String? = null): String {
        val record = dynamicLinks[shortCode] 
            ?: throw Exception("Link not found or does not exist.")

        if (record.isKilled) {
            throw Exception("LINK_KILLED: This dynamic link has been permanently terminated by the creator.")
        }

        if (record.allowedGeoRegion != null && record.allowedGeoRegion != userRegionCode) {
            throw Exception("GEO_BLOCKED: This link is restricted and cannot be accessed from your current region ($userRegionCode).")
        }
        
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
