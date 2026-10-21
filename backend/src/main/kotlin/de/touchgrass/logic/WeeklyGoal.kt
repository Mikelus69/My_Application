package de.touchgrass.logic

import de.touchgrass.users.GoalType

data class WeekTotals(val minutes: Int = 0, val activities: Int = 0, val kcal: Int = 0) {
    fun valueFor(type: GoalType): Int = when (type) {
        GoalType.MINUTES -> minutes
        GoalType.ACTIVITIES -> activities
        GoalType.KCAL -> kcal
    }

    fun plusActivity(durationMin: Int, kcal: Int) = WeekTotals(minutes + durationMin, activities + 1, this.kcal + kcal)
}

object WeeklyGoal {
    fun bonusEarned(type: GoalType, target: Int, before: WeekTotals, after: WeekTotals, bonusAlreadyGiven: Boolean): Boolean =
        !bonusAlreadyGiven && before.valueFor(type) < target && after.valueFor(type) >= target
}
