package com.example.iluminadordeaudio.ui

import android.app.Application
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iluminadordeaudio.audio.AudioDecoder
import com.example.iluminadordeaudio.export.VideoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExportState(
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val exportedUri: Uri? = null,
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext

    private val _audioUri = MutableStateFlow<Uri?>(null)
    val audioUri: StateFlow<Uri?> = _audioUri.asStateFlow()

    val outputName = MutableStateFlow("AudioVisual")

    private val _rmsFrames = MutableStateFlow<FloatArray?>(null)
    val rmsFrames: StateFlow<FloatArray?> = _rmsFrames.asStateFlow()

    private val _exportState = MutableStateFlow(ExportState())
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _audioName = MutableStateFlow<String?>(null)
    val audioName: StateFlow<String?> = _audioName.asStateFlow()

    val isPreviewPlaying = MutableStateFlow(false)

    private var previewPlayer: MediaPlayer? = null

    fun loadAudio(uri: Uri, displayName: String?) {
        _audioUri.value = uri
        _audioName.value = displayName
        _rmsFrames.value = null
        previewPlayer?.release(); previewPlayer = null
        isPreviewPlaying.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (rms, _) = AudioDecoder().decodeToRms(ctx, uri, 30)
                _rmsFrames.value = rms
            } catch (e: Exception) {
                _exportState.value = ExportState(error = "Error al cargar audio: ${e.message}")
            }
        }
    }

    fun togglePreviewPlay() {
        val uri = _audioUri.value ?: return
        if (isPreviewPlaying.value) {
            previewPlayer?.pause()
            isPreviewPlaying.value = false
        } else {
            viewModelScope.launch {
                try {
                    if (previewPlayer == null) {
                        previewPlayer = withContext(Dispatchers.IO) {
                            MediaPlayer().apply {
                                setDataSource(ctx, uri)
                                isLooping = true
                                prepare()
                            }
                        }
                    }
                    previewPlayer?.seekTo(0)   // siempre desde el principio
                    previewPlayer?.start()
                    isPreviewPlaying.value = true
                } catch (_: Exception) {
                    isPreviewPlaying.value = false
                }
            }
        }
    }

    fun getPreviewPositionMs(): Int = try { previewPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    fun pausePreviewPlayback() {
        try { previewPlayer?.pause() } catch (_: Exception) {}
    }

    fun resumePreviewPlayback() {
        if (isPreviewPlaying.value) {
            try { previewPlayer?.start() } catch (_: Exception) {}
        }
    }

    fun startExport() {
        val uri = _audioUri.value ?: return
        if (_exportState.value.isExporting) return

        _exportState.value = ExportState(isExporting = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resultUri = VideoExporter(
                    context    = ctx,
                    audioUri   = uri,
                    outputName = outputName.value
                ).export { progress ->
                    _exportState.value = _exportState.value.copy(progress = progress)
                }
                _exportState.value = ExportState(isExporting = false, progress = 1f, exportedUri = resultUri)
            } catch (e: Exception) {
                _exportState.value = ExportState(isExporting = false, error = "Error al exportar: ${e.message}")
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState()
    }

    override fun onCleared() {
        super.onCleared()
        previewPlayer?.release()
        previewPlayer = null
    }
}
