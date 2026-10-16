package com.example.apexdownloader.ui.main

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.apexdownloader.theme.*
import com.example.apexdownloader.ui.components.HkLogoIcon
import com.example.apexdownloader.ui.screens.DownloaderScreen
import com.example.apexdownloader.ui.screens.LibraryScreen
import com.example.apexdownloader.ui.screens.SettingsScreen

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: MainScreenViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app))

    var selectedTab by remember { mutableIntStateOf(0) }
    var showingSettings by remember { mutableStateOf(false) }

    val analysisState by viewModel.analysisState.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val ambientState = rememberAmbientState(
        isAnalyzing = analysisState is AnalysisState.Analyzing,
        downloads = downloads
    )

    Scaffold(
        topBar = {
            // Obsidian Header matching Web App .app-header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE606070A))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Brand: Logo + Title + Version Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HkLogoIcon(modifier = Modifier.size(28.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "HK Downloader Pro",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(Color(0x1F22D3EE))
                                    .border(1.dp, Color(0x4022D3EE), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v2.9.2",
                                    color = AccentCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Right: Settings Action
                    IconButton(
                        onClick = { showingSettings = !showingSettings },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassBg)
                            .border(1.dp, GlassBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (showingSettings) AccentOrange else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (!showingSettings) {
                // Docked Glass Bottom Nav (Matching Web App .bottom-nav)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xF206070A))
                        .border(BorderStroke(1.dp, GlassBorder))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Tab 0: Downloader
                        val isDownloader = selectedTab == 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Downloader",
                                    tint = if (isDownloader) AccentOrange else TextTertiary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Downloader",
                                    color = if (isDownloader) AccentOrange else TextTertiary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isDownloader) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        // Tab 1: Library
                        val isLibrary = selectedTab == 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Library",
                                    tint = if (isLibrary) AccentCyan else TextTertiary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Library",
                                    color = if (isLibrary) AccentCyan else TextTertiary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isLibrary) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = BgDark
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AmbientGlowBackground(state = ambientState, modifier = Modifier.fillMaxSize())

            val isWideScreen = maxWidth > 600.dp
            val contentModifier = if (isWideScreen) {
                Modifier
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            }

            Box(modifier = contentModifier) {
                if (showingSettings) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { showingSettings = false }
                    )
                } else {
                    when (selectedTab) {
                        0 -> DownloaderScreen(viewModel = viewModel)
                        1 -> LibraryScreen(
                            viewModel = viewModel,
                            onOpenSettings = { showingSettings = true }
                        )
                    }
                }
            }
        }
    }
}
