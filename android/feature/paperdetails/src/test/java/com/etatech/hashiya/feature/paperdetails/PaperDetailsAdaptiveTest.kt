package com.etatech.hashiya.feature.paperdetails

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.ROOMY_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.TestWindow
import com.etatech.hashiya.core.testing.setContentInWindow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = ROOMY_QUALIFIERS)
class PaperDetailsAdaptiveTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val loaded = PaperDetailsUiState.Loaded(
        LibraryPaper(SamplePapers.bert, ReadingStatus.Reading),
        PaperNotes(),
        NotesSaveState.Idle
    )

    private fun show(window: TestWindow, showBack: Boolean = true) = composeRule.setContentInWindow(window) {
        PaperDetailsContent(uiState = loaded, actions = PaperDetailsActions(), showBack = showBack)
    }

    @Test
    fun asAPaneThereIsNoBackButton() {
        show(TestWindow.Expanded, showBack = false)
        composeRule.onNodeWithText(SamplePapers.bert.title).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun asAScreenThereIsABackButton() {
        show(TestWindow.Compact)
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun wideWindowsCenterAnAt840dpColumn() {
        show(TestWindow.Expanded)
        val title = composeRule.onNodeWithText(SamplePapers.bert.title).getUnclippedBoundsInRoot()
        // The 840dp column is centered in 1000dp (80dp each side), with its 24dp margin inside.
        assertEquals(104f, title.left.value, 1f)
    }

    @Test
    fun phonesKeepTheir16dpMargin() {
        show(TestWindow.Compact)
        val title = composeRule.onNodeWithText(SamplePapers.bert.title).getUnclippedBoundsInRoot()
        assertEquals(16f, title.left.value, 1f)
    }
}
