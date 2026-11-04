package com.etatech.hashiya.feature.importer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.ImportCounts
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class ImportContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private val actions = ImportActions(
        onBack = { events += "back" },
        onChooseFile = { events += "choose" },
        onAddToCollectionChange = { events += "collection:$it" },
        onConfirm = { events += "confirm" },
        onCancel = { events += "cancel" },
        onShowLibrary = { events += "library" },
        onChooseAnother = { events += "another" }
    )

    private fun show(state: ImportUiState) = composeRule.setContent { HashiyaTheme { ImportContent(state, actions) } }

    private fun job(state: ImportState, counts: ImportCounts = ImportCounts()) =
        ImportJob(id = 1, fileName = "thesis.bib", state = state, counts = counts, collectionName = "thesis")

    @Test
    fun theStartOffersThePickerAndBackGoesBack() {
        show(ImportUiState.Start)
        composeRule.onNodeWithText("Choose file").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("choose", "back"), events)
    }

    @Test
    fun thePreviewShowsOnlyTheCountsThatAreNotZeroAndConfirms() {
        show(
            ImportUiState.Ready(
                job(ImportState.Ready, ImportCounts(found = 12, matched = 9, local = 2, inLibrary = 1)),
                addToCollection = true
            )
        )
        composeRule.onNodeWithText("Found").assertIsDisplayed()
        composeRule.onNodeWithText("Matched on OpenAlex").assertIsDisplayed()
        composeRule.onNodeWithText("Kept from the file").assertIsDisplayed()
        composeRule.onNodeWithText("Already in your library").assertIsDisplayed()
        composeRule.onNodeWithText("Repeated in the file").assertDoesNotExist()
        composeRule.onNodeWithText("Couldn't be read").assertDoesNotExist()
        composeRule.onNodeWithText("couldn't be checked online", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Add to a new collection").performClick()
        composeRule.onNodeWithText("Import 11 papers").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("collection:false", "confirm", "cancel"), events)
    }

    @Test
    fun thePreviewExplainsEntriesThatCouldNotBeChecked() {
        show(ImportUiState.Ready(job(ImportState.Ready, ImportCounts(found = 3, local = 3, notChecked = 2)), addToCollection = false))
        composeRule.onNodeWithText("2 entries couldn't be checked online and will be kept from the file.").assertIsDisplayed()
    }

    @Test
    fun importingShowsProgressAndCanBeCancelled() {
        show(ImportUiState.Importing(job(ImportState.Importing, ImportCounts(found = 120, matched = 100, local = 20)).copy(processed = 50)))
        composeRule.onNodeWithText("Importing 50 of 120…").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun doneNamesTheCollectionAndOffersTheLibraryOrAnotherFile() {
        show(ImportUiState.Done(job(ImportState.Done).copy(imported = 11, collectionId = 4)))
        composeRule.onNodeWithText("Imported 11 papers").assertIsDisplayed()
        composeRule.onNodeWithText("Added to thesis").assertIsDisplayed()
        composeRule.onNodeWithText("Show in library").performClick()
        composeRule.onNodeWithText("Import another file").performClick()
        assertEquals(listOf("library", "another"), events)
    }

    @Test
    fun eachFailureSaysWhy() {
        show(ImportUiState.Failed(ImportFailure.NothingToImport))
        composeRule.onNodeWithText("Everything in this file is already in your library.").assertIsDisplayed()
        composeRule.onNodeWithText("Choose another file").performClick()
        assertEquals(listOf("another"), events)
    }
}
