package com.etatech.hashiya.feature.importer

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.model.ImportCounts
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportState
import com.etatech.hashiya.core.testing.FakeImportRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ImportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeImportRepository()

    private class FakeReader(var files: Map<String, ImportFile> = emptyMap()) : ImportFileReader {
        val reads = mutableListOf<String>()

        override suspend fun read(uri: String): ImportFile? {
            reads += uri
            return files[uri]
        }
    }

    private val reader = FakeReader(mapOf("content://thesis" to ImportFile("thesis.bib", ByteArray(20))))

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): ImportViewModel {
        val viewModel = ImportViewModel(repository, reader, handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    @Test
    fun startsAtTheStartWithNoJob() = runTest {
        assertEquals(ImportUiState.Start, viewModel().uiState.value)
    }

    @Test
    fun aChosenFileIsReadAndStarted() = runTest {
        val viewModel = viewModel()
        viewModel.onFileChosen("content://thesis")
        assertEquals(listOf("thesis.bib" to 20), repository.starts)
        assertEquals(ImportState.Analyzing, (viewModel.uiState.value as ImportUiState.Analyzing).job.state)
    }

    @Test
    fun aFileThatCanNotBeReadFailsWithoutAJob() = runTest {
        val viewModel = viewModel()
        viewModel.onFileChosen("content://gone")
        assertEquals(ImportUiState.Failed(ImportFailure.Unreadable), viewModel.uiState.value)
        assertEquals(emptyList<Pair<String, Int>>(), repository.starts)
        viewModel.onDismiss()
        assertEquals(ImportUiState.Start, viewModel.uiState.value)
    }

    @Test
    fun aSharedFileIsReadOnceEvenWhenTheViewModelIsRecreated() = runTest {
        val handle = SavedStateHandle(mapOf(ARG_URI to "content://thesis"))
        viewModel(handle)
        viewModel(handle)
        assertEquals(listOf("content://thesis"), reader.reads)
    }

    @Test
    fun theCheckboxAndConfirmGoToTheRepository() = runTest {
        val viewModel = viewModel()
        repository.job.value = repository.start("thesis.bib", ByteArray(1)).copy(
            state = ImportState.Ready,
            counts = ImportCounts(found = 3, matched = 2, local = 1)
        )
        assertEquals(true, (viewModel.uiState.value as ImportUiState.Ready).addToCollection)
        viewModel.onAddToCollectionChange(false)
        assertEquals(false, (viewModel.uiState.value as ImportUiState.Ready).addToCollection)
        viewModel.onConfirm()
        assertEquals(listOf(1L to false), repository.confirms)
        assertEquals(ImportState.Importing, (viewModel.uiState.value as ImportUiState.Importing).job.state)
    }

    @Test
    fun cancelStopsARunningOrReadyJob() = runTest {
        val viewModel = viewModel()
        viewModel.onFileChosen("content://thesis")
        viewModel.onCancel()
        assertEquals(listOf(1L), repository.cancels)
        assertEquals(ImportState.Cancelled, (viewModel.uiState.value as ImportUiState.Cancelled).job.state)
    }

    @Test
    fun dismissForgetsAFinishedJobButNotARunningOne() = runTest {
        val viewModel = viewModel()
        viewModel.onFileChosen("content://thesis")
        viewModel.onDismiss()
        assertEquals(emptyList<Long>(), repository.dismissals)
        repository.job.value = repository.job.value?.copy(state = ImportState.Done, imported = 3)
        var left = false
        viewModel.onDismiss(then = { left = true })
        assertEquals(listOf(1L), repository.dismissals)
        assertEquals(true, left)
        assertNull(repository.job.value)
        assertEquals(ImportUiState.Start, viewModel.uiState.value)
    }
}
