package de.touchgrass.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer")

interface TimerStore {
    val state: Flow<TimerState?>
    suspend fun save(state: TimerState)
    suspend fun clear()
}

class DataStoreTimerStore(private val dataStore: DataStore<Preferences>) : TimerStore {
    override val state: Flow<TimerState?> = dataStore.data.map { prefs ->
        val sportCode = prefs[SPORT_CODE] ?: return@map null
        val startedAt = prefs[STARTED_AT] ?: return@map null
        TimerState(
            sportCode = sportCode,
            startedAt = Instant.ofEpochMilli(startedAt),
            pausedTotal = Duration.ofMillis(prefs[PAUSED_TOTAL] ?: 0),
            pausedSince = prefs[PAUSED_SINCE]?.let(Instant::ofEpochMilli),
        )
    }

    override suspend fun save(state: TimerState) {
        dataStore.edit { prefs ->
            prefs[SPORT_CODE] = state.sportCode
            prefs[STARTED_AT] = state.startedAt.toEpochMilli()
            prefs[PAUSED_TOTAL] = state.pausedTotal.toMillis()
            val pausedSince = state.pausedSince
            if (pausedSince != null) prefs[PAUSED_SINCE] = pausedSince.toEpochMilli() else prefs.remove(PAUSED_SINCE)
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val SPORT_CODE = stringPreferencesKey("sport_code")
        val STARTED_AT = longPreferencesKey("started_at")
        val PAUSED_TOTAL = longPreferencesKey("paused_total")
        val PAUSED_SINCE = longPreferencesKey("paused_since")
    }
}
