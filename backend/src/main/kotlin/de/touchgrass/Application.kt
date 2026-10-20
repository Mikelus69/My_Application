package de.touchgrass

import de.touchgrass.plugins.configureSerialization
import de.touchgrass.plugins.configureStatusPages
import de.touchgrass.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureStatusPages()
    routing {
        route("/api") {
            healthRoutes()
        }
    }
}
