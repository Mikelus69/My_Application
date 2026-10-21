package de.touchgrass.logic

import kotlinx.serialization.Serializable

@Serializable
data class PointsBreakdown(val minutes: Int, val completed: Int, val newSport: Int, val weeklyGoal: Int, val total: Int)

object Points {
    const val PER_ACTIVITY = 10
    const val NEW_SPORT = 20
    const val WEEKLY_GOAL = 50

    fun calculate(durationMin: Int, firstOfSport: Boolean, weeklyGoalReached: Boolean): PointsBreakdown {
        val newSport = if (firstOfSport) NEW_SPORT else 0
        val weeklyGoal = if (weeklyGoalReached) WEEKLY_GOAL else 0
        return PointsBreakdown(
            minutes = durationMin,
            completed = PER_ACTIVITY,
            newSport = newSport,
            weeklyGoal = weeklyGoal,
            total = durationMin + PER_ACTIVITY + newSport + weeklyGoal,
        )
    }
}
