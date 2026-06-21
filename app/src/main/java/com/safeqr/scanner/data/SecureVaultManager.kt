package com.safeqr.scanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safeqr.scanner.data.model.ScanResult

object SecureVaultManager {

    private const val FILE_NAME = "secure_vault_prefs"
    private const val KEY_VAULT_ITEMS = "vault_items"

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToVault(context: Context, scanResult: ScanResult) {
        val prefs = getEncryptedPrefs(context)
        val gson = Gson()
        val currentListJson = prefs.getString(KEY_VAULT_ITEMS, "[]")
        
        val type = object : TypeToken<MutableList<ScanResult>>() {}.type
        val currentList: MutableList<ScanResult> = gson.fromJson(currentListJson, type) ?: mutableListOf()
        
        // Prevent duplicates
        if (currentList.none { it.rawContent == scanResult.rawContent }) {
            currentList.add(scanResult)
            prefs.edit().putString(KEY_VAULT_ITEMS, gson.toJson(currentList)).apply()
        }
    }

    fun getVaultItems(context: Context): List<ScanResult> {
        val prefs = getEncryptedPrefs(context)
        val gson = Gson()
        val currentListJson = prefs.getString(KEY_VAULT_ITEMS, "[]")
        val type = object : TypeToken<List<ScanResult>>() {}.type
        return gson.fromJson(currentListJson, type) ?: emptyList()
    }
    
    fun removeFromVault(context: Context, rawContent: String) {
        val prefs = getEncryptedPrefs(context)
        val gson = Gson()
        val currentListJson = prefs.getString(KEY_VAULT_ITEMS, "[]")
        
        val type = object : TypeToken<MutableList<ScanResult>>() {}.type
        val currentList: MutableList<ScanResult> = gson.fromJson(currentListJson, type) ?: mutableListOf()
        
        currentList.removeAll { it.rawContent == rawContent }
        prefs.edit().putString(KEY_VAULT_ITEMS, gson.toJson(currentList)).apply()
    }
}
