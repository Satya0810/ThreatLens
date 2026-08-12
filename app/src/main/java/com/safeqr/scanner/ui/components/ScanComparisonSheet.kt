package com.safeqr.scanner.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.data.model.SafetyStatus
import com.safeqr.scanner.data.model.ScanResult
import com.safeqr.scanner.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanComparisonSheet(
    results: List<ScanResult>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Summary stats
    val safeCount = results.count { it.safetyStatus == SafetyStatus.SAFE }
    val cautionCount = results.count { it.safetyStatus == SafetyStatus.CAUTION }
    val maliciousCount = results.count { it.safetyStatus == SafetyStatus.MALICIOUS }
    val unknownCount = results.size - safeCount - cautionCount - maliciousCount
    val avgScore = if (results.isNotEmpty()) results.map { it.overallScore }.average().toFloat() else 0f

    // Determine overall batch verdict
    val (verdictText, verdictColor, verdictIcon) = when {
        maliciousCount > 0 -> Triple("Threats Detected", MaliciousRed, Icons.Default.Shield)
        cautionCount > 0 -> Triple("Caution Advised", CautionAmber, Icons.Default.Warning)
        safeCount > 0 -> Triple("All Clear", SafeGreen, Icons.Default.VerifiedUser)
        else -> Triple("Scan Complete", NeonCyan, Icons.Default.QrCodeScanner)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ── Gradient Header with Verdict ──────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                verdictColor.copy(alpha = 0.15f),
                                verdictColor.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 300f
                        )
                    )
                    .padding(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(verdictColor.copy(alpha = 0.4f))
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Verdict icon with pulsing ring
                    val infiniteTransition = rememberInfiniteTransition(label = "verdict_pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )

                    Box(contentAlignment = Alignment.Center) {
                        // Pulsing ring
                        Box(
                            modifier = Modifier
                                .size((56 * pulseScale).dp)
                                .clip(CircleShape)
                                .background(verdictColor.copy(alpha = pulseAlpha))
                        )
                        // Icon container
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(verdictColor.copy(alpha = 0.15f))
                                .border(1.5.dp, verdictColor.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = verdictIcon,
                                contentDescription = null,
                                tint = verdictColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = verdictText,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = verdictColor
                    )
                    Text(
                        text = "${results.size} QR codes scanned",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Summary Stats Chips ───────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (safeCount > 0) {
                            StatChip(count = safeCount, label = "Safe", color = SafeGreen)
                        }
                        if (cautionCount > 0) {
                            StatChip(count = cautionCount, label = "Caution", color = CautionAmber)
                        }
                        if (maliciousCount > 0) {
                            StatChip(count = maliciousCount, label = "Threats", color = MaliciousRed)
                        }
                        if (unknownCount > 0) {
                            StatChip(count = unknownCount, label = "Unknown", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Average score bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Avg Score",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        LinearProgressIndicator(
                            progress = (avgScore / 100f).coerceIn(0f, 1f),
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = when {
                                avgScore >= 70 -> SafeGreen
                                avgScore >= 40 -> CautionAmber
                                else -> MaliciousRed
                            },
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${avgScore.toInt()}/100",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Results List ──────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(results) { index, result ->
                    // Staggered entry animation
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 80L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        )
                    ) {
                        ComparisonCard(
                            result = result,
                            index = index + 1,
                            context = context
                        )
                    }
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // ── Bottom Action Bar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        // Copy summary to clipboard
                        val summary = buildString {
                            appendLine("ThreatLens Batch Scan Report")
                            appendLine("═".repeat(30))
                            appendLine("Total: ${results.size} | Safe: $safeCount | Caution: $cautionCount | Threats: $maliciousCount")
                            appendLine("Average Score: ${avgScore.toInt()}/100")
                            appendLine()
                            results.forEachIndexed { i, r ->
                                appendLine("${i + 1}. ${r.domain ?: r.rawContent.take(40)}")
                                appendLine("   Status: ${r.safetyStatus.name} | Score: ${r.overallScore.toInt()}/100")
                            }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Batch Report", summary))
                        Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Report", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = verdictColor)
                ) {
                    Icon(Icons.Default.Done, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  Stat Chip — small badge showing count + label
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun StatChip(count: Int, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "$count",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  Comparison Card — rich card for each batch scan result
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun ComparisonCard(
    result: ScanResult,
    index: Int,
    context: Context
) {
    val statusColor = when (result.safetyStatus) {
        SafetyStatus.SAFE -> SafeGreen
        SafetyStatus.CAUTION -> CautionAmber
        SafetyStatus.MALICIOUS -> MaliciousRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusLabel = when (result.safetyStatus) {
        SafetyStatus.SAFE -> "SAFE"
        SafetyStatus.CAUTION -> "CAUTION"
        SafetyStatus.MALICIOUS -> "MALICIOUS"
        else -> "UNKNOWN"
    }

    val statusIcon = when (result.safetyStatus) {
        SafetyStatus.SAFE -> Icons.Default.VerifiedUser
        SafetyStatus.CAUTION -> Icons.Default.Warning
        SafetyStatus.MALICIOUS -> Icons.Default.Shield
        else -> Icons.Default.HelpOutline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        statusColor.copy(alpha = 0.06f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                )
            )
            .border(1.dp, statusColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .clickable {
                val content = result.expandedUrl ?: result.rawContent
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("URL", content))
                Toast.makeText(context, "Copied: ${content.take(40)}...", Toast.LENGTH_SHORT).show()
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Circular Score Indicator ──
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            val score = result.overallScore
            val sweepAngle = (score / 100f * 360f).coerceIn(0f, 360f)

            // Background ring
            Canvas(modifier = Modifier.size(50.dp)) {
                drawArc(
                    color = statusColor.copy(alpha = 0.15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
                // Score arc
                drawArc(
                    color = statusColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${score.toInt()}",
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // ── Content ──
        Column(modifier = Modifier.weight(1f)) {
            // Domain with index
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$index",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = result.domain ?: "Unknown Source",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Raw URL
            Text(
                text = result.rawContent,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Status badge + first threat detail
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // First threat detail (if any)
                if (result.threatDetails.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = result.threatDetails.first().take(30),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Copy icon
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "Copy",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  Canvas helper
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun Canvas(modifier: Modifier = Modifier, onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    androidx.compose.foundation.Canvas(modifier = modifier, onDraw = onDraw)
}
