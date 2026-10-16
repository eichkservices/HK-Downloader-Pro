package com.example.apexdownloader.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.theme.*
import com.example.apexdownloader.ui.main.MainScreenViewModel
import java.io.File

@Composable
fun LibraryScreen(
    viewModel: MainScreenViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsState()
    val completedItems = remember(downloads) {
        downloads.filter { it.status == "completed" }
    }
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }

    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Media File", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete \"${target.title}\" and remove its downloaded file from device storage?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownloads(setOf(target.id), deleteFiles = true)
                        itemToDelete = null
                    }
                ) {
                    Text("Delete File", color = RedFailed, fontWeight = FontWeight.Bold)
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
        // Section Header (Matching Web App .section-header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MEDIA LIBRARY",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0x1F22D3EE))
                        .border(1.dp, Color(0x4022D3EE), RoundedCornerShape(100.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${completedItems.size} Files",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Quick Settings Action
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (completedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
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
                        text = "Your library is empty. Downloaded files will appear here!",
                        color = TextTertiary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // 2-Column Grid (matching web .library-grid)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(completedItems, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = BgPanel),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openVideoFile(context, item.localPath)
                            }
                    ) {
                        Column {
                            // Thumbnail with 16:10 aspect ratio and play overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.6f)
                                    .background(Color(0xFF141822)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.thumbnail.isNotEmpty()) {
                                    SubcomposeAsyncImage(
                                        model = item.thumbnail,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (item.type == "audio") Icons.Default.MusicNote else Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                // Dark gradient & play pill
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color(0x66000000))
                                            )
                                        )
                                )

                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xB3000000))
                                        .border(1.dp, Color(0x33FFFFFF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Format badge
                                val ext = when {
                                    item.type == "audio" -> "MP3"
                                    item.localPath.endsWith(".mp3", true) -> "MP3"
                                    item.formatId.contains("1080") -> "1080p"
                                    item.formatId.contains("720") -> "720p"
                                    else -> "MP4"
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xCC06070A))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = ext,
                                        color = AccentCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Card Info & Bottom Row
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = item.title.ifEmpty { "Downloaded Media" },
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val sizeText = if (item.downloadedSize > 0) {
                                        formatBytes(item.downloadedSize)
                                    } else if (item.totalSize > 0) {
                                        formatBytes(item.totalSize)
                                    } else "--"

                                    Text(
                                        text = sizeText,
                                        color = TextTertiary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Share Button
                                        IconButton(
                                            onClick = { shareVideoFile(context, item.localPath) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        // Delete Button
                                        IconButton(
                                            onClick = { itemToDelete = item },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = RedFailed.copy(alpha = 0.8f),
                                                modifier = Modifier.size(14.dp)
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
            val isAudio = filePath.endsWith(".mp3", true) || filePath.endsWith(".m4a", true)
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
