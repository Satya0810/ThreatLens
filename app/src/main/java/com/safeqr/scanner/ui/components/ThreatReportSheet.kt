package com.safeqr.scanner.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.safeqr.scanner.data.model.ThreatReportEntity
import com.safeqr.scanner.data.model.ThreatReportType
import com.safeqr.scanner.ui.theme.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * A ModalBottomSheet for submitting crowdsourced threat reports.
 * Users can categorize the threat, describe it, attach a photo, and include GPS location.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatReportSheet(
    rawContent: String,
    onDismiss: () -> Unit,
    onSubmit: (ThreatReportEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(ThreatReportType.PHISHING) }
    var description by remember { mutableStateOf("") }
    var includeLocation by remember { mutableStateOf(false) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var locationName by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isLocationLoading by remember { mutableStateOf(false) }

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            includeLocation = true
            isLocationLoading = true
            try {
                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                val lastKnown = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                if (lastKnown != null) {
                    latitude = lastKnown.latitude
                    longitude = lastKnown.longitude
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lastKnown.latitude, lastKnown.longitude, 1)
                        locationName = addresses?.firstOrNull()?.let { addr ->
                            listOfNotNull(addr.locality, addr.adminArea, addr.countryCode).joinToString(", ")
                        } ?: "Lat: ${"%.4f".format(lastKnown.latitude)}, Lon: ${"%.4f".format(lastKnown.longitude)}"
                    } catch (e: Exception) {
                        locationName = "Lat: ${"%.4f".format(lastKnown.latitude)}, Lon: ${"%.4f".format(lastKnown.longitude)}"
                    }
                }
            } catch (e: SecurityException) {
                // Ignore silently
            }
            isLocationLoading = false
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaliciousRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Warning, null, tint = MaliciousRed, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Report Threat", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                    Text("Help protect the community", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Threat Type Selection
            Text("Threat Category", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThreatReportType.entries.forEach { type ->
                    val isSelected = selectedType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaliciousRed.copy(alpha = 0.2f)
                                else DarkCard
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaliciousRed.copy(alpha = 0.6f) else GlassBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedType = type }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "${type.icon} ${type.label}",
                            color = if (isSelected) MaliciousRed else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Description
            Text("Description", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Describe the threat you observed...", color = TextSecondary.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaliciousRed,
                    focusedLabelColor = MaliciousRed,
                    unfocusedBorderColor = GlassBorder,
                    cursorColor = NeonCyan
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Location Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        if (!includeLocation) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                includeLocation = true
                                isLocationLoading = true
                                try {
                                    val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                                    val lastKnown = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                                        ?: locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                    if (lastKnown != null) {
                                        latitude = lastKnown.latitude
                                        longitude = lastKnown.longitude
                                        try {
                                            val geocoder = Geocoder(context, Locale.getDefault())
                                            @Suppress("DEPRECATION")
                                            val addresses = geocoder.getFromLocation(lastKnown.latitude, lastKnown.longitude, 1)
                                            locationName = addresses?.firstOrNull()?.let { addr ->
                                                listOfNotNull(addr.locality, addr.adminArea, addr.countryCode).joinToString(", ")
                                            }
                                        } catch (_: Exception) {}
                                    }
                                } catch (_: SecurityException) {}
                                isLocationLoading = false
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        } else {
                            includeLocation = false
                            latitude = null
                            longitude = null
                            locationName = null
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn, null,
                        tint = if (includeLocation) NeonCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Include Location", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary)
                        if (isLocationLoading) {
                            Text("Fetching location...", fontSize = 11.sp, color = TextSecondary)
                        } else if (locationName != null) {
                            Text("📍 $locationName", fontSize = 11.sp, color = NeonCyan)
                        } else {
                            Text("Helps locate physical scam QR stickers", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
                Switch(
                    checked = includeLocation,
                    onCheckedChange = null, // handled by row click
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonCyan,
                        checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    isSubmitting = true
                    val userId = com.safeqr.scanner.data.PreferencesManager.getCurrentUserId(context) ?: "anonymous"
                    val report = ThreatReportEntity(
                        reportId = UUID.randomUUID().toString(),
                        rawContent = rawContent,
                        reportType = selectedType.name,
                        description = description.ifBlank { "${selectedType.label} detected" },
                        latitude = latitude,
                        longitude = longitude,
                        locationName = locationName,
                        photoUri = null,
                        reporterUserId = userId
                    )
                    onSubmit(report)
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaliciousRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Submitting...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Warning, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Submit Threat Report", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Your report will be shared anonymously with the ThreatLens community to help protect others.",
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
