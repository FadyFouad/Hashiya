package com.etatech.hashiya.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.model.Paper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Empty : LibraryUiState
    data class Papers(val papers: List<Paper>) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(private val libraryRepository: LibraryRepository) : ViewModel() {
    private val savedPapers = libraryRepository.observeSavedPapers()

    val uiState: StateFlow<LibraryUiState> = savedPapers
        .map { papers -> if (papers.isEmpty()) LibraryUiState.Empty else LibraryUiState.Papers(papers) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    private val selectedId = MutableStateFlow<String?>(null)

    /** The paper shown in the preview sheet; clears itself if that paper is removed. */
    val selectedPaper: StateFlow<Paper?> = combine(savedPapers, selectedId) { papers, id ->
        papers.firstOrNull { it.openAlexId == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pendingUndo = MutableStateFlow<RemovedPaper?>(null)
    val pendingUndo: StateFlow<RemovedPaper?> = _pendingUndo.asStateFlow()

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
