package com.safeqr.scanner.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CodeOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.safeqr.scanner.ui.theme.DarkBackground
import com.safeqr.scanner.ui.theme.DarkCard
import com.safeqr.scanner.ui.theme.DarkSurface
import com.safeqr.scanner.ui.theme.GlassBorder
import com.safeqr.scanner.ui.theme.MaliciousRed
import com.safeqr.scanner.ui.theme.NeonCyan
import com.safeqr.scanner.ui.theme.PrimaryBlue
import com.safeqr.scanner.ui.theme.SafeGreen
import com.safeqr.scanner.ui.theme.TextPrimary
import com.safeqr.scanner.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
//  Heuristic Blocklist
// ─────────────────────────────────────────────────────────────────────────────

object SandboxDefaults {
    val blockedDomainKeywords = listOf(
        "malware", "phishing", "adult", "porn", "xxx",
        "casino", "gambling", "tracker", "ad.doubleclick", "ads.", "popup",
        "analytics", "pixel", "telemetry"
    )
}

private val blockedDomainKeywords get() = com.safeqr.scanner.data.remote.CloudDatasetManager.getSandboxBrowserData().blockedDomainKeywords.takeIf { it.isNotEmpty() } ?: SandboxDefaults.blockedDomainKeywords


private val blockedSchemes = listOf(
    "intent", "javascript", "data", "blob"
)

private fun isDomainBlocked(url: String, context: android.content.Context): Boolean {
    val uri = Uri.parse(url)
    val host = uri.host?.lowercase() ?: return false
    
    if (blockedDomainKeywords.any { keyword -> host.contains(keyword) }) {
        return true
    }

    if (com.safeqr.scanner.data.PreferencesManager.isChildLockEnabled(context)) {
        val config = com.safeqr.scanner.data.PreferencesManager.getParentalConfig(context)
        val inWhitelist = config.whitelistDomains.any { host.contains(it.lowercase()) }
        if (inWhitelist) return false
        
        val inBlacklist = config.blacklistDomains.any { host.contains(it.lowercase()) }
        val isSocial = host.contains("instagram.com") || host.contains("facebook.com") || host.contains("tiktok.com") || host.contains("twitter.com") || host.contains("x.com") || host.contains("snapchat.com")
        val isGaming = host.contains("roblox.com") || host.contains("minecraft.net") || host.contains("epicgames.com") || host.contains("steampowered.com")
        
        val cal = java.util.Calendar.getInstance()
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val isBedtime = if (config.bedtimeEnabled) {
            if (config.bedtimeStartHour <= config.bedtimeEndHour) currentHour in config.bedtimeStartHour until config.bedtimeEndHour
            else currentHour >= config.bedtimeStartHour || currentHour < config.bedtimeEndHour
        } else false
        
        if (inBlacklist || isBedtime || (config.blockSocial && isSocial) || (config.blockGaming && isGaming)) {
            return true
        }
    }
    return false
}

private fun isSchemeBlocked(url: String): Boolean {
    val uri = Uri.parse(url)
    val scheme = uri.scheme?.lowercase() ?: return true
    if (scheme in blockedSchemes) return true
    if (scheme != "http" && scheme != "https") return true
    return false
}

// ─────────────────────────────────────────────────────────────────────────────
//  SandboxBrowserScreen
// ─────────────────────────────────────────────────────────────────────────────

