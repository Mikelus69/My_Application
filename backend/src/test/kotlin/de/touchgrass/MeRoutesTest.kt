package de.touchgrass

import de.touchgrass.plugins.ErrorResponse
import de.touchgrass.users.ChangePasswordRequest
import de.touchgrass.users.GoalType
import de.touchgrass.users.LoginRequest
import de.touchgrass.users.UpdateMeRequest
import de.touchgrass.users.UserDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MeRoutesTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `ohne Token liefert me 401`() = testApplication {
        val client = startApp()

        val response = client.get("/api/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ErrorResponse("UNAUTHORIZED", "Bitte melde dich an."), response.body<ErrorResponse>())
    }

    @Test
    fun `kaputter Token liefert 401`() = testApplication {
        val client = startApp()

        val response = client.get("/api/me") { bearerAuth("kein-echter-token") }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `me liefert das eigene Profil`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.get("/api/me") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("max_m", response.body<UserDto>().username)
    }

    @Test
    fun `PATCH me ändert nur die mitgeschickten Felder`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        client.patch("/api/me") { bearerAuth(token); jsonBody(UpdateMeRequest(weightKg = 78.5)) }
        val response = client.patch("/api/me") {
            bearerAuth(token)
            jsonBody(UpdateMeRequest(goalType = GoalType.ACTIVITIES, goalTarget = 4))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val user = response.body<UserDto>()
        assertEquals(78.5, user.weightKg)
        assertEquals(GoalType.ACTIVITIES, user.goalType)
        assertEquals(4, user.goalTarget)
    }

    @Test
    fun `unplausibles Gewicht liefert 400 mit Feld`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.patch("/api/me") { bearerAuth(token); jsonBody(UpdateMeRequest(weightKg = 12.0)) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("weightKg", response.body<ErrorResponse>().field)
    }

    @Test
    fun `Passwort ändern macht das alte Passwort ungültig`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val change = client.put("/api/me/password") {
            bearerAuth(token)
            jsonBody(ChangePasswordRequest("geheim123", "nochgeheimer456"))
        }
        val oldLogin = client.post("/api/auth/login") { jsonBody(LoginRequest("max_m", "geheim123")) }
        val newLogin = client.post("/api/auth/login") { jsonBody(LoginRequest("max_m", "nochgeheimer456")) }

        assertEquals(HttpStatusCode.NoContent, change.status)
        assertEquals(HttpStatusCode.Unauthorized, oldLogin.status)
        assertEquals(HttpStatusCode.OK, newLogin.status)
    }

    @Test
    fun `falsches altes Passwort liefert 400 am Feld oldPassword`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.put("/api/me/password") {
            bearerAuth(token)
            jsonBody(ChangePasswordRequest("falsch123", "nochgeheimer456"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            ErrorResponse("VALIDATION_ERROR", "Das bisherige Passwort ist falsch.", "oldPassword"),
            response.body<ErrorResponse>(),
        )
    }

    @Test
    fun `Konto löschen entfernt den Benutzer`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val delete = client.delete("/api/me") { bearerAuth(token) }
        val me = client.get("/api/me") { bearerAuth(token) }
        val login = client.post("/api/auth/login") { jsonBody(LoginRequest("max_m", "geheim123")) }

        assertEquals(HttpStatusCode.NoContent, delete.status)
        assertEquals(HttpStatusCode.Unauthorized, me.status)
        assertEquals(HttpStatusCode.Unauthorized, login.status)
    }
}
