package de.touchgrass.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class WeekSummaryDto(val activities: Int, val minutes: Int, val kcal: Int)

@Serializable
data class WeekMinutesDto(val weekStart: String, val minutes: Int)

@Serializable
data class SportCountDto(val sportCode: String, val count: Int)

@Serializable
data class MonthKcalDto(val month: String, val kcal: Int)

@Serializable
data class StatsDto(
    val thisWeek: WeekSummaryDto,
    val minutesPerWeek: List<WeekMinutesDto>,
    val activitiesPerSport: List<SportCountDto>,
    val kcalPerMonth: List<MonthKcalDto>,
)
