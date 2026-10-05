package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.datastore.UserPreferencesDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    /** The user's own OpenAlex key, or null when the built-in key is used. */
    val userApiKey: Flow<String?>

    /** Trims [key]; null or blank reverts to the built-in key. */
    suspend fun setUserApiKey(key: String?)

    /** Whether crash reports may be sent; true until the user turns it off. */
    val crashReportsEnabled: Flow<Boolean>

    suspend fun setCrashReportsEnabled(enabled: Boolean)
}

internal class DataStoreUserPreferencesRepository @Inject constructor(private val dataSource: UserPreferencesDataSource) :
    UserPreferencesRepository {
    override val userApiKey: Flow<String?> = dataSource.userApiKey

    override suspend fun setUserApiKey(key: String?) = dataSource.setUserApiKey(key)

    override val crashReportsEnabled: Flow<Boolean> = dataSource.crashReportsEnabled

    override suspend fun setCrashReportsEnabled(enabled: Boolean) = dataSource.setCrashReportsEnabled(enabled)
}
