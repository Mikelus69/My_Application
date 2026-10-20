package de.touchgrass.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val token: String) : AuthState
}

class AuthSession(private val tokenStore: TokenStore, private val scope: CoroutineScope) {
    val state: StateFlow<AuthState> = tokenStore.token
        .map { token -> if (token == null) AuthState.LoggedOut else AuthState.LoggedIn(token) }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    fun currentToken(): String? = (state.value as? AuthState.LoggedIn)?.token

    suspend fun logIn(token: String) = tokenStore.save(token)

    suspend fun logOut() = tokenStore.clear()

    fun onUnauthorized() {
        scope.launch { tokenStore.clear() }
    }
}
