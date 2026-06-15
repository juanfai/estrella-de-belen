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
    val passwordResetSent: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val userRepo: UserRepository = AppContainer.userRepository

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun signIn(email: String, password: String) {
        val e = email.trim()
        val p = password
        if (e.isBlank()) { _uiState.value = _uiState.value.copy(error = "Ingresá tu email"); return }
        if (p.isBlank()) { _uiState.value = _uiState.value.copy(error = "Ingresá tu contraseña"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = userRepo.signIn(e, p)
            _uiState.value = if (result.isSuccess) {
                AuthUiState(isAuthenticated = true)
            } else {
                AuthUiState(error = friendlyError(result.exceptionOrNull()))
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val e = email.trim()
        if (e.isBlank()) { _uiState.value = _uiState.value.copy(error = "Ingresá tu email"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, passwordResetSent = false)
            val result = userRepo.sendPasswordResetEmail(e)
            _uiState.value = if (result.isSuccess) {
                _uiState.value.copy(isLoading = false, passwordResetSent = true, error = null)
            } else {
                _uiState.value.copy(isLoading = false, error = friendlyError(result.exceptionOrNull()))
            }
        }
    }

    fun clearAuth() { _uiState.value = AuthUiState() }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearPasswordResetSent() { _uiState.value = _uiState.value.copy(passwordResetSent = false) }

    private fun friendlyError(e: Throwable?): String {
        val msg = e?.message ?: return "Error al iniciar sesión"
        return when {
            "invalid-email"      in msg -> "El correo no es válido"
            "user-not-found"     in msg -> "No existe una cuenta con ese correo"
            "wrong-password"     in msg -> "Contraseña incorrecta"
            "invalid-credential" in msg -> "Email o contraseña incorrectos"
            "user-disabled"      in msg -> "Esta cuenta fue deshabilitada"
            "network"            in msg -> "Sin conexión a internet"
            else                        -> "Error al iniciar sesión"
        }
    }
}
