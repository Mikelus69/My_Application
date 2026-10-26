package de.touchgrass.ui.achievements

import de.touchgrass.data.dto.GoalType
import de.touchgrass.data.dto.LeaderboardDto
import de.touchgrass.data.dto.LeaderboardEntryDto
import de.touchgrass.data.dto.LeaderboardMetric
import de.touchgrass.data.dto.LeaderboardPeriod
import de.touchgrass.data.dto.WeeklyGoalDto
import de.touchgrass.testing.testAchievement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AchievementsFormatTest {
    @Test
    fun `Wochenziel als Anteil und Text, erreicht mit Jubel`() {
        assertEquals(0.64f, WeeklyGoalDto(GoalType.MINUTES, 150, 96).fraction(), 0.001f)
        assertEquals(1f, WeeklyGoalDto(GoalType.MINUTES, 150, 200).fraction())
        assertEquals("96 / 150 Minuten", WeeklyGoalDto(GoalType.MINUTES, 150, 96).label())
        assertEquals("4 / 4 Aktivitäten – geschafft! 🎉", WeeklyGoalDto(GoalType.ACTIVITIES, 4, 4).label())
    }

    @Test
    fun `Tage in Einzahl und Mehrzahl`() {
        assertEquals("1 Tag", daysLabel(1))
        assertEquals("0 Tage", daysLabel(0))
        assertEquals("7 Tage", daysLabel(7))
    }

    @Test
    fun `Achievement zeigt Datum oder Fortschritt`() {
        assertEquals("Freigeschaltet am 05.10.2026", testAchievement().progressLabel())
        assertEquals("7 / 10", testAchievement(code = "RUNNER_10", progress = 7, threshold = 10, unlockedAt = null).progressLabel())
    }

    @Test
    fun `Leaderboard-Wert mit Einheit`() {
        val entry = LeaderboardEntryDto(1, "max_m", 184)

        assertEquals("184 Punkte", entry.valueLabel(LeaderboardMetric.POINTS))
        assertEquals("184 min", entry.valueLabel(LeaderboardMetric.MINUTES))
    }

    @Test
    fun `eigener Platz wird nur unten angehängt, wenn er nicht in der Liste steht`() {
        val top = listOf(LeaderboardEntryDto(1, "max_m", 184), LeaderboardEntryDto(2, "lisa", 162))
        val inList = LeaderboardDto(LeaderboardPeriod.WEEK, LeaderboardMetric.POINTS, top, me = top[1])
        val below = inList.copy(me = LeaderboardEntryDto(57, "tom", 3))

        assertNull(inList.ownEntryBelowList())
        assertEquals(LeaderboardEntryDto(57, "tom", 3), below.ownEntryBelowList())
        assertNull(inList.copy(me = null).ownEntryBelowList())
    }
}
