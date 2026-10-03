package com.etatech.hashiya.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.etatech.hashiya.core.testing.ROOMY_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.TestWindow
import com.etatech.hashiya.core.testing.setContentInWindow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** From 840dp the preview is a pane beside the results; narrower, it stays the sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = ROOMY_QUALIFIERS)
class SearchTwoPaneTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val papers = listOf(SamplePapers.attention, SamplePapers.bert)
    private var dismissed = 0

    private fun show(window: TestWindow, selected: PaperItem?) = composeRule.setContentInWindow(window) {
        SearchContent(
            uiState = SearchUiState(text = "attention", isIdle = false, totalCount = 2),
            papers = flowOf(
                PagingData.from(
                    papers,
                    LoadStates(
                        refresh = LoadState.NotLoading(endOfPaginationReached = true),
                        prepend = LoadState.NotLoading(endOfPaginationReached = true),
                        append = LoadState.NotLoading(endOfPaginationReached = true)
                    )
                )
            ).collectAsLazyPagingItems(),
            savedIds = emptySet(),
            selectedItem = selected,
            message = null,
            actions = SearchActions(onDismissPreview = { dismissed++ }),
            currentYear = 2026
        )
    }

    @Test
    fun expandedShowsAPlaceholderPaneUntilAPaperIsPicked() {
        show(TestWindow.Expanded, selected = null)
        composeRule.onNodeWithText(SamplePapers.attention.title).assertIsDisplayed()
        composeRule.onNodeWithText("No paper selected").assertIsDisplayed()
    }

    @Test
    fun expandedShowsThePreviewBesideTheResults() {
        show(TestWindow.Expanded, PaperItem(SamplePapers.bert, inLibrary = false))
        composeRule.onNodeWithText(SamplePapers.attention.title).assertIsDisplayed()
        composeRule.onNodeWithText("Save to library").assertIsDisplayed()
        // The picked result is marked as selected in the list.
        composeRule.onNode(hasText(SamplePapers.bert.title) and isSelected()).assertExists()
        composeRule.onNode(hasText(SamplePapers.attention.title) and isSelected()).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Close preview").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun mediumKeepsTheSheetAndNoPane() {
        show(TestWindow.Medium, selected = null)
        composeRule.onNodeWithText(SamplePapers.attention.title).assertIsDisplayed()
        composeRule.onNodeWithText("No paper selected").assertDoesNotExist()
    }

    @Test
    fun landscapePhoneKeepsOnePane() {
        show(TestWindow.ShortExpanded, selected = null)
        composeRule.onNodeWithText("No paper selected").assertDoesNotExist()
    }
}
