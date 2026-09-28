package com.etatech.hashiya.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val usingUserKey: Boolean = false, val keyInput: String = "", val language: AppLanguage = AppLanguage.System)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val languageController: AppLanguageController
) : ViewModel() {
    /** Null until the user edits the field; the stored key is shown until then. */
    private val editedKey = MutableStateFlow<String?>(null)
    private val language = MutableStateFlow(languageController.current())

    val uiState: StateFlow<SettingsUiState> = combine(preferences.userApiKey, editedKey, language) { stored, edited, lang ->
        SettingsUiState(usingUserKey = stored != null, keyInput = edited ?: stored.orEmpty(), language = lang)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(language = language.value))

    fun onKeyInputChange(value: String) {
        editedKey.value = value
    }

    fun onSaveKey() {
        val value = uiState.value.keyInput
        viewModelScope.launch {
            preferences.setUserApiKey(value)
            editedKey.value = null
        }
    }

    fun onResetKey() {
        viewModelScope.launch {
            preferences.setUserApiKey(null)
            editedKey.value = null
        }
    }

    fun onLanguageSelected(selected: AppLanguage) {
        languageController.set(selected)
        language.value = selected
    }
}
