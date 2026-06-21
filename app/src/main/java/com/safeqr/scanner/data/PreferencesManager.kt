package com.safeqr.scanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

object PreferencesManager {

    private const val PREFS_NAME = "safeqr_prefs"
    private const val KEY_SAFE_BROWSING_API = "safe_browsing_api_key"
    private const val KEY_VIRUS_TOTAL_API = "virus_total_api_key"
    private const val KEY_WEBSHRINKER_API = "webshrinker_api_key"
    private const val KEY_AUTO_BLOCK = "auto_block_malicious"
    private const val KEY_VIBRATE = "vibrate_on_detection"

    private var encryptedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        if (encryptedPrefs == null) {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                try {
                    // Delete corrupted preferences file
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        context.deleteSharedPreferences(PREFS_NAME)
                    } else {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                    }
                    
                    // If preferences were corrupted/lost, the DB passphrase is gone.
                    // We MUST delete the old SQLCipher database because it is unreadable.
                    context.deleteDatabase("safeqr_db")
                    
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    encryptedPrefs = EncryptedSharedPreferences.create(
                        context,
                        PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (e2: Exception) {
                    // Fallback to unencrypted preferences if hardware keystore is broken
                    encryptedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            }
        }
        return encryptedPrefs!!
    }

    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    fun getDatabasePassphrase(context: Context): ByteArray {
        val prefs = getPrefs(context)
        var hexPassphrase = prefs.getString(KEY_DB_PASSPHRASE, null)

        if (hexPassphrase == null) {
            val secureRandom = SecureRandom()
            val bytes = ByteArray(32)
            secureRandom.nextBytes(bytes)
            hexPassphrase = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_DB_PASSPHRASE, hexPassphrase).apply()
        }

