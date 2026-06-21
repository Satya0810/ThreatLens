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
        
        // Find all activities that can handle this URL
        val resolveInfoList = packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_ALL
        )

        var targetPackage: String? = null
        val myPackageName = context.packageName

        // Priority 1: Find the native app for this link (e.g., com.instagram.android)
        // We consider an app "native" if it is NOT a known browser and NOT our app.
        for (resolveInfo in resolveInfoList) {
            val pkgName = resolveInfo.activityInfo.packageName
            if (pkgName != myPackageName && !isKnownBrowser(pkgName)) {
                targetPackage = pkgName
                break
            }
        }

        // Priority 2: If no native app found, find a known browser (like Chrome)
        if (targetPackage == null) {
            for (resolveInfo in resolveInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName != myPackageName && isKnownBrowser(pkgName)) {
                    targetPackage = pkgName
                    break
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
        // Create an intent chooser but we can't easily exclude our app from a standard chooser
        // easily on older Androids, so we just clear the package and fire.
        intent.setPackage(null)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SmartRouter", "Fallback chooser failed", e)
        }
    }

    private fun isKnownBrowser(packageName: String): Boolean {
        val browsers = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser", // Samsung Internet
            "com.vivaldi.browser"
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
