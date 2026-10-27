package de.touchgrass.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.ui.UiState
import de.touchgrass.ui.activities.labelFor
import de.touchgrass.ui.activities.startedAtLabel
import de.touchgrass.ui.components.LoadingIndicator
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle

@Composable
fun MapScreen(viewModel: MapViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    when (val s = state) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        is UiState.Error -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::refresh) { Text("Erneut versuchen") }
        }
        is UiState.Content -> ActivityMap(s.data)
    }
}

private const val NO_MARKERS_HINT = "Noch keine Aktivitäten mit Ort. Wähl beim Speichern einen Ort, dann erscheint hier ein Marker."

@Composable
private fun ActivityMap(content: MapContent) {
    val cameraState = rememberCameraState(firstPosition = startPosition(content.activities))
    var selected by remember { mutableStateOf<ActivityDto?>(null) }
    var showMyLocation by remember { mutableStateOf(false) }
    var centerRequests by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf<String?>(null) }
    val requestLocation = rememberLocationRequest(
        onGranted = { hint = null; showMyLocation = true; centerRequests++ },
        onDenied = { hint = LOCATION_DENIED },
    )
    val ownColor = MaterialTheme.colorScheme.primary
    val (own, others) = content.activities.partition { it.mine }

    Box(Modifier.fillMaxSize()) {
        val userLocation = if (showMyLocation) rememberUserLocation() else null
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
            cameraState = cameraState,
        ) {
            ActivityMarkers("others", others, Color.Gray) { selected = it }
            ActivityMarkers("own", own, ownColor) { selected = it }
            userLocation?.let { LocationPuck("me", it, cameraState) }
        }

        val myPosition = userLocation?.location?.position
        if (myPosition != null) {
            LaunchedEffect(centerRequests) { cameraState.animateTo(CameraPosition(target = myPosition, zoom = 14.0)) }
        }

        (hint ?: NO_MARKERS_HINT.takeIf { content.activities.isEmpty() })?.let {
            Card(Modifier.align(Alignment.TopCenter).padding(16.dp)) { Text(it, Modifier.padding(16.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = requestLocation,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text("📍 Mein Standort") }
    }

    selected?.let { ActivitySheet(it, content.sports, onDismiss = { selected = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivitySheet(activity: ActivityDto, sports: List<SportDto>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(sports.labelFor(activity.sportCode), style = MaterialTheme.typography.headlineSmall)
            Text(if (activity.mine) "von dir" else "von ${activity.username}")
            Text(activity.startedAtLabel())
            Text("${activity.durationMin} Minuten · ~${activity.caloriesKcal} kcal")
            activity.description?.let { Text("„$it“") }
        }
    }
}
