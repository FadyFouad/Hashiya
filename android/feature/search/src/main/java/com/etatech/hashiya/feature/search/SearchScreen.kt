package com.etatech.hashiya.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.etatech.hashiya.core.data.repository.SearchException
import com.etatech.hashiya.core.designsystem.R as DesignR
import com.etatech.hashiya.core.designsystem.component.LoadingSkeleton
import com.etatech.hashiya.core.designsystem.component.PaperCard
import com.etatech.hashiya.core.designsystem.component.PaperPreviewPane
import com.etatech.hashiya.core.designsystem.component.PaperPreviewSheet
import com.etatech.hashiya.core.designsystem.component.SecondaryClickMenu
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.layout.ListDetailPanes
import com.etatech.hashiya.core.designsystem.layout.showsTwoPanes
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.feature.search.components.FilterChipRow
import com.etatech.hashiya.feature.search.components.IdleState
import com.etatech.hashiya.feature.search.components.LookupBody
import com.etatech.hashiya.feature.search.components.NoResultsState
import com.etatech.hashiya.feature.search.components.SearchErrorState
import com.etatech.hashiya.feature.search.components.SearchField
import com.etatech.hashiya.feature.search.components.SearchNoteBanner
import java.text.NumberFormat
import java.util.Calendar

@Composable
internal fun SearchScreen(
    onOpenSettings: () -> Unit,
    onOpenPaper: (openAlexId: String) -> Unit,
    removeRequest: String? = null,
    onRemoveRequestHandled: () -> Unit = {},
    findRequested: Boolean = false,
    onFindHandled: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    LaunchedEffect(findRequested) {
        if (findRequested) {
            viewModel.onFocusRequested()
            onFindHandled()
        }
    }
    LaunchedEffect(removeRequest) {
        removeRequest?.let { openAlexId ->
            viewModel.onRemoveRequested(openAlexId)
            onRemoveRequestHandled()
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val lookupState by viewModel.lookupState.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val focusSearch by viewModel.focusSearch.collectAsStateWithLifecycle()
    val papers = viewModel.papers.collectAsLazyPagingItems()
    val uriHandler = LocalUriHandler.current
    SearchContent(
        uiState = uiState,
        papers = papers,
        savedIds = savedIds,
        selectedItem = selectedItem,
        message = message,
        lookupState = lookupState,
        note = note,
        focusSearch = focusSearch,
        actions = SearchActions(
            onOpenDetails = { paper -> onOpenPaper(paper.openAlexId) },
            onTextChange = viewModel::onTextChange,
            onSearchAction = viewModel::onSearchAction,
            onSuggestion = viewModel::onSuggestion,
            onSortChange = viewModel::onSortChange,
            onYearFilterChange = viewModel::onYearFilterChange,
            onOpenAccessToggle = viewModel::onOpenAccessToggle,
            onClearFilters = viewModel::onClearFilters,
            onPaperClick = viewModel::onPaperClick,
            onToggleSave = viewModel::onToggleSave,
            onDismissPreview = viewModel::onDismissPreview,
            onOpenDoi = { doi -> runCatching { uriHandler.openUri("https://doi.org/$doi") } },
            onOpenSettings = onOpenSettings,
            onMessageShown = viewModel::onMessageShown,
            onRetryLookup = viewModel::onRetryLookup,
            onFocusHandled = viewModel::onFocusHandled
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchContent(
    uiState: SearchUiState,
    papers: LazyPagingItems<Paper>,
    savedIds: Set<String>,
    selectedItem: PaperItem?,
    message: SearchMessage?,
    actions: SearchActions,
    modifier: Modifier = Modifier,
    lookupState: LookupUiState? = null,
    note: SearchNote? = null,
    focusSearch: Boolean = false,
    currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            // Wait for the first frame so the field is attached and can take focus.
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
            actions.onFocusHandled()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.search_save_failed)
    val removeFailed = stringResource(R.string.search_remove_failed)
    LaunchedEffect(message) {
        val text = when (message) {
            SearchMessage.SaveFailed -> saveFailed
            SearchMessage.RemoveFailed -> removeFailed
            null -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(text)
        actions.onMessageShown()
    }

    // From 840dp the preview is a pane beside the results, showing the same selection the sheet shows.
    val twoPane = showsTwoPanes()
    val selectedId = if (twoPane) selectedItem?.paper?.openAlexId else null
    val results: @Composable (Modifier) -> Unit = { resultsModifier ->
        Scaffold(
            modifier = resultsModifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.search_title)) },
                    actions = {
                        IconButton(onClick = actions.onOpenSettings) {
                            Icon(HashiyaIcons.Settings, contentDescription = stringResource(R.string.search_settings))
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(Modifier.padding(padding)) {
                SearchField(uiState.text, actions.onTextChange, actions.onSearchAction, Modifier.focusRequester(focusRequester))
                note?.let { SearchNoteBanner(it) }
                if (lookupState == null) {
                    FilterChipRow(
                        sort = uiState.sort,
                        years = uiState.years,
                        openAccessOnly = uiState.openAccessOnly,
                        currentYear = currentYear,
                        onSortChange = actions.onSortChange,
                        onYearFilterChange = actions.onYearFilterChange,
                        onOpenAccessToggle = actions.onOpenAccessToggle
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    if (lookupState != null) {
                        LookupBody(
                            state = lookupState,
                            savedIds = savedIds,
                            onToggleSave = actions.onToggleSave,
                            onOpenDoi = actions.onOpenDoi,
                            onSearchTitle = actions.onSuggestion,
                            onRetry = actions.onRetryLookup,
                            onOpenSettings = actions.onOpenSettings
                        )
                    } else {
                        SearchBody(uiState, papers, savedIds, actions, selectedId)
                    }
                }
            }
        }
    }
    // Details is for saved papers only.
    val openDetails: (PaperItem) -> (() -> Unit)? = { item ->
        if (item.inLibrary) {
            {
                actions.onDismissPreview()
                actions.onOpenDetails(item.paper)
            }
        } else {
            null
        }
    }

    // Back (and Esc) closes the preview pane first, as it closes the sheet on a phone.
    BackHandler(enabled = twoPane && selectedItem != null) { actions.onDismissPreview() }

    if (twoPane) {
        ListDetailPanes(
            list = { results(Modifier) },
            detail = {
                PaperPreviewPane(
                    paper = selectedItem?.paper,
                    inLibrary = selectedItem?.inLibrary == true,
                    onClose = actions.onDismissPreview,
                    onToggleSave = { selectedItem?.let(actions.onToggleSave) },
                    onOpenDoi = actions.onOpenDoi,
                    onOpenDetails = selectedItem?.let(openDetails)
                )
            },
            modifier = modifier
        )
    } else {
        results(modifier)
        selectedItem?.let { item ->
            PaperPreviewSheet(
                paper = item.paper,
                inLibrary = item.inLibrary,
                onDismiss = actions.onDismissPreview,
                onToggleSave = { actions.onToggleSave(item) },
                onOpenDoi = actions.onOpenDoi,
                onOpenDetails = openDetails(item)
            )
        }
    }
}

@Composable
private fun SearchBody(
    uiState: SearchUiState,
    papers: LazyPagingItems<Paper>,
    savedIds: Set<String>,
    actions: SearchActions,
    selectedId: String?
) {
    // Branch on the first-page state before the item count: when a new query starts, the previous query's items
    // stay in the list until the new first page arrives, so its loading or error state must replace them.
    val refresh = papers.loadState.refresh
    when {
        uiState.isIdle -> IdleState(actions.onSuggestion)

        refresh is LoadState.Error -> SearchErrorState(
            error = refresh.error.asSearchError(),
            onRetry = papers::retry,
            onOpenSettings = actions.onOpenSettings
        )

        refresh is LoadState.Loading -> LoadingSkeleton()

        papers.itemCount == 0 -> NoResultsState(
            showClearFilters = uiState.hasActiveFilters,
            onClearFilters = actions.onClearFilters
        )

        else -> ResultsList(uiState.totalCount, papers, savedIds, actions, selectedId)
    }
}

@Composable
private fun ResultsList(
    totalCount: Long?,
    papers: LazyPagingItems<Paper>,
    savedIds: Set<String>,
    actions: SearchActions,
    selectedId: String?
) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(Modifier.fillMaxSize()) {
        if (totalCount != null) {
            item {
                Text(
                    pluralStringResource(
                        R.plurals.search_result_count,
                        totalCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        NumberFormat.getInstance(locale).format(totalCount)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
        // Keys must be unique: the paging source drops works OpenAlex returns on more than one page.
        items(count = papers.itemCount, key = papers.itemKey { it.openAlexId }) { index ->
            papers[index]?.let { paper ->
                val item = PaperItem(paper, inLibrary = paper.openAlexId in savedIds)
                SecondaryClickMenu(menu = { close -> ResultMenu(item, actions, close) }) {
                    PaperCard(
                        paper = paper,
                        inLibrary = item.inLibrary,
                        onClick = { actions.onPaperClick(paper) },
                        onSave = { actions.onToggleSave(item) },
                        selected = paper.openAlexId == selectedId
                    )
                }
            }
        }
        when (val append = papers.loadState.append) {
            is LoadState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is LoadState.Error -> item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.search_append_error), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = papers::retry) { Text(stringResource(R.string.search_retry)) }
                }
            }

            is LoadState.NotLoading -> Unit
        }
    }
}

/** A result's right-click menu: open, save or remove, and the DOI, without opening the preview first. */
@Composable
private fun ResultMenu(item: PaperItem, actions: SearchActions, close: () -> Unit) {
    if (item.inLibrary) {
        DropdownMenuItem(
            text = { Text(stringResource(DesignR.string.designsystem_open_details)) },
            onClick = {
                close()
                actions.onOpenDetails(item.paper)
            }
        )
    }
    DropdownMenuItem(
        text = {
            Text(
                stringResource(
                    if (item.inLibrary) DesignR.string.designsystem_remove_from_library else DesignR.string.designsystem_save_to_library
                )
            )
        },
        onClick = {
            close()
            actions.onToggleSave(item)
        }
    )
    item.paper.doi?.let { doi ->
        DropdownMenuItem(
            text = { Text(stringResource(DesignR.string.designsystem_open_doi)) },
            trailingIcon = { Icon(HashiyaIcons.OpenInNew, contentDescription = null) },
            onClick = {
                close()
                actions.onOpenDoi(doi)
            }
        )
    }
}

private fun Throwable.asSearchError(): SearchError = (this as? SearchException)?.error ?: SearchError.Unexpected
