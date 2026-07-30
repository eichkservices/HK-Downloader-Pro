package com.example.apexdownloader.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DownloadRepository(context: Context) {
    private val prefs = context.getSharedPreferences("apex_downloads", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val json = prefs.getString("list", "[]")
        try {
            val type = object : TypeToken<List<DownloadItem>>() {}.type
            val list: List<DownloadItem> = gson.fromJson(json, type) ?: emptyList()
            _downloads.value = list.sortedByDescending { it.date }
        } catch (e: Exception) {
            _downloads.value = emptyList()
        }
    }

    private fun saveToPrefs() {
        val json = gson.toJson(_downloads.value)
        prefs.edit().putString("list", json).apply()
    }

    fun addOrUpdateDownload(item: DownloadItem) {
        val currentList = _downloads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            currentList[index] = item
        } else {
            currentList.add(0, item)
        }
        _downloads.value = currentList.sortedByDescending { it.date }
        saveToPrefs()
    }

    fun deleteDownloads(ids: Set<String>, deleteFiles: Boolean = false) {
        val currentList = _downloads.value.toMutableList()
        val itemsToDelete = currentList.filter { it.id in ids }
        currentList.removeAll { it.id in ids }
        _downloads.value = currentList
        saveToPrefs()

        if (deleteFiles) {
            itemsToDelete.forEach { item ->
                if (item.localPath.isNotEmpty()) {
                    try {
                        val file = java.io.File(item.localPath)
                        if (file.exists()) file.delete()
                        // Downloads in progress write to a "<name>.part" file
                        // and only become the real file on completion -- if
                        // this item was still downloading, that's what
                        // actually holds the bytes on disk right now.
                        val partFile = java.io.File(file.parentFile, file.name + ".part")
                        if (partFile.exists()) partFile.delete()
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        }
    }
}
