package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import com.etatech.hashiya.core.model.CitationStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeUserPreferencesRepository(initialKey: String? = null) : UserPreferencesRepository {
    private val key = MutableStateFlow(initialKey)

    override val userApiKey: StateFlow<String?> = key

    override suspend fun setUserApiKey(key: String?) {
        this.key.value = key?.trim()?.takeIf { it.isNotEmpty() }
    }

    private val crashReports = MutableStateFlow(true)

    override val crashReportsEnabled: StateFlow<Boolean> = crashReports

    override suspend fun setCrashReportsEnabled(enabled: Boolean) {
        crashReports.value = enabled
    }

    private val analytics = MutableStateFlow(true)

    override val analyticsEnabled: StateFlow<Boolean> = analytics

    override suspend fun setAnalyticsEnabled(enabled: Boolean) {
        analytics.value = enabled
    }

    /** When set, [setCitationStyle] throws it. */
    var failOnSetCitationStyle: Exception? = null

    private val style = MutableStateFlow(CitationStyle.Apa)

    override val citationStyle: StateFlow<CitationStyle> = style

    override suspend fun setCitationStyle(style: CitationStyle) {
        failOnSetCitationStyle?.let { throw it }
        this.style.value = style
    }
}
