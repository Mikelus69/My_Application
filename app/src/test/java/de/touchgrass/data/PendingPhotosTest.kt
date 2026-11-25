package de.touchgrass.data

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testActivity
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException

class PendingPhotosTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val pending by lazy { PendingPhotos(File(folder.root, "pending")) }

    private inner class Api(private val failure: Exception? = null) : FakeSportApi() {
        val uploads = mutableListOf<Pair<Long, String>>()
        val offeredWhileUploading = mutableListOf<Boolean>()
        override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto {
            uploads += id to Buffer().also { photo.writeTo(it) }.readUtf8()
            offeredWhileUploading += pending.has(id)
            if (failure != null) throw failure
            return testActivity(id = id).copy(photoVersion = 3)
        }
    }

    private fun photoFile(content: String = "jpeg"): File = folder.newFile().apply { writeText(content) }

    private suspend fun failedUpload(failure: Exception, activityId: Long, photo: File): ApiException? =
        runCatching { pending.upload(Api(failure), activityId, photo) }.exceptionOrNull() as? ApiException

    @Test
    fun `während des Hochladens wird nichts angeboten, danach wartet nichts mehr`() = runBlocking {
        val api = Api()
        val photo = photoFile()

        val activity = pending.upload(api, 42, photo)

        assertEquals(3L, activity.photoVersion)
        assertEquals(listOf(42L to "jpeg"), api.uploads)
        assertEquals(listOf(false), api.offeredWhileUploading)
        assertFalse(photo.exists())
        assertFalse(pending.has(42))
    }

    @Test
    fun `bei Netz- und Serverfehlern wartet das Foto unter der Aktivität`() = runBlocking {
        val failures = listOf(
            IOException("Verbindung abgebrochen"),
            httpError(500, """{"error":"INTERNAL_ERROR","message":"Interner Serverfehler"}"""),
            HttpException(Response.error<Any>(503, "<html>Service Unavailable</html>".toResponseBody("text/html".toMediaType()))),
        )

        val codes = failures.mapIndexed { index, failure -> failedUpload(failure, 40L + index, photoFile("jpeg $index"))?.code }

        assertEquals(listOf("NETWORK", "INTERNAL_ERROR", "HTTP_503"), codes)
        assertEquals(listOf("jpeg 0", "jpeg 1", "jpeg 2"), (40L..42L).map { pending.file(it).readText() })
    }

    @Test
    fun `lehnt der Server das Foto ab, wird es gelöscht`() = runBlocking {
        val failures = listOf(
            httpError(413, """{"error":"PAYLOAD_TOO_LARGE","message":"Das Foto ist zu groß.","field":"photo"}"""),
            httpError(400, """{"error":"VALIDATION_ERROR","message":"Bitte ein Foto im JPEG-Format schicken.","field":"photo"}"""),
            httpError(403, """{"error":"FORBIDDEN","message":"Das ist nicht deine Aktivität."}"""),
            httpError(404, """{"error":"NOT_FOUND","message":"Aktivität nicht gefunden."}"""),
        )
        val photos = List(failures.size) { photoFile() }
        failedUpload(IOException("Verbindung abgebrochen"), 44, photoFile())

        val codes = failures.mapIndexed { index, failure -> failedUpload(failure, 40L + index, photos[index])?.code }
        val retried = failedUpload(httpError(413, """{"error":"PAYLOAD_TOO_LARGE","message":"Das Foto ist zu groß.","field":"photo"}"""), 44, pending.file(44))?.code

        assertEquals(listOf("PAYLOAD_TOO_LARGE", "VALIDATION_ERROR", "FORBIDDEN", "NOT_FOUND"), codes)
        assertEquals("PAYLOAD_TOO_LARGE", retried)
        assertEquals(List(5) { false }, (40L..44L).map { pending.has(it) })
        assertTrue(photos.none { it.exists() })
    }

    @Test
    fun `ein wartendes Foto lässt sich erneut hochladen`() = runBlocking {
        failedUpload(IOException("Verbindung abgebrochen"), 42, photoFile())
        val api = Api()

        pending.upload(api, 42, pending.file(42))

        assertEquals(listOf(42L to "jpeg"), api.uploads)
        assertFalse(pending.has(42))
    }

    @Test
    fun `ein neues Foto ersetzt das wartende, remove trifft nur die eigene Aktivität`() = runBlocking {
        failedUpload(IOException("Verbindung abgebrochen"), 42, photoFile("alt"))
        failedUpload(IOException("Verbindung abgebrochen"), 42, photoFile("neu"))
        failedUpload(IOException("Verbindung abgebrochen"), 7, photoFile("sieben"))

        pending.remove(7)

        assertEquals("neu", pending.file(42).readText())
        assertFalse(pending.has(7))
    }
}
