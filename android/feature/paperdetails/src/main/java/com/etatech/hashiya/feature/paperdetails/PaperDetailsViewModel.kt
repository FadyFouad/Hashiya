package com.etatech.hashiya.feature.paperdetails

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.di.ApplicationScope
import com.etatech.hashiya.core.data.notes.NotesEditor
import com.etatech.hashiya.core.data.repository.AttachResult
import com.etatech.hashiya.core.data.repository.CitationRepository
import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.data.repository.CollectionsRepository
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.PdfRepository
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val NOTES_SAVE_DEBOUNCE_MS = NotesEditor.SAVE_DEBOUNCE_MS

/** The route's argument: PaperDetailsRoute.openAlexId. */
internal const val ARG_OPEN_ALEX_ID = "openAlexId"

@HiltViewModel
class PaperDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val collectionsRepository: CollectionsRepository,
    private val citationRepository: CitationRepository,
    private val pdfRepository: PdfRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    val openAlexId: String = checkNotNull(savedStateHandle[ARG_OPEN_ALEX_ID]) { "PaperDetailsRoute needs an openAlexId" }

    private val _message = MutableStateFlow<PaperDetailsMessage?>(null)
    val message: StateFlow<PaperDetailsMessage?> = _message.asStateFlow()

    private val notesEditor = NotesEditor(openAlexId, libraryRepository, viewModelScope, applicationScope) {
        _message.value = PaperDetailsMessage.NotesSaveFailed
    }

    /** Grows when notes written in the reader replace the ones on screen; the fields re-seed. */
    val notesVersion: StateFlow<Int> = notesEditor.version

    private val _openReader = MutableStateFlow(false)

    /** True once typed notes are saved and the reader can open; the screen navigates, then calls onReaderOpened. */
    val openReader: StateFlow<Boolean> = _openReader.asStateFlow()

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
        notesEditor.notes.filterNotNull(),
        notesEditor.saveState,
        collectionsRepository.observeCollections(),
        collectionsRepository.observeCollectionIds(openAlexId)
    ) { current, typed, state, collections, memberOf ->
        PaperDetailsUiState.Loaded(current, typed, state, collections, memberOf)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaperDetailsUiState.Loading)

    /** The PDF row: the stored file, a running or failed download, and the paper's open-access link. */
    val pdf: StateFlow<PdfRow> = combine(
        pdfRepository.observePdf(openAlexId),
        pdfRepository.observeDownload(openAlexId),
        paper
    ) { stored, download, current ->
        val link = current?.paper?.openAccessPdfUrl
        PdfRow(pdfRowState(stored, download, link), link)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PdfRow(PdfRowState.None, null))

    fun onNoteChange(section: NoteSection, text: String) = notesEditor.onNoteChange(section, text)

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

                // Only while the dialog is still open: a second tap on Create must not reopen it.
                CollectionResult.NameTaken -> _newCollectionDialog.update { it?.copy(nameTaken = true) }

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

    /** Starts the download in the application scope (the repository's), so leaving Details doesn't stop it. Also Try again. */
    fun downloadPdf() {
        pdfRepository.download(openAlexId)
    }

    fun cancelPdfDownload() {
        pdfRepository.cancelDownload(openAlexId)
    }

    /** Copies the chosen file in, replacing any stored PDF. Says why when it isn't stored; nothing changes then. */
    fun attachPdf(uri: Uri) {
        viewModelScope.launch {
            val result = try {
                pdfRepository.attach(openAlexId, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AttachResult.Unreadable
            }
            _message.value = when (result) {
                AttachResult.Done -> return@launch
                AttachResult.NotPdf -> PaperDetailsMessage.PdfAttachNotPdf
                AttachResult.TooLarge -> PaperDetailsMessage.PdfAttachTooLarge
                AttachResult.Unreadable -> PaperDetailsMessage.PdfAttachFailed
            }
        }
    }

    /** On failure the row keeps showing the stored PDF; the spec has no message for it. */
    fun removePdf() {
        viewModelScope.launch {
            try {
                pdfRepository.remove(openAlexId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Unit
            }
        }
    }

    fun onRetrySave() = notesEditor.retry()

    fun onMessageShown() {
        _message.value = null
    }

    /** Writes unsaved notes now, on the application scope, so leaving the screen can't cancel the write. */
    fun flushNotes() = notesEditor.flush()

    /**
     * Saves unsaved notes first, so Undo on the screen below restores what was just typed, then asks to leave.
     * If that save fails, the screen stays, showing Couldn't save with Retry, so Undo can never bring back older notes.
     */
    fun onRemove() {
        viewModelScope.launch {
            val saved = notesEditor.saveNow()
            if (saved) _exit.value = PaperDetailsExit.Removed
        }
    }

    /**
     * Read on the PDF row: saves typed notes first, so the reader's Notes sheet reads them and can never overwrite them with
     * older ones. If that save fails, Details stays, showing Couldn't save with Retry, as Remove does.
     */
    fun onReadPdf() {
        viewModelScope.launch { if (notesEditor.saveNow()) _openReader.value = true }
    }

    fun onReaderOpened() {
        _openReader.value = false
    }

    /** Back from the reader, whose Notes sheet may have written these notes. Never replaces unsaved typing. */
    fun reloadNotes() {
        viewModelScope.launch { notesEditor.reload() }
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
}
