package com.etatech.hashiya.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.data.backup.BackupSummary

@Composable
internal fun BackupSection(state: BackupUiState, onExportClick: () -> Unit, onRestoreClick: () -> Unit) {
    Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_backup_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExportClick, enabled = (state.summary?.papers ?: 0) > 0) {
            Text(stringResource(R.string.settings_export_library))
        }
        OutlinedButton(onClick = onRestoreClick) { Text(stringResource(R.string.settings_restore_backup)) }
    }
    if (state.export == ExportState.Saving) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_saving_backup), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/** Shown while choosing and building; the save dialog takes over after that. */
@Composable
internal fun ExportDialog(
    summary: BackupSummary,
    export: ExportState,
    onIncludePdfsChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val building = export as? ExportState.Building
    val includePdfs = (export as? ExportState.Choosing)?.includePdfs ?: building?.includePdfs ?: false
    AlertDialog(
        onDismissRequest = { if (building == null) onDismiss() else onCancel() },
        title = { Text(stringResource(R.string.settings_export_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.settings_export_counts,
                        pluralStringResource(R.plurals.settings_export_papers, summary.papers, summary.papers),
                        pluralStringResource(R.plurals.settings_export_collections, summary.collections, summary.collections)
                    )
                )
                if (summary.pdfCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = includePdfs,
                                enabled = building == null,
                                role = Role.Switch,
                                onValueChange = onIncludePdfsChange
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_export_include_pdfs))
                            Text(
                                pluralStringResource(
                                    R.plurals.settings_export_pdfs_size,
                                    summary.pdfCount,
                                    summary.pdfCount,
                                    Formatter.formatShortFileSize(context, summary.pdfBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = includePdfs, onCheckedChange = null, enabled = building == null)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_export_no_settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (building != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.settings_exporting), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { building.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = building == null) { Text(stringResource(R.string.settings_export)) }
        },
        dismissButton = {
            TextButton(onClick = if (building == null) onDismiss else onCancel) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}
