package de.touchgrass.auth

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64

object JwtSecret {
    fun load(fromEnvironment: String?, dataDir: Path): String {
        if (!fromEnvironment.isNullOrBlank()) return fromEnvironment
        val file = dataDir.resolve("jwt-secret")
        if (Files.exists(file)) return Files.readString(file).trim()
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        val secret = Base64.getEncoder().encodeToString(bytes)
        Files.createDirectories(dataDir)
        Files.writeString(file, secret)
        return secret
    }
}
