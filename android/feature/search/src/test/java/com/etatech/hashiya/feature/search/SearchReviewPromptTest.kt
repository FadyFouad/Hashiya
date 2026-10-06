package com.etatech.hashiya.feature.search

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.testing.FakeAnalytics
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePaperLookupRepository
import com.etatech.hashiya.core.testing.FakeReviewPrompt
import com.etatech.hashiya.core.testing.FakeSearchRepository
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchReviewPromptTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val libraryRepository = FakeLibraryRepository()
    private val review = FakeReviewPrompt()
    private val paper = SamplePapers.attention

    private fun TestScope.viewModel(): SearchViewModel {
        val viewModel = SearchViewModel(
            SavedStateHandle(),
            FakeSearchRepository(),
            libraryRepository,
            FakeUserPreferencesRepository(),
            FakePaperLookupRepository(),
            FakeAnalytics(),
            review
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedItem.collect() }
        runCurrent()
        return viewModel
    }

    @Test
    fun aSaveCountsAndAsksWhenNoPreviewIsOpen() = runTest {
        val viewModel = viewModel()
        viewModel.onToggleSave(PaperItem(paper, inLibrary = false))
        runCurrent()
        assertEquals(1, review.saves)
        assertEquals(1, review.asks)
    }

    @Test
    fun aSaveFromThePreviewAsksWhenThePreviewCloses() = runTest {
        val viewModel = viewModel()
        viewModel.onPaperClick(paper)
        viewModel.onToggleSave(PaperItem(paper, inLibrary = false))
        runCurrent()
        assertEquals(1, review.saves)
        assertEquals(0, review.asks)
        viewModel.onDismissPreview()
        assertEquals(1, review.asks)
    }

    @Test
    fun aRemovalOrAFailedSaveDoesNotCount() = runTest {
        val viewModel = viewModel()
        viewModel.onToggleSave(PaperItem(paper, inLibrary = true))
        runCurrent()
        libraryRepository.failOnSave = true
        viewModel.onToggleSave(PaperItem(paper, inLibrary = false))
        runCurrent()
        assertEquals(0, review.saves)
        assertEquals(0, review.asks)
    }
}
