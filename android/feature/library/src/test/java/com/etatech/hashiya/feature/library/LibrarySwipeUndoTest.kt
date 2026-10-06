package com.etatech.hashiya.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.analytics.NoOpAnalytics
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.FakeReviewPrompt
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Swipe, then Undo, through the real ViewModel: the restored row must stay in the list. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class LibrarySwipeUndoTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repository = CountingLibraryRepository()

    private fun viewModel() = LibraryViewModel(
        SavedStateHandle(),
        repository,
        FakeCollectionsRepository(FakeLibraryRepository()),
        FakeCitationRepository(),
        FakePdfRepository(),
        NoOpAnalytics,
        FakeReviewPrompt()
    )

    @Test
    fun undoAfterSwipeKeepsThePaperInTheList() {
        runBlocking {
            repository.save(SamplePapers.attention)
            repository.save(SamplePapers.bert)
            repository.save(SamplePapers.vit)
        }
        val viewModel = viewModel()
        composeRule.setContent {
            HashiyaTheme {
                LibraryScreen(onGoToSearch = {}, onAddPaper = {}, onOpenSettings = {}, onOpenPaper = {}, viewModel = viewModel)
            }
        }

        // The test locale is English (left-to-right), so a left swipe is end-to-start.
        composeRule.onNodeWithText(SamplePapers.bert.title).performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Removed from library").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(SamplePapers.bert.title).assertIsDisplayed()
        composeRule.onNodeWithText("3 papers").assertIsDisplayed()
        assertEquals(1, repository.removeCount)
    }

    /** Details' "Remove from library" arrives as a request from the back stack entry: removed once, with Undo, then cleared. */
    @Test
    fun removeRequestFromDetailsRemovesOnceWithUndo() {
        runBlocking { repository.save(SamplePapers.bert) }
        val viewModel = viewModel()
        var handled = 0
        composeRule.setContent {
            HashiyaTheme {
                LibraryScreen(
                    onGoToSearch = {},
                    onAddPaper = {},
                    onOpenSettings = {},
                    onOpenPaper = {},
                    removeRequest = SamplePapers.bert.openAlexId,
                    onRemoveRequestHandled = { handled++ },
                    viewModel = viewModel
                )
            }
        }

        composeRule.onNodeWithText("Removed from library").assertIsDisplayed()
        composeRule.waitForIdle()
        assertEquals(1, repository.removeCount)
        assertEquals(1, handled)
    }
}

private class CountingLibraryRepository(private val delegate: FakeLibraryRepository = FakeLibraryRepository()) :
    LibraryRepository by delegate {
    var removeCount = 0
        private set

    override suspend fun remove(openAlexId: String): RemovedPaper? {
        removeCount++
        return delegate.remove(openAlexId)
    }
}
