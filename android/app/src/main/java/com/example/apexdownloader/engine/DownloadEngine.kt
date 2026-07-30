package com.example.apexdownloader.engine

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.data.DownloadRepository

/**
 * Public API used by the ViewModel to start/cancel downloads.
 *
 * This used to launch a bare `CoroutineScope(Dispatchers.IO)` coroutine per
 * download, tracked in a local `activeJobs` map. That coroutine had no tie to
 * any Android lifecycle, so Android's background execution limits (Doze,
 * App Standby, process death) could -- and eventually would -- kill it
 * silently the moment the app left the foreground, with no error surfaced
 * to the user. Downloads now run as WorkManager work, which runs in the
 * foreground with a persistent notification and is the platform-recommended
 * way to do exactly this kind of long-running data transfer as of Android 14+.
 *
 * The actual byte-shuffling logic lives in [DownloadExecutor], invoked from
 * [DownloadWorker].
 */
class DownloadEngine(
    private val context: Context,
    private val repository: DownloadRepository
) {
    fun startDownload(
        item: DownloadItem,
        desktopServerUrl: String?,
        formatId: String? = null
    ) {
        val data = workDataOf(
            DownloadWorker.KEY_ITEM_ID to item.id,
            DownloadWorker.KEY_URL to item.url,
            DownloadWorker.KEY_TITLE to item.title,
            DownloadWorker.KEY_FILENAME to item.filename,
            DownloadWorker.KEY_TYPE to item.type,
            DownloadWorker.KEY_THUMBNAIL to item.thumbnail,
            DownloadWorker.KEY_FORMAT_ID to (formatId ?: ""),
            DownloadWorker.KEY_DESKTOP_SERVER to (desktopServerUrl ?: "")
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .build()

        // enqueueUniqueWork with REPLACE means starting/resuming the same
        // download id cancels any stale work for it first -- this replaces
        // the old manual `activeJobs` bookkeeping.
        WorkManager.getInstance(context).enqueueUniqueWork(item.id, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelDownload(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(id)
        val list = repository.downloads.value
        val item = list.find { it.id == id }
        if (item != null) {
            repository.addOrUpdateDownload(item.copy(status = "paused", speed = "Paused", eta = "--"))
        }
    }
}
