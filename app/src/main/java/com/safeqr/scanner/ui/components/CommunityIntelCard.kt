package com.safeqr.scanner.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.data.remote.CloudSyncManager
import com.safeqr.scanner.ui.theme.*

/**
 * Premium-styled card displaying community threat intelligence for a scanned QR code.
 * Shows report count, threat categories, location context, and risk escalation.
 */
@Composable
fun CommunityIntelCard(
    threatIntel: CloudSyncManager.ThreatIntelReport,
    modifier: Modifier = Modifier
) {
    if (threatIntel.totalReports == 0) return

    val escalationColor = when (threatIntel.riskEscalation) {
        "CRITICAL" -> MaliciousRed
        "HIGH" -> Color(0xFFFF6B35)
        "MEDIUM" -> CautionAmber
        "LOW" -> Color(0xFFFFC107)
        else -> TextSecondary
    }

    val escalationIcon = when (threatIntel.riskEscalation) {
        "CRITICAL" -> "🚨"
        "HIGH" -> "⚠️"
        "MEDIUM" -> "⚡"
        "LOW" -> "ℹ️"
        else -> "📊"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        escalationColor.copy(alpha = 0.08f),
                        DarkCard.copy(alpha = 0.95f)
                    )
                )
            )
            .border(1.dp, escalationColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(escalationIcon, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Community Intel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(escalationColor.copy(alpha = 0.15f))
                        .border(1.dp, escalationColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${threatIntel.riskEscalation} RISK",
                        color = escalationColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Report Count - big number
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBackground.copy(alpha = 0.5f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${threatIntel.totalReports}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    color = escalationColor
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (threatIntel.totalReports == 1) "Community Report" else "Community Reports",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        "Users have flagged this content",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            // Report Types
            if (threatIntel.reportTypes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Threat Categories", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    threatIntel.reportTypes.take(4).forEach { type ->
                        val icon = when (type) {
                            "PHISHING" -> "🎣"
                            "FRAUD" -> "💰"
                            "MALWARE" -> "🦠"
                            "SCAM_STICKER" -> "🏷️"
                            "IMPERSONATION" -> "🎭"
                            else -> "⚠️"
                        }
                        val label = type.replace("_", " ").lowercase()
                            .replaceFirstChar { it.titlecase() }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("$icon $label", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // Locations
            if (threatIntel.locations.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Reports from: ${threatIntel.locations.take(3).joinToString(", ")}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            // Timestamps
            if (threatIntel.firstReportedAt != null) {
                Spacer(Modifier.height(8.dp))
                val daysAgo = ((System.currentTimeMillis() - threatIntel.firstReportedAt) / (1000 * 60 * 60 * 24)).toInt()
                val timeText = when {
                    daysAgo == 0 -> "Today"
                    daysAgo == 1 -> "Yesterday"
                    daysAgo < 30 -> "$daysAgo days ago"
                    else -> "${daysAgo / 30} months ago"
                }
                Text(
                    "First reported: $timeText",
                    fontSize = 10.sp,
                    color = TextSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}
