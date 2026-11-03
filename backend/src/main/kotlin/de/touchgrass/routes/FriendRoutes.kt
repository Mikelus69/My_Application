package de.touchgrass.routes

import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import de.touchgrass.friends.FriendService
import de.touchgrass.friends.SendFriendRequest
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

fun Route.friendRoutes(service: FriendService) {
    authenticate(AUTH_JWT) {
        route("/friends") {
            get {
                call.respond(service.overview(call.userId()))
            }
            post("/requests") {
                call.respond(HttpStatusCode.Created, service.request(call.userId(), call.receive<SendFriendRequest>().username))
            }
            post("/requests/{username}/accept") {
                call.respond(service.accept(call.userId(), call.username()))
            }
            delete("/requests/{username}") {
                call.respond(service.deleteRequest(call.userId(), call.username()))
            }
            delete("/{username}") {
                call.respond(service.remove(call.userId(), call.username()))
            }
        }
    }
}

private fun ApplicationCall.username(): String = parameters["username"].orEmpty()
