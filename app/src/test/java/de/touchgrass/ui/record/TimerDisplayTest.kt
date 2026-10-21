package de.touchgrass.ui.record

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class TimerDisplayTest {
    @Test
    fun `Zeit wird als Stunden, Minuten und Sekunden angezeigt`() {
        assertEquals("00:00:00", formatElapsed(Duration.ZERO))
        assertEquals("00:12:05", formatElapsed(Duration.ofSeconds(12 * 60 + 5)))
        assertEquals("01:30:00", formatElapsed(Duration.ofMinutes(90)))
        assertEquals("26:00:59", formatElapsed(Duration.ofHours(26).plusSeconds(59)))
    }
}
