package de.touchgrass

import de.touchgrass.activities.ActivityDto
import de.touchgrass.activities.Visibility
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MapRoutesTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `Karte zeigt eigene und öffentliche fremde Aktivitäten mit Standort, neueste zuerst`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa").token
        val ownPrivate = client.createActivity(
            max,
            activity(startedAt = "2026-10-05T09:00:00+02:00", latitude = 48.1642, longitude = 11.6056),
        ).activity.id
        client.createActivity(max, activity(startedAt = "2026-10-06T09:00:00+02:00"))
        val lisaPublic = client.createActivity(
            lisa,
            activity(startedAt = "2026-10-07T09:00:00+02:00", visibility = Visibility.PUBLIC, latitude = 52.52, longitude = 13.405),
        ).activity.id
        client.createActivity(lisa, activity(startedAt = "2026-10-08T09:00:00+02:00", latitude = 50.11, longitude = 8.68))
        client.createActivity(lisa, activity(startedAt = "2026-10-09T09:00:00+02:00", visibility = Visibility.PUBLIC))

        val list = client.get("/api/map/activities") { bearerAuth(max) }.body<List<ActivityDto>>()

        assertEquals(listOf(lisaPublic, ownPrivate), list.map { it.id })
        assertEquals(listOf(false, true), list.map { it.mine })
        assertEquals(listOf("lisa", "max_m"), list.map { it.username })
        assertEquals(52.52, list.first().latitude)
        assertEquals(13.405, list.first().longitude)
    }

    @Test
    fun `Karte braucht eine Anmeldung`() = testApplication {
        val client = startApp()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/map/activities").status)
    }
}
