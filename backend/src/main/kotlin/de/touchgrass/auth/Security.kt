package de.touchgrass.auth

import de.touchgrass.plugins.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

const val AUTH_JWT = "auth-jwt"

data class UserPrincipal(val userId: Long)

fun Application.configureSecurity(jwtService: JwtService) {
    install(Authentication) {
        jwt(AUTH_JWT) {
            verifier(jwtService.verifier)
            validate { credential -> credential.payload.subject?.toLongOrNull()?.let { UserPrincipal(it) } }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "Bitte melde dich an."))
            }
        }
    }
}

fun ApplicationCall.userId(): Long = principal<UserPrincipal>()!!.userId
