package com.example.apexdownloader.ui.main

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.theme.*
import com.example.apexdownloader.ui.components.HkLogoIcon
import com.example.apexdownloader.ui.components.HkPlayerModal
import com.example.apexdownloader.ui.screens.DownloaderScreen
import com.example.apexdownloader.ui.screens.LibraryScreen
import com.example.apexdownloader.ui.screens.SettingsScreen
import kotlinx.coroutines.delay

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
    val activePlayerItem by viewModel.activePlayerItem.collectAsState()
    val activeTaskModalId by viewModel.activeTaskModalId.collectAsState()

    val ambientState = rememberAmbientState(
        isAnalyzing = analysisState is AnalysisState.Analyzing,
        downloads = downloads
    )

    // Track newly completed downloads to show a floating completion notification toast
    var completedToastItem by remember { mutableStateOf<DownloadItem?>(null) }
    val seenCompletedIds = remember { mutableStateSetOf<String>() }
    var initializedCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(downloads) {
        val currentCompleted = downloads.filter { it.status == "completed" }
        if (!initializedCompleted) {
            currentCompleted.forEach { seenCompletedIds.add(it.id) }
            initializedCompleted = true
        } else {
            val newlyFinished = currentCompleted.firstOrNull { !seenCompletedIds.contains(it.id) }
            currentCompleted.forEach { seenCompletedIds.add(it.id) }
            if (newlyFinished != null) {
                completedToastItem = newlyFinished
            }
        }
    }

    LaunchedEffect(completedToastItem?.id) {
        if (completedToastItem != null) {
            delay(3800L)
            completedToastItem = null
        }
    }

    // Active running download for floating toast banner when Task Modal is minimized
    val activeRunningItem = remember(downloads) {
        downloads.firstOrNull { it.status == "downloading" || it.status == "waiting" }
    }

    // Built-in HK PLAYER PRO Modal (Matching Web #playerModal)
    activePlayerItem?.let { playerItem ->
        HkPlayerModal(
            item = playerItem,
            onDismiss = { viewModel.closePlayer() }
        )
    }

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
                    // Left Brand: Logo + Gradient Title + Version Badge
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
                                style = TextStyle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.White, AccentOrange)
                                    ),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.2).sp
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(Color(0x1F22D3EE))
                                    .border(1.dp, Color(0x4022D3EE), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v2.9.5",
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

                // Floating Top Notification Toast Container (Active Downloads & Completion Toasts)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Live Active Download Toast Pill (shown whenever an active download is running & modal is minimized)
                    AnimatedVisibility(
                        visible = activeRunningItem != null && activeTaskModalId == null,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    ) {
                        activeRunningItem?.let { running ->
                            val pct = running.progress.toInt().coerceIn(0, 100)
                            val animPct by animateFloatAsState(
                                targetValue = (running.progress / 100f).coerceIn(0.03f, 1f),
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                label = "toastProgress"
                            )
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xF210141F)),
                                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.openTaskModal(running.id)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x26FF8A3D)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = null,
                                                    tint = AccentOrange,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Active Download • $pct%",
                                                    color = AccentOrange,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    text = "${running.title.ifEmpty { "Media" }} • ${running.speed.ifEmpty { "Downloading..." }}",
                                                    color = TextPrimary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(GlassBgLight)
                                                .border(1.dp, GlassBorder, CircleShape)
                                                .clickable { viewModel.pauseDownload(running) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Pause,
                                                contentDescription = "Pause",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color(0x22FFFFFF))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(animPct)
                                                .clip(RoundedCornerShape(100.dp))
                                                .background(Brush.horizontalGradient(listOf(AccentOrange, AccentCyan)))
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Completion Notification Toast Banner
                    AnimatedVisibility(
                        visible = completedToastItem != null,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    ) {
                        completedToastItem?.let { doneItem ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xF510181D)),
                                border = BorderStroke(1.dp, GreenActive.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        completedToastItem = null
                                        viewModel.openPlayer(doneItem)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x2610B981)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = GreenActive,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Download Complete",
                                                color = GreenActive,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = doneItem.title.ifEmpty { "Saved to Downloads" },
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(GreenActive)
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color(0xFF051B11),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Play",
                                            color = Color(0xFF051B11),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
