package com.safeqr.scanner.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeqr.scanner.ui.components.HistoryItem
import com.safeqr.scanner.ui.theme.*
import com.safeqr.scanner.viewmodel.HistoryViewModel

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun HistoryScreen(
    initialFilter: String? = null,
    viewModel: HistoryViewModel = viewModel(),
    onNavigateToSandbox: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val scanHistory by viewModel.scanHistory.collectAsState()
    val selectedScan by viewModel.selectedScan.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var activeFilter by remember(initialFilter) { mutableStateOf(initialFilter ?: "All") }
    var sortOption by remember { mutableStateOf("Newest") } // Newest, Oldest, Highest Risk
    var activeTab by remember { mutableStateOf(0) }

    val filteredHistory = scanHistory.filter { scan ->
        val matchesSearch = scan.domain?.contains(searchQuery, ignoreCase = true) == true ||
                scan.rawContent.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (activeFilter) {
            "Safe" -> scan.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.SAFE
            "Caution" -> scan.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.CAUTION
            "Malicious" -> scan.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS
            "Adult" -> scan.isAdultContent
            "Payment" -> scan.isTransaction
            else -> true
        }
        val matchesDate = if (searchDate != null) {
            val scanCalendar = java.util.Calendar.getInstance().apply { timeInMillis = scan.timestamp }
            val searchCalendar = java.util.Calendar.getInstance().apply { timeInMillis = searchDate!! }
            scanCalendar.get(java.util.Calendar.YEAR) == searchCalendar.get(java.util.Calendar.YEAR) &&
            scanCalendar.get(java.util.Calendar.DAY_OF_YEAR) == searchCalendar.get(java.util.Calendar.DAY_OF_YEAR)
        } else {
            true
        }
        matchesSearch && matchesFilter && matchesDate
    }.let { list ->
        when (sortOption) {
            "Newest" -> list.sortedByDescending { it.timestamp }
            "Oldest" -> list.sortedBy { it.timestamp }
            "Highest Risk" -> list.sortedBy { it.overallScore }
            else -> list
        }
    }

    // Stats calculations
    val totalScans = scanHistory.size
    val safeScans = scanHistory.count { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.SAFE }
    val maliciousScans = scanHistory.count { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS || it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.CAUTION }

    val context = LocalContext.current

    // Animated underline for header
    val underlineWidth by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "underline"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = DarkBackground,
            contentColor = NeonCyan,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = NeonCyan
                )
            }
        ) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                Text("My Scans", modifier = Modifier.padding(16.dp), color = if (activeTab == 0) NeonCyan else TextSecondary, fontWeight = FontWeight.Bold)
            }
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                Text("AI Neural Core", modifier = Modifier.padding(16.dp), color = if (activeTab == 1) NeonCyan else TextSecondary, fontWeight = FontWeight.Bold)
            }
        }
        
        if (activeTab == 1) {
            NeuralCoreScreen()
        } else {
        Spacer(modifier = Modifier.height(8.dp))
        // ── Header Row with animated underline ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Scan History",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Animated underline
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(underlineWidth * 0.2f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(NeonCyan)
                    )
                }

                // Scan count badge
                if (scanHistory.isNotEmpty()) {
                    var sortExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { sortExpanded = true }) {
                            @Suppress("DEPRECATION")
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = TextSecondary
                            )
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            DropdownMenuItem(text = { Text("Newest", color = TextPrimary) }, onClick = { sortOption = "Newest"; sortExpanded = false })
                            DropdownMenuItem(text = { Text("Oldest", color = TextPrimary) }, onClick = { sortOption = "Oldest"; sortExpanded = false })
                            DropdownMenuItem(text = { Text("Highest Risk", color = TextPrimary) }, onClick = { sortOption = "Highest Risk"; sortExpanded = false })
                        }
                    }

                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = MaliciousRed
                        )
                    }
                }
            }
        }

        // ── Stats Bar ──
        if (scanHistory.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "Total Scans", value = "$totalScans", color = NeonCyan)
                StatItem(label = "Safe", value = "$safeScans", color = SafeGreen)
                StatItem(label = "Threats", value = "$maliciousScans", color = MaliciousRed)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Search Bar ──
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search scans...", color = TextSecondary.copy(alpha = 0.6f)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (searchQuery.isNotEmpty()) NeonCyan else TextSecondary
                )
            },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Filter by Date",
                        tint = if (searchDate != null) NeonCyan else TextSecondary
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = GlassBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = NeonCyan,
                focusedLeadingIconColor = NeonCyan,
                unfocusedLeadingIconColor = TextSecondary,
                focusedTrailingIconColor = NeonCyan,
                unfocusedTrailingIconColor = TextSecondary
            ),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Show a little chip to clear the date filter if active
        if (searchDate != null) {
            val df = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .clickable { searchDate = null }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(text = "Date: ${df.format(java.util.Date(searchDate!!))} ✕", color = NeonCyan, fontSize = 12.sp)
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = searchDate ?: System.currentTimeMillis())
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        searchDate = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }) { Text("OK", color = NeonCyan) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextSecondary) }
                },
                colors = DatePickerDefaults.colors(containerColor = DarkSurface)
            ) {
                DatePicker(state = datePickerState, colors = DatePickerDefaults.colors(titleContentColor = NeonCyan, headlineContentColor = TextPrimary, selectedDayContainerColor = NeonCyan))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Filter Chips ──
        val filters = listOf("All", "Safe", "Caution", "Malicious", "Adult", "Payment")
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters.size) { index ->
                val filter = filters[index]
                val isSelected = activeFilter == filter
                val chipColor = when (filter) {
                    "Safe" -> SafeGreen
                    "Caution" -> CautionAmber
                    "Malicious" -> MaliciousRed
                    "Adult" -> MaliciousRed
                    "Payment" -> CautionAmber
                    else -> NeonCyan
                }
                val bgColor = if (isSelected) chipColor.copy(alpha = 0.15f) else Color.Transparent
                val borderColor = if (isSelected) chipColor.copy(alpha = 0.5f) else GlassBorder
                val textColor = if (isSelected) chipColor else TextSecondary

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                        .clickable { activeFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = filter,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Content ──
        AnimatedContent(
            targetState = filteredHistory.isEmpty(),
            transitionSpec = {
                fadeIn(tween(400)) togetherWith fadeOut(tween(400))
            },
            label = "historyContent",
            modifier = Modifier.weight(1f)
        ) { isEmpty ->
            if (isEmpty) {
                // ── Empty State with radar effect ──
                EmptyHistoryState()
            } else {
                // ── History list ──
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = filteredHistory,
                        key = { _, item -> item.timestamp }
                    ) { _, scanResult ->
                        val dismissState = rememberDismissState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == DismissValue.DismissedToStart) {
                                    viewModel.deleteScan(scanResult)
                                    true
                                } else {
                                    false
                                }
                            }
                        )
                        SwipeToDismiss(
                            state = dismissState,
                            modifier = Modifier.animateItemPlacement(),
                            background = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(MaliciousRed.copy(alpha = 0.8f)),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White,
                                        modifier = Modifier.padding(end = 24.dp)
                                    )
                                }
                            },
                            directions = setOf(DismissDirection.EndToStart),
                            dismissContent = {
                                HistoryItem(
                                    scanResult = scanResult,
                                    onClick = { viewModel.selectScan(scanResult) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Clear History Dialog ──
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaliciousRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Clear History",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to clear all scan history? This action cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed)
                ) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Result Bottom Sheet ──
    if (selectedScan != null) {
        ResultBottomSheet(
            scanResult = selectedScan!!,
            onDismiss = { viewModel.dismissSelected() },
            onOpenUrl = { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            },
            onOpenInSandbox = { url ->
                viewModel.dismissSelected()
                onNavigateToSandbox(url)
            }
        )
    }
    } // End of else block for activeTab == 0
}


// ═══════════════════════════════════════════════════════════════════════════
//  Empty History State with radar animation
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyHistoryState() {
    val infiniteTransition = rememberInfiniteTransition(label = "empty")

    // Floating animation
    val floatY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    // Radar ring pulse
    val radarScale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar1"
    )
    val radarAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAlpha1"
    )

    val radarScale2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing, delayMillis = 800),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar2"
    )
    val radarAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing, delayMillis = 800),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAlpha2"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Radar rings
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(radarScale1)
                        .clip(CircleShape)
                        .background(NeonCyan.copy(alpha = radarAlpha1 * 0.15f))
                )
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(radarScale2)
                        .clip(CircleShape)
                        .background(NeonCyan.copy(alpha = radarAlpha2 * 0.15f))
                )
                // Floating icon
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "No History",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(64.dp)
                        .offset(y = floatY.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No scan history yet",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Scan a QR code to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
