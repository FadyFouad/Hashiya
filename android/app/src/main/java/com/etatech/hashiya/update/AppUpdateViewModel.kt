package com.etatech.hashiya.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.repository.AppUpdateRepository
import com.etatech.hashiya.core.model.RequiredUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** This install's versionCode. */
fun interface InstalledVersionCode {
    operator fun invoke(): Long
}

/**
 * Whether this build must update. [check] runs on every start; the app shows normally while it runs.
 * Once blocked, it stays blocked for the life of the process, so a later failed check never lets the user back in.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    private val installedVersionCode: InstalledVersionCode
) : ViewModel() {
    private val _requiredUpdate = MutableStateFlow<RequiredUpdate?>(null)
    val requiredUpdate: StateFlow<RequiredUpdate?> = _requiredUpdate.asStateFlow()

    private var running: Job? = null

    fun check() {
        if (_requiredUpdate.value != null || running?.isActive == true) return
        running = viewModelScope.launch {
            repository.requiredUpdate(installedVersionCode())?.let { _requiredUpdate.value = it }
        }
    }
}
