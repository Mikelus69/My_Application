package de.touchgrass.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.AuthSession
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.LoginRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val login: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val api: SportApi, private val session: AuthSession) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onLoginChange(value: String) = _state.update { it.copy(login = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val response = apiCall { api.login(LoginRequest(current.login.trim(), current.password)) }
                session.logIn(response.token)
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }
}
