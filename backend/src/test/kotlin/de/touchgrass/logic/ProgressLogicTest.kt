package de.touchgrass.logic

import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreakTest {
    private fun days(vararg values: String) = values.map(LocalDate::parse).toSet()

    @Test
    fun `aktuelle Serie zählt bis heute`() {
        assertEquals(3, Streak.current(days("2026-10-06", "2026-10-07", "2026-10-08"), LocalDate.parse("2026-10-08")))
    }

    @Test
    fun `ohne Aktivität heute zählt die Serie ab gestern`() {
        assertEquals(2, Streak.current(days("2026-10-06", "2026-10-07"), LocalDate.parse("2026-10-08")))
    }

    @Test
    fun `eine Lücke von einem Tag beendet die Serie`() {
        assertEquals(0, Streak.current(days("2026-10-05", "2026-10-06"), LocalDate.parse("2026-10-08")))
        assertEquals(0, Streak.current(emptySet(), LocalDate.parse("2026-10-08")))
    }

    @Test
    fun `längste Serie ist die längste Folge überhaupt`() {
        val active = days("2026-09-01", "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-07", "2026-10-08")

        assertEquals(4, Streak.longest(active))
        assertEquals(0, Streak.longest(emptySet()))
    }

    @Test
    fun `Wochentage stehen von Montag bis Sonntag`() {
        val active = days("2026-10-04", "2026-10-06", "2026-10-07", "2026-10-12")

        assertEquals(listOf(false, true, true, false, false, false, false), Streak.weekDays(active, LocalDate.parse("2026-10-05")))
    }
}

class AchievementRulesTest {
    private val stats = UserStats(
        activities = 12,
        minutes = 480,
        kcal = 3100,
        activitiesPerSport = mapOf("RUNNING" to 7, "CYCLING" to 5),
        longestStreak = 4,
    )

    @Test
    fun `jede Regel liest ihren Wert aus den Statistiken`() {
        assertEquals(12, stats.valueFor(Requirement.TOTAL_ACTIVITIES, null))
        assertEquals(480, stats.valueFor(Requirement.TOTAL_MINUTES, null))
        assertEquals(3100, stats.valueFor(Requirement.TOTAL_KCAL, null))
        assertEquals(7, stats.valueFor(Requirement.SPORT_ACTIVITIES, "RUNNING"))
        assertEquals(0, stats.valueFor(Requirement.SPORT_ACTIVITIES, "SWIMMING"))
        assertEquals(4, stats.valueFor(Requirement.STREAK_DAYS, null))
    }

    @Test
    fun `erreicht ab dem Schwellenwert`() {
        assertTrue(AchievementRules.isReached(500, 500))
        assertFalse(AchievementRules.isReached(499, 500))
    }

    @Test
    fun `Fortschritt geht höchstens bis zum Schwellenwert, freigeschaltete sind voll`() {
        assertEquals(7, AchievementRules.progress(7, 10, unlocked = false))
        assertEquals(10, AchievementRules.progress(12, 10, unlocked = false))
        assertEquals(10, AchievementRules.progress(3, 10, unlocked = true))
    }
}

class LeaderboardTest {
    @Test
    fun `gleicher Wert gleicher Rang, danach geht es mit der Platzzahl weiter`() {
        val ranked = Leaderboard.rank(
            listOf(
                Score(1, "tom", 162),
                Score(2, "ali", 148),
                Score(3, "max_m", 184),
                Score(4, "Lisa", 162),
            )
        )

        assertEquals(listOf("max_m", "Lisa", "tom", "ali"), ranked.map { it.score.username })
        assertEquals(listOf(1, 2, 2, 4), ranked.map { it.rank })
    }

    @Test
    fun `leere Liste bleibt leer`() {
        assertEquals(emptyList(), Leaderboard.rank(emptyList()))
    }
}

class BerlinMonthTest {
    @Test
    fun `Monat geht vom Ersten 0 Uhr bis zum nächsten Ersten Berliner Zeit`() {
        val month = BerlinTime.monthOf(OffsetDateTime.parse("2026-10-31T23:30:00Z"))

        assertEquals(OffsetDateTime.parse("2026-11-01T00:00:00+01:00"), month.from)
        assertEquals(OffsetDateTime.parse("2026-12-01T00:00:00+01:00"), month.until)
    }

    @Test
    fun `Datum zählt in Berliner Zeit`() {
        assertEquals(LocalDate.parse("2026-10-09"), BerlinTime.date(OffsetDateTime.parse("2026-10-08T22:30:00Z")))
    }
}
