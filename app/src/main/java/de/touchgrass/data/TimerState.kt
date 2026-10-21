package de.touchgrass.data

import java.time.Duration
import java.time.Instant

data class TimerState(
    val sportCode: String,
    val startedAt: Instant,
    val pausedTotal: Duration = Duration.ZERO,
    val pausedSince: Instant? = null,
) {
    val isPaused: Boolean get() = pausedSince != null

    fun elapsed(now: Instant): Duration {
        val elapsed = Duration.between(startedAt, pausedSince ?: now) - pausedTotal
        return if (elapsed.isNegative) Duration.ZERO else elapsed
    }

    fun pause(now: Instant): TimerState = if (isPaused) this else copy(pausedSince = now)

    fun resume(now: Instant): TimerState {
        val since = pausedSince ?: return this
        return copy(pausedTotal = pausedTotal + Duration.between(since, now), pausedSince = null)
    }

    fun durationMinutes(now: Instant): Int = maxOf(1, ((elapsed(now).seconds + 30) / 60).toInt())
}
