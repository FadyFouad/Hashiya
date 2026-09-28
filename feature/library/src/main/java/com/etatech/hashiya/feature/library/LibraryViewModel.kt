package com.etatech.hashiya.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.launch

internal const val SEARCH_DEBOUNCE_MS = 300L
private const val KEY_QUERY = "library_query"
private const val KEY_STATUS = "library_status"

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    /** The search text as typed. */
    private val query = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY).orEmpty())

    /** The selected status chip; null is All. */
    private val status = MutableStateFlow(
        savedStateHandle.get<String>(KEY_STATUS)?.let { name -> ReadingStatus.entries.firstOrNull { it.name == name } }
    )

    /** The text actually searched: follows [query] after a pause, or at once on Search or Clear. Restored text applies at once. */
    private val appliedQuery = MutableStateFlow(query.value)

    init {
        viewModelScope.launch {
            query.drop(1).debounce(SEARCH_DEBOUNCE_MS).collect { appliedQuery.value = it }
        }
        viewModelScope.launch {
            query.collect { savedStateHandle[KEY_QUERY] = it }
        }
        viewModelScope.launch {
            status.collect { savedStateHandle[KEY_STATUS] = it?.name }
        }
    }

    /** Decides Empty (nothing saved) versus NoMatches (nothing matches), whatever the search and chip. */
    private val libraryIsEmpty = libraryRepository.observeStatusCounts("").map { counts -> counts.values.sum() == 0 }.distinctUntilChanged()

    private val results = combine(appliedQuery, status, ::Pair).flatMapLatest { (applied, selected) ->
        combine(libraryRepository.observeLibrary(applied, selected), libraryRepository.observeStatusCounts(applied), ::Pair)
    }

    val uiState: StateFlow<LibraryUiState> = combine(libraryIsEmpty, results, query, status) { empty, (papers, counts), typed, selected ->
        val filter = LibraryFilter(query = typed, status = selected, counts = counts)
        when {
            empty -> LibraryUiState.Empty
            papers.isEmpty() -> LibraryUiState.NoMatches(filter)
            else -> LibraryUiState.Papers(papers, filter)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    private val selectedId = MutableStateFlow<String?>(null)

    /**
     * The paper in the preview sheet, with its current status. Looked up in the whole library, so a status change that
     * moves it out of the selected chip keeps the sheet open; clears itself if the paper is removed.
     */
    val selectedPaper: StateFlow<LibraryPaper?> = selectedId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            libraryRepository.observeLibrary(query = "", status = null).map { papers -> papers.firstOrNull { it.paper.openAlexId == id } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pendingUndo = MutableStateFlow<RemovedPaper?>(null)
    val pendingUndo: StateFlow<RemovedPaper?> = _pendingUndo.asStateFlow()

    private val _message = MutableStateFlow<LibraryMessage?>(null)
    val message: StateFlow<LibraryMessage?> = _message.asStateFlow()

    fun onQueryChange(text: String) {
        query.value = text
    }

    /** The keyboard's Search key: search now instead of after the pause. */
    fun onSearch() {
        appliedQuery.value = query.value
    }

    fun onClearQuery() {
        query.value = ""
        appliedQuery.value = ""
    }

    fun onStatusFilterChange(status: ReadingStatus?) {
        this.status.value = status
    }

    fun onClearSearchAndFilters() {
        onClearQuery()
        status.value = null
    }

    fun onStatusChange(paper: Paper, status: ReadingStatus) {
        viewModelScope.launch {
            try {
                libraryRepository.setStatus(paper.openAlexId, status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = LibraryMessage.StatusUpdateFailed
            }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    fun onPaperClick(paper: Paper) {
        selectedId.value = paper.openAlexId
    }

    fun onDismissPreview() {
        selectedId.value = null
    }

    fun onRemove(paper: Paper) {
        selectedId.value = null
        viewModelScope.launch {
            _pendingUndo.value = libraryRepository.remove(paper.openAlexId)
        }
    }

    fun onUndoRemove() {
        val removed = _pendingUndo.value ?: return
        _pendingUndo.value = null
        viewModelScope.launch { libraryRepository.restore(removed) }
    }

    fun onUndoDismissed() {
        _pendingUndo.value = null
    }
}
