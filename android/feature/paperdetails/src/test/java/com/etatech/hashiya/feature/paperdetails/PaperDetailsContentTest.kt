package com.etatech.hashiya.feature.paperdetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.etatech.hashiya.core.designsystem.component.NOTE_FIELD_TAG_PREFIX
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class PaperDetailsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val actions = PaperDetailsActions(
        onBack = { events += "back" },
        onRemove = { events += "remove" },
        onStatusChange = { events += "status:$it" },
        onNoteChange = { section, text -> events += "note:$section:$text" },
        onRetrySave = { events += "retry" },
        onMessageShown = { events += "messageShown" },
        onOpenLink = { events += "open:$it" }
    )

    private fun loaded(
        paper: Paper = SamplePapers.attention,
        status: ReadingStatus = ReadingStatus.ToRead,
        notes: PaperNotes = PaperNotes(),
        saveState: NotesSaveState = NotesSaveState.Idle
    ) = PaperDetailsUiState.Loaded(LibraryPaper(paper, status), notes, saveState)

    private fun show(state: PaperDetailsUiState, message: PaperDetailsMessage? = null) = composeRule.setContent {
        HashiyaTheme { PaperDetailsContent(uiState = state, actions = actions, message = message) }
    }

    private fun field(section: NoteSection) = composeRule.onNodeWithTag(NOTE_FIELD_TAG_PREFIX + section.name)

    @Test
    fun headerShowsEveryAuthorVenueYearAndCitations() {
        show(loaded())

        composeRule.onNodeWithText("Attention Is All You Need").assertIsDisplayed()
        composeRule.onNodeWithText("Ashish Vaswani, Noam Shazeer, Niki Parmar, Jakob Uszkoreit, Llion Jones").assertIsDisplayed()
        composeRule.onNodeWithText("Neural Information Processing Systems · 2017 · 128,412 citations").assertIsDisplayed()
    }

    @Test
    fun untitledPaperWithoutAbstractSaysSo() {
        show(loaded(SamplePapers.untitled))

        composeRule.onNodeWithText("Untitled").assertIsDisplayed()
        composeRule.onNodeWithText("No abstract available").assertIsDisplayed()
    }

    @Test
    fun theDoiRowOpensTheDoi() {
        show(loaded(SamplePapers.attention))

        composeRule.onNodeWithText("10.48550/arxiv.1706.03762").assertExists()
        composeRule.onNodeWithTag(DOI_ROW_TAG).performScrollTo().performClick()

        assertEquals(listOf("open:https://doi.org/10.48550/arxiv.1706.03762"), events)
    }

    @Test
    fun noDoiNoDoiRow() {
        show(loaded(SamplePapers.attention.copy(doi = null)))

        composeRule.onNodeWithTag(DOI_ROW_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("DOI").assertDoesNotExist()
    }

    @Test
    fun thePdfLinkIsNoLongerAButton() {
        show(loaded(SamplePapers.attention))

        composeRule.onNodeWithText("Open PDF").assertDoesNotExist()
    }

    @Test
    fun statusSelectorReportsTheNewStatus() {
        show(loaded(status = ReadingStatus.ToRead))

        composeRule.onNodeWithText("Reading").performClick()

        assertEquals(listOf("status:Reading"), events)
    }

    @Test
    fun theSixNoteSectionsAreLabelledAndInTemplateOrder() {
        show(loaded())
        val labels = listOf("Summary", "Research question", "Method", "Key findings", "Limitations", "My thoughts")

        NoteSection.entries.zip(labels).forEach { (section, label) -> field(section).assert(hasText(label)) }
        val tops = NoteSection.entries.map { field(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun typingInASectionReportsThatSection() {
        show(loaded())

        field(NoteSection.Method).performScrollTo().performTextInput("Ablation")

        assertEquals(listOf("note:Method:Ablation"), events)
    }

    @Test
    fun storedNotesShowInTheirFields() {
        show(loaded(notes = PaperNotes(keyFindings = "Beats RNNs on WMT")))

        field(NoteSection.KeyFindings).performScrollTo().assert(hasText("Beats RNNs on WMT"))
    }

    @Test
    fun saveStatusLineFollowsTheSaveState() {
        var state by mutableStateOf(loaded())
        composeRule.setContent { HashiyaTheme { PaperDetailsContent(uiState = state, actions = actions) } }
        listOf("Saving…", "Saved", "Couldn't save").forEach { composeRule.onNodeWithText(it).assertDoesNotExist() }

        state = loaded(saveState = NotesSaveState.Saving)
        composeRule.onNodeWithText("Saving…").assertExists()
        state = loaded(saveState = NotesSaveState.Saved)
        composeRule.onNodeWithText("Saved").assertExists()
        state = loaded(saveState = NotesSaveState.Failed)
        composeRule.onNodeWithText("Couldn't save").assertExists()
    }

    @Test
    fun saveFailureOffersRetry() {
        show(loaded(saveState = NotesSaveState.Failed), message = PaperDetailsMessage.NotesSaveFailed)

        composeRule.onNodeWithText("Couldn't save your notes").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("messageShown", "retry"), events)
    }

    @Test
    fun statusUpdateFailureShowsASnackbar() {
        show(loaded(), message = PaperDetailsMessage.StatusUpdateFailed)

        composeRule.onNodeWithText("Couldn't update the status").assertIsDisplayed()
    }

    @Test
    fun removeFromTheOverflowMenu() {
        show(loaded())

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Remove from library").performClick()

        assertEquals(listOf("remove"), events)
    }

    @Test
    fun backCallsBack() {
        show(loaded())

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("back"), events)
    }

    @Test
    fun loadingHasNoMenu() {
        show(PaperDetailsUiState.Loading)

        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    /** The field keeps its own text: typing never waits for the state to come back from the ViewModel (cursor and IME stay put). */
    @Test
    fun typingDoesNotWaitForTheStateToComeBack() {
        show(loaded())

        field(NoteSection.Method).performScrollTo().performTextInput("Abl")
        field(NoteSection.Method).performTextInput("ation")

        assertEquals(listOf("note:Method:Abl", "note:Method:Ablation"), events)
        field(NoteSection.Method).assert(hasText("Ablation"))
    }
}
