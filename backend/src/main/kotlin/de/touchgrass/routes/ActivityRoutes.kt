package de.touchgrass.routes

import de.touchgrass.activities.ActivityService
import de.touchgrass.activities.CreateActivityRequest
import de.touchgrass.activities.PhotoRules
import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import de.touchgrass.plugins.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray

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
            route("/{id}/photo") {
                put {
                    call.respond(service.uploadPhoto(call.userId(), call.activityId(), call.receivePhoto()))
                }
                delete {
                    service.deletePhoto(call.userId(), call.activityId())
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
        get("/map/activities") {
            call.respond(service.map(call.userId()))
        }
    }
}

private fun ApplicationCall.activityId(): Long =
    parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Aktivität nicht gefunden.")

private suspend fun ApplicationCall.receivePhoto(): ByteArray {
    if ((request.contentLength() ?: 0L) > PhotoRules.MAX_BYTES) PhotoRules.tooLarge()
    val bytes = receiveChannel().readBuffer(PhotoRules.MAX_BYTES + 1L).readByteArray()
    if (bytes.size > PhotoRules.MAX_BYTES) PhotoRules.tooLarge()
    return bytes
}
