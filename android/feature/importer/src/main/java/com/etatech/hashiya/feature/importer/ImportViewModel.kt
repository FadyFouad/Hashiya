package com.etatech.hashiya.feature.importer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.repository.ImportRepository
import com.etatech.hashiya.core.model.ImportState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The route's argument: ImportRoute.uri, a file shared or opened from another app. */
internal const val ARG_URI = "uri"
private const val KEY_URI_HANDLED = "uri_handled"
private const val KEY_ADD_TO_COLLECTION = "add_to_collection"

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: ImportRepository,
    private val reader: ImportFileReader,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val reading = MutableStateFlow(false)
    private val readFailed = MutableStateFlow(false)

    val uiState: StateFlow<ImportUiState> = combine(
        repository.observeCurrentJob(),
        reading,
        readFailed,
        savedStateHandle.getStateFlow<Boolean?>(KEY_ADD_TO_COLLECTION, null)
    ) { job, reading, readFailed, addToCollection -> importUiState(job, reading, readFailed, addToCollection) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportUiState.Start)

    init {
        // A shared file is read once, not again after a rotation or process death.
        val uri = savedStateHandle.get<String>(ARG_URI)
        if (uri != null && savedStateHandle.get<Boolean>(KEY_URI_HANDLED) != true) {
            savedStateHandle[KEY_URI_HANDLED] = true
            onFileChosen(uri)
        }
    }

    fun onFileChosen(uri: String) {
        viewModelScope.launch {
            readFailed.value = false
            reading.value = true
            savedStateHandle[KEY_ADD_TO_COLLECTION] = null
            val file = reader.read(uri)
            if (file == null) readFailed.value = true else repository.start(file.name, file.bytes)
            reading.value = false
        }
    }

    fun onAddToCollectionChange(add: Boolean) {
        savedStateHandle[KEY_ADD_TO_COLLECTION] = add
    }

    fun onConfirm() {
        val ready = uiState.value as? ImportUiState.Ready ?: return
        viewModelScope.launch { repository.confirm(ready.job.id, ready.addToCollection) }
    }

    fun onCancel() {
        val job = when (val state = uiState.value) {
            is ImportUiState.Analyzing -> state.job
            is ImportUiState.Ready -> state.job
            is ImportUiState.Importing -> state.job
            else -> return
        }
        viewModelScope.launch { repository.cancel(job.id) }
    }

    /**
     * Forgets a finished, failed or cancelled job (or a file that couldn't be read), back to the start, then runs [then]:
     * leaving the screen only afterwards, so clearing this ViewModel can't cancel the dismiss halfway.
     */
    fun onDismiss(then: () -> Unit = {}) {
        readFailed.value = false
        savedStateHandle[KEY_ADD_TO_COLLECTION] = null
        viewModelScope.launch {
            val job = repository.observeCurrentJob().first()
            if (job != null && !job.isRunning && job.state != ImportState.Ready) repository.dismiss(job.id)
            then()
        }
    }
}
