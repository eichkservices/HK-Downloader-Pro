package com.example.apexdownloader.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.apexdownloader.data.DownloadItem
import com.example.apexdownloader.data.DownloadRepositoryProvider
import com.example.apexdownloader.engine.DownloadEngine
import com.example.apexdownloader.engine.QualityPreset
import com.example.apexdownloader.engine.VideoFormat
import com.example.apexdownloader.engine.VideoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DownloadRepositoryProvider.get(application)
    private val engine = DownloadEngine(application, repository)

    private val prefs = application.getSharedPreferences("apex_settings", Context.MODE_PRIVATE)

    private val _desktopServerUrl = MutableStateFlow(prefs.getString("desktop_server", "") ?: "")
    val desktopServerUrl: StateFlow<String> = _desktopServerUrl.asStateFlow()

    private val _cobaltInstanceUrl = MutableStateFlow(prefs.getString("cobalt_instance", "") ?: "")
    val cobaltInstanceUrl: StateFlow<String> = _cobaltInstanceUrl.asStateFlow()

    val downloads: StateFlow<List<DownloadItem>> = repository.downloads

    // Active Task-Based Download Station Modal ID (matches web #taskDownloadModal)
    private val _activeTaskModalId = MutableStateFlow<String?>(null)
    val activeTaskModalId: StateFlow<String?> = _activeTaskModalId.asStateFlow()

    // Built-in HK Player Pro Modal Item (matches web #playerModal)
    private val _activePlayerItem = MutableStateFlow<DownloadItem?>(null)
    val activePlayerItem: StateFlow<DownloadItem?> = _activePlayerItem.asStateFlow()

    fun openTaskModal(id: String) {
        _activeTaskModalId.value = id
    }

    fun closeTaskModal() {
        _activeTaskModalId.value = null
    }

    fun openPlayer(item: DownloadItem) {
        _activePlayerItem.value = item
    }

    fun closePlayer() {
        _activePlayerItem.value = null
    }

    fun saveDesktopServerUrl(url: String) {
        _desktopServerUrl.value = url
        prefs.edit().putString("desktop_server", url).apply()
    }

    fun saveCobaltInstanceUrl(url: String) {
        _cobaltInstanceUrl.value = url
        prefs.edit().putString("cobalt_instance", url).apply()
    }

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    fun resetAnalysis() {
        _analysisState.value = AnalysisState.Idle
    }

    fun analyzeUrl(url: String, preset: QualityPreset = QualityPreset.DEFAULT) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        _analysisState.value = AnalysisState.Analyzing

        viewModelScope.launch(Dispatchers.IO) {
            try {
                VideoResolver.resolveVideoInfo(
                    url = trimmed,
                    desktopServerUrl = _desktopServerUrl.value.ifEmpty { null },
                    cobaltInstanceUrl = _cobaltInstanceUrl.value.ifEmpty { null },
                    preset = preset,
                    onResult = { title, thumbnail, formats ->
                        _analysisState.value = AnalysisState.Success(
                            url = trimmed,
                            title = title,
                            thumbnail = thumbnail,
                            formats = formats
                        )
                    },
                    onError = { err ->
                        _analysisState.value = AnalysisState.Error(err)
                    }
                )
            } catch (e: Exception) {
                _analysisState.value = AnalysisState.Error(e.message ?: "Analysis failed")
            }
        }
    }

    fun triggerDownload(
        url: String,
        title: String,
        thumbnail: String,
        formatId: String?,
        filename: String,
        sizeBytes: Long = 0L
    ) {
        val id = "dl_" + System.currentTimeMillis()
        val isAudio = filename.endsWith(".mp3", ignoreCase = true) ||
            filename.endsWith(".m4a", ignoreCase = true) ||
            (formatId ?: "").contains("audio", ignoreCase = true)
        val type = if (isAudio) "audio" else VideoResolver.identifyLinkType(url)

        val item = DownloadItem(
            id = id,
            title = title,
            filename = filename.ifEmpty { title },
            url = url,
            status = "downloading",
            totalSize = sizeBytes,
            speed = "Connecting...",
            eta = "--",
            type = type,
            formatId = formatId ?: "",
            thumbnail = thumbnail
        )

        repository.addOrUpdateDownload(item, forceExplicit = true)
        _activeTaskModalId.value = id
        engine.startDownload(item, _desktopServerUrl.value.ifEmpty { null }, formatId)
    }

    fun pauseOrResumeDownload(item: DownloadItem) {
        if (item.status == "downloading" || item.status == "waiting") {
            engine.pauseDownload(item.id)
        } else {
            val latest = repository.downloads.value.find { it.id == item.id } ?: item
            engine.startDownload(latest, _desktopServerUrl.value.ifEmpty { null }, latest.formatId.ifEmpty { null })
        }
    }

    fun pauseDownload(item: DownloadItem) {
        engine.pauseDownload(item.id)
    }

    fun resumeDownload(item: DownloadItem) {
        val latest = repository.downloads.value.find { it.id == item.id } ?: item
        engine.startDownload(latest, _desktopServerUrl.value.ifEmpty { null }, latest.formatId.ifEmpty { null })
    }

    fun stopDownload(item: DownloadItem) {
        engine.stopDownload(item.id)
        if (_activeTaskModalId.value == item.id) {
            _activeTaskModalId.value = null
        }
    }

    fun deleteDownloads(ids: Set<String>, deleteFiles: Boolean) {
        if (_activeTaskModalId.value in ids) {
            _activeTaskModalId.value = null
        }
        if (_activePlayerItem.value?.id in ids) {
            _activePlayerItem.value = null
        }
        engine.deleteDownloads(ids, deleteFiles)
    }

    fun clearCompletedDownloads(deleteFiles: Boolean = false) {
        val completedIds = repository.downloads.value.filter { it.status == "completed" }.map { it.id }.toSet()
        if (completedIds.isNotEmpty()) {
            engine.deleteDownloads(completedIds, deleteFiles = deleteFiles)
        }
    }
}

sealed interface AnalysisState {
    object Idle : AnalysisState
    object Analyzing : AnalysisState
    data class Success(
        val url: String,
        val title: String,
        val thumbnail: String,
        val formats: List<VideoFormat>
    ) : AnalysisState
    data class Error(val message: String) : AnalysisState
}
