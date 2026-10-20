package de.touchgrass.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.UserDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow<UiState<UserDto>>(UiState.Loading)
    val state: StateFlow<UiState<UserDto>> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = try {
                UiState.Content(apiCall { api.me() })
            } catch (e: ApiException) {
                UiState.Error(e.message)
            }
        }
    }
}
