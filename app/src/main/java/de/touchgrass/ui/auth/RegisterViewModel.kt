package de.touchgrass.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.AuthSession
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.RegisterRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
)

class RegisterViewModel(private val api: SportApi, private val session: AuthSession) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) = edit("username") { copy(username = value) }

    fun onEmailChange(value: String) = edit("email") { copy(email = value) }

    fun onPasswordChange(value: String) = edit("password") { copy(password = value) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        _state.update { it.copy(loading = true, fieldErrors = emptyMap(), error = null) }
        viewModelScope.launch {
            try {
                val request = RegisterRequest(current.username.trim(), current.email.trim(), current.password)
                session.logIn(apiCall { api.register(request) }.token)
            } catch (e: ApiException) {
                _state.update {
                    if (e.field != null) it.copy(loading = false, fieldErrors = mapOf(e.field to e.message))
                    else it.copy(loading = false, error = e.message)
                }
            }
        }
    }

    private fun edit(field: String, change: RegisterUiState.() -> RegisterUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, error = null) }
}
