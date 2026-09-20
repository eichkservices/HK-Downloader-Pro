package com.example.apexdownloader.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.engine.VideoFormat
import com.example.apexdownloader.engine.QualityPreset
import com.example.apexdownloader.theme.*
import com.example.apexdownloader.ui.main.AnalysisState
import com.example.apexdownloader.ui.main.MainScreenViewModel
import java.io.File

// Custom Modifier to draw status-colored neon glow
fun Modifier.statusGlow(color: Color, borderRadius: Dp = 8.dp): Modifier = this.drawBehind {
    val sizePx = size
    val radPx = borderRadius.toPx()
    
    // Layer 1: Soft outer glow shadow
    drawRoundRect(
        color = color.copy(alpha = 0.15f),
        topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
        size = Size(sizePx.width + 8.dp.toPx(), sizePx.height + 8.dp.toPx()),
        cornerRadius = CornerRadius(radPx + 4.dp.toPx()),
        style = Stroke(width = 4.dp.toPx())
    )
    // Layer 2: Medium glow shadow
    drawRoundRect(
        color = color.copy(alpha = 0.35f),
        topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
        size = Size(sizePx.width + 4.dp.toPx(), sizePx.height + 4.dp.toPx()),
        cornerRadius = CornerRadius(radPx + 2.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
    // Layer 3: Solid colored outline border
    drawRoundRect(
        color = color.copy(alpha = 0.85f),
        cornerRadius = CornerRadius(radPx),
        style = Stroke(width = 1.dp.toPx())
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(
    viewModel: MainScreenViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    
    var urlInput by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    
    val downloads by viewModel.downloads.collectAsState()
    val analysisState by viewModel.analysisState.collectAsState()

    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showFormatDialog by remember { mutableStateOf<AnalysisState.Success?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Set<String>?>(null) }
    
    // Quality preset selection -- this now actually drives what gets requested,
    // instead of being a decorative index that nothing read.
    var selectedPreset by remember { mutableStateOf(QualityPreset.DEFAULT) }
    var filterTab by remember { mutableStateOf("all") }

    LaunchedEffect(analysisState) {
        if (analysisState is AnalysisState.Success) {
            showFormatDialog = analysisState as AnalysisState.Success
            viewModel.resetAnalysis()
        }
    }

    // Format selection dialog popup
    showFormatDialog?.let { successState ->
        AlertDialog(
            onDismissRequest = { showFormatDialog = null },
            title = { Text("Download Options", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text(
                        text = successState.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text("Select format & resolution:", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(successState.formats) { format ->
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = BgPanel),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.triggerDownload(
                                            url = successState.url,
                                            title = successState.title,
                                            thumbnail = successState.thumbnail,
                                            formatId = format.formatId,
                                            filename = successState.title + "." + format.ext
                                        )
                                        showFormatDialog = null
                                        urlInput = ""
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (format.ext == "mp3" || format.ext == "m4a") Icons.Default.MusicNote else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(format.note, color = TextPrimary, fontSize = 13.sp)
                                        if (format.sizeBytes != null) {
                                            Text(formatBytes(format.sizeBytes), color = TextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFormatDialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgPanel,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Delete tasks dialog
    showDeleteDialog?.let { targets ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Tasks", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${targets.size} selected tasks?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownloads(targets, deleteFiles = false)
                        selectedIds = selectedIds - targets
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete Tasks Only", color = AccentOrange)
                }
                TextButton(
                    onClick = {
                        viewModel.deleteDownloads(targets, deleteFiles = true)
                        selectedIds = selectedIds - targets
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete Tasks & Files", color = RedFailed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgPanel,
            shape = RoundedCornerShape(12.dp)
        )
    }

    val filteredDownloads = remember(downloads, filterTab) {
        when (filterTab) {
            "active" -> downloads.filter { it.status == "downloading" || it.status == "waiting" || it.status == "paused" }
            "failed" -> downloads.filter { it.status == "failed" }
            else -> downloads
        }
    }

    val allCount = downloads.size
    val activeCount = downloads.count { it.status == "downloading" || it.status == "waiting" || it.status == "paused" }
    val failedCount = downloads.count { it.status == "failed" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // 1. Redesigned Clean Header Bar (logo + HK Downloader + PRO, no search, no bell, no active/failed text)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF9E40), Color(0xFFE06518))))
                    .statusGlow(AccentOrange, 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "HK Downloader",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "PRO",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                // Subtitle
                Text(
                    text = "Paste a link to start a download",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                // 2. Unified Input Bar Row (with Paste button and Download button)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF121620))
                        .border(0.8.dp, Color(0x25FFFFFF), RoundedCornerShape(14.dp))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (urlInput.isEmpty()) {
                                Text(
                                    text = "youtube.com/watch?v=...",
                                    color = TextSecondary.copy(alpha = 0.55f),
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    viewModel.analyzeUrl(urlInput, selectedPreset)
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isInputFocused = it.isFocused }
                            )
                        }

                        // Paste button next to Download
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x18FFFFFF))
                                .clickable {
                                    clipboardManager.getText()?.text?.let { text ->
                                        urlInput = text
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Paste",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Download button (says "Download", with arrow)
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (urlInput.trim().isNotEmpty()) {
                                    viewModel.analyzeUrl(urlInput, selectedPreset)
                                } else {
                                    clipboardManager.getText()?.text?.let { text ->
                                        urlInput = text
                                        viewModel.analyzeUrl(text, selectedPreset)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            if (analysisState is AnalysisState.Analyzing) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Download",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Filter Pills Row: All | Active | Failed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF10141E))
                        .border(0.8.dp, Color(0x18FFFFFF), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // All
                    val isAll = filterTab == "all"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isAll) Brush.horizontalGradient(listOf(Color(0xFFFF9E40), Color(0xFFE06518)))
                                else SolidColor(Color.Transparent)
                            )
                            .clickable { filterTab = "all" },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "All",
                                color = if (isAll) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isAll) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "$allCount",
                                color = if (isAll) Color.White.copy(alpha = 0.85f) else TextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Active
                    val isActive = filterTab == "active"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) Brush.horizontalGradient(listOf(Color(0xFFFF9E40), Color(0xFFE06518)))
                                else SolidColor(Color.Transparent)
                            )
                            .clickable { filterTab = "active" },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Active",
                                color = if (isActive) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "$activeCount",
                                color = if (isActive) Color.White.copy(alpha = 0.85f) else TextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Failed
                    val isFailed = filterTab == "failed"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isFailed) Brush.horizontalGradient(listOf(Color(0xFFFF9E40), Color(0xFFE06518)))
                                else SolidColor(Color.Transparent)
                            )
                            .clickable { filterTab = "failed" },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Failed",
                                color = if (isFailed) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isFailed) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "$failedCount",
                                color = if (isFailed) Color.White.copy(alpha = 0.85f) else TextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. Download Cards (Title -> Size/Speed -> Progress + % + Button + 3-dots in SAME line)
            if (filteredDownloads.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (filterTab) {
                                "active" -> "No active downloads in queue."
                                "failed" -> "No failed tasks."
                                else -> "No downloads in queue. Paste a link above!"
                            },
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(filteredDownloads) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                        border = BorderStroke(0.8.dp, Color(0x18FFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1B202E))
                                    .then(
                                        if (item.status == "failed") Modifier.border(1.dp, RedFailed.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        else Modifier
                                    )
                            ) {
                                if (item.thumbnail.isNotEmpty()) {
                                    SubcomposeAsyncImage(
                                        model = item.thumbnail,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        loading = {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                                            }
                                        },
                                        error = {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (item.type == "audio") Icons.Default.MusicNote else Icons.Default.PlayCircle,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // Format badge
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
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
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

                            // Right details: Title, Size/Speed, Progress + % + Button + 3-dots
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                // Line 1: Title
                                Text(
                                    text = item.title.ifEmpty { "Downloading Media" },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Line 2: Download size, then speed (or error description)
                                val subtext = when (item.status) {
                                    "downloading" -> {
                                        val cur = formatBytes(item.downloadedSize)
                                        val tot = if (item.totalSize > 0) formatBytes(item.totalSize) else "--"
                                        val spd = item.speed.ifEmpty { "0 B/s" }
                                        "$cur / $tot · $spd"
                                    }
                                    "waiting" -> "Waiting · In queue"
                                    "failed" -> item.eta.ifEmpty { "Couldn't reach the source" }
                                    "completed" -> {
                                        val tot = formatBytes(item.downloadedSize)
                                        "$tot · Completed"
                                    }
                                    "paused" -> {
                                        val cur = formatBytes(item.downloadedSize)
                                        val tot = if (item.totalSize > 0) formatBytes(item.totalSize) else "--"
                                        "$cur / $tot · Paused"
                                    }
                                    else -> "Processing..."
                                }
                                val subtextColor = when (item.status) {
                                    "failed" -> RedFailed
                                    "completed" -> GreenActive
                                    else -> TextSecondary
                                }
                                Text(
                                    text = subtext,
                                    color = subtextColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (item.status == "failed") FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Line 3: Progress + % + button + 3 dots in SAME line!
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Progress bar
                                    val progressFraction = when {
                                        item.status == "completed" -> 1f
                                        item.status == "failed" -> (item.progress / 100f).coerceIn(0.05f, 1f)
                                        item.totalSize > 0 -> (item.progress / 100f).coerceIn(0.01f, 1f)
                                        item.status == "downloading" -> 0.15f
                                        else -> 0.05f
                                    }
                                    val progressBarColor = when (item.status) {
                                        "failed" -> RedFailed
                                        "completed" -> GreenActive
                                        else -> AccentOrange
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0x22FFFFFF))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(progressFraction)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(progressBarColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Percentage text
                                    val percentStr = when {
                                        item.status == "completed" -> "100%"
                                        item.status == "failed" -> "${item.progress.toInt()}%"
                                        item.totalSize > 0 -> "${item.progress.toInt()}%"
                                        else -> "--"
                                    }
                                    Text(
                                        text = percentStr,
                                        color = if (item.status == "failed") RedFailed else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Action button (Pause / Resume / Retry / Play)
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x20FFFFFF))
                                            .clickable {
                                                when (item.status) {
                                                    "completed" -> openVideoFile(context, item.localPath)
                                                    "downloading", "paused", "failed" -> viewModel.pauseOrResumeDownload(item)
                                                    else -> viewModel.deleteDownloads(setOf(item.id), false)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val actionIcon = when (item.status) {
                                            "downloading" -> Icons.Default.Pause
                                            "paused" -> Icons.Default.PlayArrow
                                            "failed" -> Icons.Default.Refresh
                                            "completed" -> Icons.Default.PlayArrow
                                            else -> Icons.Default.Close
                                        }
                                        val iconTint = when (item.status) {
                                            "completed" -> GreenActive
                                            "failed" -> AccentOrange
                                            else -> Color.White
                                        }
                                        Icon(
                                            imageVector = actionIcon,
                                            contentDescription = "Action",
                                            tint = iconTint,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // 3 dots button with DropdownMenu
                                    var expandedMenu by remember { mutableStateOf(false) }

                                    Box {
                                        IconButton(
                                            onClick = { expandedMenu = true },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = expandedMenu,
                                            onDismissRequest = { expandedMenu = false },
                                            modifier = Modifier.background(Color(0xFF1A1F2C))
                                        ) {
                                            if (item.status == "completed") {
                                                DropdownMenuItem(
                                                    text = { Text("Open / Play", color = Color.White) },
                                                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GreenActive) },
                                                    onClick = {
                                                        expandedMenu = false
                                                        openVideoFile(context, item.localPath)
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Copy Link", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary) },
                                                onClick = {
                                                    expandedMenu = false
                                                    clipboardManager.setText(AnnotatedString(item.url))
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete Task", color = AccentOrange) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AccentOrange) },
                                                onClick = {
                                                    expandedMenu = false
                                                    viewModel.deleteDownloads(setOf(item.id), false)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete Task & File", color = RedFailed) },
                                                leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = RedFailed) },
                                                onClick = {
                                                    expandedMenu = false
                                                    viewModel.deleteDownloads(setOf(item.id), true)
                                                }
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun openVideoFile(context: Context, filePath: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.apexdownloader.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Play Video Using")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) { /* ignore */ }
}
