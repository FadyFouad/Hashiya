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
class PaperPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var toggles = 0
    private val openedDois = mutableListOf<String>()

    private fun show(paper: Paper, inLibrary: Boolean) = composeRule.setContent {
        HashiyaTheme {
            PaperPreviewContent(paper, inLibrary, onToggleSave = { toggles++ }, onOpenDoi = { openedDois += it })
        }
    }

    @Test
    fun unsavedPaperOffersSaveToLibrary() {
        show(SamplePapers.bert, inLibrary = false)

        composeRule.onNodeWithText("Save to library").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun savedPaperOffersRemove() {
        show(SamplePapers.bert, inLibrary = true)
        composeRule.onNodeWithText("Remove from library").assertIsDisplayed()
    }

    @Test
    fun openDoiPassesTheDoi() {
        show(SamplePapers.bert, inLibrary = false)

        composeRule.onNodeWithText("Open DOI").performClick()
        assertEquals(listOf("10.18653/v1/n19-1423"), openedDois)
    }

    @Test
    fun hidesOpenDoiWithoutDoi() {
        show(SamplePapers.vit, inLibrary = false)
        composeRule.onNodeWithText("Open DOI").assertDoesNotExist()
    }

    @Test
    fun showsAllAuthorsAndFullCitationCount() {
        show(SamplePapers.attention, inLibrary = false)

        composeRule.onNodeWithText(
            "Ashish Vaswani, Noam Shazeer, Niki Parmar, Jakob Uszkoreit, Llion Jones"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Neural Information Processing Systems · 2017 · 128,412 citations").assertIsDisplayed()
        composeRule.onNodeWithText("Open access · PDF available").assertIsDisplayed()
    }

    @Test
    fun missingAbstractIsExplained() {
        show(SamplePapers.vit, inLibrary = false)
        composeRule.onNodeWithText("No abstract available").assertIsDisplayed()
    }
}
