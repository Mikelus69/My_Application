package de.touchgrass.routes

import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import de.touchgrass.progress.LeaderboardService
import de.touchgrass.progress.ProgressService
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.progressRoutes(progress: ProgressService, leaderboard: LeaderboardService) {
    authenticate(AUTH_JWT) {
        get("/progress") {
            call.respond(progress.progress(call.userId()))
        }
        get("/leaderboard") {
            val query = call.request.queryParameters
            call.respond(leaderboard.leaderboard(call.userId(), query["period"], query["metric"]))
        }
    }
}
