package com.example.apexdownloader.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // 1. Premium Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {}) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
            Text(
                text = "Apex Downloader",
                color = AccentOrange,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = "Downloader",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // 2. Large Unified Glow Input Bar Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .statusGlow(AccentOrange, 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BgPanel)
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (urlInput.isEmpty()) {
                                Text(
                                    text = "Enter or paste video URL",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
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

                        // Start Download button (Unified in row)
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (urlInput.trim().isNotEmpty()) {
                                    viewModel.analyzeUrl(urlInput, selectedPreset)
                                } else {
                                    clipboardManager.getText()?.text?.let { text ->
                                        urlInput = text
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(120.dp)
                        ) {
                            if (analysisState is AnalysisState.Analyzing) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Text(
                                    text = if (urlInput.isEmpty()) "Paste Link" else "Start\nDownload",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // 3. Horizontal Quality Pills Selection -- now a real preference
                // that's actually sent to the resolver, not decoration.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QualityPreset.ALL.forEach { option ->
                        val isSelected = option == selectedPreset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0x20E07A2B) else BgPanel)
                                .then(
                                    if (isSelected) Modifier.border(1.dp, AccentOrange, RoundedCornerShape(6.dp))
                                    else Modifier.border(0.5.dp, BorderColor, RoundedCornerShape(6.dp))
                                )
                                .clickable { selectedPreset = option }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                option.label,
                                color = if (isSelected) AccentOrange else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 4. Active Downloads List Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Downloads",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedIds.isNotEmpty()) {
                        TextButton(
                            onClick = { showDeleteDialog = selectedIds },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = RedFailed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete (${selectedIds.size})", color = RedFailed, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (downloads.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No active or historical tasks.", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                items(downloads) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgPanel),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.status == "completed") {
                                        openVideoFile(context, item.localPath)
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.id in selectedIds,
                                onCheckedChange = {
                                    selectedIds = if (item.id in selectedIds) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AccentOrange),
                                modifier = Modifier.size(24.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))

                            // Thumbnail with Status-Colored Glow matching mockup
                            val statusColor = when (item.status) {
                                "downloading" -> AccentBlue
                                "completed" -> GreenActive
                                "failed" -> RedFailed
                                else -> AccentOrange // Waiting/Verifying
                            }

                            Box(
                                modifier = Modifier
                                    .size(80.dp, 48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .statusGlow(statusColor, 6.dp)
                                    .background(Color(0x10FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                val fallbackIcon = when (item.type) {
                                    "youtube", "video" -> Icons.Default.PlayCircle
                                    "audio" -> Icons.Default.MusicNote
                                    "google-drive", "dropbox" -> Icons.Default.Cloud
                                    else -> Icons.Default.InsertDriveFile
                                }
                                if (item.thumbnail.isNotEmpty()) {
                                    SubcomposeAsyncImage(
                                        model = item.thumbnail,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        loading = {
                                            Icon(imageVector = fallbackIcon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                                        },
                                        error = {
                                            // Broken/expired thumbnail URL -- same fallback as "no thumbnail at all",
                                            // never leaves a blank box.
                                            Icon(imageVector = fallbackIcon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                                        }
                                    )
                                } else {
                                    Icon(
                                        imageVector = fallbackIcon,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Right details column
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        text = if (item.status == "completed") "100%" else "${item.progress.toInt()}%",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))

                                // Thin progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0x10FFFFFF))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(item.progress / 100f)
                                            .background(statusColor)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Info Stats Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val sizeStr = if (item.totalSize > 0) {
                                        val cur = formatBytes(item.downloadedSize)
                                        val tot = formatBytes(item.totalSize)
                                        "$cur / $tot"
                                    } else {
                                        formatBytes(item.downloadedSize)
                                    }
                                    
                                    val speedEta = when (item.status) {
                                        "downloading" -> "${item.speed}  ·  ${item.eta}"
                                        "completed" -> "Finished"
                                        "failed" -> "Failed"
                                        else -> "Waiting"
                                    }

                                    Text(
                                        text = "$sizeStr    $speedEta",
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Status text & dot
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(statusColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when (item.status) {
                                            "downloading" -> "Downloading"
                                            "completed" -> "Finished"
                                            "failed" -> "Failed"
                                            else -> "Verifying"
                                        },
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
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
