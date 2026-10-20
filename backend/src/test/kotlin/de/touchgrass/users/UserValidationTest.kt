package de.touchgrass.users

import de.touchgrass.plugins.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserValidationTest {
    private fun failingField(block: () -> Unit): String? = assertFailsWith<ApiException> { block() }.field

    @Test
    fun `gültige Eingaben werden akzeptiert`() {
        UserValidation.username("max_m")
        UserValidation.email("max@example.com")
        UserValidation.password("geheim123")
        UserValidation.weight(78.5)
        UserValidation.goalTarget(150)
    }

    @Test
    fun `Benutzername mit Leerzeichen, zu kurz oder zu lang wird abgelehnt`() {
        assertEquals("username", failingField { UserValidation.username("max m") })
        assertEquals("username", failingField { UserValidation.username("mx") })
        assertEquals("username", failingField { UserValidation.username("a".repeat(21)) })
    }

    @Test
    fun `E-Mail ohne At-Zeichen oder Punkt wird abgelehnt`() {
        assertEquals("email", failingField { UserValidation.email("max.example.com") })
        assertEquals("email", failingField { UserValidation.email("max@example") })
    }

    @Test
    fun `Passwort muss 8 bis 64 Zeichen haben`() {
        assertEquals("password", failingField { UserValidation.password("kurz") })
        assertEquals("password", failingField { UserValidation.password("x".repeat(65)) })
        assertEquals("newPassword", failingField { UserValidation.password("kurz", field = "newPassword") })
    }

    @Test
    fun `Gewicht und Wochenziel haben Grenzen`() {
        assertEquals("weightKg", failingField { UserValidation.weight(29.9) })
        assertEquals("weightKg", failingField { UserValidation.weight(300.1) })
        assertEquals("goalTarget", failingField { UserValidation.goalTarget(0) })
    }
}
