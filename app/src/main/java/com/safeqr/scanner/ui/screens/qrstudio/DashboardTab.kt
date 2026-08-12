package com.safeqr.scanner.ui.screens.qrstudio
import com.safeqr.scanner.ui.screens.*
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.*
import com.safeqr.scanner.data.model.*
import com.safeqr.scanner.security.CertificateEngine
import com.safeqr.scanner.ui.theme.*
import com.safeqr.scanner.viewmodel.QrViewModel
import com.safeqr.scanner.viewmodel.ScannerViewModel
import com.safeqr.scanner.viewmodel.EventViewModel
import com.safeqr.scanner.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.safeqr.scanner.ui.components.CustomQrGenerator
import com.safeqr.scanner.ui.components.QrLogo
import com.safeqr.scanner.ui.components.QrColorTheme
import com.safeqr.scanner.ui.components.QrDotStyle
import com.safeqr.scanner.ui.components.QrEyeStyle
import com.safeqr.scanner.ui.components.QrBgStyle
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalClipboardManager

@Composable
fun DashboardTab(qrVm: QrViewModel, historyVm: HistoryViewModel, eventVm: EventViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser by qrVm.currentUser.collectAsState()
    val qrs by qrVm.dynamicQrs.collectAsState()
    val sharedEvents by qrVm.sharedEvents.collectAsState()
    val scanHistory by historyVm.scanHistory.collectAsState()
    val formatter = remember { java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()) }

    // Filter events for the current user
    val receivedEvents = sharedEvents.filter { it.toUserId == currentUser?.userId }
    
    var selectedTicketEvent by remember { mutableStateOf<SharedQrEvent?>(null) }
    var editingQr by remember { mutableStateOf<com.safeqr.scanner.data.model.DynamicQrEntity?>(null) }
    
    var eventLogs by remember { mutableStateOf<List<com.safeqr.scanner.data.model.AttendanceLogEntity>>(emptyList()) }
    var eventTickets by remember { mutableStateOf<List<com.safeqr.scanner.data.model.TicketEntity>>(emptyList()) }
    var isLoadingLogs by remember { mutableStateOf(false) }
    var selectedCampaignForDashboard by remember { mutableStateOf<com.safeqr.scanner.data.model.DynamicQrEntity?>(null) }

    LaunchedEffect(receivedEvents) {
        isLoadingLogs = true
        // Fetch logs for an event if any exist, just pulling for the first received event for demo
        val eventId = receivedEvents.firstOrNull()?.eventId ?: "EVT-MOCK"
        eventLogs = eventVm.getLogsForEvent(eventId)
        eventTickets = eventVm.getTicketsForEvent(eventId)
        isLoadingLogs = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Campaigns & Analytics", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (qrs.isEmpty() && receivedEvents.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No Dynamic QRs or Event Passes generated yet.", color = TextSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Analytics Overview", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkCard).border(1.dp, GlassBorder, RoundedCornerShape(16.dp)).padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Total Scans", color = TextSecondary, fontSize = 12.sp)
                                    Text("${qrs.sumOf { it.scanCount }}", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Active Campaigns", color = TextSecondary, fontSize = 12.sp)
                                    Text("${qrs.count { it.isActive }}", color = SafeGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            
                            // --- Event Ticketing Analytics ---
                            Text("Event Ticketing Analytics", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Total Check-Ins", color = TextSecondary, fontSize = 12.sp)
                                    val checkIns = eventLogs.filter { it.actionType == com.safeqr.scanner.data.model.ActionType.ENTRY }.size
                                    Text("$checkIns", color = NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Exited Attendees", color = TextSecondary, fontSize = 12.sp)
                                    val exits = eventLogs.filter { it.actionType == com.safeqr.scanner.data.model.ActionType.EXIT }.size
                                    Text("$exits", color = CautionAmber, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Total Tickets", color = TextSecondary, fontSize = 12.sp)
                                    Text("${eventTickets.size}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("No-Shows", color = TextSecondary, fontSize = 12.sp)
                                    val entryLogIds = eventLogs.filter { it.actionType == com.safeqr.scanner.data.model.ActionType.ENTRY }.map { it.ticketId }.toSet()
                                    val noShows = eventTickets.filter { it.ticketId !in entryLogIds }.size
                                    Text("$noShows", color = MaliciousRed, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { Toast.makeText(context, "Exporting No-Shows PDF...", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaliciousRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaliciousRed)
                            ) {
                                Text("Export No-Shows", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            Divider(color = GlassBorder)
                            Spacer(Modifier.height(16.dp))

                            Text("Threat Intelligence Dashboard", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            
                            // Calculate Threat Stats
                            val totalHistory = scanHistory.size
                            val safeScans = scanHistory.count { it.safetyStatus == SafetyStatus.SAFE }
                            val cautionScans = scanHistory.count { it.safetyStatus == SafetyStatus.CAUTION }
                            val maliciousScans = scanHistory.count { it.safetyStatus == SafetyStatus.MALICIOUS }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                // Pie Chart
                                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                        if (totalHistory == 0) {
                                            drawArc(color = GlassBorder, startAngle = 0f, sweepAngle = 360f, useCenter = true)
                                        } else {
                                            val safeAngle = (safeScans.toFloat() / totalHistory) * 360f
                                            val cautionAngle = (cautionScans.toFloat() / totalHistory) * 360f
                                            val maliciousAngle = (maliciousScans.toFloat() / totalHistory) * 360f
                                            
                                            drawArc(color = SafeGreen, startAngle = -90f, sweepAngle = safeAngle, useCenter = true)
                                            drawArc(color = CautionAmber, startAngle = -90f + safeAngle, sweepAngle = cautionAngle, useCenter = true)
                                            drawArc(color = MaliciousRed, startAngle = -90f + safeAngle + cautionAngle, sweepAngle = maliciousAngle, useCenter = true)
                                        }
                                    }
                                }
                                
                                // Legend
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(SafeGreen))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Safe ($safeScans)", color = TextPrimary, fontSize = 12.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(CautionAmber))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Caution ($cautionScans)", color = TextPrimary, fontSize = 12.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaliciousRed))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Malicious ($maliciousScans)", color = TextPrimary, fontSize = 12.sp)
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            Text("Global Heatmap", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF101520)), contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(NeonCyan.copy(alpha=0.6f), radius = 20f, center = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.4f))
                                    drawCircle(PrimaryPurple.copy(alpha=0.6f), radius = 35f, center = androidx.compose.ui.geometry.Offset(size.width * 0.6f, size.height * 0.3f))
                                    drawCircle(SafeGreen.copy(alpha=0.6f), radius = 15f, center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.7f))
                                    drawCircle(NeonCyan.copy(alpha=0.8f), radius = 10f, center = androidx.compose.ui.geometry.Offset(size.width * 0.4f, size.height * 0.8f))
                                }
                                Text("Map View (Demo)", color = TextSecondary.copy(alpha=0.5f))
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { Toast.makeText(context, "Exporting CSV...", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, NeonCyan)) {
                                Text("Export CSV Report", color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Sent Campaigns
                if (qrs.isNotEmpty()) {
                    item {
                        Text("Your Dynamic Campaigns", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(qrs) { qr ->
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkCard).border(1.dp, GlassBorder, RoundedCornerShape(16.dp)).clickable { selectedCampaignForDashboard = qr }.padding(16.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(NeonCyan.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                        Text("⚡", fontSize = 20.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(qr.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("tl.app/${qr.shortCode}", color = NeonCyan, fontSize = 12.sp)
                                        if (qr.maxScans != null) {
                                            Spacer(Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = (qr.scanCount.toFloat() / qr.maxScans).coerceIn(0f, 1f),
                                                modifier = Modifier.fillMaxWidth(0.8f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                                color = if (qr.isActive) NeonCyan else Color(0xFFFF3B30),
                                                trackColor = GlassBorder
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val scanText = if (qr.maxScans != null) "${qr.scanCount} / ${qr.maxScans}" else "${qr.scanCount}"
                                        Text(scanText, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        Text(if (qr.isActive) "Scans" else "INACTIVE", color = if (qr.isActive) TextSecondary else Color(0xFFFF3B30), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Divider(color = GlassBorder)
                                Spacer(Modifier.height(12.dp))
                                
                                // Access Log Preview (Mock)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Visibility, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Latest Access: ${if (qr.scanCount > 0) "2 mins ago (US - NY)" else "No scans yet"}", color = TextSecondary, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                                Spacer(Modifier.height(12.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("STATUS / EXPIRES", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        if (qr.activeFrom != null) Text("Active From: ${formatter.format(java.util.Date(qr.activeFrom))}", color = NeonCyan, fontSize = 12.sp)
                                        
                                        if (qr.expiresAt != null) {
                                            val remainingMs = qr.expiresAt - System.currentTimeMillis()
                                            if (remainingMs > 0) {
                                                val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(remainingMs)
                                                val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaliciousRed, modifier = Modifier.size(12.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Self-Destruct in: ${hours}h ${mins}m", color = MaliciousRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Text("Expired", color = MaliciousRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Text("Never Expires", color = SafeGreen, fontSize = 12.sp)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { qrVm.updateDynamicQrStatus(qr.id, !qr.isActive) },
                                            modifier = Modifier.height(36.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, if (qr.isActive) Color(0xFFFFB300) else Color(0xFF4CAF50)),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text(if (qr.isActive) "Pause" else "Resume", color = if (qr.isActive) Color(0xFFFFB300) else Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { editingQr = qr },
                                            modifier = Modifier.height(36.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, NeonCyan),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text("Edit", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { qrVm.deleteDynamicQr(qr.id) },
                                            modifier = Modifier.height(36.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xFFFF3B30)),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text("Del", color = Color(0xFFFF3B30), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Received Passes
                if (receivedEvents.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text("Received Event Passes", color = SafeGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(receivedEvents) { event ->
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkCard).border(1.dp, SafeGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(16.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(SafeGreen.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                        Text("🎟️", fontSize = 20.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(event.eventName, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("From: ${event.fromUserId}", color = SafeGreen, fontSize = 12.sp)
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Divider(color = GlassBorder)
                                Spacer(Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("RECEIVED ON", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(formatter.format(java.util.Date(event.sharedAt)), color = TextPrimary, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { selectedTicketEvent = event },
                                        colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Show Ticket", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Display the selected ticket QR in a dialog
    if (selectedTicketEvent != null) {
        AlertDialog(
            onDismissRequest = { selectedTicketEvent = null },
            containerColor = DarkCard,
            title = {
                Text("Your Event Pass", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedTicketEvent!!.eventName, color = SafeGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    
                    val timeSlice = System.currentTimeMillis() / 30000
                    val raw = "${selectedTicketEvent!!.qrId}:$timeSlice"
                    val hash = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(raw.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                        .take(8)
                    val ticketPayload = "threatlens://ticket?id=${selectedTicketEvent!!.qrId}&sig=$hash"

                    // Generate QR
                    val qrBitmap = CustomQrGenerator.generate(
                        content = ticketPayload,
                        logo = QrLogo.NONE,
                        colorTheme = QrColorTheme.LIME_GREEN,
                        dotStyle = QrDotStyle.SQUARE,
                        eyeStyle = QrEyeStyle.SQUARE,
                        bgStyle = QrBgStyle.DARK,
                        size = 600
                    )
                    
                    Box(
                        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).background(DarkBackground).border(2.dp, SafeGreen, RoundedCornerShape(16.dp)).padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(qrBitmap.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Present this QR code at the entrance.", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTicketEvent = null }) {
                    Text("Close", color = SafeGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (editingQr != null) {
        var newUrl by remember { mutableStateOf(editingQr!!.targetUrl) }
        var newMaxScans by remember { mutableStateOf(editingQr!!.maxScans?.toString() ?: "") }
        var newExpiry by remember { mutableStateOf("") } // We'll just take days from now for simplicity, or let them pick
        var newActiveFrom by remember { mutableStateOf(editingQr!!.activeFrom) }

        AlertDialog(
            onDismissRequest = { editingQr = null },
            containerColor = DarkCard,
            title = { Text("Edit Campaign: ${editingQr!!.title}", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = newUrl, onValueChange = { newUrl = it }, label = { Text("Target URL") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                    OutlinedTextField(value = newMaxScans, onValueChange = { newMaxScans = it }, label = { Text("Max Scans (Blank = Unlimited)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                    OutlinedTextField(value = newExpiry, onValueChange = { newExpiry = it }, label = { Text("Extend Expiry by Days (Optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                    DateTimePickerButton("Active From:", newActiveFrom, { newActiveFrom = it }, context)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val maxScansInt = newMaxScans.toIntOrNull()
                        qrVm.updateDynamicQr(
                            id = editingQr!!.id,
                            newUrl = newUrl,
                            newExpiryDays = newExpiry.toIntOrNull(),
                            newMaxScans = maxScansInt,
                            newActiveFrom = newActiveFrom
                        )
                        editingQr = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Save Changes", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingQr = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (selectedCampaignForDashboard != null) {
        val qr = selectedCampaignForDashboard!!
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { selectedCampaignForDashboard = null },
            containerColor = DarkSurface,
            contentColor = TextPrimary
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(qr.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                Text("Short URL: tl.app/${qr.shortCode}", fontSize = 14.sp, color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                
                // Detailed Stats Grid
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${qr.scanCount}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Total Scans", fontSize = 12.sp, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val maxStr = qr.maxScans?.toString() ?: "∞"
                        Text(maxStr, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Scan Limit", fontSize = 12.sp, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val statusStr = if (qr.isActive) "Active" else "Paused"
                        Text(statusStr, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (qr.isActive) SafeGreen else Color(0xFFFF3B30))
                        Text("Status", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                Divider(color = GlassBorder)
                Spacer(Modifier.height(16.dp))
                
                // Dates and Info
                Text("CAMPAIGN DETAILS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                
                val fmt = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault())
                Text("Target URL: ${qr.targetUrl}", fontSize = 14.sp)
                if (qr.alternateUrls != null) Text("Has Alternate URLs: Yes", fontSize = 14.sp)
                if (qr.passwordHash != null) Text("Password Protected: Yes", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                
                val createdStr = qr.createdAt.let { fmt.format(java.util.Date(it)) }
                Text("Created: $createdStr", fontSize = 14.sp, color = TextSecondary)
                if (qr.activeFrom != null) Text("Activates: ${fmt.format(java.util.Date(qr.activeFrom!!))}", fontSize = 14.sp, color = NeonCyan)
                if (qr.expiresAt != null) Text("Terminates: ${fmt.format(java.util.Date(qr.expiresAt!!))}", fontSize = 14.sp, color = MaliciousRed)
                else Text("Terminates: Never", fontSize = 14.sp, color = TextSecondary)

                Spacer(Modifier.height(24.dp))
                
                // Quick Actions
                Button(
                    onClick = {
                        qrVm.updateDynamicQrStatus(qr.id, !qr.isActive)
                        selectedCampaignForDashboard = selectedCampaignForDashboard?.copy(isActive = !qr.isActive)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (qr.isActive) Color(0xFFFFB300) else SafeGreen)
                ) {
                    Text(if (qr.isActive) "Pause Campaign" else "Resume Campaign", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

