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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenerateTab(scannerVm: ScannerViewModel, qrVm: QrViewModel, context: Context, eventVm: EventViewModel) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var selectedType by remember { mutableStateOf(QrType.URL) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var certifiedPayload by remember { mutableStateOf<String?>(null) }
    var scanResult by remember { mutableStateOf<com.safeqr.scanner.data.model.ScanResult?>(null) }
    var f1 by remember { mutableStateOf("") }
    
    // Event Scan Policy
    var scanPolicy by remember { mutableStateOf("Single-Use") }
    var customLimit by remember { mutableStateOf("1") }
    var eventId by remember { mutableStateOf("EVT-DEFAULT") }
    var f2 by remember { mutableStateOf("") }
    var f3 by remember { mutableStateOf("") }
    var f4 by remember { mutableStateOf("") }
    var f5 by remember { mutableStateOf("") }
    var f6 by remember { mutableStateOf("") }
    var isBulkMode by remember { mutableStateOf(false) }
    var csvData by remember { mutableStateOf("") }
    var dynamicActiveShortCode by remember { mutableStateOf<String?>(null) }
    var dynamicActiveFrom by remember { mutableStateOf<Long?>(null) }
    var dynamicExpiresAt by remember { mutableStateOf<Long?>(null) }
    var dynamicMaxScans by remember { mutableStateOf("") }
    var selectedCampaignForDashboard by remember { mutableStateOf<com.safeqr.scanner.data.model.DynamicQrEntity?>(null) }

    // Customizer States
    var selectedLogo by remember { mutableStateOf(QrLogo.THREATLENS) }
    var customImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val resolver = context.contentResolver
                // Pass 1: Get bounds
                var inputStream = resolver.openInputStream(uri)
                val options = android.graphics.BitmapFactory.Options()
                options.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                // Calculate downsampling (we only need max ~256x256 for a QR logo)
                var scale = 1
                while (options.outWidth / scale / 2 >= 256 && options.outHeight / scale / 2 >= 256) {
                    scale *= 2
                }

                // Pass 2: Decode scaled
                val options2 = android.graphics.BitmapFactory.Options()
                options2.inSampleSize = scale
                inputStream = resolver.openInputStream(uri)
                val bmp = android.graphics.BitmapFactory.decodeStream(inputStream, null, options2)
                inputStream?.close()
                
                customImageBitmap = bmp
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var selectedColorTheme by remember { mutableStateOf(QrColorTheme.NEON_CYAN) }
    var selectedDotStyle by remember { mutableStateOf(QrDotStyle.ROUNDED) }
    var selectedEyeStyle by remember { mutableStateOf(QrEyeStyle.CYBER_HEX) }
    var selectedBgStyle by remember { mutableStateOf(QrBgStyle.DARK) }
    var selectedSize by remember { mutableStateOf(512) }
    var customFrameText by remember { mutableStateOf("") }
    
    var activeSubTab by remember { mutableStateOf("Data") }
    var certifyEnabled by remember { mutableStateOf(true) }
    var isCertifying by remember { mutableStateOf(false) }
    var passwordProtectEnabled by remember { mutableStateOf(false) }
    var unlockPassword by remember { mutableStateOf("") }
    


    LaunchedEffect(selectedType, f1, f2, f3, f4, f5, f6, selectedLogo, customImageBitmap, selectedColorTheme, selectedDotStyle, selectedEyeStyle, selectedBgStyle, dynamicActiveShortCode, customFrameText, passwordProtectEnabled, unlockPassword) {
        var payload = buildQrPayload(selectedType, f1, f2, f3, f4, f5, dynamicActiveShortCode)
        if (passwordProtectEnabled && unlockPassword.isNotBlank()) {
            payload = com.safeqr.scanner.security.QrEncryptionEngine.encrypt(payload, unlockPassword)
        }
        if (payload.isNotBlank()) {
            delay(300) // Debounce
            qrBitmap = CustomQrGenerator.generate(
                content = payload,
                logo = selectedLogo,
                colorTheme = selectedColorTheme,
                dotStyle = selectedDotStyle,
                eyeStyle = selectedEyeStyle,
                bgStyle = selectedBgStyle,
                customImage = customImageBitmap,
                size = selectedSize,
                frameText = customFrameText.takeIf { it.isNotBlank() }
            )
            certifiedPayload = null
        } else {
            qrBitmap = null
        }
    }

    val vmResult by scannerVm.scanResult.collectAsState()
    if (isCertifying && vmResult != null && vmResult!!.safetyStatus != com.safeqr.scanner.data.model.SafetyStatus.ANALYZING) {
        scanResult = vmResult
        val payload = buildQrPayload(selectedType, f1, f2, f3, f4, f5, dynamicActiveShortCode)
        val certString = CertificateEngine.buildCertifiedPayload(
            originalContent = payload,
            safetyStatus = vmResult!!.safetyStatus.name,
            score = vmResult!!.overallScore.toInt()
        )
        certifiedPayload = certString
        
        var finalPayload = certString
        if (passwordProtectEnabled && unlockPassword.isNotBlank()) {
            finalPayload = com.safeqr.scanner.security.QrEncryptionEngine.encrypt(finalPayload, unlockPassword)
        }

        qrBitmap = CustomQrGenerator.generate(
            content = finalPayload,
            logo = selectedLogo,
            colorTheme = selectedColorTheme,
            dotStyle = selectedDotStyle,
            eyeStyle = selectedEyeStyle,
            bgStyle = selectedBgStyle,
            customImage = customImageBitmap,
            size = selectedSize,
            frameText = customFrameText.takeIf { it.isNotBlank() }
        )
        isCertifying = false
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ── QR Preview Panel ─────────────────────────────────────────────
        var showZoomDialog by remember { mutableStateOf(false) }
        val isGenerating by remember(selectedType, f1, f2, f3, f4, f5, f6, selectedLogo, customImageBitmap, selectedColorTheme, selectedDotStyle, selectedEyeStyle, selectedBgStyle) {
            derivedStateOf { false } // shimmer placeholder — debounce in LaunchedEffect handles actual state
        }

        if (showZoomDialog && qrBitmap != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showZoomDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.92f))
                        .clickable { showZoomDialog = false },
                    contentAlignment = Alignment.Center
                ) {
                    val cardBgColor = when (selectedBgStyle) {
                        QrBgStyle.LIGHT -> Color.White
                        else -> Color(0xFF030509)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(cardBgColor)
                            .border(2.dp, if (certifiedPayload != null) SafeGreen else NeonCyan, RoundedCornerShape(24.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            qrBitmap!!.asImageBitmap(), null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        )
                    }
                    Text(
                        "Tap anywhere to close",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(DarkCard, DarkBackground)))
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (qrBitmap != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val cardBgColor = when (selectedBgStyle) { QrBgStyle.LIGHT -> Color.White; QrBgStyle.DARK, QrBgStyle.GRADIENT_BG -> Color(0xFF030509); QrBgStyle.TRANSPARENT -> Color.Transparent }
                    val borderColor = if (certifiedPayload != null) SafeGreen else NeonCyan.copy(alpha = 0.6f)
                    
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardBgColor)
                            .border(2.dp, borderColor, RoundedCornerShape(20.dp))
                            .clickable { showZoomDialog = true }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(qrBitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
                        // "Tap to zoom" overlay hint
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("🔍", fontSize = 12.sp)
                        }
                    }
                    
                    Spacer(Modifier.height(10.dp))

                    // Payload info chip
                    val payload = buildQrPayload(selectedType, f1, f2, f3, f4, f5, dynamicActiveShortCode)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(NeonCyan.copy(alpha = 0.08f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(selectedType.icon, fontSize = 13.sp)
                        Text("${payload.length} chars", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if (certifiedPayload != null) {
                            Text("• ✅ Certified", color = SafeGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (isCertifying) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Certifying safety...", color = PrimaryBlue, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .border(1.dp, GlassBorder, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.QrCodeScanner, null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Live Preview", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Enter content below to generate", color = TextSecondary.copy(alpha = 0.4f), fontSize = 11.sp)
                    }
                }
            }
        }
        
        val subTabs = listOf(
            "Content" to "📝",
            "Templates" to "📂",
            "Design" to "🎨",
            "Options" to "⚙️"
        )
        // Map old names to new names for backward compat
        if (activeSubTab == "Data") activeSubTab = "Content"
        if (activeSubTab == "Colors") activeSubTab = "Design"
        if (activeSubTab == "Logo") activeSubTab = "Design"
        if (activeSubTab == "Style") activeSubTab = "Design"
        if (activeSubTab == "Advanced") activeSubTab = "Options"

        ScrollableTabRow(
            selectedTabIndex = subTabs.indexOfFirst { it.first == activeSubTab }.coerceAtLeast(0),
            containerColor = Color.Transparent,
            edgePadding = 20.dp,
            indicator = { tabPositions ->
                val index = subTabs.indexOfFirst { it.first == activeSubTab }.coerceAtLeast(0)
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                    color = NeonCyan,
                    height = 3.dp
                )
            },
            divider = { Divider(color = GlassBorder) }
        ) {
            subTabs.forEach { (tabName, tabIcon) ->
                val selected = activeSubTab == tabName
                Tab(
                    selected = selected,
                    onClick = { activeSubTab = tabName; keyboard?.hide() },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tabIcon, fontSize = 13.sp)
                            Text(tabName, color = if (selected) NeonCyan else TextSecondary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                        }
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                when (activeSubTab) {
                    "Content" -> {
                        Text("SELECT TYPE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        
                        val categories = listOf(
                            "🔗 Web & Links" to listOf(QrType.URL, QrType.APP, QrType.YOUTUBE, QrType.SPOTIFY, QrType.SOCIAL),
                            "📱 Communication" to listOf(QrType.PHONE, QrType.SMS, QrType.EMAIL, QrType.WHATSAPP, QrType.TELEGRAM),
                            "💼 Business" to listOf(QrType.CONTACT, QrType.PAYMENT, QrType.PAYPAL, QrType.CRYPTO),
                            "📡 Utilities" to listOf(QrType.WIFI, QrType.LOCATION, QrType.TEXT, QrType.FILE),
                            "⚡ Advanced" to listOf(QrType.DYNAMIC, QrType.TICKET, QrType.EVENT)
                        )
                        
                        categories.forEach { (catName, types) ->
                        Text(catName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                types.forEach { type ->
                                    val sel = selectedType == type
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (sel) NeonCyan.copy(alpha = 0.15f) else DarkCard)
                                            .border(1.dp, if (sel) NeonCyan else GlassBorder, RoundedCornerShape(12.dp))
                                            .clickable {
                                                selectedType = type
                                                f1 = ""; f2 = ""; f3 = ""; f4 = ""; f5 = ""
                                                dynamicActiveShortCode = null
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(type.icon, fontSize = 14.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(type.label, color = if (sel) NeonCyan else TextPrimary, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }

                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selectedType.icon, fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(selectedType.label.uppercase(), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.height(2.dp))
                        Divider(color = GlassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        Text("ENTER CONTENT", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        QrInputFields(selectedType, f1, f2, f3, f4, f5, f6, isBulkMode, { f1 = it }, { f2 = it }, { f3 = it }, { f4 = it }, { f5 = it }, { f6 = it })
                        
                        if (selectedType != QrType.DYNAMIC) {
                            Spacer(Modifier.height(16.dp))
                            SecurityOptionRow(dotColor = PrimaryPurple, title = "Batch Generate", subtitle = "Import CSV (Content, Label, SubLabel) to generate multiple QRs", checked = isBulkMode, onCheckedChange = { isBulkMode = it })
                            if (isBulkMode) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = csvData,
                                    onValueChange = { csvData = it },
                                    label = { Text("CSV (Content, Label, SubLabel)") },
                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                                    maxLines = 10
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val items = com.safeqr.scanner.ui.components.BulkQrManager.parseCsvToGenericItems(csvData)
                                                if (items.isNotEmpty()) {
                                                    val success = com.safeqr.scanner.ui.components.BulkQrManager.generateGenericPdf(
                                                        context = context,
                                                        batchName = f1.ifBlank { "Bulk Export" },
                                                        items = items,
                                                        colorTheme = selectedColorTheme,
                                                        bgStyle = selectedBgStyle,
                                                        dotStyle = selectedDotStyle,
                                                        eyeStyle = selectedEyeStyle,
                                                        logo = selectedLogo
                                                    )
                                                    if (success) Toast.makeText(context, "Batch PDF saved to Downloads", Toast.LENGTH_LONG).show()
                                                    else Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                                                } else Toast.makeText(context, "No valid items in CSV", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                                    ) {
                                        Text("Export PDF", color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val items = com.safeqr.scanner.ui.components.BulkQrManager.parseCsvToGenericItems(csvData)
                                                if (items.isNotEmpty()) {
                                                    val success = com.safeqr.scanner.ui.components.BulkQrManager.generateGenericZip(
                                                        context = context,
                                                        batchName = f1.ifBlank { "Bulk Export" },
                                                        items = items,
                                                        colorTheme = selectedColorTheme,
                                                        bgStyle = selectedBgStyle,
                                                        dotStyle = selectedDotStyle,
                                                        eyeStyle = selectedEyeStyle,
                                                        logo = selectedLogo
                                                    )
                                                    if (success) Toast.makeText(context, "Batch ZIP saved to Downloads", Toast.LENGTH_LONG).show()
                                                    else Toast.makeText(context, "Failed to generate ZIP", Toast.LENGTH_SHORT).show()
                                                } else Toast.makeText(context, "No valid items in CSV", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                    ) {
                                        Text("Export ZIP", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (selectedType == QrType.DYNAMIC && dynamicActiveShortCode == null) {
                            Spacer(Modifier.height(16.dp))
                            DateTimePickerButton("Active From:", dynamicActiveFrom, { dynamicActiveFrom = it }, context)
                            Spacer(Modifier.height(8.dp))
                            DateTimePickerButton("Terminates At:", dynamicExpiresAt, { dynamicExpiresAt = it }, context)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = dynamicMaxScans,
                                onValueChange = { dynamicMaxScans = it },
                                label = { Text("Max Scans before Self-Destruct (Optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan)
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (f1.isNotBlank() && f2.isNotBlank()) {
                                        val targetUrl = if (!f2.startsWith("http")) "https://$f2" else f2
                                        val passHash = if (f5.isNotBlank()) com.safeqr.scanner.data.remote.DynamicLinkManager.hashPassword(f5) else null
                                        val altUrlsList = if (f6.isNotBlank()) f6.split(",").map { it.trim() }.filter { it.isNotEmpty() } else null
                                        val altUrlsJson = altUrlsList?.let { org.json.JSONArray(it).toString() }

                                        val code = qrVm.createDynamicQr(
                                            title = f1,
                                            targetUrl = targetUrl,
                                            expiryDays = null, // Will use precise expiresAt instead
                                            maxScans = dynamicMaxScans.toIntOrNull(),
                                            activeFrom = dynamicActiveFrom,
                                            expiresAt = dynamicExpiresAt,
                                            passwordHash = passHash,
                                            alternateUrls = altUrlsJson
                                        )
                                        com.safeqr.scanner.data.remote.DynamicLinkManager.createLink(
                                            shortCode = code,
                                            destinationUrl = targetUrl,
                                            allowedGeoRegion = f3.ifBlank { null },
                                            passwordHash = passHash,
                                            alternateUrls = altUrlsList,
                                            expiresAt = dynamicExpiresAt,
                                            maxScans = dynamicMaxScans.toIntOrNull(),
                                            activeFrom = dynamicActiveFrom
                                        )
                                        if (f4 == "true") {
                                            com.safeqr.scanner.data.remote.DynamicLinkManager.killLink(code)
                                        }
                                        dynamicActiveShortCode = code
                                        Toast.makeText(context, "Campaign Created! You can now apply styles.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Please enter Title and URL", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Text("Create Campaign & Render", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        } else if (selectedType == QrType.DYNAMIC && dynamicActiveShortCode != null) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val targetUrl = if (!f2.startsWith("http")) "https://$f2" else f2
                                    com.safeqr.scanner.data.remote.DynamicLinkManager.createLink(
                                        shortCode = dynamicActiveShortCode!!,
                                        destinationUrl = targetUrl,
                                        allowedGeoRegion = f3.ifBlank { null },
                                        expiresAt = dynamicExpiresAt,
                                        maxScans = dynamicMaxScans.toIntOrNull(),
                                        activeFrom = dynamicActiveFrom
                                    )
                                    if (f4 == "true") {
                                        com.safeqr.scanner.data.remote.DynamicLinkManager.killLink(dynamicActiveShortCode!!)
                                    }
                                    Toast.makeText(context, "Campaign Rules Updated!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed)
                            ) {
                                Text("Apply Rules", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    "Templates" -> {
                        val savedTemplates = remember { mutableStateOf(com.safeqr.scanner.data.PreferencesManager.getQrTemplates(context)) }
                        var showSaveDialog by remember { mutableStateOf(false) }
                        var templateToDelete by remember { mutableStateOf<String?>(null) }

                        // Save dialog
                        if (showSaveDialog) {
                            var templateName by remember { mutableStateOf("") }
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showSaveDialog = false },
                                containerColor = DarkCard,
                                title = {
                                    Text("Save Template", color = TextPrimary, fontWeight = FontWeight.Bold)
                                },
                                text = {
                                    Column {
                                        Text("Give your template a name:", color = TextSecondary, fontSize = 13.sp)
                                        Spacer(Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = templateName,
                                            onValueChange = { templateName = it.take(30) },
                                            placeholder = { Text("e.g. My Business Card", color = TextSecondary.copy(alpha = 0.5f)) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NeonCyan,
                                                focusedLabelColor = NeonCyan,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary
                                            )
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (templateName.isNotBlank()) {
                                                val tmpl = com.safeqr.scanner.data.PreferencesManager.QrTemplateData(
                                                    name = templateName.trim(),
                                                    type = selectedType.name,
                                                    f1 = f1, f2 = f2, f3 = f3, f4 = f4, f5 = f5,
                                                    colorTheme = selectedColorTheme.name,
                                                    dotStyle = selectedDotStyle.name,
                                                    eyeStyle = selectedEyeStyle.name,
                                                    bgStyle = selectedBgStyle.name,
                                                    logo = selectedLogo.name,
                                                    frameText = customFrameText
                                                )
                                                com.safeqr.scanner.data.PreferencesManager.saveQrTemplate(context, tmpl)
                                                savedTemplates.value = com.safeqr.scanner.data.PreferencesManager.getQrTemplates(context)
                                                showSaveDialog = false
                                                Toast.makeText(context, "Template '${templateName.trim()}' saved!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                                    ) { Text("Save", color = DarkBackground, fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSaveDialog = false }) {
                                        Text("Cancel", color = TextSecondary)
                                    }
                                }
                            )
                        }

                        // Delete confirmation dialog
                        if (templateToDelete != null) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { templateToDelete = null },
                                containerColor = DarkCard,
                                title = {
                                    Text("Delete Template", color = TextPrimary, fontWeight = FontWeight.Bold)
                                },
                                text = {
                                    Text("Delete '${templateToDelete}'? This cannot be undone.", color = TextSecondary)
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            com.safeqr.scanner.data.PreferencesManager.deleteQrTemplate(context, templateToDelete!!)
                                            savedTemplates.value = com.safeqr.scanner.data.PreferencesManager.getQrTemplates(context)
                                            templateToDelete = null
                                            Toast.makeText(context, "Template deleted", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed)
                                    ) { Text("Delete", fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { templateToDelete = null }) {
                                        Text("Cancel", color = TextSecondary)
                                    }
                                }
                            )
                        }

                        // Save button
                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Icon(Icons.Outlined.Save, null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save Current as Template", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(Modifier.height(20.dp))
                        Text("SAVED TEMPLATES", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp))

                        if (savedTemplates.value.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DarkCard)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Outlined.Folder, null, tint = TextSecondary, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("No templates saved yet", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("Create a QR design and save it for quick reuse", color = TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                savedTemplates.value.forEach { tmpl ->
                                    val typeIcon = try { QrType.valueOf(tmpl.type).icon } catch (_: Exception) { "📄" }
                                    val typeLabel = try { QrType.valueOf(tmpl.type).label } catch (_: Exception) { tmpl.type }
                                    val themeColors = try {
                                        val theme = com.safeqr.scanner.ui.components.QrColorTheme.valueOf(tmpl.colorTheme)
                                        theme.colors
                                    } catch (_: Exception) { listOf(0xFF00D4FF.toInt(), 0xFF00D4FF.toInt()) }
                                    val dateStr = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(java.util.Date(tmpl.createdAt))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(DarkCard)
                                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                            .clickable {
                                                // Load template — restore all fields and styles
                                                try { selectedType = QrType.valueOf(tmpl.type) } catch (_: Exception) {}
                                                f1 = tmpl.f1; f2 = tmpl.f2; f3 = tmpl.f3; f4 = tmpl.f4; f5 = tmpl.f5
                                                try { selectedColorTheme = com.safeqr.scanner.ui.components.QrColorTheme.valueOf(tmpl.colorTheme) } catch (_: Exception) {}
                                                try { selectedDotStyle = com.safeqr.scanner.ui.components.QrDotStyle.valueOf(tmpl.dotStyle) } catch (_: Exception) {}
                                                try { selectedEyeStyle = com.safeqr.scanner.ui.components.QrEyeStyle.valueOf(tmpl.eyeStyle) } catch (_: Exception) {}
                                                try { selectedBgStyle = com.safeqr.scanner.ui.components.QrBgStyle.valueOf(tmpl.bgStyle) } catch (_: Exception) {}
                                                try { selectedLogo = com.safeqr.scanner.ui.components.QrLogo.valueOf(tmpl.logo) } catch (_: Exception) {}
                                                customFrameText = tmpl.frameText
                                                activeSubTab = "Content"
                                                Toast.makeText(context, "Template '${tmpl.name}' loaded!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Color swatch
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            Color(themeColors[0]),
                                                            Color(themeColors.getOrElse(1) { themeColors[0] })
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(typeIcon, fontSize = 18.sp)
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tmpl.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                                            Text("$typeLabel • $dateStr", color = TextSecondary, fontSize = 11.sp)
                                        }

                                        // Delete button
                                        IconButton(
                                            onClick = { templateToDelete = tmpl.name },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Outlined.Delete, null, tint = MaliciousRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "Design" -> {
                        Text("CHOOSE THEME", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            QrColorTheme.values().forEach { themeOpt ->
                                val sel = selectedColorTheme == themeOpt
                                val isGradient = themeOpt.colors[0] != themeOpt.colors[1]
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (sel) NeonCyan.copy(alpha = 0.08f) else DarkCard)
                                        .border(1.5.dp, if (sel) NeonCyan else GlassBorder, RoundedCornerShape(14.dp))
                                        .clickable { selectedColorTheme = themeOpt }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Gradient swatch bar
                                    val swatchBrush = if (isGradient)
                                        Brush.horizontalGradient(listOf(Color(themeOpt.colors[0]), Color(themeOpt.colors[1])))
                                    else
                                        Brush.horizontalGradient(listOf(Color(themeOpt.colors[0]), Color(themeOpt.colors[0])))
                                    Box(
                                        modifier = Modifier
                                            .width(52.dp).height(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(swatchBrush)
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(themeOpt.label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        if (isGradient) Text("Gradient", color = TextSecondary, fontSize = 10.sp)
                                    }
                                    if (sel) {
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Outlined.CheckCircle, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("CENTER LOGO", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QrLogo.values().forEach { logoOpt ->
                                val sel = selectedLogo == logoOpt
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (sel) NeonCyan.copy(alpha = 0.12f) else DarkCard)
                                        .border(1.dp, if (sel) NeonCyan else GlassBorder, RoundedCornerShape(12.dp))
                                        .clickable { 
                                            selectedLogo = logoOpt
                                            if (logoOpt == QrLogo.CUSTOM_IMAGE) {
                                                imagePickerLauncher.launch("image/*")
                                            }
                                        }.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val iconStr = when(logoOpt) {
                                        QrLogo.NONE -> "❌"
                                        QrLogo.THREATLENS -> "🛡️"
                                        QrLogo.CUSTOM_IMAGE -> "🖼️"
                                        else -> "✨"
                                    }
                                    Text(iconStr, fontSize = 18.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(logoOpt.label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    
                                    if (logoOpt == QrLogo.CUSTOM_IMAGE && customImageBitmap != null) {
                                        Spacer(Modifier.weight(1f))
                                        Image(customImageBitmap!!.asImageBitmap(), null, modifier = Modifier.size(24.dp).clip(CircleShape))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text("DOT STYLE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(QrDotStyle.values()) { dotOpt ->
                                val sel = selectedDotStyle == dotOpt
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) NeonCyan.copy(alpha = 0.15f) else DarkCard)
                                        .border(1.dp, if (sel) NeonCyan else GlassBorder, RoundedCornerShape(12.dp))
                                        .clickable { selectedDotStyle = dotOpt }.padding(horizontal = 16.dp, vertical = 10.dp)
                                ) { Text(dotOpt.label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("EYE STYLE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(QrEyeStyle.values()) { eyeOpt ->
                                val sel = selectedEyeStyle == eyeOpt
                                val icon = when(eyeOpt) { QrEyeStyle.SQUARE -> "⬜"; QrEyeStyle.ROUNDED -> "⚪"; QrEyeStyle.CYBER_HEX -> "⬡"; QrEyeStyle.GLOW_SHIELD -> "💠" }
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) NeonCyan.copy(alpha = 0.15f) else DarkCard)
                                        .border(1.dp, if (sel) NeonCyan else GlassBorder, RoundedCornerShape(12.dp))
                                        .clickable { selectedEyeStyle = eyeOpt }.padding(horizontal = 16.dp, vertical = 10.dp)
                                ) { Text("$icon ${eyeOpt.label}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("BACKGROUND", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(QrBgStyle.values()) { bgOpt ->
                                val sel = selectedBgStyle == bgOpt
                        val icon = when(bgOpt) { QrBgStyle.LIGHT -> "⚪"; QrBgStyle.DARK -> "⚫"; QrBgStyle.TRANSPARENT -> "🏁"; QrBgStyle.GRADIENT_BG -> "🌌" }
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) NeonCyan.copy(alpha = 0.15f) else DarkCard)
                                        .border(1.dp, if (sel) NeonCyan else GlassBorder, RoundedCornerShape(12.dp))
                                        .clickable { selectedBgStyle = bgOpt }.padding(horizontal = 16.dp, vertical = 10.dp)
                                ) { Text("$icon ${bgOpt.label}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                    "Options" -> {
                        Text("SECURITY OPTIONS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkCard).border(1.dp, GlassBorder, RoundedCornerShape(16.dp)).padding(4.dp)) {
                            SecurityOptionRow(dotColor = SafeGreen, title = "ThreatLens Certified", subtitle = "Scan & stamp before saving", checked = certifyEnabled, onCheckedChange = { certifyEnabled = it })
                            SecurityOptionRow(dotColor = PrimaryBlue, title = "Password Protect", subtitle = "AES-256 encrypt this QR code", checked = passwordProtectEnabled, onCheckedChange = { passwordProtectEnabled = it })
                            if (passwordProtectEnabled) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = unlockPassword,
                                    onValueChange = { unlockPassword = it },
                                    label = { Text("Encryption Password") },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan)
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("FRAME & LABELS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customFrameText,
                            onValueChange = { customFrameText = it },
                            label = { Text("Frame Text (e.g. 'Scan to Connect')") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text("RESOLUTION", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        val sizes = listOf(256 to "Low", 512 to "Med", 1024 to "High")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sizes.forEach { (sz, lbl) ->
                                val sel = selectedSize == sz
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) NeonCyan.copy(alpha = 0.15f) else DarkCard)
                                        .border(1.dp, if (sel) NeonCyan else GlassBorder, RoundedCornerShape(12.dp))
                                        .clickable { selectedSize = sz }.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("$sz px\n$lbl", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
        
        // ── Glassmorphism Action Bar ─────────────────────────────────
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        AnimatedVisibility(
            visible = qrBitmap != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(DarkBackground.copy(alpha = 0f), DarkSurface))
                    )
                    .border(BorderStroke(1.dp, GlassBorder.copy(alpha = 0.6f)), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (certifyEnabled && certifiedPayload == null) {
                    Button(
                        onClick = {
                            isCertifying = true
                            val fullPayload = buildQrPayload(selectedType, f1, f2, f3, f4, f5, dynamicActiveShortCode)
                            scannerVm.analyzeUrl(fullPayload)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Icon(Icons.Outlined.Security, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Certify Safety", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { saveQrToGallery(context, qrBitmap!!) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(0.dp),
                                border = BorderStroke(1.dp, NeonCyan)
                            ) {
                                Icon(Icons.Outlined.Image, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PNG", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { saveSvgToDownloads(context, buildQrPayload(selectedType, f1, f2, f3, f4, f5, dynamicActiveShortCode)) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(0.dp),
                                border = BorderStroke(1.dp, PrimaryPurple)
                            ) {
                                Icon(Icons.Outlined.Code, null, tint = PrimaryPurple, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("SVG", color = PrimaryPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { 
                                    scope.launch { 
                                        com.safeqr.scanner.utils.PdfExportUtils.exportQrAsPdf(
                                            context, 
                                            qrBitmap!!, 
                                            "ThreatLens QR", 
                                            "Payload: ${buildQrPayload(selectedType, f1, f2, f3, f4, f5, dynamicActiveShortCode).take(30)}..."
                                        ) 
                                    } 
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(0.dp),
                                border = BorderStroke(1.dp, MaliciousRed)
                            ) {
                                Icon(Icons.Outlined.PictureAsPdf, null, tint = MaliciousRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PDF", color = MaliciousRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { shareQrBitmap(context, qrBitmap!!) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Icon(Icons.Outlined.Share, null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share QR", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    val payload = buildQrPayload(selectedType, f1, f2, f3, f4, f5, dynamicActiveShortCode)
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(payload))
                                    Toast.makeText(context, "Payload copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, GlassBorder),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Text", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp)) // Extra padding for navigation bar
    }
}

// ── Helpers ──────────────────────────────────────────────────
