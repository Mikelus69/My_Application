package de.touchgrass.ui.friends

import de.touchgrass.data.dto.FriendsDto
import de.touchgrass.data.dto.SendFriendRequest
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class FriendsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val nobody = FriendsDto(friends = emptyList(), incoming = emptyList(), outgoing = emptyList())

    private open inner class Api(val answer: FriendsDto = nobody) : FakeSportApi() {
        val calls = mutableListOf<String>()
        override suspend fun friends(): FriendsDto = answer
        override suspend fun sendFriendRequest(body: SendFriendRequest): FriendsDto = answer.also { calls += "send ${body.username}" }
        override suspend fun acceptFriendRequest(username: String): FriendsDto = answer.also { calls += "accept $username" }
        override suspend fun deleteFriendRequest(username: String): FriendsDto = answer.also { calls += "delete $username" }
        override suspend fun removeFriend(username: String): FriendsDto = answer.also { calls += "remove $username" }
    }

    @Test
    fun `lädt Freunde und Anfragen`() {
        val answer = nobody.copy(friends = listOf("lisa_k"), incoming = listOf("tom_b"))
        val viewModel = FriendsViewModel(Api(answer))

        viewModel.refresh()

        assertEquals(UiState.Content(answer), viewModel.state.value.friends)
    }

    @Test
    fun `Anfrage senden bestätigt den Namen und leert das Feld`() {
        val api = Api(nobody.copy(outgoing = listOf("lisa_k")))
        val viewModel = FriendsViewModel(api)

        viewModel.onUsernameChange(" LISA_K ")
        viewModel.sendRequest()

        val state = viewModel.state.value
        assertEquals(listOf("send LISA_K"), api.calls)
        assertEquals("Anfrage an lisa_k gesendet.", state.message)
        assertEquals("", state.username)
        assertEquals(UiState.Content(api.answer), state.friends)
    }

    @Test
    fun `bei Gegenanfrage meldet die App die neue Freundschaft`() {
        val viewModel = FriendsViewModel(Api(nobody.copy(friends = listOf("lisa_k"))))

        viewModel.onUsernameChange("lisa_k")
        viewModel.sendRequest()

        assertEquals("Du und lisa_k seid jetzt Freunde.", viewModel.state.value.message)
    }

    @Test
    fun `leeres Feld meldet die App selbst`() {
        val api = Api()
        val viewModel = FriendsViewModel(api)

        viewModel.onUsernameChange("   ")
        viewModel.sendRequest()

        assertEquals("Bitte einen Benutzernamen eingeben.", viewModel.state.value.usernameError)
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun `Fehler der API steht am Feld und verschwindet beim Tippen`() {
        val viewModel = FriendsViewModel(object : Api() {
            override suspend fun sendFriendRequest(body: SendFriendRequest): FriendsDto =
                throw httpError(404, """{"error":"USER_NOT_FOUND","message":"Diesen Benutzernamen gibt es nicht.","field":"username"}""")
        })

        viewModel.onUsernameChange("niemand")
        viewModel.sendRequest()
        val error = viewModel.state.value.usernameError
        viewModel.onUsernameChange("niemand2")

        assertEquals("Diesen Benutzernamen gibt es nicht.", error)
        assertNull(viewModel.state.value.usernameError)
    }

    @Test
    fun `Annehmen, Ablehnen und Entfernen schicken den Namen`() {
        val api = Api(nobody.copy(friends = listOf("tom_b")))
        val viewModel = FriendsViewModel(api)

        viewModel.accept("tom_b")
        val message = viewModel.state.value.message
        viewModel.deleteRequest("anna")
        viewModel.remove("lisa_k")

        assertEquals(listOf("accept tom_b", "delete anna", "remove lisa_k"), api.calls)
        assertEquals("Du und tom_b seid jetzt Freunde.", message)
        assertEquals(UiState.Content(api.answer), viewModel.state.value.friends)
    }

    @Test
    fun `ohne Server erscheint eine Fehlermeldung`() {
        val viewModel = FriendsViewModel(object : Api() {
            override suspend fun friends(): FriendsDto = throw IOException("offline")
        })

        viewModel.refresh()

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value.friends)
    }
}
