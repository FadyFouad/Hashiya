package com.etatech.hashiya.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
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

    private fun states(refresh: LoadState, append: LoadState = LoadState.NotLoading(endOfPaginationReached = true)) =
        LoadStates(refresh = refresh, prepend = LoadState.NotLoading(endOfPaginationReached = true), append = append)

    private val results = PagingData.from(listOf(SamplePapers.attention, SamplePapers.bert))
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
}
