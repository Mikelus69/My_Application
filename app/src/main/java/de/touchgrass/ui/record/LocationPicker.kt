package de.touchgrass.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.touchgrass.ui.map.GERMANY
import de.touchgrass.ui.map.GeoPoint
import de.touchgrass.ui.map.LOCATION_DENIED
import de.touchgrass.ui.map.MAP_STYLE_URL
import de.touchgrass.ui.map.PointMarker
import de.touchgrass.ui.map.rememberLocationRequest
import de.touchgrass.ui.map.rememberUserLocation
import de.touchgrass.ui.map.toGeoPoint
import de.touchgrass.ui.map.toPosition
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.RenderOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import java.util.Locale

@Composable
fun LocationPicker(location: GeoPoint?, error: String?, onLocationChange: (GeoPoint?) -> Unit) {
    val scope = rememberCoroutineScope()
    val cameraState = rememberCameraState(
        firstPosition = location?.let { CameraPosition(target = it.toPosition(), zoom = 14.0) } ?: GERMANY,
    )
    var locating by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val requestLocation = rememberLocationRequest(
        onGranted = { hint = null; locating = true },
        onDenied = { hint = LOCATION_DENIED },
    )
    val markerColor = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ort (freiwillig) – auf die Karte tippen")
        MaplibreMap(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
            cameraState = cameraState,
            // TextureView statt SurfaceView, damit die Karte beim Scrollen des Formulars mitwandert.
            options = MapOptions(renderOptions = RenderOptions(renderMode = RenderOptions.RenderMode.TextureView)),
            onMapClick = { position, _ ->
                onLocationChange(position.toGeoPoint())
                ClickResult.Consume
            },
        ) {
            location?.let { PointMarker("selected-location", it, markerColor) }
        }
        Text(
            location?.let { "📍 %.5f, %.5f".format(Locale.ROOT, it.latitude, it.longitude) } ?: "Kein Ort gewählt",
            style = MaterialTheme.typography.bodySmall,
        )
        hint?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = requestLocation, enabled = !locating) {
                Text(if (locating) "Suche Standort …" else "📍 Aktueller Standort")
            }
            TextButton(onClick = { onLocationChange(null) }, enabled = location != null) { Text("Ohne Standort") }
        }
    }

    if (locating) {
        val position = rememberUserLocation().location?.position
        LaunchedEffect(position != null) {
            if (position != null) {
                onLocationChange(position.toGeoPoint())
                scope.launch { cameraState.animateTo(CameraPosition(target = position, zoom = 15.0)) }
                locating = false
            }
        }
    }
}
