package com.example.apexdownloader.ui.screens

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.engine.QualityPreset
import com.example.apexdownloader.engine.VideoFormat
import com.example.apexdownloader.engine.VideoResolver
import com.example.apexdownloader.theme.*
import com.example.apexdownloader.ui.main.AnalysisState
import com.example.apexdownloader.ui.main.MainScreenViewModel
import java.io.File

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

    // Hero Format Selection Modal (Matching Web #formatModal)
    showFormatDialog?.let { success ->
        Dialog(onDismissRequest = { showFormatDialog = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgPanel),
                border = BorderStroke(1.dp, GlassBorderStrong),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column {
                    // Hero Top Banner with video cover
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color(0xFF141822))
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

                        // Gradient overlay from top and bottom
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0x88000000),
                                            Color(0x22000000),
                                            Color(0xF00D1017)
                                        )
                                    )
                                )
                        )

                        // Platform Type Badge
                        val platformType = remember(success.url) {
                            VideoResolver.identifyLinkType(success.url).uppercase()
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xCC06070A))
                                .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = platformType,
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Close Button
                        IconButton(
                            onClick = { showFormatDialog = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Video Title at bottom of banner
                        Text(
                            text = success.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    // Format Selection List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Choose Download Option:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            items(success.formats) { format ->
                                val isAudio = format.ext == "mp3" || format.ext == "m4a"
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141824)),
                                    border = BorderStroke(0.8.dp, GlassBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
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
                                                filename = "$cleanTitle.${format.ext.ifEmpty { "mp4" }}"
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
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isAudio) Color(0x2222D3EE) else Color(0x22FF8A3D)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isAudio) AccentCyan else AccentOrange,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = format.note.ifEmpty { if (isAudio) "High Quality Audio" else "Standard Quality Video" },
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = format.ext.uppercase(),
                                                color = TextTertiary,
                                                fontSize = 11.sp
                                            )
                                        }

                                        if (format.sizeBytes != null && format.sizeBytes > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(GlassBg)
                                                    .border(0.8.dp, GlassBorder, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = formatBytes(format.sizeBytes),
                                                    color = TextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
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

    // Delete confirmation dialog
    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Download", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${target.title}\"?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownloads(setOf(target.id), deleteFiles = false)
                        itemToDelete = null
                    }
                ) {
                    Text("Delete Task", color = AccentOrange)
                }
                TextButton(
                    onClick = {
                        viewModel.deleteDownloads(setOf(target.id), deleteFiles = true)
                        itemToDelete = null
                    }
                ) {
                    Text("Delete Task & File", color = RedFailed)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgPanel,
            shape = RoundedCornerShape(14.dp)
        )
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

                        // Smart Reactive Single Action Button (Paste Link or Analyze)
                        val isAnalyzing = analysisState is AnalysisState.Analyzing
                        val isEmpty = urlInput.trim().isEmpty()

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (isEmpty) {
                                    // Smart Paste
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
                                    // Analyze
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
                                CircularProgressIndicator(
                                    color = Color(0xFF1A0B02),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
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
                                        fontWeight = FontWeight.Bold
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
                                        text = "Analyze",
                                        color = Color(0xFF1A0B02),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Section Header: "ACTIVE DOWNLOADS" + "Clear Completed"
                val hasCompleted = downloads.any { it.status == "completed" }
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

                    if (hasCompleted) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
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

                    val cardBorderColor = when {
                        isCompleted -> GreenActive.copy(alpha = 0.35f)
                        isFailed -> RedFailed.copy(alpha = 0.4f)
                        else -> AccentOrange.copy(alpha = 0.35f)
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                        border = BorderStroke(1.dp, cardBorderColor),
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
                            // Left Thumbnail with format badge
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
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
                                            modifier = Modifier.size(24.dp)
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
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Middle Info Column
                            Column(modifier = Modifier.weight(1f)) {
                                // Row 1: Title & Percent
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
                                        isFailed -> "${item.progress.toInt()}%"
                                        item.totalSize > 0 -> "${item.progress.toInt()}%"
                                        else -> "--"
                                    }
                                    val percentColor = when {
                                        isCompleted -> GreenActive
                                        isFailed -> RedFailed
                                        else -> AccentOrange
                                    }
                                    Text(
                                        text = percentStr,
                                        color = percentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Row 2: Smooth Progress Bar
                                val progressFraction = when {
                                    isCompleted -> 1f
                                    isFailed -> (item.progress / 100f).coerceIn(0.05f, 1f)
                                    item.totalSize > 0 -> (item.progress / 100f).coerceIn(0.01f, 1f)
                                    item.status == "downloading" -> 0.15f
                                    else -> 0.05f
                                }
                                val barColor = when {
                                    isCompleted -> GreenActive
                                    isFailed -> RedFailed
                                    else -> AccentOrange
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(Color(0x22FFFFFF))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progressFraction)
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(barColor)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Row 3: Meta line + Action buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val metaText = when (item.status) {
                                        "downloading" -> {
                                            val cur = formatBytes(item.downloadedSize)
                                            val tot = if (item.totalSize > 0) formatBytes(item.totalSize) else "--"
                                            val spd = item.speed.ifEmpty { "0 B/s" }
                                            "$cur / $tot · $spd"
                                        }
                                        "waiting" -> "In queue..."
                                        "failed" -> item.eta.ifEmpty { "Download failed" }
                                        "completed" -> {
                                            val tot = formatBytes(item.downloadedSize)
                                            "$tot · Completed"
                                        }
                                        "paused" -> {
                                            val cur = formatBytes(item.downloadedSize)
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Quick Action Button
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(GlassBg)
                                                .border(1.dp, GlassBorder, CircleShape)
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
                                                else -> TextPrimary
                                            }
                                            Icon(
                                                imageVector = actionIcon,
                                                contentDescription = "Action",
                                                tint = iconTint,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }

                                        // 3 Dots Menu
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
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = expandedMenu,
                                                onDismissRequest = { expandedMenu = false },
                                                modifier = Modifier.background(Color(0xFF161B26))
                                            ) {
                                                if (isCompleted) {
                                                    DropdownMenuItem(
                                                        text = { Text("Open / Play", color = Color.White) },
                                                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GreenActive) },
                                                        onClick = {
                                                            expandedMenu = false
                                                            openVideoFile(context, item.localPath)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Share Media", color = Color.White) },
                                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AccentCyan) },
                                                        onClick = {
                                                            expandedMenu = false
                                                            shareVideoFile(context, item.localPath)
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
                                                        itemToDelete = item
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
        val isAudio = filePath.endsWith(".mp3", true) || filePath.endsWith(".m4a", true)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (isAudio) "audio/*" else "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Play Media")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) { /* ignore */ }
}

private fun shareVideoFile(context: Context, filePath: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.apexdownloader.fileprovider",
            file
        )
        val isAudio = filePath.endsWith(".mp3", true) || filePath.endsWith(".m4a", true)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isAudio) "audio/*" else "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Media Via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) { /* ignore */ }
}
