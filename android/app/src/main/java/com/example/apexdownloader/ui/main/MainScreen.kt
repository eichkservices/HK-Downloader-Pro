package com.example.apexdownloader.ui.main

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDark)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF131722))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(22.dp))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Queue tab
                    val queueSelected = selectedTab == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (queueSelected) Color(0x28E07A2B)
                                else Color.Transparent
                            )
                            .then(
                                if (queueSelected) Modifier.border(1.dp, Color(0x45E07A2B), RoundedCornerShape(18.dp))
                                else Modifier
                            )
                            .clickable { selectedTab = 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Queue",
                                tint = if (queueSelected) AccentOrange else TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Queue",
                                color = if (queueSelected) AccentOrange else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (queueSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Library / Settings tab
                    val librarySelected = selectedTab == 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (librarySelected) Color(0x28E07A2B)
                                else Color.Transparent
                            )
                            .then(
                                if (librarySelected) Modifier.border(1.dp, Color(0x45E07A2B), RoundedCornerShape(18.dp))
                                else Modifier
                            )
                            .clickable { selectedTab = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Library",
                                tint = if (librarySelected) AccentOrange else TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Library",
                                color = if (librarySelected) AccentOrange else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (librarySelected) FontWeight.Bold else FontWeight.Medium
                            )
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
