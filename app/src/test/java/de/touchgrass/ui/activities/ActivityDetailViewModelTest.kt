package de.touchgrass.ui.activities

import de.touchgrass.data.PendingPhotos
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.testing.FakePhotoPreparer
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.UiState
import de.touchgrass.ui.photo.PhotoException
import de.touchgrass.ui.photo.UNSUPPORTED_PHOTO
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ActivityDetailViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val noPhoto = FakePhotoPreparer { error("prepare() wird in diesem Test nicht erwartet") }

    private val pending by lazy { PendingPhotos(File(folder.root, "pending")) }

    private open inner class Api(private val photoVersion: Long? = null) : FakeSportApi() {
        var deletedId: Long? = null
        val uploads = mutableListOf<Pair<Long, String>>()
        val deletedPhotos = mutableListOf<Long>()
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun activity(id: Long): ActivityDto = testActivity(id = id).copy(photoVersion = photoVersion)
        override suspend fun deleteActivity(id: Long) {
            deletedId = id
        }
        override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto {
            uploads += id to Buffer().also { photo.writeTo(it) }.readUtf8()
            return testActivity(id = id).copy(photoVersion = 8)
        }
        override suspend fun deletePhoto(id: Long) {
            deletedPhotos += id
        }
    }

    private fun photoFile(): File = folder.newFile().apply { writeText("jpeg") }

    private fun waitingPhoto(activityId: Long): File =
        pending.file(activityId).apply {
            parentFile?.mkdirs()
            writeText("wartet")
        }

    @Test
    fun `lädt die Aktivität mit der übergebenen ID`() {
        val viewModel = ActivityDetailViewModel(Api(), id = 7, noPhoto, pending)

        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
    }

    @Test
    fun `Löschen ruft die API auf und meldet deleted`() {
        val api = Api()
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto, pending)

        viewModel.delete()

        assertEquals(7L, api.deletedId)
        assertTrue(viewModel.state.value.deleted)
    }

    @Test
    fun `Fehler beim Löschen wird angezeigt`() {
        val api = object : Api() {
            override suspend fun deleteActivity(id: Long): Unit =
                throw httpError(403, """{"error":"FORBIDDEN","message":"Das ist nicht deine Aktivität."}""")
        }
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto, pending)

        viewModel.delete()

        assertFalse(viewModel.state.value.deleted)
        assertEquals("Das ist nicht deine Aktivität.", viewModel.state.value.error)
    }

    @Test
    fun `nach einem Ladefehler lädt Erneut versuchen die Aktivität`() {
        var calls = 0
        val failingOnce = object : Api() {
            override suspend fun activity(id: Long): ActivityDto = if (calls++ == 0) throw IOException("offline") else testActivity(id = id)
        }
        val viewModel = ActivityDetailViewModel(failingOnce, id = 7, noPhoto, pending)
        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value.activity)

        viewModel.load()

        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
    }

    @Test
    fun `neues Foto wird hochgeladen und die Antwort ersetzt die Aktivität`() {
        val api = Api(photoVersion = 5)
        val photo = photoFile()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { photo }, pending)

        viewModel.onPhotoPicked("content://media/picker/0/42")

        val state = viewModel.state.value
        assertEquals(listOf(7L to "jpeg"), api.uploads)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 8)), state.activity)
        assertFalse(state.photoBusy)
        assertFalse(state.photoPending)
        assertFalse(photo.exists())
        assertFalse(pending.has(7))
    }

    @Test
    fun `Foto entfernen ruft deletePhoto auf und setzt photoVersion auf null`() {
        val api = Api(photoVersion = 5)
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto, pending)

        viewModel.removePhoto()

        assertEquals(listOf(7L), api.deletedPhotos)
        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
        assertFalse(viewModel.state.value.photoBusy)
    }

    @Test
    fun `Fehler beim Hochladen landen in error, das bisherige Foto bleibt`() {
        val api = object : Api(photoVersion = 5) {
            override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto =
                throw httpError(413, """{"error":"PAYLOAD_TOO_LARGE","message":"Das Foto ist zu groß.","field":"photo"}""")
        }
        val photo = photoFile()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { photo }, pending)

        viewModel.onPhotoPicked("content://media/picker/0/42")

        val state = viewModel.state.value
        assertEquals("Das Foto ist zu groß.", state.error)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 5)), state.activity)
        assertFalse(state.photoBusy)
        assertFalse(state.photoPending)
        assertFalse(photo.exists())
        assertFalse(pending.has(7))
    }

    @Test
    fun `nicht lesbares Bild meldet das Bildformat`() {
        val api = Api()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { throw PhotoException(UNSUPPORTED_PHOTO) }, pending)

        viewModel.onPhotoPicked("content://media/picker/0/42")

        assertEquals("Dieses Bildformat wird nicht unterstützt.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.photoBusy)
        assertEquals(emptyList<Pair<Long, String>>(), api.uploads)
    }

    @Test
    fun `scheitert das Entfernen, bleibt das Foto`() {
        val api = object : Api(photoVersion = 5) {
            override suspend fun deletePhoto(id: Long): Unit =
                throw httpError(500, """{"error":"INTERNAL_ERROR","message":"Unerwarteter Fehler."}""")
        }
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto, pending)

        viewModel.removePhoto()

        val state = viewModel.state.value
        assertEquals("Unerwarteter Fehler.", state.error)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 5)), state.activity)
        assertFalse(state.photoBusy)
    }

    @Test
    fun `fehlende Kamera-App erscheint als Fehler`() {
        val viewModel = ActivityDetailViewModel(Api(), id = 7, noPhoto, pending)

        viewModel.onPhotoError("Keine Kamera-App gefunden.")

        assertEquals("Keine Kamera-App gefunden.", viewModel.state.value.error)
    }

    @Test
    fun `scheitert das Hochladen am Netz, wartet das Foto auf einen neuen Versuch`() {
        val api = object : Api(photoVersion = 5) {
            override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto = throw IOException("Verbindung abgebrochen")
        }
        val photo = photoFile()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { photo }, pending)

        viewModel.onPhotoPicked("content://media/picker/0/42")

        val state = viewModel.state.value
        assertEquals("Server nicht erreichbar", state.error)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 5)), state.activity)
        assertFalse(state.photoBusy)
        assertTrue(state.photoPending)
        assertEquals("jpeg", pending.file(7).readText())
    }

    @Test
    fun `ein wartendes Foto wird angeboten und lässt sich erneut hochladen`() {
        val api = Api(photoVersion = 5)
        waitingPhoto(7)
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto, pending)
        val offered = viewModel.state.value.photoPending
        val offeredElsewhere = ActivityDetailViewModel(api, id = 8, noPhoto, pending).state.value.photoPending

        viewModel.retryPhotoUpload()

        val state = viewModel.state.value
        assertTrue(offered)
        assertFalse(offeredElsewhere)
        assertEquals(listOf(7L to "wartet"), api.uploads)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 8)), state.activity)
        assertFalse(state.photoPending)
        assertFalse(pending.has(7))
    }

    @Test
    fun `Löschen der Aktivität löscht auch das wartende Foto`() {
        waitingPhoto(7)
        val viewModel = ActivityDetailViewModel(Api(), id = 7, noPhoto, pending)

        viewModel.delete()

        assertTrue(viewModel.state.value.deleted)
        assertFalse(pending.has(7))
    }
}
