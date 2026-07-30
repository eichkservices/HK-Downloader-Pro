package com.example.apexdownloader.data

import android.content.Context

/**
 * DownloadRepository must be a single instance for the whole process.
 *
 * It's not just a persistence wrapper -- it also owns the in-memory
 * MutableStateFlow that the UI collects. If the ViewModel and a background
 * DownloadWorker each constructed their own `DownloadRepository(context)`,
 * they'd both read/write the same SharedPreferences file correctly, but
 * they'd hold two separate StateFlow instances in memory. The Worker's
 * progress updates would persist to disk but never reach the UI's collector,
 * since Compose is observing the *other* object's Flow. This provider
 * guarantees every caller in the process gets the same instance.
 */
object DownloadRepositoryProvider {
    @Volatile
    private var instance: DownloadRepository? = null

    fun get(context: Context): DownloadRepository {
        return instance ?: synchronized(this) {
            instance ?: DownloadRepository(context.applicationContext).also { instance = it }
        }
    }
}
