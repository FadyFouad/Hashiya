package com.etatech.hashiya.feature.library

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.designsystem.R as DesignR
import com.etatech.hashiya.core.designsystem.component.CollectionNameDialog
import com.etatech.hashiya.core.designsystem.component.EmptyState
import com.etatech.hashiya.core.designsystem.component.LoadingSkeleton
import com.etatech.hashiya.core.designsystem.component.PaperPreviewSheet
import com.etatech.hashiya.core.designsystem.component.paperTitle
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.feature.library.components.CollectionSelectorSheet
import com.etatech.hashiya.feature.library.components.LibrarySearchField
import com.etatech.hashiya.feature.library.components.ReadingStatusBadge
import com.etatech.hashiya.feature.library.components.StatusFilterChips
import com.etatech.hashiya.feature.library.export.bibShareIntent
import com.etatech.hashiya.feature.library.export.writeBibFile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val header by viewModel.header.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val pendingCollectionUndo by viewModel.pendingCollectionUndo.collectAsStateWithLifecycle()
    val exportReady by viewModel.exportReady.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Coming back from the share sheet is when "may be incomplete" can be read.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }
    LaunchedEffect(exportReady) {
        val export = exportReady ?: return@LaunchedEffect
        try {
            val uri = withContext(Dispatchers.IO) { writeBibFile(context, export) }
            context.startActivity(Intent.createChooser(bibShareIntent(uri, export.fileName), null))
            viewModel.onExportShared()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            viewModel.onExportFailed()
        }
    }
    LibraryContent(
        uiState = uiState,
        pendingUndo = pendingUndo,
        message = message,
        header = header,
        dialog = dialog,
        pendingCollectionUndo = pendingCollectionUndo,
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
            onOpenSettings = onOpenSettings,
            onSelectCollection = viewModel::onSelectCollection,
            onNewCollection = viewModel::onNewCollection,
            onRenameCollection = viewModel::onRenameCollection,
            onDeleteCollection = viewModel::onDeleteCollection,
            onDialogNameEdited = viewModel::onDialogNameEdited,
            onDialogConfirm = viewModel::onDialogConfirm,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDialogDismiss = viewModel::onDialogDismiss,
            onUndoCollection = viewModel::onUndoCollectionRemove,
            onCollectionUndoDismissed = viewModel::onCollectionUndoDismissed,
            onExport = viewModel::onExport
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
    message: LibraryMessage? = null,
    header: LibraryHeader = LibraryHeader(),
    dialog: CollectionDialog? = null,
    pendingCollectionUndo: CollectionRemoval? = null
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
    val removedFromCollection = pendingCollectionUndo?.let { stringResource(R.string.library_removed_from_collection, it.collection.name) }
    LaunchedEffect(pendingCollectionUndo) {
        if (pendingCollectionUndo != null && removedFromCollection != null) {
            val result = snackbarHostState.showSnackbar(removedFromCollection, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) actions.onUndoCollection() else actions.onCollectionUndoDismissed()
        }
    }
    val statusUpdateFailed = stringResource(R.string.library_status_update_failed)
    val collectionsUpdateFailed = stringResource(R.string.library_collections_update_failed)
    val exportFailed = stringResource(R.string.library_export_failed)
    val exportIncomplete = stringResource(R.string.library_export_incomplete)
    LaunchedEffect(message) {
        val (text, duration) = when (message) {
            LibraryMessage.StatusUpdateFailed -> statusUpdateFailed to SnackbarDuration.Short
            LibraryMessage.CollectionsUpdateFailed -> collectionsUpdateFailed to SnackbarDuration.Short
            LibraryMessage.ExportFailed -> exportFailed to SnackbarDuration.Short
            LibraryMessage.ExportIncomplete -> exportIncomplete to SnackbarDuration.Long
            null -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(text, duration = duration)
        actions.onMessageShown()
    }

    var selectorOpen by rememberSaveable { mutableStateOf(false) }
    val showsCollections = uiState !is LibraryUiState.Empty && uiState !is LibraryUiState.Loading
    if (selectorOpen && showsCollections) {
        CollectionSelectorSheet(
            header = header,
            onSelect = actions.onSelectCollection,
            onNew = actions.onNewCollection,
            onRename = actions.onRenameCollection,
            onDelete = actions.onDeleteCollection,
            onDismiss = { selectorOpen = false }
        )
    }
    CollectionDialogs(dialog, actions)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (showsCollections) {
                        val name = header.selected?.name ?: stringResource(R.string.library_all_papers)
                        CollectionTitle(name, onClick = { selectorOpen = true })
                    } else {
                        Text(stringResource(R.string.library_title))
                    }
                },
                actions = {
                    if (showsCollections && header.viewSize > 0) {
                        if (header.exporting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .size(24.dp)
                                    .testTag(EXPORT_PROGRESS_TAG)
                            )
                        } else {
                            IconButton(onClick = actions.onExport) {
                                Icon(HashiyaIcons.Export, contentDescription = stringResource(R.string.library_export_bib))
                            }
                        }
                    }
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
                LibraryUiState.Loading, LibraryUiState.Empty, is LibraryUiState.CollectionEmpty -> null
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

                    // No action: papers are added to a collection from their Details screen.
                    is LibraryUiState.CollectionEmpty -> EmptyState(
                        icon = HashiyaIcons.Collection,
                        title = stringResource(R.string.library_collection_empty),
                        message = null
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

internal const val EXPORT_PROGRESS_TAG = "export_progress"

@Composable
private fun CollectionTitle(name: String, onClick: () -> Unit) {
    val chooseLabel = stringResource(R.string.library_choose_collection)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClickLabel = chooseLabel, role = Role.Button, onClick = onClick)
    ) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(HashiyaIcons.ArrowDropDown, contentDescription = null)
    }
}

/** The name dialog is keyed per dialog, because it keeps the typed text in rememberSaveable, which ignores a new initialName. */
@Composable
private fun CollectionDialogs(dialog: CollectionDialog?, actions: LibraryActions) {
    when (dialog) {
        null -> Unit

        is CollectionDialog.New -> key("new") {
            CollectionNameDialog(
                initialName = null,
                nameTaken = dialog.nameTaken,
                onNameEdited = actions.onDialogNameEdited,
                onConfirm = actions.onDialogConfirm,
                onDismiss = actions.onDialogDismiss
            )
        }

        is CollectionDialog.Rename -> key("rename", dialog.collection.id) {
            CollectionNameDialog(
                initialName = dialog.collection.name,
                nameTaken = dialog.nameTaken,
                onNameEdited = actions.onDialogNameEdited,
                onConfirm = actions.onDialogConfirm,
                onDismiss = actions.onDialogDismiss
            )
        }

        is CollectionDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = actions.onDialogDismiss,
            title = { Text(stringResource(R.string.library_delete_collection_title, dialog.collection.name)) },
            text = { Text(stringResource(R.string.library_delete_collection_message)) },
            confirmButton = { TextButton(onClick = actions.onConfirmDelete) { Text(stringResource(R.string.library_delete)) } },
            dismissButton = { TextButton(onClick = actions.onDialogDismiss) { Text(stringResource(DesignR.string.collection_cancel)) } }
        )
    }
}
