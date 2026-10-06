package com.etatech.hashiya.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.ExportFormat
import com.etatech.hashiya.core.data.repository.CitationRepository
import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.data.repository.CollectionsRepository
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.PdfRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import com.etatech.hashiya.core.model.CitationStyle
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.review.ReviewPrompt
import com.etatech.hashiya.feature.library.export.BIB_MIME_TYPE
import com.etatech.hashiya.feature.library.export.RTF_MIME_TYPE
import com.etatech.hashiya.feature.library.export.exportFileName
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val SEARCH_DEBOUNCE_MS = 300L
private const val KEY_QUERY = "library_query"
private const val KEY_STATUS = "library_status"
private const val KEY_COLLECTION = "library_collection"

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val collectionsRepository: CollectionsRepository,
    private val citationRepository: CitationRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val pdfRepository: PdfRepository,
    private val analytics: Analytics,
    private val reviewPrompt: ReviewPrompt
) : ViewModel() {
    /** The search text as typed. */
    private val query = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY).orEmpty())

    /** The selected status chip; null is All. */
    private val status = MutableStateFlow(
        savedStateHandle.get<String>(KEY_STATUS)?.let { name -> ReadingStatus.entries.firstOrNull { it.name == name } }
    )

    /** The selected collection's id; null is All papers. Falls back to null when the collection is deleted. */
    private val selectedId = MutableStateFlow(savedStateHandle.get<Long>(KEY_COLLECTION))

    /** The text actually searched: follows [query] after a pause, or at once on Search or Clear. Restored text applies at once. */
    private val appliedQuery = MutableStateFlow(query.value)

    private val collections: StateFlow<List<PaperCollection>> =
        collectionsRepository.observeCollections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val exporting = MutableStateFlow(false)

    /** Set when an incomplete export was shared; shown when the Library resumes after the share sheet. */
    private var incompleteExportPending = false

    /** An export reached the share sheet; the rating prompt waits until the person is back from it. */
    private var reviewPending = false

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
        viewModelScope.launch {
            selectedId.collect { savedStateHandle[KEY_COLLECTION] = it }
        }
        viewModelScope.launch {
            // A deleted collection (from its menu or from Details, or before a selection lands) drops the Library back to All papers.
            // This reads the repository, not [collections], whose initial empty list would drop a restored selection.
            combine(selectedId, collectionsRepository.observeCollections()) { id, list -> id?.takeIf { list.none { it.id == id } } }
                .filterNotNull()
                .collect { missing -> selectedId.compareAndSet(missing, null) }
        }
    }

    /** Decides Empty (nothing saved) versus NoMatches (nothing matches), whatever the search, chip and collection. */
    private val libraryIsEmpty = libraryRepository.observeStatusCounts("").map { counts -> counts.values.sum() == 0 }.distinctUntilChanged()

    private val results = combine(appliedQuery, status, selectedId, ::Triple).flatMapLatest { (applied, selected, collectionId) ->
        combine(
            libraryRepository.observeLibrary(applied, selected, collectionId),
            libraryRepository.observeStatusCounts(applied, collectionId),
            libraryRepository.observeStatusCounts("", collectionId)
        ) { papers, counts, unfiltered ->
            Results(applied, selected, collectionId, papers, counts, viewSize = unfiltered.values.sum())
        }
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(libraryIsEmpty, results, query, status, collections) { empty, results, typed, selected, collectionList ->
            val filter = LibraryFilter(query = typed, status = selected, counts = results.counts)
            val counted = results.status?.let { results.counts[it] ?: 0 } ?: results.counts.values.sum()
            val collection = results.collectionId?.let { id -> collectionList.firstOrNull { it.id == id } }
            when {
                // With no search, chip or collection, an empty list is an empty library, even before the emptiness query answers.
                empty ||
                    (results.papers.isEmpty() && results.status == null && results.applied.isBlank() && results.collectionId == null) ->
                    LibraryUiState.Empty

                // The collection list hasn't caught up with the selection (or it was just deleted): keep the last state.
                results.collectionId != null && collection == null -> null

                // Papers first: the view size is a separate query and can briefly lag behind the list (an Undo, say).
                results.papers.isNotEmpty() -> LibraryUiState.Papers(results.papers, filter)

                collection != null && results.viewSize == 0 -> LibraryUiState.CollectionEmpty(collection)

                counted == 0 -> LibraryUiState.NoMatches(filter)

                // The list and the counts are separate queries: an empty list the counts disagree with is stale, so keep the last state.
                else -> null
            }
        }.filterNotNull().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    val header: StateFlow<LibraryHeader> = combine(
        collections,
        selectedId,
        selectedId.flatMapLatest { id -> libraryRepository.observeStatusCounts("", id) }.map { it.values.sum() },
        libraryRepository.observeStatusCounts("").map { it.values.sum() },
        exporting
    ) { list, id, size, total, running ->
        LibraryHeader(
            collections = list,
            selected = list.firstOrNull { it.id == id },
            viewSize = size,
            libraryCount = total,
            exporting = running
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryHeader())

    private val _pendingUndo = MutableStateFlow<RemovedPaper?>(null)
    val pendingUndo: StateFlow<RemovedPaper?> = _pendingUndo.asStateFlow()

    private val _pendingCollectionUndo = MutableStateFlow<CollectionRemoval?>(null)
    val pendingCollectionUndo: StateFlow<CollectionRemoval?> = _pendingCollectionUndo.asStateFlow()

    private val _message = MutableStateFlow<LibraryMessage?>(null)
    val message: StateFlow<LibraryMessage?> = _message.asStateFlow()

    private val _dialog = MutableStateFlow<CollectionDialog?>(null)
    val dialog: StateFlow<CollectionDialog?> = _dialog.asStateFlow()

    /** The style the export menu lists first. */
    val citationStyle: StateFlow<CitationStyle> = preferencesRepository.citationStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CitationStyle.Apa)

    private val _exportReady = MutableStateFlow<ReferenceExport?>(null)

    /** A file for the screen to write and share; the screen then calls [onExportShared] or [onExportFailed]. */
    val exportReady: StateFlow<ReferenceExport?> = _exportReady.asStateFlow()

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

    fun onSelectCollection(id: Long?) {
        selectedId.value = id
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

    /** A swipe: out of the selected collection when one is selected, otherwise out of the library. Both with Undo. */
    fun onRemove(paper: Paper) {
        val selected = selectedId.value
        if (selected == null) {
            remove(paper.openAlexId)
            return
        }
        // collections is eager, so this is current even when nothing collects the header. A collection deleted a moment ago
        // (the fallback to All papers hasn't run yet) ignores the swipe: it must never remove the paper from the library.
        val collection = collections.value.firstOrNull { it.id == selected } ?: return
        collectionChange {
            collectionsRepository.setMembership(collection.id, paper.openAlexId, member = false)
            _pendingCollectionUndo.value = CollectionRemoval(collection, paper.openAlexId)
        }
    }

    /**
     * Details' "Remove from library", handed back through the Library's back stack entry: removed with Undo, like a swipe in All papers.
     */
    fun onRemoveRequested(openAlexId: String) = remove(openAlexId)

    private fun remove(openAlexId: String) {
        viewModelScope.launch {
            val removed = libraryRepository.remove(openAlexId)
            // Read after the removal, so a banner that went away meanwhile (and discarded its own PDF) isn't discarded twice.
            val previous = _pendingUndo.value
            _pendingUndo.value = removed
            // The screen cancels the previous snackbar without a callback, so its removal is final now.
            if (previous != null) finishRemoval(previous)
        }
    }

    fun onUndoRemove() {
        val removed = _pendingUndo.value ?: return
        _pendingUndo.value = null
        viewModelScope.launch { libraryRepository.restore(removed) }
    }

    /** The Undo snackbar timed out or was swiped away: the removal is final, so its PDF goes too. */
    fun onUndoDismissed() {
        val removed = _pendingUndo.value ?: return
        _pendingUndo.value = null
        finishRemoval(removed)
    }

    /**
     * A removal can no longer be undone: it is counted and its PDF deleted. Leaving the Library while Undo shows calls neither
     * callback; the startup sweep deletes that file instead, and that removal isn't counted.
     */
    private fun finishRemoval(removed: RemovedPaper) {
        analytics.log(AnalyticsEvent.PaperRemoved)
        viewModelScope.launch { pdfRepository.discardRemoved(removed) }
    }

    fun onUndoCollectionRemove() {
        val removal = _pendingCollectionUndo.value ?: return
        _pendingCollectionUndo.value = null
        // The collection was deleted meanwhile: there is nothing to put the paper back into.
        if (collections.value.none { it.id == removal.collection.id }) return
        viewModelScope.launch {
            try {
                collectionsRepository.setMembership(removal.collection.id, removal.openAlexId, member = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Deleted before the list caught up: dropped silently. Any other failure gets the usual message.
                val stillThere = collectionsRepository.observeCollections().first().any { it.id == removal.collection.id }
                if (stillThere) _message.value = LibraryMessage.CollectionsUpdateFailed
            }
        }
    }

    fun onCollectionUndoDismissed() {
        _pendingCollectionUndo.value = null
    }

    fun onNewCollection() {
        _dialog.value = CollectionDialog.New()
    }

    fun onRenameCollection(collection: PaperCollection) {
        _dialog.value = CollectionDialog.Rename(collection)
    }

    fun onDeleteCollection(collection: PaperCollection) {
        _dialog.value = CollectionDialog.ConfirmDelete(collection)
    }

    /** Typing clears the "already exists" error. */
    fun onDialogNameEdited() {
        _dialog.update { it?.withNameTaken(false) }
    }

    fun onDialogConfirm(name: String) {
        val current = _dialog.value
        collectionChange(closeDialogOnFailure = true) {
            val result = when (current) {
                is CollectionDialog.New -> collectionsRepository.create(name)
                is CollectionDialog.Rename -> collectionsRepository.rename(current.collection.id, name)
                else -> return@collectionChange
            }
            when (result) {
                is CollectionResult.Done -> {
                    _dialog.value = null
                    if (current is CollectionDialog.New) analytics.log(AnalyticsEvent.CollectionCreated)
                }

                // Only if the dialog is still the one confirmed, so a dismissal meanwhile isn't undone.
                CollectionResult.NameTaken -> _dialog.compareAndSet(current, current.withNameTaken(true))

                // The dialog's button is disabled for invalid names, so this only happens on a race; keep the dialog open.
                CollectionResult.InvalidName -> Unit

                // Renaming a collection deleted meanwhile (from Details).
                CollectionResult.NotFound -> {
                    _dialog.value = null
                    _message.value = LibraryMessage.CollectionsUpdateFailed
                }
            }
        }
    }

    fun onConfirmDelete() {
        val current = _dialog.value as? CollectionDialog.ConfirmDelete ?: return
        _dialog.value = null
        collectionChange { collectionsRepository.delete(current.collection.id) }
    }

    fun onDialogDismiss() {
        _dialog.value = null
    }

    /**
     * Builds the reference list for the whole current collection (or library) in [style], ignoring the search and chip, and remembers
     * the style. A second tap while running does nothing.
     */
    fun onExport(style: CitationStyle) {
        if (exporting.value) return
        exporting.value = true
        val collectionId = selectedId.value
        // Null for All papers; a collection whose name isn't known yet gets exportFileName's fallback.
        val name = collectionId?.let { id -> collections.value.firstOrNull { it.id == id }?.name.orEmpty() }
        viewModelScope.launch {
            // Remembering the style is a convenience: failing to store it never stops the export.
            try {
                preferencesRepository.setCitationStyle(style)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignored: the menu just keeps its old order.
            }
            try {
                val result = citationRepository.export(collectionId, style)
                _exportReady.value = ReferenceExport(
                    fileName = exportFileName(name, style),
                    content = result.rtf ?: result.text,
                    mimeType = if (style == CitationStyle.Bibtex) BIB_MIME_TYPE else RTF_MIME_TYPE,
                    complete = result.complete,
                    style = style
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exporting.value = false
                _message.value = LibraryMessage.ExportFailed
            }
        }
    }

    fun onExportShared() {
        val shared = _exportReady.value ?: return
        _exportReady.value = null
        // Still exporting until the file is shared, so a tap while it is written does nothing.
        exporting.value = false
        if (!shared.complete) incompleteExportPending = true
        analytics.log(AnalyticsEvent.Export(shared.style.exportFormat(), withPdfs = false))
        reviewPrompt.recordExport()
        reviewPending = true
    }

    fun onExportFailed() {
        _exportReady.value = null
        exporting.value = false
        _message.value = LibraryMessage.ExportFailed
    }

    /** The user is back from the share sheet: now is when "may be incomplete" can be read. */
    fun onScreenResumed() {
        if (reviewPending) {
            reviewPending = false
            reviewPrompt.askIfDue()
        }
        if (!incompleteExportPending) return
        incompleteExportPending = false
        _message.value = LibraryMessage.ExportIncomplete
    }

    private fun collectionChange(closeDialogOnFailure: Boolean = false, change: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                change()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (closeDialogOnFailure) _dialog.value = null
                _message.value = LibraryMessage.CollectionsUpdateFailed
            }
        }
    }
}

private fun CitationStyle.exportFormat(): ExportFormat = when (this) {
    CitationStyle.Bibtex -> ExportFormat.Bibtex
    CitationStyle.Apa -> ExportFormat.Apa
    CitationStyle.Ieee -> ExportFormat.Ieee
}

private fun CollectionDialog.withNameTaken(taken: Boolean): CollectionDialog = when (this) {
    is CollectionDialog.New -> copy(nameTaken = taken)
    is CollectionDialog.Rename -> copy(nameTaken = taken)
    is CollectionDialog.ConfirmDelete -> this
}

/** The papers and counts for one applied search, chip and collection, kept together so the state is decided from one emission. */
private data class Results(
    val applied: String,
    val status: ReadingStatus?,
    val collectionId: Long?,
    val papers: List<LibraryPaper>,
    val counts: Map<ReadingStatus, Int>,
    /** Papers in the collection (or library) ignoring the search and chip. */
    val viewSize: Int
)
