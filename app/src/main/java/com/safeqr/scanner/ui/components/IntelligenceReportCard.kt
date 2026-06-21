package com.safeqr.scanner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.data.model.ScanResult
import com.safeqr.scanner.data.model.SafetyStatus
import com.safeqr.scanner.ui.theme.*

@Composable
fun IntelligenceReportCard(scanResult: ScanResult, baseColor: Color) {
    val isDangerous = scanResult.safetyStatus == SafetyStatus.MALICIOUS
    val isCaution = scanResult.safetyStatus == SafetyStatus.CAUTION
    val isSafe = scanResult.safetyStatus == SafetyStatus.SAFE

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Animated Score
    var scoreAnim by remember { mutableStateOf(0f) }
    LaunchedEffect(scanResult.overallScore) {
        animate(
            initialValue = 0f,
            targetValue = scanResult.overallScore,
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
        ) { value, _ -> scoreAnim = value }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Outer Dashboard container
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = baseColor.copy(alpha = 0.5f),
                ambientColor = baseColor.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .border(
                width = 1.dp,
                color = baseColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        // ── 1. TELEMETRY & CATEGORY GRID ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TELEMETRY",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            
            // Category Pill moved here to save space
            if (!scanResult.siteCategory.contains("Unknown", ignoreCase = true) && !scanResult.siteCategory.contains("General", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(baseColor.copy(alpha = 0.15f))
                        .border(1.dp, baseColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = scanResult.siteCategory.uppercase(),
                        color = baseColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
        
        // ── 2. ANIMATED CIRCULAR TRUST GAUGE & STAMP ──
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Animated Stamp
            val stampScale by animateFloatAsState(
                targetValue = if (isDangerous || isSafe) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "stampScale"
            )

            // Circular Gauge
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = baseColor.copy(alpha = 0.2f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = baseColor,
                        startAngle = 135f,
                        sweepAngle = 270f * (scoreAnim / 100f),
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = scoreAnim.toInt().toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "TRUST SCORE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            // Threat Stamp Overlay positioned gracefully over the gauge
            if (isDangerous || isSafe) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 12.dp)
                        .scale(stampScale)
                        .shadow(8.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, baseColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isDangerous) "MALICIOUS" else "VERIFIED SAFE",
                        color = baseColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val redirects = scanResult.redirectChain.size.coerceAtLeast(1)
            TelemetryCard(
                modifier = Modifier.weight(1f),
                title = "Redirects",
                value = if (redirects > 1) "$redirects Hops" else "Direct",
                icon = Icons.Outlined.Route,
                color = if (redirects > 2) CautionAmber else NeonCyan
            )

            val flags = scanResult.threatDetails.size
            TelemetryCard(
                modifier = Modifier.weight(1f),
                title = "Engine Flags",
                value = "$flags Found",
                icon = Icons.Outlined.Flag,
                color = if (flags > 0) CautionAmber else SafeGreen
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 3. AI INSIGHT BUBBLE ──
        val hasAiInsight = !scanResult.siteSummary.isNullOrBlank()
        val positiveReports = scanResult.communityReportReasons.filter { it.startsWith("👍") }
        val negativeReports = scanResult.communityReportReasons.filter { !it.startsWith("👍") }
        val hasCommunityIntelligence = negativeReports.isNotEmpty() || positiveReports.isNotEmpty() || scanResult.communityReportsCount > 0

        if (hasAiInsight || hasCommunityIntelligence) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                // Glowing Accent Line
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(NeonCyan.copy(alpha = pulseAlpha), Color.Transparent)
                            )
                        )
                )
                Column(modifier = Modifier.padding(16.dp)) {
                    if (hasAiInsight) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "AI",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "THREATLENS AI INSIGHT",
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = scanResult.siteSummary!!,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                    
                    if (hasAiInsight && hasCommunityIntelligence) {
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Divider(color = NeonCyan.copy(alpha = 0.2f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    if (hasCommunityIntelligence) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Group,
                                contentDescription = "Community",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "COMMUNITY INTELLIGENCE",
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (negativeReports.isNotEmpty()) {
                            Text(
                                text = "${negativeReports.size} user(s) flagged this link as dangerous.",
                                color = CautionAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Flags: ${negativeReports.distinct().joinToString(", ")}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                                lineHeight = 16.sp
                            )
                            if (positiveReports.isNotEmpty()) {
                                Text(
                                    text = "Positive feedback also present (${positiveReports.size} user(s)): ${positiveReports.map { it.removePrefix("👍").trim() }.distinct().joinToString(", ")}",
                                    color = SafeGreen.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                    lineHeight = 16.sp
                                )
                            }
                        } else if (positiveReports.isNotEmpty()) {
                            Text(
                                text = "${positiveReports.size} user(s) vouched for this link.",
                                color = SafeGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Feedback: ${positiveReports.map { it.removePrefix("👍").trim() }.distinct().joinToString(", ")}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                                lineHeight = 16.sp
                            )
                        } else if (scanResult.communityReportsCount > 0) {
                            Text(
                                text = "${scanResult.communityReportsCount} user report(s) filed without details.",
                                color = CautionAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ── 4. ENGINE LOGS TIMELINE ──
        val filteredLogs = scanResult.threatDetails.filter { 
            !it.startsWith("VirusTotal:") && !it.startsWith("Category:") && !it.startsWith("Heuristic:")
        }
        if (filteredLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "ENGINE LOGS",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                filteredLogs.take(5).forEachIndexed { index, detail ->
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Timeline Dot
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isDangerous || isCaution) baseColor else NeonCyan)
                                .shadow(4.dp, CircleShape, spotColor = baseColor)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = detail,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                if (filteredLogs.size > 5) {
                    Text(
                        text = "+ ${filteredLogs.size - 5} more intelligence flags...",
                        color = NeonCyan.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp, start = 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryCard(modifier: Modifier = Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
