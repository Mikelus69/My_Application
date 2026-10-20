package de.touchgrass.auth

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtSecretTest {
    @Test
    fun `Umgebungsvariable hat Vorrang und es wird keine Datei angelegt`() {
        val dir = createTempDirectory()

        assertEquals("aus-env", JwtSecret.load("aus-env", dir))
        assertFalse(Files.exists(dir.resolve("jwt-secret")))
    }

    @Test
    fun `ohne Umgebungsvariable wird ein Secret erzeugt und beim nächsten Mal wiederverwendet`() {
        val dir = createTempDirectory().resolve("data")

        val first = JwtSecret.load(null, dir)
        val second = JwtSecret.load(null, dir)

        assertTrue(first.length >= 64)
        assertEquals(first, second)
    }
}
