package de.touchgrass.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityDetailUiState(
    val sports: List<SportDto> = emptyList(),
    val activity: UiState<ActivityDto> = UiState.Loading,
    val deleted: Boolean = false,
    val error: String? = null,
)

class ActivityDetailViewModel(private val api: SportApi, private val id: Long) : ViewModel() {
    private val _state = MutableStateFlow(ActivityDetailUiState())
    val state: StateFlow<ActivityDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(activity = UiState.Loading) }
        viewModelScope.launch {
            try {
                val sports = apiCall { api.sports() }
                val activity = apiCall { api.activity(id) }
                _state.update { it.copy(sports = sports, activity = UiState.Content(activity)) }
            } catch (e: ApiException) {
                _state.update { it.copy(activity = UiState.Error(e.message)) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                apiCall { api.deleteActivity(id) }
                _state.update { it.copy(deleted = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
