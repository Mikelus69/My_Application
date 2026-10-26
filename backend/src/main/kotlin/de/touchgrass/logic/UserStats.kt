package de.touchgrass.logic

enum class Requirement { TOTAL_ACTIVITIES, TOTAL_MINUTES, TOTAL_KCAL, SPORT_ACTIVITIES, STREAK_DAYS }

data class UserStats(
    val activities: Int = 0,
    val minutes: Int = 0,
    val kcal: Int = 0,
    val activitiesPerSport: Map<String, Int> = emptyMap(),
    val longestStreak: Int = 0,
) {
    fun valueFor(requirement: Requirement, sportCode: String?): Int = when (requirement) {
        Requirement.TOTAL_ACTIVITIES -> activities
        Requirement.TOTAL_MINUTES -> minutes
        Requirement.TOTAL_KCAL -> kcal
        Requirement.SPORT_ACTIVITIES -> activitiesPerSport[sportCode] ?: 0
        Requirement.STREAK_DAYS -> longestStreak
    }
}

object AchievementRules {
    fun isReached(value: Int, threshold: Int): Boolean = value >= threshold

    // Freigeschaltet bleibt freigeschaltet, auch wenn später Aktivitäten gelöscht werden.
    fun progress(value: Int, threshold: Int, unlocked: Boolean): Int = if (unlocked) threshold else minOf(value, threshold)
}
