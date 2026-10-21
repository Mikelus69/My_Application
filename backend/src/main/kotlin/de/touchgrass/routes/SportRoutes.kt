package de.touchgrass.routes

import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.sports.SportRepository
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.sportRoutes(sports: SportRepository) {
    authenticate(AUTH_JWT) {
        get("/sports") {
            call.respond(sports.all())
        }
    }
}
