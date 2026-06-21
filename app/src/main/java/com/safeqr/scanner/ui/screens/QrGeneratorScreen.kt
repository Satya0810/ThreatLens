package com.safeqr.scanner.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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


private enum class GeneratorTab { GENERATE, DYNAMIC, DASHBOARD }
private enum class GenStep { INPUT, PREVIEW, CERTIFYING, CERTIFIED }

@Composable
fun QrGeneratorScreen(
    viewModel: ScannerViewModel,
    qrViewModel: QrViewModel = viewModel(),
    eventViewModel: EventViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(GeneratorTab.GENERATE) }
    val currentUser by qrViewModel.currentUser.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.QrCode, null, tint = NeonCyan, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text("QR Studio", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            }
            if (currentUser != null) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(NeonCyan.copy(alpha = 0.12f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("👤 ${currentUser!!.displayName.take(10)}", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        // Tabs
        LazyRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tabs = listOf(
                GeneratorTab.GENERATE to "Generate",
                GeneratorTab.DYNAMIC to "Dynamic",
                GeneratorTab.DASHBOARD to "Dashboard"
            )
            items(tabs) { (tab, label) ->
                val sel = activeTab == tab
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (sel) NeonCyan.copy(alpha = 0.15f) else DarkSurface)
                        .border(1.dp, if (sel) NeonCyan.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(20.dp))
                        .clickable { activeTab = tab }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(label, color = if (sel) NeonCyan else TextSecondary, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (activeTab) {
            GeneratorTab.GENERATE -> GenerateTab(viewModel, qrViewModel, context, eventViewModel)
            GeneratorTab.DYNAMIC -> DynamicQrTab(qrViewModel, context, eventViewModel)
            GeneratorTab.DASHBOARD -> DashboardTab(qrViewModel, historyViewModel, eventViewModel)
        }
    }
}

// ── GENERATE TAB ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateTab(scannerVm: ScannerViewModel, qrVm: QrViewModel, context: Context, eventVm: EventViewModel) {
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
    


    LaunchedEffect(selectedType, f1, f2, f3, f4, f5, f6, selectedLogo, customImageBitmap, selectedColorTheme, selectedDotStyle, selectedEyeStyle, selectedBgStyle, dynamicActiveShortCode, customFrameText) {
        val payload = buildQrPayload(selectedType, f1, f2, f3, f4, f5, dynamicActiveShortCode)
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
        qrBitmap = CustomQrGenerator.generate(
            content = certString,
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

    Column(modifier = Modifier.fillMaxSize()) {
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
                        .border(1.dp, GlassBorder, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.QrCodeScanner, null, tint = TextSecondary, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Live Preview", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Enter content below", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }
            }
        }
        
        val subTabs = listOf(
            "Content" to "📝",
            "Design" to "🎨",
            "Logo" to "🏷️",
            "Style" to "✨",
            "Options" to "⚙️"
        )
        // Map old names to new names for backward compat
        if (activeSubTab == "Data") activeSubTab = "Content"
        if (activeSubTab == "Colors") activeSubTab = "Design"
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

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                when (activeSubTab) {
                    "Content" -> {
                        Text("SELECT TYPE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = "${selectedType.icon} ${selectedType.label}",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = NeonCyan,
                                    focusedLabelColor = NeonCyan
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(DarkCard)
                            ) {
                                QrType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text("${type.icon} ${type.label}", color = TextPrimary) },
                                        onClick = {
                                            selectedType = type
                                            f1 = ""; f2 = ""; f3 = ""; f4 = ""; f5 = ""
                                            dynamicActiveShortCode = null
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("ENTER CONTENT", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        QrInputFields(selectedType, f1, f2, f3, f4, f5, f6, isBulkMode, { f1 = it }, { f2 = it }, { f3 = it }, { f4 = it }, { f5 = it }, { f6 = it })
                        
                        if (selectedType == QrType.TICKET) {
                            Spacer(Modifier.height(16.dp))
                            SecurityOptionRow(dotColor = PrimaryPurple, title = "Bulk Generate PDF", subtitle = "Import CSV for batch tickets", checked = isBulkMode, onCheckedChange = { isBulkMode = it })
                            if (isBulkMode) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = csvData,
                                    onValueChange = { csvData = it },
                                    label = { Text("CSV (Name, Tier)") },
                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                                    maxLines = 10
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val tickets = com.safeqr.scanner.ui.components.BulkTicketManager.parseCsvToTickets("EVT-BULK", csvData)
                                            if (tickets.isNotEmpty()) {
                                                val success = com.safeqr.scanner.ui.components.BulkTicketManager.generatePdfTickets(
                                                    context = context,
                                                    eventName = f1.ifBlank { "Bulk Event" },
                                                    tickets = tickets,
                                                    colorTheme = selectedColorTheme,
                                                    bgStyle = selectedBgStyle
                                                )
                                                if (success) {
                                                    Toast.makeText(context, "Bulk PDF saved to Downloads", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "No valid tickets in CSV", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                                ) {
                                    Text("Export Bulk PDF", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (selectedType == QrType.DYNAMIC && dynamicActiveShortCode == null) {
                            Spacer(Modifier.height(16.dp))
                            DateTimePickerButton("Active From:", dynamicActiveFrom, { dynamicActiveFrom = it }, context)
                            Spacer(Modifier.height(8.dp))
                            DateTimePickerButton("Terminates At:", dynamicExpiresAt, { dynamicExpiresAt = it }, context)
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
                                            maxScans = null,
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
                                            alternateUrls = altUrlsList
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
                                        allowedGeoRegion = f3.ifBlank { null }
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
                    }
                    "Logo" -> {
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
                    }
                    "Style" -> {
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                            Button(
                                onClick = { shareQrBitmap(context, qrBitmap!!) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Icon(Icons.Outlined.Share, null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        // Copy payload text button
                        OutlinedButton(
                            onClick = {
                                val payload = buildQrPayload(selectedType, f1, f2, f3, f4, f5, dynamicActiveShortCode)
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(payload))
                                Toast.makeText(context, "Payload copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GlassBorder),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Payload Text", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────
@Composable
private fun QrInputFields(type: QrType, f1: String, f2: String, f3: String, f4: String, f5: String, f6: String = "", isBulkMode: Boolean = false, onF1: (String)->Unit, onF2: (String)->Unit, onF3: (String)->Unit, onF4: (String)->Unit, onF5: (String)->Unit, onF6: (String)->Unit = {}) {
    val kbNext = KeyboardOptions(imeAction = ImeAction.Next)
    when (type) {
        QrType.DYNAMIC -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Campaign Title") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Target URL") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f6, onValueChange = onF6, label = { Text("Alternate URLs (comma separated) for Rotation") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f5, onValueChange = onF5, label = { Text("Password Protection (Optional PIN)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f3, onValueChange = onF3, label = { Text("Allowed Geo-Region (e.g. US, IN) - Optional") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = f4 == "true",
                    onCheckedChange = { if(it) onF4("true") else onF4("false") },
                    colors = CheckboxDefaults.colors(checkedColor = MaliciousRed)
                )
                Text("KILL SWITCH (Deactivate Link)", color = if (f4 == "true") MaliciousRed else TextSecondary)
            }
        }
        QrType.URL, QrType.FILE, QrType.APP, QrType.SOCIAL, QrType.SPOTIFY, QrType.YOUTUBE -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("URL / Link") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.TEXT -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Text Content") }, modifier = Modifier.fillMaxWidth().height(100.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.EMAIL -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f3, onValueChange = onF3, label = { Text("Body") }, modifier = Modifier.fillMaxWidth().height(100.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.PHONE, QrType.WHATSAPP -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.SMS -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Message") }, modifier = Modifier.fillMaxWidth().height(100.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.WIFI -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Network Name (SSID)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.CONTACT -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f3, onValueChange = onF3, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f4, onValueChange = onF4, label = { Text("Organization / Company") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.PAYMENT -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("UPI ID") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Payee Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.PAYPAL -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("PayPal Username / Email") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.TELEGRAM -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Telegram Username") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.LOCATION -> {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            
            OutlinedTextField(
                value = f3, // using f3 to hold the search query state so it persists
                onValueChange = onF3,
                label = { Text("Search by Address or Place") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                trailingIcon = {
                    IconButton(onClick = {
                        if (f3.isNotBlank()) {
                            Toast.makeText(context, "Searching...", Toast.LENGTH_SHORT).show()
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                    @Suppress("DEPRECATION")
                                    val addresses = geocoder.getFromLocationName(f3, 1)
                                    val address = addresses?.firstOrNull()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        if (address != null && address.hasLatitude() && address.hasLongitude()) {
                                            onF1(address.latitude.toString())
                                            onF2(address.longitude.toString())
                                            Toast.makeText(context, "Coordinates found!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Location not found.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch(e: Exception) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Error searching location.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search", tint = NeonCyan)
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Latitude") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Longitude") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            }
            Spacer(Modifier.height(12.dp))
            
            // Get Current Location implementation
            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                    permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
                    // Try to fetch location again after granted
                    Toast.makeText(context, "Permission granted! Tap again to get location.", Toast.LENGTH_SHORT).show()
                }
            }

            OutlinedButton(
                onClick = {
                    val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    
                    if (hasFine || hasCoarse) {
                        try {
                            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                            val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                            
                            if (!isGpsEnabled && !isNetworkEnabled) {
                                Toast.makeText(context, "Please enable Location Services in Settings", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }

                            val location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                            
                            if (location != null) {
                                onF1(location.latitude.toString())
                                onF2(location.longitude.toString())
                                Toast.makeText(context, "Location fetched successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Fetching fresh location...", Toast.LENGTH_SHORT).show()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    locationManager.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, null, context.mainExecutor) { loc ->
                                        if (loc != null) {
                                            onF1(loc.latitude.toString())
                                            onF2(loc.longitude.toString())
                                        } else {
                                            Toast.makeText(context, "Could not determine location.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            Toast.makeText(context, "Permission error", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        permissionLauncher.launch(arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NeonCyan)
            ) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get Current Location", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        }
        QrType.EVENT -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Event Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f5, onValueChange = onF5, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Start Time (YYYYMMDDTHHMMSSZ)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f3, onValueChange = onF3, label = { Text("End Time (YYYYMMDDTHHMMSSZ)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f4, onValueChange = onF4, label = { Text("Location") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.CRYPTO -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Wallet Address") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Amount (Optional)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.TICKET -> {
            if (!isBulkMode) {
                OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Ticket ID (e.g. TKT-1234)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Attendee Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = f3, onValueChange = onF3, label = { Text("Tier (e.g. VIP)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            } else {
                OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Event Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            }
        }
    }
    // Auto-issue the ticket to the simulated cloud when TICKET is selected
    if (type == QrType.TICKET && f1.isNotBlank() && !isBulkMode) {
        LaunchedEffect(f1, f2, f3) {
            val ticket = com.safeqr.scanner.data.model.CloudEventTicket(
                ticketId = f1,
                eventId = "EVT-MOCK",
                attendeeId = "GUEST-001",
                attendeeName = f2.ifBlank { "Guest" },
                ticketTier = f3.ifBlank { "Standard" },
                signatureHash = "mock_hash"
            )
            com.safeqr.scanner.data.remote.CloudSyncManager.issueTicket(ticket)
        }
    }
}

@Composable
fun DateTimePickerButton(
    label: String,
    timestamp: Long?,
    onTimestampSelected: (Long?) -> Unit,
    context: android.content.Context
) {
    val formatter = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
    val text = if (timestamp != null) formatter.format(java.util.Date(timestamp)) else "Not Set"

    OutlinedButton(
        onClick = {
            val calendar = java.util.Calendar.getInstance()
            if (timestamp != null) calendar.timeInMillis = timestamp

            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    android.app.TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                            calendar.set(java.util.Calendar.MINUTE, minute)
                            onTimestampSelected(calendar.timeInMillis)
                        },
                        calendar.get(java.util.Calendar.HOUR_OF_DAY),
                        calendar.get(java.util.Calendar.MINUTE),
                        true
                    ).show()
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(text, color = NeonCyan, fontWeight = FontWeight.Bold)
        }
    }
}

fun buildQrPayload(type: QrType, f1: String, f2: String, f3: String, f4: String, f5: String, dynamicActiveShortCode: String? = null): String {
    return when (type) {
        QrType.URL -> if (f1.isNotBlank()) (if (!f1.startsWith("http")) "https://$f1" else f1) else ""
        QrType.DYNAMIC -> if (dynamicActiveShortCode != null) "https://tl.app/$dynamicActiveShortCode" else ""
        QrType.TEXT -> f1
        QrType.EMAIL -> if (f1.isNotBlank()) "mailto:$f1?subject=${Uri.encode(f2)}&body=${Uri.encode(f3)}" else ""
        QrType.PHONE -> if (f1.isNotBlank()) "tel:$f1" else ""
        QrType.WHATSAPP -> if (f1.isNotBlank()) "https://wa.me/${f1.replace("+", "").replace(" ", "")}" else ""
        QrType.TELEGRAM -> if (f1.isNotBlank()) "https://t.me/${f1.removePrefix("@")}" else ""
        QrType.PAYPAL -> if (f1.isNotBlank()) "https://paypal.me/$f1" else ""
        QrType.SMS -> if (f1.isNotBlank()) "smsto:$f1:${f2}" else ""
        QrType.WIFI -> if (f1.isNotBlank()) {
            val type = if (f2.isBlank()) "nopass" else "WPA"
            "WIFI:S:$f1;T:$type;P:$f2;;"
        } else ""
        QrType.CONTACT -> if (f1.isNotBlank()) "BEGIN:VCARD\nVERSION:3.0\nN:$f1\nFN:$f1\nTEL:$f2\nEMAIL:$f3\nORG:$f4\nEND:VCARD" else ""
        QrType.PAYMENT -> if (f1.isNotBlank()) "upi://pay?pa=$f1&pn=$f2&am=$f3&cu=INR" else ""
        QrType.LOCATION -> if (f1.isNotBlank() && f2.isNotBlank()) "geo:$f1,$f2" else ""
        QrType.EVENT -> if (f1.isNotBlank()) "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nSUMMARY:$f1\nDESCRIPTION:$f5\nDTSTART:$f2\nDTEND:$f3\nLOCATION:$f4\nEND:VEVENT\nEND:VCALENDAR" else ""
        QrType.CRYPTO -> if (f1.isNotBlank()) "bitcoin:$f1${if (f2.isNotBlank()) "?amount=$f2" else ""}" else ""
        QrType.TICKET -> if (f1.isNotBlank()) {
            val timeSlice = System.currentTimeMillis() / 30000
            val raw = "$f1:$timeSlice"
            val hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(8)
            "threatlens://ticket?id=$f1&sig=$hash"
        } else ""
        QrType.FILE, QrType.APP, QrType.SOCIAL, QrType.SPOTIFY, QrType.YOUTUBE -> f1
    }
}

@Composable
private fun SecurityOptionRow(dotColor: Color, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean)->Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = dotColor)
        )
    }
}

fun saveQrToGallery(context: Context, bitmap: Bitmap) {
    val contentValues = ContentValues().apply {
        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "ThreatLens_QR_${System.currentTimeMillis()}.png")
        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        context.contentResolver.openOutputStream(uri).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out!!)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }
        Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
    }
}

fun saveSvgToDownloads(context: Context, payload: String) {
    val hints = java.util.EnumMap<com.google.zxing.EncodeHintType, Any>(com.google.zxing.EncodeHintType::class.java)
    hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = "UTF-8"
    hints[com.google.zxing.EncodeHintType.MARGIN] = 2

    val qrCode = com.google.zxing.qrcode.encoder.Encoder.encode(payload, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H, hints)
    val matrix = qrCode.matrix
    val matrixSize = matrix.width
    val size = 800
    val moduleSize = size.toFloat() / matrixSize

    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $size $size\" width=\"100%\" height=\"100%\">\n")
    sb.append("  <rect width=\"$size\" height=\"$size\" fill=\"#ffffff\" />\n")
    sb.append("  <path d=\"")
    
    for (r in 0 until matrixSize) {
        for (c in 0 until matrixSize) {
            if (matrix.get(c, r).toInt() == 1) {
                val x = c * moduleSize
                val y = r * moduleSize
                sb.append("M$x ${y}h${moduleSize}v${moduleSize}h-${moduleSize}z ")
            }
        }
    }
    sb.append("\" fill=\"#000000\" />\n")
    sb.append("</svg>")

    val svgString = sb.toString()

    val contentValues = ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "ThreatLens_QR_${System.currentTimeMillis()}.svg")
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/svg+xml")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
    }

    val externalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val uri = context.contentResolver.insert(externalUri, contentValues)
    if (uri != null) {
        context.contentResolver.openOutputStream(uri).use { out ->
            out?.write(svgString.toByteArray())
        }
        Toast.makeText(context, "SVG Saved to Downloads", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Failed to save SVG", Toast.LENGTH_SHORT).show()
    }
}

fun shareQrBitmap(context: Context, bitmap: Bitmap) {
    try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = java.io.FileOutputStream("$cachePath/shared_qr.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        val imagePath = java.io.File(context.cacheDir, "images")
        val newFile = java.io.File(imagePath, "shared_qr.png")
        val contentUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", newFile)
        if (contentUri != null) {
            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share QR Code"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun DynamicQrTab(qrVm: QrViewModel, context: Context, eventVm: EventViewModel) {
    val currentUser by qrVm.currentUser.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (currentUser == null) {
            Text("You must create an account to use Event Circulation.", color = Color(0xFFFF3B30), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            var eventName by remember { mutableStateOf("") }
            var guests by remember { mutableStateOf("") }
            
            // Scan Policy
            var scanPolicy by remember { mutableStateOf("Single-Use") }
            var customLimit by remember { mutableStateOf("1") }
            var activeFrom by remember { mutableStateOf<Long?>(null) }
            var activeUntil by remember { mutableStateOf<Long?>(null) }

            Text("CIRCULATE EVENT QRs", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = eventName, onValueChange = { eventName = it }, label = { Text("Event / Business Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = guests, onValueChange = { guests = it }, label = { Text("Guest User IDs (Comma Separated Email)") }, modifier = Modifier.fillMaxWidth().height(80.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(12.dp))
            
            // Dropdown logic for Scan Policy (simplified via Row of options)
            Text("Scan Policy", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { scanPolicy = "Single-Use" }) {
                    androidx.compose.material3.RadioButton(selected = scanPolicy == "Single-Use", onClick = { scanPolicy = "Single-Use" })
                    Text("Single-Use", color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { scanPolicy = "Multi-Use" }) {
                    androidx.compose.material3.RadioButton(selected = scanPolicy == "Multi-Use", onClick = { scanPolicy = "Multi-Use" })
                    Text("Multi-Use", color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { scanPolicy = "Unlimited" }) {
                    androidx.compose.material3.RadioButton(selected = scanPolicy == "Unlimited", onClick = { scanPolicy = "Unlimited" })
                    Text("Unlimited", color = TextPrimary)
                }
            }
            if (scanPolicy == "Multi-Use") {
                OutlinedTextField(value = customLimit, onValueChange = { customLimit = it }, label = { Text("Scan Limit (e.g. 5)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Advanced Options (Optional)", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            DateTimePickerButton("Active From:", activeFrom, { activeFrom = it }, context)
            Spacer(Modifier.height(8.dp))
            DateTimePickerButton("Valid Till:", activeUntil, { activeUntil = it }, context)

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (eventName.isNotBlank() && guests.isNotBlank()) {
                        val maxAllowed = when(scanPolicy) {
                            "Single-Use" -> 1
                            "Unlimited" -> -1
                            else -> customLimit.toIntOrNull() ?: 1
                        }
                        guests.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { guestId ->
                            // Generate event system ticket
                            val ticketId = eventVm.generateTicket(eventId = eventName, maxAllowedScans = maxAllowed, userId = guestId, activeFrom = activeFrom, activeUntil = activeUntil)
                            // Share it using existing logic, embedding ticketId in payload
                            qrVm.shareQrToUser(currentUser!!.userId, guestId, eventName, ticketId)
                        }
                        Toast.makeText(context, "Dispatched Unique QRs to Guests!", Toast.LENGTH_LONG).show()
                        eventName = ""; guests = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("Dispatch Unique QRs", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
@Composable
private fun DashboardTab(qrVm: QrViewModel, historyVm: HistoryViewModel, eventVm: EventViewModel) {
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
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("STATUS / EXPIRES", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        if (qr.activeFrom != null) Text("Active From: ${formatter.format(java.util.Date(qr.activeFrom))}", color = NeonCyan, fontSize = 12.sp)
                                        Text(if (qr.expiresAt != null) formatter.format(java.util.Date(qr.expiresAt)) else "Never Expires", color = if (qr.expiresAt != null) TextPrimary else SafeGreen, fontSize = 12.sp)
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
                    
                    // Generate QR
                    val qrBitmap = CustomQrGenerator.generate(
                        content = "TKT:${selectedTicketEvent!!.qrId}",
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

