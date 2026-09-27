package com.etatech.hashiya.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.designsystem.component.EmptyState
import com.etatech.hashiya.core.designsystem.component.LoadingSkeleton
import com.etatech.hashiya.core.designsystem.component.PaperPreviewSheet
import com.etatech.hashiya.core.designsystem.component.paperTitle
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.Paper

@Composable
internal fun LibraryScreen(onGoToSearch: () -> Unit, onOpenSettings: () -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPaper by viewModel.selectedPaper.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LibraryContent(
        uiState = uiState,
        selectedPaper = selectedPaper,
        pendingUndo = pendingUndo,
        onPaperClick = viewModel::onPaperClick,
        onDismissPreview = viewModel::onDismissPreview,
        onRemove = viewModel::onRemove,
        onUndo = viewModel::onUndoRemove,
        onUndoDismissed = viewModel::onUndoDismissed,
        onGoToSearch = onGoToSearch,
        onOpenSettings = onOpenSettings,
        onOpenDoi = { doi -> uriHandler.openUri("https://doi.org/$doi") }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryContent(
    uiState: LibraryUiState,
    selectedPaper: Paper?,
    pendingUndo: RemovedPaper?,
    onPaperClick: (Paper) -> Unit,
    onDismissPreview: () -> Unit,
    onRemove: (Paper) -> Unit,
    onUndo: () -> Unit,
    onUndoDismissed: () -> Unit,
    onGoToSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDoi: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val removedMessage = stringResource(R.string.library_removed)
    val undoLabel = stringResource(R.string.library_undo)
    // Keyed on the removed paper: each removal restarts the snackbar with its own full timeout.
    LaunchedEffect(pendingUndo) {
        if (pendingUndo != null) {
            val result = snackbarHostState.showSnackbar(removedMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndo() else onUndoDismissed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(HashiyaIcons.Settings, contentDescription = stringResource(R.string.library_settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (uiState) {
                LibraryUiState.Loading -> LoadingSkeleton()

                LibraryUiState.Empty -> EmptyState(
                    icon = HashiyaIcons.Library,
                    title = stringResource(R.string.library_empty_title),
                    message = stringResource(R.string.library_empty_message),
                    actionLabel = stringResource(R.string.library_go_to_search),
                    onAction = onGoToSearch
                )

                is LibraryUiState.Papers -> PaperList(uiState.papers, onPaperClick, onRemove)
            }
        }
    }

    selectedPaper?.let { paper ->
        PaperPreviewSheet(
            paper = paper,
            inLibrary = true,
            onDismiss = onDismissPreview,
            onToggleSave = { onRemove(paper) },
            onOpenDoi = onOpenDoi
        )
    }
}

@Composable
private fun PaperList(papers: List<Paper>, onPaperClick: (Paper) -> Unit, onRemove: (Paper) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                pluralStringResource(R.plurals.library_paper_count, papers.size, papers.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(papers, key = { it.openAlexId }) { paper ->
            SwipeToRemove(onRemove = { onRemove(paper) }) {
                LibraryRow(paper, onClick = { onPaperClick(paper) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable () -> Unit) {
    // Deliberately not rememberSwipeToDismissBoxState(): that one is saveable, so when Undo brings the same key
    // back, the lazy list restores its dismissed value and SwipeToDismissBox removes the paper again.
    val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold) }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onRemove() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    HashiyaIcons.Delete,
                    contentDescription = stringResource(R.string.library_remove),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { content() }
}

@Composable
private fun LibraryRow(paper: Paper, onClick: () -> Unit) {
    val firstAuthor = paper.authors.firstOrNull()?.name
    val authorText = when {
        firstAuthor == null -> null
        paper.authors.size == 1 -> firstAuthor
        else -> stringResource(R.string.library_et_al, firstAuthor)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            paperTitle(paper),
            style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(authorText, paper.year?.toString(), paper.venue).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
