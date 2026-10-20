package de.touchgrass.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {
    @Test
    fun `Hash passt zum Passwort, aber nicht zu einem anderen`() {
        val hash = PasswordHasher.hash("geheim123")

        assertNotEquals("geheim123", hash)
        assertTrue(PasswordHasher.verify("geheim123", hash))
        assertFalse(PasswordHasher.verify("geheim124", hash))
    }
}
