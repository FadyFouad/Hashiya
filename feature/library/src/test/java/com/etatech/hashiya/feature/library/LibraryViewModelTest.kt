package com.etatech.hashiya.feature.library

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()
    private val savedStateHandle = SavedStateHandle()

    private fun TestScope.viewModel(handle: SavedStateHandle = savedStateHandle): LibraryViewModel {
        val viewModel = LibraryViewModel(handle, repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedPaper.collect() }
        return viewModel
    }

    /** Saves attention, bert and vit in that order, so the library lists vit, bert, attention. */
    private suspend fun saveSamples() = SamplePapers.all.forEach { repository.save(it) }

    private fun LibraryViewModel.titles(): List<String> = when (val state = uiState.value) {
        is LibraryUiState.Papers -> state.papers.map { it.paper.title }
        else -> error("Expected papers but was $state")
    }

    private fun LibraryViewModel.filter(): LibraryFilter = when (val state = uiState.value) {
        is LibraryUiState.Papers -> state.filter
        is LibraryUiState.NoMatches -> state.filter
        else -> error("Expected papers or no matches but was $state")
    }

    private fun counts(toRead: Int, reading: Int, read: Int) =
        mapOf(ReadingStatus.ToRead to toRead, ReadingStatus.Reading to reading, ReadingStatus.Read to read)

    private val all = listOf(SamplePapers.vit.title, SamplePapers.bert.title, SamplePapers.attention.title)

    @Test
    fun emptyLibrary() = runTest {
        assertEquals(LibraryUiState.Empty, viewModel().uiState.value)
    }

    @Test
    fun listsPapersNewestFirstWithStatusesAndCounts() = runTest {
        saveSamples()
        repository.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Reading)

        assertEquals(
            LibraryUiState.Papers(
                listOf(
                    LibraryPaper(SamplePapers.vit, ReadingStatus.ToRead),
                    LibraryPaper(SamplePapers.bert, ReadingStatus.Reading),
                    LibraryPaper(SamplePapers.attention, ReadingStatus.ToRead)
                ),
                LibraryFilter(query = "", status = null, counts = counts(toRead = 2, reading = 1, read = 0))
            ),
            viewModel().uiState.value
        )
    }

    @Test
    fun typingSearchesAfterAPause() = runTest {
        saveSamples()
        val viewModel = viewModel()

        viewModel.onQueryChange("vaswani")
        assertEquals("vaswani", viewModel.filter().query)
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 1)
        assertEquals(all, viewModel.titles())

        advanceTimeBy(2)
        assertEquals(listOf(SamplePapers.attention.title), viewModel.titles())
    }

    @Test
    fun searchKeyAppliesAtOnce() = runTest {
        saveSamples()
        val viewModel = viewModel()

        viewModel.onQueryChange("devlin")
        viewModel.onSearch()

        assertEquals(listOf(SamplePapers.bert.title), viewModel.titles())
    }

    @Test
    fun clearAppliesAtOnce() = runTest {
        saveSamples()
        val viewModel = viewModel()
        viewModel.onQueryChange("devlin")
        viewModel.onSearch()

        viewModel.onClearQuery()

        assertEquals(all, viewModel.titles())
        assertEquals("", viewModel.filter().query)
    }

    @Test
    fun chipAndSearchCombine() = runTest {
        saveSamples()
        repository.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Reading)
        repository.setStatus(SamplePapers.attention.openAlexId, ReadingStatus.Reading)
        val viewModel = viewModel()

        viewModel.onStatusFilterChange(ReadingStatus.Reading)
        assertEquals(listOf(SamplePapers.bert.title, SamplePapers.attention.title), viewModel.titles())

        viewModel.onQueryChange("devlin")
        viewModel.onSearch()
        assertEquals(listOf(SamplePapers.bert.title), viewModel.titles())
        assertEquals(LibraryFilter("devlin", ReadingStatus.Reading, counts(toRead = 0, reading = 1, read = 0)), viewModel.filter())
    }

    @Test
    fun noMatchesWhenTheLibraryHasPapersButNoneMatch() = runTest {
        saveSamples()
        val viewModel = viewModel()

        viewModel.onQueryChange("zebra")
        viewModel.onSearch()
        assertEquals(LibraryUiState.NoMatches(LibraryFilter("zebra", null, counts(0, 0, 0))), viewModel.uiState.value)

        viewModel.onClearQuery()
        viewModel.onStatusFilterChange(ReadingStatus.Read)
        assertEquals(LibraryUiState.NoMatches(LibraryFilter("", ReadingStatus.Read, counts(3, 0, 0))), viewModel.uiState.value)
    }

    @Test
    fun clearSearchAndFiltersResetsBoth() = runTest {
        saveSamples()
        val viewModel = viewModel()
        viewModel.onQueryChange("zebra")
        viewModel.onSearch()
        viewModel.onStatusFilterChange(ReadingStatus.Read)

        viewModel.onClearSearchAndFilters()

        assertEquals(all, viewModel.titles())
        assertEquals(LibraryFilter("", null, counts(3, 0, 0)), viewModel.filter())
    }

    /** After process death the typed search and the chip come back, and the search applies without waiting. */
    @Test
    fun restoresSearchAndChipFromSavedState() = runTest {
        saveSamples()
        val first = viewModel()
        first.onQueryChange("devlin")
        first.onStatusFilterChange(ReadingStatus.ToRead)

        val restored = viewModel(SavedStateHandle(savedStateHandle.keys().associateWith { savedStateHandle.get<Any>(it) }))

        assertEquals(listOf(SamplePapers.bert.title), restored.titles())
        assertEquals(LibraryFilter("devlin", ReadingStatus.ToRead, counts(1, 0, 0)), restored.filter())
    }

    @Test
    fun statusChangeUpdatesTheListAndCounts() = runTest {
        saveSamples()
        val viewModel = viewModel()

        viewModel.onStatusChange(SamplePapers.bert, ReadingStatus.Read)

        assertEquals(ReadingStatus.Read, (viewModel.uiState.value as LibraryUiState.Papers).papers[1].status)
        assertEquals(counts(toRead = 2, reading = 0, read = 1), viewModel.filter().counts)
    }

    /** With the To read chip selected, marking the open paper as Reading moves it out of the list but keeps its sheet open. */
    @Test
    fun statusChangeOutOfTheChipKeepsThePreviewOpen() = runTest {
        saveSamples()
        val viewModel = viewModel()
        viewModel.onStatusFilterChange(ReadingStatus.ToRead)
        viewModel.onPaperClick(SamplePapers.bert)

        viewModel.onStatusChange(SamplePapers.bert, ReadingStatus.Reading)

        assertEquals(listOf(SamplePapers.vit.title, SamplePapers.attention.title), viewModel.titles())
        assertEquals(LibraryPaper(SamplePapers.bert, ReadingStatus.Reading), viewModel.selectedPaper.value)
    }

    @Test
    fun statusChangeFailureShowsTheMessageAndKeepsTheStoredStatus() = runTest {
        saveSamples()
        repository.failOnSetStatus = true
        val viewModel = viewModel()

        viewModel.onStatusChange(SamplePapers.bert, ReadingStatus.Read)

        assertEquals(LibraryMessage.StatusUpdateFailed, viewModel.message.value)
        assertEquals(counts(toRead = 3, reading = 0, read = 0), viewModel.filter().counts)
        viewModel.onMessageShown()
        assertNull(viewModel.message.value)
    }

    @Test
    fun selectingAndDismissingPreview() = runTest {
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()

        viewModel.onPaperClick(SamplePapers.bert)
        assertEquals(LibraryPaper(SamplePapers.bert, ReadingStatus.ToRead), viewModel.selectedPaper.value)
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

    /** Removing the only paper a search matched empties the library: Empty, not "No papers match". */
    @Test
    fun removingTheLastPaperDuringASearchShowsEmpty() = runTest {
        repository.save(SamplePapers.bert)
        val viewModel = viewModel()
        viewModel.onQueryChange("devlin")
        viewModel.onSearch()

        viewModel.onRemove(SamplePapers.bert)

        assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun undoRestoresPaperInItsPlaceWithItsStatus() = runTest {
        saveSamples()
        repository.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Reading)
        val viewModel = viewModel()

        viewModel.onRemove(SamplePapers.bert)
        viewModel.onUndoRemove()

        assertEquals(
            listOf(
                LibraryPaper(SamplePapers.vit, ReadingStatus.ToRead),
                LibraryPaper(SamplePapers.bert, ReadingStatus.Reading),
                LibraryPaper(SamplePapers.attention, ReadingStatus.ToRead)
            ),
            repository.observeLibrary("", null).first()
        )
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
        assertEquals(listOf(SamplePapers.bert), repository.observeLibrary("", null).first().map { it.paper })
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
