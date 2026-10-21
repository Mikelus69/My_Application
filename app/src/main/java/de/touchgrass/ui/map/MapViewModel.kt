package de.touchgrass.ui.map

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
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition

data class MapContent(val activities: List<ActivityDto>, val sports: List<SportDto>)

class MapViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow<UiState<MapContent>>(UiState.Loading)
    val state: StateFlow<UiState<MapContent>> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = try {
                UiState.Content(MapContent(apiCall { api.mapActivities() }, apiCall { api.sports() }))
            } catch (e: ApiException) {
                UiState.Error(e.message)
            }
        }
    }
}

/** Die Liste kommt neueste zuerst, also steht die letzte eigene Aktivität vorne. */
fun startPosition(activities: List<ActivityDto>): CameraPosition =
    activities.firstNotNullOfOrNull { if (it.mine) it.geoPoint() else null }
        ?.let { CameraPosition(target = it.toPosition(), zoom = 13.0) }
        ?: GERMANY
