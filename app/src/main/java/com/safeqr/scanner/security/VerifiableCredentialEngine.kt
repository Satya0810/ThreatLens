package com.safeqr.scanner.security

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * VerifiableCredentialEngine — W3C-style Verifiable Credential issuance and verification.
 *
 * Architecture mirrors CertificateEngine:
 *  1. An issuer creates a credential with subject claims (name, degree, etc.)
 *  2. The payload is HMAC-SHA256 signed to prevent tampering.
 *  3. The signed credential is Base64-encoded with a `threatlensvc://` prefix.
 *  4. When scanned, ThreatLens detects the prefix, decodes, and verifies the signature.
 *
 * Supported credential types: IDENTITY, DIPLOMA, MEMBERSHIP, HEALTH, EMPLOYMENT, LICENSE, CUSTOM
 */
object VerifiableCredentialEngine {

    // ── Constants ──────────────────────────────────────────────────────────────
    const val VC_SCHEME = "threatlensvc://"

    /** HMAC-SHA256 signing key. In production: load from Keystore or backend. */
    private const val SIGNING_KEY = "ThreatLens-VC-S1gn1ng-K3y-2025!@#"

    private val gson = Gson()

    // ── Data Classes (W3C-inspired structure) ─────────────────────────────────

    data class VcIssuer(
        val id: String,         // ThreatLens user ID of the issuer
        val name: String        // Display name of the issuer
    )

    data class VcProof(
        val type: String = "HmacSha256Signature2025",
        val created: Long,
        val proofPurpose: String = "assertionMethod",
        val signature: String
    )

    data class VerifiableCredential(
        val v: Int = 1,                                 // Schema version
        val vcId: String,                               // Unique credential ID
        val context: List<String> = listOf(
            "https://www.w3.org/2018/credentials/v1",
            "https://threatlens.app/credentials/v1"
        ),
        val type: List<String>,                         // e.g. ["VerifiableCredential", "DiplomaCredential"]
        val credentialType: String,                      // "IDENTITY", "DIPLOMA", etc.
        val issuer: VcIssuer,
        val issuanceDate: Long,
        val expirationDate: Long?,                      // null = no expiry
        val credentialSubject: Map<String, String>,      // Key-value claims
        val proof: VcProof
    )

    data class VerifyResult(
        val isValid: Boolean,
        val isTampered: Boolean,
        val isExpired: Boolean,
        val credential: VerifiableCredential?
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Issues a new Verifiable Credential and returns the encoded QR payload string.
     *
     * @param issuerId      ThreatLens user ID of the issuer.
     * @param issuerName    Display name of the issuer.
     * @param subjectClaims Key-value pairs of the credential subject (e.g., "name" to "John", "degree" to "B.Tech").
     * @param credentialType One of: IDENTITY, DIPLOMA, MEMBERSHIP, HEALTH, EMPLOYMENT, LICENSE, CUSTOM.
     * @param expirationDate Optional expiration timestamp in millis.
     * @return A string starting with [VC_SCHEME] ready to be encoded as a QR code.
     */
    fun issueCredential(
        issuerId: String,
        issuerName: String,
        subjectClaims: Map<String, String>,
        credentialType: String,
        expirationDate: Long? = null
    ): String {
        val vcId = generateVcId(issuerId, subjectClaims.toString())
        val now = System.currentTimeMillis()

        val vcTypeList = listOf("VerifiableCredential", "${credentialType}Credential")

        // Build unsigned credential
        val unsigned = VerifiableCredential(
            vcId = vcId,
            type = vcTypeList,
            credentialType = credentialType,
            issuer = VcIssuer(id = issuerId, name = issuerName),
            issuanceDate = now,
            expirationDate = expirationDate,
            credentialSubject = subjectClaims,
            proof = VcProof(created = now, signature = "")
        )

        // Sign
        val signature = sign(canonicalString(unsigned))
        val signed = unsigned.copy(proof = unsigned.proof.copy(signature = signature))

        // Encode
        val json = gson.toJson(signed)
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$VC_SCHEME$encoded"
    }

    /**
     * Verifies a Verifiable Credential QR payload.
     * Checks: prefix, Base64 decode, JSON parse, HMAC signature, and expiration.
     */
    fun verify(vcString: String): VerifyResult {
        if (!vcString.startsWith(VC_SCHEME)) {
            return VerifyResult(isValid = false, isTampered = false, isExpired = false, credential = null)
        }

        return try {
            val encoded = vcString.removePrefix(VC_SCHEME)
            val json = String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
            val vc = gson.fromJson(json, VerifiableCredential::class.java)
                ?: return VerifyResult(isValid = false, isTampered = false, isExpired = false, credential = null)

            // Re-compute expected signature
            val unsignedVc = vc.copy(proof = vc.proof.copy(signature = ""))
            val expected = sign(canonicalString(unsignedVc))
            val signatureValid = constantTimeEquals(expected, vc.proof.signature)

            // Check expiration
            val isExpired = vc.expirationDate != null && System.currentTimeMillis() > vc.expirationDate

            VerifyResult(
                isValid = signatureValid && !isExpired,
                isTampered = !signatureValid,
                isExpired = isExpired,
                credential = vc
            )
        } catch (e: JsonSyntaxException) {
            VerifyResult(isValid = false, isTampered = false, isExpired = false, credential = null)
        } catch (e: IllegalArgumentException) {
            // Base64 decode failure
            VerifyResult(isValid = false, isTampered = false, isExpired = false, credential = null)
        }
    }

    /** Quick check — is this string a ThreatLens Verifiable Credential? */
    fun isVerifiableCredential(content: String) = content.startsWith(VC_SCHEME)

    /**
     * Extracts a human-readable summary from a VC payload without full verification.
     * Useful for quick preview before opening the full card.
     */
    fun quickPeek(vcString: String): Map<String, String>? {
        if (!vcString.startsWith(VC_SCHEME)) return null
        return try {
            val encoded = vcString.removePrefix(VC_SCHEME)
            val json = String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
            val vc = gson.fromJson(json, VerifiableCredential::class.java) ?: return null
            mapOf(
                "vcId" to vc.vcId,
                "type" to vc.credentialType,
                "issuer" to vc.issuer.name,
                "subject" to (vc.credentialSubject["name"] ?: vc.credentialSubject.values.firstOrNull() ?: "Unknown")
            )
        } catch (e: Exception) {
            null
        }
    }

    // ── Private Helpers ────────────────────────────────────────────────────────

    private fun generateVcId(issuerId: String, claimsSummary: String): String {
        val bucket = (System.currentTimeMillis() / 1000).toString()
        val raw = hmacSha256(SIGNING_KEY, "$issuerId|$claimsSummary|$bucket")
        return "vc-${raw.take(12)}"
    }

    /** Canonical string — deterministic field order for signature computation. */
    private fun canonicalString(vc: VerifiableCredential): String {
        val claimsStr = vc.credentialSubject.entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}=${it.value}" }
        return "${vc.v}|${vc.vcId}|${vc.credentialType}|${vc.issuer.id}|${vc.issuer.name}|${vc.issuanceDate}|${vc.expirationDate}|$claimsStr"
    }

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
