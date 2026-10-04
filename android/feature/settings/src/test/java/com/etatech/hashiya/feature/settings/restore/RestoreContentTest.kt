package com.etatech.hashiya.feature.settings.restore

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.etatech.hashiya.core.data.backup.RestorePreview
import com.etatech.hashiya.core.data.backup.RestoreResult
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class RestoreContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: RestoreUiState) = composeRule.setContent {
        HashiyaTheme { RestoreContent(uiState = state, onConfirm = {}, onLeave = {}) }
    }

    @Test
    fun thePreviewSaysHowManyPapersAreSkipped() {
        show(RestoreUiState.Preview(RestorePreview(null, 3, 2, 1, newPapers = 1, existingPapers = 1, papersSkipped = 1)))

        composeRule.onNodeWithText("1 has no OpenAlex ID and will be skipped").assertIsDisplayed()
    }

    @Test
    fun thePreviewSaysNothingWhenNoneAreSkipped() {
        show(RestoreUiState.Preview(RestorePreview(null, 3, 2, 1, newPapers = 2, existingPapers = 1, papersSkipped = 0)))

        composeRule.onNodeWithText("will be skipped", substring = true).assertDoesNotExist()
    }

    @Test
    fun theResultSaysHowManyPapersWereSkipped() {
        show(RestoreUiState.Done(RestoreResult(2, 0, 2, 1, 0, papersSkipped = 2)))

        composeRule.onNodeWithText("2 papers without an OpenAlex ID were skipped.").assertIsDisplayed()
    }

    @Test
    fun theResultReadsWellForOne() {
        show(RestoreUiState.Done(RestoreResult(1, 1, 1, 1, 0)))

        composeRule.onNodeWithText(
            "Papers added: 1 · Notes added to existing papers: 1 · Collections added: 1 · PDFs added: 1"
        ).assertIsDisplayed()
    }

    @Test
    fun theResultSaysNothingWhenNoneWereSkipped() {
        show(RestoreUiState.Done(RestoreResult(2, 0, 2, 1, 0, papersSkipped = 0)))

        composeRule.onNodeWithText("skipped", substring = true).assertDoesNotExist()
    }
}
