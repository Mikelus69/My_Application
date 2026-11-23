package de.touchgrass

import de.touchgrass.activities.ActivityDto
import de.touchgrass.activities.ActivityPhotosTable
import de.touchgrass.activities.PhotoRules
import de.touchgrass.activities.Visibility
import de.touchgrass.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoRoutesTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    private suspend fun HttpClient.details(token: String, id: Long): ActivityDto = get("/api/activities/$id") { bearerAuth(token) }.body()

    private suspend fun HttpClient.deletePhoto(token: String, id: Long): HttpResponse =
        delete("/api/activities/$id/photo") { bearerAuth(token) }

    private fun storedPhotos(): Long = transaction(TestDatabase.database) { ActivityPhotosTable.selectAll().count() }

    private suspend fun HttpClient.photo(token: String, id: Long): HttpResponse = get("/api/activities/$id/photo") { bearerAuth(token) }

    @Test
    fun `Hochladen liefert die Aktivität mit photoVersion`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id

        val response = client.putPhoto(token, id)

        assertEquals(HttpStatusCode.OK, response.status)
        val dto = response.body<ActivityDto>()
        assertEquals(id, dto.id)
        assertNotNull(dto.photoVersion)
    }

    @Test
    fun `erneutes Hochladen vergibt eine größere photoVersion`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id

        val first = client.putPhoto(token, id).body<ActivityDto>().photoVersion!!
        val second = client.putPhoto(token, id, jpeg(3, 4)).body<ActivityDto>().photoVersion!!

        assertTrue(second > first)
        assertEquals(second, client.details(token, id).photoVersion)
    }

    @Test
    fun `Entfernen liefert 204 auch ohne Foto, die Nummer wiederholt sich danach nicht`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id
        val first = client.putPhoto(token, id).body<ActivityDto>().photoVersion!!

        val removed = client.deletePhoto(token, id)
        val removedAgain = client.deletePhoto(token, id)
        val afterRemoval = client.details(token, id).photoVersion
        val uploadedAgain = client.putPhoto(token, id).body<ActivityDto>().photoVersion!!

        assertEquals(HttpStatusCode.NoContent, removed.status)
        assertEquals(HttpStatusCode.NoContent, removedAgain.status)
        assertNull(afterRemoval)
        assertTrue(uploadedAgain > first)
    }

    @Test
    fun `photoVersion steht in Verlauf, Karte und Details`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token, activity(latitude = 48.1642, longitude = 11.6056)).activity.id
        val version = client.putPhoto(token, id).body<ActivityDto>().photoVersion

        val history = client.get("/api/activities") { bearerAuth(token) }.body<List<ActivityDto>>()
        val map = client.get("/api/map/activities") { bearerAuth(token) }.body<List<ActivityDto>>()

        assertNotNull(version)
        assertEquals(listOf(version), history.map { it.photoVersion })
        assertEquals(listOf(version), map.map { it.photoVersion })
        assertEquals(version, client.details(token, id).photoVersion)
    }

    @Test
    fun `Foto einer fremden Aktivität ändern liefert 403`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa").token
        val id = client.createActivity(lisa, activity(visibility = Visibility.PUBLIC)).activity.id

        val upload = client.putPhoto(max, id)
        val remove = client.deletePhoto(max, id)

        for (response in listOf(upload, remove)) {
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(ErrorResponse("FORBIDDEN", "Das ist nicht deine Aktivität."), response.body<ErrorResponse>())
        }
        assertNull(client.details(lisa, id).photoVersion)
    }

    @Test
    fun `fehlende Aktivität und Id abc liefern 404`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val responses = listOf(
            client.putPhoto(token, 999999),
            client.put("/api/activities/abc/photo") { bearerAuth(token); contentType(ContentType.Image.JPEG); setBody(jpeg()) },
            client.deletePhoto(token, 999999),
            client.delete("/api/activities/abc/photo") { bearerAuth(token) },
        )

        for (response in responses) {
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(ErrorResponse("NOT_FOUND", "Aktivität nicht gefunden."), response.body<ErrorResponse>())
        }
    }

    @Test
    fun `Hochladen und Entfernen brauchen eine Anmeldung`() = testApplication {
        val client = startApp()

        val upload = client.put("/api/activities/1/photo") { contentType(ContentType.Image.JPEG); setBody(jpeg()) }
        val remove = client.delete("/api/activities/1/photo")

        assertEquals(HttpStatusCode.Unauthorized, upload.status)
        assertEquals(HttpStatusCode.Unauthorized, remove.status)
    }

    @Test
    fun `mehr als 200 KB liefert 413, auch ohne Content-Length`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id
        val tooLarge = ByteArray(PhotoRules.MAX_BYTES + 1)

        val withLength = client.putPhoto(token, id, tooLarge)
        val streamed = client.put("/api/activities/$id/photo") {
            bearerAuth(token)
            contentType(ContentType.Image.JPEG)
            setBody(ByteReadChannel(tooLarge))
        }
        val exactlyMax = client.putPhoto(token, id, ByteArray(PhotoRules.MAX_BYTES))

        for (response in listOf(withLength, streamed)) {
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertEquals(ErrorResponse("PAYLOAD_TOO_LARGE", "Das Foto ist zu groß.", "photo"), response.body<ErrorResponse>())
        }
        assertEquals(HttpStatusCode.BadRequest, exactlyMax.status)
    }

    @Test
    fun `kein JPEG oder länger als 512 Pixel liefert 400 am Feld photo`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id

        val notJpeg = client.putPhoto(token, id, "kein Foto".encodeToByteArray())
        val tooLong = client.putPhoto(token, id, jpeg(600, 10))

        assertEquals(HttpStatusCode.BadRequest, notJpeg.status)
        assertEquals(ErrorResponse("VALIDATION_ERROR", "Bitte ein Foto im JPEG-Format schicken.", "photo"), notJpeg.body<ErrorResponse>())
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
        assertEquals(ErrorResponse("VALIDATION_ERROR", "Das Foto darf höchstens 512 Pixel lang sein.", "photo"), tooLong.body<ErrorResponse>())
        assertNull(client.details(token, id).photoVersion)
    }

    @Test
    fun `Löschen der Aktivität oder des Kontos löscht das Foto`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val first = client.createActivity(token).activity.id
        val second = client.createActivity(token).activity.id
        client.putPhoto(token, first)
        client.putPhoto(token, second)

        client.delete("/api/activities/$first") { bearerAuth(token) }
        val afterActivity = storedPhotos()
        client.delete("/api/me") { bearerAuth(token) }

        assertEquals(1L, afterActivity)
        assertEquals(0L, storedPhotos())
    }

    @Test
    fun `eigenes Foto kommt als JPEG mit Cache-Control und genau den hochgeladenen Bytes`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id
        client.putPhoto(token, id)
        val replacement = jpeg(3, 4)
        val version = client.putPhoto(token, id, replacement).body<ActivityDto>().photoVersion

        val response = client.get("/api/activities/$id/photo?v=$version") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("image/jpeg", response.headers[HttpHeaders.ContentType])
        assertEquals("private, max-age=31536000, immutable", response.headers[HttpHeaders.CacheControl])
        assertContentEquals(replacement, response.bodyAsBytes())
    }

    @Test
    fun `fremdes Foto nur bei öffentlicher Aktivität oder als Freund`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa_k").token
        val tom = client.register("tom_b").token
        client.befriend(max, "max_m", lisa, "lisa_k")
        val ids = listOf(Visibility.PRIVATE, Visibility.FRIENDS, Visibility.PUBLIC)
            .map { client.createActivity(max, activity(visibility = it)).activity.id }
        ids.forEach { client.putPhoto(max, it) }

        val forLisa = ids.map { client.photo(lisa, it).status }
        val forTom = ids.map { client.photo(tom, it).status }

        assertEquals(listOf(HttpStatusCode.NotFound, HttpStatusCode.OK, HttpStatusCode.OK), forLisa)
        assertEquals(listOf(HttpStatusCode.NotFound, HttpStatusCode.NotFound, HttpStatusCode.OK), forTom)
    }

    @Test
    fun `ohne Foto, nach dem Entfernen und ohne Aktivität liefert 404`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id

        val withoutPhoto = client.photo(token, id)
        client.putPhoto(token, id)
        client.deletePhoto(token, id)
        val afterRemoval = client.photo(token, id)
        val missing = client.photo(token, 999999)
        val notANumber = client.get("/api/activities/abc/photo") { bearerAuth(token) }

        for (response in listOf(withoutPhoto, afterRemoval, missing, notANumber)) {
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(ErrorResponse("NOT_FOUND", "Foto nicht gefunden."), response.body<ErrorResponse>())
            assertNull(response.headers[HttpHeaders.CacheControl])
        }
    }

    @Test
    fun `Foto abrufen braucht eine Anmeldung`() = testApplication {
        val client = startApp()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/activities/1/photo").status)
    }
}
