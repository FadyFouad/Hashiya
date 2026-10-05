package com.etatech.hashiya.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.ResultsBucket
import com.etatech.hashiya.core.analytics.SaveSource
import com.etatech.hashiya.core.analytics.SearchKind
import com.etatech.hashiya.core.analytics.SearchRoute
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.LookupResult
import com.etatech.hashiya.core.data.repository.PaperLookupRepository
import com.etatech.hashiya.core.data.repository.SearchRepository
import com.etatech.hashiya.core.data.repository.SearchResults
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.core.model.looksLikeLink
import com.etatech.hashiya.core.model.parsePaperIdentifier
import com.etatech.hashiya.core.model.withoutArabicMarks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
    private val libraryRepository: LibraryRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val paperLookupRepository: PaperLookupRepository,
    private val analytics: Analytics
) : ViewModel() {
    /** Arguments from Share or "Add paper": applied once, never over text restored after process death. */
    private val routeArgs = savedStateHandle.consumeRouteArgs()

    /**
     * Set by the user's own actions, and by arriving from Share or "Add paper", so only searches and lookups the user started are
     * counted: a restore after process death or an API key change re-runs them without counting (their further pages still count).
     */
    private var countNextSearch = routeArgs != null
    private var countNextLookup = routeArgs != null

    /** The text came from a share (with or without a page title); forgotten with the page title once the text is edited. */
    private var sharedText = routeArgs?.query != null

    /** What the user sees: the field's text as typed plus the chip selections. */
    private val draft = MutableStateFlow(
        savedStateHandle.readSearchQuery().let { restored -> routeArgs?.query?.let { restored.copy(text = it) } ?: restored }
    )

    /**
     * The text actually searched. Typing never searches: it changes on the keyboard's Search action, a suggestion,
     * a route query or clearing the field, so every OpenAlex request is one the user asked for.
     */
    private val submittedText = MutableStateFlow(draft.value.text)

    /** A shared page's title, offered as a title search if its ID isn't found; forgotten once the text is edited. */
    private val pageTitle = MutableStateFlow(routeArgs?.pageTitle ?: savedStateHandle.savedPageTitle)

    private val _note = MutableStateFlow(routeArgs?.note)
    val note: StateFlow<SearchNote?> = _note.asStateFlow()

    private val _focusSearch = MutableStateFlow(routeArgs?.focusSearch == true)
    val focusSearch: StateFlow<Boolean> = _focusSearch.asStateFlow()

    init {
        viewModelScope.launch {
            draft.collect { savedStateHandle.writeSearchQuery(it) }
        }
        viewModelScope.launch {
            pageTitle.collect { savedStateHandle.savedPageTitle = it }
        }
    }

    private val apiKey = userPreferencesRepository.userApiKey.distinctUntilChanged()

    /** The keyword query; null when the submitted text is blank, is a DOI / arXiv ID (ID mode), or is an unrecognized link. */
    private val activeQuery: StateFlow<SearchQuery?> = combine(draft, submittedText) { current, submitted ->
        submitted.trim()
            .takeIf {
                it.isNotEmpty() &&
                    withoutArabicMarks(it).trim().isNotEmpty() &&
                    parsePaperIdentifier(it) == null &&
                    !looksLikeLink(it)
            }
            ?.let { current.copy(text = it) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var lastSearchedQuery: SearchQuery? = null

    /** A new API key re-runs the active search, so fixing a rejected key in Settings takes effect right away. */
    private val search: StateFlow<ActiveSearch?> = combine(activeQuery, apiKey) { query, key -> query to key }
        .map { (query, key) ->
            // A key change re-runs the same query, which the user didn't ask for again. The flag waits for a query to search, so
            // a route query still counts after activeQuery's initial null.
            val userStarted = countNextSearch && query != lastSearchedQuery
            if (query != null) countNextSearch = false
            lastSearchedQuery = query
            query?.let { ActiveSearch(it, searchRepository.search(it), key.asRoute(), userStarted) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            search.collectLatest { current -> current?.let { count(it) } }
        }
    }

    /** Cached per query. Library state is kept out of the paging stream so saving never re-maps cached pages. */
    val papers: Flow<PagingData<Paper>> = search
        .flatMapLatest { it?.results?.papers ?: flowOf(PagingData.empty()) }
        .cachedIn(viewModelScope)

    /** OpenAlex IDs in the library; the UI combines this with each result to show "In library". */
    val savedIds: StateFlow<Set<String>> = libraryRepository.observeSavedIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val uiState: StateFlow<SearchUiState> = combine(
        draft,
        activeQuery,
        search.flatMapLatest { it?.results?.totalCount ?: flowOf(null) }
    ) { current, active, count ->
        current.toUiState(isIdle = active == null, totalCount = count)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        draft.value.toUiState(isIdle = draft.value.text.isBlank(), totalCount = null)
    )

    /** The DOI or arXiv ID in the submitted text, or null for a keyword search. */
    private val identifier: StateFlow<PaperIdentifier?> = submittedText
        .map { parsePaperIdentifier(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, parsePaperIdentifier(submittedText.value))

    private val lookupRetries = MutableStateFlow(0)

    private var lastLookupRun: Pair<PaperIdentifier?, Int>? = null

    /** The latest lookup (a null result means "still looking"); a newer identifier, a retry or a key change cancels it. */
    private val lookup: StateFlow<Pair<PaperIdentifier, LookupResult?>?> =
        combine(identifier, lookupRetries, apiKey) { id, retries, key ->
            // As for searches, a key change re-runs the same lookup without counting it.
            val userStarted = countNextLookup && (id to retries) != lastLookupRun
            if (id != null) countNextLookup = false
            lastLookupRun = id to retries
            LookupRun(id, counted = if (userStarted && id != null) lookupCount(id, key) else null)
        }
            .flatMapLatest { (id, counted) ->
                if (id == null) {
                    flowOf<Pair<PaperIdentifier, LookupResult?>?>(null)
                } else {
                    flow<Pair<PaperIdentifier, LookupResult?>?> {
                        emit(id to null)
                        val result = paperLookupRepository.lookup(id)
                        counted?.let { count(it, result) }
                        emit(id to result)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The submitted text is a link with no DOI or arXiv ID in it; [lookup] stays null for it, so this fills in the state. */
    private val isLinkWithoutId: StateFlow<Boolean> = submittedText
        .map { looksLikeLink(it) && parsePaperIdentifier(it) == null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lookupState: StateFlow<LookupUiState?> = combine(lookup, pageTitle, isLinkWithoutId) { current, title, isLink ->
        current?.let { (id, result) -> result.toUiState(id, title) } ?: LookupUiState.NoIdInLink.takeIf { isLink }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val selectedPaper = MutableStateFlow<Paper?>(null)

    val selectedItem: StateFlow<PaperItem?> = combine(selectedPaper, savedIds) { paper, ids ->
        paper?.let { PaperItem(it, it.openAlexId in ids) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<SearchMessage?>(null)
    val message: StateFlow<SearchMessage?> = _message.asStateFlow()

    /** Details' "Remove from library", handed back through the Search back stack entry: removed as the sheet's Remove does. */
    fun onRemoveRequested(openAlexId: String) {
        viewModelScope.launch {
            try {
                if (libraryRepository.remove(openAlexId) != null) analytics.log(AnalyticsEvent.PaperRemoved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = SearchMessage.RemoveFailed
            }
        }
    }

    fun onTextChange(text: String) {
        if (text != draft.value.text) forgetShareContext()
        draft.update { it.copy(text = text) }
        if (text.isBlank()) submittedText.value = ""
    }

    fun onSearchAction() {
        countNextSubmission()
        submittedText.value = draft.value.text
    }

    fun onSuggestion(text: String) {
        forgetShareContext()
        countNextSubmission()
        draft.update { it.copy(text = text) }
        submittedText.value = text
    }

    fun onSortChange(sort: SearchSort) = changeChips { it.copy(sort = sort) }

    fun onYearFilterChange(years: YearFilter) = changeChips { it.copy(years = years) }

    fun onOpenAccessToggle() = changeChips { it.copy(openAccessOnly = !it.openAccessOnly) }

    fun onClearFilters() = changeChips { it.copy(years = YearFilter.AnyTime, openAccessOnly = false) }

    fun onRetryLookup() {
        countNextLookup = true
        lookupRetries.update { it + 1 }
    }

    /** Ctrl+F: focus the search field, keeping the current search and results. */
    fun onFocusRequested() {
        _focusSearch.value = true
    }

    fun onFocusHandled() {
        _focusSearch.value = false
    }

    fun onPaperClick(paper: Paper) {
        selectedPaper.value = paper
    }

    fun onDismissPreview() {
        selectedPaper.value = null
    }

    fun onToggleSave(item: PaperItem) {
        val source = saveSource(item.paper)
        viewModelScope.launch {
            try {
                if (item.inLibrary) {
                    if (libraryRepository.remove(item.paper.openAlexId) != null) analytics.log(AnalyticsEvent.PaperRemoved)
                } else {
                    libraryRepository.save(item.paper)
                    analytics.log(AnalyticsEvent.PaperSaved(source))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = if (item.inLibrary) SearchMessage.RemoveFailed else SearchMessage.SaveFailed
            }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    private fun forgetShareContext() {
        sharedText = false
        pageTitle.value = null
        _note.value = null
    }

    private fun countNextSubmission() {
        countNextSearch = true
        countNextLookup = true
    }

    private fun changeChips(change: (SearchQuery) -> SearchQuery) {
        countNextSearch = true
        draft.update(change)
    }

    /** The found lookup's paper came from a share while its context lasts, from a typed lookup otherwise; anything else is a result. */
    private fun saveSource(paper: Paper): SaveSource {
        val found = (lookup.value?.second as? LookupResult.Found)?.paper
        return when {
            found?.openAlexId != paper.openAlexId -> SaveSource.Search
            sharedText || pageTitle.value != null -> SaveSource.Share
            else -> SaveSource.Lookup
        }
    }

    /**
     * Sends `search` when the first page of a search the user started arrives, then `search_more` for each page beyond the highest
     * already counted. A restored or re-run search sends no `search`, but each further page is still one the user scrolled to.
     */
    private suspend fun count(search: ActiveSearch) {
        if (search.userStarted) {
            val first = search.results.firstPage.filterNotNull().first()
            analytics.log(
                AnalyticsEvent.Search(
                    SearchKind.Keyword,
                    hasFilters = search.query.hasActiveFilters,
                    route = search.route,
                    results = ResultsBucket.of(first.total),
                    category = first.category
                )
            )
        }
        // A refresh starts a new PagingSource whose count restarts at 1; only pages past the highest counted are new.
        var counted = 1
        search.results.pagesLoaded.collect { pages ->
            while (counted < pages) analytics.log(AnalyticsEvent.SearchMore(++counted))
        }
    }

    private fun lookupCount(id: PaperIdentifier, key: String?) = LookupCount(
        kind = when {
            looksLikeLink(submittedText.value) -> SearchKind.Link
            id is PaperIdentifier.Doi -> SearchKind.Doi
            else -> SearchKind.Arxiv
        },
        route = key.asRoute()
    )

    /** A failed lookup isn't counted, like a search whose first page never arrives. */
    private fun count(lookup: LookupCount, result: LookupResult) {
        val results = when (result) {
            is LookupResult.Found -> ResultsBucket.UpTo25
            is LookupResult.NotFound -> ResultsBucket.Zero
            is LookupResult.Failed -> return
        }
        analytics.log(AnalyticsEvent.Search(lookup.kind, hasFilters = false, route = lookup.route, results = results, category = null))
    }

    private fun String?.asRoute() = if (this != null) SearchRoute.User else SearchRoute.Shared

    private fun SearchQuery.toUiState(isIdle: Boolean, totalCount: Long?) = SearchUiState(
        text = text,
        sort = sort,
        years = years,
        openAccessOnly = openAccessOnly,
        isIdle = isIdle,
        totalCount = totalCount
    )

    private fun LookupResult?.toUiState(identifier: PaperIdentifier, pageTitle: String?): LookupUiState = when (this) {
        null -> LookupUiState.Looking(identifier)
        is LookupResult.Found -> LookupUiState.Found(paper)
        is LookupResult.NotFound -> LookupUiState.NotFound(identifier, searchTitle = arxivTitle ?: pageTitle)
        is LookupResult.Failed -> LookupUiState.Failed(error)
    }
}

/** A keyword search and whether the user started it (rather than a restore or an API key change re-running it). */
private class ActiveSearch(val query: SearchQuery, val results: SearchResults, val route: SearchRoute, val userStarted: Boolean)

/** A lookup to run; [counted] is null when the user didn't start it. */
private data class LookupRun(val identifier: PaperIdentifier?, val counted: LookupCount?)

private class LookupCount(val kind: SearchKind, val route: SearchRoute)
