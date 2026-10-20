package de.touchgrass.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerStatusViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow<UiState<HealthDto>>(UiState.Loading)
    val state: StateFlow<UiState<HealthDto>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                UiState.Content(apiCall { api.health() })
            } catch (e: ApiException) {
                UiState.Error(e.message)
            }
        }
    }
}
