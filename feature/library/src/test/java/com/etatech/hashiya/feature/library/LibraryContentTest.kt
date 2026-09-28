package com.etatech.hashiya.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LibraryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val removedBert = RemovedPaper(SamplePapers.bert, localId = "local-1", savedAt = 1)
    private val removedVit = RemovedPaper(SamplePapers.vit, localId = "local-2", savedAt = 2)

    private fun show(state: LibraryUiState, pendingUndo: () -> RemovedPaper? = { null }) = composeRule.setContent {
        HashiyaTheme {
            LibraryContent(
                uiState = state,
                selectedPaper = null,
                pendingUndo = pendingUndo(),
                onPaperClick = { events += "open:${it.openAlexId}" },
                onDismissPreview = {},
                onRemove = { paper: Paper -> events += "remove:${paper.openAlexId}" },
                onUndo = { events += "undo" },
                onUndoDismissed = { events += "undoDismissed" },
                onGoToSearch = { events += "search" },
                onOpenSettings = { events += "settings" },
                onOpenDoi = {},
                onAddPaper = { events += "addPaper" }
            )
        }
    }

    @Test
    fun emptyStateLeadsToSearch() {
        show(LibraryUiState.Empty)

        composeRule.onNodeWithText("No saved papers yet").assertIsDisplayed()
        composeRule.onNodeWithText("Go to Search").performClick()
        assertEquals(listOf("search"), events)
    }

    @Test
    fun listShowsCountTitlesAndShortAuthorLine() {
        show(LibraryUiState.Papers(listOf(SamplePapers.attention, SamplePapers.vit)))

        composeRule.onNodeWithText("2 papers").assertIsDisplayed()
        composeRule.onNodeWithText("Attention Is All You Need").assertIsDisplayed()
        composeRule.onNodeWithText("Ashish Vaswani et al. · 2017 · Neural Information Processing Systems").assertIsDisplayed()
    }

    @Test
    fun tappingRowOpensPreview() {
        show(LibraryUiState.Papers(listOf(SamplePapers.bert)))

        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        assertEquals(listOf("open:${SamplePapers.bert.openAlexId}"), events)
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
        show(LibraryUiState.Papers(listOf(SamplePapers.bert)))

        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()
        assertEquals(listOf("addPaper"), events)
    }

    /** With enough papers to overflow the screen, the FAB must not cover the last, scrolled-to row. */
    @Config(qualifiers = PHONE_QUALIFIERS)
    @Test
    fun lastPaperStaysClearOfTheAddPaperButton() {
        val manyPapers = (1..20).map { SamplePapers.bert.copy(openAlexId = "paper-$it", title = "Paper $it") }
        show(LibraryUiState.Papers(manyPapers))

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Paper 20"))

        val lastRowBounds = composeRule.onNodeWithText("Paper 20").getUnclippedBoundsInRoot()
        val fabBounds = composeRule.onNodeWithText("Add paper", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(
            "last row bottom (${lastRowBounds.bottom}) must be above the FAB top (${fabBounds.top})",
            lastRowBounds.bottom <= fabBounds.top
        )
    }
}
