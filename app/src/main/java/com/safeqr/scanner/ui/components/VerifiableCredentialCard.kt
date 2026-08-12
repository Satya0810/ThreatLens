package com.safeqr.scanner.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.analysis.ParsedQrData
import com.safeqr.scanner.ui.theme.*

/**
 * Premium Verifiable Credential card displayed in ResultBottomSheet
 * when a W3C-style Verifiable Credential QR code is scanned.
 */
@Composable
fun VerifiableCredentialCard(
    parsedData: ParsedQrData,
    onSaveToVault: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isValid = parsedData.actionData["isValid"] == "true"
    val isTampered = parsedData.actionData["isTampered"] == "true"
    val isExpired = parsedData.actionData["isExpired"] == "true"
    val credentialType = parsedData.actionData["credentialType"] ?: "CUSTOM"
    val issuerName = parsedData.actionData["issuerName"] ?: "Unknown Issuer"
    val subjectName = parsedData.actionData["subjectName"] ?: "Unknown"
    val vcId = parsedData.actionData["vcId"] ?: ""

    // Extract claims from actionData (keys starting with "claim_")
    val claims = parsedData.actionData.filter { it.key.startsWith("claim_") }
        .map { (k, v) -> k.removePrefix("claim_").replaceFirstChar { it.titlecase() } to v }

    val (statusColor, statusIcon, statusText) = when {
        isTampered -> Triple(MaliciousRed, "❌", "TAMPERED — Signature Mismatch")
        isExpired -> Triple(CautionAmber, "⏰", "EXPIRED")
        isValid -> Triple(SafeGreen, "✅", "CRYPTOGRAPHICALLY VERIFIED")
        else -> Triple(TextSecondary, "❓", "UNKNOWN STATUS")
    }

    val typeIcon = when (credentialType) {
        "IDENTITY" -> "🆔"
        "DIPLOMA" -> "🎓"
        "MEMBERSHIP" -> "🏅"
        "HEALTH" -> "🏥"
        "EMPLOYMENT" -> "💼"
        "LICENSE" -> "📜"
        else -> "📋"
    }

    val typeLabel = credentialType.replace("_", " ").lowercase()
        .replaceFirstChar { it.titlecase() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        statusColor.copy(alpha = 0.08f),
                        DarkCard.copy(alpha = 0.95f)
                    )
                )
            )
            .border(1.5.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Type icon badge
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(typeIcon, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "$typeLabel Credential",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            "W3C Verifiable Credential",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Verification badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "$statusIcon ${if (isValid) "VALID" else if (isTampered) "INVALID" else if (isExpired) "EXPIRED" else "?"}",
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Verification Status Banner ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(statusColor.copy(alpha = 0.08f))
                    .border(1.dp, statusColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(statusIcon, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Subject & Issuer ────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                // Subject
                Column(modifier = Modifier.weight(1f)) {
                    Text("Subject", fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subjectName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(16.dp))
                // Issuer
                Column(modifier = Modifier.weight(1f)) {
                    Text("Issued By", fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        issuerName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = NeonCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Claims ──────────────────────────────────────────────────────
            if (claims.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Credential Claims", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBackground.copy(alpha = 0.5f))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        claims.forEach { (key, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(key, fontSize = 12.sp, color = TextSecondary)
                                Text(
                                    value,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Credential ID & Dates ───────────────────────────────────────
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ID: ${vcId.take(16)}${if (vcId.length > 16) "..." else ""}",
                    fontSize = 9.sp,
                    color = TextSecondary.copy(alpha = 0.5f)
                )
                val issuedTs = parsedData.actionData["issuanceDate"]?.toLongOrNull()
                if (issuedTs != null) {
                    val date = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(issuedTs))
                    Text("Issued: $date", fontSize = 9.sp, color = TextSecondary.copy(alpha = 0.5f))
                }
            }

            // ── Cryptographic Proof Info ────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonCyan.copy(alpha = 0.05f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Security, null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Proof: HMAC-SHA256 Digital Signature",
                    fontSize = 10.sp,
                    color = NeonCyan.copy(alpha = 0.8f)
                )
            }

            // ── Save to Vault Button ────────────────────────────────────────
            if (onSaveToVault != null && isValid) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSaveToVault,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save to Vault", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}
