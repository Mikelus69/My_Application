package de.touchgrass.ui.record

import de.touchgrass.data.TimerState
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.CreateActivityResponse
import de.touchgrass.data.dto.PointsBreakdownDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.Visibility
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.FakeTimerStore
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testAchievement
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.map.GeoPoint
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecordViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T08:15:30Z"), ZoneId.of("Europe/Berlin"))

    private val pausedTimer = TimerState(
        sportCode = "CYCLING",
        startedAt = Instant.parse("2026-10-05T07:30:20Z"),
        pausedTotal = Duration.ofMinutes(5),
        pausedSince = Instant.parse("2026-10-05T08:15:30Z"),
    )

    private open inner class Api : FakeSportApi() {
        var sent: CreateActivityRequest? = null
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse {
            sent = body
            return CreateActivityResponse(testActivity(), PointsBreakdownDto(45, 10, 20, 0, 75))
        }
    }

    @Test
    fun `Formular startet mit Sportarten und der aktuellen Zeit auf die Minute`() {
        val viewModel = RecordViewModel(Api(), FakeTimerStore(), fromTimer = false, clock)

        val state = viewModel.state.value
        assertEquals(testSports, state.sports)
        assertEquals(LocalDate.parse("2026-10-05"), state.date)
        assertEquals(LocalTime.parse("10:15"), state.time)
        assertFalse(state.fromTimer)
    }

    @Test
    fun `ohne Sportart oder mit ungültiger Dauer wird nichts gesendet`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, clock)

        viewModel.onDurationChange("45")
        viewModel.save()
        assertEquals("Bitte eine Sportart wählen.", viewModel.state.value.fieldErrors["sportCode"])

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("dreiviertel Stunde")
        viewModel.save()
        assertEquals("Bitte die Dauer in ganzen Minuten angeben.", viewModel.state.value.fieldErrors["durationMin"])
        assertNull(api.sent)
    }

    @Test
    fun `Speichern schickt die Aktivität mit Berliner Zeit und leert das Formular`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDateChange(LocalDate.parse("2026-10-04"))
        viewModel.onTimeChange(LocalTime.parse("09:30"))
        viewModel.onDurationChange(" 45 ")
        viewModel.onDescriptionChange("  ")
        viewModel.onVisibilityChange(Visibility.PUBLIC)
        viewModel.save()

        assertEquals(
            CreateActivityRequest("RUNNING", "2026-10-04T09:30:00+02:00", 45, visibility = Visibility.PUBLIC),
            api.sent,
        )
        val state = viewModel.state.value
        assertEquals("Gespeichert! ~480 kcal · +75 Punkte (45 min · +10 · +20 neue Sportart)", state.savedMessage)
        assertNull(state.sportCode)
        assertEquals("", state.duration)
        assertEquals(testSports, state.sports)
    }

    @Test
    fun `gewählter Ort wird mitgeschickt und lässt sich wieder entfernen`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.onLocationChange(GeoPoint(48.1642, 11.6056))
        viewModel.save()
        assertEquals(48.1642, api.sent?.latitude)
        assertEquals(11.6056, api.sent?.longitude)
        assertNull(viewModel.state.value.location)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.onLocationChange(GeoPoint(48.1642, 11.6056))
        viewModel.onLocationChange(null)
        viewModel.save()
        assertNull(api.sent?.latitude)
        assertNull(api.sent?.longitude)
    }

    @Test
    fun `Feldfehler vom Server erscheint am Feld`() {
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse = throw httpError(
                400,
                """{"error":"VALIDATION_ERROR","message":"Die Aktivität darf nicht in der Zukunft liegen.","field":"startedAt"}""",
            )
        }
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        assertEquals("Die Aktivität darf nicht in der Zukunft liegen.", viewModel.state.value.fieldErrors["startedAt"])
        assertEquals(false, viewModel.state.value.saving)
    }

    @Test
    fun `vom Timer kommen Sportart, Startzeit und Dauer ohne Pausen`() {
        val viewModel = RecordViewModel(Api(), FakeTimerStore(pausedTimer), fromTimer = true, clock)

        val state = viewModel.state.value
        assertEquals("CYCLING", state.sportCode)
        assertEquals(LocalDate.parse("2026-10-05"), state.date)
        assertEquals(LocalTime.parse("09:30"), state.time)
        assertEquals("40", state.duration)
        assertTrue(state.fromTimer)
    }

    @Test
    fun `nach dem Speichern aus dem Timer ist der Timer zurückgesetzt`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = true, clock)

        viewModel.save()

        assertNull(store.current.value)
        assertFalse(viewModel.state.value.fromTimer)
    }

    @Test
    fun `Nachtragen ohne Timer lässt einen laufenden Timer in Ruhe`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = false, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        assertEquals(pausedTimer, store.current.value)
    }

    @Test
    fun `fehlgeschlagenes Speichern behält den Timer`() {
        val store = FakeTimerStore(pausedTimer)
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse =
                throw httpError(500, """{"error":"INTERNAL_ERROR","message":"Unerwarteter Fehler."}""")
        }
        val viewModel = RecordViewModel(api, store, fromTimer = true, clock)

        viewModel.save()

        assertEquals(pausedTimer, store.current.value)
        assertEquals("Unerwarteter Fehler.", viewModel.state.value.error)
    }

    @Test
    fun `Verwerfen setzt den Timer zurück`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = true, clock)

        viewModel.discard()

        assertNull(store.current.value)
        assertTrue(viewModel.state.value.discarded)
    }

    @Test
    fun `neue Achievements erscheinen nach dem Speichern, bis sie gesehen wurden`() {
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse =
                CreateActivityResponse(testActivity(), PointsBreakdownDto(45, 10, 20, 0, 75), listOf(testAchievement()))
        }
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()
        assertEquals(listOf("FIRST_ACTIVITY"), viewModel.state.value.newAchievements.map { it.code })

        viewModel.onAchievementsSeen()
        assertEquals(emptyList<String>(), viewModel.state.value.newAchievements.map { it.code })
    }

    @Test
    fun `Sportarten lassen sich nach einem Fehler erneut laden`() {
        var calls = 0
        val failingOnce = object : FakeSportApi() {
            override suspend fun sports(): List<SportDto> = if (calls++ == 0) throw IOException("offline") else testSports
        }
        val viewModel = RecordViewModel(failingOnce, FakeTimerStore(), fromTimer = false, clock)
        assertEquals("Server nicht erreichbar", viewModel.state.value.sportsError)

        viewModel.loadSports()

        assertEquals(testSports, viewModel.state.value.sports)
        assertNull(viewModel.state.value.sportsError)
    }
}
