package de.touchgrass.ui.map

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.testing.testActivity
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.GeoJsonObject
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.geojson.toJson

class MapDataTest {
    private val own = testActivity(id = 1).copy(latitude = 48.1642, longitude = 11.6056)
    private val other = testActivity(id = 2).copy(mine = false, username = "lisa", latitude = 52.52, longitude = 13.405)

    @Test
    fun `Längengrad aus einer Kopie der Welt wird zurückgerechnet`() {
        assertEquals(GeoPoint(48.1642, 11.6056), Position(longitude = 11.6056, latitude = 48.1642).toGeoPoint())
        assertEquals(10.0, Position(longitude = 370.0, latitude = 0.0).toGeoPoint().longitude, 1e-9)
        assertEquals(170.0, Position(longitude = -190.0, latitude = 0.0).toGeoPoint().longitude, 1e-9)
    }

    @Test
    fun `nur Aktivitäten mit Standort werden zu Markern mit ihrer ID`() {
        val features = listOf(own, testActivity(id = 3)).toFeatureCollection().features

        assertEquals(1, features.size)
        assertEquals(Position(longitude = 11.6056, latitude = 48.1642), features.single().geometry.coordinates)
        assertEquals("1", features.single().properties?.get("id")?.jsonPrimitive?.content)
    }

    @Test
    fun `leere Markerliste lässt sich wie in MapLibre in JSON umwandeln`() {
        val markers: GeoJsonObject = emptyList<ActivityDto>().toFeatureCollection()

        assertTrue(markers.toJson().contains("\"features\":[]"))
    }

    @Test
    fun `Karte startet über der letzten eigenen Aktivität, sonst über Deutschland`() {
        assertEquals(Position(longitude = 11.6056, latitude = 48.1642), startPosition(listOf(other, own)).target)
        assertEquals(GERMANY, startPosition(listOf(other)))
        assertEquals(GERMANY, startPosition(emptyList()))
    }
}
