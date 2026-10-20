package de.touchgrass.ui.auth

import de.touchgrass.data.AuthSession
import de.touchgrass.data.AuthState
import de.touchgrass.data.dto.AuthResponse
import de.touchgrass.data.dto.LoginRequest
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.FakeTokenStore
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testUser
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val session = AuthSession(FakeTokenStore(), CoroutineScope(mainRule.dispatcher))

    @Test
    fun `erfolgreicher Login speichert den Token`() {
        var sent: LoginRequest? = null
        val api = object : FakeSportApi() {
            override suspend fun login(body: LoginRequest): AuthResponse {
                sent = body
                return AuthResponse("token-123", testUser)
            }
        }
        val viewModel = LoginViewModel(api, session)

        viewModel.onLoginChange(" max_m ")
        viewModel.onPasswordChange("geheim123")
        viewModel.submit()

        assertEquals(LoginRequest("max_m", "geheim123"), sent)
        assertEquals(AuthState.LoggedIn("token-123"), session.state.value)
    }

    @Test
    fun `falsches Passwort zeigt die Meldung vom Server`() {
        val api = object : FakeSportApi() {
            override suspend fun login(body: LoginRequest): AuthResponse =
                throw httpError(401, """{"error":"INVALID_CREDENTIALS","message":"Benutzername/E-Mail oder Passwort ist falsch."}""")
        }
        val viewModel = LoginViewModel(api, session)

        viewModel.submit()

        assertEquals("Benutzername/E-Mail oder Passwort ist falsch.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
        assertEquals(AuthState.LoggedOut, session.state.value)
    }
}
