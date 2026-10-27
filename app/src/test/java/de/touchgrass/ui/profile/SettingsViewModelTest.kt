package de.touchgrass.ui.profile

import de.touchgrass.data.AuthSession
import de.touchgrass.data.AuthState
import de.touchgrass.data.dto.ChangePasswordRequest
import de.touchgrass.data.dto.GoalType
import de.touchgrass.data.dto.UpdateMeRequest
import de.touchgrass.data.dto.UserDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.FakeTokenStore
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testUser
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class SettingsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val session = AuthSession(FakeTokenStore("token-123"), CoroutineScope(mainRule.dispatcher))

    private open inner class Api(private val user: UserDto = testUser) : FakeSportApi() {
        var sentUpdate: UpdateMeRequest? = null
        override suspend fun me() = user
        override suspend fun updateMe(body: UpdateMeRequest): UserDto {
            sentUpdate = body
            return user.copy(weightKg = body.weightKg, goalType = body.goalType ?: user.goalType, goalTarget = body.goalTarget ?: user.goalTarget)
        }
    }

    @Test
    fun `lädt das Profil in die Felder`() {
        val viewModel = SettingsViewModel(Api(testUser.copy(weightKg = 78.5, goalType = GoalType.ACTIVITIES, goalTarget = 4)), session)

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals("78,5", state.weight)
        assertEquals(GoalType.ACTIVITIES, state.goalType)
        assertEquals("4", state.goalTarget)
    }

    @Test
    fun `Speichern wandelt Komma in Punkt und Ziel in eine Zahl`() {
        val api = Api()
        val viewModel = SettingsViewModel(api, session)

        viewModel.onWeightChange("80,5")
        viewModel.onGoalTargetChange("200")
        viewModel.saveProfile()

        assertEquals(UpdateMeRequest(80.5, GoalType.MINUTES, 200), api.sentUpdate)
        assertEquals("Gespeichert.", viewModel.state.value.message)
    }

    @Test
    fun `leeres Gewicht wird nicht mitgeschickt`() {
        val api = Api()
        val viewModel = SettingsViewModel(api, session)

        viewModel.saveProfile()

        assertNull(api.sentUpdate?.weightKg)
        assertEquals(150, api.sentUpdate?.goalTarget)
    }

    @Test
    fun `Buchstaben im Gewicht ergeben einen Feldfehler ohne Serveraufruf`() {
        val api = Api()
        val viewModel = SettingsViewModel(api, session)

        viewModel.onWeightChange("viel")
        viewModel.saveProfile()

        assertEquals("Bitte eine Zahl eingeben, z. B. 78,5.", viewModel.state.value.fieldErrors["weightKg"])
        assertNull(api.sentUpdate)
    }

    @Test
    fun `falsches altes Passwort erscheint am Feld`() {
        val api = object : Api() {
            override suspend fun changePassword(body: ChangePasswordRequest): Unit = throw httpError(
                400,
                """{"error":"VALIDATION_ERROR","message":"Das bisherige Passwort ist falsch.","field":"oldPassword"}""",
            )
        }
        val viewModel = SettingsViewModel(api, session)

        viewModel.changePassword()

        assertEquals("Das bisherige Passwort ist falsch.", viewModel.state.value.fieldErrors["oldPassword"])
    }

    @Test
    fun `Konto löschen meldet ab`() {
        val api = object : Api() {
            override suspend fun deleteMe() = Unit
        }
        val viewModel = SettingsViewModel(api, session)

        viewModel.deleteAccount()

        assertEquals(AuthState.LoggedOut, session.state.value)
    }

    @Test
    fun `nach einem Ladefehler zeigt Erneut versuchen das Profil`() {
        var calls = 0
        val failingOnce = object : Api() {
            override suspend fun me(): UserDto = if (calls++ == 0) throw IOException("offline") else testUser.copy(goalTarget = 200)
        }
        val viewModel = SettingsViewModel(failingOnce, session)
        assertEquals("Server nicht erreichbar", viewModel.state.value.loadError)

        viewModel.load()

        assertNull(viewModel.state.value.loadError)
        assertEquals("200", viewModel.state.value.goalTarget)
    }
}
