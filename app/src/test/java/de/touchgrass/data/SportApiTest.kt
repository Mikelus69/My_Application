package de.touchgrass.data

import de.touchgrass.data.dto.HealthDto
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SportApiTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `health ruft api-health auf und ignoriert unbekannte Felder`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","database":"ok","version":"spaeter"}""")
        )
        val api = createSportApi(server.url("/api/").toString())

        val health = api.health()

        assertEquals(HealthDto(status = "ok", database = "ok"), health)
        assertEquals("/api/health", server.takeRequest().path)
    }
}
