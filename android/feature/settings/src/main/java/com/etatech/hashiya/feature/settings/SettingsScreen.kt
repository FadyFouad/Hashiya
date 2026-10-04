package com.etatech.hashiya.feature.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.layout.ControlMaxWidth
import com.etatech.hashiya.core.designsystem.layout.centeredMaxWidth
import com.etatech.hashiya.core.designsystem.layout.horizontalMargin
import com.etatech.hashiya.core.model.PdfStorage

@Composable
internal fun SettingsScreen(onBack: () -> Unit, onOpenRestore: (String) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveDialog = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        viewModel.onSaveDestination(uri)
    }
    // Drive and some file managers report a .hashiya file as a generic binary.
    val openDialog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onOpenRestore(it.toString()) }
    }
    // Survives activity recreation, so a save dialog that is already open isn't launched a second time.
    var launchedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    val export = uiState.backup.export
    LaunchedEffect(export) {
        when (export) {
            is ExportState.ReadyToSave -> if (launchedFileName != export.fileName) {
                launchedFileName = export.fileName
                saveDialog.launch(export.fileName)
            }
            ExportState.Idle -> launchedFileName = null
            else -> Unit
        }
    }
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onKeyInputChange = viewModel::onKeyInputChange,
        onSaveKey = viewModel::onSaveKey,
        onResetKey = viewModel::onResetKey,
        onLanguageSelected = viewModel::onLanguageSelected,
        onDeleteDownloadedPdfs = viewModel::onDeleteDownloadedPdfs,
        onExportClick = viewModel::onExportClick,
        onIncludePdfsChange = viewModel::onIncludePdfsChange,
        onConfirmExport = viewModel::onConfirmExport,
        onDismissExport = viewModel::onDismissExport,
        onRestoreClick = { openDialog.launch(arrayOf(BACKUP_MIME_TYPE, "application/octet-stream")) },
        onMessageShown = viewModel::onMessageShown
    )
}

internal const val BACKUP_MIME_TYPE = "application/zip"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onKeyInputChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onResetKey: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDeleteDownloadedPdfs: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onIncludePdfsChange: (Boolean) -> Unit = {},
    onConfirmExport: () -> Unit = {},
    onDismissExport: () -> Unit = {},
    onRestoreClick: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.backup.message
    val messageText = message?.let { backupMessageText(it) }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(HashiyaIcons.Back, contentDescription = stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .centeredMaxWidth()
                .padding(horizontal = horizontalMargin(), vertical = 16.dp)
        ) {
            Text(stringResource(R.string.settings_api_key_section), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    if (uiState.usingUserKey) R.string.settings_api_key_using_yours else R.string.settings_api_key_using_built_in
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.keyInput,
                onValueChange = onKeyInputChange,
                label = { Text(stringResource(R.string.settings_api_key_label)) },
                singleLine = true,
                // A password keyboard with autocorrect off, so the keyboard never learns or suggests the key.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) HashiyaIcons.VisibilityOff else HashiyaIcons.Visibility,
                            contentDescription = stringResource(
                                if (keyVisible) R.string.settings_api_key_hide else R.string.settings_api_key_show
                            )
                        )
                    }
                },
                modifier = Modifier.widthIn(max = ControlMaxWidth).fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveKey) { Text(stringResource(R.string.settings_save)) }
                TextButton(onClick = onResetKey, enabled = uiState.usingUserKey) {
                    Text(stringResource(R.string.settings_reset))
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.settings_language_section), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = uiState.language == language,
                                onClick = { onLanguageSelected(language) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = uiState.language == language, onClick = null)
                        Text(languageLabel(language), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            BackupSection(uiState.backup, onExportClick, onRestoreClick)
            uiState.storage?.let { storage ->
                Spacer(Modifier.height(32.dp))
                StorageSection(storage, onDeleteDownloadedPdfs)
            }
        }
        val summary = uiState.backup.summary
        val export = uiState.backup.export
        if (summary != null && (export is ExportState.Choosing || export is ExportState.Building)) {
            ExportDialog(summary, export, onIncludePdfsChange, onConfirmExport, onDismissExport)
        }
    }
}

@Composable
private fun backupMessageText(message: BackupMessage): String = when (message) {
    is BackupMessage.Exported -> if (message.missingPdfs == 0) {
        stringResource(R.string.settings_exported)
    } else {
        pluralStringResource(R.plurals.settings_exported_missing, message.missingPdfs, message.missingPdfs)
    }

    is BackupMessage.ExportFailed -> stringResource(
        if (message.failure == BackupFailure.NoSpace) R.string.settings_export_failed_space else R.string.settings_export_failed
    )
}

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.System -> R.string.settings_language_system
        AppLanguage.English -> R.string.settings_language_english
        AppLanguage.Arabic -> R.string.settings_language_arabic
    }
)

@Composable
private fun StorageSection(storage: PdfStorage, onDeleteDownloadedPdfs: () -> Unit) {
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        pluralStringResource(
            R.plurals.settings_downloaded_pdfs,
            storage.downloadedCount,
            Formatter.formatShortFileSize(context, storage.downloadedBytes),
            storage.downloadedCount
        ),
        style = MaterialTheme.typography.bodyMedium
    )
    if (storage.attachedCount > 0) {
        Spacer(Modifier.height(4.dp))
        Text(
            pluralStringResource(
                R.plurals.settings_attached_pdfs,
                storage.attachedCount,
                Formatter.formatShortFileSize(context, storage.attachedBytes),
                storage.attachedCount
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    if (storage.downloadedCount > 0) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { confirming = true }) { Text(stringResource(R.string.settings_delete_downloaded)) }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            text = {
                Text(pluralStringResource(R.plurals.settings_delete_downloaded_message, storage.downloadedCount, storage.downloadedCount))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDeleteDownloadedPdfs()
                }) { Text(stringResource(R.string.settings_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }
}
