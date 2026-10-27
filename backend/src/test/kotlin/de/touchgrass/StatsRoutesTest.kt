package de.touchgrass

import de.touchgrass.stats.MonthKcalDto
import de.touchgrass.stats.SportCountDto
import de.touchgrass.stats.StatsDto
import de.touchgrass.stats.WeekMinutesDto
import de.touchgrass.stats.WeekSummaryDto
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

class StatsRoutesTest {
    // Donnerstag, 8. Oktober 2026, 12 Uhr Berliner Zeit
    private val thursday = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC)

    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `Statistik fasst Woche, acht Wochen, Sportarten und sechs Monate zusammen`() = testApplication {
        val client = startApp(thursday)
        val token = client.register("max_m").token
        client.createActivity(token, activity(sportCode = "CYCLING", startedAt = "2026-09-01T08:00:00+02:00", durationMin = 60))
        client.createActivity(token, activity(startedAt = "2026-10-06T08:00:00+02:00", durationMin = 30))
        client.createActivity(token, activity(startedAt = "2026-10-08T08:00:00+02:00", durationMin = 15))

        val stats = client.get("/api/stats") { bearerAuth(token) }.body<StatsDto>()

        assertEquals(WeekSummaryDto(activities = 2, minutes = 45, kcal = 420), stats.thisWeek)
        assertEquals(
            listOf("2026-08-17", "2026-08-24", "2026-08-31", "2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28", "2026-10-05"),
            stats.minutesPerWeek.map { it.weekStart },
        )
        assertEquals(listOf(0, 0, 60, 0, 0, 0, 0, 45), stats.minutesPerWeek.map { it.minutes })
        assertEquals(listOf(SportCountDto("RUNNING", 2), SportCountDto("CYCLING", 1)), stats.activitiesPerSport)
        assertEquals(
            listOf(
                MonthKcalDto("2026-05", 0),
                MonthKcalDto("2026-06", 0),
                MonthKcalDto("2026-07", 0),
                MonthKcalDto("2026-08", 0),
                MonthKcalDto("2026-09", 490),
                MonthKcalDto("2026-10", 420),
            ),
            stats.kcalPerMonth,
        )
    }

    @Test
    fun `ohne Aktivitäten gibt es Nullen und keine Sportarten`() = testApplication {
        val client = startApp(thursday)
        val token = client.register("max_m").token

        val stats = client.get("/api/stats") { bearerAuth(token) }.body<StatsDto>()

        assertEquals(WeekSummaryDto(0, 0, 0), stats.thisWeek)
        assertEquals(List(8) { 0 }, stats.minutesPerWeek.map { it.minutes })
        assertEquals(WeekMinutesDto("2026-10-05", 0), stats.minutesPerWeek.last())
        assertEquals(emptyList(), stats.activitiesPerSport)
        assertEquals(List(6) { 0 }, stats.kcalPerMonth.map { it.kcal })
    }

    @Test
    fun `Statistik braucht eine Anmeldung`() = testApplication {
        val client = startApp(thursday)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/stats").status)
    }
}
