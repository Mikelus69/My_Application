package de.touchgrass.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.touchgrass.config.AppConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import java.io.Closeable
import java.nio.file.Path

class AppDatabase(val database: Database, private val resources: List<Closeable>) : Closeable {
    override fun close() = resources.reversed().forEach { it.close() }
}

object DatabaseFactory {
    fun start(config: AppConfig): AppDatabase {
        if (config.dbUrl != null) {
            val dataSource = pooled(config.dbUrl, config.dbUser ?: "", config.dbPassword ?: "")
            return connect(dataSource, listOf(dataSource))
        }
        val embedded = EmbeddedPostgres.builder()
            .setDataDirectory(Path.of(config.dataDir, "postgres"))
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
