package com.example.apexdownloader.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.apexdownloader.MainActivity

object DownloadNotifications {
    const val CHANNEL_ID = "apex_downloads"
    private const val CHANNEL_NAME = "Downloads"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW // low: progress ticks shouldn't buzz the phone repeatedly
                ).apply {
                    description = "Shows progress for active downloads"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * notificationId must be stable per download for the life of that download
     * (WorkManager foreground notifications are keyed by this id), but distinct
     * across concurrent downloads or they'll overwrite each other.
     */
    fun notificationIdFor(downloadId: String): Int = downloadId.hashCode()

    private fun openAppIntent(context: Context, downloadId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // requestCode keyed by download id so each download's tap target is
        // distinct rather than every notification sharing/overwriting one PendingIntent.
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
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openAppIntent(context, downloadId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), indeterminate)
            .build()
    }

    fun buildFinishedNotification(
        context: Context,
        downloadId: String,
        title: String,
        statusText: String,
        success: Boolean
    ): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentIntent(openAppIntent(context, downloadId))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }
}
