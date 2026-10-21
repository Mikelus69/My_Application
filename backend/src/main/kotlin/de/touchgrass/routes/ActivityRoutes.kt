package de.touchgrass.routes

import de.touchgrass.activities.ActivityService
import de.touchgrass.activities.CreateActivityRequest
import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.activityRoutes(service: ActivityService) {
    authenticate(AUTH_JWT) {
        route("/activities") {
            post {
                call.respond(HttpStatusCode.Created, service.create(call.userId(), call.receive<CreateActivityRequest>()))
            }
        }
    }
}
