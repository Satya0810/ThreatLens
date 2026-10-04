package com.safeqr.scanner.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.safeqr.scanner.analysis.graph.ScamWorkflowGraph
import com.safeqr.scanner.ui.theme.*
import kotlinx.coroutines.delay

/**
 * PrePinInterventionDialog — Cognitive Interlock Trance Breaker
 *
 * Designed specifically to halt psychological compliance during active
 * Digital Arrest, Remote Screen-Share, and Reverse-UPI fraud workflows:
 * 1. Enforces a mandatory 5-second countdown to break the adrenaline loop.
 * 2. Reveals true registered recipient legal identity.
 * 3. Highlights exact multi-vector triggers (e.g. stranger call, AnyDesk active).
 * 4. Provides a prominent 1-tap "Cancel & Disconnect" action.
 */
@Composable
fun PrePinInterventionDialog(
    profile: ScamWorkflowGraph.WorkflowRiskProfile,
    payeeName: String?,
    payeeVpa: String?,
    amount: Double?,
    onCancel: () -> Unit,
    onProceedAnyway: () -> Unit
) {
    var secondsRemaining by remember { mutableStateOf(5) }
    val isTimerFinished = secondsRemaining == 0

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaliciousRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Threat Alert",
                        tint = MaliciousRed,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "🚨 Cognitive Security Interlock",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaliciousRed,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Active Scam Workflow Detected",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Explainable Trigger Reasons Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Why is this transaction paused?",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        profile.explainableReasons.take(3).forEach { reason ->
                            Text(
                                text = "• $reason",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real Recipient Identity Card
                if (!payeeVpa.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            if (!payeeName.isNullOrBlank()) {
                                Text(
                                    text = "Claimed Payee: $payeeName",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Destination VPA: $payeeVpa",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Normal
                            )
                            if (amount != null && amount > 0.0) {
                                Text(
                                    text = "Outgoing Debit Amount: ₹$amount",
                                    fontSize = 11.sp,
                                    color = Color(0xFFF87171),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Safe Action: Cancel & Escape
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cancel Payment & Disconnect",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Action: Proceed Anyway (Interlocked with 5s Timer)
                OutlinedButton(
                    onClick = { if (isTimerFinished) onProceedAnyway() },
                    enabled = isTimerFinished,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isTimerFinished) "I Understand, Proceed Anyway" else "Cognitive Pause ($secondsRemaining s)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isTimerFinished) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Report financial fraud immediately to National Helpline 1930",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
