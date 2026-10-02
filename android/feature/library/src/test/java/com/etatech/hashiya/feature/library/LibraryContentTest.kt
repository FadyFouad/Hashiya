package com.etatech.hashiya.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.feature.library.components.LIBRARY_SEARCH_FIELD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class LibraryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val removedBert = RemovedPaper(SamplePapers.bert, localId = "local-1", savedAt = 1, status = ReadingStatus.ToRead)
    private val removedVit = RemovedPaper(SamplePapers.vit, localId = "local-2", savedAt = 2, status = ReadingStatus.ToRead)

    private val actions = LibraryActions(
        onQueryChange = { events += "query:$it" },
        onSearch = { events += "search" },
        onClearQuery = { events += "clearQuery" },
        onStatusFilterChange = { events += "filter:$it" },
        onClearSearchAndFilters = { events += "clearAll" },
        onPaperClick = { events += "open:${it.openAlexId}" },
        onStatusChange = { paper, status -> events += "status:${paper.openAlexId}:$status" },
        onRemove = { paper: Paper -> events += "remove:${paper.openAlexId}" },
        onUndo = { events += "undo" },
        onUndoDismissed = { events += "undoDismissed" },
        onMessageShown = { events += "messageShown" },
        onGoToSearch = { events += "search-tab" },
        onAddPaper = { events += "addPaper" },
        onOpenSettings = { events += "settings" }
    )

    private fun counts(toRead: Int, reading: Int, read: Int) =
        mapOf(ReadingStatus.ToRead to toRead, ReadingStatus.Reading to reading, ReadingStatus.Read to read)

    private fun papersState(vararg papers: Pair<Paper, ReadingStatus>, filter: LibraryFilter = LibraryFilter()) =
        LibraryUiState.Papers(papers.map { (paper, status) -> LibraryPaper(paper, status) }, filter)

    private fun toRead(vararg papers: Paper) = papersState(*papers.map { it to ReadingStatus.ToRead }.toTypedArray())

    private fun show(
        state: () -> LibraryUiState,
        pendingUndo: () -> RemovedPaper? = { null },
        message: LibraryMessage? = null,
        actions: LibraryActions = this.actions
    ) = composeRule.setContent {
        HashiyaTheme {
            LibraryContent(
                uiState = state(),
                pendingUndo = pendingUndo(),
                actions = actions,
                message = message
            )
        }
    }

    private fun show(state: LibraryUiState, pendingUndo: () -> RemovedPaper? = { null }) = show({ state }, pendingUndo)

    @Test
    fun emptyStateLeadsToSearchWithoutSearchFieldOrChips() {
        show(LibraryUiState.Empty)

        composeRule.onNodeWithText("No saved papers yet").assertIsDisplayed()
        composeRule.onNodeWithText("Search your library").assertDoesNotExist()
        composeRule.onNodeWithText("All", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Go to Search").performClick()
        assertEquals(listOf("search-tab"), events)
    }

    @Test
    fun listShowsCountTitlesAndShortAuthorLine() {
        show(toRead(SamplePapers.attention, SamplePapers.vit))

        composeRule.onNodeWithText("2 papers").assertIsDisplayed()
        composeRule.onNodeWithText("Attention Is All You Need").assertIsDisplayed()
        composeRule.onNodeWithText("Ashish Vaswani et al. · 2017 · Neural Information Processing Systems").assertIsDisplayed()
    }

    @Test
    fun tappingRowOpensThePaper() {
        show(toRead(SamplePapers.bert))

        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        assertEquals(listOf("open:${SamplePapers.bert.openAlexId}"), events)
    }

    @Test
    fun typingAndTheSearchKeyReachTheViewModel() {
        var query by mutableStateOf("")
        show(
            state = { papersState(SamplePapers.bert to ReadingStatus.ToRead, filter = LibraryFilter(query = query)) },
            actions = actions.copy(
                onQueryChange = {
                    query = it
                    events += "query:$it"
                }
            )
        )

        composeRule.onNodeWithText("Search your library").assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG).performTextInput("bert")
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG).performImeAction()

        assertEquals(listOf("query:bert", "search"), events)
    }

    @Test
    fun clearButtonClearsTheSearch() {
        show(papersState(SamplePapers.bert to ReadingStatus.ToRead, filter = LibraryFilter(query = "bert")))

        composeRule.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(listOf("clearQuery"), events)
    }

    @Test
    fun chipsShowCountsAndSelectAStatus() {
        show(
            papersState(
                SamplePapers.bert to ReadingStatus.Reading,
                filter = LibraryFilter(status = ReadingStatus.Reading, counts = counts(toRead = 2, reading = 1, read = 0))
            )
        )

        composeRule.onNodeWithText("All · 3").assertIsNotSelected()
        composeRule.onNodeWithText("To read · 2").assertIsDisplayed()
        composeRule.onNodeWithText("Reading · 1").assertIsSelected()
        composeRule.onNodeWithText("Read · 0").performClick()
        composeRule.onNodeWithText("All · 3").performClick()
        assertEquals(listOf("filter:Read", "filter:null"), events)
    }

    @Test
    fun badgeShowsTheStatusAndItsMenuChangesIt() {
        show(papersState(SamplePapers.bert to ReadingStatus.ToRead))

        composeRule.onNodeWithContentDescription("Status: To read. Change status").performClick()
        composeRule.onNode(hasText("To read") and hasAnyAncestor(isPopup())).assertIsSelected()
        composeRule.onNode(hasText("Reading") and hasAnyAncestor(isPopup())).assertIsNotSelected()
        composeRule.onNode(hasText("Reading") and hasAnyAncestor(isPopup())).performClick()

        assertEquals(listOf("status:${SamplePapers.bert.openAlexId}:Reading"), events)
        composeRule.onNode(hasAnyAncestor(isPopup())).assertDoesNotExist()
    }

    @Test
    fun readBadgeSaysReadNotJustAColour() {
        show(papersState(SamplePapers.bert to ReadingStatus.Read))

        composeRule.onNodeWithContentDescription("Status: Read. Change status").assertIsDisplayed()
    }

    @Test
    fun noMatchesOffersToClearSearchAndFilters() {
        show(LibraryUiState.NoMatches(LibraryFilter(query = "zebra", counts = counts(0, 0, 0))))

        composeRule.onNodeWithText("No papers match").assertIsDisplayed()
        composeRule.onNodeWithText("All · 0").assertIsDisplayed()
        composeRule.onNodeWithText("Clear search and filters").performClick()
        assertEquals(listOf("clearAll"), events)
    }

    /** Typing a word that matches nothing swaps the list for "No papers match"; the field must keep focus and the keyboard. */
    @Test
    fun searchFieldKeepsFocusWhenNothingMatches() {
        var state by mutableStateOf<LibraryUiState>(toRead(SamplePapers.bert))
        show({ state })
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG).performClick()
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG).assertIsFocused()

        composeRule.runOnIdle { state = LibraryUiState.NoMatches(LibraryFilter(query = "zebra")) }

        composeRule.onNodeWithText("No papers match").assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG).assertIsFocused()
    }

    @Test
    fun statusUpdateFailureShowsASnackbar() {
        show({ toRead(SamplePapers.bert) }, message = LibraryMessage.StatusUpdateFailed)

        composeRule.onNodeWithText("Couldn't update the status").assertIsDisplayed()
    }

    @Test
    fun undoSnackbarActionInvokesUndo() {
        show(LibraryUiState.Empty, pendingUndo = { removedBert })

        composeRule.onNodeWithText("Removed from library").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("undo"), events)
    }

    /** A second removal must get its own full snackbar, not the remainder of the first one's timeout. */
    @Test
    fun secondRemovalRestartsTheUndoSnackbar() {
        composeRule.mainClock.autoAdvance = false
        var pending by mutableStateOf<RemovedPaper?>(removedBert)
        show(LibraryUiState.Empty, pendingUndo = { pending })

        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.runOnIdle { pending = removedVit }
        // With the main clock paused, a plain snapshot write made from outside composition (as
        // here) is not otherwise flushed to the recomposer until something drives the real
        // Robolectric looper (e.g. a node query) - which would happen too late relative to the
        // next advanceTimeBy below. Forcing the flush now reproduces what a live Choreographer
        // does within a frame of a real state change, so the assertions below observe the
        // second removal's own snackbar restart rather than a stale one racing the first's timeout.
        Snapshot.sendApplyNotifications()
        // The first snackbar (4 s, short duration) would have timed out by now.
        composeRule.mainClock.advanceTimeBy(2_000)

        composeRule.onNodeWithText("Removed from library").assertExists()
        assertFalse("undoDismissed" in events)
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("undo"), events)
    }

    // The extended FAB's label is only in the unmerged semantics tree.
    @Test
    fun addPaperButtonOnEmptyLibrary() {
        show(LibraryUiState.Empty)

        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()
        assertEquals(listOf("addPaper"), events)
    }

    @Test
    fun addPaperButtonWithPapers() {
        show(toRead(SamplePapers.bert))

        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()
        assertEquals(listOf("addPaper"), events)
    }

    /** With enough papers to overflow the screen, the FAB must not cover the last, scrolled-to row. */
    @Test
    fun lastPaperStaysClearOfTheAddPaperButton() {
        val manyPapers = (1..20).map { SamplePapers.bert.copy(openAlexId = "paper-$it", title = "Paper $it") }
        show(toRead(*manyPapers.toTypedArray()))

        // The list, not the sideways-scrolling chips.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Paper 20"))

        val lastRowBounds = composeRule.onNodeWithText("Paper 20").getUnclippedBoundsInRoot()
        val fabBounds = composeRule.onNodeWithText("Add paper", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(
            "last row bottom (${lastRowBounds.bottom}) must be above the FAB top (${fabBounds.top})",
            lastRowBounds.bottom <= fabBounds.top
        )
    }

    @Test
    fun aPaperWithAPdfShowsTheOfflineIconAndOneWithoutDoesNot() {
        composeRule.setContent {
            HashiyaTheme {
                LibraryContent(
                    uiState = LibraryUiState.Papers(
                        listOf(
                            LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead, hasPdf = true),
                            LibraryPaper(SamplePapers.vit, ReadingStatus.ToRead)
                        ),
                        LibraryFilter(counts = counts(2, 0, 0))
                    ),
                    pendingUndo = null,
                    actions = actions,
                    header = LibraryHeader(viewSize = 2, libraryCount = 2)
                )
            }
        }

        composeRule.onNodeWithContentDescription("PDF available offline").assertIsDisplayed()
        // The icon is merged into its clickable row, so its tag is only in the unmerged tree.
        assertEquals(1, composeRule.onAllNodesWithTag(LIBRARY_PDF_ICON_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
