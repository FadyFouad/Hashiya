package com.etatech.hashiya.feature.importer

import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportUiStateTest {
    private fun job(state: ImportState, failure: ImportFailure? = null) =
        ImportJob(id = 1, fileName = "thesis.bib", state = state, addToCollection = true, failure = failure)

    @Test
    fun `no job is the start`() {
        assertEquals(ImportUiState.Start, importUiState(null, reading = false, readFailed = false, addToCollection = null))
    }

    @Test
    fun `reading and a file that couldn't be read come before any job`() {
        val done = job(ImportState.Done)
        assertEquals(ImportUiState.Reading, importUiState(done, reading = true, readFailed = false, addToCollection = null))
        assertEquals(
            ImportUiState.Failed(ImportFailure.Unreadable),
            importUiState(done, reading = false, readFailed = true, addToCollection = null)
        )
    }

    @Test
    fun `each job state has its screen`() {
        fun ui(job: ImportJob) = importUiState(job, reading = false, readFailed = false, addToCollection = null)
        assertEquals(ImportUiState.Analyzing(job(ImportState.Analyzing)), ui(job(ImportState.Analyzing)))
        assertEquals(ImportUiState.Ready(job(ImportState.Ready), addToCollection = true), ui(job(ImportState.Ready)))
        assertEquals(ImportUiState.Importing(job(ImportState.Importing)), ui(job(ImportState.Importing)))
        assertEquals(ImportUiState.Done(job(ImportState.Done)), ui(job(ImportState.Done)))
        assertEquals(ImportUiState.Cancelled(job(ImportState.Cancelled)), ui(job(ImportState.Cancelled)))
        assertEquals(
            ImportUiState.Failed(ImportFailure.TooLarge),
            ui(job(ImportState.Failed, ImportFailure.TooLarge))
        )
        assertEquals(ImportUiState.Failed(ImportFailure.Unreadable), ui(job(ImportState.Failed)))
    }

    @Test
    fun `an unsaved change to the checkbox wins over the job's choice`() {
        assertEquals(
            ImportUiState.Ready(job(ImportState.Ready), addToCollection = false),
            importUiState(job(ImportState.Ready), reading = false, readFailed = false, addToCollection = false)
        )
    }
}
