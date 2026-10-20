package de.touchgrass.ui.profile

import de.touchgrass.data.dto.UserDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.testUser
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ProfileViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `refresh lädt das Profil`() {
        val viewModel = ProfileViewModel(object : FakeSportApi() {
            override suspend fun me() = testUser
        })

        viewModel.refresh()

        assertEquals(UiState.Content(testUser), viewModel.state.value)
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
}
