package de.touchgrass.ui.achievements

import de.touchgrass.data.dto.AchievementDto
import de.touchgrass.data.dto.GoalType
import de.touchgrass.data.dto.LeaderboardDto
import de.touchgrass.data.dto.LeaderboardEntryDto
import de.touchgrass.data.dto.LeaderboardMetric
import de.touchgrass.data.dto.LeaderboardPeriod
import de.touchgrass.data.dto.LeaderboardScope
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
        val leaderboardCalls = mutableListOf<Triple<LeaderboardPeriod, LeaderboardMetric, LeaderboardScope>>()
        override suspend fun progress(): ProgressDto = progress
        override suspend fun achievements(): List<AchievementDto> = listOf(testAchievement())
        override suspend fun leaderboard(period: LeaderboardPeriod, metric: LeaderboardMetric, scope: LeaderboardScope): LeaderboardDto {
            leaderboardCalls += Triple(period, metric, scope)
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
        assertEquals(listOf(Triple(LeaderboardPeriod.WEEK, LeaderboardMetric.POINTS, LeaderboardScope.ALL)), api.leaderboardCalls)
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
                Triple(LeaderboardPeriod.WEEK, LeaderboardMetric.POINTS, LeaderboardScope.ALL),
                Triple(LeaderboardPeriod.MONTH, LeaderboardMetric.POINTS, LeaderboardScope.ALL),
                Triple(LeaderboardPeriod.MONTH, LeaderboardMetric.MINUTES, LeaderboardScope.ALL),
            ),
            api.leaderboardCalls,
        )
        val board = (viewModel.state.value.leaderboard as UiState.Content).data
        assertEquals(LeaderboardPeriod.MONTH, board.period)
        assertEquals(LeaderboardMetric.MINUTES, board.metric)
    }

    @Test
    fun `Umschalten auf Freunde fragt nur die Freunde ab`() {
        val api = Api()
        val viewModel = AchievementsViewModel(api)
        viewModel.refresh()

        viewModel.onScopeChange(LeaderboardScope.FRIENDS)

        assertEquals(Triple(LeaderboardPeriod.WEEK, LeaderboardMetric.POINTS, LeaderboardScope.FRIENDS), api.leaderboardCalls.last())
        assertEquals(LeaderboardScope.FRIENDS, viewModel.state.value.scope)
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
