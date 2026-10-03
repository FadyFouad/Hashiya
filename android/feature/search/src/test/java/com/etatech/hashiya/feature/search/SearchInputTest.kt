package com.etatech.hashiya.feature.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
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

/** Mouse and Back: the result's right-click menu, and Back closing the preview pane. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = ROOMY_QUALIFIERS)
class SearchInputTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<PaperItem>()
    private val doisOpened = mutableListOf<String>()
    private var dismissed = 0

    private fun show(window: TestWindow, selected: PaperItem? = null) = composeRule.setContentInWindow(window) {
        SearchContent(
            uiState = SearchUiState(text = "attention", isIdle = false, totalCount = 2),
            papers = flowOf(
                PagingData.from(
                    listOf(SamplePapers.attention, SamplePapers.bert),
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
            actions = SearchActions(
                onToggleSave = { saved += it },
                onOpenDoi = { doisOpened += it },
                onDismissPreview = { dismissed++ }
            ),
            currentYear = 2026
        )
    }

    @Test
    fun rightClickSavesWithoutOpeningThePreview() {
        show(TestWindow.Compact)
        composeRule.onNodeWithText(SamplePapers.bert.title).performMouseInput { rightClick() }
        composeRule.onNodeWithText("Save to library").performClick()
        assertEquals(listOf(PaperItem(SamplePapers.bert, inLibrary = false)), saved)
    }

    @Test
    fun rightClickOpensTheDoi() {
        show(TestWindow.Compact)
        composeRule.onNodeWithText(SamplePapers.bert.title).performMouseInput { rightClick() }
        composeRule.onNodeWithText("Open DOI").performClick()
        assertEquals(listOf(SamplePapers.bert.doi), doisOpened)
    }

    @Test
    fun backClosesThePreviewPaneFirst() {
        show(TestWindow.Expanded, PaperItem(SamplePapers.bert, inLibrary = false))
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(1, dismissed)
    }
}
