package com.example.apexdownloader.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.theme.AccentBlue
import com.example.apexdownloader.theme.AccentOrange
import com.example.apexdownloader.theme.BgDark
import com.example.apexdownloader.theme.GreenActive
import com.example.apexdownloader.theme.RedFailed
import kotlinx.coroutines.delay

/**
 * Mirrors the web app's ambient background: an overall page "mood", not a
 * per-item status indicator (individual download cards already have their
 * own colored glow for that). One state at a time, same priority as web:
 * analyzing > actively downloading > a brief flash for the most recent
 * complete/fail event > idle default.
 */
enum class AmbientState { Idle, Analyzing, Downloading, Completed, Failed }

/**
 * Derives the current [AmbientState] from real app state, including a
 * temporary "flash" when a download newly completes or fails -- detected by
 * diffing this composition's previous snapshot of (id, status) pairs against
 * the current one, so it fires once per transition rather than continuously
 * for as long as a completed item happens to exist in the list.
 */
@Composable
fun rememberAmbientState(
    isAnalyzing: Boolean,
    downloads: List<DownloadItem>
): AmbientState {
    var flashState by remember { mutableStateOf<AmbientState?>(null) }
    val previousStatuses = remember { mutableMapOf<String, String>() }

    // Keyed on just the (id, status) pairs -- NOT the full downloads list --
    // so this only re-runs when a status actually transitions, not on every
    // progress-percentage tick during an active download.
    val statusSnapshot = downloads.map { it.id to it.status }
    LaunchedEffect(statusSnapshot) {
        for ((id, status) in statusSnapshot) {
            val previous = previousStatuses[id]
            if (previous != status) {
                when (status) {
                    "completed" -> flashState = AmbientState.Completed
                    "failed" -> flashState = AmbientState.Failed
                }
            }
            previousStatuses[id] = status
        }
    }

    // Separate effect keyed on flashState itself, so a download-list update
    // (e.g. a progress tick on some OTHER item) can't cancel/restart this
    // timer before the flash has actually finished showing.
    LaunchedEffect(flashState) {
        if (flashState != null) {
            delay(2500)
            flashState = null
        }
    }

    return when {
        isAnalyzing -> AmbientState.Analyzing
        flashState != null -> flashState!!
        downloads.any { it.status == "downloading" } -> AmbientState.Downloading
        else -> AmbientState.Idle
    }
}

private data class BlobSpec(val durationMs: Int, val sizeDp: Int, val alignment: Alignment, val baseOffsetYDp: Int)

// Different durations per blob (not different start delays -- Compose's tween
// delay doesn't behave like CSS's negative animation-delay trick) are enough
// on their own to keep the three blobs organically out of phase with each
// other, since they drift in and out of sync naturally over time.
private val blobSpecs = listOf(
    BlobSpec(durationMs = 11000, sizeDp = 320, alignment = Alignment.TopStart, baseOffsetYDp = -110),
    BlobSpec(durationMs = 13500, sizeDp = 300, alignment = Alignment.TopEnd, baseOffsetYDp = -95),
    BlobSpec(durationMs = 15500, sizeDp = 260, alignment = Alignment.TopCenter, baseOffsetYDp = -80),
)

/** Idle uses three different colors (brand blend); every other state uses one color for all three blobs. */
private fun colorsForState(state: AmbientState): List<Color> = when (state) {
    AmbientState.Idle -> listOf(AccentOrange, AccentBlue, Color(0xFFA855F7))
    AmbientState.Analyzing -> listOf(Color(0xFF8B5CF6), Color(0xFF8B5CF6), Color(0xFF8B5CF6))
    AmbientState.Downloading -> listOf(AccentOrange, AccentOrange, AccentOrange)
    AmbientState.Completed -> listOf(GreenActive, GreenActive, GreenActive)
    AmbientState.Failed -> listOf(RedFailed, RedFailed, RedFailed)
}

private fun speedMultiplierForState(state: AmbientState): Float = when (state) {
    AmbientState.Idle -> 1f
    AmbientState.Analyzing -> 1.6f
    AmbientState.Downloading -> 1.3f
    AmbientState.Completed, AmbientState.Failed -> 1.8f
}

@Composable
fun AmbientGlowBackground(state: AmbientState, modifier: Modifier = Modifier) {
    val targetColors = colorsForState(state)
    val speedMultiplier = speedMultiplierForState(state)

    // Smooth crossfade whenever the state (and therefore target color) changes.
    val color1 by animateColorAsState(targetColors[0], animationSpec = tween(1200), label = "ambientColor1")
    val color2 by animateColorAsState(targetColors[1], animationSpec = tween(1200), label = "ambientColor2")
    val color3 by animateColorAsState(targetColors[2], animationSpec = tween(1200), label = "ambientColor3")
    val colors = listOf(color1, color2, color3)

    val infiniteTransition = rememberInfiniteTransition(label = "ambientDrift")

    Box(modifier = modifier.fillMaxSize()) {
        blobSpecs.forEachIndexed { index, spec ->
            val duration = (spec.durationMs / speedMultiplier).toInt().coerceAtLeast(1500)
            val drift by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ambientDrift$index"
            )
            // Drift moves the blob a modest amount and breathes its size slightly,
            // similar in spirit to the web version's translate+scale keyframes.
            // baseOffsetYDp pulls the blob mostly above the visible area, so only
            // a soft glow spills down from the top edge -- matches the web
            // version's negative top percentage.
            val offsetX = (drift * 36 - 18).dp
            val offsetY = spec.baseOffsetYDp.dp + (drift * 28 - 14).dp
            val sizeBreathe = (spec.sizeDp * (0.94f + drift * 0.12f)).dp

            Box(
                modifier = Modifier
                    .align(spec.alignment)
                    .offset(x = offsetX, y = offsetY)
                    .size(sizeBreathe)
                    .blur(70.dp)
                    .background(colors[index].copy(alpha = 0.30f), CircleShape)
            )
        }

        // Fade mask: dissolves the glow to the solid dark background by
        // roughly mid-screen, so it reads as a top-anchored wash rather than
        // depending purely on blur falloff to look clean lower down.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.15f to Color.Transparent,
                        0.62f to BgDark
                    )
                )
        )
    }
}
