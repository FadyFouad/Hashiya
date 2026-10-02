package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
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
            FilledTonalButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.designsystem_open_details))
            }
        }
        Spacer(Modifier.height(if (onOpenDetails != null) 8.dp else 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
