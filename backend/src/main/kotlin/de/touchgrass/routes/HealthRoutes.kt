package de.touchgrass.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Serializable
data class HealthResponse(val status: String, val database: String)

fun Route.healthRoutes(database: Database) {
    get("/health") {
        transaction(database) { exec("SELECT 1") }
        call.respond(HealthResponse(status = "ok", database = "ok"))
    }
}
