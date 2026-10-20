package de.touchgrass

import de.touchgrass.plugins.ErrorResponse
import de.touchgrass.users.AuthResponse
import de.touchgrass.users.GoalType
import de.touchgrass.users.LoginRequest
import de.touchgrass.users.RegisterRequest
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRoutesTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `Registrierung liefert Token und Benutzer mit Standardwerten`() = testApplication {
        val client = startApp()

        val response = client.post("/api/auth/register") {
            jsonBody(RegisterRequest(" max_m ", "Max@Example.com", "geheim123"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<AuthResponse>()
        assertTrue(body.token.isNotBlank())
        assertEquals("max_m", body.user.username)
        assertEquals("max@example.com", body.user.email)
        assertNull(body.user.weightKg)
        assertEquals(GoalType.MINUTES, body.user.goalType)
        assertEquals(150, body.user.goalTarget)
    }

    @Test
    fun `Benutzername ist unabhängig von Groß- und Kleinschreibung vergeben`() = testApplication {
        val client = startApp()
        client.register("max_m")

        val response = client.post("/api/auth/register") {
            jsonBody(RegisterRequest("MAX_M", "anders@example.com", "geheim123"))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(
            ErrorResponse("USERNAME_TAKEN", "Dieser Benutzername ist schon vergeben.", "username"),
            response.body<ErrorResponse>(),
        )
    }

    @Test
    fun `E-Mail ist nur einmal registrierbar`() = testApplication {
        val client = startApp()
        client.register("max_m", email = "max@example.com")

        val response = client.post("/api/auth/register") {
            jsonBody(RegisterRequest("lisa", "MAX@example.com", "geheim123"))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("EMAIL_TAKEN", response.body<ErrorResponse>().error)
    }

    @Test
    fun `ungültiger Benutzername liefert 400 mit Feld`() = testApplication {
        val client = startApp()

        val response = client.post("/api/auth/register") {
            jsonBody(RegisterRequest("max m", "max@example.com", "geheim123"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("VALIDATION_ERROR", error.error)
        assertEquals("username", error.field)
    }

    @Test
    fun `unvollständiges JSON liefert 400`() = testApplication {
        val client = startApp()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"max_m"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorResponse("VALIDATION_ERROR", "Ungültige Anfrage."), response.body<ErrorResponse>())
    }

    @Test
    fun `Login klappt mit Benutzername und mit E-Mail`() = testApplication {
        val client = startApp()
        client.register("max_m", email = "max@example.com")

        val byName = client.post("/api/auth/login") { jsonBody(LoginRequest("MAX_M", "geheim123")) }
        val byEmail = client.post("/api/auth/login") { jsonBody(LoginRequest("max@example.com", "geheim123")) }

        assertEquals(HttpStatusCode.OK, byName.status)
        assertEquals("max_m", byName.body<AuthResponse>().user.username)
        assertEquals(HttpStatusCode.OK, byEmail.status)
    }

    @Test
    fun `falsches Passwort liefert 401`() = testApplication {
        val client = startApp()
        client.register("max_m")

        val response = client.post("/api/auth/login") { jsonBody(LoginRequest("max_m", "falsch123")) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("INVALID_CREDENTIALS", response.body<ErrorResponse>().error)
    }
}
