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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
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
}
