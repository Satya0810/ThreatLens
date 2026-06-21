package com.safeqr.scanner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.ui.theme.NeonCyan
import com.safeqr.scanner.ui.theme.NeonCyanGlow
import com.safeqr.scanner.ui.theme.TextPrimary
import com.safeqr.scanner.ui.theme.SafeGreen
import kotlin.math.sin

@Composable
fun ScanOverlay(isAnalyzing: Boolean) {
    val neonCyan = NeonCyan
    val neonCyanGlow = NeonCyanGlow
    val infiniteTransition = rememberInfiniteTransition(label = "scan")

    // Scan line sweeps up and down
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    // Corner pulse (breathing)
    val cornerPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cornerPulse"
    )

    // Traveling dot position along the perimeter (0 to 1)
    val travelDot by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "travelDot"
    )

    // Status text dots animation
    val dotPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotPhase"
    )

    // Text breathing alpha
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textAlpha"
    )

    // Particle phase for floating particles
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particlePhase"
    )

    // Remember particle seed positions
    val particleSeeds = remember {
        List(18) {
            Triple(
                (it * 37 + 13) % 100 / 100f,
                (it * 53 + 7) % 100 / 100f,
                (it * 29 + 17) % 60 / 100f + 0.4f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val cutoutSize = canvasWidth * 0.7f
            val cutoutLeft = (canvasWidth - cutoutSize) / 2
            val cutoutTop = (canvasHeight - cutoutSize) / 2
            val cutoutRect = Rect(cutoutLeft, cutoutTop, cutoutLeft + cutoutSize, cutoutTop + cutoutSize)
            val cornerRadius = 20.dp.toPx()

            // ── Dark overlay with cutout hole ──
            // Removed to remove the transparent box extending over the real scanner
            // val backgroundPath = Path().apply {
            //     addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
            //     addRoundRect(RoundRect(cutoutRect, CornerRadius(cornerRadius, cornerRadius)))
            //     fillType = PathFillType.EvenOdd
            // }
            // drawPath(path = backgroundPath, color = Color.Black.copy(alpha = 0.65f))



            // ── Content inside cutout ──
            clipPath(Path().apply { addRoundRect(RoundRect(cutoutRect, CornerRadius(cornerRadius, cornerRadius))) }) {
                // Grid dots
                val dotSpacing = 24.dp.toPx()
                val cols = (cutoutSize / dotSpacing).toInt()
                val rows = (cutoutSize / dotSpacing).toInt()
                for (c in 0..cols) {
                    for (r in 0..rows) {
                        val dotX = cutoutLeft + c * dotSpacing
                        val dotY = cutoutTop + r * dotSpacing
                        val wave = sin((c + r).toFloat() * 0.5f + particlePhase * 6.28f).coerceIn(0f, 1f)
                        drawCircle(
                            color = neonCyan.copy(alpha = 0.04f + wave * 0.08f),
                            radius = 1.2.dp.toPx(),
                            center = Offset(dotX, dotY)
                        )
                    }
                }

                // Scan line with long glow trail
                val lineY = cutoutTop + (scanLineY * cutoutSize)
                val trailHeight = 60.dp.toPx()

                // Glow trail
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            neonCyanGlow.copy(alpha = 0.15f),
                            neonCyanGlow.copy(alpha = 0.4f),
                            neonCyan.copy(alpha = 0.8f)
                        ),
                        startY = lineY - trailHeight,
                        endY = lineY
                    ),
                    topLeft = Offset(cutoutLeft, lineY - trailHeight),
                    size = Size(cutoutSize, trailHeight)
                )

                // Main laser line
                drawLine(
                    color = neonCyan,
                    start = Offset(cutoutLeft, lineY),
                    end = Offset(cutoutLeft + cutoutSize, lineY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Line edge glows (bright dots at ends)
                drawCircle(
                    color = neonCyan.copy(alpha = 0.6f),
                    radius = 4.dp.toPx(),
                    center = Offset(cutoutLeft, lineY)
                )
                drawCircle(
                    color = neonCyan.copy(alpha = 0.6f),
                    radius = 4.dp.toPx(),
                    center = Offset(cutoutLeft + cutoutSize, lineY)
                )

                // ── Floating particles when analyzing ──
                if (isAnalyzing) {
                    particleSeeds.forEach { (xRatio, yRatio, sizeFactor) ->
                        val px = cutoutLeft + xRatio * cutoutSize
                        val baseY = cutoutTop + cutoutSize
                        val py = baseY - ((particlePhase + yRatio) % 1f) * cutoutSize
                        val drift = sin(particlePhase * 6.28f + xRatio * 10f) * 8.dp.toPx()
                        val alpha = (1f - ((particlePhase + yRatio) % 1f)).coerceIn(0f, 0.6f)
                        drawCircle(
                            color = neonCyan.copy(alpha = alpha * sizeFactor),
                            radius = (1.5.dp.toPx()) * sizeFactor,
                            center = Offset(px + drift, py)
                        )
                    }
                }
            }

            // ── Corner brackets with glow (Rounded) ──
            val cornerLength = 36.dp.toPx()
            val strokeW = 5.dp.toPx()
            val glowStrokeW = 10.dp.toPx()
            val cornerColor = neonCyan.copy(alpha = cornerPulse)
            val glowColor = neonCyanGlow.copy(alpha = cornerPulse * 0.4f)
            
            val cr = cornerRadius
            val dia = cr * 2

            val paths = listOf(
                // Top-Left
                Path().apply {
                    moveTo(cutoutLeft, cutoutTop + cornerLength)
                    lineTo(cutoutLeft, cutoutTop + cr)
                    arcTo(Rect(cutoutLeft, cutoutTop, cutoutLeft + dia, cutoutTop + dia), 180f, 90f, false)
                    lineTo(cutoutLeft + cornerLength, cutoutTop)
                },
                // Top-Right
                Path().apply {
                    moveTo(cutoutLeft + cutoutSize - cornerLength, cutoutTop)
                    lineTo(cutoutLeft + cutoutSize - cr, cutoutTop)
                    arcTo(Rect(cutoutLeft + cutoutSize - dia, cutoutTop, cutoutLeft + cutoutSize, cutoutTop + dia), 270f, 90f, false)
                    lineTo(cutoutLeft + cutoutSize, cutoutTop + cornerLength)
                },
                // Bottom-Right
                Path().apply {
                    moveTo(cutoutLeft + cutoutSize, cutoutTop + cutoutSize - cornerLength)
                    lineTo(cutoutLeft + cutoutSize, cutoutTop + cutoutSize - cr)
                    arcTo(Rect(cutoutLeft + cutoutSize - dia, cutoutTop + cutoutSize - dia, cutoutLeft + cutoutSize, cutoutTop + cutoutSize), 0f, 90f, false)
                    lineTo(cutoutLeft + cutoutSize - cornerLength, cutoutTop + cutoutSize)
                },
                // Bottom-Left
                Path().apply {
                    moveTo(cutoutLeft + cornerLength, cutoutTop + cutoutSize)
                    lineTo(cutoutLeft + cr, cutoutTop + cutoutSize)
                    arcTo(Rect(cutoutLeft, cutoutTop + cutoutSize - dia, cutoutLeft + dia, cutoutTop + cutoutSize), 90f, 90f, false)
                    lineTo(cutoutLeft, cutoutTop + cutoutSize - cornerLength)
                }
            )

            paths.forEach { path ->
                // Glow layer
                drawPath(path = path, color = glowColor, style = Stroke(width = glowStrokeW, cap = StrokeCap.Round))
                // Main corner lines
                drawPath(path = path, color = cornerColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))
            }
        }

        // ── Status text ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(top = 290.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isAnalyzing) {
                    val dots = when {
                        dotPhase < 1f -> "."
                        dotPhase < 2f -> ".."
                        else -> "..."
                    }
                    Text(
                        text = "Analyzing$dots",
                        style = MaterialTheme.typography.bodyLarge,
                        color = neonCyan.copy(alpha = cornerPulse),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
