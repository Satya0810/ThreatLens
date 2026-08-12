package com.safeqr.scanner.ui.screens

import com.safeqr.scanner.ui.screens.qrstudio.DateTimePickerButton

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import com.safeqr.scanner.ui.screens.qrstudio.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeqr.scanner.data.model.*
import com.safeqr.scanner.ui.components.*
import com.safeqr.scanner.ui.theme.*
import com.safeqr.scanner.viewmodel.EventViewModel
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════
// EVENT MANAGER — self-contained event management UI embedded as a tab
// ═══════════════════════════════════════════════════════════════════════════

private enum class EventView { HUB, DETAIL }
private enum class DetailTab { TICKETS, LOGS, ANALYTICS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventManagerScreen(eventVm: EventViewModel = viewModel()) {
    val events by eventVm.events.collectAsState()
    var currentView by remember { mutableStateOf(EventView.HUB) }
    var selectedEventId by remember { mutableStateOf<String?>(null) }
    var showBuilder by remember { mutableStateOf(false) }

    // Navigate between views
    when (currentView) {
        EventView.HUB -> EventsHub(
            events = events,
            onEventClick = { eventId ->
                selectedEventId = eventId
                currentView = EventView.DETAIL
            },
            onNewEvent = { showBuilder = true },
            eventVm = eventVm
        )
        EventView.DETAIL -> {
            val event = events.find { it.eventId == selectedEventId }
            if (event != null) {
                EventDetailScreen(
                    event = event,
                    eventVm = eventVm,
                    onBack = { currentView = EventView.HUB }
                )
            } else {
                currentView = EventView.HUB
            }
        }
    }

