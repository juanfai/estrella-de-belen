package com.estrelladebelen.app.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estrelladebelen.app.data.model.Meditation
import com.estrelladebelen.app.data.repository.AppContainer
import com.estrelladebelen.app.data.repository.MeditationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class DurationFilter { ALL, SHORT, MEDIUM, LONG }

data class HomeUiState(
    val isLoading: Boolean = true,
    val meditations: List<Meditation> = emptyList(),
    val featured: Meditation? = null,
    val activeFilter: DurationFilter = DurationFilter.ALL,
    val error: String? = null,
    val downloads: List<String> = emptyList(),
    val downloadingIds: Set<String> = emptySet(),
    val isSubscribed: Boolean = false
)

class HomeViewModel : ViewModel() {

    private val repository: MeditationRepository = AppContainer.meditationRepository
    private val subscriptionRepo = AppContainer.subscriptionRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private var allMeditations: List<Meditation> = emptyList()

    init {
        loadMeditations()
        observeDownloads()
        observeSubscription()
    }

    private fun observeSubscription() {
        viewModelScope.launch {
            subscriptionRepo.isSubscribed.collect { subscribed ->
                _uiState.value = _uiState.value.copy(isSubscribed = subscribed)
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            repository.getDownloads().collect { list ->
                _uiState.value = _uiState.value.copy(
                    downloads = list.map { it.meditationId }
                )
            }
        }
    }

    fun downloadMeditation(context: Context, meditationId: String) {
        val meditation = allMeditations.find { it.id == meditationId } ?: return

        viewModelScope.launch {
            // Toggle: si ya está descargado, lo borra
            if (repository.isDownloaded(meditationId)) {
                withContext(Dispatchers.IO) {
                    File(context.filesDir, audioFileName(meditationId)).delete()
                    repository.removeDownload(meditationId)
                }
                return@launch
            }

            if (meditation.audioUrl.isBlank()) return@launch

            _uiState.value = _uiState.value.copy(
                downloadingIds = _uiState.value.downloadingIds + meditationId
            )

            runCatching {
                withContext(Dispatchers.IO) {
                    val file = File(context.filesDir, audioFileName(meditationId))
                    val conn = URL(meditation.audioUrl).openConnection() as HttpURLConnection
                    conn.connect()
                    conn.inputStream.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    conn.disconnect()
                    repository.saveDownload(meditation, file.absolutePath)
                }
            }

            _uiState.value = _uiState.value.copy(
                downloadingIds = _uiState.value.downloadingIds - meditationId
            )
        }
    }

    private fun audioFileName(meditationId: String) = "meditation_$meditationId.audio"

    private fun loadMeditations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { repository.getAll() }
                .onSuccess { list ->
                    allMeditations = list
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        meditations = list,
                        featured = list.firstOrNull { it.isFree } ?: list.firstOrNull()
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
        }
    }

    fun setFilter(filter: DurationFilter) {
        val filtered = when (filter) {
            DurationFilter.ALL    -> allMeditations
            DurationFilter.SHORT  -> allMeditations.filter { it.durationSeconds <= 5 * 60 }
            DurationFilter.MEDIUM -> allMeditations.filter { it.durationSeconds in (5 * 60 + 1)..(10 * 60) }
            DurationFilter.LONG   -> allMeditations.filter { it.durationSeconds > 10 * 60 }
        }
        _uiState.value = _uiState.value.copy(activeFilter = filter, meditations = filtered)
    }
}
