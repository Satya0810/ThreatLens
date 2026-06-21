package com.safeqr.scanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeqr.scanner.data.PreferencesManager
import com.safeqr.scanner.data.remote.CloudSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    // ── Login Flow ───────────────────────────────────────────────────────────
    fun login(userId: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val cleanUserId = userId.trim()
            
            if (cleanUserId.isBlank() || pass.isBlank()) {
                _authState.value = AuthState.Error("User ID and password are required.")
                return@launch
            }

            val errorMessage = CloudSyncManager.loginUser(cleanUserId, pass)
            
            if (errorMessage == null) {
                completeLogin(cleanUserId)
            } else {
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    private suspend fun completeLogin(cleanUserId: String) {
        PreferencesManager.setCurrentUserId(getApplication(), cleanUserId)
        
        // Ensure local user entity exists and has latest email/age for Settings Profile Sync!
        val dao = com.safeqr.scanner.data.local.ScanDatabase.getInstance(getApplication()).userDao()
        var name = cleanUserId
        var email: String? = null
        var age: Int? = null
        
        if (cleanUserId != "guest_user") {
            try {
                val dbFirestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val sanitizedId = com.safeqr.scanner.data.remote.CloudSyncManager.sanitizeUserId(cleanUserId)
                val doc = dbFirestore.collection("users").document(sanitizedId).get().await()
                name = doc.getString("name") ?: cleanUserId
                email = doc.getString("email")
                age = doc.getLong("age")?.toInt()
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Error fetching user profile", e)
            }
        }
        
        val existing = dao.getUserById(cleanUserId)
        if (existing == null) {
            dao.insert(com.safeqr.scanner.data.model.UserEntity(
                userId = cleanUserId, 
                displayName = name, 
                email = email,
                age = age,
                phoneNumber = "", 
                password = "", 
                photoUrl = null
            ))
        } else {
            // Update existing with cloud data
            dao.insert(existing.copy(
                displayName = name,
                email = email ?: existing.email,
                age = age ?: existing.age
            ))
        }

        // Restore Cloud History
        val history = CloudSyncManager.fetchHistoryFromCloud(cleanUserId)
        val scanDao = com.safeqr.scanner.data.local.ScanDatabase.getInstance(getApplication()).scanDao()
        history.forEach { 
            scanDao.insert(com.safeqr.scanner.data.local.ScanEntity.fromScanResult(it)) 
        }

        _authState.value = AuthState.LoginSuccess(cleanUserId)
    }

    // ── Registration Flow ────────────────────────────────────────────────────
    
    fun register(name: String, age: String, userId: String, email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val safeId = userId.trim()
            val safeEmail = email.trim()
            
            if (safeId.isBlank() || safeEmail.isBlank() || pass.length < 6) {
                _authState.value = AuthState.Error("All fields are required and password must be 6+ chars.")
                return@launch
            }
            val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
            if (!emailRegex.matches(safeEmail)) {
                _authState.value = AuthState.Error("Please enter a valid email address.")
                return@launch
            }
            
            val errorMessage = CloudSyncManager.registerUser(name, age, safeId, safeEmail, pass)
            
            if (errorMessage == null) {
                // Do NOT auto-login, because they must verify their email first!
                _authState.value = AuthState.RegistrationSuccess
            } else {
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    // ── Reset Password Flow ──────────────────────────────────────────────────
    fun startForgotPassword(userId: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val safeId = userId.trim()
            
            if (safeId.isBlank()) {
                _authState.value = AuthState.Error("Please enter your User ID.")
                return@launch
            }
            
            val errorMessage = CloudSyncManager.sendPasswordResetEmail(safeId)
            if (errorMessage == null) {
                _authState.value = AuthState.PasswordResetEmailSent
            } else {
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    // ── Guest Login Flow ─────────────────────────────────────────────────────
    fun loginAsGuest() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            // Use a specific ID for guest
            val guestId = "guest_user"
            completeLogin(guestId)
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    // ── Email Verification Polling ───────────────────────────────────────────
    fun startEmailVerificationPolling(onVerified: () -> Unit) {
        viewModelScope.launch {
            while (true) {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    try {
                        user.reload().await()
                        if (user.isEmailVerified) {
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                            onVerified()
                            break
                        }
                    } catch (e: Exception) {
                        // ignore and continue polling
                    }
                }
                delay(3000)
            }
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object RegistrationSuccess : AuthState()
    object PasswordResetEmailSent : AuthState()
    data class LoginSuccess(val userId: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
