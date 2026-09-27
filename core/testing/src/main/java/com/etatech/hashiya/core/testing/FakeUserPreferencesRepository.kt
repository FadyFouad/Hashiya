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
}
