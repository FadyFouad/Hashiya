package com.etatech.hashiya.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {
    /** The user's own OpenAlex key, or null when the built-in key should be used. */
    val userApiKey: Flow<String?> = dataStore.data.map { it[USER_API_KEY] }

    /** Stores [key] trimmed. Null or blank removes the stored key. */
    suspend fun setUserApiKey(key: String?) {
        val trimmed = key?.trim()
        dataStore.edit { preferences ->
            if (trimmed.isNullOrEmpty()) {
                preferences.remove(USER_API_KEY)
            } else {
                preferences[USER_API_KEY] = trimmed
            }
        }
    }

    /** Whether crash reports may be sent; on until the user turns it off. */
    val crashReportsEnabled: Flow<Boolean> = dataStore.data.map { it[CRASH_REPORTS_ENABLED] ?: true }

    suspend fun setCrashReportsEnabled(enabled: Boolean) {
        dataStore.edit { it[CRASH_REPORTS_ENABLED] = enabled }
    }

    private companion object {
        val CRASH_REPORTS_ENABLED = booleanPreferencesKey("crash_reports_enabled")
        val USER_API_KEY = stringPreferencesKey("user_api_key")
    }
}
