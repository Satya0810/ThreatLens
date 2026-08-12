package com.safeqr.scanner.ui.screens

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.safeqr.scanner.R
import com.safeqr.scanner.viewmodel.AuthState
import com.safeqr.scanner.viewmodel.AuthViewModel
import com.safeqr.scanner.viewmodel.QrViewModel

// Beautiful Dark Mode Colors
private val DarkBackground = Color(0xFF0F172A) // Sleek slate dark
private val PrimaryAccent = Color(0xFF38BDF8) // Bright sky blue
private val SurfaceColor = Color(0xFF1E293B)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)
private val ErrorColor = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()

    var currentView by remember { mutableStateOf("login") }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    
    val isGmsAvailable = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { idToken ->
                    viewModel.signInWithGoogle(idToken)
                } ?: run {
                    Toast.makeText(context, "Google sign in failed: Missing ID Token", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.e("LoginScreen", "Google sign in failed: ${e.statusCode}", e)
                Toast.makeText(context, "Google sign in failed (Code: ${e.statusCode})", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.LoginSuccess) {
            onLoginSuccess()
        } else if (authState is AuthState.RegistrationSuccess) {
            viewModel.startEmailVerificationPolling {
                viewModel.resetState()
                userId = ""
                password = ""
                name = ""
                email = ""
                age = ""
                currentView = "login"
            }
        } else if (authState is AuthState.NewGoogleUserSetup) {
            email = (authState as AuthState.NewGoogleUserSetup).email
            userId = ""
            password = ""
            currentView = "google_setup"
        }
    }

    BackHandler(enabled = currentView != "login") {
        viewModel.resetState()
        password = ""
        name = ""
        email = ""
        age = ""
        currentView = "login"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo
            Image(
                painter = painterResource(id = R.drawable.ic_threatlens_shield),
                contentDescription = "ThreatLens Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("ThreatLens", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Secure Code Scanner", color = TextSecondary, fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(32.dp))

            // Error Banner
            AnimatedVisibility(visible = authState is AuthState.Error || authState is AuthState.NewGoogleUserSetupError) {
                val errorMsg = (authState as? AuthState.Error)?.message 
                    ?: (authState as? AuthState.NewGoogleUserSetupError)?.message ?: ""
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = errorMsg,
                        color = ErrorColor,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }
            
            // Success Banner for Registration
            AnimatedVisibility(visible = authState is AuthState.RegistrationSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Registration successful! Please check your email and click the link to verify your account.",
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            // Success Banner for Reset Email
            AnimatedVisibility(visible = authState is AuthState.PasswordResetEmailSent) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "A password reset link has been sent to your email!",
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            // Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = currentView,
                        transitionSpec = {
                            fadeIn() + slideInHorizontally(initialOffsetX = { 50 }) togetherWith 
                            fadeOut() + slideOutHorizontally(targetOffsetX = { -50 })
                        }
                    ) { view ->
                        Column {
                            
                            if (view != "login") {
                                TextButton(
                                    onClick = { 
                                        viewModel.resetState()
                                        password = ""
                                        name = ""
                                        email = ""
                                        age = ""
                                        currentView = "login" 
                                    },
                                    modifier = Modifier.align(Alignment.Start)
                                ) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = PrimaryAccent)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Go Back", color = PrimaryAccent)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (view == "register") {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Full Name") },
                                    leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null, tint = PrimaryAccent) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryAccent,
                                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                        focusedLabelColor = PrimaryAccent,
                                        cursorColor = PrimaryAccent
                                    ),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                OutlinedTextField(
                                    value = age,
                                    onValueChange = { age = it },
                                    label = { Text("Age") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    leadingIcon = { Icon(Icons.Rounded.Numbers, contentDescription = null, tint = PrimaryAccent) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryAccent,
                                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                        focusedLabelColor = PrimaryAccent,
                                        cursorColor = PrimaryAccent
                                    ),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email Address") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = PrimaryAccent) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryAccent,
                                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                        focusedLabelColor = PrimaryAccent,
                                        cursorColor = PrimaryAccent
                                    ),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (view != "verify_google_otp") {
                                if (view == "google_setup") {
                                    Text(
                                        text = "Welcome! Since this is your first time using Google, please choose a unique User ID and set a backup password.",
                                        color = TextSecondary,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(bottom = 16.dp, start = 4.dp, end = 4.dp)
                                    )
                                }
                            // User ID Field (used in all views)
                            OutlinedTextField(
                                value = userId,
                                onValueChange = { userId = it.lowercase().replace(" ", "") },
                                label = { 
                                    Text(
                                        when (view) {
                                            "login" -> "User ID or Email"
                                            "forgot" -> "Enter your User ID or Email"
                                            "google_setup" -> "Choose your User ID"
                                            else -> "Choose a Unique User ID"
                                        }
                                    ) 
                                },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = PrimaryAccent) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryAccent,
                                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                    focusedLabelColor = PrimaryAccent,
                                    cursorColor = PrimaryAccent
                                ),
                                singleLine = true,
                                enabled = authState !is AuthState.RegistrationSuccess
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            } // End of User ID Field check

                            // Password Field (not for forgot)
                            if (view != "forgot" && view != "verify_google_otp") {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = PrimaryAccent) },
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                contentDescription = "Toggle password visibility",
                                                tint = TextSecondary
                                            )
                                        }
                                    },
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryAccent,
                                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                        focusedLabelColor = PrimaryAccent,
                                        cursorColor = PrimaryAccent
                                    ),
                                    singleLine = true,
                                    enabled = authState !is AuthState.RegistrationSuccess
                                )

                                if (view == "login") {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                        TextButton(onClick = { 
                                            viewModel.resetState()
                                            userId = ""
                                            password = ""
                                            name = ""
                                            email = ""
                                            age = ""
                                            currentView = "forgot" 
                                        }) {
                                            Text("Forgot Password?", color = PrimaryAccent, fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Main Action Button
                            Button(
                                onClick = {
                                    if (authState is AuthState.RegistrationSuccess) return@Button
                                    when (view) {
                                        "login" -> viewModel.login(userId, password)
                                        "register" -> viewModel.register(name, age, userId, email, password)
                                        "forgot" -> viewModel.startForgotPassword(userId)
                                        "google_setup" -> viewModel.completeGoogleSetup(userId, password)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                                enabled = authState !is AuthState.Loading
                            ) {
                                if (authState is AuthState.Loading) {
                                    CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(24.dp))
                                } else if (authState is AuthState.RegistrationSuccess) {
                                    Text("Waiting to get the email verified...", color = DarkBackground, fontWeight = FontWeight.Bold)
                                } else {
                                    val btnText = when (view) {
                                        "login" -> "LOGIN"
                                        "register" -> "CREATE ACCOUNT"
                                        "google_setup" -> "COMPLETE SETUP"
                                        else -> "SEND RESET LINK"
                                    }
                                    Text(btnText, color = DarkBackground, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (view == "login") {
                                Spacer(modifier = Modifier.height(14.dp))
                                
                                if (isGmsAvailable) {
                                    Button(
                                        onClick = {
                                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                .requestIdToken(context.getString(com.safeqr.scanner.R.string.default_web_client_id))
                                                .requestEmail()
                                                .build()
                                            val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                            googleSignInClient.signOut().addOnCompleteListener {
                                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                        enabled = authState !is AuthState.Loading
                                    ) {
                                        Text("CONTINUE WITH GOOGLE", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                }

                                Button(
                                    onClick = { viewModel.loginAsGuest() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    enabled = authState !is AuthState.Loading
                                ) {
                                    Text("CONTINUE AS GUEST", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))
                            
                            // Footer Links
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (view == "login") {
                                    Text("New hacker? ", color = TextSecondary, fontSize = 13.sp)
                                    Text(
                                        "Register here", 
                                        color = PrimaryAccent, 
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { 
                                            viewModel.resetState()
                                            userId = ""
                                            password = ""
                                            name = ""
                                            email = ""
                                            age = ""
                                            currentView = "register" 
                                        }
                                    )
                                } else if (view == "register") {
                                    Text("Already a user? ", color = TextSecondary, fontSize = 13.sp)
                                    Text(
                                        "Login here", 
                                        color = PrimaryAccent, 
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { 
                                            viewModel.resetState()
                                            userId = ""
                                            password = ""
                                            name = ""
                                            email = ""
                                            age = ""
                                            currentView = "login" 
                                        }
                                    )
                                } else if (view == "forgot") {
                                    Text("Remember your password? ", color = TextSecondary, fontSize = 13.sp)
                                    Text(
                                        "Login here", 
                                        color = PrimaryAccent, 
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { 
                                            viewModel.resetState()
                                            userId = ""
                                            password = ""
                                            name = ""
                                            email = ""
                                            age = ""
                                            currentView = "login" 
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
