package de.touchgrass.ui.map

import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

val GERMANY = CameraPosition(target = Position(longitude = 10.45, latitude = 51.16), zoom = 4.8)

data class GeoPoint(val latitude: Double, val longitude: Double)

// Tippt man auf eine Kopie der Welt neben der eigentlichen Karte, liefert MapLibre Längengrade außerhalb von ±180.
fun Position.toGeoPoint(): GeoPoint =
    GeoPoint(latitude, if (longitude in -180.0..180.0) longitude else (longitude + 180).mod(360.0) - 180)

fun GeoPoint.toPosition(): Position = Position(longitude = longitude, latitude = latitude)
