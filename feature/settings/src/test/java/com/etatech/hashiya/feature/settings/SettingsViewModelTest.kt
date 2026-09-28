package com.etatech.hashiya.feature.settings

import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(preferences, languageController)
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
}
