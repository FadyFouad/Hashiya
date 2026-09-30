package com.etatech.hashiya.feature.paperdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.FakeLibraryRepository
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
import org.junit.Rule
import org.junit.Test

private const val SAVE_DURATION_MS = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class PaperDetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLibraryRepository()
    private val paper = SamplePapers.bert
    private val id = paper.openAlexId

    /** The application scope is the test's backgroundScope: it outlives viewModelScope, like the real one. */
    private fun TestScope.viewModel(libraryRepository: LibraryRepository = repository): PaperDetailsViewModel {
        val viewModel = PaperDetailsViewModel(SavedStateHandle(mapOf(ARG_OPEN_ALEX_ID to id)), libraryRepository, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
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
}

/** Takes [SAVE_DURATION_MS] of virtual time to write notes, so a test can see a write in progress. */
private class SlowNotesRepository(private val delegate: FakeLibraryRepository) : LibraryRepository by delegate {
    override suspend fun saveNotes(openAlexId: String, notes: PaperNotes) {
        delay(SAVE_DURATION_MS)
        delegate.saveNotes(openAlexId, notes)
    }
}
