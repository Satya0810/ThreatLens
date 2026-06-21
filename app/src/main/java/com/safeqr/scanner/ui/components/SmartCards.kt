package com.safeqr.scanner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EventCountdownCard(startTimeStr: String?, title: String?) {
    if (startTimeStr == null) return
    
    // Parse time like 20260605T100000Z or standard formats
    var targetMillis by remember { mutableStateOf<Long?>(null) }
    
    LaunchedEffect(startTimeStr) {
        try {
            // Simplified parsing for demo
            val cleanStr = startTimeStr.replace("T", "").replace("Z", "")
            if (cleanStr.length >= 14) {
                val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                targetMillis = format.parse(cleanStr)?.time
            }
        } catch (e: Exception) {
            targetMillis = System.currentTimeMillis() + 86400000L // Fake +24h fallback
        }
    }
    
    if (targetMillis == null) return
    
    var timeRemaining by remember { mutableStateOf(targetMillis!! - System.currentTimeMillis()) }
    
    LaunchedEffect(targetMillis) {
        while (timeRemaining > 0) {
            delay(1000)
            timeRemaining = targetMillis!! - System.currentTimeMillis()
        }
    }
    
    val days = (timeRemaining / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
    val hours = ((timeRemaining / (1000 * 60 * 60)) % 24).coerceAtLeast(0)
    val mins = ((timeRemaining / (1000 * 60)) % 60).coerceAtLeast(0)
    val secs = ((timeRemaining / 1000) % 60).coerceAtLeast(0)

    val isLive = timeRemaining <= 0
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = PrimaryBlue, ambientColor = PrimaryBlue.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, PrimaryBlue.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Event, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title ?: "Upcoming Event",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (isLive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(SafeGreen.copy(alpha = pulseAlpha)))
                Spacer(modifier = Modifier.width(8.dp))
                Text("EVENT IS LIVE", color = SafeGreen, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CountdownUnit(days.toString(), "DAYS")
                CountdownUnit(hours.toString().padStart(2, '0'), "HOURS")
                CountdownUnit(mins.toString().padStart(2, '0'), "MINS")
                CountdownUnit(secs.toString().padStart(2, '0'), "SECS", NeonCyan.copy(alpha = pulseAlpha))
            }
        }
    }
}

@Composable
private fun CountdownUnit(value: String, label: String, valueColor: Color = TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = valueColor)
        Text(label, fontSize = 10.sp, color = TextSecondary, letterSpacing = 1.sp)
    }
}

@Composable
fun CryptoBalanceCard(address: String?, coin: String?, amountRequested: String? = null) {
    if (address == null) return
    
    val displayCoin = (coin ?: "BTC").uppercase()
    
    // Simulate fetching balance
    var balance by remember { mutableStateOf<String?>("Loading...") }
    var usdValue by remember { mutableStateOf<String?>("...") }
    
    LaunchedEffect(address) {
        delay(1200) // Mock API latency
        
        // Mock data based on first char of address to be deterministic
        val mockBal = if (address.startsWith("1") || address.startsWith("0x")) "1.452" else "0.024"
        val mockUsd = if (mockBal == "1.452") "$94,321.50" else "$1,558.80"
        
        balance = "$mockBal $displayCoin"
        usdValue = mockUsd
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp), spotColor = Color(0xFFF7931A), ambientColor = Color(0xFFF7931A).copy(alpha = 0.5f))
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(DarkSurface, Color(0xFF151820))))
            .border(1.dp, Color(0xFFF7931A).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = Color(0xFFF7931A), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Live Wallet Balance", color = TextSecondary, fontSize = 12.sp)
            }
            Text("Network: $displayCoin", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.background(DarkBackground, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(balance ?: "", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(usdValue ?: "", color = SafeGreen, fontSize = 14.sp)
        
        if (amountRequested != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryBlue.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .border(1.dp, PrimaryBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Requested Payment:", color = TextPrimary, fontSize = 13.sp)
                Text("$amountRequested $displayCoin", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        }
    }
}
