package com.estrelladebelen.app.ui.screens.paywall

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estrelladebelen.app.data.repository.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class PaywallViewModel : ViewModel() {

    private val subscriptionRepo = AppContainer.subscriptionRepository

    var isLoading by mutableStateOf(false)
        private set

    private val _events = MutableSharedFlow<PaywallEvent>()
    val events: SharedFlow<PaywallEvent> = _events

    fun purchase(activity: Activity, productId: String) {
        viewModelScope.launch {
            isLoading = true
            val result = subscriptionRepo.purchase(activity, productId)
            isLoading = false
            result.onFailure { e ->
                _events.emit(PaywallEvent.Error(e.message ?: "Error al procesar el pago"))
            }.onSuccess { isActive ->
                if (isActive) _events.emit(PaywallEvent.PurchaseSuccess)
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            isLoading = true
            val result = subscriptionRepo.restorePurchases()
            isLoading = false
            result.fold(
                onSuccess = { active ->
                    _events.emit(if (active) PaywallEvent.RestoreSuccess else PaywallEvent.NothingToRestore)
                },
                onFailure = { e ->
                    _events.emit(PaywallEvent.Error(e.message ?: "Error al restaurar"))
                }
            )
        }
    }
}

sealed class PaywallEvent {
    data class Error(val message: String) : PaywallEvent()
    object PurchaseSuccess : PaywallEvent()
    object RestoreSuccess : PaywallEvent()
    object NothingToRestore : PaywallEvent()
}
