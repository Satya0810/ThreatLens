package com.safeqr.scanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.analysis.UpiPaymentAnalyzer
import com.safeqr.scanner.data.UpiGuardPreferences
import com.safeqr.scanner.ui.theme.*
import kotlinx.coroutines.delay

/**
 * UPI Guard Status Card — Real-time protection indicators
 *
 * Shows:
 * - Daily spending limit progress bar
 * - VPA trust badge (first-time / trusted / blocked)
 * - Rapid-fire scan alert (if applicable)
 */
@Composable
fun UpiGuardStatusCard(analysis: UpiPaymentAnalyzer.UpiAnalysisResult) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(350)
        showContent = true
    }

    AnimatedVisibility(
        visible = showContent,
        enter = fadeIn(tween(500, delayMillis = 200)) + slideInVertically(tween(400, delayMillis = 200)) { it / 3 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            NeonCyan.copy(alpha = 0.05f),
                            DarkSurface.copy(alpha = 0.8f)
                        )
                    )
                )
                .border(1.dp, NeonCyan.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "UPI Guard",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "REAL-TIME",
                    color = NeonCyan.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 1. VPA Trust Badge ──────────────────────────────────
            val hasBlockedFlag = analysis.flags.any { it.id == "VPA_PREVIOUSLY_REPORTED" }
            val hasTrustedFlag = analysis.flags.any { it.id == "VPA_TRUSTED_PAYEE" }
            val hasFirstTimeFlag = analysis.flags.any { it.id == "VPA_FIRST_TIME" }

            if (hasBlockedFlag || hasTrustedFlag || hasFirstTimeFlag) {
                val (icon, text, color) = when {
                    hasBlockedFlag -> Triple(Icons.Filled.Block, "Blocked VPA — Previously Reported", MaliciousRed)
                    hasTrustedFlag -> Triple(
                        Icons.Filled.VerifiedUser,
                        "Trusted Payee — ${analysis.previousPayCount} previous payment(s)",
                        SafeGreen
                    )
                    else -> Triple(Icons.Filled.NewReleases, "First-Time Payee — Verify Identity", CautionAmber)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.08f))
                        .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text,
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── 2. Daily Limit Progress ────────────────────────────
            val limitStatus = analysis.dailyLimitStatus
            if (limitStatus != null) {
                val progress = (limitStatus.spentToday / limitStatus.dailyLimit).toFloat().coerceIn(0f, 1f)
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    label = "limitBar"
                )

                val barColor = when {
                    limitStatus.wouldExceed -> MaliciousRed
                    progress > 0.75f -> CautionAmber
                    else -> SafeGreen
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Daily Limit",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            "₹${String.format("%,.0f", limitStatus.spentToday)} / ₹${String.format("%,.0f", limitStatus.dailyLimit)}",
                            color = barColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(barColor.copy(alpha = 0.6f), barColor)
                                    )
                                )
                        )
                    }

                    // Exceeded warning
                    if (limitStatus.wouldExceed) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaliciousRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "This payment would exceed your daily limit",
                                color = MaliciousRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── 3. Rapid-Fire Alert ────────────────────────────────
            val hasRapidFire = analysis.flags.any { it.id == "RAPID_FIRE_PAYMENT" }
            if (hasRapidFire) {
                val rapidFlag = analysis.flags.first { it.id == "RAPID_FIRE_PAYMENT" }
                val infiniteTransition = rememberInfiniteTransition(label = "rapid")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "rapidPulse"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaliciousRed.copy(alpha = 0.08f))
                        .border(0.5.dp, MaliciousRed.copy(alpha = 0.3f * pulseAlpha), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaliciousRed.copy(alpha = 0.15f * pulseAlpha), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.FlashOn,
                            contentDescription = null,
                            tint = MaliciousRed.copy(alpha = pulseAlpha),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            rapidFlag.title,
                            color = MaliciousRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Slow down and verify each payment",
                            color = MaliciousRed.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── 4. Late Night Warning ──────────────────────────────
            val hasLateNight = analysis.flags.any { it.id == "LATE_NIGHT_HIGH_RISK" }
            if (hasLateNight) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CautionAmber.copy(alpha = 0.08f))
                        .border(0.5.dp, CautionAmber.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.NightsStay,
                        contentDescription = null,
                        tint = CautionAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Late-Night Payment",
                            color = CautionAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "UPI scams are 3× more common after 11 PM",
                            color = CautionAmber.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
