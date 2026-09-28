package com.etatech.hashiya.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.paging.testing.asSnapshot
import com.etatech.hashiya.core.data.repository.LookupResult
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePaperLookupRepository
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
    private val lookupRepository = FakePaperLookupRepository()
    private val savedStateHandle = SavedStateHandle()

    private val bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"

    private fun TestScope.viewModel(handle: SavedStateHandle = savedStateHandle): SearchViewModel {
        val viewModel = SearchViewModel(handle, searchRepository, libraryRepository, userPreferencesRepository, lookupRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedItem.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.savedIds.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.lookupState.collect() }
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
        assertEquals(listOf(SamplePapers.bert), libraryRepository.observeLibrary("", null).first().map { it.paper })

        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = true))
        runCurrent()
        assertTrue(libraryRepository.observeLibrary("", null).first().isEmpty())
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
    fun removeFailureShowsRemoveMessage() = runTest {
        libraryRepository.save(SamplePapers.bert)
        libraryRepository.failOnRemove = true
        val viewModel = viewModel()

        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = true))
        runCurrent()

        assertEquals(SearchMessage.RemoveFailed, viewModel.message.value)
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

    @Test
    fun pastedDoiIsLookedUpInsteadOfSearched() = runTest {
        val doi = PaperIdentifier.Doi("10.1038/nature14539")
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        val viewModel = viewModel()

        viewModel.onTextChange("https://doi.org/10.1038/nature14539")
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(listOf(doi), lookupRepository.lookups)
        assertTrue(searchRepository.queries.isEmpty())
        assertEquals(LookupUiState.Found(SamplePapers.attention), viewModel.lookupState.value)
    }

    @Test
    fun lookupShowsLookingUntilItFinishes() = runTest {
        val arxiv = PaperIdentifier.Arxiv("1706.03762")
        lookupRepository.results[arxiv] = LookupResult.Found(SamplePapers.attention)
        lookupRepository.holdLookups()
        val viewModel = viewModel()

        viewModel.onSuggestion("1706.03762")
        runCurrent()
        assertEquals(LookupUiState.Looking(arxiv), viewModel.lookupState.value)

        lookupRepository.releaseLookups()
        runCurrent()
        assertEquals(LookupUiState.Found(SamplePapers.attention), viewModel.lookupState.value)
    }

    @Test
    fun textContainingADoiIsAKeywordSearch() = runTest {
        val viewModel = viewModel()

        viewModel.onTextChange("a study of 10.1038/nature14539")
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(listOf(SearchQuery("a study of 10.1038/nature14539")), searchRepository.queries)
        assertTrue(lookupRepository.lookups.isEmpty())
        assertNull(viewModel.lookupState.value)
    }

    @Test
    fun pastedLinkWithoutAnIdSaysSoInsteadOfSearching() = runTest {
        val viewModel = viewModel()

        viewModel.onTextChange("https://example.com/some/article")
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(LookupUiState.NoIdInLink, viewModel.lookupState.value)
        assertTrue(searchRepository.queries.isEmpty())
        assertTrue(lookupRepository.lookups.isEmpty())

        viewModel.onTextChange("graph neural networks")
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(listOf(SearchQuery("graph neural networks")), searchRepository.queries)
        assertNull(viewModel.lookupState.value)
    }

    @Test
    fun newerLookupReplacesOlderOne() = runTest {
        val first = PaperIdentifier.Doi("10.1000/first")
        val second = PaperIdentifier.Doi("10.1000/second")
        lookupRepository.results[first] = LookupResult.Found(SamplePapers.attention)
        lookupRepository.results[second] = LookupResult.Found(SamplePapers.bert)
        lookupRepository.holdLookups()
        val viewModel = viewModel()

        viewModel.onSuggestion("10.1000/first")
        runCurrent()
        viewModel.onSuggestion("10.1000/second")
        runCurrent()
        lookupRepository.releaseLookups()
        runCurrent()

        assertEquals(listOf(first, second), lookupRepository.lookups)
        assertEquals(LookupUiState.Found(SamplePapers.bert), viewModel.lookupState.value)
    }

    @Test
    fun retryRerunsTheLookup() = runTest {
        val doi = PaperIdentifier.Doi("10.1038/nature14539")
        lookupRepository.results[doi] = LookupResult.Failed(SearchError.Offline)
        val viewModel = viewModel()
        viewModel.onSuggestion("10.1038/nature14539")
        runCurrent()
        assertEquals(LookupUiState.Failed(SearchError.Offline), viewModel.lookupState.value)

        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        viewModel.onRetryLookup()
        runCurrent()

        assertEquals(listOf(doi, doi), lookupRepository.lookups)
        assertEquals(LookupUiState.Found(SamplePapers.attention), viewModel.lookupState.value)
    }

    @Test
    fun changingTheApiKeyRerunsTheLookup() = runTest {
        val viewModel = viewModel()
        viewModel.onSuggestion("1706.03762")
        runCurrent()

        userPreferencesRepository.setUserApiKey("new-key")
        runCurrent()

        assertEquals(2, lookupRepository.lookups.size)
    }

    @Test
    fun notFoundOffersTheArxivTitle() = runTest {
        val bert = PaperIdentifier.Arxiv("1810.04805")
        lookupRepository.results[bert] = LookupResult.NotFound(arxivTitle = bertTitle)
        val viewModel = viewModel()

        viewModel.onSuggestion("1810.04805")
        runCurrent()

        assertEquals(LookupUiState.NotFound(bert, searchTitle = bertTitle), viewModel.lookupState.value)
    }

    @Test
    fun routeQueryIsSubmittedImmediately() = runTest {
        val handle = SavedStateHandle(mapOf("query" to "arXiv:1706.03762", "pageTitle" to "Attention Is All You Need"))

        val viewModel = viewModel(handle)

        assertEquals(listOf(PaperIdentifier.Arxiv("1706.03762")), lookupRepository.lookups)
        assertEquals("arXiv:1706.03762", viewModel.uiState.value.text)
    }

    @Test
    fun notFoundFallsBackToTheSharedPageTitle() = runTest {
        val handle = SavedStateHandle(mapOf("query" to "10.1038/nature14539", "pageTitle" to "Deep learning"))

        val viewModel = viewModel(handle)

        assertEquals(
            LookupUiState.NotFound(PaperIdentifier.Doi("10.1038/nature14539"), searchTitle = "Deep learning"),
            viewModel.lookupState.value
        )
    }

    @Test
    fun editingTheTextForgetsThePageTitle() = runTest {
        val viewModel = viewModel(SavedStateHandle(mapOf("query" to "10.1038/nature14539", "pageTitle" to "Deep learning")))

        viewModel.onTextChange("1810.04805")
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(LookupUiState.NotFound(PaperIdentifier.Arxiv("1810.04805"), searchTitle = null), viewModel.lookupState.value)
    }

    @Test
    fun routeArgsNotReappliedOverRestoredText() = runTest {
        val handle = SavedStateHandle(mapOf("query" to "10.1038/nature14539", "search_text" to "bert"))

        val viewModel = viewModel(handle)

        assertEquals("bert", viewModel.uiState.value.text)
        assertTrue(lookupRepository.lookups.isEmpty())
        assertEquals(listOf(SearchQuery("bert")), searchRepository.queries)
    }

    @Test
    fun routeArgsAppliedOnlyOnce() = runTest {
        val handle = SavedStateHandle(mapOf("query" to "10.1038/nature14539", "focusSearch" to true))
        val first = viewModel(handle)
        first.onTextChange("gpt")
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()

        val recreated = viewModel(handle)

        assertEquals("gpt", recreated.uiState.value.text)
        assertFalse(recreated.focusSearch.value)
        assertEquals(1, lookupRepository.lookups.size)
    }

    @Test
    fun routeNoteShowsUntilTheTextChanges() = runTest {
        val viewModel = viewModel(SavedStateHandle(mapOf("query" to "Deep learning", "note" to SearchNote.NoIdInShare.name)))
        assertEquals(SearchNote.NoIdInShare, viewModel.note.value)

        viewModel.onTextChange("Deep learning review")

        assertNull(viewModel.note.value)
    }

    @Test
    fun focusIsRequestedOnce() = runTest {
        val viewModel = viewModel(SavedStateHandle(mapOf("focusSearch" to true)))
        assertTrue(viewModel.focusSearch.value)

        viewModel.onFocusHandled()

        assertFalse(viewModel.focusSearch.value)
    }
}
