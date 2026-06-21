package com.safeqr.scanner.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Safety status colors — refined, modern tones
val SafeGreen = Color(0xFF10B981)        // Modern Emerald Green
val CautionAmber = Color(0xFFF59E0B)     // Rich Amber
val MaliciousRed = Color(0xFFEF4444)     // Standard Danger Red

// Core palette - deep premium dark (Dynamic)
var DarkBackground by mutableStateOf(Color(0xFF0F172A))   // Deep Slate
var DarkSurface by mutableStateOf(Color(0xFF1E293B))      // Elevated surface
var DarkCard by mutableStateOf(Color(0xFF334155))         // Visible card elevation

// Accent colors (defaults/static fallbacks)
val PrimaryBlue = Color(0xFF3B82F6)      // Professional Modern Blue (no longer neon)
val StaticPrimaryPurple = Color(0xFF8B5CF6) // Elegant Violet
val AccentGreen = Color(0xFF10B981)      // Modern Emerald

// Glassmorphism
var GlassWhite by mutableStateOf(Color(0x1AFFFFFF))       // 10% white
var GlassBorder by mutableStateOf(Color(0x33FFFFFF))      // 20% white border
var GlassOverlay by mutableStateOf(Color(0x0DFFFFFF))     // 5% white

val NeonGreenGlow = Color(0x3310B981)    // Subtle glow
val NeonRedGlow = Color(0x33EF4444)      // Subtle glow

// Text
var TextPrimary by mutableStateOf(Color(0xFFF8FAFC))      // Soft white
var TextSecondary by mutableStateOf(Color(0xFF94A3B8))     // Slate Gray
var TextTertiary by mutableStateOf(Color(0xFF64748B))      // Darker Slate

// Dynamic theme-mapped color observables (keeping variable names for compatibility but removing neon aspect)
var NeonCyan by mutableStateOf(PrimaryBlue)
var NeonCyanGlow by mutableStateOf(Color(0x333B82F6))
var PrimaryPurple by mutableStateOf(StaticPrimaryPurple)
var NeonPurpleGlow by mutableStateOf(Color(0x338B5CF6))
