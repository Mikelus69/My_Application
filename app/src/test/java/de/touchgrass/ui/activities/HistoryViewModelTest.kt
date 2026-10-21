package de.touchgrass.ui.activities

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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class HistoryViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneId.of("Europe/Berlin"))

    private inner class Api : FakeSportApi() {
        val queries = mutableListOf<Pair<String?, String?>>()
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun activities(sport: String?, from: String?, to: String?): List<ActivityDto> {
            queries += sport to from
            return listOf(testActivity())
        }
    }

    @Test
    fun `refresh lädt Sportarten und Aktivitäten ohne Filter`() {
        val api = Api()
        val viewModel = HistoryViewModel(api, clock)

        viewModel.refresh()

        assertEquals(testSports, viewModel.state.value.sports)
        assertEquals(UiState.Content(listOf(testActivity())), viewModel.state.value.activities)
        assertEquals(listOf<Pair<String?, String?>>(null to null), api.queries)
    }

    @Test
    fun `Filter werden als Sportart und Startdatum geschickt`() {
        val api = Api()
        val viewModel = HistoryViewModel(api, clock)

        viewModel.onSportFilterChange("RUNNING")
        viewModel.onPeriodChange(Period.WEEK)

        assertEquals(listOf<Pair<String?, String?>>("RUNNING" to null, "RUNNING" to "2026-10-15"), api.queries)
    }

    @Test
    fun `ohne Server erscheint eine Fehlermeldung`() {
        val viewModel = HistoryViewModel(object : FakeSportApi() {
            override suspend fun sports(): List<SportDto> = throw IOException("offline")
        }, clock)

        viewModel.refresh()

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value.activities)
    }
}
