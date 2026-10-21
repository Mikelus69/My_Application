package de.touchgrass.activities

import de.touchgrass.logic.PointsBreakdown
import kotlinx.serialization.Serializable

@Serializable
enum class Visibility { PRIVATE, PUBLIC }

@Serializable
data class CreateActivityRequest(
    val sportCode: String,
    val startedAt: String,
    val durationMin: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val visibility: Visibility = Visibility.PRIVATE,
)

@Serializable
data class ActivityDto(
    val id: Long,
    val username: String,
    val mine: Boolean,
    val sportCode: String,
    val startedAt: String,
    val durationMin: Int,
    val caloriesKcal: Int,
    val points: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val visibility: Visibility,
)

@Serializable
data class CreateActivityResponse(val activity: ActivityDto, val pointsBreakdown: PointsBreakdown)
