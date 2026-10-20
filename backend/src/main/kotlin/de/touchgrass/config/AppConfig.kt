package de.touchgrass.config

data class AppConfig(
    val port: Int,
    val dataDir: String,
    val embeddedDbPort: Int,
    val dbUrl: String?,
    val dbUser: String?,
    val dbPassword: String?,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()) = AppConfig(
            port = env["PORT"]?.toInt() ?: 8080,
            dataDir = env["DATA_DIR"] ?: "data",
            embeddedDbPort = env["EMBEDDED_DB_PORT"]?.toInt() ?: 5433,
            dbUrl = env["DB_URL"],
            dbUser = env["DB_USER"],
            dbPassword = env["DB_PASSWORD"],
        )
    }
}
