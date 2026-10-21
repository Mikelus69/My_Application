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
import java.time.Clock
import java.time.LocalDate

enum class Period(val label: String, val days: Long?) {
    ALL("Alle", null),
    WEEK("7 Tage", 7),
    MONTH("30 Tage", 30),
}

data class HistoryUiState(
    val sports: List<SportDto> = emptyList(),
    val sportFilter: String? = null,
    val period: Period = Period.ALL,
    val activities: UiState<List<ActivityDto>> = UiState.Loading,
)

class HistoryViewModel(private val api: SportApi, private val clock: Clock = Clock.systemDefaultZone()) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun onSportFilterChange(code: String?) {
        _state.update { it.copy(sportFilter = code) }
        refresh()
    }

    fun onPeriodChange(period: Period) {
        _state.update { it.copy(period = period) }
        refresh()
    }

    fun refresh() {
        val current = _state.value
        val from = current.period.days?.let { LocalDate.now(clock).minusDays(it - 1).toString() }
        viewModelScope.launch {
            try {
                val sports = current.sports.ifEmpty { apiCall { api.sports() } }
                val activities = apiCall { api.activities(sport = current.sportFilter, from = from) }
                _state.update { it.copy(sports = sports, activities = UiState.Content(activities)) }
            } catch (e: ApiException) {
                _state.update { it.copy(activities = UiState.Error(e.message)) }
            }
        }
    }
}
