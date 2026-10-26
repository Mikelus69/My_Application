package de.touchgrass

import de.touchgrass.progress.ProgressDto
import de.touchgrass.progress.StreakDto
import de.touchgrass.progress.WeeklyGoalDto
import de.touchgrass.users.GoalType
import de.touchgrass.users.UpdateMeRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressRoutesTest {
    // Donnerstag, 8. Oktober 2026, 12 Uhr Berliner Zeit
    private val thursday = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC)

    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `Wochenziel zählt nur die laufende Woche, die Serie zählt ab gestern`() = testApplication {
        val client = startApp(thursday)
        val token = client.register("max_m").token
        listOf("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-06", "2026-10-07").forEach { day ->
            client.createActivity(token, activity(startedAt = "${day}T08:00:00+02:00", durationMin = 30))
        }

        val progress = client.get("/api/progress") { bearerAuth(token) }.body<ProgressDto>()

        assertEquals(WeeklyGoalDto(GoalType.MINUTES, 150, 60), progress.weeklyGoal)
        assertEquals(StreakDto(current = 2, longest = 4), progress.streak)
        assertEquals(listOf(false, true, true, false, false, false, false), progress.weekDays)
    }

    @Test
    fun `Wochenziel folgt dem gewählten Zieltyp`() = testApplication {
        val client = startApp(thursday)
        val token = client.register("max_m").token
        client.patch("/api/me") { bearerAuth(token); jsonBody(UpdateMeRequest(goalType = GoalType.ACTIVITIES, goalTarget = 4)) }
        client.createActivity(token, activity(startedAt = "2026-10-05T08:00:00+02:00"))
        client.createActivity(token, activity(startedAt = "2026-10-08T08:00:00+02:00"))

        val progress = client.get("/api/progress") { bearerAuth(token) }.body<ProgressDto>()

        assertEquals(WeeklyGoalDto(GoalType.ACTIVITIES, 4, 2), progress.weeklyGoal)
        assertEquals(StreakDto(current = 1, longest = 1), progress.streak)
    }

    @Test
    fun `ohne Aktivitäten steht alles auf null`() = testApplication {
        val client = startApp(thursday)
        val token = client.register("max_m").token

        val progress = client.get("/api/progress") { bearerAuth(token) }.body<ProgressDto>()

        assertEquals(ProgressDto(WeeklyGoalDto(GoalType.MINUTES, 150, 0), StreakDto(0, 0), List(7) { false }), progress)
    }

    @Test
    fun `Fortschritt braucht eine Anmeldung`() = testApplication {
        val client = startApp(thursday)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/progress").status)
    }
}
