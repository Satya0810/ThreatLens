package com.safeqr.scanner.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.safeqr.scanner.data.model.ScanResult
import kotlinx.coroutines.tasks.await

object CloudSyncManager {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // Helper to strictly sanitize User IDs
    fun sanitizeUserId(userId: String): String {
        return userId.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun getEmailFromUserId(userId: String): String {
        val sanitized = sanitizeUserId(userId)
        return "$sanitized@threatlens.app"
    }

    /**
     * Registers a user in Firebase Authentication.
     * Returns null on success, or an error message string on failure.
     */
    suspend fun registerUser(name: String, age: String, userId: String, email: String, pass: String): String? {
        return try {
            if (pass.length < 6) return "Firebase requires a password of at least 6 characters."
            
            // Check if email or userId is already taken in firestore?
            val doc = db.collection("users").document(sanitizeUserId(userId)).get().await()
            if (doc.exists()) {
                return "This User ID is already taken!"
            }

            val authResult = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            if (authResult.user != null) {
                // Send verification email
                authResult.user?.sendEmailVerification()?.await()
                
                val ageInt = age.toIntOrNull()
                val userMap = hashMapOf(
                    "name" to name.trim(),
                    "age" to ageInt,
                    "email" to email.trim(),
                    "userId" to userId.trim(),
                    "createdAt" to System.currentTimeMillis()
                )
                db.collection("users").document(sanitizeUserId(userId)).set(userMap).await()
                null // Success
            } else {
                "Unknown registration error."
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            "This email is already in use by another account."
        } catch (e: com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
            "Password is too weak. Please use at least 6 characters."
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            "The email address is invalid."
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to register user: ${e.message}")
            e.message ?: "Registration failed. Check your network."
        }
    }

    /**
     * Verifies a user's login against Firebase Authentication.
     * Returns null on success, or an error message string on failure.
     */
    suspend fun loginUser(userId: String, pass: String): String? {
        return try {
            // Lookup email by userId
            val doc = db.collection("users").document(sanitizeUserId(userId)).get().await()
            if (!doc.exists()) {
                return "User ID not found! Please register first."
            }
            
            val email = doc.getString("email")
            if (email.isNullOrEmpty()) {
                return "This account does not have an email address linked."
            }

            val authResult = auth.signInWithEmailAndPassword(email, pass).await()
            if (authResult.user != null) {
                if (authResult.user?.isEmailVerified == false) {
                    return "Please verify your email address before logging in. Check your inbox!"
                }
                null
            } else {
                "Unknown login error."
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            "User ID not found! Please register first."
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            "Incorrect password! Please try again or use Forgot Password."
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to login user: ${e.message}")
            "Incorrect password or User ID."
        }
    }

    /**
     * Retrieves the user's phone number from Firestore for OTP verification.
     */
    
    suspend fun sendPasswordResetEmail(userId: String): String? {
        return try {
            val doc = db.collection("users").document(sanitizeUserId(userId)).get().await()
            if (!doc.exists()) {
                return "User ID not found!"
            }
            val email = doc.getString("email")
            if (email.isNullOrEmpty()) {
                return "This account does not have an email address linked."
            }
            auth.sendPasswordResetEmail(email).await()
            null // Success
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to send reset email: ${e.message}")
            e.message ?: "Failed to send password reset email."
        }
    }

    suspend fun getUserTotpSecret(userId: String): String? {
        return try {
            val doc = db.collection("users").document(sanitizeUserId(userId)).get().await()
            if (doc.exists()) {
                doc.getString("totpSecret")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to get user secret: ${e.message}")
            null
        }
    }

    /**
     * Checks if a user exists by attempting to fetch their auth providers.
     */
    suspend fun userExists(userId: String): Boolean {
        return try {
            val email = getEmailFromUserId(userId)
            val result = auth.fetchSignInMethodsForEmail(email).await()
            !result.signInMethods.isNullOrEmpty()
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to check user existence: ${e.message}")
            false
        }
    }

    /**
     * Resets a user's password in Firebase Authentication.
     * Note: Since we use pseudo-emails, a real password reset requires admin SDK or updating the password while logged in.
     * For this demo app using phone numbers, we will update Firestore to flag a manual reset if necessary,
     * but true Firebase password reset links require valid emails.
     */
    suspend fun resetPassword(userId: String, newPass: String): Boolean {
        return try {
            db.collection("users").document(sanitizeUserId(userId))
                .update("overridePassword", newPass).await()
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to reset password: ${e.message}")
            false
        }
    }

    /**
     * Reports a URL to the global Firebase Firestore community database.
     */
    suspend fun reportWebsite(url: String, issue: String): Boolean {
        return try {
            val sanitizedUrl = url.replace(Regex("[^a-zA-Z0-9]"), "_")
            val reportRef = db.collection("reports").document(sanitizedUrl)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(reportRef)
                val currentReasons = if (snapshot.exists()) {
                    val existing = snapshot.get("reasons") as? List<*>
                    existing?.mapNotNull { it?.toString() }?.toMutableList() ?: mutableListOf()
                } else {
                    mutableListOf()
                }
                currentReasons.add(issue)
                transaction.set(reportRef, hashMapOf("reasons" to currentReasons), SetOptions.merge())
            }.await()
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to report website: ${e.message}")
            false
        }
    }

    /**
     * Logs a visit to a URL.
     */
    suspend fun logVisit(url: String) {
        try {
            val sanitizedUrl = url.replace(Regex("[^a-zA-Z0-9]"), "_")
            val reportRef = db.collection("reports").document(sanitizedUrl)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(reportRef)
                val currentVisits = if (snapshot.exists()) {
                    snapshot.getLong("visitCount") ?: 0L
                } else {
                    0L
                }
                transaction.set(reportRef, hashMapOf("visitCount" to currentVisits + 1), SetOptions.merge())
            }.await()
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to log visit: ${e.message}")
        }
    }

    /**
     * Gets community reports for a given URL from Firestore.
     * Returns Pair<reasons_size, reasons_list, visit_count>
     */
    suspend fun getCommunityReports(url: String): Triple<Int, List<String>, Long> {
        return try {
            val sanitizedUrl = url.replace(Regex("[^a-zA-Z0-9]"), "_")
            val document = db.collection("reports").document(sanitizedUrl).get().await()
            
            if (document.exists()) {
                val reasons = document.get("reasons") as? List<String> ?: emptyList()
                val visits = document.getLong("visitCount") ?: 0L
                Triple(reasons.size, reasons, visits)
            } else {
                Triple(0, emptyList(), 0L)
            }
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to get reports: ${e.message}")
            Triple(0, emptyList(), 0L)
        }
    }

    
    // ── Cloud History Sync ───────────────────────────────────────────────

    suspend fun syncScanToCloud(userId: String, scanResult: ScanResult): Boolean {
        if (userId == "guest_user") return true // Silently ignore cloud sync for guests
        return try {
            val sanitized = sanitizeUserId(userId)
            val json = Gson().toJson(scanResult)
            
            // We use the rawContent hash as the document ID to prevent duplicate history entries
            val docId = scanResult.rawContent.hashCode().toString()
            val map = hashMapOf(
                "timestamp" to scanResult.timestamp,
                "data" to json
            )
            
            db.collection("users").document(sanitized)
              .collection("history").document(docId)
              .set(map, SetOptions.merge())
              .await()
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to sync scan to cloud: ${e.message}")
            false
        }
    }

    suspend fun fetchHistoryFromCloud(userId: String): List<ScanResult> {
        if (userId == "guest_user") return emptyList()
        return try {
            val sanitized = sanitizeUserId(userId)
            val snapshot = db.collection("users").document(sanitized)
                             .collection("history")
                             .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                             .get().await()
                             
            val results = mutableListOf<ScanResult>()
            val gson = Gson()
            for (doc in snapshot.documents) {
                val json = doc.getString("data")
                if (json != null) {
                    try {
                        val result = gson.fromJson(json, ScanResult::class.java)
                        results.add(result)
                    } catch (e: Exception) {
                        Log.e("CloudSync", "Failed to parse scan history item: ${e.message}")
                    }
                }
            }
            results
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to fetch history from cloud: ${e.message}")
            emptyList()
        }
    }


    suspend fun deleteUserAccount(userId: String): Boolean {
        return try {
            val sanitized = sanitizeUserId(userId)
            val userRef = db.collection("users").document(sanitized)
            
            // Delete history subcollection documents
            val historyDocs = userRef.collection("history").get().await()
            db.runBatch { batch ->
                for (doc in historyDocs) {
                    batch.delete(doc.reference)
                }
                // Delete user document
                batch.delete(userRef)
            }.await()
            
            // Delete user from Firebase Authentication
            auth.currentUser?.delete()?.await()
            
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to delete account: ${e.message}")
            false
        }
    }


    suspend fun signInWithGoogle(idToken: String): String? {
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user
            
            if (user != null) {
                // Save user info to firestore if new
                val userRef = db.collection("users").document(sanitizeUserId(user.uid))
                val doc = userRef.get().await()
                if (!doc.exists()) {
                    val userMap = hashMapOf(
                        "userId" to user.uid,
                        "email" to user.email,
                        "displayName" to user.displayName,
                        "photoUrl" to user.photoUrl?.toString(),
                        "createdAt" to System.currentTimeMillis()
                    )
                    userRef.set(userMap).await()
                }
                null // null means success
            } else {
                "Failed to get user after Google Sign-In"
            }
        } catch (e: Exception) {
            Log.e("CloudSync", "Google Sign-In failed: ${e.message}")
            e.localizedMessage ?: "Google Sign-In Failed"
        }
    }

    // ── Global Cloud Cache ───────────────────────────────────────────────

    suspend fun cacheGlobalScan(scanResult: ScanResult): Boolean {
        return try {
            val json = Gson().toJson(scanResult)
            val docId = scanResult.rawContent.hashCode().toString()
            val map = hashMapOf(
                "timestamp" to scanResult.timestamp,
                "data" to json
            )
            
            db.collection("global_scans").document(docId).set(map, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to cache global scan: ${e.message}")
            false
        }
    }

    suspend fun getGlobalCachedScan(url: String): ScanResult? {
        return try {
            val docId = url.hashCode().toString()
            val doc = db.collection("global_scans").document(docId).get().await()
            
            if (doc.exists()) {
                val json = doc.getString("data")
                if (json != null) {
                    val result = Gson().fromJson(json, ScanResult::class.java)
                    // Only use cache if it's less than 24 hours old
                    if (System.currentTimeMillis() - result.timestamp < 24 * 60 * 60 * 1000) {
                        return result
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to get global cached scan: ${e.message}")
            null
        }
    }

    // ── Event Ticketing & Gatekeeper System (Simulated Cloud) ─────────────
    
    // In a real app, this would be backed by Firestore or Supabase
    // For now, we simulate an in-memory database to demonstrate the architecture
    private val cloudTickets = mutableMapOf<String, com.safeqr.scanner.data.model.CloudEventTicket>()

    /**
     * Issues a cryptographic ticket to the cloud database.
     */
    suspend fun issueTicket(ticket: com.safeqr.scanner.data.model.CloudEventTicket): Boolean {
        // Simulate network delay
        kotlinx.coroutines.delay(800)
        cloudTickets[ticket.ticketId] = ticket
        return true
    }

    /**
     * Gatekeeper validates a ticket. Checks signature, TOTP (if provided), and scan status.
     */
    class NetworkException(message: String) : Exception(message)
    class RoleException(message: String) : Exception(message)

    /**
     * Extracts TOTP signature validation so it can be used locally for offline fallback.
     */
    fun verifyTotpSignature(ticketId: String, signature: String): Boolean {
        val timeSlice = System.currentTimeMillis() / 30000
        val raw = "$ticketId:$timeSlice"
        val expectedHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(8)
            
        val prevRaw = "$ticketId:${timeSlice - 1}"
        val prevHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(prevRaw.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(8)

        return signature == expectedHash || signature == prevHash
    }

    /**
     * Gatekeeper validates a ticket. Checks role in Firestore, signature, and entry/exit status.
     */
    suspend fun validateTicket(ticketId: String, gatekeeperId: String, signature: String, isEntryMode: Boolean): com.safeqr.scanner.data.model.CloudEventTicket? {
        // Gatekeeper Role Verification via Firestore
        try {
            val sanitizedGatekeeperId = sanitizeUserId(gatekeeperId)
            val doc = db.collection("users").document(sanitizedGatekeeperId).get().await()
            if (!doc.exists() || doc.getBoolean("isGatekeeper") != true) {
                throw RoleException("Unauthorized: You do not have Gatekeeper privileges.")
            }
        } catch (e: Exception) {
            if (e is RoleException) throw e
            // If network fails, throw NetworkException to trigger offline fallback
            Log.e("CloudSync", "Network error verifying gatekeeper: ${e.message}")
            throw NetworkException("Network error verifying gatekeeper")
        }

        // Simulate network for the in-memory ticket fetch
        kotlinx.coroutines.delay(600)
        
        val ticket = cloudTickets[ticketId] ?: return null
        
        // Anti-screenshot TOTP Check
        if (!verifyTotpSignature(ticketId, signature)) {
            // Invalid signature! (Could be expired screenshot)
            return null
        }
        
        // Check if ticket is already scanned to prevent double entry
        if (isEntryMode) {
            if (ticket.isScanned) {
                throw Exception("Ticket already checked in!")
            }
            // Mark as scanned
            val updatedTicket = ticket.copy(
                isScanned = true,
                scannedAt = System.currentTimeMillis()
            )
            cloudTickets[ticketId] = updatedTicket
            return updatedTicket
        } else {
            // Exit Mode
            if (!ticket.isScanned) {
                throw Exception("Guest has already checked out or never checked in.")
            }
            // Mark as checked out
            val updatedTicket = ticket.copy(
                isScanned = false,
                scannedAt = System.currentTimeMillis()
            )
            cloudTickets[ticketId] = updatedTicket
            return updatedTicket
        }
    }

    fun getTicketStatus(ticketId: String): com.safeqr.scanner.data.model.CloudEventTicket? {
        return cloudTickets[ticketId]
    }

    // ── AI HIVE MIND (FEDERATED LEARNING) ──────────────────────────────────────────

    /**
     * Uploads locally updated Neural Network weights to the global Hive Mind.
     */
    suspend fun reportLearnedWeights(weights: Map<String, Float>) {
        try {
            // Push to a central 'global_ai_core' collection under the user's ID
            // In a real federated system, a backend Cloud Function would average all users' weights.
            val userId = auth.currentUser?.uid ?: return // Do not upload weights for guest users
            db.collection("global_ai_core").document(userId).set(weights).await()
            Log.d("CloudSync", "Hive Mind updated with local learnings.")
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to upload learned weights to Hive Mind: ${e.message}")
        }
    }

    /**
     * Fetches the aggregated Neural Network weights from the Hive Mind.
     * (Simulated by pulling all users' weights and averaging them on the client for demonstration).
     */
    suspend fun fetchGlobalWeights(): Map<String, Float> {
        return try {
            val querySnapshot = db.collection("global_ai_core").get().await()
            if (querySnapshot.isEmpty) return emptyMap()

            val aggregatedWeights = mutableMapOf<String, Float>()
            val counts = mutableMapOf<String, Int>()

            for (doc in querySnapshot.documents) {
                val data = doc.data ?: continue
                for ((key, value) in data) {
                    val weight = (value as? Number)?.toFloat() ?: continue
                    aggregatedWeights[key] = (aggregatedWeights[key] ?: 0.0f) + weight
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }

            // Calculate averages
            aggregatedWeights.mapValues { (key, totalSum) ->
                totalSum / (counts[key] ?: 1).toFloat()
            }
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to fetch Hive Mind weights: ${e.message}")
            emptyMap()
        }
    }
}
