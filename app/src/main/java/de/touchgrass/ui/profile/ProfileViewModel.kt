package de.touchgrass.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.UserDto
import de.touchgrass.data.dto.WeekSummaryDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileData(val user: UserDto, val thisWeek: WeekSummaryDto)

class ProfileViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ProfileData>>(UiState.Loading)
    val state: StateFlow<UiState<ProfileData>> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = try {
                UiState.Content(ProfileData(apiCall { api.me() }, apiCall { api.stats() }.thisWeek))
            } catch (e: ApiException) {
                UiState.Error(e.message)
            }
        }
    }
}
