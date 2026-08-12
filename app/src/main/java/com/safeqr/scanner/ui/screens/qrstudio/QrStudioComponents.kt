package com.safeqr.scanner.ui.screens.qrstudio
import com.safeqr.scanner.ui.screens.*
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.*
import com.safeqr.scanner.data.model.*
import com.safeqr.scanner.security.CertificateEngine
import com.safeqr.scanner.ui.theme.*
import com.safeqr.scanner.viewmodel.QrViewModel
import com.safeqr.scanner.viewmodel.ScannerViewModel
import com.safeqr.scanner.viewmodel.EventViewModel
import com.safeqr.scanner.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.safeqr.scanner.ui.components.CustomQrGenerator
import com.safeqr.scanner.ui.components.QrLogo
import com.safeqr.scanner.ui.components.QrColorTheme
import com.safeqr.scanner.ui.components.QrDotStyle
import com.safeqr.scanner.ui.components.QrEyeStyle
import com.safeqr.scanner.ui.components.QrBgStyle
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalClipboardManager

enum class GeneratorTab { GENERATE, EVENTS, CIRCULATE, DASHBOARD }
enum class GenStep { INPUT, PREVIEW, CERTIFYING, CERTIFIED }
@Composable
fun QrInputFields(type: QrType, f1: String, f2: String, f3: String, f4: String, f5: String, f6: String = "", isBulkMode: Boolean = false, onF1: (String)->Unit, onF2: (String)->Unit, onF3: (String)->Unit, onF4: (String)->Unit, onF5: (String)->Unit, onF6: (String)->Unit = {}) {
    val kbNext = KeyboardOptions(imeAction = ImeAction.Next)
    when (type) {
        QrType.DYNAMIC -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Campaign Title") }, leadingIcon = { Icon(Icons.Outlined.Title, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Target URL") }, leadingIcon = { Icon(Icons.Outlined.Link, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f6, onValueChange = onF6, label = { Text("Alternate URLs (comma separated) for Rotation") }, leadingIcon = { Icon(Icons.Outlined.AltRoute, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f5, onValueChange = onF5, label = { Text("Password Protection (Optional PIN)") }, leadingIcon = { Icon(Icons.Outlined.Password, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f3, onValueChange = onF3, label = { Text("Allowed Geo-Region (e.g. US, IN) - Optional") }, leadingIcon = { Icon(Icons.Outlined.Map, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = f4 == "true",
                    onCheckedChange = { if(it) onF4("true") else onF4("false") },
                    colors = CheckboxDefaults.colors(checkedColor = MaliciousRed)
                )
                Text("KILL SWITCH (Deactivate Link)", color = if (f4 == "true") MaliciousRed else TextSecondary)
            }
        }
        QrType.URL, QrType.FILE, QrType.APP, QrType.SOCIAL, QrType.SPOTIFY, QrType.YOUTUBE -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("URL / Link") }, leadingIcon = { Icon(Icons.Outlined.Link, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.TEXT -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Text Content") }, leadingIcon = { Icon(Icons.Outlined.Notes, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth().height(100.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.EMAIL -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Email Address") }, leadingIcon = { Icon(Icons.Outlined.Email, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Subject") }, leadingIcon = { Icon(Icons.Outlined.Subject, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f3, onValueChange = onF3, label = { Text("Body") }, leadingIcon = { Icon(Icons.Outlined.Notes, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth().height(100.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.PHONE, QrType.WHATSAPP -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Phone Number") }, leadingIcon = { Icon(Icons.Outlined.Phone, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.SMS -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Phone Number") }, leadingIcon = { Icon(Icons.Outlined.Phone, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Message") }, leadingIcon = { Icon(Icons.Outlined.Message, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth().height(100.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.WIFI -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Network Name (SSID)") }, leadingIcon = { Icon(Icons.Outlined.Wifi, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Password") }, leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.CONTACT -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Full Name") }, leadingIcon = { Icon(Icons.Outlined.Person, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Phone") }, leadingIcon = { Icon(Icons.Outlined.Phone, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f3, onValueChange = onF3, label = { Text("Email") }, leadingIcon = { Icon(Icons.Outlined.Email, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f4, onValueChange = onF4, label = { Text("Organization / Company") }, leadingIcon = { Icon(Icons.Outlined.Business, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.PAYMENT -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("UPI ID") }, leadingIcon = { Icon(Icons.Outlined.Payment, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Payee Name") }, leadingIcon = { Icon(Icons.Outlined.Person, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.PAYPAL -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("PayPal Username / Email") }, leadingIcon = { Icon(Icons.Outlined.Payment, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.TELEGRAM -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Telegram Username") }, leadingIcon = { Icon(Icons.Outlined.Send, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.LOCATION -> {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            
            OutlinedTextField(
                value = f3, // using f3 to hold the search query state so it persists
                onValueChange = onF3,
                label = { Text("Search by Address or Place") }, leadingIcon = { Icon(Icons.Outlined.Place, null, tint = NeonCyan) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan),
                trailingIcon = {
                    IconButton(onClick = {
                        if (f3.isNotBlank()) {
                            Toast.makeText(context, "Searching...", Toast.LENGTH_SHORT).show()
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                    @Suppress("DEPRECATION")
                                    val addresses = geocoder.getFromLocationName(f3, 1)
                                    val address = addresses?.firstOrNull()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        if (address != null && address.hasLatitude() && address.hasLongitude()) {
                                            onF1(address.latitude.toString())
                                            onF2(address.longitude.toString())
                                            Toast.makeText(context, "Coordinates found!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Location not found.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch(e: Exception) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Error searching location.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search", tint = NeonCyan)
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Latitude") }, leadingIcon = { Icon(Icons.Outlined.Explore, null, tint = NeonCyan) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Longitude") }, leadingIcon = { Icon(Icons.Outlined.Explore, null, tint = NeonCyan) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            }
            Spacer(Modifier.height(12.dp))
            
            // Get Current Location implementation
            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                    permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
                    // Try to fetch location again after granted
                    Toast.makeText(context, "Permission granted! Tap again to get location.", Toast.LENGTH_SHORT).show()
                }
            }

            OutlinedButton(
                onClick = {
                    val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    
                    if (hasFine || hasCoarse) {
                        try {
                            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                            val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                            
                            if (!isGpsEnabled && !isNetworkEnabled) {
                                Toast.makeText(context, "Please enable Location Services in Settings", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }

                            val location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                            
                            if (location != null) {
                                onF1(location.latitude.toString())
                                onF2(location.longitude.toString())
                                Toast.makeText(context, "Location fetched successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Fetching fresh location...", Toast.LENGTH_SHORT).show()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    locationManager.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, null, context.mainExecutor) { loc ->
                                        if (loc != null) {
                                            onF1(loc.latitude.toString())
                                            onF2(loc.longitude.toString())
                                        } else {
                                            Toast.makeText(context, "Could not determine location.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            Toast.makeText(context, "Permission error", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        permissionLauncher.launch(arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NeonCyan)
            ) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get Current Location", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        }
        QrType.EVENT -> {
            val context = LocalContext.current
            var startTimestamp by remember { mutableStateOf<Long?>(null) }
            var endTimestamp by remember { mutableStateOf<Long?>(null) }
            val calFmt = remember { java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.getDefault()) }

            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Event Name") }, leadingIcon = { Icon(Icons.Outlined.Event, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f5, onValueChange = onF5, label = { Text("Description") }, leadingIcon = { Icon(Icons.Outlined.Description, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f4, onValueChange = onF4, label = { Text("Location") }, leadingIcon = { Icon(Icons.Outlined.LocationOn, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(12.dp))
            Text("SCHEDULE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            DateTimePickerButton("Start Time:", startTimestamp, { ts ->
                startTimestamp = ts
                if (ts != null) onF2(calFmt.format(java.util.Date(ts)))
            }, context)
            Spacer(Modifier.height(8.dp))
            DateTimePickerButton("End Time:", endTimestamp, { ts ->
                endTimestamp = ts
                if (ts != null) onF3(calFmt.format(java.util.Date(ts)))
            }, context)
        }
        QrType.CRYPTO -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Wallet Address") }, leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Amount (Optional)") }, leadingIcon = { Icon(Icons.Outlined.Numbers, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.CREDENTIAL -> {
            OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Issuer Name") }, leadingIcon = { Icon(Icons.Outlined.Business, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Subject Name") }, leadingIcon = { Icon(Icons.Outlined.Person, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = f3, onValueChange = onF3, label = { Text("Credential Type") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
        }
        QrType.TICKET -> {
            if (!isBulkMode) {
                OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Ticket ID (e.g. TKT-1234)") }, leadingIcon = { Icon(Icons.Outlined.ConfirmationNumber, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = f2, onValueChange = onF2, label = { Text("Attendee Name") }, leadingIcon = { Icon(Icons.Outlined.Person, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = f3, onValueChange = onF3, label = { Text("Tier (e.g. VIP)") }, leadingIcon = { Icon(Icons.Outlined.Star, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            } else {
                OutlinedTextField(value = f1, onValueChange = onF1, label = { Text("Event Name") }, leadingIcon = { Icon(Icons.Outlined.Event, null, tint = NeonCyan) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            }
        }
    }
    // Auto-issue the ticket to the simulated cloud when TICKET is selected
    if (type == QrType.TICKET && f1.isNotBlank() && !isBulkMode) {
        LaunchedEffect(f1, f2, f3) {
            val ticket = com.safeqr.scanner.data.model.CloudEventTicket(
                ticketId = f1,
                eventId = "EVT-MOCK",
                attendeeId = "GUEST-001",
                attendeeName = f2.ifBlank { "Guest" },
                ticketTier = f3.ifBlank { "Standard" },
                signatureHash = "mock_hash"
            )
            com.safeqr.scanner.data.remote.CloudSyncManager.issueTicket(ticket)
        }
    }
}

@Composable
fun DateTimePickerButton(
    label: String,
    timestamp: Long?,
    onTimestampSelected: (Long?) -> Unit,
    context: android.content.Context
) {
    val formatter = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
    val text = if (timestamp != null) formatter.format(java.util.Date(timestamp)) else "Not Set"

    OutlinedButton(
        onClick = {
            val calendar = java.util.Calendar.getInstance()
            if (timestamp != null) calendar.timeInMillis = timestamp

            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    android.app.TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                            calendar.set(java.util.Calendar.MINUTE, minute)
                            onTimestampSelected(calendar.timeInMillis)
                        },
                        calendar.get(java.util.Calendar.HOUR_OF_DAY),
                        calendar.get(java.util.Calendar.MINUTE),
                        true
                    ).show()
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (timestamp != null) NeonCyan else GlassBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarToday, null, tint = if (timestamp != null) NeonCyan else TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, color = TextSecondary, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = if (timestamp != null) NeonCyan else TextSecondary.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (timestamp != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaliciousRed.copy(alpha = 0.15f))
                            .clickable { onTimestampSelected(null) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = MaliciousRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun buildQrPayload(type: QrType, f1: String, f2: String, f3: String, f4: String, f5: String, dynamicActiveShortCode: String? = null): String {
    return when (type) {
        QrType.URL -> if (f1.isNotBlank()) (if (!f1.startsWith("http")) "https://$f1" else f1) else ""
        QrType.DYNAMIC -> if (dynamicActiveShortCode != null) "https://tl.app/$dynamicActiveShortCode" else ""
        QrType.TEXT -> f1
        QrType.EMAIL -> if (f1.isNotBlank()) "mailto:$f1?subject=${Uri.encode(f2)}&body=${Uri.encode(f3)}" else ""
        QrType.PHONE -> if (f1.isNotBlank()) "tel:$f1" else ""
        QrType.WHATSAPP -> if (f1.isNotBlank()) "https://wa.me/${f1.replace("+", "").replace(" ", "")}" else ""
        QrType.TELEGRAM -> if (f1.isNotBlank()) "https://t.me/${f1.removePrefix("@")}" else ""
        QrType.PAYPAL -> if (f1.isNotBlank()) "https://paypal.me/$f1" else ""
        QrType.SMS -> if (f1.isNotBlank()) "smsto:$f1:${f2}" else ""
        QrType.WIFI -> if (f1.isNotBlank()) {
            val type = if (f2.isBlank()) "nopass" else "WPA"
            "WIFI:S:$f1;T:$type;P:$f2;;"
        } else ""
        QrType.CONTACT -> if (f1.isNotBlank()) "BEGIN:VCARD\nVERSION:3.0\nN:$f1\nFN:$f1\nTEL:$f2\nEMAIL:$f3\nORG:$f4\nEND:VCARD" else ""
        QrType.PAYMENT -> if (f1.isNotBlank()) "upi://pay?pa=$f1&pn=$f2&am=$f3&cu=INR" else ""
        QrType.LOCATION -> if (f1.isNotBlank() && f2.isNotBlank()) "geo:$f1,$f2" else ""
        QrType.EVENT -> if (f1.isNotBlank()) "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nSUMMARY:$f1\nDESCRIPTION:$f5\nDTSTART:$f2\nDTEND:$f3\nLOCATION:$f4\nEND:VEVENT\nEND:VCALENDAR" else ""
        QrType.CRYPTO -> if (f1.isNotBlank()) "bitcoin:$f1${if (f2.isNotBlank()) "?amount=$f2" else ""}" else ""
        QrType.TICKET -> if (f1.isNotBlank()) {
            val timeSlice = System.currentTimeMillis() / 30000
            val raw = "$f1:$timeSlice"
            val hash = java.security.MessageDigest.getInstance("SHA-256")
            val hashStr = hash.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(10)
            "tl-ticket://$f1/$hashStr"
        } else ""
        QrType.CREDENTIAL -> if (f1.isNotBlank()) "threatlensvc://issue?issuer=${android.net.Uri.encode(f1)}&subject=${android.net.Uri.encode(f2)}&type=${android.net.Uri.encode(f3)}" else ""
        QrType.FILE, QrType.APP, QrType.SOCIAL, QrType.SPOTIFY, QrType.YOUTUBE -> f1
    }
}

@Composable
fun SecurityOptionRow(dotColor: Color, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean)->Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = dotColor)
        )
    }
}

fun saveQrToGallery(context: Context, bitmap: Bitmap) {
    val contentValues = ContentValues().apply {
        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "ThreatLens_QR_${System.currentTimeMillis()}.png")
        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        context.contentResolver.openOutputStream(uri).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out!!)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }
        Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
    }
}

fun saveSvgToDownloads(context: Context, payload: String) {
    val hints = java.util.EnumMap<com.google.zxing.EncodeHintType, Any>(com.google.zxing.EncodeHintType::class.java)
    hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = "UTF-8"
    hints[com.google.zxing.EncodeHintType.MARGIN] = 2

    val qrCode = com.google.zxing.qrcode.encoder.Encoder.encode(payload, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H, hints)
    val matrix = qrCode.matrix
    val matrixSize = matrix.width
    val size = 800
    val moduleSize = size.toFloat() / matrixSize

    val sb = StringBuilder()
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $size $size\" width=\"100%\" height=\"100%\">\n")
    sb.append("  <rect width=\"$size\" height=\"$size\" fill=\"#ffffff\" />\n")
    sb.append("  <path d=\"")
    
    for (r in 0 until matrixSize) {
        for (c in 0 until matrixSize) {
            if (matrix.get(c, r).toInt() == 1) {
                val x = c * moduleSize
                val y = r * moduleSize
                sb.append("M$x ${y}h${moduleSize}v${moduleSize}h-${moduleSize}z ")
            }
        }
    }
    sb.append("\" fill=\"#000000\" />\n")
    sb.append("</svg>")

    val svgString = sb.toString()

    val contentValues = ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "ThreatLens_QR_${System.currentTimeMillis()}.svg")
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/svg+xml")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
    }

    val externalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val uri = context.contentResolver.insert(externalUri, contentValues)
    if (uri != null) {
        context.contentResolver.openOutputStream(uri).use { out ->
            out?.write(svgString.toByteArray())
        }
        Toast.makeText(context, "SVG Saved to Downloads", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Failed to save SVG", Toast.LENGTH_SHORT).show()
    }
}

fun shareQrBitmap(context: Context, bitmap: Bitmap) {
    try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = java.io.FileOutputStream("$cachePath/shared_qr.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        val imagePath = java.io.File(context.cacheDir, "images")
        val newFile = java.io.File(imagePath, "shared_qr.png")
        val contentUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", newFile)
        if (contentUri != null) {
            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share QR Code"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

