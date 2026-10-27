package de.touchgrass.ui.stats

import de.touchgrass.data.dto.WeekSummaryDto
import de.touchgrass.testing.testSports
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsFormatTest {
    @Test
    fun `Wochenzusammenfassung in Einzahl und Mehrzahl`() {
        assertEquals("3 Aktivitäten · 156 min · ~1420 kcal", WeekSummaryDto(3, 156, 1420).label())
        assertEquals("1 Aktivität · 30 min · ~280 kcal", WeekSummaryDto(1, 30, 280).label())
    }

    @Test
    fun `Wochen als Kalenderwoche, Monate als Kurzname`() {
        assertEquals("KW 34", weekLabel("2026-08-17"))
        assertEquals("KW 41", weekLabel("2026-10-05"))
        assertEquals("Mär", monthLabel("2026-03"))
        assertEquals("Dez", monthLabel("2025-12"))
    }

    @Test
    fun `Sportart als Emoji, unbekannte als Code`() {
        assertEquals("🏃", testSports.emojiFor("RUNNING"))
        assertEquals("CURLING", testSports.emojiFor("CURLING"))
    }

    @Test
    fun `Achsenschritte sind runde Zahlen`() {
        assertEquals(1.0, axisStep(0), 0.0)
        assertEquals(1.0, axisStep(3), 0.0)
        assertEquals(5.0, axisStep(10), 0.0)
        assertEquals(10.0, axisStep(40), 0.0)
        assertEquals(50.0, axisStep(156), 0.0)
        assertEquals(500.0, axisStep(1420), 0.0)
    }
}
