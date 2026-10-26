package de.touchgrass.logic

import java.time.LocalDate

object Streak {
    fun current(days: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in days) today else today.minusDays(1)
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun longest(days: Set<LocalDate>): Int = days
        .filter { it.minusDays(1) !in days }
        .maxOfOrNull { start -> generateSequence(start) { it.plusDays(1) }.takeWhile { it in days }.count() }
        ?: 0

    fun weekDays(days: Set<LocalDate>, monday: LocalDate): List<Boolean> = (0L..6L).map { monday.plusDays(it) in days }
}
