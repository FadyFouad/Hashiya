package com.etatech.hashiya.feature.search

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.LimitKind
import com.etatech.hashiya.core.analytics.ResearchCategory
import com.etatech.hashiya.core.analytics.ResultsBucket
import com.etatech.hashiya.core.analytics.SaveSource
import com.etatech.hashiya.core.analytics.SearchKind
import com.etatech.hashiya.core.analytics.SearchRoute
import com.etatech.hashiya.core.data.repository.FirstPage
import com.etatech.hashiya.core.data.repository.LookupResult
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchAnalyticsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val searchRepository = FakeSearchRepository()
    private val libraryRepository = FakeLibraryRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val lookupRepository = FakePaperLookupRepository()
    private val analytics = FakeAnalytics()

    private val doi = PaperIdentifier.Doi("10.1038/nature14539")
    private val bertFirstPage = FirstPage(48_210, ResearchCategory.Ai)

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): SearchViewModel {
        val viewModel = SearchViewModel(
            handle,
            searchRepository,
            libraryRepository,
            userPreferencesRepository,
            lookupRepository,
            analytics,
            FakeReviewPrompt()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedItem.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.savedIds.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.lookupState.collect() }
        runCurrent()
        return viewModel
    }

    private fun TestScope.search(viewModel: SearchViewModel, text: String) {
        viewModel.onTextChange(text)
        viewModel.onSearchAction()
        runCurrent()
    }

    private fun keyword(
        hasFilters: Boolean = false,
        route: SearchRoute = SearchRoute.Shared,
        results: ResultsBucket = ResultsBucket.Over200,
        category: ResearchCategory? = ResearchCategory.Ai
    ) = AnalyticsEvent.Search(SearchKind.Keyword, hasFilters, route, results, category)

    private fun lookup(kind: SearchKind, results: ResultsBucket = ResultsBucket.UpTo25) =
        AnalyticsEvent.Search(kind, hasFilters = false, route = SearchRoute.Shared, results = results, category = null)

    @Test
    fun aSubmittedKeywordSearchSendsSearchWhenItsFirstPageArrives() = runTest {
        searchRepository.firstPage = bertFirstPage
        val viewModel = viewModel()

        search(viewModel, "bert")

        assertEquals(listOf(keyword()), analytics.events)
    }

    @Test
    fun nothingIsSentUntilTheFirstPageArrives() = runTest {
        val viewModel = viewModel()
        search(viewModel, "bert")
        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)

        searchRepository.lastFirstPage.value = FirstPage(12, ResearchCategory.Unknown)
        runCurrent()

        assertEquals(listOf(keyword(results = ResultsBucket.UpTo25, category = ResearchCategory.Unknown)), analytics.events)
    }

    @Test
    fun aSearchIsSentOnceEvenIfItsFirstPageLoadsAgain() = runTest {
        searchRepository.firstPage = bertFirstPage
        val viewModel = viewModel()
        search(viewModel, "bert")

        searchRepository.lastFirstPage.value = FirstPage(48_300, ResearchCategory.Ai)
        runCurrent()

        assertEquals(listOf(keyword()), analytics.events)
    }

    @Test
    fun aFilteredSearchSaysSo() = runTest {
        searchRepository.firstPage = bertFirstPage
        val viewModel = viewModel()

        viewModel.onOpenAccessToggle()
        search(viewModel, "bert")
        assertEquals(listOf(keyword(hasFilters = true)), analytics.events)

        viewModel.onYearFilterChange(YearFilter.Since(2020))
        runCurrent()
        assertEquals(listOf(keyword(hasFilters = true), keyword(hasFilters = true)), analytics.events)
    }

    @Test
    fun sortIsNotAFilterButChangingItIsASearch() = runTest {
        searchRepository.firstPage = bertFirstPage
        val viewModel = viewModel()
        search(viewModel, "bert")

        viewModel.onSortChange(SearchSort.MostCited)
        runCurrent()

        assertEquals(listOf(keyword(), keyword()), analytics.events)
    }

    @Test
    fun clearingFiltersIsASearch() = runTest {
        searchRepository.firstPage = bertFirstPage
        val viewModel = viewModel()
        viewModel.onOpenAccessToggle()
        search(viewModel, "bert")

        viewModel.onClearFilters()
        runCurrent()

        assertEquals(listOf(keyword(hasFilters = true), keyword(hasFilters = false)), analytics.events)
    }

    @Test
    fun aPersonalKeyMakesTheRouteUser() = runTest {
        userPreferencesRepository.setUserApiKey("my-own-key")
        searchRepository.firstPage = bertFirstPage
        val viewModel = viewModel()

        search(viewModel, "bert")

        assertEquals(listOf(keyword(route = SearchRoute.User)), analytics.events)
    }

    @Test
    fun theRouteTheResponseReportsIsSent() = runTest {
        userPreferencesRepository.setUserApiKey("my-own-key")
        searchRepository.firstPage = bertFirstPage.copy(route = SearchRoute.Keyless)
        val viewModel = viewModel()

        search(viewModel, "bert")

        assertEquals(listOf(keyword(route = SearchRoute.Keyless)), analytics.events)
    }

    @Test
    fun aCachedFirstPageIsSentAsCached() = runTest {
        searchRepository.firstPage = bertFirstPage.copy(route = SearchRoute.Cached)
        val viewModel = viewModel()

        search(viewModel, "bert")

        assertEquals(listOf(keyword(route = SearchRoute.Cached)), analytics.events)
    }

    @Test
    fun aFirstPageStoppedByTheDailyLimitSendsOnlyTheLimit() = runTest {
        searchRepository.error = SearchError.DailyLimit(resetAtMillis = 1L)
        val viewModel = viewModel()

        search(viewModel, "bert")

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.SearchLimitReached(LimitKind.Daily)), analytics.events)
    }

    @Test
    fun aLaterPageStoppedByTheDailyLimitSendsTheLimitOnce() = runTest {
        searchRepository.firstPage = bertFirstPage
        val viewModel = viewModel()
        search(viewModel, "bert")

        searchRepository.lastDailyLimitHit.value = true
        runCurrent()
        searchRepository.lastDailyLimitHit.value = false
        runCurrent()
        searchRepository.lastDailyLimitHit.value = true
        runCurrent()

        assertEquals(listOf(keyword(), AnalyticsEvent.SearchLimitReached(LimitKind.Daily)), analytics.events)
    }

    @Test
    fun reachingThePageCapSendsTheLimitAfterSearchOnce() = runTest {
        searchRepository.firstPage = bertFirstPage
        val viewModel = viewModel()
        search(viewModel, "bert")

        searchRepository.lastCapReached.value = 160
        runCurrent()
        searchRepository.lastCapReached.value = 160
        runCurrent()

        assertEquals(listOf(keyword(), AnalyticsEvent.SearchLimitReached(LimitKind.PageCap)), analytics.events)
    }

    @Test
    fun noEventCarriesTheSearchText() = runTest {
        searchRepository.firstPage = bertFirstPage
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        val viewModel = viewModel()

        search(viewModel, "Attention Is All You Need")
        searchRepository.lastPagesLoaded.value = 2
        runCurrent()
        search(viewModel, "10.1038/nature14539")
        viewModel.onToggleSave(PaperItem(SamplePapers.attention, inLibrary = false))
        runCurrent()
        viewModel.onToggleSave(PaperItem(SamplePapers.attention, inLibrary = true))
        runCurrent()

        assertEquals(5, analytics.events.size)
        val sent = analytics.events.flatMap { listOf(it.name) + it.parameters.keys + it.parameters.values }
        for (secret in listOf("Attention", "Need", "10.1038", "nature14539", SamplePapers.attention.openAlexId)) {
            assertTrue("$secret was sent in $sent", sent.none { it.contains(secret, ignoreCase = true) })
        }
    }

    @Test
    fun furtherPagesAreCounted() = runTest {
        searchRepository.firstPage = bertFirstPage
        searchRepository.pagesLoaded = 1
        val viewModel = viewModel()
        search(viewModel, "bert")

        searchRepository.lastPagesLoaded.value = 2
        runCurrent()

        assertEquals(listOf(keyword(), AnalyticsEvent.SearchMore(2)), analytics.events)
    }

    /** A refresh makes a new PagingSource, which counts its pages from 1 again: those pages were already counted. */
    @Test
    fun pagesLoadedAgainAfterARefreshAreNotCountedTwice() = runTest {
        searchRepository.firstPage = bertFirstPage
        searchRepository.pagesLoaded = 1
        val viewModel = viewModel()
        search(viewModel, "bert")
        searchRepository.lastPagesLoaded.value = 2
        runCurrent()

        for (page in listOf(1, 2, 3)) {
            searchRepository.lastPagesLoaded.value = page
            runCurrent()
        }

        assertEquals(listOf(keyword(), AnalyticsEvent.SearchMore(2), AnalyticsEvent.SearchMore(3)), analytics.events)
    }

    @Test
    fun aSupersededSearchStopsCounting() = runTest {
        searchRepository.firstPage = bertFirstPage
        val viewModel = viewModel()
        search(viewModel, "bert")
        val firstSearchPages = searchRepository.lastPagesLoaded

        search(viewModel, "gpt")
        firstSearchPages.value = 2
        runCurrent()

        assertEquals(listOf(keyword(), keyword()), analytics.events)
    }

    /** A restore re-runs a search the user didn't start again, but each further page is still one they scrolled to. */
    @Test
    fun aRestoredSearchSendsNoSearchButCountsFurtherPages() = runTest {
        searchRepository.firstPage = bertFirstPage
        val handle = SavedStateHandle(mapOf("search_text" to "bert", "search_oa" to true))

        viewModel(handle)
        runCurrent()
        assertEquals(1, searchRepository.queries.size)
        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)

        searchRepository.lastPagesLoaded.value = 2
        runCurrent()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.SearchMore(2)), analytics.events)
    }

    @Test
    fun anApiKeyChangeRerunSendsNoSearchButCountsFurtherPages() = runTest {
        searchRepository.firstPage = bertFirstPage
        val viewModel = viewModel()
        search(viewModel, "bert")
        assertEquals(1, analytics.events.size)

        // Submitting the same text again doesn't search, so it mustn't make the key change's re-run count either.
        viewModel.onSearchAction()
        runCurrent()
        userPreferencesRepository.setUserApiKey("new-key")
        runCurrent()

        assertEquals(2, searchRepository.queries.size)
        assertEquals(listOf(keyword()), analytics.events)

        searchRepository.lastPagesLoaded.value = 2
        runCurrent()

        assertEquals(listOf(keyword(), AnalyticsEvent.SearchMore(2)), analytics.events)
    }

    @Test
    fun aSearchFromAShareCounts() = runTest {
        searchRepository.firstPage = bertFirstPage

        viewModel(SavedStateHandle(mapOf("query" to "Deep learning", "note" to SearchNote.NoIdInShare.name)))

        assertEquals(listOf(keyword()), analytics.events)
    }

    @Test
    fun aDoiLookupIsASearchOfKindDoi() = runTest {
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        val viewModel = viewModel()

        search(viewModel, "10.1038/nature14539")

        assertEquals(listOf(lookup(SearchKind.Doi)), analytics.events)
    }

    @Test
    fun anArxivLookupIsASearchOfKindArxiv() = runTest {
        lookupRepository.results[PaperIdentifier.Arxiv("1706.03762")] = LookupResult.Found(SamplePapers.attention)
        val viewModel = viewModel()

        viewModel.onSuggestion("1706.03762")
        runCurrent()

        assertEquals(listOf(lookup(SearchKind.Arxiv)), analytics.events)
    }

    @Test
    fun aLookupThatFindsNothingHasNoResults() = runTest {
        lookupRepository.results[doi] = LookupResult.NotFound(arxivTitle = null)
        val viewModel = viewModel()

        search(viewModel, "10.1038/nature14539")

        assertEquals(listOf(lookup(SearchKind.Doi, ResultsBucket.Zero)), analytics.events)
    }

    @Test
    fun aFailedLookupSendsNothingUntilARetrySucceeds() = runTest {
        lookupRepository.results[doi] = LookupResult.Failed(SearchError.Offline)
        val viewModel = viewModel()
        search(viewModel, "10.1038/nature14539")
        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)

        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        viewModel.onRetryLookup()
        runCurrent()

        assertEquals(listOf(lookup(SearchKind.Doi)), analytics.events)
    }

    @Test
    fun aLinkWithAnIdIsASearchOfKindLink() = runTest {
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        val viewModel = viewModel()

        search(viewModel, "https://doi.org/10.1038/nature14539")

        assertEquals(listOf(lookup(SearchKind.Link)), analytics.events)
    }

    @Test
    fun aLinkWithoutAnIdSendsNothing() = runTest {
        val viewModel = viewModel()

        search(viewModel, "https://example.com/some/article")

        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    @Test
    fun aSharedLookupCounts() = runTest {
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)

        viewModel(SavedStateHandle(mapOf("query" to "10.1038/nature14539", "pageTitle" to "Deep learning")))

        assertEquals(listOf(lookup(SearchKind.Doi)), analytics.events)
    }

    @Test
    fun aLookupRerunByAKeyChangeSendsNothing() = runTest {
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        val viewModel = viewModel()
        search(viewModel, "10.1038/nature14539")

        viewModel.onSearchAction()
        runCurrent()
        userPreferencesRepository.setUserApiKey("new-key")
        runCurrent()

        assertEquals(2, lookupRepository.lookups.size)
        assertEquals(listOf(lookup(SearchKind.Doi)), analytics.events)
    }

    @Test
    fun aRestoredLookupSendsNothing() = runTest {
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)

        viewModel(SavedStateHandle(mapOf("search_text" to "10.1038/nature14539")))

        assertEquals(listOf(doi), lookupRepository.lookups)
        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    @Test
    fun savingAResultSaysSearch() = runTest {
        searchRepository.papers = listOf(SamplePapers.bert)
        val viewModel = viewModel()
        search(viewModel, "bert")

        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = false))
        runCurrent()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PaperSaved(SaveSource.Search)), analytics.events)
    }

    @Test
    fun savingAFoundLookupSaysLookup() = runTest {
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        val viewModel = viewModel()
        search(viewModel, "10.1038/nature14539")

        viewModel.onToggleSave(PaperItem(SamplePapers.attention, inLibrary = false))
        runCurrent()

        assertEquals(AnalyticsEvent.PaperSaved(SaveSource.Lookup), analytics.events.last())
    }

    @Test
    fun savingASharedLookupSaysShare() = runTest {
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        val viewModel = viewModel(SavedStateHandle(mapOf("query" to "10.1038/nature14539", "pageTitle" to "Deep learning")))

        viewModel.onToggleSave(PaperItem(SamplePapers.attention, inLibrary = false))
        runCurrent()

        assertEquals(AnalyticsEvent.PaperSaved(SaveSource.Share), analytics.events.last())
    }

    /** Browsers often share a link with no page title; the lookup still came from the share. */
    @Test
    fun savingASharedLookupWithoutATitleSaysShare() = runTest {
        lookupRepository.results[doi] = LookupResult.Found(SamplePapers.attention)
        val viewModel = viewModel(SavedStateHandle(mapOf("query" to "10.1038/nature14539")))

        viewModel.onToggleSave(PaperItem(SamplePapers.attention, inLibrary = false))
        runCurrent()

        assertEquals(AnalyticsEvent.PaperSaved(SaveSource.Share), analytics.events.last())
    }

    @Test
    fun aLookupTypedAfterAShareSaysLookup() = runTest {
        val arxiv = PaperIdentifier.Arxiv("1810.04805")
        lookupRepository.results[arxiv] = LookupResult.Found(SamplePapers.bert)
        val viewModel = viewModel(SavedStateHandle(mapOf("query" to "10.1038/nature14539", "pageTitle" to "Deep learning")))

        search(viewModel, "1810.04805")
        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = false))
        runCurrent()

        assertEquals(AnalyticsEvent.PaperSaved(SaveSource.Lookup), analytics.events.last())
    }

    @Test
    fun aFailedSaveSendsNothing() = runTest {
        libraryRepository.failOnSave = true
        val viewModel = viewModel()

        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = false))
        runCurrent()

        assertEquals(SearchMessage.SaveFailed, viewModel.message.value)
        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    @Test
    fun removingFromSearchCountsOnlyARealRemoval() = runTest {
        libraryRepository.save(SamplePapers.bert)
        libraryRepository.save(SamplePapers.vit)
        val viewModel = viewModel()

        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = true))
        runCurrent()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PaperRemoved), analytics.events)

        viewModel.onRemoveRequested(SamplePapers.attention.openAlexId)
        runCurrent()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PaperRemoved), analytics.events)

        viewModel.onRemoveRequested(SamplePapers.vit.openAlexId)
        runCurrent()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PaperRemoved, AnalyticsEvent.PaperRemoved), analytics.events)
    }

    @Test
    fun aFailedRemovalSendsNothing() = runTest {
        libraryRepository.save(SamplePapers.bert)
        libraryRepository.failOnRemove = true
        val viewModel = viewModel()

        viewModel.onToggleSave(PaperItem(SamplePapers.bert, inLibrary = true))
        viewModel.onRemoveRequested(SamplePapers.bert.openAlexId)
        runCurrent()

        assertFalse(analytics.events.contains(AnalyticsEvent.PaperRemoved))
    }
}
