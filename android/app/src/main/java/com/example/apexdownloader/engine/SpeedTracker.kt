package com.example.apexdownloader.engine

import java.util.ArrayDeque

/**
 * Tracks download throughput using a sliding time window instead of a
 * cumulative average since the download started.
 *
 * Why this matters: with `speed = totalBytesRead / secondsSinceStart`, a fast
 * first few seconds (e.g. while a CDN edge cache is warm) drags the reported
 * speed up for the rest of the download even after throughput drops, and a
 * slow start does the opposite. The reported number stops reflecting what's
 * actually happening right now. A sliding window (here: the last ~4 seconds
 * of samples) tracks current throughput instead, which is both more accurate
 * and gives a more useful ETA.
 */
class SpeedTracker(private val windowMillis: Long = 4000L) {
    private data class Sample(val timeMs: Long, val totalBytes: Long)

    private val samples = ArrayDeque<Sample>()

    /** Record a new (time, cumulative bytes read) data point and evict samples older than the window. */
    fun record(nowMs: Long, totalBytesRead: Long) {
        samples.addLast(Sample(nowMs, totalBytesRead))
        while (samples.isNotEmpty() && nowMs - samples.first().timeMs > windowMillis) {
            samples.removeFirst()
        }
    }

    /** Current throughput in bytes/sec, based on the oldest and newest samples still in the window. */
    fun currentBytesPerSecond(): Double {
        if (samples.size < 2) return 0.0
        val oldest = samples.first()
        val newest = samples.last()
        val elapsedSec = (newest.timeMs - oldest.timeMs) / 1000.0
        if (elapsedSec <= 0.0) return 0.0
        return (newest.totalBytes - oldest.totalBytes) / elapsedSec
    }

    fun reset() {
        samples.clear()
    }
}
