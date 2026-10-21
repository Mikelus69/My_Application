package de.touchgrass.ui.activities

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActivityDetailViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private open inner class Api : FakeSportApi() {
        var deletedId: Long? = null
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun activity(id: Long): ActivityDto = testActivity(id = id)
        override suspend fun deleteActivity(id: Long) {
            deletedId = id
        }
    }

    @Test
    fun `lädt die Aktivität mit der übergebenen ID`() {
        val viewModel = ActivityDetailViewModel(Api(), id = 7)

        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
    }

    @Test
    fun `Löschen ruft die API auf und meldet deleted`() {
        val api = Api()
        val viewModel = ActivityDetailViewModel(api, id = 7)

        viewModel.delete()

        assertEquals(7L, api.deletedId)
        assertTrue(viewModel.state.value.deleted)
    }

    @Test
    fun `Fehler beim Löschen wird angezeigt`() {
        val api = object : Api() {
            override suspend fun deleteActivity(id: Long): Unit =
                throw httpError(403, """{"error":"FORBIDDEN","message":"Das ist nicht deine Aktivität."}""")
        }
        val viewModel = ActivityDetailViewModel(api, id = 7)

        viewModel.delete()

        assertFalse(viewModel.state.value.deleted)
        assertEquals("Das ist nicht deine Aktivität.", viewModel.state.value.error)
    }
}
