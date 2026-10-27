package de.touchgrass.data

import de.touchgrass.data.dto.ChangePasswordRequest
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.data.dto.LeaderboardMetric
import de.touchgrass.data.dto.LeaderboardPeriod
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

    @Test
    fun `createActivity schickt Pflichtfelder und lässt Standardwerte weg`() = runBlocking {
        enqueueJson(201, """{"activity":{"id":1,"username":"max_m","mine":true,"sportCode":"RUNNING","startedAt":"2026-10-05T09:30:00+02:00","durationMin":45,"caloriesKcal":480,"points":75,"visibility":"PRIVATE"},"pointsBreakdown":{"minutes":45,"completed":10,"newSport":20,"weeklyGoal":0,"total":75}}""")

        val response = api.createActivity(CreateActivityRequest("RUNNING", "2026-10-05T09:30:00+02:00", 45))

        val request = server.takeRequest()
        assertEquals("/api/activities", request.path)
        assertEquals("""{"sportCode":"RUNNING","startedAt":"2026-10-05T09:30:00+02:00","durationMin":45}""", request.body.readUtf8())
        assertEquals(75, response.pointsBreakdown.total)
    }

    @Test
    fun `activities lässt leere Filter weg`() = runBlocking {
        enqueueJson(200, "[]")
        enqueueJson(200, "[]")

        api.activities()
        api.activities(sport = "RUNNING", from = "2026-10-01")

        assertEquals("/api/activities", server.takeRequest().path)
        assertEquals("/api/activities?sport=RUNNING&from=2026-10-01", server.takeRequest().path)
    }

    @Test
    fun `deleteActivity setzt die ID in den Pfad`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        api.deleteActivity(42)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/activities/42", request.path)
    }

    @Test
    fun `mapActivities liest Aktivitäten mit Standort`() = runBlocking {
        enqueueJson(200, """[{"id":7,"username":"lisa","mine":false,"sportCode":"CYCLING","startedAt":"2026-10-05T09:30:00+02:00","durationMin":60,"caloriesKcal":560,"points":90,"latitude":52.52,"longitude":13.405,"visibility":"PUBLIC"}]""")

        val activities = api.mapActivities()

        assertEquals("/api/map/activities", server.takeRequest().path)
        assertEquals(52.52, activities.single().latitude)
        assertEquals(13.405, activities.single().longitude)
    }

    @Test
    fun `achievements liest freigeschaltete und gesperrte`() = runBlocking {
        enqueueJson(200, """[{"code":"FIRST_ACTIVITY","name":"Erste Schritte","description":"Erste Aktivität eingetragen","emoji":"🥉","threshold":1,"progress":1,"unlockedAt":"2026-10-05T10:18:00+02:00"},{"code":"RUNNER_10","name":"Läufer","description":"10 Laufaktivitäten","emoji":"🏃","threshold":10,"progress":7}]""")

        val achievements = api.achievements()

        assertEquals("/api/achievements", server.takeRequest().path)
        assertEquals("2026-10-05T10:18:00+02:00", achievements[0].unlockedAt)
        assertEquals(null, achievements[1].unlockedAt)
        assertEquals(7, achievements[1].progress)
    }

    @Test
    fun `leaderboard schickt Zeitraum und Wertung als Parameter`() = runBlocking {
        enqueueJson(200, """{"period":"MONTH","metric":"MINUTES","entries":[{"rank":1,"username":"lisa","value":96}]}""")

        val board = api.leaderboard(LeaderboardPeriod.MONTH, LeaderboardMetric.MINUTES)

        assertEquals("/api/leaderboard?period=MONTH&metric=MINUTES", server.takeRequest().path)
        assertEquals(96, board.entries.single().value)
        assertEquals(null, board.me)
    }

    @Test
    fun `stats liest Woche, Wochenminuten, Sportarten und Monats-kcal`() = runBlocking {
        enqueueJson(200, """{"thisWeek":{"activities":3,"minutes":156,"kcal":1420},"minutesPerWeek":[{"weekStart":"2026-10-05","minutes":156}],"activitiesPerSport":[{"sportCode":"RUNNING","count":12}],"kcalPerMonth":[{"month":"2026-10","kcal":1420}]}""")

        val stats = api.stats()

        assertEquals("/api/stats", server.takeRequest().path)
        assertEquals(156, stats.thisWeek.minutes)
        assertEquals("2026-10-05", stats.minutesPerWeek.single().weekStart)
        assertEquals(12, stats.activitiesPerSport.single().count)
        assertEquals("2026-10", stats.kcalPerMonth.single().month)
    }
}
