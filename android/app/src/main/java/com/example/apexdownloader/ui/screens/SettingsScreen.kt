package com.example.apexdownloader.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apexdownloader.theme.*
import com.example.apexdownloader.ui.main.MainScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainScreenViewModel,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val desktopUrl by viewModel.desktopServerUrl.collectAsState()
    var urlInput by remember { mutableStateOf(desktopUrl) }

    LaunchedEffect(desktopUrl) {
        urlInput = desktopUrl
    }

    val cobaltUrl by viewModel.cobaltInstanceUrl.collectAsState()
    var cobaltInput by remember { mutableStateOf(cobaltUrl) }

    LaunchedEffect(cobaltUrl) {
        cobaltInput = cobaltUrl
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "SETTINGS",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Card 1: Desktop Sync Connection
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BgPanel),
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Desktop Sync Connection",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = "Enter the local network IP and port of your PC running the HK Desktop App to download and convert YouTube videos at maximum speed.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("e.g. http://192.168.1.100:3982", color = TextTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = Color(0xFF0F131D),
                        unfocusedContainerColor = Color(0xFF0F131D)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                Button(
                    onClick = {
                        viewModel.saveDesktopServerUrl(urlInput.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Connection Settings", color = Color(0xFF1A0B02), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: Battery Optimization
        val context = LocalContext.current
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        var isIgnoringBatteryOptimizations by remember {
            mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BgPanel),
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Download Reliability",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = "Some phones (Samsung, Xiaomi, etc.) aggressively terminate background downloads to save battery. Exempting the app ensures large downloads finish reliably.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                if (isIgnoringBatteryOptimizations) {
                    Text("✓ Battery optimization already disabled for this app", color = GreenActive, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Battery Optimization", color = Color(0xFF06070A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 3: Storage
        val downloads by viewModel.downloads.collectAsState()
        var refreshTrigger by remember { mutableIntStateOf(0) }
        var storageBytes by remember { mutableStateOf<Long?>(null) }

        LaunchedEffect(downloads.size, refreshTrigger) {
            storageBytes = withContext(Dispatchers.IO) {
                val dir = context.getExternalFilesDir(null)
                dir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
            }
        }

        val completedCount = downloads.count { it.status == "completed" }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BgPanel),
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Storage",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = when (val used = storageBytes) {
                        null -> "Calculating..."
                        else -> "${formatStorageSize(used)} used by downloaded files"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Button(
                    onClick = {
                        viewModel.clearCompletedDownloads()
                        refreshTrigger++
                    },
                    enabled = completedCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedFailed.copy(alpha = 0.85f),
                        disabledContainerColor = GlassBg
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (completedCount > 0) "Clear $completedCount Completed Download${if (completedCount == 1) "" else "s"}" else "No Completed Downloads to Clear",
                        color = if (completedCount > 0) Color.White else TextTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun formatStorageSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
