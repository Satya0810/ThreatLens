package com.safeqr.scanner.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.analysis.AILearningEngine
import com.safeqr.scanner.data.remote.CloudSyncManager
import com.safeqr.scanner.ui.theme.DarkBackground
import com.safeqr.scanner.ui.theme.DarkSurface
import com.safeqr.scanner.ui.theme.NeonCyan
import com.safeqr.scanner.ui.theme.TextPrimary
import com.safeqr.scanner.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun NeuralCoreScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var weights by remember { mutableStateOf(AILearningEngine.getWeights()) }
    var isSyncing by remember { mutableStateOf(false) }

    // Periodically refresh weights from memory to show "real-time" shifts if a scan happens in background
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            weights = AILearningEngine.getWeights()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "AI Neural Core",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Autonomous Federated Learning Engine",
            color = NeonCyan,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── 🧠 Animated Neural Network Visualizer ──
        NeuralNetworkVisualizer(isSyncing = isSyncing)

        Spacer(modifier = Modifier.height(40.dp))

        // ── Hive Mind Sync Button ──
        Button(
            onClick = {
                if (isSyncing) return@Button
                isSyncing = true
                coroutineScope.launch {
                    try {
                        val globalWeights = CloudSyncManager.fetchGlobalWeights()
                        if (globalWeights.isNotEmpty()) {
                            AILearningEngine.mergeGlobalWeights(context, globalWeights)
                            weights = AILearningEngine.getWeights()
                            Toast.makeText(context, "Hive Mind Synced Successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No global data found.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Sync Failed", Toast.LENGTH_SHORT).show()
                    } finally {
                        isSyncing = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSyncing) DarkSurface else NeonCyan.copy(alpha = 0.2f),
                contentColor = if (isSyncing) TextSecondary else NeonCyan
            ),
            shape = RoundedCornerShape(28.dp),
            border = if (!isSyncing) androidx.compose.foundation.BorderStroke(1.dp, NeonCyan) else null
        ) {
            if (isSyncing) {
                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Syncing with Global Hive Mind...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync with Hive Mind", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Active Feature Weights ──
        Text(
            text = "Active Synaptic Weights",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(16.dp))

        weights.forEach { (feature, weight) ->
            WeightProgressBar(feature = feature, weight = weight)
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun NeuralNetworkVisualizer(isSyncing: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Core Pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSyncing) 500 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSyncing) 3000 else 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp)
    ) {
        // Nodes and Connections Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 3
            
            val nodeCount = 8
            val nodes = mutableListOf<Offset>()
            
            // Draw nodes in a circle
            for (i in 0 until nodeCount) {
                val angle = (i * (360f / nodeCount)) + rotation
                val rad = Math.toRadians(angle.toDouble())
                val x = center.x + (radius * Math.cos(rad)).toFloat()
                val y = center.y + (radius * Math.sin(rad)).toFloat()
                val offset = Offset(x, y)
                nodes.add(offset)
                
                // Draw line to center
                drawLine(
                    color = NeonCyan.copy(alpha = if (isSyncing) 0.8f else 0.3f),
                    start = offset,
                    end = center,
                    strokeWidth = if (isSyncing) 4f else 2f
                )
                
                // Draw node
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = 10f,
                    center = offset
                )
            }
            
            // Draw lines between adjacent nodes
            for (i in 0 until nodeCount) {
                val next = if (i == nodeCount - 1) 0 else i + 1
                drawLine(
                    color = NeonCyan.copy(alpha = 0.15f),
                    start = nodes[i],
                    end = nodes[next],
                    strokeWidth = 2f
                )
            }
        }

        // Central AI Core
        Box(
            modifier = Modifier
                .size(60.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NeonCyan, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun WeightProgressBar(feature: String, weight: Float) {
    // Normalize weight for display (assuming typical weights range -1.0 to 1.0)
    // Map to 0.0 - 1.0 for the progress bar. Center (0.0 weight) is 0.5.
    val normalized = ((weight + 1.0f) / 2.0f).coerceIn(0.0f, 1.0f)
    
    // Animate the progress bar changes
    val animatedProgress by animateFloatAsState(
        targetValue = normalized,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = feature.replace("_", " ").uppercase(),
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format("%.4f", weight),
                color = NeonCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF0077B6), NeonCyan)
                        )
                    )
            )
        }
    }
}
