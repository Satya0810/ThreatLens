package com.safeqr.scanner.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated preferences manager for UPI real-time protection features.
 *
 * Manages:
 * - Daily transaction limit tracking
 * - VPA reputation database (blocklist/allowlist/history)
 *
 * Stored separately from the main EncryptedSharedPreferences to avoid
 * coupling with the primary app prefs and to keep UPI data lightweight.
 */
object UpiGuardPreferences {

    private const val PREFS_NAME = "upi_guard_prefs"
    private const val KEY_DAILY_LIMIT = "upi_daily_limit"
    private const val KEY_DAILY_SPENT = "upi_daily_spent"
    private const val KEY_VPA_HISTORY = "upi_vpa_history"
    private const val KEY_LATE_NIGHT_WARNING = "upi_late_night_warning"

    private const val DEFAULT_DAILY_LIMIT = 10000.0
    private const val MAX_VPA_RECORDS = 500

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ══════════════════════════════════════════════════════════════════
    //  DAILY LIMIT TRACKER
    // ══════════════════════════════════════════════════════════════════

    fun getDailyLimit(context: Context): Double {
        // Use a long-based storage to avoid float precision issues
        val stored = getPrefs(context).getLong(KEY_DAILY_LIMIT, -1L)
        return if (stored == -1L) DEFAULT_DAILY_LIMIT else stored.toDouble()
    }

    fun setDailyLimit(context: Context, limit: Double) {
        getPrefs(context).edit().putLong(KEY_DAILY_LIMIT, limit.toLong()).apply()
    }

    /**
     * Returns the total amount spent today via UPI QR payments.
     * Automatically resets when a new day starts.
     */
    fun getTodaySpent(context: Context): Double {
        val json = getPrefs(context).getString(KEY_DAILY_SPENT, null) ?: return 0.0
        return try {
            val obj = JSONObject(json)
            val storedDate = obj.optString("date", "")
            val today = todayDateString()
            if (storedDate == today) {
                obj.optDouble("total", 0.0)
            } else {
                // New day — reset
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    /**
     * Records a UPI transaction amount for today's spending tracker.
     */
    fun addTransaction(context: Context, amount: Double) {
        val currentSpent = getTodaySpent(context)
        val newTotal = currentSpent + amount
        val obj = JSONObject().apply {
            put("date", todayDateString())
            put("total", newTotal)
        }
        getPrefs(context).edit().putString(KEY_DAILY_SPENT, obj.toString()).apply()
    }

    /**
     * Checks if adding [amount] would exceed the user's daily limit.
     * Returns a pair of (wouldExceed, remainingBudget).
     */
    fun checkDailyLimit(context: Context, amount: Double?): DailyLimitStatus {
        val limit = getDailyLimit(context)
        val spent = getTodaySpent(context)
        val remaining = (limit - spent).coerceAtLeast(0.0)

        return DailyLimitStatus(
            dailyLimit = limit,
            spentToday = spent,
            remaining = remaining,
            wouldExceed = amount != null && (spent + amount) > limit,
            pendingAmount = amount
        )
    }

    data class DailyLimitStatus(
        val dailyLimit: Double,
        val spentToday: Double,
        val remaining: Double,
        val wouldExceed: Boolean,
        val pendingAmount: Double?
    )

    // ══════════════════════════════════════════════════════════════════
    //  VPA REPUTATION DATABASE
    // ══════════════════════════════════════════════════════════════════

    data class VpaRecord(
        val vpa: String,
        val payCount: Int = 0,
        val reportCount: Int = 0,
        val lastTimestamp: Long = 0L,
        val isBlocked: Boolean = false
    )

    /**
     * Retrieves the full VPA history map from storage.
     */
    private fun getVpaMap(context: Context): MutableMap<String, VpaRecord> {
        val json = getPrefs(context).getString(KEY_VPA_HISTORY, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, VpaRecord>()
            obj.keys().forEach { vpa ->
                val record = obj.getJSONObject(vpa)
                map[vpa] = VpaRecord(
                    vpa = vpa,
                    payCount = record.optInt("payCount", 0),
                    reportCount = record.optInt("reportCount", 0),
                    lastTimestamp = record.optLong("lastTimestamp", 0L),
                    isBlocked = record.optBoolean("isBlocked", false)
                )
            }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveVpaMap(context: Context, map: Map<String, VpaRecord>) {
        val obj = JSONObject()
        // Keep only the most recent MAX_VPA_RECORDS entries
        val trimmed = map.entries
            .sortedByDescending { it.value.lastTimestamp }
            .take(MAX_VPA_RECORDS)

        trimmed.forEach { (vpa, record) ->
            obj.put(vpa, JSONObject().apply {
                put("payCount", record.payCount)
                put("reportCount", record.reportCount)
                put("lastTimestamp", record.lastTimestamp)
                put("isBlocked", record.isBlocked)
            })
        }
        getPrefs(context).edit().putString(KEY_VPA_HISTORY, obj.toString()).apply()
    }

    /**
     * Retrieves the reputation record for a specific VPA.
     * Returns null if this VPA has never been seen before.
     */
    fun getVpaHistory(context: Context, vpa: String): VpaRecord? {
        return getVpaMap(context)[vpa.lowercase().trim()]
    }

    /**
     * Records a VPA interaction — either a successful payment or a user report.
     */
    fun recordVpaInteraction(context: Context, vpa: String, action: VpaAction) {
        val key = vpa.lowercase().trim()
        val map = getVpaMap(context)
        val existing = map[key] ?: VpaRecord(vpa = key)

        map[key] = when (action) {
            VpaAction.PAID -> existing.copy(
                payCount = existing.payCount + 1,
                lastTimestamp = System.currentTimeMillis()
            )
            VpaAction.REPORTED -> existing.copy(
                reportCount = existing.reportCount + 1,
                lastTimestamp = System.currentTimeMillis(),
                isBlocked = true // Auto-block on report
            )
        }
        saveVpaMap(context, map)
    }

    enum class VpaAction { PAID, REPORTED }

    fun isVpaBlocked(context: Context, vpa: String): Boolean {
        return getVpaHistory(context, vpa)?.isBlocked == true
    }

    fun blockVpa(context: Context, vpa: String) {
        val key = vpa.lowercase().trim()
        val map = getVpaMap(context)
        val existing = map[key] ?: VpaRecord(vpa = key)
        map[key] = existing.copy(isBlocked = true, lastTimestamp = System.currentTimeMillis())
        saveVpaMap(context, map)
    }

    fun unblockVpa(context: Context, vpa: String) {
        val key = vpa.lowercase().trim()
        val map = getVpaMap(context)
        val existing = map[key] ?: return
        map[key] = existing.copy(isBlocked = false)
        saveVpaMap(context, map)
    }

    /**
     * Returns all blocked VPAs for the settings screen.
     */
    fun getBlockedVpas(context: Context): List<VpaRecord> {
        return getVpaMap(context).values.filter { it.isBlocked }.sortedByDescending { it.lastTimestamp }
    }

    // ══════════════════════════════════════════════════════════════════
    //  LATE NIGHT WARNING TOGGLE
    // ══════════════════════════════════════════════════════════════════

    fun isLateNightWarningEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LATE_NIGHT_WARNING, true)
    }

    fun setLateNightWarningEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LATE_NIGHT_WARNING, enabled).apply()
    }

    // ══════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ══════════════════════════════════════════════════════════════════

    private fun todayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
