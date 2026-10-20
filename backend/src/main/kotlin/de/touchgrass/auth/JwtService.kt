package de.touchgrass.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.time.Duration
import java.time.Instant

class JwtService(secret: String, private val validity: Duration = Duration.ofDays(30)) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(ISSUER).build()

    fun createToken(userId: Long): String = JWT.create()
        .withIssuer(ISSUER)
        .withSubject(userId.toString())
        .withExpiresAt(Instant.now().plus(validity))
        .sign(algorithm)

    private companion object {
        const val ISSUER = "touchgrass"
    }
}
