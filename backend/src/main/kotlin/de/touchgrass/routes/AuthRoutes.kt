package de.touchgrass.routes

import de.touchgrass.users.LoginRequest
import de.touchgrass.users.RegisterRequest
import de.touchgrass.users.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(userService: UserService) {
    route("/auth") {
        post("/register") {
            call.respond(HttpStatusCode.Created, userService.register(call.receive<RegisterRequest>()))
        }
        post("/login") {
            call.respond(userService.login(call.receive<LoginRequest>()))
        }
    }
}
