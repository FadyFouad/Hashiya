package com.etatech.hashiya.analytics

import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsProperty
import com.etatech.hashiya.core.analytics.Language
import com.etatech.hashiya.core.analytics.LibrarySize
import com.etatech.hashiya.core.analytics.YesNo
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Runs once at launch: collection follows the switch (release builds only), then the user properties are set. */
class AnalyticsStartup(
    private val analytics: Analytics,
    private val preferences: UserPreferencesRepository,
    private val libraryBackup: LibraryBackup,
    private val languageTag: () -> String,
    private val isRelease: Boolean
) {
    suspend fun run() {
        analytics.setEnabled(isRelease && preferences.analyticsEnabled.first())
        analytics.setProperty(AnalyticsProperty.Language, Language.of(languageTag()))
        analytics.setProperty(AnalyticsProperty.HasOwnKey, YesNo.of(preferences.userApiKey.first() != null))
        val papers = try {
            libraryBackup.summary().papers
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (papers != null) analytics.setProperty(AnalyticsProperty.LibrarySizeBucket, LibrarySize.of(papers))
    }

    /** Keeps `has_own_key` current when the key changes in Settings. Never returns. */
    suspend fun followOwnKey() {
        preferences.userApiKey.map { it != null }.distinctUntilChanged().collect {
            analytics.setProperty(AnalyticsProperty.HasOwnKey, YesNo.of(it))
        }
    }
}
