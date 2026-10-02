package com.etatech.hashiya

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.data.repository.AttachResult
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.PdfRepository
import com.etatech.hashiya.core.designsystem.component.COLLECTION_NAME_FIELD_TAG
import com.etatech.hashiya.core.testing.SamplePapers
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HashiyaAppNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) = composeRule.waitUntil(timeoutMillis = 5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun opensOnEmptyLibraryAndGoesToSearch() {
        waitForText("No saved papers yet")

        composeRule.onNodeWithText("Go to Search").performClick()

        waitForText("Search OpenAlex")
        composeRule.onNodeWithText("Search OpenAlex").assertIsDisplayed()
    }

    @Test
    fun settingsOpensFromGearAndBackReturns() {
        waitForText("No saved papers yet")

        composeRule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
        waitForText("OpenAlex API key")

        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("No saved papers yet")
    }

    @Test
    fun bottomBarSwitchesBetweenLibraryAndSearch() {
        waitForText("No saved papers yet")

        composeRule.onNodeWithText("Search").performClick()
        waitForText("Search OpenAlex")
        composeRule.onAllNodesWithText("Library").onFirst().performClick()
        waitForText("No saved papers yet")
    }

    @Test
    fun addPaperOpensSearchReadyForAnId() {
        waitForText("No saved papers yet")

        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()

        waitForText("Search, or paste a DOI, arXiv ID or link")
        composeRule.onNodeWithText("Search, or paste a DOI, arXiv ID or link").assertIsFocused()
    }

    @Test
    fun libraryTabWorksAfterAddPaper() {
        waitForText("No saved papers yet")
        composeRule.onNodeWithText("Add paper", useUnmergedTree = true).performClick()
        waitForText("Search, or paste a DOI, arXiv ID or link")

        composeRule.onAllNodesWithText("Library").onFirst().performClick()

        waitForText("No saved papers yet")
    }

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var pdfRepository: PdfRepository

    @Before
    fun inject() = hiltRule.inject()

    private fun openSavedPaper() {
        runBlocking { libraryRepository.save(SamplePapers.bert) }
        waitForText(SamplePapers.bert.title)
        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        waitForText("My notes")
    }

    @Test
    fun libraryRowOpensDetailsWithoutTheNavigationBarAndBackReturns() {
        openSavedPaper()

        // The navigation bar (with its "Search" item) is gone once the transition from the Library ends.
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText("Search").fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForText("Search your library")
    }

    @Test
    fun removeOnDetailsReturnsToTheLibraryWithUndo() {
        openSavedPaper()

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Remove from library").performClick()

        waitForText("Removed from library")
        composeRule.onNodeWithText("Undo").performClick()
        waitForText(SamplePapers.bert.title)
    }

    @Test
    fun collectionCreatedOnDetailsFiltersTheLibrary() {
        runBlocking {
            libraryRepository.save(SamplePapers.bert)
            libraryRepository.save(SamplePapers.vit)
        }
        waitForText(SamplePapers.bert.title)
        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        waitForText("My notes")

        // The row's tag is internal to feature/paperdetails, so the test finds it by its text.
        composeRule.onNodeWithText("Not in any collection").performScrollTo().performClick()
        // Only the checklist sheet shows "New collection" until the name dialog opens.
        composeRule.onNodeWithText("New collection").performClick()
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("Thesis")
        composeRule.onNodeWithText("Create").performClick()
        // "Thesis" shows as a chip on Details and as a row in the sheet, which stays open.
        waitForText("Thesis")
        // The checklist sheet belongs to Details, so leaving Details closes it too.
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForText("All papers")
        composeRule.onNodeWithText("All papers").performClick()
        waitForText("Thesis")
        composeRule.onNodeWithText("Thesis").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(SamplePapers.vit.title).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText(SamplePapers.bert.title).assertIsDisplayed()
    }

    @Test
    fun detailsOpensTheReaderItsNotesAndBack() {
        runBlocking { libraryRepository.save(SamplePapers.bert) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdf = File(context.cacheDir, "navigation-test.pdf").apply { writeText(MINIMAL_PDF) }
        runBlocking { assertEquals(AttachResult.Done, pdfRepository.attach(SamplePapers.bert.openAlexId, Uri.fromFile(pdf))) }
        waitForText(SamplePapers.bert.title)
        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        waitForText("My notes")

        // The stored row reads "PDF · <size> · Attached"; its tag is internal to feature/paperdetails.
        composeRule.onNodeWithText("Attached", substring = true).performScrollTo().performClick()

        // The reader: it shows pages, or "This PDF can't be opened." where Robolectric's PdfRenderer can't render; the toolbar is the same.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Notes").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Notes").performClick()
        waitForText("Summary")

        composeRule.onNodeWithContentDescription("Close notes").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText("Summary").fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForText("My notes")
    }
}

/** The smallest file PdfFileStore accepts: it starts with %PDF-. */
private const val MINIMAL_PDF = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
    "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\n" +
    "trailer<</Root 1 0 R>>\n%%EOF\n"
