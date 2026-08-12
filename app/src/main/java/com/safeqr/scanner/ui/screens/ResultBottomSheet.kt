package com.safeqr.scanner.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.safeqr.scanner.data.local.ScanDatabase
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.safeqr.scanner.data.model.SafetyStatus
import com.safeqr.scanner.data.model.ScanResult
import com.safeqr.scanner.security.CertificateEngine
import com.safeqr.scanner.ui.components.SafetyIndicator
import com.safeqr.scanner.ui.theme.CautionAmber
import com.safeqr.scanner.ui.theme.GlassWhite
import com.safeqr.scanner.ui.theme.MaliciousRed
import com.safeqr.scanner.ui.theme.NeonCyan
import com.safeqr.scanner.ui.theme.PrimaryBlue
import com.safeqr.scanner.ui.theme.SafeGreen
import com.safeqr.scanner.ui.theme.TextPrimary
import com.safeqr.scanner.ui.theme.TextSecondary
import com.safeqr.scanner.ui.theme.DarkSurface
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultBottomSheet(
    scanResult: ScanResult,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenInSandbox: (String) -> Unit = {},
    onReport: (String, String) -> Unit = {_,_ ->},
    onToggleFavorite: (Boolean) -> Unit = {},
    onAddTag: (String) -> Unit = {},
    autoConnectWifi: Boolean = false,
    onUnlockQR: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val isCertified = remember(scanResult.rawContent) { scanResult.rawContent.startsWith("threatlenscert://") }
    
    LaunchedEffect(scanResult) {
        if (scanResult.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }

    // Staggered animation visibility states
    var showIndicator by remember { mutableStateOf(false) }
    var showDomain by remember { mutableStateOf(false) }
    var showClassification by remember { mutableStateOf(false) }
    var showUrlExpansion by remember { mutableStateOf(false) }
    var showThreats by remember { mutableStateOf(false) }
    var showHeuristics by remember { mutableStateOf(false) }
    var showApiResults by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    // Threat Intel State
    var communityIntel by remember { mutableStateOf<com.safeqr.scanner.data.remote.CloudSyncManager.ThreatIntelReport?>(null) }
    
    LaunchedEffect(scanResult.rawContent) {
        if (!isCertified && scanResult.rawContent.isNotBlank()) {
            communityIntel = com.safeqr.scanner.data.remote.CloudSyncManager.getThreatIntel(scanResult.rawContent)
        }
    }

    // Malicious URL confirmation dialog
    var showMaliciousDialog by remember { mutableStateOf(false) }

    // Adult/Transaction popups
    var showAdultWarningDialog by remember { mutableStateOf(false) }
    var showTransactionWarningDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var isPositiveReport by remember { mutableStateOf(false) }

    val upiPaymentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK || result.resultCode == android.app.Activity.RESULT_CANCELED) {
            val data = result.data?.getStringExtra("response")
            if (data != null) {
                if (data.contains("Status=SUCCESS", ignoreCase = true)) {
                    onAddTag("Paid: Success")
                } else if (data.contains("Status=SUBMITTED", ignoreCase = true)) {
                    onAddTag("Paid: Pending")
                } else {
                    onAddTag("Payment Failed")
                }
            } else if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
                onAddTag("Payment Cancelled")
            }
        }
    }

    LaunchedEffect(Unit) {
        showIndicator = true
        delay(100L)
        showDomain = true
        delay(100L)
        showClassification = true
        delay(100L)
        showUrlExpansion = true
        delay(100L)
        showThreats = true
        delay(100L)
        showHeuristics = true
        delay(100L)
        showApiResults = true
        delay(100L)
        showActions = true
    }

    val statusColor = when {
        scanResult.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS -> MaliciousRed
        scanResult.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.CAUTION -> CautionAmber
        else -> SafeGreen
    }

    val parsedData = remember(scanResult.rawContent) { com.safeqr.scanner.analysis.QrDataParser.parse(scanResult.rawContent) }

    // Derive UPI/WiFi analysis directly from the ScanResult that ThreatAnalyzer already populated.
    // No re-execution of analyzers — just a synchronous type check as a fallback.
    val isUpiScan = remember(scanResult, parsedData) {
        scanResult.upiAnalysis != null ||
        scanResult.isTransaction ||
        parsedData.type == com.safeqr.scanner.analysis.QrDataType.PAYMENT
    }
    val isWifiScan = remember(scanResult, parsedData) {
        scanResult.wifiAnalysis != null ||
        parsedData.type == com.safeqr.scanner.analysis.QrDataType.WIFI
    }
    val activeUpiAnalysis = scanResult.upiAnalysis
    val activeWifiAnalysis = scanResult.wifiAnalysis

    LaunchedEffect(parsedData, autoConnectWifi) {
        if (autoConnectWifi && parsedData.type == com.safeqr.scanner.analysis.QrDataType.WIFI) {
            connectToWifi(context, parsedData)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.12f),
                            statusColor.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 400f
                    )
                )
        ) {
            if (scanResult.isLocked) {
                PasswordUnlockView(
                    scanResult = scanResult,
                    onUnlock = onUnlockQR
                )
                return@ModalBottomSheet
            }

            // SCROLLABLE AREA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(statusColor.copy(alpha = 0.4f))
            )

            // Domain / raw content
            AnimatedVisibility(
                visible = showDomain,
                enter = fadeIn() + slideInVertically { it / 2 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = scanResult.domain ?: if (scanResult.isUrl) scanResult.rawContent else "Scanned Data",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Independent Actions Row (Favorite & Tag)
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favorite Toggle
                        IconButton(
                            onClick = { onToggleFavorite(!scanResult.isFavorite) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (scanResult.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "Favorite",
                                tint = if (scanResult.isFavorite) com.safeqr.scanner.ui.theme.NeonCyan else com.safeqr.scanner.ui.theme.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Add Tag Button
                        var showTagDialog by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showTagDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Label,
                                contentDescription = "Add Tag",
                                tint = com.safeqr.scanner.ui.theme.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        
                        if (showTagDialog) {
                            var tagText by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { showTagDialog = false },
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                title = { Text("Add Tag", color = com.safeqr.scanner.ui.theme.TextPrimary) },
                                text = {
                                    OutlinedTextField(
                                        value = tagText,
                                        onValueChange = { tagText = it },
                                        placeholder = { Text("e.g. Work, Receipt") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = com.safeqr.scanner.ui.theme.TextPrimary,
                                            unfocusedTextColor = com.safeqr.scanner.ui.theme.TextPrimary
                                        )
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        if (tagText.isNotBlank()) onAddTag(tagText.trim())
                                        showTagDialog = false
                                    }) { Text("Add", color = com.safeqr.scanner.ui.theme.NeonCyan) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showTagDialog = false }) { Text("Cancel", color = com.safeqr.scanner.ui.theme.TextSecondary) }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content Type Badge — always visible unless generic URL
            val (contentTypeLabel, contentTypeIcon) = when {
                scanResult.isBrandImpersonation -> "Brand Impersonation" to Icons.Outlined.Warning
                scanResult.isAdultContent && scanResult.isTransaction -> "Adult + Payment" to Icons.Default.Lock
                scanResult.isAdultContent -> "Adult / 18+ Content" to Icons.Default.Lock
                scanResult.isTransaction -> "Payment / Transaction" to Icons.Outlined.CreditCard
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.WIFI -> "Wi-Fi Network" to Icons.Outlined.Security
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.VCARD -> "Contact Card" to Icons.Outlined.Group
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.EMAIL -> "Email Address" to Icons.Outlined.Email
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.SMS -> "SMS Message" to Icons.Outlined.Sms
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.PHONE -> "Phone Number" to Icons.Outlined.Phone
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.EVENT -> "Calendar Event" to Icons.Outlined.Info
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.LOCATION -> "Geolocation" to Icons.Outlined.Place
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.CRYPTO -> "Crypto Wallet" to Icons.Outlined.AccountBalanceWallet
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.PAYMENT -> "Payment Request" to Icons.Outlined.CreditCard
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.APP_STORE -> "App Store Link" to Icons.Outlined.Star
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.AUTHENTICATOR -> "Authenticator 2FA" to Icons.Default.Lock
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.ESIM -> "eSIM Activation" to Icons.Default.SimCard
                parsedData.type == com.safeqr.scanner.analysis.QrDataType.PROVISIONING -> "System Provisioning" to Icons.Default.SettingsApplications
                scanResult.isUrl -> "" to Icons.Outlined.OpenInBrowser // Hide if we can't decide
                else -> "Plain Text / Data" to Icons.Outlined.ContentCopy
            }

            AnimatedVisibility(
                visible = showClassification && contentTypeLabel.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 2 }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val contentTypeColor = when {
                        scanResult.isBrandImpersonation -> MaliciousRed
                        scanResult.isAdultContent -> MaliciousRed
                        scanResult.isTransaction -> CautionAmber
                        else -> PrimaryBlue
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(contentTypeColor.copy(alpha = 0.12f))
                            .border(1.dp, contentTypeColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = contentTypeIcon,
                            contentDescription = null,
                            tint = contentTypeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = contentTypeLabel,
                            color = contentTypeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                    
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isCertified) {
                AnimatedVisibility(
                    visible = showClassification,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    Column {
                        // Premium Intelligence Report Card
                        val statusColor = when (scanResult.safetyStatus) {
                            com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS -> MaliciousRed
                            com.safeqr.scanner.data.model.SafetyStatus.CAUTION -> CautionAmber
                            else -> SafeGreen
                        }

                        val isWebOrDomain = scanResult.isUrl || parsedData.type == com.safeqr.scanner.analysis.QrDataType.URL || scanResult.domain != null
                        val isLinkGuardSafe = scanResult.tags.contains("LinkGuard") && scanResult.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.SAFE

                        if (isWebOrDomain && !isUpiScan && !isWifiScan && !isLinkGuardSafe) {
                            com.safeqr.scanner.ui.components.IntelligenceReportCard(
                                scanResult = scanResult,
                                baseColor = statusColor
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Community Intel Card
                        val intel = communityIntel
                        if (intel != null && intel.totalReports > 0) {
                            com.safeqr.scanner.ui.components.CommunityIntelCard(threatIntel = intel)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            // ── Top-Level Smart Cards (UPI / WiFi) ──
            if (activeUpiAnalysis != null) {
                com.safeqr.scanner.ui.components.UpiPaymentFraudCard(
                    analysis = activeUpiAnalysis
                )
                Spacer(modifier = Modifier.height(12.dp))
                com.safeqr.scanner.ui.components.UpiGuardStatusCard(
                    analysis = activeUpiAnalysis
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else if (activeWifiAnalysis != null) {
                com.safeqr.scanner.ui.components.WifiThreatCard(
                    analysis = activeWifiAnalysis
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Attractive Box for Structured Data
            if (!scanResult.isUrl && !isUpiScan && !isWifiScan) {
                AnimatedVisibility(
                    visible = showDomain,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when(parsedData.type) {
                                com.safeqr.scanner.analysis.QrDataType.WIFI -> Icons.Outlined.Security
                                com.safeqr.scanner.analysis.QrDataType.VCARD -> Icons.Outlined.Group
                                com.safeqr.scanner.analysis.QrDataType.EMAIL -> Icons.Outlined.Email
                                com.safeqr.scanner.analysis.QrDataType.SMS -> Icons.Outlined.Sms
                                com.safeqr.scanner.analysis.QrDataType.PHONE -> Icons.Outlined.Phone
                                com.safeqr.scanner.analysis.QrDataType.CRYPTO -> Icons.Outlined.AccountBalanceWallet
                                com.safeqr.scanner.analysis.QrDataType.PAYMENT -> Icons.Outlined.CreditCard
                                com.safeqr.scanner.analysis.QrDataType.LOCATION -> Icons.Outlined.Place
                                com.safeqr.scanner.analysis.QrDataType.APP_STORE -> Icons.Outlined.Star
                                com.safeqr.scanner.analysis.QrDataType.AUTHENTICATOR -> Icons.Default.Lock
                                com.safeqr.scanner.analysis.QrDataType.TICKET -> Icons.Outlined.Event
                                else -> Icons.Outlined.Info
                            }
                            Icon(imageVector = icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(parsedData.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            if (parsedData.isCertified) {
                                Spacer(modifier = Modifier.weight(1f))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SafeGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = "Certified", tint = SafeGreen, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Certified", color = SafeGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        var humanAddress by remember { mutableStateOf<String?>(null) }
                        val context = LocalContext.current
                        
                        LaunchedEffect(parsedData) {
                            if (parsedData.type == com.safeqr.scanner.analysis.QrDataType.LOCATION) {
                                val coords = parsedData.actionData["geo"] ?: ""
                                val parts = coords.split(",")
                                if (parts.size >= 2) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val lat = parts[0].toDoubleOrNull()
                                            val lng = parts[1].toDoubleOrNull()
                                            if (lat != null && lng != null) {
                                                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                                @Suppress("DEPRECATION")
                                                val addresses = geocoder.getFromLocation(lat, lng, 1)
                                                val address = addresses?.firstOrNull()
                                                if (address != null) {
                                                    val readable = listOfNotNull(address.subLocality, address.locality, address.adminArea, address.countryName)
                                                        .joinToString(", ")
                                                        .takeIf { it.isNotBlank() } ?: address.getAddressLine(0)
                                                    humanAddress = readable
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // ignore network errors
                                        }
                                    }
                                }
                            }
                        }

                        if (activeUpiAnalysis == null && activeWifiAnalysis == null) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Column {
                                    Text(
                                        text = parsedData.primaryText,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 24.sp
                                    )
                                    val displayText = if (parsedData.type == com.safeqr.scanner.analysis.QrDataType.LOCATION && humanAddress != null) humanAddress!! else parsedData.secondaryText
                                    if (displayText != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = displayText,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        )
                                    }
                                    
                                    if (parsedData.type == com.safeqr.scanner.analysis.QrDataType.TEXT) {
                                        Box(modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                                            Text(text = parsedData.rawData, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (parsedData.type == com.safeqr.scanner.analysis.QrDataType.EVENT) {
                            com.safeqr.scanner.ui.components.EventCountdownCard(
                                startTimeStr = parsedData.actionData["start"],
                                title = parsedData.actionData["title"] ?: parsedData.primaryText
                            )
                        } else if (parsedData.type == com.safeqr.scanner.analysis.QrDataType.CRYPTO) {
                            com.safeqr.scanner.ui.components.CryptoBalanceCard(
                                address = parsedData.actionData["address"],
                                coin = parsedData.actionData["coin"],
                                amountRequested = parsedData.actionData["amount"]
                            )
                        } else if (parsedData.type == com.safeqr.scanner.analysis.QrDataType.VERIFIABLE_CREDENTIAL) {
                            val context = LocalContext.current
                            com.safeqr.scanner.ui.components.VerifiableCredentialCard(
                                parsedData = parsedData,
                                onSaveToVault = {
                                    val vcId = parsedData.actionData["vcId"] ?: ""
                                    val type = parsedData.actionData["credentialType"] ?: "CUSTOM"
                                    val issuerName = parsedData.actionData["issuerName"] ?: "Unknown Issuer"
                                    val issuerId = parsedData.actionData["issuerId"] ?: "Unknown"
                                    val subjectName = parsedData.actionData["subjectName"] ?: "Unknown"
                                    val claims = parsedData.actionData["claims"] ?: "{}"
                                    val issuedAt = parsedData.actionData["issuanceDate"]?.toLongOrNull() ?: System.currentTimeMillis()
                                    val expiresAt = parsedData.actionData["expirationDate"]?.toLongOrNull()
                                    
                                    val vcEntity = com.safeqr.scanner.data.model.VerifiableCredentialEntity(
                                        vcId = vcId,
                                        type = type,
                                        issuerName = issuerName,
                                        issuerId = issuerId,
                                        subjectName = subjectName,
                                        claims = claims,
                                        issuedAt = issuedAt,
                                        expiresAt = expiresAt,
                                        qrPayload = scanResult.rawContent,
                                        createdByUserId = "local_vault"
                                    )
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        val db = com.safeqr.scanner.data.local.ScanDatabase.getInstance(context)
                                        db.verifiableCredentialDao().insert(vcEntity)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            Toast.makeText(context, "Credential saved to Secure Vault", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                        // Action Button based on Type
                        val actionContext = LocalContext.current
                        var showUpiWarningDialog by remember { mutableStateOf(false) }
                        
                        // UPI fraud warning dialog
                        if (showUpiWarningDialog && scanResult.upiAnalysis != null) {
                            val upiRisk = scanResult.upiAnalysis
                            AlertDialog(
                                onDismissRequest = { showUpiWarningDialog = false },
                                containerColor = DarkSurface,
                                icon = {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = if (upiRisk.riskLevel == com.safeqr.scanner.analysis.UpiPaymentAnalyzer.RiskLevel.CRITICAL) MaliciousRed else CautionAmber,
                                        modifier = Modifier.size(48.dp)
                                    )
                                },
                                title = {
                                    Text(
                                        "⚠️ Fraud Risk Detected",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column {
                                        Text(
                                            "ThreatLens detected ${upiRisk.flags.size} fraud indicator(s) with ${upiRisk.riskLevel.name} risk level.",
                                            color = TextSecondary,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        upiRisk.flags.take(3).forEach { flag ->
                                            Text(
                                                "${flag.emoji} ${flag.title}",
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Are you SURE you want to proceed with this payment?",
                                            color = MaliciousRed,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showUpiWarningDialog = false
                                            try {
                                                // Record VPA interaction + daily spending even for risky payments
                                                val vpa = parsedData.actionData["payeeAddress"]
                                                if (!vpa.isNullOrBlank()) {
                                                    com.safeqr.scanner.data.UpiGuardPreferences.recordVpaInteraction(
                                                        actionContext, vpa, com.safeqr.scanner.data.UpiGuardPreferences.VpaAction.PAID
                                                    )
                                                }
                                                val amt = parsedData.actionData["amount"]?.toDoubleOrNull()
                                                if (amt != null) {
                                                    com.safeqr.scanner.data.UpiGuardPreferences.addTransaction(actionContext, amt)
                                                }
                                                
                                                val upiIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(parsedData.rawData))
                                                val chooser = android.content.Intent.createChooser(upiIntent, "Pay with")
                                                upiPaymentLauncher.launch(chooser)
                                            } catch (e: Exception) {
                                                Toast.makeText(actionContext, "No UPI app found", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed)
                                    ) {
                                        Text("Pay Anyway", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = { showUpiWarningDialog = false }) {
                                        Text("Cancel", color = SafeGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }
                        
                        // WiFi threat warning dialog
                        var showWifiWarningDialog by remember { mutableStateOf(false) }
                        
                        if (showWifiWarningDialog && scanResult.wifiAnalysis != null) {
                            val wifiRisk = scanResult.wifiAnalysis
                            AlertDialog(
                                onDismissRequest = { showWifiWarningDialog = false },
                                containerColor = DarkSurface,
                                icon = {
                                    Icon(
                                        Icons.Filled.WifiOff,
                                        contentDescription = null,
                                        tint = if (wifiRisk.riskLevel == com.safeqr.scanner.analysis.WifiThreatAnalyzer.RiskLevel.CRITICAL) MaliciousRed else CautionAmber,
                                        modifier = Modifier.size(48.dp)
                                    )
                                },
                                title = {
                                    Text(
                                        "⚠️ Insecure Network",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column {
                                        Text(
                                            "ThreatLens detected ${wifiRisk.flags.size} security issue(s). Encryption: ${wifiRisk.encryptionName} (Grade ${wifiRisk.encryptionGrade}).",
                                            color = TextSecondary,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        wifiRisk.flags.take(3).forEach { flag ->
                                            Text(
                                                "${flag.emoji} ${flag.title}",
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Your data may be intercepted on this network. Continue?",
                                            color = MaliciousRed,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showWifiWarningDialog = false
                                            // Copy password if available, then open WiFi Settings
                                            val wifiPassword = parsedData.actionData["password"]?.removeSurrounding("\"")
                                            val wifiSsid = (parsedData.actionData["ssid"] ?: parsedData.primaryText).removeSurrounding("\"")
                                            if (!wifiPassword.isNullOrBlank()) {
                                                val clipboard = actionContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi Password", wifiPassword))
                                                Toast.makeText(actionContext, "Password copied! Select '$wifiSsid' to connect.", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(actionContext, "Select '$wifiSsid' to connect.", Toast.LENGTH_LONG).show()
                                            }
                                            val wifiSettingsIntent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                                            wifiSettingsIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            actionContext.startActivity(wifiSettingsIntent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CautionAmber)
                                    ) {
                                        Text("Connect Anyway", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = { showWifiWarningDialog = false }) {
                                        Text("Cancel", color = SafeGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }
                        
                        // ── Parental Control Check for Primary Actions ──
                        val parentalContext = LocalContext.current
                        val isChildLocked = com.safeqr.scanner.data.PreferencesManager.isChildLockEnabled(parentalContext)
                        val parentalConfig = if (isChildLocked) com.safeqr.scanner.data.PreferencesManager.getParentalConfig(parentalContext) else null

                        val isParentalBlocked = if (isChildLocked && parentalConfig != null) {
                            when {
                                // Block payments if blockPayment is on
                                parentalConfig.blockPayment && parsedData.type == com.safeqr.scanner.analysis.QrDataType.PAYMENT -> true
                                // Block malicious/caution content always under child lock
                                scanResult.safetyStatus == SafetyStatus.MALICIOUS -> true
                                // Block adult content
                                parentalConfig.blockAdult && scanResult.isAdultContent -> true
                                // Block gaming URLs
                                parentalConfig.blockGaming && scanResult.isUrl && run {
                                    val lUrl = (scanResult.expandedUrl ?: scanResult.rawContent).lowercase()
                                    lUrl.contains("roblox.com") || lUrl.contains("minecraft.net") || lUrl.contains("epicgames.com") || lUrl.contains("steampowered.com") || lUrl.contains("play.google.com/store/apps/category/GAME") || lUrl.contains("fortnite.com") || lUrl.contains("valorant.com") || lUrl.contains("twitch.tv")
                                } -> true
                                // Block social media URLs
                                parentalConfig.blockSocial && scanResult.isUrl && run {
                                    val lUrl = (scanResult.expandedUrl ?: scanResult.rawContent).lowercase()
                                    lUrl.contains("instagram.com") || lUrl.contains("facebook.com") || lUrl.contains("tiktok.com") || lUrl.contains("twitter.com") || lUrl.contains("x.com") || lUrl.contains("snapchat.com") || lUrl.contains("reddit.com") || lUrl.contains("discord.com")
                                } -> true
                                // Block blacklisted domains
                                scanResult.isUrl && parentalConfig.blacklistDomains.any { (scanResult.expandedUrl ?: scanResult.rawContent).lowercase().contains(it.lowercase()) } -> true
                                else -> false
                            }
                        } else false

                        if (isParentalBlocked) {
                            // Show blocked banner instead of action button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaliciousRed.copy(alpha = 0.12f))
                                    .border(1.dp, MaliciousRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaliciousRed, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔒 Blocked by Parental Controls", color = MaliciousRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            // Log the block
                            LaunchedEffect(Unit) {
                                val blockReason = when {
                                    parentalConfig?.blockPayment == true && parsedData.type == com.safeqr.scanner.analysis.QrDataType.PAYMENT -> "Payment Blocked"
                                    scanResult.safetyStatus == SafetyStatus.MALICIOUS -> "Malicious Content Blocked"
                                    scanResult.isAdultContent -> "Adult Content Blocked"
                                    else -> "Parental Rule"
                                }
                                com.safeqr.scanner.data.PreferencesManager.addParentalLog(parentalContext, scanResult.rawContent, "Blocked", blockReason)
                            }
                        } else {
                        // Determine UPI button color based on risk
                        val isUpiDangerous = parsedData.type == com.safeqr.scanner.analysis.QrDataType.PAYMENT &&
                            scanResult.upiAnalysis != null &&
                            (scanResult.upiAnalysis.riskLevel == com.safeqr.scanner.analysis.UpiPaymentAnalyzer.RiskLevel.HIGH ||
                             scanResult.upiAnalysis.riskLevel == com.safeqr.scanner.analysis.UpiPaymentAnalyzer.RiskLevel.CRITICAL)
                        val isUpiCaution = parsedData.type == com.safeqr.scanner.analysis.QrDataType.PAYMENT &&
                            scanResult.upiAnalysis != null &&
                            scanResult.upiAnalysis.riskLevel == com.safeqr.scanner.analysis.UpiPaymentAnalyzer.RiskLevel.MEDIUM
                        
                        // Determine WiFi button color based on risk
                        val isWifiDangerous = parsedData.type == com.safeqr.scanner.analysis.QrDataType.WIFI &&
                            scanResult.wifiAnalysis != null &&
                            (scanResult.wifiAnalysis.riskLevel == com.safeqr.scanner.analysis.WifiThreatAnalyzer.RiskLevel.HIGH ||
                             scanResult.wifiAnalysis.riskLevel == com.safeqr.scanner.analysis.WifiThreatAnalyzer.RiskLevel.CRITICAL)
                        val isWifiCaution = parsedData.type == com.safeqr.scanner.analysis.QrDataType.WIFI &&
                            scanResult.wifiAnalysis != null &&
                            scanResult.wifiAnalysis.riskLevel == com.safeqr.scanner.analysis.WifiThreatAnalyzer.RiskLevel.MEDIUM
                        
                        val buttonColor = when {
                            isUpiDangerous || isWifiDangerous -> MaliciousRed
                            isUpiCaution || isWifiCaution -> CautionAmber
                            else -> PrimaryBlue
                        }
                        
                        Button(
                            onClick = {
                                try {
                                    when(parsedData.type) {
                                        com.safeqr.scanner.analysis.QrDataType.WIFI -> {
                                            if (isWifiDangerous) {
                                                showWifiWarningDialog = true
                                            } else {
                                                // Copy password if available, then open WiFi Settings
                                                val wifiPassword = parsedData.actionData["password"]?.removeSurrounding("\"")
                                                val wifiSsid = (parsedData.actionData["ssid"] ?: parsedData.primaryText).removeSurrounding("\"")
                                                if (!wifiPassword.isNullOrBlank()) {
                                                    val clipboard = actionContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi Password", wifiPassword))
                                                    Toast.makeText(actionContext, "Password copied! Select '$wifiSsid' to connect.", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(actionContext, "Select '$wifiSsid' to connect.", Toast.LENGTH_LONG).show()
                                                }
                                                val wifiSettingsIntent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                                                wifiSettingsIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                actionContext.startActivity(wifiSettingsIntent)
                                            }
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.VCARD -> {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                                type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
                                                putExtra(android.provider.ContactsContract.Intents.Insert.NAME, parsedData.actionData["name"])
                                                parsedData.actionData["phone"]?.let { putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, it) }
                                                parsedData.actionData["email"]?.let { putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, it) }
                                                parsedData.actionData["org"]?.let { putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, it) }
                                            }
                                            actionContext.startActivity(intent)
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.CRYPTO -> {
                                            val address = parsedData.actionData["address"] ?: ""
                                            val amount = parsedData.actionData["amount"]
                                            val uriStr = if (amount != null) "${parsedData.actionData["coin"]?.lowercase()}:$address?amount=$amount" else "${parsedData.actionData["coin"]?.lowercase()}:$address"
                                            try {
                                                actionContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriStr)))
                                            } catch(e: Exception) {
                                                val clipboard = actionContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Crypto Address", address))
                                                Toast.makeText(actionContext, "Address copied (No wallet app found)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.PAYMENT -> {
                                            if (isUpiDangerous) {
                                                // Show warning dialog before allowing payment
                                                showUpiWarningDialog = true
                                            } else {
                                                // Record VPA interaction + daily spending
                                                val vpa = parsedData.actionData["payeeAddress"]
                                                if (!vpa.isNullOrBlank()) {
                                                    com.safeqr.scanner.data.UpiGuardPreferences.recordVpaInteraction(
                                                        actionContext, vpa, com.safeqr.scanner.data.UpiGuardPreferences.VpaAction.PAID
                                                    )
                                                }
                                                val amt = parsedData.actionData["amount"]?.toDoubleOrNull()
                                                if (amt != null) {
                                                    com.safeqr.scanner.data.UpiGuardPreferences.addTransaction(actionContext, amt)
                                                }
                                                
                                                val upiIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(parsedData.rawData))
                                                val chooser = android.content.Intent.createChooser(upiIntent, "Pay with")
                                                upiPaymentLauncher.launch(chooser)
                                            }
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.EMAIL -> {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${parsedData.actionData["email"]}"))
                                            parsedData.actionData["subject"]?.let { intent.putExtra(android.content.Intent.EXTRA_SUBJECT, it) }
                                            parsedData.actionData["body"]?.let { intent.putExtra(android.content.Intent.EXTRA_TEXT, it) }
                                            actionContext.startActivity(intent)
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.SMS -> {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${parsedData.actionData["phone"]}"))
                                            parsedData.actionData["body"]?.let { intent.putExtra("sms_body", it) }
                                            actionContext.startActivity(intent)
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.PHONE -> {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${parsedData.actionData["phone"]}"))
                                            actionContext.startActivity(intent)
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.LOCATION -> {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(parsedData.actionData["geo"])}"))
                                            actionContext.startActivity(intent)
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.EVENT -> {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                                type = "vnd.android.cursor.item/event"
                                                putExtra("title", parsedData.actionData["title"])
                                                parsedData.actionData["location"]?.let { putExtra("eventLocation", it) }
                                            }
                                            actionContext.startActivity(intent)
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.APP_STORE -> {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(parsedData.rawData))
                                            actionContext.startActivity(intent)
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.AUTHENTICATOR -> {
                                            val clipboard = actionContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val secret = parsedData.actionData["secret"] ?: parsedData.rawData
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Authenticator Secret", secret))
                                            Toast.makeText(actionContext, "Secret Copied to Clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.ESIM -> {
                                            try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(parsedData.rawData))
                                                actionContext.startActivity(intent)
                                            } catch (e: Exception) {
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                    val fallbackIntent = android.content.Intent("android.settings.QR_CODE_SCANNER")
                                                    actionContext.startActivity(fallbackIntent)
                                                    Toast.makeText(actionContext, "Please scan the code again with the System Scanner", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(actionContext, "Action not supported on this device", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.PROVISIONING -> {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                val fallbackIntent = android.content.Intent("android.settings.QR_CODE_SCANNER")
                                                actionContext.startActivity(fallbackIntent)
                                                Toast.makeText(actionContext, "Please scan the code again with the System Scanner", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(actionContext, "Action not supported on this device", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        com.safeqr.scanner.analysis.QrDataType.TEXT -> {
                                            val clipboard = actionContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("QR Content", parsedData.rawData))
                                            Toast.makeText(actionContext, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {}
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(actionContext, "Action not supported on this device", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val btnText = when(parsedData.type) {
                                com.safeqr.scanner.analysis.QrDataType.WIFI -> {
                                    if (isWifiDangerous) "⚠️ Connect (Insecure)"
                                    else if (isWifiCaution) "⚡ Connect (Caution)"
                                    else "Open Wi-Fi Settings"
                                }
                                com.safeqr.scanner.analysis.QrDataType.VCARD -> "Add to Contacts"
                                com.safeqr.scanner.analysis.QrDataType.EMAIL -> "Send Email"
                                com.safeqr.scanner.analysis.QrDataType.SMS -> "Send SMS"
                                com.safeqr.scanner.analysis.QrDataType.PHONE -> "Call Phone"
                                com.safeqr.scanner.analysis.QrDataType.LOCATION -> "Open Map"
                                com.safeqr.scanner.analysis.QrDataType.EVENT -> "Add to Calendar"
                                com.safeqr.scanner.analysis.QrDataType.CRYPTO -> "Send / Pay Crypto"
                                com.safeqr.scanner.analysis.QrDataType.PAYMENT -> {
                                    if (isUpiDangerous) "⚠️ Pay via UPI (Risky)"
                                    else if (isUpiCaution) "⚡ Pay via UPI (Caution)"
                                    else "Pay via UPI"
                                }
                                com.safeqr.scanner.analysis.QrDataType.APP_STORE -> "Open App Store"
                                com.safeqr.scanner.analysis.QrDataType.AUTHENTICATOR -> "Copy Secret Key"
                                com.safeqr.scanner.analysis.QrDataType.ESIM -> "Setup eSIM"
                                com.safeqr.scanner.analysis.QrDataType.PROVISIONING -> "Open System Scanner"
                                else -> "Copy Text"
                            }
                            Text(btnText, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                        }
                        } // end of !isParentalBlocked else
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Classification Banners ──────────────────────────────────────────
                // Community Reports are now displayed inside the ThreatLens AI Insight bubble to prevent UI repetition.


            // URL expansion / Redirect Chain info
            if (scanResult.redirectChain.size > 1) {
                AnimatedVisibility(
                    visible = showUrlExpansion,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Redirect Chain (\uD83D\uDD0E Deep Unroller)",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        scanResult.redirectChain.forEachIndexed { index, url ->
                            val isLast = index == scanResult.redirectChain.size - 1
                            val textColor = if (index == 0) CautionAmber else if (isLast) SafeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            )
                            
                            if (!isLast) {
                                Icon(
                                    imageVector = Icons.Outlined.OpenInBrowser,
                                    contentDescription = "Redirects to",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(vertical = 4.dp).size(16.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Aggregated Security Report (Sectional) ──────────────────────────
            // Note: API Threat Intelligence and Heuristic flags are now rendered via IntelligenceReportCard
            
            if (!isCertified) {
                // Section 4: Positive signals
                val positiveItems = scanResult.positiveDetails
                
                if (positiveItems.isNotEmpty()) {
                    AnimatedVisibility(
                        visible = showHeuristics,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        SectionCard(
                            title = "\uD83D\uDD0D Domain Intelligence",
                            items = positiveItems.distinct(),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Domain Info",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // ── Certificate verification (replaces API results for certified QRs) ──
            val certResult = scanResult.certVerifyResult
            if (certResult != null) {
                AnimatedVisibility(
                    visible = showApiResults,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    CertificateVerificationBanner(
                        scanResult = scanResult,
                        verifyResult = certResult,
                        statusColor = statusColor
                    )
                }
            }

            // ── Visit History ──
            if (scanResult.visitHistory.isNotEmpty()) {
                AnimatedVisibility(
                    visible = showThreats, // Reusing showThreats animation timing
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    SectionCard(
                        title = "Visit History (${scanResult.visitCount})",
                        items = scanResult.visitHistory.map {
                            com.safeqr.scanner.ui.components.formatRelativeTime(it) + " - " + 
                            java.text.SimpleDateFormat("MMM dd, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it))
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            } // End of scrollable area



            // --- STICKY SECONDARY ACTIONS ---
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                var shouldBlockAction = false
                val isChildLocked = com.safeqr.scanner.data.PreferencesManager.isChildLockEnabled(context)
                val config = com.safeqr.scanner.data.PreferencesManager.getParentalConfig(context)

                if (scanResult.isUrl) {
                    val targetUrl = scanResult.expandedUrl ?: scanResult.rawContent
                    val lowerUrl = targetUrl.lowercase()
                    val isSocial = lowerUrl.contains("instagram.com") || lowerUrl.contains("facebook.com") || lowerUrl.contains("tiktok.com") || lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com") || lowerUrl.contains("snapchat.com")
                    val isGaming = lowerUrl.contains("roblox.com") || lowerUrl.contains("minecraft.net") || lowerUrl.contains("epicgames.com") || lowerUrl.contains("steampowered.com")
                    
                    if (isChildLocked) {
                        val inWhitelist = config.whitelistDomains.any { lowerUrl.contains(it.lowercase()) }
                        val inBlacklist = config.blacklistDomains.any { lowerUrl.contains(it.lowercase()) }
                        val cal = java.util.Calendar.getInstance()
                        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        val isBedtime = if (config.bedtimeEnabled) {
                            if (config.bedtimeStartHour <= config.bedtimeEndHour) currentHour in config.bedtimeStartHour until config.bedtimeEndHour
                            else currentHour >= config.bedtimeStartHour || currentHour < config.bedtimeEndHour
                        } else false
                        
                        if (inWhitelist) shouldBlockAction = false
                        else if (inBlacklist || isBedtime || (config.blockAdult && scanResult.isAdultContent) || (config.blockPayment && scanResult.isTransaction) || (config.blockSocial && isSocial) || (config.blockGaming && isGaming) || scanResult.safetyStatus == SafetyStatus.CAUTION || scanResult.safetyStatus == SafetyStatus.MALICIOUS) {
                            shouldBlockAction = true
                        }
                    }
                    
                    LaunchedEffect(targetUrl, isChildLocked) {
                        if (isChildLocked) {
                            val action = if (shouldBlockAction) "Blocked" else "Allowed"
                            com.safeqr.scanner.data.PreferencesManager.addParentalLog(context, targetUrl, action, "Parental Rule")
                        }
                    }
                    
                    if (shouldBlockAction) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaliciousRed.copy(alpha = 0.12f))
                                .border(1.dp, MaliciousRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaliciousRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Blocked by Parental Controls", color = MaliciousRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val cyanColor = NeonCyan
                        val redColor = MaliciousRed
                        val greenColor = SafeGreen
                        val textColor = MaterialTheme.colorScheme.onSurface
                        
                        if (!shouldBlockAction) {
                            Button(
                                onClick = { onOpenInSandbox(targetUrl) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Icon(Icons.Outlined.Security, contentDescription = null, tint = cyanColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sandbox", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                        
                        Button(
                            onClick = { isPositiveReport = false; showReportDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = redColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Report", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = { isPositiveReport = true; showReportDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = greenColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Appreciate", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    if (!shouldBlockAction) {
                        val (buttonLabel, buttonIcon) = when {
                            lowerUrl.startsWith("mailto:") -> "Compose Email" to Icons.Outlined.Email
                            lowerUrl.startsWith("tel:") -> "Call Phone" to Icons.Outlined.Phone
                            lowerUrl.startsWith("sms:") || lowerUrl.startsWith("smsto:") -> "Send SMS" to Icons.Outlined.Sms
                            lowerUrl.startsWith("geo:") -> "Open Map" to Icons.Outlined.Place
                            lowerUrl.startsWith("upi:") -> "Pay with..." to Icons.Outlined.CreditCard
                            else -> "Open URL" to Icons.Outlined.OpenInBrowser
                        }
                        Button(
                            onClick = {
                                if (scanResult.safetyStatus == SafetyStatus.MALICIOUS) {
                                    showMaliciousDialog = true
                                } else {
                                    onOpenUrl(targetUrl)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = statusColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(buttonLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } // end if scanResult.isUrl

                // ── Action Buttons for UPI & WiFi ──
                
                if (!scanResult.isUrl && (isUpiScan || isWifiScan)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val redColor = MaliciousRed
                            val greenColor = SafeGreen
                            val textColor = MaterialTheme.colorScheme.onSurface

                            Button(
                                onClick = { isPositiveReport = false; showReportDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Icon(Icons.Outlined.Warning, contentDescription = null, tint = redColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Report", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            Button(
                                onClick = { isPositiveReport = true; showReportDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = greenColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Appreciate", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        if (isUpiScan) {
                            Button(
                                onClick = {
                                    if (scanResult.safetyStatus == SafetyStatus.MALICIOUS) {
                                        showMaliciousDialog = true
                                    } else {
                                        onOpenUrl(scanResult.rawContent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = statusColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Outlined.CreditCard, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pay via UPI App", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (parsedData.type == com.safeqr.scanner.analysis.QrDataType.TEXT) {
                    // Universal "Report Threat" button for Text
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { isPositiveReport = false; showReportDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed.copy(alpha = 0.15f), contentColor = MaliciousRed)
                    ) {
                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaliciousRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Report Threat", color = MaliciousRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!shouldBlockAction) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val textColor = MaterialTheme.colorScheme.onSurface
                        
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val contentToCopy = if (scanResult.isUrl) (scanResult.expandedUrl ?: scanResult.rawContent) else scanResult.rawContent
                                clipboard.setPrimaryClip(ClipData.newPlainText("QR Content", contentToCopy))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (scanResult.isUrl) "Copy Link" else "Copy Text", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        
                        Button(
                            onClick = {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, scanResult.rawContent)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Content"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Threat Report Sheet
    if (showReportDialog && !isPositiveReport) {
        com.safeqr.scanner.ui.components.ThreatReportSheet(
            rawContent = scanResult.rawContent,
            onDismiss = { showReportDialog = false },
            onSubmit = { report ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    com.safeqr.scanner.data.remote.CloudSyncManager.submitThreatReport(report)
                }
                showReportDialog = false
                Toast.makeText(context, "Threat report submitted. Thank you!", Toast.LENGTH_SHORT).show()
                // Update intel optimistically
                val currentIntel = communityIntel ?: com.safeqr.scanner.data.remote.CloudSyncManager.ThreatIntelReport()
                communityIntel = currentIntel.copy(
                    totalReports = currentIntel.totalReports + 1,
                    reportTypes = if (currentIntel.reportTypes.contains(report.reportType)) currentIntel.reportTypes else currentIntel.reportTypes + report.reportType,
                    riskEscalation = if (currentIntel.totalReports + 1 >= 3) "HIGH" else "MEDIUM"
                )
            }
        )
    }

    // Malicious URL confirmation dialog
    if (showMaliciousDialog) {
        AlertDialog(
            onDismissRequest = { showMaliciousDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaliciousRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Malicious URL Detected",
                    color = MaliciousRed,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "This URL has been flagged as potentially dangerous. Opening it may expose your device to malware, phishing, or other threats.\n\nAre you sure you want to proceed?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMaliciousDialog = false
                        val targetUrl = scanResult.expandedUrl ?: scanResult.rawContent
                        onOpenUrl(targetUrl)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed)
                ) {
                    Text("Open Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMaliciousDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    // 🔞 Adult Content Warning Dialog
    if (showAdultWarningDialog) {
        AlertDialog(
            onDismissRequest = { showAdultWarningDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaliciousRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "18+ Content Alert",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "This QR code contains a link classified as adult or 18+ content.\n\nLink: ${scanResult.expandedUrl ?: scanResult.rawContent}\n\nPlease proceed only if you are of legal age and trust the destination.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAdultWarningDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("I Understand")
                }
            }
        )
    }

    // 💳 Transaction Warning Dialog
    if (showTransactionWarningDialog) {
        AlertDialog(
            onDismissRequest = { showTransactionWarningDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = CautionAmber,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Transaction Alert",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "This QR code is related to financial transactions or payments.\n\nContent: ${scanResult.rawContent}\n\nBE CAREFUL: Never send money or approve payments unless you have verified the recipient's identity in person.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { showTransactionWarningDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CautionAmber),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Verify & Continue", color = MaterialTheme.colorScheme.surface)
                }
            }
        )
    }

    if (showReportDialog) {
        val issues = if (isPositiveReport) {
            listOf("Safe & Trustworthy", "Useful Content", "Verified Source", "Other")
        } else {
            listOf("Phishing or Scam", "Malware Download", "Inappropriate Content", "Other")
        }
        var selectedIssue by remember { mutableStateOf(issues.first()) }
        var customIssue by remember { mutableStateOf("") }
        val dialogColor = if (isPositiveReport) SafeGreen else MaliciousRed
        val dialogTitle = if (isPositiveReport) "Appreciate Website" else "Report Website"
        val dialogDesc = if (isPositiveReport) "Select why you appreciate this website:" else "Select the issue you found on this website:"

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = dialogTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(dialogDesc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    issues.forEach { issue ->
                        val isSelected = issue == selectedIssue
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) dialogColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                .border(1.dp, if (isSelected) dialogColor.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { selectedIssue = issue }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                                contentDescription = null,
                                tint = if (isSelected) dialogColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = issue,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                    if (selectedIssue == "Other") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customIssue,
                            onValueChange = { customIssue = it },
                            placeholder = { Text("Please describe...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = dialogColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 3
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalIssue = if (selectedIssue == "Other" && customIssue.isNotBlank()) {
                            customIssue.trim()
                        } else selectedIssue
                        
                        // Add positive/negative context to the report
                        val reportedIssue = if (isPositiveReport) "👍 $finalIssue" else finalIssue
                        
                        onReport(scanResult.rawContent, reportedIssue)
                        showReportDialog = false
                        Toast.makeText(context, "Feedback submitted. Thank you!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dialogColor)
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

/**
 * Banner shown when scanning a ThreatLens-certified QR.
 * Replaces the full API results panel — verification is instant and offline.
 */
@Composable
private fun CertificateVerificationBanner(
    scanResult: ScanResult,
    verifyResult: com.safeqr.scanner.security.CertificateEngine.VerifyResult,
    statusColor: androidx.compose.ui.graphics.Color
) {
    val payload = verifyResult.payload
    val isStale = scanResult.threatDetails.any { it.startsWith("⏰ Stale") }
    val (borderColor, bgColor, icon, title, subtitle) = when {
        verifyResult.isValid && !isStale -> CertBannerStyle(
            border = Color(0xFFFFD700), // Premium Gold
            bg = Color(0xFFFFD700).copy(alpha = 0.1f),
            icon = "🛡️",
            title = "ThreatLens Certified",
            subtitle = "Cryptographically signed and verified. Cert ID: ${payload?.id ?: "—"}"
        )
        verifyResult.isValid && isStale -> CertBannerStyle(
            border = CautionAmber,
            bg = CautionAmber.copy(alpha = 0.08f),
            icon = "⚠️",
            title = "Certified, but STALE",
            subtitle = "Live analysis found issues that were not present when this QR was certified."
        )
        verifyResult.isTampered -> CertBannerStyle(
            border = MaliciousRed,
            bg = MaliciousRed.copy(alpha = 0.08f),
            icon = "🚫",
            title = "Certificate TAMPERED",
            subtitle = "Signature mismatch — this QR was modified after certification. Do not trust."
        )
        else -> CertBannerStyle(
            border = CautionAmber,
            bg = CautionAmber.copy(alpha = 0.08f),
            icon = "⚠️",
            title = "Certificate Unreadable",
            subtitle = "Could not parse certificate data."
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    color = borderColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        if (verifyResult.isValid && payload != null) {
            Spacer(modifier = Modifier.height(12.dp))
            // Show original content and cert metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CertRow("Original content", payload.content, NeonCyan)
                CertRow("Safety status", payload.status, statusColor)
                CertRow("Score", "${payload.score}/100", statusColor)
                CertRow("Cert ID", payload.id, NeonCyan)
                CertRow("Certified at", java.text.SimpleDateFormat(
                    "dd MMM yyyy  HH:mm:ss", java.util.Locale.getDefault()
                ).format(java.util.Date(payload.ts)), MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "🔐 Certificate is cryptographically signed · cannot be forged without ThreatLens private key",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                fontSize = 10.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class CertBannerStyle(
    val border: androidx.compose.ui.graphics.Color,
    val bg: androidx.compose.ui.graphics.Color,
    val icon: String,
    val title: String,
    val subtitle: String
)

@Composable
private fun CertRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.width(110.dp)
        )
        Text(
            value.take(60) + if (value.length > 60) "…" else "",
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Reusable section card for displaying a titled list of items with leading icons.
 */
@Composable
private fun SectionCard(
    title: String,
    items: List<String>,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * A single row showing one security service's result with a colored indicator.
 */
@Composable
private fun ServiceResultRow(
    name: String,
    result: String,
    iconColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = name,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = result,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun connectToWifi(actionContext: android.content.Context, parsedData: com.safeqr.scanner.analysis.ParsedQrData) {
    try {
        val rawSsid = parsedData.actionData["ssid"] ?: parsedData.primaryText
        val rawPassword = parsedData.actionData["password"]
        val wifiType = parsedData.actionData["type"] ?: "WPA"
        
        val ssid = rawSsid.removeSurrounding("\"")
        val password = rawPassword?.removeSurrounding("\"")

        if (ssid.isNotBlank()) {
            val wifiManager = actionContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            
            // Android 11+ (API 30+) Auto-Connect System Dialog
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val builder = android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(ssid)
                    if (!password.isNullOrBlank() && !wifiType.contains("nopass", ignoreCase = true)) {
                        if (wifiType.contains("WEP", ignoreCase = true)) {
                            // WEP is deprecated in newer APIs for Suggestions, skip auto-connect
                            throw IllegalArgumentException("WEP not supported for auto-connect on Android 11+")
                        } else if (wifiType.contains("SAE", ignoreCase = true) || wifiType.contains("WPA3", ignoreCase = true)) {
                            builder.setWpa3Passphrase(password)
                        } else {
                            builder.setWpa2Passphrase(password)
                        }
                    }
                    val suggestion = builder.build()
                    val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_ADD_NETWORKS)
                    intent.putParcelableArrayListExtra(android.provider.Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    actionContext.startActivity(intent)
                    return // Success, exit out
                } catch (e: Exception) {
                    // Intent failed or password invalid, fall through to manual fallback
                }
            } 
            // Android 10 (API 29) Auto-Connect System Dialog
            else if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q) {
                try {
                    val builder = android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(ssid)
                    if (!password.isNullOrBlank() && !wifiType.contains("nopass", ignoreCase = true)) {
                        if (wifiType.contains("WEP", ignoreCase = true)) {
                            throw IllegalArgumentException("WEP not supported for auto-connect on Android 10")
                        } else if (wifiType.contains("SAE", ignoreCase = true) || wifiType.contains("WPA3", ignoreCase = true)) {
                            builder.setWpa3Passphrase(password)
                        } else {
                            builder.setWpa2Passphrase(password)
                        }
                    }
                    val suggestion = builder.build()
                    val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
                    if (status == android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                        android.widget.Toast.makeText(actionContext, "Network suggested. The system may prompt you to connect.", android.widget.Toast.LENGTH_LONG).show()
                        return // Success, exit out
                    }
                } catch (e: Exception) {
                    // Fall through to manual fallback
                }
            }
            // Android 9 and below (API 28-) Direct Auto-Connect
            else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                try {
                    val wifiConfig = android.net.wifi.WifiConfiguration()
                    wifiConfig.SSID = String.format("\"%s\"", ssid)
                    if (password.isNullOrBlank() || wifiType.contains("nopass", ignoreCase = true)) {
                        wifiConfig.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                    } else if (wifiType.contains("WEP", ignoreCase = true)) {
                        wifiConfig.wepKeys[0] = String.format("\"%s\"", password)
                        wifiConfig.wepTxKeyIndex = 0
                        wifiConfig.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                        wifiConfig.allowedGroupCiphers.set(android.net.wifi.WifiConfiguration.GroupCipher.WEP40)
                    } else {
                        wifiConfig.preSharedKey = String.format("\"%s\"", password)
                    }
                    
                    val netId = wifiManager.addNetwork(wifiConfig)
                    if (netId != -1) {
                        wifiManager.disconnect()
                        wifiManager.enableNetwork(netId, true)
                        wifiManager.reconnect()
                        android.widget.Toast.makeText(actionContext, "Connecting to '$ssid'...", android.widget.Toast.LENGTH_SHORT).show()
                        return // Success, exit out
                    }
                } catch (e: Exception) {
                    // Fall through to manual fallback
                }
            }

            // --- Robust Fallback (For any unhandled API, or if auto-connect failed) ---

            if (!password.isNullOrBlank()) {
                val clipboard = actionContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Wi-Fi Password", password))
                android.widget.Toast.makeText(actionContext, "Password copied! Select '$ssid' to connect.", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(actionContext, "Select '$ssid' to connect.", android.widget.Toast.LENGTH_LONG).show()
            }

            val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            fallbackIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            actionContext.startActivity(fallbackIntent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(actionContext, "Failed to initiate Wi-Fi connection", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PasswordUnlockView(
    scanResult: ScanResult,
    onUnlock: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isAttempting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(Brush.radialGradient(listOf(NeonCyan.copy(alpha = 0.2f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Protected QR Code", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text("This QR code is encrypted. Enter the password to view its contents.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan)
        )
        if (error != null) {
            Text(error!!, color = MaliciousRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).align(Alignment.Start))
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (password.isBlank()) return@Button
                isAttempting = true
                val result = com.safeqr.scanner.security.QrEncryptionEngine.decrypt(scanResult.rawContent, password)
                if (result.success && result.content != null) {
                    onUnlock(result.content)
                } else {
                    error = result.error ?: "Incorrect password"
                    isAttempting = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
        ) {
            if (isAttempting) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DarkSurface)
            } else {
                Text("Unlock QR", color = DarkSurface, fontWeight = FontWeight.Bold)
            }
        }
    }
}
