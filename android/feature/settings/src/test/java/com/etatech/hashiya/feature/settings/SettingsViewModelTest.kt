package com.etatech.hashiya.feature.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import com.etatech.hashiya.core.model.PdfStorage
import com.etatech.hashiya.core.testing.FakeCrashReporter
import com.etatech.hashiya.core.testing.FakeLibraryBackup
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeUserPreferencesRepository()
    private val languageController = FakeAppLanguageController()
    private val pdfs = FakePdfRepository()
    private val crashReporter = FakeCrashReporter()

    private fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(preferences, languageController, pdfs, FakeLibraryBackup(), crashReporter)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    @Test
    fun startsOnBuiltInKey() = runTest {
        val state = viewModel().uiState.value
        assertFalse(state.usingUserKey)
        assertEquals("", state.keyInput)
        assertEquals(AppLanguage.System, state.language)
    }

    @Test
    fun showsStoredUserKey() = runTest {
        preferences.setUserApiKey("stored")
        val state = viewModel().uiState.value
        assertTrue(state.usingUserKey)
        assertEquals("stored", state.keyInput)
    }

    @Test
    fun savesTrimmedKey() = runTest {
        val viewModel = viewModel()
        viewModel.onKeyInputChange("  new-key ")
        viewModel.onSaveKey()

        assertEquals("new-key", preferences.userApiKey.value)
        assertTrue(viewModel.uiState.value.usingUserKey)
        assertEquals("new-key", viewModel.uiState.value.keyInput)
    }

    @Test
    fun savingBlankRevertsToBuiltIn() = runTest {
        preferences.setUserApiKey("stored")
        val viewModel = viewModel()
        viewModel.onKeyInputChange("   ")
        viewModel.onSaveKey()

        assertNull(preferences.userApiKey.value)
        assertFalse(viewModel.uiState.value.usingUserKey)
    }

    @Test
    fun resetRevertsToBuiltIn() = runTest {
        preferences.setUserApiKey("stored")
        val viewModel = viewModel()
        viewModel.onResetKey()

        assertNull(preferences.userApiKey.value)
        assertEquals("", viewModel.uiState.value.keyInput)
    }

    @Test
    fun selectingLanguageAppliesIt() = runTest {
        val viewModel = viewModel()
        viewModel.onLanguageSelected(AppLanguage.Arabic)

        assertEquals(AppLanguage.Arabic, languageController.language)
        assertEquals(AppLanguage.Arabic, viewModel.uiState.value.language)
    }

    @Test
    fun selectingLanguageUpdatesTheCrashKey() = runTest {
        val viewModel = viewModel()

        viewModel.onLanguageSelected(AppLanguage.Arabic)
        viewModel.onLanguageSelected(AppLanguage.English)
        viewModel.onLanguageSelected(AppLanguage.System)

        assertEquals(
            listOf(CrashKey.Language to "ar", CrashKey.Language to "en", CrashKey.Language to "system"),
            crashReporter.keyHistory
        )
    }

    @Test
    fun loadsThePdfStorage() = runTest {
        pdfs.setStorage(PdfStorage(downloadedBytes = 3_000_000, downloadedCount = 2, attachedBytes = 1_000_000, attachedCount = 1))

        val state = viewModel().uiState.value

        assertEquals(PdfStorage(3_000_000, 2, 1_000_000, 1), state.storage)
    }

    @Test
    fun deletingDownloadedPdfsKeepsAttachedOnesAndReloads() = runTest {
        pdfs.setStorage(PdfStorage(downloadedBytes = 3_000_000, downloadedCount = 2, attachedBytes = 1_000_000, attachedCount = 1))
        val viewModel = viewModel()

        viewModel.onDeleteDownloadedPdfs()

        assertEquals(1, pdfs.deleteDownloadedCalls)
        assertEquals(PdfStorage(0, 0, 1_000_000, 1), viewModel.uiState.value.storage)
    }

    @Test
    fun crashReportsFollowTheStoredChoice() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.crashReportsEnabled)

        preferences.setCrashReportsEnabled(false)
        assertFalse(viewModel.uiState.value.crashReportsEnabled)
    }

    @Test
    fun turningCrashReportsOffStoresItAndStopsTheReporter() = runTest {
        val viewModel = viewModel()
        viewModel.onCrashReportsChange(false)

        assertFalse(viewModel.uiState.value.crashReportsEnabled)
        assertEquals(listOf(false), crashReporter.enabledCalls)
    }

    @Test
    fun turningCrashReportsOnStoresItAndStartsTheReporter() = runTest {
        preferences.setCrashReportsEnabled(false)
        val viewModel = viewModel()
        viewModel.onCrashReportsChange(true)

        assertTrue(viewModel.uiState.value.crashReportsEnabled)
        assertEquals(listOf(true), crashReporter.enabledCalls)
    }

    @Test
    fun theCrashReportsChoiceIsStoredEvenWhenSettingsCloses() = runTest {
        val slowPreferences = object : UserPreferencesRepository by preferences {
            override suspend fun setCrashReportsEnabled(enabled: Boolean) {
                delay(1_000)
                preferences.setCrashReportsEnabled(enabled)
            }
        }
        val store = ViewModelStore()
        val factory = viewModelFactory {
            initializer { SettingsViewModel(slowPreferences, languageController, pdfs, FakeLibraryBackup(), crashReporter) }
        }
        val viewModel = ViewModelProvider(store, factory)[SettingsViewModel::class.java]

        viewModel.onCrashReportsChange(false)
        store.clear()
        advanceUntilIdle()

        assertEquals(listOf(false), crashReporter.enabledCalls)
        assertFalse(preferences.crashReportsEnabled.value)
    }
}
