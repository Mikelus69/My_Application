package de.touchgrass.ui.record

import androidx.lifecycle.SavedStateHandle
import de.touchgrass.data.TimerState
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.CreateActivityResponse
import de.touchgrass.data.dto.PointsBreakdownDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.Visibility
import de.touchgrass.testing.FakePhotoPreparer
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.FakeTimerStore
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testAchievement
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.map.GeoPoint
import de.touchgrass.ui.photo.PhotoException
import de.touchgrass.ui.photo.UNSUPPORTED_PHOTO
import kotlinx.coroutines.CompletableDeferred
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
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

    @get:Rule
    val folder = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T08:15:30Z"), ZoneId.of("Europe/Berlin"))

    private val pausedTimer = TimerState(
        sportCode = "CYCLING",
        startedAt = Instant.parse("2026-10-05T07:30:20Z"),
        pausedTotal = Duration.ofMinutes(5),
        pausedSince = Instant.parse("2026-10-05T08:15:30Z"),
    )

    private val noPhoto = FakePhotoPreparer { error("prepare() wird in diesem Test nicht erwartet") }

    private open inner class Api : FakeSportApi() {
        var sent: CreateActivityRequest? = null
        val uploads = mutableListOf<Pair<Long, String>>()
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse {
            sent = body
            return CreateActivityResponse(testActivity(), PointsBreakdownDto(45, 10, 20, 0, 75))
        }
        override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto {
            uploads += id to Buffer().also { photo.writeTo(it) }.readUtf8()
            return testActivity(id = id).copy(photoVersion = 1)
        }
    }

    private fun photoFile(): File = folder.newFile().apply { writeText("jpeg") }

    @Test
    fun `Formular startet mit Sportarten und der aktuellen Zeit auf die Minute`() {
        val viewModel = RecordViewModel(Api(), FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)

        val state = viewModel.state.value
        assertEquals(testSports, state.sports)
        assertEquals(LocalDate.parse("2026-10-05"), state.date)
        assertEquals(LocalTime.parse("10:15"), state.time)
        assertFalse(state.fromTimer)
    }

    @Test
    fun `ohne Sportart oder mit ungültiger Dauer wird nichts gesendet`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)

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
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)

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
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)

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
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        assertEquals("Die Aktivität darf nicht in der Zukunft liegen.", viewModel.state.value.fieldErrors["startedAt"])
        assertEquals(false, viewModel.state.value.saving)
    }

    @Test
    fun `vom Timer kommen Sportart, Startzeit und Dauer ohne Pausen`() {
        val viewModel = RecordViewModel(Api(), FakeTimerStore(pausedTimer), fromTimer = true, noPhoto, SavedStateHandle(), clock)

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
        val viewModel = RecordViewModel(Api(), store, fromTimer = true, noPhoto, SavedStateHandle(), clock)

        viewModel.save()

        assertNull(store.current.value)
        assertFalse(viewModel.state.value.fromTimer)
    }

    @Test
    fun `Nachtragen ohne Timer lässt einen laufenden Timer in Ruhe`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = false, noPhoto, SavedStateHandle(), clock)

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
        val viewModel = RecordViewModel(api, store, fromTimer = true, noPhoto, SavedStateHandle(), clock)

        viewModel.save()

        assertEquals(pausedTimer, store.current.value)
        assertEquals("Unerwarteter Fehler.", viewModel.state.value.error)
    }

    @Test
    fun `Verwerfen setzt den Timer zurück`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = true, noPhoto, SavedStateHandle(), clock)

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
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)

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
        val viewModel = RecordViewModel(failingOnce, FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)
        assertEquals("Server nicht erreichbar", viewModel.state.value.sportsError)

        viewModel.loadSports()

        assertEquals(testSports, viewModel.state.value.sports)
        assertNull(viewModel.state.value.sportsError)
    }

    @Test
    fun `Sichtbarkeit Freunde wird mitgeschickt`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.onVisibilityChange(Visibility.FRIENDS)
        viewModel.save()

        assertEquals(Visibility.FRIENDS, api.sent?.visibility)
    }

    @Test
    fun `Foto wird nach dem Anlegen hochgeladen und danach gelöscht`() {
        val api = Api()
        val photo = photoFile()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, FakePhotoPreparer { photo }, SavedStateHandle(), clock)

        viewModel.onPhotoPicked("content://media/picker/0/42")
        val picked = viewModel.state.value.photoPath
        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        val state = viewModel.state.value
        assertEquals(photo.path, picked)
        assertEquals(listOf(42L to "jpeg"), api.uploads)
        assertFalse(photo.exists())
        assertEquals("Gespeichert! ~480 kcal · +75 Punkte (45 min · +10 · +20 neue Sportart)", state.savedMessage)
        assertNull(state.photoPath)
    }

    @Test
    fun `ohne Foto wird nichts hochgeladen`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        assertEquals(emptyList<Pair<Long, String>>(), api.uploads)
        assertEquals("Gespeichert! ~480 kcal · +75 Punkte (45 min · +10 · +20 neue Sportart)", viewModel.state.value.savedMessage)
    }

    @Test
    fun `scheitert das Hochladen, bleibt die Aktivität gespeichert und das Formular wird geleert`() {
        val store = FakeTimerStore(pausedTimer)
        val api = object : Api() {
            override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto =
                throw httpError(413, """{"error":"PAYLOAD_TOO_LARGE","message":"Das Foto ist zu groß.","field":"photo"}""")
        }
        val photo = photoFile()
        val viewModel = RecordViewModel(api, store, fromTimer = true, FakePhotoPreparer { photo }, SavedStateHandle(), clock)

        viewModel.onPhotoPicked("content://media/picker/0/42")
        viewModel.save()

        val state = viewModel.state.value
        assertEquals("CYCLING", api.sent?.sportCode)
        assertEquals(
            "Gespeichert, aber das Foto konnte nicht hochgeladen werden. Du kannst es in den Details erneut hinzufügen.",
            state.savedMessage,
        )
        assertNull(state.sportCode)
        assertNull(state.photoPath)
        assertTrue(state.fieldErrors.isEmpty())
        assertNull(store.current.value)
        assertFalse(photo.exists())
    }

    @Test
    fun `nicht lesbares Bild meldet das Formular am Foto`() {
        val viewModel = RecordViewModel(
            Api(),
            FakeTimerStore(),
            fromTimer = false,
            FakePhotoPreparer { throw PhotoException(UNSUPPORTED_PHOTO) },
            SavedStateHandle(),
            clock,
        )

        viewModel.onPhotoPicked("content://media/picker/0/42")

        val state = viewModel.state.value
        assertEquals("Dieses Bildformat wird nicht unterstützt.", state.fieldErrors["photo"])
        assertNull(state.photoPath)
        assertFalse(state.photoBusy)
    }

    @Test
    fun `Entfernen leert das Foto und löscht die Datei`() {
        val photo = photoFile()
        val viewModel = RecordViewModel(Api(), FakeTimerStore(), fromTimer = false, FakePhotoPreparer { photo }, SavedStateHandle(), clock)
        viewModel.onPhotoPicked("content://media/picker/0/42")

        viewModel.removePhoto()

        assertNull(viewModel.state.value.photoPath)
        assertFalse(photo.exists())
    }

    @Test
    fun `nach dem Beenden der App stellt das SavedStateHandle Formular und Foto wieder her`() {
        val handleWithRouteArgs = SavedStateHandle(mapOf("fromTimer" to true))
        val photo = photoFile()
        val before = RecordViewModel(
            Api(),
            FakeTimerStore(pausedTimer),
            fromTimer = true,
            FakePhotoPreparer { photo },
            handleWithRouteArgs,
            clock,
        )
        before.onDurationChange("35")
        before.onLocationChange(GeoPoint(48.1642, 11.6056))
        before.onDescriptionChange("Runde an der Isar")
        before.onVisibilityChange(Visibility.FRIENDS)
        before.onPhotoPicked("content://media/picker/0/42")

        val after = RecordViewModel(Api(), FakeTimerStore(pausedTimer), fromTimer = true, noPhoto, handleWithRouteArgs, clock)

        assertEquals("CYCLING", before.state.value.sportCode)
        assertEquals(before.state.value, after.state.value)
        assertEquals("35", after.state.value.duration)
        assertEquals(photo.path, after.state.value.photoPath)
    }

    @Test
    fun `während des Speicherns sichert das Handle kein Formular`() {
        val handle = SavedStateHandle()
        val created = CompletableDeferred<CreateActivityResponse>()
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse = created.await()
        }
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, handle, clock)
        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")

        viewModel.save()
        val dateWhileSaving = handle.get<String>("date")
        created.complete(CreateActivityResponse(testActivity(), PointsBreakdownDto(45, 10, 20, 0, 75)))

        assertNull(dateWhileSaving)
        assertEquals("2026-10-05", handle.get<String>("date"))
        assertNull(handle.get<String>("sportCode"))
    }

    @Test
    fun `scheitert schon das Anlegen, bleibt das Foto im Formular`() {
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse =
                throw httpError(500, """{"error":"INTERNAL_ERROR","message":"Unerwarteter Fehler."}""")
        }
        val photo = photoFile()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, FakePhotoPreparer { photo }, SavedStateHandle(), clock)

        viewModel.onPhotoPicked("content://media/picker/0/42")
        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        val state = viewModel.state.value
        assertEquals("Unerwarteter Fehler.", state.error)
        assertEquals(photo.path, state.photoPath)
        assertTrue(photo.exists())
        assertEquals(emptyList<Pair<Long, String>>(), api.uploads)
    }

    @Test
    fun `während das Foto vorbereitet wird, schickt Speichern nichts`() {
        val api = Api()
        val photo = photoFile()
        val prepared = CompletableDeferred<File>()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, FakePhotoPreparer { prepared.await() }, SavedStateHandle(), clock)
        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")

        viewModel.onPhotoPicked("content://media/picker/0/42")
        val busy = viewModel.state.value.photoBusy
        viewModel.save()
        val sentWhileBusy = api.sent
        prepared.complete(photo)

        val state = viewModel.state.value
        assertTrue(busy)
        assertNull(sentWhileBusy)
        assertEquals(photo.path, state.photoPath)
        assertFalse(state.photoBusy)
    }

    @Test
    fun `fehlende Kamera-App erscheint am Foto`() {
        val viewModel = RecordViewModel(Api(), FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)

        viewModel.onPhotoError("Keine Kamera-App gefunden.")

        assertEquals("Keine Kamera-App gefunden.", viewModel.state.value.fieldErrors["photo"])
    }
}
