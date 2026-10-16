package com.example.apexdownloader.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.apexdownloader.MainActivity
import kotlin.concurrent.thread
import kotlin.math.exp
import kotlin.math.sin

object DownloadNotifications {
    const val CHANNEL_PROGRESS_ID = "hk_downloads_progress"
    const val CHANNEL_COMPLETE_ID = "hk_downloads_complete"
    private const val CHANNEL_PROGRESS_NAME = "Active Downloads"
    private const val CHANNEL_COMPLETE_NAME = "Completed Downloads"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            if (manager.getNotificationChannel(CHANNEL_PROGRESS_ID) == null) {
                val progressChannel = NotificationChannel(
                    CHANNEL_PROGRESS_ID,
                    CHANNEL_PROGRESS_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live progress bar for active media downloads"
                    setShowBadge(true)
                    setSound(null, null)
                    enableVibration(false)
                }
                manager.createNotificationChannel(progressChannel)
            }

            if (manager.getNotificationChannel(CHANNEL_COMPLETE_ID) == null) {
                val completeChannel = NotificationChannel(
                    CHANNEL_COMPLETE_ID,
                    CHANNEL_COMPLETE_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when a download finishes"
                    setShowBadge(true)
                    // Silent channel sound because we play our own tiny subtle crystal chime
                    setSound(null, null)
                    enableVibration(false)
                }
                manager.createNotificationChannel(completeChannel)
            }
        }
    }

    fun notificationIdFor(downloadId: String): Int = (downloadId.hashCode() and 0x7FFFFFFF).coerceAtLeast(1001)

    private fun openAppIntent(context: Context, downloadId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("download_id", downloadId)
        }
        return PendingIntent.getActivity(
            context,
            downloadId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun buildProgressNotification(
        context: Context,
        downloadId: String,
        title: String,
        progressPercent: Int,
        statusText: String,
        indeterminate: Boolean = false
    ): android.app.Notification {
        val cleanTitle = title.ifEmpty { "Downloading Media..." }
        val pct = progressPercent.coerceIn(0, 100)
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setContentTitle(cleanTitle)
            .setContentText(if (indeterminate) statusText else "$pct% • $statusText")
            .setSubText("HK Downloader Pro")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openAppIntent(context, downloadId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, pct, indeterminate)
            .build()
    }

    fun buildFinishedNotification(
        context: Context,
        downloadId: String,
        title: String,
        statusText: String,
        success: Boolean
    ): android.app.Notification {
        val cleanTitle = title.ifEmpty { "Media Download" }
        return NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
            .setContentTitle(if (success) "✓ Download Complete" else "Download Failed")
            .setContentText("$cleanTitle • $statusText")
            .setSubText("HK Downloader Pro")
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentIntent(openAppIntent(context, downloadId))
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(0)
            .build()
    }

    /**
     * Plays a very tiny, subtle, pleasant two-note crystal chime (130ms total, 15% volume)
     * when a download completes.
     */
    fun playTinyCompletionChime() {
        thread(start = true, isDaemon = true, name = "TinyCompletionChime") {
            var track: AudioTrack? = null
            try {
                val sampleRate = 24000
                val durationMs = 140
                val numSamples = (sampleRate * durationMs) / 1000
                val pcm = ShortArray(numSamples)

                val splitSample = (sampleRate * 55) / 1000 // First note 55ms, second note 85ms
                val freq1 = 880.0   // A5
                val freq2 = 1318.5  // E6 (perfect fifth higher)
                val maxAmp = 0.14 * Short.MAX_VALUE // Very tiny, soft volume (~14%)

                for (i in 0 until numSamples) {
                    val isSecondNote = i >= splitSample
                    val localIdx = if (isSecondNote) (i - splitSample) else i
                    val localLen = if (isSecondNote) (numSamples - splitSample) else splitSample
                    val freq = if (isSecondNote) freq2 else freq1

                    val t = i.toDouble() / sampleRate
                    // Smooth attack (4ms) + exponential decay envelope
                    val attackSamples = (sampleRate * 0.004).toInt().coerceAtLeast(1)
                    val attack = if (localIdx < attackSamples) localIdx.toDouble() / attackSamples else 1.0
                    val decay = exp(-4.8 * (localIdx.toDouble() / localLen))

                    val sample = sin(2.0 * Math.PI * freq * t) * attack * decay * maxAmp
                    pcm[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val bufferSize = (pcm.size * 2).coerceAtLeast(
                    AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                )

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 60L)
            } catch (_: Throwable) {
            } finally {
                try {
                    track?.stop()
                } catch (_: Throwable) {}
                try {
                    track?.release()
                } catch (_: Throwable) {}
            }
        }
    }
}
