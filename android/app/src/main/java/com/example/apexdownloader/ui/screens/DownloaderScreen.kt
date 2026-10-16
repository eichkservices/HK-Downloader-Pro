package com.example.apexdownloader.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.engine.VideoResolver
import com.example.apexdownloader.theme.*
import com.example.apexdownloader.ui.components.DeleteConfirmModal
import com.example.apexdownloader.ui.components.TaskDownloadModal
import com.example.apexdownloader.ui.components.formatBytesUi
import com.example.apexdownloader.ui.components.openMediaExternally
import com.example.apexdownloader.ui.components.shareMediaExternally
import com.example.apexdownloader.ui.main.AnalysisState
import com.example.apexdownloader.ui.main.MainScreenViewModel

// Custom Modifier for obsidian neon glow
fun Modifier.glowBorder(color: Color, borderRadius: Dp = 14.dp): Modifier = this.drawBehind {
    val sizePx = size
    val radPx = borderRadius.toPx()

    // Outer soft glow
    drawRoundRect(
        color = color.copy(alpha = 0.15f),
        topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
        size = Size(sizePx.width + 8.dp.toPx(), sizePx.height + 8.dp.toPx()),
        cornerRadius = CornerRadius(radPx + 4.dp.toPx()),
        style = Stroke(width = 4.dp.toPx())
    )
    // Inner medium glow
    drawRoundRect(
        color = color.copy(alpha = 0.35f),
        topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
        size = Size(sizePx.width + 3.dp.toPx(), sizePx.height + 3.dp.toPx()),
        cornerRadius = CornerRadius(radPx + 1.5.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )
}

@Composable
fun DownloaderScreen(
    viewModel: MainScreenViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

    var urlInput by remember { mutableStateOf("") }
    val downloads by viewModel.downloads.collectAsState()
    val analysisState by viewModel.analysisState.collectAsState()
    val activeTaskModalId by viewModel.activeTaskModalId.collectAsState()

    var showFormatDialog by remember { mutableStateOf<AnalysisState.Success?>(null) }
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }

    LaunchedEffect(analysisState) {
        when (val state = analysisState) {
            is AnalysisState.Success -> {
                showFormatDialog = state
                viewModel.resetAnalysis()
            }
            is AnalysisState.Error -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_LONG).show()
                viewModel.resetAnalysis()
            }
            else -> {}
        }
    }

    // 1. Task-Based Download Station Modal (Matches Web #taskDownloadModal)
    val activeTaskItem = remember(downloads, activeTaskModalId) {
        activeTaskModalId?.let { id -> downloads.find { it.id == id } }
    }
    if (activeTaskItem != null) {
        TaskDownloadModal(
            item = activeTaskItem,
            onMinimize = { viewModel.closeTaskModal() },
            onPauseOrResume = { viewModel.pauseOrResumeDownload(activeTaskItem) },
            onStop = { viewModel.stopDownload(activeTaskItem) },
            onDelete = {
                viewModel.closeTaskModal()
                itemToDelete = activeTaskItem
            },
            onPlayInApp = {
                viewModel.closeTaskModal()
                viewModel.openPlayer(activeTaskItem)
            }
        )
    }

    // 2. Delete Confirmation Modal (Matches Web #deleteModal)
    itemToDelete?.let { target ->
        DeleteConfirmModal(
            item = target,
            onDismiss = { itemToDelete = null },
            onDeleteTaskOnly = {
                viewModel.deleteDownloads(setOf(target.id), deleteFiles = false)
                itemToDelete = null
            },
            onDeleteTaskAndFile = {
                viewModel.deleteDownloads(setOf(target.id), deleteFiles = true)
                itemToDelete = null
            }
        )
    }

    // 3. Hero Format Selection Modal (Matches Web #formatModal)
    showFormatDialog?.let { success ->
        Dialog(
            onDismissRequest = { showFormatDialog = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1017)),
                border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .padding(vertical = 16.dp)
            ) {
                Column {
                    // Hero Top Banner with video cover (16:9)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color(0xFF08090C))
                    ) {
                        if (success.thumbnail.isNotEmpty()) {
                            SubcomposeAsyncImage(
                                model = success.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        // Gradient overlay matching web .format-hero-gradient
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0x590E1017),
                                            Color(0x1A0E1017),
                                            Color(0xFA0E1017)
                                        )
                                    )
                                )
                        )

                        // Close Button (top-right)
                        IconButton(
                            onClick = { showFormatDialog = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xA60F1118))
                                .border(1.dp, Color(0x26FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Platform Badge + Title at bottom of banner
                        val platformType = remember(success.url) {
                            VideoResolver.identifyLinkType(success.url).uppercase()
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(Color(0x26FF8A3D))
                                    .border(1.dp, Color(0x59FF8A3D), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "HD • $platformType",
                                    color = AccentOrange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Text(
                                text = success.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Format Selection List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "Choose Download Option:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(success.formats) { format ->
                                val isAudio = format.ext == "mp3" || format.ext == "m4a"
                                val sizeStr = if (format.sizeBytes != null && format.sizeBytes > 0) {
                                    formatBytesUi(format.sizeBytes)
                                } else "Size unknown"

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0x09FFFFFF)),
                                    border = BorderStroke(1.dp, GlassBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val cleanTitle = success.title
                                                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                                .trim()
                                                .ifEmpty { "video" }
                                            viewModel.triggerDownload(
                                                url = success.url,
                                                title = success.title,
                                                thumbnail = success.thumbnail,
                                                formatId = format.formatId,
                                                filename = "$cleanTitle.${format.ext.ifEmpty { "mp4" }}",
                                                sizeBytes = format.sizeBytes ?: 0L
                                            )
                                            showFormatDialog = null
                                            urlInput = ""
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Format Row Thumbnail / Icon (matching web .format-item-thumb)
                                        Box(
                                            modifier = Modifier
                                                .width(48.dp)
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0A0C10))
                                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (success.thumbnail.isNotEmpty() && !isAudio) {
                                                SubcomposeAsyncImage(
                                                    model = success.thumbnail,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = if (isAudio) AccentCyan else AccentOrange,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = format.note.ifEmpty { if (isAudio) "Audio Only (MP3)" else "Video (${format.ext.uppercase()})" },
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${format.ext.uppercase()} • $sizeStr",
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Orange Download Pill Button (Matching web .format-dl-btn)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    Brush.horizontalGradient(listOf(AccentOrange, AccentOrangeEnd))
                                                )
                                                .padding(horizontal = 12.dp, vertical = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Download",
                                                color = Color(0xFF1A0B02),
                                                fontSize = 12.sp,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // 1. Section Title: "DOWNLOADER"
            item {
                Text(
                    text = "DOWNLOADER",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // 2. Glowing URL Input Box (Matching web .glowing-input-box)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F131D))
                        .border(1.dp, GlassBorderStrong, RoundedCornerShape(14.dp))
                        .glowBorder(AccentOrange.copy(alpha = 0.25f), 14.dp)
                        .padding(start = 14.dp, end = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Inner text input field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (urlInput.isEmpty()) {
                                Text(
                                    text = "Enter or paste video URL",
                                    color = TextTertiary,
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    if (urlInput.trim().isNotEmpty()) {
                                        viewModel.analyzeUrl(urlInput.trim())
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Clear X button when URL is typed
                        if (urlInput.isNotEmpty()) {
                            IconButton(
                                onClick = { urlInput = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear URL",
                                    tint = TextTertiary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                        }

                        // Smart Reactive Single Action Button (Paste Link -> Download -> Analyzing...)
                        val isAnalyzing = analysisState is AnalysisState.Analyzing
                        val isEmpty = urlInput.trim().isEmpty()

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (isEmpty) {
                                    val clip = clipboardManager.getText()?.text?.toString()?.trim() ?: ""
                                    if (clip.isNotEmpty()) {
                                        urlInput = clip
                                        if (clip.startsWith("http://") || clip.startsWith("https://") || clip.contains(".")) {
                                            viewModel.analyzeUrl(clip)
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Clipboard is empty", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    viewModel.analyzeUrl(urlInput.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(AccentOrange, AccentOrangeEnd)),
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            if (isAnalyzing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF1A0B02),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Analyzing…",
                                        color = Color(0xFF1A0B02),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            } else if (isEmpty) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = Color(0xFF1A0B02),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Paste Link",
                                        color = Color(0xFF1A0B02),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = Color(0xFF1A0B02),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Download",
                                        color = Color(0xFF1A0B02),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // 3. Section Header: "ACTIVE DOWNLOADS" + "Clear Completed" (Always visible like web app)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ACTIVE DOWNLOADS",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(GlassBg)
                            .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                            .clickable {
                                viewModel.clearCompletedDownloads()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Clear Completed",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 4. Downloads List (Matching web .dl-card)
            if (downloads.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(GlassBg)
                                    .border(1.dp, GlassBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "No active downloads",
                                color = TextTertiary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(downloads, key = { it.id }) { item ->
                    val isCompleted = item.status == "completed"
                    val isFailed = item.status == "failed"
                    val isPaused = item.status == "paused"

                    val cardBorderColor = when {
                        isCompleted -> GreenActive.copy(alpha = 0.35f)
                        isFailed -> RedFailed.copy(alpha = 0.4f)
                        else -> AccentOrange.copy(alpha = 0.35f)
                    }

                    val targetProgress = when {
                        isCompleted -> 1f
                        item.progress > 0f -> (item.progress / 100f).coerceIn(0.02f, 1f)
                        item.status == "downloading" -> 0.06f
                        else -> 0.02f
                    }
                    val animatedProgress by animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        label = "cardProgress"
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                        border = BorderStroke(1.dp, cardBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                if (isCompleted) {
                                    viewModel.openPlayer(item)
                                } else {
                                    viewModel.openTaskModal(item.id)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Thumbnail with format badge
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1B202E))
                                    .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
                            ) {
                                if (item.thumbnail.isNotEmpty()) {
                                    SubcomposeAsyncImage(
                                        model = item.thumbnail,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (item.type == "audio") Icons.Default.MusicNote else Icons.Default.PlayCircle,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                // Format badge in corner
                                val badgeText = when {
                                    item.type == "audio" -> "MP3"
                                    item.formatId.contains("1080") -> "1080p"
                                    item.formatId.contains("720") -> "720p"
                                    else -> "MP4"
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Middle Info Column (Unified Name, Quality, Progress Bar & Controls in One Box)
                            Column(modifier = Modifier.weight(1f)) {
                                // Row 1: Title & Live Percentage
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.title.ifEmpty { "Downloading Media" },
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    val percentStr = when {
                                        isCompleted -> "100%"
                                        else -> "${item.progress.toInt().coerceIn(0, 100)}%"
                                    }
                                    val percentColor = when {
                                        isCompleted -> GreenActive
                                        isFailed -> RedFailed
                                        else -> AccentOrange
                                    }
                                    Text(
                                        text = percentStr,
                                        color = percentColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Row 2: Smooth Animated Progress Bar
                                val barBrush = when {
                                    isCompleted -> Brush.horizontalGradient(listOf(GreenActive, AccentCyan))
                                    isFailed -> Brush.horizontalGradient(listOf(RedFailed, RedFailed))
                                    else -> Brush.horizontalGradient(listOf(AccentOrange, AccentCyan))
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(Color(0x22FFFFFF))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(animatedProgress)
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(barBrush)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Row 3: Meta line + Direct Action Buttons (Play / Pause / Stop / Delete)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val metaText = when (item.status) {
                                        "downloading" -> {
                                            val cur = formatBytesUi(item.downloadedSize)
                                            val tot = if (item.totalSize > 0) formatBytesUi(item.totalSize) else "Streaming"
                                            val spd = item.speed.ifEmpty { "" }
                                            if (spd.isNotEmpty() && spd != "0 B/s") "$cur / $tot · $spd" else "$cur / $tot"
                                        }
                                        "waiting" -> "Starting download..."
                                        "failed" -> item.eta.ifEmpty { "Stopped / Failed" }
                                        "completed" -> {
                                            val tot = formatBytesUi(if (item.downloadedSize > 0) item.downloadedSize else item.totalSize)
                                            "$tot · Ready"
                                        }
                                        "paused" -> {
                                            val cur = formatBytesUi(item.downloadedSize)
                                            "$cur · Paused"
                                        }
                                        else -> "Processing..."
                                    }
                                    val metaColor = when {
                                        isFailed -> RedFailed
                                        isCompleted -> GreenActive
                                        else -> TextSecondary
                                    }

                                    Text(
                                        text = metaText,
                                        color = metaColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Direct Visible Action Buttons matching Web App .dl-actions
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (isCompleted) {
                                            // Play in Built-in HK Player Pill Button
                                            Row(
                                                modifier = Modifier
                                                    .height(26.dp)
                                                    .clip(RoundedCornerShape(100.dp))
                                                    .background(Color(0x2410B981))
                                                    .border(1.dp, Color(0x6610B981), RoundedCornerShape(100.dp))
                                                    .clickable { viewModel.openPlayer(item) }
                                                    .padding(horizontal = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Play",
                                                    tint = GreenActive,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = "Play",
                                                    color = GreenActive,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }

                                            // Open Externally Button
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(GlassBg)
                                                    .border(1.dp, GlassBorder, CircleShape)
                                                    .clickable { openMediaExternally(context, item.localPath) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.OpenInNew,
                                                    contentDescription = "External Player",
                                                    tint = AccentCyan,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }

                                            // Share Button
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(GlassBg)
                                                    .border(1.dp, GlassBorder, CircleShape)
                                                    .clickable { shareMediaExternally(context, item.localPath) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Share",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }

                                            // Delete Button
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x1AEF4444))
                                                    .border(1.dp, Color(0x40EF4444), CircleShape)
                                                    .clickable { itemToDelete = item },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = RedFailed,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        } else {
                                            // Pause / Resume / Retry Button
                                            val isRunning = item.status == "downloading" || item.status == "waiting"
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isRunning) GlassBg else Color(0x26FF8A3D))
                                                    .border(
                                                        1.dp,
                                                        if (isRunning) GlassBorder else AccentOrange.copy(alpha = 0.5f),
                                                        CircleShape
                                                    )
                                                    .clickable { viewModel.pauseOrResumeDownload(item) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = when {
                                                        isRunning -> Icons.Default.Pause
                                                        isPaused -> Icons.Default.PlayArrow
                                                        else -> Icons.Default.Refresh
                                                    },
                                                    contentDescription = if (isRunning) "Pause" else "Resume",
                                                    tint = if (isRunning) TextPrimary else AccentOrange,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }

                                            // Stop Button (when running or paused)
                                            if (isRunning || isPaused) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .clip(CircleShape)
                                                        .background(GlassBg)
                                                        .border(1.dp, GlassBorder, CircleShape)
                                                        .clickable { viewModel.stopDownload(item) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Stop,
                                                        contentDescription = "Stop",
                                                        tint = TextSecondary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }

                                            // Delete Button
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x1AEF4444))
                                                    .border(1.dp, Color(0x40EF4444), CircleShape)
                                                    .clickable { itemToDelete = item },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = RedFailed,
                                                    modifier = Modifier.size(13.dp)
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
    }
}
