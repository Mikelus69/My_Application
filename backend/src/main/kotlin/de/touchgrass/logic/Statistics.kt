package de.touchgrass.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class ActivityFact(val date: LocalDate, val durationMin: Int, val kcal: Int, val sportCode: String)

data class WeekValue(val weekStart: LocalDate, val value: Int)

data class MonthValue(val month: YearMonth, val value: Int)

data class SportCount(val sportCode: String, val count: Int)

object Statistics {
    const val WEEKS = 8
    const val MONTHS = 6

    fun thisWeek(facts: List<ActivityFact>, today: LocalDate): WeekTotals {
        val monday = mondayOf(today)
        val week = facts.filter { mondayOf(it.date) == monday }
        return WeekTotals(minutes = week.sumOf { it.durationMin }, activities = week.size, kcal = week.sumOf { it.kcal })
    }

    fun minutesPerWeek(facts: List<ActivityFact>, today: LocalDate): List<WeekValue> {
        val minutes = facts.groupBy { mondayOf(it.date) }.mapValues { (_, week) -> week.sumOf { it.durationMin } }
        val current = mondayOf(today)
        return (WEEKS - 1 downTo 0).map { back ->
            val monday = current.minusWeeks(back.toLong())
            WeekValue(monday, minutes[monday] ?: 0)
        }
    }

    fun kcalPerMonth(facts: List<ActivityFact>, today: LocalDate): List<MonthValue> {
        val kcal = facts.groupBy { YearMonth.from(it.date) }.mapValues { (_, month) -> month.sumOf { it.kcal } }
        val current = YearMonth.from(today)
        return (MONTHS - 1 downTo 0).map { back ->
            val month = current.minusMonths(back.toLong())
            MonthValue(month, kcal[month] ?: 0)
        }
    }

    fun activitiesPerSport(facts: List<ActivityFact>): List<SportCount> = facts
        .groupingBy { it.sportCode }
        .eachCount()
        .map { (code, count) -> SportCount(code, count) }
        .sortedWith(compareByDescending<SportCount> { it.count }.thenBy { it.sportCode })

    private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
