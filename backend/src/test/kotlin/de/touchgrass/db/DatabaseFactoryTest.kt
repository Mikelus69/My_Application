package de.touchgrass.db

import de.touchgrass.config.AppConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DatabaseFactoryTest {
    @Test
    fun `zweiter Start bricht ab, solange ein laufendes Backend den Datenordner sperrt`() {
        val dataDir = Files.createTempDirectory("touchgrass-db")
        val lockFile = dataDir.resolve("epg-lock")

        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                assertFailsWith<BackendAlreadyRunning> { DatabaseFactory.ensureNotRunning(dataDir) }
            }
        }

        DatabaseFactory.ensureNotRunning(dataDir)
    }

    @Test
    fun `erster Start ohne Datenordner läuft durch`() {
        DatabaseFactory.ensureNotRunning(Files.createTempDirectory("touchgrass-db").resolve("postgres"))
    }

    @Test
    fun `mit DB_URL nutzt das Backend diese Datenbank und startet kein eigenes PostgreSQL`() {
        EmbeddedPostgres.start().use { cloud ->
            val dataDir = Files.createTempDirectory("touchgrass-data")
            val config = AppConfig(
                port = 8080,
                dataDir = dataDir.toString(),
                embeddedDbPort = 5499,
                dbUrl = cloud.getJdbcUrl("postgres", "postgres"),
                dbUser = "postgres",
                dbPassword = "postgres",
                jwtSecret = null,
            )

            DatabaseFactory.start(config).use { appDatabase ->
                val sports = transaction(appDatabase.database) {
                    exec("SELECT COUNT(*) FROM sport_types") { rs -> rs.next(); rs.getInt(1) }
                }
                assertEquals(11, sports)
            }
            assertFalse(Files.exists(dataDir.resolve("postgres")))
        }
    }
}
