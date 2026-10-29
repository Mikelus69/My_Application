package de.touchgrass.config

import java.net.URLDecoder

data class AppConfig(
    val port: Int,
    val dataDir: String,
    val embeddedDbPort: Int,
    val dbUrl: String?,
    val dbUser: String?,
    val dbPassword: String?,
    val jwtSecret: String?,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            val db = DbSettings.of(env["DB_URL"]?.trim(), env["DB_USER"], env["DB_PASSWORD"])
            return AppConfig(
                port = env["PORT"]?.toInt() ?: 8080,
                dataDir = env["DATA_DIR"] ?: "data",
                embeddedDbPort = env["EMBEDDED_DB_PORT"]?.toInt() ?: 5433,
                dbUrl = db.url,
                dbUser = db.user,
                dbPassword = db.password,
                jwtSecret = env["JWT_SECRET"],
            )
        }
    }
}

// Neon zeigt die Zugangsdaten als eine Zeile: postgresql://Benutzer:Passwort@Host/Datenbank?…
// DB_URL nimmt diese Zeile unverändert, damit niemand sie von Hand zerlegen muss.
private class DbSettings(val url: String?, val user: String?, val password: String?) {
    companion object {
        private val line = Regex("""postgres(?:ql)?://(?:([^:@/]*)(?::([^@/]*))?@)?([^/?\s'"]+)(/[^?\s'"]*)?(\?[^\s'"]*)?""")

        fun of(url: String?, user: String?, password: String?): DbSettings {
            val match = url?.takeUnless { it.startsWith("jdbc:") }?.let { line.find(it) }
                ?: return DbSettings(url, user, password)
            val (lineUser, linePassword, host, database, query) = match.destructured
            require(!linePassword.matches(Regex("""\*+"""))) {
                "In DB_URL steht statt des Passworts nur ****. Bei Neon unter Connect erst das Passwort einblenden, dann die Zeile kopieren."
            }
            // Für Migrationen rät Neon zur direkten Verbindung, nicht zum Pooler.
            val directHost = if (host.endsWith(".neon.tech")) host.replace("-pooler.", ".") else host
            return DbSettings(
                url = "jdbc:postgresql://$directHost${database.ifEmpty { "/" }}${query.replace("channel_binding=", "channelBinding=")}",
                user = lineUser.ifEmpty { null }?.let(::decode) ?: user,
                password = linePassword.ifEmpty { null }?.let(::decode) ?: password,
            )
        }

        private fun decode(part: String) = URLDecoder.decode(part.replace("+", "%2B"), Charsets.UTF_8)
    }
}
