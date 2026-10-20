package de.touchgrass.data

import de.touchgrass.testing.FakeTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthInterceptorTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun session(storedToken: String?) =
        AuthSession(FakeTokenStore(storedToken), CoroutineScope(UnconfinedTestDispatcher()))

    private fun apiFor(session: AuthSession) = createSportApi(
        server.url("/api/").toString(),
        OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).build(),
    )

    private val healthJson = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"status":"ok","database":"ok"}""")

    @Test
    fun `angemeldet wird der Token mitgeschickt`() = runBlocking {
        server.enqueue(healthJson)

        apiFor(session("abc")).health()

        assertEquals("Bearer abc", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `abgemeldet wird kein Token mitgeschickt`() = runBlocking {
        server.enqueue(healthJson)

        apiFor(session(null)).health()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `401 mit Token meldet ab`() = runBlocking {
        val session = session("abgelaufen")
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"UNAUTHORIZED","message":"Bitte melde dich an."}"""))

        runCatching { apiFor(session).me() }

        assertEquals(AuthState.LoggedOut, session.state.value)
    }
}
