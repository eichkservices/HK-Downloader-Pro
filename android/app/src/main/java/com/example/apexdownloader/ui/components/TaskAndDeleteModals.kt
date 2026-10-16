package com.example.apexdownloader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.theme.*

@Composable
fun TaskDownloadModal(
    item: DownloadItem,
    onMinimize: () -> Unit,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onPlayInApp: () -> Unit
) {
    val context = LocalContext.current
    val isCompleted = item.status == "completed"
    val isFailed = item.status == "failed"
    val isPaused = item.status == "paused"

    val targetFraction = when {
        isCompleted -> 1f
        item.progress > 0f -> (item.progress / 100f).coerceIn(0.01f, 1f)
        item.status == "downloading" -> 0.04f
        else -> 0f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = "modalProgress"
    )

    Dialog(
        onDismissRequest = onMinimize,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBF030406))
                .clickable(onClick = onMinimize),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = BgPanel),
                border = BorderStroke(1.dp, GlassBorderStrong),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {}
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 1. Modal Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val dotColor = when {
                                isCompleted -> GreenActive
                                isFailed -> RedFailed
                                isPaused -> AccentCyan
                                else -> AccentOrange
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = when {
                                    isCompleted -> "Download Complete!"
                                    isFailed -> "Download Failed"
                                    isPaused -> "Download Paused"
                                    else -> "Downloading Media..."
                                },
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Minimize button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GlassBgLight)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onMinimize)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Minimize",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Cancel / Close button
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1FFB7185))
                                    .border(1.dp, RedFailed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isCompleted) onMinimize() else onStop()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = RedFailed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Unified Prominent Download Status Card (matches web .task-active-card)
                    val cardBorder = when {
                        isCompleted -> GreenActive.copy(alpha = 0.45f)
                        isFailed -> RedFailed.copy(alpha = 0.45f)
                        else -> AccentOrange.copy(alpha = 0.4f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF121622))
                            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            // Thumbnail + Title + Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1B202E))
                                        .border(1.dp, cardBorder, RoundedCornerShape(10.dp)),
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
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title.ifEmpty { "Media Download" },
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val qualityBadge = when {
                                            item.type == "audio" -> "MP3 AUDIO"
                                            item.formatId.contains("1080") -> "1080p MP4"
                                            item.formatId.contains("720") -> "720p MP4"
                                            else -> "HD MP4"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x1F22D3EE))
                                                .border(0.8.dp, Color(0x4522D3EE), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = qualityBadge,
                                                color = AccentCyan,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        val speedBadge = when {
                                            isCompleted -> "Saved to Device"
                                            isFailed -> item.eta.ifEmpty { "Failed" }
                                            isPaused -> item.speed.ifEmpty { "Paused" }
                                            item.eta.isNotEmpty() && item.eta != "--" -> "${item.speed} • ${item.eta}"
                                            else -> item.speed.ifEmpty { "Preparing stream..." }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x1FFF8A3D))
                                                .border(0.8.dp, Color(0x45FF8A3D), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = speedBadge,
                                                color = if (isCompleted) GreenActive else AccentOrange,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Progress Percentage & Size Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val pctText = when {
                                        isCompleted -> "100%"
                                        else -> "${item.progress.toInt().coerceIn(0, 100)}%"
                                    }
                                    Text(
                                        text = pctText,
                                        color = when {
                                            isCompleted -> GreenActive
                                            isFailed -> RedFailed
                                            else -> AccentOrange
                                        },
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(GlassBgLight)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isCompleted -> "DONE"
                                                isPaused -> "PAUSED"
                                                isFailed -> "ERROR"
                                                else -> "LIVE"
                                            },
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                val sizeReadout = when {
                                    isCompleted && item.downloadedSize > 0 -> fmtBytesModal(item.downloadedSize)
                                    item.totalSize > 0 -> "${fmtBytesModal(item.downloadedSize)} / ${fmtBytesModal(item.totalSize)}"
                                    item.downloadedSize > 0 -> fmtBytesModal(item.downloadedSize)
                                    else -> "Connecting..."
                                }
                                Text(
                                    text = sizeReadout,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Smooth Glowing Progress Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(Color(0x22FFFFFF))
                            ) {
                                val barBrush = when {
                                    isCompleted -> Brush.horizontalGradient(listOf(GreenActive, GreenActive))
                                    isFailed -> Brush.horizontalGradient(listOf(RedFailed, RedFailed))
                                    else -> Brush.horizontalGradient(listOf(AccentOrange, AccentOrangeEnd))
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedFraction)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(barBrush)
                                )
                            }

                            // Active Controls (Pause/Resume, Stop, Delete) when not completed
                            if (!isCompleted) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Pause / Resume Button
                                    Button(
                                        onClick = onPauseOrResume,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPaused || isFailed) AccentOrange else GlassBgLight
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPaused || isFailed) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            contentDescription = null,
                                            tint = if (isPaused || isFailed) Color(0xFF1A0B02) else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = when {
                                                isFailed -> "Retry"
                                                isPaused -> "Resume"
                                                else -> "Pause"
                                            },
                                            color = if (isPaused || isFailed) Color(0xFF1A0B02) else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Stop Button
                                    Button(
                                        onClick = onStop,
                                        colors = ButtonDefaults.buttonColors(containerColor = GlassBgLight),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = null,
                                            tint = AccentCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Stop",
                                            color = AccentCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Delete Button
                                    Button(
                                        onClick = onDelete,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1FFB7185)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = RedFailed,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Delete",
                                            color = RedFailed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Completion Actions (Matching web #taskCompleteActions)
                    if (isCompleted) {
                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1A34D399))
                                .border(1.dp, GreenActive.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenActive, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Download Complete & Ready!",
                                    color = GreenActive,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onPlayInApp,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF1A0B02), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play in HK Player", color = Color(0xFF1A0B02), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { openMediaExternally(context, item.localPath) },
                                colors = ButtonDefaults.buttonColors(containerColor = GlassBgLight),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("External App", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(GlassBg)
                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .clickable(onClick = onMinimize)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Done & Return",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmModal(
    item: DownloadItem,
    onDismiss: () -> Unit,
    onDeleteTaskOnly: () -> Unit,
    onDeleteTaskAndFile: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BgPanel),
            border = BorderStroke(1.dp, GlassBorderStrong),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Delete Confirmation",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "What would you like to delete for \"${item.title.ifEmpty { "this item" }}\"?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Delete Task Only
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassBgLight)
                        .border(1.dp, GlassBorderStrong, RoundedCornerShape(12.dp))
                        .clickable(onClick = onDeleteTaskOnly)
                        .padding(vertical = 12.dp, horizontal = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Delete Task Only (Keep File)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Delete Task & Video File
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1FFB7185))
                        .border(1.dp, RedFailed.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onDeleteTaskAndFile)
                        .padding(vertical = 12.dp, horizontal = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = RedFailed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Delete Task & Media File",
                            color = RedFailed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

fun formatBytesUi(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun fmtBytesModal(bytes: Long): String = formatBytesUi(bytes)