    // Event Builder Bottom Sheet
    if (showBuilder) {
        EventBuilderSheet(
            eventVm = eventVm,
            onDismiss = { showBuilder = false }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// EVENTS HUB — grid of all events with live stats
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun EventsHub(
    events: List<EventEntity>,
    onEventClick: (String) -> Unit,
    onNewEvent: () -> Unit,
    eventVm: EventViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (events.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎪", fontSize = 52.sp)
                Spacer(Modifier.height(16.dp))
                Text("No Events Yet", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create your first event to start\nmanaging tickets & attendance",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onNewEvent,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Outlined.Add, null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create Event", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Stats summary bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkCard)
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${events.size}", color = NeonCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Events", color = TextSecondary, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${events.count { it.isActive }}", color = SafeGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Active", color = TextSecondary, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${events.count { !it.isActive }}", color = CautionAmber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Inactive", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                items(events, key = { it.eventId }) { event ->
                    EventCard(
                        event = event,
                        eventVm = eventVm,
                        onClick = { onEventClick(event.eventId) }
                    )
                }
            }
        }

        // FAB
        if (events.isNotEmpty()) {
            FloatingActionButton(
                onClick = onNewEvent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = NeonCyan,
                contentColor = DarkBackground,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.Add, "New Event")
            }
        }
    }
}

// ── Event Card ──────────────────────────────────────────────────────────────
@Composable
private fun EventCard(
    event: EventEntity,
    eventVm: EventViewModel,
    onClick: () -> Unit
) {
    val tickets by eventVm.getTicketsFlow(event.eventId).collectAsState(initial = emptyList())
    val checkedIn = tickets.count { it.currentStatus == TicketStatus.CHECKED_IN }
    val bannerColor = Color(event.bannerColor.toInt())
    val formatter = remember { java.text.SimpleDateFormat("MMM dd · HH:mm", java.util.Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkCard)
            .border(1.dp, bannerColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            // Banner gradient
            EventColorBanner(
                bannerColor = event.bannerColor,
                modifier = Modifier.height(70.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            event.name,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (event.venue.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📍", fontSize = 11.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    event.venue,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    // Status indicator
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (event.isActive) SafeGreen.copy(alpha = 0.15f) else MaliciousRed.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (event.isActive) "LIVE" else "OFF",
                            color = if (event.isActive) SafeGreen else MaliciousRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎟️", fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("${tickets.size} tickets", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("$checkedIn checked in", color = SafeGreen, fontSize = 11.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (event.startTime != null) {
                        Text(
                            formatter.format(java.util.Date(event.startTime)),
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (event.capacity > 0) {
                        Spacer(Modifier.height(4.dp))
                        CapacityBar(
                            current = tickets.size,
                            total = event.capacity,
                            modifier = Modifier.width(100.dp),
                            showLabel = false
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// EVENT BUILDER BOTTOM SHEET
// ══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventBuilderSheet(
    eventVm: EventViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf<Long?>(null) }
    var endTime by remember { mutableStateOf<Long?>(null) }
    var capacity by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(EventBannerColors.BLUE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        contentColor = TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Create Event", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
            Spacer(Modifier.height(4.dp))
            Text("Set up your event details", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Event Name *") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan)
            )
            Spacer(Modifier.height(12.dp))

            // Venue
            OutlinedTextField(
                value = venue,
                onValueChange = { venue = it },
                label = { Text("Venue / Location") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            // Date/Time pickers
            SectionHeader("SCHEDULE")
            DateTimePickerButton("Start Time:", startTime, { startTime = it }, context)
            Spacer(Modifier.height(8.dp))
            DateTimePickerButton("End Time:", endTime, { endTime = it }, context)
            Spacer(Modifier.height(16.dp))

            // Capacity
            OutlinedTextField(
                value = capacity,
                onValueChange = { capacity = it },
                label = { Text("Capacity (0 = Unlimited)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            // Color picker
            SectionHeader("EVENT COLOUR")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(EventBannerColors.all.size) { index ->
                    val color = EventBannerColors.all[index]
                    val label = EventBannerColors.labels[index]
                    val sel = selectedColor == color
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { selectedColor = color }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(color.toInt()))
                                .then(
                                    if (sel) Modifier.border(3.dp, Color.White, CircleShape)
                                    else Modifier.border(1.dp, GlassBorder, CircleShape)
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = if (sel) NeonCyan else TextSecondary, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // Save button
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        eventVm.createEvent(
                            name = name,
                            description = description,
                            venue = venue,
                            startTime = startTime,
                            endTime = endTime,
                            capacity = capacity.toIntOrNull() ?: 0,
                            bannerColor = selectedColor
                        )
                        Toast.makeText(context, "Event created!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Please enter an event name", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("Save Event", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// EVENT DETAIL SCREEN — drill-down with tabs
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun EventDetailScreen(
    event: EventEntity,
    eventVm: EventViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(DetailTab.TICKETS) }
    val tickets by eventVm.getTicketsFlow(event.eventId).collectAsState(initial = emptyList())
    val logs by eventVm.getLogsFlow(event.eventId).collectAsState(initial = emptyList())
    val bannerColor = Color(event.bannerColor.toInt())
    val formatter = remember { java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()) }
    val checkedIn = tickets.count { it.currentStatus == TicketStatus.CHECKED_IN }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header with banner ──────────────────────────────────────────
        EventColorBanner(
            bannerColor = event.bannerColor,
            modifier = Modifier.height(110.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Back", tint = TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(event.name, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (event.venue.isNotBlank()) {
                            Text("📍 ${event.venue}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (event.isActive) SafeGreen.copy(alpha = 0.15f) else MaliciousRed.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (event.isActive) "LIVE" else "INACTIVE",
                            color = if (event.isActive) SafeGreen else MaliciousRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Time + capacity row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (event.startTime != null) {
                            Text("🕐 ${formatter.format(java.util.Date(event.startTime))}", color = NeonCyan, fontSize = 12.sp)
                        }
                        Text("🎟️ ${tickets.size} tickets · $checkedIn inside", color = TextSecondary, fontSize = 11.sp)
                    }
                    if (event.capacity > 0) {
                        CapacityBar(
                            current = tickets.size,
                            total = event.capacity,
                            modifier = Modifier.width(120.dp)
                        )
                    }
                }
            }
        }

        // ── Tab row ─────────────────────────────────────────────────────
        val detailTabs = listOf(
            DetailTab.TICKETS to "🎟️ Tickets",
            DetailTab.LOGS to "📋 Logs",
            DetailTab.ANALYTICS to "📊 Analytics",
            DetailTab.SETTINGS to "⚙️ Settings"
        )
        ScrollableTabRow(
            selectedTabIndex = detailTabs.indexOfFirst { it.first == activeTab },
            containerColor = Color.Transparent,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                val index = detailTabs.indexOfFirst { it.first == activeTab }
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                    color = bannerColor,
                    height = 3.dp
                )
            },
            divider = { Divider(color = GlassBorder) }
        ) {
            detailTabs.forEach { (tab, label) ->
                val sel = activeTab == tab
                Tab(
                    selected = sel,
                    onClick = { activeTab = tab },
                    text = {
                        Text(
                            label,
                            color = if (sel) bannerColor else TextSecondary,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // ── Tab Content ─────────────────────────────────────────────────
        when (activeTab) {
            DetailTab.TICKETS -> TicketsTab(event, tickets, eventVm)
            DetailTab.LOGS -> LogsTab(event, logs, eventVm)
            DetailTab.ANALYTICS -> AnalyticsTab(tickets, logs)
            DetailTab.SETTINGS -> SettingsTab(event, eventVm, onBack)
        }
    }
}

// ── TICKETS TAB ─────────────────────────────────────────────────────────────
@Composable
private fun TicketsTab(
    event: EventEntity,
    tickets: List<TicketEntity>,
    eventVm: EventViewModel
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showBulkDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = tickets.filter {
        searchQuery.isBlank() ||
        it.attendeeName.contains(searchQuery, ignoreCase = true) ||
        it.ticketId.contains(searchQuery, ignoreCase = true) ||
        it.ticketTier.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search + action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search attendees...", fontSize = 13.sp) },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(12.dp)
            )
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeonCyan.copy(alpha = 0.12f))
            ) { Icon(Icons.Outlined.PersonAdd, null, tint = NeonCyan) }
            IconButton(
                onClick = { showBulkDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryPurple.copy(alpha = 0.12f))
            ) { Icon(Icons.Outlined.Groups, null, tint = PrimaryPurple) }
        }

        // Tier summary chips
        val tiers = tickets.groupBy { it.ticketTier }
        if (tiers.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tiers.entries.toList()) { (tier, tierTickets) ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(GlassWhite)
                            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TierBadge(tier)
                        Text("${tierTickets.size}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎫", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (tickets.isEmpty()) "No tickets yet" else "No results for \"$searchQuery\"",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filtered, key = { it.ticketId }) { ticket ->
                    TicketRow(ticket, eventVm)
                }
            }
        }
    }

    // Add Single Ticket Dialog
    if (showAddDialog) {
        var tName by remember { mutableStateOf("") }
        var tTier by remember { mutableStateOf("General") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = DarkCard,
            title = { Text("Add Ticket", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = tName, onValueChange = { tName = it }, label = { Text("Attendee Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                    // Tier selector
                    SectionHeader("TICKET TIER")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tiers = listOf("General", "VIP", "Staff", "Premium")
                        items(tiers) { tier ->
                            val sel = tTier == tier
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) NeonCyan.copy(alpha = 0.15f) else DarkSurface)
                                    .border(1.dp, if (sel) NeonCyan else GlassBorder, RoundedCornerShape(10.dp))
                                    .clickable { tTier = tier }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text(tier, color = if (sel) NeonCyan else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        eventVm.generateTicket(
                            eventId = event.eventId,
                            maxAllowedScans = 1,
                            attendeeName = tName.ifBlank { "Guest" },
                            ticketTier = tTier
                        )
                        Toast.makeText(context, "Ticket created!", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) { Text("Create", color = DarkBackground, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    // Bulk Generate Dialog
    if (showBulkDialog) {
        var bTier by remember { mutableStateOf("General") }
        var bCount by remember { mutableStateOf("10") }
        AlertDialog(
            onDismissRequest = { showBulkDialog = false },
            containerColor = DarkCard,
            title = { Text("Bulk Generate", color = PrimaryPurple, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("TICKET TIER")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tiers = listOf("General", "VIP", "Staff", "Premium")
                        items(tiers) { tier ->
                            val sel = bTier == tier
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) PrimaryPurple.copy(alpha = 0.15f) else DarkSurface)
                                    .border(1.dp, if (sel) PrimaryPurple else GlassBorder, RoundedCornerShape(10.dp))
                                    .clickable { bTier = tier }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text(tier, color = if (sel) PrimaryPurple else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    OutlinedTextField(
                        value = bCount,
                        onValueChange = { bCount = it },
                        label = { Text("Count") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryPurple, focusedLabelColor = PrimaryPurple)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = bCount.toIntOrNull() ?: 0
                        if (count > 0) {
                            val ids = eventVm.generateBulkTickets(event.eventId, bTier, count)
                            Toast.makeText(context, "${ids.size} tickets created!", Toast.LENGTH_SHORT).show()
                            showBulkDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) { Text("Generate", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

// ── Ticket Row ──────────────────────────────────────────────────────────────
@Composable
private fun TicketRow(ticket: TicketEntity, eventVm: EventViewModel) {
    var showActions by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .clickable { showActions = !showActions }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    ticket.attendeeName.ifBlank { "Guest" },
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TierBadge(ticket.ticketTier)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                ticket.ticketId,
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        Spacer(Modifier.width(8.dp))
        StatusChip(ticket.currentStatus)
    }

    // Expandable actions
    AnimatedVisibility(visible = showActions) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (ticket.currentStatus != TicketStatus.REVOKED) {
                OutlinedButton(
                    onClick = { eventVm.revokeTicket(ticket.ticketId) },
                    border = BorderStroke(1.dp, MaliciousRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) { Text("Revoke", color = MaliciousRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            OutlinedButton(
                onClick = { eventVm.deleteTicket(ticket.ticketId) },
                border = BorderStroke(1.dp, TextSecondary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("Delete", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ── LOGS TAB ────────────────────────────────────────────────────────────────
@Composable
private fun LogsTab(
    event: EventEntity,
    logs: List<AttendanceLogEntity>,
    eventVm: EventViewModel
) {
    val context = LocalContext.current
    val formatter = remember { java.text.SimpleDateFormat("MMM dd · HH:mm:ss", java.util.Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Export bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${logs.size} records", color = TextSecondary, fontSize = 13.sp)
            OutlinedButton(
                onClick = { eventVm.exportAttendanceCsv(event.eventId, event.name) },
                border = BorderStroke(1.dp, NeonCyan),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(Icons.Outlined.FileDownload, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export CSV", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No attendance logs yet", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logs) { log ->
                    val isEntry = log.actionType == ActionType.ENTRY
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCard)
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Entry/Exit indicator
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isEntry) SafeGreen.copy(alpha = 0.15f) else CautionAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isEntry) "→" else "←", color = if (isEntry) SafeGreen else CautionAmber, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isEntry) "ENTRY" else "EXIT",
                                color = if (isEntry) SafeGreen else CautionAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(log.ticketId, color = TextSecondary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                formatter.format(java.util.Date(log.timestamp)),
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                            Text("by ${log.scannedByUserId}", color = TextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── ANALYTICS TAB ───────────────────────────────────────────────────────────
@Composable
private fun AnalyticsTab(
    tickets: List<TicketEntity>,
    logs: List<AttendanceLogEntity>
) {
    val entryLogs = logs.filter { it.actionType == ActionType.ENTRY }
    val exitLogs = logs.filter { it.actionType == ActionType.EXIT }
    val checkedIn = tickets.count { it.currentStatus == TicketStatus.CHECKED_IN }
    val pending = tickets.count { it.currentStatus == TicketStatus.PENDING }
    val checkedOut = tickets.count { it.currentStatus == TicketStatus.CHECKED_OUT }
    val revoked = tickets.count { it.currentStatus == TicketStatus.REVOKED }
    val tiers = tickets.groupBy { it.ticketTier }
    val entryTicketIds = entryLogs.map { it.ticketId }.toSet()
    val noShows = tickets.filter { it.ticketId !in entryTicketIds && it.currentStatus == TicketStatus.PENDING }.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overview stats grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard("Currently\nInside", "$checkedIn", SafeGreen, Modifier.weight(1f))
            StatCard("Total\nEntries", "${entryLogs.size}", NeonCyan, Modifier.weight(1f))
            StatCard("Total\nExits", "${exitLogs.size}", CautionAmber, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard("Pending", "$pending", TextSecondary, Modifier.weight(1f))
            StatCard("No-Shows", "$noShows", MaliciousRed, Modifier.weight(1f))
            StatCard("Revoked", "$revoked", MaliciousRed.copy(alpha = 0.7f), Modifier.weight(1f))
        }

        // Check-in rate donut
        SectionHeader("CHECK-IN RATE")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (tickets.isEmpty()) {
                Text("No data yet", color = TextSecondary)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Donut chart
                    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val total = tickets.size.toFloat()
                            val checkAngle = (checkedIn / total) * 360f
                            val pendAngle = (pending / total) * 360f
                            val outAngle = (checkedOut / total) * 360f
                            val revAngle = (revoked / total) * 360f

                            drawArc(SafeGreen, -90f, checkAngle, true)
                            drawArc(CautionAmber, -90f + checkAngle, pendAngle, true)
                            drawArc(NeonCyan, -90f + checkAngle + pendAngle, outAngle, true)
                            drawArc(MaliciousRed, -90f + checkAngle + pendAngle + outAngle, revAngle, true)
                            drawCircle(DarkCard, radius = size.minDimension / 3f)
                        }
                        val rate = if (tickets.isNotEmpty()) (checkedIn * 100 / tickets.size) else 0
                        Text("$rate%", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    // Legend
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LegendItem("Checked In ($checkedIn)", SafeGreen)
                        LegendItem("Pending ($pending)", CautionAmber)
                        LegendItem("Checked Out ($checkedOut)", NeonCyan)
                        LegendItem("Revoked ($revoked)", MaliciousRed)
                    }
                }
            }
        }

        // Tier breakdown
        if (tiers.isNotEmpty()) {
            SectionHeader("TIER BREAKDOWN")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiers.forEach { (tier, tierTickets) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TierBadge(tier)
                            CapacityBar(
                                current = tierTickets.size,
                                total = tickets.size,
                                modifier = Modifier.weight(1f),
                                showLabel = false
                            )
                            Text("${tierTickets.size}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Peak check-in time
        if (entryLogs.isNotEmpty()) {
            SectionHeader("PEAK CHECK-IN")
            val hourFormatter = java.text.SimpleDateFormat("HH:00", java.util.Locale.getDefault())
            val hourCounts = entryLogs.groupBy {
                hourFormatter.format(java.util.Date(it.timestamp))
            }.mapValues { it.value.size }
            val peakHour = hourCounts.maxByOrNull { it.value }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Peak Hour", color = TextSecondary, fontSize = 12.sp)
                        Text(peakHour?.key ?: "-", color = NeonCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Check-ins", color = TextSecondary, fontSize = 12.sp)
                        Text("${peakHour?.value ?: 0}", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(label, color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextPrimary, fontSize = 11.sp)
    }
}

// ── SETTINGS TAB ────────────────────────────────────────────────────────────
@Composable
private fun SettingsTab(
    event: EventEntity,
    eventVm: EventViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editedCapacity by remember { mutableStateOf(event.capacity.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toggle Active
        SectionHeader("EVENT STATUS")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Event Active", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Toggle event on/off", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = event.isActive,
                    onCheckedChange = { eventVm.toggleEventActive(event.eventId) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SafeGreen
                    )
                )
            }
        }

        // Edit Capacity
        SectionHeader("CAPACITY")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = editedCapacity,
                onValueChange = { editedCapacity = it },
                label = { Text("Max Capacity (0 = Unlimited)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                singleLine = true
            )
            Button(
                onClick = {
                    val newCapacity = editedCapacity.toIntOrNull() ?: 0
                    eventVm.updateEvent(event.copy(capacity = newCapacity))
                    Toast.makeText(context, "Capacity updated!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) { Text("Save", color = DarkBackground, fontWeight = FontWeight.Bold) }
        }

        // Event Info
        SectionHeader("EVENT INFO")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            val fmt = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ID: ${event.eventId}", color = TextSecondary, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Created: ${fmt.format(java.util.Date(event.createdAt))}", color = TextSecondary, fontSize = 12.sp)
                if (event.description.isNotBlank()) {
                    Text("Description: ${event.description}", color = TextPrimary, fontSize = 13.sp)
                }
            }
        }

        // Danger Zone
        SectionHeader("DANGER ZONE")
        Button(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, MaliciousRed)
        ) {
            Icon(Icons.Outlined.Delete, null, tint = MaliciousRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete Event & All Tickets", color = MaliciousRed, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkCard,
            title = { Text("Delete Event?", color = MaliciousRed, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete \"${event.name}\" and all its tickets. This action cannot be undone.", color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        eventVm.deleteEvent(event.eventId)
                        showDeleteConfirm = false
                        onBack()
                        Toast.makeText(context, "Event deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaliciousRed)
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}
