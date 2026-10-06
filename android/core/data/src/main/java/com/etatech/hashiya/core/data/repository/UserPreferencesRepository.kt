package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.datastore.UserPreferencesDataSource
import com.etatech.hashiya.core.model.CitationStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserPreferencesRepository {
    /** The user's own OpenAlex key, or null when the built-in key is used. */
    val userApiKey: Flow<String?>

    /** Trims [key]; null or blank reverts to the built-in key. */
    suspend fun setUserApiKey(key: String?)

    /** Whether crash reports may be sent; true until the user turns it off. */
    val crashReportsEnabled: Flow<Boolean>

    suspend fun setCrashReportsEnabled(enabled: Boolean)

    /** Whether usage statistics may be sent; true until the user turns it off. */
    val analyticsEnabled: Flow<Boolean>

    suspend fun setAnalyticsEnabled(enabled: Boolean)

    /** The style Copy citation and Export use first; APA until one is chosen. */
    val citationStyle: Flow<CitationStyle>

    suspend fun setCitationStyle(style: CitationStyle)
}

internal class DataStoreUserPreferencesRepository @Inject constructor(private val dataSource: UserPreferencesDataSource) :
    UserPreferencesRepository {
    override val userApiKey: Flow<String?> = dataSource.userApiKey

    override suspend fun setUserApiKey(key: String?) = dataSource.setUserApiKey(key)

    override val crashReportsEnabled: Flow<Boolean> = dataSource.crashReportsEnabled

    override suspend fun setCrashReportsEnabled(enabled: Boolean) = dataSource.setCrashReportsEnabled(enabled)

    override val analyticsEnabled: Flow<Boolean> = dataSource.analyticsEnabled

    override suspend fun setAnalyticsEnabled(enabled: Boolean) = dataSource.setAnalyticsEnabled(enabled)

    override val citationStyle: Flow<CitationStyle> = dataSource.citationStyleId.map(CitationStyle::fromId)

    override suspend fun setCitationStyle(style: CitationStyle) = dataSource.setCitationStyleId(style.id)
}
