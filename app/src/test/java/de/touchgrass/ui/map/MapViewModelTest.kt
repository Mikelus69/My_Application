package de.touchgrass.ui.map

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class MapViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `lädt Aktivitäten für die Karte und die Sportarten`() {
        val activities = listOf(testActivity(id = 1).copy(latitude = 48.1642, longitude = 11.6056))
        val api = object : FakeSportApi() {
            override suspend fun mapActivities(): List<ActivityDto> = activities
            override suspend fun sports(): List<SportDto> = testSports
        }
        val viewModel = MapViewModel(api)
        assertEquals(UiState.Loading, viewModel.state.value)

        viewModel.refresh()

        assertEquals(UiState.Content(MapContent(activities, testSports)), viewModel.state.value)
    }

    @Test
    fun `nicht erreichbarer Server wird angezeigt`() {
        val api = object : FakeSportApi() {
            override suspend fun mapActivities(): List<ActivityDto> = throw IOException("kein Netz")
        }
        val viewModel = MapViewModel(api)

        viewModel.refresh()

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value)
    }
}
