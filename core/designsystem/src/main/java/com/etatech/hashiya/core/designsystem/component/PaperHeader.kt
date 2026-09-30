package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.model.Paper

/**
 * The title, every author, venue · year · citations and the open access badge: the top of the preview sheet and of Details.
 * Paper text is full width so it aligns by its own direction (Latin left, Arabic right) in either locale.
 */
@Composable
fun PaperHeader(paper: Paper, modifier: Modifier = Modifier) {
    val contentText = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
    Column(modifier) {
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
    }
}

/** The "Abstract" label and the full abstract, or "No abstract available". */
@Composable
fun PaperAbstract(paper: Paper, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stringResource(R.string.designsystem_abstract),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            paper.abstract ?: stringResource(R.string.designsystem_no_abstract),
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            color = if (paper.abstract == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
