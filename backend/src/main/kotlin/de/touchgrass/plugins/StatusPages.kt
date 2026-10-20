package de.touchgrass.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val message: String, val field: String? = null)

class ApiException(
    val status: HttpStatusCode,
    val error: String,
    override val message: String,
    val field: String? = null,
) : RuntimeException(message)

fun validationError(field: String, message: String): Nothing =
    throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", message, field)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.error, cause.message, cause.field))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Ungültige Anfrage."))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unerwarteter Fehler", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "Interner Serverfehler"))
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorResponse("NOT_FOUND", "Nicht gefunden"))
        }
    }
}
