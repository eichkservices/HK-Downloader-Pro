package com.example.apexdownloader.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Environment
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.data.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class DownloadExecutor(
    private val context: Context,
    private val repository: DownloadRepository,
    private val onProgress: suspend (DownloadItem) -> Unit = {}
) {
    companion object {
        private val activeCalls = ConcurrentHashMap<String, Call>()

        fun registerCall(id: String, call: Call) {
            activeCalls[id] = call
        }

        fun unregisterCall(id: String, call: Call) {
            activeCalls.remove(id, call)
        }

        fun cancelActiveCall(id: String) {
            activeCalls.remove(id)?.cancel()
        }
    }

    private val client = NetworkClient.client

    private suspend fun checkActive(itemId: String) {
        currentCoroutineContext().ensureActive()
        if (repository.isDeleted(itemId) || repository.isPaused(itemId)) {
            throw CancellationException("Download $itemId paused or deleted")
        }
    }

    suspend fun execute(item: DownloadItem, desktopServerUrl: String?, formatId: String?) {
        checkActive(item.id)

        val downloadsDir = try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(publicDownloads, "HK-Downloader-Pro")
            if (!appDir.exists()) appDir.mkdirs()
            if (appDir.canWrite()) appDir else (context.getExternalFilesDir(null) ?: context.filesDir)
        } catch (e: Exception) {
            context.getExternalFilesDir(null) ?: context.filesDir
        }

        var filenameClean = sanitizeFilename(item.filename.ifEmpty { "download_" + System.currentTimeMillis() })
        val isAudio = item.type == "audio" ||
            (formatId ?: "").contains("audio") ||
            item.formatId.contains("audio") ||
            filenameClean.endsWith(".mp3", ignoreCase = true) ||
            filenameClean.endsWith(".m4a", ignoreCase = true)
        val defaultExt = if (isAudio) ".mp3" else ".mp4"
        if (!filenameClean.contains(".")) {
            filenameClean += defaultExt
        }
        val finalFile = File(downloadsDir, filenameClean)

        val currentItem = item.copy(
            status = "downloading",
            speed = if (item.downloadedSize > 0) "Resuming..." else "Connecting...",
            localPath = finalFile.absolutePath
        )
        repository.addOrUpdateDownload(currentItem)
        onProgress(currentItem)

        if (item.type in VideoResolver.videoPlatformTypes && !desktopServerUrl.isNullOrEmpty() && formatId?.startsWith("cobalt|") != true && formatId?.startsWith("http") != true) {
            downloadViaDesktopServer(currentItem, desktopServerUrl, formatId, finalFile)
        } else if (item.type == "google-drive" && item.url.contains("/folders/")) {
            downloadGDriveFolder(currentItem, downloadsDir)
        } else {
            val downloadUrl = when {
                formatId?.startsWith("cobalt|") == true -> formatId.substringAfter("cobalt|")
                formatId?.startsWith("http://") == true || formatId?.startsWith("https://") == true -> formatId
                item.formatId.isNotEmpty() && (item.formatId.startsWith("http://") || item.formatId.startsWith("https://")) -> item.formatId
                else -> getDirectDownloadUrl(item.url, item.type)
            }
            if (downloadUrl.contains("|http")) {
                val videoUrl = downloadUrl.substringBefore("|")
                val audioUrl = downloadUrl.substringAfter("|")
                downloadAndMuxDirectFiles(currentItem, videoUrl, audioUrl, finalFile)
            } else {
                downloadDirectFile(currentItem, downloadUrl, finalFile)
            }
        }
    }

    private suspend fun downloadDirectFile(
        item: DownloadItem,
        url: String,
        destFile: File,
        isRetryAfter416: Boolean = false,
        isRetryAfter403: Boolean = false,
        markCompleted: Boolean = true,
        progressStart: Float = 0f,
        progressEnd: Float = 100f
    ) {
        checkActive(item.id)

        val partFile = File(destFile.parentFile, destFile.name + ".part")
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val lowerUrl = url.lowercase()

        val effectiveUa = when {
            lowerUrl.contains("googlevideo.com") && lowerUrl.contains("c=visionos") ->
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
            lowerUrl.contains("googlevideo.com") && lowerUrl.contains("c=android") ->
                "com.google.android.youtube/20.10.38 (Linux; U; Android 14; en_US) gzip"
            lowerUrl.contains("googlevideo.com") && lowerUrl.contains("c=ios") ->
                "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
            else -> NetworkClient.USER_AGENT
        }

        val requestBuilder = Request.Builder().url(url)
            .header("User-Agent", effectiveUa)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")

        if (lowerUrl.contains("tiktok") || lowerUrl.contains("byteoversea") || lowerUrl.contains("ibytedtos")) {
            requestBuilder.header("Referer", "https://www.tiktok.com/")
        } else if (lowerUrl.contains("instagram") || lowerUrl.contains("cdninstagram")) {
            requestBuilder.header("Referer", "https://www.instagram.com/")
        } else if ((lowerUrl.contains("googlevideo.com") || lowerUrl.contains("youtube.com")) &&
            !lowerUrl.contains("c=ios") && !lowerUrl.contains("c=android")
        ) {
            requestBuilder.header("Referer", "https://www.youtube.com/")
        } else if (lowerUrl.contains("fbcdn.net") || lowerUrl.contains("facebook.com")) {
            requestBuilder.header("Referer", "https://www.facebook.com/")
        } else if (lowerUrl.contains("twimg.com") || lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com")) {
            requestBuilder.header("Referer", "https://x.com/")
        }

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val call = client.newCall(requestBuilder.build())
        registerCall(item.id, call)

        try {
            call.execute().use { response ->
                checkActive(item.id)

                if (response.code == 416 && !isRetryAfter416) {
                    partFile.delete()
                    unregisterCall(item.id, call)
                    downloadDirectFile(
                        item = item,
                        url = url,
                        destFile = destFile,
                        isRetryAfter416 = true,
                        isRetryAfter403 = isRetryAfter403,
                        markCompleted = markCompleted,
                        progressStart = progressStart,
                        progressEnd = progressEnd
                    )
                    return
                }
                if (response.code == 403 && !isRetryAfter403 && (lowerUrl.contains("googlevideo.com") || item.type == "youtube")) {
                    val itag = Regex("[?&]itag=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
                    val isAudioTrack = destFile.name.contains(".aud.") ||
                        destFile.name.endsWith(".mp3", ignoreCase = true) ||
                        destFile.name.endsWith(".m4a", ignoreCase = true)
                    val freshUrl = VideoResolver.resolveFreshYoutubeUrl(
                        videoUrlOrId = item.url,
                        targetItag = itag,
                        isAudioOnly = isAudioTrack
                    )
                    if (!freshUrl.isNullOrEmpty() && freshUrl != url) {
                        if (existingBytes == 0L) partFile.delete()
                        unregisterCall(item.id, call)
                        downloadDirectFile(
                            item = item,
                            url = freshUrl,
                            destFile = destFile,
                            isRetryAfter416 = isRetryAfter416,
                            isRetryAfter403 = true,
                            markCompleted = markCompleted,
                            progressStart = progressStart,
                            progressEnd = progressEnd
                        )
                        return
                    }
                }
                if (!response.isSuccessful) throw Exception("Failed with HTTP code ${response.code}")

                val body = response.body ?: throw Exception("Empty body response")
                val isResuming = response.code == 206 && existingBytes > 0

                val serverTotalBytes = if (isResuming) {
                    val remaining = body.contentLength()
                    if (remaining > 0) existingBytes + remaining else -1L
                } else {
                    body.contentLength()
                }
                val effectiveTotalBytes = when {
                    serverTotalBytes > 0 -> serverTotalBytes
                    item.totalSize > 0 && markCompleted -> item.totalSize
                    else -> -1L
                }

                var totalRead = if (isResuming) existingBytes else 0L
                val initialProgress = if (effectiveTotalBytes > 0) {
                    val rawRatio = (totalRead.toFloat() / effectiveTotalBytes).coerceIn(0f, 1f)
                    progressStart + rawRatio * (progressEnd - progressStart)
                } else {
                    maxOf(item.progress, progressStart)
                }

                val startState = item.copy(
                    status = "downloading",
                    progress = initialProgress,
                    totalSize = if (effectiveTotalBytes > 0) effectiveTotalBytes else item.totalSize,
                    downloadedSize = totalRead,
                    localPath = item.localPath.ifEmpty { destFile.absolutePath }
                )
                repository.addOrUpdateDownload(startState)

                val speedTracker = SpeedTracker()
                speedTracker.record(System.currentTimeMillis(), totalRead)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var lastUpdate = System.currentTimeMillis()
                var lastNotificationUpdate = lastUpdate
                var highestProgress = initialProgress

                body.byteStream().use { input ->
                    FileOutputStream(partFile, isResuming).use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            checkActive(item.id)

                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 180) {
                                speedTracker.record(now, totalRead)
                                val speedBps = speedTracker.currentBytesPerSecond()
                                val rawPct = if (effectiveTotalBytes > 0) {
                                    (totalRead.toFloat() / effectiveTotalBytes).coerceIn(0f, 1f)
                                } else {
                                    // Smooth asymptotic estimate when server omits Content-Length
                                    val approxMb = totalRead.toFloat() / (15f * 1024f * 1024f)
                                    (approxMb / (1f + approxMb)).coerceIn(0.02f, 0.95f)
                                }
                                val mappedProgress = (progressStart + rawPct * (progressEnd - progressStart))
                                    .coerceIn(progressStart, progressEnd)
                                highestProgress = maxOf(highestProgress, mappedProgress)

                                val speedStr = formatSpeed(speedBps)
                                val eta = if (speedBps > 0 && effectiveTotalBytes > totalRead) {
                                    formatEta(((effectiveTotalBytes - totalRead) / speedBps).toLong())
                                } else "--"

                                val updated = item.copy(
                                    status = "downloading",
                                    progress = highestProgress,
                                    totalSize = if (effectiveTotalBytes > 0) effectiveTotalBytes else item.totalSize,
                                    downloadedSize = totalRead,
                                    speed = speedStr,
                                    eta = eta,
                                    localPath = item.localPath.ifEmpty { destFile.absolutePath }
                                )
                                repository.addOrUpdateDownload(updated)

                                // Throttle system notification IPC to once per 900ms so it never slows down socket reads
                                if (now - lastNotificationUpdate >= 900) {
                                    onProgress(updated)
                                    lastNotificationUpdate = now
                                }
                                lastUpdate = now
                            }
                        }
                    }
                }

                checkActive(item.id)

                if (destFile.exists()) destFile.delete()
                if (!partFile.renameTo(destFile)) {
                    partFile.copyTo(destFile, overwrite = true)
                    partFile.delete()
                }

                if (markCompleted) {
                    val finalSize = if (destFile.exists() && destFile.length() > 0) destFile.length() else totalRead
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(destFile.absolutePath),
                            arrayOf(if (destFile.name.endsWith(".mp3", ignoreCase = true) || destFile.name.endsWith(".m4a", ignoreCase = true)) "audio/mpeg" else "video/mp4")
                        ) { _, _ -> }
                    } catch (e: Exception) {}

                    val finished = item.copy(
                        status = "completed",
                        progress = 100f,
                        totalSize = finalSize,
                        downloadedSize = finalSize,
                        speed = "Saved to Downloads",
                        eta = "0s",
                        localPath = destFile.absolutePath
                    )
                    repository.addOrUpdateDownload(finished)
                    onProgress(finished)
                }
            }
        } finally {
            unregisterCall(item.id, call)
        }
    }

    private suspend fun downloadAndMuxDirectFiles(
        item: DownloadItem,
        videoUrl: String,
        audioUrl: String,
        destFile: File
    ) {
        val videoPart = File(destFile.parentFile, destFile.name + ".vid.part")
        val audioPart = File(destFile.parentFile, destFile.name + ".aud.part")

        // 1. Download video track (mapped smoothly to 0% -> 84%)
        checkActive(item.id)
        if (!videoPart.exists() || videoPart.length() == 0L) {
            downloadDirectFile(
                item = item.copy(localPath = destFile.absolutePath),
                url = videoUrl,
                destFile = videoPart,
                markCompleted = false,
                progressStart = 0f,
                progressEnd = 84f
            )
        }

        // 2. Download audio track (mapped smoothly to 84% -> 96%)
        checkActive(item.id)
        if (!audioPart.exists() || audioPart.length() == 0L) {
            downloadDirectFile(
                item = item.copy(progress = 84f, localPath = destFile.absolutePath),
                url = audioUrl,
                destFile = audioPart,
                markCompleted = false,
                progressStart = 84f,
                progressEnd = 96f
            )
        }

        // 3. Native Muxing via MediaMuxer
        checkActive(item.id)
        val mergingState = item.copy(
            status = "downloading",
            speed = "Merging video & audio...",
            eta = "1s",
            progress = 97f,
            localPath = destFile.absolutePath
        )
        repository.addOrUpdateDownload(mergingState)
        onProgress(mergingState)

        if (destFile.exists()) destFile.delete()

        try {
            muxVideoAndAudio(videoPart, audioPart, destFile)
        } catch (muxErr: Exception) {
            if (videoPart.exists() && !destFile.exists()) {
                videoPart.copyTo(destFile, overwrite = true)
            }
        }

        // Only delete completed track parts after successful muxing so paused downloads can resume!
        if (videoPart.exists()) videoPart.delete()
        if (audioPart.exists()) audioPart.delete()

        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("video/mp4")
            ) { _, _ -> }
        } catch (e: Exception) {}

        val finalLength = if (destFile.exists()) destFile.length() else 0L
        val finished = item.copy(
            status = "completed",
            progress = 100f,
            totalSize = finalLength,
            downloadedSize = finalLength,
            speed = "Saved to Downloads",
            eta = "0s",
            localPath = destFile.absolutePath
        )
        repository.addOrUpdateDownload(finished)
        onProgress(finished)
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = muxer.addTrack(format)
                    videoExtractor.selectTrack(i)
                    break
                }
            }

            var audioTrackIndex = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = muxer.addTrack(format)
                    audioExtractor.selectTrack(i)
                    break
                }
            }

            muxer.start()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            if (videoTrackIndex >= 0) {
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break
                    bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                    bufferInfo.flags = videoExtractor.sampleFlags
                    muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                    videoExtractor.advance()
                }
            }

            if (audioTrackIndex >= 0) {
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }
        } finally {
            try { muxer?.stop() } catch (e: Exception) {}
            try { muxer?.release() } catch (e: Exception) {}
            try { videoExtractor?.release() } catch (e: Exception) {}
            try { audioExtractor?.release() } catch (e: Exception) {}
        }
    }

    private suspend fun downloadGDriveFolder(item: DownloadItem, baseDir: File) {
        val folderId = extractGDriveFolderId(item.url) ?: throw Exception("Invalid Google Drive folder ID")

        repository.addOrUpdateDownload(item.copy(status = "downloading", speed = "Retrieving folder contents...", progress = 0f))

        val filesToDownload = mutableListOf<FolderFile>()

        val folderPageUrl = "https://drive.google.com/drive/folders/$folderId"
        val request = Request.Builder()
            .url(folderPageUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
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
            checkActive(item.id)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { /* skip individual errors */ }
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
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "--"
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s ETA" else "${s}s ETA"
    }

    private data class FolderFile(val id: String, val name: String, val url: String)
}
