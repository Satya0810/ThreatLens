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

@Composable
fun DynamicQrTab(qrVm: QrViewModel, context: Context, eventVm: EventViewModel) {
    val currentUser by qrVm.currentUser.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (currentUser == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaliciousRed.copy(alpha = 0.08f))
                    .border(1.dp, MaliciousRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Lock, null, tint = MaliciousRed, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("You must create an account to use Event Circulation.", color = MaliciousRed, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            var eventName by remember { mutableStateOf("") }
            var guests by remember { mutableStateOf("") }
            
            // Scan Policy
            var scanPolicy by remember { mutableStateOf("Single-Use") }
            var customLimit by remember { mutableStateOf("1") }
            var activeFrom by remember { mutableStateOf<Long?>(null) }
            var activeUntil by remember { mutableStateOf<Long?>(null) }

            Text("CIRCULATE EVENT QRs", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            
            // Event Details Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                OutlinedTextField(value = eventName, onValueChange = { eventName = it }, label = { Text("Event / Business Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = guests, onValueChange = { guests = it }, label = { Text("Guest User IDs (Comma Separated Email)") }, modifier = Modifier.fillMaxWidth().height(80.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Scan Policy Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text("SCAN POLICY", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Single-Use", "Multi-Use", "Unlimited").forEach { policy ->
                        val sel = scanPolicy == policy
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) NeonCyan.copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.5.dp, if (sel) NeonCyan else GlassBorder, RoundedCornerShape(12.dp))
                                .clickable { scanPolicy = policy }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(policy, color = if (sel) NeonCyan else TextSecondary, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                if (scanPolicy == "Multi-Use") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = customLimit, onValueChange = { customLimit = it }, label = { Text("Scan Limit (e.g. 5)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, focusedLabelColor = NeonCyan))
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Schedule Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text("SCHEDULE (OPTIONAL)", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                DateTimePickerButton("Active From:", activeFrom, { activeFrom = it }, context)
                Spacer(Modifier.height(8.dp))
                DateTimePickerButton("Valid Till:", activeUntil, { activeUntil = it }, context)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (eventName.isNotBlank() && guests.isNotBlank()) {
                        val maxAllowed = when(scanPolicy) {
                            "Single-Use" -> 1
                            "Unlimited" -> -1
                            else -> customLimit.toIntOrNull() ?: 1
                        }
                        guests.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { guestId ->
                            val ticketId = eventVm.generateTicket(eventId = eventName, maxAllowedScans = maxAllowed, userId = guestId, activeFrom = activeFrom, activeUntil = activeUntil)
                            qrVm.shareQrToUser(currentUser!!.userId, guestId, eventName, ticketId)
                        }
                        Toast.makeText(context, "Dispatched Unique QRs to Guests!", Toast.LENGTH_LONG).show()
                        eventName = ""; guests = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Icon(Icons.Outlined.Send, null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dispatch Unique QRs", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
