package com.etatech.hashiya.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val DEBOUNCE_MS = 300L

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
    private val libraryRepository: LibraryRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val paperLookupRepository: PaperLookupRepository
) : ViewModel() {
    /** Arguments from Share or "Add paper": applied once, never over text restored after process death. */
    private val routeArgs = savedStateHandle.consumeRouteArgs()

    /** What the user sees: the field's text as typed plus the chip selections. */
    private val draft = MutableStateFlow(
        savedStateHandle.readSearchQuery().let { restored -> routeArgs?.query?.let { restored.copy(text = it) } ?: restored }
    )

    /** The text actually searched: set after the debounce, or immediately on IME search / suggestion / clear / route query. */
    private val submittedText = MutableStateFlow(draft.value.text)

    /** A shared page's title, offered as a title search if its ID isn't found; forgotten once the text is edited. */
    private val pageTitle = MutableStateFlow(routeArgs?.pageTitle ?: savedStateHandle.savedPageTitle)

    private val _note = MutableStateFlow(routeArgs?.note)
    val note: StateFlow<SearchNote?> = _note.asStateFlow()

    private val _focusSearch = MutableStateFlow(routeArgs?.focusSearch == true)
    val focusSearch: StateFlow<Boolean> = _focusSearch.asStateFlow()

    init {
        viewModelScope.launch {
            draft.map { it.text }.distinctUntilChanged().drop(1).debounce(DEBOUNCE_MS).collect { submittedText.value = it }
        }
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

    /** A new API key re-runs the active search, so fixing a rejected key in Settings takes effect right away. */
    private val results: StateFlow<SearchResults?> = combine(activeQuery, apiKey) { query, _ -> query }
        .map { query -> query?.let(searchRepository::search) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Cached per query. Library state is kept out of the paging stream so saving never re-maps cached pages. */
    val papers: Flow<PagingData<Paper>> = results
        .flatMapLatest { it?.papers ?: flowOf(PagingData.empty()) }
        .cachedIn(viewModelScope)

    /** OpenAlex IDs in the library; the UI combines this with each result to show "In library". */
    val savedIds: StateFlow<Set<String>> = libraryRepository.observeSavedIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val uiState: StateFlow<SearchUiState> = combine(
        draft,
        activeQuery,
        results.flatMapLatest { it?.totalCount ?: flowOf(null) }
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

    /** The latest lookup (a null result means "still looking"); a newer identifier, a retry or a key change cancels it. */
    private val lookup: StateFlow<Pair<PaperIdentifier, LookupResult?>?> =
        combine(identifier, lookupRetries, apiKey) { id, _, _ -> id }
            .flatMapLatest { id ->
                if (id == null) {
                    flowOf<Pair<PaperIdentifier, LookupResult?>?>(null)
                } else {
                    flow<Pair<PaperIdentifier, LookupResult?>?> {
                        emit(id to null)
                        emit(id to paperLookupRepository.lookup(id))
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

    fun onTextChange(text: String) {
        if (text != draft.value.text) forgetShareContext()
        draft.update { it.copy(text = text) }
        if (text.isBlank()) submittedText.value = ""
    }

    fun onSearchAction() {
        submittedText.value = draft.value.text
    }

    fun onSuggestion(text: String) {
        forgetShareContext()
        draft.update { it.copy(text = text) }
        submittedText.value = text
    }

    fun onSortChange(sort: SearchSort) = draft.update { it.copy(sort = sort) }

    fun onYearFilterChange(years: YearFilter) = draft.update { it.copy(years = years) }

    fun onOpenAccessToggle() = draft.update { it.copy(openAccessOnly = !it.openAccessOnly) }

    fun onClearFilters() = draft.update { it.copy(years = YearFilter.AnyTime, openAccessOnly = false) }

    fun onRetryLookup() = lookupRetries.update { it + 1 }

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
        viewModelScope.launch {
            try {
                if (item.inLibrary) {
                    libraryRepository.remove(item.paper.openAlexId)
                } else {
                    libraryRepository.save(item.paper)
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
        pageTitle.value = null
        _note.value = null
    }

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
