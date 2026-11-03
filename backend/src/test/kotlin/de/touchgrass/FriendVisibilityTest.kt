package de.touchgrass

import de.touchgrass.activities.ActivityDto
import de.touchgrass.activities.Visibility
import de.touchgrass.friends.SendFriendRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendVisibilityTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    private suspend fun HttpClient.map(token: String): List<ActivityDto> = get("/api/map/activities") { bearerAuth(token) }.body()

    @Test
    fun `Freunde-Aktivität sehen nur angenommene Freunde`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa_k").token
        val tom = client.register("tom_b").token
        val anna = client.register("anna").token
        client.befriend(max, "max_m", lisa, "lisa_k")
        client.post("/api/friends/requests") { bearerAuth(anna); jsonBody(SendFriendRequest("max_m")) }
        val id = client.createActivity(max, activity(visibility = Visibility.FRIENDS, latitude = 48.1642, longitude = 11.6056)).activity.id

        val lisaMap = client.map(lisa)
        val lisaDetail = client.get("/api/activities/$id") { bearerAuth(lisa) }

        assertEquals(listOf(id), lisaMap.map { it.id })
        assertEquals(HttpStatusCode.OK, lisaDetail.status)
        assertEquals(Visibility.FRIENDS, lisaDetail.body<ActivityDto>().visibility)
        for (stranger in listOf(tom, anna)) {
            assertTrue(client.map(stranger).isEmpty())
            assertEquals(HttpStatusCode.NotFound, client.get("/api/activities/$id") { bearerAuth(stranger) }.status)
        }
    }

    @Test
    fun `nach dem Beenden der Freundschaft ist die Freunde-Aktivität nicht mehr sichtbar`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa_k").token
        client.befriend(max, "max_m", lisa, "lisa_k")
        val id = client.createActivity(max, activity(visibility = Visibility.FRIENDS, latitude = 48.1642, longitude = 11.6056)).activity.id

        client.delete("/api/friends/max_m") { bearerAuth(lisa) }

        assertTrue(client.map(lisa).isEmpty())
        assertEquals(HttpStatusCode.NotFound, client.get("/api/activities/$id") { bearerAuth(lisa) }.status)
    }

    @Test
    fun `friend kennzeichnet alle Aktivitäten von Freunden`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa_k").token
        val tom = client.register("tom_b").token
        client.befriend(max, "max_m", lisa, "lisa_k")
        client.createActivity(max, activity(startedAt = "2026-10-05T09:00:00+02:00", latitude = 48.1642, longitude = 11.6056))
        client.createActivity(
            lisa,
            activity(startedAt = "2026-10-06T09:00:00+02:00", visibility = Visibility.PUBLIC, latitude = 52.52, longitude = 13.405),
        )
        client.createActivity(
            tom,
            activity(startedAt = "2026-10-07T09:00:00+02:00", visibility = Visibility.PUBLIC, latitude = 50.11, longitude = 8.68),
        )

        val list = client.map(max)

        assertEquals(listOf("tom_b", "lisa_k", "max_m"), list.map { it.username })
        assertEquals(listOf(false, true, false), list.map { it.friend })
        assertEquals(listOf(false, false, true), list.map { it.mine })
    }
}
