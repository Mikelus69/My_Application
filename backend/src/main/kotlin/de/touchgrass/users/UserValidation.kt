package de.touchgrass.users

import de.touchgrass.plugins.validationError

object UserValidation {
    private val usernamePattern = Regex("^[A-Za-z0-9_]{3,20}$")
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun username(value: String) {
        if (!usernamePattern.matches(value)) {
            validationError("username", "Der Benutzername muss 3–20 Zeichen lang sein und darf nur Buchstaben, Ziffern und _ enthalten.")
        }
    }

    fun email(value: String) {
        if (value.length > 254 || !emailPattern.matches(value)) {
            validationError("email", "Bitte gib eine gültige E-Mail-Adresse ein.")
        }
    }

    fun password(value: String, field: String = "password") {
        if (value.length !in 8..64) {
            validationError(field, "Das Passwort muss 8–64 Zeichen lang sein.")
        }
    }

    fun weight(value: Double) {
        if (value !in 30.0..300.0) {
            validationError("weightKg", "Das Gewicht muss zwischen 30 und 300 kg liegen.")
        }
    }

    fun goalTarget(value: Int) {
        if (value <= 0) {
            validationError("goalTarget", "Das Wochenziel muss größer als 0 sein.")
        }
    }
}
