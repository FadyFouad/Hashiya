package com.etatech.hashiya.feature.search.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.component.EmptyState
import com.etatech.hashiya.core.designsystem.component.LoadingSkeleton
import com.etatech.hashiya.core.designsystem.component.PaperPreviewContent
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.feature.search.LookupUiState
import com.etatech.hashiya.feature.search.PaperItem
import com.etatech.hashiya.feature.search.R
import com.etatech.hashiya.feature.search.SearchNote

private const val MAX_TITLE_IN_BUTTON = 60

/** Search's body in ID mode: the found paper's preview is shown in the screen, not in a sheet. */
@Composable
internal fun LookupBody(
    state: LookupUiState,
    savedIds: Set<String>,
    onToggleSave: (PaperItem) -> Unit,
    onOpenDoi: (String) -> Unit,
    onSearchTitle: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is LookupUiState.Looking -> Column(modifier.fillMaxWidth()) {
            Text(
                text = lookingLabel(state.identifier),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LoadingSkeleton(rows = 1)
        }

        is LookupUiState.Found -> {
            val item = PaperItem(state.paper, inLibrary = state.paper.openAlexId in savedIds)
            PaperPreviewContent(
                paper = state.paper,
                inLibrary = item.inLibrary,
                onToggleSave = { onToggleSave(item) },
                onOpenDoi = onOpenDoi,
                modifier = modifier.padding(top = 8.dp)
            )
        }

        is LookupUiState.NotFound -> EmptyState(
            icon = HashiyaIcons.SearchOff,
            title = stringResource(notFoundTitle(state.identifier)),
            message = stringResource(R.string.search_lookup_not_found_message),
            modifier = modifier,
            actionLabel = state.searchTitle?.let { stringResource(R.string.search_lookup_search_title, shortTitle(it)) },
            onAction = { state.searchTitle?.let(onSearchTitle) }
        )

        is LookupUiState.Failed -> SearchErrorState(state.error, onRetry, onOpenSettings, modifier)
    }
}

/** Why Search opened the way it did after a share. */
@Composable
internal fun SearchNoteBanner(note: SearchNote, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            when (note) {
                SearchNote.NoIdInShare -> R.string.search_note_no_id
                SearchNote.NothingInShare -> R.string.search_note_nothing
            }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun lookingLabel(identifier: PaperIdentifier): String = when (identifier) {
    is PaperIdentifier.Doi -> stringResource(R.string.search_lookup_looking_doi, identifier.value)
    is PaperIdentifier.Arxiv -> stringResource(R.string.search_lookup_looking_arxiv, identifier.id)
}

@StringRes
private fun notFoundTitle(identifier: PaperIdentifier): Int = when (identifier) {
    is PaperIdentifier.Doi -> R.string.search_lookup_not_found_doi
    is PaperIdentifier.Arxiv -> R.string.search_lookup_not_found_arxiv
}

internal fun shortTitle(title: String): String =
    if (title.length <= MAX_TITLE_IN_BUTTON) title else title.take(MAX_TITLE_IN_BUTTON - 1).trimEnd() + "…"
