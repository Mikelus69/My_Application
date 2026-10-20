package de.touchgrass.ui.auth

import de.touchgrass.data.AuthSession
import de.touchgrass.data.AuthState
import de.touchgrass.data.dto.AuthResponse
import de.touchgrass.data.dto.RegisterRequest
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.FakeTokenStore
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testUser
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RegisterViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val session = AuthSession(FakeTokenStore(), CoroutineScope(mainRule.dispatcher))

    @Test
    fun `erfolgreiche Registrierung meldet an`() {
        val api = object : FakeSportApi() {
            override suspend fun register(body: RegisterRequest) = AuthResponse("token-123", testUser)
        }
        val viewModel = RegisterViewModel(api, session)

        viewModel.onUsernameChange("max_m")
        viewModel.onEmailChange("max@example.com")
        viewModel.onPasswordChange("geheim123")
        viewModel.submit()

        assertEquals(AuthState.LoggedIn("token-123"), session.state.value)
    }

    @Test
    fun `Feldfehler vom Server erscheint am Feld und verschwindet beim Tippen`() {
        val api = object : FakeSportApi() {
            override suspend fun register(body: RegisterRequest): AuthResponse = throw httpError(
                409,
                """{"error":"USERNAME_TAKEN","message":"Dieser Benutzername ist schon vergeben.","field":"username"}""",
            )
        }
        val viewModel = RegisterViewModel(api, session)

        viewModel.submit()
        assertEquals(mapOf("username" to "Dieser Benutzername ist schon vergeben."), viewModel.state.value.fieldErrors)

        viewModel.onUsernameChange("max_neu")
        assertEquals(emptyMap<String, String>(), viewModel.state.value.fieldErrors)
    }
}
