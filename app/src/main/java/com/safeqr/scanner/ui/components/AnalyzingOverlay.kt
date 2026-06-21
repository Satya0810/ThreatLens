package com.safeqr.scanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.safeqr.scanner.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun AnalyzingOverlay(
    isVisible: Boolean,
    analyzedUrl: String = ""
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300))
    ) {
        // Pick a random animation style when shown
        val animationStyle = 2 // Hardcoded to Matrix Decryption (2nd new animation) per user request

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f)),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "overlay_infinite")

            // Shared Animations
            val corePulse by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "corePulse"
            )

            val scanPhases = listOf(
                "Resolving DNS...",
                "Checking SSL Certificate...",
                "Analyzing URL Pattern...",
                "Scanning for Threats...",
                "Checking Reputation Database...",
                "Running Heuristic Analysis...",
                "Generating Security Report..."
            )
            var currentPhaseIndex by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1200L)
                    currentPhaseIndex = (currentPhaseIndex + 1) % scanPhases.size
                }
            }

            val cursorAlpha by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "cursorAlpha"
            )

            val primaryColor = MaterialTheme.colorScheme.primary
            val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
            val secondaryColor = Color(0xFFB47AFF)
            val neonCyan = Color(0xFF00F0FF)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                    
                    // Style 0: Double-Ring Radar (Original)
                    if (animationStyle == 0) {
                        val outerRotate by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
                            label = "outerRotate"
                        )
                        val innerRotate by infiniteTransition.animateFloat(
                            initialValue = 360f, targetValue = 0f,
                            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
                            label = "innerRotate"
                        )
                        val ringRadius by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(2500, easing = LinearOutSlowInEasing), RepeatMode.Restart),
                            label = "ringRadius"
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val maxRadius = size.width / 2.5f

                            drawCircle(color = primaryColor.copy(alpha = (1f - ringRadius) * 0.3f), radius = maxRadius * ringRadius, center = center, style = Stroke(width = 1.5f.dp.toPx()))
                            val secondRing = (ringRadius + 0.5f) % 1f
                            drawCircle(color = primaryColor.copy(alpha = (1f - secondRing) * 0.15f), radius = maxRadius * secondRing, center = center, style = Stroke(width = 1.dp.toPx()))

                            drawArc(
                                brush = Brush.sweepGradient(listOf(Color.Transparent, primaryColor.copy(alpha = 0.8f), Color.Transparent), center),
                                startAngle = outerRotate, sweepAngle = 100f, useCenter = false,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                                size = Size(maxRadius * 1.6f, maxRadius * 1.6f),
                                topLeft = Offset(center.x - maxRadius * 0.8f, center.y - maxRadius * 0.8f)
                            )
                            drawArc(
                                brush = Brush.sweepGradient(listOf(Color.Transparent, secondaryColor.copy(alpha = 0.6f), Color.Transparent), center),
                                startAngle = innerRotate, sweepAngle = 80f, useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                                size = Size(maxRadius * 1.1f, maxRadius * 1.1f),
                                topLeft = Offset(center.x - maxRadius * 0.55f, center.y - maxRadius * 0.55f)
                            )
                        }
                    }

                    // Style 1: Holographic Laser Sweep
                    if (animationStyle == 1) {
                        val laserY by infiniteTransition.animateFloat(
                            initialValue = -1f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "laserY"
                        )
                        val scannerWidth by infiniteTransition.animateFloat(
                            initialValue = 0.8f, targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(tween(600, easing = FastOutLinearInEasing), RepeatMode.Reverse),
                            label = "scannerWidth"
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val maxRadius = size.width / 2.5f

                            // Removed Hexagon wireframe per user request

                            // Sweeping Laser
                            val lY = center.y + (maxRadius * laserY)
                            drawLine(
                                color = neonCyan,
                                start = Offset(center.x - maxRadius * scannerWidth, lY),
                                end = Offset(center.x + maxRadius * scannerWidth, lY),
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            // Laser Glow
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, neonCyan.copy(alpha = 0.3f), Color.Transparent),
                                    startY = lY - 20f,
                                    endY = lY + 20f
                                ),
                                topLeft = Offset(center.x - maxRadius * scannerWidth, lY - 20f),
                                size = Size(maxRadius * 2 * scannerWidth, 40f)
                            )
                        }
                    }

                    // Style 2: Matrix Data Decryption
                    if (animationStyle == 2) {
                        val ringRotate by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
                            label = "ringRotate"
                        )
                        val dashPhase by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = 100f,
                            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
                            label = "dashPhase"
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val maxRadius = size.width / 2.5f

                            // Multiple dashed concentric circles rotating
                            for (i in 1..4) {
                                val radius = maxRadius * (i / 4f)
                                val color = if (i % 2 == 0) neonCyan else secondaryColor
                                val rotate = if (i % 2 == 0) ringRotate else -ringRotate
                                val dashOn = 10f * i
                                val dashOff = 15f * i
                                
                                withTransform({
                                    rotate(rotate, center)
                                }) {
                                    val rectOffset = Offset(center.x - radius, center.y - radius)
                                    val rectSize = Size(radius * 2, radius * 2)
                                    
                                    drawArc(
                                        color = color.copy(alpha = 0.6f),
                                        startAngle = 0f, sweepAngle = 45f,
                                        useCenter = false,
                                        topLeft = rectOffset, size = rectSize,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    drawArc(
                                        color = color.copy(alpha = 0.6f),
                                        startAngle = 90f, sweepAngle = 90f,
                                        useCenter = false,
                                        topLeft = rectOffset, size = rectSize,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    drawArc(
                                        color = color.copy(alpha = 0.6f),
                                        startAngle = 220f, sweepAngle = 60f,
                                        useCenter = false,
                                        topLeft = rectOffset, size = rectSize,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    drawArc(
                                        color = color.copy(alpha = 0.6f),
                                        startAngle = 310f, sweepAngle = 30f,
                                        useCenter = false,
                                        topLeft = rectOffset, size = rectSize,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }

                    // Core Shield Icon with subtle pulse
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(corePulse)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.2f), Color.Transparent)
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_threatlens_shield),
                            contentDescription = "ThreatLens Logo",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = scanPhases[currentPhaseIndex],
                    color = primaryColor.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "THREAT ANALYSIS IN PROGRESS",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (analyzedUrl.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfaceVariantColor.copy(alpha = 0.4f))
                            .border(1.dp, primaryColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = analyzedUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryColor.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 260.dp)
                            )
                            Text(
                                text = "▎",
                                color = primaryColor.copy(alpha = cursorAlpha),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
