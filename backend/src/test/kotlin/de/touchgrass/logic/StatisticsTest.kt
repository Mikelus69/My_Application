package de.touchgrass.logic

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class StatisticsTest {
    private val today = LocalDate.parse("2026-10-08")

    private fun fact(date: String, minutes: Int = 30, kcal: Int = 200, sport: String = "RUNNING") =
        ActivityFact(LocalDate.parse(date), minutes, kcal, sport)

    @Test
    fun `diese Woche zählt Montag bis heute`() {
        val facts = listOf(fact("2026-10-04"), fact("2026-10-05", minutes = 40), fact("2026-10-08", minutes = 20, kcal = 150))

        assertEquals(WeekTotals(minutes = 60, activities = 2, kcal = 350), Statistics.thisWeek(facts, today))
    }

    @Test
    fun `acht Wochen, älteste zuerst, Wochen ohne Sport mit 0`() {
        val facts = listOf(fact("2026-08-17"), fact("2026-08-18", minutes = 15), fact("2026-09-30", minutes = 45), fact("2026-10-08", minutes = 20))

        val weeks = Statistics.minutesPerWeek(facts, today)

        assertEquals(8, weeks.size)
        assertEquals(LocalDate.parse("2026-08-17"), weeks.first().weekStart)
        assertEquals(LocalDate.parse("2026-10-05"), weeks.last().weekStart)
        assertEquals(listOf(45, 0, 0, 0, 0, 0, 45, 20), weeks.map { it.value })
    }

    @Test
    fun `sechs Monate über den Jahreswechsel`() {
        val facts = listOf(fact("2025-12-24", kcal = 300), fact("2026-02-01", kcal = 100), fact("2026-02-28", kcal = 50))

        val months = Statistics.kcalPerMonth(facts, LocalDate.parse("2026-03-10"))

        assertEquals((9..14).map { YearMonth.of(2025, 1).plusMonths(it.toLong()) }, months.map { it.month })
        assertEquals(listOf(0, 0, 300, 0, 150, 0), months.map { it.value })
    }

    @Test
    fun `Sportarten nach Anzahl, bei Gleichstand nach Code`() {
        val facts = listOf(fact("2026-10-01", sport = "WALKING"), fact("2026-10-02", sport = "CYCLING"), fact("2026-10-03", sport = "WALKING"), fact("2026-10-04", sport = "RUNNING"))

        assertEquals(
            listOf(SportCount("WALKING", 2), SportCount("CYCLING", 1), SportCount("RUNNING", 1)),
            Statistics.activitiesPerSport(facts),
        )
    }
}
