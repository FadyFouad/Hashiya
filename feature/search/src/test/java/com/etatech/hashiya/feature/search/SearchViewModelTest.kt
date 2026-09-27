package com.etatech.hashiya.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.paging.testing.asSnapshot
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakeSearchRepository
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val searchRepository = FakeSearchRepository()
    private val libraryRepository = FakeLibraryRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val savedStateHandle = SavedStateHandle()

    private fun TestScope.viewModel(handle: SavedStateHandle = savedStateHandle): SearchViewModel {
        val viewModel = SearchViewModel(handle, searchRepository, libraryRepository, userPreferencesRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedItem.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.savedIds.collect() }
        runCurrent()
        return viewModel
    }

    @Test
    fun blankQueryIsIdleAndDoesNotSearch() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.isIdle)
        assertTrue(searchRepository.queries.isEmpty())
    }

    @Test
    fun typingIsDebounced() = runTest {
        val viewModel = viewModel()
        viewModel.onTextChange("t")
        advanceTimeBy(100)
        viewModel.onTextChange("tra")
        advanceTimeBy(100)
        viewModel.onTextChange("transformer")

        advanceTimeBy(DEBOUNCE_MS - 1)
        assertTrue(searchRepository.queries.isEmpty())

        advanceTimeBy(2)
        assertEquals(listOf(SearchQuery("transformer")), searchRepository.queries)
        assertFalse(viewModel.uiState.value.isIdle)
    }

    @Test
    fun searchActionSkipsDebounce() = runTest {
        val viewModel = viewModel()
        viewModel.onTextChange("bert")
        viewModel.onSearchAction()
        runCurrent()

        assertEquals(listOf(SearchQuery("bert")), searchRepository.queries)
    }

    @Test
    fun trimsQueryText() = runTest {
        val viewModel = viewModel()
        viewModel.onTextChange("  bert  ")
        viewModel.onSearchAction()
        runCurrent()

        assertEquals("bert", searchRepository.queries.single().text)
        assertEquals("  bert  ", viewModel.uiState.value.text)
    }

    @Test
    fun filterChangesApplyImmediately() = runTest {
        val viewModel = viewModel()
        viewModel.onSuggestion("bert")
        runCurrent()

        viewModel.onSortChange(SearchSort.MostCited)
        runCurrent()
        viewModel.onYearFilterChange(YearFilter.Since(2020))
        runCurrent()
        viewModel.onOpenAccessToggle()
        runCurrent()

        assertEquals(
            listOf(
                SearchQuery("bert"),
                SearchQuery("bert", sort = SearchSort.MostCited),
                SearchQuery("bert", sort = SearchSort.MostCited, years = YearFilter.Since(2020)),
                SearchQuery("bert", sort = SearchSort.MostCited, years = YearFilter.Since(2020), openAccessOnly = true)
            ),
            searchRepository.queries
        )
    }

    @Test
    fun clearFiltersKeepsSort() = runTest {
        val viewModel = viewModel()
        viewModel.onSuggestion("bert")
        viewModel.onSortChange(SearchSort.Newest)
        viewModel.onYearFilterChange(YearFilter.Between(2015, 2020))
        viewModel.onOpenAccessToggle()
        runCurrent()

        viewModel.onClearFilters()
        runCurrent()

        assertEquals(SearchQuery("bert", sort = SearchSort.Newest), searchRepository.queries.last())
        assertFalse(viewModel.uiState.value.hasActiveFilters)
    }

    @Test
    fun clearingTextReturnsToIdleImmediately() = runTest {
        val viewModel = viewModel()
        viewModel.onSuggestion("bert")
        runCurrent()

        viewModel.onTextChange("")
        runCurrent()

        assertTrue(viewModel.uiState.value.isIdle)
    }

    @Test
    fun changingTheApiKeyRerunsTheActiveSearch() = runTest {
        val viewModel = viewModel()
        viewModel.onSuggestion("bert")
        runCurrent()
        assertEquals(listOf(SearchQuery("bert")), searchRepository.queries)

        userPreferencesRepository.setUserApiKey("fixed-key")
        runCurrent()

        assertEquals(listOf(SearchQuery("bert"), SearchQuery("bert")), searchRepository.queries)
        assertEquals("bert", viewModel.uiState.value.text)
    }

    @Test
    fun changingTheApiKeyWhileIdleDoesNotSearch() = runTest {
        viewModel()

        userPreferencesRepository.setUserApiKey("fixed-key")
        runCurrent()

        assertTrue(searchRepository.queries.isEmpty())
    }

    @Test
    fun exposesPapersAndSavedIdsSeparately() = runTest {
        searchRepository.papers = listOf(SamplePapers.attention, SamplePapers.bert)
        libraryRepository.save(SamplePapers.attention)
        val viewModel = viewModel()
        viewModel.onSuggestion("transformers")
        runCurrent()

        assertEquals(listOf(SamplePapers.attention, SamplePapers.bert), viewModel.papers.asSnapshot())
        assertEquals(setOf(SamplePapers.attention.openAlexId), viewModel.savedIds.value)
    }

    @Test
    fun savingUpdatesSavedIdsWithoutNewSearch() = runTest {
        searchRepository.papers = listOf(SamplePapers.bert)
        val viewModel = viewModel()
        viewModel.onSuggestion("bert")
        runCurrent()

        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = false))
        runCurrent()

        assertEquals(setOf(SamplePapers.bert.openAlexId), viewModel.savedIds.value)
        assertEquals(1, searchRepository.queries.size)
    }

    @Test
    fun toggleSaveSavesThenRemoves() = runTest {
        val viewModel = viewModel()

        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = false))
        runCurrent()
        assertEquals(listOf(SamplePapers.bert), libraryRepository.observeSavedPapers().first())

        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = true))
        runCurrent()
        assertTrue(libraryRepository.observeSavedPapers().first().isEmpty())
    }

    @Test
    fun saveFailureShowsMessageOnce() = runTest {
        libraryRepository.failOnSave = true
        val viewModel = viewModel()

        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = false))
        runCurrent()
        assertEquals(SearchMessage.SaveFailed, viewModel.message.value)

        viewModel.onMessageShown()
        assertNull(viewModel.message.value)
    }

    @Test
    fun selectedItemFollowsLibraryState() = runTest {
        val viewModel = viewModel()
        viewModel.onPaperClick(SamplePapers.bert)
        runCurrent()
        assertEquals(PaperItem(SamplePapers.bert, inLibrary = false), viewModel.selectedItem.value)

        libraryRepository.save(SamplePapers.bert)
        runCurrent()
        assertEquals(PaperItem(SamplePapers.bert, inLibrary = true), viewModel.selectedItem.value)

        viewModel.onDismissPreview()
        runCurrent()
        assertNull(viewModel.selectedItem.value)
    }

    @Test
    fun exposesTotalCount() = runTest {
        searchRepository.totalCount = 48210
        val viewModel = viewModel()
        viewModel.onSuggestion("bert")
        runCurrent()

        assertEquals(48210L, viewModel.uiState.value.totalCount)
    }

    @Test
    fun restoresQueryAfterProcessDeath() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                "search_text" to "bert",
                "search_sort" to "MostCited",
                "search_year_kind" to "since",
                "search_year_from" to 2020,
                "search_oa" to true
            )
        )

        val viewModel = viewModel(handle)

        assertEquals(
            listOf(SearchQuery("bert", SearchSort.MostCited, YearFilter.Since(2020), openAccessOnly = true)),
            searchRepository.queries
        )
        assertEquals("bert", viewModel.uiState.value.text)
    }

    @Test
    fun savesQueryForProcessDeath() = runTest {
        val viewModel = viewModel()
        viewModel.onTextChange("bert")
        viewModel.onYearFilterChange(YearFilter.Between(2015, 2020))
        viewModel.onOpenAccessToggle()
        runCurrent()

        assertEquals("bert", savedStateHandle.get<String>("search_text"))
        assertEquals("between", savedStateHandle.get<String>("search_year_kind"))
        assertEquals(2015, savedStateHandle.get<Int>("search_year_from"))
        assertEquals(2020, savedStateHandle.get<Int>("search_year_to"))
        assertEquals(true, savedStateHandle.get<Boolean>("search_oa"))
    }
}
