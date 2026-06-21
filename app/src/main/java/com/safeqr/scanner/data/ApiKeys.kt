package com.safeqr.scanner.data

import com.safeqr.scanner.BuildConfig

object ApiKeys {
    // Keys are now securely loaded from local.properties -> BuildConfig
    val SAFE_BROWSING = BuildConfig.SAFE_BROWSING_KEY
    val VIRUS_TOTAL = BuildConfig.VIRUS_TOTAL_KEY
    val URL_SCAN_IO = BuildConfig.URL_SCAN_IO_KEY
    val ABUSE_IPDB = BuildConfig.ABUSE_IPDB_KEY
    val WHOIS_XML = BuildConfig.WHOIS_XML_KEY
    val CLOUDFLARE = BuildConfig.CLOUDFLARE_KEY
    val GEMINI = BuildConfig.GEMINI_KEY
}
