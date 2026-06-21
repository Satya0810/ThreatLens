package com.safeqr.scanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeqr.scanner.data.PreferencesManager
import com.safeqr.scanner.data.local.ScanDatabase
import com.safeqr.scanner.data.model.DynamicQrEntity
import com.safeqr.scanner.data.model.SharedQrEvent
import com.safeqr.scanner.data.model.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlinx.coroutines.launch
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class QrViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ScanDatabase.getInstance(application)
    private val dynamicQrDao = db.dynamicQrDao()
    private val userDao = db.userDao()
    private val sharedQrDao = db.sharedQrDao()

    // ── Dynamic QRs ────────────────────────────────────────────────────────
    val dynamicQrs: StateFlow<List<DynamicQrEntity>> = dynamicQrDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createDynamicQr(title: String, targetUrl: String, expiryDays: Int?, maxScans: Int?, activeFrom: Long? = null, expiresAt: Long? = null, passwordHash: String? = null, alternateUrls: String? = null): String {
        val shortCode = generateShortCode()
        val finalExpiresAt = expiresAt ?: expiryDays?.let { System.currentTimeMillis() + it * 86400000L }
        viewModelScope.launch {
            dynamicQrDao.insert(
                DynamicQrEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    shortCode = shortCode,
                    targetUrl = targetUrl,
                    expiresAt = finalExpiresAt,
                    activeFrom = activeFrom,
                    maxScans = maxScans,
                    passwordHash = passwordHash,
                    alternateUrls = alternateUrls
                )
            )
        }
        return shortCode
    }

    fun updateDynamicQr(id: String, newUrl: String, newExpiryDays: Int?, newMaxScans: Int?, newActiveFrom: Long? = null) {
        viewModelScope.launch {
            val qr = dynamicQrDao.getById(id)
            if (qr != null) {
                val expiresAt = newExpiryDays?.let { System.currentTimeMillis() + it * 86400000L }
                dynamicQrDao.update(qr.copy(targetUrl = newUrl, expiresAt = expiresAt, maxScans = newMaxScans, activeFrom = newActiveFrom))
            }
        }
    }

    fun updateDynamicQrStatus(id: String, active: Boolean) {
        viewModelScope.launch {
            val qr = dynamicQrDao.getById(id)
            if (qr != null) {
                dynamicQrDao.update(qr.copy(isActive = active))
            }
        }
    }

    fun incrementScan(id: String) {
        viewModelScope.launch { 
            dynamicQrDao.incrementScan(id) 
            val qr = dynamicQrDao.getById(id)
            if (qr != null && qr.maxScans != null && qr.scanCount >= qr.maxScans) {
                dynamicQrDao.update(qr.copy(isActive = false))
            }
        }
    }

    fun deleteDynamicQr(id: String) {
        viewModelScope.launch { dynamicQrDao.delete(id) }
    }

    private fun generateShortCode(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    // ── User account ───────────────────────────────────────────────────────
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser

    init {
        refreshUser()
    }

    fun refreshUser() {
        viewModelScope.launch {
            val currentId = PreferencesManager.getCurrentUserId(getApplication())
            if (currentId != null) {
                _currentUser.value = userDao.getUserById(currentId)
            } else {
                _currentUser.value = null
            }
        }
    }

    // Real Email OTP flow using Firebase Cloud Functions backend
    private val client = okhttp3.OkHttpClient()
    
    fun requestEmailOtp(identifier: String, onOtpSent: (Boolean) -> Unit) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            val fetchEmail = { onSuccess: (String) -> Unit, onFailure: () -> Unit ->
                if (identifier.contains("@")) {
                    onSuccess(identifier)
                } else {
                    val sanitizedId = com.safeqr.scanner.data.remote.CloudSyncManager.sanitizeUserId(identifier)
                    db.collection("users").document(sanitizedId).get()
                        .addOnSuccessListener { doc ->
                            val email = doc.getString("email")
                            if (!email.isNullOrBlank()) onSuccess(email) else onFailure()
                        }
                        .addOnFailureListener { onFailure() }
                }
            }

            fetchEmail({ actualEmail ->
                val generatedOtp = (100000..999999).random().toString()
                val otpData = hashMapOf(
                    "otp" to generatedOtp,
                    "expiresAt" to System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes
                )
                
                db.collection("otps").document(actualEmail).set(otpData).addOnSuccessListener {
                    // Trigger Firebase Email Extension
                    val mailData = hashMapOf(
                        "to" to actualEmail,
                        "message" to hashMapOf(
                            "subject" to "ThreatLens Account Deletion OTP",
                            "text" to "Your secure OTP code for account deletion is: $generatedOtp. This code expires in 5 minutes.",
                            "html" to "<h3>ThreatLens Security</h3><p>Your secure OTP code for account deletion is: <strong style='font-size:24px; color:#00F0FF;'>$generatedOtp</strong></p><p>This code expires in 5 minutes. If you did not request this, please ignore this email.</p>"
                        )
                    )
                    db.collection("mail").add(mailData).addOnSuccessListener {
                        android.util.Log.d("QrViewModel", "Email queued for Trigger Email Extension")
                        onOtpSent(true)
                    }.addOnFailureListener {
                        onOtpSent(false)
                    }
                }.addOnFailureListener {
                    onOtpSent(false)
                }
            }, {
                onOtpSent(false)
            })
        } catch (e: Exception) {
            onOtpSent(false)
        }
    }
    
    fun verifyOtp(identifier: String, otpCode: String, onResult: (Boolean) -> Unit) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            val fetchEmail = { onSuccess: (String) -> Unit, onFailure: () -> Unit ->
                if (identifier.contains("@")) {
                    onSuccess(identifier)
                } else {
                    val sanitizedId = com.safeqr.scanner.data.remote.CloudSyncManager.sanitizeUserId(identifier)
                    db.collection("users").document(sanitizedId).get()
                        .addOnSuccessListener { doc ->
                            val email = doc.getString("email")
                            if (!email.isNullOrBlank()) onSuccess(email) else onFailure()
                        }
                        .addOnFailureListener { onFailure() }
                }
            }

            fetchEmail({ actualEmail ->
                db.collection("otps").document(actualEmail).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val savedOtp = document.getString("otp")
                            val expiresAt = document.getLong("expiresAt") ?: 0L
                            
                            if (savedOtp == otpCode && System.currentTimeMillis() < expiresAt) {
                                onResult(true)
                            } else {
                                onResult(false)
                            }
                        } else {
                            onResult(false)
                        }
                    }
                    .addOnFailureListener {
                        onResult(false)
                    }
            }, {
                onResult(false)
            })
        } catch (e: Exception) {
            onResult(false)
        }
    }



    fun updateUser(displayName: String) {
        viewModelScope.launch {
            val current = _currentUser.value
            if (current != null) {
                val updated = current.copy(displayName = displayName)
                userDao.insert(updated)
                _currentUser.value = updated
            }
        }
    }


    fun deleteAccount(userId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            // 1. Delete from Cloud
            val success = com.safeqr.scanner.data.remote.CloudSyncManager.deleteUserAccount(userId)
            
            if (success) {
                // 2. Delete from Local DB
                val user = userDao.getUserById(userId)
                if (user != null) {
                    userDao.delete(user)
                }
                
                // Clear settings and history
                PreferencesManager.clearUserSettings(getApplication())
                ScanDatabase.getInstance(getApplication()).scanDao().clearAll()
                
                // 3. Sign Out
                PreferencesManager.setCurrentUserId(getApplication(), null)

                _currentUser.value = null
            }
            
            onComplete(success)
        }
    }

    fun verifyPasswordAndDelete(userId: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val error = com.safeqr.scanner.data.remote.CloudSyncManager.loginUser(userId, pass)
            if (error == null) {
                deleteAccount(userId) { success ->
                    if (success) {
                        onResult(true, null)
                    } else {
                        onResult(false, "Failed to delete from cloud")
                    }
                }
            } else {
                onResult(false, error)
            }
        }
    }

    fun signOut() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // Clear settings
            PreferencesManager.clearUserSettings(getApplication())
            // Clear history
            ScanDatabase.getInstance(getApplication()).scanDao().clearAll()
            
            // Clear auth session
            PreferencesManager.setCurrentUserId(getApplication(), null)
            _currentUser.value = null
        }
    }

    private fun generateUserId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val code = (1..4).map { chars.random() }.joinToString("")
        return "TL-$code"
    }

    // ── Shared QR Events ───────────────────────────────────────────────────
    val sharedEvents: StateFlow<List<SharedQrEvent>> = sharedQrDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun shareQrToUser(fromUserId: String, toUserId: String, eventName: String, ticketId: String? = null) {
        viewModelScope.launch {
            sharedQrDao.insert(
                SharedQrEvent(
                    eventId = UUID.randomUUID().toString(),
                    qrId = ticketId ?: UUID.randomUUID().toString(),
                    fromUserId = fromUserId,
                    toUserId = toUserId,
                    eventName = eventName
                )
            )
        }
    }
}
