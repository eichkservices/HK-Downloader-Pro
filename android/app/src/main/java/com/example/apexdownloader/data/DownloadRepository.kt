package com.example.apexdownloader.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadRepository(context: Context) {
    private val prefs = context.getSharedPreferences("apex_downloads", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val deletedIds = ConcurrentHashMap.newKeySet<String>()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    init {
        loadFromPrefs()
    }

    fun isDeleted(id: String): Boolean = id in deletedIds
    fun isPaused(id: String): Boolean = id in pausedIds

    fun clearFlagsForStart(id: String) {
        deletedIds.remove(id)
        pausedIds.remove(id)
    }

    fun markPaused(id: String) {
        pausedIds.add(id)
        synchronized(lock) {
            val currentList = _downloads.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index != -1) {
                val existing = currentList[index]
                currentList[index] = existing.copy(
                    status = "paused",
                    speed = "Paused",
                    eta = "--"
                )
                _downloads.value = currentList
                saveToPrefsAsync(currentList)
            }
        }
    }

    fun markStopped(id: String) {
        pausedIds.add(id)
        var targetItem: DownloadItem? = null
        synchronized(lock) {
            val currentList = _downloads.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index != -1) {
                targetItem = currentList[index]
                currentList[index] = targetItem!!.copy(
                    status = "paused",
                    progress = 0f,
                    downloadedSize = 0L,
                    speed = "Stopped",
                    eta = "--"
                )
                _downloads.value = currentList
                saveToPrefsAsync(currentList)
            }
        }
        targetItem?.let { item ->
            ioScope.launch {
                deletePartialFiles(item)
            }
        }
    }

    private fun loadFromPrefs() {
        val json = prefs.getString("list", "[]")
        try {
            val type = object : TypeToken<List<DownloadItem>>() {}.type
            val list: List<DownloadItem> = gson.fromJson(json, type) ?: emptyList()
            // Any download that was mid-flight when process died should be shown as paused so user can resume
            val normalized = list.map {
                if (it.status == "downloading" || it.status == "waiting") {
                    pausedIds.add(it.id)
                    it.copy(status = "paused", speed = "Paused", eta = "--")
                } else {
                    if (it.status == "paused") pausedIds.add(it.id)
                    it
                }
            }
            _downloads.value = normalized.sortedByDescending { it.date }
        } catch (e: Exception) {
            _downloads.value = emptyList()
        }
    }

    private fun saveToPrefsAsync(snapshot: List<DownloadItem>) {
        ioScope.launch {
            try {
                val json = gson.toJson(snapshot)
                prefs.edit().putString("list", json).apply()
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun addOrUpdateDownload(item: DownloadItem, forceExplicit: Boolean = false) {
        if (!forceExplicit) {
            if (item.id in deletedIds) return
            if (item.id in pausedIds && (item.status == "downloading" || item.status == "waiting" || item.status == "failed")) {
                return
            }
        } else {
            deletedIds.remove(item.id)
            if (item.status == "downloading" || item.status == "waiting") {
                pausedIds.remove(item.id)
            }
        }

        synchronized(lock) {
            if (!forceExplicit && item.id in deletedIds) return
            val currentList = _downloads.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == item.id }
            if (index != -1) {
                val prev = currentList[index]
                // Never allow totalSize to regress to 0 once known, and never allow progress to jitter backwards while downloading
                val mergedTotal = if (item.totalSize > 0) item.totalSize else prev.totalSize
                val mergedProgress = if (item.status == "downloading" && prev.status == "downloading") {
                    maxOf(prev.progress, item.progress)
                } else {
                    item.progress
                }
                val mergedPath = item.localPath.ifEmpty { prev.localPath }
                currentList[index] = item.copy(
                    totalSize = mergedTotal,
                    progress = mergedProgress,
                    localPath = mergedPath,
                    date = prev.date
                )
            } else {
                if (!forceExplicit && item.id in deletedIds) return
                currentList.add(0, item)
            }
            _downloads.value = currentList
            saveToPrefsAsync(currentList)
        }
    }

    fun deleteDownloads(ids: Set<String>, deleteFiles: Boolean = false) {
        ids.forEach {
            deletedIds.add(it)
            pausedIds.add(it)
        }
        val itemsToDelete: List<DownloadItem>
        synchronized(lock) {
            val currentList = _downloads.value.toMutableList()
            itemsToDelete = currentList.filter { it.id in ids }
            currentList.removeAll { it.id in ids }
            _downloads.value = currentList
            saveToPrefsAsync(currentList)
        }

        ioScope.launch {
            itemsToDelete.forEach { item ->
                deletePartialFiles(item)
                if (deleteFiles && item.localPath.isNotEmpty()) {
                    try {
                        val file = File(item.localPath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        }
    }

    private fun deletePartialFiles(item: DownloadItem) {
        if (item.localPath.isNotEmpty()) {
            try {
                val file = File(item.localPath)
                val parent = file.parentFile
                if (parent != null) {
                    File(parent, file.name + ".part").takeIf { it.exists() }?.delete()
                    File(parent, file.name + ".vid.part").takeIf { it.exists() }?.delete()
                    File(parent, file.name + ".vid.part.part").takeIf { it.exists() }?.delete()
                    File(parent, file.name + ".aud.part").takeIf { it.exists() }?.delete()
                    File(parent, file.name + ".aud.part.part").takeIf { it.exists() }?.delete()
                }
            } catch (e: Exception) { /* ignore */ }
        }
    }
}
