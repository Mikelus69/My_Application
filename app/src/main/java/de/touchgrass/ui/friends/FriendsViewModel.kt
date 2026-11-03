package de.touchgrass.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.FriendsDto
import de.touchgrass.data.dto.SendFriendRequest
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val friends: UiState<FriendsDto> = UiState.Loading,
    val username: String = "",
    val usernameError: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class FriendsViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val friends = try {
                UiState.Content(apiCall { api.friends() })
            } catch (e: ApiException) {
                UiState.Error(e.message)
            }
            _state.update { it.copy(friends = friends) }
        }
    }

    fun onUsernameChange(value: String) =
        _state.update { it.copy(username = value, usernameError = null, message = null, error = null) }

    fun sendRequest() {
        val name = _state.value.username.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(usernameError = "Bitte einen Benutzernamen eingeben.", message = null) }
            return
        }
        change({ api.sendFriendRequest(SendFriendRequest(name)) }) { friends ->
            val friend = friends.friends.firstOrNull { it.equals(name, ignoreCase = true) }
            val sentTo = friends.outgoing.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
            copy(username = "", message = if (friend != null) "Du und $friend seid jetzt Freunde." else "Anfrage an $sentTo gesendet.")
        }
    }

    fun accept(username: String) = change({ api.acceptFriendRequest(username) }) {
        copy(message = "Du und $username seid jetzt Freunde.")
    }

    fun deleteRequest(username: String) = change({ api.deleteFriendRequest(username) })

    fun remove(username: String) = change({ api.removeFriend(username) })

    private fun change(call: suspend () -> FriendsDto, after: FriendsUiState.(FriendsDto) -> FriendsUiState = { this }) {
        viewModelScope.launch {
            try {
                val friends = apiCall(call)
                _state.update { it.copy(friends = UiState.Content(friends), message = null, error = null).after(friends) }
            } catch (e: ApiException) {
                _state.update {
                    if (e.field == "username") it.copy(usernameError = e.message, message = null)
                    else it.copy(error = e.message, message = null)
                }
            }
        }
    }
}
