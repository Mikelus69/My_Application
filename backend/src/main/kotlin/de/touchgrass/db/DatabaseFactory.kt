package de.touchgrass.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.touchgrass.config.AppConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class AppDatabase(val database: Database, private val resources: List<Closeable>) : Closeable {
    override fun close() = resources.reversed().forEach { it.close() }
}

object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun start(config: AppConfig): AppDatabase {
        if (config.dbUrl != null) {
            val dataSource = pooled(config.dbUrl, config.dbUser ?: "", config.dbPassword ?: "")
            return connect(dataSource, listOf(dataSource))
        }
        val dataDir = Path.of(config.dataDir, "postgres")
        stopLeftoverPostgres(dataDir)
        val embedded = EmbeddedPostgres.builder()
            .setDataDirectory(dataDir)
            .setCleanDataDirectory(false)
            .setPort(config.embeddedDbPort)
            .start()
        val dataSource = pooled(embedded.getJdbcUrl("postgres", "postgres"), "postgres", "postgres")
        return connect(dataSource, listOf(embedded, dataSource))
    }

    fun startForTests(): AppDatabase {
        val embedded = EmbeddedPostgres.start()
        val dataSource = pooled(embedded.getJdbcUrl("postgres", "postgres"), "postgres", "postgres")
        return connect(dataSource, listOf(embedded, dataSource))
    }

    // `gradlew run` beendet den Server hart, PostgreSQL läuft dann weiter. Jeder Start räumt so einen Rest weg.
    // Erst den Hauptprozess beenden, sonst startet er in der Lücke neue Kindprozesse.
    private fun stopLeftoverPostgres(dataDir: Path) {
        val pidFile = dataDir.resolve("postmaster.pid")
        if (!Files.exists(pidFile)) return
        val pid = Files.readAllLines(pidFile).firstOrNull()?.trim()?.toLongOrNull() ?: return
        val postmaster = ProcessHandle.of(pid).orElse(null) ?: return
        val command = postmaster.info().command().orElse("")
        if (!command.endsWith("postgres.exe") && !command.endsWith("/postgres")) return

        log.warn("Stoppe altes PostgreSQL vom letzten Start (PID {})", pid)
        val children = postmaster.descendants().toList()
        postmaster.destroy()
        children.forEach { it.destroy() }
        (children + postmaster).forEach { it.onExit().get(10, TimeUnit.SECONDS) }
    }

    private fun pooled(url: String, user: String, password: String) = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 5
        }
    )

    private fun connect(dataSource: HikariDataSource, resources: List<Closeable>): AppDatabase {
        Flyway.configure().dataSource(dataSource).load().migrate()
        return AppDatabase(Database.connect(dataSource), resources)
    }
}
