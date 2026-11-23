package de.touchgrass

import de.touchgrass.activities.CreateActivityRequest
import de.touchgrass.activities.CreateActivityResponse
import de.touchgrass.activities.Visibility
import de.touchgrass.auth.JwtService
import de.touchgrass.friends.SendFriendRequest
import de.touchgrass.users.AuthResponse
import de.touchgrass.users.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Clock
import javax.imageio.ImageIO

val testJwtService = JwtService("test-secret")

fun ApplicationTestBuilder.startApp(clock: Clock = Clock.systemUTC()): HttpClient {
    application { module(TestDatabase.database, testJwtService, clock) }
    return createClient { install(ContentNegotiation) { json() } }
}

inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

suspend fun HttpClient.register(
    username: String,
    email: String = "$username@example.com",
    password: String = "geheim123",
): AuthResponse = post("/api/auth/register") { jsonBody(RegisterRequest(username, email, password)) }.body()

fun activity(
    sportCode: String = "RUNNING",
    startedAt: String = "2026-10-05T09:30:00+02:00",
    durationMin: Int = 48,
    visibility: Visibility = Visibility.PRIVATE,
    latitude: Double? = null,
    longitude: Double? = null,
) = CreateActivityRequest(
    sportCode = sportCode,
    startedAt = startedAt,
    durationMin = durationMin,
    latitude = latitude,
    longitude = longitude,
    visibility = visibility,
)

suspend fun HttpClient.createActivity(token: String, request: CreateActivityRequest = activity()): CreateActivityResponse =
    post("/api/activities") { bearerAuth(token); jsonBody(request) }.body()

suspend fun HttpClient.befriend(token: String, username: String, otherToken: String, otherUsername: String) {
    post("/api/friends/requests") { bearerAuth(token); jsonBody(SendFriendRequest(otherUsername)) }
    post("/api/friends/requests/$username/accept") { bearerAuth(otherToken) }
}

fun jpeg(width: Int = 4, height: Int = 3): ByteArray {
    val out = ByteArrayOutputStream()
    check(ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", out))
    return out.toByteArray()
}

suspend fun HttpClient.putPhoto(token: String, id: Long, bytes: ByteArray = jpeg()): HttpResponse =
    put("/api/activities/$id/photo") { bearerAuth(token); contentType(ContentType.Image.JPEG); setBody(bytes) }
