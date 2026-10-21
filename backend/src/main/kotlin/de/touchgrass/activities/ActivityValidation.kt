package de.touchgrass.activities

import de.touchgrass.plugins.validationError
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

object ActivityValidation {
    fun startedAt(value: String, now: OffsetDateTime): OffsetDateTime {
        val parsed = try {
            OffsetDateTime.parse(value)
        } catch (e: DateTimeParseException) {
            validationError("startedAt", "Bitte Datum und Uhrzeit angeben.")
        }
        if (parsed.isAfter(now.plusMinutes(5))) {
            validationError("startedAt", "Die Aktivität darf nicht in der Zukunft liegen.")
        }
        return parsed
    }

    fun durationMin(value: Int) {
        if (value !in 1..1440) {
            validationError("durationMin", "Die Dauer muss zwischen 1 und 1440 Minuten liegen.")
        }
    }

    fun location(latitude: Double?, longitude: Double?) {
        if ((latitude == null) != (longitude == null)) {
            validationError("latitude", "Breite und Länge nur zusammen angeben.")
        }
        if (latitude != null && latitude !in -90.0..90.0) {
            validationError("latitude", "Die Breite muss zwischen -90 und 90 liegen.")
        }
        if (longitude != null && longitude !in -180.0..180.0) {
            validationError("longitude", "Die Länge muss zwischen -180 und 180 liegen.")
        }
    }

    fun description(value: String?) {
        if (value != null && value.length > 500) {
            validationError("description", "Die Beschreibung darf höchstens 500 Zeichen haben.")
        }
    }

    fun dateFilter(value: String?, field: String): LocalDate? = value?.let {
        try {
            LocalDate.parse(it)
        } catch (e: DateTimeParseException) {
            validationError(field, "Datum bitte im Format JJJJ-MM-TT angeben.")
        }
    }
}
