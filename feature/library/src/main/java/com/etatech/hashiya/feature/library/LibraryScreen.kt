package com.etatech.hashiya.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.feature.library.components.LibrarySearchField
import com.etatech.hashiya.feature.library.components.ReadingStatusBadge
import com.etatech.hashiya.feature.library.components.StatusFilterChips

@Composable
internal fun LibraryScreen(
    onGoToSearch: () -> Unit,
    onAddPaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPaper: (openAlexId: String) -> Unit,
    removeRequest: String? = null,
    onRemoveRequestHandled: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    LaunchedEffect(removeRequest) {
        removeRequest?.let { openAlexId ->
            viewModel.onRemoveRequested(openAlexId)
            onRemoveRequestHandled()
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    LibraryContent(
        uiState = uiState,
        pendingUndo = pendingUndo,
        message = message,
        actions = LibraryActions(
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::onSearch,
            onClearQuery = viewModel::onClearQuery,
            onStatusFilterChange = viewModel::onStatusFilterChange,
            onClearSearchAndFilters = viewModel::onClearSearchAndFilters,
            onPaperClick = { paper -> onOpenPaper(paper.openAlexId) },
            onStatusChange = viewModel::onStatusChange,
            onRemove = viewModel::onRemove,
            onUndo = viewModel::onUndoRemove,
            onUndoDismissed = viewModel::onUndoDismissed,
            onMessageShown = viewModel::onMessageShown,
            onGoToSearch = onGoToSearch,
            onAddPaper = onAddPaper,
            onOpenSettings = onOpenSettings
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryContent(
    uiState: LibraryUiState,
    pendingUndo: RemovedPaper?,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    message: LibraryMessage? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val removedMessage = stringResource(R.string.library_removed)
    val undoLabel = stringResource(R.string.library_undo)
    // Keyed on the removed paper: each removal restarts the snackbar with its own full timeout.
    LaunchedEffect(pendingUndo) {
        if (pendingUndo != null) {
            val result = snackbarHostState.showSnackbar(removedMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) actions.onUndo() else actions.onUndoDismissed()
        }
    }
    val statusUpdateFailed = stringResource(R.string.library_status_update_failed)
    LaunchedEffect(message) {
        when (message) {
            LibraryMessage.StatusUpdateFailed -> {
                snackbarHostState.showSnackbar(statusUpdateFailed)
                actions.onMessageShown()
            }

            null -> Unit
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(HashiyaIcons.Settings, contentDescription = stringResource(R.string.library_settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAddPaper,
                icon = { Icon(HashiyaIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_add_paper)) }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            val filter = when (uiState) {
                is LibraryUiState.Papers -> uiState.filter
                is LibraryUiState.NoMatches -> uiState.filter
                LibraryUiState.Loading, LibraryUiState.Empty -> null
            }
            // The same place in the tree for Papers and NoMatches, so the field keeps focus when nothing matches.
            if (filter != null) {
                LibrarySearchField(filter.query, actions.onQueryChange, actions.onSearch, actions.onClearQuery)
                StatusFilterChips(filter, actions.onStatusFilterChange)
            }
            Box(Modifier.fillMaxSize()) {
                when (uiState) {
                    LibraryUiState.Loading -> LoadingSkeleton()

                    LibraryUiState.Empty -> EmptyState(
                        icon = HashiyaIcons.Library,
                        title = stringResource(R.string.library_empty_title),
                        message = stringResource(R.string.library_empty_message),
                        actionLabel = stringResource(R.string.library_go_to_search),
                        onAction = actions.onGoToSearch
                    )

                    is LibraryUiState.NoMatches -> EmptyState(
                        icon = HashiyaIcons.SearchOff,
                        title = stringResource(R.string.library_no_matches_title),
                        message = null,
                        actionLabel = stringResource(R.string.library_no_matches_action),
                        onAction = actions.onClearSearchAndFilters
                    )

                    is LibraryUiState.Papers -> PaperList(uiState.papers, actions)
                }
            }
        }
    }
}

// Scaffold's floatingActionButton doesn't reserve content padding for the FAB, so the list must
// leave room itself: the extended FAB is 56dp tall with a 16dp margin, plus a little breathing room.
private val FAB_CLEARANCE = PaddingValues(bottom = 88.dp)

@Composable
private fun PaperList(papers: List<LibraryPaper>, actions: LibraryActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = FAB_CLEARANCE) {
        item {
            Text(
                pluralStringResource(R.plurals.library_paper_count, papers.size, papers.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(papers, key = { it.paper.openAlexId }) { item ->
            SwipeToRemove(onRemove = { actions.onRemove(item.paper) }) {
                LibraryRow(
                    item = item,
                    onClick = { actions.onPaperClick(item.paper) },
                    onStatusChange = { status -> actions.onStatusChange(item.paper, status) }
                )
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
private fun LibraryRow(item: LibraryPaper, onClick: () -> Unit, onStatusChange: (ReadingStatus) -> Unit) {
    val paper = item.paper
    val firstAuthor = paper.authors.firstOrNull()?.name
    val authorText = when {
        firstAuthor == null -> null
        paper.authors.size == 1 -> firstAuthor
        else -> stringResource(R.string.library_et_al, firstAuthor)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                paperTitle(paper),
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Full width so the text aligns by its own direction, even on one line.
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                listOfNotNull(authorText, paper.year?.toString(), paper.venue).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(12.dp))
        ReadingStatusBadge(item.status, onStatusChange)
    }
}
