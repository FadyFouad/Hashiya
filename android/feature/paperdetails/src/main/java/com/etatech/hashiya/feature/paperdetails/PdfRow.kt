package com.etatech.hashiya.feature.paperdetails

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.data.repository.DownloadFailure
import com.etatech.hashiya.core.data.repository.DownloadState
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource

/** What the PDF row on Details shows (spec §6). */
sealed interface PdfRowState {
    /** No PDF yet, and the paper has an open-access link. */
    data object Available : PdfRowState

    /** No PDF and no link. */
    data object None : PdfRowState

    data class Downloading(val bytes: Long, val totalBytes: Long?) : PdfRowState

    data class Stored(val pdf: PaperPdf) : PdfRowState

    data class Failed(val reason: DownloadFailure) : PdfRowState
}

/** Every action the PDF row can offer. */
internal enum class PdfAction { Read, Download, Cancel, Attach, TryAgain, OpenInBrowser, Replace, Remove, OpenLink }

/** The row's state with the paper's open-access [link], and the actions they allow. */
data class PdfRow(val state: PdfRowState, val link: String?) {
    /** The row's buttons: the first is the filled main action (Cancel while downloading stays outlined). */
    internal val primary: List<PdfAction>
        get() = when (state) {
            PdfRowState.Available -> listOf(PdfAction.Download)

            PdfRowState.None -> listOf(PdfAction.Attach)

            is PdfRowState.Downloading -> listOf(PdfAction.Cancel)

            is PdfRowState.Stored -> listOf(PdfAction.Read)

            is PdfRowState.Failed -> listOfNotNull(
                PdfAction.TryAgain.takeIf { link != null },
                PdfAction.OpenInBrowser.takeIf { link != null },
                PdfAction.Attach
            )
        }

    /** Shown in the row's overflow menu. */
    internal val overflow: List<PdfAction>
        get() = when (state) {
            PdfRowState.Available -> listOf(PdfAction.Attach)
            is PdfRowState.Stored -> listOfNotNull(PdfAction.Replace, PdfAction.Remove, PdfAction.OpenLink.takeIf { link != null })
            else -> emptyList()
        }
}

/**
 * A running download wins, then a stored file, then a failure. A [DownloadFailure.NoLink] failure means the link is
 * gone, so the row falls back to the no-PDF state.
 */
internal fun pdfRowState(pdf: PaperPdf?, download: DownloadState?, link: String?): PdfRowState = when {
    download is DownloadState.Running -> PdfRowState.Downloading(download.bytes, download.totalBytes)
    pdf != null -> PdfRowState.Stored(pdf)
    download is DownloadState.Failed && download.reason != DownloadFailure.NoLink -> PdfRowState.Failed(download.reason)
    link != null -> PdfRowState.Available
    else -> PdfRowState.None
}

internal const val PDF_ROW_TAG = "pdf_row"
internal const val PDF_MENU_TAG = "pdf_menu"

/**
 * The PDF row, the middle of the Details group: what is stored or happening, the row's own action as a filled button
 * (Download, Read or Attach, by state), and an overflow menu for the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfRowView(row: PdfRow, shape: Shape, onAction: (PdfAction) -> Unit) {
    val state = row.state
    val hasMenu = row.overflow.isNotEmpty()
    GroupedRowSurface(shape, Modifier.testTag(PDF_ROW_TAG)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = if (hasMenu) 4.dp else 16.dp, bottom = 16.dp)
        ) {
            Icon(
                HashiyaIcons.Pdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                // Level with the label when the menu button makes the row taller than its text.
                modifier = Modifier.padding(top = if (hasMenu) 8.dp else 0.dp)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = if (hasMenu) 8.dp else 0.dp)
            ) {
                GroupedRowLabel(stringResource(R.string.details_pdf))
                PdfSummary(state)
                if (row.primary.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.primary.forEachIndexed { index, action ->
                            if (index == 0 && action != PdfAction.Cancel) {
                                Button(onClick = { onAction(action) }) {
                                    action.icon?.let { icon ->
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    }
                                    Text(stringResource(action.labelRes))
                                }
                            } else {
                                OutlinedButton(onClick = { onAction(action) }) { Text(stringResource(action.labelRes)) }
                            }
                        }
                    }
                }
            }
            if (hasMenu) PdfOverflow(row.overflow, onAction)
        }
    }
}

@Composable
private fun PdfSummary(state: PdfRowState) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (state) {
        PdfRowState.Available -> Text(stringResource(R.string.details_pdf_available), style = MaterialTheme.typography.bodyLarge)

        PdfRowState.None -> Text(stringResource(R.string.details_pdf_none), style = MaterialTheme.typography.bodyLarge)

        is PdfRowState.Downloading -> {
            val total = state.totalBytes
            Text(
                if (total != null) {
                    stringResource(R.string.details_pdf_progress, size(state.bytes), size(total))
                } else {
                    size(state.bytes)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = muted
            )
            Spacer(Modifier.height(4.dp))
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (state.bytes.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        is PdfRowState.Stored -> Text(
            stringResource(
                if (state.pdf.source == PdfSource.Downloaded) R.string.details_pdf_downloaded else R.string.details_pdf_attached,
                size(state.pdf.sizeBytes)
            ),
            style = MaterialTheme.typography.bodyLarge
        )

        is PdfRowState.Failed -> {
            Text(
                stringResource(R.string.details_pdf_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(stringResource(state.reason.messageRes), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
private fun PdfOverflow(actions: List<PdfAction>, onAction: (PdfAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag(PDF_MENU_TAG)) {
            Icon(
                HashiyaIcons.MoreOptions,
                contentDescription = stringResource(R.string.details_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.labelRes)) },
                    onClick = {
                        expanded = false
                        onAction(action)
                    }
                )
            }
        }
    }
}

@get:StringRes
private val PdfAction.labelRes: Int
    get() = when (this) {
        PdfAction.Read -> R.string.details_pdf_read
        PdfAction.Download -> R.string.details_pdf_download
        PdfAction.Cancel -> R.string.details_pdf_cancel
        PdfAction.Attach -> R.string.details_pdf_attach
        PdfAction.TryAgain -> R.string.details_pdf_try_again
        PdfAction.OpenInBrowser -> R.string.details_pdf_open_browser
        PdfAction.Replace -> R.string.details_pdf_replace
        PdfAction.Remove -> R.string.details_pdf_remove
        PdfAction.OpenLink -> R.string.details_pdf_open_link
    }

/** The filled buttons' icons, as in the design: download, an open book, a plus. */
private val PdfAction.icon: ImageVector?
    get() = when (this) {
        PdfAction.Download -> HashiyaIcons.Download
        PdfAction.Read -> HashiyaIcons.Read
        PdfAction.Attach -> HashiyaIcons.Add
        else -> null
    }

@get:StringRes
private val DownloadFailure.messageRes: Int
    get() = when (this) {
        DownloadFailure.Offline -> R.string.details_pdf_offline

        DownloadFailure.NotPdf -> R.string.details_pdf_not_pdf

        DownloadFailure.TooLarge -> R.string.details_pdf_too_large

        DownloadFailure.Http -> R.string.details_pdf_http

        // The row shows the no-PDF state for NoLink (pdfRowState), so this is never on screen.
        DownloadFailure.NoLink -> R.string.details_pdf_failed
    }
