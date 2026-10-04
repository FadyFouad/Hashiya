package com.etatech.hashiya.feature.settings

import android.net.Uri
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.BackupSummary
import com.etatech.hashiya.core.model.PdfStorage
import com.etatech.hashiya.core.testing.FakeLibraryBackup
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsBackupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val backup = FakeLibraryBackup().apply {
        summary =
            BackupSummary(papers = 182, collections = 6, pdfCount = 41, pdfBytes = 238_000_000)
    }

    private val pdfs = FakePdfRepository()

    private fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(FakeUserPreferencesRepository(), FakeAppLanguageController(), pdfs, backup)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    private val SettingsViewModel.backupState get() = uiState.value.backup

    @Test
    fun loadsTheSummary() = runTest {
        assertEquals(182, viewModel().backupState.summary?.papers)
    }

    @Test
    fun resumingReloadsTheSummaryAndStorage() = runTest {
        val viewModel = viewModel()
        // A restore elsewhere changed the library while Settings was in the back stack.
        backup.summary = BackupSummary(papers = 200, collections = 7, pdfCount = 41, pdfBytes = 238_000_000)
        pdfs.setStorage(PdfStorage(downloadedBytes = 5, downloadedCount = 1, attachedBytes = 0, attachedCount = 0))

        viewModel.onResume()

        assertEquals(200, viewModel.backupState.summary?.papers)
        assertEquals(1, viewModel.uiState.value.storage?.downloadedCount)
    }

    @Test
    fun exportClickReloadsTheSummary() = runTest {
        val viewModel = viewModel()
        backup.summary = BackupSummary(papers = 200, collections = 7, pdfCount = 0, pdfBytes = 0)

        viewModel.onExportClick()

        assertEquals(7, viewModel.backupState.summary?.collections)
    }

    @Test
    fun exportClickIsIgnoredWhileSaving() = runTest {
        val gate = CompletableDeferred<Unit>()
        backup.saveGate = gate
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onConfirmExport()
        viewModel.onSaveDestination(Uri.parse("content://docs/1"))
        assertEquals(ExportState.Saving, viewModel.backupState.export)

        viewModel.onExportClick()

        assertEquals(ExportState.Saving, viewModel.backupState.export)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ExportState.Idle, viewModel.backupState.export)
        assertEquals(listOf(false), backup.exports)
    }

    @Test
    fun aSecondSaveDestinationWhileSavingIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        backup.saveGate = gate
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onConfirmExport()
        val uri = Uri.parse("content://docs/1")
        viewModel.onSaveDestination(uri)

        viewModel.onSaveDestination(Uri.parse("content://docs/2"))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(uri), backup.saved)
        assertEquals(emptyList<Uri>(), backup.deletedDestinations)
    }

    @Test
    fun aDestinationWithNoExportedFileIsDeletedAndReportedAsFailed() = runTest {
        // As after process death while the save dialog was open: a fresh ViewModel holds no archive.
        val viewModel = viewModel()
        val uri = Uri.parse("content://docs/empty")

        viewModel.onSaveDestination(uri)

        assertEquals(listOf(uri), backup.deletedDestinations)
        assertEquals(emptyList<Uri>(), backup.saved)
        assertEquals(ExportState.Idle, viewModel.backupState.export)
        assertEquals(BackupMessage.ExportFailed(BackupFailure.WriteFailed), viewModel.backupState.message)
    }

    @Test
    fun exportWithoutPdfsByDefaultThenSave() = runTest {
        val viewModel = viewModel()
        viewModel.onExportClick()
        assertEquals(ExportState.Choosing(includePdfs = false), viewModel.backupState.export)

        viewModel.onConfirmExport()
        assertEquals(listOf(false), backup.exports)
        assertEquals(ExportState.ReadyToSave("Hashiya-library-2026-10-04.hashiya"), viewModel.backupState.export)

        val uri = Uri.parse("content://docs/1")
        viewModel.onSaveDestination(uri)
        assertEquals(listOf(uri), backup.saved)
        assertEquals(ExportState.Idle, viewModel.backupState.export)
        assertEquals(BackupMessage.Exported(missingPdfs = 0), viewModel.backupState.message)
        assertEquals(1, backup.discardedExports)
    }

    @Test
    fun includePdfsIsPassedThrough() = runTest {
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onIncludePdfsChange(true)
        viewModel.onConfirmExport()
        assertEquals(listOf(true), backup.exports)
    }

    @Test
    fun dismissDiscardsExportedFile() = runTest {
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onConfirmExport()
        viewModel.onSaveDestination(null)
        assertEquals(ExportState.Idle, viewModel.backupState.export)
        assertEquals(1, backup.discardedExports)
        assertEquals(null, viewModel.backupState.message)
    }

    @Test
    fun aFailedExportShowsItsReason() = runTest {
        backup.exportFailure = BackupFailure.NoSpace
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onConfirmExport()
        assertEquals(ExportState.Idle, viewModel.backupState.export)
        assertEquals(BackupMessage.ExportFailed(BackupFailure.NoSpace), viewModel.backupState.message)
        viewModel.onMessageShown()
        assertEquals(null, viewModel.backupState.message)
    }

    @Test
    fun missingPdfsAreReported() = runTest {
        backup.missingPdfs = 2
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onConfirmExport()
        viewModel.onSaveDestination(Uri.parse("content://docs/1"))
        assertEquals(BackupMessage.Exported(missingPdfs = 2), viewModel.backupState.message)
    }

    @Test
    fun cancellingABuildingExportReturnsToIdle() = runTest {
        val gate = CompletableDeferred<Unit>()
        backup.exportGate = gate
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onConfirmExport()
        assertEquals(ExportState.Building(includePdfs = false, progress = 0f), viewModel.backupState.export)

        viewModel.onCancelExport()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(ExportState.Idle, viewModel.backupState.export)
        assertEquals(null, viewModel.backupState.message)
    }
}
