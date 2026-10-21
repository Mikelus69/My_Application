package de.touchgrass

import de.touchgrass.activities.CreateActivityResponse
import de.touchgrass.activities.Visibility
import de.touchgrass.logic.PointsBreakdown
import de.touchgrass.plugins.ErrorResponse
import de.touchgrass.users.GoalType
import de.touchgrass.users.UpdateMeRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityCreateTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `erste Aktivität bekommt kcal, Punkte und den Bonus für die neue Sportart`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        client.patch("/api/me") { bearerAuth(token); jsonBody(UpdateMeRequest(weightKg = 80.0)) }

        val response = client.post("/api/activities") {
            bearerAuth(token)
            jsonBody(activity(startedAt = "2026-10-05T07:30:00Z", durationMin = 45).copy(description = "  Schöne Runde  "))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<CreateActivityResponse>()
        assertTrue(body.activity.mine)
        assertEquals("max_m", body.activity.username)
        assertEquals("2026-10-05T09:30:00+02:00", body.activity.startedAt)
        assertEquals(480, body.activity.caloriesKcal)
        assertEquals("Schöne Runde", body.activity.description)
        assertEquals(Visibility.PRIVATE, body.activity.visibility)
        assertNull(body.activity.latitude)
        assertEquals(PointsBreakdown(45, 10, 20, 0, 75), body.pointsBreakdown)
        assertEquals(75, body.activity.points)
    }

    @Test
    fun `gleiche Sportart ein zweites Mal gibt keinen Sportart-Bonus`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        client.createActivity(token)

        val second = client.createActivity(token, activity(startedAt = "2026-10-06T18:00:00+02:00", durationMin = 30))

        assertEquals(PointsBreakdown(30, 10, 0, 0, 40), second.pointsBreakdown)
    }

    @Test
    fun `Wochenziel-Bonus genau einmal, wenn das Ziel erreicht wird`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        client.patch("/api/me") { bearerAuth(token); jsonBody(UpdateMeRequest(goalType = GoalType.MINUTES, goalTarget = 60)) }

        val first = client.createActivity(token, activity(startedAt = "2026-10-05T09:00:00+02:00", durationMin = 40))
        val second = client.createActivity(token, activity(sportCode = "CYCLING", startedAt = "2026-10-07T09:00:00+02:00", durationMin = 30))
        val third = client.createActivity(token, activity(sportCode = "HIKING", startedAt = "2026-10-08T09:00:00+02:00", durationMin = 30))
        val nextWeek = client.createActivity(token, activity(startedAt = "2026-10-12T09:00:00+02:00", durationMin = 60))

        assertEquals(0, first.pointsBreakdown.weeklyGoal)
        assertEquals(50, second.pointsBreakdown.weeklyGoal)
        assertEquals(0, third.pointsBreakdown.weeklyGoal)
        assertEquals(50, nextWeek.pointsBreakdown.weeklyGoal)
    }

    @Test
    fun `unbekannte Sportart liefert 400 am Feld sportCode`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.post("/api/activities") { bearerAuth(token); jsonBody(activity(sportCode = "CURLING")) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorResponse("VALIDATION_ERROR", "Unbekannte Sportart.", "sportCode"), response.body<ErrorResponse>())
    }

    @Test
    fun `Startzeit in der Zukunft liefert 400 am Feld startedAt`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.post("/api/activities") { bearerAuth(token); jsonBody(activity(startedAt = "2099-01-01T10:00:00+01:00")) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("startedAt", response.body<ErrorResponse>().field)
    }

    @Test
    fun `Anlegen braucht einen Token`() = testApplication {
        val client = startApp()

        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/activities") { jsonBody(activity()) }.status)
    }
}
