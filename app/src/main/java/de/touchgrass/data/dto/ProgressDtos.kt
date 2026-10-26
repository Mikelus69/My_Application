package de.touchgrass.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AchievementDto(
    val code: String,
    val name: String,
    val description: String,
    val emoji: String,
    val threshold: Int,
    val progress: Int,
    val unlockedAt: String? = null,
)

@Serializable
data class WeeklyGoalDto(val type: GoalType, val target: Int, val current: Int)

@Serializable
data class StreakDto(val current: Int, val longest: Int)

@Serializable
data class ProgressDto(val weeklyGoal: WeeklyGoalDto, val streak: StreakDto, val weekDays: List<Boolean>)

@Serializable
enum class LeaderboardPeriod { WEEK, MONTH }

@Serializable
enum class LeaderboardMetric { POINTS, MINUTES }

@Serializable
data class LeaderboardEntryDto(val rank: Int, val username: String, val value: Int)

@Serializable
data class LeaderboardDto(
    val period: LeaderboardPeriod,
    val metric: LeaderboardMetric,
    val entries: List<LeaderboardEntryDto>,
    val me: LeaderboardEntryDto? = null,
)
