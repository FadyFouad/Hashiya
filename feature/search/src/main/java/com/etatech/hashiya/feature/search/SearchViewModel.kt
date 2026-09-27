package com.etatech.hashiya.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.SearchRepository
import com.etatech.hashiya.core.data.repository.SearchResults
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
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
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    /** What the user sees: the field's text as typed plus the chip selections. */
    private val draft = MutableStateFlow(savedStateHandle.readSearchQuery())

    /** The text actually searched: set after the debounce, or immediately on IME search / suggestion / clear. */
    private val submittedText = MutableStateFlow(draft.value.text)

    init {
        viewModelScope.launch {
            draft.map { it.text }.distinctUntilChanged().drop(1).debounce(DEBOUNCE_MS).collect { submittedText.value = it }
        }
        viewModelScope.launch {
            draft.collect { savedStateHandle.writeSearchQuery(it) }
        }
    }

    private val activeQuery: StateFlow<SearchQuery?> = combine(draft, submittedText) { current, submitted ->
        submitted.trim().takeIf { it.isNotEmpty() }?.let { current.copy(text = it) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val results: StateFlow<SearchResults?> = activeQuery
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

    private val selectedPaper = MutableStateFlow<Paper?>(null)

    val selectedItem: StateFlow<PaperItem?> = combine(selectedPaper, savedIds) { paper, ids ->
        paper?.let { PaperItem(it, it.openAlexId in ids) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<SearchMessage?>(null)
    val message: StateFlow<SearchMessage?> = _message.asStateFlow()

    fun onTextChange(text: String) {
        draft.update { it.copy(text = text) }
        if (text.isBlank()) submittedText.value = ""
    }

    fun onSearchAction() {
        submittedText.value = draft.value.text
    }

    fun onSuggestion(text: String) {
        draft.update { it.copy(text = text) }
        submittedText.value = text
    }

    fun onSortChange(sort: SearchSort) = draft.update { it.copy(sort = sort) }

    fun onYearFilterChange(years: YearFilter) = draft.update { it.copy(years = years) }

    fun onOpenAccessToggle() = draft.update { it.copy(openAccessOnly = !it.openAccessOnly) }

    fun onClearFilters() = draft.update { it.copy(years = YearFilter.AnyTime, openAccessOnly = false) }

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
                _message.value = SearchMessage.SaveFailed
            }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    private fun SearchQuery.toUiState(isIdle: Boolean, totalCount: Long?) = SearchUiState(
        text = text,
        sort = sort,
        years = years,
        openAccessOnly = openAccessOnly,
        isIdle = isIdle,
        totalCount = totalCount
    )
}
