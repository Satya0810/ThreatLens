package com.safeqr.scanner.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * QrEncryptionEngine — AES-256-GCM password-based QR code encryption.
 *
 * Allows users to create password-protected QR codes. The content is encrypted
 * with a user-provided password using industry-standard cryptography:
 *
 * Key Derivation:  PBKDF2WithHmacSHA256 (10,000 iterations, 256-bit key)
 * Encryption:      AES-256-GCM (authenticated encryption with associated data)
 * Output Format:   threatlenslock://v1:{salt_b64}:{iv_b64}:{ciphertext_b64}
 *
 * Security properties:
 * - Each encryption uses a unique random salt + IV → same password + content = different ciphertext
 * - GCM mode provides both confidentiality AND integrity (tamper detection built-in)
 * - PBKDF2 with 10K iterations makes brute-force attacks costly
 * - No key material is stored anywhere — derived on-the-fly from password
 */
object QrEncryptionEngine {

    /** URI scheme that marks this QR as password-locked. */
    const val LOCK_SCHEME = "threatlenslock://"

    private const val LOCK_VERSION = "v1"
    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    // ══════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════

    /**
     * Encrypts QR content with a password.
     *
     * @param content   The raw QR payload to encrypt (URL, WiFi string, UPI, etc.)
     * @param password  User-chosen password/PIN
     * @return          A string starting with [LOCK_SCHEME] ready for QR encoding
     */
    fun encrypt(content: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val ciphertext = cipher.doFinal(content.toByteArray(Charsets.UTF_8))

        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP or Base64.URL_SAFE)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP or Base64.URL_SAFE)
        val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP or Base64.URL_SAFE)

        return "$LOCK_SCHEME$LOCK_VERSION:$saltB64:$ivB64:$ctB64"
    }

    /**
     * Decrypts a password-locked QR string.
     *
     * @param lockedContent The full `threatlenslock://...` string
     * @param password      The password to try
     * @return              [DecryptResult] with success/failure and decrypted content
     */
    fun decrypt(lockedContent: String, password: String): DecryptResult {
        if (!isLockedQr(lockedContent)) {
            return DecryptResult(success = false, content = null, error = "Not a locked QR code")
        }

        return try {
            val payload = lockedContent.removePrefix(LOCK_SCHEME)
            val parts = payload.split(":", limit = 4)
            if (parts.size != 4) {
                return DecryptResult(success = false, content = null, error = "Invalid locked QR format")
            }

            val version = parts[0]
            if (version != LOCK_VERSION) {
                return DecryptResult(success = false, content = null, error = "Unsupported version: $version")
            }

            val salt = Base64.decode(parts[1], Base64.NO_WRAP or Base64.URL_SAFE)
            val iv = Base64.decode(parts[2], Base64.NO_WRAP or Base64.URL_SAFE)
            val ciphertext = Base64.decode(parts[3], Base64.NO_WRAP or Base64.URL_SAFE)

            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            val plaintext = cipher.doFinal(ciphertext)
            val content = String(plaintext, Charsets.UTF_8)

            DecryptResult(success = true, content = content, error = null)
        } catch (e: javax.crypto.AEADBadTagException) {
            // Wrong password — GCM tag mismatch
            DecryptResult(success = false, content = null, error = "Incorrect password")
        } catch (e: Exception) {
            DecryptResult(success = false, content = null, error = "Decryption failed: ${e.message}")
        }
    }

    /** Quick check — is this string a password-locked QR? */
    fun isLockedQr(content: String) = content.startsWith(LOCK_SCHEME)

    // ══════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ══════════════════════════════════════════════════════════════════

    data class DecryptResult(
        val success: Boolean,
        val content: String?,
        val error: String?
    )

    // ══════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }
}
