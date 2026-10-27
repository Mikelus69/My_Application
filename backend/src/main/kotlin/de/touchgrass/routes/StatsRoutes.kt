package de.touchgrass.routes

import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import de.touchgrass.stats.StatsService
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.statsRoutes(service: StatsService) {
    authenticate(AUTH_JWT) {
        get("/stats") {
            call.respond(service.stats(call.userId()))
        }
    }
}
