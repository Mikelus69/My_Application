package de.touchgrass.ui.achievements

import de.touchgrass.data.dto.AchievementDto
import de.touchgrass.data.dto.GoalType
import de.touchgrass.data.dto.LeaderboardDto
import de.touchgrass.data.dto.LeaderboardEntryDto
import de.touchgrass.data.dto.LeaderboardMetric
import de.touchgrass.data.dto.LeaderboardPeriod
import de.touchgrass.data.dto.ProgressDto
import de.touchgrass.data.dto.StreakDto
import de.touchgrass.data.dto.WeeklyGoalDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.testAchievement
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class AchievementsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val progress = ProgressDto(WeeklyGoalDto(GoalType.MINUTES, 150, 96), StreakDto(3, 7), List(7) { it < 3 })

    private open inner class Api : FakeSportApi() {
        val leaderboardCalls = mutableListOf<Pair<LeaderboardPeriod, LeaderboardMetric>>()
        override suspend fun progress(): ProgressDto = progress
        override suspend fun achievements(): List<AchievementDto> = listOf(testAchievement())
        override suspend fun leaderboard(period: LeaderboardPeriod, metric: LeaderboardMetric): LeaderboardDto {
            leaderboardCalls += period to metric
            return LeaderboardDto(period, metric, listOf(LeaderboardEntryDto(1, "max_m", 184)))
        }
    }

    @Test
    fun `lädt Fortschritt, Achievements und Leaderboard`() {
        val api = Api()
        val viewModel = AchievementsViewModel(api)

        viewModel.refresh()

        val state = viewModel.state.value
        assertEquals(UiState.Content(progress), state.progress)
        assertEquals(UiState.Content(listOf(testAchievement())), state.achievements)
        assertEquals(listOf(LeaderboardPeriod.WEEK to LeaderboardMetric.POINTS), api.leaderboardCalls)
        assertEquals(184, (state.leaderboard as UiState.Content).data.entries.single().value)
    }

    @Test
    fun `Umschalten lädt das Leaderboard mit Zeitraum und Wertung neu`() {
        val api = Api()
        val viewModel = AchievementsViewModel(api)
        viewModel.refresh()

        viewModel.onPeriodChange(LeaderboardPeriod.MONTH)
        viewModel.onMetricChange(LeaderboardMetric.MINUTES)

        assertEquals(
            listOf(
                LeaderboardPeriod.WEEK to LeaderboardMetric.POINTS,
                LeaderboardPeriod.MONTH to LeaderboardMetric.POINTS,
                LeaderboardPeriod.MONTH to LeaderboardMetric.MINUTES,
            ),
            api.leaderboardCalls,
        )
        val board = (viewModel.state.value.leaderboard as UiState.Content).data
        assertEquals(LeaderboardPeriod.MONTH, board.period)
        assertEquals(LeaderboardMetric.MINUTES, board.metric)
    }

    @Test
    fun `ein Fehler trifft nur seinen Bereich`() {
        val api = object : Api() {
            override suspend fun achievements(): List<AchievementDto> = throw IOException("kein Netz")
        }
        val viewModel = AchievementsViewModel(api)

        viewModel.refresh()

        val state = viewModel.state.value
        assertEquals(UiState.Error("Server nicht erreichbar"), state.achievements)
        assertEquals(UiState.Content(progress), state.progress)
    }
}
