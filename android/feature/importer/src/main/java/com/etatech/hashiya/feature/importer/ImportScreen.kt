package com.etatech.hashiya.feature.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.designsystem.component.EmptyState
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.layout.centeredMaxWidth
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob

internal const val IMPORT_PROGRESS_TAG = "import_progress"

/** The system picker asks for any file: .bib and .ris have no registered MIME types, so the content decides (spec §8.1). */
private val PICKER_TYPES = arrayOf("*/*")

@Composable
internal fun ImportScreen(onBack: () -> Unit, onShowLibrary: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFileChosen(it.toString()) }
    }
    ImportContent(
        uiState = uiState,
        actions = ImportActions(
            onBack = onBack,
            onChooseFile = { picker.launch(PICKER_TYPES) },
            onAddToCollectionChange = viewModel::onAddToCollectionChange,
            onConfirm = viewModel::onConfirm,
            onCancel = viewModel::onCancel,
            onShowLibrary = { viewModel.onDismiss(then = onShowLibrary) },
            onChooseAnother = {
                viewModel.onDismiss()
                picker.launch(PICKER_TYPES)
            }
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportContent(uiState: ImportUiState, actions: ImportActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(HashiyaIcons.Back, contentDescription = stringResource(R.string.import_back))
                    }
                }
            )
        }
    ) { padding ->
        // A centred column on wide windows, like Settings and Restore.
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .centeredMaxWidth()
        ) {
            when (uiState) {
                ImportUiState.Start -> EmptyState(
                    icon = HashiyaIcons.Import,
                    title = stringResource(R.string.import_start_title),
                    message = stringResource(R.string.import_start_message),
                    actionLabel = stringResource(R.string.import_choose_file),
                    onAction = actions.onChooseFile
                )

                ImportUiState.Reading -> Working(stringResource(R.string.import_reading), onCancel = null)

                is ImportUiState.Analyzing -> Working(stringResource(R.string.import_analyzing, uiState.job.fileName), actions.onCancel)

                is ImportUiState.Ready -> Preview(uiState, actions)

                is ImportUiState.Importing -> Importing(uiState.job, actions.onCancel)

                is ImportUiState.Done -> Done(uiState.job, actions)

                is ImportUiState.Failed -> EmptyState(
                    icon = HashiyaIcons.Error,
                    title = stringResource(uiState.failure.message()),
                    message = null,
                    actionLabel = stringResource(R.string.import_choose_another),
                    onAction = actions.onChooseAnother
                )

                is ImportUiState.Cancelled -> EmptyState(
                    icon = HashiyaIcons.Import,
                    title = stringResource(R.string.import_cancelled),
                    message = null,
                    actionLabel = stringResource(R.string.import_choose_another),
                    onAction = actions.onChooseAnother
                )
            }
        }
    }
}

private fun ImportFailure.message(): Int = when (this) {
    ImportFailure.Unsupported -> R.string.import_failed_unsupported
    ImportFailure.TooLarge -> R.string.import_failed_too_large
    ImportFailure.Unreadable -> R.string.import_failed_unreadable
    ImportFailure.NothingToImport -> R.string.import_failed_nothing
}

@Composable
private fun Working(text: String, onCancel: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(Modifier.testTag(IMPORT_PROGRESS_TAG))
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onCancel != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.import_cancel)) }
        }
    }
}

@Composable
private fun Preview(state: ImportUiState.Ready, actions: ImportActions) {
    val counts = state.job.counts
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(state.job.fileName, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        CountRow(R.string.import_found, counts.found, always = true)
        CountRow(R.string.import_matched, counts.matched)
        CountRow(R.string.import_local, counts.local)
        CountRow(R.string.import_in_library, counts.inLibrary)
        CountRow(R.string.import_repeated, counts.repeated)
        CountRow(R.string.import_unreadable, counts.unreadable)
        if (counts.notChecked > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                pluralStringResource(R.plurals.import_not_checked, counts.notChecked, counts.notChecked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = state.addToCollection, onValueChange = actions.onAddToCollectionChange, role = Role.Checkbox)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = state.addToCollection, onCheckedChange = null)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(stringResource(R.string.import_add_to_collection), style = MaterialTheme.typography.bodyLarge)
                Text(
                    state.job.collectionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = actions.onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text(pluralStringResource(R.plurals.import_confirm, counts.toImport, counts.toImport))
        }
        TextButton(onClick = actions.onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_cancel))
        }
    }
}

/** A count row; rows with 0 are hidden unless [always]. */
@Composable
private fun CountRow(label: Int, count: Int, always: Boolean = false) {
    if (count == 0 && !always) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.import_count, count), style = MaterialTheme.typography.titleMedium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Importing(job: ImportJob, onCancel: () -> Unit) {
    val total = job.counts.toImport.coerceAtLeast(1)
    val done = job.processed.coerceIn(0, total)
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LinearProgressIndicator(
            progress = { done.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(IMPORT_PROGRESS_TAG)
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.import_progress, done, job.counts.toImport), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.import_continues),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.import_cancel)) }
    }
}

@Composable
private fun Done(job: ImportJob, actions: ImportActions) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(HashiyaIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            pluralStringResource(R.plurals.import_done, job.imported, job.imported),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        if (job.collectionId != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.import_done_collection, job.collectionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = actions.onShowLibrary) { Text(stringResource(R.string.import_show_library)) }
        TextButton(onClick = actions.onChooseAnother) { Text(stringResource(R.string.import_another)) }
    }
}
