package com.rmp.trader.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rmp.trader.data.Repository
import com.rmp.trader.data.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, REGISTER }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val mode: AuthMode = AuthMode.LOGIN,
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class AuthViewModel(private val repository: Repository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, error = null) }

    fun toggleMode() = _state.update {
        it.copy(
            mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN,
            error = null
        )
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return

        val email = current.email.trim()
        val password = current.password
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Email and password are required.") }
            return
        }

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                if (current.mode == AuthMode.REGISTER) {
                    repository.register(email, password)
                } else {
                    repository.login(email, password)
                }
                _state.update { it.copy(loading = false, success = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Network error. Is the gateway running?")
                }
            }
        }
    }

    /** Called by the screen once navigation away has been consumed. */
    fun consumeSuccess() = _state.update { it.copy(success = false) }
}
