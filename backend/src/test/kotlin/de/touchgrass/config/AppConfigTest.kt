package de.touchgrass.config

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppConfigTest {
    private val neonLine = "postgresql://neondb_owner:npg_AbC123@ep-cool-darkness-123456.eu-central-1.aws.neon.tech/neondb" +
        "?sslmode=require&channel_binding=require"
    private val neonJdbcUrl = "jdbc:postgresql://ep-cool-darkness-123456.eu-central-1.aws.neon.tech/neondb" +
        "?sslmode=require&channelBinding=require"

    @Test
    fun `DB_URL nimmt die Zeile von Neon so, wie Neon sie zeigt`() {
        val config = AppConfig.fromEnvironment(
            mapOf("DB_URL" to " $neonLine\n", "DB_USER" to "nils@example.org", "DB_PASSWORD" to "falsch"),
        )

        assertEquals(neonJdbcUrl, config.dbUrl)
        assertEquals("neondb_owner", config.dbUser)
        assertEquals("npg_AbC123", config.dbPassword)
    }

    @Test
    fun `mit eingeschaltetem Connection pooling verbindet sich das Backend trotzdem direkt`() {
        val config = AppConfig.fromEnvironment(mapOf("DB_URL" to neonLine.replace("-123456.", "-123456-pooler.")))

        assertEquals(neonJdbcUrl, config.dbUrl)
    }

    @Test
    fun `aus dem env-Schnipsel von Neon zählt nur die Zeile`() {
        val config = AppConfig.fromEnvironment(mapOf("DB_URL" to "DATABASE_URL='$neonLine'"))

        assertEquals(neonJdbcUrl, config.dbUrl)
        assertEquals("npg_AbC123", config.dbPassword)
    }

    @Test
    fun `Port und Sonderzeichen im Passwort bleiben erhalten`() {
        val config = AppConfig.fromEnvironment(mapOf("DB_URL" to "postgres://anna:p%40ss%3Aw+rd@db.example.org:6543/sport"))

        assertEquals("jdbc:postgresql://db.example.org:6543/sport", config.dbUrl)
        assertEquals("anna", config.dbUser)
        assertEquals("p@ss:w+rd", config.dbPassword)
    }

    @Test
    fun `verdecktes Passwort bricht mit einem Hinweis ab`() {
        val error = assertFailsWith<IllegalArgumentException> {
            AppConfig.fromEnvironment(mapOf("DB_URL" to neonLine.replace("npg_AbC123", "****************")))
        }

        assertContains(error.message.orEmpty(), "einblenden")
    }

    @Test
    fun `DB_URL im JDBC-Format nimmt Benutzer und Passwort aus DB_USER und DB_PASSWORD`() {
        val config = AppConfig.fromEnvironment(
            mapOf("DB_URL" to "jdbc:postgresql://localhost:5432/touchgrass", "DB_USER" to "postgres", "DB_PASSWORD" to "geheim"),
        )

        assertEquals("jdbc:postgresql://localhost:5432/touchgrass", config.dbUrl)
        assertEquals("postgres", config.dbUser)
        assertEquals("geheim", config.dbPassword)
    }

    @Test
    fun `ohne DB_URL startet das eingebaute PostgreSQL`() {
        assertNull(AppConfig.fromEnvironment(emptyMap()).dbUrl)
    }
}
