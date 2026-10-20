package de.touchgrass.routes

import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import de.touchgrass.users.ChangePasswordRequest
import de.touchgrass.users.UpdateMeRequest
import de.touchgrass.users.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.meRoutes(userService: UserService) {
    authenticate(AUTH_JWT) {
        route("/me") {
            get {
                call.respond(userService.me(call.userId()))
            }
            patch {
                call.respond(userService.updateMe(call.userId(), call.receive<UpdateMeRequest>()))
            }
            delete {
                userService.deleteMe(call.userId())
                call.respond(HttpStatusCode.NoContent)
            }
            put("/password") {
                userService.changePassword(call.userId(), call.receive<ChangePasswordRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
