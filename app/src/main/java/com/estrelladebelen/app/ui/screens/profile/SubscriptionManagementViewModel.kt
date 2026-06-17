package com.estrelladebelen.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estrelladebelen.app.data.repository.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

class SubscriptionManagementViewModel : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val productId: String? = null,
        val expirationDate: Date? = null,
        val willRenew: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val info = AppContainer.subscriptionRepository.getSubscriptionInfo()
            _uiState.value = UiState(
                isLoading      = false,
                productId      = info.productId,
                expirationDate = info.expirationDate,
                willRenew      = info.willRenew
            )
        }
    }
}
