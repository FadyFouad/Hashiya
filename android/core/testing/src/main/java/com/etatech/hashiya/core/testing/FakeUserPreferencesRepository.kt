package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
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
}
