package com.etatech.hashiya.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.etatech.hashiya.core.datastore.UserPreferencesDataSource
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreUserApiKeySourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun followsStoredKey() = runTest {
        val preferences = UserPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "p.preferences_pb") })
        )
        val source = DataStoreUserApiKeySource(preferences, backgroundScope)

        assertNull(source.userKey.value)
        preferences.setUserApiKey("abc")
        assertEquals("abc", source.userKey.first { it == "abc" })
        preferences.setUserApiKey(null)
        assertNull(source.userKey.first { it == null })
    }
}
