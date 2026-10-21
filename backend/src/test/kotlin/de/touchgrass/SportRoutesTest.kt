package de.touchgrass

import de.touchgrass.sports.SportDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SportRoutesTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `sports liefert die acht Sportarten alphabetisch`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.get("/api/sports") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val sports = response.body<List<SportDto>>()
        assertEquals(8, sports.size)
        assertEquals("Basketball", sports.first().name)
        assertEquals(SportDto("RUNNING", "Joggen", "🏃", 8.0), sports.single { it.code == "RUNNING" })
    }

    @Test
    fun `sports braucht einen Token`() = testApplication {
        val client = startApp()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/sports").status)
    }
}
