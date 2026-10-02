package com.etatech.hashiya.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.testing.SamplePapers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaperCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0
    private var saves = 0

    private fun show(paper: Paper, inLibrary: Boolean) = composeRule.setContent {
        HashiyaTheme { PaperCard(paper, inLibrary, onClick = { clicks++ }, onSave = { saves++ }) }
    }

    @Test
    fun unsavedPaperOffersSave() {
        show(SamplePapers.bert, inLibrary = false)

        composeRule.onNodeWithText("Save").performClick()
        assertEquals(1, saves)
        composeRule.onNodeWithText("In library").assertDoesNotExist()
    }

    @Test
    fun savedPaperShowsBadgeInsteadOfSave() {
        show(SamplePapers.attention, inLibrary = true)

        composeRule.onNodeWithText("In library").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun showsMetadataLine() {
        show(SamplePapers.attention, inLibrary = false)

        composeRule.onNodeWithText(
            "Ashish Vaswani, Noam Shazeer, Niki Parmar +2 · 2017 · Neural Information Processing Systems"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("128K cited").assertIsDisplayed()
        composeRule.onNodeWithText("Open access").assertIsDisplayed()
    }

    @Test
    fun blankTitleShowsUntitled() {
        show(SamplePapers.untitled, inLibrary = false)
        composeRule.onNodeWithText("Untitled").assertIsDisplayed()
    }

    @Test
    fun tappingCardInvokesOnClick() {
        show(SamplePapers.bert, inLibrary = false)

        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        assertEquals(1, clicks)
    }
}
