package com.etatech.hashiya.core.data.notes

import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The autosave behind every notes editor (Details and the reader's Notes sheet). The notes are read once, so later database
 * emissions never replace typing; typing is saved [SAVE_DEBOUNCE_MS] after it stops; writes run one at a time, so they land in
 * order and a flush can't be overtaken.
 *
 * [scope] is the screen's (viewModelScope); [applicationScope] outlives it, for [flush]. [onSaveFailed] runs after a failed write, [onSaved] after a successful one that changed the notes.
 */
@OptIn(FlowPreview::class)
class NotesEditor(
    private val openAlexId: String,
    private val libraryRepository: LibraryRepository,
    private val scope: CoroutineScope,
    private val applicationScope: CoroutineScope,
    private val onSaveFailed: () -> Unit,
    private val onSaved: () -> Unit = {}
) {
    private val _notes = MutableStateFlow<PaperNotes?>(null)

    /** The notes as typed; null until the stored notes are read. */
    val notes: StateFlow<PaperNotes?> = _notes.asStateFlow()

    private val _saveState = MutableStateFlow(NotesSaveState.Idle)
    val saveState: StateFlow<NotesSaveState> = _saveState.asStateFlow()

    private val _version = MutableStateFlow(0)

    /** Grows when [reload] replaces notes already on screen, so fields that seeded themselves once start again. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val mutex = Mutex()

    /** The notes last read or written. Guarded by [mutex]. */
    private var storedNotes: PaperNotes? = null

    init {
        scope.launch { reload() }
        // collect is sequential: each write finishes before the next starts, and a burst of typing is one write.
        scope.launch { _notes.filterNotNull().debounce(SAVE_DEBOUNCE_MS).collect { save(it) } }
    }

    fun onNoteChange(section: NoteSection, text: String) {
        _notes.update { current -> current?.with(section, text) }
    }

    fun retry() {
        scope.launch { _notes.value?.let { save(it) } }
    }

    /** Writes unsaved notes now, on the application scope, so leaving the screen can't cancel the write. */
    fun flush() {
        val current = _notes.value ?: return
        applicationScope.launch { save(current) }
    }

    /** Writes unsaved notes now and waits. Returns false only when the write failed. */
    suspend fun saveNow(): Boolean = _notes.value?.let { save(it) } ?: true

    /**
     * Reads the stored notes again, for when another screen may have written them, but only when nothing typed here is unsaved,
     * so typing is never replaced. The first read happens on creation.
     */
    suspend fun reload() {
        mutex.withLock {
            val current = _notes.value
            if (current != storedNotes) return@withLock
            val stored = libraryRepository.observeNotes(openAlexId).first()
            storedNotes = stored
            if (current != null && current != stored) _version.update { it + 1 }
            _notes.value = stored
        }
    }

    /** Returns false only when the write failed. */
    private suspend fun save(value: PaperNotes): Boolean = mutex.withLock {
        if (value == storedNotes) return@withLock true
        _saveState.value = NotesSaveState.Saving
        try {
            libraryRepository.saveNotes(openAlexId, value)
            storedNotes = value
            _saveState.value = NotesSaveState.Saved
            onSaved()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _saveState.value = NotesSaveState.Failed
            onSaveFailed()
            false
        }
    }

    companion object {
        const val SAVE_DEBOUNCE_MS = 500L
    }
}
