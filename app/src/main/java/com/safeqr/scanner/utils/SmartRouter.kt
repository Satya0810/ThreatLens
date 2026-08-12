package com.safeqr.scanner.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

object SmartRouter {

    /**
     * Intelligently routes the URL to the best available application.
     * Prevents infinite loops if ThreatLens is set as the default browser.
     * It queries the system for all apps that can handle the URL, and if it finds
     * a specific native app (e.g., Instagram, Twitter, YouTube), it forces the intent
     * to open in that app. Otherwise, it falls back to Chrome or another browser,
     * specifically excluding ThreatLens itself.
     */
    fun openUrlSmartly(context: Context, url: String) {
        val uri = Uri.parse(url)
        val mimeType = getMimeType(url)

        val intent = Intent(Intent.ACTION_VIEW)
        
        // If a file MIME type is detected, try to open with a system chooser first
        if (mimeType != null) {
            intent.setDataAndType(uri, mimeType)
            val chooser = Intent.createChooser(intent, "Open file with...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
                return
            } catch (e: Exception) {
                Log.e("SmartRouter", "Failed to open file with chooser, falling back to browser.", e)
                // Reset intent to default URL handling if no file viewer is found
                intent.setData(uri)
                intent.type = null
            }
        } else {
            intent.data = uri
        }

        val packageManager = context.packageManager
        val myPackageName = context.packageName
        
        // Find all activities that can handle this URL
        val resolveInfoList = packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_ALL
        )

        var targetPackage: String? = null

        // Priority 1: Check if a known native app exists for this URL's domain.
        // Only route to native apps when the domain clearly maps to a specific app.
        val domain = uri.host?.lowercase()?.removePrefix("www.") ?: ""
        val nativeAppPackage = findNativeAppForDomain(domain)
        if (nativeAppPackage != null) {
            // Verify the native app is actually installed and can handle this intent
            val isInstalled = resolveInfoList.any { it.activityInfo.packageName == nativeAppPackage }
            if (isInstalled) {
                targetPackage = nativeAppPackage
                Log.d("SmartRouter", "Routing to verified native app: $targetPackage for domain: $domain")
            }
        }

        // Priority 2: Find a known browser (prefer Chrome, then others)
        if (targetPackage == null) {
            // First try Chrome specifically
            val chromeAvailable = resolveInfoList.any { it.activityInfo.packageName == "com.android.chrome" }
            if (chromeAvailable) {
                targetPackage = "com.android.chrome"
            } else {
                // Try other known browsers
                for (resolveInfo in resolveInfoList) {
                    val pkgName = resolveInfo.activityInfo.packageName
                    if (pkgName != myPackageName && isKnownBrowser(pkgName)) {
                        targetPackage = pkgName
                        break
                    }
                }
            }
        }

