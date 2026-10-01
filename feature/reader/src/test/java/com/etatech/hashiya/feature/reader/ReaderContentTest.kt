package com.etatech.hashiya.feature.reader

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ready = ReaderState.Ready("Attention Is All You Need", 3, List(3) { 1.3f }, startPage = 0, file = File("x.pdf"))

    private fun show(state: ReaderState, actions: ReaderActions, showNotes: Boolean = false) = composeRule.setContent {
        HashiyaTheme {
            ReaderContent(
                state = state,
                pages = emptyMap<Int, Bitmap>(),
                notes = PaperNotes(),
                notesSaveState = NotesSaveState.Idle,
                showNotes = showNotes,
                actions = actions
            )
        }
    }

    @Test
    fun theToolbarOpensNotesSharesAndGoesBack() {
        val events = mutableListOf<String>()
        show(ready, ReaderActions(onBack = { events += "back" }, onOpenNotes = { events += "notes" }, onShare = { events += "share" }))

        composeRule.onNodeWithContentDescription("Notes").performClick()
        composeRule.onNodeWithContentDescription("Share").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("notes", "share", "back"), events)
    }

    @Test
    fun eachPageIsLabelledWithItsNumber() {
        show(ready, ReaderActions())

        composeRule.onNodeWithContentDescription("1 of 3").assertExists()
    }

    @Test
    fun cantOpenOffersReplaceAndRemove() {
        val events = mutableListOf<String>()
        show(ReaderState.CantOpen("Attention"), ReaderActions(onReplace = { events += "replace" }, onRemovePdf = { events += "remove" }))

        composeRule.onNodeWithText("This PDF can't be opened.").assertExists()
        composeRule.onNodeWithText("Replace PDF").performClick()
        composeRule.onNodeWithText("Remove PDF").performClick()

        assertEquals(listOf("replace", "remove"), events)
    }

    @Test
    fun theNotesSheetShowsTheFieldsAndCloses() {
        var closed = false
        show(ready, ReaderActions(onCloseNotes = { closed = true }), showNotes = true)

        composeRule.onNodeWithText("Summary").assertExists()
        composeRule.onNodeWithContentDescription("Close notes").performClick()

        assertEquals(true, closed)
    }
}
