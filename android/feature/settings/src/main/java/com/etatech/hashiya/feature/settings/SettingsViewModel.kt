package com.etatech.hashiya.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.repository.PdfRepository
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import com.etatech.hashiya.core.model.PdfStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val usingUserKey: Boolean = false,
    val keyInput: String = "",
    val language: AppLanguage = AppLanguage.System,
    /** Null until loaded; the Storage section is hidden while null. */
    val storage: PdfStorage? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val languageController: AppLanguageController,
    private val pdfRepository: PdfRepository
) : ViewModel() {
    /** Null until the user edits the field; the stored key is shown until then. */
    private val editedKey = MutableStateFlow<String?>(null)
    private val language = MutableStateFlow(languageController.current())
    private val storage = MutableStateFlow<PdfStorage?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(preferences.userApiKey, editedKey, language, storage) { stored, edited, lang, pdfs ->
            SettingsUiState(usingUserKey = stored != null, keyInput = edited ?: stored.orEmpty(), language = lang, storage = pdfs)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(language = language.value))

    init {
        viewModelScope.launch { storage.value = pdfRepository.storage() }
    }

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

    /** Deletes downloaded PDFs only; attached ones can't be fetched again, so they stay. */
    fun onDeleteDownloadedPdfs() {
        viewModelScope.launch {
            pdfRepository.deleteDownloaded()
            storage.value = pdfRepository.storage()
        }
    }
}
