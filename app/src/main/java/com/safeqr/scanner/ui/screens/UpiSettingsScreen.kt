package com.safeqr.scanner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.data.UpiGuardPreferences
import com.safeqr.scanner.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var lateNightWarning by remember {
        mutableStateOf(UpiGuardPreferences.isLateNightWarningEnabled(context))
    }
    var dailyLimit by remember {
        mutableStateOf(UpiGuardPreferences.getDailyLimit(context).toFloat())
    }
    var blockedVpas by remember {
        mutableStateOf(UpiGuardPreferences.getBlockedVpas(context))
    }

    var showLimitDialog by remember { mutableStateOf(false) }

    if (showLimitDialog) {
        var tempLimit by remember { mutableStateOf(dailyLimit.toString()) }
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("Set Daily Limit", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempLimit,
                    onValueChange = { tempLimit = it },
                    label = { Text("Amount (₹)", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextSecondary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limit = tempLimit.toDoubleOrNull()
                        if (limit != null && limit >= 0) {
                            UpiGuardPreferences.setDailyLimit(context, limit)
                            dailyLimit = limit.toFloat()
                            showLimitDialog = false
                        } else {
                            Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UPI Protection", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Security Settings", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Spacer(modifier = Modifier.height(8.dp))
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Late Night Warning",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Alerts for payments between 11 PM - 6 AM",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = lateNightWarning,
                            onCheckedChange = {
                                lateNightWarning = it
                                UpiGuardPreferences.setLateNightWarningEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeonCyan,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = DarkSurface
                            )
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Transaction Limits", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Spacer(modifier = Modifier.height(8.dp))
                GlassCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLimitDialog = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Expenditure Limit",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Warn if daily spending exceeds ₹${String.format("%.2f", dailyLimit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = NeonCyan)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Blocked VPAs", style = MaterialTheme.typography.titleMedium, color = MaliciousRed)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (blockedVpas.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No blocked VPAs.", color = TextSecondary)
                    }
                }
            } else {
                items(blockedVpas) { vpaRecord ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Block, contentDescription = null, tint = MaliciousRed, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = vpaRecord.vpa, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "Blocked on: ${java.text.SimpleDateFormat("MMM dd, yyyy").format(java.util.Date(vpaRecord.lastTimestamp))}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    UpiGuardPreferences.unblockVpa(context, vpaRecord.vpa)
                                    blockedVpas = UpiGuardPreferences.getBlockedVpas(context)
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Unblock", tint = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        GlassBorder.copy(alpha = 0.6f),
                        GlassBorder.copy(alpha = 0.2f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
