package com.etatech.hashiya.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.CitationStyle
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperCollection
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
class LibraryCollectionsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val actions = LibraryActions(
        onSelectCollection = { events += "select:$it" },
        onNewCollection = { events += "new" },
        onRenameCollection = { events += "rename:${it.name}" },
        onDeleteCollection = { events += "delete:${it.name}" },
        onDialogConfirm = { events += "confirm:$it" },
        onConfirmDelete = { events += "confirmDelete" },
        onUndoCollection = { events += "undoCollection" },
        onExport = { events += "export:${it.name}" }
    )
    private val thesis = PaperCollection(1, "Thesis", 2)
    private val papers = LibraryUiState.Papers(
        listOf(LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead)),
        LibraryFilter(counts = mapOf(ReadingStatus.ToRead to 1, ReadingStatus.Reading to 0, ReadingStatus.Read to 0))
    )

    private fun show(
        state: LibraryUiState = papers,
        header: LibraryHeader = LibraryHeader(collections = listOf(thesis), viewSize = 3, libraryCount = 3),
        dialog: CollectionDialog? = null,
        collectionUndo: CollectionRemoval? = null,
        message: LibraryMessage? = null
    ) = composeRule.setContent {
        HashiyaTheme {
            LibraryContent(
                uiState = state,
                pendingUndo = null,
                actions = actions,
                header = header,
                dialog = dialog,
                pendingCollectionUndo = collectionUndo,
                message = message
            )
        }
    }

    @Test
    fun selectorListsAllPapersAndCollectionsAndSelects() {
        show()

        composeRule.onNodeWithText("All papers").performClick()
        composeRule.onNodeWithText("Thesis").assertIsDisplayed()
        composeRule.onNodeWithText("New collection").assertIsDisplayed()
        composeRule.onNodeWithText("Thesis").performClick()

        assertEquals(listOf("select:1"), events)
    }

    @Test
    fun selectorRowMenuRenamesAndDeletes() {
        show()
        composeRule.onNodeWithText("All papers").performClick()

        composeRule.onNodeWithContentDescription("Options for Thesis").performClick()
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithText("All papers").performClick()
        composeRule.onNodeWithContentDescription("Options for Thesis").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf("rename:Thesis", "delete:Thesis"), events)
    }

    @Test
    fun newCollectionFromTheSelector() {
        show()
        composeRule.onNodeWithText("All papers").performClick()
        composeRule.onNodeWithText("New collection").performClick()
        assertEquals(listOf("new"), events)
    }

    @Test
    fun theRememberedStyleIsListedFirst() {
        composeRule.setContent {
            HashiyaTheme {
                LibraryContent(
                    uiState = papers,
                    pendingUndo = null,
                    actions = actions,
                    header = LibraryHeader(viewSize = 3, libraryCount = 3),
                    citationStyle = CitationStyle.Ieee
                )
            }
        }
        composeRule.onNodeWithContentDescription("Export references").performClick()

        val tops = listOf("IEEE (.rtf)", "APA 7 (.rtf)", "BibTeX (.bib)").map {
            composeRule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun selectedCollectionIsTheTitle() {
        show(header = LibraryHeader(collections = listOf(thesis), selected = thesis, viewSize = 2, libraryCount = 3))
        composeRule.onNodeWithText("Thesis").assertIsDisplayed()
    }

    @Test
    fun exportIsOfferedOnlyWhenTheViewHasPapersAndShowsProgress() {
        var header by mutableStateOf(LibraryHeader(viewSize = 3, libraryCount = 3))
        composeRule.setContent {
            HashiyaTheme { LibraryContent(uiState = papers, pendingUndo = null, actions = actions, header = header) }
        }
        composeRule.onNodeWithContentDescription("Export references").performClick()
        composeRule.onNodeWithText("BibTeX (.bib)").assertIsDisplayed()
        composeRule.onNodeWithText("APA 7 (.rtf)").assertIsDisplayed()
        composeRule.onNodeWithText("IEEE (.rtf)").performClick()
        assertEquals(listOf("export:Ieee"), events)

        header = header.copy(exporting = true)
        composeRule.onNodeWithTag(EXPORT_PROGRESS_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Export references").assertDoesNotExist()

        header = LibraryHeader(viewSize = 0, libraryCount = 3)
        composeRule.onNodeWithTag(EXPORT_PROGRESS_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Export references").assertDoesNotExist()
    }

    @Test
    fun emptyCollectionExplainsHowToAddPapersWithoutSearch() {
        show(state = LibraryUiState.CollectionEmpty(thesis.copy(paperCount = 0)))
        composeRule.onNodeWithText("No papers in this collection yet. Add papers from their details screen.").assertIsDisplayed()
        composeRule.onNodeWithText("Search your library").assertDoesNotExist()
    }

    @Test
    fun removedFromCollectionSnackbarOffersUndo() {
        show(collectionUndo = CollectionRemoval(thesis, SamplePapers.bert.openAlexId))
        composeRule.onNodeWithText("Removed from Thesis").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("undoCollection"), events)
    }

    @Test
    fun deleteAsksForConfirmation() {
        show(dialog = CollectionDialog.ConfirmDelete(thesis))
        composeRule.onNodeWithText("Delete \"Thesis\"?").assertIsDisplayed()
        composeRule.onNodeWithText("Its papers stay in your library.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf("confirmDelete"), events)
    }

    @Test
    fun nameDialogConfirmsTheTypedName() {
        show(dialog = CollectionDialog.Rename(thesis))
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(listOf("confirm:Thesis"), events)
    }

    @Test
    fun collectionsUpdateFailureShowsASnackbar() {
        show(message = LibraryMessage.CollectionsUpdateFailed)
        composeRule.onNodeWithText("Couldn't update collections").assertIsDisplayed()
    }

    @Test
    fun exportFailureShowsASnackbar() {
        show(message = LibraryMessage.ExportFailed)
        composeRule.onNodeWithText("Couldn't export").assertIsDisplayed()
    }

    @Test
    fun incompleteExportShowsASnackbar() {
        show(message = LibraryMessage.ExportIncomplete)
        composeRule.onNodeWithText("Some entries may be incomplete. Export again when you're online.").assertIsDisplayed()
    }
}
