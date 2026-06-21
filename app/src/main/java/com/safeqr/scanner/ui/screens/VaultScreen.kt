package com.safeqr.scanner.ui.screens

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.safeqr.scanner.data.SecureVaultManager
import com.safeqr.scanner.data.model.ScanResult
import com.safeqr.scanner.ui.theme.DarkSurface
import com.safeqr.scanner.ui.theme.GlassWhite
import com.safeqr.scanner.ui.theme.MaliciousRed
import com.safeqr.scanner.ui.theme.NeonCyan
import com.safeqr.scanner.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var isAuthenticated by remember { mutableStateOf(false) }
    var vaultItems by remember { mutableStateOf<List<ScanResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        val biometricManager = BiometricManager.from(context)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            isAuthenticated = true
                            vaultItems = SecureVaultManager.getVaultItems(context)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Zero-Trust QR Vault")
                    .setSubtitle("Authenticate to access your secure QR codes")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()

                biometricPrompt.authenticate(promptInfo)
            } else {
                Toast.makeText(context, "Biometric authentication not available on this screen", Toast.LENGTH_SHORT).show()
                onNavigateBack()
            }
        } else {
            Toast.makeText(context, "Biometric authentication not configured on this device", Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = "Vault", tint = NeonCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Secure Vault", color = Color.White)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkSurface
    ) { paddingValues ->
        if (isAuthenticated) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (vaultItems.isEmpty()) {
                    item {
                        Text(
                            "Your vault is empty.",
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    items(vaultItems) { item ->
                        VaultItemCard(
                            scanResult = item,
                            onDelete = {
                                SecureVaultManager.removeFromVault(context, item.rawContent)
                                vaultItems = SecureVaultManager.getVaultItems(context)
                            }
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Authenticating...", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun VaultItemCard(scanResult: ScanResult, onDelete: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassWhite.copy(alpha = 0.05f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (scanResult.domain != null) "Domain: ${scanResult.domain}" else "Data Record",
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isVisible) scanResult.rawContent else "••••••••••••••••••••••••",
                color = Color.White,
                fontSize = 16.sp
            )
        }
        
        IconButton(onClick = { isVisible = !isVisible }) {
            Icon(
                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = "Toggle Visibility",
                tint = Color.Gray
            )
        }
        
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaliciousRed)
        }
    }
}
