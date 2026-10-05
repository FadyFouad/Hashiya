package com.etatech.hashiya.feature.paperdetails

import androidx.lifecycle.ViewModelStore
import com.etatech.hashiya.core.data.repository.DownloadFailure
import com.etatech.hashiya.core.data.repository.DownloadState
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.FakeAnalytics
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val SAVE_DURATION_MS = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class PaperDetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()
    private val pdfs = FakePdfRepository()
    private val analytics = FakeAnalytics()
    private val paper = SamplePapers.bert
    private val id = paper.openAlexId

    /** The application scope is the test's backgroundScope: it outlives viewModelScope, like the real one. */
    private fun TestScope.viewModel(libraryRepository: LibraryRepository = repository): PaperDetailsViewModel {
        val viewModel = PaperDetailsViewModel(
            id,
            libraryRepository,
            FakeCollectionsRepository(repository),
            FakeCitationRepository(),
            pdfs,
            backgroundScope,
            analytics
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.pdf.collect() }
        return viewModel
    }

    private fun PaperDetailsViewModel.loaded(): PaperDetailsUiState.Loaded =
        uiState.value as? PaperDetailsUiState.Loaded ?: error("Expected Loaded but was ${uiState.value}")

    /** Lets exactly the debounce pause pass, then runs what is due. */
    private fun TestScope.waitOutTheDebounce() {
        advanceTimeBy(NOTES_SAVE_DEBOUNCE_MS)
        runCurrent()
    }

    private val noSaves = emptyList<Pair<String, PaperNotes>>()

    @Test
    fun loadsThePaperWithItsStatusAndStoredNotesWithoutSaving() = runTest {
        repository.save(paper)
        repository.setStatus(id, ReadingStatus.Reading)
        repository.saveNotes(id, PaperNotes(summary = "Bidirectional pre-training"))
        repository.notesSaves.clear()

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            PaperDetailsUiState.Loaded(
                LibraryPaper(paper, ReadingStatus.Reading),
                PaperNotes(summary = "Bidirectional pre-training"),
                NotesSaveState.Idle
            ),
            viewModel.uiState.value
        )
        assertEquals(noSaves, repository.notesSaves)
    }

    @Test
    fun typingSavesOnlyAfterThePause() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Method, "Masked LM")
        advanceTimeBy(NOTES_SAVE_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(noSaves, repository.notesSaves)
        assertEquals(PaperNotes(method = "Masked LM"), viewModel.loaded().notes)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(id to PaperNotes(method = "Masked LM")), repository.notesSaves)
        assertEquals(NotesSaveState.Saved, viewModel.loaded().saveState)
    }

    @Test
    fun aBurstOfTypingIsOneWrite() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Method, "M")
        advanceTimeBy(200)
        viewModel.onNoteChange(NoteSection.Method, "Ma")
        advanceTimeBy(200)
        viewModel.onNoteChange(NoteSection.Method, "Mas")
        advanceUntilIdle()

        assertEquals(listOf(id to PaperNotes(method = "Mas")), repository.notesSaves)
    }

    @Test
    fun saveStateShowsSavingWhileTheWriteRuns() = runTest {
        repository.save(paper)
        val viewModel = viewModel(SlowNotesRepository(repository))
        advanceUntilIdle()
        assertEquals(NotesSaveState.Idle, viewModel.loaded().saveState)

        viewModel.onNoteChange(NoteSection.Summary, "Pre-training")
        waitOutTheDebounce()
        assertEquals(NotesSaveState.Saving, viewModel.loaded().saveState)

        advanceTimeBy(SAVE_DURATION_MS)
        runCurrent()
        assertEquals(NotesSaveState.Saved, viewModel.loaded().saveState)
    }

    @Test
    fun failedSaveShowsCouldntSaveKeepsTheTextAndRetrySaves() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        repository.failOnSaveNotes = true

        viewModel.onNoteChange(NoteSection.Limitations, "Small sample")
        advanceUntilIdle()

        assertEquals(NotesSaveState.Failed, viewModel.loaded().saveState)
        assertEquals(PaperDetailsMessage.NotesSaveFailed, viewModel.message.value)
        assertEquals(PaperNotes(limitations = "Small sample"), viewModel.loaded().notes)
        viewModel.onMessageShown()
        assertNull(viewModel.message.value)

        repository.failOnSaveNotes = false
        viewModel.onRetrySave()
        advanceUntilIdle()
        assertEquals(NotesSaveState.Saved, viewModel.loaded().saveState)
        assertEquals(PaperNotes(limitations = "Small sample"), repository.observeNotes(id).first())
    }

    @Test
    fun theNextEditAfterAFailureSavesToo() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        repository.failOnSaveNotes = true
        viewModel.onNoteChange(NoteSection.Method, "a")
        advanceUntilIdle()
        repository.failOnSaveNotes = false

        viewModel.onNoteChange(NoteSection.Method, "ab")
        advanceUntilIdle()

        assertEquals(NotesSaveState.Saved, viewModel.loaded().saveState)
        assertEquals(PaperNotes(method = "ab"), repository.observeNotes(id).first())
    }

    @Test
    fun clearingEveryNoteSavesBlankNotes() = runTest {
        repository.save(paper)
        repository.saveNotes(id, PaperNotes(method = "Survey"))
        repository.notesSaves.clear()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Method, "")
        advanceUntilIdle()

        assertEquals(listOf(id to PaperNotes()), repository.notesSaves)
        assertEquals(PaperNotes(), repository.observeNotes(id).first())
    }

    @Test
    fun laterStoredNotesDoNotReplaceTyping() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Summary, "Mine")
        repository.saveNotes(id, PaperNotes(summary = "From elsewhere"))
        runCurrent()

        assertEquals(PaperNotes(summary = "Mine"), viewModel.loaded().notes)
    }

    @Test
    fun flushSavesPendingNotesAtOnce() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Thoughts, "Chapter 2")
        viewModel.flushNotes()
        runCurrent()
        assertEquals(listOf(id to PaperNotes(thoughts = "Chapter 2")), repository.notesSaves)

        // The debounced save that follows finds nothing new to write.
        advanceUntilIdle()
        assertEquals(1, repository.notesSaves.size)
    }

    @Test
    fun flushWithNothingNewWritesNothing() = runTest {
        repository.save(paper)
        repository.saveNotes(id, PaperNotes(summary = "Stored"))
        repository.notesSaves.clear()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.flushNotes()
        runCurrent()

        assertEquals(noSaves, repository.notesSaves)
    }

    @Test
    fun clearingTheViewModelSavesPendingNotes() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNoteChange(NoteSection.KeyFindings, "Beats ELMo")

        // Leaving the screen clears the ViewModel and cancels viewModelScope, so the debounced save can never run:
        // only the flush on the application scope can save. ViewModelStore.put is library-internal, which is fine in a test.
        ViewModelStore().apply { put("details", viewModel) }.clear()
        // runCurrent, not advanceUntilIdle: the application scope here is backgroundScope, whose work advanceUntilIdle skips.
        runCurrent()

        assertEquals(listOf(id to PaperNotes(keyFindings = "Beats ELMo")), repository.notesSaves)
    }

    @Test
    fun changingTheStatusUpdatesThePaper() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onStatusChange(ReadingStatus.Read)
        advanceUntilIdle()

        assertEquals(ReadingStatus.Read, viewModel.loaded().paper.status)
    }

    @Test
    fun failedStatusChangeShowsTheMessageAndKeepsTheStoredStatus() = runTest {
        repository.save(paper)
        repository.failOnSetStatus = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onStatusChange(ReadingStatus.Read)
        advanceUntilIdle()

        assertEquals(PaperDetailsMessage.StatusUpdateFailed, viewModel.message.value)
        assertEquals(ReadingStatus.ToRead, viewModel.loaded().paper.status)
    }

    @Test
    fun aPaperRemovedElsewhereClosesTheScreen() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertNull(viewModel.exit.value)

        repository.remove(id)
        advanceUntilIdle()

        assertEquals(PaperDetailsExit.Closed, viewModel.exit.value)
    }

    @Test
    fun anUnsavedPaperClosesTheScreenAndStaysLoading() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(PaperDetailsExit.Closed, viewModel.exit.value)
        assertEquals(PaperDetailsUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun removeSavesPendingNotesBeforeLeaving() = runTest {
        repository.save(paper)
        val viewModel = viewModel(SlowNotesRepository(repository))
        advanceUntilIdle()
        viewModel.onNoteChange(NoteSection.Thoughts, "Keep this")

        viewModel.onRemove()
        runCurrent()
        assertNull(viewModel.exit.value)

        advanceTimeBy(SAVE_DURATION_MS)
        runCurrent()
        assertEquals(PaperDetailsExit.Removed, viewModel.exit.value)
        assertEquals(PaperNotes(thoughts = "Keep this"), repository.observeNotes(id).first())
    }

    @Test
    fun removeWaitsWhenTheSaveFailsSoUndoCantRestoreStaleNotes() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        repository.failOnSaveNotes = true
        viewModel.onNoteChange(NoteSection.Thoughts, "Keep this")

        viewModel.onRemove()
        runCurrent()

        assertNull(viewModel.exit.value)
        assertEquals(PaperDetailsMessage.NotesSaveFailed, viewModel.message.value)

        repository.failOnSaveNotes = false
        viewModel.onRemove()
        runCurrent()
        assertEquals(PaperDetailsExit.Removed, viewModel.exit.value)
    }

    @Test
    fun readingThePdfSavesTypedNotesFirst() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNoteChange(NoteSection.Summary, "Read the method twice")

        viewModel.onReadPdf()
        runCurrent()

        // Saved before the reader opens, so the reader reads these notes and never overwrites them with older ones.
        assertEquals(listOf(id to PaperNotes(summary = "Read the method twice")), repository.notesSaves)
        assertEquals(true, viewModel.openReader.value)
    }

    @Test
    fun aFailedSaveKeepsDetailsOpenInsteadOfOpeningTheReader() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        repository.failOnSaveNotes = true
        viewModel.onNoteChange(NoteSection.Summary, "Not saved yet")

        viewModel.onReadPdf()
        runCurrent()

        assertEquals(false, viewModel.openReader.value)
        assertEquals(PaperDetailsMessage.NotesSaveFailed, viewModel.message.value)
    }

    @Test
    fun notesWrittenInTheReaderAppearAfterReload() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        val versionBefore = viewModel.notesVersion.value
        // The reader's Notes sheet writes through the same repository.
        repository.saveNotes(id, PaperNotes(summary = "Written while reading"))

        viewModel.reloadNotes()
        advanceUntilIdle()

        assertEquals(PaperNotes(summary = "Written while reading"), viewModel.loaded().notes)
        assertEquals(versionBefore + 1, viewModel.notesVersion.value)
    }

    @Test
    fun reloadNeverReplacesUnsavedTyping() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNoteChange(NoteSection.Method, "Still typing")
        repository.saveNotes(id, PaperNotes(summary = "Written elsewhere"))

        viewModel.reloadNotes()
        runCurrent()

        assertEquals(PaperNotes(method = "Still typing"), viewModel.loaded().notes)
    }

    private val linked = SamplePapers.attention
    private val linkedId = linked.openAlexId
    private val link = "https://arxiv.org/pdf/1706.03762"
    private val storedPdf = PaperPdf(PdfSource.Downloaded, sizeBytes = 2_400_000, addedAt = 1_000)

    /** The view model for [linked], the paper with an open-access link. */
    private fun TestScope.linkedViewModel(): PaperDetailsViewModel {
        val viewModel = PaperDetailsViewModel(
            linkedId,
            repository,
            FakeCollectionsRepository(repository),
            FakeCitationRepository(),
            pdfs,
            backgroundScope,
            analytics
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.pdf.collect() }
        return viewModel
    }

    @Test
    fun aPaperWithALinkAndNoPdfOffersDownloadAndAttach() = runTest {
        repository.save(linked)
        val viewModel = linkedViewModel()
        advanceUntilIdle()

        assertEquals(PdfRow(PdfRowState.Available, link), viewModel.pdf.value)
        assertEquals(listOf(PdfAction.Download), viewModel.pdf.value.primary)
        assertEquals(listOf(PdfAction.Attach), viewModel.pdf.value.overflow)
    }

    @Test
    fun aPaperWithoutALinkOffersAttachOnly() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(PdfRow(PdfRowState.None, null), viewModel.pdf.value)
        assertEquals(listOf(PdfAction.Attach), viewModel.pdf.value.primary)
        assertEquals(emptyList<PdfAction>(), viewModel.pdf.value.overflow)
    }

    @Test
    fun aRunningDownloadShowsItsProgressAndOffersCancel() = runTest {
        repository.save(linked)
        val viewModel = linkedViewModel()
        pdfs.setDownload(linkedId, DownloadState.Running(bytes = 500_000, totalBytes = 2_000_000))
        advanceUntilIdle()

        assertEquals(PdfRowState.Downloading(500_000, 2_000_000), viewModel.pdf.value.state)
        assertEquals(listOf(PdfAction.Cancel), viewModel.pdf.value.primary)
    }

    @Test
    fun aStoredPdfOpensTheReaderAndOffersReplaceRemoveAndTheLink() = runTest {
        repository.save(linked)
        pdfs.setPdf(linkedId, storedPdf)
        val viewModel = linkedViewModel()
        advanceUntilIdle()

        assertEquals(PdfRowState.Stored(storedPdf), viewModel.pdf.value.state)
        assertEquals(listOf(PdfAction.Read), viewModel.pdf.value.primary)
        assertEquals(listOf(PdfAction.Replace, PdfAction.Remove, PdfAction.OpenLink), viewModel.pdf.value.overflow)
    }

    @Test
    fun aStoredPdfWithoutALinkHasNoOpenLink() = runTest {
        repository.save(paper)
        pdfs.setPdf(id, storedPdf.copy(source = PdfSource.Attached))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(PdfAction.Replace, PdfAction.Remove), viewModel.pdf.value.overflow)
    }

    @Test
    fun aNotPdfDownloadOffersBrowserAndAttach() = runTest {
        repository.save(linked)
        val viewModel = linkedViewModel()
        pdfs.setDownload(linkedId, DownloadState.Failed(DownloadFailure.NotPdf))
        advanceUntilIdle()

        assertEquals(PdfRowState.Failed(DownloadFailure.NotPdf), viewModel.pdf.value.state)
        assertEquals(link, viewModel.pdf.value.link)
        val actions = viewModel.pdf.value.primary
        assertTrue(PdfAction.OpenInBrowser in actions)
        assertTrue(PdfAction.Attach in actions)
        assertEquals(listOf(PdfAction.TryAgain, PdfAction.OpenInBrowser, PdfAction.Attach), actions)
    }

    @Test
    fun aNoLinkFailureFallsBackToNoPdf() = runTest {
        repository.save(paper)
        val viewModel = viewModel()
        pdfs.setDownload(id, DownloadState.Failed(DownloadFailure.NoLink))
        advanceUntilIdle()

        assertEquals(PdfRowState.None, viewModel.pdf.value.state)
    }

    @Test
    fun aRunningDownloadWinsOverAnEarlierFailure() = runTest {
        repository.save(linked)
        val viewModel = linkedViewModel()
        pdfs.setDownload(linkedId, DownloadState.Failed(DownloadFailure.Offline))
        advanceUntilIdle()
        pdfs.setDownload(linkedId, DownloadState.Running(bytes = 0, totalBytes = null))
        advanceUntilIdle()

        assertEquals(PdfRowState.Downloading(0, null), viewModel.pdf.value.state)
    }

    @Test
    fun downloadCancelAndRemoveReachTheRepository() = runTest {
        repository.save(linked)
        pdfs.setPdf(linkedId, storedPdf)
        val viewModel = linkedViewModel()

        viewModel.downloadPdf()
        viewModel.cancelPdfDownload()
        viewModel.removePdf()
        advanceUntilIdle()

        assertEquals(listOf(linkedId), pdfs.downloads)
        assertEquals(listOf(linkedId), pdfs.cancels)
        assertEquals(listOf(linkedId), pdfs.removals)
    }
}

/** Takes [SAVE_DURATION_MS] of virtual time to write notes, so a test can see a write in progress. */
private class SlowNotesRepository(private val delegate: FakeLibraryRepository) : LibraryRepository by delegate {
    override suspend fun saveNotes(openAlexId: String, notes: PaperNotes) {
        delay(SAVE_DURATION_MS)
        delegate.saveNotes(openAlexId, notes)
    }
}
