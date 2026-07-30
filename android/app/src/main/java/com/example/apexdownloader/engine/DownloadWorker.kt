package com.example.apexdownloader.engine

import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.data.DownloadRepositoryProvider

class DownloadWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_ITEM_ID = "item_id"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_FILENAME = "filename"
        const val KEY_TYPE = "type"
        const val KEY_THUMBNAIL = "thumbnail"
        const val KEY_FORMAT_ID = "format_id"
        const val KEY_DESKTOP_SERVER = "desktop_server"
    }

    override suspend fun doWork(): Result {
        val repository = DownloadRepositoryProvider.get(applicationContext)
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()

        DownloadNotifications.ensureChannel(applicationContext)
        val notificationId = DownloadNotifications.notificationIdFor(itemId)

        // Start in the foreground immediately with an indeterminate notification;
        // the executor's onProgress callback below will replace it with real progress.
        val title = inputData.getString(KEY_TITLE) ?: "Download"
        setForeground(
            ForegroundInfo(
                notificationId,
                DownloadNotifications.buildProgressNotification(applicationContext, itemId, title, 0, "Starting...", indeterminate = true),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        )

        val desktopServerUrl = inputData.getString(KEY_DESKTOP_SERVER)?.ifEmpty { null }
        val formatId = inputData.getString(KEY_FORMAT_ID)?.ifEmpty { null }

        // Pull the current, most up-to-date copy of the item (it may already carry
        // progress from a prior partial attempt) rather than trusting only the
        // small set of fields WorkManager's input Data can hold.
        val existing = repository.downloads.value.find { it.id == itemId }
        val item = existing ?: DownloadItem(
            id = itemId,
            title = title,
            filename = inputData.getString(KEY_FILENAME) ?: title,
            url = inputData.getString(KEY_URL) ?: return Result.failure(),
            type = inputData.getString(KEY_TYPE) ?: "direct-link",
            formatId = formatId ?: "",
            thumbnail = inputData.getString(KEY_THUMBNAIL) ?: ""
        )

        val executor = DownloadExecutor(applicationContext, repository) { updated ->
            val statusText = "${updated.speed} · ${updated.eta}"
            setForeground(
                ForegroundInfo(
                    notificationId,
                    DownloadNotifications.buildProgressNotification(
                        applicationContext,
                        itemId,
                        updated.title,
                        updated.progress.toInt(),
                        statusText
                    ),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
                )
            )
        }

        // The foreground notification above disappears the moment doWork()
        // returns (that's how stopping a foreground service works) -- so a
        // "finished" state needs its own, separate, non-foreground notify()
        // call to actually stick around for the user to see and tap.
        fun postFinishedNotification(success: Boolean, statusText: String, finalTitle: String) {
            try {
                val notification = DownloadNotifications.buildFinishedNotification(
                    applicationContext, itemId, finalTitle, statusText, success
                )
                NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
            } catch (e: SecurityException) {
                // POST_NOTIFICATIONS not granted -- the download itself still
                // succeeded/failed correctly, the user just won't see a system
                // notification about it (they'll see it in-app instead).
            }
        }

        return try {
            executor.execute(item, desktopServerUrl, formatId)
            postFinishedNotification(success = true, statusText = "Finished", finalTitle = item.title)
            Result.success()
        } catch (e: Exception) {
            val message = e.message ?: "Unknown error"
            repository.addOrUpdateDownload(
                item.copy(status = "failed", speed = "Error", eta = message)
            )
            postFinishedNotification(success = false, statusText = "Failed: $message", finalTitle = item.title)
            Result.failure()
        }
    }
}
