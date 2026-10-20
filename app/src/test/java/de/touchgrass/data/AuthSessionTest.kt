package de.touchgrass.data

import de.touchgrass.testing.FakeTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionTest {
    private fun session(storedToken: String?) =
        AuthSession(FakeTokenStore(storedToken), CoroutineScope(UnconfinedTestDispatcher()))

    @Test
    fun `gespeicherter Token ergibt LoggedIn`() {
        val session = session("abc")

        assertEquals(AuthState.LoggedIn("abc"), session.state.value)
        assertEquals("abc", session.currentToken())
    }

    @Test
    fun `ohne Token ist man abgemeldet`() {
        val session = session(null)

        assertEquals(AuthState.LoggedOut, session.state.value)
        assertNull(session.currentToken())
    }

    @Test
    fun `logIn und logOut ändern den Zustand`() {
        val session = session(null)

        runBlocking { session.logIn("neu") }
        assertEquals(AuthState.LoggedIn("neu"), session.state.value)

        runBlocking { session.logOut() }
        assertEquals(AuthState.LoggedOut, session.state.value)
    }

    @Test
    fun `onUnauthorized meldet ab`() {
        val session = session("abc")

        session.onUnauthorized()

        assertEquals(AuthState.LoggedOut, session.state.value)
    }
}
