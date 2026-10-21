package de.touchgrass.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimerStateTest {
    private val start = Instant.parse("2026-10-05T08:00:00Z")

    private fun at(minutes: Long, seconds: Long = 0): Instant = start.plusSeconds(minutes * 60 + seconds)

    @Test
    fun `laufender Timer zählt die Zeit seit dem Start`() {
        val timer = TimerState("RUNNING", start)

        assertFalse(timer.isPaused)
        assertEquals(Duration.ofMinutes(12), timer.elapsed(at(12)))
    }

    @Test
    fun `Pausen zählen nicht mit`() {
        val paused = TimerState("RUNNING", start).pause(at(10)).resume(at(15)).pause(at(20))

        assertTrue(paused.isPaused)
        assertEquals(Duration.ofMinutes(15), paused.elapsed(at(45)))
        assertEquals(Duration.ofMinutes(25), paused.resume(at(30)).elapsed(at(40)))
    }

    @Test
    fun `doppeltes Pausieren oder Weitermachen ändert nichts`() {
        val running = TimerState("RUNNING", start)
        val paused = running.pause(at(10))

        assertEquals(paused, paused.pause(at(12)))
        assertEquals(running, running.resume(at(12)))
    }

    @Test
    fun `zurückgestellte Uhr ergibt keine negative Dauer`() {
        assertEquals(Duration.ZERO, TimerState("RUNNING", start).elapsed(start.minusSeconds(60)))
    }

    @Test
    fun `Minuten für das Formular sind gerundet und mindestens 1`() {
        val timer = TimerState("RUNNING", start)

        assertEquals(1, timer.durationMinutes(at(0, 10)))
        assertEquals(44, timer.durationMinutes(at(44, 29)))
        assertEquals(45, timer.durationMinutes(at(44, 30)))
    }
}
