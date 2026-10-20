package de.touchgrass.data

import de.touchgrass.data.dto.ChangePasswordRequest
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.data.dto.RegisterRequest
import de.touchgrass.data.dto.UpdateMeRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SportApiTest {
    private val server = MockWebServer()
    private val api by lazy { createSportApi(server.url("/api/").toString()) }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    @Test
    fun `health ruft api-health auf und ignoriert unbekannte Felder`() = runBlocking {
        enqueueJson(200, """{"status":"ok","database":"ok","version":"spaeter"}""")

        val health = api.health()

        assertEquals(HealthDto(status = "ok", database = "ok"), health)
        assertEquals("/api/health", server.takeRequest().path)
    }

    @Test
    fun `register schickt die Daten als JSON`() = runBlocking {
        enqueueJson(201, """{"token":"t","user":{"id":1,"username":"max_m","email":"max@example.com","goalType":"MINUTES","goalTarget":150}}""")

        val response = api.register(RegisterRequest("max_m", "max@example.com", "geheim123"))

        val request = server.takeRequest()
        assertEquals("/api/auth/register", request.path)
        assertEquals("""{"username":"max_m","email":"max@example.com","password":"geheim123"}""", request.body.readUtf8())
        assertEquals("t", response.token)
        assertEquals(null, response.user.weightKg)
    }

    @Test
    fun `updateMe lässt nicht gesetzte Felder weg`() = runBlocking {
        enqueueJson(200, """{"id":1,"username":"max_m","email":"max@example.com","weightKg":78.5,"goalType":"MINUTES","goalTarget":150}""")

        api.updateMe(UpdateMeRequest(weightKg = 78.5))

        assertEquals("""{"weightKg":78.5}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `changePassword akzeptiert 204 ohne Inhalt`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        api.changePassword(ChangePasswordRequest("alt12345", "neu12345"))

        assertEquals("PUT", server.takeRequest().method)
    }

    @Test
    fun `apiCall übersetzt die Fehlerantwort des Servers`() = runBlocking {
        enqueueJson(409, """{"error":"USERNAME_TAKEN","message":"Dieser Benutzername ist schon vergeben.","field":"username"}""")

        val error = runCatching { apiCall { api.register(RegisterRequest("max_m", "max@example.com", "geheim123")) } }
            .exceptionOrNull() as ApiException

        assertEquals("USERNAME_TAKEN", error.code)
        assertEquals("Dieser Benutzername ist schon vergeben.", error.message)
        assertEquals("username", error.field)
    }

    @Test
    fun `apiCall meldet einen nicht erreichbaren Server`() = runBlocking {
        server.shutdown()

        val error = runCatching { apiCall { api.health() } }.exceptionOrNull() as ApiException

        assertEquals("NETWORK", error.code)
        assertEquals("Server nicht erreichbar", error.message)
    }
}
