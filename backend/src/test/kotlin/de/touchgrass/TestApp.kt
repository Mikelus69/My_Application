package de.touchgrass

import de.touchgrass.auth.JwtService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder

val testJwtService = JwtService("test-secret")

fun ApplicationTestBuilder.startApp(): HttpClient {
    application { module(TestDatabase.database, testJwtService) }
    return createClient { install(ContentNegotiation) { json() } }
}

inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
