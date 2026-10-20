package de.touchgrass

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRoutesTest {
    @Test
    fun `health meldet ok`() = testApplication {
        application { module() }

        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun `unbekannter Pfad liefert einheitliches Fehlerformat`() = testApplication {
        application { module() }

        val response = client.get("/api/gibt-es-nicht")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("""{"error":"NOT_FOUND","message":"Nicht gefunden"}""", response.bodyAsText())
    }

    @Test
    fun `unerwarteter Fehler liefert 500 im einheitlichen Fehlerformat`() = testApplication {
        application {
            module()
            routing { get("/api/kaputt") { error("absichtlich kaputt") } }
        }

        val response = client.get("/api/kaputt")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("""{"error":"INTERNAL_ERROR","message":"Interner Serverfehler"}""", response.bodyAsText())
    }
}
