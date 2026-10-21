package de.touchgrass.routes

import de.touchgrass.activities.ActivityService
import de.touchgrass.activities.CreateActivityRequest
import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import de.touchgrass.plugins.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.activityRoutes(service: ActivityService) {
    authenticate(AUTH_JWT) {
        route("/activities") {
            post {
                call.respond(HttpStatusCode.Created, service.create(call.userId(), call.receive<CreateActivityRequest>()))
            }
            get {
                val query = call.request.queryParameters
                call.respond(service.list(call.userId(), query["sport"], query["from"], query["to"]))
            }
            get("/{id}") {
                call.respond(service.get(call.userId(), call.activityId()))
            }
            delete("/{id}") {
                service.delete(call.userId(), call.activityId())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ApplicationCall.activityId(): Long =
    parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Aktivität nicht gefunden.")
