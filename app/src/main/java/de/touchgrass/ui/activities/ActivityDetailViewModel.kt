package de.touchgrass.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.PendingPhotos
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.ui.UiState
import de.touchgrass.ui.photo.PhotoException
import de.touchgrass.ui.photo.PhotoPreparer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityDetailUiState(
    val sports: List<SportDto> = emptyList(),
    val activity: UiState<ActivityDto> = UiState.Loading,
    val photoBusy: Boolean = false,
    val photoPending: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
)

class ActivityDetailViewModel(
    private val api: SportApi,
    private val id: Long,
    private val photos: PhotoPreparer,
    private val pending: PendingPhotos,
) : ViewModel() {
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
                _state.update { it.copy(sports = sports, activity = UiState.Content(activity), photoPending = pending.has(id)) }
            } catch (e: ApiException) {
                _state.update { it.copy(activity = UiState.Error(e.message)) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                apiCall { api.deleteActivity(id) }
                pending.remove(id)
                _state.update { it.copy(deleted = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun onPhotoPicked(source: String) = changePhoto { pending.upload(api, id, photos.prepare(source)) }

    fun retryPhotoUpload() = changePhoto { pending.upload(api, id, pending.file(id)) }

    fun onPhotoError(message: String) = _state.update { it.copy(error = message) }

    fun removePhoto() {
        val current = _state.value.activity
        if (current !is UiState.Content) return
        changePhoto {
            apiCall { api.deletePhoto(id) }
            current.data.copy(photoVersion = null)
        }
    }

    private fun changePhoto(change: suspend () -> ActivityDto) {
        _state.update { it.copy(photoBusy = true, error = null) }
        viewModelScope.launch {
            try {
                val activity = change()
                _state.update { it.copy(activity = UiState.Content(activity), photoBusy = false, photoPending = pending.has(id)) }
            } catch (e: PhotoException) {
                _state.update { it.copy(photoBusy = false, error = e.message) }
            } catch (e: ApiException) {
                _state.update { it.copy(photoBusy = false, photoPending = pending.has(id), error = e.message) }
            }
        }
    }
}
