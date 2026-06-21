package com.safeqr.scanner.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.ui.theme.*
import kotlinx.coroutines.delay

import androidx.lifecycle.repeatOnLifecycle

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
        var startAnimation by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            startAnimation = true
            delay(2200) // Keep splash screen for 2.2 seconds
            onSplashFinished()
        }
    }
    
    // Logo scaling animation (Spring)
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1.2f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )

    // Gentle floating animation (up and down)
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoFloat"
    )

    // Fade-in animation for text
    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 300),
        label = "textAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E1A),
                        Color(0xFF111827),
                        Color(0xFF0A0E1A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Shield Logo
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.safeqr.scanner.R.drawable.ic_threatlens_shield),
                contentDescription = "ThreatLens Logo",
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
                    .offset(y = floatOffset.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Animated Text
            Row(
                modifier = Modifier.alpha(alpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Threat",
                    color = Color(0xFFF8FAFC),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Lens",
                    color = Color(0xFF3B82F6),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Advanced Security Scanner",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(alpha)
            )
        }
    }
}


