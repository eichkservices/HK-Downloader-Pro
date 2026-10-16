package com.example.apexdownloader.ui.components

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.theme.*
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun HkPlayerModal(
    item: DownloadItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val file = remember(item.localPath) { File(item.localPath) }
    val isAudio = remember(item) {
        item.type == "audio" ||
            item.localPath.endsWith(".mp3", ignoreCase = true) ||
            item.localPath.endsWith(".m4a", ignoreCase = true) ||
            item.localPath.endsWith(".opus", ignoreCase = true) ||
            item.localPath.endsWith(".wav", ignoreCase = true)
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(!file.exists()) }

    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var seekFlashText by remember { mutableStateOf<String?>(null) }

    val speeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
    var speedIndex by remember { mutableIntStateOf(2) }

    val aspectLabels = remember { listOf("Fit Auto", "Fill", "Stretch") }
    var aspectIndex by remember { mutableIntStateOf(0) }

    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    // Audio-only MediaPlayer lifecycle
    DisposableEffect(item.localPath, isAudio) {
        var audioPlayer: MediaPlayer? = null
        if (isAudio && file.exists()) {
            try {
                audioPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnPreparedListener { mp ->
                        durationMs = mp.duration.coerceAtLeast(1)
                        mp.start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = durationMs
                    }
                    setOnErrorListener { _, _, _ ->
                        hasError = true
                        true
                    }
                    prepareAsync()
                }
                mediaPlayerRef = audioPlayer
            } catch (e: Exception) {
                hasError = true
            }
        }
        onDispose {
            try {
                audioPlayer?.stop()
                audioPlayer?.release()
            } catch (e: Exception) {}
            mediaPlayerRef = null
        }
    }

    // Poll playback position every 200ms while playing
    LaunchedEffect(isPlaying, mediaPlayerRef, videoViewRef) {
        while (true) {
            try {
                if (isAudio) {
                    val mp = mediaPlayerRef
                    if (mp != null && mp.isPlaying) {
                        currentPositionMs = mp.currentPosition
                        if (mp.duration > 0) durationMs = mp.duration
                    }
                } else {
                    val vv = videoViewRef
                    if (vv != null && vv.isPlaying) {
                        currentPositionMs = vv.currentPosition
                        if (vv.duration > 0) durationMs = vv.duration
                    }
                }
            } catch (e: Exception) {}
            delay(200)
        }
    }

    // Clear seek flash label after 650ms
    LaunchedEffect(seekFlashText) {
        if (seekFlashText != null) {
            delay(650)
            seekFlashText = null
        }
    }

    fun seekToMs(targetMs: Int) {
        val clamped = targetMs.coerceIn(0, durationMs.coerceAtLeast(1))
        currentPositionMs = clamped
        try {
            if (isAudio) {
                mediaPlayerRef?.seekTo(clamped)
            } else {
                videoViewRef?.seekTo(clamped)
            }
        } catch (e: Exception) {}
    }

    fun togglePlayPause() {
        if (isLocked) return
        try {
            if (isAudio) {
                val mp = mediaPlayerRef ?: return
                if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                } else {
                    mp.start()
                    isPlaying = true
                }
            } else {
                val vv = videoViewRef ?: return
                if (vv.isPlaying) {
                    vv.pause()
                    isPlaying = false
                } else {
                    vv.start()
                    isPlaying = true
                }
            }
        } catch (e: Exception) {}
    }

    fun applySpeed(newSpeed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val mp = mediaPlayerRef ?: return
                val params = PlaybackParams().setSpeed(newSpeed)
                mp.playbackParams = params
                isPlaying = true
            } catch (e: Exception) {}
        }
    }

    fun applyMute(mute: Boolean) {
        try {
            val vol = if (mute) 0f else 1f
            mediaPlayerRef?.setVolume(vol, vol)
            isMuted = mute
        } catch (e: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD9030406))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0C12)),
                border = BorderStroke(1.dp, GlassBorderStrong),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
                    .clickable(enabled = false) {}
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .background(Color.Black)
                ) {
                    // 1. Video or Audio Surface
                    if (!hasError && file.exists()) {
                        if (isAudio) {
                            // Audio Player View (matching web #audioView)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0x33FF8A3D), Color(0xFF080A10))
                                        )
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x22FF8A3D))
                                        .border(2.dp, AccentOrange.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = item.title.ifEmpty { "Audio Playback" },
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "High Quality Audio Stream",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        videoViewRef = this
                                        setVideoPath(file.absolutePath)
                                        setOnPreparedListener { mp ->
                                            mediaPlayerRef = mp
                                            durationMs = mp.duration.coerceAtLeast(1)
                                            mp.start()
                                            isPlaying = true
                                        }
                                        setOnCompletionListener {
                                            isPlaying = false
                                            currentPositionMs = durationMs
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            hasError = true
                                            true
                                        }
                                    }
                                },
                                modifier = when (aspectIndex) {
                                    1 -> Modifier.fillMaxSize()
                                    2 -> Modifier.fillMaxSize()
                                    else -> Modifier
                                        .fillMaxSize()
                                        .align(Alignment.Center)
                                }
                            )
                        }
                    }

                    // 2. Error Fallback Overlay (matching web #playerErrorOverlay)
                    if (hasError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xEE0A0C12))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = RedFailed,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Playback Format Not Supported",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Open in an external player such as VLC or your device's media player.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { openMediaExternally(context, item.localPath) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF1A0B02), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open in External Player", color = Color(0xFF1A0B02), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // 3. Touch Gesture Layer (Tap to toggle controls, Double-tap left -10s / right +10s)
                    if (!hasError) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pointerInput(isLocked, durationMs) {
                                        detectTapGestures(
                                            onTap = { showControls = !showControls },
                                            onDoubleTap = {
                                                if (!isLocked) {
                                                    seekToMs(currentPositionMs - 10_000)
                                                    seekFlashText = "⏪ -10s"
                                                }
                                            }
                                        )
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pointerInput(isLocked, durationMs) {
                                        detectTapGestures(
                                            onTap = { showControls = !showControls },
                                            onDoubleTap = {
                                                if (!isLocked) {
                                                    seekToMs(currentPositionMs + 10_000)
                                                    seekFlashText = "⏩ +10s"
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                    }

                    // Double-tap seek feedback pill
                    seekFlashText?.let { flash ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color(0xCC06070A))
                                .border(1.dp, AccentOrange, RoundedCornerShape(100.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = flash,
                                color = AccentOrange,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 4. MX Player Controls Overlay
                    if (showControls && !hasError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xCC06070A),
                                            Color(0x33000000),
                                            Color(0xE606070A)
                                        )
                                    )
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Bar: Badge + Title + Lock + Close
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x28FF8A3D))
                                            .border(1.dp, AccentOrange.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "HK PLAYER PRO",
                                            color = AccentOrange,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Text(
                                        text = item.title.ifEmpty { "Playing Media" },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Lock button
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (isLocked) AccentOrange.copy(alpha = 0.25f) else GlassBgLight)
                                            .border(1.dp, if (isLocked) AccentOrange else GlassBorder, CircleShape)
                                            .clickable { isLocked = !isLocked },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = "Lock",
                                            tint = if (isLocked) AccentOrange else Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    // Close button
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(GlassBgLight)
                                            .border(1.dp, GlassBorder, CircleShape)
                                            .clickable { onDismiss() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Center Hub: Rewind 10s | Play/Pause | Forward 10s
                            if (!isLocked) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Rewind 10s
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color(0x990D1017))
                                            .border(1.dp, GlassBorderStrong, RoundedCornerShape(100.dp))
                                            .clickable {
                                                seekToMs(currentPositionMs - 10_000)
                                                seekFlashText = "⏪ -10s"
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("10s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(20.dp))

                                    // Play/Pause
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(AccentOrange, AccentOrangeEnd)))
                                            .clickable { togglePlayPause() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color(0xFF1A0B02),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(20.dp))

                                    // Forward 10s
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color(0x990D1017))
                                            .border(1.dp, GlassBorderStrong, RoundedCornerShape(100.dp))
                                            .clickable {
                                                seekToMs(currentPositionMs + 10_000)
                                                seekFlashText = "⏩ +10s"
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("10s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Default.FastForward, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // Bottom Dock: Seekbar + Time + Speed + Aspect + External + Share
                            if (!isLocked) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Slider(
                                        value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                                        onValueChange = { ratio ->
                                            if (durationMs > 0) {
                                                seekToMs((ratio * durationMs).toInt())
                                            }
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = AccentOrange,
                                            activeTrackColor = AccentOrange,
                                            inactiveTrackColor = Color(0x33FFFFFF)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Left: Mute + Time
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(GlassBgLight)
                                                    .clickable { applyMute(!isMuted) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                    contentDescription = "Mute",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }

                                            Text(
                                                text = "${formatDurationMs(currentPositionMs)} / ${formatDurationMs(durationMs)}",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // Right: Speed + Aspect + Open With + Share
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Speed pill
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(GlassBgLight)
                                                    .border(1.dp, GlassBorder, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        speedIndex = (speedIndex + 1) % speeds.size
                                                        applySpeed(speeds[speedIndex])
                                                    }
                                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "${speeds[speedIndex]}x",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // Aspect Ratio pill
                                            if (!isAudio) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(GlassBgLight)
                                                        .border(1.dp, GlassBorder, RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            aspectIndex = (aspectIndex + 1) % aspectLabels.size
                                                        }
                                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = aspectLabels[aspectIndex],
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            // Open With System Player
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(GlassBgLight)
                                                    .border(1.dp, GlassBorder, CircleShape)
                                                    .clickable { openMediaExternally(context, item.localPath) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.OpenInNew,
                                                    contentDescription = "Open With",
                                                    tint = AccentCyan,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }

                                            // Share
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(GlassBgLight)
                                                    .border(1.dp, GlassBorder, CircleShape)
                                                    .clickable { shareMediaExternally(context, item.localPath) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Share",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%02d:%02d", m, s)
}

fun openMediaExternally(context: Context, filePath: String) {
    if (filePath.isEmpty()) {
        android.widget.Toast.makeText(context, "File path not found", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val file = File(filePath)
        if (!file.exists()) {
            android.widget.Toast.makeText(context, "File not found on storage", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "com.example.apexdownloader.fileprovider",
            file
        )
        val isAudio = filePath.endsWith(".mp3", true) || filePath.endsWith(".m4a", true) || filePath.endsWith(".opus", true)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (isAudio) "audio/*" else "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Open Media With").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Unable to open external player: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun shareMediaExternally(context: Context, filePath: String) {
    if (filePath.isEmpty()) return
    try {
        val file = File(filePath)
        if (!file.exists()) {
            android.widget.Toast.makeText(context, "File not found on storage", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "com.example.apexdownloader.fileprovider",
            file
        )
        val isAudio = filePath.endsWith(".mp3", true) || filePath.endsWith(".m4a", true) || filePath.endsWith(".opus", true)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isAudio) "audio/*" else "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share Media Via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Unable to share file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
