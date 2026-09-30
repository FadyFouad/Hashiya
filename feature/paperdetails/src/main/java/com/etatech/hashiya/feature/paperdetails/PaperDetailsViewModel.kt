package com.etatech.hashiya.feature.paperdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.di.ApplicationScope
import com.etatech.hashiya.core.data.repository.CitationRepository
import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.data.repository.CollectionsRepository
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val NOTES_SAVE_DEBOUNCE_MS = 500L

/** The route's argument: PaperDetailsRoute.openAlexId. */
internal const val ARG_OPEN_ALEX_ID = "openAlexId"

@OptIn(FlowPreview::class)
@HiltViewModel
class PaperDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val collectionsRepository: CollectionsRepository,
    private val citationRepository: CitationRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    val openAlexId: String = checkNotNull(savedStateHandle[ARG_OPEN_ALEX_ID]) { "PaperDetailsRoute needs an openAlexId" }

    /** The notes as typed; null until the stored notes are read. Read once: later database emissions never replace typing. */
    private val notes = MutableStateFlow<PaperNotes?>(null)

    /** Every write goes through [save], one at a time, so writes land in order and a flush can't be overtaken. */
    private val saveMutex = Mutex()

    /** The notes last read or written. Guarded by [saveMutex]. */
    private var storedNotes: PaperNotes? = null

    private val saveState = MutableStateFlow(NotesSaveState.Idle)

    private val _message = MutableStateFlow<PaperDetailsMessage?>(null)
    val message: StateFlow<PaperDetailsMessage?> = _message.asStateFlow()

    private val _exit = MutableStateFlow<PaperDetailsExit?>(null)
    val exit: StateFlow<PaperDetailsExit?> = _exit.asStateFlow()

    private val _newCollectionDialog = MutableStateFlow<NewCollectionDialog?>(null)
    val newCollectionDialog: StateFlow<NewCollectionDialog?> = _newCollectionDialog.asStateFlow()

    private val _copied = MutableStateFlow<CopiedBibTeX?>(null)
    val copied: StateFlow<CopiedBibTeX?> = _copied.asStateFlow()

    /** Eager, so a paper that is gone closes the screen even before anything collects the UI state. */
    private val paper: StateFlow<LibraryPaper?> = libraryRepository.observePaper(openAlexId)
        .onEach { if (it == null) _exit.compareAndSet(null, PaperDetailsExit.Closed) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<PaperDetailsUiState> = combine(
        paper.filterNotNull(),
        notes.filterNotNull(),
        saveState,
        collectionsRepository.observeCollections(),
        collectionsRepository.observeCollectionIds(openAlexId)
    ) { current, typed, state, collections, memberOf ->
        PaperDetailsUiState.Loaded(current, typed, state, collections, memberOf)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaperDetailsUiState.Loading)

    init {
        viewModelScope.launch {
            val stored = libraryRepository.observeNotes(openAlexId).first()
            saveMutex.withLock { storedNotes = stored }
            notes.value = stored
        }
        viewModelScope.launch {
            // collect is sequential: each write finishes before the next starts, and a burst of typing is one write.
            notes.filterNotNull().debounce(NOTES_SAVE_DEBOUNCE_MS).collect { save(it) }
        }
    }

    fun onNoteChange(section: NoteSection, text: String) {
        notes.update { current -> current?.with(section, text) }
    }

    fun onStatusChange(status: ReadingStatus) {
        viewModelScope.launch {
            try {
                libraryRepository.setStatus(openAlexId, status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = PaperDetailsMessage.StatusUpdateFailed
            }
        }
    }

    /** Applied at once. On failure nothing changes, so the checklist shows the stored state again and a failed tick un-ticks. */
    fun onToggleCollection(collectionId: Long, member: Boolean) {
        collectionChange { collectionsRepository.setMembership(collectionId, openAlexId, member) }
    }

    fun onNewCollection() {
        _newCollectionDialog.value = NewCollectionDialog()
    }

    fun onNewCollectionNameEdited() {
        _newCollectionDialog.update { it?.copy(nameTaken = false) }
    }

    /** Creates the collection and puts this paper in it. */
    fun onNewCollectionConfirm(name: String) {
        collectionChange(closeDialogOnFailure = true) {
            when (val result = collectionsRepository.create(name)) {
                is CollectionResult.Done -> {
                    collectionsRepository.setMembership(result.id, openAlexId, member = true)
                    _newCollectionDialog.value = null
                }

                CollectionResult.NameTaken -> _newCollectionDialog.value = NewCollectionDialog(nameTaken = true)

                // The dialog's button is disabled for invalid names, so this only happens on a race; keep the dialog open.
                CollectionResult.InvalidName -> Unit

                // Only rename returns NotFound; create never does, so just close the dialog.
                CollectionResult.NotFound -> _newCollectionDialog.value = null
            }
        }
    }

    fun onNewCollectionDismiss() {
        _newCollectionDialog.value = null
    }

    fun onCopyBibTeX() {
        viewModelScope.launch {
            try {
                citationRepository.entry(openAlexId)?.let { _copied.value = CopiedBibTeX(it.bibtex, it.complete) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = PaperDetailsMessage.CopyFailed
            }
        }
    }

    /** The screen put [copied] on the clipboard; [confirmation] is what to show, from copyConfirmation. */
    fun onCopyHandled(confirmation: PaperDetailsMessage?) {
        _copied.value = null
        if (confirmation != null) _message.value = confirmation
    }

    fun onRetrySave() {
        viewModelScope.launch { notes.value?.let { save(it) } }
    }

    fun onMessageShown() {
        _message.value = null
    }

    /** Writes unsaved notes now, on the application scope, so leaving the screen can't cancel the write. */
    fun flushNotes() {
        val current = notes.value ?: return
        applicationScope.launch { save(current) }
    }

    /**
     * Saves unsaved notes first, so Undo on the screen below restores what was just typed, then asks to leave.
     * If that save fails, the screen stays, showing Couldn't save with Retry, so Undo can never bring back older notes.
     */
    fun onRemove() {
        viewModelScope.launch {
            val saved = notes.value?.let { save(it) } ?: true
            if (saved) _exit.value = PaperDetailsExit.Removed
        }
    }

    override fun onCleared() {
        flushNotes()
    }

    private fun collectionChange(closeDialogOnFailure: Boolean = false, change: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                change()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (closeDialogOnFailure) _newCollectionDialog.value = null
                _message.value = PaperDetailsMessage.CollectionsUpdateFailed
            }
        }
    }

    /** Returns false only when the write failed. */
    private suspend fun save(value: PaperNotes): Boolean = saveMutex.withLock {
        if (value == storedNotes) return@withLock true
        saveState.value = NotesSaveState.Saving
        try {
            libraryRepository.saveNotes(openAlexId, value)
            storedNotes = value
            saveState.value = NotesSaveState.Saved
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saveState.value = NotesSaveState.Failed
            _message.value = PaperDetailsMessage.NotesSaveFailed
            false
        }
    }
}
