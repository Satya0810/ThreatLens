package com.safeqr.scanner.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.safeqr.scanner.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.safeqr.scanner.ui.components.AnalyzingOverlay
import com.safeqr.scanner.ui.components.ScanOverlay
import com.safeqr.scanner.ui.theme.DarkBackground
import com.safeqr.scanner.ui.theme.GlassBorder
import com.safeqr.scanner.ui.theme.GlassWhite
import com.safeqr.scanner.ui.theme.TextPrimary
import com.safeqr.scanner.ui.theme.NeonCyan
import com.safeqr.scanner.ui.theme.NeonCyanGlow
import com.safeqr.scanner.ui.theme.TextSecondary
import com.safeqr.scanner.ui.theme.SafeGreen
import com.safeqr.scanner.ui.theme.MaliciousRed
import com.safeqr.scanner.ui.theme.CautionAmber

import com.safeqr.scanner.viewmodel.ScannerViewModel
import com.safeqr.scanner.viewmodel.EventViewModel
import com.safeqr.scanner.viewmodel.QrViewModel
import com.safeqr.scanner.viewmodel.EventScanResult

/**
 * Active scan mode selected in the bottom action bar.
 */
private enum class ScanMode { CAMERA, UPLOAD, PASTE }

@OptIn(ExperimentalPermissionsApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = viewModel(),
    eventViewModel: EventViewModel = viewModel(),
    qrViewModel: QrViewModel = viewModel(),
    externalUrl: String? = null,
    onNavigateToSandbox: (String) -> Unit = {},
    onNavigateToHistory: (String?) -> Unit = {}
) {
    val eventScanResult by eventViewModel.scanResult.collectAsState()
    var isEntryMode by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current



    val scanResult by viewModel.scanResult.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val showResult by viewModel.showResult.collectAsState()
    val scanningEnabled by viewModel.scanningEnabled.collectAsState()
    val analyzingUrl by viewModel.analyzingUrl.collectAsState()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Helper: opens a URL externally (delegates to OS to find the right app, e.g. LinkedIn, Dialer, Browser)
    val openUrlExternally: (String) -> Unit = remember {
        { url: String ->
            try {
                // Ensure proper intent scheme parsing
                val parsedUrl = if (!url.contains("://") && !url.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))) {
                    "http://$url"
                } else {
                    url
                }
                
                // If the URL is an intent:// or app-specific scheme (not http/https), handle it normally
                if (!parsedUrl.startsWith("http://") && !parsedUrl.startsWith("https://")) {
                    val intent = try {
                        Intent.parseUri(parsedUrl, Intent.URI_INTENT_SCHEME)
                    } catch (e: Exception) {
                        Intent(Intent.ACTION_VIEW, Uri.parse(parsedUrl))
                    }
                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    val targetPackage = intent.`package`
                    if (targetPackage != null && targetPackage != context.packageName) {
                        try {
                            context.startActivity(intent)
                        } catch (e: android.content.ActivityNotFoundException) {
                            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                            if (fallbackUrl != null) {
                                com.safeqr.scanner.utils.SmartRouter.openUrlSmartly(context, fallbackUrl)
                            } else {
                                android.widget.Toast.makeText(context, "App not installed to handle this link", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        val chooser = Intent.createChooser(intent, "Open with...")
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            chooser.putExtra(
                                Intent.EXTRA_EXCLUDE_COMPONENTS, 
                                arrayOf(ComponentName(context, com.safeqr.scanner.MainActivity::class.java))
                            )
                        }
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }
                } else {
                    // For standard http/https links, use the SmartRouter to find the correct native app or fallback
                    com.safeqr.scanner.utils.SmartRouter.openUrlSmartly(context, parsedUrl)
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error opening link", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -- Event Scan Result Dialog --
    if (eventScanResult != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { eventViewModel.resetScanResult() },
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text(
                    text = if (eventScanResult is EventScanResult.Success || eventScanResult is EventScanResult.CloudSuccess) "Valid Pass" else "Scan Failed",
                    color = if (eventScanResult is EventScanResult.Success || eventScanResult is EventScanResult.CloudSuccess) SafeGreen else MaliciousRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = when (val res = eventScanResult!!) {
                            is EventScanResult.Success -> res.message + "\nStatus: ${res.ticket.currentStatus}\nScans: ${res.ticket.currentScanCount}"
                            is EventScanResult.CloudSuccess -> res.message + "\nAttendee: ${res.ticket.attendeeName}\nTier: ${res.ticket.ticketTier}\nStatus: Verified Check-in"
                            is EventScanResult.Error -> res.message
                        },
                        color = TextPrimary
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { eventViewModel.resetScanResult() }) {
                    Text("OK", color = NeonCyan)
                }
            }
        )
    }

    // -- Normal URL scan result handling is below --

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Track which bottom-bar action is "active" (purely visual highlight)
    var activeMode by remember { mutableStateOf(ScanMode.CAMERA) }

    // Paste-link dialog state
        var showPasteDialog by remember { mutableStateOf(false) }

    // -- Auto-analyze external URL from link intercept / share ------------
    LaunchedEffect(externalUrl) {
        if (!externalUrl.isNullOrBlank()) {
            viewModel.analyzeUrl(externalUrl)
        }
    }

    // -- Image picker -----------------------------------------------------
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                @Suppress("DEPRECATION")
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val rawValue = barcodes.firstOrNull()?.rawValue
                        if (rawValue != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.analyzeImage(rawValue)
                        } else {
                            Toast.makeText(
                                context,
                                "No QR code found in the selected image",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            context,
                            "Failed to process image",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -- Animations for the floating action bar --
    val infiniteTransition = rememberInfiniteTransition(label = "actionBar")

    // -- Main Layout ------------------------------------------------------
    if (cameraPermissionState.status.isGranted) {
        // Full camera scanner experience
        val previewView = remember { PreviewView(context) }
        var cameraInstance by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
        var zoomRatio by remember { mutableFloatStateOf(1f) }
        var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
        var isTorchOn by remember { mutableStateOf(false) }

        val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
        // -- Animated Dynamic Background --
        val infiniteTransitionBg = rememberInfiniteTransition(label = "bg_anim")
        val color1 by infiniteTransitionBg.animateColor(
            initialValue = colorScheme.background,
            targetValue = colorScheme.surface,
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
            label = "color1"
        )
        val color2 by infiniteTransitionBg.animateColor(
            initialValue = colorScheme.surface,
            targetValue = colorScheme.background,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
            label = "color2"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(color1, color2), radius = 1500f))
        ) {
            if (isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)) // Darken background when analyzing
                ) {
                    AnalyzingOverlay(
                        isVisible = true,
                        analyzedUrl = analyzingUrl
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Removed "Open Directly" button as requested
                        OutlinedButton(
                            onClick = { viewModel.resetScanner() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            )
                        ) {
                            Text("Stop Scan", color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // -- Modern Instagram-like Dashboard Layout --
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Top Header — Clean floating text and icon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Shield with radial glow
                            Box(contentAlignment = Alignment.Center) {
                                // Custom ThreatLens Logo
                                Image(
                                    painter = painterResource(id = R.drawable.ic_threatlens_logo),
                                    contentDescription = "ThreatLens Logo",
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(verticalArrangement = Arrangement.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Threat", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                    Text("Lens", color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Scan & Protect", color = TextSecondary, fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(androidx.compose.material3.MaterialTheme.colorScheme.primary))
                                }
                            }
                        }
                    }

                    // 1.5 Stats Overview — Floating clickable stats
                    val stats by viewModel.scanStats.collectAsState()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Total Scans
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { onNavigateToHistory(null) }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Column {
                                    Text("${stats.total}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Total", color = TextSecondary, fontSize = 9.sp)
                                }
                            }

                            // Safe
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { onNavigateToHistory("Safe") }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Column {
                                    Text("${stats.safe}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Safe", color = TextSecondary, fontSize = 9.sp)
                                }
                            }

                            // Caution
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { onNavigateToHistory("Caution") }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = CautionAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Column {
                                    Text("${stats.caution}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Caution", color = TextSecondary, fontSize = 9.sp)
                                }
                            }

                            // Threats
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { onNavigateToHistory("Malicious") }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = MaliciousRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Column {
                                    Text("${stats.threats}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Threats", color = TextSecondary, fontSize = 9.sp)
                                }
                            }
                        }
                    
                    // Controls moved inside the Camera Preview Box

                    // 3. Camera Preview Box (No extra styling)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                    ) {
                        // Android View (Camera)
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        ScanOverlay(isAnalyzing = false)
                        
                        // Advanced Target Lock Capture Animation
                        var triggerCaptureAnim by remember { mutableStateOf(false) }
                        LaunchedEffect(isAnalyzing) {
                            if (isAnalyzing) {
                                triggerCaptureAnim = true
                                kotlinx.coroutines.delay(600)
                                triggerCaptureAnim = false
                            }
                        }
                        
                        val captureScale by animateFloatAsState(
                            targetValue = if (triggerCaptureAnim) 0.8f else 2.5f,
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                            label = "captureScale"
                        )
                        val captureAlpha by animateFloatAsState(
                            targetValue = if (triggerCaptureAnim) 1f else 0f,
                            animationSpec = tween(durationMillis = 200),
                            label = "captureAlpha"
                        )

                        if (captureAlpha > 0f) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                // Subtle shutter flash background
                                Box(modifier = Modifier.fillMaxSize().background(NeonCyan.copy(alpha = captureAlpha * 0.15f)))
                                
                                // Target Lock Crosshair
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.QrCodeScanner,
                                    contentDescription = "Target Locked",
                                    tint = NeonCyan.copy(alpha = captureAlpha),
                                    modifier = Modifier
                                        .size(250.dp)
                                        .scale(captureScale)
                                )
                            }
                        }

                        // ── Top HUD (Torch, Flip Camera) ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 24.dp, vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Torch Button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (cameraInstance?.cameraInfo?.hasFlashUnit() == true) {
                                            isTorchOn = !isTorchOn
                                            cameraInstance?.cameraControl?.enableTorch(isTorchOn)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                    contentDescription = "Torch",
                                    tint = if (isTorchOn) NeonCyan else Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(64.dp))

                            // Rotate Button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                            CameraSelector.LENS_FACING_FRONT
                                        } else {
                                            CameraSelector.LENS_FACING_BACK
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlipCameraAndroid,
                                    contentDescription = "Switch Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // ── Bottom HUD (Horizontal Zoom Slider) ──
                        if (cameraInstance != null) {
                            val zoomState = cameraInstance?.cameraInfo?.zoomState?.value
                            val minZoom = zoomState?.minZoomRatio ?: 1f
                            val maxZoom = (zoomState?.maxZoomRatio ?: 4f).coerceAtMost(6f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 32.dp, vertical = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("-", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(16.dp))
                                Slider(
                                    value = zoomRatio,
                                    onValueChange = { newZoom ->
                                        zoomRatio = newZoom
                                        cameraInstance?.cameraControl?.setZoomRatio(newZoom)
                                    },
                                    valueRange = minZoom..maxZoom,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                        activeTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.5f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("+", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Camera Setup Effect (handles lifecycle binds)
                    LaunchedEffect(lensFacing) {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val mlKitScanner = BarcodeScanning.getClient()
                        var lastScanTime = 0L

                        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                            if (!scanningEnabled) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                mlKitScanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull()?.rawValue?.let { rawValue ->
                                            val now = System.currentTimeMillis()
                                            if (now - lastScanTime > 2500) {
                                                lastScanTime = now
                                                if (rawValue.startsWith("threatlens://ticket", ignoreCase = true)) {
                                                    val uri = android.net.Uri.parse(rawValue)
                                                    val ticketId = uri.getQueryParameter("id") ?: ""
                                                    val signature = uri.getQueryParameter("sig") ?: ""
                                                    val gatekeeperId = qrViewModel.currentUser.value?.userId ?: ""
                                                    if (gatekeeperId.isNotEmpty()) {
                                                        eventViewModel.processCloudScan(ticketId, gatekeeperId, signature, isEntryMode)
                                                    } else {
                                                        Toast.makeText(context, "Must be logged in to scan tickets.", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    viewModel.onQrCodeDetected(rawValue) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            cameraInstance = cam
                            
                            // Re-apply torch state if it was on
                            if (cam.cameraInfo.hasFlashUnit()) {
                                cam.cameraControl.enableTorch(isTorchOn)
                            } else {
                                isTorchOn = false
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }



                    // 5. Action Buttons Row (Scan QR, Upload, Paste Link)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionButton(
                            icon = Icons.Default.QrCodeScanner,
                            label = "Scan QR",
                            isSelected = activeMode == ScanMode.CAMERA,
                            onClick = { activeMode = ScanMode.CAMERA }
                        )
                        ActionButton(
                            icon = Icons.Outlined.Image,
                            label = "Upload",
                            isSelected = activeMode == ScanMode.UPLOAD,
                            onClick = {
                                activeMode = ScanMode.UPLOAD
                                imagePickerLauncher.launch("image/*")
                            }
                        )
                        ActionButton(
                            icon = Icons.Outlined.Link,
                            label = "Paste Link",
                            isSelected = activeMode == ScanMode.PASTE,
                            onClick = {
                                activeMode = ScanMode.PASTE; showPasteDialog = true
                            }
                        )
                    }
                }
            }
        // Result bottom sheet (outside of Column so it can overlay)
        if (showResult && scanResult != null) {
            ResultBottomSheet(
                scanResult = scanResult!!,
                onDismiss = { viewModel.dismissResult() },
                onOpenUrl = { url -> openUrlExternally(url) },
                onOpenInSandbox = { url ->
                    viewModel.dismissResult()
                    onNavigateToSandbox(url)
                },
                onReport = { url, issue -> viewModel.reportWebsite(url, issue) },
                autoConnectWifi = true
            )
        }
        }

    } else {
        // -- Permission denied UI -----------------------------------------
        val floatAnim by infiniteTransition.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "float"
        )

        val shieldPulse by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shieldPulse"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Custom ThreatLens logo with pulse
                Image(
                    painter = painterResource(id = R.drawable.ic_threatlens_logo),
                    contentDescription = "ThreatLens Logo",
                    modifier = Modifier
                        .size(64.dp)
                        .scale(shieldPulse)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Permission request card
                Card(
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Floating camera icon
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .offset { IntOffset(0, floatAnim.toInt()) },
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "We need camera access to scan QR codes and analyze them for potential threats.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        // Grant Permission button with glowing border
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Grant Permission",
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = DarkBackground,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Still-accessible actions when camera is not granted
                Text(
                    text = "Or use these options without camera:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(
                        icon = Icons.Outlined.Image,
                        label = "Upload Image",
                        isSelected = false,
                        onClick = { imagePickerLauncher.launch("image/*") }
                    )
                    ActionButton(
                        icon = Icons.Outlined.Link,
                        label = "Paste Link",
                        isSelected = false,
                        onClick = { showPasteDialog = true }
                    )
                }
            }

            // Result bottom sheet (also needed here for upload / paste results)
            if (showResult && scanResult != null) {
                ResultBottomSheet(
                    scanResult = scanResult!!,
                    onDismiss = { viewModel.dismissResult() },
                    onOpenUrl = { url -> openUrlExternally(url) },
                    onOpenInSandbox = { url ->
                        viewModel.dismissResult()
                        onNavigateToSandbox(url)
                    },
                    onReport = { url, issue -> viewModel.reportWebsite(url, issue) },
                    autoConnectWifi = true
                )
            }
            }

            // Analyzing overlay (also needed here for upload / paste)
            AnalyzingOverlay(
                isVisible = isAnalyzing,
                analyzedUrl = analyzingUrl
            )
        }

    // -- Paste Link Dialog ------------------------------------------------
    if (showPasteDialog) {
        PasteLinkDialog(
            onDismiss = {
                showPasteDialog = false
                activeMode = ScanMode.CAMERA
            },
            onAnalyze = { url ->
                showPasteDialog = false
                activeMode = ScanMode.CAMERA
                viewModel.analyzeUrl(url)
            }
        )
    }

}


// ---------------------------------------------------------------------------
//  Single action button (icon + label column) with animations
// ---------------------------------------------------------------------------

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (isSelected) NeonCyan else TextSecondary,
        animationSpec = tween(300),
        label = "actionTint"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "actionScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "actionPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .scale(scale)
    ) {
        val bgModifier = if (isSelected) {
            Modifier.background(NeonCyan.copy(alpha = pulseAlpha), CircleShape).padding(8.dp)
        } else {
            Modifier.padding(8.dp)
        }
        Box(contentAlignment = Alignment.Center, modifier = bgModifier) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontSize = 10.sp
        )
        // Animated underline dot
        if (isSelected) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(NeonCyan)
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ---------------------------------------------------------------------------
//  Paste Link Dialog
// ---------------------------------------------------------------------------

@Composable
private fun PasteLinkDialog(
    onDismiss: () -> Unit,
    onAnalyze: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Analyze URL",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Paste or type a URL to check for threats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = {
                        Text("https://example.com", color = TextSecondary.copy(alpha = 0.5f))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = NeonCyan,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = GlassBorder
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = urlText.trim()
                    if (trimmed.isNotEmpty()) {
                        onAnalyze(trimmed)
                    }
                },
                enabled = urlText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = CircleShape
            ) {
                Text("Analyze", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun StatsBottomSheet(
    onDismiss: () -> Unit,
    stats: com.safeqr.scanner.viewmodel.ScanStats
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = com.safeqr.scanner.ui.theme.DarkBackground,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f),
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 1. Hero Section: Pulsating Shield ──
            val infiniteTransition = rememberInfiniteTransition()
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            val shieldColor = if (stats.deviceSafetyScore > 80) com.safeqr.scanner.ui.theme.SafeGreen else com.safeqr.scanner.ui.theme.CautionAmber

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp).padding(top = 16.dp)) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .background(shieldColor.copy(alpha = 0.2f), CircleShape)
                )
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Shield",
                    tint = shieldColor,
                    modifier = Modifier.size(64.dp)
                )
            }
            Text(
                text = "Device Protected",
                style = MaterialTheme.typography.titleLarge,
                color = com.safeqr.scanner.ui.theme.TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Engine Last Updated: Just Now",
                style = MaterialTheme.typography.labelSmall,
                color = com.safeqr.scanner.ui.theme.TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── 2. AI Security Advisor Summary ──
            val aiSummary = if (stats.threats == 0) {
                "Your device is highly secure. No active threats were detected this week."
            } else {
                "ThreatLens AI mitigated ${stats.threats} threats this week, primarily originating from unsafe links."
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.SmartToy, contentDescription = "AI", tint = androidx.compose.material3.MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = aiSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = com.safeqr.scanner.ui.theme.TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 3. Weekly Threat Trend (Bar Chart) ──
            Text(
                text = "7-Day Activity Trend",
                style = MaterialTheme.typography.titleMedium,
                color = com.safeqr.scanner.ui.theme.TextPrimary,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                val maxVal = (stats.weeklyTrend.maxOrNull() ?: 1).coerceAtLeast(1)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    stats.weeklyTrend.forEach { value ->
                        val heightFraction = value.toFloat() / maxVal.toFloat()
                        val barColor = if (value > 0) com.safeqr.scanner.ui.theme.NeonCyan else com.safeqr.scanner.ui.theme.TextSecondary.copy(alpha=0.3f)
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight(heightFraction.coerceAtLeast(0.05f))
                                .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 4. Threat Categorization Breakdown ──
            Text(
                text = "Threat Breakdown",
                style = MaterialTheme.typography.titleMedium,
                color = com.safeqr.scanner.ui.theme.TextPrimary,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                val totalCategorized = (stats.phishingCount + stats.trackerCount + stats.adultCount).coerceAtLeast(1)
                ThreatBar("Phishing", stats.phishingCount, totalCategorized, com.safeqr.scanner.ui.theme.MaliciousRed)
                Spacer(modifier = Modifier.height(12.dp))
                ThreatBar("Trackers / Adware", stats.trackerCount, totalCategorized, com.safeqr.scanner.ui.theme.CautionAmber)
                Spacer(modifier = Modifier.height(12.dp))
                ThreatBar("Adult Content", stats.adultCount, totalCategorized, androidx.compose.material3.MaterialTheme.colorScheme.secondary)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 5. Global Threat Radar / Recent Threats ──
            Text(
                text = "Recent Intercepts",
                style = MaterialTheme.typography.titleMedium,
                color = com.safeqr.scanner.ui.theme.TextPrimary,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
            )
            if (stats.recentThreats.isEmpty()) {
                Text("No recent threats intercepted.", color = com.safeqr.scanner.ui.theme.TextSecondary, modifier = Modifier.padding(16.dp))
            } else {
                stats.recentThreats.forEach { threat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = com.safeqr.scanner.ui.theme.MaliciousRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = threat, color = com.safeqr.scanner.ui.theme.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 6. Zero-Day ML vs Database Metrics ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "${stats.dbCaught}", style = MaterialTheme.typography.headlineMedium, color = com.safeqr.scanner.ui.theme.NeonCyan, fontWeight = FontWeight.Bold)
                    Text(text = "Known Threats", color = com.safeqr.scanner.ui.theme.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(1.dp).height(40.dp).background(com.safeqr.scanner.ui.theme.GlassBorder))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "${stats.aiCaught}", style = MaterialTheme.typography.headlineMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    Text(text = "Zero-Day (AI)", color = com.safeqr.scanner.ui.theme.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ThreatBar(label: String, count: Int, total: Int, color: androidx.compose.ui.graphics.Color) {
    val progress = (count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = com.safeqr.scanner.ui.theme.TextPrimary, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(text = "$count", color = com.safeqr.scanner.ui.theme.TextSecondary, modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelSmall)
        androidx.compose.material3.LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = com.safeqr.scanner.ui.theme.DarkBackground
        )
    }
}
