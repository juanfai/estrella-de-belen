package com.estrelladebelen.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estrelladebelen.app.data.model.Meditation
import com.estrelladebelen.app.data.repository.AppContainer
import com.estrelladebelen.app.data.repository.MeditationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DurationFilter { ALL, SHORT, MEDIUM, LONG }

data class HomeUiState(
    val isLoading: Boolean = true,
    val meditations: List<Meditation> = emptyList(),
    val featured: Meditation? = null,
    val activeFilter: DurationFilter = DurationFilter.ALL,
    val error: String? = null
)

class HomeViewModel : ViewModel() {

    private val repository: MeditationRepository = AppContainer.meditationRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private var allMeditations: List<Meditation> = emptyList()

    init { loadMeditations() }

    private fun loadMeditations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { repository.getAll() }
                .onSuccess { list ->
                    allMeditations = list
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        meditations = list,
                        featured = list.firstOrNull()
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
