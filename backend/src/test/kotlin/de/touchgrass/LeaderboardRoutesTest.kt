package de.touchgrass

import de.touchgrass.plugins.ErrorResponse
import de.touchgrass.progress.LeaderboardDto
import de.touchgrass.progress.LeaderboardEntryDto
import de.touchgrass.progress.LeaderboardMetric
import de.touchgrass.progress.LeaderboardPeriod
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeaderboardRoutesTest {
    // Donnerstag, 8. Oktober 2026, 12 Uhr Berliner Zeit
    private val thursday = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC)

    @BeforeTest
    fun setUp() = TestDatabase.reset()

    // Punkte je erste Aktivität: Minuten + 10 + 20 (neue Sportart). Alle bleiben unter dem Wochenziel von 150 Minuten.
    private suspend fun HttpClient.seed(): Map<String, String> {
        val tokens = listOf("max_m", "lisa", "tom", "ali", "nina").associateWith { register(it).token }
        createActivity(tokens.getValue("max_m"), activity(startedAt = "2026-10-06T08:00:00+02:00", durationMin = 60))
        createActivity(tokens.getValue("lisa"), activity(startedAt = "2026-10-07T08:00:00+02:00", durationMin = 40))
        createActivity(tokens.getValue("tom"), activity(startedAt = "2026-10-08T08:00:00+02:00", durationMin = 40))
        createActivity(tokens.getValue("ali"), activity(startedAt = "2026-10-01T08:00:00+02:00", durationMin = 100))
        return tokens
    }

    @Test
    fun `Woche nach Punkten mit gleichem Rang bei gleichem Wert`() = testApplication {
        val client = startApp(thursday)
        val tokens = client.seed()

        val board = client.get("/api/leaderboard") { bearerAuth(tokens.getValue("lisa")) }.body<LeaderboardDto>()

        assertEquals(LeaderboardPeriod.WEEK, board.period)
        assertEquals(LeaderboardMetric.POINTS, board.metric)
        assertEquals(
            listOf(LeaderboardEntryDto(1, "max_m", 90), LeaderboardEntryDto(2, "lisa", 70), LeaderboardEntryDto(2, "tom", 70)),
            board.entries,
        )
        assertEquals(LeaderboardEntryDto(2, "lisa", 70), board.me)
    }

    @Test
    fun `Monat nach Minuten zählt auch die Woche davor`() = testApplication {
        val client = startApp(thursday)
        val tokens = client.seed()

        val board = client.get("/api/leaderboard?period=MONTH&metric=MINUTES") { bearerAuth(tokens.getValue("max_m")) }
            .body<LeaderboardDto>()

        assertEquals(listOf("ali" to 100, "max_m" to 60, "lisa" to 40, "tom" to 40), board.entries.map { it.username to it.value })
        assertEquals(listOf(1, 2, 3, 3), board.entries.map { it.rank })
        assertEquals(LeaderboardEntryDto(2, "max_m", 60), board.me)
    }

    @Test
    fun `ohne Aktivität im Zeitraum fehlt der eigene Platz`() = testApplication {
        val client = startApp(thursday)
        val tokens = client.seed()

        val board = client.get("/api/leaderboard") { bearerAuth(tokens.getValue("nina")) }.body<LeaderboardDto>()

        assertEquals(3, board.entries.size)
        assertNull(board.me)
    }

    @Test
    fun `unbekannter Zeitraum oder Wertung liefert 400`() = testApplication {
        val client = startApp(thursday)
        val token = client.register("max_m").token

        val period = client.get("/api/leaderboard?period=YEAR") { bearerAuth(token) }
        val metric = client.get("/api/leaderboard?metric=KCAL") { bearerAuth(token) }

        assertEquals(HttpStatusCode.BadRequest, period.status)
        assertEquals("period", period.body<ErrorResponse>().field)
        assertEquals(HttpStatusCode.BadRequest, metric.status)
        assertEquals("metric", metric.body<ErrorResponse>().field)
    }

    @Test
    fun `Leaderboard braucht eine Anmeldung`() = testApplication {
        val client = startApp(thursday)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/leaderboard").status)
    }
}
