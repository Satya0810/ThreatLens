package com.safeqr.scanner.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * SharedPreferences wrapper for Link Guard (System-Wide Link Protection) settings.
 */
object LinkGuardPreferences {

    private const val PREFS_NAME = "link_guard_prefs"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Master Toggle ──────────────────────────────────────────────────────────

    fun isLinkGuardEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean("link_guard_enabled", false)

    fun setLinkGuardEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("link_guard_enabled", enabled).apply()
    }

    // ── Sensitivity Level ──────────────────────────────────────────────────────

    enum class SensitivityLevel { LOW, MEDIUM, HIGH }

    fun getSensitivityLevel(context: Context): SensitivityLevel {
        val value = getPrefs(context).getString("sensitivity_level", "MEDIUM") ?: "MEDIUM"
        return try {
            SensitivityLevel.valueOf(value)
        } catch (e: Exception) {
            SensitivityLevel.MEDIUM
        }
    }

    fun setSensitivityLevel(context: Context, level: SensitivityLevel) {
        getPrefs(context).edit().putString("sensitivity_level", level.name).apply()
    }

    // ── Monitored Apps ─────────────────────────────────────────────────────────

    data class MonitoredApp(val packageName: String, val label: String, val isEnabled: Boolean = true)

    private val defaultMonitoredApps = listOf(
        MonitoredApp("com.android.messaging", "SMS / Messages", true),
        MonitoredApp("com.google.android.apps.messaging", "Google Messages", true),
        MonitoredApp("com.whatsapp", "WhatsApp", true),
        MonitoredApp("org.telegram.messenger", "Telegram", true),
        MonitoredApp("com.instagram.android", "Instagram", true),
        MonitoredApp("com.facebook.katana", "Facebook", true),
        MonitoredApp("com.facebook.orca", "Messenger", true),
        MonitoredApp("com.twitter.android", "X (Twitter)", true),
        MonitoredApp("com.google.android.gm", "Gmail", true),
        MonitoredApp("com.microsoft.office.outlook", "Outlook", true),
        MonitoredApp("com.yahoo.mobile.client.android.mail", "Yahoo Mail", true),
        MonitoredApp("com.android.chrome", "Chrome", true),
        MonitoredApp("org.mozilla.firefox", "Firefox", true),
        MonitoredApp("com.brave.browser", "Brave Browser", true),
        MonitoredApp("com.sec.android.app.sbrowser", "Samsung Internet", true)
    )

    fun getMonitoredApps(context: Context): List<MonitoredApp> {
        val json = getPrefs(context).getString("monitored_apps", null)
        val savedApps: List<MonitoredApp> = if (json != null) {
            try {
                val type = object : TypeToken<List<MonitoredApp>>() {}.type
                Gson().fromJson<List<MonitoredApp>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

        // Merge saved apps with defaults to ensure new apps (like Gmail/Chrome) are added
        val savedPackages = savedApps.map { it.packageName }.toSet()
        val missingDefaults = defaultMonitoredApps.filter { it.packageName !in savedPackages }
        
        return if (missingDefaults.isNotEmpty() || savedApps.isEmpty()) {
            val merged = if (savedApps.isEmpty()) defaultMonitoredApps else savedApps + missingDefaults
            saveMonitoredApps(context, merged)
            merged
        } else {
            savedApps
        }
    }

    fun saveMonitoredApps(context: Context, apps: List<MonitoredApp>) {
        getPrefs(context).edit().putString("monitored_apps", Gson().toJson(apps)).apply()
    }

    fun isAppMonitored(context: Context, packageName: String): Boolean {
        return getMonitoredApps(context).any { it.packageName == packageName && it.isEnabled }
    }

    // ── Stats Tracking ─────────────────────────────────────────────────────────

    fun getLinksScanned(context: Context): Int =
        getPrefs(context).getInt("links_scanned", 0)

    fun incrementLinksScanned(context: Context) {
        val current = getLinksScanned(context)
        getPrefs(context).edit().putInt("links_scanned", current + 1).apply()
    }

    fun getThreatsBlocked(context: Context): Int =
        getPrefs(context).getInt("threats_blocked", 0)

    fun incrementThreatsBlocked(context: Context) {
        val current = getThreatsBlocked(context)
        getPrefs(context).edit().putInt("threats_blocked", current + 1).apply()
    }

    // ── Recent Detected Links (for display in settings) ─────────────────────────

    data class DetectedLink(val url: String, val source: String, val timestamp: Long, val isThreat: Boolean)

    fun addDetectedLink(context: Context, link: DetectedLink) {
        val existing = getRecentDetectedLinks(context).toMutableList()
        existing.add(0, link)
        // Keep only last 50
        val trimmed = existing.take(50)
        getPrefs(context).edit().putString("recent_links", Gson().toJson(trimmed)).apply()
    }

    fun getRecentDetectedLinks(context: Context): List<DetectedLink> {
        val json = getPrefs(context).getString("recent_links", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DetectedLink>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
