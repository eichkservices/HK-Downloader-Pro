package com.example.apexdownloader.data

data class DownloadItem(
    val id: String,
    val title: String,
    val filename: String = "",
    val url: String,
    val status: String = "queued", // analyzing, downloading, waiting, completed, failed, paused
    val progress: Float = 0f,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val speed: String = "",
    val eta: String = "",
    val type: String = "direct-link", // google-drive, dropbox, direct-link, youtube, etc.
    val formatId: String = "",
    val localPath: String = "",
    val thumbnail: String = "",
    val date: Long = System.currentTimeMillis()
)
