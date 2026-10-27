package de.touchgrass.ui.record

import de.touchgrass.data.TimerState
import de.touchgrass.data.dto.SportDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.FakeTimerStore
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.testSports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class TimerViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val now = Instant.parse("2026-10-05T08:15:30Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Berlin"))

    private val api = object : FakeSportApi() {
        override suspend fun sports(): List<SportDto> = testSports
    }

    @Test
    fun `Start ohne Sportart zeigt einen Hinweis`() {
        val store = FakeTimerStore()
        val viewModel = TimerViewModel(api, store, clock)

        viewModel.start()

        assertEquals("Bitte eine Sportart wählen.", viewModel.state.value.error)
        assertNull(store.current.value)
    }

    @Test
    fun `Start und Pause landen im TimerStore`() {
        val store = FakeTimerStore()
        val viewModel = TimerViewModel(api, store, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.start()
        assertEquals(TimerState("RUNNING", now), store.current.value)
        assertEquals(TimerState("RUNNING", now), viewModel.state.value.timer)

        viewModel.pause()
        assertEquals(now, store.current.value?.pausedSince)

        viewModel.resume()
        assertEquals(false, store.current.value?.isPaused)
    }

    @Test
    fun `gespeicherter Timer erscheint nach einem Neustart`() {
        val running = TimerState("CYCLING", now.minus(Duration.ofMinutes(10)))

        val viewModel = TimerViewModel(api, FakeTimerStore(running), clock)

        assertEquals(running, viewModel.state.value.timer)
        assertEquals("CYCLING", viewModel.state.value.sportCode)
        assertEquals(testSports, viewModel.state.value.sports)
    }

    @Test
    fun `Stopp pausiert den Timer und öffnet das Formular`() {
        val store = FakeTimerStore(TimerState("RUNNING", now.minus(Duration.ofMinutes(10))))
        val viewModel = TimerViewModel(api, store, clock)

        viewModel.stop()

        assertEquals(now, store.current.value?.pausedSince)
        assertTrue(viewModel.state.value.openForm)
        viewModel.onFormOpened()
        assertFalse(viewModel.state.value.openForm)
    }

    @Test
    fun `Sportarten lassen sich nach einem Fehler erneut laden`() {
        var calls = 0
        val failingOnce = object : FakeSportApi() {
            override suspend fun sports(): List<SportDto> = if (calls++ == 0) throw IOException("offline") else testSports
        }
        val viewModel = TimerViewModel(failingOnce, FakeTimerStore(), clock)
        assertEquals("Server nicht erreichbar", viewModel.state.value.sportsError)

        viewModel.loadSports()

        assertEquals(testSports, viewModel.state.value.sports)
        assertNull(viewModel.state.value.sportsError)
    }
}
