package de.touchgrass.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

interface TokenStore {
    val token: Flow<String?>
    suspend fun save(token: String)
    suspend fun clear()
}

class DataStoreTokenStore(private val dataStore: DataStore<Preferences>) : TokenStore {
    override val token: Flow<String?> = dataStore.data.map { it[TOKEN] }

    override suspend fun save(token: String) {
        dataStore.edit { it[TOKEN] = token }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(TOKEN) }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
    }
}
