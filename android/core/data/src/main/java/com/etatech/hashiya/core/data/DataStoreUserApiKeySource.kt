package com.etatech.hashiya.core.data

import com.etatech.hashiya.core.data.di.ApplicationScope
import com.etatech.hashiya.core.datastore.UserPreferencesDataSource
import com.etatech.hashiya.core.network.UserApiKeySource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Keeps the stored key in memory so the network interceptor never blocks on disk. */
@Singleton
internal class DataStoreUserApiKeySource @Inject constructor(
    preferences: UserPreferencesDataSource,
    @ApplicationScope scope: CoroutineScope
) : UserApiKeySource {
    override val userKey: StateFlow<String?> = preferences.userApiKey.stateIn(scope, SharingStarted.Eagerly, null)
}
