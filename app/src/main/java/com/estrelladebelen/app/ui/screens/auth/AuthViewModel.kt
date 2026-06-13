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
    val isAuthenticated: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val userRepo: UserRepository = AppContainer.userRepository

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun signIn(email: String, password: String) {
        val e = email.trim()
        val p = password.trim()
        if (e.isBlank() || p.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Completá todos los campos")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = userRepo.signIn(e, p)
            _uiState.value = if (result.isSuccess) {
                AuthUiState(isAuthenticated = true)
            } else {
                AuthUiState(error = result.exceptionOrNull()?.message ?: "Error al iniciar sesión")
            }
        }
    }

    fun register(name: String, email: String, password: String, confirmPassword: String) {
        val n  = name.trim()
        val e  = email.trim()
        val p  = password.trim()
        val cp = confirmPassword.trim()
        when {
            n.isBlank() || e.isBlank() || p.isBlank() ->
                _uiState.value = _uiState.value.copy(error = "Completá todos los campos")
            p != cp ->
                _uiState.value = _uiState.value.copy(error = "Las contraseñas no coinciden")
            p.length < 6 ->
                _uiState.value = _uiState.value.copy(error = "La contraseña debe tener al menos 6 caracteres")
            else -> viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                val result = userRepo.register(n, e, p)
                _uiState.value = if (result.isSuccess) {
                    AuthUiState(isAuthenticated = true)
                } else {
                    AuthUiState(error = result.exceptionOrNull()?.message ?: "Error al registrarse")
                }
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
