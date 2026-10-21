package de.touchgrass.logic

import de.touchgrass.users.GoalType
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaloriesTest {
    @Test
    fun `kcal ist MET mal Gewicht mal Stunden, gerundet`() {
        assertEquals(480, Calories.estimate(met = 8.0, weightKg = 80.0, durationMin = 45))
        assertEquals(169, Calories.estimate(met = 3.5, weightKg = 78.5, durationMin = 37))
    }

    @Test
    fun `ohne Gewicht wird mit 70 kg gerechnet`() {
        assertEquals(280, Calories.estimate(met = 8.0, weightKg = null, durationMin = 30))
    }
}

class PointsTest {
    @Test
    fun `Minuten plus 10 für die Aktivität`() {
        assertEquals(PointsBreakdown(48, 10, 0, 0, 58), Points.calculate(48, firstOfSport = false, weeklyGoalReached = false))
    }

    @Test
    fun `neue Sportart und Wochenziel geben Bonuspunkte`() {
        assertEquals(PointsBreakdown(48, 10, 20, 50, 128), Points.calculate(48, firstOfSport = true, weeklyGoalReached = true))
    }
}

class WeeklyGoalTest {
    private val before = WeekTotals(minutes = 120, activities = 2, kcal = 900)

    @Test
    fun `Bonus nur, wenn das Ziel genau mit dieser Aktivität erreicht wird`() {
        assertTrue(WeeklyGoal.bonusEarned(GoalType.MINUTES, 150, before, before.plusActivity(30, 200), bonusAlreadyGiven = false))
        assertFalse(WeeklyGoal.bonusEarned(GoalType.MINUTES, 150, before, before.plusActivity(20, 200), bonusAlreadyGiven = false))
        assertFalse(WeeklyGoal.bonusEarned(GoalType.MINUTES, 100, before, before.plusActivity(30, 200), bonusAlreadyGiven = false))
    }

    @Test
    fun `höchstens ein Bonus pro Woche`() {
        assertFalse(WeeklyGoal.bonusEarned(GoalType.MINUTES, 150, before, before.plusActivity(30, 200), bonusAlreadyGiven = true))
    }

    @Test
    fun `Zieltyp bestimmt den gezählten Wert`() {
        assertTrue(WeeklyGoal.bonusEarned(GoalType.ACTIVITIES, 3, before, before.plusActivity(5, 40), bonusAlreadyGiven = false))
        assertTrue(WeeklyGoal.bonusEarned(GoalType.KCAL, 1000, before, before.plusActivity(15, 100), bonusAlreadyGiven = false))
    }
}

class BerlinTimeTest {
    @Test
    fun `Woche geht von Montag 0 Uhr bis Montag 0 Uhr Berliner Zeit`() {
        val week = BerlinTime.weekOf(OffsetDateTime.parse("2026-10-08T23:30:00+02:00"))

        assertEquals(OffsetDateTime.parse("2026-10-05T00:00:00+02:00"), week.from)
        assertEquals(OffsetDateTime.parse("2026-10-12T00:00:00+02:00"), week.until)
    }

    @Test
    fun `Sonntagabend in UTC gehört schon zur nächsten Berliner Woche`() {
        val week = BerlinTime.weekOf(OffsetDateTime.parse("2026-10-11T22:30:00Z"))

        assertEquals(OffsetDateTime.parse("2026-10-12T00:00:00+02:00"), week.from)
    }

    @Test
    fun `Woche mit Zeitumstellung endet in Winterzeit`() {
        val week = BerlinTime.weekOf(OffsetDateTime.parse("2026-10-21T12:00:00+02:00"))

        assertEquals(OffsetDateTime.parse("2026-10-19T00:00:00+02:00"), week.from)
        assertEquals(OffsetDateTime.parse("2026-10-26T00:00:00+01:00"), week.until)
    }

    @Test
    fun `Zeitpunkte werden in Berliner Zeit mit Sekunden ausgegeben`() {
        assertEquals("2026-10-05T09:30:00+02:00", BerlinTime.format(OffsetDateTime.parse("2026-10-05T07:30Z")))
        assertEquals(OffsetDateTime.parse("2026-10-05T00:00:00+02:00"), BerlinTime.startOfDay(LocalDate.parse("2026-10-05")))
    }
}
