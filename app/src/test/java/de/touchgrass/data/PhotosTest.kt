package de.touchgrass.data

import de.touchgrass.testing.testActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotosTest {
    @Test
    fun `photoUrl hängt die Versionsnummer an die Adresse des Fotos`() {
        val activity = testActivity(id = 7).copy(photoVersion = 3)

        assertEquals("https://example.org/api/activities/7/photo?v=3", activity.photoUrl("https://example.org/api/"))
    }

    @Test
    fun `ohne photoVersion gibt es keine Adresse`() {
        assertNull(testActivity(id = 7).photoUrl("https://example.org/api/"))
    }
}
