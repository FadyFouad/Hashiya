package com.etatech.hashiya.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.feature.search.components.SEARCH_FIELD_TAG
import com.etatech.hashiya.feature.search.components.shortTitle
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class SearchLookupContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"

    private val actions = SearchActions(
        onSuggestion = { events += "suggestion:$it" },
        onToggleSave = { events += "toggle:${it.paper.openAlexId}:${it.inLibrary}" },
        onRetryLookup = { events += "retry" },
        onOpenSettings = { events += "settings" },
        onFocusHandled = { events += "focusHandled" }
    )

    private fun show(
        text: String = "10.1038/nature14539",
        lookupState: LookupUiState? = null,
        note: SearchNote? = null,
        focusSearch: Boolean = false,
        savedIds: Set<String> = emptySet()
    ) = composeRule.setContent {
        HashiyaTheme {
            SearchContent(
                uiState = SearchUiState(text = text),
                papers = flowOf(PagingData.empty<Paper>()).collectAsLazyPagingItems(),
                savedIds = savedIds,
                selectedItem = null,
                message = null,
                actions = actions,
                lookupState = lookupState,
                note = note,
                focusSearch = focusSearch,
                currentYear = 2026
            )
        }
    }

    @Test
    fun lookingShowsTheIdentifierAndHidesFilters() {
        show(lookupState = LookupUiState.Looking(PaperIdentifier.Doi("10.1038/nature14539")))

        composeRule.onNodeWithText("Looking up DOI 10.1038/nature14539…").assertIsDisplayed()
        composeRule.onNodeWithText("Relevance").assertDoesNotExist()
    }

    @Test
    fun lookingForArxivNamesArxiv() {
        show(text = "1706.03762", lookupState = LookupUiState.Looking(PaperIdentifier.Arxiv("1706.03762")))
        composeRule.onNodeWithText("Looking up arXiv 1706.03762…").assertIsDisplayed()
    }

    @Test
    fun foundShowsThePreviewAndSaves() {
        show(lookupState = LookupUiState.Found(SamplePapers.attention))

        composeRule.onNodeWithText(SamplePapers.attention.title).assertIsDisplayed()
        composeRule.onNodeWithText("Save to library").performClick()
        assertEquals(listOf("toggle:${SamplePapers.attention.openAlexId}:false"), events)
    }

    @Test
    fun foundPaperAlreadySavedOffersRemove() {
        show(lookupState = LookupUiState.Found(SamplePapers.attention), savedIds = setOf(SamplePapers.attention.openAlexId))

        composeRule.onNodeWithText("Remove from library").performClick()
        assertEquals(listOf("toggle:${SamplePapers.attention.openAlexId}:true"), events)
    }

    @Test
    fun notFoundOffersATitleSearch() {
        show(text = "1810.04805", lookupState = LookupUiState.NotFound(PaperIdentifier.Arxiv("1810.04805"), bertTitle))

        composeRule.onNodeWithText("No paper found for this arXiv ID").assertIsDisplayed()
        composeRule.onNodeWithText("Search for", substring = true).performClick()
        assertEquals(listOf("suggestion:$bertTitle"), events)
    }

    @Test
    fun notFoundButtonIsolatesThePastedTitle() {
        show(text = "1810.04805", lookupState = LookupUiState.NotFound(PaperIdentifier.Arxiv("1810.04805"), bertTitle))

        // First-strong isolates (U+2068/U+2069) keep the Latin title left-to-right inside the Arabic label,
        // wherever it is rendered — a plain substring match would pass even if they were dropped by mistake.
        composeRule.onNodeWithText("\u2068${shortTitle(bertTitle)}\u2069", substring = true).assertIsDisplayed()
    }

    @Test
    fun notFoundWithoutTitleHasNoButton() {
        show(lookupState = LookupUiState.NotFound(PaperIdentifier.Doi("10.1038/nature14539"), searchTitle = null))

        composeRule.onNodeWithText("No paper found for this DOI").assertIsDisplayed()
        composeRule.onNodeWithText("Search for", substring = true).assertDoesNotExist()
    }

    @Test
    fun failedLookupRetries() {
        show(lookupState = LookupUiState.Failed(SearchError.Offline))

        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry"), events)
    }

    @Test
    fun rejectedKeyOpensSettings() {
        show(lookupState = LookupUiState.Failed(SearchError.InvalidUserKey))

        composeRule.onNodeWithText("Open Settings").performClick()
        assertEquals(listOf("settings"), events)
    }

    @Test
    fun shareNoteIsShown() {
        show(text = "Deep learning", note = SearchNote.NoIdInShare)
        composeRule.onNodeWithText("No DOI or arXiv ID in the shared link — searching by page title.").assertIsDisplayed()
    }

    @Test
    fun emptyShareNoteIsShown() {
        show(text = "", note = SearchNote.NothingInShare)
        composeRule.onNodeWithText("Couldn't find a paper in what you shared.").assertIsDisplayed()
    }

    @Test
    fun focusRequestIsHonouredOnce() {
        show(text = "", focusSearch = true)

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()
        assertEquals(listOf("focusHandled"), events)
    }

    @Test
    fun hintMentionsIds() {
        show(text = "")
        composeRule.onNodeWithText("Search, or paste a DOI, arXiv ID or link").assertIsDisplayed()
    }
}
