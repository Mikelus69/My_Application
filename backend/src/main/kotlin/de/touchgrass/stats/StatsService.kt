package de.touchgrass.stats

import de.touchgrass.activities.ActivityRepository
import de.touchgrass.logic.ActivityFact
import de.touchgrass.logic.BerlinTime
import de.touchgrass.logic.Statistics
import java.time.Clock
import java.time.OffsetDateTime

class StatsService(
    private val activities: ActivityRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun stats(userId: Long): StatsDto {
        val today = BerlinTime.date(OffsetDateTime.now(clock))
        val facts = activities.list(userId).map {
            ActivityFact(BerlinTime.date(it.startedAt), it.durationMin, it.caloriesKcal, it.sportCode)
        }
        val week = Statistics.thisWeek(facts, today)
        return StatsDto(
            thisWeek = WeekSummaryDto(activities = week.activities, minutes = week.minutes, kcal = week.kcal),
            minutesPerWeek = Statistics.minutesPerWeek(facts, today).map { WeekMinutesDto(it.weekStart.toString(), it.value) },
            activitiesPerSport = Statistics.activitiesPerSport(facts).map { SportCountDto(it.sportCode, it.count) },
            kcalPerMonth = Statistics.kcalPerMonth(facts, today).map { MonthKcalDto(it.month.toString(), it.value) },
        )
    }
}
