package de.touchgrass.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerState
import de.touchgrass.data.TimerStore
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.SportDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

data class TimerUiState(
    val sports: List<SportDto> = emptyList(),
    val sportsError: String? = null,
    val sportCode: String? = null,
    val timer: TimerState? = null,
    val error: String? = null,
    val openForm: Boolean = false,
)

class TimerViewModel(
    private val api: SportApi,
    private val timerStore: TimerStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    init {
        loadSports()
        viewModelScope.launch {
            timerStore.state.collect { timer ->
                _state.update { it.copy(timer = timer, sportCode = timer?.sportCode ?: it.sportCode) }
            }
        }
    }

    fun loadSports() {
        viewModelScope.launch {
            try {
                val sports = apiCall { api.sports() }
                _state.update { it.copy(sports = sports, sportsError = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(sportsError = e.message) }
            }
        }
    }

    fun onSportChange(code: String) = _state.update { it.copy(sportCode = code, error = null) }

    fun start() {
        val sportCode = _state.value.sportCode ?: return _state.update { it.copy(error = "Bitte eine Sportart wählen.") }
        save(TimerState(sportCode, clock.instant()))
    }

    fun pause() {
        _state.value.timer?.let { save(it.pause(clock.instant())) }
    }

    fun resume() {
        _state.value.timer?.let { save(it.resume(clock.instant())) }
    }

    fun stop() {
        val timer = _state.value.timer ?: return
        viewModelScope.launch {
            timerStore.save(timer.pause(clock.instant()))
            _state.update { it.copy(openForm = true) }
        }
    }

    fun onFormOpened() = _state.update { it.copy(openForm = false) }

    private fun save(timer: TimerState) {
        viewModelScope.launch { timerStore.save(timer) }
    }
}
