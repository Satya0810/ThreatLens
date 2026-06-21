package com.safeqr.scanner.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf

// Global theme state for reactive system-wide theme changes
val activeThemeName = mutableStateOf("NEON_CYAN")

private val CyanColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color(0xFF0A0E1A),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = PrimaryBlue,

    secondary = StaticPrimaryPurple,
    onSecondary = Color(0xFF0A0E1A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = StaticPrimaryPurple,

    tertiary = AccentGreen,
    onTertiary = Color(0xFF0A0E1A),
    tertiaryContainer = Color(0xFF1E293B),
    onTertiaryContainer = AccentGreen,

    error = MaliciousRed,
    onError = Color(0xFFF1F5F9),
    errorContainer = Color(0xFF1E293B),
    onErrorContainer = MaliciousRed,

    background = Color(0xFF0A0E1A),
    onBackground = Color(0xFFF1F5F9),

    surface = Color(0xFF111827),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),

    outline = Color(0x33FFFFFF),
    outlineVariant = Color(0x1AFFFFFF),

    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF0A0E1A),
    inversePrimary = Color(0xFF0A0E1A),

    scrim = Color(0xFF000000),
    surfaceTint = PrimaryBlue
)

private val GreenColorScheme = darkColorScheme(
    primary = Color(0xFF39FF14), // Hacker Green
    onPrimary = DarkBackground,
    primaryContainer = DarkCard,
    onPrimaryContainer = Color(0xFF39FF14),

    secondary = Color(0xFF00FF88), // Hacker Cyan/Sea Green
    onSecondary = DarkBackground,
    secondaryContainer = DarkCard,
    onSecondaryContainer = Color(0xFF00FF88),

    tertiary = Color(0xFF00F0FF),
    onTertiary = DarkBackground,
    tertiaryContainer = DarkCard,
    onTertiaryContainer = Color(0xFF00F0FF),

    error = MaliciousRed,
    onError = TextPrimary,
    errorContainer = DarkCard,
    onErrorContainer = MaliciousRed,

    background = DarkBackground,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,

    outline = GlassBorder,
    outlineVariant = GlassWhite,

    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = DarkBackground,

    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF39FF14)
)

private val PurpleColorScheme = darkColorScheme(
    primary = Color(0xFFFF00FF), // Magenta
    onPrimary = DarkBackground,
    primaryContainer = DarkCard,
    onPrimaryContainer = Color(0xFFFF00FF),

    secondary = Color(0xFF00F0FF), // Cyber Cyan
    onSecondary = DarkBackground,
    secondaryContainer = DarkCard,
    onSecondaryContainer = Color(0xFF00F0FF),

    tertiary = Color(0xFFFFB800),
    onTertiary = DarkBackground,
    tertiaryContainer = DarkCard,
    onTertiaryContainer = Color(0xFFFFB800),

    error = MaliciousRed,
    onError = TextPrimary,
    errorContainer = DarkCard,
    onErrorContainer = MaliciousRed,

    background = DarkBackground,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,

    outline = GlassBorder,
    outlineVariant = GlassWhite,

    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = DarkBackground,

    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFFF00FF)
)

private val OrangeColorScheme = darkColorScheme(
    primary = Color(0xFFFF4500), // Volcanic Orange-Red
    onPrimary = DarkBackground,
    primaryContainer = DarkCard,
    onPrimaryContainer = Color(0xFFFF4500),

    secondary = Color(0xFFFFB800), // Electric Amber
    onSecondary = DarkBackground,
    secondaryContainer = DarkCard,
    onSecondaryContainer = Color(0xFFFFB800),

    tertiary = Color(0xFFFF003C),
    onTertiary = DarkBackground,
    tertiaryContainer = DarkCard,
    onTertiaryContainer = Color(0xFFFF003C),

    error = MaliciousRed,
    onError = TextPrimary,
    errorContainer = DarkCard,
    onErrorContainer = MaliciousRed,

    background = DarkBackground,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,

    outline = GlassBorder,
    outlineVariant = GlassWhite,

    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = DarkBackground,

    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFFF4500)
)

private val WhiteColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF1455FF), // Instagram/Vibrant Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = Color(0xFF1455FF),

    secondary = Color(0xFF5856D6), // Purple
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBEBFA),
    onSecondaryContainer = Color(0xFF5856D6),

    tertiary = Color(0xFF34C759), // Green
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8F8EB),
    onTertiaryContainer = Color(0xFF34C759),

    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFEECEB),
    onErrorContainer = Color(0xFFFF3B30),

    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF8E8E93),

    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE5E5EA),

    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = Color(0xFF007AFF),

    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF007AFF)
)

@Composable
fun SafeQRScannerTheme(
    content: @Composable () -> Unit
) {
    val context = LocalView.current.context
    
    // Load preference value on startup
    LaunchedEffect(Unit) {
        activeThemeName.value = com.safeqr.scanner.data.PreferencesManager.getAppTheme(context)
    }

    val colorScheme = when (activeThemeName.value) {
        "SNOW_WHITE" -> WhiteColorScheme
        else -> CyanColorScheme
    }

    // Update dynamic global color observables reactively when theme changes
    LaunchedEffect(colorScheme) {
        NeonCyan = colorScheme.primary
        NeonCyanGlow = colorScheme.primary.copy(alpha = 0.4f)
        PrimaryPurple = colorScheme.secondary
        NeonPurpleGlow = colorScheme.secondary.copy(alpha = 0.4f)
        
        // Default Dark Text Colors
        var defaultTextPrimary = Color(0xFFFFFFFF)
        var defaultTextSecondary = Color(0xFF94A3B8)
        var defaultTextTertiary = Color(0xFF64748B)
        
        var defaultGlassWhite = Color(0x26FFFFFF)
        var defaultGlassBorder = Color(0x4DFFFFFF)
        var defaultGlassOverlay = Color(0x1AFFFFFF)

        when (activeThemeName.value) {
            "SNOW_WHITE" -> {
                DarkBackground = Color(0xFFF2F2F7) // iOS Light Gray Background
                DarkSurface = Color(0xFFFFFFFF)    // Pure White Surface
                DarkCard = Color(0xFFFFFFFF)       // Pure White Card
                
                defaultTextPrimary = Color(0xFF000000) // Black Text
                defaultTextSecondary = Color(0xFF3C3C43) // Dark Gray Text
                defaultTextTertiary = Color(0x993C3C43)  // Lighter Gray Text
                
                defaultGlassWhite = Color(0x1A000000) // 10% black
                defaultGlassBorder = Color(0x33000000) // 20% black border
                defaultGlassOverlay = Color(0x0D000000) // 5% black
            }
            else -> {
                // NEON_CYAN (Refined Premium Cyberpunk)
                DarkBackground = Color(0xFF0A0E1A)
                DarkSurface = Color(0xFF111827)
                DarkCard = Color(0xFF1E293B)
            }
        }
        
        // Apply text and glass colors
        TextPrimary = defaultTextPrimary
        TextSecondary = defaultTextSecondary
        TextTertiary = defaultTextTertiary
        GlassWhite = defaultGlassWhite
        GlassBorder = defaultGlassBorder
        GlassOverlay = defaultGlassOverlay
    }

    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = activeThemeName.value == "SNOW_WHITE"
            insetsController.isAppearanceLightNavigationBars = activeThemeName.value == "SNOW_WHITE"
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SafeQRTypography,
        content = content
    )
}
