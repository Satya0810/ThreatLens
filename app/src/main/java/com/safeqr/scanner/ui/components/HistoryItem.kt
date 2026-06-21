package com.safeqr.scanner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.data.model.SafetyStatus
import com.safeqr.scanner.data.model.ScanResult
import com.safeqr.scanner.ui.theme.CautionAmber
import com.safeqr.scanner.ui.theme.DarkCard
import com.safeqr.scanner.ui.theme.DarkSurface
import com.safeqr.scanner.ui.theme.GlassBorder
import com.safeqr.scanner.ui.theme.MaliciousRed
import com.safeqr.scanner.ui.theme.NeonCyan
import com.safeqr.scanner.ui.theme.PrimaryBlue
import com.safeqr.scanner.ui.theme.SafeGreen
import com.safeqr.scanner.ui.theme.TextPrimary
import com.safeqr.scanner.ui.theme.TextSecondary

/**
 * A card composable for displaying a scan history entry with glassmorphism styling,
 * animated status indicator, and a colored accent bar.
 */
@Composable
fun HistoryItem(
    scanResult: ScanResult,
    onClick: () -> Unit
) {
    val displayText = scanResult.domain
        ?: scanResult.rawContent.let { if (it.length > 40) it.take(40) + "…" else it }

    val statusColor = when (scanResult.safetyStatus) {
        SafetyStatus.SAFE -> SafeGreen
        SafetyStatus.CAUTION -> CautionAmber
        SafetyStatus.MALICIOUS -> MaliciousRed
        SafetyStatus.UNKNOWN -> Color.Gray
        SafetyStatus.ANALYZING -> PrimaryBlue
    }

    val statusLabel = when (scanResult.safetyStatus) {
        SafetyStatus.SAFE -> "SAFE"
        SafetyStatus.CAUTION -> "CAUTION"
        SafetyStatus.MALICIOUS -> "MALICIOUS"
        SafetyStatus.UNKNOWN -> "UNKNOWN"
        SafetyStatus.ANALYZING -> "ANALYZING"
    }

    // Pulsing dot animation for status
    val infiniteTransition = rememberInfiniteTransition(label = "historyPulse")
    val dotPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotPulse"
    )

    // Press animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pressScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = "Scan history item. Domain: $displayText, Status: $statusLabel"
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface // Solid color prevents swipe-to-dismiss background bleed
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // ── Left accent bar ──
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(statusColor.copy(alpha = 0.8f))
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side — domain/content and timestamp
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Domain text with monospace feel
                    Text(
                        text = displayText,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Default,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Badges row
                    val threatTag = when {
                        scanResult.isAdultContent -> "18+"
                        scanResult.isTransaction -> "Payment"
                        scanResult.threatDetails.isNotEmpty() -> scanResult.threatDetails.first()
                        scanResult.safetyStatus == SafetyStatus.MALICIOUS -> "Malware"
                        scanResult.safetyStatus == SafetyStatus.CAUTION -> "Caution"
                        scanResult.safetyStatus == SafetyStatus.SAFE -> "Safe"
                        else -> "Unknown"
                    }

                    if (threatTag != null) {
                        Row(
                            modifier = Modifier.padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tagColor = when {
                                threatTag == "18+" -> MaliciousRed
                                threatTag == "Payment" -> CautionAmber
                                threatTag == "Safe" -> SafeGreen
                                scanResult.safetyStatus == SafetyStatus.MALICIOUS -> MaliciousRed
                                scanResult.safetyStatus == SafetyStatus.CAUTION -> CautionAmber
                                else -> statusColor
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tagColor.copy(alpha = 0.12f),
                                contentColor = tagColor,
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp, tagColor.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = threatTag,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    // Timestamp with clock icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Scan Time",
                            tint = TextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatRelativeTime(scanResult.timestamp),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                        
                        if (scanResult.visitCount > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NeonCyan.copy(alpha = 0.12f),
                                contentColor = NeonCyan
                            ) {
                                Text(
                                    text = "${scanResult.visitCount} visits",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Right side — status pill and score
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Status pill with pulsing border
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        contentColor = statusColor,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, statusColor.copy(alpha = dotPulse * 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Pulsing status dot
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = dotPulse))
                            )
                            Text(
                                text = statusLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Compact safety indicator
                    SafetyIndicator(
                        score = scanResult.overallScore,
                        safetyStatus = scanResult.safetyStatus,
                        size = 42.dp
                    )
                }
            }
        }
    }
}

/**
 * Formats a Unix-millis [timestamp] as a human-readable relative time string.
 */
internal fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    if (diff < 0) return "just now"

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    val months = days / 30

    return when {
        seconds < 60 -> "just now"
        minutes == 1L -> "1 min ago"
        minutes < 60 -> "$minutes min ago"
        hours == 1L -> "1 hour ago"
        hours < 24 -> "$hours hours ago"
        days == 1L -> "yesterday"
        days < 7 -> "$days days ago"
        weeks == 1L -> "1 week ago"
        weeks < 4 -> "$weeks weeks ago"
        months == 1L -> "1 month ago"
        months < 12 -> "$months months ago"
        else -> {
            val years = months / 12
            if (years == 1L) "1 year ago" else "$years years ago"
        }
    }
}
