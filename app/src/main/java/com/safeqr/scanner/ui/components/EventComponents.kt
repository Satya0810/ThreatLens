package com.safeqr.scanner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.data.model.TicketStatus
import com.safeqr.scanner.ui.theme.*

// ── Status Chip ─────────────────────────────────────────────────────────────
@Composable
fun StatusChip(status: TicketStatus, modifier: Modifier = Modifier) {
    val (label, dotColor, bgAlpha) = when (status) {
        TicketStatus.PENDING     -> Triple("Pending",     CautionAmber,  0.12f)
        TicketStatus.CHECKED_IN  -> Triple("Checked In",  SafeGreen,     0.12f)
        TicketStatus.CHECKED_OUT -> Triple("Checked Out", NeonCyan,      0.10f)
        TicketStatus.EXPIRED     -> Triple("Expired",     TextSecondary, 0.08f)
        TicketStatus.REVOKED     -> Triple("Revoked",     MaliciousRed,  0.12f)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(dotColor.copy(alpha = bgAlpha))
            .border(1.dp, dotColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            color = dotColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Tier Badge ──────────────────────────────────────────────────────────────
@Composable
fun TierBadge(tier: String, modifier: Modifier = Modifier) {
    val (bgBrush, textColor) = when (tier.lowercase()) {
        "vip" -> Pair(
            Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFEAB308))),
            Color(0xFF1C1917)
        )
        "staff" -> Pair(
            Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF2563EB))),
            Color.White
        )
        "premium" -> Pair(
            Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED))),
            Color.White
        )
        else -> Pair(
            Brush.horizontalGradient(listOf(GlassBorder, GlassBorder)),
            TextPrimary
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgBrush)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = tier.uppercase(),
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ── Capacity Bar ────────────────────────────────────────────────────────────
@Composable
fun CapacityBar(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    if (total <= 0) {
        if (showLabel) {
            Text("Unlimited", color = TextSecondary, fontSize = 11.sp)
        }
        return
    }

    val fraction = (current.toFloat() / total).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(600),
        label = "capacity"
    )

    val barColor = when {
        fraction >= 0.9f  -> MaliciousRed
        fraction >= 0.7f  -> CautionAmber
        else              -> SafeGreen
    }

    Column(modifier = modifier) {
        if (showLabel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$current / $total",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${(fraction * 100).toInt()}%",
                    color = barColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        LinearProgressIndicator(
            progress = animatedFraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = GlassBorder,
        )
    }
}

// ── Event Color Banner (gradient wash at top of card) ───────────────────────
@Composable
fun EventColorBanner(
    bannerColor: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val color = Color(bannerColor.toInt())
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.25f),
                        color.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                )
            )
    ) {
        content()
    }
}

// ── Section Header ──────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(bottom = 8.dp)
    )
}
