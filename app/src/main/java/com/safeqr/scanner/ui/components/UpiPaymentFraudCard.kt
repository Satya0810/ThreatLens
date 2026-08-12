package com.safeqr.scanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.analysis.UpiPaymentAnalyzer
import com.safeqr.scanner.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Premium UPI Payment Fraud Analysis Card
 *
 * Displays comprehensive fraud analysis results with:
 * - Animated risk score arc
 * - Payee verification badge
 * - VPA handle trust indicator
 * - ML confidence meter
 * - Fraud flag chips
 * - Transaction details
 * - Recommendations
 */
@Composable
fun UpiPaymentFraudCard(analysis: UpiPaymentAnalyzer.UpiAnalysisResult) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        showContent = true
    }

    val riskColor = when (analysis.riskLevel) {
        UpiPaymentAnalyzer.RiskLevel.CRITICAL -> MaliciousRed
        UpiPaymentAnalyzer.RiskLevel.HIGH -> Color(0xFFFF6B35) // Deep Orange
        UpiPaymentAnalyzer.RiskLevel.MEDIUM -> CautionAmber
        UpiPaymentAnalyzer.RiskLevel.LOW -> SafeGreen
    }

    val glowColor = riskColor.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // ══════════════════════════════════════════════════════════════
        //  RISK SCORE + ML CONFIDENCE HEADER
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 3 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = glowColor, ambientColor = glowColor)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(DarkSurface, DarkSurface.copy(alpha = 0.95f), Color(0xFF0D1117))
                        )
                    )
                    .border(1.dp, riskColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            tint = riskColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "UPI Fraud Analysis",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    // Risk Level Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(riskColor.copy(alpha = 0.15f))
                            .border(1.dp, riskColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${analysis.riskLevel.name} RISK",
                            color = riskColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Animated Risk Score Arc ──────────────────────────
                RiskScoreArc(
                    riskScore = analysis.riskScore,
                    riskColor = riskColor,
                    mlConfidence = analysis.mlConfidence
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── ML Confidence Bar ───────────────────────────────
                if (analysis.mlConfidence > 0f) {
                    MLConfidenceMeter(confidence = analysis.mlConfidence, riskColor = riskColor)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ══════════════════════════════════════════════════════════════
        //  TRANSACTION DETAILS CARD
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(500, delayMillis = 150)) + slideInVertically(tween(400, delayMillis = 150)) { it / 3 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface.copy(alpha = 0.7f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                // Payee Verification Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val verifyIcon: @Composable () -> Unit
                    val verifyText: String
                    val verifyColor: Color

                    if (analysis.payeeVerified) {
                        verifyIcon = {
                            Icon(Icons.Filled.VerifiedUser, null, tint = SafeGreen, modifier = Modifier.size(20.dp))
                        }
                        verifyText = "Verified: ${analysis.handleBankName ?: "Known Bank"}"
                        verifyColor = SafeGreen
                    } else if (analysis.flags.any { it.id == "VPA_MISSING" || it.id == "VPA_INVALID_FORMAT" }) {
                        verifyIcon = {
                            Icon(Icons.Filled.Error, null, tint = MaliciousRed, modifier = Modifier.size(20.dp))
                        }
                        verifyText = "Invalid/Missing VPA"
                        verifyColor = MaliciousRed
                    } else {
                        verifyIcon = {
                            Icon(Icons.Filled.Warning, null, tint = CautionAmber, modifier = Modifier.size(20.dp))
                        }
                        verifyText = "Unverified Handle: @${analysis.vpaHandle ?: "unknown"}"
                        verifyColor = CautionAmber
                    }

                    verifyIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        verifyText,
                        color = verifyColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Transaction Details Grid
                if (!analysis.payeeName.isNullOrBlank()) {
                    TransactionDetailRow("Payee", analysis.payeeName)
                }
                if (!analysis.payeeVpa.isNullOrBlank()) {
                    TransactionDetailRow("VPA", analysis.payeeVpa)
                }
                if (analysis.amount != null) {
                    TransactionDetailRow("Amount", "₹${String.format("%,.2f", analysis.amount)}")
                } else {
                    TransactionDetailRow("Amount", "Not Specified (Open QR)")
                }
                if (!analysis.transactionNote.isNullOrBlank()) {
                    TransactionDetailRow("Note", analysis.transactionNote)
                }
                if (!analysis.merchantCode.isNullOrBlank()) {
                    TransactionDetailRow("Merchant Code", analysis.merchantCode)
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        //  FRAUD FLAGS
        // ══════════════════════════════════════════════════════════════
        if (analysis.flags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(500, delayMillis = 300)) + slideInVertically(tween(400, delayMillis = 300)) { it / 3 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(riskColor.copy(alpha = 0.05f))
                        .border(1.dp, riskColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warning, null, tint = riskColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Detected Issues (${analysis.flags.size})",
                            color = riskColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    analysis.flags.forEach { flag ->
                        FraudFlagItem(flag)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        //  RECOMMENDATIONS
        // ══════════════════════════════════════════════════════════════
        if (analysis.recommendations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(500, delayMillis = 450)) + slideInVertically(tween(400, delayMillis = 450)) { it / 3 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PrimaryBlue.copy(alpha = 0.05f))
                        .border(1.dp, PrimaryBlue.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Recommendations",
                            color = PrimaryBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    analysis.recommendations.forEach { rec ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("•", color = TextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                rec,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  SUB-COMPONENTS
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun RiskScoreArc(riskScore: Float, riskColor: Color, mlConfidence: Float) {
    val animatedScore by animateFloatAsState(
        targetValue = riskScore,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "riskArc"
    )

    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background arc
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background track
            drawArc(
                color = Color.White.copy(alpha = 0.06f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Filled arc
            val sweepAngle = (animatedScore / 100f) * 270f
            drawArc(
                color = riskColor,
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.0f", animatedScore),
                color = riskColor,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "RISK",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun MLConfidenceMeter(confidence: Float, riskColor: Color) {
    val animatedWidth by animateFloatAsState(
        targetValue = confidence,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "mlBar"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Psychology, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("AI Confidence", color = TextSecondary, fontSize = 11.sp)
            }
            Text(
                "${String.format("%.0f", confidence * 100)}%",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(PrimaryBlue, riskColor)
                        )
                    )
            )
        }
    }
}

@Composable
private fun TransactionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(90.dp)
        )
        Text(
            value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FraudFlagItem(flag: UpiPaymentAnalyzer.UpiFlag) {
    val flagColor = when (flag.severity) {
        UpiPaymentAnalyzer.RiskLevel.CRITICAL -> MaliciousRed
        UpiPaymentAnalyzer.RiskLevel.HIGH -> Color(0xFFFF6B35)
        UpiPaymentAnalyzer.RiskLevel.MEDIUM -> CautionAmber
        UpiPaymentAnalyzer.RiskLevel.LOW -> PrimaryBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(flagColor.copy(alpha = 0.08f))
            .border(0.5.dp, flagColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(flag.emoji, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                flag.title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                flag.description,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        // Severity badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(flagColor.copy(alpha = 0.15f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                flag.severity.name.take(4),
                color = flagColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
