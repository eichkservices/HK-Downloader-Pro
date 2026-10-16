package com.example.apexdownloader.engine

import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.data.DownloadRepositoryProvider
import kotlinx.coroutines.CancellationException

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

        if (repository.isDeleted(itemId) || repository.isPaused(itemId)) {
            return Result.success()
        }

        DownloadNotifications.ensureChannel(applicationContext)
        val notificationId = DownloadNotifications.notificationIdFor(itemId)

        val title = inputData.getString(KEY_TITLE) ?: "Download"
        try {
            setForeground(
                ForegroundInfo(
                    notificationId,
                    DownloadNotifications.buildProgressNotification(applicationContext, itemId, title, 0, "Starting...", indeterminate = true),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
                )
            )
        } catch (e: Exception) {
            // Silently fallback if foreground service permission/type is restricted on device
        }

        val desktopServerUrl = inputData.getString(KEY_DESKTOP_SERVER)?.ifEmpty { null }
        val formatId = inputData.getString(KEY_FORMAT_ID)?.ifEmpty { null }

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
            if (repository.isDeleted(itemId) || repository.isPaused(itemId)) return@DownloadExecutor
            val statusText = "${updated.speed} · ${updated.eta}"
            try {
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
            } catch (e: Exception) {
                // Ignore foreground notification update failure
            }
        }

        fun postFinishedNotification(success: Boolean, statusText: String, finalTitle: String) {
            try {
                val notification = DownloadNotifications.buildFinishedNotification(
                    applicationContext, itemId, finalTitle, statusText, success
                )
                NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
            } catch (e: SecurityException) {
            }
        }

        return try {
            executor.execute(item, desktopServerUrl, formatId)
            if (!repository.isDeleted(itemId) && !repository.isPaused(itemId)) {
                postFinishedNotification(success = true, statusText = "Finished", finalTitle = item.title)
            }
            Result.success()
        } catch (e: CancellationException) {
            NotificationManagerCompat.from(applicationContext).cancel(notificationId)
            Result.success()
        } catch (e: Exception) {
            if (repository.isDeleted(itemId) || repository.isPaused(itemId) || isStopped) {
                NotificationManagerCompat.from(applicationContext).cancel(notificationId)
                return Result.success()
            }
            val message = e.message ?: "Unknown error"
            repository.addOrUpdateDownload(
                item.copy(status = "failed", speed = "Error", eta = message)
            )
            postFinishedNotification(success = false, statusText = "Failed: $message", finalTitle = item.title)
            Result.failure()
        }
    }
}
