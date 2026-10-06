package com.etatech.hashiya.feature.library

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.model.CitationStyle
import com.etatech.hashiya.core.testing.FakeAnalytics
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.FakeReviewPrompt
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryReviewPromptTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val review = FakeReviewPrompt()

    private suspend fun TestScope.viewModel(): LibraryViewModel {
        SamplePapers.all.forEach { library.save(it) }
        val viewModel = LibraryViewModel(
            SavedStateHandle(),
            library,
            FakeCollectionsRepository(library),
            FakeCitationRepository(),
            FakeUserPreferencesRepository(),
            FakePdfRepository(),
            FakeAnalytics(),
            review
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.header.collect() }
        return viewModel
    }

    @Test
    fun anExportCountsAndAsksOnlyAfterTheShareSheetCloses() = runTest {
        val viewModel = viewModel()
        viewModel.onExport(CitationStyle.Bibtex)
        advanceUntilIdle()
        viewModel.onExportShared()
        assertEquals(1, review.exports)
        assertEquals(0, review.asks)
        viewModel.onScreenResumed()
        assertEquals(1, review.asks)
        viewModel.onScreenResumed()
        assertEquals(1, review.asks) // one export, one ask
    }

    @Test
    fun aFailedExportNeitherCountsNorAsks() = runTest {
        val viewModel = viewModel()
        viewModel.onExport(CitationStyle.Bibtex)
        advanceUntilIdle()
        viewModel.onExportFailed()
        viewModel.onScreenResumed()
        assertEquals(0, review.exports)
        assertEquals(0, review.asks)
    }
}
