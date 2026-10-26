package de.touchgrass.routes

import de.touchgrass.achievements.AchievementService
import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.achievementRoutes(service: AchievementService) {
    authenticate(AUTH_JWT) {
        get("/achievements") {
            call.respond(service.list(call.userId()))
        }
    }
}