class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String
) {
    var url by mutableStateOf(initialUrl)
    var webView: WebView? = null
    var progress by mutableStateOf(0f)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SandboxBrowserScreen(
    url: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val tabs = remember { mutableStateListOf(BrowserTab(initialUrl = url)) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()

    val blockedLog = remember { mutableStateListOf<String>() }
    var lastBlockedDomain by remember { mutableStateOf<String?>(null) }
    var showBlockedBanner by remember { mutableStateOf(false) }
    var isJavaScriptEnabled by remember { mutableStateOf(true) }
    var showConsole by remember { mutableStateOf(false) }

    // Pro Features States
    var isDesktopMode by remember { mutableStateOf(false) }
    var showPageSource by remember { mutableStateOf(false) }
    var threatScore by remember { mutableStateOf(0f) }
    var pageSourceHtml by remember { mutableStateOf("") }
    var isCapturing by remember { mutableStateOf(false) }

    // Clear cookies & storage on start and exit to maintain isolation
    DisposableEffect(Unit) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        WebStorage.getInstance().deleteAllData()

        onDispose {
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    // Auto-dismiss blocked banner after 2 seconds
    LaunchedEffect(showBlockedBanner, lastBlockedDomain) {
        if (showBlockedBanner) {
            delay(2000L)
            showBlockedBanner = false
        }
    }

    // Handle System Back Button
    BackHandler(enabled = true) {
        if (activeTab.webView?.canGoBack() == true) {
            activeTab.webView?.goBack()
        } else if (tabs.size > 1) {
            val idx = tabs.indexOf(activeTab)
            tabs.remove(activeTab)
            if (tabs.isNotEmpty()) {
                activeTabId = tabs[maxOf(0, idx - 1)].id
            } else {
                onBack()
            }
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Exit Sandbox",
                    tint = MaliciousRed
                )
            }
            
            // URL input
            var urlInput by remember(activeTabId, activeTab.url) { mutableStateOf(activeTab.url) }
            androidx.compose.material3.OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard,
                    focusedBorderColor = if (blockedLog.isNotEmpty()) MaliciousRed else NeonCyan,
                    unfocusedBorderColor = if (blockedLog.isNotEmpty()) MaliciousRed.copy(alpha=0.5f) else NeonCyan.copy(alpha=0.5f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextSecondary
                ),
                singleLine = true,
                leadingIcon = {
                    val isHttps = urlInput.startsWith("https://")
                    Icon(
                        imageVector = if (isHttps) Icons.Filled.Shield else Icons.Filled.Warning,
                        contentDescription = "Security Status",
                        tint = if (isHttps) SafeGreen else androidx.compose.ui.graphics.Color(0xFFFFA000)
                    )
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Go
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onGo = {
                        var finalUrl = urlInput.trim()
                        if (finalUrl.isNotBlank()) {
                            if (!android.util.Patterns.WEB_URL.matcher(finalUrl).matches() && !finalUrl.startsWith("http")) {
                                finalUrl = "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(finalUrl, "UTF-8")}"
                            } else if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                                finalUrl = "https://$finalUrl"
                            }
                            activeTab.url = finalUrl
                            activeTab.webView?.loadUrl(finalUrl)
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))
        }

        // Threat Meter
        if (threatScore > 0f) {
            val meterColor = when {
                threatScore < 0.3f -> SafeGreen
                threatScore < 0.7f -> androidx.compose.ui.graphics.Color(0xFFFFA000) // Amber
                else -> MaliciousRed
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = threatScore.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = meterColor,
                trackColor = DarkCard,
            )
        }

        // Progress Bar
        if (activeTab.progress > 0f && activeTab.progress < 1f) {
            androidx.compose.material3.LinearProgressIndicator(
                activeTab.progress,
                Modifier.fillMaxWidth().height(2.dp),
                NeonCyan,
                Color.Transparent
            )
        }

        // Tabs Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs) { tab ->
                val isActive = tab.id == activeTabId
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) PrimaryBlue else DarkCard)
                        .clickable { activeTabId = tab.id }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tab ${tabs.indexOf(tab) + 1}",
                        color = if (isActive) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (tabs.size > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Tab",
                            tint = if (isActive) Color.White else TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    val idx = tabs.indexOf(tab)
                                    tabs.remove(tab)
                                    if (isActive && tabs.isNotEmpty()) {
                                        activeTabId = tabs[maxOf(0, idx - 1)].id
                                    } else if (tabs.isEmpty()) {
                                        onBack()
                                    }
                                }
                        )
                    }
                }
            }
            item {
                IconButton(
                    onClick = {
                        val newTab = BrowserTab(initialUrl = "https://duckduckgo.com")
                        tabs.add(newTab)
                        activeTabId = newTab.id
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New Tab", tint = TextPrimary)
                }
            }
        }

        // ── Security Controls Bar ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // JS Toggle
                IconButton(onClick = { isJavaScriptEnabled = !isJavaScriptEnabled }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isJavaScriptEnabled) Icons.Filled.Code else Icons.Filled.CodeOff,
                        contentDescription = "JS Toggle",
                        tint = if (isJavaScriptEnabled) SafeGreen else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Desktop Toggle
                IconButton(onClick = { 
                    isDesktopMode = !isDesktopMode 
                    activeTab.webView?.settings?.userAgentString = if (isDesktopMode) {
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    } else {
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    activeTab.webView?.reload()
                }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Settings,
                        contentDescription = "Desktop Mode",
                        tint = if (isDesktopMode) NeonCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // View Source Toggle
                IconButton(onClick = {
                    showPageSource = !showPageSource
                    if (showPageSource) {
                        activeTab.webView?.evaluateJavascript(
                            "(function() { return document.documentElement.outerHTML; })();"
                        ) { html ->
                            pageSourceHtml = try {
                                org.json.JSONTokener(html).nextValue() as String
                            } catch (e: Exception) {
                                html ?: "Unable to read source."
                            }
                        }
                    }
                }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Search,
                        contentDescription = "View Source",
                        tint = if (showPageSource) NeonCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Capture Threat Toggle
                IconButton(onClick = {
                    isCapturing = true
                    val webView = activeTab.webView
                    if (webView != null) {
                        try {
                            val bitmap = android.graphics.Bitmap.createBitmap(webView.width, webView.height, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            webView.draw(canvas)
                            // Save bitmap logic
                            val resolver = context.contentResolver
                            val contentValues = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "ThreatCapture_${System.currentTimeMillis()}.png")
                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ThreatLens")
                            }
                            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            if (uri != null) {
                                resolver.openOutputStream(uri)?.use { out ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                }
                                Toast.makeText(context, "Threat Captured to Gallery", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to capture", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isCapturing = false
                }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Add,
                        contentDescription = "Capture",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showBlockedBanner, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = "Blocked Tracker!",
                    color = MaliciousRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Content Area with WebViews
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            tabs.forEach { tab ->
                val isVisible = tab.id == activeTabId
                key(tab.id) {
                    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                    DisposableEffect(tab.id, lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                tab.webView?.onResume()
                            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                                tab.webView?.onPause()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            tab.webView?.destroy()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isVisible) 1f else 0f)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    tab.webView = this
                                    settings.apply {
                                        javaScriptEnabled = isJavaScriptEnabled
                                        javaScriptCanOpenWindowsAutomatically = false
                                        domStorageEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            var requestUrl = request?.url?.toString() ?: return true
                                            
                                            if (isSchemeBlocked(requestUrl) || isDomainBlocked(requestUrl, ctx)) {
                                                val domain = Uri.parse(requestUrl).host ?: requestUrl
                                                blockedLog.add("Redirect Blocked: $domain")
                                                lastBlockedDomain = domain
                                                showBlockedBanner = true
                                                threatScore += 0.15f
                                                com.safeqr.scanner.data.PreferencesManager.addParentalLog(ctx, requestUrl, "Blocked", "Sandbox Intervention")
                                                return true
                                            }
                                            
                                            // HTTPS Enforcement (After block check)
                                            if (requestUrl.startsWith("http://")) {
                                                requestUrl = requestUrl.replaceFirst("http://", "https://")
                                                Toast.makeText(ctx, "Upgraded to HTTPS", Toast.LENGTH_SHORT).show()
                                                view?.loadUrl(requestUrl)
                                                return true
                                            }
                                            tab.url = requestUrl
                                            return false
                                        }
                                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                            val requestUrl = request?.url?.toString() ?: return null
                                            if (isDomainBlocked(requestUrl, ctx)) {
                                                val domain = Uri.parse(requestUrl).host ?: requestUrl
                                                view?.post { 
                                                    blockedLog.add("Resource Blocked: $domain") 
                                                    threatScore += 0.05f
                                                }
                                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                            }
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                            super.onPageFinished(view, finishedUrl)
                                            if (!finishedUrl.isNullOrBlank()) {
                                                tab.url = finishedUrl
                                            }
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                            super.onShowCustomView(view, callback)
                                        }
                                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                            return false
                                        }
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            super.onProgressChanged(view, newProgress)
                                            tab.progress = newProgress / 100f
                                        }
                                    }
                                    setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                        try {
                                            if (url.startsWith("data:") || url.startsWith("blob:")) {
                                                Toast.makeText(ctx, "Direct file extraction from Data/Blob is restricted in Sandbox.", Toast.LENGTH_LONG).show()
                                                return@DownloadListener
                                            }
                                            if (isDomainBlocked(url, ctx)) {
                                                Toast.makeText(ctx, "Download Blocked by ThreatLens", Toast.LENGTH_SHORT).show()
                                                return@DownloadListener
                                            }
                                            val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                                                setMimeType(mimetype)
                                                addRequestHeader("User-Agent", userAgent)
                                                setDescription("Downloading file...")
                                                setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                                                allowScanningByMediaScanner()
                                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                                            }
                                            val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                            dm.enqueue(request)
                                            Toast.makeText(ctx, "Downloading...", Toast.LENGTH_SHORT).show()
                                        } catch(e: Exception) {
                                            Toast.makeText(ctx, "Download failed", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                                    loadUrl(tab.url)
                                }
                            },
                            update = { webView ->
                                webView.settings.javaScriptEnabled = isJavaScriptEnabled
                                webView.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                            }
                        )
                        
                        // Page Source Overlay
                        if (showPageSource && isVisible) {
                            Box(modifier = Modifier.fillMaxSize().zIndex(2f).background(DarkCard).padding(16.dp)) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item {
                                        Text(
                                            text = pageSourceHtml,
                                            color = TextSecondary,
                                            style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Shield Flash Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = showBlockedBanner,
                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150)),
                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(800)),
                modifier = Modifier.fillMaxSize().zIndex(10f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 6.dp,
                            color = MaliciousRed.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(MaliciousRed.copy(alpha = 0.1f))
                )
            }

            // Security Console Overlay
            if (showConsole) {
                var consoleTab by remember { mutableStateOf("Logs") }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground.copy(alpha = 0.95f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Live Security",
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Row {
                                IconButton(onClick = { 
                                    if (consoleTab == "Logs") blockedLog.clear()
                                    else CookieManager.getInstance().removeAllCookies(null)
                                }) {
                                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear", tint = TextSecondary)
                                }
                                IconButton(onClick = { showConsole = false }) {
                                    Icon(Icons.Filled.Warning, contentDescription = "Close", tint = TextSecondary)
                                }
                            }
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if(consoleTab=="Logs") PrimaryBlue else DarkCard).clickable{consoleTab="Logs"}.padding(8.dp)) { Text("Blocked Logs", color=TextPrimary, fontSize=12.sp, fontWeight=FontWeight.Bold) }
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if(consoleTab=="Cookies") PrimaryBlue else DarkCard).clickable{consoleTab="Cookies"}.padding(8.dp)) { Text("Live Cookies", color=TextPrimary, fontSize=12.sp, fontWeight=FontWeight.Bold) }
                        }

                        if (consoleTab == "Logs") {
                            if (blockedLog.isEmpty()) {
                                Text("No items have been blocked yet.", color = TextSecondary, modifier = Modifier.padding(16.dp))
                            } else {
                                LazyColumn {
                                    items(blockedLog) { logItem ->
                                        Text(
                                            text = logItem,
                                            color = MaliciousRed,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Cookies tab
                            var cookiesStr by remember { mutableStateOf("") }
                            LaunchedEffect(activeTab.url) {
                                while(true) {
                                    cookiesStr = CookieManager.getInstance().getCookie(activeTab.url) ?: ""
                                    delay(1000)
                                }
                            }
                            if (cookiesStr.isEmpty()) {
                                Text("No cookies set by this site.", color = TextSecondary, modifier = Modifier.padding(16.dp))
                            } else {
                                LazyColumn {
                                    items(cookiesStr.split(";")) { cookie ->
                                        if (cookie.isNotBlank()) {
                                            Text(
                                                text = cookie.trim(),
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom Info Bar ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .clickable { showConsole = !showConsole }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (blockedLog.isNotEmpty()) MaliciousRed else NeonCyan)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (showConsole) "Close Security Log" else "Threats Handled: ${blockedLog.size} | View Log",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}
