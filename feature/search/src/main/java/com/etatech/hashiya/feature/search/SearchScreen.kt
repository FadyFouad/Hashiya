package com.etatech.hashiya.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import com.etatech.hashiya.core.designsystem.component.LoadingSkeleton
import com.etatech.hashiya.core.designsystem.component.PaperCard
import com.etatech.hashiya.core.designsystem.component.PaperPreviewSheet
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.feature.search.components.FilterChipRow
import com.etatech.hashiya.feature.search.components.IdleState
import com.etatech.hashiya.feature.search.components.NoResultsState
import com.etatech.hashiya.feature.search.components.SearchErrorState
import com.etatech.hashiya.feature.search.components.SearchField
import java.text.NumberFormat
import java.util.Calendar

@Composable
internal fun SearchScreen(onOpenSettings: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val papers = viewModel.papers.collectAsLazyPagingItems()
    val uriHandler = LocalUriHandler.current
    SearchContent(
        uiState = uiState,
        papers = papers,
        savedIds = savedIds,
        selectedItem = selectedItem,
        message = message,
        actions = SearchActions(
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
            onOpenDoi = { doi -> uriHandler.openUri("https://doi.org/$doi") },
            onOpenSettings = onOpenSettings,
            onMessageShown = viewModel::onMessageShown
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
    currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.search_save_failed)
    LaunchedEffect(message) {
        if (message == SearchMessage.SaveFailed) {
            snackbarHostState.showSnackbar(saveFailed)
            actions.onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            SearchField(uiState.text, actions.onTextChange, actions.onSearchAction)
            FilterChipRow(
                sort = uiState.sort,
                years = uiState.years,
                openAccessOnly = uiState.openAccessOnly,
                currentYear = currentYear,
                onSortChange = actions.onSortChange,
                onYearFilterChange = actions.onYearFilterChange,
                onOpenAccessToggle = actions.onOpenAccessToggle
            )
            Box(Modifier.fillMaxSize()) {
                SearchBody(uiState, papers, savedIds, actions)
            }
        }
    }

    selectedItem?.let { item ->
        PaperPreviewSheet(
            paper = item.paper,
            inLibrary = item.inLibrary,
            onDismiss = actions.onDismissPreview,
            onToggleSave = { actions.onToggleSave(item) },
            onOpenDoi = actions.onOpenDoi
        )
    }
}

@Composable
private fun SearchBody(uiState: SearchUiState, papers: LazyPagingItems<Paper>, savedIds: Set<String>, actions: SearchActions) {
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

        else -> ResultsList(uiState.totalCount, papers, savedIds, actions)
    }
}

@Composable
private fun ResultsList(totalCount: Long?, papers: LazyPagingItems<Paper>, savedIds: Set<String>, actions: SearchActions) {
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
                PaperCard(
                    paper = paper,
                    inLibrary = item.inLibrary,
                    onClick = { actions.onPaperClick(paper) },
                    onSave = { actions.onToggleSave(item) }
                )
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

private fun Throwable.asSearchError(): SearchError = (this as? SearchException)?.error ?: SearchError.Unexpected
