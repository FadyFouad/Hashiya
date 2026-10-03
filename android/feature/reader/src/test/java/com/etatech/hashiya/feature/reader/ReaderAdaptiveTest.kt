package com.etatech.hashiya.feature.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.testing.ROOMY_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.TestWindow
import com.etatech.hashiya.core.testing.setContentInWindow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wide windows: notes in a pane beside the PDF, and pages no wider than 840dp. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = ROOMY_QUALIFIERS)
class ReaderAdaptiveTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ready = ReaderState.Ready(SamplePapers.attention.title, 3, List(3) { 11f / 8.5f }, startPage = 0, file = File("x.pdf"))
    private var closed = 0
    private var opened = 0

    private fun show(window: TestWindow, showNotes: Boolean, notesBeside: Boolean) = composeRule.setContentInWindow(window) {
        ReaderContent(
            state = ready,
            pages = emptyMap(),
            notes = PaperNotes(summary = "Attention replaces recurrence."),
            notesSaveState = NotesSaveState.Saved,
            showNotes = showNotes,
            notesBeside = notesBeside,
            actions = ReaderActions(onOpenNotes = { opened++ }, onCloseNotes = { closed++ })
        )
    }

    @Test
    fun notesSitBesideThePdf() {
        show(TestWindow.Expanded, showNotes = true, notesBeside = true)
        composeRule.onNodeWithText("Attention replaces recurrence.").assertIsDisplayed()
        composeRule.onNodeWithText(SamplePapers.attention.title).assertIsDisplayed()
        val page = composeRule.onNodeWithContentDescription("1 of 3").getUnclippedBoundsInRoot()
        val notes = composeRule.onNodeWithText("Attention replaces recurrence.").getUnclippedBoundsInRoot()
        assertTrue("the notes are beside the page, not over it", notes.left >= page.right)
    }

    @Test
    fun theNotesButtonClosesTheNotesPane() {
        show(TestWindow.Expanded, showNotes = true, notesBeside = true)
        composeRule.onNodeWithContentDescription("Notes").performClick()
        composeRule.onNodeWithContentDescription("Close notes").performClick()
        assertEquals(2, closed)
        assertEquals(0, opened)
    }

    @Test
    fun pagesStopGrowingAt840dp() {
        show(TestWindow.Expanded, showNotes = false, notesBeside = true)
        val page = composeRule.onNodeWithContentDescription("1 of 3").getUnclippedBoundsInRoot()
        // 840dp less the page's 12dp side padding, centered in the 1000dp window.
        assertEquals(816f, (page.right - page.left).value, 1f)
        assertEquals(92f, page.left.value, 1f)
    }

    @Test
    fun pagesFillPhoneWidths() {
        show(TestWindow.Compact, showNotes = false, notesBeside = false)
        val page = composeRule.onNodeWithContentDescription("1 of 3").getUnclippedBoundsInRoot()
        assertEquals(12f, page.left.value, 1f)
        assertEquals(399f, page.right.value, 1f)
    }
}
