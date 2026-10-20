package de.touchgrass.ui.status

import de.touchgrass.data.dto.HealthDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ServerStatusViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `erreichbarer Server ergibt Content`() {
        val api = object : FakeSportApi() {
            override suspend fun health() = HealthDto("ok", "ok")
        }

        val viewModel = ServerStatusViewModel(api)

        assertEquals(UiState.Content(HealthDto("ok", "ok")), viewModel.state.value)
    }

    @Test
    fun `Netzwerkfehler ergibt Error`() {
        val api = object : FakeSportApi() {
            override suspend fun health(): HealthDto = throw IOException("offline")
        }

        val viewModel = ServerStatusViewModel(api)

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value)
    }
}
