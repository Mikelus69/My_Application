package de.touchgrass

import de.touchgrass.config.AppConfig
import de.touchgrass.db.DatabaseFactory
import de.touchgrass.plugins.configureSerialization
import de.touchgrass.plugins.configureStatusPages
import de.touchgrass.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database

fun main() {
    val config = AppConfig.fromEnvironment()
    val appDatabase = DatabaseFactory.start(config)
    Runtime.getRuntime().addShutdownHook(Thread { appDatabase.close() })
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(appDatabase.database)
    }.start(wait = true)
}

fun Application.module(database: Database) {
    configureSerialization()
    configureStatusPages()
    routing {
        route("/api") {
            healthRoutes(database)
        }
    }
}
