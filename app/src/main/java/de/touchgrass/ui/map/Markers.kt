package de.touchgrass.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.touchgrass.data.dto.ActivityDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

fun ActivityDto.geoPoint(): GeoPoint? =
    if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null

// Die ID steht als Text in den Eigenschaften, weil MapLibre Zahlen beim Antippen als Kommazahl zurückgibt.
fun List<ActivityDto>.toFeatureCollection(): FeatureCollection<Point, JsonObject?> =
    FeatureCollection(
        mapNotNull { activity ->
            activity.geoPoint()?.let { point ->
                Feature(geometry = Point(point.toPosition()), properties = buildJsonObject { put("id", activity.id.toString()) })
            }
        }
    )

@Composable
@MaplibreComposable
fun ActivityMarkers(id: String, activities: List<ActivityDto>, color: Color, onClick: (ActivityDto) -> Unit) {
    val data = remember(activities) { GeoJsonData.Features(activities.toFeatureCollection()) }
    val source = rememberGeoJsonSource(data)
    CircleLayer(
        id = id,
        source = source,
        color = const(color),
        radius = const(8.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        onClick = { features ->
            val clickedId = features.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content
            val activity = activities.firstOrNull { it.id.toString() == clickedId }
            if (activity != null) {
                onClick(activity)
                ClickResult.Consume
            } else {
                ClickResult.Pass
            }
        },
    )
}

@Composable
@MaplibreComposable
fun PointMarker(id: String, point: GeoPoint, color: Color) {
    val data = remember(point) { GeoJsonData.Features(Point(point.toPosition())) }
    val source = rememberGeoJsonSource(data)
    CircleLayer(
        id = id,
        source = source,
        color = const(color),
        radius = const(8.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
    )
}
