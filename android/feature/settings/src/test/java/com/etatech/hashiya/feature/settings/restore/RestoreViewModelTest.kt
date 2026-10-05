package com.etatech.hashiya.feature.settings.restore

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.OpenFailure
import com.etatech.hashiya.core.data.backup.OpenResult
import com.etatech.hashiya.core.data.backup.RestorePreview
import com.etatech.hashiya.core.data.backup.RestoreResult
import com.etatech.hashiya.core.testing.FakeAnalytics
import com.etatech.hashiya.core.testing.FakeLibraryBackup
import com.etatech.hashiya.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestoreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val backup = FakeLibraryBackup()
    private val analytics = FakeAnalytics()
    private val preview = RestorePreview(exportedAt = 1L, papers = 182, collections = 6, pdfs = 41, newPapers = 150, existingPapers = 32)

    /** The test scope stands in for the application scope the restore runs in. */
    private fun TestScope.viewModel() = RestoreViewModel(SavedStateHandle(mapOf("uri" to "content://docs/backup")), backup, this, analytics)

    @Test
    fun opensTheUriAndShowsThePreview() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals("content://docs/backup", backup.opened.single().toString())
        assertEquals(RestoreUiState.Preview(preview), viewModel.uiState.value)
    }

    @Test
    fun showsWhyAFileCantBeRestored() = runTest {
        backup.openResult = OpenResult.Failed(OpenFailure.NewerFormat)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(RestoreUiState.Invalid(OpenFailure.NewerFormat), viewModel.uiState.value)
    }

    @Test
    fun confirmAppliesAndShowsTheResult() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        backup.applyResult = RestoreResult(150, 3, 6, 38, 3)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onConfirm()
        advanceUntilIdle()
        assertEquals(RestoreUiState.Done(backup.applyResult), viewModel.uiState.value)
        assertEquals(1, backup.discardedBackups)
    }

    @Test
    fun aFailedRestoreSaysWhy() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        backup.applyFailure = BackupFailure.NoSpace
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onConfirm()
        advanceUntilIdle()
        assertEquals(RestoreUiState.Failed(BackupFailure.NoSpace), viewModel.uiState.value)
    }

    @Test
    fun cancelDiscards() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onCancel()
        assertEquals(1, backup.discardedBackups)
    }

    @Test
    fun aFinishedRestoreIsCountedAsOk() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onConfirm()
        advanceUntilIdle()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.Restore(succeeded = true)), analytics.events)
    }

    @Test
    fun aFailedRestoreIsCountedAsFailed() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        backup.applyFailure = BackupFailure.NoSpace
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onConfirm()
        advanceUntilIdle()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.Restore(succeeded = false)), analytics.events)
    }

    @Test
    fun cancellingOrAnInvalidFileSendsNothing() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        viewModel().also {
            advanceUntilIdle()
            it.onCancel()
        }
        backup.openResult = OpenResult.Failed(OpenFailure.NotABackup)
        viewModel()
        advanceUntilIdle()
        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }
}
