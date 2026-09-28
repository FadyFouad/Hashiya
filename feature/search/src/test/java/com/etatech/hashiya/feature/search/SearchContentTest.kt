package com.etatech.hashiya.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import com.etatech.hashiya.core.data.repository.SearchException
import com.etatech.hashiya.core.designsystem.component.LOADING_SKELETON_TAG
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.feature.search.components.SEARCH_FIELD_TAG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// PHONE_QUALIFIERS gives a full-height phone viewport; Robolectric's tiny default window would
// place the second result card below the fold, where a real performClick() touch cannot land.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class SearchContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private val actions = SearchActions(
        onSearchAction = { events += "search" },
        onSuggestion = { events += "suggestion:$it" },
        onSortChange = { events += "sort:$it" },
        onClearFilters = { events += "clearFilters" },
        onToggleSave = { events += "toggle:${it.paper.openAlexId}" },
        onOpenSettings = { events += "settings" }
    )

    private val searching = SearchUiState(text = "transformers", isIdle = false, totalCount = 48210)

    private fun show(uiState: SearchUiState, data: PagingData<Paper>, savedIds: Set<String> = emptySet()) = composeRule.setContent {
        HashiyaTheme {
            SearchContent(
                uiState = uiState,
                papers = flowOf(data).collectAsLazyPagingItems(),
                savedIds = savedIds,
                selectedItem = null,
                message = null,
                actions = actions,
                currentYear = 2026
            )
        }
    }

    /**
     * Shows results from real [Pager]s that can be swapped, like the ViewModel's flatMapLatest does for a new
     * query. Unlike PagingData.from/empty, a new Pager's first-page state arrives while the old items are still held.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun showSwitching(pagers: MutableStateFlow<Flow<PagingData<Paper>>>) = composeRule.setContent {
        HashiyaTheme {
            SearchContent(
                uiState = searching,
                papers = remember { pagers.flatMapLatest { it } }.collectAsLazyPagingItems(),
                savedIds = emptySet(),
                selectedItem = null,
                message = null,
                actions = actions,
                currentYear = 2026
            )
        }
    }

    private fun pager(load: suspend () -> PagingSource.LoadResult<Int, Paper>): Flow<PagingData<Paper>> =
        Pager(PagingConfig(pageSize = 20)) {
            object : PagingSource<Int, Paper>() {
                override fun getRefreshKey(state: PagingState<Int, Paper>): Int? = null

                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Paper> = load()
            }
        }.flow

    private fun onePage(vararg papers: Paper) = pager { PagingSource.LoadResult.Page(papers.toList(), prevKey = null, nextKey = null) }

    private fun states(refresh: LoadState, append: LoadState = LoadState.NotLoading(endOfPaginationReached = true)) =
        LoadStates(refresh = refresh, prepend = LoadState.NotLoading(endOfPaginationReached = true), append = append)

    // Explicit load states, as a real Pager dispatches them: without them the first page counts as still loading.
    private val results = PagingData.from(
        listOf(SamplePapers.attention, SamplePapers.bert),
        states(refresh = LoadState.NotLoading(endOfPaginationReached = false))
    )
    private val attentionSaved = setOf(SamplePapers.attention.openAlexId)

    @Test
    fun idleShowsSuggestions() {
        show(SearchUiState(), PagingData.empty())

        composeRule.onNodeWithText("Search OpenAlex").assertIsDisplayed()
        composeRule.onNodeWithText("large language models").performClick()
        assertEquals(listOf("suggestion:large language models"), events)
    }

    @Test
    fun imeSearchActionIsForwarded() {
        show(SearchUiState(text = "bert"), PagingData.empty())

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performImeAction()
        assertEquals(listOf("search"), events)
    }

    @Test
    fun resultsShowCountAndCards() {
        show(searching, results, savedIds = attentionSaved)

        composeRule.onNodeWithText("About 48,210 results").assertIsDisplayed()
        composeRule.onNodeWithText(SamplePapers.attention.title).assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(listOf("toggle:${SamplePapers.bert.openAlexId}"), events)
    }

    @Test
    fun loadingShowsSkeleton() {
        show(searching, PagingData.empty(states(refresh = LoadState.Loading)))
        composeRule.onNodeWithTag(LOADING_SKELETON_TAG).assertIsDisplayed()
    }

    @Test
    fun emptyResultsOfferClearFiltersOnlyWhenFiltered() {
        show(searching.copy(openAccessOnly = true), PagingData.empty(states(refresh = LoadState.NotLoading(false))))

        composeRule.onNodeWithText("No papers match").assertIsDisplayed()
        composeRule.onNodeWithText("Clear filters").performClick()
        assertEquals(listOf("clearFilters"), events)
    }

    @Test
    fun emptyResultsWithoutFiltersHaveNoClearButton() {
        show(searching, PagingData.empty(states(refresh = LoadState.NotLoading(false))))
        composeRule.onNodeWithText("Clear filters").assertDoesNotExist()
    }

    @Test
    fun offlineErrorOffersRetry() {
        show(searching, PagingData.empty(states(refresh = LoadState.Error(SearchException(SearchError.Offline)))))

        composeRule.onNodeWithText("Can't reach OpenAlex").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun rejectedUserKeyOpensSettings() {
        show(searching, PagingData.empty(states(refresh = LoadState.Error(SearchException(SearchError.InvalidUserKey)))))

        composeRule.onNodeWithText("Your API key was rejected").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").performClick()
        assertEquals(listOf("settings"), events)
    }

    @Test
    fun appendErrorShowsRetryFooterAndKeepsResults() {
        val data = PagingData.from(
            listOf(SamplePapers.bert),
            states(
                refresh = LoadState.NotLoading(endOfPaginationReached = false),
                append = LoadState.Error(SearchException(SearchError.Offline))
            )
        )
        show(searching, data)

        composeRule.onNodeWithText(SamplePapers.bert.title).assertIsDisplayed()
        composeRule.onNodeWithText("Couldn't load more results").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun saveAndRemoveFailuresShowTheirOwnMessage() {
        var message by mutableStateOf<SearchMessage?>(SearchMessage.RemoveFailed)
        composeRule.setContent {
            HashiyaTheme {
                SearchContent(
                    uiState = searching,
                    papers = flowOf(results).collectAsLazyPagingItems(),
                    savedIds = emptySet(),
                    selectedItem = null,
                    message = message,
                    actions = actions,
                    currentYear = 2026
                )
            }
        }
        composeRule.onNodeWithText("Couldn't remove the paper").assertIsDisplayed()

        composeRule.runOnIdle { message = SearchMessage.SaveFailed }
        composeRule.onNodeWithText("Couldn't save the paper").assertIsDisplayed()
    }

    @Test
    fun choosingSortFromMenu() {
        show(searching, results, savedIds = attentionSaved)

        composeRule.onNodeWithText("Relevance").performClick()
        composeRule.onNodeWithText("Most cited").performClick()
        assertEquals(listOf("sort:${SearchSort.MostCited}"), events)
    }

    @Test
    fun yearChipShowsActiveRange() {
        show(searching.copy(years = YearFilter.Between(2015, 2020)), results, savedIds = attentionSaved)
        composeRule.onNodeWithText("2015–2020").assertIsDisplayed()
    }

    @Test
    fun newQueryFirstPageErrorReplacesPreviousResults() {
        val pagers = MutableStateFlow(onePage(SamplePapers.attention))
        showSwitching(pagers)
        composeRule.onNodeWithText(SamplePapers.attention.title).assertIsDisplayed()

        composeRule.runOnIdle { pagers.value = pager { PagingSource.LoadResult.Error(SearchException(SearchError.Offline)) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(SamplePapers.attention.title).assertDoesNotExist()
        composeRule.onNodeWithText("Can't reach OpenAlex").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun newQueryLoadingReplacesPreviousResults() {
        val pagers = MutableStateFlow(onePage(SamplePapers.attention))
        showSwitching(pagers)
        composeRule.onNodeWithText(SamplePapers.attention.title).assertIsDisplayed()

        composeRule.runOnIdle { pagers.value = pager { awaitCancellation() } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(SamplePapers.attention.title).assertDoesNotExist()
        composeRule.onNodeWithTag(LOADING_SKELETON_TAG).assertIsDisplayed()
    }
}
