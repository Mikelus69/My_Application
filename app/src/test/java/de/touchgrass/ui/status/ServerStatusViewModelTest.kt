package de.touchgrass.ui.status

import de.touchgrass.data.SportApi
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ServerStatusViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `erreichbarer Server ergibt Content`() {
        val viewModel = ServerStatusViewModel(FakeApi { HealthDto("ok", "ok") })

        assertEquals(UiState.Content(HealthDto("ok", "ok")), viewModel.state.value)
    }

    @Test
    fun `Netzwerkfehler ergibt Error`() {
        val viewModel = ServerStatusViewModel(FakeApi { throw IOException("offline") })

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value)
    }
}

private class FakeApi(private val answer: () -> HealthDto) : SportApi {
    override suspend fun health(): HealthDto = answer()
}
