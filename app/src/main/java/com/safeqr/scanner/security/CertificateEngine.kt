package com.safeqr.scanner.security

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * CertificateEngine — cryptographic certificate embed + verify for ThreatLens QR codes.
 *
 * How it works:
 *  1. After safety analysis, we build a JSON certificate payload containing
 *     the original content, safety score, status, cert ID, and timestamp.
 *  2. The payload is HMAC-SHA256 signed with a secret key that only ThreatLens holds.
 *  3. The signed payload is Base64-encoded and prefixed with CERT_SCHEME,
 *     then encoded as a NEW QR code — the certificate lives INSIDE the QR data.
 *  4. When ThreatLens scans this QR, it detects the prefix, decodes and verifies
 *     the signature. Tampered payloads fail MAC validation.
 *
 * Security level:
 *   - Visual stamp only            → ❌ screenshottable, trivially fakeable
 *   - Embedded cert ID (no sig)   → ✅ hard to fake without knowing ID space
 *   - HMAC-SHA256 signature        → ✅✅ computationally infeasible to forge
 *
 * The key is never transmitted or stored on-device outside this file.
 * For production, replace SIGNING_KEY with a value loaded from a secure backend
 * or Android Keystore. The compile-time key demonstrates the cryptographic
 * principle; rotate it before deploying to end users.
 */
object CertificateEngine {

    // ── Constants ──────────────────────────────────────────────────────────────

    /** URI scheme that marks this QR as a ThreatLens certificate. */
    const val CERT_SCHEME = "threatlenscert://"

    /** HMAC-SHA256 signing key.  In production: load from Keystore or backend. */
    private const val SIGNING_KEY = "ThreatLens-S3cur3-S1gn1ng-K3y-2025!@#"

    private val gson = Gson()

    // ── Data classes ───────────────────────────────────────────────────────────

    data class CertPayload(
        val v: Int = 1,                 // schema version
        val id: String,                 // unique cert ID  (8-char hex)
        val content: String,            // original URL / text the user encoded
        val status: String,             // "SAFE" | "CAUTION" | "MALICIOUS" | "UNKNOWN"
        val score: Int,                 // 0–100
        val ts: Long,                   // unix millis
        val sig: String = ""            // HMAC-SHA256 over all other fields, base64
    )

    data class VerifyResult(
        val isValid: Boolean,
        val isTampered: Boolean,        // payload parsed but signature mismatch
        val payload: CertPayload?
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Build a certified QR content string that embeds the certificate inside the QR data.
     *
     * @param originalContent  The raw text/URL the user wants to encode.
     * @param safetyStatus     e.g. "SAFE", "CAUTION", "MALICIOUS"
     * @param score            0–100 safety score
     * @return  A string starting with [CERT_SCHEME] ready to be passed to the QR encoder.
     */
    fun buildCertifiedPayload(
        originalContent: String,
        safetyStatus: String,
        score: Int
    ): String {
        val certId = generateCertId(originalContent)
        val ts = System.currentTimeMillis()

        // Build unsigned payload first to compute signature over canonical fields
        val unsigned = CertPayload(
            id = certId,
            content = originalContent,
            status = safetyStatus,
            score = score,
            ts = ts
        )
        val signature = sign(canonicalString(unsigned))
        val signed = unsigned.copy(sig = signature)
        val json = gson.toJson(signed)
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$CERT_SCHEME$encoded"
    }

    /**
     * Verify a string that was produced by [buildCertifiedPayload].
     * Call this when the scanner sees [CERT_SCHEME] as a prefix.
     */
    fun verify(certString: String): VerifyResult {
        if (!certString.startsWith(CERT_SCHEME)) {
            return VerifyResult(isValid = false, isTampered = false, payload = null)
        }
        return try {
            val encoded = certString.removePrefix(CERT_SCHEME)
            val json = String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
            val payload = gson.fromJson(json, CertPayload::class.java)
                ?: return VerifyResult(isValid = false, isTampered = false, payload = null)

            // Re-compute expected signature over all non-sig fields
            val expected = sign(canonicalString(payload.copy(sig = "")))
            val valid = constantTimeEquals(expected, payload.sig)

            VerifyResult(
                isValid = valid,
                isTampered = !valid,   // parsed OK but sig wrong → tampered
                payload = if (valid) payload else payload  // return payload either way for UI
            )
        } catch (e: JsonSyntaxException) {
            VerifyResult(isValid = false, isTampered = false, payload = null)
        } catch (e: IllegalArgumentException) {
            // Base64 decode failure
            VerifyResult(isValid = false, isTampered = false, payload = null)
        }
    }

    /** Quick check — is this string a ThreatLens certificate? */
    fun isCertifiedQr(content: String) = content.startsWith(CERT_SCHEME)

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Deterministic cert ID: first 8 hex chars of HMAC over (content + current-second bucket).
     * Same content + same second = same ID; different second = different ID.
     */
    private fun generateCertId(content: String): String {
        val bucket = (System.currentTimeMillis() / 1000).toString()
        val raw = hmacSha256(SIGNING_KEY, "$content|$bucket")
        return raw.take(8)
    }

    /** Canonical string for signing — deterministic field order, no sig field. */
    private fun canonicalString(p: CertPayload) =
        "${p.v}|${p.id}|${p.content}|${p.status}|${p.score}|${p.ts}"

    private fun sign(data: String): String = hmacSha256(SIGNING_KEY, data)

    private fun hmacSha256(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    /** Constant-time string comparison — prevents timing attacks. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
