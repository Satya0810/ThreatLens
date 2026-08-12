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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
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
    var searchDateRange by remember { mutableStateOf<androidx.compose.material3.DateRangePickerState?>(null) }
    var searchStartTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var searchEndTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var activeFilter by remember(initialFilter) { mutableStateOf(initialFilter ?: "All") }
    var activeTag by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf("Newest") } // Newest, Oldest, Highest Risk
    val context = LocalContext.current

    val allTags = remember(scanHistory) {
        scanHistory.flatMap { it.tags }.toSet().sorted()
    }

    val filteredHistory = remember(scanHistory, searchQuery, activeFilter, activeTag, searchDateRange, searchStartTime, searchEndTime, sortOption) {
        val hasDateFilter = searchDateRange?.selectedStartDateMillis != null
        val startLocal = if (hasDateFilter) {
            val startUtc = searchDateRange!!.selectedStartDateMillis!!
            val offset = java.util.TimeZone.getDefault().getOffset(startUtc).toLong()
            var s = startUtc - offset
            if (searchStartTime != null) {
                s += (searchStartTime!!.first * 3600000L) + (searchStartTime!!.second * 60000L)
            }
            s
        } else 0L

        val endLocal = if (hasDateFilter) {
            val startUtc = searchDateRange!!.selectedStartDateMillis!!
            val endUtc = searchDateRange!!.selectedEndDateMillis ?: startUtc
            val offset = java.util.TimeZone.getDefault().getOffset(startUtc).toLong()
            var e = endUtc - offset + 86400000L - 1L
            if (searchEndTime != null) {
                e = (endUtc - offset) + (searchEndTime!!.first * 3600000L) + (searchEndTime!!.second * 60000L)
            }
            e
        } else Long.MAX_VALUE

        scanHistory.filter { scan ->
            val matchesSearch = scan.domain?.contains(searchQuery, ignoreCase = true) == true ||
                    scan.rawContent.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (activeFilter) {
                "Favorites" -> scan.isFavorite
                "Safe" -> scan.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.SAFE
                "Caution" -> scan.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.CAUTION
                "Malicious" -> scan.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS
                "Adult" -> scan.isAdultContent
                "Payment" -> scan.isTransaction
                "All" -> true
                else -> scan.tags.contains(activeFilter)
            }
            val matchesTag = activeTag == null || scan.tags.contains(activeTag)
            val matchesDate = if (hasDateFilter) scan.timestamp in startLocal..endLocal else true
            matchesSearch && matchesFilter && matchesTag && matchesDate
        }.let { list ->
            when (sortOption) {
                "Newest" -> list.sortedByDescending { it.timestamp }
                "Oldest" -> list.sortedBy { it.timestamp }
                "Highest Risk" -> list.sortedBy { it.overallScore }
                else -> list
            }
        }
    }

    // Stats calculations
    val totalScans = scanHistory.size
    val safeScans = scanHistory.count { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.SAFE }
    val maliciousScans = scanHistory.count { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS || it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.CAUTION }

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
                    var filterExpanded by remember { mutableStateOf(false) }
                    val baseFilters = listOf("All", "Favorites", "Safe", "Caution", "Malicious", "Adult", "Payment")
                    val filtersList = (baseFilters + allTags).distinct()
                    
                    if (activeFilter != "All") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(NeonCyan.copy(alpha = 0.15f))
                                .clickable { activeFilter = "All" }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "$activeFilter ✕", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Box {
                        IconButton(onClick = { filterExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (activeFilter != "All") NeonCyan else TextSecondary
                            )
                        }
                        DropdownMenu(
                            expanded = filterExpanded,
                            onDismissRequest = { filterExpanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            filtersList.forEach { filter ->
                                val color = when (filter) {
                                    "Favorites" -> NeonCyan
                                    "Safe" -> SafeGreen
                                    "Caution" -> CautionAmber
                                    "Malicious", "Adult" -> MaliciousRed
                                    "Payment" -> CautionAmber
                                    "All" -> TextPrimary
                                    else -> PrimaryPurple
                                }
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            filter, 
                                            color = color,
                                            fontWeight = if (activeFilter == filter) FontWeight.Bold else FontWeight.Medium
                                        ) 
                                    },
                                    onClick = { activeFilter = filter; filterExpanded = false }
                                )
                            }
                        }
                    }

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
                        tint = if (searchDateRange?.selectedStartDateMillis != null) NeonCyan else TextSecondary
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
        if (searchDateRange?.selectedStartDateMillis != null) {
            val df = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val start = df.format(java.util.Date(searchDateRange!!.selectedStartDateMillis!!))
            val end = searchDateRange!!.selectedEndDateMillis?.let { df.format(java.util.Date(it)) } ?: start
            var dateLabel = if (start == end) start else "$start - $end"
            
            if (searchStartTime != null || searchEndTime != null) {
                val st = searchStartTime?.let { String.format("%02d:%02d", it.first, it.second) } ?: "00:00"
                val et = searchEndTime?.let { String.format("%02d:%02d", it.first, it.second) } ?: "23:59"
                dateLabel += " ($st - $et)"
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
                if (activeTag != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryPurple.copy(alpha = 0.15f))
                            .clickable { activeTag = null }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(text = "#$activeTag ✕", color = PrimaryPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .clickable { 
                            searchDateRange = null
                            searchStartTime = null
                            searchEndTime = null
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(text = "Date: $dateLabel ✕", color = NeonCyan, fontSize = 12.sp)
                }
            }
        } else if (activeTag != null) {
            // If date isn't set but we have an active tag, we still need a Row for it
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(PrimaryPurple.copy(alpha = 0.15f))
                        .clickable { activeTag = null }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(text = "#$activeTag ✕", color = PrimaryPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showDatePicker) {
            val dateRangeState = rememberDateRangePickerState(
                initialSelectedStartDateMillis = searchDateRange?.selectedStartDateMillis,
                initialSelectedEndDateMillis = searchDateRange?.selectedEndDateMillis
            )
            var showTimePickers by remember { mutableStateOf(searchStartTime != null) }
            var startHour by remember { mutableIntStateOf(searchStartTime?.first ?: 0) }
            var startMinute by remember { mutableIntStateOf(searchStartTime?.second ?: 0) }
            var endHour by remember { mutableIntStateOf(searchEndTime?.first ?: 23) }
            var endMinute by remember { mutableIntStateOf(searchEndTime?.second ?: 59) }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showDatePicker = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.85f)
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = DarkSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        DateRangePicker(
                            state = dateRangeState,
                            modifier = Modifier.weight(1f),
                            title = {
                                Text(
                                    "Select Date Range",
                                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
                                    color = NeonCyan,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(
                                containerColor = DarkSurface,
                                titleContentColor = NeonCyan,
                                headlineContentColor = TextPrimary,
                                selectedDayContainerColor = NeonCyan,
                                selectedDayContentColor = Color.Black,
                                dayContentColor = TextPrimary,
                                todayContentColor = NeonCyan,
                                todayDateBorderColor = NeonCyan,
                                dayInSelectionRangeContainerColor = NeonCyan.copy(alpha = 0.2f),
                                dayInSelectionRangeContentColor = TextPrimary,
                                selectedYearContainerColor = NeonCyan,
                                selectedYearContentColor = Color.Black,
                                yearContentColor = TextPrimary,
                                currentYearContentColor = NeonCyan
                            )
                        )
                        // Toggle Time Selection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTimePickers = !showTimePickers }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showTimePickers) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Time",
                                tint = NeonCyan
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Specify Time Range (Optional)", 
                                color = TextPrimary, 
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Time Selectors
                        AnimatedVisibility(visible = showTimePickers) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Start Time
                                Column {
                                    Text("Start Time", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    TimeSelector(
                                        hour = startHour, 
                                        minute = startMinute, 
                                        onHourChange = { startHour = it }, 
                                        onMinuteChange = { startMinute = it }
                                    )
                                }
                                // End Time
                                Column {
                                    Text("End Time", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    TimeSelector(
                                        hour = endHour, 
                                        minute = endMinute, 
                                        onHourChange = { endHour = it }, 
                                        onMinuteChange = { endMinute = it }
                                    )
                                }
                            }
                        }

                        // Footer with actions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showDatePicker = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    searchDateRange = dateRangeState
                                    if (showTimePickers) {
                                        searchStartTime = Pair(startHour, startMinute)
                                        searchEndTime = Pair(endHour, endMinute)
                                    } else {
                                        searchStartTime = null
                                        searchEndTime = null
                                    }
                                    showDatePicker = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Apply Filter", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }



        val availableTags = allTags.filter { it != activeTag }
        if (availableTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableTags.size) { index ->
                    val tag = availableTags[index]
                    val bgColor = Color.Transparent
                    val borderColor = GlassBorder
                    val textColor = TextSecondary

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                            .clickable {
                                activeTag = tag
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "#$tag",
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
                        key = { _, item -> item.rawContent }
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
                com.safeqr.scanner.utils.SmartRouter.openUrlSmartly(context, url)
            },
            onOpenInSandbox = { url ->
                viewModel.dismissSelected()
                onNavigateToSandbox(url)
            }
        )
    }
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

@Composable
fun TimeSelector(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Hour Dropdown
        var hourExpanded by remember { mutableStateOf(false) }
        Box {
            Text(
                text = String.format("%02d", hour),
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                    .clickable { hourExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            DropdownMenu(
                expanded = hourExpanded,
                onDismissRequest = { hourExpanded = false },
                modifier = Modifier.background(DarkSurface).heightIn(max = 200.dp)
            ) {
                (0..23).forEach { h ->
                    DropdownMenuItem(
                        text = { Text(String.format("%02d", h), color = TextPrimary) },
                        onClick = { onHourChange(h); hourExpanded = false }
                    )
                }
            }
        }
        
        Text(" : ", color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        
        // Minute Dropdown
        var minuteExpanded by remember { mutableStateOf(false) }
        Box {
            Text(
                text = String.format("%02d", minute),
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                    .clickable { minuteExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            DropdownMenu(
                expanded = minuteExpanded,
                onDismissRequest = { minuteExpanded = false },
                modifier = Modifier.background(DarkSurface).heightIn(max = 200.dp)
            ) {
                (0..59 step 5).forEach { m ->
                    DropdownMenuItem(
                        text = { Text(String.format("%02d", m), color = TextPrimary) },
                        onClick = { onMinuteChange(m); minuteExpanded = false }
                    )
                }
            }
        }
    }
}
