package de.touchgrass.ui.activities

import de.touchgrass.data.dto.PointsBreakdownDto
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityFormatTest {
    @Test
    fun `Punkte-Aufschlüsselung nennt nur vergebene Boni`() {
        assertEquals("45 min · +10 · +20 neue Sportart", PointsBreakdownDto(45, 10, 20, 0, 75).describe())
        assertEquals("30 min · +10 · +50 Wochenziel", PointsBreakdownDto(30, 10, 0, 50, 90).describe())
    }

    @Test
    fun `Aktivität wird mit Datum, Uhrzeit und Zusammenfassung angezeigt`() {
        val activity = testActivity(startedAt = "2026-10-05T09:30:00+02:00")

        assertEquals("05.10.2026, 09:30 Uhr", activity.startedAtLabel())
        assertEquals("45 min · ~480 kcal · 75 Punkte", activity.summary())
    }

    @Test
    fun `Sportart wird mit Emoji angezeigt, unbekannte mit Code`() {
        assertEquals("🏃 Joggen", testSports.labelFor("RUNNING"))
        assertEquals("CURLING", testSports.labelFor("CURLING"))
    }
}
