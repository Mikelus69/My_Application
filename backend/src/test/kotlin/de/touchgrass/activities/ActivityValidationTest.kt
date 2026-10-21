package de.touchgrass.activities

import de.touchgrass.plugins.ApiException
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ActivityValidationTest {
    private val now = OffsetDateTime.parse("2026-10-05T12:00:00+02:00")

    private fun failingField(block: () -> Unit): String? = assertFailsWith<ApiException> { block() }.field

    @Test
    fun `Startzeit darf höchstens 5 Minuten in der Zukunft liegen`() {
        assertEquals(now.plusMinutes(5), ActivityValidation.startedAt("2026-10-05T12:05:00+02:00", now))
        assertEquals("startedAt", failingField { ActivityValidation.startedAt("2026-10-05T12:06:00+02:00", now) })
        assertEquals("startedAt", failingField { ActivityValidation.startedAt("gestern", now) })
    }

    @Test
    fun `Dauer zwischen 1 und 1440 Minuten`() {
        ActivityValidation.durationMin(1)
        ActivityValidation.durationMin(1440)
        assertEquals("durationMin", failingField { ActivityValidation.durationMin(0) })
        assertEquals("durationMin", failingField { ActivityValidation.durationMin(1441) })
    }

    @Test
    fun `Koordinaten nur zusammen und im gültigen Bereich`() {
        ActivityValidation.location(null, null)
        ActivityValidation.location(48.16, 11.6)
        assertEquals("latitude", failingField { ActivityValidation.location(48.16, null) })
        assertEquals("latitude", failingField { ActivityValidation.location(91.0, 11.6) })
        assertEquals("longitude", failingField { ActivityValidation.location(48.16, 181.0) })
    }

    @Test
    fun `Beschreibung höchstens 500 Zeichen`() {
        ActivityValidation.description("x".repeat(500))
        assertEquals("description", failingField { ActivityValidation.description("x".repeat(501)) })
    }

    @Test
    fun `Datumsfilter im Format JJJJ-MM-TT`() {
        assertEquals(LocalDate.parse("2026-10-01"), ActivityValidation.dateFilter("2026-10-01", "from"))
        assertNull(ActivityValidation.dateFilter(null, "from"))
        assertEquals("to", failingField { ActivityValidation.dateFilter("01.10.2026", "to") })
    }
}
