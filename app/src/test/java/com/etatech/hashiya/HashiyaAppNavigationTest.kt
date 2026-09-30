package com.etatech.hashiya

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.testing.SamplePapers
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
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
}
