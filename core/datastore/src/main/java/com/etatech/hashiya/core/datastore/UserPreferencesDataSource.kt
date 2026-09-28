package com.etatech.hashiya.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

    private companion object {
        val USER_API_KEY = stringPreferencesKey("user_api_key")
    }
}
