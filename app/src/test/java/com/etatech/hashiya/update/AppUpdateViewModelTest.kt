package com.etatech.hashiya.update

import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.core.testing.FakeAppUpdateRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AppUpdateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAppUpdateRepository()
    private val update = RequiredUpdate("https://play.google.com/store/apps/details?id=com.etatech.hashiya")

    private fun viewModel() = AppUpdateViewModel(repository) { 7L }

    @Test
    fun checksTheInstalledVersionCode() = runTest {
        viewModel().check()

        assertEquals(listOf(7L), repository.checkedVersionCodes)
    }

    @Test
    fun blocksWhenAnUpdateIsRequired() = runTest {
        repository.result = update
        val viewModel = viewModel()

        viewModel.check()

        assertEquals(update, viewModel.requiredUpdate.value)
    }

    @Test
    fun staysUnblockedWhileTheCheckIsRunning() = runTest {
        repository.result = update
        repository.holdChecks()
        val viewModel = viewModel()

        viewModel.check()

        assertNull(viewModel.requiredUpdate.value)
        repository.releaseChecks()
        assertEquals(update, viewModel.requiredUpdate.value)
    }

    @Test
    fun aReturnDuringACheckDoesNotStartAnother() = runTest {
        repository.holdChecks()
        val viewModel = viewModel()

        viewModel.check()
        viewModel.check()
        repository.releaseChecks()

        assertEquals(1, repository.checkedVersionCodes.size)
    }

    @Test
    fun staysBlockedAfterALaterCheckFindsNothing() = runTest {
        repository.result = update
        val viewModel = viewModel()
        viewModel.check()

        repository.result = null
        viewModel.check()

        assertEquals(update, viewModel.requiredUpdate.value)
        assertEquals(1, repository.checkedVersionCodes.size)
    }

    @Test
    fun checksAgainOnReturnWhenNotBlocked() = runTest {
        val viewModel = viewModel()
        viewModel.check()
        viewModel.check()

        assertEquals(2, repository.checkedVersionCodes.size)
    }
}
