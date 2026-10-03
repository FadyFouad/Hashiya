package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.layout.ControlMaxWidth
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperPreviewSheet(
    paper: Paper,
    inLibrary: Boolean,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenDoi: (String) -> Unit,
    status: ReadingStatus? = null,
    onStatusChange: (ReadingStatus) -> Unit = {},
    onOpenDetails: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PaperPreviewContent(
            paper,
            inLibrary,
            onToggleSave,
            onOpenDoi,
            status = status,
            onStatusChange = onStatusChange,
            onOpenDetails = onOpenDetails
        )
    }
}

/**
 * The sheet's body, separate so it can be tested and screenshotted without a window.
 * With a [status] (the Library), a To read · Reading · Read selector sits above the buttons; Search passes none.
 * With [onOpenDetails] (a saved paper in Search), an Open details button sits above the other buttons.
 */
@Composable
fun PaperPreviewContent(
    paper: Paper,
    inLibrary: Boolean,
    onToggleSave: () -> Unit,
    onOpenDoi: (String) -> Unit,
    modifier: Modifier = Modifier,
    status: ReadingStatus? = null,
    onStatusChange: (ReadingStatus) -> Unit = {},
    onOpenDetails: (() -> Unit)? = null
) {
    Column(modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            PaperHeader(paper)
            Spacer(Modifier.height(16.dp))
            PaperAbstract(paper)
        }
        if (status != null) {
            Spacer(Modifier.height(16.dp))
            ReadingStatusSelector(status, onStatusChange)
        }
        if (onOpenDetails != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onOpenDetails, modifier = Modifier.widthIn(max = ControlMaxWidth).fillMaxWidth()) {
                Text(stringResource(R.string.designsystem_open_details))
            }
        }
        Spacer(Modifier.height(if (onOpenDetails != null) 8.dp else 16.dp))
        Row(Modifier.widthIn(max = ControlMaxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            paper.doi?.let { doi ->
                OutlinedButton(onClick = { onOpenDoi(doi) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.designsystem_open_doi))
                    Spacer(Modifier.width(6.dp))
                    Icon(HashiyaIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            Button(onClick = onToggleSave, modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (inLibrary) R.string.designsystem_remove_from_library else R.string.designsystem_save_to_library
                    )
                )
            }
        }
    }
}

/**
 * The preview as the detail pane beside Search's results on wide windows, in place of the sheet. [onClose] clears
 * the selection; with no [paper], a placeholder asks to pick one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperPreviewPane(
    paper: Paper?,
    inLibrary: Boolean,
    onClose: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenDoi: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDetails: (() -> Unit)? = null
) {
    if (paper == null) {
        NoPaperSelected(stringResource(R.string.designsystem_pick_result_message), modifier)
        return
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(HashiyaIcons.Close, contentDescription = stringResource(R.string.designsystem_close_preview))
                    }
                }
            )
        }
    ) { padding ->
        PaperPreviewContent(
            paper,
            inLibrary,
            onToggleSave,
            onOpenDoi,
            modifier = Modifier.padding(padding),
            onOpenDetails = onOpenDetails
        )
    }
}

/** A detail pane's placeholder while nothing is selected. */
@Composable
fun NoPaperSelected(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = HashiyaIcons.Library,
            title = stringResource(R.string.designsystem_no_paper_selected),
            message = message
        )
    }
}
