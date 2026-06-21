package com.safeqr.scanner.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.data.model.SafetyStatus
import com.safeqr.scanner.ui.theme.CautionAmber
import com.safeqr.scanner.ui.theme.MaliciousRed
import com.safeqr.scanner.ui.theme.PrimaryBlue
import com.safeqr.scanner.ui.theme.SafeGreen
import com.safeqr.scanner.ui.theme.TextPrimary
import com.safeqr.scanner.ui.theme.TextSecondary

/**
 * Circular safety score indicator that displays a filled arc ring, score number, and status label.
 *
 * @param score Safety score from 0f to 100f.
 * @param safetyStatus The current [SafetyStatus] determining ring color and label.
 * @param modifier Optional modifier for the root container.
 * @param size Diameter of the indicator circle.
 */
@Composable
fun SafetyIndicator(
    score: Float,
    safetyStatus: SafetyStatus,
    overrideColor: Color? = null,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val statusColor = overrideColor ?: when (safetyStatus) {
        SafetyStatus.SAFE -> SafeGreen
        SafetyStatus.CAUTION -> CautionAmber
        SafetyStatus.MALICIOUS -> MaliciousRed
        SafetyStatus.UNKNOWN -> Color.Gray
        SafetyStatus.ANALYZING -> PrimaryBlue
    }

    val statusLabel = when (safetyStatus) {
        SafetyStatus.SAFE -> "SAFE"
        SafetyStatus.CAUTION -> "CAUTION"
        SafetyStatus.MALICIOUS -> "MALICIOUS"
        SafetyStatus.UNKNOWN -> "UNKNOWN"
        SafetyStatus.ANALYZING -> "ANALYZING"
    }

    // Animated sweep angle (score maps 0–100 → 0–360 degrees)
    val targetSweep = (score.coerceIn(0f, 100f) / 100f) * 360f
    val animatedSweep by animateFloatAsState(
        targetValue = targetSweep,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "sweepAnimation"
    )

    // Pulsing glow for MALICIOUS status
    val infiniteTransition = rememberInfiniteTransition(label = "maliciousGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val isMalicious = safetyStatus == SafetyStatus.MALICIOUS

    // Determine text sizing based on indicator size
    val scoreFontSize = if (size <= 50.dp) 14.sp else if (size <= 80.dp) 22.sp else 32.sp
    val labelFontSize = if (size <= 50.dp) 7.sp else if (size <= 80.dp) 9.sp else 11.sp
    val ringWidth = if (size <= 50.dp) 3.dp else if (size <= 80.dp) 6.dp else 8.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Safety Score: ${score.toInt()} out of 100, Status: $statusLabel"
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size)
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val strokeWidth = ringWidth.toPx()
                val arcSize = this.size.width - strokeWidth
                val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
                val arcSizeObj = androidx.compose.ui.geometry.Size(arcSize, arcSize)

                // Optional outer glow ring for MALICIOUS
                if (isMalicious) {
                    drawArc(
                        color = MaliciousRed.copy(alpha = glowAlpha * 0.4f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSizeObj,
                        style = Stroke(width = strokeWidth + 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Background track
                drawArc(
                    color = Color(0xFF2A2A2E),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSizeObj,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Filled arc
                if (animatedSweep > 0f) {
                    drawArc(
                        color = if (isMalicious) statusColor.copy(alpha = glowAlpha.coerceIn(0.7f, 1f)) else statusColor,
                        startAngle = -90f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSizeObj,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Score text in center
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = score.toInt().toString(),
                    color = TextPrimary,
                    fontSize = scoreFontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(if (size <= 50.dp) 2.dp else 6.dp))

        // Status label
        Text(
            text = statusLabel,
            color = if (isMalicious) statusColor.copy(alpha = glowAlpha.coerceIn(0.7f, 1f)) else statusColor,
            fontSize = labelFontSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp
        )
    }
}