        // Priority 3: Fallback to ANY app that isn't ThreatLens
        if (targetPackage == null) {
            for (resolveInfo in resolveInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName != myPackageName) {
                    targetPackage = pkgName
                    break
                }
            }
        }

        if (targetPackage != null) {
            Log.d("SmartRouter", "Routing URL to: $targetPackage")
            intent.setPackage(targetPackage)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("SmartRouter", "Failed to route to $targetPackage", e)
                fallbackToChooser(context, intent)
            }
        } else {
            Log.w("SmartRouter", "No suitable external app found, using chooser fallback.")
            fallbackToChooser(context, intent)
        }
    }

    private fun fallbackToChooser(context: Context, intent: Intent) {
        intent.setPackage(null)
        val chooser = Intent.createChooser(intent, "Open with...")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            chooser.putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(android.content.ComponentName(context, "com.safeqr.scanner.MainActivity"))
            )
        }
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("SmartRouter", "Fallback chooser failed", e)
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e2: Exception) {}
        }
    }

    /**
     * Maps well-known domains to their native Android app package names.
     * Only domains with a clear 1:1 relationship to a specific native app should be listed.
     * This prevents generic URLs from being incorrectly routed to random non-browser apps.
     */
    private fun findNativeAppForDomain(domain: String): String? {
        val domainToApp = mapOf(
            // Social Media
            "instagram.com" to "com.instagram.android",
            "twitter.com" to "com.twitter.android",
            "x.com" to "com.twitter.android",
            "facebook.com" to "com.facebook.katana",
            "fb.com" to "com.facebook.katana",
            "linkedin.com" to "com.linkedin.android",
            "tiktok.com" to "com.zhiliaoapp.musically",
            "snapchat.com" to "com.snapchat.android",
            "reddit.com" to "com.reddit.frontpage",
            "pinterest.com" to "com.pinterest",
            "tumblr.com" to "com.tumblr",
            "threads.net" to "com.instagram.barcelona",
            // Messaging
            "wa.me" to "com.whatsapp",
            "whatsapp.com" to "com.whatsapp",
            "t.me" to "org.telegram.messenger",
            "telegram.me" to "org.telegram.messenger",
            "discord.com" to "com.discord",
            "discord.gg" to "com.discord",
            // Video / Streaming
            "youtube.com" to "com.google.android.youtube",
            "youtu.be" to "com.google.android.youtube",
            "twitch.tv" to "tv.twitch.android.app",
            "spotify.com" to "com.spotify.music",
            "open.spotify.com" to "com.spotify.music",
            "netflix.com" to "com.netflix.mediaclient",
            // Shopping
            "amazon.com" to "com.amazon.mShop.android.shopping",
            "amazon.in" to "com.amazon.mShop.android.shopping",
            "flipkart.com" to "com.flipkart.android",
            // Maps / Navigation
            "maps.google.com" to "com.google.android.apps.maps",
            // Productivity
            "docs.google.com" to "com.google.android.apps.docs",
            "drive.google.com" to "com.google.android.apps.docs",
            "github.com" to "com.github.android",
            // Payments (India)
            "phonepe.com" to "com.phonepe.app",
            "paytm.com" to "net.one97.paytm",
            "gpay.app.goo.gl" to "com.google.android.apps.nbu.paisa.user",
        )
        
        // Check exact domain match
        domainToApp[domain]?.let { return it }
        
        // Check if the domain is a subdomain of a known domain
        // e.g. "m.youtube.com" should match "youtube.com"
        for ((knownDomain, pkg) in domainToApp) {
            if (domain.endsWith(".$knownDomain")) {
                return pkg
            }
        }
        
        return null
    }

    private fun isKnownBrowser(packageName: String): Boolean {
        val browsers = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser", // Samsung Internet
            "com.vivaldi.browser",
            "com.kiwibrowser.browser",
            "com.UCMobile.intl",            // UC Browser
            "com.mi.globalbrowser",         // Mi Browser
            "com.coloros.browser",          // OPPO Browser
            "com.heytap.browser",           // Realme Browser
            "mark.via.gp",                  // Via Browser
            "org.chromium.chrome",
        )
        return browsers.contains(packageName)
    }

    private fun getMimeType(url: String): String? {
        val lowerUrl = url.lowercase()
        
        // Step 1: Try to extract extension cleanly using Uri parser to ignore query params
        var extension: String? = null
        try {
            val uri = Uri.parse(url)
            val lastPathSegment = uri.lastPathSegment
            if (lastPathSegment != null && lastPathSegment.contains(".")) {
                extension = lastPathSegment.substringAfterLast('.').lowercase()
            }
        } catch (e: Exception) {
            // Fallback to manual extraction if Uri parsing fails
        }

        // Step 2: Fallback to regex extraction if Uri parsing didn't work
        if (extension == null) {
            val match = Regex("""\.([a-zA-Z0-9]+)(?:[\?#]|$)""").find(lowerUrl)
            if (match != null) {
                extension = match.groupValues[1]
            }
        }

        // Step 3: Lookup in Android's native MimeTypeMap
        var type: String? = null
        if (extension != null) {
            type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            
            // Step 4: Robust fallback for common formats in case OS MimeTypeMap is incomplete
            if (type == null) {
                type = when (extension) {
                    "pdf" -> "application/pdf"
                    "apk" -> "application/vnd.android.package-archive"
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "doc" -> "application/msword"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "xls" -> "application/vnd.ms-excel"
                    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    "ppt" -> "application/vnd.ms-powerpoint"
                    "zip" -> "application/zip"
                    "rar" -> "application/x-rar-compressed"
                    "7z" -> "application/x-7z-compressed"
                    "tar" -> "application/x-tar"
                    "gz" -> "application/gzip"
                    "csv" -> "text/csv"
                    "txt" -> "text/plain"
                    "rtf" -> "application/rtf"
                    "mp4" -> "video/mp4"
                    "mkv" -> "video/x-matroska"
                    "avi" -> "video/x-msvideo"
                    "mp3" -> "audio/mpeg"
                    "wav" -> "audio/x-wav"
                    "ogg" -> "audio/ogg"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "svg" -> "image/svg+xml"
                    "json" -> "application/json"
                    "xml" -> "application/xml"
                    else -> null
                }
            }
        }
        
        // Ignore standard web pages, they should be routed to browsers natively without a file chooser
        if (type == "text/html" || type == "application/xhtml+xml") {
            return null
        }
        
        return type
    }
}
