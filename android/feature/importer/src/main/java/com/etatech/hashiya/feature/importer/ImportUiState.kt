package com.etatech.hashiya.feature.importer

import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState

/** What the Import screen shows (spec §8.2). */
sealed interface ImportUiState {
    /** No job: explains the feature and offers the picker. */
    data object Start : ImportUiState

    /** Reading the chosen file, before a job exists. */
    data object Reading : ImportUiState

    data class Analyzing(val job: ImportJob) : ImportUiState

    data class Ready(val job: ImportJob, val addToCollection: Boolean) : ImportUiState

    data class Importing(val job: ImportJob) : ImportUiState

    data class Done(val job: ImportJob) : ImportUiState

    data class Failed(val failure: ImportFailure) : ImportUiState

    data class Cancelled(val job: ImportJob) : ImportUiState
}

/**
 * Combines the repository's job with what only the screen knows: a file being read, a file that couldn't be read, and an
 * unsaved change to the collection checkbox (null: the job's own choice).
 */
internal fun importUiState(job: ImportJob?, reading: Boolean, readFailed: Boolean, addToCollection: Boolean?): ImportUiState = when {
    reading -> ImportUiState.Reading

    readFailed -> ImportUiState.Failed(ImportFailure.Unreadable)

    job == null -> ImportUiState.Start

    else -> when (job.state) {
        ImportState.Analyzing -> ImportUiState.Analyzing(job)
        ImportState.Ready -> ImportUiState.Ready(job, addToCollection ?: job.addToCollection)
        ImportState.Importing -> ImportUiState.Importing(job)
        ImportState.Done -> ImportUiState.Done(job)
        ImportState.Failed -> ImportUiState.Failed(job.failure ?: ImportFailure.Unreadable)
        ImportState.Cancelled -> ImportUiState.Cancelled(job)
    }
}
