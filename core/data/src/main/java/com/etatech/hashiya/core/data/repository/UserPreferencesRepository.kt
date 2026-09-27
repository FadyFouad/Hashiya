package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.datastore.UserPreferencesDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    /** The user's own OpenAlex key, or null when the built-in key is used. */
    val userApiKey: Flow<String?>

    /** Trims [key]; null or blank reverts to the built-in key. */
    suspend fun setUserApiKey(key: String?)
}

internal class DataStoreUserPreferencesRepository @Inject constructor(private val dataSource: UserPreferencesDataSource) :
    UserPreferencesRepository {
    override val userApiKey: Flow<String?> = dataSource.userApiKey

    override suspend fun setUserApiKey(key: String?) = dataSource.setUserApiKey(key)
}
