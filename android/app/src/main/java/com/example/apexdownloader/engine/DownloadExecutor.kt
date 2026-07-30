package com.example.apexdownloader.engine

import android.content.Context
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.data.DownloadRepository
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Does the actual downloading. Pulled out of DownloadEngine so the same
 * logic can run inside a WorkManager CoroutineWorker (which is what keeps a
 * download alive when the app backgrounds) instead of a bare, lifecycle-less
 * coroutine scope.
 *
 * [onProgress] is invoked at the same throttle as repository updates, so a
 * caller (the Worker) can drive a foreground notification off of it without
 * duplicating the throttling logic.
 */
class DownloadExecutor(
    private val context: Context,
    private val repository: DownloadRepository,
    private val onProgress: suspend (DownloadItem) -> Unit = {}
) {
    private val client = NetworkClient.client

    suspend fun execute(item: DownloadItem, desktopServerUrl: String?, formatId: String?) {
        repository.addOrUpdateDownload(item.copy(status = "downloading", speed = "Connecting...", progress = 0f))

        val downloadsDir = context.getExternalFilesDir(null) ?: context.filesDir
        val filenameClean = sanitizeFilename(item.filename.ifEmpty { "download_" + System.currentTimeMillis() })
        val finalFile = File(downloadsDir, filenameClean)

        if (item.type in VideoResolver.videoPlatformTypes && !desktopServerUrl.isNullOrEmpty() && formatId?.startsWith("cobalt|") != true) {
            downloadViaDesktopServer(item, desktopServerUrl, formatId, finalFile)
        } else if (item.type == "google-drive" && item.url.contains("/folders/")) {
            downloadGDriveFolder(item, downloadsDir)
        } else {
            val downloadUrl = if (formatId?.startsWith("cobalt|") == true) {
                formatId.substringAfter("cobalt|")
            } else {
                getDirectDownloadUrl(item.url, item.type)
            }
            downloadDirectFile(item, downloadUrl, finalFile)
        }
    }

    /**
     * Downloads [url] into [destFile]. Writes into a sibling `<name>.part` file
     * while in progress and only renames it to the real filename once the
     * download finishes successfully -- so a paused/killed/failed download
     * never leaves something at the final filename that looks complete but
     * isn't (e.g. if the user opens a file manager mid-download). Resume
     * checks the `.part` file's length via `Range: bytes=N-`, falling back to
     * a full clean restart if the server doesn't honor the range (plain 200
     * instead of 206), and recovers once from a 416 (Range Not Satisfiable --
     * the partial file is stale/invalid) by discarding it and retrying fresh.
     */
    private suspend fun downloadDirectFile(
        item: DownloadItem,
        url: String,
        destFile: File,
        isRetryAfter416: Boolean = false
    ) {
        val partFile = File(destFile.parentFile, destFile.name + ".part")
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416 && !isRetryAfter416) {
                // The server says our existing partial data is out of range for
                // the current resource (stale file, or the resource changed).
                // Only safe move is to drop it and start over, once.
                partFile.delete()
                downloadDirectFile(item, url, destFile, isRetryAfter416 = true)
                return
            }
            if (!response.isSuccessful) throw Exception("Failed with HTTP code ${response.code}")

            val body = response.body ?: throw Exception("Empty body response")
            val isResuming = response.code == 206 && existingBytes > 0

            val totalBytes = if (isResuming) {
                val remaining = body.contentLength()
                if (remaining > 0) existingBytes + remaining else -1L
            } else {
                body.contentLength()
            }

            var totalRead = if (isResuming) existingBytes else 0L
            repository.addOrUpdateDownload(
                item.copy(totalSize = totalBytes, downloadedSize = totalRead, localPath = destFile.absolutePath)
            )

            val speedTracker = SpeedTracker()
            speedTracker.record(System.currentTimeMillis(), totalRead)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var lastUpdate = System.currentTimeMillis()

            body.byteStream().use { input ->
                // Append when resuming a partial file; otherwise start clean
                // (also covers the case where the server ignored our Range
                // request and sent the whole file back with a 200).
                FileOutputStream(partFile, isResuming).use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 800) {
                            speedTracker.record(now, totalRead)
                            val speedBps = speedTracker.currentBytesPerSecond()
                            val progress = if (totalBytes > 0) (totalRead.toFloat() / totalBytes * 100) else 0f
                            val speedStr = formatSpeed(speedBps)
                            val eta = if (speedBps > 0 && totalBytes > 0) {
                                formatEta(((totalBytes - totalRead) / speedBps).toLong())
                            } else "--"

                            val updated = item.copy(
                                status = "downloading",
                                progress = progress,
                                downloadedSize = totalRead,
                                speed = speedStr,
                                eta = eta,
                                localPath = destFile.absolutePath
                            )
                            repository.addOrUpdateDownload(updated)
                            onProgress(updated)
                            lastUpdate = now
                        }
                    }
                }
            }

            // Only now, with the transfer fully complete, does the file become
            // the real thing -- delete() first in case a stale full-size
            // destFile exists from a previous run (renameTo fails if the
            // target already exists on some filesystems/API levels).
            if (destFile.exists()) destFile.delete()
            if (!partFile.renameTo(destFile)) {
                // Rename can fail across filesystems on some devices; copy+delete as a fallback.
                partFile.copyTo(destFile, overwrite = true)
                partFile.delete()
            }

            val finished = item.copy(
                status = "completed",
                progress = 100f,
                downloadedSize = totalRead,
                speed = "Done",
                eta = "0s",
                localPath = destFile.absolutePath
            )
            repository.addOrUpdateDownload(finished)
            onProgress(finished)
        }
    }

    private suspend fun downloadGDriveFolder(item: DownloadItem, baseDir: File) {
        val folderId = extractGDriveFolderId(item.url) ?: throw Exception("Invalid Google Drive folder ID")

        repository.addOrUpdateDownload(item.copy(status = "downloading", speed = "Retrieving folder contents...", progress = 0f))

        val filesToDownload = mutableListOf<FolderFile>()

        // Scraping public sharing page HTML for folder contents
        val folderPageUrl = "https://drive.google.com/drive/folders/$folderId"
        val request = Request.Builder()
            .url(folderPageUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    // Regex matching standard resource list JSON array structure inside Drive HTML scripts:
                    // e.g. ["file_id_33_chars","filename.ext",...]
                    val regex = """\["([a-zA-Z0-9_-]{33})","([^"]+)"""".toRegex()
                    val matches = regex.findAll(html)
                    for (match in matches) {
                        val fileId = match.groupValues[1]
                        val name = match.groupValues[2]
                        val fileUrl = "https://drive.google.com/uc?export=download&id=$fileId"
                        if (filesToDownload.none { it.id == fileId }) {
                            filesToDownload.add(FolderFile(fileId, name, fileUrl))
                        }
                    }
                }
            }
        } catch (e: Exception) {}

        if (filesToDownload.isEmpty()) {
            throw Exception("No files found in this Google Drive folder. Make sure link sharing is enabled.")
        }

        val total = filesToDownload.size
        repository.addOrUpdateDownload(
            item.copy(
                status = "downloading",
                title = "Folder ($total files)",
                progress = 0f,
                speed = "0/$total files",
                eta = ""
            )
        )

        for (i in 0 until total) {
            val f = filesToDownload[i]
            val indexNum = i + 1
            val cleanName = sanitizeFilename(f.name)

            val progressItem = item.copy(
                filename = "File $indexNum of $total: $cleanName",
                speed = "$indexNum of $total files",
                progress = (i.toFloat() / total * 100)
            )
            repository.addOrUpdateDownload(progressItem)
            onProgress(progressItem)

            val destFile = File(baseDir, cleanName)
            try {
                downloadDirectFile(
                    item.copy(id = item.id + "_$i"),
                    f.url,
                    destFile
                )
            } catch (e: Exception) { /* skip individual errors or report */ }
        }

        val finished = item.copy(status = "completed", progress = 100f, speed = "Finished", eta = "0s", filename = "Downloaded $total files")
        repository.addOrUpdateDownload(finished)
        onProgress(finished)
    }

    private suspend fun downloadViaDesktopServer(
        item: DownloadItem,
        desktopServerUrl: String,
        formatId: String?,
        destFile: File
    ) {
        val cleanServerUrl = desktopServerUrl.trimEnd('/')
        val streamUrl = "$cleanServerUrl/api/stream-video?url=${item.url}&formatId=${formatId ?: "best"}"
        downloadDirectFile(item, streamUrl, destFile)
    }

    private fun getDirectDownloadUrl(url: String, type: String): String {
        return when (type) {
            "dropbox" -> url.replace("www.dropbox.com", "dl.dropboxusercontent.com").replace("?dl=0", "").replace("?dl=1", "")
            "google-drive" -> {
                val id = extractGDriveFileId(url)
                if (id != null) "https://drive.google.com/uc?export=download&id=$id" else url
            }
            else -> url
        }
    }

    private fun extractGDriveFileId(url: String): String? {
        val reg = "(?:id=|[\\/])([a-zA-Z0-9_-]{33,})".toRegex()
        return reg.find(url)?.groupValues?.get(1)
    }

    private fun extractGDriveFolderId(url: String): String? {
        val reg = "/folders/([a-zA-Z0-9_-]{28,})".toRegex()
        return reg.find(url)?.groupValues?.get(1)
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%.2f KB/s", bytesPerSec / 1024)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "--"
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) String.format("%d:%02d", m, s) else "${s}s"
    }

    private data class FolderFile(val id: String, val name: String, val url: String)
}
