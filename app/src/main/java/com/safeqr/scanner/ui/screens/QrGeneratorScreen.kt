package com.safeqr.scanner.ui.screens
import com.safeqr.scanner.ui.screens.qrstudio.*
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


import com.safeqr.scanner.ui.screens.qrstudio.GeneratorTab
import com.safeqr.scanner.ui.screens.qrstudio.GenStep
@Composable
fun QrGeneratorScreen(
    viewModel: ScannerViewModel,
    qrViewModel: QrViewModel = viewModel(),
    eventViewModel: EventViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(GeneratorTab.GENERATE) }
    val currentUser by qrViewModel.currentUser.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground).statusBarsPadding()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.QrCode, null, tint = NeonCyan, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text("QR Studio", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            }
            if (currentUser != null) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(NeonCyan.copy(alpha = 0.12f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("👤 ${currentUser!!.displayName.take(10)}", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        // Tabs
        LazyRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tabs = listOf(
                GeneratorTab.GENERATE to "⚡ Generate",
                GeneratorTab.EVENTS to "🎪 Events",
                GeneratorTab.CIRCULATE to "📡 Circulate",
                GeneratorTab.DASHBOARD to "📊 Dashboard"
            )
            items(tabs) { (tab, label) ->
                val sel = activeTab == tab
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (sel) NeonCyan.copy(alpha = 0.15f) else DarkCard)
                        .border(1.5.dp, if (sel) NeonCyan.copy(alpha = 0.6f) else GlassBorder, RoundedCornerShape(14.dp))
                        .clickable { activeTab = tab }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) { Text(label, color = if (sel) NeonCyan else TextSecondary, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (activeTab) {
            GeneratorTab.GENERATE -> GenerateTab(viewModel, qrViewModel, context, eventViewModel)
            GeneratorTab.EVENTS -> EventManagerScreen(eventVm = eventViewModel)
            GeneratorTab.CIRCULATE -> DynamicQrTab(qrViewModel, context, eventViewModel)
            GeneratorTab.DASHBOARD -> DashboardTab(qrViewModel, historyViewModel, eventViewModel)
        }
    }
}
