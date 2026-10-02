package com.etatech.hashiya.feature.paperdetails

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.etatech.hashiya.core.designsystem.component.COLLECTION_NAME_FIELD_TAG
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class PaperDetailsCollectionsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val actions = PaperDetailsActions(
        onCopyBibTeX = { events += "copy" },
        onRemove = { events += "remove" },
        onToggleCollection = { id, member -> events += "toggle:$id:$member" },
        onNewCollection = { events += "new" },
        onNewCollectionConfirm = { events += "confirm:$it" }
    )
    private val thesis = PaperCollection(1, "Thesis", 1)
    private val chapter = PaperCollection(2, "Chapter 2", 0)

    private fun show(
        collections: List<PaperCollection>,
        memberOf: Set<Long>,
        dialog: NewCollectionDialog? = null,
        message: PaperDetailsMessage? = null
    ) = composeRule.setContent {
        HashiyaTheme {
            PaperDetailsContent(
                uiState = PaperDetailsUiState.Loaded(
                    LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead),
                    PaperNotes(),
                    NotesSaveState.Idle,
                    collections,
                    memberOf
                ),
                actions = actions,
                message = message,
                newCollectionDialog = dialog
            )
        }
    }

    @Test
    fun rowShowsTheCollectionsThePaperIsIn() {
        show(listOf(chapter, thesis), setOf(thesis.id))
        composeRule.onNodeWithTag(COLLECTIONS_ROW_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Thesis").assertIsDisplayed()
        composeRule.onNodeWithText("Chapter 2").assertDoesNotExist()
        composeRule.onNodeWithText("Not in any collection").assertDoesNotExist()
    }

    @Test
    fun rowSaysNotInAnyCollection() {
        show(emptyList(), emptySet())
        composeRule.onNodeWithText("Not in any collection").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun checklistTogglesAndCreates() {
        show(listOf(chapter, thesis), setOf(thesis.id))
        composeRule.onNodeWithTag(COLLECTIONS_ROW_TAG).performScrollTo().performClick()

        composeRule.onNodeWithTag(CHECKLIST_ROW_TAG_PREFIX + thesis.id).assertIsOn()
        composeRule.onNodeWithTag(CHECKLIST_ROW_TAG_PREFIX + chapter.id).assertIsOff().performClick()
        composeRule.onNodeWithTag(CHECKLIST_ROW_TAG_PREFIX + thesis.id).performClick()
        composeRule.onNodeWithText("New collection").performClick()

        assertEquals(listOf("toggle:2:true", "toggle:1:false", "new"), events)
    }

    @Test
    fun emptyChecklistExplainsCollections() {
        show(emptyList(), emptySet())
        composeRule.onNodeWithTag(COLLECTIONS_ROW_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Group papers for a chapter, a course or a project.").assertIsDisplayed()
        composeRule.onNodeWithText("New collection").assertIsDisplayed()
    }

    @Test
    fun newCollectionDialogConfirms() {
        show(emptyList(), emptySet(), dialog = NewCollectionDialog())
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("Thesis")
        composeRule.onNodeWithText("Create").performClick()
        assertEquals(listOf("confirm:Thesis"), events)
    }

    @Test
    fun newCollectionDialogShowsTheClash() {
        show(emptyList(), emptySet(), dialog = NewCollectionDialog(nameTaken = true))
        composeRule.onNodeWithText("A collection with that name already exists").assertIsDisplayed()
    }

    @Test
    fun overflowOffersCopyBibTeXAboveRemove() {
        show(emptyList(), emptySet())
        composeRule.onNodeWithContentDescription("More options").performClick()
        val copyTop = composeRule.onNodeWithText("Copy BibTeX").getUnclippedBoundsInRoot().top
        val removeTop = composeRule.onNodeWithText("Remove from library").getUnclippedBoundsInRoot().top
        assertTrue(copyTop < removeTop)

        composeRule.onNodeWithText("Copy BibTeX").performClick()
        assertEquals(listOf("copy"), events)
    }

    @Test
    fun collectionsUpdateFailureShowsASnackbar() {
        show(emptyList(), emptySet(), message = PaperDetailsMessage.CollectionsUpdateFailed)
        composeRule.onNodeWithText("Couldn't update collections").assertIsDisplayed()
    }

    @Test
    fun incompleteCopyShowsASnackbar() {
        show(emptyList(), emptySet(), message = PaperDetailsMessage.BibTeXIncomplete)
        composeRule.onNodeWithText("Some details may be missing. Copy again when you're online.").assertIsDisplayed()
    }

    @Test
    fun failedCopyShowsASnackbar() {
        show(emptyList(), emptySet(), message = PaperDetailsMessage.CopyFailed)
        composeRule.onNodeWithText("Couldn't copy BibTeX").assertIsDisplayed()
    }
}
