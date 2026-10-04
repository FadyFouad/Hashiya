package com.etatech.hashiya.feature.settings.restore

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.OpenFailure
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.layout.centeredMaxWidth
import com.etatech.hashiya.core.designsystem.layout.horizontalMargin
import com.etatech.hashiya.feature.settings.R
import java.util.Date

@Composable
internal fun RestoreScreen(onDone: () -> Unit, viewModel: RestoreViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val leave = {
        viewModel.onCancel()
        onDone()
    }
    // While applying, leaving would look like cancelling, which a merge can't be: Back waits.
    BackHandler(enabled = uiState is RestoreUiState.Applying) {}
    RestoreContent(uiState, onConfirm = viewModel::onConfirm, onLeave = leave)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RestoreContent(uiState: RestoreUiState, onConfirm: () -> Unit, onLeave: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.restore_title)) },
                navigationIcon = {
                    if (uiState !is RestoreUiState.Applying) {
                        IconButton(onClick = onLeave) { Icon(HashiyaIcons.Back, contentDescription = stringResource(R.string.restore_back)) }
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
            when (uiState) {
                RestoreUiState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.restore_reading))
                }

                is RestoreUiState.Invalid -> {
                    Text(invalidText(uiState.reason), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLeave) { Text(stringResource(R.string.restore_done)) }
                }

                is RestoreUiState.Preview -> PreviewBody(uiState, onConfirm, onLeave)

                is RestoreUiState.Applying -> {
                    Text(stringResource(R.string.restore_applying))
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { uiState.progress }, modifier = Modifier.fillMaxWidth())
                }

                is RestoreUiState.Done -> {
                    val result = uiState.result
                    Text(stringResource(R.string.restore_done_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.restore_done_body, result.papersAdded, result.notesAdded, result.collectionsCreated, result.pdfsAdded))
                    if (result.pdfsMissing > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(pluralStringResource(R.plurals.restore_done_missing_pdfs, result.pdfsMissing, result.pdfsMissing))
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLeave) { Text(stringResource(R.string.restore_done)) }
                }

                is RestoreUiState.Failed -> {
                    Text(
                        stringResource(if (uiState.failure == BackupFailure.NoSpace) R.string.restore_failed_space else R.string.restore_failed),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLeave) { Text(stringResource(R.string.restore_done)) }
                }
            }
        }
    }
}

@Composable
private fun PreviewBody(state: RestoreUiState.Preview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val preview = state.preview
    preview.exportedAt?.let { millis ->
        Text(
            stringResource(R.string.restore_from_date, DateFormat.getMediumDateFormat(context).format(Date(millis))),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
    }
    Text(
        stringResource(
            R.string.restore_counts,
            pluralStringResource(R.plurals.settings_export_papers, preview.papers, preview.papers),
            pluralStringResource(R.plurals.settings_export_collections, preview.collections, preview.collections),
            pluralStringResource(R.plurals.restore_pdfs, preview.pdfs, preview.pdfs)
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    Text(pluralStringResource(R.plurals.restore_new_papers, preview.newPapers, preview.newPapers))
    if (preview.existingPapers > 0) {
        Text(pluralStringResource(R.plurals.restore_existing_papers, preview.existingPapers, preview.existingPapers))
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.restore_keeps_library),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onConfirm) { Text(stringResource(R.string.restore_add)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.restore_cancel)) }
    }
}

@Composable
private fun invalidText(reason: OpenFailure): String = stringResource(
    when (reason) {
        OpenFailure.NotABackup -> R.string.restore_not_backup
        OpenFailure.NewerFormat -> R.string.restore_newer
        OpenFailure.Damaged -> R.string.restore_damaged
        OpenFailure.Unreadable -> R.string.restore_unreadable
    }
)
