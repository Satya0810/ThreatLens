package com.safeqr.scanner.analysis

enum class QrDataType {
    URL, WIFI, VCARD, EMAIL, SMS, PHONE, TEXT, EVENT, LOCATION, CRYPTO, TICKET, PAYMENT, APP_STORE, AUTHENTICATOR, ESIM, PROVISIONING, LOCKED, VERIFIABLE_CREDENTIAL
}

data class ParsedQrData(
    val type: QrDataType,
    val title: String,
    val primaryText: String,
    val secondaryText: String? = null,
    val rawData: String,
    val actionData: Map<String, String> = emptyMap(),
    // Certificate fields — populated when the QR was generated with ThreatLens Certification
    val isCertified: Boolean = false,
    val certScore: Int? = null,
    val certStatus: String? = null,
    val certId: String? = null,
    val isTampered: Boolean = false
)

object QrDataParser {
    
    fun parse(rawContent: String): ParsedQrData {
        val content = rawContent.trim()
        
        // ── Check for ThreatLens Certified QR ──────────────────────────
        // Certified QRs wrap the original payload (vCard, URL, etc.) inside a
        // signed Base64 envelope. We unwrap it, verify the signature, then
        // parse the inner content so the user sees a proper contact card / link
        // instead of raw Base64 gibberish.
        if (com.safeqr.scanner.security.CertificateEngine.isCertifiedQr(content)) {
            val result = com.safeqr.scanner.security.CertificateEngine.verify(content)
            if (result.payload != null) {
                // Recursively parse the ORIGINAL inner content (e.g. the vCard, URL, geo:, etc.)
                val innerParsed = parse(result.payload.content)
                // Return the inner parsed data but with certification metadata attached
                return innerParsed.copy(
                    isCertified = result.isValid,
                    certScore = result.payload.score,
                    certStatus = result.payload.status,
                    certId = result.payload.id,
                    isTampered = result.isTampered,
                    rawData = result.payload.content // Show the real content, not the Base64 envelope
                )
            }
            // If verification completely failed (corrupt data), fall through to plain text
        }

        // Check for Password-Protected (Locked) QR
        if (com.safeqr.scanner.security.QrEncryptionEngine.isLockedQr(content)) {
            return ParsedQrData(
                type = QrDataType.LOCKED,
                title = "🔒 Password-Protected QR",
                primaryText = "This QR code is encrypted",
                secondaryText = "Enter password to unlock and view contents",
                rawData = content,
                actionData = mapOf("locked" to "true")
            )
        }

        // Check for Verifiable Credential
        if (com.safeqr.scanner.security.VerifiableCredentialEngine.isVerifiableCredential(content)) {
            val vcResult = com.safeqr.scanner.security.VerifiableCredentialEngine.verify(content)
            val vc = vcResult.credential
            if (vc != null) {
                val typeIcon = when (vc.credentialType) {
                    "IDENTITY" -> "🆔"
                    "DIPLOMA" -> "🎓"
                    "MEMBERSHIP" -> "🏅"
                    "HEALTH" -> "🏥"
                    "EMPLOYMENT" -> "💼"
                    "LICENSE" -> "📜"
                    else -> "📋"
                }
                val statusText = when {
                    vcResult.isTampered -> "❌ TAMPERED"
                    vcResult.isExpired -> "⏰ EXPIRED"
                    vcResult.isValid -> "✅ VERIFIED"
                    else -> "❓ UNKNOWN"
                }
                val subjectName = vc.credentialSubject["name"] ?: vc.credentialSubject.values.firstOrNull() ?: "Unknown"
                val actionData = mutableMapOf(
                    "vcId" to vc.vcId,
                    "credentialType" to vc.credentialType,
                    "issuerName" to vc.issuer.name,
                    "issuerId" to vc.issuer.id,
                    "subjectName" to subjectName,
                    "isValid" to vcResult.isValid.toString(),
                    "isTampered" to vcResult.isTampered.toString(),
                    "isExpired" to vcResult.isExpired.toString(),
                    "issuanceDate" to vc.issuanceDate.toString()
                )
                vc.expirationDate?.let { actionData["expirationDate"] = it.toString() }
                vc.credentialSubject.forEach { (k, v) -> actionData["claim_$k"] = v }

                return ParsedQrData(
                    type = QrDataType.VERIFIABLE_CREDENTIAL,
                    title = "$typeIcon Verifiable Credential",
                    primaryText = "${vc.credentialType.replace("_", " ")} — $subjectName",
                    secondaryText = "Issued by ${vc.issuer.name} • $statusText",
                    rawData = content,
                    actionData = actionData
                )
            } else {
                return ParsedQrData(
                    type = QrDataType.VERIFIABLE_CREDENTIAL,
                    title = "📋 Verifiable Credential",
                    primaryText = "Invalid or corrupt credential",
                    secondaryText = "Could not decode credential data",
                    rawData = content,
                    actionData = mapOf("isValid" to "false", "isTampered" to "false")
                )
            }
        }

        // Check for Event Ticket
        if (content.startsWith("threatlens://ticket", ignoreCase = true)) {
            val uri = android.net.Uri.parse(content)
            val ticketId = uri.getQueryParameter("id") ?: "Unknown"
            val sig = uri.getQueryParameter("sig") ?: ""
            val map = mutableMapOf("ticketId" to ticketId, "signature" to sig)
            return ParsedQrData(QrDataType.TICKET, "Event Ticket", "Ticket ID: $ticketId", "Secure Gatekeeper Pass", content, map)
        }

        // Check for UPI Payment
        if (content.startsWith("upi://", ignoreCase = true)) {
            val uri = try { android.net.Uri.parse(content) } catch(e:Exception) { null }
            val map = mutableMapOf<String, String>()
            var payee = "Unknown Payee"
            if (uri != null) {
                uri.getQueryParameter("pn")?.let { payee = it; map["payeeName"] = it }
                uri.getQueryParameter("pa")?.let { map["payeeAddress"] = it }
                uri.getQueryParameter("am")?.let { map["amount"] = it }
                uri.getQueryParameter("tn")?.let { map["note"] = it }
                // ── Additional UPI fields for fraud analysis ──
                uri.getQueryParameter("mc")?.let { map["merchantCode"] = it }
                uri.getQueryParameter("cu")?.let { map["currency"] = it }
                uri.getQueryParameter("tr")?.let { map["transactionRef"] = it }
                uri.getQueryParameter("mode")?.let { map["mode"] = it }
                uri.getQueryParameter("orgid")?.let { map["orgId"] = it }
            }
            return ParsedQrData(
                type = QrDataType.PAYMENT,
                title = "UPI Payment",
                primaryText = "Pay: $payee",
                secondaryText = map["amount"]?.let { "Amount: ₹$it" } ?: "Amount: Not specified",
                rawData = content,
                actionData = map
            )
        }

        // Check for WiFi
        if (content.startsWith("WIFI:", ignoreCase = true)) {
            val ssid = extractField(content, "S:")
            val password = extractField(content, "P:")
            val type = extractField(content, "T:") ?: "WPA"
            val hidden = extractField(content, "H:") == "true"
            
            val map = mutableMapOf<String, String>()
            ssid?.let { map["ssid"] = it }
            password?.let { map["password"] = it }
            map["type"] = type
            if (hidden) map["hidden"] = "true"
            
            return ParsedQrData(
                type = QrDataType.WIFI,
                title = "Wi-Fi Network",
                primaryText = ssid ?: "Unknown Network",
                secondaryText = "Security: $type" + (password?.let { "\nPassword: $it" } ?: ""),
                rawData = content,
                actionData = map
            )
        }
        
        // Check for vCard / MeCard
        if (content.startsWith("BEGIN:VCARD", ignoreCase = true) || content.startsWith("MECARD:", ignoreCase = true)) {
            val isMeCard = content.startsWith("MECARD:", ignoreCase = true)
            
            val name = if (isMeCard) {
                val n = extractField(content, "N:") ?: "Unknown Contact"
                n.replace(",", " ") // MeCard often uses Last,First
            } else {
                extractVCardField(content, "FN:") ?: extractVCardField(content, "N:") ?: "Unknown Contact"
            }
            
            val phone = if (isMeCard) extractField(content, "TEL:") else extractVCardField(content, "TEL")
            val email = if (isMeCard) extractField(content, "EMAIL:") else extractVCardField(content, "EMAIL")
            val org = if (isMeCard) extractField(content, "ORG:") else extractVCardField(content, "ORG:")
            
            val map = mutableMapOf<String, String>()
            map["name"] = name
            phone?.let { map["phone"] = it }
            email?.let { map["email"] = it }
            org?.let { map["org"] = it }
            
            return ParsedQrData(
                type = QrDataType.VCARD,
                title = "Contact Info",
                primaryText = name,
                secondaryText = listOfNotNull(phone, email, org).joinToString(" | "),
                rawData = content,
                actionData = map
            )
        }
        
        // Check for Email
        if (content.startsWith("MATMSG:", ignoreCase = true) || content.startsWith("mailto:", ignoreCase = true)) {
            val email: String
            val subject: String?
            val body: String?
            
            if (content.startsWith("mailto:", ignoreCase = true)) {
                val urlObj = try { java.net.URI.create(content) } catch(e:Exception) { null }
                if (urlObj != null && urlObj.schemeSpecificPart != null) {
                    val parts = urlObj.schemeSpecificPart.split("?")
                    email = parts[0]
                    var s: String? = null
                    var b: String? = null
                    if (parts.size > 1) {
                        parts[1].split("&").forEach { param ->
                            val kv = param.split("=")
                            if (kv.size == 2) {
                                if (kv[0].lowercase() == "subject") s = java.net.URLDecoder.decode(kv[1], "UTF-8")
                                if (kv[0].lowercase() == "body") b = java.net.URLDecoder.decode(kv[1], "UTF-8")
                            }
                        }
                    }
                    subject = s
                    body = b
                } else {
                    email = content.removePrefix("mailto:")
                    subject = null
                    body = null
                }
            } else {
                // MATMSG
                email = extractField(content, "TO:") ?: ""
                subject = extractField(content, "SUB:")
                body = extractField(content, "BODY:")
            }
            
            val map = mutableMapOf<String, String>()
            map["email"] = email
            subject?.let { map["subject"] = it }
            body?.let { map["body"] = it }
            
            return ParsedQrData(
                type = QrDataType.EMAIL,
                title = "Email Address",
                primaryText = email,
                secondaryText = subject?.let { "Subject: $it" },
                rawData = content,
                actionData = map
            )
        }
        
        // Check for SMS
        if (content.startsWith("SMSTO:", ignoreCase = true) || content.startsWith("sms:", ignoreCase = true)) {
            val isSmsto = content.startsWith("SMSTO:", ignoreCase = true)
            val prefixLen = if (isSmsto) 6 else 4
            val parts = content.substring(prefixLen).split(":", limit = 2)
            val phone = parts.getOrNull(0) ?: ""
            val body = parts.getOrNull(1)
            
            val map = mutableMapOf<String, String>()
            map["phone"] = phone
            body?.let { map["body"] = it }
            
            return ParsedQrData(
                type = QrDataType.SMS,
                title = "SMS Message",
                primaryText = phone,
                secondaryText = body,
                rawData = content,
                actionData = map
            )
        }
        
        // Check for Phone
        if (content.startsWith("TEL:", ignoreCase = true)) {
            val phone = content.substring(4)
            val map = mapOf("phone" to phone)
            return ParsedQrData(
                type = QrDataType.PHONE,
                title = "Phone Number",
                primaryText = phone,
                secondaryText = null,
                rawData = content,
                actionData = map
            )
        }
        
        // Check for Location
        if (content.startsWith("geo:", ignoreCase = true)) {
            val coords = content.substring(4).split("?")[0]
            val parts = coords.split(",")
            val displayCoords = if (parts.size >= 2) "Lat: ${parts[0]} | Lng: ${parts[1]}" else "Coordinates: $coords"
            
            val map = mapOf("geo" to coords)
            return ParsedQrData(
                type = QrDataType.LOCATION,
                title = "Location",
                primaryText = "Map Coordinates",
                secondaryText = displayCoords,
                rawData = content,
                actionData = map
            )
        }

        // Check for Event
        if (content.contains("BEGIN:VEVENT", ignoreCase = true)) {
            val summary = extractVCardField(content, "SUMMARY:") ?: "Unknown Event"
            val start = extractVCardField(content, "DTSTART:")
            val end = extractVCardField(content, "DTEND:")
            val loc = extractVCardField(content, "LOCATION:")
            
            val map = mutableMapOf<String, String>()
            map["title"] = summary
            start?.let { map["start"] = it }
            end?.let { map["end"] = it }
            loc?.let { map["location"] = it }
            
            return ParsedQrData(
                type = QrDataType.EVENT,
                title = "Calendar Event",
                primaryText = summary,
                secondaryText = listOfNotNull(start, loc).joinToString(" | "),
                rawData = content,
                actionData = map
            )
        }

        
        // Check for Crypto
        if (content.startsWith("bitcoin:", ignoreCase = true) || 
            content.startsWith("ethereum:", ignoreCase = true) || 
            content.startsWith("litecoin:", ignoreCase = true)) {
            
            val schemeEnd = content.indexOf(":")
            val coin = content.substring(0, schemeEnd).replaceFirstChar { it.uppercase() }
            
            val rest = content.substring(schemeEnd + 1)
            val parts = rest.split("?")
            val address = parts[0]
            
            var amount: String? = null
            if (parts.size > 1) {
                parts[1].split("&").forEach { param ->
                    val kv = param.split("=")
                    if (kv.size == 2 && kv[0].lowercase() == "amount") {
                        amount = kv[1]
                    }
                }
            }
            
            val map = mutableMapOf<String, String>()
            map["coin"] = coin
            map["address"] = address
            amount?.let { map["amount"] = it }
            
            return ParsedQrData(
                type = QrDataType.CRYPTO,
                title = "$coin Wallet",
                primaryText = address,
                secondaryText = amount?.let { "Amount: $it" },
                rawData = content,
                actionData = map
            )
        }
        // Check for Authenticator / 2FA
        if (content.startsWith("otpauth://", ignoreCase = true)) {
            val uri = try { android.net.Uri.parse(content) } catch(e:Exception) { null }
            val map = mutableMapOf<String, String>()
            
            var accountName = "Unknown Account"
            var issuer = "Unknown Issuer"
            var secret = ""
            
            if (uri != null) {
                // Path is usually like /totp/Issuer:AccountName or /totp/AccountName
                val path = uri.path ?: ""
                val label = path.removePrefix("/totp/").removePrefix("/hotp/")
                if (label.contains(":")) {
                    val parts = label.split(":", limit = 2)
                    issuer = parts[0]
                    accountName = parts[1]
                } else {
                    accountName = label
                }
                
                uri.getQueryParameter("issuer")?.let { issuer = it }
                uri.getQueryParameter("secret")?.let { secret = it; map["secret"] = it }
            }
            
            val typeStr = if (content.contains("hotp", ignoreCase = true)) "HOTP" else "TOTP"
            
            return ParsedQrData(
                type = QrDataType.AUTHENTICATOR,
                title = "Authenticator Setup",
                primaryText = issuer.takeIf { it != "Unknown Issuer" } ?: accountName,
                secondaryText = accountName.takeIf { issuer != "Unknown Issuer" } ?: "2FA ($typeStr)",
                rawData = content,
                actionData = map
            )
        }

        // Check for App Store / Play Store
        if (content.startsWith("market://", ignoreCase = true) || 
            content.contains("play.google.com/store/apps", ignoreCase = true) ||
            content.contains("apps.apple.com", ignoreCase = true)) {
            
            val map = mutableMapOf<String, String>()
            map["url"] = content
            
            return ParsedQrData(
                type = QrDataType.APP_STORE,
                title = "App Store Link",
                primaryText = "Download App",
                secondaryText = if (content.contains("apple.com", true)) "Apple App Store" else "Google Play Store",
                rawData = content,
                actionData = map
            )
        }
        
        // Check for URL
        if (content.startsWith("http://", ignoreCase = true) || 
            content.startsWith("https://", ignoreCase = true) ||
            (content.contains(".") && !content.contains(" ") && !content.contains("\n") && content.length < 255)) {
            return ParsedQrData(QrDataType.URL, "Website Link", content, null, content)
        }
        
        // Check for eSIM
        if (content.startsWith("LPA:1$", ignoreCase = true)) {
            return ParsedQrData(
                type = QrDataType.ESIM,
                title = "eSIM Activation",
                primaryText = "Mobile Network Profile",
                secondaryText = "Open in System Settings",
                rawData = content
            )
        }

        // Check for Android Device Provisioning
        if (content.contains("\"android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME\"")) {
            return ParsedQrData(
                type = QrDataType.PROVISIONING,
                title = "Device Provisioning",
                primaryText = "System Configuration",
                secondaryText = "Requires System QR Scanner",
                rawData = content
            )
        }
        
        // Default to Plain Text
        return ParsedQrData(
            type = QrDataType.TEXT,
            title = "Plain Text / Data",
            primaryText = if (content.length > 50) content.take(47) + "..." else content,
            secondaryText = null,
            rawData = content
        )
    }
    
    private fun extractField(data: String, prefix: String): String? {
        val startIndex = data.indexOf(prefix, ignoreCase = true)
        if (startIndex == -1) return null
        val start = startIndex + prefix.length
        val end = data.indexOf(";", start)
        return if (end == -1) {
            data.substring(start)
        } else {
            data.substring(start, end)
        }.takeIf { it.isNotEmpty() }
    }
    
    private fun extractVCardField(data: String, prefix: String): String? {
        val lines = data.split("\n", "\r\n")
        for (line in lines) {
            if (line.startsWith(prefix, ignoreCase = true) || 
                (prefix == "TEL" && line.startsWith("TEL;", ignoreCase = true)) ||
                (prefix == "EMAIL" && line.startsWith("EMAIL;", ignoreCase = true))) {
                val colonIndex = line.indexOf(":")
                if (colonIndex != -1) {
                    return line.substring(colonIndex + 1).trim()
                }
            }
        }
        return null
    }
}
