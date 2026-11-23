package de.touchgrass.ui.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerState
import de.touchgrass.data.TimerStore
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.AchievementDto
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.Visibility
import de.touchgrass.data.uploadPhotoFile
import de.touchgrass.ui.activities.describe
import de.touchgrass.ui.map.GeoPoint
import de.touchgrass.ui.photo.PhotoException
import de.touchgrass.ui.photo.PhotoPreparer
import de.touchgrass.ui.photo.UNSUPPORTED_PHOTO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val PHOTO_UPLOAD_FAILED =
    "Gespeichert, aber das Foto konnte nicht hochgeladen werden. Du kannst es in den Details erneut hinzufügen."

private const val KEY_SPORT = "sportCode"
private const val KEY_DATE = "date"
private const val KEY_TIME = "time"
private const val KEY_DURATION = "duration"
private const val KEY_LATITUDE = "latitude"
private const val KEY_LONGITUDE = "longitude"
private const val KEY_DESCRIPTION = "description"
private const val KEY_VISIBILITY = "visibility"
private const val KEY_PHOTO = "photoPath"
private const val KEY_FROM_TIMER = "formFromTimer"

data class RecordUiState(
    val sports: List<SportDto> = emptyList(),
    val sportsError: String? = null,
    val sportCode: String? = null,
    val date: LocalDate,
    val time: LocalTime,
    val duration: String = "",
    val location: GeoPoint? = null,
    val description: String = "",
    val visibility: Visibility = Visibility.PRIVATE,
    val photoPath: String? = null,
    val photoBusy: Boolean = false,
    val fromTimer: Boolean = false,
    val saving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val savedMessage: String? = null,
    val newAchievements: List<AchievementDto> = emptyList(),
    val discarded: Boolean = false,
)

class RecordViewModel(
    private val api: SportApi,
    private val timerStore: TimerStore,
    fromTimer: Boolean,
    private val photos: PhotoPreparer,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _state = MutableStateFlow(emptyForm())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    init {
        val saved = savedForm()
        if (saved != null) {
            _state.value = saved
        } else if (fromTimer) {
            viewModelScope.launch { timerStore.state.first()?.let(::fillFromTimer) }
        }
        loadSports()
        viewModelScope.launch { _state.collect { keepForm(it) } }
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

    fun onSportChange(code: String) = edit("sportCode") { copy(sportCode = code) }

    fun onDateChange(date: LocalDate) = edit("startedAt") { copy(date = date) }

    fun onTimeChange(time: LocalTime) = edit("startedAt") { copy(time = time) }

    fun onDurationChange(value: String) = edit("durationMin") { copy(duration = value) }

    fun onLocationChange(location: GeoPoint?) = edit("latitude", "longitude") { copy(location = location) }

    fun onDescriptionChange(value: String) = edit("description") { copy(description = value) }

    fun onVisibilityChange(value: Visibility) = edit("visibility") { copy(visibility = value) }

    fun onPhotoPicked(source: String) {
        edit("photo") { copy(photoBusy = true) }
        viewModelScope.launch {
            try {
                val file = photos.prepare(source)
                _state.update { it.copy(photoPath = file.path, photoBusy = false) }
            } catch (e: PhotoException) {
                _state.update { it.copy(photoBusy = false) }
                showFieldError("photo", e.message ?: UNSUPPORTED_PHOTO)
            }
        }
    }

    fun onPhotoError(message: String) = showFieldError("photo", message)

    fun removePhoto() {
        _state.value.photoPath?.let { File(it).delete() }
        edit("photo") { copy(photoPath = null) }
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.photoBusy) return
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
                val photoUploaded = current.photoPath == null || tryUploadPhoto(response.activity.id, File(current.photoPath))
                val points = response.pointsBreakdown
                _state.value = emptyForm().copy(
                    sports = current.sports,
                    savedMessage = if (photoUploaded) {
                        "Gespeichert! ~${response.activity.caloriesKcal} kcal · +${points.total} Punkte (${points.describe()})"
                    } else {
                        PHOTO_UPLOAD_FAILED
                    },
                    newAchievements = response.newAchievements,
                )
            } catch (e: ApiException) {
                _state.update { it.copy(saving = false) }
                if (e.field != null) showFieldError(e.field, e.message) else _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun onAchievementsSeen() = _state.update { it.copy(newAchievements = emptyList()) }

    fun discard() {
        viewModelScope.launch {
            timerStore.clear()
            _state.update { it.copy(discarded = true) }
        }
    }

    private suspend fun tryUploadPhoto(activityId: Long, file: File): Boolean =
        try {
            apiCall { api.uploadPhotoFile(activityId, file) }
            true
        } catch (e: ApiException) {
            false
        } finally {
            file.delete()
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

    // Während des Speicherns steht kein Formular im Handle. Endet das Speichern im Hintergrund und beendet Android danach die App, käme sonst das schon gespeicherte Formular samt gelöschtem Foto zurück.
    private fun keepForm(form: RecordUiState) {
        if (form.saving) {
            savedState.remove<String>(KEY_DATE)
            return
        }
        savedState[KEY_SPORT] = form.sportCode
        savedState[KEY_DATE] = form.date.toString()
        savedState[KEY_TIME] = form.time.toString()
        savedState[KEY_DURATION] = form.duration
        savedState[KEY_LATITUDE] = form.location?.latitude
        savedState[KEY_LONGITUDE] = form.location?.longitude
        savedState[KEY_DESCRIPTION] = form.description
        savedState[KEY_VISIBILITY] = form.visibility.name
        savedState[KEY_PHOTO] = form.photoPath
        savedState[KEY_FROM_TIMER] = form.fromTimer
    }

    // Im Handle stehen auch die Argumente der Route. Ein gesichertes Formular erkennt man deshalb erst am Datum.
    private fun savedForm(): RecordUiState? {
        val date = savedState.get<String>(KEY_DATE) ?: return null
        val time = savedState.get<String>(KEY_TIME) ?: return null
        val latitude = savedState.get<Double>(KEY_LATITUDE)
        val longitude = savedState.get<Double>(KEY_LONGITUDE)
        return RecordUiState(
            sportCode = savedState.get<String>(KEY_SPORT),
            date = LocalDate.parse(date),
            time = LocalTime.parse(time),
            duration = savedState.get<String>(KEY_DURATION).orEmpty(),
            location = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
            description = savedState.get<String>(KEY_DESCRIPTION).orEmpty(),
            visibility = savedState.get<String>(KEY_VISIBILITY)?.let(Visibility::valueOf) ?: Visibility.PRIVATE,
            photoPath = savedState.get<String>(KEY_PHOTO),
            fromTimer = savedState.get<Boolean>(KEY_FROM_TIMER) == true,
        )
    }

    private fun edit(vararg fields: String, change: RecordUiState.() -> RecordUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - fields.toSet(), error = null, savedMessage = null) }

    private fun showFieldError(field: String, message: String) =
        _state.update { it.copy(fieldErrors = it.fieldErrors + (field to message)) }
}
