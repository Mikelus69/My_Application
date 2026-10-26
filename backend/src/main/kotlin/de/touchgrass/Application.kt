package de.touchgrass

import de.touchgrass.achievements.AchievementRepository
import de.touchgrass.achievements.AchievementService
import de.touchgrass.activities.ActivityRepository
import de.touchgrass.activities.ActivityService
import de.touchgrass.auth.JwtSecret
import de.touchgrass.auth.JwtService
import de.touchgrass.auth.configureSecurity
import de.touchgrass.config.AppConfig
import de.touchgrass.db.DatabaseFactory
import de.touchgrass.plugins.configureSerialization
import de.touchgrass.plugins.configureStatusPages
import de.touchgrass.routes.achievementRoutes
import de.touchgrass.routes.activityRoutes
import de.touchgrass.routes.authRoutes
import de.touchgrass.routes.healthRoutes
import de.touchgrass.routes.meRoutes
import de.touchgrass.routes.sportRoutes
import de.touchgrass.sports.SportRepository
import de.touchgrass.users.UserRepository
import de.touchgrass.users.UserService
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path
import java.time.Clock

fun main() {
    val config = AppConfig.fromEnvironment()
    val appDatabase = DatabaseFactory.start(config)
    Runtime.getRuntime().addShutdownHook(Thread { appDatabase.close() })
    val jwtService = JwtService(JwtSecret.load(config.jwtSecret, Path.of(config.dataDir)))
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(appDatabase.database, jwtService)
    }.start(wait = true)
}

fun Application.module(database: Database, jwtService: JwtService, clock: Clock = Clock.systemUTC()) {
    val userRepository = UserRepository(database)
    val sportRepository = SportRepository(database)
    val activityRepository = ActivityRepository(database)
    val userService = UserService(userRepository, jwtService)
    val achievementService = AchievementService(AchievementRepository(database), activityRepository, clock)
    val activityService = ActivityService(activityRepository, sportRepository, userRepository, achievementService, clock)

    configureSerialization()
    configureStatusPages()
    configureSecurity(jwtService)
    routing {
        route("/api") {
            healthRoutes(database)
            authRoutes(userService)
            meRoutes(userService)
            sportRoutes(sportRepository)
            activityRoutes(activityService)
            achievementRoutes(achievementService)
        }
    }
}
