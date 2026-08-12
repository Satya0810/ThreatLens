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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.analysis.WifiThreatAnalyzer
import com.safeqr.scanner.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Premium WiFi Security Analysis Card
 *
 * Displays comprehensive WiFi threat analysis with:
 * - Encryption grade badge (A+/A/B/C/F)
 * - Password strength meter
 * - Evil twin detection banner
 * - Threat flag chips
 * - Network details
 * - Recommendations
 */
@Composable
fun WifiThreatCard(analysis: WifiThreatAnalyzer.WifiAnalysisResult) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        showContent = true
    }

    val riskColor = when (analysis.riskLevel) {
        WifiThreatAnalyzer.RiskLevel.CRITICAL -> MaliciousRed
        WifiThreatAnalyzer.RiskLevel.HIGH -> Color(0xFFFF6B35)
        WifiThreatAnalyzer.RiskLevel.MEDIUM -> CautionAmber
        WifiThreatAnalyzer.RiskLevel.LOW -> SafeGreen
    }

    val gradeColor = Color(analysis.encryptionColor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // ══════════════════════════════════════════════════════════════
        //  ENCRYPTION GRADE + RISK HEADER
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 3 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = riskColor.copy(alpha = 0.3f))
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Wifi, null, tint = riskColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "WiFi Security Analysis",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(riskColor.copy(alpha = 0.15f))
                            .border(1.dp, riskColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${analysis.riskLevel.name} RISK",
                            color = riskColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Encryption Grade Circle ─────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Grade Circle
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .shadow(8.dp, CircleShape, spotColor = gradeColor.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(gradeColor.copy(alpha = 0.2f), Color.Transparent)
                                )
                            )
                            .border(3.dp, gradeColor.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                analysis.encryptionGrade,
                                color = gradeColor,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "GRADE",
                                color = TextSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }

                    // Encryption details
                    Column {
                        Text(
                            analysis.encryptionName,
                            color = gradeColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val shieldIcon = when (analysis.encryptionGrade) {
                                "A+", "A" -> Icons.Filled.Shield
                                "B", "C" -> Icons.Outlined.Shield
                                else -> Icons.Filled.RemoveModerator
                            }
                            Icon(shieldIcon, null, tint = gradeColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when (analysis.encryptionGrade) {
                                    "A+" -> "Best-in-class security"
                                    "A" -> "Strong encryption"
                                    "B" -> "Adequate encryption"
                                    "C" -> "Weak encryption"
                                    else -> "No/Broken encryption"
                                },
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        if (analysis.isHidden) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.VisibilityOff, null, tint = CautionAmber, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hidden Network", color = CautionAmber, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // ── Password Strength Meter ─────────────────────────
                if (analysis.hasPassword && analysis.passwordStrength != WifiThreatAnalyzer.PasswordStrength.NONE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    PasswordStrengthMeter(
                        strength = analysis.passwordStrength,
                        score = analysis.passwordScore
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        //  EVIL TWIN WARNING BANNER (if detected)
        // ══════════════════════════════════════════════════════════════
        if (analysis.isSpoofedSSID) {
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(500, delayMillis = 200)) + slideInVertically(tween(400, delayMillis = 200)) { it / 3 }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(MaliciousRed.copy(alpha = 0.12f), Color(0xFFFF6B35).copy(alpha = 0.08f))
                            )
                        )
                        .border(1.dp, MaliciousRed.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, null, tint = MaliciousRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "⚠️ Potential Evil Twin Attack",
                            color = MaliciousRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "This SSID matches a known public network. Verify with the location's staff before connecting.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        //  NETWORK DETAILS
        // ══════════════════════════════════════════════════════════════
        Spacer(modifier = Modifier.height(12.dp))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Router, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Network Details", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))

                WifiDetailRow("Network", analysis.ssid ?: "Unknown")
                WifiDetailRow("Security", analysis.encryptionName)
                WifiDetailRow("Hidden", if (analysis.isHidden) "Yes (Broadcasting Probes)" else "No (Visible)")
                
                if (analysis.hasPassword && !analysis.password.isNullOrBlank()) {
                    var showPassword by remember { mutableStateOf(false) }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Password", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (showPassword) analysis.password!! else "••••••••",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.IconButton(
                                onClick = { showPassword = !showPassword },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle Password Visibility",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Wi-Fi Password", analysis.password))
                                    android.widget.Toast.makeText(context, "Password copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy Password",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    WifiDetailRow("Password", if (analysis.hasPassword) "Included in QR" else "Not Included")
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        //  THREAT FLAGS
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
                            "Security Issues (${analysis.flags.size})",
                            color = riskColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    analysis.flags.forEach { flag ->
                        WifiFlagItem(flag)
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
                        Text("Recommendations", color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    analysis.recommendations.forEach { rec ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("•", color = TextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(rec, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
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
private fun PasswordStrengthMeter(
    strength: WifiThreatAnalyzer.PasswordStrength,
    score: Float
) {
    val animatedWidth by animateFloatAsState(
        targetValue = score,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "pwdBar"
    )

    val strengthColor = when (strength) {
        WifiThreatAnalyzer.PasswordStrength.EXCELLENT -> SafeGreen
        WifiThreatAnalyzer.PasswordStrength.STRONG -> Color(0xFF22C55E)
        WifiThreatAnalyzer.PasswordStrength.MODERATE -> CautionAmber
        WifiThreatAnalyzer.PasswordStrength.WEAK -> Color(0xFFFF6B35)
        WifiThreatAnalyzer.PasswordStrength.VERY_WEAK -> MaliciousRed
        WifiThreatAnalyzer.PasswordStrength.NONE -> TextSecondary
    }

    val strengthLabel = when (strength) {
        WifiThreatAnalyzer.PasswordStrength.EXCELLENT -> "Excellent"
        WifiThreatAnalyzer.PasswordStrength.STRONG -> "Strong"
        WifiThreatAnalyzer.PasswordStrength.MODERATE -> "Moderate"
        WifiThreatAnalyzer.PasswordStrength.WEAK -> "Weak"
        WifiThreatAnalyzer.PasswordStrength.VERY_WEAK -> "Very Weak"
        WifiThreatAnalyzer.PasswordStrength.NONE -> "None"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, null, tint = strengthColor, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Password Strength", color = TextSecondary, fontSize = 11.sp)
            }
            Text(strengthLabel, color = strengthColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(strengthColor.copy(alpha = 0.7f), strengthColor)
                        )
                    )
            )
        }
    }
}

@Composable
private fun WifiDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(80.dp))
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
private fun WifiFlagItem(flag: WifiThreatAnalyzer.WifiFlag) {
    val flagColor = when (flag.severity) {
        WifiThreatAnalyzer.RiskLevel.CRITICAL -> MaliciousRed
        WifiThreatAnalyzer.RiskLevel.HIGH -> Color(0xFFFF6B35)
        WifiThreatAnalyzer.RiskLevel.MEDIUM -> CautionAmber
        WifiThreatAnalyzer.RiskLevel.LOW -> PrimaryBlue
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
