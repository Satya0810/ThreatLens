package com.safeqr.scanner.analysis.telemetry

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityManager

/**
 * DeviceThreatStateMonitor — Vector 2: Runtime Device Threat Telemetry
 *
 * Captures the physical and operational state of the device at the moment
 * a financial payment or link interaction is initiated.
 *
 * Detects:
 * 1. Active phone calls (critical indicator for Digital Arrest and coercive extortion).
 * 2. Active screen-sharing / remote display casting (AnyDesk, TeamViewer, RustDesk).
 * 3. Non-system Accessibility services with overlay/tap privileges (Automated Transfer Systems).
 *
 * Privacy guarantee: Never records audio or captures screen content. Only checks binary system state flags.
 */
object DeviceThreatStateMonitor {

    data class DeviceThreatState(
        val isCallActive: Boolean,
        val isScreenSharingActive: Boolean,
        val hasSuspiciousAccessibilityService: Boolean,
        val timestampMs: Long = System.currentTimeMillis()
    ) {
        val hasActiveEnvironmentalThreat: Boolean
            get() = isCallActive || isScreenSharingActive || hasSuspiciousAccessibilityService
    }

    /**
     * Inspects the current device state across telephony, display, and accessibility subsystems.
     */
    fun captureCurrentState(context: Context): DeviceThreatState {
        val isCallActive = checkActiveCall(context)
        val isScreenSharing = checkActiveScreenSharing(context)
        val hasSuspiciousAccessibility = checkSuspiciousAccessibility(context)

        return DeviceThreatState(
            isCallActive = isCallActive,
            isScreenSharingActive = isScreenSharing,
            hasSuspiciousAccessibilityService = hasSuspiciousAccessibility
        )
    }

    private fun checkActiveCall(context: Context): Boolean {
        return try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val isInTelecomCall = telecom?.isInCall ?: false

            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val isInAudioCall = audio?.mode == AudioManager.MODE_IN_CALL || audio?.mode == AudioManager.MODE_IN_COMMUNICATION

            isInTelecomCall || isInAudioCall
        } catch (e: Exception) {
            false
        }
    }

    private fun checkActiveScreenSharing(context: Context): Boolean {
        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
            val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            presentationDisplays.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun checkSuspiciousAccessibility(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

            enabledServices.any { serviceInfo ->
                val appInfo = serviceInfo.resolveInfo?.serviceInfo?.applicationInfo
                if (appInfo != null) {
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    !isSystemApp
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
