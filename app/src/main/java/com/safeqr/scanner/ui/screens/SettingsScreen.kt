package com.safeqr.scanner.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeqr.scanner.ui.theme.*
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.*
import com.safeqr.scanner.viewmodel.QrViewModel
import kotlinx.coroutines.launch

@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    qrViewModel: com.safeqr.scanner.viewmodel.QrViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToLogin: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToSandbox: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        var childLockEnabled by remember {
            mutableStateOf(com.safeqr.scanner.data.PreferencesManager.isChildLockEnabled(context))
        }
        var showSetPinDialog by remember { mutableStateOf(false) }
        var showVerifyPinDialog by remember { mutableStateOf(false) }
        var showChildLockPinDialog by remember { mutableStateOf(false) }
    var childLockPinError by remember { mutableStateOf(false) }
    var isDisablingChildLock by remember { mutableStateOf(false) }
    
    // Auth Flow Dialogs
    var showParentalHub by remember { mutableStateOf(false) }
    val currentUser by qrViewModel.currentUser.collectAsState()
    
    val roleManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        context.getSystemService(android.content.Context.ROLE_SERVICE) as? android.app.role.RoleManager
    } else null
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER) == true) {
            Toast.makeText(context, "ThreatLens is now your default security browser!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Default browser request denied.", Toast.LENGTH_SHORT).show()
        }
    }
    var showAppLinksGuide by remember { mutableStateOf(false) }

    // Slow rotating gear icon
    val infiniteTransition = rememberInfiniteTransition(label = "settings")
    val gearRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gearRotation"
    )

    // Pulsing dot for active services
    val servicePulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "servicePulse"
    )

    // Animated underline
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }
    val underlineWidth by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "underline"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 24.dp)
    ) {
        // ——— Header with animated gear ———
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = NeonCyan.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(gearRotation)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        // Animated underline
        Box(
            modifier = Modifier
                .fillMaxWidth(underlineWidth * 0.2f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NeonCyan)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ——— PROFILE Section ———
        SectionHeader(title = "PROFILE")
        Spacer(modifier = Modifier.height(10.dp))

        var showEditProfileDialog by remember { mutableStateOf(false) }
        var showSignOutDialog by remember { mutableStateOf(false) }
        var showDeletePasswordDialog by remember { mutableStateOf(false) }
        var showUserDetailsDialog by remember { mutableStateOf(false) }
        var deletePassword by remember { mutableStateOf("") }

        GlassCard {
            if (currentUser != null) {
                // WOW Factor Profile View
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Glowing Avatar
                    Box(
                        modifier = Modifier
                            .size(65.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(NeonCyan, Color(0xFF9D4EDD), NeonCyan)
                                )
                            )
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(DarkBackground)
                            .clickable { showUserDetailsDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentUser!!.photoUrl != null) {
                            AsyncImage(
                                model = currentUser!!.photoUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = currentUser!!.displayName.take(1).uppercase(),
                                color = NeonCyan,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = currentUser!!.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = NeonCyan.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonCyan))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ThreatLens ID: ${currentUser!!.userId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, GlassBorder, Color.Transparent))))
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Edit Profile Button
                    OutlinedButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Edit", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Copy ID Button
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(currentUser!!.userId))
                            android.widget.Toast.makeText(context, "User ID copied!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Copy ID", color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Sign Out Button
                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Sign Out", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Delete Account Button
                    Button(
                        onClick = { showDeletePasswordDialog = true },
                        modifier = Modifier.weight(1.2f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaliciousRed.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Delete", color = MaliciousRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Custom Auth Card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text("Secure Login", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Sign in to access your profile and sync ThreatLens data.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onNavigateToLogin()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Login / Register", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Delete with Password Dialog
        if (showDeletePasswordDialog && currentUser != null) {
            var isDeleting by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { showDeletePasswordDialog = false },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Verify Password to Delete", color = MaliciousRed, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column {
                        Text("Please enter your account password to confirm deletion.", color = TextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it },
                            label = { Text("Password", color = TextSecondary) },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaliciousRed, unfocusedBorderColor = GlassBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (deletePassword.isBlank()) return@Button
                            isDeleting = true
                            qrViewModel.verifyPasswordAndDelete(currentUser!!.userId, deletePassword) { success, error ->
                                isDeleting = false
                                if (success) {
                                    showDeletePasswordDialog = false
                                    android.widget.Toast.makeText(context, "Account deleted successfully.", android.widget.Toast.LENGTH_SHORT).show()
                                    // Handle post-deletion navigation/state update here
                                } else {
                                    android.widget.Toast.makeText(context, error ?: "Failed to delete account", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed),
                        enabled = !isDeleting
                    ) {
                        if (isDeleting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Verify & Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeletePasswordDialog = false }, enabled = !isDeleting) { Text("Cancel", color = TextSecondary) }
                }
            )
        }


        // Sign Out Dialog
        if (showSignOutDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutDialog = false },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Sign Out", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = { Text("Are you sure you want to sign out?", color = TextSecondary, fontSize = 14.sp) },
                confirmButton = {
                    Button(onClick = { 
                        showSignOutDialog = false
                        qrViewModel.signOut()
                        onNavigateToLogin()
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed)) {
                        Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel", color = TextSecondary) }
                }
            )
        }

        // Edit Profile Dialog
        if (showEditProfileDialog && currentUser != null) {
            var editName by remember { mutableStateOf(currentUser!!.displayName) }
            AlertDialog(
                onDismissRequest = { showEditProfileDialog = false },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Edit Cyber Profile", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                        OutlinedTextField(
                            value = editName, onValueChange = { editName = it },
                            label = { Text("Display Name", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, unfocusedBorderColor = GlassBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = NeonCyan)
                        )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editName.isNotBlank()) {
                                qrViewModel.updateUser(editName.trim())
                                showEditProfileDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text("Save Profile", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditProfileDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        // WOW Factor User Details Dialog
        if (showUserDetailsDialog && currentUser != null) {
            AlertDialog(
                onDismissRequest = { showUserDetailsDialog = false },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("User Dossier", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBackground, RoundedCornerShape(16.dp))
                            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailRow(label = "Name", value = currentUser!!.displayName)
                        DetailRow(label = "Email", value = currentUser!!.email ?: "Not linked")
                        DetailRow(label = "Age", value = currentUser!!.age?.toString() ?: "Unknown")
                        DetailRow(label = "ThreatLens ID", value = currentUser!!.userId)
                        if (currentUser!!.isVerified) {
                            DetailRow(label = "Status", value = "Verified ✓")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showUserDetailsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text("Close", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }



        // ——— SYSTEM INTEGRATION Section ———
        SectionHeader(title = "SYSTEM INTEGRATION")
        Spacer(modifier = Modifier.height(10.dp))
        GlassCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default Security Browser", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text("Intercept external links (like WhatsApp) for scanning", color = TextSecondary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && roleManager != null) {
                                if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
                                    roleLauncher.launch(intent)
                                } else {
                                    Toast.makeText(context, "Role not available on this device.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Requires Android 10 or higher.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Enable", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
                
                Divider(color = GlassBorder, modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CautionAmber, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Intercept Verified Apps", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text("Fix Instagram/YouTube opening directly", color = TextSecondary, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { showAppLinksGuide = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Guide", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // App Links Guide Dialog
        if (showAppLinksGuide) {
            AlertDialog(
                onDismissRequest = { showAppLinksGuide = false },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CautionAmber, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Intercepting Verified Links", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column {
                        Text("Android automatically bypasses security browsers for 'Verified Apps' like Instagram, TikTok, or YouTube.", color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("To intercept these links:", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("1. Open your Android App Settings manually.", color = TextSecondary, fontSize = 14.sp)
                        Text("2. Find the app (e.g. Instagram).", color = TextSecondary, fontSize = 14.sp)
                        Text("3. Turn OFF \"Open supported links\".", color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Once disabled, links will be safely routed to ThreatLens first!", color = SafeGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAppLinksGuide = false }) {
                        Text("Close", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // ——— SECURITY Section ———
        SectionHeader(title = "SECURITY")
        Spacer(modifier = Modifier.height(10.dp))
        GlassCard {
            var autoBlock by remember {
                mutableStateOf(com.safeqr.scanner.data.PreferencesManager.getAutoBlock(context))
            }
            var vibrateOnDetection by remember {
                mutableStateOf(com.safeqr.scanner.data.PreferencesManager.getVibrate(context))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-block Malicious Links",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Block dangerous URLs automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = autoBlock,
                    onCheckedChange = {
                        autoBlock = it
                        com.safeqr.scanner.data.PreferencesManager.setAutoBlock(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = NeonCyan,
                        uncheckedTrackColor = DarkCard
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(GlassBorder)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vibrate on Detection",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Haptic feedback when QR is found",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = vibrateOnDetection,
                    onCheckedChange = {
                        vibrateOnDetection = it
                        com.safeqr.scanner.data.PreferencesManager.setVibrate(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = NeonCyan,
                        uncheckedTrackColor = DarkCard
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(GlassBorder))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSandbox() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sandbox Browser",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Open secure isolated browsing session",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = NeonCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))





        // ——— PARENTAL CONTROL Section ———
        SectionHeader(title = "PARENTAL CONTROL")
        Spacer(modifier = Modifier.height(10.dp))


        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (childLockEnabled) {
                        isDisablingChildLock = false
                        showVerifyPinDialog = true
                    } else {
                        showSetPinDialog = true
                    }
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (childLockEnabled) NeonCyan else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Parental Controls",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (childLockEnabled) "Active — advanced filtering enabled"
                               else "Off — all links accessible",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (childLockEnabled) SafeGreen else TextSecondary
                    )
                }
                Switch(
                    checked = childLockEnabled,
                    onCheckedChange = { wantEnabled ->
                        if (wantEnabled) {
                            showSetPinDialog = true
                        } else {
                            isDisablingChildLock = true
                            showVerifyPinDialog = true
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = NeonCyan,
                        uncheckedTrackColor = DarkCard
                    )
                )
            }

            if (childLockEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { 
                        isDisablingChildLock = false
                        showVerifyPinDialog = true 
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                ) {
                    Text("Manage Parental Controls", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ——— DEVELOPER OPTIONS (SATTUJI ONLY) ———
        if (currentUser?.userId == "sattuji") {
            Spacer(modifier = Modifier.height(28.dp))
            SectionHeader(title = "DEVELOPER OPTIONS")
            Spacer(modifier = Modifier.height(10.dp))
            
            var testUrl by remember { mutableStateOf("") }
            var testCategoryResult by remember { mutableStateOf<com.safeqr.scanner.analysis.WebsiteCategorizer.CategoryResult?>(null) }
            var isTestingUrl by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            
            GlassCard {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaliciousRed, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Engine Categorization Test", color = MaliciousRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Force-categorize any URL directly", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = testUrl,
                        onValueChange = { testUrl = it },
                        label = { Text("Enter URL or domain to test", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaliciousRed,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (testUrl.isNotBlank()) {
                                isTestingUrl = true
                                scope.launch {
                                    val domain = try {
                                        java.net.URI(if (testUrl.startsWith("http")) testUrl else "https://$testUrl").host ?: testUrl
                                    } catch (e: Exception) { testUrl }
                                    
                                    val signals = com.safeqr.scanner.analysis.WebsiteCategorizer.PageSignals(
                                        url = testUrl,
                                        domain = domain
                                    )
                                    val result = com.safeqr.scanner.analysis.WebsiteCategorizer.categorize(signals)
                                    testCategoryResult = result
                                    isTestingUrl = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isTestingUrl
                    ) {
                        if (isTestingUrl) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Test Categorizer Engine", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    if (testCategoryResult != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        var selectedOverrideCategory by remember(testCategoryResult) { mutableStateOf(testCategoryResult!!.category) }
                        var showOverrideDropdown by remember { mutableStateOf(false) }
                        var isOverriding by remember { mutableStateOf(false) }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkBackground,
                            border = BorderStroke(1.dp, MaliciousRed.copy(0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                Text("Engine Evaluation:", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${testCategoryResult!!.category.emoji} ${testCategoryResult!!.category.label}",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(Modifier.height(4.dp))
                                Text("Confidence: ${(testCategoryResult!!.confidence * 100).toInt()}%", color = NeonCyan, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Reason: ${testCategoryResult!!.reason}", color = TextSecondary, fontSize = 12.sp)
                                
                                Spacer(Modifier.height(16.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))
                                Spacer(Modifier.height(16.dp))
                                
                                Text("Correct & Assign Override (Global DB):", color = MaliciousRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { showOverrideDropdown = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                        border = BorderStroke(1.dp, GlassBorder),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("${selectedOverrideCategory.emoji} ${selectedOverrideCategory.label}")
                                    }
                                    
                                    var searchQuery by remember { mutableStateOf("") }
                                    val filteredCategories = com.safeqr.scanner.analysis.WebsiteCategorizer.SiteCategory.values().filter {
                                        it.name.contains(searchQuery, ignoreCase = true) || it.label.contains(searchQuery, ignoreCase = true)
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showOverrideDropdown,
                                        onDismissRequest = { showOverrideDropdown = false },
                                        modifier = Modifier.background(DarkSurface).heightIn(max = 350.dp).fillMaxWidth(0.85f)
                                    ) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                            placeholder = { Text("Search category...", color = TextSecondary) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NeonCyan,
                                                unfocusedBorderColor = GlassBorder,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary
                                            )
                                        )
                                        filteredCategories.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { Text("${cat.emoji} ${cat.label}", color = TextPrimary) },
                                                onClick = {
                                                    selectedOverrideCategory = cat
                                                    showOverrideDropdown = false
                                                    searchQuery = "" // Reset search
                                                }
                                            )
                                        }
                                        if (filteredCategories.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("No category found", color = TextSecondary) },
                                                onClick = { }
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.height(12.dp))
                                
                                Button(
                                    onClick = {
                                        isOverriding = true
                                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        val domain = try {
                                            java.net.URI(if (testUrl.startsWith("http")) testUrl else "https://$testUrl").host ?: testUrl
                                        } catch (e: Exception) { testUrl }
                                        
                                        db.collection("app_config").document("datasets")
                                            .update(com.google.firebase.firestore.FieldPath.of("websiteCategorizerData", "KNOWN_DOMAINS", domain), selectedOverrideCategory.name)
                                            .addOnSuccessListener {
                                                isOverriding = false
                                                android.widget.Toast.makeText(context, "Global Database Updated Successfully!", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                            .addOnFailureListener { e ->
                                                isOverriding = false
                                                android.widget.Toast.makeText(context, "Failed to update DB: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !isOverriding
                                ) {
                                    if (isOverriding) {
                                        CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("DEPLOY GLOBAL OVERRIDE", color = DarkBackground, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Set PIN Dialog
        if (showSetPinDialog) {
            PinDialog(
                title = "Set Parental PIN",
                subtitle = "Enter a 4-digit PIN to protect parental settings.",
                confirmLabel = "Enable Controls",
                onConfirm = { pin ->
                    com.safeqr.scanner.data.PreferencesManager.setChildLockPin(context, pin)
                    com.safeqr.scanner.data.PreferencesManager.setChildLockEnabled(context, true)
                    childLockEnabled = true
                    showSetPinDialog = false
                    showParentalHub = true // Open immediately after setting
                },
                onDismiss = { showSetPinDialog = false }
            )
        }
        // Verify PIN Dialog (to disable or manage)
        if (showVerifyPinDialog) {
            PinDialog(
                title = "Enter PIN",
                subtitle = "Enter your 4-digit PIN to manage or disable parental controls.",
                confirmLabel = "Unlock",
                onConfirm = { pin ->
                    if (com.safeqr.scanner.data.PreferencesManager.verifyChildLockPin(context, pin)) {
                        showVerifyPinDialog = false
                        if (isDisablingChildLock) {
                            com.safeqr.scanner.data.PreferencesManager.setChildLockEnabled(context, false)
                            childLockEnabled = false
                            isDisablingChildLock = false
                        } else {
                            showParentalHub = true
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Incorrect PIN", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { 
                    showVerifyPinDialog = false 
                    isDisablingChildLock = false
                }
            )
        }

        // Render the Parental Control Hub
        if (showParentalHub) {
            ParentalControlHub(
                onDismiss = { showParentalHub = false },
                onDisableChildLock = {
                    com.safeqr.scanner.data.PreferencesManager.setChildLockEnabled(context, false)
                    childLockEnabled = false
                    showParentalHub = false
                }
            )
        }

        // ── SYSTEM THEME Section ──
        SectionHeader(title = "SYSTEM THEME")
        Spacer(modifier = Modifier.height(10.dp))
        GlassCard {
            val themes = listOf(
                Triple("NEON_CYAN", "Neo Cyan", Color(0xFF00F0FF)),
                Triple("SNOW_WHITE", "Snow White", Color(0xFF007AFF))
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                themes.forEach { (themeId, label, color) ->
                    val isSelected = activeThemeName.value == themeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) color.copy(alpha = 0.08f) else DarkCard.copy(alpha = 0.4f))
                            .border(1.dp, if (isSelected) color else GlassBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                activeThemeName.value = themeId
                                com.safeqr.scanner.data.PreferencesManager.setAppTheme(context, themeId)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = label,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }



        Spacer(modifier = Modifier.height(28.dp))

        // ── ABOUT Section ──
        SectionHeader(title = "ABOUT")
        Spacer(modifier = Modifier.height(10.dp))
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Threat Lens",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                // Version badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeonCyan.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, NeonCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "v1.0.0",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Threat Lens is a security-focused QR code reader that analyzes links for potential threats before you open them. It uses multiple threat intelligence sources including Google Safe Browsing, VirusTotal, and heuristic analysis to provide comprehensive safety scores.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 22.sp
            )
        }



        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ——————————————————————————————————————————————————————————————————————————
//  Section Header
// ——————————————————————————————————————————————————————————————————————————

@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, GlassBorder)
                    )
                )
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(GlassBorder, Color.Transparent)
                    )
                )
        )
    }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
//  GlassCard
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

@Composable
private fun GlassCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassBorder.copy(alpha = 0.6f),
                        GlassBorder.copy(alpha = 0.2f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
//  Service Status Item with pulsing indicator
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

@Composable
private fun ServiceStatusItem(
    name: String,
    isConfigured: Boolean,
    isFree: Boolean = false,
    pulseAlpha: Float = 1f
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing status dot
        Box(contentAlignment = Alignment.Center) {
            // Glow behind
            if (isConfigured) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(SafeGreen.copy(alpha = pulseAlpha * 0.2f))
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isConfigured)
                            SafeGreen.copy(alpha = if (isConfigured) pulseAlpha else 1f)
                        else MaliciousRed.copy(alpha = 0.7f)
                    )
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isFree) "Free — Active" else if (isConfigured) "Active" else "Not configured",
                style = MaterialTheme.typography.bodySmall,
                color = if (isConfigured) SafeGreen.copy(alpha = 0.8f) else TextSecondary
            )
        }
    }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
//  PIN Dialog for Child Lock
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

@Composable
private fun PinDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // PIN input — 4 digit boxes
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            pin = it
                            pinError = false
                        }
                    },
                    placeholder = {
                        Text("● ● ● ●", color = TextSecondary.copy(alpha = 0.4f), textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 12.sp,
                        color = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = pinError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = GlassBorder,
                        errorBorderColor = MaliciousRed,
                        cursorColor = NeonCyan,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )

                if (pinError) {
                    Text(
                        text = "PIN must be exactly 4 digits",
                        color = MaliciousRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length == 4) {
                        onConfirm(pin)
                    } else {
                        pinError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(confirmLabel, color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}



@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ParentalControlHub(onDismiss: () -> Unit, onDisableChildLock: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var config by remember { mutableStateOf(com.safeqr.scanner.data.PreferencesManager.getParentalConfig(context)) }
    var showLogs by remember { mutableStateOf(false) }

    // Mock Presentation Features
    var aiStrictness by remember { mutableStateOf(true) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground.copy(alpha = 0.95f)) {
            if (showLogs) {
                ParentalActivityLogScreen(onBack = { showLogs = false })
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 32.dp).verticalScroll(rememberScrollState())) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("ThreatLens", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("Family Guardian", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.background(DarkCard, CircleShape)) { 
                            Icon(Icons.Default.Close, null, tint = TextPrimary) 
                        }
                    }
                    Spacer(Modifier.height(24.dp))

                    // Dashboard Stats
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBox("Threats Blocked", "24", "This Week", NeonCyan, Modifier.weight(1f))
                        StatBox("AI Enforcer", if (aiStrictness) "ON" else "OFF", "Adaptive Mode", PrimaryPurple, Modifier.weight(1f))
                        StatBox("Active Filters", "${listOf(config.blockAdult, config.blockPayment, config.blockSocial, config.blockGaming).count { it }}/4", "Categories", SafeGreen, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(32.dp))

                    // AI Toggle
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Adaptive AI Strictness", color = PrimaryPurple, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Auto-adjusts threat threshold based on scanning habits.", color = TextSecondary, fontSize = 12.sp)
                            }
                            Switch(checked = aiStrictness, onCheckedChange = { aiStrictness = it }, colors = SwitchDefaults.colors(checkedTrackColor = PrimaryPurple))
                        }
                    }
                    Spacer(Modifier.height(24.dp))

                    Text("CONTENT CATEGORIES", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    
                    // 2x2 Grid for Categories
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) {
                                CategoryCard("🔞", "Adult Content", config.blockAdult) {
                                    config = config.copy(blockAdult = !config.blockAdult)
                                    com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                CategoryCard("🎮", "Gaming", config.blockGaming) {
                                    config = config.copy(blockGaming = !config.blockGaming)
                                    com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) {
                                CategoryCard("💳", "Payments", config.blockPayment) {
                                    config = config.copy(blockPayment = !config.blockPayment)
                                    com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                CategoryCard("📱", "Social Media", config.blockSocial) {
                                    config = config.copy(blockSocial = !config.blockSocial)
                                    com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    Text("DIGITAL WELLBEING", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🌙", fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Bedtime Lock", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("Blocks all links during sleep hours", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                            Switch(checked = config.bedtimeEnabled, onCheckedChange = { 
                                config = config.copy(bedtimeEnabled = it)
                                com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                            }, colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan))
                        }

                        if (config.bedtimeEnabled) {
                            Spacer(Modifier.height(16.dp))
                            Divider(color = GlassBorder)
                            Spacer(Modifier.height(16.dp))
                            var startText by remember { mutableStateOf(config.bedtimeStartHour.toString()) }
                            var endText by remember { mutableStateOf(config.bedtimeEndHour.toString()) }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("START", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Surface(shape = RoundedCornerShape(8.dp), color = DarkBackground, border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                                        OutlinedTextField(
                                            value = startText,
                                            onValueChange = { 
                                                startText = it
                                                val v = it.toIntOrNull()
                                                if (v != null && v in 0..23) {
                                                    config = config.copy(bedtimeStartHour = v)
                                                    com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                                                }
                                            },
                                            modifier = Modifier.width(60.dp).height(50.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
                                        )
                                    }
                                }
                                Text("➡", color = TextSecondary, fontSize = 20.sp)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("END", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Surface(shape = RoundedCornerShape(8.dp), color = DarkBackground, border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                                        OutlinedTextField(
                                            value = endText,
                                            onValueChange = { 
                                                endText = it
                                                val v = it.toIntOrNull()
                                                if (v != null && v in 0..23) {
                                                    config = config.copy(bedtimeEndHour = v)
                                                    com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                                                }
                                            },
                                            modifier = Modifier.width(60.dp).height(50.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = CautionAmber, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    Text("EXCEPTIONS", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    GlassCard {
                        var whitelistInput by remember { mutableStateOf("") }
                        var blacklistInput by remember { mutableStateOf("") }

                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Whitelist (Always Allow)", color = SafeGreen, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = DarkBackground, border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                                OutlinedTextField(
                                    value = whitelistInput, 
                                    onValueChange = { whitelistInput = it }, 
                                    modifier = Modifier.fillMaxWidth(), 
                                    placeholder = { Text("example.com", color = TextSecondary) }, 
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            if (whitelistInput.isNotBlank()) {
                                                config = config.copy(whitelistDomains = config.whitelistDomains + whitelistInput)
                                                com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                                                whitelistInput = ""
                                            }
                                        }) { Icon(Icons.Default.Add, null, tint = SafeGreen) }
                                    }
                                )
                            }
                            config.whitelistDomains.forEach { domain ->
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(domain, color = TextPrimary)
                                    IconButton(onClick = {
                                        config = config.copy(whitelistDomains = config.whitelistDomains - domain)
                                        com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                                    }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, tint = MaliciousRed) }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Divider(color = GlassBorder)
                            Spacer(Modifier.height(16.dp))

                            Text("Blacklist (Always Block)", color = MaliciousRed, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = DarkBackground, border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                                OutlinedTextField(
                                    value = blacklistInput, 
                                    onValueChange = { blacklistInput = it }, 
                                    modifier = Modifier.fillMaxWidth(), 
                                    placeholder = { Text("badsite.com", color = TextSecondary) }, 
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            if (blacklistInput.isNotBlank()) {
                                                config = config.copy(blacklistDomains = config.blacklistDomains + blacklistInput)
                                                com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                                                blacklistInput = ""
                                            }
                                        }) { Icon(Icons.Default.Add, null, tint = MaliciousRed) }
                                    }
                                )
                            }
                            config.blacklistDomains.forEach { domain ->
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(domain, color = TextPrimary)
                                    IconButton(onClick = {
                                        config = config.copy(blacklistDomains = config.blacklistDomains - domain)
                                        com.safeqr.scanner.data.PreferencesManager.saveParentalConfig(context, config)
                                    }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, tint = MaliciousRed) }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { showLogs = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("View Threat Timeline", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onDisableChildLock,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaliciousRed),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaliciousRed,
                            containerColor = MaliciousRed.copy(alpha = 0.1f)
                        )
                    ) {
                        Text("Disable Guardian Mode", color = MaliciousRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
fun StatBox(title: String, value: String, subtitle: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 12.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextSecondary, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun CategoryCard(icon: String, title: String, isEnabled: Boolean, onClick: () -> Unit) {
    val borderColor = if (isEnabled) NeonCyan else GlassBorder
    val bgColor = if (isEnabled) NeonCyan.copy(alpha = 0.15f) else DarkCard
    Surface(
        modifier = Modifier.fillMaxWidth().height(120.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(if (isEnabled) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = if (isEnabled) TextPrimary else TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isEnabled) NeonCyan else Color.Transparent))
        }
    }
}

@Composable
fun ParentalActivityLogScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val logs = remember { com.safeqr.scanner.data.PreferencesManager.getParentalLogs(context) }
    val formatter = remember { java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(DarkCard, CircleShape)) { Icon(Icons.Default.ChevronLeft, null, tint = NeonCyan) }
            Spacer(Modifier.width(16.dp))
            Text("Threat Timeline", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(24.dp))
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No network activity recorded.", color = TextSecondary)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(logs.size) { i ->
                    val log = logs[i]
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Timeline Line
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(if (log.action == "Allowed") SafeGreen else MaliciousRed))
                            if (i < logs.size - 1) {
                                Box(modifier = Modifier.width(2.dp).height(80.dp).background(GlassBorder))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        // Event Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = DarkCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatter.format(java.util.Date(log.timestamp)), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        log.action.uppercase(),
                                        color = if (log.action == "Allowed") SafeGreen else MaliciousRed,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.background(if (log.action == "Allowed") SafeGreen.copy(0.1f) else MaliciousRed.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(log.url, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (log.reason.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚠", fontSize = 12.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(log.reason, color = CautionAmber, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

