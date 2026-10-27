package de.touchgrass.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.StatsDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsContent(val stats: StatsDto, val sports: List<SportDto>)

class StatsViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow<UiState<StatsContent>>(UiState.Loading)
    val state: StateFlow<UiState<StatsContent>> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = try {
                UiState.Content(StatsContent(apiCall { api.stats() }, apiCall { api.sports() }))
            } catch (e: ApiException) {
                UiState.Error(e.message)
            }
        }
    }
}
