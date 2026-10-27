package de.touchgrass.db

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
