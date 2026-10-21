package de.touchgrass.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

// Eine echte DataStore-Datei lässt sich unter Windows im Test nicht zweimal ersetzen, darum liegen die Werte im Speicher.
private class InMemoryDataStore : DataStore<Preferences> {
    private val current = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = current
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(current.value).also { current.value = it }
}

class TimerStoreTest {
    @Test
    fun `Timer wird gespeichert, wieder gelesen und gelöscht`() = runBlocking {
        val store = DataStoreTimerStore(InMemoryDataStore())
        val paused = TimerState(
            sportCode = "RUNNING",
            startedAt = Instant.parse("2026-10-05T08:00:00Z"),
            pausedTotal = Duration.ofMinutes(3),
            pausedSince = Instant.parse("2026-10-05T08:30:00Z"),
        )

        assertNull(store.state.first())

        store.save(paused)
        assertEquals(paused, store.state.first())

        val running = paused.resume(Instant.parse("2026-10-05T08:35:00Z"))
        store.save(running)
        assertEquals(running, store.state.first())

        store.clear()
        assertNull(store.state.first())
    }
}
