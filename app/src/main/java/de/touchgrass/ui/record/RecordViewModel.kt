package de.touchgrass.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerState
import de.touchgrass.data.TimerStore
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.Visibility
import de.touchgrass.ui.activities.describe
import de.touchgrass.ui.map.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class RecordUiState(
    val sports: List<SportDto> = emptyList(),
    val sportCode: String? = null,
    val date: LocalDate,
    val time: LocalTime,
    val duration: String = "",
    val location: GeoPoint? = null,
    val description: String = "",
    val visibility: Visibility = Visibility.PRIVATE,
    val fromTimer: Boolean = false,
    val saving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val savedMessage: String? = null,
    val discarded: Boolean = false,
)

class RecordViewModel(
    private val api: SportApi,
    private val timerStore: TimerStore,
    fromTimer: Boolean,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _state = MutableStateFlow(emptyForm())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    init {
        loadSports()
        if (fromTimer) {
            viewModelScope.launch { timerStore.state.first()?.let(::fillFromTimer) }
        }
    }

    fun loadSports() {
        viewModelScope.launch {
            try {
                val sports = apiCall { api.sports() }
                _state.update { it.copy(sports = sports, error = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun onSportChange(code: String) = edit("sportCode") { copy(sportCode = code) }

    fun onDateChange(date: LocalDate) = edit("startedAt") { copy(date = date) }

    fun onTimeChange(time: LocalTime) = edit("startedAt") { copy(time = time) }

    fun onDurationChange(value: String) = edit("durationMin") { copy(duration = value) }

    fun onLocationChange(location: GeoPoint?) = edit("latitude", "longitude") { copy(location = location) }

    fun onDescriptionChange(value: String) = edit("description") { copy(description = value) }

    fun onVisibilityChange(value: Visibility) = edit("visibility") { copy(visibility = value) }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val sportCode = current.sportCode ?: return showFieldError("sportCode", "Bitte eine Sportart wählen.")
        val duration = current.duration.trim().toIntOrNull()
            ?: return showFieldError("durationMin", "Bitte die Dauer in ganzen Minuten angeben.")
        val request = CreateActivityRequest(
            sportCode = sportCode,
            startedAt = ZonedDateTime.of(current.date, current.time, clock.zone)
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            durationMin = duration,
            latitude = current.location?.latitude,
            longitude = current.location?.longitude,
            description = current.description.trim().ifEmpty { null },
            visibility = current.visibility,
        )
        _state.update { it.copy(saving = true, fieldErrors = emptyMap(), error = null, savedMessage = null) }
        viewModelScope.launch {
            try {
                val response = apiCall { api.createActivity(request) }
                if (current.fromTimer) timerStore.clear()
                val points = response.pointsBreakdown
                _state.value = emptyForm().copy(
                    sports = current.sports,
                    savedMessage = "Gespeichert! ~${response.activity.caloriesKcal} kcal · +${points.total} Punkte (${points.describe()})",
                )
            } catch (e: ApiException) {
                _state.update { it.copy(saving = false) }
                if (e.field != null) showFieldError(e.field, e.message) else _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun discard() {
        viewModelScope.launch {
            timerStore.clear()
            _state.update { it.copy(discarded = true) }
        }
    }

    private fun fillFromTimer(timer: TimerState) {
        val start = LocalDateTime.ofInstant(timer.startedAt, clock.zone).truncatedTo(ChronoUnit.MINUTES)
        _state.update {
            it.copy(
                sportCode = timer.sportCode,
                date = start.toLocalDate(),
                time = start.toLocalTime(),
                duration = timer.durationMinutes(clock.instant()).toString(),
                fromTimer = true,
            )
        }
    }

    private fun emptyForm(): RecordUiState {
        val now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        return RecordUiState(date = now.toLocalDate(), time = now.toLocalTime())
    }

    private fun edit(vararg fields: String, change: RecordUiState.() -> RecordUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - fields.toSet(), error = null, savedMessage = null) }

    private fun showFieldError(field: String, message: String) =
        _state.update { it.copy(fieldErrors = it.fieldErrors + (field to message)) }
}
