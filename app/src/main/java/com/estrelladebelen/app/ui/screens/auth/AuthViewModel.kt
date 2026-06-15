package com.estrelladebelen.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estrelladebelen.app.data.repository.AppContainer
import com.estrelladebelen.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val linkSent: Boolean = false,
    val sentEmail: String = "",
    val pendingLink: String? = null   // cross-device: link arrived but email unknown
)

class AuthViewModel : ViewModel() {

    private val userRepo: UserRepository = AppContainer.userRepository

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun sendSignInLink(email: String) {
        val e = email.trim()
        if (e.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Ingresá tu email")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = userRepo.sendSignInLink(e)
            _uiState.value = if (result.isSuccess) {
                AuthUiState(linkSent = true, sentEmail = e)
            } else {
                AuthUiState(error = friendlyError(result.exceptionOrNull()?.message))
            }
        }
    }

    fun handleEmailLink(link: String) {
        // Prefer in-memory email; fall back to SharedPreferences for killed-process case.
        val email = _uiState.value.sentEmail.ifBlank { userRepo.getSavedEmail() }
        if (email.isNullOrBlank()) {
            // Cross-device: app opened on a different device — we have the link
            // but not the email. Navigate to CheckEmailScreen to ask for it.
            _uiState.value = AuthUiState(pendingLink = link, linkSent = true)
            return
        }
        completeSignIn(email, link)
    }

    // Called from CheckEmailScreen when the user types their email (cross-device).
    fun completeWithEmail(email: String) {
        val link = _uiState.value.pendingLink ?: return
        val e = email.trim()
        if (e.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Ingresá tu email")
            return
        }
        completeSignIn(e, link)
    }

    private fun completeSignIn(email: String, link: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = userRepo.completeSignInWithLink(email, link)
            _uiState.value = if (result.isSuccess) {
                AuthUiState(isAuthenticated = true)
            } else {
                _uiState.value.copy(
                    isLoading = false,
                    error = "El enlace es inválido o ya expiró. Solicitá uno nuevo."
                )
            }
        }
    }

    fun clearLinkSent() {
        _uiState.value = _uiState.value.copy(linkSent = false)
    }

    fun clearAuth() {
        _uiState.value = AuthUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun friendlyError(msg: String?): String {
        if (msg == null) return "Error al enviar el enlace"
        return when {
            "invalid-email" in msg  -> "El correo no es válido"
            "network"       in msg  -> "Sin conexión a internet"
            else                    -> "Error al enviar el enlace"
        }
    }
}
