package com.etatech.hashiya.feature.library

import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()

    private fun TestScope.viewModel(): LibraryViewModel {
        val viewModel = LibraryViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedPaper.collect() }
        return viewModel
    }

    @Test
    fun emptyLibrary() = runTest {
        assertEquals(LibraryUiState.Empty, viewModel().uiState.value)
    }

    @Test
    fun listsPapersNewestFirst() = runTest {
        repository.save(SamplePapers.attention)
        repository.save(SamplePapers.bert)

        assertEquals(LibraryUiState.Papers(listOf(SamplePapers.bert, SamplePapers.attention)), viewModel().uiState.value)
    }

    @Test
    fun selectingAndDismissingPreview() = runTest {
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()

        viewModel.onPaperClick(SamplePapers.bert)
        assertEquals(SamplePapers.bert, viewModel.selectedPaper.value)
        viewModel.onDismissPreview()
        assertNull(viewModel.selectedPaper.value)
    }

    @Test
    fun removingOffersUndoAndClosesPreview() = runTest {
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()
        viewModel.onPaperClick(SamplePapers.bert)

        viewModel.onRemove(SamplePapers.bert)

        assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
        assertEquals(SamplePapers.bert, viewModel.pendingUndo.value?.paper)
        assertNull(viewModel.selectedPaper.value)
    }

    @Test
    fun undoRestoresPaperInItsPlace() = runTest {
        repository.save(SamplePapers.attention)
        repository.save(SamplePapers.bert)
        repository.save(SamplePapers.vit)
        val viewModel = viewModel()

        viewModel.onRemove(SamplePapers.bert)
        viewModel.onUndoRemove()

        assertEquals(listOf(SamplePapers.vit, SamplePapers.bert, SamplePapers.attention), repository.observeSavedPapers().first())
        assertNull(viewModel.pendingUndo.value)
    }

    @Test
    fun twoQuickRemovalsKeepOnlyTheLatestForUndo() = runTest {
        repository.save(SamplePapers.attention)
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()

        viewModel.onRemove(SamplePapers.attention)
        viewModel.onRemove(SamplePapers.bert)
        assertEquals(SamplePapers.bert, viewModel.pendingUndo.value?.paper)

        viewModel.onUndoRemove()
        assertEquals(listOf(SamplePapers.bert), repository.observeSavedPapers().first())
    }

    @Test
    fun dismissingUndoForgetsRemovedPaper() = runTest {
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()
        viewModel.onRemove(SamplePapers.bert)

        viewModel.onUndoDismissed()

        assertNull(viewModel.pendingUndo.value)
        assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
    }
}
