package com.etatech.hashiya.feature.library

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryCollectionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val collections = FakeCollectionsRepository(library)
    private val citations = FakeCitationRepository()

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): LibraryViewModel {
        val viewModel = LibraryViewModel(handle, library, collections, citations, FakePdfRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.header.collect() }
        return viewModel
    }

    /** attention, bert and vit saved; bert and vit in "Thesis". */
    private suspend fun thesis(): PaperCollection {
        SamplePapers.all.forEach { library.save(it) }
        val id = (collections.create("Thesis") as CollectionResult.Done).id
        collections.setMembership(id, SamplePapers.bert.openAlexId, true)
        collections.setMembership(id, SamplePapers.vit.openAlexId, true)
        return PaperCollection(id, "Thesis", 2)
    }

    private fun LibraryViewModel.titles() = (uiState.value as LibraryUiState.Papers).papers.map { it.paper.title }

    @Test
    fun selectingACollectionFiltersTheListCountsAndHeader() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()
        assertEquals(3, viewModel.header.value.viewSize)

        viewModel.onSelectCollection(thesis.id)
        advanceUntilIdle()

        assertEquals(listOf(SamplePapers.vit.title, SamplePapers.bert.title), viewModel.titles())
        assertEquals(2, (viewModel.uiState.value as LibraryUiState.Papers).filter.total)
        assertEquals(thesis, viewModel.header.value.selected)
        assertEquals(2, viewModel.header.value.viewSize)
        assertEquals(3, viewModel.header.value.libraryCount)
    }

    @Test
    fun searchAndChipApplyInsideTheCollection() = runTest {
        val thesis = thesis()
        library.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Read)
        val viewModel = viewModel()
        viewModel.onSelectCollection(thesis.id)
        viewModel.onStatusFilterChange(ReadingStatus.Read)
        advanceUntilIdle()

        assertEquals(listOf(SamplePapers.bert.title), viewModel.titles())
        assertEquals(2, viewModel.header.value.viewSize)
    }

    @Test
    fun emptyCollectionHasItsOwnState() = runTest {
        SamplePapers.all.forEach { library.save(it) }
        val id = (collections.create("Empty") as CollectionResult.Done).id
        val viewModel = viewModel()
        viewModel.onSelectCollection(id)
        advanceUntilIdle()

        assertEquals(LibraryUiState.CollectionEmpty(PaperCollection(id, "Empty", 0)), viewModel.uiState.value)
        assertEquals(0, viewModel.header.value.viewSize)
    }

    @Test
    fun selectionSurvivesProcessDeathAndFallsBackWhenTheCollectionIsDeleted() = runTest {
        val thesis = thesis()
        val handle = SavedStateHandle()
        viewModel(handle).onSelectCollection(thesis.id)
        advanceUntilIdle()

        val restored = viewModel(handle)
        advanceUntilIdle()
        assertEquals(thesis.id, restored.header.value.selected?.id)

        collections.delete(thesis.id)
        advanceUntilIdle()
        assertNull(restored.header.value.selected)
        assertEquals(3, restored.titles().size)
    }

    @Test
    fun swipeInACollectionRemovesOnlyTheMembershipWithUndo() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()
        viewModel.onSelectCollection(thesis.id)
        advanceUntilIdle()

        viewModel.onRemove(SamplePapers.bert)
        advanceUntilIdle()

        assertEquals(CollectionRemoval(thesis, SamplePapers.bert.openAlexId), viewModel.pendingCollectionUndo.value)
        assertNull(viewModel.pendingUndo.value)
        assertEquals(listOf(SamplePapers.vit.title), viewModel.titles())
        viewModel.onSelectCollection(null)
        advanceUntilIdle()
        assertEquals(3, viewModel.titles().size)

        viewModel.onUndoCollectionRemove()
        advanceUntilIdle()
        assertNull(viewModel.pendingCollectionUndo.value)
        assertEquals(setOf(thesis.id), collections.observeCollectionIdsNow(SamplePapers.bert.openAlexId))
    }

    @Test
    fun undoIntoACollectionDeletedMeanwhileIsDropped() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()
        viewModel.onSelectCollection(thesis.id)
        advanceUntilIdle()
        viewModel.onRemove(SamplePapers.bert)
        advanceUntilIdle()

        collections.delete(thesis.id)
        advanceUntilIdle()
        viewModel.onUndoCollectionRemove()
        advanceUntilIdle()

        assertNull(viewModel.pendingCollectionUndo.value)
        assertNull(viewModel.message.value)
        assertEquals(3, viewModel.titles().size)
    }

    @Test
    fun swipeInAllPapersStillRemovesFromTheLibrary() = runTest {
        thesis()
        val viewModel = viewModel()
        viewModel.onRemove(SamplePapers.bert)
        advanceUntilIdle()

        assertEquals(SamplePapers.bert, viewModel.pendingUndo.value?.paper)
        assertNull(viewModel.pendingCollectionUndo.value)
    }

    @Test
    fun newCollectionDialogCreatesOrShowsTheClash() = runTest {
        thesis()
        val viewModel = viewModel()

        viewModel.onNewCollection()
        assertEquals(CollectionDialog.New(), viewModel.dialog.value)
        viewModel.onDialogConfirm(" thesis ")
        advanceUntilIdle()
        assertEquals(CollectionDialog.New(nameTaken = true), viewModel.dialog.value)
        viewModel.onDialogNameEdited()
        assertEquals(CollectionDialog.New(nameTaken = false), viewModel.dialog.value)

        viewModel.onDialogConfirm("Chapter 2")
        advanceUntilIdle()
        assertNull(viewModel.dialog.value)
        assertEquals(listOf("Chapter 2", "Thesis"), viewModel.header.value.collections.map { it.name })
    }

    @Test
    fun renameAndDelete() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()

        viewModel.onRenameCollection(thesis)
        viewModel.onDialogConfirm("Dissertation")
        advanceUntilIdle()
        assertNull(viewModel.dialog.value)
        assertEquals(listOf("Dissertation"), viewModel.header.value.collections.map { it.name })

        viewModel.onDeleteCollection(viewModel.header.value.collections.single())
        assertTrue(viewModel.dialog.value is CollectionDialog.ConfirmDelete)
        viewModel.onConfirmDelete()
        advanceUntilIdle()
        assertNull(viewModel.dialog.value)
        assertEquals(emptyList<PaperCollection>(), viewModel.header.value.collections)
        assertEquals(3, viewModel.titles().size)
    }

    @Test
    fun aFailedCollectionChangeClosesTheDialogWithAMessage() = runTest {
        thesis()
        val viewModel = viewModel()
        collections.failOnChange = true

        viewModel.onNewCollection()
        viewModel.onDialogConfirm("Chapter 2")
        advanceUntilIdle()

        assertNull(viewModel.dialog.value)
        assertEquals(LibraryMessage.CollectionsUpdateFailed, viewModel.message.value)
    }

    @Test
    fun renameShowsTheClashForTheSameNameInAnotherCaseOrSpacing() = runTest {
        val thesis = thesis()
        collections.create("Chapter 2")
        val viewModel = viewModel()
        advanceUntilIdle()
        val chapter = viewModel.header.value.collections.first { it.name == "Chapter 2" }

        viewModel.onRenameCollection(chapter)
        viewModel.onDialogConfirm(" thesis ")
        advanceUntilIdle()
        assertEquals(CollectionDialog.Rename(chapter, nameTaken = true), viewModel.dialog.value)
        viewModel.onDialogNameEdited()
        assertEquals(CollectionDialog.Rename(chapter, nameTaken = false), viewModel.dialog.value)
        viewModel.onDialogDismiss()
        assertNull(viewModel.dialog.value)
        assertEquals(listOf("Chapter 2", "Thesis"), viewModel.header.value.collections.map { it.name })

        // A new case of its own name is not a clash.
        viewModel.onRenameCollection(thesis)
        viewModel.onDialogConfirm("THESIS")
        advanceUntilIdle()
        assertNull(viewModel.dialog.value)
        assertEquals(listOf("Chapter 2", "THESIS"), viewModel.header.value.collections.map { it.name })
    }

    @Test
    fun renamingACollectionDeletedMeanwhileClosesTheDialogWithAMessage() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()
        viewModel.onRenameCollection(thesis)
        collections.delete(thesis.id)
        advanceUntilIdle()

        viewModel.onDialogConfirm("Dissertation")
        advanceUntilIdle()

        assertNull(viewModel.dialog.value)
        assertEquals(LibraryMessage.CollectionsUpdateFailed, viewModel.message.value)
        assertEquals(emptyList<PaperCollection>(), viewModel.header.value.collections)
    }

    @Test
    fun deletingTheSelectedCollectionFallsBackToAllPapers() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()
        viewModel.onSelectCollection(thesis.id)
        advanceUntilIdle()

        viewModel.onDeleteCollection(viewModel.header.value.selected!!)
        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertNull(viewModel.header.value.selected)
        assertEquals(3, viewModel.header.value.viewSize)
        assertEquals(3, viewModel.titles().size)
    }

    @Test
    fun selectingACollectionDeletedMeanwhileFallsBackToAllPapers() = runTest {
        val thesis = thesis()
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        advanceUntilIdle()
        collections.delete(thesis.id)
        advanceUntilIdle()

        viewModel.onSelectCollection(thesis.id)
        advanceUntilIdle()

        assertNull(viewModel.header.value.selected)
        assertEquals(3, viewModel.titles().size)
        assertNull(handle.get<Long>("library_collection"))
    }

    @Test
    fun exportRunsOnceForTheSelectedCollectionAndNamesTheFile() = runTest {
        val thesis = thesis()
        val viewModel = viewModel()
        viewModel.onSelectCollection(thesis.id)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        citations.gate = gate

        viewModel.onExport()
        viewModel.onExport()
        advanceUntilIdle()
        assertTrue(viewModel.header.value.exporting)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf<Long?>(thesis.id), citations.exports)
        assertEquals(BibExport("Thesis.bib", citations.exportText, complete = true), viewModel.exportReady.value)
        // The screen is still writing and sharing the file: another tap does nothing.
        assertTrue(viewModel.header.value.exporting)
        viewModel.onExport()
        advanceUntilIdle()
        assertEquals(listOf<Long?>(thesis.id), citations.exports)
        viewModel.onExportShared()
        assertNull(viewModel.exportReady.value)
        assertEquals(false, viewModel.header.value.exporting)
        viewModel.onScreenResumed()
        assertNull(viewModel.message.value)
    }

    @Test
    fun incompleteExportShowsItsMessageWhenTheScreenResumes() = runTest {
        thesis()
        citations.complete = false
        val viewModel = viewModel()

        viewModel.onExport()
        advanceUntilIdle()
        assertEquals("hashiya-library.bib", viewModel.exportReady.value?.fileName)
        viewModel.onExportShared()
        assertNull(viewModel.message.value)

        viewModel.onScreenResumed()
        assertEquals(LibraryMessage.ExportIncomplete, viewModel.message.value)
        viewModel.onMessageShown()
        viewModel.onScreenResumed()
        assertNull(viewModel.message.value)
    }

    @Test
    fun failedExportShowsCouldntExport() = runTest {
        thesis()
        citations.failure = IOException("disk full")
        val viewModel = viewModel()

        viewModel.onExport()
        advanceUntilIdle()
        assertEquals(LibraryMessage.ExportFailed, viewModel.message.value)
        assertEquals(false, viewModel.header.value.exporting)

        citations.failure = null
        viewModel.onMessageShown()
        viewModel.onExport()
        advanceUntilIdle()
        viewModel.onExportFailed()
        assertNull(viewModel.exportReady.value)
        assertEquals(LibraryMessage.ExportFailed, viewModel.message.value)
    }
}

private suspend fun FakeCollectionsRepository.observeCollectionIdsNow(openAlexId: String): Set<Long> =
    observeCollectionIds(openAlexId).first()
