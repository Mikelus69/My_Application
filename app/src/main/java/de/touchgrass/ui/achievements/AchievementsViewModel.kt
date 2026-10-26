package de.touchgrass.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.AchievementDto
import de.touchgrass.data.dto.LeaderboardDto
import de.touchgrass.data.dto.LeaderboardMetric
import de.touchgrass.data.dto.LeaderboardPeriod
import de.touchgrass.data.dto.ProgressDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AchievementsUiState(
    val progress: UiState<ProgressDto> = UiState.Loading,
    val achievements: UiState<List<AchievementDto>> = UiState.Loading,
    val leaderboard: UiState<LeaderboardDto> = UiState.Loading,
    val period: LeaderboardPeriod = LeaderboardPeriod.WEEK,
    val metric: LeaderboardMetric = LeaderboardMetric.POINTS,
)

class AchievementsViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow(AchievementsUiState())
    val state: StateFlow<AchievementsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val progress = load { api.progress() }
            _state.update { it.copy(progress = progress) }
        }
        viewModelScope.launch {
            val achievements = load { api.achievements() }
            _state.update { it.copy(achievements = achievements) }
        }
        loadLeaderboard()
    }

    fun onPeriodChange(period: LeaderboardPeriod) {
        _state.update { it.copy(period = period) }
        loadLeaderboard()
    }

    fun onMetricChange(metric: LeaderboardMetric) {
        _state.update { it.copy(metric = metric) }
        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        val period = _state.value.period
        val metric = _state.value.metric
        _state.update { it.copy(leaderboard = UiState.Loading) }
        viewModelScope.launch {
            val leaderboard = load { api.leaderboard(period, metric) }
            // Eine ältere Antwort darf die Auswahl nicht überschreiben, wenn inzwischen umgeschaltet wurde.
            _state.update { if (it.period == period && it.metric == metric) it.copy(leaderboard = leaderboard) else it }
        }
    }

    private suspend fun <T> load(call: suspend () -> T): UiState<T> =
        try {
            UiState.Content(apiCall(call))
        } catch (e: ApiException) {
            UiState.Error(e.message)
        }
}
