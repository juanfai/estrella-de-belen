package com.example.iluminadordeaudio.export

import android.net.Uri
import com.example.iluminadordeaudio.ui.ExportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton que comparte el estado del export entre el Service (escribe)
 * y el ViewModel/UI (lee). Permite que el Service viva independiente del Activity.
 */
object ExportManager {
    private val _state = MutableStateFlow(ExportState())
    val state = _state.asStateFlow()

    // Parámetros del export pendiente (se pasan en memoria; mismo proceso)
    @Volatile var pendingUri:  Uri?        = null
    @Volatile var pendingName: String      = "AudioVisual"
    @Volatile var pendingRms:  FloatArray? = null

    fun updateState(new: ExportState) { _state.value = new }
    fun resetState()                  { _state.value = ExportState() }
}
