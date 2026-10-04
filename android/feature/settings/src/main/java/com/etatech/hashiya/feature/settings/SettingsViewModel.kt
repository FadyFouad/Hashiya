package com.etatech.hashiya.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.data.backup.BackupException
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.BackupSummary
import com.etatech.hashiya.core.data.backup.ExportedFile
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.repository.PdfRepository
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import com.etatech.hashiya.core.model.PdfStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val usingUserKey: Boolean = false,
    val keyInput: String = "",
    val language: AppLanguage = AppLanguage.System,
    /** Null until loaded; the Storage section is hidden while null. */
    val storage: PdfStorage? = null,
    val backup: BackupUiState = BackupUiState(),
    val crashReportsEnabled: Boolean = true
)

data class BackupUiState(val summary: BackupSummary? = null, val export: ExportState = ExportState.Idle, val message: BackupMessage? = null)

sealed interface ExportState {
    data object Idle : ExportState

    data class Choosing(val includePdfs: Boolean) : ExportState

    data class Building(val includePdfs: Boolean, val progress: Float) : ExportState

    /** The UI opens the save dialog with [fileName] once, then reports back with onSaveDestination. */
    data class ReadyToSave(val fileName: String) : ExportState

    data object Saving : ExportState
}

sealed interface BackupMessage {
    data class Exported(val missingPdfs: Int) : BackupMessage

    data class ExportFailed(val failure: BackupFailure) : BackupMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val languageController: AppLanguageController,
    private val pdfRepository: PdfRepository,
    private val libraryBackup: LibraryBackup,
    private val crashReporter: CrashReporter
) : ViewModel() {
    /** Null until the user edits the field; the stored key is shown until then. */
    private val editedKey = MutableStateFlow<String?>(null)
    private val language = MutableStateFlow(languageController.current())
    private val storage = MutableStateFlow<PdfStorage?>(null)
    private val backup = MutableStateFlow(BackupUiState())

    /** The archive waiting for the save dialog; deleted once saved, cancelled or the screen goes away. */
    private var exported: ExportedFile? = null
    private var exportJob: Job? = null

    val uiState: StateFlow<SettingsUiState> =
        combine(
            combine(preferences.userApiKey, editedKey, language, storage, backup) { stored, edited, lang, pdfs, backupState ->
                SettingsUiState(
                    usingUserKey = stored != null,
                    keyInput = edited ?: stored.orEmpty(),
                    language = lang,
                    storage = pdfs,
                    backup = backupState
                )
            },
            preferences.crashReportsEnabled
        ) { state, crashReports ->
            state.copy(crashReportsEnabled = crashReports)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(language = language.value))

    init {
        refresh()
    }

    /** The screen calls this each time it is shown again: a restore or a deleted PDF elsewhere changes both. */
    fun onResume() {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch { storage.value = pdfRepository.storage() }
        viewModelScope.launch { refreshSummary() }
    }

    private suspend fun refreshSummary() {
        val summary = libraryBackup.summary()
        backup.update { it.copy(summary = summary) }
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
        crashReporter.setKey(
            CrashKey.Language,
            when (selected) {
                AppLanguage.English -> "en"
                AppLanguage.Arabic -> "ar"
                AppLanguage.System -> "system"
            }
        )
    }

    /** The reporter is told first, so an opt-out takes effect before the preference is written. */
    fun onCrashReportsChange(enabled: Boolean) {
        crashReporter.setEnabled(enabled)
        viewModelScope.launch { preferences.setCrashReportsEnabled(enabled) }
    }

    /** Deletes downloaded PDFs only; attached ones can't be fetched again, so they stay. */
    fun onDeleteDownloadedPdfs() {
        viewModelScope.launch {
            pdfRepository.deleteDownloaded()
            storage.value = pdfRepository.storage()
        }
    }

    fun onExportClick() {
        if (backup.value.export != ExportState.Idle) return
        backup.update { it.copy(export = ExportState.Choosing(includePdfs = false)) }
        viewModelScope.launch { refreshSummary() }
    }

    fun onIncludePdfsChange(include: Boolean) {
        backup.update { state ->
            (state.export as? ExportState.Choosing)?.let { state.copy(export = it.copy(includePdfs = include)) }
                ?: state
        }
    }

    fun onDismissExport() {
        backup.update { if (it.export is ExportState.Choosing) it.copy(export = ExportState.Idle) else it }
    }

    fun onConfirmExport() {
        val choosing = backup.value.export as? ExportState.Choosing ?: return
        backup.update { it.copy(export = ExportState.Building(choosing.includePdfs, progress = 0f)) }
        exportJob = viewModelScope.launch {
            try {
                // Progress arrives on an IO thread; updating the flow is thread-safe.
                val file = libraryBackup.export(choosing.includePdfs) { progress ->
                    backup.update { state ->
                        (state.export as? ExportState.Building)?.let { state.copy(export = it.copy(progress = progress)) } ?: state
                    }
                }
                exported = file
                backup.update { it.copy(export = ExportState.ReadyToSave(file.fileName)) }
            } catch (e: BackupException) {
                backup.update { it.copy(export = ExportState.Idle, message = BackupMessage.ExportFailed(e.failure)) }
            }
        }
    }

    /** Stops an export being built; the archive's temporary file is deleted by the export itself. */
    fun onCancelExport() {
        if (backup.value.export !is ExportState.Building) return
        exportJob?.cancel()
        exportJob = null
        backup.update { it.copy(export = ExportState.Idle) }
    }

    /** The save dialog's answer; null when it was cancelled. */
    fun onSaveDestination(uri: Uri?) {
        val file = exported
        if (file == null) {
            // No archive to write, as after process death while the dialog was open: the dialog already created an empty
            // document, which must not be left looking like a backup.
            if (uri != null) {
                viewModelScope.launch {
                    libraryBackup.deleteDestination(uri)
                    backup.update { it.copy(message = BackupMessage.ExportFailed(BackupFailure.WriteFailed)) }
                }
            }
            return
        }
        if (backup.value.export !is ExportState.ReadyToSave) return
        if (uri == null) {
            discardExport()
            backup.update { it.copy(export = ExportState.Idle) }
            return
        }
        backup.update { it.copy(export = ExportState.Saving) }
        viewModelScope.launch {
            val message = try {
                libraryBackup.save(file, uri)
                BackupMessage.Exported(file.missingPdfs)
            } catch (e: BackupException) {
                BackupMessage.ExportFailed(e.failure)
            }
            discardExport()
            backup.update { it.copy(export = ExportState.Idle, message = message) }
        }
    }

    fun onMessageShown() {
        backup.update { it.copy(message = null) }
    }

    private fun discardExport() {
        exported?.let(libraryBackup::discard)
        exported = null
    }

    override fun onCleared() {
        discardExport()
    }
}
