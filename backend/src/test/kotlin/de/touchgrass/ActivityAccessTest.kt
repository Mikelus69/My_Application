package de.touchgrass

import de.touchgrass.activities.ActivityDto
import de.touchgrass.activities.Visibility
import de.touchgrass.plugins.ErrorResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ActivityAccessTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `Verlauf zeigt nur eigene Aktivitäten, neueste zuerst`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa").token
        client.createActivity(max, activity(startedAt = "2026-10-05T09:00:00+02:00"))
        client.createActivity(max, activity(startedAt = "2026-10-07T09:00:00+02:00"))
        client.createActivity(lisa, activity(startedAt = "2026-10-06T09:00:00+02:00"))

        val list = client.get("/api/activities") { bearerAuth(max) }.body<List<ActivityDto>>()

        assertEquals(listOf("2026-10-07T09:00:00+02:00", "2026-10-05T09:00:00+02:00"), list.map { it.startedAt })
    }

    @Test
    fun `Verlauf filtert nach Sportart und Zeitraum`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        client.createActivity(token, activity(sportCode = "RUNNING", startedAt = "2026-09-30T23:30:00+02:00"))
        client.createActivity(token, activity(sportCode = "RUNNING", startedAt = "2026-10-01T00:15:00+02:00"))
        client.createActivity(token, activity(sportCode = "CYCLING", startedAt = "2026-10-03T10:00:00+02:00"))
        client.createActivity(token, activity(sportCode = "RUNNING", startedAt = "2026-10-04T23:59:00+02:00"))
        client.createActivity(token, activity(sportCode = "RUNNING", startedAt = "2026-10-05T00:00:00+02:00"))

        val list = client.get("/api/activities?sport=RUNNING&from=2026-10-01&to=2026-10-04") { bearerAuth(token) }
            .body<List<ActivityDto>>()

        assertEquals(listOf("2026-10-04T23:59:00+02:00", "2026-10-01T00:15:00+02:00"), list.map { it.startedAt })
    }

    @Test
    fun `falsches Datumsformat im Filter liefert 400`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.get("/api/activities?from=01.10.2026") { bearerAuth(token) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("from", response.body<ErrorResponse>().field)
    }

    @Test
    fun `fremde private Aktivität ist nicht sichtbar, öffentliche schon`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa").token
        val private = client.createActivity(lisa).activity.id
        val public = client.createActivity(lisa, activity(visibility = Visibility.PUBLIC)).activity.id

        val privateResponse = client.get("/api/activities/$private") { bearerAuth(max) }
        val publicResponse = client.get("/api/activities/$public") { bearerAuth(max) }

        assertEquals(HttpStatusCode.NotFound, privateResponse.status)
        assertEquals(HttpStatusCode.OK, publicResponse.status)
        val dto = publicResponse.body<ActivityDto>()
        assertFalse(dto.mine)
        assertEquals("lisa", dto.username)
    }

    @Test
    fun `eigene Aktivität löschen, fremde nicht`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa").token
        val own = client.createActivity(max).activity.id
        val foreign = client.createActivity(lisa, activity(visibility = Visibility.PUBLIC)).activity.id

        val deleteOwn = client.delete("/api/activities/$own") { bearerAuth(max) }
        val getOwn = client.get("/api/activities/$own") { bearerAuth(max) }
        val deleteForeign = client.delete("/api/activities/$foreign") { bearerAuth(max) }
        val deleteMissing = client.delete("/api/activities/999999") { bearerAuth(max) }
        val deleteGarbage = client.delete("/api/activities/abc") { bearerAuth(max) }

        assertEquals(HttpStatusCode.NoContent, deleteOwn.status)
        assertEquals(HttpStatusCode.NotFound, getOwn.status)
        assertEquals(HttpStatusCode.Forbidden, deleteForeign.status)
        assertEquals("FORBIDDEN", deleteForeign.body<ErrorResponse>().error)
        assertEquals(HttpStatusCode.NotFound, deleteMissing.status)
        assertEquals(HttpStatusCode.NotFound, deleteGarbage.status)
    }
}
