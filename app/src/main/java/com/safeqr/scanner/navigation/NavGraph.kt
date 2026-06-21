package com.safeqr.scanner.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safeqr.scanner.ui.screens.LoginScreen
import com.safeqr.scanner.ui.screens.HistoryScreen
import com.safeqr.scanner.ui.screens.QrGeneratorScreen
import com.safeqr.scanner.ui.screens.SandboxBrowserScreen
import com.safeqr.scanner.ui.screens.ScannerScreen
import com.safeqr.scanner.ui.screens.SettingsScreen
import com.safeqr.scanner.ui.screens.SplashScreen
import com.safeqr.scanner.ui.screens.VaultScreen
import com.safeqr.scanner.viewmodel.ScannerViewModel
import com.safeqr.scanner.ui.theme.DarkBackground
import com.safeqr.scanner.ui.theme.DarkSurface
import com.safeqr.scanner.ui.theme.GlassWhite
import com.safeqr.scanner.ui.theme.NeonCyan
import com.safeqr.scanner.ui.theme.NeonCyanGlow
import com.safeqr.scanner.ui.theme.TextSecondary

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Login : Screen("login", "Login", Icons.Default.QrCodeScanner)
    object Register : Screen("register", "Register", Icons.Default.QrCodeScanner)
    object Splash : Screen("splash", "Splash", Icons.Default.QrCodeScanner)
    object Scan : Screen("scan", "Scan", Icons.Default.QrCodeScanner)
    object History : Screen("history?filter={filter}", "History", Icons.AutoMirrored.Filled.List) {
        fun createRoute(filter: String? = null) = if (filter != null) "history?filter=$filter" else "history"
    }
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Generate : Screen("generate", "Generate", Icons.Outlined.QrCode)
    object Vault : Screen("vault", "Vault", Icons.Default.Lock)
}

/**
 * Main navigation composable with animated bottom nav bar.
 *
 * @param externalUrl An optional URL received from an external intent (link click or share).
 *                    When non-null, the ScannerScreen will auto-analyze this URL on launch.
 */
@Composable
fun SafeQRNavigation(
    externalUrl: String? = null,
    forceScanRoute: Boolean = false,
    viewModel: ScannerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    qrViewModel: com.safeqr.scanner.viewmodel.QrViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Scan, Screen.Generate, Screen.History, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val shouldShowBottomBar = currentRoute != Screen.Splash.route && currentRoute != Screen.Login.route && currentRoute?.startsWith("sandbox") != true

    // Shimmer effect removed for cleaner look

    val context = androidx.compose.ui.platform.LocalContext.current
    val initialRoute = remember {
        val currentUserId = com.safeqr.scanner.data.PreferencesManager.getCurrentUserId(context)
        if (currentUserId != null) Screen.Scan.route else Screen.Login.route
    }

    var showSplashOverlay by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = DarkBackground,
            bottomBar = {
                if (shouldShowBottomBar) {
            Column {
// Removed neon top border shimmer for a cleaner, professional look

                // ── Glassmorphism nav bar ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    DarkSurface.copy(alpha = 0.95f),
                                    DarkBackground.copy(alpha = 0.98f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    screens.forEach { screen ->
                        val isSelected = currentRoute == screen.route

                        // Animated scale
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.1f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "scale_${screen.route}"
                        )

                        // Animated icon color
                        val iconColor by animateColorAsState(
                            targetValue = if (isSelected) NeonCyan else TextSecondary,
                            animationSpec = tween(300),
                            label = "color_${screen.route}"
                        )

                        // Animated label alpha
                        val labelAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.5f,
                            animationSpec = tween(300),
                            label = "labelAlpha_${screen.route}"
                        )

                        // Animated pill background alpha
                        val pillAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = tween(350),
                            label = "pillAlpha_${screen.route}"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        val destination = if (screen == Screen.History) Screen.History.createRoute(null) else screen.route
                                        navController.navigate(destination) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .scale(scale)
                        ) {
                            // Icon with animated pill background
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50)) // Fully rounded pill shape
                                    .background(
                                        NeonCyanGlow.copy(alpha = pillAlpha * 0.15f)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = iconColor.copy(alpha = labelAlpha),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Visible
                            )
                        }
                    }
                }
            }
            } // end if !isSandboxRoute
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (forceScanRoute) Screen.Scan.route else initialRoute,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(400)) + slideInHorizontally { it / 5 }
            },
            exitTransition = {
                fadeOut(animationSpec = tween(400)) + slideOutHorizontally { -it / 5 }
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(400)) + slideInHorizontally { -it / 5 }
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(400)) + slideOutHorizontally { it / 5 }
            }
        ) {
            composable(Screen.Login.route) {
                val authViewModel: com.safeqr.scanner.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        qrViewModel.refreshUser()
                        navController.navigate(Screen.Scan.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Splash.route) {
                val context = androidx.compose.ui.platform.LocalContext.current
                SplashScreen(
                    onSplashFinished = {
                        val currentUserId = com.safeqr.scanner.data.PreferencesManager.getCurrentUserId(context)
                        val targetRoute = if (currentUserId != null) Screen.Scan.route else Screen.Login.route
                        navController.navigate(targetRoute) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Scan.route) {
                ScannerScreen(
                    externalUrl = externalUrl,
                    onNavigateToSandbox = { url ->
                        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                        navController.navigate("sandbox/$encoded")
                    },
                    onNavigateToHistory = { filter ->
                        navController.navigate(Screen.History.createRoute(filter))
                    }
                )
            }
            composable(
                route = Screen.History.route,
                arguments = listOf(navArgument("filter") { type = NavType.StringType; nullable = true })
            ) { backStackEntry ->
                val filter = backStackEntry.arguments?.getString("filter")
                HistoryScreen(
                    initialFilter = filter,
                    onNavigateToSandbox = { url ->
                        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                        navController.navigate("sandbox/$encoded")
                    },
                    onBack = {
                        navController.navigate(Screen.Scan.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Generate.route) { 
                QrGeneratorScreen(
                    viewModel = viewModel,
                    onBack = {
                        navController.navigate(Screen.Scan.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ) 
            }
            composable(Screen.Settings.route) { 
                SettingsScreen(
                    qrViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToVault = {
                        navController.navigate(Screen.Vault.route)
                    },
                    onNavigateToSandbox = {
                        val encoded = java.net.URLEncoder.encode("https://google.com", "UTF-8")
                        navController.navigate("sandbox/$encoded")
                    },
                    onBack = {
                        navController.navigate(Screen.Scan.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ) 
            }
            composable(Screen.Vault.route) {
                VaultScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = "sandbox/{url}",
                arguments = listOf(navArgument("url") { type = NavType.StringType })
            ) { backStackEntry ->
                val url = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("url") ?: "",
                    "UTF-8"
                )
                SandboxBrowserScreen(
                    url = url,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        }
        
        // Overlay the splash screen on top of EVERYTHING
        androidx.compose.animation.AnimatedVisibility(
            visible = showSplashOverlay,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(500)),
            modifier = Modifier.fillMaxSize()
        ) {
            com.safeqr.scanner.ui.screens.SplashScreen(
                onSplashFinished = { showSplashOverlay = false }
            )
        }
    }
}