        return hexPassphrase.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    fun getSafeBrowsingApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_SAFE_BROWSING_API, "") ?: ""
    }

    fun setSafeBrowsingApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_SAFE_BROWSING_API, key).apply()
    }

    fun getVirusTotalApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_VIRUS_TOTAL_API, "") ?: ""
    }

    fun setVirusTotalApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_VIRUS_TOTAL_API, key).apply()
    }

    fun getWebshrinkerApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_WEBSHRINKER_API, "") ?: ""
    }

    fun setWebshrinkerApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_WEBSHRINKER_API, key).apply()
    }

    fun getAutoBlock(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_BLOCK, true)
    }

    fun setAutoBlock(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_BLOCK, value).apply()
    }

    fun getVibrate(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VIBRATE, true)
    }

    fun setVibrate(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VIBRATE, value).apply()
    }

    // ── Child Lock (Parental Control) ────────────────────────────────────

    private const val KEY_CHILD_LOCK_ENABLED = "child_lock_enabled"
    private const val KEY_CHILD_LOCK_PIN = "child_lock_pin"

    fun isChildLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CHILD_LOCK_ENABLED, false)
    }

    fun setChildLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CHILD_LOCK_ENABLED, enabled).apply()
    }

    fun getChildLockPin(context: Context): String {
        return getPrefs(context).getString(KEY_CHILD_LOCK_PIN, "") ?: ""
    }

    fun setChildLockPin(context: Context, pin: String) {
        getPrefs(context).edit().putString(KEY_CHILD_LOCK_PIN, pin).apply()
    }

    fun verifyChildLockPin(context: Context, inputPin: String): Boolean {
        return getPrefs(context).getString(KEY_CHILD_LOCK_PIN, "") == inputPin
    }

    private const val KEY_PARENTAL_CONFIG = "parental_config"
    private const val KEY_PARENTAL_LOGS = "parental_logs"

    data class ParentalConfig(
        val blockAdult: Boolean = true,
        val blockPayment: Boolean = true,
        val blockSocial: Boolean = false,
        val blockGaming: Boolean = false,
        val bedtimeEnabled: Boolean = false,
        val bedtimeStartHour: Int = 21,
        val bedtimeEndHour: Int = 7,
        val whitelistDomains: List<String> = emptyList(),
        val blacklistDomains: List<String> = emptyList()
    )

    fun getParentalConfig(context: Context): ParentalConfig {
        val jsonString = getPrefs(context).getString(KEY_PARENTAL_CONFIG, null)
        if (jsonString.isNullOrEmpty()) return ParentalConfig()
        return try {
            val json = JSONObject(jsonString)
            val whitelist = mutableListOf<String>()
            val blacklist = mutableListOf<String>()
            val wArray = json.optJSONArray("whitelistDomains")
            if (wArray != null) {
                for (i in 0 until wArray.length()) whitelist.add(wArray.getString(i))
            }
            val bArray = json.optJSONArray("blacklistDomains")
            if (bArray != null) {
                for (i in 0 until bArray.length()) blacklist.add(bArray.getString(i))
            }

            ParentalConfig(
                blockAdult = json.optBoolean("blockAdult", true),
                blockPayment = json.optBoolean("blockPayment", true),
                blockSocial = json.optBoolean("blockSocial", false),
                blockGaming = json.optBoolean("blockGaming", false),
                bedtimeEnabled = json.optBoolean("bedtimeEnabled", false),
                bedtimeStartHour = json.optInt("bedtimeStartHour", 21),
                bedtimeEndHour = json.optInt("bedtimeEndHour", 7),
                whitelistDomains = whitelist,
                blacklistDomains = blacklist
            )
        } catch (e: Exception) {
            ParentalConfig()
        }
    }

    fun saveParentalConfig(context: Context, config: ParentalConfig) {
        val json = JSONObject().apply {
            put("blockAdult", config.blockAdult)
            put("blockPayment", config.blockPayment)
            put("blockSocial", config.blockSocial)
            put("blockGaming", config.blockGaming)
            put("bedtimeEnabled", config.bedtimeEnabled)
            put("bedtimeStartHour", config.bedtimeStartHour)
            put("bedtimeEndHour", config.bedtimeEndHour)
            val wArray = JSONArray()
            config.whitelistDomains.forEach { wArray.put(it) }
            put("whitelistDomains", wArray)
            val bArray = JSONArray()
            config.blacklistDomains.forEach { bArray.put(it) }
            put("blacklistDomains", bArray)
        }
        getPrefs(context).edit().putString(KEY_PARENTAL_CONFIG, json.toString()).apply()
    }

    data class ParentalLog(
        val timestamp: Long,
        val url: String,
        val action: String, // "Allowed" or "Blocked"
        val reason: String // e.g. "Bedtime", "Adult Content"
    )

    fun getParentalLogs(context: Context): List<ParentalLog> {
        val jsonString = getPrefs(context).getString(KEY_PARENTAL_LOGS, null)
        if (jsonString.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val logs = mutableListOf<ParentalLog>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                logs.add(
                    ParentalLog(
                        timestamp = obj.getLong("timestamp"),
                        url = obj.getString("url"),
                        action = obj.getString("action"),
                        reason = obj.optString("reason", "")
                    )
                )
            }
            logs.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addParentalLog(context: Context, url: String, action: String, reason: String = "") {
        val currentLogs = getParentalLogs(context).toMutableList()
        currentLogs.add(0, ParentalLog(System.currentTimeMillis(), url, action, reason))
        // Keep last 100 logs
        val trimmed = currentLogs.take(100)
        
        val array = JSONArray()
        trimmed.forEach { log ->
            val obj = JSONObject().apply {
                put("timestamp", log.timestamp)
                put("url", log.url)
                put("action", log.action)
                put("reason", log.reason)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_PARENTAL_LOGS, array.toString()).apply()
    }

    // ── App Theme ────────────────────────────────────────────────────────
    private const val KEY_THEME = "app_theme_name"

    fun getAppTheme(context: Context): String {
        return getPrefs(context).getString(KEY_THEME, "NEON_CYAN") ?: "NEON_CYAN"
    }

    fun setAppTheme(context: Context, themeName: String) {
        getPrefs(context).edit().putString(KEY_THEME, themeName).apply()
    }

    private const val SESSION_PREFS_NAME = "safeqr_session_prefs"

    private fun getSessionPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Current User ID ────────────────────────────────────────────────────────
    private const val KEY_CURRENT_USER_ID = "current_user_id"

    fun getCurrentUserId(context: Context): String? {
        return getSessionPrefs(context).getString(KEY_CURRENT_USER_ID, null)
    }

    fun setCurrentUserId(context: Context, userId: String?) {
        getSessionPrefs(context).edit().putString(KEY_CURRENT_USER_ID, userId).apply()
    }

    fun clearUserSettings(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_SAFE_BROWSING_API)
            .remove(KEY_VIRUS_TOTAL_API)
            .remove(KEY_WEBSHRINKER_API)
            .remove(KEY_AUTO_BLOCK)
            .remove(KEY_VIBRATE)
            .remove(KEY_CHILD_LOCK_ENABLED)
            .remove(KEY_CHILD_LOCK_PIN)
            .remove(KEY_PARENTAL_CONFIG)
            .remove(KEY_PARENTAL_LOGS)
            .remove(KEY_THEME)
            .apply()
    }
}
