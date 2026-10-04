package com.etatech.hashiya.feature.settings.restore

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.backup.BackupException
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.backup.OpenFailure
import com.etatech.hashiya.core.data.backup.OpenResult
import com.etatech.hashiya.core.data.backup.PreparedBackup
import com.etatech.hashiya.core.data.backup.RestorePreview
import com.etatech.hashiya.core.data.backup.RestoreResult
import com.etatech.hashiya.core.data.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RestoreUiState {
    data object Loading : RestoreUiState

    data class Invalid(val reason: OpenFailure) : RestoreUiState

    data class Preview(val preview: RestorePreview) : RestoreUiState

    data class Applying(val progress: Float) : RestoreUiState

    data class Done(val result: RestoreResult) : RestoreUiState

    data class Failed(val failure: BackupFailure) : RestoreUiState
}

@HiltViewModel
class RestoreViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryBackup: LibraryBackup,
    /** A merge can't be cancelled halfway, so it runs on, and finishes, even if the screen goes away. */
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    private val state = MutableStateFlow<RestoreUiState>(RestoreUiState.Loading)
    val uiState: StateFlow<RestoreUiState> = state.asStateFlow()

    /** The copied archive; deleted when the restore finishes or the screen goes away. */
    private var backup: PreparedBackup? = null

    init {
        // RestoreRoute's only argument; read by name so the ViewModel doesn't need the navigation route type.
        val uri = Uri.parse(checkNotNull(savedStateHandle.get<String>("uri")))
        viewModelScope.launch {
            state.value = when (val result = libraryBackup.open(uri)) {
                is OpenResult.Ready -> {
                    backup = result.backup
                    RestoreUiState.Preview(result.preview)
                }

                is OpenResult.Failed -> RestoreUiState.Invalid(result.reason)
            }
        }
    }

    fun onConfirm() {
        val prepared = backup ?: return
        if (state.value !is RestoreUiState.Preview) return
        state.value = RestoreUiState.Applying(0f)
        applicationScope.launch {
            state.value = try {
                RestoreUiState.Done(libraryBackup.apply(prepared) { progress -> state.value = RestoreUiState.Applying(progress) })
            } catch (e: BackupException) {
                RestoreUiState.Failed(e.failure)
            }
            discard()
        }
    }

    fun onCancel() = discard()

    private fun discard() {
        backup?.let(libraryBackup::discard)
        backup = null
    }

    override fun onCleared() = discard()
}
