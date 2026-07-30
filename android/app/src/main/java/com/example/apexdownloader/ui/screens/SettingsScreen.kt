package com.example.apexdownloader.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    modifier: Modifier = Modifier
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgPanel),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Desktop Sync Connection",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Enter the local network IP and port of your PC running the Apex Desktop App to download and convert YouTube videos at maximum speed.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("e.g. http://192.168.1.100:3982", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Button(
                    onClick = {
                        viewModel.saveDesktopServerUrl(urlInput.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Connection Settings", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val context = LocalContext.current
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        var isIgnoringBatteryOptimizations by remember {
            mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgPanel),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Download Reliability",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Some phones (Samsung, Xiaomi, and others) aggressively kill background downloads to save battery, even with the progress notification showing. Exempting the app stops that.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isIgnoringBatteryOptimizations) {
                    Text("✓ Battery optimization already disabled for this app", color = GreenActive, fontSize = 13.sp)
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
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Battery Optimization", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgPanel),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Storage",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = when (val used = storageBytes) {
                        null -> "Calculating..."
                        else -> "${formatStorageSize(used)} used by downloaded files"
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = {
                        viewModel.clearCompletedDownloads()
                        refreshTrigger++
                    },
                    enabled = completedCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (completedCount > 0) "Clear $completedCount Completed Download${if (completedCount == 1) "" else "s"}" else "No Completed Downloads to Clear",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
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
