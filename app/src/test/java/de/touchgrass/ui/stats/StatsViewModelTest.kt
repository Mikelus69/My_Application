package de.touchgrass.ui.stats

import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.StatsDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.testSports
import de.touchgrass.testing.testStats
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class StatsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `lädt Statistik und Sportarten`() {
        val viewModel = StatsViewModel(object : FakeSportApi() {
            override suspend fun stats(): StatsDto = testStats
            override suspend fun sports(): List<SportDto> = testSports
        })

        viewModel.refresh()

        assertEquals(UiState.Content(StatsContent(testStats, testSports)), viewModel.state.value)
    }

    @Test
    fun `nicht erreichbarer Server wird angezeigt`() {
        val viewModel = StatsViewModel(object : FakeSportApi() {
            override suspend fun stats(): StatsDto = throw IOException("offline")
        })

        viewModel.refresh()

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value)
    }
}
