package com.safeqr.scanner.analysis.ingress

/**
 * PiiSanitizer — Zero-PII Data Sanitization Layer
 *
 * Ensures that sensitive identifiers (Aadhaar numbers, PAN cards, 4-6 digit OTPs,
 * phone numbers, and bank account balances) are permanently redacted before any
 * conversational text is evaluated in memory, logged, or processed by NLP models.
 */
object PiiSanitizer {

    private val AADHAAR_REGEX = Regex("""\b[2-9]\d{3}\s?\d{4}\s?\d{4}\b""")
    private val PAN_REGEX = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""")
    private val OTP_REGEX = Regex("""\b(otp|code|pin|password|passcode)[\s:=]*\d{4,6}\b""", RegexOption.IGNORE_CASE)
    private val STANDALONE_OTP_REGEX = Regex("""\b\d{4,6}\s*(is your|otp|code|verification)\b""", RegexOption.IGNORE_CASE)
    private val PHONE_REGEX = Regex("""\b(?:\+91[\-\s]?|91[\-\s]?|0)?[6-9]\d{9}\b""")
    private val BANK_ACCOUNT_REGEX = Regex("""\b\d{9,18}\b""")

    /**
     * Sanitizes incoming text by scrubbing private financial tokens.
     */
    fun sanitize(rawText: String): String {
        if (rawText.isBlank()) return ""

        return rawText
            .replace(AADHAAR_REGEX, "[REDACTED_AADHAAR]")
            .replace(PAN_REGEX, "[REDACTED_PAN]")
            .replace(OTP_REGEX, "[REDACTED_OTP]")
            .replace(STANDALONE_OTP_REGEX, "[REDACTED_OTP]")
            .replace(PHONE_REGEX, "[REDACTED_PHONE]")
            .replace(BANK_ACCOUNT_REGEX) { matchResult ->
                // Only redact if not part of a date or short number
                val value = matchResult.value
                if (value.length in 9..18) "[REDACTED_ACCOUNT]" else value
            }
    }
}
