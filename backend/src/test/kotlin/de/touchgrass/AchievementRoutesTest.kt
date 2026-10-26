package de.touchgrass

import de.touchgrass.achievements.AchievementDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AchievementRoutesTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `erste Aktivität schaltet Erste Schritte frei, die zweite nichts mehr`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val first = client.createActivity(token)
        val second = client.createActivity(token, activity(startedAt = "2026-10-07T09:00:00+02:00"))

        val unlocked = first.newAchievements.single()
        assertEquals("FIRST_ACTIVITY", unlocked.code)
        assertEquals(1, unlocked.progress)
        assertNotNull(unlocked.unlockedAt)
        assertEquals(emptyList(), second.newAchievements)
    }

    @Test
    fun `Liste zeigt alle Achievements mit Fortschritt, leichte zuerst`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        listOf("2026-10-05", "2026-10-06", "2026-10-07").forEach { day ->
            client.createActivity(token, activity(startedAt = "${day}T09:00:00+02:00", durationMin = 20))
        }

        val list = client.get("/api/achievements") { bearerAuth(token) }.body<List<AchievementDto>>()

        assertEquals(listOf("FIRST_ACTIVITY", "STREAK_7", "RUNNER_10", "MINUTES_500", "KCAL_1000", "MARATHON"), list.map { it.code })
        assertEquals(listOf(1, 3, 3, 60, 561, 60), list.map { it.progress })
        assertEquals(listOf(1, 7, 10, 500, 1000, 2520), list.map { it.threshold })
        assertNotNull(list.first().unlockedAt)
        assertEquals(listOf(null, null, null, null, null), list.drop(1).map { it.unlockedAt })
    }

    @Test
    fun `freigeschaltetes Achievement bleibt nach dem Löschen der Aktivität`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id
        client.delete("/api/activities/$id") { bearerAuth(token) }

        val first = client.get("/api/achievements") { bearerAuth(token) }.body<List<AchievementDto>>().first()

        assertEquals("FIRST_ACTIVITY", first.code)
        assertNotNull(first.unlockedAt)
        assertEquals(1, first.progress)
    }

    @Test
    fun `sieben Tage in Folge schalten die Sportwoche frei, auch nachgetragen`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        listOf("2026-10-04", "2026-10-03", "2026-10-02", "2026-10-01", "2026-09-30", "2026-09-29").forEach { day ->
            client.createActivity(token, activity(startedAt = "${day}T09:00:00+02:00"))
        }

        val seventh = client.createActivity(token, activity(startedAt = "2026-09-28T09:00:00+02:00"))

        assertEquals(listOf("STREAK_7"), seventh.newAchievements.map { it.code })
    }

    @Test
    fun `Achievements brauchen eine Anmeldung`() = testApplication {
        val client = startApp()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/achievements").status)
    }

    @Test
    fun `ohne Aktivitäten ist nichts freigeschaltet`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val list = client.get("/api/achievements") { bearerAuth(token) }.body<List<AchievementDto>>()

        assertEquals(6, list.size)
        assertNull(list.firstOrNull { it.unlockedAt != null })
        assertEquals(List(6) { 0 }, list.map { it.progress })
    }
}
