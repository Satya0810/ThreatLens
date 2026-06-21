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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultBottomSheet(
    scanResult: ScanResult,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenInSandbox: (String) -> Unit = {},
    onReport: (String, String) -> Unit = {_,_ ->},
    autoConnectWifi: Boolean = false
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

    // Malicious URL confirmation dialog
    var showMaliciousDialog by remember { mutableStateOf(false) }

    // Adult/Transaction popups
    var showAdultWarningDialog by remember { mutableStateOf(false) }
    var showTransactionWarningDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var isPositiveReport by remember { mutableStateOf(false) }

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
                Text(
                    text = scanResult.domain ?: if (scanResult.isUrl) scanResult.rawContent else "Scanned Data",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
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
                scanResult.isUrl -> "" to Icons.Outlined.OpenInBrowser // Hide if we can't decide
                else -> "Plain Text / Data" to Icons.Outlined.ContentCopy
            }

            AnimatedVisibility(
                visible = showClassification && contentTypeLabel.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 2 }
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

            Spacer(modifier = Modifier.height(16.dp))

            if (!isCertified) {
                // Premium Intelligence Report Card
                AnimatedVisibility(
                    visible = showIndicator,
                    enter = fadeIn() + slideInVertically { -it / 2 }
                ) {
                    com.safeqr.scanner.ui.components.IntelligenceReportCard(scanResult, statusColor)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Attractive Box for Structured Data
            if (!scanResult.isUrl) {
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
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Smart Cards
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
                        }
                        // Action Button based on Type
                        val actionContext = LocalContext.current
                        Button(
                            onClick = {
                                try {
                                    when(parsedData.type) {
                                        com.safeqr.scanner.analysis.QrDataType.WIFI -> {
                                            connectToWifi(actionContext, parsedData)
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
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(parsedData.rawData))
                                            actionContext.startActivity(intent)
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
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val btnText = when(parsedData.type) {
                                com.safeqr.scanner.analysis.QrDataType.WIFI -> "Open Wi-Fi Settings"
                                com.safeqr.scanner.analysis.QrDataType.VCARD -> "Add to Contacts"
                                com.safeqr.scanner.analysis.QrDataType.EMAIL -> "Send Email"
                                com.safeqr.scanner.analysis.QrDataType.SMS -> "Send SMS"
                                com.safeqr.scanner.analysis.QrDataType.PHONE -> "Call Phone"
                                com.safeqr.scanner.analysis.QrDataType.LOCATION -> "Open Map"
                                com.safeqr.scanner.analysis.QrDataType.EVENT -> "Add to Calendar"
                                com.safeqr.scanner.analysis.QrDataType.CRYPTO -> "Send / Pay Crypto"
                                com.safeqr.scanner.analysis.QrDataType.PAYMENT -> "Pay via UPI"
                                com.safeqr.scanner.analysis.QrDataType.APP_STORE -> "Open App Store"
                                com.safeqr.scanner.analysis.QrDataType.AUTHENTICATOR -> "Copy Secret Key"
                                else -> "Copy Text"
                            }
                            Text(btnText, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                        }
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
                            title = "\u2705 Positive Signals",
                            items = positiveItems.distinct(),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Positive",
                                    tint = SafeGreen,
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
    verifyResult: com.safeqr.scanner.security.CertificateEngine.VerifyResult,
    statusColor: androidx.compose.ui.graphics.Color
) {
    val payload = verifyResult.payload
    val (borderColor, bgColor, icon, title, subtitle) = when {
        verifyResult.isValid -> CertBannerStyle(
            border = SafeGreen,
            bg = SafeGreen.copy(alpha = 0.08f),
            icon = "✅",
            title = "ThreatLens Certificate Valid",
            subtitle = "Signature verified · this QR was certified by ThreatLens · cert ID: ${payload?.id ?: "—"}"
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

            // --- Robust Fallback (For Android 10, or if auto-connect failed) ---
            if (!password.isNullOrBlank()) {
                val clipboard = actionContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Wi-Fi Password", password))
                android.widget.Toast.makeText(actionContext, "Password copied! Select '$ssid' to connect.", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(actionContext, "Select '$ssid' to connect.", android.widget.Toast.LENGTH_LONG).show()
            }

            val fallbackIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.Intent(android.provider.Settings.Panel.ACTION_WIFI)
            } else {
                android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            }
            fallbackIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            actionContext.startActivity(fallbackIntent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(actionContext, "Failed to initiate Wi-Fi connection", android.widget.Toast.LENGTH_SHORT).show()
    }
}
