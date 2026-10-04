package com.etatech.hashiya.feature.settings

import android.net.Uri
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.BackupSummary
import com.etatech.hashiya.core.testing.FakeLibraryBackup
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private val backup = FakeLibraryBackup().apply { summary = BackupSummary(papers = 182, collections = 6, pdfCount = 41, pdfBytes = 238_000_000) }

    private fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(FakeUserPreferencesRepository(), FakeAppLanguageController(), FakePdfRepository(), backup)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    private val SettingsViewModel.backupState get() = uiState.value.backup

    @Test
    fun loadsTheSummary() = runTest {
        assertEquals(182, viewModel().backupState.summary?.papers)
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
}
