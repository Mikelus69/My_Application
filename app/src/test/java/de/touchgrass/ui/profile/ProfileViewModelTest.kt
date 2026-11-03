package de.touchgrass.ui.profile

import de.touchgrass.data.dto.FriendsDto
import de.touchgrass.data.dto.UserDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.testStats
import de.touchgrass.testing.testUser
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ProfileViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `refresh lädt Profil, Zahlen dieser Woche und offene Anfragen`() {
        val viewModel = ProfileViewModel(object : FakeSportApi() {
            override suspend fun me() = testUser
            override suspend fun stats() = testStats
            override suspend fun friends() = FriendsDto(friends = listOf("lisa_k"), incoming = listOf("tom_b", "anna"), outgoing = emptyList())
        })

        viewModel.refresh()

        assertEquals(UiState.Content(ProfileData(testUser, testStats.thisWeek, openRequests = 2)), viewModel.state.value)
    }

    @Test
    fun `ohne Server erscheint eine Fehlermeldung`() {
        val viewModel = ProfileViewModel(object : FakeSportApi() {
            override suspend fun me(): UserDto = throw IOException("offline")
        })

        viewModel.refresh()

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value)
    }

    @Test
    fun `Gewicht wird mit Komma und ohne unnötige Nachkommastelle angezeigt`() {
        assertEquals("78,5", formatWeight(78.5))
        assertEquals("80", formatWeight(80.0))
    }

    @Test
    fun `offene Anfragen stehen nur da, wenn es welche gibt`() {
        assertNull(openRequestsLabel(0))
        assertEquals("1 offene Anfrage", openRequestsLabel(1))
        assertEquals("3 offene Anfragen", openRequestsLabel(3))
    }
}
