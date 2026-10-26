package de.touchgrass.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SportDto(val code: String, val name: String, val emoji: String, val met: Double)

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
data class PointsBreakdownDto(val minutes: Int, val completed: Int, val newSport: Int, val weeklyGoal: Int, val total: Int)

@Serializable
data class CreateActivityResponse(
    val activity: ActivityDto,
    val pointsBreakdown: PointsBreakdownDto,
    val newAchievements: List<AchievementDto> = emptyList(),
)
