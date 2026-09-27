package com.etatech.hashiya.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesDataSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataSource() = UserPreferencesDataSource(
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp.root, "test.preferences_pb") }
        )
    )

    @Test
    fun noKeyByDefault() = runTest {
        assertNull(dataSource().userApiKey.first())
    }

    @Test
    fun storesTrimmedKey() = runTest {
        val source = dataSource()
        source.setUserApiKey("  abc123 \n")
        assertEquals("abc123", source.userApiKey.first())
    }

    @Test
    fun blankKeyRemovesStoredKey() = runTest {
        val source = dataSource()
        source.setUserApiKey("abc123")
        source.setUserApiKey("   ")
        assertNull(source.userApiKey.first())
    }

    @Test
    fun nullRemovesStoredKey() = runTest {
        val source = dataSource()
        source.setUserApiKey("abc123")
        source.setUserApiKey(null)
        assertNull(source.userApiKey.first())
    }
}
