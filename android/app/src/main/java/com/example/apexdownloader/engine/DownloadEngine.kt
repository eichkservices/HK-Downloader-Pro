package com.example.apexdownloader.engine

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.data.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DownloadEngine(
    private val context: Context,
    private val repository: DownloadRepository
) {
    companion object {
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val activeJobs = ConcurrentHashMap<String, Job>()
    }

    fun startDownload(
        item: DownloadItem,
        desktopServerUrl: String?,
        formatId: String? = null
    ) {
        // Cancel any previous job/call for this ID first
        activeJobs.remove(item.id)?.cancel()
        DownloadExecutor.cancelActiveCall(item.id)
        try {
            WorkManager.getInstance(context).cancelUniqueWork(item.id)
        } catch (e: Exception) {}

        repository.clearFlagsForStart(item.id)
        val startingItem = item.copy(
            status = "downloading",
            speed = if (item.downloadedSize > 0) "Resuming..." else "Connecting...",
            eta = "--"
        )
        repository.addOrUpdateDownload(startingItem, forceExplicit = true)

        DownloadNotifications.ensureChannel(context)
        val notificationId = DownloadNotifications.notificationIdFor(item.id)

        val job = engineScope.launch {
            val executor = DownloadExecutor(context, repository) { updated ->
                if (repository.isDeleted(item.id) || repository.isPaused(item.id)) return@DownloadExecutor
                val statusText = "${updated.speed} · ${updated.eta}"
                try {
                    val notification = DownloadNotifications.buildProgressNotification(
                        context,
                        item.id,
                        updated.title,
                        updated.progress.toInt(),
                        statusText
                    )
                    NotificationManagerCompat.from(context).notify(notificationId, notification)
                } catch (e: SecurityException) {
                } catch (e: Exception) {
                }
            }

            try {
                executor.execute(startingItem, desktopServerUrl, formatId ?: item.formatId)
                if (!repository.isDeleted(item.id) && !repository.isPaused(item.id)) {
                    try {
                        val notification = DownloadNotifications.buildFinishedNotification(
                            context, item.id, item.title, "Finished", true
                        )
                        NotificationManagerCompat.from(context).notify(notificationId, notification)
                    } catch (e: SecurityException) {}
                }
            } catch (e: CancellationException) {
                try {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                } catch (ex: Exception) {}
            } catch (e: Exception) {
                if (repository.isDeleted(item.id) || repository.isPaused(item.id)) {
                    try {
                        NotificationManagerCompat.from(context).cancel(notificationId)
                    } catch (ex: Exception) {}
                    return@launch
                }
                val message = e.message ?: "Download failed"
                val latest = repository.downloads.value.find { it.id == item.id } ?: startingItem
                repository.addOrUpdateDownload(
                    latest.copy(status = "failed", speed = "Error", eta = message)
                )
                try {
                    val notification = DownloadNotifications.buildFinishedNotification(
                        context, item.id, item.title, "Failed: $message", false
                    )
                    NotificationManagerCompat.from(context).notify(notificationId, notification)
                } catch (ex: SecurityException) {}
            } finally {
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    fun pauseDownload(id: String) {
        repository.markPaused(id)
        DownloadExecutor.cancelActiveCall(id)
        activeJobs.remove(id)?.cancel()
        try {
            WorkManager.getInstance(context).cancelUniqueWork(id)
            NotificationManagerCompat.from(context).cancel(DownloadNotifications.notificationIdFor(id))
        } catch (e: Exception) {}
    }

    fun stopDownload(id: String) {
        repository.markStopped(id)
        DownloadExecutor.cancelActiveCall(id)
        activeJobs.remove(id)?.cancel()
        try {
            WorkManager.getInstance(context).cancelUniqueWork(id)
            NotificationManagerCompat.from(context).cancel(DownloadNotifications.notificationIdFor(id))
        } catch (e: Exception) {}
    }

    fun cancelDownload(id: String) {
        pauseDownload(id)
    }

    fun deleteDownloads(ids: Set<String>, deleteFiles: Boolean) {
        ids.forEach { id ->
            DownloadExecutor.cancelActiveCall(id)
            activeJobs.remove(id)?.cancel()
            try {
                WorkManager.getInstance(context).cancelUniqueWork(id)
                NotificationManagerCompat.from(context).cancel(DownloadNotifications.notificationIdFor(id))
            } catch (e: Exception) {}
        }
        repository.deleteDownloads(ids, deleteFiles)
    }
}
