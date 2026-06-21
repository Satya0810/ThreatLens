package com.safeqr.scanner.analysis

import java.net.URI

/**
 * Performs local heuristic analysis on URLs to detect common phishing and malicious patterns.
 * Each check appends a human-readable warning flag if the condition is detected.
 */
object   HeuristicChecker {

object HeuristicDefaults {
    /** TLDs frequently abused for malicious purposes. */
    val SUSPICIOUS_TLDS = listOf(
        ".tk", ".ml", ".ga", ".cf", ".gq",
        ".xyz", ".top", ".buzz", ".club"
    )

    /** Keywords commonly found in phishing URL paths. */
    val SUSPICIOUS_KEYWORDS = listOf(
        "login", "signin", "account", "verify",
        "secure", "update", "bank", "paypal"
    )
}

private val SUSPICIOUS_TLDS get() = com.safeqr.scanner.data.remote.CloudDatasetManager.getHeuristicCheckerData().SUSPICIOUS_TLDS.takeIf { it.isNotEmpty() } ?: HeuristicDefaults.SUSPICIOUS_TLDS
private val SUSPICIOUS_KEYWORDS get() = com.safeqr.scanner.data.remote.CloudDatasetManager.getHeuristicCheckerData().SUSPICIOUS_KEYWORDS.takeIf { it.isNotEmpty() } ?: HeuristicDefaults.SUSPICIOUS_KEYWORDS

    /** Regex matching Cyrillic characters (U+0400–U+04FF) and Greek characters (U+0370–U+03FF). */
    private val HOMOGRAPH_REGEX = Regex("[\\u0370-\\u03FF\\u0400-\\u04FF]")

    /** Regex to detect an IP address used as the host (IPv4). */
    private val IP_ADDRESS_REGEX = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")

    /**
     * Analyzes the given [url] and returns a list of warning flags.
     * An empty list indicates no heuristic issues were detected.
     */
    fun analyze(url: String): List<String> {
        val flags = mutableListOf<String>()
        val lowerUrl = url.lowercase()

        // ── Check for dangerous URL schemes ───────────────────────────────────
        if (lowerUrl.startsWith("data:") || lowerUrl.startsWith("javascript:")) {
            flags.add("Dangerous URL scheme")
            return flags // No further analysis meaningful for non-HTTP schemes
        }

        // ── Parse the URL ─────────────────────────────────────────────────────
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            flags.add("Malformed URL")
            return flags
        }

        val host = uri.host?.lowercase() ?: ""

        // ── Check for IP address instead of domain name ───────────────────────
        if (IP_ADDRESS_REGEX.matches(host)) {
            flags.add("Uses IP address directly")
        }

        // ── Check for suspicious TLDs ─────────────────────────────────────────
        if (SUSPICIOUS_TLDS.any { host.endsWith(it) }) {
            flags.add("Suspicious TLD")
        }

        // ── Check for homograph attacks (mixed scripts in domain) ─────────────
        if (HOMOGRAPH_REGEX.containsMatchIn(host)) {
            flags.add("Possible homograph attack")
        }

        // ── Check for excessive subdomains (>3 dots in host) ──────────────────
        if (host.count { it == '.' } > 3) {
            flags.add("Excessive subdomains")
        }

        // ── Check for @ symbol in URL authority (credential phishing trick) ─────────
        // Only flag if @ appears before the first / (i.e., in the authority portion),
        // and skip mailto: URLs where @ is expected.
        if (!lowerUrl.startsWith("mailto:")) {
            val authorityPart = uri.authority ?: ""
            if (authorityPart.contains("@")) {
                flags.add("Contains @ symbol in URL authority")
            }
        }

        // ── Check for suspicious keywords in path ─────────────────────────────
        val path = uri.path?.lowercase() ?: ""
        if (SUSPICIOUS_KEYWORDS.any { path.contains(it) }) {
            flags.add("Contains suspicious keywords")
        }

        // ── Check for unusually long URL ──────────────────────────────────────
        if (url.length > 200) {
            flags.add("Unusually long URL")
        }

        return flags
    }
}
