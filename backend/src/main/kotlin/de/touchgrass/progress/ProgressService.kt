package de.touchgrass.progress

import de.touchgrass.activities.ActivityRepository
import de.touchgrass.logic.BerlinTime
import de.touchgrass.logic.Streak
import de.touchgrass.logic.WeekTotals
import de.touchgrass.plugins.ApiException
import de.touchgrass.users.UserRepository
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.OffsetDateTime

class ProgressService(
    private val users: UserRepository,
    private val activities: ActivityRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun progress(userId: Long): ProgressDto {
        val user = users.findById(userId) ?: throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Bitte melde dich an.")
        val now = OffsetDateTime.now(clock)
        val week = BerlinTime.weekOf(now)
        val thisWeek = activities.inRange(userId, week)
        val totals = WeekTotals(thisWeek.sumOf { it.durationMin }, thisWeek.size, thisWeek.sumOf { it.caloriesKcal })
        val days = activities.list(userId).map { BerlinTime.date(it.startedAt) }.toSet()
        return ProgressDto(
            weeklyGoal = WeeklyGoalDto(user.goalType, user.goalTarget, totals.valueFor(user.goalType)),
            streak = StreakDto(current = Streak.current(days, BerlinTime.date(now)), longest = Streak.longest(days)),
            weekDays = Streak.weekDays(days, BerlinTime.date(week.from)),
        )
    }
}
