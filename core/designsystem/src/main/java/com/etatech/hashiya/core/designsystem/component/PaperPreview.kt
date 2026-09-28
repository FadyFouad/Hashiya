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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperPreviewSheet(paper: Paper, inLibrary: Boolean, onDismiss: () -> Unit, onToggleSave: () -> Unit, onOpenDoi: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PaperPreviewContent(paper, inLibrary, onToggleSave, onOpenDoi)
    }
}

/** The sheet's body, separate so it can be tested and screenshotted without a window. */
@Composable
fun PaperPreviewContent(
    paper: Paper,
    inLibrary: Boolean,
    onToggleSave: () -> Unit,
    onOpenDoi: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Paper text is full width so it aligns by its own direction (Latin left, Arabic right) in either locale.
    val contentText = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
    Column(modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(
                paperTitle(paper),
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            if (paper.authors.isNotEmpty()) {
                Text(paper.authors.joinToString(", ") { it.name }, style = contentText, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
            }
            Text(
                listOfNotNull(
                    paper.venue,
                    paper.year?.toString(),
                    stringResource(R.string.designsystem_citations, fullCount(paper.citationCount))
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            if (paper.isOpenAccess) {
                Spacer(Modifier.height(8.dp))
                StatusBadge(
                    text = stringResource(
                        if (paper.openAccessPdfUrl != null) R.string.designsystem_open_access_pdf else R.string.designsystem_open_access
                    ),
                    kind = BadgeKind.OpenAccess
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.designsystem_abstract),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                paper.abstract ?: stringResource(R.string.designsystem_no_abstract),
                style = contentText,
                color = if (paper.abstract == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
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
