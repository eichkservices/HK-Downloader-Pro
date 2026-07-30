package com.example.apexdownloader.ui.main

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.apexdownloader.theme.*
import com.example.apexdownloader.ui.screens.DownloaderScreen
import com.example.apexdownloader.ui.screens.SettingsScreen

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(app) }

    var selectedTab by remember { mutableStateOf(0) }

    val analysisState by viewModel.analysisState.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val ambientState = rememberAmbientState(
        isAnalyzing = analysisState is AnalysisState.Analyzing,
        downloads = downloads
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = BgPanel,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Downloader") },
                    label = { Text("Downloader", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentOrange,
                        selectedTextColor = AccentOrange,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = Color(0x15E07A2B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentOrange,
                        selectedTextColor = AccentOrange,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = Color(0x15E07A2B)
                    )
                )
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

            // Phones (the common case) get the existing full-width layout
            // unchanged. On anything wider -- tablets, unfolded foldables,
            // split-screen/multi-window -- content is capped at a readable
            // width and centered instead of stretching edge to edge.
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
                when (selectedTab) {
                    0 -> DownloaderScreen(viewModel = viewModel)
                    1 -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
